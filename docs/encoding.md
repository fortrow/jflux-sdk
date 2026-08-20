# Encoding package

Package: `com.fortrow.jflux.encoding`

The package provides compact hexadecimal and Base64 helpers used by encryption,
configuration, storage, and HTTP code throughout STRATO.

## Hexadecimal

`Hex.encode(byte[])` returns lowercase hexadecimal without creating a temporary byte
array for the character table or decoding each byte through a substring. The older
`getHex(byte[])` name remains available but is deprecated in 1.1.0.

`Hex.decode(String)` is the preferred decoder and throws `IllegalArgumentException` for
malformed input. The older `fromHex(String)` name remains available but is deprecated;
it returns `null` for a null, odd-length, or invalid hexadecimal string to preserve the
1.0.x failure contract. `Hex.isValid(String)` can be used to check input without
allocating a decoded byte array.

Migration from the deprecated API is therefore:

```java
String encoded = Hex.encode(bytes);       // formerly Hex.getHex(bytes)
byte[] decoded = Hex.decode(encoded);     // formerly Hex.fromHex(encoded)
```

Use `Hex.fromHex` only when a `null` result is intentionally part of the caller's
control flow.

## Base64

`BaseX` uses UTF-8 explicitly for all string conversions. This avoids platform-default
charset behavior and lets the `String` overloads pass directly to the JDK Base64
codec without first allocating an intermediate byte array.

The existing `encode64`, `stringFrom64`, and `bytesFrom64` methods remain available.
Invalid input passed to `bytesFrom64(String)` continues to return `null`; the byte-array
overload continues to expose the JDK decoder exception.

All helpers return newly allocated output arrays or strings. They do not retain caller
buffers, so callers may safely reuse or clear their input arrays after a call returns.

## Compatibility notes

The encoded formats are unchanged: hexadecimal is lowercase and Base64 is standard
RFC 4648 Base64 with padding. The only intentional portability correction is that
string conversions now use UTF-8 explicitly instead of the host default charset.

## Error handling

Malformed hexadecimal and Base64 input is routed through the jflux exception
reporting bridge. `Hex.decode` still throws `IllegalArgumentException`, while the
legacy nullable helpers still return `null` after reporting the failure. This lets
applications centralize error logging without forcing a source-incompatible
change on existing STRATO callers.
