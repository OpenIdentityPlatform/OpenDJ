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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server;

import org.testng.TestListenerAdapter;
import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.ITestResult;
import org.testng.IClass;
import org.testng.ITestNGMethod;
import org.testng.ITestContext;
import org.testng.xml.XmlSuite;
import static org.opends.server.util.ServerConstants.EOL;
import static org.opends.server.TestCaseUtils.originalSystemErr;
import org.opends.server.loggers.debug.DebugLogFormatter;
import org.opends.server.loggers.debug.DebugConfiguration;
import org.opends.server.loggers.debug.TraceSettings;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.LogLevel;
import org.opends.server.types.DebugLogLevel;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.io.PrintStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/**
 * This class is our replacement for the test results that TestNG generates.
 *   It prints out test to the console as they happen.
 *   It
 *
 */
public class TestListener extends TestListenerAdapter implements IReporter {
  private final StringBuilder _bufferedTestFailures = new StringBuilder();

  public static final String REPORT_FILE_NAME = "results.txt";

  // This is used to communicate with build.xml.  So that even when a test
  // fails, we can do the coverage report before failing the build.
  public static final String ANT_TESTS_FAILED_FILE_NAME = ".tests-failed-marker";

  /**
   * The Log Publisher for the Debug Logger
   */
  public static TestLogPublisher DEBUG_LOG_PUBLISHER =
      new TestLogPublisher(new DebugLogFormatter());

  private static final String DIVIDER_LINE = "-------------------------------------------------------------------------------" + EOL;

  public void onStart(ITestContext testContext) {
    super.onStart(testContext);

    // Delete the previous report if it's there.
    new File(testContext.getOutputDirectory(), REPORT_FILE_NAME).delete();
  }

  public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
    File reportFile = new File(outputDirectory, REPORT_FILE_NAME);

    writeReportToFile(reportFile);
    writeReportToScreen(reportFile);
    writeAntTestsFailedMarker(outputDirectory);
  }

  private void writeAntTestsFailedMarker(String outputDirectory) {
    // Signal 'ant' that all of the tests passed by removing this
    // special file.
    if (countTestsWithStatus(ITestResult.FAILURE) == 0) {
      new File(outputDirectory, ANT_TESTS_FAILED_FILE_NAME).delete();
    }
  }

  private void writeReportToFile(File reportFile) {
    PrintStream reportStream = null;
    try {
      reportStream = new PrintStream(new FileOutputStream(reportFile));
    } catch (FileNotFoundException e) {
      originalSystemErr.println("Could not open " + reportFile + " for writing.  Will write the unit test report to the console instead.");
      e.printStackTrace(originalSystemErr);
      reportStream = originalSystemErr;
    }

    reportStream.println(center("UNIT TEST REPORT"));
    reportStream.println(center("----------------") + EOL);
    reportStream.println("Finished at: " + (new Date()));
    reportStream.println("# Test clases: " + _classResults.size());
    reportStream.println("# Test methods: " + countTestMethods());
    reportStream.println("# Tests passed: " + countTestsWithStatus(ITestResult.SUCCESS));
    reportStream.println("# Tests failed: " + countTestsWithStatus(ITestResult.FAILURE));
    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL + EOL);
    reportStream.println(center("FAILED TESTS"));
    reportStream.println(EOL + EOL);
    reportStream.println(_bufferedTestFailures);

    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL);

    reportStream.println(getTimingInfo());

    reportStream.close();
  }

  private String getFqMethod(ITestResult result) {
    IClass cls = result.getTestClass();
    ITestNGMethod method = result.getMethod();

    return cls.getName() + "#" + method.getMethodName();
  }

  private void writeReportToScreen(File reportFile) {
    List<ITestResult> failedTests = getFailedTests();
    StringBuilder failed = new StringBuilder();
    for (int i = 0; i < failedTests.size(); i++) {
      ITestResult failedTest = failedTests.get(i);
      String fqMethod = getFqMethod(failedTest);
      int numFailures = 1;
      // Peek ahead to see if we had multiple failures for the same method
      // In which case, we list it once with a count of the failures.
      while (((i + 1) < failedTests.size()) &&
              fqMethod.equals(getFqMethod(failedTests.get(i+1)))) {
        numFailures++;
        i++;
      }

      failed.append("  ").append(fqMethod);

      if (numFailures > 1) {
        failed.append(" (x " + numFailures + ")");
      }

      failed.append(EOL);
    }




    if (failed.length() > 0) {
      originalSystemErr.println("The following unit tests failed: ");
      originalSystemErr.println(failed);
      originalSystemErr.println();
      originalSystemErr.println("Include the ant option '-Dtest.failures=true' to rerun only the failed tests.");
    } else {
      originalSystemErr.println("All of the tests passed.");
    }

    originalSystemErr.println();
    originalSystemErr.println("Wrote full test report to:");
    originalSystemErr.println(reportFile.getAbsolutePath());
  }


  public void onTestStart(ITestResult tr) {
    super.onTestStart(tr);
    TestAccessLogger.clear();
    TestErrorLogger.clear();
    TestCaseUtils.clearSystemOutContents();
    TestCaseUtils.clearSystemErrContents();

    DEBUG_LOG_PUBLISHER.clear();
  }


  public void onTestSuccess(ITestResult tr) {
    super.onTestSuccess(tr);
    addTestResult(tr);
  }

  public void onTestFailure(ITestResult tr) {
    super.onTestFailure(tr);
    addTestResult(tr);

    IClass cls = tr.getTestClass();
    ITestNGMethod method = tr.getMethod();

    String fqMethod = cls.getName() + "#" + method.getMethodName();

    StringBuilder failureInfo = new StringBuilder();
    failureInfo.append("Failed Test:  ").append(fqMethod).append(EOL);
    Object[] parameters = tr.getParameters();


    Throwable cause = tr.getThrowable();
    if (cause != null) {
      failureInfo.append("Failure Cause:  ").append(getTestngLessStack(cause));
    }

    for (int i = 0; (parameters != null) && (i < parameters.length); i++) {
      Object parameter = parameters[i];
      failureInfo.append("parameter[" + i + "]: ").append(parameter).append(EOL);
    }

    List<String> messages = TestAccessLogger.getMessages();
    if (! messages.isEmpty())
    {
      failureInfo.append(EOL);
      failureInfo.append("Access Log Messages:");
      failureInfo.append(EOL);
      for (String message : messages)
      {
        failureInfo.append(message);
        failureInfo.append(EOL);
      }
    }

    messages = TestErrorLogger.getMessages();
    if (! messages.isEmpty())
    {
      failureInfo.append(EOL);
      failureInfo.append("Error Log Messages:");
      failureInfo.append(EOL);
      for (String message : messages)
      {
        failureInfo.append(message);
        failureInfo.append(EOL);
      }
    }

    messages = DEBUG_LOG_PUBLISHER.getMessages();
    if(! messages.isEmpty())
    {
      failureInfo.append(EOL);
      failureInfo.append("Debug Log Messages:");
      failureInfo.append(EOL);
      for (String message : messages)
      {
        failureInfo.append(message);
        failureInfo.append(EOL);
      }
    }

    String systemOut = TestCaseUtils.getSystemOutContents();
    if (systemOut.length() > 0) {
      failureInfo.append(EOL + "System.out contents:" + EOL + systemOut);
    }

    String systemErr = TestCaseUtils.getSystemErrContents();
    if (systemErr.length() > 0) {
      failureInfo.append(EOL + "System.err contents:" + EOL + systemErr);
    }

    failureInfo.append(EOL + EOL);
    originalSystemErr.print(EOL + EOL + EOL + "                 T E S T   F A I L U R E ! ! !" + EOL + EOL);
    originalSystemErr.print(failureInfo);
    originalSystemErr.print(DIVIDER_LINE + EOL + EOL);

    _bufferedTestFailures.append(failureInfo);
  }

  private String getTestngLessStack(Throwable t) {
    StackTraceElement[] elements = t.getStackTrace();

    int lowestOpenDSFrame;
    for (lowestOpenDSFrame = elements.length - 1; lowestOpenDSFrame >= 0; lowestOpenDSFrame--) {
      StackTraceElement element = elements[lowestOpenDSFrame];
      String clsName = element.getClassName();
      if (clsName.startsWith("org.opends.") && !clsName.equals("org.opends.server.SuiteRunner")) {
        break;
      }
    }

    StringBuilder buffer = new StringBuilder();
    buffer.append(t).append(EOL);
    for (int i = 0; i <= lowestOpenDSFrame; i++) {
      buffer.append("    ").append(elements[i]).append(EOL);
    }

    return buffer.toString();
  }


  private final static int PAGE_WIDTH = 80;
  private static String center(String header) {
    StringBuilder buffer = new StringBuilder();
    int indent = (PAGE_WIDTH - header.length()) / 2;
    for (int i = 0; i < indent; i++) {
      buffer.append(" ");
    }
    buffer.append(header);
    return buffer.toString();
  }


  public void onTestSkipped(ITestResult tr) {
    super.onTestSkipped(tr);
    // TODO: do we need to do anything with this?
  }

  public void onTestFailedButWithinSuccessPercentage(ITestResult tr) {
    super.onTestFailedButWithinSuccessPercentage(tr);
    addTestResult(tr);
  }

  private void addTestResult(ITestResult result) {
    getResultsForClass(result.getTestClass()).addTestResult(result);
  }

  private final LinkedHashMap<IClass, TestClassResults> _classResults = new LinkedHashMap<IClass, TestClassResults>();

  private TestClassResults getResultsForClass(IClass cls) {
    TestClassResults results = _classResults.get(cls);
    if (results == null) {
      results = new TestClassResults(cls);
      _classResults.put(cls, results);
    }
    return results;
  }

  synchronized StringBuilder getTimingInfo() {
    StringBuilder timingOutput = new StringBuilder();
    timingOutput.append(center("TESTS RUN BY CLASS")).append(EOL);
    timingOutput.append(center("[method-name total-time (total-invocations)]")).append(EOL + EOL);
    for (TestClassResults results: _classResults.values()) {
      results.getTimingInfo(timingOutput);
    }

    timingOutput.append(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL);

    getSlowestTestsOutput(timingOutput);
    return timingOutput;
  }

  private int countTestMethods() {
    int count = 0;
    for (TestClassResults results: _classResults.values()) {
      count += results._methods.size();
    }
    return count;
  }

  private int countTestsWithStatus(int status) {
    int count = 0;
    for (TestClassResults results: _classResults.values()) {
      count += results._resultCounts[status];
    }
    return count;
  }

  synchronized private List<TestMethodResults> getAllMethodResults() {
    List<TestMethodResults> allResults = new ArrayList<TestMethodResults>();
    for (TestClassResults results: _classResults.values()) {
      allResults.addAll(results.getAllMethodResults());
    }
    return allResults;
  }

  private static final int NUM_SLOWEST_METHODS = 100;

  private void getSlowestTestsOutput(StringBuilder timingOutput) {
    timingOutput.append(center("CLASS SUMMARY SORTED BY DURATION")).append(EOL);
    timingOutput.append(center("[class-name total-time (total-invocations)]")).append(EOL + EOL);
    List<TestClassResults> sortedClasses = getClassesDescendingSortedByDuration();
    for (int i = 0; i < sortedClasses.size(); i++) {
      TestClassResults results = sortedClasses.get(i);
      timingOutput.append("  ");
      results.getSummaryTimingInfo(timingOutput);
      timingOutput.append(EOL);
    }

    timingOutput.append(EOL + DIVIDER_LINE + EOL + EOL);
    timingOutput.append(center("SLOWEST METHODS")).append(EOL);
    timingOutput.append(center("[method-name total-time (total-invocations)]")).append(EOL + EOL);
    List<TestMethodResults> sortedMethods = getMethodsDescendingSortedByDuration();
    for (int i = 0; i < Math.min(sortedMethods.size(), NUM_SLOWEST_METHODS); i++) {
      TestMethodResults results = sortedMethods.get(i);
      results.getTimingInfo(timingOutput, true);
    }
  }


  private List<TestMethodResults> getMethodsDescendingSortedByDuration() {
    List<TestMethodResults> allMethods = getAllMethodResults();
    Collections.sort(allMethods, new Comparator<TestMethodResults>() {
      public int compare(TestMethodResults o1, TestMethodResults o2) {
        if (o1._totalDurationMs > o2._totalDurationMs) {
          return -1;
        } else if (o1._totalDurationMs < o2._totalDurationMs) {
          return 1;
        } else {
          return 0;
        }
      }
    });
    return allMethods;
  }

  private List<TestClassResults> getClassesDescendingSortedByDuration() {
    List<TestClassResults> allClasses = new ArrayList<TestClassResults>(_classResults.values());
    Collections.sort(allClasses, new Comparator<TestClassResults>() {
      public int compare(TestClassResults o1, TestClassResults o2) {
        if (o1._totalDurationMs > o2._totalDurationMs) {
          return -1;
        } else if (o1._totalDurationMs < o2._totalDurationMs) {
          return 1;
        } else {
          return 0;
        }
      }
    });
    return allClasses;
  }

  private final static String[] STATUSES =
          {"<<invalid>>", "Success", "Failure", "Skip", "Success Percentage Failure"};


  /**
   *
   */
  private static class TestClassResults {
    private final IClass _cls;
    private final LinkedHashMap<ITestNGMethod, TestMethodResults> _methods = new LinkedHashMap<ITestNGMethod, TestMethodResults>();
    private int _totalInvocations = 0;
    private long _totalDurationMs = 0;

    // Indexed by SUCCESS, FAILURE, SKIP, SUCCESS_PERCENTAGE_FAILURE
    private int[] _resultCounts = new int[STATUSES.length];

    public TestClassResults(IClass cls) {
      _cls = cls;
    }

    synchronized void addTestResult(ITestResult result) {
      _totalInvocations++;
      _totalDurationMs += result.getEndMillis() - result.getStartMillis();

      getResultsForMethod(result.getMethod()).addTestResult(result);
      int status = result.getStatus();
      if (status < 0 || status >= _resultCounts.length) {
        status = 0;
      }
      _resultCounts[status]++;
    }

    private TestMethodResults getResultsForMethod(ITestNGMethod method) {
      TestMethodResults results = _methods.get(method);
      if (results == null) {
        results = new TestMethodResults(method);
        _methods.put(method, results);
      }
      return results;
    }

    synchronized void getSummaryTimingInfo(StringBuilder timingOutput) {
      timingOutput.append(_cls.getRealClass().getName() + "    ");
      timingOutput.append(getTotalDurationMs() + " ms" + " (" + getTotalInvocations() + ")");
    }

    synchronized Collection<TestMethodResults> getAllMethodResults() {
      return _methods.values();
    }

    synchronized void getTimingInfo(StringBuilder timingOutput) {
      getSummaryTimingInfo(timingOutput);
      timingOutput.append(EOL);
      for (TestMethodResults results: getAllMethodResults()) {
        results.getTimingInfo(timingOutput, false);
      }

      timingOutput.append(EOL);
    }

    int getTotalInvocations() {
      return _totalInvocations;
    }

    long getTotalDurationMs() {
      return _totalDurationMs;
    }
  }


  /**
   *
   */
  private static class TestMethodResults {
    private final ITestNGMethod _method;
    int _totalInvocations = 0;
    long _totalDurationMs = 0;

    // Indexed by SUCCESS, FAILURE, SKIP, SUCCESS_PERCENTAGE_FAILURE
    private int[] _resultCounts = new int[STATUSES.length];

    public TestMethodResults(ITestNGMethod method) {
      _method = method;
    }

    synchronized void addTestResult(ITestResult result) {
      _totalInvocations++;
      _totalDurationMs += result.getEndMillis() - result.getStartMillis();

      int status = result.getStatus();
      if (status < 0 || status >= _resultCounts.length) {
        status = 0;
      }
      _resultCounts[status]++;
    }

    synchronized void getTimingInfo(StringBuilder timingOutput, boolean includeClassName) {
      timingOutput.append("    ");
      if (includeClassName) {
        timingOutput.append(_method.getRealClass().getName()).append("#");
      }
      timingOutput.append(_method.getMethodName() + "  ");
      timingOutput.append(_totalDurationMs + " ms" + " (" + _totalInvocations + ")");
      if (_resultCounts[ITestResult.FAILURE] > 0) {
        timingOutput.append(" " + _resultCounts[ITestResult.FAILURE] + " failure(s)");
      }
      timingOutput.append(EOL);
    }
  }
}
