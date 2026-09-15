# SSL and certificate helpers

JFlux 1.1.0 provides certificate-file and Java keystore helpers under
`com.fortrow.jflux.ssl`.

## Self-signed certificates

`SelfSignedSSL.create(...)` uses an argument-list OpenSSL invocation, creates the
output directory when needed, and adds a subject alternative name for the host:

```java
SslCertKeyPair pair = SelfSignedSSL.create(
        "/var/lib/my-api/tls", "server.pem", "US", "Virginia", "Richmond",
        "Example", "Engineering", "api.example.com");
JavaKeyStore keyStore = new JavaKeyStore(pair, "v1.3");
```

The overload accepting additional names supports DNS names and values prefixed
with `IP:`. Certificate subject fields, filenames, and output paths are validated
before OpenSSL runs. OpenSSL execution has a bounded timeout and failures use the
JFlux exception bridge instead of printing stack traces.

`SelfSignedSSL.createWithCA(...)` also supports signing from a CA certificate and
CA private key. The older overload that accepts only one CA file remains for
source compatibility but reports that a CA private key is required.

## JavaKeyStore

`JavaKeyStore` accepts PKCS#8 PEM private keys and one or more PEM X.509
certificates. The first certificate is used as the key entry certificate and any
following certificates form the chain. TLS versions are limited to `TLSv1.2` and
`TLSv1.3`; values such as `v1.3` and `TLSv1.3` are accepted.

Keystore initialization now fails immediately with a reported operational error
when files, PEM data, key material, or TLS configuration are invalid. It no longer
returns a null `SSLContext` or prints a stack trace. `SslCertKeyPair` defaults to
an empty key-entry password because the existing OpenSSL helper creates an
unencrypted PKCS#8 key; callers can provide a password explicitly.

`JavaKeyStore.createLetsEncrypt()` is deprecated because a Let's Encrypt context
requires an account, domain, and output directory. Use `LetsEncrypt.request(...)`
first, then pass its returned `SslCertKeyPair` to `JavaKeyStore`.
