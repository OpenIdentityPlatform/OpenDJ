/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.tools;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;

import com.forgerock.opendj.ldap.tools.AuthenticatedConnectionFactory.AuthenticatedConnection;
import com.forgerock.opendj.util.StaticUtils;

/**
 * Benchmark application framework.
 */
abstract class PerformanceRunner implements ConnectionEventListener {
    /**
     * Statistics thread base implementation.
     */
    class StatsThread extends Thread {
        private final String[] additionalColumns;

        private final List<GarbageCollectorMXBean> beans;

        private final Set<Double> percentiles;

        private final int numColumns;

        private ReversableArray etimes = new ReversableArray(100000);

        private final ReversableArray array = new ReversableArray(200000);

        protected long totalSuccessCount;

        protected long totalOperationCount;

        protected long totalFailedCount;

        protected long totalWaitTime;

        protected int successCount;

        protected int operationCount;

        protected int failedCount;

        protected long waitTime;

        protected long lastStatTime;

        protected long lastGCDuration;

        protected double recentDuration;

        protected double averageDuration;

        public StatsThread(final String[] additionalColumns) {
            super("Stats Thread");

            this.additionalColumns = additionalColumns;

            final TreeSet<Double> pSet = new TreeSet<Double>();
            if (!percentilesArgument.isPresent()) {
                pSet.add(.1);
                pSet.add(.01);
                pSet.add(.001);
            } else {
                for (final String percentile : percentilesArgument.getValues()) {
                    pSet.add(100.0 - Double.parseDouble(percentile));
                }
            }
            this.percentiles = pSet.descendingSet();
            this.numColumns =
                    5 + this.percentiles.size() + additionalColumns.length + (isAsync ? 1 : 0);
            this.beans = ManagementFactory.getGarbageCollectorMXBeans();
        }

        @Override
        public void run() {
            final MultiColumnPrinter printer;

            if (!app.isScriptFriendly()) {
                printer = new MultiColumnPrinter(numColumns, 2, "-", MultiColumnPrinter.RIGHT, app);
                printer.setTitleAlign(MultiColumnPrinter.RIGHT);

                String[] title = new String[numColumns];
                Arrays.fill(title, "");
                title[0] = "Throughput";
                title[2] = "Response Time";
                int[] span = new int[numColumns];
                span[0] = 2;
                span[1] = 0;
                span[2] = 2 + this.percentiles.size();
                Arrays.fill(span, 3, 4 + this.percentiles.size(), 0);
                Arrays.fill(span, 4 + this.percentiles.size(), span.length, 1);
                printer.addTitle(title, span);
                title = new String[numColumns];
                Arrays.fill(title, "");
                title[0] = "(ops/second)";
                title[2] = "(milliseconds)";
                printer.addTitle(title, span);
                title = new String[numColumns];
                title[0] = "recent";
                title[1] = "average";
                title[2] = "recent";
                title[3] = "average";
                int i = 4;
                for (final Double percentile : this.percentiles) {
                    title[i++] = Double.toString(100.0 - percentile) + "%";
                }
                title[i++] = "err/sec";
                if (isAsync) {
                    title[i++] = "req/res";
                }
                for (final String column : additionalColumns) {
                    title[i++] = column;
                }
                span = new int[numColumns];
                Arrays.fill(span, 1);
                printer.addTitle(title, span);
                printer.printTitle();
            } else {
                app.getOutputStream().print("Time (seconds)");
                app.getOutputStream().print(",");
                app.getOutputStream().print("Recent throughput (ops/second)");
                app.getOutputStream().print(",");
                app.getOutputStream().print("Average throughput (ops/second)");
                app.getOutputStream().print(",");
                app.getOutputStream().print("Recent response time (milliseconds)");
                app.getOutputStream().print(",");
                app.getOutputStream().print("Average response time (milliseconds)");
                for (final Double percentile : this.percentiles) {
                    app.getOutputStream().print(",");
                    app.getOutputStream().print(Double.toString(100.0 - percentile));
                    app.getOutputStream().print("% response time (milliseconds)");
                }
                app.getOutputStream().print(",");
                app.getOutputStream().print("Errors/second");
                if (isAsync) {
                    app.getOutputStream().print(",");
                    app.getOutputStream().print("Requests/response");
                }
                for (final String column : additionalColumns) {
                    app.getOutputStream().print(",");
                    app.getOutputStream().print(column);
                }
                app.getOutputStream().println();
                printer = null;
            }

            final String[] strings = new String[numColumns];

            final long startTime = System.currentTimeMillis();
            long statTime = startTime;
            long gcDuration = 0;
            for (final GarbageCollectorMXBean bean : beans) {
                gcDuration += bean.getCollectionTime();
            }
            while (!stopRequested) {
                try {
                    sleep(statsInterval);
                } catch (final InterruptedException ie) {
                    // Ignore.
                }

                lastStatTime = statTime;
                statTime = System.currentTimeMillis();

                lastGCDuration = gcDuration;
                gcDuration = 0;
                for (final GarbageCollectorMXBean bean : beans) {
                    gcDuration += bean.getCollectionTime();
                }

                operationCount = operationRecentCount.getAndSet(0);
                successCount = successRecentCount.getAndSet(0);
                failedCount = failedRecentCount.getAndSet(0);
                waitTime = waitRecentTime.getAndSet(0);

                final int resultCount = successCount + failedCount;

                totalOperationCount += operationCount;
                totalSuccessCount += successCount;
                totalFailedCount += failedCount;
                totalWaitTime += waitTime;

                final long totalResultCount = totalSuccessCount + totalFailedCount;

                recentDuration = statTime - lastStatTime;
                averageDuration = statTime - startTime;
                recentDuration -= gcDuration - lastGCDuration;
                averageDuration -= gcDuration;
                recentDuration /= 1000.0;
                averageDuration /= 1000.0;

                strings[0] = String.format("%.1f", resultCount / recentDuration);
                strings[1] = String.format("%.1f", totalResultCount / averageDuration);

                if (resultCount > 0) {
                    strings[2] =
                            String.format("%.3f", (waitTime - (gcDuration - lastGCDuration))
                                    / (double) resultCount / 1000000.0);
                } else {
                    strings[2] = "-";
                }

                if (totalResultCount > 0) {
                    strings[3] =
                            String.format("%.3f", (totalWaitTime - gcDuration)
                                    / (double) totalResultCount / 1000000.0);
                } else {
                    strings[3] = "-";
                }

                boolean changed = false;
                etimes = eTimeBuffer.getAndSet(etimes);
                final int appendLength = Math.min(array.remaining(), etimes.size());
                if (appendLength > 0) {
                    array.append(etimes, appendLength);
                    for (int i = array.size - appendLength; i < array.size; i++) {
                        array.siftUp(0, i);
                    }
                    changed = true;
                }

                // Our window buffer is now full. Replace smallest with anything
                // larger and re-heapify
                for (int i = appendLength; i < etimes.size(); i++) {
                    if (etimes.get(i) > array.get(0)) {
                        array.set(0, etimes.get(i));
                        array.siftDown(0, array.size() - 1);
                        changed = true;
                    }
                }
                etimes.clear();

                if (changed) {
                    // Perform heapsort
                    int i = array.size() - 1;
                    while (i > 0) {
                        array.swap(i, 0);
                        array.siftDown(0, i - 1);
                        i--;
                    }
                    array.reverse();
                }

                // Now everything is ordered from smallest to largest
                int index;
                int i = 4;
                for (final Double percent : percentiles) {
                    if (array.size() <= 0) {
                        strings[i++] = "-";
                    } else {
                        index =
                                array.size()
                                        - (int) Math.floor((percent / 100.0) * totalResultCount)
                                        - 1;
                        if (index < 0) {
                            strings[i++] = String.format("*%.3f", array.get(0) / 1000000.0);
                        } else {
                            strings[i++] = String.format("%.3f", array.get(index) / 1000000.0);
                        }
                    }
                }
                strings[i++] = String.format("%.1f", failedCount / recentDuration);
                if (isAsync) {
                    if (resultCount > 0) {
                        strings[i++] = String.format("%.1f", (double) operationCount / resultCount);
                    } else {
                        strings[i++] = "-";
                    }
                }
                for (final String column : getAdditionalColumns()) {
                    strings[i++] = column;
                }

                if (printer != null) {
                    printer.printRow(strings);
                } else {
                    // Script-friendly.
                    app.getOutputStream().print(averageDuration);
                    for (final String s : strings) {
                        app.getOutputStream().print(",");
                        app.getOutputStream().print(s);
                    }
                    app.getOutputStream().println();
                }
            }
        }

        String[] getAdditionalColumns() {
            return EMPTY_STRINGS;
        }
    }

    /**
     * Statistics update result handler implementation.
     *
     * @param <S>
     *            The type of expected result.
     */
    class UpdateStatsResultHandler<S extends Result> implements ResultHandler<S> {
        private final long startTime;

        UpdateStatsResultHandler(final long startTime) {
            this.startTime = startTime;
        }

        public void handleErrorResult(final ErrorResultException error) {
            failedRecentCount.getAndIncrement();
            updateStats();

            if (app.isVerbose()) {
                app.println(LocalizableMessage.raw(error.getResult().toString()));
            }
        }

        public void handleResult(final S result) {
            successRecentCount.getAndIncrement();
            updateStats();
        }

        private void updateStats() {
            final long eTime = System.nanoTime() - startTime;
            waitRecentTime.getAndAdd(eTime);
            synchronized (this) {
                final ReversableArray array = eTimeBuffer.get();
                if (array.remaining() == 0) {
                    array.set(array.size() - 1, eTime);
                } else {
                    array.append(eTime);
                }
            }
        }
    }

    /**
     * Worker thread base implementation.
     */
    abstract class WorkerThread extends Thread {
        private int count;
        private final Connection connection;
        private final ConnectionFactory connectionFactory;

        WorkerThread(final Connection connection, final ConnectionFactory connectionFactory) {
            super("Worker Thread");
            this.connection = connection;
            this.connectionFactory = connectionFactory;
        }

        public abstract FutureResult<?> performOperation(Connection connection,
                DataSource[] dataSources, long startTime);

        @Override
        public void run() {
            FutureResult<?> future;
            Connection connection;

            final double targetTimeInMS =
                    (1000.0 / (targetThroughput / (double) (numThreads * numConnections)));
            double sleepTimeInMS = 0;
            long start;
            while (!stopRequested && !(maxIterations > 0 && count >= maxIterations)) {
                if (this.connection == null) {
                    try {
                        connection = connectionFactory.getConnectionAsync(null).get();
                    } catch (final InterruptedException e) {
                        // Ignore and check stop requested
                        continue;
                    } catch (final ErrorResultException e) {
                        app.println(LocalizableMessage.raw(e.getResult().getDiagnosticMessage()));
                        if (e.getCause() != null && app.isVerbose()) {
                            e.getCause().printStackTrace(app.getErrorStream());
                        }
                        stopRequested = true;
                        break;
                    }
                } else {
                    connection = this.connection;
                    if (!noRebind && connection instanceof AuthenticatedConnection) {
                        final AuthenticatedConnection ac = (AuthenticatedConnection) connection;
                        try {
                            ac.rebindAsync(null).get();
                        } catch (final InterruptedException e) {
                            // Ignore and check stop requested
                            continue;
                        } catch (final ErrorResultException e) {
                            app.println(LocalizableMessage.raw(e.getResult().toString()));
                            if (e.getCause() != null && app.isVerbose()) {
                                e.getCause().printStackTrace(app.getErrorStream());
                            }
                            stopRequested = true;
                            break;
                        }
                    }
                }

                start = System.nanoTime();
                future = performOperation(connection, dataSources.get(), start);
                operationRecentCount.getAndIncrement();
                count++;
                if (!isAsync) {
                    try {
                        future.get();
                    } catch (final InterruptedException e) {
                        // Ignore and check stop requested
                        continue;
                    } catch (final ErrorResultException e) {
                        if (e.getCause() instanceof IOException) {
                            e.getCause().printStackTrace(app.getErrorStream());
                            stopRequested = true;
                            break;
                        }
                        // Ignore. Handled by result handler
                    } finally {
                        if (this.connection == null) {
                            connection.close();
                        }
                    }
                }
                if (targetThroughput > 0) {
                    try {
                        if (sleepTimeInMS > 1) {
                            sleep((long) Math.floor(sleepTimeInMS));
                        }
                    } catch (final InterruptedException e) {
                        continue;
                    }

                    sleepTimeInMS += targetTimeInMS - ((System.nanoTime() - start) / 1000000.0);
                    if (sleepTimeInMS < -60000) {
                        // If we fall behind by 60 seconds, just forget about
                        // catching up
                        sleepTimeInMS = -60000;
                    }
                }
            }
        }
    }

    private static class ReversableArray {
        private final long[] array;

        private boolean reversed;

        private int size;

        public ReversableArray(final int capacity) {
            this.array = new long[capacity];
        }

        public void append(final long value) {
            if (size == array.length) {
                throw new IndexOutOfBoundsException();
            }

            if (!reversed) {
                array[size] = value;
            } else {
                System.arraycopy(array, 0, array, 1, size);
                array[0] = value;
            }
            size++;
        }

        public void append(final ReversableArray a, final int length) {
            if (length > a.size() || length > remaining()) {
                throw new IndexOutOfBoundsException();
            }
            if (!reversed) {
                System.arraycopy(a.array, 0, array, size, length);
            } else {
                System.arraycopy(array, 0, array, length, size);
                System.arraycopy(a.array, 0, array, 0, length);
            }
            size += length;
        }

        public void clear() {
            size = 0;
        }

        public long get(final int index) {
            if (index >= size) {
                throw new IndexOutOfBoundsException();
            }
            if (!reversed) {
                return array[index];
            } else {
                return array[size - index - 1];
            }
        }

        public int remaining() {
            return array.length - size;
        }

        public void reverse() {
            reversed = !reversed;
        }

        public void set(final int index, final long value) {
            if (index >= size) {
                throw new IndexOutOfBoundsException();
            }
            if (!reversed) {
                array[index] = value;
            } else {
                array[size - index - 1] = value;
            }
        }

        public void siftDown(final int start, final int end) {
            int root = start;
            int child;
            while (root * 2 + 1 <= end) {
                child = root * 2 + 1;
                if (child + 1 <= end && get(child) > get(child + 1)) {
                    child = child + 1;
                }
                if (get(root) > get(child)) {
                    swap(root, child);
                    root = child;
                } else {
                    return;
                }
            }
        }

        public void siftUp(final int start, final int end) {
            int child = end;
            int parent;
            while (child > start) {
                parent = (int) Math.floor((child - 1) / 2.0);
                if (get(parent) > get(child)) {
                    swap(parent, child);
                    child = parent;
                } else {
                    return;
                }
            }
        }

        public int size() {
            return size;
        }

        private void swap(final int i, final int i2) {
            final long temp = get(i);
            set(i, get(i2));
            set(i2, temp);
        }
    }

    private static final String[] EMPTY_STRINGS = new String[0];

    private final AtomicInteger operationRecentCount = new AtomicInteger();

    protected final AtomicInteger successRecentCount = new AtomicInteger();

    protected final AtomicInteger failedRecentCount = new AtomicInteger();

    private final AtomicLong waitRecentTime = new AtomicLong();

    private final AtomicReference<ReversableArray> eTimeBuffer =
            new AtomicReference<ReversableArray>(new ReversableArray(100000));

    private final ConsoleApplication app;

    private DataSource[] dataSourcePrototypes;

    // Thread local copies of the data sources
    private final ThreadLocal<DataSource[]> dataSources = new ThreadLocal<DataSource[]>() {
        /**
         * {@inheritDoc}
         */
        @Override
        protected DataSource[] initialValue() {
            final DataSource[] prototypes = getDataSources();
            final int sz = prototypes.length;
            final DataSource[] threadLocalCopy = new DataSource[sz];
            for (int i = 0; i < sz; i++) {
                threadLocalCopy[i] = prototypes[i].duplicate();
            }
            return threadLocalCopy;
        }

    };

    private volatile boolean stopRequested;

    private int numThreads;

    private int numConnections;

    private int targetThroughput;

    private int maxIterations;

    private boolean isAsync;

    private boolean noRebind;

    private int statsInterval;

    private final IntegerArgument numThreadsArgument;

    private final IntegerArgument maxIterationsArgument;

    private final IntegerArgument statsIntervalArgument;

    private final IntegerArgument targetThroughputArgument;

    private final IntegerArgument numConnectionsArgument;

    private final IntegerArgument percentilesArgument;

    private final BooleanArgument keepConnectionsOpen;

    private final BooleanArgument noRebindArgument;

    private final BooleanArgument asyncArgument;

    private final StringArgument arguments;

    PerformanceRunner(final ArgumentParser argParser, final ConsoleApplication app,
            final boolean neverRebind, final boolean neverAsynchronous,
            final boolean alwaysSingleThreaded) throws ArgumentException {
        this.app = app;
        numThreadsArgument =
                new IntegerArgument("numThreads", 't', "numThreads", false, false, true,
                        LocalizableMessage.raw("{numThreads}"), 1, null, true, 1, false, 0,
                        LocalizableMessage.raw("Number of worker threads per connection"));
        numThreadsArgument.setPropertyName("numThreads");
        if (!alwaysSingleThreaded) {
            argParser.addArgument(numThreadsArgument);
        } else {
            numThreadsArgument.addValue("1");
        }

        numConnectionsArgument =
                new IntegerArgument("numConnections", 'c', "numConnections", false, false, true,
                        LocalizableMessage.raw("{numConnections}"), 1, null, true, 1, false, 0,
                        LocalizableMessage.raw("Number of connections"));
        numConnectionsArgument.setPropertyName("numConnections");
        argParser.addArgument(numConnectionsArgument);

        maxIterationsArgument =
                new IntegerArgument("maxIterations", 'm', "maxIterations", false, false, true,
                        LocalizableMessage.raw("{maxIterations}"), 0, null, LocalizableMessage
                                .raw("Max iterations, 0 for unlimited"));
        maxIterationsArgument.setPropertyName("maxIterations");
        argParser.addArgument(maxIterationsArgument);

        statsIntervalArgument =
                new IntegerArgument("statInterval", 'i', "statInterval", false, false, true,
                        LocalizableMessage.raw("{statInterval}"), 5, null, true, 1, false, 0,
                        LocalizableMessage.raw("Display results each specified number of seconds"));
        statsIntervalArgument.setPropertyName("statInterval");
        argParser.addArgument(statsIntervalArgument);

        targetThroughputArgument =
                new IntegerArgument("targetThroughput", 'M', "targetThroughput", false, false,
                        true, LocalizableMessage.raw("{targetThroughput}"), 0, null,
                        LocalizableMessage.raw("Target average throughput to achieve"));
        targetThroughputArgument.setPropertyName("targetThroughput");
        argParser.addArgument(targetThroughputArgument);

        percentilesArgument =
                new IntegerArgument("percentile", 'e', "percentile", false, true,
                        LocalizableMessage.raw("{percentile}"), true, 0, true, 100,
                        LocalizableMessage.raw("Calculate max response time for a "
                                + "percentile of operations"));
        percentilesArgument.setPropertyName("percentile");
        percentilesArgument.setMultiValued(true);
        argParser.addArgument(percentilesArgument);

        keepConnectionsOpen =
                new BooleanArgument("keepConnectionsOpen", 'f', "keepConnectionsOpen",
                        LocalizableMessage.raw("Keep connections open"));
        keepConnectionsOpen.setPropertyName("keepConnectionsOpen");
        argParser.addArgument(keepConnectionsOpen);

        noRebindArgument =
                new BooleanArgument("noRebind", 'F', "noRebind", LocalizableMessage
                        .raw("Keep connections open and don't rebind"));
        noRebindArgument.setPropertyName("noRebind");
        if (!neverRebind) {
            argParser.addArgument(noRebindArgument);
        }

        asyncArgument =
                new BooleanArgument("asynchronous", 'A', "asynchronous", LocalizableMessage
                        .raw("Use asynchronous mode and don't "
                                + "wait for results before sending the next request"));
        asyncArgument.setPropertyName("asynchronous");
        if (!neverAsynchronous) {
            argParser.addArgument(asyncArgument);
        }

        arguments =
                new StringArgument(
                        "argument",
                        'g',
                        "argument",
                        false,
                        true,
                        true,
                        LocalizableMessage.raw("{generator function or static string}"),
                        null,
                        null,
                        LocalizableMessage
                                .raw("Argument used to evaluate the Java "
                                        + "style format strings in program parameters (ie. Base DN, "
                                        + "Search Filter). The set of all arguments provided form the "
                                        + "the argument list in order. Besides static string "
                                        + "arguments, they can be generated per iteration with the "
                                        + "following functions: " + StaticUtils.EOL
                                        + DataSource.getUsage()));
        argParser.addArgument(arguments);
    }

    public void handleConnectionClosed() {
        // Ignore
    }

    public synchronized void handleConnectionError(final boolean isDisconnectNotification,
            final ErrorResultException error) {
        if (!stopRequested) {
            app.println(LocalizableMessage.raw("Error occurred on one or more " + "connections: "
                    + error.getResult().toString()));
            if (error.getCause() != null && app.isVerbose()) {
                error.getCause().printStackTrace(app.getErrorStream());
            }
            stopRequested = true;
        }
    }

    public void handleUnsolicitedNotification(final ExtendedResult notification) {
        // Ignore
    }

    public final void validate() throws ArgumentException {
        numConnections = numConnectionsArgument.getIntValue();
        numThreads = numThreadsArgument.getIntValue();
        maxIterations = maxIterationsArgument.getIntValue() / numConnections / numThreads;
        statsInterval = statsIntervalArgument.getIntValue() * 1000;
        targetThroughput = targetThroughputArgument.getIntValue();

        isAsync = asyncArgument.isPresent();
        noRebind = noRebindArgument.isPresent();

        if (!noRebindArgument.isPresent() && this.numThreads > 1) {
            throw new ArgumentException(LocalizableMessage.raw("--"
                    + noRebindArgument.getLongIdentifier() + " must be used if --"
                    + numThreadsArgument.getLongIdentifier() + " is > 1"));
        }

        if (!noRebindArgument.isPresent() && asyncArgument.isPresent()) {
            throw new ArgumentException(LocalizableMessage.raw("--"
                    + noRebindArgument.getLongIdentifier() + " must be used when using --"
                    + asyncArgument.getLongIdentifier()));
        }

        dataSourcePrototypes = DataSource.parse(arguments.getValues());
    }

    final DataSource[] getDataSources() {
        if (dataSourcePrototypes == null) {
            throw new IllegalStateException(
                    "dataSources are null - validate() must be called first");
        }
        return dataSourcePrototypes;
    }

    abstract WorkerThread newWorkerThread(final Connection connection,
            final ConnectionFactory connectionFactory);

    abstract StatsThread newStatsThread();

    final int run(final ConnectionFactory connectionFactory) {
        final List<Thread> threads = new ArrayList<Thread>();
        final List<Connection> connections = new ArrayList<Connection>();

        Connection connection = null;
        try {
            for (int i = 0; i < numConnections; i++) {
                if (keepConnectionsOpen.isPresent() || noRebindArgument.isPresent()) {
                    connection = connectionFactory.getConnectionAsync(null).get();
                    connection.addConnectionEventListener(this);
                    connections.add(connection);
                }
                for (int j = 0; j < numThreads; j++) {
                    final Thread thread = newWorkerThread(connection, connectionFactory);
                    threads.add(thread);
                    thread.start();
                }
            }

            final Thread statsThread = newStatsThread();
            statsThread.start();

            for (final Thread t : threads) {
                t.join();
            }
            stopRequested = true;
            statsThread.join();
        } catch (final InterruptedException e) {
            stopRequested = true;
        } catch (final ErrorResultException e) {
            stopRequested = true;
            app.println(LocalizableMessage.raw(e.getResult().getDiagnosticMessage()));
        } finally {
            for (final Connection c : connections) {
                c.close();
            }
        }

        return 0;
    }
}
