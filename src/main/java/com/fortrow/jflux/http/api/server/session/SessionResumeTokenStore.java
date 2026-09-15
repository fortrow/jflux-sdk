package com.fortrow.jflux.http.api.server.session;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.output.json.JSON;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores only hashed resume-token metadata. Raw resume tokens are returned to
 * the client once and are never written to disk.
 */
public final class SessionResumeTokenStore {

    public enum ConsumeResult { VALID, UNKNOWN, REPLAY, EXPIRED, API_KEY_MISMATCH }

    private record Entry(String tokenHash, String apiKeyHash, String sessionId, long expiresAtMs, boolean used) { }

    private final Path path;
    private final List<Entry> entries = new ArrayList<>();

    public SessionResumeTokenStore(Path path) throws APIException {
        this.path = path == null ? null : path.toAbsolutePath().normalize();
        load();
    }

    public synchronized String issue(String apiKey, String secret, String sessionId, Duration lifetime) throws APIException {
        require(apiKey, "API key");
        require(secret, "API secret");
        require(sessionId, "session id");
        Duration safeLifetime = lifetime == null || lifetime.isNegative() || lifetime.isZero()
                ? Duration.ofMinutes(30) : lifetime;
        String nonce = Hex.getHex(Rand.secure(32));
        String proof = SHA.getHmac384(apiKey + ":" + sessionId + ":" + nonce, secret);
        String token = "jflux_resume_v1." + nonce + "." + proof;
        entries.removeIf(entry -> entry.expiresAtMs() <= System.currentTimeMillis());
        entries.add(new Entry(hash(token), hash(apiKey), sessionId,
                Instant.now().plus(safeLifetime).toEpochMilli(), false));
        persist();
        return token;
    }

    public synchronized ConsumeResult consume(String apiKey, String secret, String token) throws APIException {
        if (apiKey == null || apiKey.isBlank() || secret == null || secret.isBlank() || token == null || token.isBlank()) {
            return ConsumeResult.UNKNOWN;
        }
        String tokenHash = hash(token);
        Entry matching = null;
        for (Entry entry : entries) {
            if (SecureCompare.equals(entry.tokenHash(), tokenHash)) {
                matching = entry;
                break;
            }
        }
        if (matching == null) return isTokenShape(token) ? ConsumeResult.REPLAY : ConsumeResult.UNKNOWN;
        if (matching.used()) return ConsumeResult.REPLAY;
        if (matching.expiresAtMs() <= System.currentTimeMillis()) {
            replace(matching, new Entry(matching.tokenHash(), matching.apiKeyHash(), matching.sessionId(), matching.expiresAtMs(), true));
            persist();
            return ConsumeResult.EXPIRED;
        }
        if (!SecureCompare.equals(matching.apiKeyHash(), hash(apiKey))) return ConsumeResult.API_KEY_MISMATCH;
        if (!verifyProof(apiKey, secret, matching.sessionId(), token)) return ConsumeResult.API_KEY_MISMATCH;
        replace(matching, new Entry(matching.tokenHash(), matching.apiKeyHash(), matching.sessionId(), matching.expiresAtMs(), true));
        persist();
        return ConsumeResult.VALID;
    }

    /** Issues or refreshes one stable token without closing the associated session. */
    public synchronized String issueWhileActive(String apiKey, String secret, String sessionId, Duration lifetime) throws APIException {
        require(apiKey, "API key"); require(secret, "API secret"); require(sessionId, "session id");
        String nonce = SHA.getHmac384("JFLUX-EARLY-RESUME/1:" + apiKey + ":" + sessionId, secret);
        String token = "jflux_resume_v1." + nonce + "." + SHA.getHmac384(apiKey + ":" + sessionId + ":" + nonce, secret);
        entries.removeIf(entry -> entry.apiKeyHash().equals(hash(apiKey)) && entry.sessionId().equals(sessionId));
        entries.add(new Entry(hash(token), hash(apiKey), sessionId, Instant.now().plus(lifetime).toEpochMilli(), false));
        persist();
        return token;
    }

    /** Returns the associated session only for an unconsumed, authenticated token. */
    public boolean isEarlyToken(String apiKey, String secret, String sessionId, String token) {
        String nonce = SHA.getHmac384("JFLUX-EARLY-RESUME/1:" + apiKey + ":" + sessionId, secret);
        return SecureCompare.equals("jflux_resume_v1." + nonce + "."
                + SHA.getHmac384(apiKey + ":" + sessionId + ":" + nonce, secret), token);
    }

    /** Returns the associated session only for an unconsumed, authenticated token. */
    public synchronized String sessionForToken(String apiKey, String secret, String token) {
        if (apiKey == null || secret == null || token == null) return null;
        for (Entry entry : entries) {
            if (!entry.used() && entry.expiresAtMs() > System.currentTimeMillis()
                    && entry.tokenHash().equals(hash(token)) && entry.apiKeyHash().equals(hash(apiKey))
                    && verifyProof(apiKey, secret, entry.sessionId(), token)) return entry.sessionId();
        }
        return null;
    }

    /** Tokens attached to live sessions do not reserve an additional slot. */
    public synchronized int outstandingForApiKey(String apiKey, java.util.Set<String> liveSessions) {
        return (int) entries.stream().filter(entry -> !entry.used()
                && entry.expiresAtMs() > System.currentTimeMillis()
                && entry.apiKeyHash().equals(hash(apiKey)) && !liveSessions.contains(entry.sessionId()))
                .map(Entry::sessionId).distinct().count();
    }

    public synchronized void invalidateForSession(String apiKey, String sessionId) throws APIException {
        entries.removeIf(entry -> entry.apiKeyHash().equals(hash(apiKey)) && entry.sessionId().equals(sessionId));
        persist();
    }

    public synchronized int outstandingForApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return 0;
        pruneExpired();
        String apiKeyHash = hash(apiKey);
        int count = 0;
        for (Entry entry : entries) {
            if (!entry.used() && entry.expiresAtMs() > System.currentTimeMillis()
                    && SecureCompare.equals(entry.apiKeyHash(), apiKeyHash)) count++;
        }
        return count;
    }

    public synchronized void invalidateForApiKey(String apiKey) throws APIException {
        if (apiKey == null || apiKey.isBlank()) return;
        String apiKeyHash = hash(apiKey);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (SecureCompare.equals(entry.apiKeyHash(), apiKeyHash)) {
                entries.set(i, new Entry(entry.tokenHash(), entry.apiKeyHash(), entry.sessionId(), entry.expiresAtMs(), true));
            }
        }
        persist();
    }

    public synchronized void invalidateAll() throws APIException {
        entries.clear();
        persist();
    }

    private void load() throws APIException {
        if (path == null || !Files.exists(path)) return;
        try {
            JSONObject root = JSON.getObject(Files.readString(path, StandardCharsets.UTF_8));
            Object rawEntries = root.get("entries");
            if (!(rawEntries instanceof JSONArray array)) return;
            for (Object raw : array) {
                if (!(raw instanceof JSONObject object)) continue;
                String tokenHash = string(object.get("token_hash"));
                String apiKeyHash = string(object.get("api_key_hash"));
                String sessionId = string(object.get("session_id"));
                long expires = number(object.get("expires_at_ms"));
                boolean used = Boolean.parseBoolean(string(object.get("used")));
                if (!tokenHash.isBlank() && !apiKeyHash.isBlank() && !sessionId.isBlank() && expires > 0) {
                    entries.add(new Entry(tokenHash, apiKeyHash, sessionId, expires, used));
                }
            }
            pruneExpired();
        } catch (Exception ex) {
            throw new APIException("Failed to load encrypted session resume-token metadata.", ex);
        }
    }

    private void persist() throws APIException {
        if (path == null) return;
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            JSONObject root = new JSONObject();
            JSONArray array = new JSONArray();
            for (Entry entry : entries) {
                JSONObject object = new JSONObject();
                object.put("token_hash", entry.tokenHash());
                object.put("api_key_hash", entry.apiKeyHash());
                object.put("session_id", entry.sessionId());
                object.put("expires_at_ms", entry.expiresAtMs());
                object.put("used", entry.used());
                array.add(object);
            }
            root.put("entries", array);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.getString(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            secureOwnerOnly(path);
        } catch (IOException ex) {
            throw new APIException("Failed to persist encrypted session resume-token metadata.", ex);
        }
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        entries.removeIf(entry -> entry.expiresAtMs() <= now && entry.used());
    }

    private void replace(Entry oldEntry, Entry newEntry) {
        int index = entries.indexOf(oldEntry);
        if (index >= 0) entries.set(index, newEntry);
    }

    private static boolean verifyProof(String apiKey, String secret, String sessionId, String token) {
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3 || !"jflux_resume_v1".equals(parts[0])) return false;
        String expected = SHA.getHmac384(apiKey + ":" + sessionId + ":" + parts[1], secret);
        return SecureCompare.equals(expected, parts[2]);
    }

    private static boolean isTokenShape(String token) {
        return token.startsWith("jflux_resume_v1.");
    }

    private static String hash(String value) { return SHA.get256(value); }
    private static String string(Object value) { return value == null ? "" : value.toString(); }
    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(string(value)); } catch (NumberFormatException ignored) { return -1; }
    }
    private static void require(String value, String label) throws APIException {
        if (value == null || value.isBlank()) throw new APIException(label + " is required for a resume token.");
    }

    private static void secureOwnerOnly(Path path) {
        try {
            Files.setPosixFilePermissions(path, java.util.EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and other non-POSIX filesystems do not expose this mode.
        }
    }
}
