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

package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.exceptions.types.ReportedException;

import java.net.URI;

final class ReporterSupport {

    private ReporterSupport() { }

    static boolean usable(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.trim().toLowerCase();
        return !lower.contains("example.invalid") && !lower.contains("placeholder") && !lower.equals("change-me");
    }

    static String payload(ReportedException exception) {
        return exception.reportJsonString();
    }

    static URI uri(String value) {
        try {
            return URI.create(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
