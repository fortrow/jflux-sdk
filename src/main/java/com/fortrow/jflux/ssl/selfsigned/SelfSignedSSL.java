/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

package com.fortrow.jflux.ssl.selfsigned;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;
import com.fortrow.jflux.ssl.SslCertKeyPair;
import com.fortrow.jflux.sys.PackageUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** OpenSSL-backed self-signed and private-CA certificate helpers. */
public final class SelfSignedSSL {

    private static final Duration OPENSSL_TIMEOUT = Duration.ofMinutes(2);

    private SelfSignedSSL() { }

    public static boolean isAvailable() {
        return PackageUtils.executableAvailable("openssl");
    }

    public static void install() {
        if (isAvailable()) return;
        PackageUtils.InstallResult result = PackageUtils.install("openssl");
        if (!result.success()) throw ExceptionHandling.failure("Unable to install OpenSSL.",
                new IllegalStateException(result.output()));
    }

    public static SslCertKeyPair create(String filepath, String filename, String country, String state,
                                        String city, String organization, String department, String hostname) {
        return create(filepath, filename, country, state, city, organization, department, hostname, hostname);
    }

    /** Creates a self-signed certificate with DNS/IP subject alternative names. */
    public static SslCertKeyPair create(String filepath, String filename, String country, String state,
                                        String city, String organization, String department,
                                        String hostname, String... subjectAltNames) {
        Path directory = prepareDirectory(filepath);
        String base = safeBaseName(filename);
        Path key = directory.resolve(base + ".pem");
        Path cert = directory.resolve(base + "-cert.pem");
        runOpenSsl(List.of("openssl", "req", "-x509", "-newkey", "rsa:4096",
                "-keyout", key.toString(), "-out", cert.toString(), "-sha256", "-days", "3650", "-nodes",
                "-subj", subject(country, state, city, organization, department, hostname),
                "-addext", "subjectAltName=" + subjectAltNames(subjectAltNames)),
                directory, "Unable to create self-signed TLS certificate.");
        return new SslCertKeyPair(key.toFile(), cert.toFile());
    }

    /** Compatibility overload retained; signing requires both CA files. */
    public static SslCertKeyPair createWithCA(String filepath, String filename, File ca, String country,
                                              String state, String city, String organization,
                                              String department, String hostname) {
        throw ExceptionHandling.unsupported("A CA private key is required. Use the createWithCA overload that accepts both CA certificate and private key files.");
    }

    /** Creates a certificate signed by a supplied private CA. */
    public static SslCertKeyPair createWithCA(String filepath, String filename, File caCertificate,
                                              File caPrivateKey, String country, String state, String city,
                                              String organization, String department, String hostname,
                                              String... subjectAltNames) {
        if (caCertificate == null || caPrivateKey == null || !caCertificate.isFile() || !caPrivateKey.isFile()) {
            throw ExceptionHandling.invalid("CA certificate and private key files must exist.");
        }
        Path directory = prepareDirectory(filepath);
        String base = safeBaseName(filename);
        Path key = directory.resolve(base + ".pem");
        Path csr = directory.resolve(base + ".csr.pem");
        Path cert = directory.resolve(base + "-cert.pem");
        Path serial = directory.resolve(base + ".srl");
        try {
            runOpenSsl(List.of("openssl", "req", "-new", "-newkey", "rsa:4096", "-nodes",
                    "-keyout", key.toString(), "-out", csr.toString(),
                    "-subj", subject(country, state, city, organization, department, hostname),
                    "-addext", "subjectAltName=" + subjectAltNames(subjectAltNames)),
                    directory, "Unable to create the CA-signed certificate request.");
            runOpenSsl(List.of("openssl", "x509", "-req", "-in", csr.toString(),
                    "-CA", caCertificate.getAbsolutePath(), "-CAkey", caPrivateKey.getAbsolutePath(),
                    "-CAcreateserial", "-CAserial", serial.toString(), "-out", cert.toString(),
                    "-days", "3650", "-sha256", "-copy_extensions", "copy"),
                    directory, "Unable to sign the TLS certificate with the supplied CA.");
            return new SslCertKeyPair(key.toFile(), cert.toFile());
        } finally {
            try { Files.deleteIfExists(csr); } catch (Exception ignored) { }
            try { Files.deleteIfExists(serial); } catch (Exception ignored) { }
        }
    }

    private static Path prepareDirectory(String filepath) {
        if (filepath == null || filepath.isBlank()) throw ExceptionHandling.invalid("Certificate output directory is required.");
        try {
            Path directory = Path.of(filepath).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            return directory;
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to prepare the certificate output directory.", e);
        }
    }

    private static String safeBaseName(String filename) {
        if (filename == null || filename.isBlank()) throw ExceptionHandling.invalid("Certificate filename is required.");
        String base = Path.of(filename).getFileName().toString().replaceFirst("(?i)\\.pem$", "");
        if (base.isBlank() || !base.matches("[A-Za-z0-9._-]+")) throw ExceptionHandling.invalid("Certificate filename contains unsupported characters.");
        return base;
    }

    private static String subject(String country, String state, String city, String organization,
                                  String department, String hostname) {
        return "/C=" + subjectPart(country) + "/ST=" + subjectPart(state) + "/L=" + subjectPart(city)
                + "/O=" + subjectPart(organization) + "/OU=" + subjectPart(department) + "/CN=" + subjectPart(hostname);
    }

    private static String subjectPart(String value) {
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw ExceptionHandling.invalid("Certificate subject fields cannot be blank or contain line breaks.");
        }
        return value.replace("/", "\\/");
    }

    private static String subjectAltNames(String[] values) {
        if (values == null || values.length == 0) return "DNS:localhost";
        ArrayList<String> names = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw ExceptionHandling.invalid("Certificate subject alternative names cannot be blank or contain line breaks.");
            }
            names.add(value.startsWith("IP:") || value.startsWith("DNS:") ? value : "DNS:" + value);
        }
        return String.join(",", names);
    }

    private static void runOpenSsl(List<String> command, Path directory, String message) {
        if (!isAvailable()) throw ExceptionHandling.failure("OpenSSL is not available.", null);
        CommandResult result = Shell.execute(command, directory, OPENSSL_TIMEOUT);
        if (!result.success()) throw ExceptionHandling.failure(message, new IllegalStateException(result.output()));
    }
}
