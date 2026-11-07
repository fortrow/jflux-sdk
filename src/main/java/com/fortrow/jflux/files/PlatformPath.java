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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.files;

import com.fortrow.jflux.sys.PlatformUtils;

import java.util.HashMap;

public class PlatformPath {

    private HashMap<PlatformUtils.OpSys, PathObject> map = new HashMap<>();

    public PlatformPath() { }

    public void addPlatform(PlatformUtils.OpSys os, PathObject path) {
        map.put(os, path);
    }

    public PathObject get() {
        PlatformUtils.OpSys os = PlatformUtils.getOsType(containsDistros());
        return map.get(os);
    }

    private boolean containsDistros() {
        for(PlatformUtils.OpSys os : map.keySet()) {
            switch(os) {
                case ALPINE_LINUX:
                case ARCH_LINUX:
                case DEBIAN_LINUX:
                case FREE_BSD_LINUX:
                case REDHAT_LINUX:
                case MINT_LINUX:
                case UBUNTU_LINUX:
                case FEDORA_LINUX:
                case OPENSUS_LINUX:
                case CENTOS_LINUX:
                case SLACKWARE_LINUX:
                    return true;
            }
        }
        return false;
    }

}
