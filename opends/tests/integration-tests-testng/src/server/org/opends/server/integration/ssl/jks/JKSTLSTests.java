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
package org.opends.server.integration.ssl.jks;

import static org.testng.Assert.*;
import org.testng.annotations.Configuration;
import org.testng.annotations.Parameters;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.ExpectedExceptions;
import org.opends.server.tools.*;

/**
 * This class contains the TestNG tests for the SSL JKS TLS tests.
 */
@Test
public class JKSTLSTests extends JKSTests
{
  //public String jks_cert8 = jks_certdb + "/cert8.db";
  //public String jks_add_args[] = {" ", "-a", "-X", "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};
  //public String jks_add_3args[] = {" ", "-a", "-X", "-P", jks_cert8, "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};
  //public String jks_mod_args[] = {" ", "-X", "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};
  //public String jks_mod_3args[] = {" ", "-X", "-P", jks_cert8, "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};

//public String tls_test_search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-q", "-X", "-b", "dc=example,dc=com", "-s", "base", "\'(objectclass=*)\'"};

  //public String jks_mod_datafiledir = integration_test_home + "/ssl/jks/data";

  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest8" })
  public void testJKSTLSTest1(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 1");
    String datafile = integration_test_home + "/ssl/jks/data/startup/enable_TLS.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
   
    ds_output.redirectOutput(logDir, "JKSTLSTest1.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest1" })
  public void testJKSTLSTest2(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 2");
    String tls_test_search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-q", "-X", "-b", "dc=example,dc=com", "-s", "base", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "JKSTLSTest2.txt"); 
    int retCode = LDAPSearch.mainSearch(tls_test_search_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest2" })
  public void testJKSTLSTest3(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 3");
    String datafile = integration_test_home + "/ssl/jks/data/add/bin_a1_tls_in.ldif";
    String jks_add_args[] = {"-q", "-a", "-X", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest3.txt"); 
    int retCode = LDAPModify.mainModify(jks_add_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
/*
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest3" })
  public void testJKSTLSTest4(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 4");
    String datafile = integration_test_home + "/ssl/jks/data/add/bin_a1_tls_in_2nd.ldif";
    String jks_add_args[] = {"-q", "-a", "-X", "-P", jks_cert8, "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest4.txt"); 
    int retCode = LDAPModify.mainModify(jks_add_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest3" })
  public void testJKSTLSTest5(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 5");
    String datafile = integration_test_home + "/ssl/jks/data/mod/bin_a1_tls_mod.ldif";
    String jks_mod_args[] = {"-q", "-X", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest5.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
/*
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest5" })
  public void testJKSTLSTest6(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 6");
    String datafile = integration_test_home + "/ssl/jks/data/mod/bin_a1_tls_mod_2nd.ldif";
    String jks_mod_args[] = {"-q", "-X", "-P", jks_cert8, "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest6.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest5" })
  public void testJKSTLSTest7(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 7");
    String datafile = integration_test_home + "/ssl/jks/data/del/bin_a1_tls_out.ldif";
    String jks_mod_args[] = {"-q", "-X", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest7.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
/*
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest7" })
  public void testJKSTLSTest8(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 8");
    String datafile = integration_test_home + "/ssl/jks/data/mod/bin_a1_tls_out_2nd.ldif";
    String jks_mod_args[] = {"-q", "-X", "-P", jks_cert8, "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest8.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest7" })
  public void testJKSTLSTest9(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 9");
    String datafile = integration_test_home + "/ssl/jks/data/shutdown/disable_TLS.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};

    ds_output.redirectOutput(logDir, "JKSTLSTest9.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSTLSTests.testJKSTLSTest9" })
  public void testJKSTLSTest10(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 10");
    String tls_test_search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-q", "-X", "-b", "dc=example,dc=com", "-s", "base", "(objectclass=*)"};

    ds_output.redirectOutput(logDir, "JKSTLSTest10.txt"); 
    int retCode = LDAPSearch.mainSearch(tls_test_search_args);
    ds_output.resetOutput();
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

}
