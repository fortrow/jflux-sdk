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
package com.fortrow.jflux.threading.types;


import com.fortrow.jflux.threading.base.DetachesThread;

public abstract class DetachedThread extends DetachesThread {

    protected abstract boolean execute();

    protected void onStart() {  }

    protected void onStop() { }

    public DetachedThread() { }

    public DetachedThread(int idleMiilis) {
        this.idleMillis = Math.max(0, idleMiilis);
    }

    @Override
    public synchronized void start() {
        markStarted();
        thread = Thread.ofVirtual().name("jflux-detached-").unstarted(this::runLoop);
        thread.start();
    }

    @Override
    public void stop() {
        Thread current = thread;
        if (current != null) current.interrupt();
    }

}
