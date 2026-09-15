# Crypto package

Package: `com.fortrow.jflux.crypto`

The crypto package now has an adapter-oriented API so applications can select an
algorithm by stable name and replace its implementation at registration time. This
keeps algorithm selection out of application business logic and gives STRATO projects
a migration path from legacy algorithms to newer ones.

## Adapter categories

The adapter types intentionally describe different cryptographic jobs:

- `SymmetricAlgorithm` encrypts and decrypts payloads with a shared key.
- `AsymmetricAlgorithm` preserves the existing legacy public-key encryption shape.
- `KeyEncapsulationAlgorithm` establishes a shared secret and returns an
  `EncapsulatedSecret` containing the encapsulation and secret.
- `SignatureAlgorithm` signs and verifies messages.
- `HashAlgorithm` and `HmacAlgorithm` provide digest and keyed-digest selection.

ML-KEM is a KEM, not a general-purpose payload cipher. ML-DSA and SLH-DSA are
signatures, not encryption algorithms. Keeping these operations separate prevents a
future RSA migration from silently changing a protocol's meaning.

## Builtin algorithms

The default registry includes:

- `chacha20-poly1305`, using the JDK ChaCha20-Poly1305 implementation;
- ML-KEM-512, ML-KEM-768, and ML-KEM-1024;
- ML-DSA-44, ML-DSA-65, and ML-DSA-87;
- SHA2 SLH-DSA parameter sets 128/192/256 with fast (`f`) and small-signature
  (`s`) variants.

ML-KEM, ML-DSA, and SLH-DSA correspond to NIST FIPS 203, FIPS 204, and FIPS 205.
The standard references are [FIPS 203](https://csrc.nist.gov/pubs/fips/203/final),
[FIPS 204](https://csrc.nist.gov/pubs/fips/204/final), and
[FIPS 205](https://csrc.nist.gov/pubs/fips/205/final).

## Provider and jar-size behavior

The PQC implementations use the Bouncy Castle lightweight API version `1.85.2`.
The Maven dependency is optional so core consumers that do not use PQC do not receive
the roughly 10 MB provider jar transitively. `CryptographyRegister.fromDefaults()`
registers PQC algorithms only when the provider classes are available.

Applications using the core jar on Java 23 or earlier must add:

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk18on</artifactId>
  <version>1.85.2</version>
</dependency>
```

The full shaded jflux artifact includes the optional provider. The core, database, and
HTTP split artifacts intentionally do not include it yet; this keeps ordinary runtime
images small and lets a later packaging milestone provide a dedicated PQC classifier.

The regular Bouncy Castle provider should not be described as a FIPS 140 validated
cryptographic module. A regulated deployment must supply and configure its validated
provider, then register compatible adapter implementations at application startup.

## Selecting and replacing algorithms

```java
CryptographyRegister algorithms = CryptographyRegister.fromDefaults();
Cryptography crypto = new Cryptography(algorithms);

byte[][] kemKeys = crypto.generateKeyPair("ml-kem-768");
EncapsulatedSecret encapsulated = crypto.encapsulate("ml-kem-768", kemKeys[0]);
byte[] sharedSecret = crypto.decapsulate(
        "ml-kem-768", encapsulated.encapsulation(), kemKeys[1]);
```

`Cryptography.defaults()` provides the process-wide standard register for small
applications. It also exposes the application-facing helpers used by MeridianDB:
`hashHex`, `hmacHex`, `deriveKey`, `pbkdf2`, `randomBytes`, `randomString`, `id`,
`shortId`, and `secureEquals`. The default register includes `hmac-sha1` for
legacy TOTP interoperability. Ed25519
signatures and RSA-2048/RSA-4096 key pairs are registered by name. X25519 key
generation and key agreement are available through `generateKeyPair("x25519")`
and `keyAgreement("x25519", privateKey, publicKey)`.

Applications should use these facade methods instead of importing classes from
`com.fortrow.jflux.crypto.builtin`; the builtin implementations remain an internal
implementation detail of the default registry.

To switch an implementation without changing callers, register a replacement under
the same normalized name. The register uses direct map lookup and case-insensitive
algorithm names; unknown names fail with `IllegalArgumentException` instead of
returning an ambiguous empty byte array.

## Error handling

Crypto APIs now route validation and operational failures through
`com.fortrow.jflux.exceptions.ExceptionHandling`. Invalid keys, non-existent
algorithms, unsupported parameter sets, and invalid lengths are reported as jflux
errors before the legacy `IllegalArgumentException` is returned to the caller.
Provider and reflection failures are reported before the existing
`IllegalStateException` or sentinel result is produced. This preserves the 1.0.x
method contracts while allowing an application to configure one automatic error
logger and reporting policy for all crypto implementations.

The adapter classes retain empty-array results for an adapter that has not yet had
a legacy operation registered. That state is now reported as an operational error;
applications should register a real operation or use one of the built-in adapters.

Key arrays and encapsulation results are copied at adapter boundaries. This makes
ownership explicit and prevents provider-owned buffers from being retained by callers.
