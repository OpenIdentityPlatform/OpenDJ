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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.util;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.forgerock.util.Reject;

/**
 * Timer useful for testing: it helps to write loops that repeatedly runs code until some condition
 * is met.
 */
public interface TestTimer
{
  /**
   * Constant that can be used at the end of {@code Callable<Void>.call()} to better explicit this
   * is the end of the method.
   */
  Void END_RUN = null;

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
   * particular, asserts can and should be used inside the {@link Callable#call()}.
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
    private final long sleepTime;
    private final long totalNbSteps;
    private long nbStepsRemaining;
    private boolean started;

    private SteppingTimer(Builder builder)
    {
      this.sleepTime = builder.sleepTimes;
      this.totalNbSteps = sleepTime > 0 ? builder.maxSleepTimeInMillis / sleepTime : 0;
      this.nbStepsRemaining = totalNbSteps;
    }

    private SteppingTimer startTimer()
    {
      started = true;
      return this;
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
      Reject.ifTrue(!started, "start() method should have been called first");
      return nbStepsRemaining-- <= 0;
    }

    @Override
    public <R> R repeatUntilSuccess(Callable<R> callable) throws Exception, InterruptedException
    {
      startTimer();
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
            throw e;
          }
        }
      }
      while (!hasTimedOut());
      return null;
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
