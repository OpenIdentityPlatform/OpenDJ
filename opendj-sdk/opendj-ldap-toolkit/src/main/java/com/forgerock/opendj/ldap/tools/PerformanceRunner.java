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
 *      Portions Copyright 2011-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.util.StaticUtils;

/** Benchmark application framework. */
abstract class PerformanceRunner implements ConnectionEventListener {
    private static final double[] DEFAULT_PERCENTILES = new double[] { 99.9, 99.99, 99.999 };

    class TimerThread extends Thread {
        private final long timeToWait;

        TimerThread(long timeToWait) {
            this.timeToWait = timeToWait;
        }

        void performStopOperations() {
            stopTool();
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
        protected final long operationStartTimeNs;

        UpdateStatsResultHandler(final long currentTimeNs) {
            this.operationStartTimeNs = currentTimeNs;
        }

        @Override
        public final void handleException(final LdapException exception) {
            statsThread.incrementFailedCount();
            updateResponseTime();
            app.errPrintVerboseMessage(LocalizableMessage.raw(exception.getResult().toString()));
        }

        @Override
        public final void handleResult(final S result) {
            statsThread.incrementSuccessCount();
            updateResponseTime();
            updateAdditionalStatsOnResult();
        }

        /** Do nothing by default, child classes which manage additional stats need to override this method. */
        void updateAdditionalStatsOnResult() { }

        private void updateResponseTime() {
            statsThread.addResponseTime(System.nanoTime() - operationStartTimeNs);
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
                Connection connection, DataSource[] dataSources, long currentTimeNs);

        @Override
        public void run() {
            Promise<?, LdapException> promise;
            Connection connection;
            final double targetTimeMs = 1000.0 / (targetThroughput / (double) (numThreads * numConnections));
            double sleepTimeMs = 0;

            while (!stopRequested && !localStopRequested
                    && (maxIterations <= 0 || count < maxIterations)) {
                try {
                    connection = getConnectionToUse();
                } catch (final InterruptedException e) {
                    // Ignore and check stop requested
                    continue;
                } catch (final LdapException e) {
                    handleConnectionError(false, e);
                    break;
                }

                long startTimeNs = System.nanoTime();
                promise = performOperation(connection, dataSources.get(), startTimeNs);
                statsThread.incrementOperationCount();
                if (!isAsync) {
                    try {
                        promise.getOrThrow();
                    } catch (final InterruptedException e) {
                        // Ignore and check stop requested
                        continue;
                    } catch (final LdapException e) {
                        if (!stopRequested && e.getCause() instanceof IOException) {
                            e.getCause().printStackTrace(app.getErrorStream());
                            stopTool(true);
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

        private Connection getConnectionToUse() throws InterruptedException, LdapException {
            if (this.connection == null) {
                return connectionFactory.getConnectionAsync().getOrThrow();
            } else {
                final Connection resultConnection = this.connection;
                if (!noRebind && bindRequest != null) {
                    resultConnection.bindAsync(bindRequest).getOrThrow();
                }
                return resultConnection;
            }
        }

        void incrementIterationCount() {
            count++;
        }
    }

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
    private boolean stopRequested;

    private int targetThroughput;
    private int maxIterations;
    /** Warm-up duration time in ms. **/
    private long warmUpDurationMs;
    /** Max duration time in ms, 0 for unlimited. **/
    private long maxDurationTimeMs;
    private boolean isAsync;
    private boolean noRebind;
    private BindRequest bindRequest;
    private int statsIntervalMs;
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
    StatsThread statsThread;

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
            app.errPrintln(ERROR_RATE_TOOLS_CANNOT_GET_CONNECTION.get(error.getMessage()));
            if (error.getCause() != null && app.isVerbose()) {
                error.getCause().printStackTrace(app.getErrorStream());
            }
            stopTool(true);
        }
    }

    @Override
    public void handleUnsolicitedNotification(final ExtendedResult notification) {
        // Ignore
    }

    public final void validate() throws ArgumentException {
        numConnections = numConnectionsArgument.getIntValue();
        numThreads = numThreadsArgument.getIntValue();
        warmUpDurationMs = warmUpArgument.getIntValue() * 1000L;
        maxIterations = maxIterationsArgument.getIntValue() / numConnections / numThreads;
        maxDurationTimeMs = maxDurationArgument.getIntValue() * 1000L;
        statsIntervalMs = statsIntervalArgument.getIntValue() * 1000;
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
    abstract StatsThread newStatsThread(final PerformanceRunner performanceRunner, final ConsoleApplication app);

    TimerThread newEndTimerThread(final long timeToWait) {
        return new TimerThread(timeToWait);
    }

    final int run(final ConnectionFactory connectionFactory) {
        final List<Connection> connections = new ArrayList<>();
        statsThread = newStatsThread(this, app);

        try {
            validateCanConnectToServer(connectionFactory);
            for (int i = 0; i < numConnections; i++) {
                Connection connection = null;
                if (keepConnectionsOpen.isPresent() || noRebindArgument.isPresent()) {
                    connection = connectionFactory.getConnection();
                    connection.addConnectionEventListener(this);
                    connections.add(connection);
                }
                for (int j = 0; j < numThreads; j++) {
                    final Thread thread = newWorkerThread(connection, connectionFactory);
                    workerThreads.add(thread);
                    thread.start();
                }
            }

            if (maxDurationTimeMs > 0) {
                newEndTimerThread(maxDurationTimeMs).start();
            }

            statsThread.startReporting();
            joinAllWorkerThreads();
            stopTool();
        } catch (final InterruptedException e) {
            stopTool(true);
        } catch (final LdapException e) {
            stopTool(true);
            printErrorMessage(app, e);
            return e.getResult().getResultCode().intValue();
        } finally {
            closeSilently(connections);
        }

        return 0;
    }

    private void validateCanConnectToServer(ConnectionFactory connectionFactory) throws LdapException {
        try (Connection c = connectionFactory.getConnection()) {
            // detects wrong bind parameters, server unreachable (server down, network problem?), etc.
        }
    }

    synchronized void stopTool() {
        stopTool(false);
    }

    synchronized void stopTool(final boolean stoppedByError) {
        if (!stopRequested) {
            stopRequested = true;
            statsThread.stopRecording(stoppedByError);
        }
    }

    void setBindRequest(final BindRequest request) {
        this.bindRequest = request;
    }

    protected void joinAllWorkerThreads() throws InterruptedException {
        for (final Thread t : workerThreads) {
            t.join();
        }
    }

    boolean isAsync() {
        return isAsync;
    }

    double[] getPercentiles() {
        if (percentilesArgument.isPresent()) {
            double[] percentiles = new double[percentilesArgument.getValues().size()];
            int index = 0;
            for (final String percentile : percentilesArgument.getValues()) {
                percentiles[index++] = Double.parseDouble(percentile);
            }
            Arrays.sort(percentiles);
            return percentiles;
        }

        return DEFAULT_PERCENTILES;
    }

    long getWarmUpDurationMs() {
        return warmUpDurationMs;
    }

    long getStatsInterval() {
        return statsIntervalMs;
    }
}
