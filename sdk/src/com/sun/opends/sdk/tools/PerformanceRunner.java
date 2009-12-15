/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.tools;



import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.sdk.*;
import org.opends.sdk.AuthenticatedConnectionFactory.AuthenticatedAsynchronousConnection;
import org.opends.sdk.responses.Result;




/**
 * Benchmark application framework.
 */
abstract class PerformanceRunner
{
  private final AtomicInteger operationRecentCount = new AtomicInteger();

  private final AtomicInteger successRecentCount = new AtomicInteger();

  private final AtomicInteger failedRecentCount = new AtomicInteger();

  private final AtomicLong waitRecentTime = new AtomicLong();

  private final AtomicReference<ReversableArray> eTimeBuffer = new AtomicReference<ReversableArray>(
      new ReversableArray(100000));

  private final ConsoleApplication app;

  private final ThreadLocal<DataSource[]> dataSources = new ThreadLocal<DataSource[]>();

  private volatile boolean stopRequested;

  private int numThreads;

  private int numConnections;

  private int targetThroughput;

  private int maxIterations;

  private boolean isAsync;

  private boolean noRebind;

  private int statsInterval;

  private IntegerArgument numThreadsArgument;

  private IntegerArgument maxIterationsArgument;

  private IntegerArgument statsIntervalArgument;

  private IntegerArgument targetThroughputArgument;

  private IntegerArgument numConnectionsArgument;

  private IntegerArgument percentilesArgument;

  private BooleanArgument keepConnectionsOpen;

  private BooleanArgument noRebindArgument;

  private BooleanArgument asyncArgument;

  private StringArgument arguments;



  PerformanceRunner(ArgumentParser argParser, ConsoleApplication app)
      throws ArgumentException
  {
    this.app = app;
    numThreadsArgument = new IntegerArgument("numThreads", 't',
        "numThreads", false, false, true, LocalizableMessage.raw("{numThreads}"),
        1, null, true, 1, false, 0, LocalizableMessage
            .raw("number of search threads per connection"));
    numThreadsArgument.setPropertyName("numThreads");
    argParser.addArgument(numThreadsArgument);

    numConnectionsArgument = new IntegerArgument("numConnections", 'c',
        "numConnections", false, false, true, LocalizableMessage
            .raw("{numConnections}"), 1, null, true, 1, false, 0,
        LocalizableMessage.raw("number of connections"));
    numThreadsArgument.setPropertyName("numConnections");
    argParser.addArgument(numConnectionsArgument);

    maxIterationsArgument = new IntegerArgument("maxIterations", 'm',
        "maxIterations", false, false, true, LocalizableMessage
            .raw("{maxIterations}"), 0, null, LocalizableMessage
            .raw("max searches per thread, 0 for unlimited"));
    numThreadsArgument.setPropertyName("maxIterations");
    argParser.addArgument(maxIterationsArgument);

    statsIntervalArgument = new IntegerArgument("statInterval", 'i',
        "statInterval", false, false, true, LocalizableMessage
            .raw("{statInterval}"), 5, null, true, 1, false, 0, LocalizableMessage
            .raw("Display results each specified number of seconds"));
    numThreadsArgument.setPropertyName("statInterval");
    argParser.addArgument(statsIntervalArgument);

    targetThroughputArgument = new IntegerArgument("targetThroughput",
        'M', "targetThroughput", false, false, true, LocalizableMessage
            .raw("{targetThroughput}"), 0, null, LocalizableMessage
            .raw("Target average throughput to achieve"));
    targetThroughputArgument.setPropertyName("targetThroughput");
    argParser.addArgument(targetThroughputArgument);

    percentilesArgument = new IntegerArgument("percentile", 'e',
        "percentile", false, true, LocalizableMessage.raw("{percentile}"), true,
        50, true, 100, LocalizableMessage.raw("Calculate max response time for a "
            + "percentile of operations"));
    percentilesArgument.setPropertyName("percentile");
    argParser.addArgument(percentilesArgument);

    keepConnectionsOpen = new BooleanArgument("keepConnectionsOpen",
        'f', "keepConnectionsOpen", LocalizableMessage
            .raw("keep connections open"));
    keepConnectionsOpen.setPropertyName("keepConnectionsOpen");
    argParser.addArgument(keepConnectionsOpen);

    noRebindArgument = new BooleanArgument("noRebind", 'F', "noRebind",
        LocalizableMessage.raw("keep connections open and don't rebind"));
    keepConnectionsOpen.setPropertyName("noRebind");
    argParser.addArgument(noRebindArgument);

    asyncArgument = new BooleanArgument("asynchronous", 'A',
        "asynchronous", LocalizableMessage.raw("asynch, don't wait for results"));
    keepConnectionsOpen.setPropertyName("asynchronous");
    argParser.addArgument(asyncArgument);

    arguments = new StringArgument(
        "arguments",
        'g',
        "arguments",
        false,
        true,
        true,
        LocalizableMessage.raw("{arguments}"),
        null,
        null,
        LocalizableMessage
            .raw("arguments for variables in the filter and/or base DN"));
    arguments.setPropertyName("arguments");
    argParser.addArgument(arguments);
  }



  public void validate() throws ArgumentException
  {
    numConnections = numConnectionsArgument.getIntValue();
    numThreads = numThreadsArgument.getIntValue();
    maxIterations = maxIterationsArgument.getIntValue();
    statsInterval = statsIntervalArgument.getIntValue() * 1000;
    targetThroughput = targetThroughputArgument.getIntValue();

    isAsync = asyncArgument.isPresent();
    noRebind = noRebindArgument.isPresent();

    if (!noRebindArgument.isPresent() && this.numThreads > 1)
    {
      throw new ArgumentException(LocalizableMessage.raw("--"
          + noRebindArgument.getLongIdentifier()
          + " must be used if --"
          + numThreadsArgument.getLongIdentifier() + " is > 1"));
    }

    if (!noRebindArgument.isPresent() && asyncArgument.isPresent())
    {
      throw new ArgumentException(LocalizableMessage.raw("--"
          + noRebindArgument.getLongIdentifier()
          + " must be used when using --"
          + asyncArgument.getLongIdentifier()));
    }

    try
    {
      DataSource.parse(arguments.getValues());
    }
    catch (IOException ioe)
    {
      throw new ArgumentException(LocalizableMessage
          .raw("Error occured while parsing arguments: "
              + ioe.toString()));
    }
  }



  final int run(ConnectionFactory<?> connectionFactory)
  {
    List<Thread> threads = new ArrayList<Thread>();

    AsynchronousConnection connection = null;
    Thread thread;
    try
    {
      for (int i = 0; i < numConnections; i++)
      {
        if (keepConnectionsOpen.isPresent()
            || noRebindArgument.isPresent())
        {
          connection = connectionFactory.getAsynchronousConnection(
              null).get();
        }
        for (int j = 0; j < numThreads; j++)
        {
          thread = newWorkerThread(connection, connectionFactory);

          threads.add(thread);
          thread.start();
        }
      }

      Thread statsThread = newStatsThread();
      statsThread.start();

      for (Thread t : threads)
      {
        t.join();
      }
      stopRequested = true;
      statsThread.join();
    }
    catch (InterruptedException e)
    {
      stopRequested = true;
    }
    catch (ErrorResultException e)
    {
      stopRequested = true;
      app.println(LocalizableMessage.raw(e.getResult().getDiagnosticMessage()));
    }

    return 0;
  }



  final DataSource[] getDataSources()
  {
    try
    {
      return DataSource.parse(arguments.getValues());
    }
    catch (IOException ioe)
    {
      // Ignore as this shouldn've been handled eariler
    }
    return new DataSource[0];
  }



  abstract WorkerThread<?> newWorkerThread(
      AsynchronousConnection connection,
      ConnectionFactory<?> connectionFactory);



  abstract StatsThread newStatsThread();



  class UpdateStatsResultHandler<S extends Result> implements
      ResultHandler<S>
  {
    private long eTime;



    UpdateStatsResultHandler(long eTime)
    {
      this.eTime = eTime;
    }



    public void handleResult(S result)
    {
      successRecentCount.getAndIncrement();
      eTime = System.nanoTime() - eTime;
      waitRecentTime.getAndAdd(eTime);
      synchronized (this)
      {
        ReversableArray array = eTimeBuffer.get();
        if (array.remaining() == 0)
        {
          array.set(array.size() - 1, eTime);
        }
        else
        {
          array.append(eTime);
        }
      }
    }



    public void handleErrorResult(ErrorResultException error)
    {
      failedRecentCount.getAndIncrement();
      app.println(LocalizableMessage.raw(error.getResult().toString()));
    }



    public long getETime()
    {
      return eTime;
    }
  }



  abstract class WorkerThread<R extends ResultHandler<?>> extends
      Thread
  {
    private int count;

    private final AsynchronousConnection connection;

    private final ConnectionFactory<?> connectionFactory;



    WorkerThread(AsynchronousConnection connection,
        ConnectionFactory<?> connectionFactory)
    {
      super("Worker Thread");
      this.connection = connection;
      this.connectionFactory = connectionFactory;
    }



    public abstract FutureResult<?> performOperation(
        AsynchronousConnection connection, R handler,
        DataSource[] dataSources);



    public abstract R getHandler(long startTime);



    public void run()
    {
      if (dataSources.get() == null)
      {
        try
        {
          dataSources.set(DataSource.parse(arguments.getValues()));
        }
        catch (IOException ioe)
        {
          // Ignore as this shouldn've been handled eariler
        }
      }

      FutureResult<?> future;
      AsynchronousConnection connection;
      R handler;

      double targetTimeInMS = (1.0 / (targetThroughput / (numThreads * numConnections))) * 1000.0;
      double sleepTimeInMS = 0;
      long start;
      while (!stopRequested
          && !(maxIterations > 0 && count >= maxIterations))
      {
        start = System.nanoTime();
        handler = getHandler(start);

        if (this.connection == null)
        {
          try
          {
            connection = connectionFactory.getAsynchronousConnection(
                null).get();
          }
          catch (InterruptedException e)
          {
            // Ignore and check stop requested
            continue;
          }
          catch (ErrorResultException e)
          {
            app.println(LocalizableMessage.raw(e.getResult()
                .getDiagnosticMessage()));
            if (e.getCause() != null && app.isVerbose())
            {
              e.getCause().printStackTrace(app.getErrorStream());
            }
            stopRequested = true;
            break;
          }
        }
        else
        {
          connection = this.connection;
          if (!noRebind
              && connection instanceof AuthenticatedAsynchronousConnection)
          {
            AuthenticatedAsynchronousConnection ac = (AuthenticatedAsynchronousConnection) connection;
            try
            {
              ac.rebind(null).get();
            }
            catch (InterruptedException e)
            {
              // Ignore and check stop requested
              continue;
            }
            catch (ErrorResultException e)
            {
              app.println(LocalizableMessage.raw(e.getResult().toString()));
              if (e.getCause() != null && app.isVerbose())
              {
                e.getCause().printStackTrace(app.getErrorStream());
              }
              stopRequested = true;
              break;
            }
          }
        }
        future = performOperation(connection, handler, dataSources
            .get());
        operationRecentCount.getAndIncrement();
        count++;
        if (!isAsync)
        {
          try
          {
            future.get();
          }
          catch (InterruptedException e)
          {
            // Ignore and check stop requested
            continue;
          }
          catch (ErrorResultException e)
          {
            if (e.getCause() instanceof IOException)
            {
              e.getCause().printStackTrace(app.getErrorStream());
              stopRequested = true;
              break;
            }
            // Ignore. Handled by result handler
          }
          if (this.connection == null)
          {
            connection.close();
          }
        }
        if (targetThroughput > 0)
        {
          try
          {
            if (sleepTimeInMS > 1)
            {
              sleep((long) Math.floor(sleepTimeInMS));
            }
          }
          catch (InterruptedException e)
          {
            continue;
          }

          sleepTimeInMS += targetTimeInMS
              - ((System.nanoTime() - start) / 1000000.0);
          if (sleepTimeInMS < -60000)
          {
            // If we fall behind by 60 seconds, just forget about
            // catching up
            sleepTimeInMS = -60000;
          }
        }
      }
    }
  }



  class StatsThread extends Thread
  {
    protected final String[] EMPTY_STRINGS = new String[0];

    private final MultiColumnPrinter printer;

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

    protected int searchCount;

    protected int failedCount;

    protected long waitTime;

    protected long lastStatTime;

    protected long lastGCDuration;

    protected double recentDuration;

    protected double averageDuration;



    public StatsThread(String[] additionalColumns)
    {
      super("Stats Thread");
      TreeSet<Double> pSet = new TreeSet<Double>();
      if (!percentilesArgument.isPresent())
      {
        pSet.add(.1);
        pSet.add(.01);
        pSet.add(.001);
      }
      else
      {
        for (String percentile : percentilesArgument.getValues())
        {
          pSet.add(100.0 - Double.parseDouble(percentile));
        }
      }
      this.percentiles = pSet.descendingSet();
      numColumns = 5 + this.percentiles.size()
          + additionalColumns.length + (isAsync ? 1 : 0);
      printer = new MultiColumnPrinter(numColumns, 2, "-",
          MultiColumnPrinter.RIGHT, app);
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
      for (Double percentile : this.percentiles)
      {
        title[i++] = Double.toString(100.0 - percentile) + "%";
      }
      title[i++] = "err/sec";
      if (isAsync)
      {
        title[i++] = "req/res";
      }
      for (String column : additionalColumns)
      {
        title[i++] = column;
      }
      span = new int[numColumns];
      Arrays.fill(span, 1);
      printer.addTitle(title, span);
      beans = ManagementFactory.getGarbageCollectorMXBeans();
    }



    String[] getAdditionalColumns()
    {
      return EMPTY_STRINGS;
    }



    @Override
    public void run()
    {
      printer.printTitle();

      String[] strings = new String[numColumns];

      long startTime = System.currentTimeMillis();
      long statTime = startTime;
      long gcDuration = 0;
      for (GarbageCollectorMXBean bean : beans)
      {
        gcDuration += bean.getCollectionTime();
      }
      while (!stopRequested)
      {
        try
        {
          sleep(statsInterval);
        }
        catch (InterruptedException ie)
        {
          // Ignore.
        }

        lastStatTime = statTime;
        statTime = System.currentTimeMillis();

        lastGCDuration = gcDuration;
        gcDuration = 0;
        for (GarbageCollectorMXBean bean : beans)
        {
          gcDuration += bean.getCollectionTime();
        }

        successCount = successRecentCount.getAndSet(0);
        searchCount = operationRecentCount.getAndSet(0);
        failedCount = failedRecentCount.getAndSet(0);
        waitTime = waitRecentTime.getAndSet(0);
        totalSuccessCount += successCount;
        totalOperationCount += searchCount;
        totalFailedCount += failedCount;
        totalWaitTime += waitTime;
        recentDuration = statTime - lastStatTime;
        averageDuration = statTime - startTime;
        recentDuration -= gcDuration - lastGCDuration;
        averageDuration -= gcDuration;
        recentDuration /= 1000.0;
        averageDuration /= 1000.0;
        strings[0] = String.format("%.1f", successCount
            / recentDuration);
        strings[1] = String.format("%.1f", totalSuccessCount
            / averageDuration);
        strings[2] = String.format("%.3f",
            (waitTime - (gcDuration - lastGCDuration)) / successCount
                / 1000000.0);
        strings[3] = String.format("%.3f", (totalWaitTime - gcDuration)
            / totalSuccessCount / 1000000.0);

        boolean changed = false;
        etimes = eTimeBuffer.getAndSet(etimes);
        int appendLength = Math.min(array.remaining(), etimes.size());
        if (appendLength > 0)
        {
          array.append(etimes, appendLength);
          for (int i = array.size - appendLength; i < array.size; i++)
          {
            array.siftUp(0, i);
          }
          changed = true;
        }

        // Our window buffer is now full. Replace smallest with anything
        // larger
        // and re-heapify
        for (int i = appendLength; i < etimes.size(); i++)
        {
          if (etimes.get(i) > array.get(0))
          {
            array.set(0, etimes.get(i));
            array.siftDown(0, array.size() - 1);
            changed = true;
          }
        }
        etimes.clear();

        if (changed)
        {
          // Perform heapsort
          int i = array.size() - 1;
          while (i > 0)
          {
            array.swap(i, 0);
            array.siftDown(0, i - 1);
            i--;
          }
          array.reverse();
        }

        // Now everything is ordered from smallest to largest
        int index;
        int i = 4;
        for (Double percent : percentiles)
        {
          index = array.size()
              - (int) Math.floor((percent / 100.0) * totalSuccessCount)
              - 1;
          if (index < 0)
          {
            strings[i++] = String.format("*%.3f",
                array.get(0) / 1000000.0);
          }
          else
          {
            strings[i++] = String.format("%.3f",
                array.get(index) / 1000000.0);
          }
        }
        strings[i++] = String.format("%.1f", totalFailedCount
            / averageDuration);
        if (isAsync)
        {
          strings[i++] = String.format("%.1f", (double) searchCount
              / successCount);
        }
        for (String column : getAdditionalColumns())
        {
          strings[i++] = column;
        }
        printer.printRow(strings);
      }
    }
  }



  private static class ReversableArray
  {
    private final long[] array;

    private boolean reversed;

    private int size;



    public ReversableArray(int capacity)
    {
      this.array = new long[capacity];
    }



    public void set(int index, long value)
    {
      if (index >= size)
      {
        throw new IndexOutOfBoundsException();
      }
      if (!reversed)
      {
        array[index] = value;
      }
      else
      {
        array[size - index - 1] = value;
      }
    }



    public long get(int index)
    {
      if (index >= size)
      {
        throw new IndexOutOfBoundsException();
      }
      if (!reversed)
      {
        return array[index];
      }
      else
      {
        return array[size - index - 1];
      }
    }



    public int size()
    {
      return size;
    }



    public void reverse()
    {
      reversed = !reversed;
    }



    public void append(long value)
    {
      if (size == array.length)
      {
        throw new IndexOutOfBoundsException();
      }

      if (!reversed)
      {
        array[size] = value;
      }
      else
      {
        System.arraycopy(array, 0, array, 1, size);
        array[0] = value;
      }
      size++;
    }



    public void append(ReversableArray a, int length)
    {
      if (length > a.size() || length > remaining())
      {
        throw new IndexOutOfBoundsException();
      }
      if (!reversed)
      {
        System.arraycopy(a.array, 0, array, size, length);
      }
      else
      {
        System.arraycopy(array, 0, array, length, size);
        System.arraycopy(a.array, 0, array, 0, length);
      }
      size += length;
    }



    public int remaining()
    {
      return array.length - size;
    }



    public void clear()
    {
      size = 0;
    }



    public void siftDown(int start, int end)
    {
      int root = start;
      int child;
      while (root * 2 + 1 <= end)
      {
        child = root * 2 + 1;
        if (child + 1 <= end && get(child) > get(child + 1))
        {
          child = child + 1;
        }
        if (get(root) > get(child))
        {
          swap(root, child);
          root = child;
        }
        else
        {
          return;
        }
      }
    }



    public void siftUp(int start, int end)
    {
      int child = end;
      int parent;
      while (child > start)
      {
        parent = (int) Math.floor((child - 1) / 2);
        if (get(parent) > get(child))
        {
          swap(parent, child);
          child = parent;
        }
        else
        {
          return;
        }
      }
    }



    private void swap(int i, int i2)
    {
      long temp = get(i);
      set(i, get(i2));
      set(i2, temp);
    }
  }
}
