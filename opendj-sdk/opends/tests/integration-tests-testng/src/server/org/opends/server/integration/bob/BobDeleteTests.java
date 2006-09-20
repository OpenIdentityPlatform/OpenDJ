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
    #@TestSuiteName             Bob Delete Tests
    #@TestSuitePurpose          Test the delete functionality for OpenDS
    #@TestSuiteID               Delete Tests
    #@TestSuiteGroup            Bob
    #@TestGroup                 Bob/BobDeleteTests
    #@TestScript                BobDeleteTests.java
    #@TestHTMLLink              blahblah
*/
/**
 * This class contains the TestNG tests for the Bob deletes.
 */
@Test
public class BobDeleteTests extends BobTests
{
/**
 *  Delete an existing entry using ldapmodify.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobModrdnTests.testBobModrdn3" })
  public void testBobDelete1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 1");
    String datafile = integration_test_home + "/bob/data/del/a1_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest1.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete a non-existent entry using ldapmodify by trying to delete the entry that
 *  was deleted in the last step.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete1" })
  public void testBobDelete2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 2");
    String datafile = integration_test_home + "/bob/data/del/a1_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest2.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete an entry that has a bad DN using ladpmodify.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete2" })
  public void testBobDelete3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 3");
    String datafile = integration_test_home + "/bob/data/del/a2_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest3.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete five existing entries using ldapmodify.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete3" })
  public void testBobDelete4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 4");
    String datafile = integration_test_home + "/bob/data/del/a3_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest4.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete five existing entries using ldapmodify, the fourth of which
 *  has a bad DN.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete4" })
  public void testBobDelete5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 5");
    String datafile = integration_test_home + "/bob/data/del/a4_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest5.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete an existing entry using ldapmodify with an ldif file that
 *  improperly has the format for ldapdelete.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete5" })
  public void testBobDelete6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 6");
    String datafile = integration_test_home + "/bob/data/del/a5_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest6.txt");
    int retCode = LDAPModify.mainModify(bob_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete an existing entry using ldapdelete.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete6" })
  public void testBobDelete7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 7");
    String datafile = integration_test_home + "/bob/data/del/b1_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest7.txt");
    int retCode = LDAPDelete.mainDelete(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete a non-existent entry using ldapdelete by trying to delete the entry that
 *  was deleted in the last step.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete7" })
  public void testBobDelete8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 8");
    String datafile = integration_test_home + "/bob/data/del/b1_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest8.txt");
    int retCode = LDAPDelete.mainDelete(bob_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete an entry that has a bad DN using ldapdelete.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete8" })
  public void testBobDelete9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 9");
    String datafile = integration_test_home + "/bob/data/del/b2_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest9.txt");
    int retCode = LDAPDelete.mainDelete(bob_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete five existing entries using ldapdelete.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete9" })
  public void testBobDelete10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 10");
    String datafile = integration_test_home + "/bob/data/del/b3_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest10.txt");
    int retCode = LDAPDelete.mainDelete(bob_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete five existing entries using ldapdelete, the fourth of which
 *  has a bad DN.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete10" })
  public void testBobDelete11(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 11");
    String datafile = integration_test_home + "/bob/data/del/b4_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest11.txt");
    int retCode = LDAPDelete.mainDelete(bob_args);
    ds_output.resetOutput();
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

/**
 *  Delete an existing entry using ldapdelete with a file that
 *  improperly has the format for ldapmodify.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.bob.BobDeleteTests.testBobDelete11" })
  public void testBobDelete12(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 12");
    String datafile = integration_test_home + "/bob/data/del/b5_out.ldif";
    String bob_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "BobDeleteTest12.txt");
    int retCode = LDAPDelete.mainDelete(bob_args);
    ds_output.resetOutput();
    int expCode = 34;

    compareExitCode(retCode, expCode);
  }

}
