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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

/**
 * This class defines a base test case that should be subclassed by all
 * unit tests used by the Directory Server.
 * <p>
 * This class adds the ability to print error messages and automatically
 * have them include the class name.
 */
public abstract class DirectoryServerTestCase {
  @BeforeSuite
  public final void suppressOutput() {
    TestCaseUtils.suppressOutput();
  }

  @AfterSuite
  public final void shutdownServer() {
    TestCaseUtils.shutdownServer("The current test suite has finished.");
    TestCaseUtils.unsupressOutput();
  }
}
