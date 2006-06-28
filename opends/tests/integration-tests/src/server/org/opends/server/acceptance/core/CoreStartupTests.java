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
package org.opends.server.acceptance.core;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the Core startup.
 */
public class CoreStartupTests extends DirectoryServerAcceptanceTestCase
{
  public String core_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String core_startup_datafiledir = acceptance_test_home + "/core/data";

  public CoreStartupTests(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();
  }

  public void tearDown() throws Exception
  {
    super.tearDown();
  }

/**
 *  Setup for core tests
*/
  public void testCoreStartup1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Startup test 1");
    String datafile = core_startup_datafiledir + "/core_start.ldif";
    core_args[10] = datafile; 

    int retCode = LDAPModify.mainModify(core_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreStartup2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core Startup test 2");
    String datafile = core_startup_datafiledir + "/core_test_1K.ldif";
    core_args[10] = datafile; 

    int retCode = LDAPModify.mainModify(core_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
