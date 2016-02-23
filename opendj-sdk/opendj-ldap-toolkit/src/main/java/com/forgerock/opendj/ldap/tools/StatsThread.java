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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.MultiColumnPrinter.column;
import static com.forgerock.opendj.cli.MultiColumnPrinter.separatorColumn;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.INFO_TOOL_WARMING_UP;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.MultiColumnPrinter;
import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramReservoir;

/**
 * Statistics thread base implementation.
 * <p>
 * The goal of this class is to compute and print rate tool general statistics.
 */
class StatsThread extends Thread {

    static final String STAT_ID_PREFIX = "org.forgerock.opendj.";

    private static final String TIME_NOW = STAT_ID_PREFIX + "current_time";
    private static final String RECENT_THROUGHPUT = STAT_ID_PREFIX + "recent_throughput";
    private static final String AVERAGE_THROUGHPUT = STAT_ID_PREFIX + "average_throughput";
    private static final String RECENT_RESPONSE_TIME_MS = STAT_ID_PREFIX + "recent_response_time";
    private static final String AVERAGE_RESPONSE_TIME_MS = STAT_ID_PREFIX + "average_response_time";
    private static final String PERCENTILES = STAT_ID_PREFIX + "percentiles";
    private static final String ERROR_PER_SECOND = STAT_ID_PREFIX + "error_per_second";

    public static final double MS_IN_S = TimeUnit.SECONDS.toMillis(1);
    public static final double NS_IN_MS = TimeUnit.MILLISECONDS.toNanos(1);

    private abstract class RateReporter extends ScheduledReporter {
        final MultiColumnPrinter printer;

        private RateReporter() {
            super(registry, "", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
            printer = createPrinter();
        }

        abstract MultiColumnPrinter createPrinter();
        abstract void printTitle();

        @Override
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void report(final SortedMap<String, Gauge> gauges,
                           final SortedMap<String, Counter> counters,
                           final SortedMap<String, Histogram> histograms,
                           final SortedMap<String, Meter> meters,
                           final SortedMap<String, Timer> timers) {
            int percentileIndex = 0;
            for (final MultiColumnPrinter.Column column : printer.getColumns()) {
                final String statKey = column.getId();
                if (gauges.containsKey(statKey)) {
                    printer.printData(((Gauge<Double>) gauges.get(statKey)).getValue());
                } else if (statKey.startsWith(PERCENTILES)) {
                    final double quantile = percentiles[percentileIndex++] / 100.0;
                    printer.printData(
                            histograms.get(PERCENTILES).getSnapshot().getValue(quantile) / MILLISECONDS.toNanos(1));
                } else {
                    printer.printData("-");
                }
            }
        }
    }

    private final class ConsoleRateReporter extends RateReporter {
        private static final int STANDARD_WIDTH = 8;
        private List<MultiColumnPrinter.Column> additionalColumns;

        @Override
        void printTitle() {
            final int throughputRawSpan = 2;
            final int responseTimeRawSpan = 2 + percentiles.length;
            final int additionalStatsRawSpan = 1 + additionalColumns.size();

            printer.printDashedLine();
            printer.printTitleSection("Throughput", throughputRawSpan);
            printer.printTitleSection("Response Time", responseTimeRawSpan);
            printer.printTitleSection(additionalStatsRawSpan > 1 ? "Additional" : "", additionalStatsRawSpan);
            printer.printTitleSection("(ops/second)", throughputRawSpan);
            printer.printTitleSection("(milliseconds)", responseTimeRawSpan);
            printer.printTitleSection(additionalStatsRawSpan > 1 ? "Statistics" : "", additionalStatsRawSpan);
            printer.printTitleLine();
            printer.printDashedLine();
        }

        @Override
        MultiColumnPrinter createPrinter() {
            final List<MultiColumnPrinter.Column> columns = new ArrayList<>();
            // Throughput (ops/sec)
            columns.add(separatorColumn());
            columns.add(column(RECENT_THROUGHPUT, "recent", STANDARD_WIDTH, 1));
            columns.add(column(AVERAGE_THROUGHPUT, "average", STANDARD_WIDTH, 1));
            // Response Time (ms)
            columns.add(separatorColumn());
            columns.add(column(RECENT_RESPONSE_TIME_MS, "recent", STANDARD_WIDTH, 3));
            columns.add(column(AVERAGE_RESPONSE_TIME_MS, "average", STANDARD_WIDTH, 3));
            for (double percentile : percentiles) {
                columns.add(column(PERCENTILES + percentile, percentile + "%", STANDARD_WIDTH, 2));
            }
            // Additional stats
            columns.add(separatorColumn());
            columns.add(column(ERROR_PER_SECOND, "err/sec", STANDARD_WIDTH, 1));
            additionalColumns = registerAdditionalColumns();
            if (!additionalColumns.isEmpty()) {
                columns.addAll(additionalColumns);
            }
            columns.add(separatorColumn());


            return MultiColumnPrinter.builder(app.getOutputStream(), columns)
                    .format(true)
                    .titleAlignment(MultiColumnPrinter.Alignment.CENTER)
                    .build();
        }
    }

    private final class CsvRateReporter extends RateReporter {
        @Override
        void printTitle() {
            printer.printTitleLine();
        }

        @Override
        MultiColumnPrinter createPrinter() {
            final List<MultiColumnPrinter.Column> columns = new ArrayList<>();
            columns.add(column(TIME_NOW, "time", 3));
            columns.add(column(RECENT_THROUGHPUT, "recent throughput", 1));
            columns.add(column(AVERAGE_THROUGHPUT, "average throughput", 1));
            columns.add(column(RECENT_RESPONSE_TIME_MS, "recent response time", 3));
            columns.add(column(AVERAGE_RESPONSE_TIME_MS, "average response time", 3));
            for (double percentile : percentiles) {
                columns.add(column(
                        PERCENTILES + percentile, percentile + "% response time", 2));
            }
            columns.add(column(ERROR_PER_SECOND, "errors/second", 1));
            columns.addAll(registerAdditionalColumns());


            return MultiColumnPrinter.builder(app.getOutputStream(), columns)
                    .columnSeparator(",")
                    .build();
        }
    }

    /** A timer to prevent adding temporary variables in {@link StatsThread#run()}. **/
    private static abstract class StatsTimer {
        private long startTimeMeasure;
        private long elapsed;

        abstract long getInstantTime();

        private void start() {
            startTimeMeasure = getInstantTime();
        }

        private long reset() {
            final long time = getInstantTime();
            elapsed = time - this.startTimeMeasure;
            this.startTimeMeasure = time;

            return elapsed;
        }

        private long elapsed() {
            return elapsed;
        }
    }

    /** A counter to prevent adding temporary variables in {@link StatsThread#run()}. **/
    static final class IntervalCounter extends Counter {
        private long lastIntervalCount;
        private long lastTotalCount;

        long refreshIntervalCount() {
            final long totalCount = getCount();
            lastIntervalCount = totalCount - lastTotalCount;
            lastTotalCount = totalCount;
            return lastIntervalCount;
        }

        long getLastIntervalCount() {
            return lastIntervalCount;
        }

        long getLastTotalCount() {
            return lastTotalCount;
        }
    }

    static final IntervalCounter newIntervalCounter() {
        return new IntervalCounter();
    }


    final MetricRegistry registry = new MetricRegistry();
    private final Histogram responseTimes = new Histogram(new HdrHistogramReservoir());

    private final StatsTimer gcTimerMs = new StatsTimer() {
        private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        @Override
        long getInstantTime() {
            long gcDurationMs = 0;
            for (final GarbageCollectorMXBean bean : gcBeans) {
                gcDurationMs += bean.getCollectionTime();
            }

            return gcDurationMs;
        }
    };

    private final StatsTimer timerMs = new StatsTimer() {
        @Override
        long getInstantTime() {
            return System.currentTimeMillis();
        }
    };

    IntervalCounter waitDurationNsCount;
    IntervalCounter successCount;
    private IntervalCounter operationCount;
    private IntervalCounter errorCount;
    private IntervalCounter durationMsCount;

    private final ConsoleApplication app;
    private final double[] percentiles;
    private final PerformanceRunner performanceRunner;
    private final RateReporter reporter;
    private long startTimeMs;
    private volatile boolean warmingUp;
    private final ScheduledExecutorService statThreadScheduler = Executors.newSingleThreadScheduledExecutor();

    StatsThread(final PerformanceRunner performanceRunner, final ConsoleApplication application) {
        super("Stats Thread");
        resetStats();
        this.performanceRunner = performanceRunner;
        this.app = application;
        this.percentiles = performanceRunner.getPercentiles();
        this.reporter = app.isScriptFriendly() ? new CsvRateReporter()
                                               : new ConsoleRateReporter();
        registerStats();
    }

    /** Resets both general and recent statistic indicators. */
    final void resetStats() {
        errorCount = newIntervalCounter();
        operationCount = newIntervalCounter();
        successCount = newIntervalCounter();
        waitDurationNsCount = newIntervalCounter();
        durationMsCount = newIntervalCounter();
        resetAdditionalStats();
    }

    private void registerStats() {
        if (app.isScriptFriendly()) {
            registry.register(TIME_NOW, new RatioGauge() {
                @Override
                protected Ratio getRatio() {
                    return Ratio.of(System.currentTimeMillis() - startTimeMs, MS_IN_S);
                }
            });
        }

        registry.register(RECENT_THROUGHPUT, new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(successCount.getLastIntervalCount() + errorCount.getLastIntervalCount(),
                                durationMsCount.getLastIntervalCount() / MS_IN_S);
            }
        });

        registry.register(AVERAGE_THROUGHPUT, new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(successCount.getLastTotalCount() + errorCount.getLastTotalCount(),
                                durationMsCount.getLastTotalCount() / MS_IN_S);
            }
        });

        registry.register(RECENT_RESPONSE_TIME_MS, new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of((waitDurationNsCount.getLastIntervalCount() / NS_IN_MS) - gcTimerMs.elapsed(),
                                successCount.getLastIntervalCount() + errorCount.getLastIntervalCount());
            }
        });

        registry.register(AVERAGE_RESPONSE_TIME_MS, new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of((waitDurationNsCount.getLastTotalCount() / NS_IN_MS) - gcTimerMs.getInstantTime(),
                                 successCount.getLastTotalCount() + errorCount.getLastTotalCount());
            }
        });

        registry.register(ERROR_PER_SECOND, new RatioGauge() {
            @Override
            protected Ratio getRatio() {
                return Ratio.of(errorCount.getLastIntervalCount(), durationMsCount.getLastIntervalCount() / MS_IN_S);
            }
        });
        registry.register(PERCENTILES, responseTimes);
    }

    void startReporting() throws InterruptedException {
        warmUp();
        init();
        final long statsIntervalMs = performanceRunner.getStatsInterval();
        statThreadScheduler.scheduleAtFixedRate(this, statsIntervalMs, statsIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void warmUp() throws InterruptedException {
        final long warmUpDurationMs = performanceRunner.getWarmUpDurationMs();
        if (warmUpDurationMs > 0) {
            if (!app.isScriptFriendly()) {
                app.println(INFO_TOOL_WARMING_UP.get(warmUpDurationMs / TimeUnit.SECONDS.toMillis(1)));
            }
            Thread.sleep(warmUpDurationMs);
            resetStats();
        }
        warmingUp = false;
    }

    private void init() {
        reporter.printTitle();
        timerMs.start();
        gcTimerMs.start();
        startTimeMs = System.currentTimeMillis();
    }

    public void stopRecording(final boolean stoppedByError) {
        statThreadScheduler.shutdown();
        if (!stoppedByError) {
            // If stats thread is printing stats, wait for it to finish and print a last line of stats.
            try {
                statThreadScheduler.awaitTermination(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                // Do nothing.
            }
            run();
        }
    }

    /** Performs stat snapshots and reports results to application. */
    @Override
    public void run() {
        durationMsCount.inc(timerMs.reset() - gcTimerMs.reset());
        durationMsCount.refreshIntervalCount();
        operationCount.refreshIntervalCount();
        successCount.refreshIntervalCount();
        errorCount.refreshIntervalCount();
        waitDurationNsCount.refreshIntervalCount();

        reporter.report();
    }

    void addResponseTime(final long responseTimeNs) {
        if (!warmingUp) {
            waitDurationNsCount.inc(responseTimeNs);
            responseTimes.update(responseTimeNs);
        }
    }

    void incrementFailedCount() {
        errorCount.inc();
    }

    void incrementSuccessCount() {
        successCount.inc();
    }

    void incrementOperationCount() {
        operationCount.inc();
    }

    /** Child classes which manage additional stats need to override this method. */
    List<MultiColumnPrinter.Column> registerAdditionalColumns() {
        return Collections.emptyList();
    }

    /** Do nothing by default, child classes which manage additional stats need to override this method. */
    void resetAdditionalStats() { }
}
