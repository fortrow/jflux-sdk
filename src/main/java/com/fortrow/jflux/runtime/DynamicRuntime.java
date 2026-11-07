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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Library facade for built-ins, encrypted packages, and bounded dynamic execution. */
public final class DynamicRuntime implements AutoCloseable {
    private final RuntimeFunctionRegistry registry;
    private final RuntimePackageLoader loader;

    public DynamicRuntime() {
        this(new RuntimeFunctionRegistry(), new RuntimePackageLoader());
    }

    public DynamicRuntime(RuntimeFunctionRegistry registry, RuntimePackageLoader loader) {
        this.registry = registry == null ? new RuntimeFunctionRegistry() : registry;
        this.loader = loader == null ? new RuntimePackageLoader() : loader;
    }

    public RuntimeFunctionRegistry registry() { return registry; }

    public void registerBuiltin(RuntimeFunction function) { registry.registerBuiltin(function); }

    public void registerBuiltin(RuntimeFunction function, boolean serverOverrideable) {
        registry.registerBuiltin(function, serverOverrideable);
    }

    public void registerApplicationOverride(RuntimeFunction function, String version, List<String> replaces) {
        registry.registerApplicationOverride(function, version, replaces);
    }

    public RuntimePackage load(byte[] jar, RuntimeFunctionSource source) throws IOException {
        RuntimePackage runtimePackage = loader.load(jar, source);
        registry.registerPackage(runtimePackage);
        return runtimePackage;
    }

    public RuntimePackage load(Path jar, RuntimeFunctionSource source) throws IOException {
        RuntimePackage runtimePackage = loader.load(jar, source);
        registry.registerPackage(runtimePackage);
        return runtimePackage;
    }

    public RuntimePackage loadEncrypted(byte[] encoded, RuntimePackageKeyProvider keys,
                                        RuntimeFunctionSource source) throws Exception {
        RuntimePackage runtimePackage = loader.loadEncrypted(encoded, keys, source);
        registry.registerPackage(runtimePackage);
        return runtimePackage;
    }

    public RuntimeResult execute(RuntimeRequest request) { return registry.execute(request); }

    @Override
    public void close() { registry.close(); }
}
