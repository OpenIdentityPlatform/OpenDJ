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
package org.opends.server.integration.schema;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;
import java.io.*;

/*
    Place suite-specific test information here.
    #@TestSuiteName             Schema RFC Tests
    #@TestSuitePurpose          Perform rfc-specific schema tests
    #@TestSuiteID               Schema RFC tests
    #@TestSuiteGroup            Schema
    #@TestGroup                 Schema
    #@TestScript                SchemaRFCTests.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Schema RFC tests.
 */
@Test
public class SchemaRFCTests extends SchemaTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 1
    #@TestID                    Schema RFC Test 1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that has a 
				labeledURI attribute.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 2079.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.schema.SchemaStartupTests.testSchemaStartup1" })
  public void testSchemaRFC1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 1");
    String datafile = integration_test_home + "/schema/data/rfc2079.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC1.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 2
    #@TestID                    Schema RFC Test 2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses
				dcObject objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 2247.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC1" })
  public void testSchemaRFC2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 2");
    String datafile = integration_test_home + "/schema/data/rfc2247_1.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC2.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 3
    #@TestID                    Schema RFC Test 3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses
				domain objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 2247.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC2" })
  public void testSchemaRFC3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 3");
    String datafile = integration_test_home + "/schema/data/rfc2247_2.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC3.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 4
    #@TestID                    Schema RFC Test 4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses
				dcObject objectclass, but has no structural
				objectclass which is required.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is covered by rfc 2247.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC3" })
  public void testSchemaRFC4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 4");
    String datafile = integration_test_home + "/schema/data/rfc2247_3.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC4.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 5
    #@TestID                    Schema RFC Test 5
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses
				dcObject objectclass, but lacks required attributes.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is covered by rfc 2247.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC4" })
  public void testSchemaRFC5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 5");
    String datafile = integration_test_home + "/schema/data/rfc2247_4.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC5.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 6
    #@TestID                    Schema RFC Test 6
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses
				dcObject objectclass, but lacks required attributes
				and lacks a required structural objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is covered by rfc 2247.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC5" })
  public void testSchemaRFC6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 6");
    String datafile = integration_test_home + "/schema/data/rfc2247_5.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC6.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 7
    #@TestID                    Schema RFC Test 7
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains entries that use
				uid=, dc=, and cn= in the rdn.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 2377.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC6" })
  public void testSchemaRFC7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 7");
    String datafile = integration_test_home + "/schema/data/rfc2377.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC7.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 8
    #@TestID                    Schema RFC Test 8
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains entries that use
				inetorgperson objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 2798.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC7" })
  public void testSchemaRFC8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 8");
    String datafile = integration_test_home + "/schema/data/rfc2798.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC8.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 9
    #@TestID                    Schema RFC Test 9
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				which modifies the vendorName attribute. 
				The user should not be able to modify this attribute.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 53
*/
/**
 *  Modify an entry that is covered by rfc 3045.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC8" })
  public void testSchemaRFC9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 9");
    String datafile = integration_test_home + "/schema/data/rfc3045_1.ldif";
    String schema_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC9.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 53;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 10
    #@TestID                    Schema RFC Test 10
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				which modifies the vendorVersion attribute. 
				The user should not be able to modify this attribute.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 53
*/
/**
 *  Modify an entry that is covered by rfc 3045.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC9" })
  public void testSchemaRFC10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 10");
    String datafile = integration_test_home + "/schema/data/rfc3045_2.ldif";
    String schema_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC10.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 53;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 11
    #@TestID                    Schema RFC Test 11
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses uddiBusinessEntity objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 4403.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC10" })
  public void testSchemaRFC11(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 11");
    String datafile = integration_test_home + "/schema/data/rfc4403_1.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC11.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 12
    #@TestID                    Schema RFC Test 12
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses uddiBusinessService objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 4403.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC11" })
  public void testSchemaRFC12(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 12");
    String datafile = integration_test_home + "/schema/data/rfc4403_2.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC12.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 13
    #@TestID                    Schema RFC Test 13
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses uddiBusinessService objectclass
				but violates the DIT structure rule.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is covered by rfc 4403.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC12" })
  public void testSchemaRFC13(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 13");
    String datafile = integration_test_home + "/schema/data/rfc4403_3.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC13.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 14
    #@TestID                    Schema RFC Test 14
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses uddiContact objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by rfc 4403.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC13" })
  public void testSchemaRFC14(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 14");
    String datafile = integration_test_home + "/schema/data/rfc4403_4.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC14.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 15
    #@TestID                    Schema RFC Test 15
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses uddiContact objectclass,
				but violates the DIT structure rule.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is covered by rfc 4403.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC14" })
  public void testSchemaRFC15(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 15");
    String datafile = integration_test_home + "/schema/data/rfc4403_5.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC15.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 16
    #@TestID                    Schema RFC Test 16
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses ipHost and device objectclasses.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-howard-rfc2307bis.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC15" })
  public void testSchemaRFC16(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 16");
    String datafile = integration_test_home + "/schema/data/rfc2307bis_1.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC16.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 17
    #@TestID                    Schema RFC Test 17
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses ipHost and device objectclasses,
				but required attributes are missing.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is violates draft-howard-rfc2307bis.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC16" })
  public void testSchemaRFC17(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 17");
    String datafile = integration_test_home + "/schema/data/rfc2307bis_2.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC17.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 18
    #@TestID                    Schema RFC Test 18
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses ipHost, device and bootabledevice
				objectclasses.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-howard-rfc2307bis.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC17" })
  public void testSchemaRFC18(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 18");
    String datafile = integration_test_home + "/schema/data/rfc2307bis_3.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC18.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 19
    #@TestID                    Schema RFC Test 19
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses ipHost and device objectclasses,
				but contains attributes for bootabledevice objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is covered by draft-howard-rfc2307bis.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC18" })
  public void testSchemaRFC19(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 19");
    String datafile = integration_test_home + "/schema/data/rfc2307bis_4.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC19.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 20
    #@TestID                    Schema RFC Test 20
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses untypedobject objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-furseth-ldap-untypedobject.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC19" })
  public void testSchemaRFC20(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 20");
    String datafile = integration_test_home + "/schema/data/untypedobject.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC20.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 21
    #@TestID                    Schema RFC Test 21
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses changeLogEntry objectclass. The
				change type for the log is add.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-good-ldap-changelog.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC20" })
  public void testSchemaRFC21(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 21");
    String datafile = integration_test_home + "/schema/data/changelog_1.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC21.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 22
    #@TestID                    Schema RFC Test 22
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses changeLogEntry objectclass. The
				change type for the log is delete.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-good-ldap-changelog.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC21" })
  public void testSchemaRFC22(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 22");
    String datafile = integration_test_home + "/schema/data/changelog_2.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC22.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 23
    #@TestID                    Schema RFC Test 23
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses changeLogEntry objectclass. The
				change type for the log is modify.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-good-ldap-changelog.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC22" })
  public void testSchemaRFC23(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 23");
    String datafile = integration_test_home + "/schema/data/changelog_3.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC23.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 24
    #@TestID                    Schema RFC Test 24
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses namedObject objectclass. 
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-howard-namedobject.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC23" })
  public void testSchemaRFC24(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 24");
    String datafile = integration_test_home + "/schema/data/namedobject_1.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC24.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 25
    #@TestID                    Schema RFC Test 25
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses namedObject objectclass, 
				and contains other optional attributes. 
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-howard-namedobject.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC24" })
  public void testSchemaRFC25(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 25");
    String datafile = integration_test_home + "/schema/data/namedobject_2.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC25.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 26
    #@TestID                    Schema RFC Test 26
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses ldapSubEntry objectclass. 
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-ietf-ldup-subentry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC25" })
  public void testSchemaRFC26(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 26");
    String datafile = integration_test_home + "/schema/data/ldup_subentry_1.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC26.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 27
    #@TestID                    Schema RFC Test 27
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses ldapSubEntry and posixGroup objectclasses. 
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-ietf-ldup-subentry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC26" })
  public void testSchemaRFC27(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 27");
    String datafile = integration_test_home + "/schema/data/ldup_subentry_2.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC27.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 28
    #@TestID                    Schema RFC Test 28
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry
				that uses ldapSubEntry and posixGroup objectclasses,
				but contains an attribute defined as NO_USER_MODIFICATION. 
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 53
*/
/**
 *  Add an entry that is covered by draft-ietf-ldup-subentry.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC27" })
  public void testSchemaRFC28(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 28");
    String datafile = integration_test_home + "/schema/data/ldup_subentry_3.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC28.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 53;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 29
    #@TestID                    Schema RFC Test 29
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Disable schema checking.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC28" })
  public void testSchemaRFC29(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 29");
    String datafile = integration_test_home + "/schema/data/disable_schema_checking.ldif";
    String schema_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC29.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 30
    #@TestID                    Schema RFC Test 30
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Enable schema checking.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC29" })
  public void testSchemaRFC30(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 30");
    String datafile = integration_test_home + "/schema/data/enable_schema_checking.ldif";
    String schema_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC30.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 31
    #@TestID                    Schema RFC Test 31
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Disable syntax checking.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC30" })
  public void testSchemaRFC31(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 31");
    String datafile = integration_test_home + "/schema/data/disable_syntax_checking.ldif";
    String schema_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC31.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 32
    #@TestID                    Schema RFC Test 32
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Enable syntax checking.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC31" })
  public void testSchemaRFC32(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 32");
    String datafile = integration_test_home + "/schema/data/enable_syntax_checking.ldif";
    String schema_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC32.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 33
    #@TestID                    Schema RFC Test 33
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses document
				objectclass.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-ietf-zeilenga-ldap-cosine.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC32" })
  public void testSchemaRFC33(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 33");
    String datafile = integration_test_home + "/schema/data/ldap_cosine_1.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC33.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 34
    #@TestID                    Schema RFC Test 34
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses document
				objectclass, and contains additional optional
				attributes.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 0
*/
/**
 *  Add an entry that is covered by draft-ietf-zeilenga-ldap-cosine.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC33" })
  public void testSchemaRFC34(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 34");
    String datafile = integration_test_home + "/schema/data/ldap_cosine_2.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC34.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, must be present and must be the same as the marker, TestSuiteName.
    #@TestMarker                Schema RFC Tests
    #@TestName                  Schema RFC Test 35
    #@TestID                    Schema RFC Test 35
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPModify.mainModify()
                                with the filename to the appropriate ldif file.
				The ldif file contains an entry that uses document
				objectclass, but is missing required attributes.
    #@TestPostamble
    #@TestResult                Success if OpenDS returns 65
*/
/**
 *  Add an entry that is covered by draft-ietf-zeilenga-ldap-cosine.
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
  @Test(alwaysRun=true, dependsOnMethods = { "testSchemaRFC34" })
  public void testSchemaRFC35(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 35");
    String datafile = integration_test_home + "/schema/data/ldap_cosine_3.ldif";
    String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "SchemaRFC35.txt");
    int retCode = LDAPModify.mainModify(schema_args);
    ds_output.resetOutput();
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

}
