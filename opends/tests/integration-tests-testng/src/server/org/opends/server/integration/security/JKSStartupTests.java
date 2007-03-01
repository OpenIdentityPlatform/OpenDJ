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
package org.opends.server.integration.security;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Security Setup
    #@TestSuitePurpose          Setup methods for the Security test suites
    #@TestSuiteID               Security Setup
    #@TestSuiteGroup            Security Setup
    #@TestGroup                 Security Setup
    #@TestScript                JKSStartupTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the SSL JKS startup.
 */
@Test
public class JKSStartupTests extends JKSTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Setup
    #@TestName                  JKS Setup 1
    #@TestID                    JKSSetup1
    #@TestPreamble
    #@TestSteps                 The script, generate_server_cert.sh in unix
				systems, and the script, generate_server_cert.bat
				in Windows systems are called by Runtime.exec().
				The server certificate is generated and copied 
				to [opends_home]/config directory.
    #@TestPostamble
    #@TestResult                 -
*/
/**
 *  Generate the server certificate and copy it to the config subdirectory
 *  in the OpenDS installation.
 *
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  dsee_home              The home directory for the OpenDS
 *                                 installation.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "integration_test_home", "dsee_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.core.CoreEntryCacheTests.testCoreEntryCache6" })
  public void testJKSStartup1(String integration_test_home, String dsee_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 1");
   
    String osName = new String(System.getProperty("os.name"));
      
    if (osName.indexOf("Windows") >= 0)  // For Windows
    {
      String exec_cmd = "CMD /C " + integration_test_home + "\\security\\generate_server_cert";
      Runtime rtime = Runtime.getRuntime();
      Process child = rtime.exec(exec_cmd);
      child.waitFor();

      exec_cmd = "CMD /C copy " + integration_test_home + "\\..\\..\\..\\..\\..\\..\\keystore " + dsee_home + "\\config";
      rtime = Runtime.getRuntime();
      child = rtime.exec(exec_cmd);
      child.waitFor();

      exec_cmd = "CMD /C cd " + dsee_home;
      rtime = Runtime.getRuntime();
      child = rtime.exec(exec_cmd);
      child.waitFor();

    }
    else  // all other unix systems
    {
      String exec_cmd = "cd " + integration_test_home;
      Runtime rtime = Runtime.getRuntime();
      Process child = rtime.exec(exec_cmd);
      child.waitFor();

      exec_cmd = "chmod +x " + integration_test_home + "/security/generate_server_cert.sh";
      rtime = Runtime.getRuntime();
      child = rtime.exec(exec_cmd);
      child.waitFor();

      exec_cmd = integration_test_home + "/security/generate_server_cert.sh";
      rtime = Runtime.getRuntime();
      child = rtime.exec(exec_cmd);
      child.waitFor();

      exec_cmd = "cp " + "keystore " + dsee_home + "/config";
      rtime = Runtime.getRuntime();
      child = rtime.exec(exec_cmd);
      child.waitFor();

      exec_cmd = "cd " + dsee_home;
      rtime = Runtime.getRuntime();
      child = rtime.exec(exec_cmd);
      child.waitFor();
    }
    
    compareExitCode(0, 0);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Setup
    #@TestName                  JKS Setup 2
    #@TestID                    JKSSetup2
    #@TestPreamble
    #@TestSteps                 Client calls LDAPModify.mainModify() with the
				filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Modify the entry, cn=Key Manager Provider,cn=SSL,cn=config.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSStartupTests.testJKSStartup1" })
  public void testJKSStartup2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 2");
    String datafile = integration_test_home + "/security/data/startup/enable_key_mgr_provider.ldif";
    String jks_mod_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSStartup2.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Setup
    #@TestName                  JKS Setup 3 prep
    #@TestID                    JKSSetup3prep
    #@TestPreamble
    #@TestSteps                 Client calls LDAPModify.mainModify() with the
				filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Delete the default entry, cn=Trust Manager Provider,cn=SSL,cn=config.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSStartupTests.testJKSStartup2" })
  public void testJKSStartup3_prep(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 3 prep");
    String datafile = integration_test_home + "/security/data/startup/deleteTrustMgr.ldif";
    String jks_mod_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSStartup3_prep.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Setup
    #@TestName                  JKS Setup 3
    #@TestID                    JKSSetup3
    #@TestPreamble
    #@TestSteps                 Client calls LDAPModify.mainModify() with the
				filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add the new entry, cn=Trust Manager Provider,cn=SSL,cn=config.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSStartupTests.testJKSStartup3_prep" })
  public void testJKSStartup3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 3");
    String datafile = integration_test_home + "/security/data/startup/enable_trust_mgr_provider.ldif";
    String jks_add_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSStartup3.txt"); 
    int retCode = LDAPModify.mainModify(jks_add_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Setup
    #@TestName                  JKS Setup 4
    #@TestID                    JKSSetup4
    #@TestPreamble
    #@TestSteps                 Client calls LDAPModify.mainModify() with the
				filename to the appropriate file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add the entry, cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSStartupTests.testJKSStartup3" })
  public void testJKSStartup4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 4");
    String datafile = integration_test_home + "/security/data/startup/enable_ldaps_conn_handler.ldif";
    String jks_add_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSStartup4.txt"); 
    int retCode = LDAPModify.mainModify(jks_add_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Security Setup
    #@TestName                  JKS Setup 5
    #@TestID                    JKSSetup5
    #@TestPreamble
    #@TestSteps                 Client calls LDAPSearch.mainSearch() with the
				port 636 and with -Z and -X extra parameters.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add the entry, cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config.
 *
 *  @param  hostname               The hostname for the server where OpenDS
 *                                 is installed.
 *  @param  sport                  The ssl port number for OpenDS.
 *  @param  bindDN                 The bind DN.
 *  @param  bindPW                 The password for the bind DN.
 *  @param  integration_test_home  The home directory for the Integration
 *                                 Test Suites.
 *  @param  logDir                 The directory for the log files that are
 *                                 generated during the Integration Tests.
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.security.JKSStartupTests.testJKSStartup4" })
  public void testJKSStartup5(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 5");
    String jks_test_search_args[] = {"-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-Z", "-X", "-b", "dc=example,dc=com", "-s", "base", "\'(objectclass=*)\'"};

    ds_output.redirectOutput(logDir, "JKSStartup5.txt"); 
    int retCode = LDAPSearch.mainSearch(jks_test_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
