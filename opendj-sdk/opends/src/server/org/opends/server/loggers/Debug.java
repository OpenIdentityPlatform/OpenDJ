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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;



import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.DebugLogger;
import org.opends.server.api.ProtocolElement;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;




/**
 * This class defines the wrapper that will invoke all registered debug loggers
 * for each type of debugging that may be performed.
 */
public class Debug
{
  // The set of debug loggers that have been registered with the server.  It
  // will initially be empty.
  private static CopyOnWriteArrayList<DebugLogger> debugLoggers =
       new CopyOnWriteArrayList<DebugLogger>();

  // A mutex that will be used to provide threadsafe access to methods changing
  // the set of defined loggers.
  private static ReentrantLock loggerMutex = new ReentrantLock();



  /**
   * Adds a new debug logger to which debug messages should be sent.
   *
   * @param  logger  The debug logger to which messages should be sent.
   */
  public static void addDebugLogger(DebugLogger logger)
  {
    loggerMutex.lock();

    try
    {
      for (DebugLogger l : debugLoggers)
      {
        if (l.equals(logger))
        {
          return;
        }
      }

      debugLoggers.add(logger);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Removes the provided debug logger so it will no longer be sent any new
   * debug messages.
   *
   * @param  logger  The debug logger to remove from the set.
   */
  public static void removeDebugLogger(DebugLogger logger)
  {
    loggerMutex.lock();

    try
    {
      debugLoggers.remove(logger);
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Removes all active debug loggers so that no debug messages will be sent
   * anywhere.
   *
   * @param  closeLoggers  Indicates whether the loggers should be closed as
   *                       they are unregistered.
   */
  public static void removeAllDebugLoggers(boolean closeLoggers)
  {
    loggerMutex.lock();

    try
    {
      if (closeLoggers)
      {
        DebugLogger[] loggers = new DebugLogger[debugLoggers.size()];
        debugLoggers.toArray(loggers);

        debugLoggers.clear();

        for (DebugLogger logger : loggers)
        {
          logger.closeDebugLogger();
        }
      }
      else
      {
        debugLoggers.clear();
      }
    }
    catch (Exception e)
    {
      // This should never happen.
      e.printStackTrace();
    }
    finally
    {
      loggerMutex.unlock();
    }
  }



  /**
   * Writes a message to the debug logger indicating that the specified raw data
   * was read.
   *
   * @param  className   The fully-qualified name of the Java class in which the
   *                     data was read.
   * @param  methodName  The name of the method in which the data was read.
   * @param  buffer      The byte buffer containing the data that has been read.
   *                     The byte buffer must be in the same state when this
   *                     method returns as when it was entered.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugBytesRead(String className, String methodName,
                                       ByteBuffer buffer)
  {
    for (DebugLogger logger : debugLoggers)
    {
      logger.debugBytesRead(className, methodName, buffer);
    }

    return true;
  }



  /**
   * Writes a message to the debug logger indicating that the specified raw data
   * was written.
   *
   * @param  className   The fully-qualified name of the Java class in which the
   *                     data was written.
   * @param  methodName  The name of the method in which the data was written.
   * @param  buffer      The byte buffer containing the data that has been
   *                     written.  The byte buffer must be in the same state
   *                     when this method returns as when it was entered.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugBytesWritten(String className, String methodName,
                                          ByteBuffer buffer)
  {
    for (DebugLogger logger : debugLoggers)
    {
      logger.debugBytesWritten(className, methodName, buffer);
    }

    return true;
  }



  /**
   * Writes a message to the debug logger indicating that the constructor for
   * the specified class has been invoked.
   *
   * @param  className  The fully-qualified name of the Java class whose
   *                    constructor has been invoked.
   * @param  args       The set of arguments provided for the constructor.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugConstructor(String className, String... args)
  {
    for (DebugLogger logger : debugLoggers)
    {
      logger.debugConstructor(className, args);
    }

    return true;
  }



  /**
   * Writes a message to the debug logger indicating that the specified method
   * has been entered.
   *
   * @param  className   The fully-qualified name of the Java class in which the
   *                     specified method resides.
   * @param  methodName  The name of the method that has been entered.
   * @param  args        The set of arguments provided to the method.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugEnter(String className, String methodName,
                                   String... args)
  {
    for (DebugLogger logger : debugLoggers)
    {
      logger.debugEnter(className, methodName, args);
    }

    return true;
  }



  /**
   * Writes a generic message to the debug logger using the provided
   * information.
   *
   * @param  category    The category associated with this debug message.
   * @param  severity    The severity associated with this debug message.
   * @param  className   The fully-qualified name of the Java class in which the
   *                     debug message was generated.
   * @param  methodName  The name of the method in which the debug message was
   *                     generated.
   * @param  message     The actual contents of the debug message.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugMessage(DebugLogCategory category,
                                     DebugLogSeverity severity,
                                     String className, String methodName,
                                     String message)
  {
    for (DebugLogger logger : debugLoggers)
    {
      logger.debugMessage(category, severity, className, methodName, message);
    }

    return true;
  }



  /**
   * Writes a message to the debug logger containing information from the
   * provided exception that was thrown.
   *
   * @param  className   The fully-qualified name of the Java class in which the
   *                     exception was thrown.
   * @param  methodName  The name of the method in which the exception was
   *                     thrown.
   * @param  exception   The exception that was thrown.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugException(String className, String methodName,
                                       Throwable exception)
  {
//    exception.printStackTrace();


    for (DebugLogger logger : debugLoggers)
    {
      logger.debugException(className, methodName, exception);
    }

    return true;
  }



  /**
   * Writes a message to the debug logger indicating that the provided protocol
   * element has been read.
   *
   * @param  className        The fully-qualified name of the Java class in
   *                          which the protocol element was read.
   * @param  methodName       The name of the method in which the protocol
   *                          element was read.
   * @param  protocolElement  The protocol element that was read.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugProtocolElementRead(String className,
                                                 String methodName,
                                                 ProtocolElement
                                                      protocolElement)
  {
    for (DebugLogger logger : debugLoggers)
    {
      logger.debugProtocolElementRead(className, methodName, protocolElement);
    }

    return true;
  }



  /**
   * Writes a message to the debug logger indicating that the provided protocol
   * element has been written.
   *
   * @param  className        The fully-qualified name of the Java class in
   *                          which the protocol element was written.
   * @param  methodName       The name of the method in which the protocol
   *                          element was written.
   * @param  protocolElement  The protocol element that was written.
   *
   * @return  <CODE>true</CODE>, so that this method can be used in assertions
   *          for conditional execution.
   */
  public static boolean debugProtocolElementWritten(String className,
                                                    String methodName,
                                                    ProtocolElement
                                                         protocolElement)
  {
    for (DebugLogger logger : debugLoggers)
    {
      logger.debugProtocolElementWritten(className, methodName,
                                         protocolElement);
    }

    return true;
  }
}

