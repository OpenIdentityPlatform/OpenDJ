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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import org.opends.server.api.DebugLogPublisher;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.LogCategory;
import org.opends.server.loggers.debug.TraceSettings;
import org.opends.server.loggers.debug.DebugStackTraceFormatter;
import org.opends.server.loggers.debug.DebugMessageFormatter;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;
import org.opends.server.admin.std.server.DebugLogPublisherCfg;
import org.opends.server.config.ConfigException;
import com.sleepycat.je.*;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * The debug log publisher implementation that writes debug messages in a
 * friendly for console output.
 */
public class ConsoleDebugLogPublisher extends DebugLogPublisher
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

  /**
   * {@inheritDoc}
   */
  public void initializeDebugLogPublisher(DebugLogPublisherCfg config)
      throws ConfigException, InitializationException {
    // This pubisher is not configurable.
  }

  /**
   * {@inheritDoc}
   */
  public void traceConstructor(LogLevel level,
                               TraceSettings settings,
                               String signature,
                               String sourceLocation,
                               Object[] args,
                               StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.CONSTRUCTOR;

    String msg = "";
    if(args != null)
    {
      msg = buildDefaultEntryMessage(signature, sourceLocation, args);
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceMethodEntry(LogLevel level,
                               TraceSettings settings,
                               String signature,
                               String sourceLocation,
                               Object obj,
                               Object[] args,
                               StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.ENTER;
    String msg = "";
    if(args != null)
    {
      msg = buildDefaultEntryMessage(signature, sourceLocation, args);
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceStaticMethodEntry(LogLevel level,
                                     TraceSettings settings,
                                     String signature,
                                     String sourceLocation,
                                     Object[] args,
                                     StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.ENTER;
    String msg = "";
    if(args != null)
    {
      msg = buildDefaultEntryMessage(signature, sourceLocation, args);
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceReturn(LogLevel level,
                          TraceSettings settings,
                          String signature,
                          String sourceLocation,
                          Object ret,
                          StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.EXIT;
    String msg = "";
    if(ret != null)
    {
      StringBuilder format = new StringBuilder();
      format.append("returned={%s} ");
      format.append(signature);
      format.append("():");
      format.append(sourceLocation);

      msg = DebugMessageFormatter.format(format.toString(),
                                         new Object[] {ret});
    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level,  msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceThrown(LogLevel level,
                          TraceSettings settings,
                          String signature,
                          String sourceLocation,
                          Throwable ex,
                          StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.THROWN;

    StringBuilder format = new StringBuilder();
    format.append("thrown={%s} ");
    format.append(signature);
    format.append("():");
    format.append(sourceLocation);

    String msg = DebugMessageFormatter.format(format.toString(),
                                              new Object[] {ex});

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(ex,
                                                       settings.getStackDepth(),
                                                     settings.isIncludeCause());
    }
    publish(category, level, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceMessage(LogLevel level,
                           TraceSettings settings,
                           String signature,
                           String sourceLocation,
                           String msg,
                           StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.MESSAGE;

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceCaught(LogLevel level,
                          TraceSettings settings,
                          String signature,
                          String sourceLocation,
                          Throwable ex,
                          StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.CAUGHT;

    StringBuilder format = new StringBuilder();
    format.append("caught={%s} ");
    format.append(signature);
    format.append("():");
    format.append(sourceLocation);

    String msg = DebugMessageFormatter.format("caught={%s}",
                                              new Object[] {ex});

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(ex,
                                                       settings.getStackDepth(),
                                                     settings.isIncludeCause());
    }
    publish(category, level, msg, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceJEAccess(LogLevel level,
                            TraceSettings settings,
                            String signature,
                            String sourceLocation,
                            OperationStatus status,
                            Database database, Transaction txn,
                            DatabaseEntry key, DatabaseEntry data,
                            StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.DATABASE_ACCESS;

    // Build the string that is common to category DATABASE_ACCESS.
    StringBuilder builder = new StringBuilder();
    builder.append(" (");
    builder.append(status.toString());
    builder.append(")");
    builder.append(" db=");
    try
    {
      builder.append(database.getDatabaseName());
    }
    catch(DatabaseException de)
    {
      builder.append(de.toString());
    }
    if (txn != null)
    {
      builder.append(" txnid=");
      try
      {
        builder.append(txn.getId());
      }
      catch(DatabaseException de)
      {
        builder.append(de.toString());
      }
    }
    else
    {
      builder.append(" txnid=none");
    }

    builder.append(ServerConstants.EOL);
    if(key != null)
    {
      builder.append("key:");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, key.getData(), 4);
    }

    // If the operation was successful we log the same common information
    // plus the data
    if (status == OperationStatus.SUCCESS && data != null)
    {

      builder.append("data(len=");
      builder.append(data.getSize());
      builder.append("):");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, data.getData(), 4);

    }

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level, builder.toString(), stack);
  }

  /**
   * {@inheritDoc}
   */
  public void traceData(LogLevel level,
                        TraceSettings settings,
                        String signature,
                        String sourceLocation,
                        byte[] data,
                        StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.DATA;
    if(data != null)
    {
      StringBuilder builder = new StringBuilder();
      builder.append(ServerConstants.EOL);
      builder.append("data(len=");
      builder.append(data.length);
      builder.append("):");
      builder.append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(builder, data, 4);

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level, builder.toString(), stack);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void traceProtocolElement(LogLevel level,
                                   TraceSettings settings,
                                   String signature,
                                   String sourceLocation,
                                   String decodedForm,
                                   StackTraceElement[] stackTrace)
  {
    LogCategory category = DebugLogCategory.PROTOCOL;

    String stack = null;
    if(stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
                                                      settings.getStackDepth());
    }
    publish(category, level, decodedForm, stack);
  }

  /**
   * {@inheritDoc}
   */
  public void close()
  {
    // Nothing to do.
  }


  // Publishes a record, optionally performing some "special" work:
  // - injecting a stack trace into the message
  private void publish(LogCategory category, LogLevel level, String msg,
                       String stack)
  {
    StringBuilder buf = new StringBuilder();
    // Emit the timestamp.
    buf.append(dateFormat.format(System.currentTimeMillis()));
    buf.append(" ");

    // Emit debug category.
    buf.append(category);
    buf.append(" ");

    // Emit the debug level.
    buf.append(level);
    buf.append(" ");

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

  private String buildDefaultEntryMessage(String signature,
                                          String sourceLocation, Object[] args)
  {
    StringBuilder format = new StringBuilder();
    format.append(signature);
    format.append("(");
    for (int i = 0; i < args.length; i++)
    {
      if (i != 0) format.append(", ");
      format.append("arg");
      format.append(i + 1);
      format.append("={%s}");
    }
    format.append("):");
    format.append(sourceLocation);

    return DebugMessageFormatter.format(format.toString(), args);
  }

  /**
   * {@inheritDoc}
   */
  public DN getDN()
  {
    // There is no configuration DN associated with this publisher.
    return DN.NULL_DN;
  }

}
