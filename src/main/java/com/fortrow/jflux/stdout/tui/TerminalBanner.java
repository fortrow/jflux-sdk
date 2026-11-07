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
 */
package com.fortrow.jflux.stdout.tui;

/**
 * Ascii banner and branding components for Ryel and Keystone.
 */
public final class TerminalBanner {

    private TerminalBanner() {
    }

    public static String keystoneBanner() {
        return keystoneBanner("v0.9.4");
    }

    public static String keystoneBanner(String version) {
        StringBuilder sb = new StringBuilder();
        String amber = AnsiStyle.RYEL_AMBER;
        String mint = AnsiStyle.RYEL_MINT;
        String coral = AnsiStyle.RYEL_CORAL;
        String reset = AnsiStyle.RESET;
        String bold = AnsiStyle.BOLD;
        String dim = AnsiStyle.DIM;

        sb.append(bold).append(amber)
          .append("  ██████╗ ██╗   ██╗███████╗██╗     \n")
          .append("  ██╔══██╗╚██╗ ██╔╝██╔════╝██║     \n")
          .append("  ██████╔╝ ╚████╔╝ █████╗  ██║     \n")
          .append("  ██╔══██╗  ╚██╔╝  ██╔══╝  ██║     \n")
          .append("  ██║  ██║   ██║   ███████╗███████╗\n")
          .append(reset);

        sb.append(bold).append(mint)
          .append("  ██╗  ██╗███████╗██╗   ██╗███████╗████████╗ ██████╗ ███╗   ██╗███████╗\n")
          .append("  ██║ ██╔╝██╔════╝╚██╗ ██╔╝██╔════╝╚══██╔══╝██╔═══██╗████╗  ██║██╔════╝\n")
          .append("  █████╔╝ █████╗   ╚████╔╝ ███████╗   ██║   ██║   ██║██╔██╗ ██║█████╗  \n")
          .append("  ██╔═██╗ ██╔══╝    ╚██╔╝  ╚════██║   ██║   ██║   ██║██║╚██╗██║██╔══╝  \n")
          .append("  ██║  ██╗███████╗   ██║   ███████║   ██║   ╚██████╔╝██║ ╚████║███████╗\n")
          .append("  ╚═╝  ╚═╝╚══════╝   ╚═╝   ╚══════╝   ╚═╝    ╚═════╝ ╚═╝  ╚═══╝╚══════╝\n")
          .append(reset);

        sb.append("\n");
        sb.append("  ").append(bold).append(AnsiStyle.white("Ryel Keystone — Dedicated SWE Vibe Coding"))
          .append(dim).append(" • ").append(reset)
          .append(AnsiStyle.badge("CPU SERVER ENGINE", AnsiStyle.RYEL_BLACK, AnsiStyle.BG_RYEL_MINT))
          .append(" ")
          .append(AnsiStyle.badge(version, AnsiStyle.RYEL_BLACK, AnsiStyle.BG_RYEL_AMBER))
          .append(" ")
          .append(AnsiStyle.dim("Meridian Synced • Local Worktree Control"))
          .append("\n\n");

        return sb.toString();
    }

    public static String ryelHeader(String title, String badge) {
        StringBuilder sb = new StringBuilder();
        sb.append(AnsiStyle.amber("◆ "));
        sb.append(AnsiStyle.bold(title));
        if (badge != null && !badge.isBlank()) {
            sb.append(" ").append(AnsiStyle.badge(badge, AnsiStyle.RYEL_BLACK, AnsiStyle.BG_RYEL_AMBER));
        }
        sb.append("\n");
        return sb.toString();
    }
}
