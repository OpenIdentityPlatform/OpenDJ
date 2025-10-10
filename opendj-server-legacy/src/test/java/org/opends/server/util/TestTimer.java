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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.util;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Reject;
import org.opends.server.TestCaseUtils;

/**
 * Timer useful for testing: it helps to write loops that repeatedly runs code until some condition
 * is met.
 */
public interface TestTimer
{
  /**
   * Equivalent to {@code Callable<Void>} or a {@code Runnable} that can throw exception.
   * <p>
   * <b>Note:</b> The name has been designed to stay close to {@code Callable<Void>} to allow to
   * easily change test code back and forth between {@code java.util.Callable} and
   * {@code CallableVoid} while writing the test code. It also avoids name collisions while writing
   * the rest of the OpenDJ code base.
   */
  public static interface CallableVoid
  {
    /**
     * Equivalent of {@link Runnable#run()} or {@link Callable#call()}.
     *
     * @throws Exception
     *           if an error occurred
     * @see {@link Runnable#run()}
     * @see {@link Callable#call()}
     */
    void call() throws Exception;
  }

  /**
   * Repeatedly call the supplied callable (respecting a sleep interval) until:
   * <ul>
   * <li>it returns,</li>
   * <li>it throws an exception other than {@link AssertionError},</li>
   * <li>the current timer times out.</li>
   * </ul>
   * If the current timer times out, then it will:
   * <ul>
   * <li>either rethrow an {@link AssertionError} thrown by the callable,</li>
   * <li>or return {@code null}.</li>
   * </ul>
   * <p>
   * Note: The test code in the callable can be written as any test code outside a callable. In
   * particular, asserts can and should be used inside the {@link Callable#call()} method.
   *
   * @param callable
   *          the callable to repeat until success
   * @param <R>
   *          The return type of the callable
   * @return the value returned by the callable (may be {@code null}), or {@code null} if the timer
   *         times out
   * @throws Exception
   *           The exception thrown by the provided callable
   * @throws InterruptedException
   *           If the thread is interrupted while sleeping
   */
  <R> R repeatUntilSuccess(Callable<R> callable) throws Exception, InterruptedException;

  /**
   * Repeatedly call the supplied callable (respecting a sleep interval) until:
   * <ul>
   * <li>it returns,</li>
   * <li>it throws an exception other than {@link AssertionError},</li>
   * <li>the current timer times out.</li>
   * </ul>
   * If the current timer times out, then it will:
   * <ul>
   * <li>either rethrow an {@link AssertionError} thrown by the callable,</li>
   * <li>or return {@code null}.</li>
   * </ul>
   * <p>
   * Note: The test code in the callable can be written as any test code outside a callable. In
   * particular, asserts can and should be used inside the {@link TestTimer.Callable#call()} method.
   *
   * @param callable
   *          the callable to repeat until success
   * @throws Exception
   *           The exception thrown by the provided callable
   * @throws InterruptedException
   *           If the thread is interrupted while sleeping
   */
  void repeatUntilSuccess(CallableVoid callable) throws Exception, InterruptedException;

  /** Builder for a {@link TestTimer}. */
  public static final class Builder
  {
    private long maxSleepTimeInMillis;
    private long sleepTimes;

    /**
     * Configures the maximum sleep duration.
     *
     * @param time
     *          the duration
     * @param unit
     *          the time unit for the duration
     * @return this builder
     */
    public Builder maxSleep(long time, TimeUnit unit)
    {
      Reject.ifFalse(time > 0, "time must be positive");
      this.maxSleepTimeInMillis = unit.toMillis(time);
      return this;
    }

    /**
     * Configures the duration for sleep times.
     *
     * @param time
     *          the duration
     * @param unit
     *          the time unit for the duration
     * @return this builder
     */
    public Builder sleepTimes(long time, TimeUnit unit)
    {
      Reject.ifFalse(time > 0, "time must be positive");
      this.sleepTimes = unit.toMillis(time);
      return this;
    }

    /**
     * Creates a new timer and start it.
     *
     * @return a new timer
     */
    public TestTimer toTimer()
    {
      return new SteppingTimer(this);
    }
  }

  /** A {@link TestTimer} that sleeps in steps and sleeps at maximum {@code nbSteps * sleepTimes}. */
  public static class SteppingTimer implements TestTimer
  {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    private final long sleepTime;
    private final long totalNbSteps;
    private long nbStepsRemaining;

    private SteppingTimer(Builder builder)
    {
      this.sleepTime = builder.sleepTimes;
      this.totalNbSteps = sleepTime > 0 ? builder.maxSleepTimeInMillis / sleepTime : 0;
      this.nbStepsRemaining = totalNbSteps;
    }

    /**
     * Returns whether the timer has reached the timeout. This method may block by sleeping.
     *
     * @return {@code true} if the timer has reached the timeout, {@code false} otherwise
     * @throws InterruptedException if the thread has been interrupted
     */
    private boolean hasTimedOut() throws InterruptedException
    {
      final boolean done = hasTimedOutNoSleep();
      if (!done)
      {
        Thread.sleep(sleepTime);
        nbStepsRemaining--;
      }
      return done;
    }

    /**
     * Returns whether the timer has reached the timeout, without sleep.
     *
     * @return {@code true} if the timer has reached the timeout, {@code false} otherwise
     */
    private boolean hasTimedOutNoSleep()
    {
      return nbStepsRemaining <= 0;
    }

    @Override
    public <R> R repeatUntilSuccess(Callable<R> callable) throws Exception, InterruptedException
    {
      do
      {
        try
        {
          return callable.call();
        }
        catch (AssertionError e)
        {
          if (hasTimedOutNoSleep())
          {
            logger.info(LocalizableMessage.raw("failed to wait for" + callable + "\n" + TestCaseUtils.generateThreadDump()));
            throw e;
          }
        }
      }
      while (!hasTimedOut());
      return null;
    }

    @Override
    public void repeatUntilSuccess(final CallableVoid callable) throws Exception, InterruptedException
    {
      repeatUntilSuccess(new Callable<Void>()
      {
        @Override
        public Void call() throws Exception
        {
          callable.call();
          return null;
        }
      });
    }

    @Override
    public String toString()
    {
      return totalNbSteps * sleepTime + " ms max sleep time"
          + " (" + totalNbSteps + " steps x " + sleepTime + " ms)"
          + ", remaining = " + nbStepsRemaining + " steps";
    }
  }
}
