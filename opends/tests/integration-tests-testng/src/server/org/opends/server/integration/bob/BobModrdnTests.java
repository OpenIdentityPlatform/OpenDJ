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
package org.opends.server.integration.bob;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Bob Modify RDN Tests
    #@TestSuitePurpose          Test the modify rdn functionality for OpenDS
    #@TestSuiteID               Modify RDN Tests
    #@TestSuiteGroup            Bob
    #@TestGroup                 Bob/BobModifyRDNTests
    #@TestScript                BobModrdnTests.java
    #@TestHTMLLink              blahblah
*/
/**
 * This class contains the TestNG tests for the Bob modify rdns.
 */
@Test
public class BobModrdnTests extends BobTests
{
/**
 *  Replace the RDN of an existing entry with a short RDN.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobModTests.testBobMod28" })
  public void testBobModrdn1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify rdn test 1");
    String datafile = integration_test_home + "/bob/data/modrdn/a1_modrdn.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobModrdnTest1.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Replace the RDN of an existing entry with a long RDN.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobModrdnTests.testBobModrdn1" })
  public void testBobModrdn2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify rdn test 2");
    String datafile = integration_test_home + "/bob/data/modrdn/a2_modrdn.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobModrdnTest2.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Replace the RDN of an existing entry with a very long RDN.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  port                   The port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobModrdnTests.testBobModrdn2" })
  public void testBobModrdn3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify rdn test 3");
    String datafile = integration_test_home + "/bob/data/modrdn/a3_modrdn.ldif";
    String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobModrdnTest3.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
}
