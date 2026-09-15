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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.util;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/** Tests for {@link FailureLogThrottle}. */
@SuppressWarnings("javadoc")
public class FailureLogThrottleTest extends DirectoryServerTestCase
{
  private static final long INTERVAL_NANOS = MINUTES.toNanos(5);

  private static FailureLogThrottle newThrottle()
  {
    return new FailureLogThrottle(5, MINUTES);
  }

  @Test
  public void theFirstFailureIsLoggedHoweverLongTheThrottleHasExisted() throws Exception
  {
    assertThat(newThrottle().record(System.nanoTime()))
        .as("the first failure is logged, and stands for itself alone").isEqualTo(0);
  }

  @Test
  public void failuresInsideTheIntervalAreSuppressedAndCounted() throws Exception
  {
    final FailureLogThrottle throttle = newThrottle();
    final long start = System.nanoTime();

    throttle.record(start);
    assertThat(throttle.record(start + 1))
        .as("the next failure is the first suppressed one").isEqualTo(-2);
    assertThat(throttle.record(start + INTERVAL_NANOS - 1))
        .as("a failure one nanosecond before the interval is up is the second").isEqualTo(-3);
  }

  @Test
  public void theNextFailureLoggedReportsTheSuppressedOnesBeforeIt() throws Exception
  {
    final FailureLogThrottle throttle = newThrottle();
    final long start = System.nanoTime();

    throttle.record(start);
    throttle.record(start + 1);
    throttle.record(start + 2);

    assertThat(throttle.record(start + INTERVAL_NANOS))
        .as("the failure ending the interval reports the two suppressed before it").isEqualTo(2);
    assertThat(throttle.record(start + 2 * INTERVAL_NANOS))
        .as("the count starts again from the failure last logged").isEqualTo(0);
  }

  @Test
  public void eachThrottleCountsOnItsOwn() throws Exception
  {
    final FailureLogThrottle throttle = newThrottle();
    final FailureLogThrottle otherThrottle = newThrottle();
    final long start = System.nanoTime();

    throttle.record(start);
    throttle.record(start + 1);

    assertThat(otherThrottle.record(start + 2))
        .as("a failure of another kind is logged with a count of its own").isEqualTo(0);
  }
}
