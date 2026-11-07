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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 10:54:51 EDT
 */
package com.fortrow.jflux.files.volumes.supporting;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.sys.PlatformUtils;

public interface VolumeImpl {
    default VolumeStatus mount() throws CommonIOException {
        if (PlatformUtils.isWindows()) return mountWindows();
        if (PlatformUtils.isMac()) return mountMac();
        if (PlatformUtils.isUnix()) return mountLinux();
        return VolumeStatus.UNSUPPORTED;
    }
    default VolumeStatus umount() throws CommonIOException {
        if (PlatformUtils.isWindows()) return umountWindows();
        if (PlatformUtils.isMac()) return umountMac();
        if (PlatformUtils.isUnix()) return umountLinux();
        return VolumeStatus.UNSUPPORTED;
    }
    VolumeStatus mountWindows() throws CommonIOException;
    VolumeStatus mountMac() throws CommonIOException;
    VolumeStatus mountLinux() throws CommonIOException;
    VolumeStatus umountWindows() throws CommonIOException;
    VolumeStatus umountMac() throws CommonIOException;
    VolumeStatus umountLinux() throws CommonIOException;
}
