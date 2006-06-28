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

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.ListIterator;

import junit.framework.TestCase;

/**
 * This class defines a base JUnit test case that should be subclassed
 * by all unit tests used by the Directory Server.
 * <p>
 * This class offers two major benefits over
 * {@link junit.framework.TestCase}:
 * <ul>
 * <li>It adds the ability to print error messages and automatically
 * have them include the class name
 * <li>It is possible to register dependencies which are initialized
 * during the {@link #setUp()} phase and finalized during the
 * {@link #tearDown()} phase of each test.
 * </ul>
 *
 * @see org.opends.server.DirectoryServerTestSetup
 * @author Neil A. Wilson
 */
public abstract class DirectoryServerTestCase extends TestCase {
  // The print stream to use for printing error messages.
  private PrintStream errorStream;

  // List of test case dependencies required by each test.
  private LinkedList<TestCaseDependency> dependencies;

  /**
   * Creates a new instance of this JUnit test case with the provided
   * name.
   *
   * @param name
   *          The name to use for this JUnit test case.
   */
  protected DirectoryServerTestCase(String name) {
    super(name);

    this.errorStream = System.err;
    this.dependencies = new LinkedList<TestCaseDependency>();
  }

  /**
   * Register a dependency with the test case.
   *
   * @param dependency
   *          The test case dependency.
   */
  public final void registerDependency(TestCaseDependency dependency) {
    dependencies.add(dependency);
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

  /**
   * {@inheritDoc}
   */
  @Override
  protected void setUp() throws Exception {
    // Initialize each dependency in order (oldest first).
    for (TestCaseDependency dependency : dependencies) {
      dependency.setUp();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void tearDown() throws Exception {
    // Clean up each dependency in reverse order (newest first).
    int size = dependencies.size();

    if (size != 0) {
      ListIterator<TestCaseDependency> i = dependencies.listIterator(size);
      while (i.hasPrevious()) {
        i.previous().tearDown();
      }
    }
  }

}
