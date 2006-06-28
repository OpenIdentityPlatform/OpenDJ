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
package org.opends.server.backends.jeb;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * This class defines a JUnit test suite that will launch all the tests for the
 * classes in the Jeb package.  Whenever a new Jeb test class is defined,
 * this suite should be updated to include it.
 *
 */
public class JebTestSuite
       extends TestCase
{
  /**
   * Retrieves a test suite containing all of the Jeb tests.
   *
   * @return  A test suite containing all of the Jeb tests.
   */
  public static Test suite()
  {
    TestSuite jebSuite = new TestSuite("Jeb Unit Tests");

    jebSuite.addTestSuite(TestJebFormat.class);
    jebSuite.addTestSuite(TestEnvManager.class);
    jebSuite.addTestSuite(TestEntryContainer.class);
    jebSuite.addTestSuite(TestBackendImpl.class);

    return jebSuite;
  }
}

