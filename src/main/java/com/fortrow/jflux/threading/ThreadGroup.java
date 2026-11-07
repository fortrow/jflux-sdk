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
package com.fortrow.jflux.threading;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.threading.base.ThreadImpl;
import com.fortrow.jflux.threading.types.DetachedThread;
import com.fortrow.jflux.threading.types.JoinedThread;
import com.fortrow.jflux.todash.__;

import java.util.ArrayList;
import java.util.List;

public class ThreadGroup {

    private final List<ThreadImpl> threads = new ArrayList<>();
    private final List<ThreadImpl> detached = new ArrayList<>();
    private boolean containedThread = false;
    private boolean joinContainer = false;
    private boolean mainThread = false;
    private boolean interrupt = false;
    private JoinedThread currentJoined = null;
    private Thread container = null;
    private boolean started;

    public ThreadGroup() { }

    public ThreadGroup(boolean containedThread) {
        this.containedThread = containedThread;
    }

    public synchronized void appendThread(JoinedThread thread) {
        if (started) throw ExceptionHandling.failure("Cannot append to a started thread group.", null);
        if (thread == null) return;
        threads.add(thread);
    }

    public synchronized void appendThread(DetachedThread thread) {
        if (started) throw ExceptionHandling.failure("Cannot append to a started thread group.", null);
        if (thread == null) return;
        threads.add(thread);
    }

    public void startGroup() {
        startGroup(false, 0);
    }

    public void startAndWait() {
        startGroup(true, 0);
    }

    public void startAndWait(int stopDelay) {
        startGroup(true, stopDelay);
    }

    public void joinGroup() {
        joinGroup(false);
    }

    public void joinGroup(boolean mainThread) {
        this.mainThread = mainThread;
        startGroup(true, 0);
    }

    public void joinGroup(int stopDelay, boolean mainThread) {
        this.mainThread = mainThread;
        startGroup(true, stopDelay);
    }

    public synchronized void startGroup(boolean joinContainer, int stopDelay) {

        if (started) throw ExceptionHandling.failure("Thread group can only be started once.", null);
        started = true;

        if(mainThread && joinContainer) {
            Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
                __.console().println("Shutting down all application threads...", __.TextColor.YELLOW);
                stopGroup();
            }));
        }

        this.joinContainer = joinContainer;
        interrupt = false;

        ArrayList<ThreadImpl> joined = new ArrayList<>();

        for(int i=0; i<threads.size(); i++) {
            ThreadImpl thread = threads.get(i);
            if(thread.doesJoin()) joined.add(thread);
            else detached.add(thread);
        }

        if(joinContainer && containedThread) {
            joined.add(new JoinedThread(stopDelay > 0 ? stopDelay : 5000) {
                @Override
                protected boolean execute() {
                    return true;
                }
            });
        }

        if(containedThread) {
            container = Thread.ofVirtual().unstarted(() -> {
                for (int i = 0; i < detached.size(); i++) {
                    detached.get(i).start();
                }

                for (int i = 0; i < joined.size(); i++) {
                    currentJoined = (JoinedThread) joined.get(i);
                    currentJoined.start();
                    if(interrupt) break;
                }
            });
            container.start();
            if(joinContainer) {
                try { container.join(); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    ExceptionHandling.report(interrupted);
                }
            }
        } else {
            for (int i = 0; i < detached.size(); i++) {
                detached.get(i).start();
            }

            for (int i = 0; i < joined.size(); i++) {
                currentJoined = (JoinedThread) joined.get(i);
                currentJoined.start();
                if(interrupt) break;
            }
        }

    }

    public void stopGroup() {
        interrupt = true;
        for (ThreadImpl thread : threads) {
            thread.stop();
        }
        if (container != null) container.interrupt();
        if(__.isset(currentJoined)) currentJoined.stop();
        currentJoined = null;
        detached.clear();
    }

}
