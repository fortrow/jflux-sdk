# Public HTTP/3 dependency forks

JFlux's public remote is `https://github.com/fortrow/jflux-sdk.git`.
The related local repositories now have matching `public` remotes under the same
GitHub owner. Their existing GitLab `origin` remotes are retained.

| Local repository | Public remote | Published branch |
| --- | --- | --- |
| `../agent15` | `https://github.com/fortrow/agent15.git` | `jflux-mtls` |
| `../kwik` | `https://github.com/fortrow/kwik.git` | `jflux-mtls` |
| `../flupke` | `https://github.com/fortrow/flupke.git` | `jflux-mtls` |

These are GitHub forks of `ptrd/agent15`, `ptrd/kwik`, and `ptrd/flupke`.
The dedicated branch preserves the local Fortrow changes without overwriting
upstream branches. It is the public default branch for each fork.

JFlux requires Agent15 `3.3-mtls` and Kwik `0.10.9-mtls` for its custom HTTP/3
client-authentication support. Flupke `0.9.4` and upstream QPACK are ordinary
dependencies; there is no local custom QPACK fork to publish. The dependency
forks retain their upstream copyright and LGPL/GPL license texts. JFlux's
commercial offer does not override those licenses.

Publish future dependency changes explicitly to the corresponding public branch:

```sh
git push public HEAD:refs/heads/jflux-mtls
```

Review the branch and its intended changes before publishing. Do not mirror all
GitLab refs or force-update the upstream branches. Source availability does not
mean the custom `-mtls` Maven artifacts have been published to Maven Central;
consumers still need locally built or separately supplied artifacts.

For local source builds, Agent15 exposes `-PpublicationVersion=3.3-mtls` and
`publishToMavenLocal`; skip `signLibraryPublication` when no signing key is
configured. Kwik exposes `:kwik:publishToMavenLocal` with
`-PpublicationVersion=0.10.9-mtls -Pagent15Version=3.3-mtls`; skip
`:kwik:signMavenPublication` for local installation. The publication and signing
task names were checked against both Gradle wrappers. These commands install to
the local Maven cache, not to Maven Central. See the public README for complete
clone/build commands.
