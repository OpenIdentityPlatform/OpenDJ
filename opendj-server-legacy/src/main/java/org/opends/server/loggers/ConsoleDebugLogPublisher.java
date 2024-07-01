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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.forgerock.opendj.server.config.server.DebugLogPublisherCfg;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.util.ServerConstants;

/**
 * The debug log publisher implementation that writes debug messages in a
 * friendly for console output.
 */
public class ConsoleDebugLogPublisher extends
    DebugLogPublisher<DebugLogPublisherCfg>
{
  /** The print stream where tracing will be sent. */
  private PrintStream err;

  /** The format used for trace timestamps. */
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

  @Override
  public void initializeLogPublisher(DebugLogPublisherCfg config, ServerContext serverContext)
      throws ConfigException, InitializationException {
    // This publisher is not configurable.
  }

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

  @Override
  public DN getDN()
  {
    // There is no configuration DN associated with this publisher.
    return DN.rootDN();
  }

}
