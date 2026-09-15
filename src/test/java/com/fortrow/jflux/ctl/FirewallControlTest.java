package com.fortrow.jflux.ctl;

import com.fortrow.jflux.ctl.firewall.FirewallControl;
import com.fortrow.jflux.ctl.ssl.JavaSSL;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLParameters;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirewallControlTest {

    private static FirewallControl.Options dryRun(FirewallControl.Backend backend) {
        return new FirewallControl.Options(backend, true, Duration.ofSeconds(2), 4096,
                FirewallControl.PrivilegeMode.NONE);
    }

    @Test
    void windowsPortRulesAreSafeArgumentLists() {
        FirewallControl control = new FirewallControl(dryRun(FirewallControl.Backend.WINDOWS_DEFENDER));
        FirewallControl.FirewallResult result = control.allowPort(8443, FirewallControl.Protocol.TCP,
                "api-https", FirewallControl.Direction.INBOUND);

        assertTrue(result.supported());
        assertTrue(result.success());
        assertTrue(result.planned());
        assertEquals(List.of("netsh", "advfirewall", "firewall", "add", "rule", "name=api-https",
                "dir=in", "action=allow", "protocol=tcp", "localport=8443", "profile=any", "enable=yes"),
                result.command());
    }

    @Test
    void macApplicationRulesUseTwoPhaseRegistration() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        FirewallControl control = new FirewallControl(dryRun(FirewallControl.Backend.MACOS_SOCKET_FILTER));
        FirewallControl.FirewallResult result = control.allowApplication(java, "jflux-java");

        assertTrue(result.success());
        assertEquals(2, result.commands().size());
        assertEquals("/usr/libexec/ApplicationFirewall/socketfilterfw", result.command().getFirst());
        assertEquals("--add", result.command().get(1));
    }

    @Test
    void unsupportedBackendOperationsAreReportedWithoutExecution() {
        FirewallControl control = new FirewallControl(dryRun(FirewallControl.Backend.MACOS_SOCKET_FILTER));
        FirewallControl.FirewallResult result = control.allowPort(443, FirewallControl.Protocol.TCP);

        assertFalse(result.supported());
        assertFalse(result.attempted());
        assertFalse(result.success());
        assertTrue(result.commands().isEmpty());
    }

    @Test
    void linuxPortCommandsRemainInspectable() {
        FirewallControl control = new FirewallControl(dryRun(FirewallControl.Backend.LINUX_UFW));
        FirewallControl.FirewallResult result = control.allowPort(53, FirewallControl.Protocol.UDP,
                "dns", FirewallControl.Direction.OUTBOUND);

        assertEquals(List.of("ufw", "allow", "out", "53/udp", "comment", "dns"), result.command());
        assertTrue(result.planned());
    }

    @Test
    void tlsBaselineEnablesHostnameVerificationForClients() {
        SSLParameters parameters = JavaSSL.clientParameters();

        assertEquals("HTTPS", parameters.getEndpointIdentificationAlgorithm());
        assertArrayEquals(new String[]{"TLSv1.3", "TLSv1.2"}, parameters.getProtocols());
        assertNotNull(JavaSSL.context());
    }

    @Test
    void invalidFirewallInputsUseJfluxCompatibilityExceptions() {
        FirewallControl control = new FirewallControl(dryRun(FirewallControl.Backend.WINDOWS_DEFENDER));

        assertThrows(IllegalArgumentException.class,
                () -> control.allowPort(0, FirewallControl.Protocol.TCP));
        assertThrows(IllegalArgumentException.class,
                () -> control.allowPort(443, FirewallControl.Protocol.TCP, "bad\nname"));
    }
}
