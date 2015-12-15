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
 *      Copyright 2014-2015 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.tools;

import static java.util.Locale.ENGLISH;
import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.util.promise.Promises.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.forgerock.opendj.cli.CommonArguments;
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
            public void handleResult(final Result result) {
                super.handleResult(result);

                switch (delStrategy) {
                case RANDOM:
                    long newKey;
                    do {
                        newKey = randomSeq.get().nextInt();
                    } while (dnEntriesAdded.putIfAbsent(newKey, entryDN) != null);
                    break;
                case FIFO:
                    long uniqueTime = currentTime;
                    while (dnEntriesAdded.putIfAbsent(uniqueTime, entryDN) != null) {
                        uniqueTime++;
                    }
                    break;
                default:
                    break;
                }

                recentAdds.getAndIncrement();
                totalAdds.getAndIncrement();
                entryCount.getAndIncrement();
            }
        }

        private final class DeleteStatsHandler extends UpdateStatsResultHandler<Result> {
            private DeleteStatsHandler(final long startTime) {
                super(startTime);
            }

            @Override
            public void handleResult(final Result result) {
                super.handleResult(result);
                recentDeletes.getAndIncrement();
                entryCount.getAndDecrement();
            }
        }

        private final class AddRateStatsThread extends StatsThread {
            private final String[] extraColumn = new String[1];

            private AddRateStatsThread() {
                super("Add%");
            }

            @Override
            void resetStats() {
                super.resetStats();
                recentAdds.set(0);
                recentDeletes.set(0);
            }

            @Override
            String[] getAdditionalColumns() {
                final int adds = recentAdds.getAndSet(0);
                final int deleteStat = recentDeletes.getAndSet(0);
                final int total = adds + deleteStat;

                extraColumn[0] = String.format(ENGLISH, "%.2f", total > 0 ? ((double) adds / total) * 100 : 0.0);

                return extraColumn;
            }
        }

        private final class AddDeleteWorkerThread extends WorkerThread {
            private AddDeleteWorkerThread(final Connection connection, final ConnectionFactory connectionFactory) {
                super(connection, connectionFactory);
            }

            @Override
            public Promise<?, LdapException> performOperation(
                    final Connection connection, final DataSource[] dataSources, final long currentTime) {
                startPurgeIfMaxNumberAddReached();
                startToggleDeleteIfAgeThresholdReached(currentTime);
                try {
                    String entryToRemove = getEntryToRemove(currentTime);
                    if (entryToRemove != null) {
                        return doDelete(connection, currentTime, entryToRemove);
                    }

                    return doAdd(connection, currentTime);
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
                    setSizeThreshold(entryCount.get());
                }
            }

            private void startPurgeIfMaxNumberAddReached() {
                AtomicBoolean purgeLatch = new AtomicBoolean();
                if (!isPurgeBranchRunning.get()
                            && 0 < maxNbAddIterations && maxNbAddIterations < totalAdds.get()
                            && purgeLatch.compareAndSet(false, true)) {
                    newPurgerThread().start();
                }
            }

            // FIXME Followings @Checkstyle:ignore tags are related to the maven-checkstyle-plugin
            // issue related here: https://github.com/checkstyle/checkstyle/issues/5
            // @Checkstyle:ignore
            private String getEntryToRemove(final long currentTime) throws AddRateExecutionEndedException {
                if (isPurgeBranchRunning.get()) {
                    return purgeEntry();
                }

                if (toggleDelete && entryCount.get() > sizeThreshold) {
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
                Entry entry;
                synchronized (generator) {
                    entry = generator.readEntry();
                }

                final LdapResultHandler<Result> addHandler = new AddStatsHandler(
                    currentTime, entry.getName().toString());
                return connection.addAsync(newAddRequest(entry))
                                 .thenOnResult(addHandler)
                                 .thenOnException(addHandler);
            }

            private Promise<?, LdapException> doDelete(
                    final Connection connection, final long currentTime, final String entryToRemove) {
                final LdapResultHandler<Result> deleteHandler = new DeleteStatsHandler(currentTime);
                return connection.deleteAsync(newDeleteRequest(entryToRemove))
                                 .thenOnResult(deleteHandler)
                                 .thenOnException(deleteHandler);
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
                    stopRequested = true;
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
        private int sizeThreshold;
        private volatile boolean toggleDelete;
        private long timeToWait;
        private int maxNbAddIterations;
        private boolean purgeEnabled;
        private final AtomicInteger recentAdds = new AtomicInteger();
        private final AtomicInteger recentDeletes = new AtomicInteger();
        private final AtomicInteger totalAdds = new AtomicInteger();
        private final AtomicInteger entryCount = new AtomicInteger();
        private final AtomicBoolean isPurgeBranchRunning = new AtomicBoolean();

        private AddPerformanceRunner(final PerformanceRunnerOptions options) throws ArgumentException {
            super(options);
            maxIterationsArgument.setPropertyName("maxNumberOfAdd");
        }

        @Override
        WorkerThread newWorkerThread(final Connection connection, final ConnectionFactory connectionFactory) {
            return new AddDeleteWorkerThread(connection, connectionFactory);
        }

        @Override
        StatsThread newStatsThread() {
            return new AddRateStatsThread();
        }

        @Override
        TimerThread newEndTimerThread(final long timeTowait) {
            return new AddRateTimerThread(timeTowait);
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

        private void setSizeThreshold(int entriesSizeThreshold) {
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
                new StringArgument("resourcepath", 'r', MakeLDIF.OPTION_LONG_RESOURCE_PATH, false, false, true,
                    INFO_PATH_PLACEHOLDER.get(), null, null, INFO_ADDRATE_DESCRIPTION_RESOURCE_PATH.get());
            resourcePathArg.setDocDescriptionSupplement(SUPPLEMENT_DESCRIPTION_RESOURCE_PATH.get());
            argParser.addArgument(resourcePathArg);

            randomSeedArg =
                new IntegerArgument("randomseed", 'R', OPTION_LONG_RANDOM_SEED, false, false, true,
                    INFO_SEED_PLACEHOLDER.get(), 0, null, INFO_ADDRATE_DESCRIPTION_SEED.get());
            argParser.addArgument(randomSeedArg);

            constantsArg =
                new StringArgument("constant", 'g', MakeLDIF.OPTION_LONG_CONSTANT, false, true, true,
                    INFO_CONSTANT_PLACEHOLDER.get(), null, null, INFO_ADDRATE_DESCRIPTION_CONSTANT.get());
            argParser.addArgument(constantsArg);

            /* addrate specifics arguments */
            deleteMode =
                new MultiChoiceArgument<>("deletemode", 'C', "deleteMode", false, true,
                    INFO_DELETEMODE_PLACEHOLDER.get(), Arrays.asList(DeleteStrategy.values()), false,
                    INFO_ADDRATE_DESCRIPTION_DELETEMODE.get());
            deleteMode.setDefaultValue(DeleteStrategy.FIFO.toString());
            argParser.addArgument(deleteMode);

            deleteSizeThreshold =
                new IntegerArgument("deletesizethreshold", 's', "deleteSizeThreshold", false, false, true,
                    INFO_DELETESIZETHRESHOLD_PLACEHOLDER.get(), DEFAULT_SIZE_THRESHOLD, "deleteSizeThreshold", true,
                    SIZE_THRESHOLD_LOWERBOUND, false, Integer.MAX_VALUE,
                    INFO_ADDRATE_DESCRIPTION_DELETESIZETHRESHOLD.get());
            argParser.addArgument(deleteSizeThreshold);

            deleteAgeThreshold =
                new IntegerArgument("deleteagethreshold", 'a', "deleteAgeThreshold", false, true,
                    INFO_DELETEAGETHRESHOLD_PLACEHOLDER.get(), true, AGE_THRESHOLD_LOWERBOUND, false,
                    Integer.MAX_VALUE, INFO_ADDRATE_DESCRIPTION_DELETEAGETHRESHOLD.get());
            deleteAgeThreshold.setPropertyName(deleteAgeThreshold.getLongIdentifier());
            argParser.addArgument(deleteAgeThreshold);

            noPurgeArgument = new BooleanArgument("nopurge", 'n', "noPurge", INFO_ADDRATE_DESCRIPTION_NOPURGE.get());
            argParser.addArgument(noPurgeArgument);
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
        final StringArgument propertiesFileArgument = CommonArguments.getPropertiesFile();
        argParser.addArgument(propertiesFileArgument);
        argParser.setFilePropertiesArgument(propertiesFileArgument);

        final BooleanArgument noPropertiesFileArgument = CommonArguments.getNoPropertiesFile();
        argParser.addArgument(noPropertiesFileArgument);
        argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

        final BooleanArgument showUsage = CommonArguments.getShowUsage();
        argParser.addArgument(showUsage);
        argParser.setUsageArgument(showUsage, getOutputStream());

        verbose = CommonArguments.getVerbose();
        argParser.addArgument(verbose);

        scriptFriendly =
            new BooleanArgument("scriptFriendly", 'S', "scriptFriendly", INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());
        scriptFriendly.setPropertyName("scriptFriendly");
        argParser.addArgument(scriptFriendly);

    }
}
