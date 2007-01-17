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
package org.opends.server.util;

import static org.opends.server.loggers.Debug.debugMessage;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.messages.UtilityMessages;

/**
 * This utility class provides static methods that make parameter checking
 * easier (e.g. in constructors and setters).
 * In particular the ensureNotNull methods provide an easy way to validate that
 * certain parameters are not null, and the ensureTrue methods provide the
 * ability to check arbitrary boolean conditions.
 * <p>
 * Invocation of these methods should be limited to situations where the only
 * way that they should fail is if there is a defect somewhere in the
 * system (including 3rd-party plugins).
 * <p>
 * You can think of these methods as being similar to <code>assert</code>,
 * but there are some additional advantages:
 * <ul>
 *   <li>All failures are logged to the debug and error logs</li>
 *   <li>Checks are always enabled (even if asserts are off)</li>
 *   <li>This class tracks the number of failures, allowing it to
 *       be exposed via monitoring, etc.<li>
 *   <li>The unit tests can track unnoticed internal failures and
 *       report on them.</li>
 *   <li>Developers can catch all Validator failures with a single
 *       break point.</li>
 * </ul>
 *
 * In general, you should not worry about the performance impact of calling
 * these methods.  Some micro-benchmarking has shown that ensureNotNull can be
 * called 200M times per second on a single CPU laptop.  The value of catching
 * defects early will almost always out-weigh any overhead that is introduced.
 * There are a couple of exceptions to this.  Any execution overhead that
 * happens before the method is invoked cannot be eliminated, e.g.
 * <code>Validator.ensureTrue(someExpensiveCheck())</code> will always invoke
 * <code>someExpensiveCheck()</code>.  When this code is on the critical path,
 * and we do not expect the validation to fail, you can guard the call with
 * an <code>assert</code> because each method returns true, and this code will
 * only be executed when asserts are enabled.
 * <p>
 * These methods are provided primarily to check parameter values for
 * constructors, setters, etc, and when they are used in this way, the javadoc
 * for the method must be updated to reflect what constraints are placed on the
 * parameters (e.g. attributeType cannot be null).
 * <p>
 * Feel free to add any method to this class that makes sense.  Be sure to
 * ensure that they don't violate the spirit of this class in that performance
 * is second only to correctness.
 * <p>
 * There are a few issues open for remaining tasks:
 * <ul>
 *  <li>757 Validator should expose a way to turn it off</li>
 *  <li>758 Validator should provide a way to throttle it's error messages</li>
 *  <li>759 Unit tests should always check that no unexpected Validator checks
 *      failed</li>
 * </ul>
 */
public class Validator {
  /** This static final variable theoretically allows us to compile out all of
   *  these checks.  Since all of the code below is guarded with this check,
   *  the compiler should eliminate it if ENABLE_CHECKS is false.
   *  From doing a little bit of micro-benchmarking, it appears that setting
   *  ENABLE_CHECKS=false speeds the code up by about a factor of four, but
   *  it's still not the same as not having the invocation in the first place.
   *  On a single CPU laptop, I was able to get 200M
   *  invocations per second with ENABLE_CHECKS=true, and 350M with
   *  ENABLE_CHECKS=false.
   *  <p>
   *  Setting this to false, will not eliminate any expensive computation
   *  done in a parameter list (e.g. some self-check that returns true).*/
  public static final boolean ENABLE_CHECKS = true;


  /** The fully-qualified class name for this class, which is used for
   *  debugging purposes. */
  private static final String CLASS_NAME = Validator.class.getName();


  /** A one-based array for parameter descriptions. */
  private static final String[] PARAM_DESCRIPTIONS =
          {"** A ZERO-BASED INDEX IS INVALID **",
           "(1st parameter)",
           "(2nd parameter)",
           "(3rd parameter)",
           "(4th parameter)",
           "(5th parameter)",
           "(6th parameter)",
           "(7th parameter)",
           "(8th parameter)",
           "(9th parameter)",
           "(10th parameter)"};

  /** A count of the errors detected by the methods in this class since the
   *  last time that resetErrorCount was called.  */
  private static long _errorCount = 0;


  /**
   * This method validates that the specified parameter is not null.  It
   * throws an AssertionError if it is null after logging this error.
   * <p>
   * This should be used like an assert, except it is not turned
   * off at runtime.  That is, it should only be used in situations where
   * there is a bug in someone's code if param is null.
   *
   * @param param the parameter to validate as non-null.
   * @return true always.  This allows this call to be used in an assert
   * statement, which can skip this check and remove all
   * overhead from the calling code.  This idiom should only be used when
   * performance testing proves that it is necessary.
   *
   * @throws AssertionError if and only if param is null
   * if assertions are enabled
   */
  public static boolean ensureNotNull(Object param)
          throws AssertionError {
    if (ENABLE_CHECKS) {
      if (param == null) throwNull("");
    }
    return true;
  }



  /**
   * This method validates that the specified parameters are not null.  It
   * throws an AssertionError if one of them are null after logging this error.
   * It's similar to the ensureNotNull(Object) call except it provides the
   * convenience of checking two parameters at once.
   * <p>
   * This should be used like an assert, except it is not turned
   * off at runtime.  That is, it should only be used in situations where
   * there is a bug in someone's code if param is null.
   * <p>
   * See the class level javadoc for why we did not use varargs to
   * implement this method.
   *
   * @param param1 the first parameter to validate as non-null.
   * @param param2 the second parameter to validate as non-null.
   * @return true always.  This allows this call to be used in an assert
   * statement, which can skip this check and remove all
   * overhead from the calling code.  This idiom should only be used when
   * performance testing proves that it is necessary.
   *
   * @throws AssertionError if and only if any of the parameters is null
   */
  public static boolean ensureNotNull(Object param1, Object param2)
          throws AssertionError {
    if (ENABLE_CHECKS) {
      if (param1 == null) throwNull(PARAM_DESCRIPTIONS[1]);
      if (param2 == null) throwNull(PARAM_DESCRIPTIONS[2]);
    }
    return true;
  }



  /**
   * This method validates that the specified parameters are not null.  It
   * throws an AssertionError if one of them are null after logging this error.
   * It's similar to the ensureNotNull(Object) call except it provides the
   * convenience of checking three parameters at once.
   * <p>
   * This should be used like an assert, except it is not turned
   * off at runtime.  That is, it should only be used in situations where
   * there is a bug in someone's code if param is null.
   * <p>
   * See the class level javadoc for why we did not use varargs to
   * implement this method.
   *
   * @param param1 the first parameter to validate as non-null.
   * @param param2 the second parameter to validate as non-null.
   * @param param3 the third parameter to validate as non-null.
   * @return true always.  This allows this call to be used in an assert
   * statement, which can skip this check and remove all
   * overhead from the calling code.  This idiom should only be used when
   * performance testing proves that it is necessary.
   *
   * @throws AssertionError if and only if one of the parameters is null
   */
  public static boolean ensureNotNull(Object param1, Object param2,
                                      Object param3)
          throws AssertionError {
    if (ENABLE_CHECKS) {
      if (param1 == null) throwNull(PARAM_DESCRIPTIONS[1]);
      if (param2 == null) throwNull(PARAM_DESCRIPTIONS[2]);
      if (param3 == null) throwNull(PARAM_DESCRIPTIONS[3]);
    }
    return true;
  }



  /**
   * This method validates that the specified parameters are not null.  It
   * throws an AssertionError if one of them are null after logging this error.
   * It's similar to the ensureNotNull(Object) call except it provides the
   * convenience of checking four parameters at once.
   * <p>
   * This should be used like an assert, except it is not turned
   * off at runtime.  That is, it should only be used in situations where
   * there is a bug in someone's code if param is null.
   * <p>
   * See the class level javadoc for why we did not use varargs to
   * implement this method.
   *
   * @param param1 the first parameter to validate as non-null.
   * @param param2 the second parameter to validate as non-null.
   * @param param3 the third parameter to validate as non-null.
   * @param param4 the fourth parameter to validate as non-null.
   * @return true always.  This allows this call to be used in an assert
   * statement, which can skip this check and remove all
   * overhead from the calling code.  This idiom should only be used when
   * performance testing proves that it is necessary.
   *
   * @throws AssertionError if and only if one of the parameters is null
   */
  public static boolean ensureNotNull(Object param1, Object param2,
                                      Object param3, Object param4)
          throws AssertionError {
    if (ENABLE_CHECKS) {
      if (param1 == null) throwNull(PARAM_DESCRIPTIONS[1]);
      if (param2 == null) throwNull(PARAM_DESCRIPTIONS[2]);
      if (param3 == null) throwNull(PARAM_DESCRIPTIONS[3]);
      if (param4 == null) throwNull(PARAM_DESCRIPTIONS[4]);
    }
    return true;
  }


  /**
   * This method validates that the specified parameter is true.  It
   * throws an AssertionError if it is not true.
   * <p>
   * This should be used like an assert, except it is not turned
   * off at runtime.  That is, it should only be used in situations where
   * there is a bug in someone's code if param is null.  The other advantage of
   * using this method instead of an assert is that it logs the error to the
   * debug and error logs.
   *
   * @param condition the condition that must be true.
   * @return true always.  This allows this call to be used in an assert
   * statement, which can skip this check and remove all
   * overhead from the calling code.  This idiom should only be used when
   * performance testing proves that it is necessary.
   *
   * @throws AssertionError if condition is false
   */
  public static boolean ensureTrue(boolean condition)
          throws AssertionError {
    if (ENABLE_CHECKS) {
      if (!condition) {
        ensureTrue(condition, "");
      }
    }
    return true;
  }



  /**
   * This method validates that the specified parameter is true.  It
   * throws an AssertionError if it is not true.  The supplied message is
   * included in the error message.
   * <p>
   * This should be used like an assert, except it is not turned
   * off at runtime.  That is, it should only be used in situations where
   * there is a bug in someone's code if param is null.  The other advantage of
   * using this method instead of an assert is that it logs the error to the
   * debug and error logs.
   *
   * @param condition the condition that must be true.
   * @param message the textual message to include in the error message.
   * @return true always.  This allows this call to be used in an assert
   * statement, which can skip this check and remove all
   * overhead from the calling code.  This idiom should only be used when
   * performance testing proves that it is necessary.
   *
   * @throws AssertionError if condition is false
   */
  public static boolean ensureTrue(boolean condition, String message)
          throws AssertionError {
    if (ENABLE_CHECKS) {
      if (!condition) {
        String fullMessage = generateLineSpecificErrorMessage(
                "The specified condition must be true. " + message);

        logError(fullMessage);

        throw new AssertionError(fullMessage);
      }
    }
    return true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  //  ERROR COUNT
  //
  ////////////////////////////////////////////////////////////////////////////


  /**
   * Returns the number of errors that this class has detected since the
   * system started or the last time that resetErrorCount() was called.
   * <p>
   * This could be useful in the unit tests to validate that no background
   * errors occurred during a test.  It could also be exposed by the monitoring
   * framekwork.
   *
   * @return the number of errors detected by this class since the error count
   * was last reset.
   */
  public static synchronized long getErrorCount() {
    return _errorCount;
  }


  /**
   * Resets the error count to zero.
   */
  public static synchronized void resetErrorCount() {
    _errorCount = 0;
  }


  private static synchronized void incrementErrorCount() {
    _errorCount++;
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  //  PRIVATE
  //
  ////////////////////////////////////////////////////////////////////////////


  private static String generateLineSpecificErrorMessage(String message) {
    return message + "  The error occurred at " + getOriginalCallerLineInfo();
  }


  private static void throwNull(String message)
          throws AssertionError {
    String fullMessage = generateLineSpecificErrorMessage(
            "The specified parameter must not be null. " + message);

    logError(fullMessage);

    throw new AssertionError(fullMessage);
  }



  private static void logError(String message) {
    incrementErrorCount();

    String messageWithStack = message + ServerConstants.EOL + getCallingStack();

    // Log to the debug log.
    debugMessage(DebugLogCategory.CORE_SERVER, DebugLogSeverity.ERROR,
            CLASS_NAME, "logError", messageWithStack);

    // Log to the error log.
    org.opends.server.loggers.Error.logError(ErrorLogCategory.CORE_SERVER,
            ErrorLogSeverity.SEVERE_ERROR,
            UtilityMessages.MSGID_VALIDATOR_PRECONDITION_NOT_MET,
            messageWithStack);
  }


  /*
   * @return a String representation of the line that called the
   * Validator method.
   */
  private static String getOriginalCallerLineInfo() {
    StackTraceElement stackElements[] = Thread.currentThread().getStackTrace();
    int callerIndex = getOriginalCallerStackIndex(stackElements);
    return stackElements[callerIndex].toString();
  }


  /*
   * @return a stack trace rooted at the line that called the first
   * Validator method.
   */
  private static String getCallingStack() {
    StackTraceElement stackElements[] = Thread.currentThread().getStackTrace();
    int callerIndex = getOriginalCallerStackIndex(stackElements);

    StringBuilder buffer = new StringBuilder();
    for (int i = callerIndex; i < stackElements.length; i++) {
      StackTraceElement stackElement = stackElements[i];
      buffer.append(stackElement).append(ServerConstants.EOL);
    }

    return buffer.toString();
  }


  /*
   * @return the index in the supplied stack trace of the first non-Validator
   * method.
   */
  private static int getOriginalCallerStackIndex(StackTraceElement stack[]) {
    // Go up the stack until we find who called the first Validator method.
    StackTraceElement element = null;
    int i;
    for (i = 0; i < stack.length; i++) {
      element = stack[i];
      // The stack trace of this thread looks like
      //   java.lang.Thread.dumpThreads(Native Method)
      //   java.lang.Thread.getStackTrace(Thread.java:1383)
      //   org.opends.server.util.Validator.getOriginalCallerLineInfo...
      //   ...
      //   original caller  <---- this is what we want to return
      //   more stack
      if (!element.getClassName().equals(Validator.class.getName()) &&
          !element.getClassName().equals(Thread.class.getName())) {
        break;
      }
    }

    return i;
  }
}
