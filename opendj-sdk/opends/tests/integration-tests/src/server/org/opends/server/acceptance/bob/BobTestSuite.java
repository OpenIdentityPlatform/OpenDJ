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
package org.opends.server.acceptance.bob;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * This class defines a JUnit test suite that will launch all the tests for the
 * classes in the Bob Test Suite package.  Whenever a new Bob test class is defined,
 * this suite should be updated to include it.
 */
public class BobTestSuite
       extends TestCase
{
  /**
   * Retrieves a test suite containing all of the Bob tests.
   *
   * @return  A test suite containing all of the Bob tests.
   */
  public static Test suite()
  {
    TestSuite bobSuite = new TestSuite("Bob Functional Tests");

    bobSuite.addTestSuite(BobStartupTests.class);
    bobSuite.addTestSuite(BobAddTests.class);
    bobSuite.addTestSuite(BobModTests.class);
    bobSuite.addTestSuite(BobModrdnTests.class);
    bobSuite.addTestSuite(BobDeleteTests.class);

    return bobSuite;
  }
}

