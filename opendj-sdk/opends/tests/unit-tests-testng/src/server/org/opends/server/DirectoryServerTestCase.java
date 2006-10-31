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
package org.opends.server;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.PrintStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogManager;

import org.opends.server.types.NullOutputStream;

/**
 * This class defines a base test case that should be subclassed by all
 * unit tests used by the Directory Server.
 * <p>
 * This class adds the ability to print error messages and automatically
 * have them include the class name.
 */
public abstract class DirectoryServerTestCase {
  // The set of loggers for which the console logger has been disabled.
  private HashMap<Logger,Handler> disabledLogHandlers;

  // The print stream to use for printing error messages.
  private PrintStream errorStream;

  // The original System.err print stream.
  private PrintStream originalSystemErr;

  // The original System.out print stream.
  private PrintStream originalSystemOut;

  @BeforeSuite
  public final void suppressOutput() {
    String suppressStr = System.getProperty("org.opends.test.suppressOutput");
    if ((suppressStr != null) && suppressStr.equalsIgnoreCase("true"))
    {
      System.setOut(NullOutputStream.printStream());
      System.setErr(NullOutputStream.printStream());
      errorStream = NullOutputStream.printStream();

      LogManager logManager = LogManager.getLogManager();
      Enumeration<String> loggerNames = logManager.getLoggerNames();
      while (loggerNames.hasMoreElements())
      {
        String loggerName = loggerNames.nextElement();
        Logger logger = logManager.getLogger(loggerName);
        for (Handler h : logger.getHandlers())
        {
          if (h instanceof ConsoleHandler)
          {
            disabledLogHandlers.put(logger, h);
            logger.removeHandler(h);
            break;
          }
        }
      }
    }
  }

  @AfterSuite
  public final void shutdownServer() {
    TestCaseUtils.shutdownServer("The current test suite has finished.");

    System.setOut(originalSystemOut);
    System.setErr(originalSystemErr);
    errorStream = originalSystemErr;

    for (Logger l : disabledLogHandlers.keySet())
    {
      Handler h = disabledLogHandlers.get(l);
      l.addHandler(h);
    }
    disabledLogHandlers.clear();
  }

  /**
   * Creates a new instance of this test case with the provided name.
   */
  protected DirectoryServerTestCase() {
    this.errorStream = System.err;

    disabledLogHandlers = new HashMap<Logger,Handler>();
    originalSystemOut   = System.out;
    originalSystemErr   = System.err;
  }

  /**
   * Prints the provided message to the error stream, prepending the
   * fully-qualified class name.
   *
   * @param message
   *          The message to be printed to the error stream.
   */
  public final void printError(String message) {
    errorStream.print(getClass().getName());
    errorStream.print(" -- ");
    errorStream.println(message);
  }

  /**
   * Prints the stack trace for the provided exception to the error
   * stream.
   *
   * @param exception
   *          The exception to be printed to the error stream.
   */
  public final void printException(Throwable exception) {
    exception.printStackTrace(errorStream);
  }

  /**
   * Specifies the error stream to which messages will be printed.
   *
   * @param errorStream
   *          The error stream to which messages will be printed.
   */
  public final void setErrorStream(PrintStream errorStream) {
    this.errorStream = errorStream;
  }

}
