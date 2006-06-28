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

/**
 * An abstract class used to implement test case dependencies.
 * <p>
 * A test case should create dependencies and initialize them during its
 * {@link junit.framework.TestCase#setUp()} phase. Dependencies should
 * then be finalized during the
 * {@link junit.framework.TestCase#tearDown()} phase.
 *
 * @author Matthew Swift
 */
public abstract class TestCaseDependency {

  /**
   * Create a new abstract test case dependency.
   */
  protected TestCaseDependency() {
    // No implementation required.
  }

  /**
   * Initialize the test case dependency.
   *
   * @throws Exception
   *           If the dependency could not be initialized.
   */
  public abstract void setUp() throws Exception;

  /**
   * Clean up resources owned by the test case dependency.
   *
   * @throws Exception
   *           If clean up failed for some reason.
   */
  public void tearDown() throws Exception {
    // No-op default implementation.
  }

}
