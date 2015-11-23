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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static java.util.Locale.ENGLISH;
import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.util.promise.Promise;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiColumnPrinter;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.util.StaticUtils;

/** Benchmark application framework. */
abstract class PerformanceRunner implements ConnectionEventListener {
    static final class ResponseTimeBuckets {
        private static final long NS_1_US = NANOSECONDS.convert(1, MICROSECONDS);
        private static final long NS_100_US = NANOSECONDS.convert(100, MICROSECONDS);
        private static final long NS_1_MS = NANOSECONDS.convert(1, MILLISECONDS);
        private static final long NS_10_MS = NANOSECONDS.convert(10, MILLISECONDS);
        private static final long NS_100_MS = NANOSECONDS.convert(100, MILLISECONDS);
        private static final long NS_1_S = NANOSECONDS.convert(1, SECONDS);
        private static final long NS_5_S = NANOSECONDS.convert(5, SECONDS);

        private static final int NB_INDEX = 2120;
        private static final int RANGE_100_MICROSECONDS_START_INDEX = 1000;
        private static final int RANGE_1_MILLISECOND_START_INDEX = 1090;
        private static final int RANGE_100_MILLISECONDS_START_INDEX = 2080;

        /**
         * Array of response time buckets.
         *
         * <pre>
         * index    0 ->  999: 1000 buckets for    0ms -    1ms  interval with       1 µs increments
         * index 1000 -> 1089:   90 buckets for    1ms -   10ms  interval with     100 µs increments
         * index 1090 -> 2079:  990 buckets for   10ms - 1000ms  interval with    1000 µs increments
         * index 2080 -> 2119:   40 buckets for 1000ms - 5000ms  interval with 100 000 µs increments
         * </pre>
         */
        private final AtomicLong[] index2Frequency = new AtomicLong[NB_INDEX];

        /**
         * Store the lower bounds (in microseconds) of the eTime buckets.
         */
        private final long[] index2Etime = new long[NB_INDEX];

        /**
         * Sorted map used for storing response times from 5s+ with 500 millisecond increments.
         *
         * Keys (Long in microseconds) of this map must respect this pattern: n * 500 000 + 5 000 000,
         * where n is a natural integer.
         */
        private final ConcurrentSkipListMap<Long, AtomicLong> bigEtimes = new ConcurrentSkipListMap<>();

        /**
         * Initialize both index2Frequency and index2Etime arrays.
         */
        private ResponseTimeBuckets() {
            // Helpful variables to compute index2Etime values.
            long rangeWidthMicroSecs;
            long rangeStart = 0;
            long initialTimeMicroSecs = 0;

            for (int i = 0; i < NB_INDEX; i++) {
                index2Frequency[i] = new AtomicLong();
                if (i < RANGE_100_MICROSECONDS_START_INDEX) {
                    // 0ms-1ms in 1 us increments
                    rangeWidthMicroSecs = 1;
                } else if (i < RANGE_1_MILLISECOND_START_INDEX) {
                    // 1ms-10ms in 100 us increments
                    rangeWidthMicroSecs = 100;
                    rangeStart = RANGE_100_MICROSECONDS_START_INDEX;
                    initialTimeMicroSecs = MICROSECONDS.convert(1, MILLISECONDS);
                } else if (i < RANGE_100_MILLISECONDS_START_INDEX) {
                    // 10ms-1000ms in 1000 us increments
                    rangeWidthMicroSecs = MICROSECONDS.convert(1, MILLISECONDS);
                    rangeStart = RANGE_1_MILLISECOND_START_INDEX;
                    initialTimeMicroSecs = MICROSECONDS.convert(10, MILLISECONDS);
                } else {
                    // 1000ms-5000ms with 100 000 us increments
                    rangeWidthMicroSecs = MICROSECONDS.convert(100, MILLISECONDS);
                    rangeStart = RANGE_100_MILLISECONDS_START_INDEX;
                    initialTimeMicroSecs = MICROSECONDS.convert(1, SECONDS);
                }
                index2Etime[i] = (i - rangeStart) * rangeWidthMicroSecs + initialTimeMicroSecs;
            }
        }

        /**
         * Compute the closest response time values for each percentile given in
         * parameter. Percentiles array has to be sorted from lower to higher
         * percentiles.
         *
         * @param percentiles
         *            array of {@code double}
         *
         * @param nbData
         *            number of response times recorded.
         *
         * @return array of response times in microseconds corresponding to
         *         percentiles.
         */
        List<Long> getPercentile(double[] percentiles, long nbData) {
            List<Long> responseTimes = new ArrayList<>();
            Queue<Long> nbDataThresholds = new LinkedList<>();
            long nbDataSum = nbData;

            for (int i = percentiles.length - 1; i >= 0; i--) {
                nbDataThresholds.add((long) (percentiles[i] * nbData) / 100);
            }

            Iterator<Entry<Long, AtomicLong>> iter = bigEtimes.descendingMap().entrySet().iterator();
            while (iter.hasNext() && !nbDataThresholds.isEmpty()) {
                Entry<Long, AtomicLong> currentETime = iter.next();
                nbDataSum -= currentETime.getValue().get();
                computePercentiles(nbDataThresholds, responseTimes, nbDataSum, currentETime.getKey());
            }

            int stdTimeIndex = NB_INDEX - 1;
            while (stdTimeIndex >= 0 && !nbDataThresholds.isEmpty()) {
                long currentETime = index2Etime[stdTimeIndex];
                nbDataSum -= index2Frequency[stdTimeIndex].get();
                computePercentiles(nbDataThresholds, responseTimes, nbDataSum, currentETime);
                stdTimeIndex--;
            }

            return responseTimes;
        }

        private void computePercentiles(Queue<Long> currentDataThreshold, List<Long> responseTimes, long currentSum,
                long currentETime) {
            while (currentDataThreshold.peek() != null && currentDataThreshold.peek() >= currentSum) {
                responseTimes.add(currentETime);
                currentDataThreshold.poll();
            }
        }

        void addTimeToInterval(long responseTimeNanoSecs) {
            if (responseTimeNanoSecs >= NS_5_S) {
                long matchingKey = responseTimeNanoSecs / NS_100_MS;
                matchingKey -= matchingKey % 5;
                matchingKey = matchingKey * MICROSECONDS.convert(100, MILLISECONDS);
                // We now have a key corresponding to pattern 5 000 000 + n * 500 000 µs
                AtomicLong existingKey = bigEtimes.putIfAbsent(matchingKey, new AtomicLong(1));
                if (existingKey != null) {
                    existingKey.getAndIncrement();
                }
                return;
            }

            final int startRangeIndex;
            final long rangeWidthNanoSecs;
            if (responseTimeNanoSecs < NS_1_MS) {
                rangeWidthNanoSecs = NS_1_US;
                startRangeIndex = 0;
            } else if (responseTimeNanoSecs < NS_10_MS) {
                rangeWidthNanoSecs = NS_100_US;
                startRangeIndex = RANGE_100_MICROSECONDS_START_INDEX - 10;
            } else if (responseTimeNanoSecs < NS_1_S) {
                rangeWidthNanoSecs = NS_1_MS;
                startRangeIndex = RANGE_1_MILLISECOND_START_INDEX - 10;
            } else {
                rangeWidthNanoSecs = NS_100_MS;
                startRangeIndex = RANGE_100_MILLISECONDS_START_INDEX - 10;
            }
            final int intervalIndex = ((int) (responseTimeNanoSecs / rangeWidthNanoSecs)) + startRangeIndex;
            index2Frequency[intervalIndex].getAndIncrement();
        }
    }

    /** To allow tests. */
    static ResponseTimeBuckets getResponseTimeBuckets() {
        return new ResponseTimeBuckets();
    }


    /** Statistics thread base implementation. */
    class StatsThread extends Thread {
        protected long totalResultCount;
        protected long totalOperationCount;
        protected double totalDurationSec;
        protected long totalWaitTimeNs;

        protected int intervalSuccessCount;
        protected int intervalOperationCount;
        protected int intervalFailedCount;
        protected double intervalDurationSec;
        protected long intervalWaitTimeNs;

        protected long lastStatTimeMs;
        protected long lastGCDurationMs;

        private final int numColumns;
        private final String[] additionalColumns;
        private final double[] percentiles;
        private final List<GarbageCollectorMXBean> gcBeans;
        private final boolean isScriptFriendly = app.isScriptFriendly();
        private MultiColumnPrinter printer;

        public StatsThread(final String... additionalColumns) {
            super("Stats Thread");

            this.additionalColumns = additionalColumns;
            if (!percentilesArgument.isPresent()) {
                this.percentiles = new double[] { 99.9, 99.99, 99.999 };
            } else {
                this.percentiles = new double[percentilesArgument.getValues().size()];
                int index = 0;
                for (final String percentile : percentilesArgument.getValues()) {
                    percentiles[index++] = Double.parseDouble(percentile);
                }
                Arrays.sort(percentiles);
            }

            this.numColumns = 5 + this.percentiles.length + additionalColumns.length + (isAsync ? 1 : 0);
            this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        }

        private void printResultsTitle() {
            if (isScriptFriendly) {
                printResultsTitleScriptFriendly();
                return;
            }

            printer = new MultiColumnPrinter(numColumns, 2, "-", MultiColumnPrinter.RIGHT, app);
            printer.setTitleAlign(MultiColumnPrinter.RIGHT);
            printResultTitleHeaders();
            printResultTitleDetails();
        }

        private void printResultTitleHeaders() {
            final String[][] titleHeaders = new String[2][numColumns];
            for (final String[] titleLine : titleHeaders) {
                Arrays.fill(titleLine, "");
            }

            titleHeaders[0][0] = "Throughput";
            titleHeaders[0][2] = "Response Time";

            titleHeaders[1][0] = "(ops/second)";
            titleHeaders[1][2] = "(milliseconds)";

            final int[] span = new int[numColumns];
            span[0] = 2;
            span[1] = 0;
            span[2] = 2 + this.percentiles.length;
            Arrays.fill(span, 3, 4 + this.percentiles.length, 0);
            Arrays.fill(span, 4 + this.percentiles.length, span.length, 1);

            for (final String[] titleLine : titleHeaders) {
                printer.addTitle(titleLine, span);
            }
        }

        private void printResultTitleDetails() {
            final String[] titleDetails = new String[numColumns];
            titleDetails[0] = "recent";
            titleDetails[1] = "average";
            titleDetails[2] = "recent";
            titleDetails[3] = "average";
            int i = 4;
            for (double percentile :percentiles) {
                titleDetails[i++] = percentile + "%";
            }
            titleDetails[i++] = "err/sec";
            if (isAsync) {
                titleDetails[i++] = "req/res";
            }
            for (final String column : additionalColumns) {
                titleDetails[i++] = column;
            }

            final int[] span = new int[numColumns];
            Arrays.fill(span, 1);

            printer.addTitle(titleDetails, span);
            printer.printTitle();
        }

        private void printResultsTitleScriptFriendly() {
            final PrintStream out = app.getOutputStream();
            out.print("Time (seconds)");
            out.print(",");
            out.print("Recent throughput (ops/second)");
            out.print(",");
            out.print("Average throughput (ops/second)");
            out.print(",");
            out.print("Recent response time (milliseconds)");
            out.print(",");
            out.print("Average response time (milliseconds)");
            for (final double percentile : this.percentiles) {
                out.print(",");
                out.print(percentile);
                out.print("% response time (milliseconds)");
            }
            out.print(",");
            out.print("Errors/second");
            if (isAsync) {
                out.print(",");
                out.print("Requests/response");
            }
            for (final String column : additionalColumns) {
                out.print(",");
                out.print(column);
            }
            out.println();
        }

        @Override
        public void run() {
            printResultsTitle();

            long totalStatTimeMs = System.currentTimeMillis();
            long gcDurationMs = getGCDuration();

            while (!stopRequested) {
                try {
                    sleep(statsInterval);
                } catch (final InterruptedException ie) {
                    // Ignore.
                }
                lastStatTimeMs = totalStatTimeMs;
                totalStatTimeMs = System.currentTimeMillis();

                lastGCDurationMs = gcDurationMs;
                gcDurationMs = getGCDuration();
                final long gcIntervalDurationMs = gcDurationMs - lastGCDurationMs;

                computeStatsForInterval(totalStatTimeMs, gcIntervalDurationMs);
                final long intervalResultCount = intervalSuccessCount + intervalFailedCount;

                final String[] printableStats = new String[numColumns];
                Arrays.fill(printableStats, "-");
                printableStats[0] = getDivisionResult(intervalResultCount, intervalDurationSec, 1);
                printableStats[1] = getDivisionResult(totalResultCount, totalDurationSec, 1);

                final long intervalWaitTimeMs = NANOSECONDS.toMillis(intervalWaitTimeNs) - gcIntervalDurationMs;
                printableStats[2] = getDivisionResult(intervalWaitTimeMs, intervalResultCount, 3);

                final long totalWaitTimeMs = NANOSECONDS.toMillis(totalWaitTimeNs) - gcDurationMs;
                printableStats[3] = getDivisionResult(totalWaitTimeMs, totalResultCount, 3);

                int i = 4;
                final List<Long> computedPercentiles = eTimesBuckets.getPercentile(percentiles, totalOperationCount);
                for (int j = computedPercentiles.size() - 1; j >= 0; j--) {
                    printableStats[i++] = getDivisionResult(computedPercentiles.get(j) , 1000.0, 2);
                }
                i = 4 + percentiles.length;
                printableStats[i++] = intervalFailedCount == 0
                                      ? "0.0"
                                      : getDivisionResult(intervalFailedCount, intervalDurationSec, 1);
                if (isAsync) {
                    printableStats[i++] = getDivisionResult(intervalOperationCount, intervalResultCount, 1);
                }

                for (final String column : getAdditionalColumns()) {
                    printableStats[i++] = column;
                }

                if (isScriptFriendly) {
                    printScriptFriendlyStats(printableStats);
                } else {
                    printer.printRow(printableStats);
                }
            }
        }

        private void computeStatsForInterval(final long statTime, final long gcIntervalDurationMs) {
            intervalOperationCount = operationRecentCount.getAndSet(0);
            intervalSuccessCount = successRecentCount.getAndSet(0);
            intervalFailedCount = failedRecentCount.getAndSet(0);
            intervalWaitTimeNs = waitRecentTimeNs.getAndSet(0);

            totalOperationCount += intervalOperationCount;
            totalResultCount += intervalSuccessCount + intervalFailedCount;
            totalWaitTimeNs += intervalWaitTimeNs;

            final long intervalDurationMs = statTime - lastStatTimeMs;
            intervalDurationSec = (intervalDurationMs - gcIntervalDurationMs) / 1000.0;
            totalDurationSec += intervalDurationSec;
        }

        private long getGCDuration() {
            long gcDuration = 0;
            for (final GarbageCollectorMXBean bean : gcBeans) {
                gcDuration += bean.getCollectionTime();
            }

            return gcDuration;
        }

        private String getDivisionResult(final long numerator, final double denominator, final int precision) {
            return getDivisionResult(numerator, denominator, precision, "-");
        }

        protected String getDivisionResult(
                final long numerator, final double denominator, final int precision, final String fallBack) {
            return denominator > 0 ? String.format(ENGLISH, "%." + precision + "f", numerator / denominator)
                                   : fallBack;
        }

        private void printScriptFriendlyStats(String[] printableStats) {
            final PrintStream out = app.getOutputStream();
            out.print(String.format(ENGLISH, "%.3f", totalDurationSec));
            for (final String s : printableStats) {
                out.print(",");
                out.print(s);
            }
            out.println();
        }

        String[] getAdditionalColumns() {
            return EMPTY_STRINGS;
        }

        /** Resets both general and recent statistic indicators. */
        void resetStats() {
            intervalFailedCount = 0;
            intervalOperationCount = 0;
            intervalSuccessCount = 0;
            operationRecentCount.set(0);
            successRecentCount.set(0);
            failedRecentCount.set(0);
            waitRecentTimeNs.set(0);
        }
    }

    class TimerThread extends Thread {
        private final long timeToWait;

        TimerThread(long timeToWait) {
            this.timeToWait = timeToWait;
        }

        void performStopOperations() {
            stopRequested = true;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(timeToWait);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            } finally {
                performStopOperations();
            }
        }
    }

    /**
     * Statistics update result handler implementation.
     *
     * @param <S>
     *            The type of expected result.
     */
    class UpdateStatsResultHandler<S extends Result> implements LdapResultHandler<S> {
        protected final long currentTime;

        UpdateStatsResultHandler(final long currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        public void handleException(final LdapException exception) {
            failedRecentCount.getAndIncrement();
            updateStats();
            app.errPrintVerboseMessage(LocalizableMessage.raw(exception.getResult().toString()));
        }

        @Override
        public void handleResult(final S result) {
            successRecentCount.getAndIncrement();
            updateStats();
        }

        private void updateStats() {
            if (!isWarmingUp) {
                final long eTime = System.nanoTime() - currentTime;
                waitRecentTimeNs.getAndAdd(eTime);
                eTimesBuckets.addTimeToInterval(eTime);
            }
        }
    }

    /** Worker thread base implementation. */
    abstract class WorkerThread extends Thread {
        private int count;
        private final Connection connection;
        private final ConnectionFactory connectionFactory;
        boolean localStopRequested;

        WorkerThread(final Connection connection, final ConnectionFactory connectionFactory) {
            super("Worker Thread");
            this.connection = connection;
            this.connectionFactory = connectionFactory;
        }

        public abstract Promise<?, LdapException> performOperation(
                Connection connection, DataSource[] dataSources, long startTime);

        @Override
        public void run() {
            Promise<?, LdapException> promise;
            Connection connection;
            final double targetTimeMs = 1000.0 / (targetThroughput / (double) (numThreads * numConnections));
            double sleepTimeMs = 0;

            while (!stopRequested && !localStopRequested
                    && (maxIterations <= 0 || count < maxIterations)) {
                if (this.connection == null) {
                    try {
                        connection = connectionFactory.getConnectionAsync().getOrThrow();
                    } catch (final InterruptedException e) {
                        // Ignore and check stop requested
                        continue;
                    } catch (final LdapException e) {
                        app.errPrintln(LocalizableMessage.raw(e.getResult().getDiagnosticMessage()));
                        if (e.getCause() != null && app.isVerbose()) {
                            e.getCause().printStackTrace(app.getErrorStream());
                        }
                        stopRequested = true;
                        break;
                    }
                } else {
                    connection = this.connection;
                    if (!noRebind && bindRequest != null) {
                        try {
                            connection.bindAsync(bindRequest).getOrThrow();
                        } catch (final InterruptedException e) {
                            // Ignore and check stop requested
                            continue;
                        } catch (final LdapException e) {
                            app.errPrintln(LocalizableMessage.raw(e.getResult().toString()));
                            if (e.getCause() != null && app.isVerbose()) {
                                e.getCause().printStackTrace(app.getErrorStream());
                            }
                            stopRequested = true;
                            break;
                        }
                    }
                }

                long startTimeNs = System.nanoTime();
                promise = performOperation(connection, dataSources.get(), startTimeNs);
                operationRecentCount.getAndIncrement();
                if (!isAsync) {
                    try {
                        promise.getOrThrow();
                    } catch (final InterruptedException e) {
                        // Ignore and check stop requested
                        continue;
                    } catch (final LdapException e) {
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
                        if (sleepTimeMs > 1) {
                            sleep((long) Math.floor(sleepTimeMs));
                        }
                    } catch (final InterruptedException e) {
                        continue;
                    }

                    sleepTimeMs += targetTimeMs - NANOSECONDS.toMillis(System.nanoTime() - startTimeNs);
                    final long oneMinuteMs = MINUTES.toMillis(1);
                    if (sleepTimeMs + oneMinuteMs < 0) {
                        // If we fall behind by 60 seconds, just forget about catching up
                        sleepTimeMs = -oneMinuteMs;
                    }
                }
            }
        }

        void incrementIterationCount() {
            count++;
        }
    }

    private static final String[] EMPTY_STRINGS = new String[0];
    private final AtomicInteger operationRecentCount = new AtomicInteger();
    protected final AtomicInteger successRecentCount = new AtomicInteger();
    protected final AtomicInteger failedRecentCount = new AtomicInteger();
    private final AtomicLong waitRecentTimeNs = new AtomicLong();
    private final ResponseTimeBuckets eTimesBuckets = new ResponseTimeBuckets();


    private final ConsoleApplication app;
    private DataSource[] dataSourcePrototypes;

    /** Thread local copies of the data sources. */
    private final ThreadLocal<DataSource[]> dataSources = new ThreadLocal<DataSource[]>() {
        /** {@inheritDoc} */
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

    int numThreads;
    int numConnections;
    volatile boolean stopRequested;
    private volatile boolean isWarmingUp;
    private int targetThroughput;
    private int maxIterations;
    /** Warm-up duration time in ms. **/
    private long warmUpDuration;
    /** Max duration time in ms, 0 for unlimited. **/
    private long maxDurationTime;
    private boolean isAsync;
    private boolean noRebind;
    private BindRequest bindRequest;
    private int statsInterval;
    private final IntegerArgument numThreadsArgument;
    private final IntegerArgument maxDurationArgument;
    private final IntegerArgument statsIntervalArgument;
    private final IntegerArgument targetThroughputArgument;
    private final IntegerArgument numConnectionsArgument;
    private final IntegerArgument percentilesArgument;
    private final BooleanArgument keepConnectionsOpen;
    private final BooleanArgument noRebindArgument;
    private final BooleanArgument asyncArgument;
    private final StringArgument arguments;
    protected final IntegerArgument maxIterationsArgument;
    protected final IntegerArgument warmUpArgument;

    private final List<Thread> workerThreads = new ArrayList<>();

    PerformanceRunner(final PerformanceRunnerOptions options) throws ArgumentException {
        ArgumentParser argParser = options.getArgumentParser();

        this.app = options.getConsoleApplication();
        numThreadsArgument =
                new IntegerArgument("numThreads", 't', "numThreads", false, false, true,
                        LocalizableMessage.raw("{numThreads}"), 1, null, true, 1, false, 0,
                        LocalizableMessage.raw("Number of worker threads per connection"));
        numThreadsArgument.setPropertyName("numThreads");
        if (options.supportsMultipleThreadsPerConnection()) {
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
                        LocalizableMessage.raw("{maxIterations}"), 0, null,
                        LocalizableMessage.raw("Max iterations, 0 for unlimited"));
        maxIterationsArgument.setPropertyName("maxIterations");
        argParser.addArgument(maxIterationsArgument);

        maxDurationArgument =
            new IntegerArgument("maxDuration", 'd', "maxDuration", false, false, true,
                LocalizableMessage.raw("{maxDuration}"), 0, null, true, 1, false, 0,
                LocalizableMessage.raw("Maximum duration in seconds, 0 for unlimited"));
        argParser.addArgument(maxDurationArgument);

        warmUpArgument =
            new IntegerArgument("warmUpDuration", 'B', "warmUpDuration", false, false, true,
                LocalizableMessage.raw("{warmUpDuration}"), 0, null,
                LocalizableMessage.raw("Warm up duration in seconds"));
        argParser.addArgument(warmUpArgument);

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
                        .raw("Keep connections open and do not rebind"));
        noRebindArgument.setPropertyName("noRebind");
        if (options.supportsRebind()) {
            argParser.addArgument(noRebindArgument);
        }

        asyncArgument =
                new BooleanArgument("asynchronous", 'A', "asynchronous", LocalizableMessage
                        .raw("Use asynchronous mode and do not "
                                + "wait for results before sending the next request"));
        asyncArgument.setPropertyName("asynchronous");
        if (options.supportsAsynchronousRequests()) {
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
        if (options.supportsGeneratorArgument()) {
            argParser.addArgument(arguments);
        }
    }

    @Override
    public void handleConnectionClosed() {
        // Ignore
    }

    @Override
    public synchronized void handleConnectionError(final boolean isDisconnectNotification, final LdapException error) {
        if (!stopRequested) {
            app.errPrintln(LocalizableMessage.raw("Error occurred on one or more connections: "
                    + error.getResult()));
            if (error.getCause() != null && app.isVerbose()) {
                error.getCause().printStackTrace(app.getErrorStream());
            }
            stopRequested = true;
        }
    }

    @Override
    public void handleUnsolicitedNotification(final ExtendedResult notification) {
        // Ignore
    }

    public final void validate() throws ArgumentException {
        numConnections = numConnectionsArgument.getIntValue();
        numThreads = numThreadsArgument.getIntValue();
        warmUpDuration = warmUpArgument.getIntValue() * 1000L;
        maxIterations = maxIterationsArgument.getIntValue() / numConnections / numThreads;
        maxDurationTime = maxDurationArgument.getIntValue() * 1000L;
        statsInterval = statsIntervalArgument.getIntValue() * 1000;
        targetThroughput = targetThroughputArgument.getIntValue();

        isAsync = asyncArgument.isPresent();
        noRebind = noRebindArgument.isPresent();

        if (!noRebindArgument.isPresent() && this.numThreads > 1) {
            throw new ArgumentException(ERR_TOOL_ARG_MUST_BE_USED_WHEN_ARG_CONDITION.get(
                "--" + noRebindArgument.getLongIdentifier(), "--" + numThreadsArgument.getLongIdentifier(), "> 1"));
        }

        if (!noRebindArgument.isPresent() && asyncArgument.isPresent()) {
            throw new ArgumentException(ERR_TOOL_ARG_NEEDED_WHEN_USING_ARG.get(
                "--" + noRebindArgument.getLongIdentifier(), asyncArgument.getLongIdentifier()));
        }

        if (maxIterationsArgument.isPresent() && maxIterations <= 0) {
            throw new ArgumentException(ERR_TOOL_NOT_ENOUGH_ITERATIONS.get(
                "--" + maxIterationsArgument.getLongIdentifier(), numConnections * numThreads,
                numConnectionsArgument.getLongIdentifier(), numThreadsArgument.getLongIdentifier()));
        }

        dataSourcePrototypes = DataSource.parse(arguments.getValues());
    }

    final DataSource[] getDataSources() {
        if (dataSourcePrototypes == null) {
            throw new IllegalStateException("dataSources are null - validate() must be called first");
        }
        return dataSourcePrototypes;
    }

    abstract WorkerThread newWorkerThread(final Connection connection, final ConnectionFactory connectionFactory);
    abstract StatsThread newStatsThread();

    TimerThread newEndTimerThread(final long timeTowait) {
        return new TimerThread(timeTowait);
    }

    final int run(final ConnectionFactory connectionFactory) {
        final List<Connection> connections = new ArrayList<>();

        Connection connection = null;
        try {
            isWarmingUp = warmUpDuration > 0;
            for (int i = 0; i < numConnections; i++) {
                if (keepConnectionsOpen.isPresent() || noRebindArgument.isPresent()) {
                    connection = connectionFactory.getConnectionAsync().getOrThrow();
                    connection.addConnectionEventListener(this);
                    connections.add(connection);
                }
                for (int j = 0; j < numThreads; j++) {
                    final Thread thread = newWorkerThread(connection, connectionFactory);
                    workerThreads.add(thread);
                    thread.start();
                }
            }

            if (maxDurationTime > 0) {
                newEndTimerThread(maxDurationTime).start();
            }

            final StatsThread statsThread = newStatsThread();

            if (isWarmingUp) {
                if (!app.isScriptFriendly()) {
                    app.println(INFO_TOOL_WARMING_UP.get(warmUpDuration / 1000));
                }
                Thread.sleep(warmUpDuration);
                statsThread.resetStats();
                isWarmingUp = false;
            }

            statsThread.start();
            joinAllWorkerThreads();
            stopRequested = true;
            statsThread.join();
        } catch (final InterruptedException e) {
            stopRequested = true;
        } catch (final LdapException e) {
            stopRequested = true;
            printErrorMessage(app, e);
            return e.getResult().getResultCode().intValue();
        } finally {
            closeSilently(connections);
        }

        return 0;
    }

    void setBindRequest(final BindRequest request) {
        this.bindRequest = request;
    }

    BindRequest getBindRequest() {
        return bindRequest;
    }

    protected void joinAllWorkerThreads() throws InterruptedException {
        for (final Thread t : workerThreads) {
            t.join();
        }
    }
}
