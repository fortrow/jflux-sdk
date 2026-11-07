/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 13:42:09 EDT
 */
package com.fortrow.jflux.ssl.letsencrypt;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.PathObject;
import com.fortrow.jflux.files.PathUtil;
import com.fortrow.jflux.ssl.SslCertKeyPair;
import com.fortrow.jflux.sys.installers.LetsEncryptInstaller;
import com.fortrow.jflux.sys.PlatformUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LetsEncrypt {

    public static RequestResult prereqs() {
        LetsEncryptInstaller.InstallResult result = new LetsEncryptInstaller().install();
        return new RequestResult(result.success(), result.message(), null, new ArrayList<>());
    }

    public static RequestResult install() {
        return prereqs();
    }

    public static SslCertKeyPair create(String outputDirectory, String email, String domain, String...subjectAltNames) throws CommonIOException {
        return request(outputDirectory, email, domain, subjectAltNames).certKeyPair();
    }

    public static RequestResult request(String outputDirectory, String email, String domain, String...subjectAltNames) throws CommonIOException {
        Request request = new Request(outputDirectory, email, domain, subjectAltNames);
        return new LetsEncrypt().request(request);
    }

    public RequestResult request(Request request) throws CommonIOException {
        request.validate();
        PathObject output = PathUtil.fromString(request.outputDirectory(), true);

        if(PlatformUtils.isWindows()) return requestWithPoshAcme(request, output);
        return requestWithCertbot(request, output);
    }

    private RequestResult requestWithCertbot(Request request, PathObject output) {
        String command = certbotCommand(request, output.getString());
        CommandResult result = run(command);
        ArrayList<CommandResult> commands = new ArrayList<>();
        commands.add(result);
        if(result.status != 0) {
            return RequestResult.failed("Certbot standalone certificate request failed.", commands);
        }

        String liveDirectory = output.getString() + PlatformUtils.fileSeparator() + "config" +
                PlatformUtils.fileSeparator() + "live" + PlatformUtils.fileSeparator() + request.certificateName();
        File key = new File(liveDirectory + PlatformUtils.fileSeparator() + "privkey.pem");
        File cert = new File(liveDirectory + PlatformUtils.fileSeparator() + "fullchain.pem");
        if(!key.exists() || !cert.exists()) {
            return RequestResult.failed("Certbot completed, but the expected certificate files were not found in " + liveDirectory + ".", commands);
        }
        return RequestResult.success("Let's Encrypt certificate created with Certbot.", new SslCertKeyPair(key, cert), commands);
    }

    private RequestResult requestWithPoshAcme(Request request, PathObject output) {
        String command = poshAcmeCommand(request, output.getString());
        CommandResult result = run(command);
        ArrayList<CommandResult> commands = new ArrayList<>();
        commands.add(result);
        if(result.status != 0) {
            return RequestResult.failed("Posh-ACME WebSelfHost certificate request failed.", commands);
        }

        File key = fileFromOutput("KEY=", result.stdout);
        File cert = fileFromOutput("CERT=", result.stdout);
        if(key == null || cert == null || !key.exists() || !cert.exists()) {
            return RequestResult.failed("Posh-ACME completed, but the expected certificate files were not reported.", commands);
        }
        return RequestResult.success("Let's Encrypt certificate created with Posh-ACME.", new SslCertKeyPair(key, cert, request.pfxPassword()), commands);
    }

    private String certbotCommand(Request request, String outputDirectory) {
        ArrayList<String> parts = new ArrayList<>();
        if(request.challengePort() < 1024) parts.add("sudo");
        parts.add("certbot");
        parts.add("certonly");
        parts.add("--standalone");
        parts.add("--non-interactive");
        parts.add("--agree-tos");
        parts.add("--preferred-challenges");
        parts.add("http");
        parts.add("--http-01-port");
        parts.add(String.valueOf(request.challengePort()));
        parts.add("--cert-name");
        parts.add(shellQuote(request.certificateName()));
        parts.add("--email");
        parts.add(shellQuote(request.email()));
        parts.add("--config-dir");
        parts.add(shellQuote(outputDirectory + PlatformUtils.fileSeparator() + "config"));
        parts.add("--work-dir");
        parts.add(shellQuote(outputDirectory + PlatformUtils.fileSeparator() + "work"));
        parts.add("--logs-dir");
        parts.add(shellQuote(outputDirectory + PlatformUtils.fileSeparator() + "logs"));
        if(request.staging()) parts.add("--staging");
        for(String domain : request.domains()) {
            parts.add("-d");
            parts.add(shellQuote(domain));
        }
        return String.join(" ", parts);
    }

    private String poshAcmeCommand(Request request, String outputDirectory) {
        String domains = powershellArray(request.domains());
        String server = request.staging() ? "LE_STAGE" : "LE_PROD";
        String pluginArgs = "@{ WSHPort = " + request.challengePort() + "; WSHTimeout = " + request.listenerTimeoutSeconds() + " }";
        String script = "$ErrorActionPreference = 'Stop'; " +
                "Import-Module Posh-ACME; " +
                "$env:POSHACME_HOME = " + powershellQuote(outputDirectory) + "; " +
                "$domains = " + domains + "; " +
                "$cert = New-PACertificate $domains -Name " + powershellQuote(request.certificateName()) +
                " -Contact " + powershellQuote(request.email()) +
                " -AcceptTOS -DirectoryUrl " + powershellQuote(server) +
                " -Plugin WebSelfHost -PluginArgs " + pluginArgs +
                " -PfxPass " + powershellQuote(request.pfxPassword()) +
                (request.force() ? " -Force" : "") + "; " +
                "Write-Output ('KEY=' + $cert.KeyFile); " +
                "Write-Output ('CERT=' + $cert.FullChainFile); " +
                "Write-Output ('PFX=' + $cert.PfxFile);";
        return "powershell -NoProfile -ExecutionPolicy Bypass -Command " + windowsCommandQuote(script);
    }

    private CommandResult run(String command) {
        com.fortrow.jflux.shell.CommandResult result = com.fortrow.jflux.shell.Shell.execute(command);
        return new CommandResult(command, result.exitCode(), result.stdout(), result.stderr());
    }

    private static File fileFromOutput(String prefix, String output) {
        String[] lines = output.split("\\R");
        for(String line : lines) {
            if(line.startsWith(prefix)) return new File(line.substring(prefix.length()).trim());
        }
        return null;
    }

    private static String powershellArray(List<String> values) {
        ArrayList<String> quoted = new ArrayList<>();
        for(String value : values) quoted.add(powershellQuote(value));
        return "@(" + String.join(",", quoted) + ")";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String powershellQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String windowsCommandQuote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    public static class Request {
        private final String outputDirectory;
        private final String email;
        private final ArrayList<String> domains;
        private String certificateName;
        private int challengePort = 80;
        private int listenerTimeoutSeconds = 120;
        private boolean staging = false;
        private boolean force = false;
        private String pfxPassword = "poshacme";

        public Request(String outputDirectory, String email, String domain, String...subjectAltNames) {
            this.outputDirectory = outputDirectory;
            this.email = email;
            this.domains = new ArrayList<>();
            this.domains.add(domain);
            if (subjectAltNames != null) this.domains.addAll(Arrays.asList(subjectAltNames));
            this.certificateName = safeName(domain);
        }

        public Request certificateName(String certificateName) {
            this.certificateName = safeName(certificateName);
            return this;
        }

        public Request challengePort(int challengePort) {
            this.challengePort = challengePort;
            return this;
        }

        public Request listenerTimeoutSeconds(int listenerTimeoutSeconds) {
            this.listenerTimeoutSeconds = listenerTimeoutSeconds;
            return this;
        }

        public Request staging(boolean staging) {
            this.staging = staging;
            return this;
        }

        public Request force(boolean force) {
            this.force = force;
            return this;
        }

        public Request pfxPassword(String pfxPassword) {
            this.pfxPassword = pfxPassword == null ? "" : pfxPassword;
            return this;
        }

        private void validate() throws CommonIOException {
            if(outputDirectory == null || outputDirectory.isBlank()) throw new CommonIOException("Output directory is required.");
            if(email == null || email.isBlank()) throw new CommonIOException("Let's Encrypt account email is required.");
            if(domains.isEmpty() || domains.get(0) == null || domains.get(0).isBlank()) throw new CommonIOException("At least one domain is required.");
            if(challengePort < 1 || challengePort > 65535) throw new CommonIOException("Challenge port must be between 1 and 65535.");
            if(listenerTimeoutSeconds < 1) throw new CommonIOException("Listener timeout must be greater than zero.");
            if(pfxPassword == null || pfxPassword.isBlank()) throw new CommonIOException("PFX password cannot be blank.");
            for(String domain : domains) {
                if(domain == null || domain.isBlank()) throw new CommonIOException("Domain names cannot be blank.");
                if(domain.startsWith("*.")) throw new CommonIOException("Standalone HTTP challenges cannot request wildcard certificates. Use a DNS challenge for " + domain + ".");
            }
        }

        public String outputDirectory() {
            return outputDirectory;
        }

        public String email() {
            return email;
        }

        public List<String> domains() {
            return List.copyOf(domains);
        }

        public String certificateName() {
            return certificateName;
        }

        public int challengePort() {
            return challengePort;
        }

        public int listenerTimeoutSeconds() {
            return listenerTimeoutSeconds;
        }

        public boolean staging() {
            return staging;
        }

        public boolean force() {
            return force;
        }

        public String pfxPassword() {
            return pfxPassword;
        }

        private static String safeName(String value) {
            if(value == null || value.isBlank()) return "letsencrypt";
            return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9.-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        }
    }

    public static class RequestResult {
        private final boolean success;
        private final String message;
        private final SslCertKeyPair certKeyPair;
        private final List<CommandResult> commands;

        private RequestResult(boolean success, String message, SslCertKeyPair certKeyPair, List<CommandResult> commands) {
            this.success = success;
            this.message = message;
            this.certKeyPair = certKeyPair;
            this.commands = List.copyOf(commands == null ? List.of() : commands);
        }

        public static RequestResult success(String message, SslCertKeyPair certKeyPair, List<CommandResult> commands) {
            return new RequestResult(true, message, certKeyPair, commands);
        }

        public static RequestResult failed(String message, List<CommandResult> commands) {
            return new RequestResult(false, message, null, commands);
        }

        public boolean success() {
            return success;
        }

        public String message() {
            return message;
        }

        public SslCertKeyPair certKeyPair() {
            return certKeyPair;
        }

        public List<CommandResult> commands() {
            return commands;
        }
    }

    public static class CommandResult {
        private final String command;
        private final int status;
        private final String stdout;
        private final String stderr;

        private CommandResult(String command, int status, String stdout, String stderr) {
            this.command = command;
            this.status = status;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        public String command() {
            return command;
        }

        public int status() {
            return status;
        }

        public String stdout() {
            return stdout;
        }

        public String stderr() {
            return stderr;
        }
    }
}
