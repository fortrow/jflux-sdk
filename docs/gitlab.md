# GitLab development repository

The development branch is `dev`. A production branch will be created later.

```sh
git remote set-url origin ssh://git@gitlab.fortrow.cloud:2222/strato/jflux.git
git push -u origin dev
```

Commit source, tests, documentation, dependency manifests and lockfiles. Ignore
local environment configuration, dependencies, build output, editor caches,
logs, private runtime state and temporary files. Example environment files
remain versioned. Rebuild generated assets using this project's build guide.
