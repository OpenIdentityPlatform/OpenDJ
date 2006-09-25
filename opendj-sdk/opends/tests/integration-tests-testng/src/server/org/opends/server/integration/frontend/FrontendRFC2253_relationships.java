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

/*
    Place suite-specific test information here.
    #@TestSuiteName             Frontend RFC2253 Relationships
    #@TestSuitePurpose          Test the RFC2253 standards regarding realtionships
    #@TestSuiteID               RFC2253 Relationships
    #@TestSuiteGroup            RFC2253 Relationships
    #@TestGroup                 RFC2253 Relationships
    #@TestScript                FrontendRFC2253_relationships.java
    #@TestHTMLLink
*/
/**
 * This class contains the TestNG tests for the Frontend functional tests for relationships in DNs.
 */
@Test
public class FrontendRFC2253_relationships extends FrontendTests
{
/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 1
    #@TestID                    FrontendUserRelationship1
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder;ou=People;o=frontend tests,
                                dc=example,dc=com and filter, objectclass=*. 
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN that contains semicolons.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2252_syntax.testFrontendUserSyntax3" })
  public void testFrontendUserRelationship1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 1");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=jvedder;ou=People;o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship1.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 2
    #@TestID                    FrontendUserRelationship2
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder,ou=People,o=frontend tests,
                                dc=example,dc=com, with extra spaces and filter, objectclass=*. 
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN that contains extra spaces.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship1" })
  public void testFrontendUserRelationship2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 2");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", " uid=jvedder, ou=People,  o=frontend tests ,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship2.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 3
    #@TestID                    FrontendUserRelationship3
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN that contains quote marks.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship2" })
  public void testFrontendUserRelationship3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 3");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder\",ou=People,o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship3.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 4
    #@TestID                    FrontendUserRelationship4
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder, and Sons,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks  and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN that contains quote marks
 *  and a comma.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship3" })
  public void testFrontendUserRelationship4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 4");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder, and Sons\", ou=People, o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship4.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 5
    #@TestID                    FrontendUserRelationship5
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder=superguy,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks  and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN where the lowest level contains an equal sign.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship4" })
  public void testFrontendUserRelationship5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 5");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder=superguy\", ou=People, o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship5.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 6
    #@TestID                    FrontendUserRelationship6
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder+football=superguy,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks  and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN where the lowest level contains an equal sign 
 *  and a plus sign.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship5" })
  public void testFrontendUserRelationship6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 6");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder+football=superguy\", ou=People, o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship6.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 7
    #@TestID                    FrontendUserRelationship7
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder>Sons,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN where the lowest level contains 
 *  a greater-than sign.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship6" })
  public void testFrontendUserRelationship7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 7");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder>Sons\", ou=People, o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship7.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 8
    #@TestID                    FrontendUserRelationship8
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder[less-than-sign]Boss,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN that contains an equal sign 
 *  and a less-than sign.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship7" })
  public void testFrontendUserRelationship8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 8");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder<boss\", ou=People, o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship8.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 9
    #@TestID                    FrontendUserRelationship9
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder#Sons,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN where the lowest level contains an oglethorpe.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship8" })
  public void testFrontendUserRelationship9(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 9");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder#Sons\", ou=People, o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship9.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
    Place test-specific test information here.
    The tag, TestMarker, be the same as the marker, TestSuiteName.
    #@TestMarker                Frontend RFC2253 Relationships
    #@TestName                  Frontend User Relationship 10
    #@TestID                    FrontendUserRelationship10
    #@TestPreamble
    #@TestSteps                 Client calls static method LDAPSearch.mainSearch()
                                with base DN, uid=jvedder;Sons,ou=People,o=frontend tests,
                                dc=example,dc=com, with quote marks and filter, objectclass=*.
    #@TestPostamble
    #@TestResult                Success if the static method, LDAPSearch.mainSearch(), returns 0
*/
/**
 *  Search for entries that have a base DN where the lowest level contains a semicolon.
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
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.frontend.FrontendRFC2253_relationships.testFrontendUserRelationship9" })
  public void testFrontendUserRelationship10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 10");
    String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", "uid=\"jvedder;Sons\", ou=People, o=frontend tests,dc=example,dc=com", "objectclass=*"};

    ds_output.redirectOutput(logDir, "FrontendUserRelationship10.txt");
    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
