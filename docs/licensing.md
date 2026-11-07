# Licensing and header maintenance

JFlux-owned code is offered under **AGPL-3.0-only** or a separate commercial
license from Fortrow LLC. AGPLv3 suits JFlux's API/server use because section 13
adds source-offer obligations for modified versions used remotely over a network.
GPLv3 alone does not add that network-specific requirement. This is why AGPL was
selected; it does not impose a ban on commercial activity.

The full, unmodified AGPL text is in `LICENSE`. The alternative commercial offer,
identified as `LicenseRef-Fortrow-Commercial`, is in `COMMERCIAL-LICENSE.md`.
Contact `ryan@fortrow.com` to obtain a separate written agreement permitting use
without the AGPL copyleft requirements for code Fortrow can license commercially.
Third-party dependency licenses remain independent. Maintainers must obtain any
necessary contribution/relicensing permissions before offering contributed code
commercially.

Maven includes the AGPL metadata and commercial-contact comment. Packages carry
both root licensing files under `META-INF/jflux/`; existing dependency notices
remain under their original resource paths. Existing built JARs must be rebuilt
to include the new metadata and license resources.

## Source header policy

Fortrow-owned source headers use
`SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial`.
Keep original creation annotations and copyright years, preserve source content
and line endings, and retain third-party notices. Header maintenance does not
establish ownership or substitute for a dependency-license audit. Local
maintenance tooling is not part of the public repository.

The existing MIT notice in `websockets/server/HandshakeTlsChannel.java` is
deliberately preserved. It is not Fortrow's proprietary
header and is not replaced with the JFlux license.

Validation of this migration compared all 385 current Java/native source bodies
and creation annotations against their pre-migration contents, including local
uncommitted work. All remained unchanged. The eight updater tests and Maven
validation/resource-copy checks pass. For whitespace checking while preserving
the existing CRLF files, run `git -c core.whitespace=cr-at-eol diff --check`.

## Repository ignore rules

`docs/` is included in the public repository. `.gitignore` excludes `AGENTS.md`,
`IMPLEMENT.md`, other common AI instruction/working Markdown filenames and tool
directories' Markdown, and macOS `.DS_Store`/AppleDouble DS_Store files.
The root `scripts/` directory is also ignored and retained only for local
maintenance. It is excluded from the public commit; Git history inspection
confirmed it was never committed, so no history rewrite is necessary.
`AGENTS.md` and `IMPLEMENT.md` are removed from the rewritten public Git history
and retained only as ignored local files. `README.md`, `COMMERCIAL-LICENSE.md`,
and package guides remain public-facing, tracked documents.

## Sources

- [GNU: Why the Affero GPL](https://www.gnu.org/licenses/why-affero-gpl.html)
- [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.html)
- [GNU licensing FAQ: multiple licensing](https://www.gnu.org/licenses/gpl-faq.html#HeardOtherLicense)
- [Canonical AGPL text from SPDX](https://github.com/spdx/license-list-data/blob/main/text/AGPL-3.0-only.txt)
