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
package org.opends.server.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.server.api.DirectoryThread;

/**
 * Implements a {@link ScheduledExecutorService} on top of a {@link Executors#newCachedThreadPool()
 * cached thread pool} to achieve UNIX's cron-like capabilities.
 * <p>
 * This executor service is implemented with two underlying executor services:
 * <ul>
 * <li>{@link Executors#newSingleThreadScheduledExecutor() a single-thread scheduled executor} which
 * allows to start tasks based on a schedule</li>
 * <li>{@link Executors#newCachedThreadPool() a cached thread pool} which allows to run several
 * tasks concurrently, adjusting the thread pool size when necessary. In particular, when the number
 * of tasks to run concurrently is bigger than the tread pool size, then new threads are spawned.
 * The thread pool is then shrinked when threads sit idle with no tasks to execute.</li>
 * </ul>
 * <p>
 * All the tasks submitted to the current class are 1. scheduled by the single-thread scheduled
 * executor and 2. finally executed by the cached thread pool.
 * <p>
 * Because of this setup, the assumptions of
 * {@link #scheduleWithFixedDelay(Runnable, long, long, TimeUnit)} cannot be fulfilled, so calling
 * this method will throw a {@link UnsupportedOperationException}.
 * <p>
 * &nbsp;
 * <p>
 * Current (Nov. 2016) OpenDJ threads that may be replaced by using the current class:
 * <ul>
 * <li>Idle Time Limit Thread</li>
 * <li>LDAP Connection Finalizer for connection handler Administration Connector</li>
 * <li>LDAP Connection Finalizer for connection handler LDAP Connection Handler</li>
 * <li>Monitor Provider State Updater</li>
 * <li>Task Scheduler Thread</li>
 * <li>Rejected: Time Thread - high resolution thread</li>
 * </ul>
 */
public class CronExecutorService implements ScheduledExecutorService
{
  /** Made necessary by the double executor services. */
  private static class FutureTaskResult<V> implements ScheduledFuture<V>
  {
    private volatile Future<V> executorFuture;
    private Future<?> schedulerFuture;

    public void setExecutorFuture(Future<V> executorFuture)
    {
      this.executorFuture = executorFuture;
    }

    public void setSchedulerFuture(Future<?> schedulerFuture)
    {
      this.schedulerFuture = schedulerFuture;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
      return schedulerFuture.cancel(mayInterruptIfRunning)
          | (executorFuture != null && executorFuture.cancel(mayInterruptIfRunning));
    }

    @Override
    public V get() throws InterruptedException, ExecutionException
    {
      schedulerFuture.get();
      return executorFuture.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
      schedulerFuture.get(timeout, unit);
      return executorFuture.get(timeout, unit);
    }

    @Override
    public boolean isCancelled()
    {
      return schedulerFuture.isCancelled() || executorFuture.isCancelled();
    }

    @Override
    public boolean isDone()
    {
      return schedulerFuture.isDone() && executorFuture.isDone();
    }

    @Override
    public long getDelay(TimeUnit unit)
    {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int compareTo(Delayed o)
    {
      throw new UnsupportedOperationException("Not implemented");
    }
  }

  private final ExecutorService cronExecutorService;
  private final ScheduledExecutorService cronScheduler;

  /** Default constructor. */
  public CronExecutorService()
  {
    cronExecutorService = Executors.newCachedThreadPool(new ThreadFactory()
    {
      private final List<WeakReference<Thread>> workerThreads = new ArrayList<>();

      @Override
      public Thread newThread(Runnable r)
      {
        final Thread t = new DirectoryThread(r, "Cron worker thread - waiting for number");
        t.setDaemon(true);
        final int index = addThreadAndReturnWorkerThreadNumber(t);
        t.setName("Cron worker thread " + index);
        return t;
      }

      private synchronized int addThreadAndReturnWorkerThreadNumber(Thread t)
      {
        for (final ListIterator<WeakReference<Thread>> it = workerThreads.listIterator(); it.hasNext();)
        {
          final WeakReference<Thread> threadRef = it.next();
          if (threadRef.get() == null)
          {
            // Free slot, let's use it
            final int result = it.previousIndex();
            it.set(new WeakReference<>(t));
            return result;
          }
        }
        final int result = workerThreads.size();
        workerThreads.add(new WeakReference<>(t));
        return result;
      }
    });
    cronScheduler = new ScheduledThreadPoolExecutor(1, new ThreadFactory()
    {
      @Override
      public Thread newThread(final Runnable r)
      {
        Thread t = new DirectoryThread(r, "Cron scheduler thread");
        t.setDaemon(true);
        return t;
      }
    });
  }

  @Override
  public void execute(final Runnable task)
  {
    /** Class specialized for this method. */
    class ExecuteRunnable implements Runnable
    {
      @Override
      public void run()
      {
        cronExecutorService.execute(task);
      }
    }
    cronScheduler.execute(new ExecuteRunnable());
  }

  @Override
  public Future<?> submit(final Runnable task)
  {
    return submit(task, null);
  }

  @Override
  public <T> Future<T> submit(final Runnable task, final T result)
  {
    final FutureTaskResult<T> futureResult = new FutureTaskResult<>();
    /** Class specialized for this method. */
    class SubmitRunnable implements Runnable
    {
      @Override
      public void run()
      {
        futureResult.setExecutorFuture(cronExecutorService.submit(task, result));
      }
    }
    futureResult.setSchedulerFuture(cronScheduler.submit(new SubmitRunnable(), null));
    return futureResult;
  }

  @Override
  public <T> Future<T> submit(final Callable<T> task)
  {
    final FutureTaskResult<T> futureResult = new FutureTaskResult<>();
    /** Class specialized for this method. */
    class SubmitCallableRunnable implements Runnable
    {
      @Override
      public void run()
      {
        futureResult.setExecutorFuture(cronExecutorService.submit(task));
      }
    }
    futureResult.setSchedulerFuture(cronScheduler.submit(new SubmitCallableRunnable()));
    return futureResult;
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(final Runnable task, final long initialDelay, final long period,
      final TimeUnit unit)
  {
    /** Class specialized for this method. */
    class NoConcurrentRunRunnable implements Runnable
    {
      private AtomicBoolean isRunning = new AtomicBoolean();

      @Override
      public void run()
      {
        if (isRunning.compareAndSet(false, true))
        {
          try
          {
            task.run();
          }
          finally
          {
            isRunning.set(false);
          }
        }
      }
    }

    final FutureTaskResult<?> futureResult = new FutureTaskResult<>();
    /** Class specialized for this method. */
    class ScheduleAtFixedRateRunnable implements Runnable
    {
      @SuppressWarnings({ "rawtypes", "unchecked" })
      @Override
      public void run()
      {
        futureResult.setExecutorFuture((Future) cronExecutorService.submit(new NoConcurrentRunRunnable()));
      }
    }
    futureResult.setSchedulerFuture(
        cronScheduler.scheduleAtFixedRate(new ScheduleAtFixedRateRunnable(), initialDelay, period, unit));
    return futureResult;
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable task, final long initialDelay, final long delay,
      final TimeUnit unit)
  {
    throw new UnsupportedOperationException("Cannot schedule with fixed delay between each runs of the task");
  }

  @Override
  public <V> ScheduledFuture<V> schedule(final Callable<V> task, final long delay, final TimeUnit unit)
  {
    final FutureTaskResult<V> futureResult = new FutureTaskResult<>();
    /** Class specialized for this method. */
    class ScheduleRunnable implements Runnable
    {
      @Override
      public void run()
      {
        futureResult.setExecutorFuture(cronExecutorService.submit(task));
      }
    }
    futureResult.setSchedulerFuture(cronScheduler.schedule(new ScheduleRunnable(), delay, unit));
    return futureResult;
  }

  @Override
  public ScheduledFuture<?> schedule(final Runnable task, final long delay, final TimeUnit unit)
  {
    final FutureTaskResult<?> futureResult = new FutureTaskResult<>();
    /** Class specialized for this method. */
    class ScheduleCallableRunnable implements Runnable
    {
      @SuppressWarnings({ "rawtypes", "unchecked" })
      @Override
      public void run()
      {
        futureResult.setExecutorFuture((Future) cronExecutorService.submit(task));
      }
    }
    futureResult.setSchedulerFuture(cronScheduler.schedule(new ScheduleCallableRunnable(), delay, unit));
    return futureResult;
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException
  {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException
  {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout,
      final TimeUnit unit) throws InterruptedException
  {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException
  {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException
  {
    return cronScheduler.awaitTermination(timeout, unit) & cronExecutorService.awaitTermination(timeout, unit);
  }

  @Override
  public void shutdown()
  {
    cronScheduler.shutdown();
    cronExecutorService.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow()
  {
    final List<Runnable> results = new ArrayList<>();
    results.addAll(cronScheduler.shutdownNow());
    results.addAll(cronExecutorService.shutdownNow());
    return results;
  }

  @Override
  public boolean isShutdown()
  {
    return cronScheduler.isShutdown() && cronExecutorService.isShutdown();
  }

  @Override
  public boolean isTerminated()
  {
    return cronScheduler.isTerminated() && cronExecutorService.isTerminated();
  }
}