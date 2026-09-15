# Email package

`com.fortrow.jflux.email` provides a dependency-free SMTP client for text,
HTML, and file-attachment messages. It uses the JDK socket and TLS APIs, so the
email package adds no mail-provider SDK or transitive transport dependency to
the core artifact.

## SMTP configuration

Use a `char[]` password when it originates from a secret store. `SMTP`
defensively copies it and returns copies from the configuration accessor, which
avoids exposing one long-lived mutable array. A string constructor remains in
the provider clients for existing configuration sources.

```java
SMTP.Configuration config = new SMTP.Configuration(
        "mail.example.com", 587, "alerts@example.com", appPassword,
        SMTP.Security.STARTTLS, SMTP.Authentication.AUTO, "service.example.com",
        Duration.ofSeconds(10), Duration.ofSeconds(30), 25L * 1024 * 1024);
SMTP smtp = new SMTP(config);
```

`STARTTLS` is the default and requires the server to advertise the extension.
`IMPLICIT_TLS` is available for servers such as port 465. `PLAINTEXT` is only
valid for an unauthenticated local relay: the configuration rejects credentials
over an unencrypted connection. TLS connections enable endpoint identification
before the handshake.

Authentication is `AUTO` by default, selecting `AUTH PLAIN` and then `AUTH
LOGIN` from the server's post-TLS ESMTP capabilities. Select `PLAIN`, `LOGIN`,
or `NONE` explicitly only when the server contract requires it.

## Creating and sending messages

Use the builder instead of the positional `Message` record constructor:

```java
SMTP.Message message = SMTP.message()
        .from("alerts@example.com")
        .to("oncall@example.com")
        .cc("operations@example.com")
        .subject("Database backup complete")
        .text("The scheduled backup completed successfully.")
        .html("<p>The scheduled <strong>backup</strong> completed successfully.</p>")
        .attach(Path.of("reports", "backup-summary.txt"))
        .build();

SMTP.Delivery delivery = smtp.send(message);
```

Messages support `to`, `cc`, `bcc`, `replyTo`, UTF-8 text/HTML alternatives,
and streamed attachments. BCC addresses are used for the SMTP envelope but are
not emitted as message headers. Attachments are read in 57-byte chunks and
encoded as 76-character MIME Base64 lines, so an entire attachment is never
copied into memory. The configured size limit applies to the raw body and all
attachment bytes before a connection is opened.

`send` throws JFlux `CommonIOException` for network and protocol failure.
`trySend` returns an `SMTP.Attempt` instead and is appropriate for optional
notifications or error reporters. `sendAsync` runs `send` on a Java virtual
thread and completes its future exceptionally with the same `CommonIOException`.

Address, header, file-name, content-type, and SMTP client-name inputs reject
line breaks and other header-injection values. Sender and recipient values are
plain addresses; display-name parsing is intentionally left to an application
that owns that policy. SMTPUTF8/internationalized envelope addresses are not
negotiated; use ASCII/punycode addresses with this transport.

## Gmail and Outlook

`Gmail` and `Outlook` are thin provider clients over `SMTP` with safe STARTTLS
defaults. Their `message()` builders already set the configured account as the
sender.

```java
Gmail gmail = new Gmail("alerts@example.com", googleAppPassword);
gmail.send("oncall@example.com", "Service alert", "The worker queue is unavailable.");

Outlook outlook = new Outlook("alerts@company.example", smtpPassword);
SMTP.Attempt attempt = outlook.trySend(
        outlook.message().to("oncall@company.example").subject("Alert").text("Details").build());
```

Gmail uses `smtp.gmail.com:587`; Google commonly requires an app password.
Outlook/Microsoft 365 uses `smtp.office365.com:587`. Microsoft tenant policy can
disable password-based SMTP AUTH, in which case this package reports a normal
delivery failure rather than attempting an OAuth browser/device flow.

## Exception reporting integration

`exceptions.reporting.types.EmailReporter` now delegates to this SMTP
transport. It uses `trySend`, so a failed report email returns `false` and
never replaces or recursively reports the exception that triggered it. The
legacy reporter configuration API remains compatible; both Gmail and Outlook
configuration helpers select STARTTLS.
