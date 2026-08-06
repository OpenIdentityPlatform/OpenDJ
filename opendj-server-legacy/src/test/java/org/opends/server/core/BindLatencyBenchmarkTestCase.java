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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.core;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.tools.RemoteConnection;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Server side latency micro-benchmark for the simple BIND operation under high
 * concurrency.
 * <p>
 * The benchmark opens {@code bind.bench.threads} persistent LDAP connections
 * (one per thread), each repeatedly performing a simple BIND as a <em>random</em>
 * one of {@code bind.bench.users} provisioned users for
 * {@code bind.bench.durationSeconds} seconds, and reports the observed server
 * side latency distribution (mean / p50 / p90 / p99 / max) and throughput.
 * <p>
 * Many connections binding as different users is the canonical high-concurrency
 * authentication workload and is the scenario that stresses the per-bind
 * bookkeeping done in {@code ClientConnection.setAuthenticationInfo()} /
 * {@code AuthenticatedUsers}, whose user map is a concurrent map so that binds
 * for different users do not serialize on a single global lock.
 * <p>
 * The benchmark is <strong>disabled by default</strong> so that it never runs as
 * part of the normal test suite. Enable it explicitly, e.g.:
 * <pre>
 *   JAVA_HOME=&lt;jdk11&gt; mvn -P precommit -pl opendj-server-legacy verify \
 *       -Dit.test=BindLatencyBenchmarkTestCase -DfailIfNoTests=false \
 *       -Dbind.bench=true -Dbind.bench.threads=200 \
 *       -Dbind.bench.durationSeconds=120 -Dbind.bench.label=before
 * </pre>
 * Results are printed to stdout and also written to
 * {@code target/bind-bench-result-&lt;label&gt;.txt} (stdout may be suppressed
 * during the test run).
 */
@SuppressWarnings("javadoc")
public class BindLatencyBenchmarkTestCase extends DirectoryServerTestCase
{
  private static final String PASSWORD = "password";

  /** Whether the benchmark is enabled (it is skipped otherwise). */
  private static final boolean ENABLED = Boolean.getBoolean("bind.bench");
  private static final int THREADS = Integer.getInteger("bind.bench.threads", 200);
  /**
   * Number of distinct users to provision and bind as. Each bind picks a random
   * user, so binds spread across users (and, on the server, across the concurrent
   * {@code AuthenticatedUsers} map) - this is what exposes per-bind lock
   * contention. Defaults to the thread count.
   */
  private static final int USERS = Integer.getInteger("bind.bench.users", THREADS);

  private static String userDN(int i)
  {
    return "uid=bench.user." + i + ",o=test";
  }
  private static final int DURATION_SECONDS = Integer.getInteger("bind.bench.durationSeconds", 120);
  private static final int WARMUP_SECONDS = Integer.getInteger("bind.bench.warmupSeconds", 10);
  private static final String LABEL = System.getProperty("bind.bench.label", "run");
  private static final String HOST = System.getProperty("bind.bench.host", "127.0.0.1");

  private volatile boolean running = true;
  private volatile boolean recording;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    for (int i = 0; i < USERS; i++)
    {
      TestCaseUtils.addEntry(
          "dn: " + userDN(i),
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "uid: bench.user." + i,
          "givenName: Bench",
          "sn: User " + i,
          "cn: Bench User " + i,
          "userPassword: " + PASSWORD);
    }
  }

  @Test
  public void benchmarkConcurrentBind() throws Exception
  {
    if (!ENABLED)
    {
      // Keep the regular test suite fast: the benchmark only runs when
      // explicitly requested with -Dbind.bench=true.
      System.out.println("BindLatencyBenchmarkTestCase skipped (set -Dbind.bench=true to run).");
      return;
    }

    final int port = TestCaseUtils.getServerLdapPort();
    final CountDownLatch ready = new CountDownLatch(THREADS);
    final CountDownLatch startGate = new CountDownLatch(1);

    final List<Worker> workers = new ArrayList<>(THREADS);
    final List<Thread> threads = new ArrayList<>(THREADS);
    for (int i = 0; i < THREADS; i++)
    {
      Worker w = new Worker(i, HOST, port, ready, startGate);
      workers.add(w);
      Thread t = new Thread(w, "bind-bench-" + i);
      threads.add(t);
      t.start();
    }

    // Wait until every worker has its connection ready, then release them all together.
    assertTrue(ready.await(60, TimeUnit.SECONDS), "workers failed to connect in time");
    startGate.countDown();

    // Warm up (let JIT settle) without recording, then measure for the requested duration.
    Thread.sleep(TimeUnit.SECONDS.toMillis(WARMUP_SECONDS));
    long measureStart = System.nanoTime();
    recording = true;
    Thread.sleep(TimeUnit.SECONDS.toMillis(DURATION_SECONDS));
    recording = false;
    long measureEnd = System.nanoTime();
    running = false;

    for (Thread t : threads)
    {
      t.join(TimeUnit.SECONDS.toMillis(60));
    }

    // Aggregate results.
    LatencyHistogram total = new LatencyHistogram();
    long ops = 0;
    long errors = 0;
    for (Worker w : workers)
    {
      total.mergeFrom(w.hist);
      ops += w.ops;
      errors += w.errors;
    }

    double elapsedSeconds = (measureEnd - measureStart) / 1_000_000_000.0;
    double throughput = ops / elapsedSeconds;

    StringBuilder sb = new StringBuilder();
    sb.append("\n================ BIND latency benchmark [").append(LABEL).append("] ================\n");
    sb.append(String.format(Locale.ROOT, "threads               : %d%n", THREADS));
    sb.append(String.format(Locale.ROOT, "measured duration     : %.1f s (warmup %d s)%n", elapsedSeconds, WARMUP_SECONDS));
    sb.append(String.format(Locale.ROOT, "bind operations       : %d%n", ops));
    sb.append(String.format(Locale.ROOT, "errors                : %d%n", errors));
    sb.append(String.format(Locale.ROOT, "throughput            : %,.0f binds/s%n", throughput));
    sb.append(String.format(Locale.ROOT, "latency mean          : %.3f ms%n", total.meanMillis()));
    sb.append(String.format(Locale.ROOT, "latency p50           : %.3f ms%n", total.percentileMillis(50.0)));
    sb.append(String.format(Locale.ROOT, "latency p90           : %.3f ms%n", total.percentileMillis(90.0)));
    sb.append(String.format(Locale.ROOT, "latency p99           : %.3f ms%n", total.percentileMillis(99.0)));
    sb.append(String.format(Locale.ROOT, "latency p99.9         : %.3f ms%n", total.percentileMillis(99.9)));
    sb.append(String.format(Locale.ROOT, "latency max           : %.3f ms%n", total.maxMillis()));
    sb.append("=========================================================================\n");
    String report = sb.toString();

    System.out.println(report);
    writeReport(report);

    // Basic sanity checks - this is a measurement, not a pass/fail gate.
    assertEquals(errors, 0L, "some BIND operations failed");
    assertTrue(ops > 0, "no BIND operations were recorded");
  }

  private void writeReport(String report)
  {
    String buildDir = System.getProperty("org.opends.server.BuildDir", "target");
    File out = new File(buildDir, "bind-bench-result-" + LABEL + ".txt");
    try (PrintWriter pw = new PrintWriter(out, "UTF-8"))
    {
      pw.print(report);
    }
    catch (Exception e)
    {
      System.out.println("Could not write benchmark report to " + out + ": " + e);
    }
    System.out.println("Benchmark report written to " + out.getAbsolutePath());
  }

  /** A single benchmark worker: owns one connection and binds in a tight loop. */
  private final class Worker implements Runnable
  {
    private final int id;
    private final String host;
    private final int port;
    private final CountDownLatch ready;
    private final CountDownLatch startGate;
    final LatencyHistogram hist = new LatencyHistogram();
    long ops;
    long errors;

    Worker(int id, String host, int port, CountDownLatch ready, CountDownLatch startGate)
    {
      this.id = id;
      this.host = host;
      this.port = port;
      this.ready = ready;
      this.startGate = startGate;
    }

    @Override
    public void run()
    {
      RemoteConnection conn = null;
      try
      {
        conn = new RemoteConnection(host, port);
        ready.countDown();
        startGate.await();

        while (running)
        {
          String dn = userDN(ThreadLocalRandom.current().nextInt(USERS));
          long start = System.nanoTime();
          try
          {
            conn.bind(dn, PASSWORD);
          }
          catch (Throwable t)
          {
            errors++;
            conn = reconnect(conn);
            continue;
          }
          long elapsed = System.nanoTime() - start;
          if (recording)
          {
            hist.record(elapsed);
            ops++;
          }
        }
      }
      catch (Throwable t)
      {
        errors++;
        System.out.println("worker " + id + " aborted: " + t);
      }
      finally
      {
        close(conn);
      }
    }

    private RemoteConnection reconnect(RemoteConnection old)
    {
      close(old);
      try
      {
        return new RemoteConnection(host, port);
      }
      catch (Exception e)
      {
        return null;
      }
    }

    private void close(RemoteConnection conn)
    {
      if (conn != null)
      {
        try
        {
          conn.close();
        }
        catch (Exception ignored)
        {
          // best effort
        }
      }
    }
  }

  /**
   * Compact log-linear latency histogram (HdrHistogram style, ~16 sub-buckets per
   * power of two) with bounded memory and full dynamic range. Values are stored in
   * microseconds; reporting is in milliseconds.
   */
  static final class LatencyHistogram
  {
    private static final int SUB_BITS = 4;
    private static final int SUB_COUNT = 1 << SUB_BITS; // 16
    private static final int SIZE = 512;

    private final long[] counts = new long[SIZE];
    private long count;
    private long sumNanos;
    private long maxNanos;

    void record(long nanos)
    {
      long micros = (nanos + 500) / 1000;
      counts[bucketIndex(micros)]++;
      count++;
      sumNanos += nanos;
      if (nanos > maxNanos)
      {
        maxNanos = nanos;
      }
    }

    void mergeFrom(LatencyHistogram other)
    {
      for (int i = 0; i < SIZE; i++)
      {
        counts[i] += other.counts[i];
      }
      count += other.count;
      sumNanos += other.sumNanos;
      if (other.maxNanos > maxNanos)
      {
        maxNanos = other.maxNanos;
      }
    }

    static int bucketIndex(long micros)
    {
      if (micros < SUB_COUNT)
      {
        return (int) Math.max(0, micros);
      }
      int m = 63 - Long.numberOfLeadingZeros(micros); // floor(log2(micros))
      int sub = (int) ((micros - (1L << m)) >> (m - SUB_BITS));
      int idx = SUB_COUNT + (m - SUB_BITS) * SUB_COUNT + sub;
      return Math.min(idx, SIZE - 1);
    }

    private static long bucketMidpointMicros(int idx)
    {
      if (idx < SUB_COUNT)
      {
        return idx;
      }
      int j = idx - SUB_COUNT;
      int m = SUB_BITS + j / SUB_COUNT;
      int sub = j % SUB_COUNT;
      long lower = (1L << m) + ((long) sub << (m - SUB_BITS));
      long width = 1L << (m - SUB_BITS);
      return lower + width / 2;
    }

    double meanMillis()
    {
      return count == 0 ? 0.0 : (sumNanos / (double) count) / 1_000_000.0;
    }

    double maxMillis()
    {
      return maxNanos / 1_000_000.0;
    }

    double percentileMillis(double percentile)
    {
      if (count == 0)
      {
        return 0.0;
      }
      long target = (long) Math.ceil(percentile / 100.0 * count);
      if (target < 1)
      {
        target = 1;
      }
      long cumulative = 0;
      for (int i = 0; i < SIZE; i++)
      {
        cumulative += counts[i];
        if (cumulative >= target)
        {
          return bucketMidpointMicros(i) / 1000.0;
        }
      }
      return maxMillis();
    }
  }
}
