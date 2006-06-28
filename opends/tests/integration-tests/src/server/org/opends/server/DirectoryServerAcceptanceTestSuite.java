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

import junit.framework.*;
import org.opends.server.acceptance.bob.BobTestSuite;
import org.opends.server.acceptance.core.CoreTestSuite;
import org.opends.server.acceptance.backend.BackendTestSuite;
import org.opends.server.acceptance.frontend.FrontendTestSuite;
import org.opends.server.acceptance.ssl.jks.JKSTestSuite;
import org.opends.server.acceptance.schema.SchemaTestSuite;

public class DirectoryServerAcceptanceTestSuite
       extends TestCase
{
  public static Test suite()
  {
    TestSuite directorySuite = new TestSuite("DirectoryServer Acceptance Tests");

    directorySuite.addTest(BobTestSuite.suite());
    directorySuite.addTest(CoreTestSuite.suite());
    directorySuite.addTest(FrontendTestSuite.suite());
    directorySuite.addTest(JKSTestSuite.suite());
    directorySuite.addTest(SchemaTestSuite.suite());
    directorySuite.addTest(BackendTestSuite.suite());

    return directorySuite;
  }

  public static void main(String[] args) 
  {
    junit.textui.TestRunner.run(suite());
  }
}

