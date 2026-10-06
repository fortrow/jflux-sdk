package com.fortrow.jflux.http.api.client;

import com.fortrow.jflux.http.api.server.containers.EncryptedPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BinaryEncryptedPayloadTest {
    private final String key = "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20";
    private final String nonce = "000102030405060708090a0b";
    @Test void roundTripsNonUnicodeBytesAndOwnsAad() {
        byte[] aad = {1, 2, 3};
        var payload = new EncryptedPayload("", key, nonce, aad);
        aad[0] = 99;
        byte[] plaintext = {0, -1, -128, 13, 10, 0};
        byte[] ciphertext = payload.encryptBytes(plaintext);
        assertEquals(plaintext.length + 16, ciphertext.length);
        assertArrayEquals(plaintext, new EncryptedPayload("", key, nonce, new byte[] {1, 2, 3}).decryptBytes(ciphertext));
        assertArrayEquals(new byte[0], payload.decryptBytes(payload.encryptBytes(new byte[0])));
    }
    @Test void rejectsCiphertextTamperAndWrongAadNonceOrKey() {
        byte[] ciphertext = new EncryptedPayload("", key, nonce, new byte[] {1}).encryptBytes(new byte[] {0, -1});
        byte[] tampered = ciphertext.clone(); tampered[0] ^= 1;
        assertThrows(SecurityException.class, () -> new EncryptedPayload("", key, nonce, new byte[] {1}).decryptBytes(tampered));
        assertThrows(SecurityException.class, () -> new EncryptedPayload("", key, nonce, new byte[] {2}).decryptBytes(ciphertext));
        assertThrows(SecurityException.class, () -> new EncryptedPayload("", key, "010102030405060708090a0b", new byte[] {1}).decryptBytes(ciphertext));
        assertThrows(SecurityException.class, () -> new EncryptedPayload("", "00".repeat(32), nonce, new byte[] {1}).decryptBytes(ciphertext));
    }
    @Test void rejectsMalformedSizesBeforeCryptoAndPreservesLegacyDecode() {
        var payload = new EncryptedPayload("", key, nonce);
        assertThrows(IllegalArgumentException.class, () -> payload.encryptBytes(new byte[EncryptedPayload.MAX_BINARY_PLAINTEXT_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> payload.decryptBytes(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> new EncryptedPayload("", key, "01").encryptBytes(new byte[0]));
        byte[] bytes = "legacy UTF-8 é".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String encoded = com.fortrow.jflux.encoding.BaseX.encode64(payload.encryptBytes(bytes));
        assertEquals("legacy UTF-8 é", new EncryptedPayload(encoded, key, nonce).decrypt());
    }
}
