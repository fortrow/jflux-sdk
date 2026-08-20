# HTTP, REST, and encrypted APIs

JFlux 1.1.0 treats HTTP failures, invalid request data, and encrypted protocol
failures as reportable API events through `APIException`, which extends
`ReportedException`. Applications can configure the normal exception reporting
policy and use `tryReport()` when an API failure should be escalated explicitly.

## Server request boundaries

`APIServerConfig` limits the request body retained in memory. The default is
4 MiB and applications can change it before starting the server:

```java
APIServerConfig config = new APIServerConfig(8443, "/api")
        .setMaxRequestBodyBytes(2 * 1024 * 1024);
```

Requests over the limit receive HTTP 413 with a `request_too_large` status. Body
reads use bounded byte buffers and UTF-8 decoding rather than repeated string
concatenation.

JSON request bodies are validated before endpoint execution. Malformed JSON,
non-object JSON payloads, and missing registered endpoint parameters are rejected
as request failures. Missing or malformed encrypted timestamps are rejected as
unauthorized requests instead of becoming server errors.

## Client resource handling

`RESTClient` and `EncryptedClient` should be used with try-with-resources. Their
HTTP/1 response entities are consumed and released, HTTP/3 transports are closed
with the client, and encrypted request execution does not share mutable request
objects between concurrent calls.

```java
try (RESTClient client = new RESTClient(apiKey, endpoint, HttpVersion.HTTP_1, false).build()) {
    RESTClient.Response response = client.get("/health", null);
}
```

The encrypted client continues to expose `beginSession`, `beginSessionOrThrow`,
`request`, `requestAndWait`, and `closeSessionOrThrow`. Invalid encrypted response
headers or authentication material result in an unverified response or a
reportable `APIException`; callers must check `isVerified()` before trusting
encrypted response data.

Malformed encrypted response JSON or ciphertext is also treated as an
unverified response and is sent through the configured exception reporters.
Resource paths are normalized only when they begin with the configured API
prefix, preventing unrelated path text from being stripped accidentally.

## Compatibility

Existing REST and encrypted public entry points remain available. Header and
body containers now return safe null/empty values for absent optional fields,
while strict request validation occurs at the server boundary. Existing
application-level error response codes and JSON status fields are preserved.
