/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.ldap;

/** A snapshot of connection pool state. */
public final class ConnectionPoolStatistics {
    private final int size;
    private final int idle;
    private final int inUse;
    private final int pending;
    private final int waiting;
    private final int maximum;
    private final boolean closed;

    /** Constructs a pool snapshot.
     * @param size reserved slots, including pending connections
     * @param idle idle connections
     * @param inUse borrowed connections
     * @param pending pending connection attempts
     * @param waiting waiting acquisitions
     * @param maximum maximum number of connections
     * @param closed whether the pool is closed
     */
    public ConnectionPoolStatistics(int size, int idle, int inUse, int pending, int waiting,
                                    int maximum, boolean closed) {
        this.size = size;
        this.idle = idle;
        this.inUse = inUse;
        this.pending = pending;
        this.waiting = waiting;
        this.maximum = maximum;
        this.closed = closed;
    }

    /** @return reserved slots */
    public int getSize() { return size; }
    /** @return idle connections */
    public int getIdle() { return idle; }
    /** @return borrowed connections */
    public int getInUse() { return inUse; }
    /** @return pending connection attempts */
    public int getPending() { return pending; }
    /** @return waiting acquisitions */
    public int getWaiting() { return waiting; }
    /** @return maximum number of connections */
    public int getMaximum() { return maximum; }
    /** @return whether the pool is closed */
    public boolean isClosed() { return closed; }
}
