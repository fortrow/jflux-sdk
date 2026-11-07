# JFlux

**A universal software development kit for Java.**

JFlux brings the utilities used across applications, command-line tools, backend
services, and system integrations into one toolkit. It provides practical APIs
for files, structured data, databases, networking, cryptography, process
execution, terminal interfaces, and dynamic runtimes under `com.fortrow.jflux`.

Developed by **Fortrow LLC**, JFlux emphasizes developer-friendly interfaces,
bounded resource use, explicit cleanup, and consistent error reporting. It is
under active development; support and maturity vary by package. Review the APIs
you depend on and test upgrades with your application.

## What is included

| Area | Capabilities |
| --- | --- |
| Files and storage | UTF-8 and binary file helpers, paths, JSON files, logging, symlinks, and storage-volume integrations. |
| Structured data | JSON-Simple-first parsing, optional Gson conversions/pretty printing, YAML, streaming CSV, and XML utilities. |
| Networking and APIs | HTTP clients, REST and encrypted APIs with configurable post-quantum key envelopes, session lifecycle controls, WebSocket clients/servers, sockets, TLS, and mTLS configuration. |
| Cryptography | Algorithm adapters, AES, ChaCha20-Poly1305, hashing/HMAC, key derivation, and optional ML-KEM/ML-DSA/SLH-DSA primitives. |
| Databases | SQLite and MySQL query builders, prepared values, batch inserts, structured PRAGMA support, and Redis connections. |
| Concurrency | Virtual-thread-backed workers, task lifecycle helpers, bounded executors, and timeouts. |
| System integration | Cross-platform OS/package utilities, shell commands, Git wrappers, source installers, and firewall helpers. |
| Terminal tools | ANSI styling, prompts, progress displays, and an interactive JSON console editor. |
| Runtime and native code | Dynamic Java package loading/execution, encrypted runtime packages, and cross-platform JNI build/loading helpers. |
| Application services | Central exception logging/reporting, MCP server/client utilities, SMTP email, and cloud-storage integrations. |
| Everyday helpers | Lodash-style `__` collection/string utilities, encoding, regex, identifiers, and related application helpers. |

Java utilities are reusable across platforms. Native libraries, installers,
firewall controls, and OS commands depend on the target platform and available
executables. Administrative operations require the appropriate permissions.

## Requirements

- **JDK 23 or newer**; the current Maven configuration targets Java 23.
- **Maven 3.9+** and Git for source builds.
- Network access for Maven dependency downloads.
- Matching custom HTTP/3/mTLS dependency builds, described below.
- Feature-specific tools such as OpenSSL, Git, or a C compiler when using the
  corresponding certificate, source-installation, or JNI functionality.

Check your environment:

```sh
java -version
mvn -version
```

### Custom HTTP/3 dependencies

This checkout depends on `tech.kwik:kwik:0.10.9-mtls`, whose POM also references
`tech.kwik:agent15:3.3-mtls`. These are custom mTLS builds and are not ordinary
Maven Central releases. Their source is available in Fortrow's public GitHub
forks:

| Repository | Role in JFlux |
| --- | --- |
| [fortrow/agent15](https://github.com/fortrow/agent15) | TLS 1.3 implementation with the client-certificate authentication changes used by `3.3-mtls`. |
| [fortrow/kwik](https://github.com/fortrow/kwik) | QUIC transport with the server mTLS controls used by `0.10.9-mtls`. |
| [fortrow/flupke](https://github.com/fortrow/flupke) | Public fork of the Java HTTP/3 layer; JFlux currently consumes Flupke `0.9.4` from Maven. |

The `jflux-mtls` branch in each fork contains Fortrow's current source. Build
Agent15 before Kwik, and keep the custom versions above when generating JARs and
dependency POMs. [QPACK](https://github.com/ptrd/qpack) is an upstream dependency
available separately; JFlux does not require a custom QPACK fork.

#### Build the custom dependencies from source

Run from a workspace directory where the dependency checkouts can live side by
side. The wrappers provide Gradle; Windows users can run `gradlew.bat` instead
of `./gradlew` from each project directory.

```sh
git clone --branch jflux-mtls https://github.com/fortrow/agent15.git
git clone --branch jflux-mtls https://github.com/fortrow/kwik.git

cd agent15
./gradlew -PpublicationVersion=3.3-mtls \
  publishToMavenLocal -x signLibraryPublication

cd ../kwik
./gradlew -PpublicationVersion=0.10.9-mtls -Pagent15Version=3.3-mtls \
  :kwik:publishToMavenLocal -x :kwik:signMavenPublication
cd ..
```

This installs the custom JARs and their POMs in your local Maven repository.
Signing is skipped for local installation; these commands do not publish to
Maven Central. Flupke and QPACK do not need to be built from source for the
current JFlux dependency configuration.

#### Install prebuilt custom dependencies

For prebuilt dependencies, obtain the matching JARs **and their dependency POMs**
from your project's artifact repository or contact
[ryan@fortrow.com](mailto:ryan@fortrow.com).

If installing supplied builds locally, install Agent15 first, then Kwik:

```sh
mvn install:install-file \
  -Dfile=/path/to/agent15-3.3-mtls.jar \
  -DpomFile=/path/to/agent15-3.3-mtls.pom

mvn install:install-file \
  -Dfile=/path/to/kwik-0.10.9-mtls.jar \
  -DpomFile=/path/to/kwik-0.10.9-mtls.pom
```

Use their actual POM files so Maven can resolve transitive dependencies. Replacing
these artifacts with upstream versions can change the expected mTLS behavior.

## Installation

### Build and install from source

After installing the custom dependencies above, clone JFlux and build it:

```sh
git clone https://github.com/fortrow/jflux-sdk.git jflux
cd jflux
mvn -B verify
mvn -B install -DskipTests
```

`mvn verify` builds and tests the project. `mvn install -DskipTests` installs the built
artifact into your local Maven repository without repeating the tests. For a
different machine or CI runner, provide the same dependency artifacts or configure
your organization's Maven repository.

### Add JFlux to a Maven application

After installing this checkout locally, add:

```xml
<dependency>
  <groupId>com.fortrow</groupId>
  <artifactId>jflux</artifactId>
  <version>1.2.1</version>
</dependency>
```

The version above matches this checkout's `pom.xml`. When using another release,
use that release's version. A source installation or configured artifact
repository is required for coordinates that are not published publicly.

### Choose a packaged JAR

`mvn package` also writes these artifacts to `target/` (`VERSION` is the Maven
project version):

| Artifact | Intended use |
| --- | --- |
| `jflux-VERSION.jar` | Broad shaded SDK distribution. |
| `jflux-VERSION-core.jar` | General-purpose utility packages with a smaller dependency set. |
| `jflux-VERSION-db.jar` | Database-oriented distribution. |
| `jflux-VERSION-http.jar` | HTTP/API-oriented distribution. |
| `jflux-meridiandb-VERSION.jar` | Compatibility distribution for MeridianDB consumers. |

These are overlapping distributions, not independent Java modules. Choose the
appropriate one rather than putting every JAR on the same classpath. Split
artifacts are produced as files by the build; they are not currently attached
Maven classifiers. HTTP/3 libraries are excluded from the shaded distributions
and must be supplied separately when required. Feature dependencies can still
be needed by a particular split artifact.

Post-quantum primitives use the optional Bouncy Castle provider. The full and HTTP
shaded distributions include it; Maven consumers and core/database distributions
should add `org.bouncycastle:bcprov-jdk18on:1.86` when using those primitives. Algorithm
support does not by itself imply that a deployment is a FIPS-validated module.

## Quick examples

### Parse and format JSON

```java
import com.fortrow.jflux.output.json.JSON;
import org.json.simple.JSONObject;

JSONObject settings = JSON.parse("{\"service\":\"example\",\"enabled\":true}");
System.out.println(settings.get("service"));
System.out.println(JSON.prettyString(settings));
```

### Run a command with bounded output and a timeout

```java
import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;
import java.time.Duration;
import java.util.List;

CommandResult result = Shell.execute(
        List.of("git", "--version"), Duration.ofSeconds(5));

if (result.success()) {
    System.out.println(result.stdout());
} else {
    System.err.println(result.stderr());
}
```

Argument lists keep executable arguments separate. Use the string overload only
when you intentionally need shell syntax such as pipes or redirects.

### Query SQLite with the fluent builder

```java
import com.fortrow.jflux.db.Db;
import com.fortrow.jflux.db.connectors.details.DbConnectionSqlLiteDetails;

try (Db db = new Db(new DbConnectionSqlLiteDetails("app.db"))) {
    db.query().pragma("foreign_keys", true).run();
    var users = db.query().table("users")
            .select("id", "name")
            .where("enabled", "=", true)
            .limit(20)
            .get();
}
```

Create your application's schema before querying it. Use the builder's structured
operations and bound values; it does not expose arbitrary raw SQL. Database
failures use JFlux's exception/reporting types, so handle or declare the relevant
exception in your surrounding application code.

### Encrypt through an algorithm adapter

```java
import com.fortrow.jflux.crypto.Cryptography;
import java.nio.charset.StandardCharsets;

Cryptography crypto = Cryptography.defaults();
byte[] key = crypto.randomBytes(32);
byte[] nonce = crypto.randomBytes(12);
byte[] plaintext = "hello, JFlux".getBytes(StandardCharsets.UTF_8);
byte[] ciphertext = crypto.encrypt("aes-gcm-256", plaintext, key, nonce);
byte[] recovered = crypto.decrypt("aes-gcm-256", ciphertext, key, nonce);
```

Use a fresh nonce for every encryption under the same key. Protect keys outside
source control. Low-level cryptographic primitives are building blocks and do
not establish an authenticated application protocol on their own.

## Development and contributions

```sh
mvn -B test
```

Keep source headers consistent with the licensing files and preserve third-party
copyright and license notices. Local maintenance scripts are not distributed
with this repository.

Keep changes focused, include relevant tests, and update public usage information
alongside API changes. Use JFlux's exception types for new failure paths. Report
bugs through the repository's issue tracker with your Java version, OS, a minimal
reproduction, and relevant logs with credentials removed.

## License and commercial use

Copyright (c) 2026 Fortrow LLC.

JFlux-owned code is available under **GNU AGPL version 3 only**
(`AGPL-3.0-only`) or a separate **commercial license** from Fortrow LLC.
See [LICENSE](LICENSE) for the complete AGPL and
[COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md) for the commercial option.

AGPLv3 includes source-sharing requirements for modified versions made available
over a network, as described in [GNU's explanation](https://www.gnu.org/licenses/why-affero-gpl.html).
It also permits commercial use under its terms. To obtain terms that permit use
of Fortrow-owned JFlux code without the AGPL copyleft requirements, email
**[ryan@fortrow.com](mailto:ryan@fortrow.com)**. Commercial rights require a
separate written agreement.

Third-party libraries remain governed by their own licenses; a Fortrow commercial
license does not remove their obligations. See the
[dependency notice inventory](src/main/resources/THIRD-PARTY.txt) and the license
files shipped with each dependency.
