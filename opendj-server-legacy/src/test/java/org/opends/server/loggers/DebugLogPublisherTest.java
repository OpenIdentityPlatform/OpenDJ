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
package org.opends.server.loggers;

import static org.testng.Assert.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.DebugLogPublisherCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.core.ServerContext;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DebugLogPublisherTest extends DirectoryServerTestCase
{
  /** Minimal publisher exposing nothing but the trace settings bookkeeping under test. */
  private static final class TestDebugLogPublisher extends DebugLogPublisher<DebugLogPublisherCfg>
  {
    @Override
    public void initializeLogPublisher(DebugLogPublisherCfg config, ServerContext serverContext)
    {
      // Nothing to initialize.
    }

    @Override
    public void trace(TraceSettings settings, String signature, String sourceLocation, String msg,
        StackTraceElement[] stackTrace)
    {
      // Not used by these tests.
    }

    @Override
    public void traceException(TraceSettings settings, String signature, String sourceLocation, String msg,
        Throwable ex, StackTraceElement[] stackTrace)
    {
      // Not used by these tests.
    }

    @Override
    public DN getDN()
    {
      return null;
    }

    @Override
    public void close()
    {
      // Nothing to close.
    }
  }

  @Test
  public void testFreshPublisherTracesNothing() throws Exception
  {
    DebugLogPublisher<?> publisher = new TestDebugLogPublisher();

    assertSame(publisher.getClassSettings("com.example.Foo"), TraceSettings.DISABLED);
    assertNull(publisher.getMethodSettings("com.example.Foo"));
    assertFalse(publisher.hasTraceSettings("com.example.Foo"));
    assertFalse(publisher.hasTraceSettings("com.example.Foo#bar"));
  }

  @Test
  public void testGlobalSettingsOnlyApplyWhileNoTargetIsDefined() throws Exception
  {
    DebugLogPublisher<?> publisher = new TestDebugLogPublisher();
    TraceSettings global = new TraceSettings();
    publisher.addTraceSettings(null, global);

    assertSame(publisher.getClassSettings("com.example.Foo"), global);

    publisher.addTraceSettings("com.example", new TraceSettings());

    assertSame(publisher.getClassSettings("org.other.Bar"), TraceSettings.DISABLED);
    assertSame(publisher.removeTraceSettings(null), global);
  }

  @Test
  public void testMostSpecificClassScopeWins() throws Exception
  {
    DebugLogPublisher<?> publisher = new TestDebugLogPublisher();
    TraceSettings packageSettings = new TraceSettings();
    TraceSettings classSettings = new TraceSettings();
    publisher.addTraceSettings("com.example", packageSettings);
    publisher.addTraceSettings("com.example.Foo", classSettings);

    assertSame(publisher.getClassSettings("com.example.Foo"), classSettings);
    assertSame(publisher.getClassSettings("com.example.Foo$Inner"), classSettings);
    assertSame(publisher.getClassSettings("com.example.Bar"), packageSettings);

    assertSame(publisher.removeTraceSettings("com.example.Foo"), classSettings);
    assertSame(publisher.getClassSettings("com.example.Foo"), packageSettings);
    assertNull(publisher.removeTraceSettings("no.such.Scope"));
  }

  @Test
  public void testMethodSettings() throws Exception
  {
    DebugLogPublisher<?> publisher = new TestDebugLogPublisher();
    TraceSettings barSettings = new TraceSettings();
    TraceSettings bazSettings = new TraceSettings();
    publisher.addTraceSettings("com.example.Foo#bar", barSettings);
    publisher.addTraceSettings("com.example.Foo#baz", bazSettings);

    Map<String, TraceSettings> methodSettings = publisher.getMethodSettings("com.example.Foo");
    assertNotNull(methodSettings);
    assertEquals(methodSettings.size(), 2);
    assertSame(methodSettings.get("bar"), barSettings);
    assertSame(methodSettings.get("baz"), bazSettings);
    assertTrue(publisher.hasTraceSettings("com.example.Foo#bar"));
    assertFalse(publisher.hasTraceSettings("com.example.Foo#unknown"));
    assertNull(publisher.removeTraceSettings("com.example.Foo#unknown"));
    assertNull(publisher.removeTraceSettings("no.such.Class#bar"));
  }

  @Test
  public void testRemovingLastMethodSettingsDiscardsTheEnclosingMap() throws Exception
  {
    DebugLogPublisher<?> publisher = new TestDebugLogPublisher();
    TraceSettings barSettings = new TraceSettings();
    TraceSettings bazSettings = new TraceSettings();
    publisher.addTraceSettings("com.example.Foo#bar", barSettings);
    publisher.addTraceSettings("com.example.Foo#baz", bazSettings);

    assertSame(publisher.removeTraceSettings("com.example.Foo#bar"), barSettings);
    assertNotNull(publisher.getMethodSettings("com.example.Foo"));

    assertSame(publisher.removeTraceSettings("com.example.Foo#baz"), bazSettings);
    assertNull(publisher.getMethodSettings("com.example.Foo"));
  }

  /**
   * The trace settings are read without any lock, so adding and removing method settings must be
   * atomic with respect to each other: dropping the map holding the settings of a class must not
   * discard a setting added concurrently for another method of the same class.
   */
  @Test
  public void testConcurrentUpdatesDoNotLoseMethodSettings() throws Exception
  {
    final DebugLogPublisher<?> publisher = new TestDebugLogPublisher();
    final int writerCount = 4;
    final int readerCount = 2;
    final int rounds = 5000;
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch finished = new CountDownLatch(writerCount + readerCount);
    final AtomicReference<Throwable> failure = new AtomicReference<>();

    for (int i = 0; i < writerCount; i++)
    {
      final String methodName = "method" + i;
      new Thread(() -> {
        try
        {
          start.await();
          for (int round = 0; round < rounds; round++)
          {
            TraceSettings settings = new TraceSettings();
            publisher.addTraceSettings("com.example.Hot#" + methodName, settings);
            Map<String, TraceSettings> methodSettings = publisher.getMethodSettings("com.example.Hot");
            assertNotNull(methodSettings, "the map holding " + methodName + " was discarded");
            assertSame(methodSettings.get(methodName), settings, "the settings of " + methodName + " were lost");
            publisher.removeTraceSettings("com.example.Hot#" + methodName);
          }
        }
        catch (Throwable t)
        {
          failure.compareAndSet(null, t);
        }
        finally
        {
          finished.countDown();
        }
      }).start();
    }

    for (int i = 0; i < readerCount; i++)
    {
      new Thread(() -> {
        try
        {
          start.await();
          for (int round = 0; round < rounds; round++)
          {
            publisher.getClassSettings("com.example.Hot$Inner");
            Map<String, TraceSettings> methodSettings = publisher.getMethodSettings("com.example.Hot");
            if (methodSettings != null)
            {
              for (TraceSettings settings : methodSettings.values())
              {
                assertNotNull(settings.getLevel());
              }
            }
            publisher.hasTraceSettings("com.example.Hot#method0");
          }
        }
        catch (Throwable t)
        {
          failure.compareAndSet(null, t);
        }
        finally
        {
          finished.countDown();
        }
      }).start();
    }

    start.countDown();
    finished.await();
    if (failure.get() != null)
    {
      throw new AssertionError("concurrent access to the trace settings failed", failure.get());
    }
    assertNull(publisher.getMethodSettings("com.example.Hot"));
  }
}
