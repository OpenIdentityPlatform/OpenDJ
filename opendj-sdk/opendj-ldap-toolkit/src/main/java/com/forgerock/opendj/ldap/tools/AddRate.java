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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.MultiColumnPrinter.column;
import static java.util.concurrent.TimeUnit.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.requests.Requests.newAddRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newDeleteRequest;
import static org.forgerock.util.promise.Promises.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.codahale.metrics.Counter;
import com.codahale.metrics.RatioGauge;
import com.forgerock.opendj.cli.MultiColumnPrinter;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldif.EntryGenerator;
import org.forgerock.util.promise.Promise;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * A load generation tool that can be used to load a Directory Server with Add
 * and Delete requests using one or more LDAP connections.
 */
public class AddRate extends ConsoleApplication {

    @SuppressWarnings("serial")
    private static final class AddRateExecutionEndedException extends LdapException {
        private AddRateExecutionEndedException() {
            super(Responses.newResult(OTHER));
        }
    }

    private final class AddPerformanceRunner extends PerformanceRunner {
        private final class AddStatsHandler extends UpdateStatsResultHandler<Result> {
            private final String entryDN;

            private AddStatsHandler(final long currentTime, final String entryDN) {
                super(currentTime);
                this.entryDN = entryDN;
            }

            @Override
            void updateAdditionalStatsOnResult() {
                switch (delStrategy) {
                case RANDOM:
                    long newKey;
                    do {
                        newKey = randomSeq.get().nextInt();
                    } while (dnEntriesAdded.putIfAbsent(newKey, entryDN) != null);
                    break;
                case FIFO:
                    long uniqueTime = operationStartTimeNs;
                    while (dnEntriesAdded.putIfAbsent(uniqueTime, entryDN) != null) {
                        uniqueTime++;
                    }
                    break;
                default:
                    break;
                }

                addCounter.inc();
                entryCount.inc();
            }
        }

        private final class DeleteStatsHandler extends UpdateStatsResultHandler<Result> {
            private DeleteStatsHandler(final long startTime) {
                super(startTime);
            }

            @Override
            void updateAdditionalStatsOnResult() {
                deleteCounter.inc();
                entryCount.dec();
            }
        }

        private final class AddRateStatsThread extends StatsThread {
            private static final int PERCENTAGE_ADD_COLUMN_WIDTH = 6;
            private static final String PERCENTAGE_ADD = STAT_ID_PREFIX + "add_percentage";

            private AddRateStatsThread(final PerformanceRunner perfRunner, final ConsoleApplication app) {
                super(perfRunner, app);
            }

            @Override
            void resetAdditionalStats() {
                addCounter = newIntervalCounter();
                deleteCounter = newIntervalCounter();
            }

            @Override
            List<MultiColumnPrinter.Column> registerAdditionalColumns() {
                registry.register(PERCENTAGE_ADD, new RatioGauge() {
                    @Override
                    protected Ratio getRatio() {
                        final long addIntervalCount = addCounter.refreshIntervalCount();
                        final long deleteIntervalCount = deleteCounter.refreshIntervalCount();
                        return Ratio.of(addIntervalCount * 100, addIntervalCount + deleteIntervalCount);
                    }
                });
                return Collections.singletonList(column(PERCENTAGE_ADD, "Add%", PERCENTAGE_ADD_COLUMN_WIDTH, 2));
            }
        }

        private final class AddDeleteWorkerThread extends WorkerThread {
            private AddDeleteWorkerThread(final Connection connection, final ConnectionFactory connectionFactory) {
                super(connection, connectionFactory);
            }

            @Override
            public Promise<?, LdapException> performOperation(
                    final Connection connection, final DataSource[] dataSources, final long currentTimeNs) {
                startPurgeIfMaxNumberAddReached();
                startToggleDeleteIfAgeThresholdReached(currentTimeNs);
                try {
                    String entryToRemove = getEntryToRemove();
                    if (entryToRemove != null) {
                        return doDelete(connection, currentTimeNs, entryToRemove);
                    }

                    return doAdd(connection, currentTimeNs);
                } catch (final AddRateExecutionEndedException a) {
                    return newResultPromise(OTHER);
                } catch (final IOException e) {
                    return newExceptionPromise(newLdapException(OTHER, e));
                }
            }

            private void startToggleDeleteIfAgeThresholdReached(long currentTime) {
                if (!toggleDelete
                        && delThreshold == DeleteThreshold.AGE_THRESHOLD
                        && !dnEntriesAdded.isEmpty()
                        && dnEntriesAdded.firstKey() + timeToWait < currentTime) {
                    setSizeThreshold(entryCount.getCount());
                }
            }

            private void startPurgeIfMaxNumberAddReached() {
                AtomicBoolean purgeLatch = new AtomicBoolean();
                if (!isPurgeBranchRunning.get()
                            && 0 < maxNbAddIterations && maxNbAddIterations < addCounter.getCount()
                            && purgeLatch.compareAndSet(false, true)) {
                    newPurgerThread().start();
                }
            }

            // FIXME Followings @Checkstyle:ignore tags are related to the maven-checkstyle-plugin
            // issue related here: https://github.com/checkstyle/checkstyle/issues/5
            // @Checkstyle:ignore
            private String getEntryToRemove() throws AddRateExecutionEndedException {
                if (isPurgeBranchRunning.get()) {
                    return purgeEntry();
                } else if (toggleDelete && entryCount.getCount() > sizeThreshold) {
                    return removeFirstAddedEntry();
                }
                return null;
            }

            // @Checkstyle:ignore
            private String purgeEntry() throws AddRateExecutionEndedException {
                if (!dnEntriesAdded.isEmpty()) {
                    return removeFirstAddedEntry();
                }
                localStopRequested = true;
                throw new AddRateExecutionEndedException();
            }

            private String removeFirstAddedEntry() {
                final Map.Entry<Long, String> entry = dnEntriesAdded.pollFirstEntry();
                return entry != null ? entry.getValue() : null;
            }

            private Promise<Result, LdapException> doAdd(
                    final Connection connection, final long currentTime) throws IOException {
                final Entry entry;
                synchronized (generator) {
                    entry = generator.readEntry();
                }

                final LdapResultHandler<Result> addHandler = new AddStatsHandler(
                        currentTime, entry.getName().toString());
                return connection.addAsync(newAddRequest(entry))
                                 .thenOnResultOrException(addHandler, addHandler);
            }

            private Promise<?, LdapException> doDelete(
                    final Connection connection, final long currentTime, final String entryToRemove) {
                final LdapResultHandler<Result> deleteHandler = new DeleteStatsHandler(currentTime);
                return connection.deleteAsync(newDeleteRequest(entryToRemove))
                                 .thenOnResultOrException(deleteHandler, deleteHandler);
            }
        }

        private final class AddRateTimerThread extends TimerThread {
            private AddRateTimerThread(final long timeToWait) {
                super(timeToWait);
            }

            @Override
            void performStopOperations() {
                if (purgeEnabled && isPurgeBranchRunning.compareAndSet(false, true)) {
                    if (!isScriptFriendly()) {
                        println(LocalizableMessage.raw("Purge phase..."));
                    }
                    try {
                        joinAllWorkerThreads();
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException();
                    }
                } else if (!purgeEnabled) {
                    stopTool();
                }
            }
        }

        private final ConcurrentSkipListMap<Long, String> dnEntriesAdded = new ConcurrentSkipListMap<>();
        private final ThreadLocal<Random> randomSeq = new ThreadLocal<Random>() {
            @Override
            protected Random initialValue() {
                return new Random();
            }
        };

        private EntryGenerator generator;
        private DeleteStrategy delStrategy;
        private DeleteThreshold delThreshold;
        private long sizeThreshold;
        private volatile boolean toggleDelete;
        private long timeToWait;
        private int maxNbAddIterations;
        private boolean purgeEnabled;
        private StatsThread.IntervalCounter addCounter = StatsThread.newIntervalCounter();
        private StatsThread.IntervalCounter deleteCounter = StatsThread.newIntervalCounter();
        private final Counter entryCount = new Counter();
        private final AtomicBoolean isPurgeBranchRunning = new AtomicBoolean();

        private AddPerformanceRunner(final PerformanceRunnerOptions options) throws ArgumentException {
            super(options);
        }

        @Override
        WorkerThread newWorkerThread(final Connection connection, final ConnectionFactory connectionFactory) {
            return new AddDeleteWorkerThread(connection, connectionFactory);
        }

        @Override
        StatsThread newStatsThread(final PerformanceRunner performanceRunner, final ConsoleApplication app) {
            return new AddRateStatsThread(performanceRunner, app);
        }

        @Override
        TimerThread newEndTimerThread(final long timeToWait) {
            return new AddRateTimerThread(timeToWait);
        }

        TimerThread newPurgerThread() {
            return newEndTimerThread(0);
        }

        public void validate(final MultiChoiceArgument<DeleteStrategy> delModeArg,
                final IntegerArgument delSizeThresholdArg, final IntegerArgument delAgeThresholdArg,
                final BooleanArgument noPurgeArgument) throws ArgumentException {
            super.validate();
            delStrategy = delModeArg.getTypedValue();
            maxNbAddIterations = maxIterationsArgument.getIntValue();
            purgeEnabled = !noPurgeArgument.isPresent();

            // Check for inconsistent use cases
            if (delSizeThresholdArg.isPresent() && delAgeThresholdArg.isPresent()) {
                throw new ArgumentException(ERR_ADDRATE_THRESHOLD_SIZE_AND_AGE.get());
            } else if (delStrategy == DeleteStrategy.OFF
                && (delSizeThresholdArg.isPresent() || delAgeThresholdArg.isPresent())) {
                throw new ArgumentException(ERR_ADDRATE_DELMODE_OFF_THRESHOLD_ON.get());
            } else if (delStrategy == DeleteStrategy.RANDOM && delAgeThresholdArg.isPresent()) {
                throw new ArgumentException(ERR_ADDRATE_DELMODE_RAND_THRESHOLD_AGE.get());
            }

            if (delStrategy != DeleteStrategy.OFF) {
                delThreshold =
                    delAgeThresholdArg.isPresent() ? DeleteThreshold.AGE_THRESHOLD : DeleteThreshold.SIZE_THRESHOLD;
                if (delThreshold == DeleteThreshold.SIZE_THRESHOLD) {
                    setSizeThreshold(delSizeThresholdArg.getIntValue());
                    if (0 < maxNbAddIterations && maxNbAddIterations < sizeThreshold) {
                        throw new ArgumentException(ERR_ADDRATE_SIZE_THRESHOLD_LOWER_THAN_ITERATIONS.get());
                    }
                } else {
                    timeToWait = NANOSECONDS.convert(delAgeThresholdArg.getIntValue(), SECONDS);
                }
            }
        }

        private void setSizeThreshold(final long entriesSizeThreshold) {
            sizeThreshold = entriesSizeThreshold;
            toggleDelete = true;
        }
    }

    private enum DeleteStrategy {
        OFF, RANDOM, FIFO;
    }

    private enum DeleteThreshold {
        SIZE_THRESHOLD, AGE_THRESHOLD, OFF;
    }

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int DEFAULT_SIZE_THRESHOLD = 10000;
    /** The minimum time to wait before starting add/delete phase (in seconds). */
    private static final int AGE_THRESHOLD_LOWERBOUND = 1;
    /** The minimum number of entries to add before starting add/delete phase. */
    private static final int SIZE_THRESHOLD_LOWERBOUND = 1;

    /**
     * The main method for AddRate tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new AddRate().run(args);
        System.exit(filterExitCode(retCode));
    }

    private BooleanArgument verbose;
    private BooleanArgument scriptFriendly;

    private AddRate() {
        // Nothing to do
    }

    AddRate(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isScriptFriendly() {
        return scriptFriendly.isPresent();
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_ADDRATE_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
            new ArgumentParser(AddRate.class.getName(), toolDescription, false, true, 1, 1, "template-file-path");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_ADDRATE.get());
        argParser.setDocToolDescriptionSupplement(SUPPLEMENT_DESCRIPTION_RATE_TOOLS.get());

        final ConnectionFactoryProvider connectionFactoryProvider;
        final ConnectionFactory connectionFactory;
        final AddPerformanceRunner runner;

        /* Entries generation parameters */
        final IntegerArgument randomSeedArg;
        final StringArgument resourcePathArg;
        final StringArgument constantsArg;

        /* addrate specifics arguments */
        final MultiChoiceArgument<DeleteStrategy> deleteMode;
        final IntegerArgument deleteSizeThreshold;
        final IntegerArgument deleteAgeThreshold;
        final BooleanArgument noPurgeArgument;

        try {
            Utils.setDefaultPerfToolProperties();
            final PerformanceRunnerOptions options = new PerformanceRunnerOptions(argParser, this);
            options.setSupportsGeneratorArgument(false);

            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);
            runner = new AddPerformanceRunner(options);

            addCommonArguments(argParser);

            /* Entries generation parameters */
            resourcePathArg =
                    StringArgument.builder(MakeLDIF.OPTION_LONG_RESOURCE_PATH)
                            .shortIdentifier('r')
                            .description(INFO_ADDRATE_DESCRIPTION_RESOURCE_PATH.get())
                            .docDescriptionSupplement(SUPPLEMENT_DESCRIPTION_RESOURCE_PATH.get())
                            .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            randomSeedArg =
                    IntegerArgument.builder(OPTION_LONG_RANDOM_SEED)
                            .shortIdentifier('R')
                            .description(INFO_ADDRATE_DESCRIPTION_SEED.get())
                            .defaultValue(0)
                            .valuePlaceholder(INFO_SEED_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            constantsArg =
                    StringArgument.builder(MakeLDIF.OPTION_LONG_CONSTANT)
                            .shortIdentifier('g')
                            .description(INFO_ADDRATE_DESCRIPTION_CONSTANT.get())
                            .multiValued()
                            .valuePlaceholder(INFO_CONSTANT_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            /* addrate specifics arguments */
            deleteMode =
                    MultiChoiceArgument.<DeleteStrategy>builder("deleteMode")
                            .shortIdentifier('C')
                            .description(INFO_ADDRATE_DESCRIPTION_DELETEMODE.get())
                            .allowedValues(DeleteStrategy.values())
                            .defaultValue(DeleteStrategy.FIFO)
                            .valuePlaceholder(INFO_DELETEMODE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            deleteSizeThreshold =
                    IntegerArgument.builder("deleteSizeThreshold")
                            .shortIdentifier('s')
                            .description(INFO_ADDRATE_DESCRIPTION_DELETESIZETHRESHOLD.get())
                            .lowerBound(SIZE_THRESHOLD_LOWERBOUND)
                            .defaultValue(DEFAULT_SIZE_THRESHOLD)
                            .valuePlaceholder(INFO_DELETESIZETHRESHOLD_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            deleteAgeThreshold =
                    IntegerArgument.builder("deleteAgeThreshold")
                            .shortIdentifier('a')
                            .description(INFO_ADDRATE_DESCRIPTION_DELETEAGETHRESHOLD.get())
                            .lowerBound(AGE_THRESHOLD_LOWERBOUND)
                            .valuePlaceholder(INFO_DELETEAGETHRESHOLD_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            noPurgeArgument =
                    BooleanArgument.builder("noPurge")
                        .shortIdentifier('n')
                        .description(INFO_ADDRATE_DESCRIPTION_NOPURGE.get())
                        .buildAndAddToParser(argParser);
        } catch (final ArgumentException ae) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            if (argParser.usageOrVersionDisplayed()) {
                return EXIT_CODE_SUCCESS;
            }

            connectionFactory = connectionFactoryProvider.getAuthenticatedConnectionFactory();
            runner.setBindRequest(connectionFactoryProvider.getBindRequest());
            runner.validate(deleteMode, deleteSizeThreshold, deleteAgeThreshold, noPurgeArgument);
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final String templatePath = argParser.getTrailingArguments().get(0);
        runner.generator = MakeLDIF.createGenerator(
                templatePath, resourcePathArg, randomSeedArg, constantsArg, false, this);
        if (runner.generator == null) {
            // Error message has already been logged.
            return ResultCode.OPERATIONS_ERROR.intValue();
        }
        Runtime.getRuntime().addShutdownHook(runner.newPurgerThread());

        return runner.run(connectionFactory);
    }

    private void addCommonArguments(final ArgumentParser argParser) throws ArgumentException {
        final StringArgument propertiesFileArgument = propertiesFileArgument();
        argParser.addArgument(propertiesFileArgument);
        argParser.setFilePropertiesArgument(propertiesFileArgument);

        final BooleanArgument noPropertiesFileArgument = noPropertiesFileArgument();
        argParser.addArgument(noPropertiesFileArgument);
        argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

        final BooleanArgument showUsage = showUsageArgument();
        argParser.addArgument(showUsage);
        argParser.setUsageArgument(showUsage, getOutputStream());

        verbose = verboseArgument();
        argParser.addArgument(verbose);

        scriptFriendly =
                BooleanArgument.builder("scriptFriendly")
                        .shortIdentifier('S')
                        .description(INFO_DESCRIPTION_SCRIPT_FRIENDLY.get())
                        .buildAndAddToParser(argParser);
    }
}
