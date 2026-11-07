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
package com.fortrow.jflux.runtime.lib;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @deprecated Use {@code Files.newInputStream} or the JFlux runtime package loader.
 */
@Deprecated(since = "1.1.0", forRemoval = false)
public class JarFileInputStream extends InputStream {

    private final InputStream input;

    public JarFileInputStream(String path) throws IOException {
        if(path == null || path.isBlank()) throw new IOException("JAR path is required");
        input = new BufferedInputStream(Files.newInputStream(Path.of(path)));
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public int read(byte[] toStore) throws IOException {
        return input.read(toStore);
    }

    @Override
    public int read(byte[] toStore, int off, int len) throws IOException {
        return input.read(toStore, off, len);
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

}
