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
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.loggers;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.opends.server.admin.std.server.DebugLogPublisherCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.util.ServerConstants;

/**
 * The debug log publisher implementation that writes debug messages in a
 * friendly for console output.
 */
public class ConsoleDebugLogPublisher extends
    DebugLogPublisher<DebugLogPublisherCfg>
{
  /**
   * The print stream where tracing will be sent.
   */
  private PrintStream err;

  /**
   * The format used for trace timestamps.
   */
  private DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

  /**
   * Constructs a new ConsoleDebugLogPublisher that writes debug messages
   * to the given PrintStream.
   * @param err The PrintStream to write messages to.
   */
  public ConsoleDebugLogPublisher(PrintStream err)
  {
    this.err = err;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeLogPublisher(DebugLogPublisherCfg config, ServerContext serverContext)
      throws ConfigException, InitializationException {
    // This publisher is not configurable.
  }

  /** {@inheritDoc} */
  @Override
  public void trace(TraceSettings settings,
                           String signature,
                           String sourceLocation,
                           String msg,
                           StackTraceElement[] stackTrace)
  {
    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace, settings.getStackDepth());
    }
    publish(msg, stack);
  }

  /** {@inheritDoc} */
  @Override
  public void traceException(TraceSettings settings,
                          String signature,
                          String sourceLocation,
                          String msg,
                          Throwable ex, StackTraceElement[] stackTrace)
  {
    String message = DebugMessageFormatter.format("%s caught={%s} %s(): %s",
        new Object[] { msg, ex, signature, sourceLocation });

    String stack = null;
    if (stackTrace != null)
    {
      stack =
          DebugStackTraceFormatter.formatStackTrace(ex, settings
              .getStackDepth(), settings.isIncludeCause());
    }
    publish(message, stack);
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    // Nothing to do.
  }


  /**
   * Publishes a record, optionally performing some "special" work:
   * - injecting a stack trace into the message
   */
  private void publish(String msg, String stack)
  {
    StringBuilder buf = new StringBuilder();
    // Emit the timestamp.
    buf.append(dateFormat.format(System.currentTimeMillis()));
    buf.append(" ");

    // Emit the debug level.
    buf.append("trace ");

    // Emit message.
    buf.append(msg);
    buf.append(ServerConstants.EOL);

    // Emit Stack Trace.
    if(stack != null)
    {
      buf.append("\nStack Trace:\n");
      buf.append(stack);
    }

    err.print(buf);
  }

  /** {@inheritDoc} */
  @Override
  public DN getDN()
  {
    // There is no configuration DN associated with this publisher.
    return DN.NULL_DN;
  }

}
