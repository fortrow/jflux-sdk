# Sys package

The `com.fortrow.jflux.sys` package contains platform, executable, package
manager, installer, and source-build helpers for Windows, macOS, and Unix-like
systems.

## Platform utilities

`PlatformUtils` provides OS detection, Java/runtime properties, user and
temporary directories, privilege checks, hidden-directory helpers, process ID,
CPU count, and an immutable `PlatformSnapshot`. OS and environment reads use
safe Java properties and NIO APIs. Failures at legacy nullable or boolean
boundaries are routed through `ExceptionHandling`.

## Package and binary auditing

`PackageUtils` supports detection and querying for APT/dpkg, DNF/YUM/RPM,
Pacman, APK, Zypper, Homebrew, WinGet, and Chocolatey. It can:

- find executables in `PATH` and inspect their version output;
- select a preferred package manager and list available managers;
- query whether a named package is installed;
- install named packages through an available manager;
- install local `.deb` and `.rpm` files; and
- return structured audit and install results.

Installation methods have external side effects and may invoke `sudo` or an
operating-system package elevation mechanism. Callers should obtain their own
authorization before invoking them. Commands are passed to the shell package
as argument lists, so package names are not interpolated into shell strings.

`JavaPackageUtils` is separate and only handles Java class/package metadata,
resource paths, class locations, module names, and implementation versions.

## Installer integration

The LetsEncrypt, OpenSSL, SQLite, and Redis installers use `PackageUtils` for
availability checks and `Shell` for command execution. Their existing command
planning APIs remain compatible, but command results now have bounded output and
consistent timeout/error behavior.

## Source installation

`GitUtils` supports hosted GitHub/GitLab identification, clone, shallow clone,
pull, checkout, remote updates, and authenticated fork requests.
`InstallerBuilder` handles Git or wget zip/tar source acquisition and lets the
application add arbitrary setup, build, and cleanup argument commands. It does
not prescribe language-specific commands such as Maven, Cargo, CMake, or Make;
the programmer supplies whatever the source requires.
