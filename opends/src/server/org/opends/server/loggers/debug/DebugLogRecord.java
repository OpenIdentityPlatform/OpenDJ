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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers.debug;

import org.opends.server.loggers.LogRecord;
import org.opends.server.loggers.LogCategory;
import org.opends.server.loggers.LogLevel;
import org.opends.server.loggers.Logger;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DebugLogCategory;

import java.util.Date;

/**
 * A DebugLogRecord is reponsible for passing tracing log messages from the
 * individual Loggers to the LogPublishers.
 */
public class DebugLogRecord extends LogRecord
{
  private static long globalSequenceNumber;

  /**
   * The category of this record.
   */
  private LogCategory category;

  /**
   * The level of this record.
   */
  private LogLevel level;

  /**
   * Sequence number.
   */
  private long sequenceNumber;

  /**
   * Thread name for thread that issued logging call.
   */
  private String threadName;

  /**
   * Thread ID for thread that issued logging call.
   */
  private long threadID;

  /**
   * Event time in milliseconds since 1970.
   */
  private Date timestamp;

  /**
   * The signature signature.
   */
  private String signature;

  /**
   * The arguments objects.
   */
  private Object[] arguments;

  /**
   * The source location.
   */
  private String sourceLocation;

  /**
   * The stack trace.
   */
  private String stackTrace;

  /**
   * Construct a DebugLogRecord with the given values(s).
   * <p>
   * The sequence property will be initialized with a new unique value.
   * These sequence values are allocated in increasing order within a VM.
   * <p>
   * The timestamp property will be initialized to the current time.
   * <p>
   * The thread ID property will be initialized with a unique ID for
   * the current thread.
   * <p>
   * All other properties will be initialized to "null".
   *
   * @param category the category of this logging message.
   * @param level the level of this logging message.
   * @param msg  the raw non-localized logging message (may be null).
   */
  public DebugLogRecord(DebugLogCategory category,
                        DebugLogLevel level,
                        String msg)
  {
    super(msg);
    this.category = category;
    this.level = level;
  }

  /**
   * Construct a DebugLogRecord with the given values(s).
   * <p>
   * The sequence property will be initialized with a new unique value.
   * These sequence values are allocated in increasing order within a VM.
   * <p>
   * The timestamp property will be initialized to the current time.
   * <p>
   * The thread ID property will be initialized with a unique ID for
   * the current thread.
   * <p>
   * All other properties will be initialized to "null".
   *
   * @param category the category of this logging message.
   * @param level the level of this logging message.
   * @param logger  the source logger (may be null).
   * @param msg  the raw non-localized logging message (may be null).
   */
  public DebugLogRecord(DebugLogCategory category,
                        DebugLogLevel level,
                        Logger logger,
                        String msg)
  {
    super(logger, msg);
    this.category = category;
    this.level = level;
  }

  /**
   * Construct a DebugLogRecord with the given values(s).
   * <p>
   * The sequence property will be initialized with a new unique value.
   * These sequence values are allocated in increasing order within a VM.
   * <p>
   * The timestamp property will be initialized to the current time.
   * <p>
   * The thread ID property will be initialized with a unique ID for
   * the current thread.
   * <p>
   * All other properties will be initialized to "null".
   *
   * @param category the category of this logging message.
   * @param level the level of this logging message.
   * @param caller  the source object (may be null).
   * @param logger  the source logger (may be null).
   * @param msg  the raw non-localized logging message (may be null).
   */
  public DebugLogRecord(LogCategory category,
                        LogLevel level,
                        Object caller,
                        Logger logger,
                        String msg)
  {
    super(caller, logger, msg);
    this.category = category;
    this.level = level;

    // Assign a thread ID, name, and a unique sequence number.
    Thread thread= Thread.currentThread();
    threadID = thread.getId();
    threadName = thread.getName();
    sequenceNumber = globalSequenceNumber++;
    timestamp = new Date();
  }

  /**
   * Get an identifier for the thread where the message originated.
   * <p>
   * This is a thread identifier within the Java VM and may or
   * may not map to any operating system ID.
   *
   * @return thread ID
   */
  public long getThreadID() {
    return threadID;
  }

  /**
   * Set an identifier for the thread where the message originated.
   * @param threadID the thread ID
   */
  public void setThreadID(long threadID) {
    this.threadID = threadID;
  }

  /**
   * Get event time in milliseconds since 1970.
   *
   * @return event time in timestamp since 1970
   */
  public Date getTimestamp() {
    return timestamp;
  }

  /**
   * Set event time.
   *
   * @param timestamp event time in timestamp since 1970
   */
  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Get the category of this message.
   *
   * @return the log category.
   */
  public LogCategory getCategory()
  {
    return category;
  }

  /**
   * Set the category of this message.
   *
   * @param category the log category to set.
   */
  public void setCategory(LogCategory category)
  {
    this.category = category;
  }

  /**
   * Get the level of this message.
   *
   * @return the log level.
   */
  public LogLevel getLevel()
  {
    return level;
  }

  /**
   * Set the level of this message.
   *
   * @param level the log level to set.
   */
  public void setLevel(LogLevel level)
  {
    this.level = level;
  }

  /**
   * Get the thread name that generated this message.
   *
   * @return the thread name.
   */
  public String getThreadName()
  {
    return threadName;
  }

  /**
   * Set the thread name that genreated this message.
   *
   * @param threadName the thread name to set.
   */
  public void setThreadName(String threadName)
  {
    this.threadName = threadName;
  }

  /**
   * Get the method signature of this message.
   *
   * @return the method signature.
   */
  public String getSignature()
  {
    return signature;
  }

  /**
   * Set the method signature of this message.
   *
   * @param signature the method signature to set.
   */
  public void setSignature(String signature)
  {
    this.signature = signature;
  }

  /**
   * Get the arguments of this message. Usually the paramter values of a method.
   *
   * @return the arguments.
   */
  public Object[] getArguments()
  {
    return arguments;
  }

  /**
   * Set the arguments of this message.
   *
   * @param arguments the arguments to set.
   */
  public void setArguments(Object[] arguments)
  {
    this.arguments = arguments;
  }

  /**
   * Get the source location where this message is generated in the format
   * filename:linenumber.
   *
   * @return the source location.
   */
  public String getSourceLocation()
  {
    return sourceLocation;
  }

  /**
   * Set the source location where this message is generated.
   *
   * @param sourceLocation the source location string to set.
   */
  public void setSourceLocation(String sourceLocation)
  {
    this.sourceLocation = sourceLocation;
  }

  /**
   * Get the strack trace at the point this message is generated.
   *
   * @return the stack trace string.
   */
  public String getStackTrace()
  {
    return stackTrace;
  }

  /**
   * Set the stack trace at the point this message is generated.
   *
   * @param stackTrace the stack trace string to set.
   */
  public void setStackTrace(String stackTrace)
  {
    this.stackTrace = stackTrace;
  }

  /**
   * Get the sequence number.
   * <p>
   * Sequence numbers are normally assigned in the LogRecord
   * constructor, which assigns unique sequence numbers to
   * each new LogRecord in increasing order.
   *
   * @return the sequence number
   */
  public long getSequenceNumber() {
    return sequenceNumber;
  }

  /**
   * Set the sequence number.
   * <p>
   * Sequence numbers are normally assigned in the LogRecord constructor,
   * so it should not normally be necessary to use this signature.
   *
   * @param seq sequence number
   */
  public void setSequenceNumber(long seq) {
    sequenceNumber = seq;
  }
}
