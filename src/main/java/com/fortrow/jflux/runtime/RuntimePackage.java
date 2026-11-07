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

package com.fortrow.jflux.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loaded runtime functions and the classloader that owns their bytecode. */
public final class RuntimePackage implements AutoCloseable {
    private final RuntimeFunctionDescriptor descriptor;
    private final List<RuntimeFunction> functions;
    private final RuntimeClassLoader loader;
    private final AtomicBoolean closed = new AtomicBoolean();

    RuntimePackage(RuntimeFunctionDescriptor descriptor, List<RuntimeFunction> functions,
                   RuntimeClassLoader loader) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.functions = List.copyOf(functions);
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public RuntimeFunctionDescriptor descriptor() { return descriptor; }

    public List<RuntimeFunction> functions() { return functions; }

    public boolean closed() { return closed.get(); }

    @Override
    public void close() {
        if(closed.compareAndSet(false, true)) loader.close();
    }
}
