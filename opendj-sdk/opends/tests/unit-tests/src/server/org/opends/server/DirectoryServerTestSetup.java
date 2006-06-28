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

import java.util.LinkedList;
import java.util.ListIterator;

import junit.extensions.TestSetup;
import junit.framework.Test;

/**
 * This class defines a base JUnit test case that can be used to express
 * test case dependencies which must only be
 * {@link org.opends.server.TestCaseDependency#setUp()} and
 * {@link org.opends.server.TestCaseDependency#tearDown()} once per test
 * case.
 *
 * @author Matthew Swift
 */
public final class DirectoryServerTestSetup extends TestSetup {
  // List of test case dependencies required by each test.
  private LinkedList<TestCaseDependency> dependencies;

  /**
   * Create a directory server test which will execute setup once for
   * all tests in the specified test.
   *
   * @param test
   *          The test.
   */
  public DirectoryServerTestSetup(Test test) {
    super(test);

    this.dependencies = new LinkedList<TestCaseDependency>();
  }

  /**
   * Register a dependency with the test case. The dependency is
   * evaluated only once per test case.
   *
   * @param dependency
   *          The test case dependency.
   */
  public void registerDependency(TestCaseDependency dependency) {
    dependencies.add(dependency);
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
