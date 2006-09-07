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
package org.opends.server.integration.frontend;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/**
 * This class contains the TestNG tests for the Frontend functional tests for standards.
 */
@Test
public class FrontendRFC2252_standards extends FrontendTests
{
/**
 *  Verify the existence of the attribute, createTimestamp, 
 *  for an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2251_deletes.testFrontendUserDelete3_checkentries" })
  public void testFrontendUserStandard1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 1");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "createTimestamp:2006", "uid=jreuter,ou=People,o=frontend tests,dc=example,dc=com"};
  
    ds_output.redirectOutput(logDir, "FrontendUserStandard1.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Verify the existence of the attribute, creatorsName, 
 *  for an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2252_standards.testFrontendUserStandard1" })
  public void testFrontendUserStandard2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 2");
    String frontend_compare_args[] = {"-h", hostname, "-p", port, "creatorsName:cn=Directory Manager,cn=Root DNs,cn=config", "uid=jreuter,ou=People,o=frontend tests,dc=example,dc=com"};
  
    ds_output.redirectOutput(logDir, "FrontendUserStandard2.txt");
    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Verify the existence of the attribute, modifyTimestamp, 
 *  for an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2252_standards.testFrontendUserStandard2" })
  public void testFrontendUserStandard3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 3");
    String datafile = integration_test_home + "/frontend/data/standards/standards_1.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserStandard3_mod.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();

    if(retCode==0)
    {
      String frontend_compare_args[] = {"-h", hostname, "-p", port, "modifyTimestamp:2006", "uid=jreuter,ou=People,o=frontend tests,dc=example,dc=com"};
  
      ds_output.redirectOutput(logDir, "FrontendUserStandard3_com.txt");
      retCode = LDAPCompare.mainCompare(frontend_compare_args);
	ds_output.resetOutput();
    }
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/**
 *  Verify the existence of the attribute, modifiersName, 
 *  for an existing entry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2252_standards.testFrontendUserStandard3" })
  public void testFrontendUserStandard4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 4");
    String datafile = integration_test_home + "/frontend/data/standards/standards_2.ldif";
    String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", datafile};

    ds_output.redirectOutput(logDir, "FrontendUserStandard4_mod.txt");
    int retCode = LDAPModify.mainModify(frontend_mod_args);
    ds_output.resetOutput();

    if(retCode==0)
    {
      String frontend_compare_args[] = {"-h", hostname, "-p", port, "modifiersName:cn=Directory Manager,cn=Root DNs,cn=config", "uid=jreuter,ou=People,o=frontend tests,dc=example,dc=com"};
  
      ds_output.redirectOutput(logDir, "FrontendUserStandard4_com.txt");
      retCode = LDAPCompare.mainCompare(frontend_compare_args);
 	ds_output.resetOutput();
    }
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
