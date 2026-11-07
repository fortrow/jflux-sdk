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

package com.fortrow.jflux.exceptions;

import com.fortrow.jflux.exceptions.types.CommonException;
import com.fortrow.jflux.exceptions.types.ExceptionAdapter;
import com.fortrow.jflux.exceptions.types.ReportedException;

/** Compatibility bridge for APIs that predate the jflux exception hierarchy. */
public final class ExceptionHandling {

    private ExceptionHandling() { }

    /** Converts and reports any failure through the configured jflux reporters. */
    public static boolean report(Throwable failure) {
        if (failure == null) return false;
        if (failure instanceof ReportedException reported) return reported.tryReport();
        if (failure instanceof ExceptionAdapter adapter) return new ReportedException(adapter).tryReport();
        return new ReportedException(failure).tryReport();
    }

    /** Reports invalid caller input while preserving a legacy runtime contract. */
    public static IllegalArgumentException invalid(String message) {
        new CommonException(message);
        return new IllegalArgumentException(message);
    }

    public static IllegalArgumentException invalid(String message, Throwable cause) {
        new CommonException(message, cause);
        return new IllegalArgumentException(message, cause);
    }

    /** Reports an operational failure while preserving a legacy runtime contract. */
    public static IllegalStateException failure(String message, Throwable cause) {
        new ReportedException(message, cause).tryReport();
        return new IllegalStateException(message, cause);
    }

    public static UnsupportedOperationException unsupported(String message) {
        new CommonException(message);
        return new UnsupportedOperationException(message);
    }
}
