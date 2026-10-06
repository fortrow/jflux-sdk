package com.fortrow.jflux.websockets.server;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

/** Immutable DER snapshot obtained exclusively from a verified TLS peer session. */
public final class TlsPeerIdentity {
    private final byte[][] certificateChain;
    private final String leafSha256;
    private TlsPeerIdentity(Certificate[] chain) throws CertificateEncodingException {
        if (chain.length == 0 || chain.length > 32)
            throw ExceptionHandling.invalid("Verified TLS certificate chain size is invalid.");
        certificateChain = new byte[chain.length][];
        for (int index = 0; index < chain.length; index++) {
            if (!(chain[index] instanceof X509Certificate))
                throw ExceptionHandling.invalid("Verified TLS peer certificate must be X509.");
            byte[] encoded = chain[index].getEncoded();
            if (encoded.length == 0 || encoded.length > 65_536)
                throw ExceptionHandling.invalid("Verified TLS certificate size is invalid.");
            certificateChain[index] = encoded.clone();
        }
        try { leafSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificateChain[0])); }
        catch (java.security.NoSuchAlgorithmException error) { throw ExceptionHandling.failure("SHA-256 unavailable.", error); }
    }
    /** Trust verification belongs to the TLS handshake; no caller headers are accepted. */
    public static TlsPeerIdentity fromVerifiedSession(SSLSession session) {
        if (session == null) throw ExceptionHandling.invalid("Verified TLS session is required.");
        try { return new TlsPeerIdentity(session.getPeerCertificates()); }
        catch (SSLPeerUnverifiedException | CertificateEncodingException error) {
            throw ExceptionHandling.failure("TLS peer identity is unavailable.", error);
        }
    }
    public String leafSha256() { return leafSha256; }
    public byte[][] certificateChainDer() {
        byte[][] copy = new byte[certificateChain.length][];
        for (int index = 0; index < copy.length; index++) copy[index] = certificateChain[index].clone();
        return copy;
    }
}
