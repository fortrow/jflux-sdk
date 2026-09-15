# Control-plane utilities

The `com.fortrow.jflux.ctl` package contains small helpers for operating-system
controls that are useful to API servers and installers.

## FirewallControl

`FirewallControl` detects the preferred firewall executable on the current
host:

- Windows: Windows Defender Firewall through `netsh`;
- macOS: the application firewall through
  `/usr/libexec/ApplicationFirewall/socketfilterfw`; and
- Linux: `ufw`, `firewalld`, `nftables`, or `iptables`, in that order.

The API passes every value as an argument-list element through `Shell`; it does
not build a shell command string. Results are immutable and include the
backend, operation, planned commands, bounded command results, and a
diagnostic message.

```java
FirewallControl firewall = new FirewallControl(
        FirewallControl.Options.defaults().withDryRun(true));

FirewallControl.FirewallResult plan = firewall.allowPort(
        8443, FirewallControl.Protocol.TCP, "api-https");

if (plan.planned()) {
    System.out.println(plan.commands());
}
```

The main operations are `status()`, `enable()`, `disable()`, `allowPort(...)`,
`removePort(...)`, `allowApplication(...)`, `removeApplication(...)`, and
`removeRule(...)`. Port rules default to inbound traffic and a generated
`jflux-<direction>-<protocol>-<port>` name. Use `Direction.OUTBOUND` when an
outbound rule is needed.

Set `Options.dryRun()` for startup validation, audit logs, and tests. A dry
run reports a successful plan but never launches a process. The default Unix
privilege mode uses `sudo -n` when the current process is not root, so it
fails fast instead of waiting for an interactive password. Use
`PrivilegeMode.REQUIRE_ELEVATED` when the application must be started with
the required privilege, or `PrivilegeMode.NONE` when an external supervisor
handles elevation.

Backend differences are explicit. macOS `socketfilterfw` is application based
and therefore does not implement arbitrary port rules. Linux `firewalld`,
`nftables`, and `iptables` expose port controls; application rules and
unsupported global enable/disable operations return `supported() == false`.
`nftables` removal is also reported unsupported unless the application supplies
its own handle-aware rule lifecycle because nftables deletion is handle based.
The Linux commands assume their normal table/chain configuration, so an
application should inspect `status()` and review a dry-run plan before applying
changes.

Firewall changes are privileged external side effects. Run them only with
configuration and authorization supplied by the application owner. Invalid
ports, executable paths, rule names, timeouts, and output limits are rejected
through the JFlux exception bridge.

## JavaSSL

`JavaSSL.context()` creates an initialized JVM-trust TLS context. The helper
accepts `TLS`, `TLSv1.2`, and `TLSv1.3`; `clientParameters()` enables HTTPS
hostname verification and both client/server parameter helpers restrict
negotiation to TLS 1.2 and TLS 1.3. Certificate and private-key file workflows
remain in `com.fortrow.jflux.ssl`.
