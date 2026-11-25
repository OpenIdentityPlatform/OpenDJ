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

/** Per-operation routing information, never sent to the LDAP server. */
public final class ConnectionRoutingContext {
    private volatile String selectedServer;
    private volatile String respondingServer;
    private volatile ResultCode responseCode;
    private volatile long startedNanos;
    private volatile long completedNanos;
    private boolean requestIssued;
    private String poolServer;
    private java.util.function.Supplier<ConnectionPoolStatistics> poolStatistics;
    private PoolSnapshot poolSnapshot;
    private final java.util.concurrent.atomic.AtomicInteger searchEntries = new java.util.concurrent.atomic.AtomicInteger();

    /** An immutable pool snapshot associated with its configured endpoint. */
    public static final class PoolSnapshot {
        private final String server;
        private final ConnectionPoolStatistics statistics;

        private PoolSnapshot(String server, ConnectionPoolStatistics statistics) {
            this.server = server;
            this.statistics = statistics;
        }

        /** @return the configured endpoint for this pool */
        public String getServer() { return server; }
        /** @return the pool state */
        public ConnectionPoolStatistics getStatistics() { return statistics; }
    }

    /** Registers the pool being tried for this operation.
     * @param server configured endpoint
     * @param statistics provider for a best-effort pool snapshot
     */
    public synchronized void onPoolAttempt(String server,
                                           java.util.function.Supplier<ConnectionPoolStatistics> statistics) {
        poolServer = server;
        poolStatistics = statistics;
        poolSnapshot = null;
    }

    /** Captures pool state when connection acquisition fails. */
    public synchronized void onPoolAcquisitionFailure() {
        capturePoolSnapshot();
    }

    /** @return the pool snapshot recorded at completion or acquisition failure, or null */
    public synchronized PoolSnapshot getPoolSnapshot() {
        return poolSnapshot;
    }

    private void capturePoolSnapshot() {
        if (poolStatistics == null) return;
        try {
            ConnectionPoolStatistics snapshot = poolStatistics.get();
            if (snapshot != null) poolSnapshot = new PoolSnapshot(poolServer, snapshot);
        } catch (RuntimeException ignored) {
            // Diagnostics must not change the LDAP operation's outcome.
        }
    }

    /** Marks a proxy request as attempted. */
    public void onRequestIssued() {
        requestIssued = true;
    }

    /** @return whether a proxy request was attempted */
    public boolean wasRequestIssued() {
        return requestIssued;
    }

    /** Starts measuring before the first connection acquisition attempt. */
    public synchronized void onAcquisitionStarted() {
        if (startedNanos == 0) startedNanos = System.nanoTime();
    }

    /** Records the endpoint whose connection was obtained.
     * @param server configured endpoint
     */
    public void onServerSelected(String server) {
        selectedServer = server;
    }

    /** Records a completed LDAP response, excluding SDK-generated client-side failures.
     * @param result result returned by the operation
     */
    public void recordResponse(org.forgerock.opendj.ldap.responses.Result result) {
        if (result != null && !result.getResultCode().asEnum().name().startsWith("CLIENT_SIDE_")) {
            respondingServer = selectedServer;
            responseCode = result.getResultCode();
        }
    }

    /** Records completion before the response is delivered to the caller.
     * @param result LDAP result or client-side error result
     */
    public synchronized void onComplete(org.forgerock.opendj.ldap.responses.Result result) {
        if (completedNanos == 0) {
            recordResponse(result);
            completedNanos = System.nanoTime();
            capturePoolSnapshot();
        }
    }

    /** @return time spent waiting for a connection and its response in milliseconds */
    public long getElapsedMillis() {
        long start = startedNanos;
        if (start == 0) return 0;
        long end = completedNanos;
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                (end == 0 ? System.nanoTime() : end) - start);
    }

    /** Records an entry received from the selected endpoint. */
    public void onSearchEntry() {
        searchEntries.incrementAndGet();
    }

    /** @return the number of remote search entries received */
    public int getSearchEntries() {
        return searchEntries.get();
    }

    /** @return the result code returned by the endpoint, or null if no response was received */
    public ResultCode getResponseCode() {
        return responseCode;
    }

    /** @return the endpoint which returned an LDAP response, or null if none was received */
    public String getRespondingServer() {
        return respondingServer;
    }

    /** @return the selected endpoint, or null if no connection was obtained */
    public String getSelectedServer() {
        return selectedServer;
    }
}
