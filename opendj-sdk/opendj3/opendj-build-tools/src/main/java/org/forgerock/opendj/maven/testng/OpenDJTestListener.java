/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */
package org.forgerock.opendj.maven.testng;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import org.testng.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.xml.XmlSuite;



/**
 * This class is our replacement for the test results that TestNG generates. It
 * prints out test to the console as they happen.
 */
public class OpenDJTestListener extends TestListenerAdapter implements IReporter
{

  /**
   * The end-of-line character for this platform.
   */
  private static final String EOL = System.getProperty("line.separator");



  private static class TestClassResults
  {
    private final IClass _cls;

    private final LinkedHashMap<ITestNGMethod, TestMethodResults> _methods = new LinkedHashMap<ITestNGMethod, TestMethodResults>();

    private int _totalInvocations = 0;

    private long _totalDurationMs = 0;

    // Indexed by SUCCESS, FAILURE, SKIP, SUCCESS_PERCENTAGE_FAILURE
    private final int[] _resultCounts = new int[STATUSES.length];



    public TestClassResults(final IClass cls)
    {
      _cls = cls;
    }



    synchronized void addTestResult(final ITestResult result)
    {
      _totalInvocations++;
      _totalDurationMs += result.getEndMillis() - result.getStartMillis();

      getResultsForMethod(result.getMethod()).addTestResult(result);
      int status = result.getStatus();
      if (status < 0 || status >= _resultCounts.length)
      {
        status = 0;
      }
      _resultCounts[status]++;
    }



    synchronized Collection<TestMethodResults> getAllMethodResults()
    {
      return _methods.values();
    }



    synchronized void getSummaryTimingInfo(final StringBuilder timingOutput)
    {
      timingOutput.append(_cls.getRealClass().getName() + "    ");
      timingOutput.append(getTotalDurationMs() + " ms" + " ("
          + getTotalInvocations() + ")");
    }



    synchronized void getTimingInfo(final StringBuilder timingOutput)
    {
      getSummaryTimingInfo(timingOutput);
      timingOutput.append(EOL);
      for (final TestMethodResults results : getAllMethodResults())
      {
        results.getTimingInfo(timingOutput, false);
      }

      timingOutput.append(EOL);
    }



    long getTotalDurationMs()
    {
      return _totalDurationMs;
    }



    int getTotalInvocations()
    {
      return _totalInvocations;
    }



    private TestMethodResults getResultsForMethod(final ITestNGMethod method)
    {
      TestMethodResults results = _methods.get(method);
      if (results == null)
      {
        results = new TestMethodResults(method);
        _methods.put(method, results);
      }
      return results;
    }
  }



  /**
   *
   */
  private static class TestMethodResults
  {
    private final ITestNGMethod _method;

    int _totalInvocations = 0;

    long _totalDurationMs = 0;

    // Indexed by SUCCESS, FAILURE, SKIP, SUCCESS_PERCENTAGE_FAILURE
    private final int[] _resultCounts = new int[STATUSES.length];



    public TestMethodResults(final ITestNGMethod method)
    {
      _method = method;
    }



    synchronized void addTestResult(final ITestResult result)
    {
      _totalInvocations++;
      _totalDurationMs += result.getEndMillis() - result.getStartMillis();

      int status = result.getStatus();
      if (status < 0 || status >= _resultCounts.length)
      {
        status = 0;
      }
      _resultCounts[status]++;
    }



    synchronized void getTimingInfo(final StringBuilder timingOutput,
        final boolean includeClassName)
    {
      timingOutput.append("    ");
      if (includeClassName)
      {
        timingOutput.append(_method.getRealClass().getName()).append("#");
      }
      timingOutput.append(_method.getMethodName() + "  ");
      timingOutput.append(_totalDurationMs + " ms" + " (" + _totalInvocations
          + ")");
      if (_resultCounts[ITestResult.FAILURE] > 0)
      {
        timingOutput.append(" " + _resultCounts[ITestResult.FAILURE]
            + " failure(s)");
      }
      timingOutput.append(EOL);
    }
  }



  private static final String REPORT_FILE_NAME = "results.txt";

  // This is used to communicate with build.xml. So that even when a
  // test fails, we can do the coverage report before failing the build.
  private static final String ANT_TESTS_FAILED_FILE_NAME = ".tests-failed-marker";

  private final StringBuilder _bufferedTestFailures = new StringBuilder();

  private static final String PROPERTY_TEST_PROGRESS = "test.progress";

  private static final String TEST_PROGRESS_NONE = "none";

  private static final String TEST_PROGRESS_ALL = "all";

  private static final String TEST_PROGRESS_DEFAULT = "default";

  private static final String TEST_PROGRESS_TIME = "time";

  private static final String TEST_PROGRESS_TEST_COUNT = "count";

  /*
   * for now, since it's not useful to most developers
   */
  private static final String TEST_PROGRESS_MEMORY = "memory";

  private static final String TEST_PROGRESS_MEMORY_GCS = "gcs"; // Hidden

  private static final String TEST_PROGRESS_THREAD_COUNT = "threadcount";

  private static final String TEST_PROGRESS_THREAD_CHANGES = "threadchanges";

  private boolean doProgressNone = false;

  private boolean doProgressTime = true;

  private boolean doProgressTestCount = true;

  private boolean doProgressMemory = false;

  private boolean doProgressMemoryGcs = false;

  private boolean doProgressThreadCount = false;

  private boolean doProgressThreadChanges = false;

  private static final String DIVIDER_LINE = "-------------------------------------------------------------------------------"
      + EOL;

  private final static int PAGE_WIDTH = 80;



  private static void pauseOnFailure()
  {
    File tempFile = null;
    try
    {
      tempFile = File.createTempFile("testfailure", "watchdog");
      tempFile.deleteOnExit();
      System.err.println("**** Pausing test execution until file "
          + tempFile.getCanonicalPath() + " is removed.");
    }
    catch (final Exception e)
    {
      System.err.println("**** ERROR:  Could not create a watchdog "
          + "file.  Pausing test execution indefinitely.");
      System.err.println("**** You will have to manually kill the "
          + "JVM when you're done investigating the problem.");
    }

    while ((tempFile != null) && tempFile.exists())
    {
      try
      {
        Thread.sleep(100);
      }
      catch (final Exception e)
      {
      }
    }

    System.err.println("**** Watchdog file removed.  Resuming test "
        + "case execution.");
  }



  /**
   * Return a String representation of all of the current threads.
   *
   * @return a dump of all Threads on the server
   */
  public static String threadStacksToString()
  {
    final Map<Thread, StackTraceElement[]> threadStacks = Thread
        .getAllStackTraces();

    // Re-arrange all of the elements by thread ID so that there is some
    // logical
    // order.
    final TreeMap<Long, Map.Entry<Thread, StackTraceElement[]>> orderedStacks = new TreeMap<Long, Map.Entry<Thread, StackTraceElement[]>>();
    for (final Map.Entry<Thread, StackTraceElement[]> e : threadStacks
        .entrySet())
    {
      orderedStacks.put(e.getKey().getId(), e);
    }

    final StringBuilder buffer = new StringBuilder();
    for (final Map.Entry<Thread, StackTraceElement[]> e : orderedStacks
        .values())
    {
      final Thread t = e.getKey();
      final StackTraceElement[] stackElements = e.getValue();

      final long id = t.getId();

      buffer.append("id=");
      buffer.append(id);
      buffer.append(" ---------- ");
      buffer.append(t.getName());
      buffer.append(" ----------");
      buffer.append(EOL);

      if (stackElements != null)
      {
        for (final StackTraceElement stackElement : stackElements)
        {
          buffer.append("   ").append(stackElement.getClassName());
          buffer.append(".");
          buffer.append(stackElement.getMethodName());
          buffer.append("(");
          buffer.append(stackElement.getFileName());
          buffer.append(":");
          if (stackElement.isNativeMethod())
          {
            buffer.append("native");
          }
          else
          {
            buffer.append(stackElement.getLineNumber());
          }
          buffer.append(")").append(EOL);
        }
      }
      buffer.append(EOL);
    }

    return buffer.toString();
  }



  private static String center(final String header)
  {
    final StringBuilder buffer = new StringBuilder();
    final int indent = (PAGE_WIDTH - header.length()) / 2;
    for (int i = 0; i < indent; i++)
    {
      buffer.append(" ");
    }
    buffer.append(header);
    return buffer.toString();
  }



  private final Set<Class<?>> _checkedForTypeAndAnnotations = new HashSet<Class<?>>();

  private final LinkedHashSet<Class<?>> _classesWithTestsRunInterleaved = new LinkedHashSet<Class<?>>();

  private Object _lastTestObject = null;

  private final IdentityHashMap<Object, Object> _previousTestObjects = new IdentityHashMap<Object, Object>();

  private final Set<Method> _checkedForAnnotation = new HashSet<Method>();

  private boolean statusHeaderPrinted = false;

  private final long startTimeMs = System.currentTimeMillis();

  private long prevTimeMs = System.currentTimeMillis();

  private List<String> prevThreads = new ArrayList<String>();

  private long prevMemInUse = 0;

  private long maxMemInUse = 0;

  private final LinkedHashMap<IClass, TestClassResults> _classResults = new LinkedHashMap<IClass, TestClassResults>();

  private static final int NUM_SLOWEST_METHODS = 100;

  private final static String[] STATUSES = { "<<invalid>>", "Success",
      "Failure", "Skip", "Success Percentage Failure" };



  /**
   * Creates the new TestNG listener.
   */
  public OpenDJTestListener()
  {
    initializeProgressVars();
  }



  public void generateReport(final List<XmlSuite> xmlSuites,
      final List<ISuite> suites, final String outputDirectory)
  {
    final File reportFile = new File(outputDirectory, REPORT_FILE_NAME);

    writeReportToFile(reportFile);
    writeReportToScreen(reportFile);
    writeAntTestsFailedMarker(outputDirectory);
  }



  public void onConfigurationFailure(final ITestResult tr)
  {
    super.onConfigurationFailure(tr);

    final IClass cls = tr.getTestClass();
    final ITestNGMethod method = tr.getMethod();

    final String fqMethod = cls.getName() + "#" + method.getMethodName();

    final StringBuilder failureInfo = new StringBuilder();
    failureInfo.append("Failed Test:  ").append(fqMethod).append(EOL);
    // Object[] parameters = tr.getParameters();

    final Throwable cause = tr.getThrowable();
    if (cause != null)
    {
      failureInfo.append("Failure Cause:  ").append(getTestngLessStack(cause));
    }

    failureInfo.append(EOL + EOL);
    System.err.print(EOL + EOL + EOL
        + "         C O N F I G U R A T I O N   F A I L U R E ! ! !" + EOL
        + EOL);
    System.err.print(failureInfo);
    System.err.print(DIVIDER_LINE + EOL + EOL);

    _bufferedTestFailures.append(failureInfo);
  }



  public void onStart(final ITestContext testContext)
  {
    super.onStart(testContext);

    // Delete the previous report if it's there.
    new File(testContext.getOutputDirectory(), REPORT_FILE_NAME).delete();
  }



  public void onTestFailedButWithinSuccessPercentage(final ITestResult tr)
  {
    super.onTestFailedButWithinSuccessPercentage(tr);
    onTestFinished(tr);
  }



  public void onTestFailure(final ITestResult tr)
  {
    super.onTestFailure(tr);

    final IClass cls = tr.getTestClass();
    final ITestNGMethod method = tr.getMethod();

    final String fqMethod = cls.getName() + "#" + method.getMethodName();

    final StringBuilder failureInfo = new StringBuilder();
    failureInfo.append("Failed Test:  ").append(fqMethod).append(EOL);
    final Object[] parameters = tr.getParameters();

    final Throwable cause = tr.getThrowable();
    if (cause != null)
    {
      failureInfo.append("Failure Cause:  ").append(getTestngLessStack(cause));
    }

    for (int i = 0; (parameters != null) && (i < parameters.length); i++)
    {
      final Object parameter = parameters[i];
      failureInfo.append("parameter[" + i + "]: ").append(parameter)
          .append(EOL);
    }

    failureInfo.append(EOL + EOL);
    System.err.print(EOL + EOL + EOL
        + "                 T E S T   F A I L U R E ! ! !" + EOL + EOL);
    System.err.print(failureInfo);
    System.err.print(DIVIDER_LINE + EOL + EOL);

    _bufferedTestFailures.append(failureInfo);

    final String pauseStr = System
        .getProperty("org.opendj.test.pauseOnFailure");
    if ((pauseStr != null) && pauseStr.equalsIgnoreCase("true"))
    {
      pauseOnFailure();
    }

    onTestFinished(tr);
  }



  public void onTestSkipped(final ITestResult tr)
  {
    super.onTestSkipped(tr);
    onTestFinished(tr);
  }



  public void onTestStart(final ITestResult tr)
  {
    super.onTestStart(tr);

    enforceTestClassTypeAndAnnotations(tr);
    checkForInterleavedBetweenClasses(tr);
    enforceMethodHasAnnotation(tr);
  }



  public void onTestSuccess(final ITestResult tr)
  {
    super.onTestSuccess(tr);
    onTestFinished(tr);
  }



  synchronized StringBuilder getTimingInfo()
  {
    final StringBuilder timingOutput = new StringBuilder();
    timingOutput.append(center("TESTS RUN BY CLASS")).append(EOL);
    timingOutput.append(center("[method-name total-time (total-invocations)]"))
        .append(EOL + EOL);
    for (final TestClassResults results : _classResults.values())
    {
      results.getTimingInfo(timingOutput);
    }

    timingOutput.append(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL);

    getSlowestTestsOutput(timingOutput);
    return timingOutput;
  }



  private void addTestResult(final ITestResult result)
  {
    getResultsForClass(result.getTestClass()).addTestResult(result);

    // Read the comments in DirectoryServerTestCase to understand what's
    // going on here.
    final Object[] testInstances = result.getMethod().getInstances();
    for (final Object testInstance : testInstances)
    {
      if (testInstance instanceof OpenDJTestCase)
      {
        final OpenDJTestCase openDJTestCase = (OpenDJTestCase) testInstance;
        final Object[] parameters = result.getParameters();
        if (result.getStatus() == ITestResult.SUCCESS)
        {
          openDJTestCase.addParamsFromSuccessfulTests(parameters);
          // This can eat up a bunch of memory for tests that are
          // expected to throw
          result.setThrowable(null);
        }
        else
        {
          openDJTestCase.addParamsFromFailedTest(parameters);

          // When the test finishes later on, we might not have
          // everything
          // that we need to print the result (e.g. the Schema for an
          // Entry
          // or DN), so go ahead and convert it to a String now.
          result.setParameters(convertToStringParameters(parameters));
        }
      }
      else
      {
        // We already warned about it.
      }
    }
  }



  private void checkForInterleavedBetweenClasses(final ITestResult tr)
  {
    final Object[] testInstances = tr.getMethod().getInstances();
    // This will almost always have a single element. If it doesn't,
    // just
    // skip it.
    if (testInstances.length != 1)
    {
      return;
    }

    final Object testInstance = testInstances[0];

    // We're running another test on the same test object. Everything is
    // fine.
    if (_lastTestObject == testInstance)
    {
      return;
    }

    // Otherwise, we're running a new test, so save the old one.
    if (_lastTestObject != null)
    {
      _previousTestObjects.put(_lastTestObject, _lastTestObject);
    }

    // Output progress info since we're running a new class
    outputTestProgress(_lastTestObject);

    // And make sure we don't have a test object that we already ran
    // tests with.
    if (_previousTestObjects.containsKey(testInstance))
    {
      _classesWithTestsRunInterleaved.add(testInstance.getClass());
    }

    _lastTestObject = testInstance;
  }



  private String[] convertToStringParameters(final Object[] parameters)
  {
    if (parameters == null)
    {
      return null;
    }

    final String[] strParams = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++)
    {
      strParams[i] = String.valueOf(parameters[i]).intern();
    }

    return strParams;
  }



  private int countTestMethods()
  {
    int count = 0;
    for (final TestClassResults results : _classResults.values())
    {
      count += results._methods.size();
    }
    return count;
  }



  private int countTestsWithStatus(final int status)
  {
    int count = 0;
    for (final TestClassResults results : _classResults.values())
    {
      count += results._resultCounts[status];
    }
    return count;
  }



  private int countTotalInvocations()
  {
    int count = 0;
    for (final TestClassResults results : _classResults.values())
    {
      count += results._totalInvocations;
    }
    return count;
  }



  private void enforceMethodHasAnnotation(final ITestResult tr)
  {
    // Only warn once per method.
    final Method testMethod = tr.getMethod().getConstructorOrMethod()
        .getMethod();
    if (_checkedForAnnotation.contains(testMethod))
    {
      return;
    }
    _checkedForAnnotation.add(testMethod);

    final Annotation testAnnotation = testMethod.getAnnotation(Test.class);
    final Annotation dataProviderAnnotation = testMethod
        .getAnnotation(DataProvider.class);

    if ((testAnnotation == null) && (dataProviderAnnotation == null))
    {
      final String errorMessage = "The test method "
          + testMethod
          + " does not have a @Test annotation.  "
          + "However, TestNG assumes it is a test method because it's a public method "
          + "in a class with a class-level @Test annotation.  You can remove this warning by either "
          + "marking the method with @Test or by making it non-public.";
      System.err.println("\n\nWARNING: " + errorMessage + "\n\n");
    }
  }



  private void enforceTestClassTypeAndAnnotations(final ITestResult tr)
  {
    Class<?> testClass = null;
    testClass = tr.getMethod().getRealClass();

    // Only warn once per class.
    if (_checkedForTypeAndAnnotations.contains(testClass))
    {
      return;
    }
    _checkedForTypeAndAnnotations.add(testClass);

    if (!OpenDJTestCase.class.isAssignableFrom(testClass))
    {
      final String errorMessage = "The test class " + testClass.getName()
          + " must inherit (directly or indirectly) "
          + "from DirectoryServerTestCase.";
      System.err.println("\n\nERROR: " + errorMessage + "\n\n");
      throw new RuntimeException(errorMessage);
    }

    final Class<?> classWithTestAnnotation = findClassWithTestAnnotation(testClass);

    if (classWithTestAnnotation == null)
    {
      final String errorMessage = "The test class "
          + testClass.getName()
          + " does not have a @Test annotation.  "
          + "All test classes must have a @Test annotation";
      System.err.println("\n\nERROR: " + errorMessage + "\n\n");
      throw new RuntimeException(errorMessage);
    }
  }



  // Return the class in cls's inheritence hierarchy that has the @Test
  // annotation defined.
  private Class<?> findClassWithTestAnnotation(Class<?> cls)
  {
    while (cls != null)
    {
      if (cls.getAnnotation(Test.class) != null)
      {
        return cls;
      }
      else
      {
        cls = cls.getSuperclass();
      }
    }
    return null;
  }



  synchronized private List<TestMethodResults> getAllMethodResults()
  {
    final List<TestMethodResults> allResults = new ArrayList<TestMethodResults>();
    for (final TestClassResults results : _classResults.values())
    {
      allResults.addAll(results.getAllMethodResults());
    }
    return allResults;
  }



  private List<TestClassResults> getClassesDescendingSortedByDuration()
  {
    final List<TestClassResults> allClasses = new ArrayList<TestClassResults>(
        _classResults.values());
    Collections.sort(allClasses, new Comparator<TestClassResults>()
    {
      public int compare(final TestClassResults o1, final TestClassResults o2)
      {
        if (o1._totalDurationMs > o2._totalDurationMs)
        {
          return -1;
        }
        else if (o1._totalDurationMs < o2._totalDurationMs)
        {
          return 1;
        }
        else
        {
          return 0;
        }
      }
    });
    return allClasses;
  }



  private String getFqMethod(final ITestResult result)
  {
    final IClass cls = result.getTestClass();
    final ITestNGMethod method = result.getMethod();

    return cls.getName() + "#" + method.getMethodName();
  }



  private List<TestMethodResults> getMethodsDescendingSortedByDuration()
  {
    final List<TestMethodResults> allMethods = getAllMethodResults();
    Collections.sort(allMethods, new Comparator<TestMethodResults>()
    {
      public int compare(final TestMethodResults o1, final TestMethodResults o2)
      {
        if (o1._totalDurationMs > o2._totalDurationMs)
        {
          return -1;
        }
        else if (o1._totalDurationMs < o2._totalDurationMs)
        {
          return 1;
        }
        else
        {
          return 0;
        }
      }
    });
    return allMethods;
  }



  private TestClassResults getResultsForClass(final IClass cls)
  {
    TestClassResults results = _classResults.get(cls);
    if (results == null)
    {
      results = new TestClassResults(cls);
      _classResults.put(cls, results);
    }
    return results;
  }



  private void getSlowestTestsOutput(final StringBuilder timingOutput)
  {
    timingOutput.append(center("CLASS SUMMARY SORTED BY DURATION")).append(EOL);
    timingOutput.append(center("[class-name total-time (total-invocations)]"))
        .append(EOL + EOL);
    final List<TestClassResults> sortedClasses = getClassesDescendingSortedByDuration();
    for (int i = 0; i < sortedClasses.size(); i++)
    {
      final TestClassResults results = sortedClasses.get(i);
      timingOutput.append("  ");
      results.getSummaryTimingInfo(timingOutput);
      timingOutput.append(EOL);
    }

    timingOutput.append(EOL + DIVIDER_LINE + EOL + EOL);
    timingOutput.append(center("SLOWEST METHODS")).append(EOL);
    timingOutput.append(center("[method-name total-time (total-invocations)]"))
        .append(EOL + EOL);
    final List<TestMethodResults> sortedMethods = getMethodsDescendingSortedByDuration();
    for (int i = 0; i < Math.min(sortedMethods.size(), NUM_SLOWEST_METHODS); i++)
    {
      final TestMethodResults results = sortedMethods.get(i);
      results.getTimingInfo(timingOutput, true);
    }
  }



  private String getTestngLessStack(final Throwable t)
  {
    final StackTraceElement[] elements = t.getStackTrace();

    int lowestOpenDJFrame;
    for (lowestOpenDJFrame = elements.length - 1; lowestOpenDJFrame >= 0; lowestOpenDJFrame--)
    {
      final StackTraceElement element = elements[lowestOpenDJFrame];
      final String clsName = element.getClassName();
      if (clsName.startsWith("org.opendj.")
          && !clsName.equals("org.opendj.server.SuiteRunner"))
      {
        break;
      }
    }

    final StringBuilder buffer = new StringBuilder();
    buffer.append(t).append(EOL);
    for (int i = 0; i <= lowestOpenDJFrame; i++)
    {
      buffer.append("    ").append(elements[i]).append(EOL);
    }

    final Throwable cause = t.getCause();
    if (cause instanceof InvocationTargetException)
    {
      final InvocationTargetException invocation = ((InvocationTargetException) cause);
      buffer.append("Invocation Target Exception: "
          + getTestngLessStack(invocation));
    }

    return buffer.toString();
  }



  private void initializeProgressVars()
  {
    String prop = System.getProperty(PROPERTY_TEST_PROGRESS);
    if (prop == null)
    {
      return;
    }

    prop = prop.toLowerCase();
    final List<String> progressValues = Arrays.asList(prop
        .split("\\s*\\W+\\s*"));

    if ((prop.length() == 0) || progressValues.isEmpty())
    {
      // Accept the defaults
    }
    else if (progressValues.contains(TEST_PROGRESS_NONE))
    {
      doProgressNone = true;
      doProgressTime = false;
      doProgressTestCount = false;
      doProgressMemory = false;
      doProgressMemoryGcs = false;
      doProgressThreadCount = false;
      doProgressThreadChanges = false;
    }
    else if (progressValues.contains(TEST_PROGRESS_ALL))
    {
      doProgressNone = false;
      doProgressTime = true;
      doProgressTestCount = true;
      doProgressMemory = true;
      doProgressMemoryGcs = true;
      doProgressThreadCount = true;
      doProgressThreadChanges = true;
    }
    else
    {
      doProgressNone = false;
      doProgressTime = progressValues.contains(TEST_PROGRESS_TIME);
      doProgressTestCount = progressValues.contains(TEST_PROGRESS_TEST_COUNT);
      doProgressMemory = progressValues.contains(TEST_PROGRESS_MEMORY);
      doProgressMemoryGcs = progressValues.contains(TEST_PROGRESS_MEMORY_GCS);
      doProgressThreadCount = progressValues
          .contains(TEST_PROGRESS_THREAD_COUNT);
      doProgressThreadChanges = progressValues
          .contains(TEST_PROGRESS_THREAD_CHANGES);

      // If we were asked to do the defaults, then restore anything
      // that's on by default
      if (progressValues.contains(TEST_PROGRESS_DEFAULT))
      {
        doProgressTime = true;
        doProgressTestCount = true;
      }
    }
  }



  private List<String> listAllThreadNames()
  {
    final Thread currentThread = Thread.currentThread();
    ThreadGroup topGroup = currentThread.getThreadGroup();
    while (topGroup.getParent() != null)
    {
      topGroup = topGroup.getParent();
    }

    final Thread threads[] = new Thread[topGroup.activeCount() * 2];
    final int numThreads = topGroup.enumerate(threads);

    final List<String> activeThreads = new ArrayList<String>();
    for (int i = 0; i < numThreads; i++)
    {
      final Thread thread = threads[i];
      if (thread.isAlive())
      {
        final String fullName = thread.getName();
        activeThreads.add(fullName);
      }
    }

    Collections.sort(activeThreads);
    return activeThreads;
  }



  private void onTestFinished(final ITestResult tr)
  {
    // Clear when a test finishes instead before the next one starts
    // so that we get the output generated by any @BeforeClass method
    // etc.
    addTestResult(tr);
  }



  private void outputTestProgress(final Object finishedTestObject)
  {
    if (doProgressNone)
    {
      return;
    }

    printStatusHeaderOnce();

    if (doProgressTime)
    {
      final long curTimeMs = System.currentTimeMillis();
      final long durationSec = (curTimeMs - startTimeMs) / 1000;
      final long durationLastMs = curTimeMs - prevTimeMs;
      System.err.printf("{%2d:%02d (%3.0fs)}  ", (durationSec / 60),
          (durationSec % 60), (durationLastMs / 1000.0));
      prevTimeMs = curTimeMs;
    }

    if (doProgressTestCount)
    {
      System.err.printf("{%3dc %4dm %5di %df}  ", _classResults.size(),
          countTestMethods(), countTotalInvocations(),
          countTestsWithStatus(ITestResult.FAILURE));
    }

    if (doProgressMemory)
    {
      final Runtime runtime = Runtime.getRuntime();
      final long beforeGc = System.currentTimeMillis();
      final int gcs = runGc();
      final long gcDuration = System.currentTimeMillis() - beforeGc;

      final long totalMemory = runtime.totalMemory();
      final long freeMemory = runtime.freeMemory();
      final long curMemInUse = totalMemory - freeMemory;
      final long memDelta = curMemInUse - prevMemInUse;
      final double perMegaByte = 1.0 / (1024.0 * 1024.0);

      maxMemInUse = Math.max(maxMemInUse, curMemInUse);

      System.err.printf("{%5.1fMB  %+5.1fMB}  ", curMemInUse * perMegaByte,
          memDelta * perMegaByte);

      if (doProgressMemoryGcs)
      {
        System.err.printf("{%2d gcs  %4.1fs}  ", gcs, gcDuration / 1000.0);
      }
      prevMemInUse = curMemInUse;
    }

    if (doProgressThreadCount)
    {
      System.err.printf("{#td %3d}  ", Thread.activeCount());
    }

    if (finishedTestObject == null)
    {
      System.err.println(": starting");
    }
    else
    {
      final String abbrClass = packageLessClass(finishedTestObject);
      System.err.printf(": %s ", abbrClass).flush();
      System.err.println();
    }

    if (doProgressThreadChanges)
    {
      final List<String> currentThreads = listAllThreadNames();
      final List<String> newThreads = removeExactly(prevThreads, currentThreads);
      final List<String> oldThreads = removeExactly(currentThreads, prevThreads);

      if (!newThreads.isEmpty())
      {
        System.err.println("  Thread changes:");
        for (int i = 0; i < oldThreads.size(); i++)
        {
          final String threadName = oldThreads.get(i);
          System.err.println("    + " + threadName);
        }
        for (int i = 0; i < newThreads.size(); i++)
        {
          final String threadName = newThreads.get(i);
          System.err.println("    - " + threadName);
        }
      }

      prevThreads = currentThreads;
    }
  }



  private String packageLessClass(final Object obj)
  {
    return obj.getClass().getName().replaceAll(".*\\.", "");
  }



  private synchronized void printStatusHeaderOnce()
  {
    if (statusHeaderPrinted)
    {
      return;
    }
    statusHeaderPrinted = true;

    if (doProgressNone)
    {
      return;
    }

    System.err.println();
    System.err.println("How to read the progressive status info:");

    if (doProgressTime)
    {
      System.err
          .println("  Test duration status: {Total min:sec.  Since last status sec.}");
    }

    if (doProgressTestCount)
    {
      System.err
          .println("  Test count status:  {# test classes  # test methods  # test method invocations  # test failures}.");
    }

    if (doProgressMemory)
    {
      System.err
          .println("  Memory usage status: {MB in use  +/-change since last status}");
    }

    if (doProgressMemoryGcs)
    {
      System.err
          .println("  GCs during status:  {GCs done to settle used memory   time to do it}");
    }

    if (doProgressThreadCount)
    {
      System.err
          .println("  Thread count status:  {#td number of active threads}");
    }

    if (doProgressThreadChanges)
    {
      System.err
          .println("  Thread change status: +/- thread name for new or finished threads since last status");
    }

    System.err.println("  TestClass (the class that just completed)");
    System.err.println();
  }



  /**
   * Removes toRemove from base. If there are duplicate items in base, then only
   * one is removed for each item in toRemove.
   *
   * @return a new List with base with toRemove items removed from it
   */
  private List<String> removeExactly(final List<String> base,
      final List<String> toRemove)
  {
    final List<String> diff = new ArrayList<String>(base);
    for (int i = 0; i < toRemove.size(); i++)
    {
      final String item = toRemove.get(i);
      diff.remove(item);
    }
    return diff;
  }



  private int runGc()
  {
    final Runtime runtime = Runtime.getRuntime();
    int numGcs;
    long curMem = usedMemory();
    long prevMem = Long.MAX_VALUE;
    final StringBuilder gcConvergence = new StringBuilder();
    for (numGcs = 0; (prevMem > curMem) && numGcs < 100; numGcs++)
    {
      runtime.runFinalization();
      runtime.gc();
      Thread.yield();
      Thread.yield();

      prevMem = curMem;
      curMem = usedMemory();

      gcConvergence.append("[" + numGcs + "]: " + (prevMem - curMem)).append(
          "  ");
    }
    return numGcs;
  }



  private long usedMemory()
  {
    final Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }



  private void writeAntTestsFailedMarker(final String outputDirectory)
  {
    // Signal 'ant' that all of the tests passed by removing this
    // special file.
    if ((countTestsWithStatus(ITestResult.FAILURE) == 0)
        && (countTestsWithStatus(ITestResult.SKIP) == 0))
    {
      new File(outputDirectory, ANT_TESTS_FAILED_FILE_NAME).delete();
    }
  }



  private void writeReportToFile(final File reportFile)
  {
    PrintStream reportStream = null;
    try
    {
      reportStream = new PrintStream(new FileOutputStream(reportFile));
    }
    catch (final FileNotFoundException e)
    {
      System.err
          .println("Could not open "
              + reportFile
              + " for writing.  Will write the unit test report to the console instead.");
      e.printStackTrace(System.err);
      reportStream = System.err;
    }

    reportStream.println(center("UNIT TEST REPORT"));
    reportStream.println(center("----------------") + EOL);
    reportStream.println("Finished at: " + (new Date()));
    reportStream.println("# Test classes: " + _classResults.size());
    reportStream.println("# Test classes interleaved: "
        + _classesWithTestsRunInterleaved.size());
    reportStream.println("# Test methods: " + countTestMethods());
    reportStream.println("# Tests passed: "
        + countTestsWithStatus(ITestResult.SUCCESS));
    reportStream.println("# Tests failed: "
        + countTestsWithStatus(ITestResult.FAILURE));
    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL + EOL);
    reportStream.println(center("TEST CLASSES RUN INTERLEAVED"));
    reportStream.println(EOL + EOL);
    for (final Class<?> cls : _classesWithTestsRunInterleaved)
    {
      reportStream.println("  " + cls.getName());
    }

    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL + EOL);
    reportStream.println(center("FAILED TESTS"));
    reportStream.println(EOL + EOL);
    reportStream.println(_bufferedTestFailures);

    reportStream.println(EOL + DIVIDER_LINE + DIVIDER_LINE + EOL);

    reportStream.println(getTimingInfo());

    reportStream.close();

    if ((countTestsWithStatus(ITestResult.FAILURE) == 0)
        && (countTestsWithStatus(ITestResult.SKIP) != 0))
    {
      System.err
          .println("There were no explicit test failures, but some tests were skipped (possibly due to errors in @Before* or @After* methods).");
      System.exit(-1);
    }
  }



  private void writeReportToScreen(final File reportFile)
  {
    // HACK: print out status for the last test object
    outputTestProgress(_lastTestObject);

    final List<ITestResult> failedTests = getFailedTests();
    final StringBuilder failed = new StringBuilder();
    for (int i = 0; i < failedTests.size(); i++)
    {
      final ITestResult failedTest = failedTests.get(i);
      final String fqMethod = getFqMethod(failedTest);
      int numFailures = 1;
      // Peek ahead to see if we had multiple failures for the same
      // method
      // In which case, we list it once with a count of the failures.
      while (((i + 1) < failedTests.size())
          && fqMethod.equals(getFqMethod(failedTests.get(i + 1))))
      {
        numFailures++;
        i++;
      }

      failed.append("  ").append(fqMethod);

      if (numFailures > 1)
      {
        failed.append(" (x " + numFailures + ")");
      }

      failed.append(EOL);
    }

    if (failed.length() > 0)
    {
      System.err.println("The following unit tests failed: ");
      System.err.println(failed);
      System.err.println();
      System.err
          .println("Include the ant option '-Dtest.failures=true' to rerun only the failed tests.");
    }
    else
    {
      System.err.println("All of the tests passed.");
    }

    System.err.println();
    System.err.println("Wrote full test report to:");
    System.err.println(reportFile.getAbsolutePath());
    System.err.println("Test classes run interleaved: "
        + _classesWithTestsRunInterleaved.size());

    // Try to hard to reclaim as much memory as possible.
    runGc();

    System.err.printf("Final amount of memory in use: %.1f MB",
        (usedMemory() / (1024.0 * 1024.0))).println();
    if (doProgressMemory)
    {
      System.err.printf("Maximum amount of memory in use: %.1f MB",
          (maxMemInUse / (1024.0 * 1024.0))).println();
    }
    System.err.println("Final number of threads: " + Thread.activeCount());

    System.err.println();

    if (doProgressThreadChanges)
    {
      System.err.print(threadStacksToString());
    }
  }
}
