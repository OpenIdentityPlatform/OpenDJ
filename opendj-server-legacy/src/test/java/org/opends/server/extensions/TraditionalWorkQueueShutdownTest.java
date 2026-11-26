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
package org.opends.server.extensions;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.TraditionalWorkQueueCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;
import org.opends.server.util.TestTimer;
import org.testng.annotations.Test;

/** Verifies that shutting down a traditional work queue wakes idle workers and accounts for pending work. */
public class TraditionalWorkQueueShutdownTest extends DirectoryServerTestCase
{
  private static final LocalizableMessage SHUTDOWN_REASON = LocalizableMessage.raw("Test work queue shutdown");

  @Test
  public void wakesAnIdleWorkerBeforeThePollExpires() throws Exception
  {
    TestWorkQueue queue = new TestWorkQueue();
    queue.initializeWorkQueue(config(10));
    try
    {
      waitForIdleWorker(queue);
    }
    finally
    {
      long start = System.nanoTime();
      queue.finalizeWorkQueue(SHUTDOWN_REASON);
      assertTrue(System.nanoTime() - start < SECONDS.toNanos(3),
          "Shutdown waited for the idle worker's five-second poll");
    }
    queue.worker.join(SECONDS.toMillis(3));
    assertFalse(queue.worker.isAlive(), "The idle worker did not exit");
    assertTrue(queue.isIdle());
  }

  @Test
  public void accountsForDrainedOperationsWithoutInterruptingAnActiveWorker() throws Exception
  {
    CountDownLatch running = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    TestWorkQueue queue = new TestWorkQueue();
    queue.initializeWorkQueue(config(10));
    Operation active = mock(Operation.class);
    doAnswer(invocation -> {
      running.countDown();
      try
      {
        finish.await();
      }
      catch (InterruptedException e)
      {
        interrupted.countDown();
        Thread.currentThread().interrupt();
      }
      return null;
    }).when(active).run();
    Operation pending = mock(Operation.class);
    try
    {
      queue.submitOperation(active);
      assertTrue(running.await(5, SECONDS), "Worker did not begin the active operation");
      queue.submitOperation(pending);
      Operation anotherPending = mock(Operation.class);
      queue.submitOperation(anotherPending);
      queue.applyConfigurationChange(config(1));
      assertFalse(queue.trySubmitOperation(mock(Operation.class)), "The smaller queue is full");
      queue.finalizeWorkQueue(SHUTDOWN_REASON);
      verify(active).cancel(any(CancelRequest.class));
      verify(anotherPending).abort(any(CancelRequest.class));
      verify(anotherPending, never()).run();
      verify(pending).abort(any(CancelRequest.class));
      verify(pending, never()).run();
      assertFalse(queue.isIdle(), "The active operation is still pending");
    }
    finally
    {
      finish.countDown();
      if (!queue.shutdownRequested())
      {
        queue.finalizeWorkQueue(SHUTDOWN_REASON);
      }
    }
    assertTrue(queue.waitUntilIdle(1000), "The drained operation still counts as pending");
    assertEquals(interrupted.getCount(), 1L, "Shutdown interrupted a worker processing an operation");
  }

  @Test
  public void stoppingAdmissionWakesAllBlockedSubmittersBeforeFinalization() throws Exception
  {
    CountDownLatch running = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    TestWorkQueue queue = new TestWorkQueue();
    queue.initializeWorkQueue(config(1));
    Operation active = mock(Operation.class);
    doAnswer(invocation -> {
      running.countDown();
      finish.await();
      return null;
    }).when(active).run();
    Operation queued = mock(Operation.class);
    try
    {
      queue.submitOperation(active);
      assertTrue(running.await(5, SECONDS), "Worker did not begin the active operation");
      queue.submitOperation(queued);
      FutureTask<ResultCode> first = startBlockedSubmitter(queue, "First submitter");
      FutureTask<ResultCode> second = startBlockedSubmitter(queue, "Second submitter");

      queue.stopAcceptingOperations();

      assertEquals(first.get(5, SECONDS), ResultCode.UNAVAILABLE, "First submitter was not rejected");
      assertEquals(second.get(5, SECONDS), ResultCode.UNAVAILABLE, "Second submitter was not rejected");
      DirectoryException rejection = expectThrows(DirectoryException.class,
          () -> queue.trySubmitOperation(mock(Operation.class)));
      assertEquals(rejection.getResultCode(), ResultCode.UNAVAILABLE);
      assertFalse(queue.shutdownRequested(), "Stopping admission stopped the worker pool");
      assertEquals(queue.size(), 1, "Stopping admission drained the queue");
      verify(active, never()).cancel(any(CancelRequest.class));
      verify(active, never()).operationCompleted();
      verify(queued, never()).abort(any(CancelRequest.class));
    }
    finally
    {
      try
      {
        queue.finalizeWorkQueue(SHUTDOWN_REASON);
      }
      finally
      {
        finish.countDown();
      }
    }
    verify(active).cancel(any(CancelRequest.class));
    verify(queued).abort(any(CancelRequest.class));
    assertTrue(queue.waitUntilIdle(1000), "Rejected submissions or canceled operations still count as pending");
  }

  @Test
  public void abortsADequeuedOperationWhenShutdownWinsTheHandoff() throws Exception
  {
    CountDownLatch dequeued = new CountDownLatch(1);
    CountDownLatch handoff = new CountDownLatch(1);
    TestWorkQueue queue = new TestWorkQueue()
    {
      @Override
      public Operation nextOperation(TraditionalWorkerThread worker)
      {
        Operation operation = super.nextOperation(worker);
        if (operation != null)
        {
          dequeued.countDown();
          try
          {
            assertTrue(handoff.await(5, SECONDS), "Timed out waiting for handoff to resume");
          }
          catch (InterruptedException e)
          {
            throw new AssertionError("Shutdown interrupted the handoff", e);
          }
        }
        return operation;
      }
    };
    queue.initializeWorkQueue(config(10));
    Operation pending = mock(Operation.class);
    try
    {
      queue.submitOperation(pending);
      assertTrue(dequeued.await(5, SECONDS), "Worker did not dequeue the operation");
      queue.finalizeWorkQueue(SHUTDOWN_REASON);
      assertFalse(queue.isIdle(), "The dequeued operation is still in the handoff");
    }
    finally
    {
      handoff.countDown();
      if (!queue.shutdownRequested())
      {
        queue.finalizeWorkQueue(SHUTDOWN_REASON);
      }
    }
    assertTrue(queue.waitUntilIdle(1000), "The handoff operation was not aborted");
    verify(pending).abort(any(CancelRequest.class));
    verify(pending, never()).run();
  }

  private TraditionalWorkQueueCfg config(int capacity)
  {
    TraditionalWorkQueueCfg config = mock(TraditionalWorkQueueCfg.class);
    when(config.getNumWorkerThreads()).thenReturn(1);
    when(config.getMaxWorkQueueCapacity()).thenReturn(capacity);
    return config;
  }

  private void waitForIdleWorker(TestWorkQueue queue) throws Exception
  {
    new TestTimer.Builder().maxSleep(5, SECONDS).sleepTimes(10, MILLISECONDS).toTimer().repeatUntilSuccess(() -> {
      Thread worker = queue.worker;
      assertNotNull(worker, "Worker has not started");
      assertWaitingInQueue(worker, "retryNextOperation");
    });
  }

  private FutureTask<ResultCode> startBlockedSubmitter(TestWorkQueue queue, String name) throws Exception
  {
    Operation operation = mock(Operation.class);
    FutureTask<ResultCode> submission = new FutureTask<>(() ->
        expectThrows(DirectoryException.class, () -> queue.submitOperation(operation)).getResultCode());
    Thread thread = new Thread(submission, name);
    thread.setDaemon(true);
    thread.start();
    new TestTimer.Builder().maxSleep(5, SECONDS).sleepTimes(10, MILLISECONDS).toTimer().repeatUntilSuccess(
        () -> assertWaitingInQueue(thread, "submitOperation"));
    return submission;
  }

  private void assertWaitingInQueue(Thread thread, String methodName)
  {
    assertEquals(thread.getState(), Thread.State.WAITING, thread.getName() + " has not entered the condition wait");
    StackTraceElement[] stack = thread.getStackTrace();
    for (int i = 1; i < stack.length; i++)
    {
      StackTraceElement frame = stack[i];
      if (frame.getClassName().equals(TraditionalWorkQueue.class.getName())
          && frame.getMethodName().equals(methodName)
          && stack[i - 1].getMethodName().equals("await"))
      {
        return;
      }
    }
    fail(thread.getName() + " has not reached the queue wait");
  }

  private static class TestWorkQueue extends TraditionalWorkQueue
  {
    private volatile TraditionalWorkerThread worker;

    @Override
    void registerQueueMonitor()
    {
      // This queue is local to the test and has no server monitor.
    }

    @Override
    public Operation nextOperation(TraditionalWorkerThread workerThread)
    {
      worker = workerThread;
      return super.nextOperation(workerThread);
    }
  }
}
