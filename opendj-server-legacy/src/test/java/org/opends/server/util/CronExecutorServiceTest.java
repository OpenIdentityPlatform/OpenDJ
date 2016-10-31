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

import static java.util.concurrent.TimeUnit.*;

import static org.mockito.Mockito.*;

import java.util.concurrent.Callable;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.util.TestTimer.CallableVoid;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = "precommit", sequential = true)
public class CronExecutorServiceTest extends DirectoryServerTestCase
{
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @Test
  public void execute() throws Exception
  {
    final Runnable mock = mock(Runnable.class);
    new CronExecutorService().execute(mock);

    verifyRunnableInvokedOnce(mock);
  }

  @Test
  public void submitRunnable() throws Exception
  {
    final Runnable mock = mock(Runnable.class);
    new CronExecutorService().submit(mock);

    verifyRunnableInvokedOnce(mock);
  }

  @Test
  public void submitRunnableAndReturn() throws Exception
  {
    final Runnable mock = mock(Runnable.class);
    new CronExecutorService().submit(mock, null);

    verifyRunnableInvokedOnce(mock);
  }

  @Test
  public void submitCallable() throws Exception
  {
    final Callable<Void> mock = mock(Callable.class);
    new CronExecutorService().submit(mock);

    verifyCallableInvokedOnce(mock);
  }

  @Test
  public void scheduleRunnable() throws Exception
  {
    final Runnable mock = mock(Runnable.class);
    new CronExecutorService().schedule(mock, 200, MILLISECONDS);

    verifyNoMoreInteractions(mock);

    Thread.sleep(SECONDS.toMillis(1));

    verifyRunnableInvokedOnce(mock);
  }

  @Test
  public void scheduleCallable() throws Exception
  {
    final Callable<Void> mock = mock(Callable.class);
    new CronExecutorService().schedule(mock, 200, MILLISECONDS);

    verifyNoMoreInteractions(mock);

    Thread.sleep(SECONDS.toMillis(1));

    maxOneSecond().repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        verify(mock, atLeastOnce()).call();
        verifyNoMoreInteractions(mock);
      }
    });
  }

  @Test
  public void scheduleAtFixedRate() throws Exception
  {
    final Runnable mock = mock(Runnable.class);
    new CronExecutorService().scheduleAtFixedRate(mock, 0 /* execute immediately */, 200, MILLISECONDS);

    verifyNoMoreInteractions(mock);

    Thread.sleep(MILLISECONDS.toMillis(200));

    maxOneSecond().repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        verify(mock, atLeastOnce()).run();
        verifyNoMoreInteractions(mock);
      }
    });
  }

  private void verifyRunnableInvokedOnce(final Runnable mock) throws Exception, InterruptedException
  {
    maxOneSecond().repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        verify(mock).run();
        verifyNoMoreInteractions(mock);
      }
    });
  }

  private void verifyCallableInvokedOnce(final Callable<Void> mock) throws Exception, InterruptedException
  {
    maxOneSecond().repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        verify(mock).call();
        verifyNoMoreInteractions(mock);
      }
    });
  }

  private TestTimer maxOneSecond()
  {
    return new TestTimer.Builder()
        .maxSleep(1, SECONDS)
        .sleepTimes(10, MILLISECONDS)
        .toTimer();
  }
}
