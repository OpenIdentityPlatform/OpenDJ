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
 * This class contains the TestNG tests for the SSL JKS bob tests.
 */
@Test
public class JKSBobTests extends JKSTests
{
  //public String jks_cert8 = jks_certdb + "/cert8.db";
  //public String jks_add_args[] = {"-a", "-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", " "};
  //public String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", " "};
  //public String jks_mod_3args[] = {"-Z", "-X", "-P", jks_cert8, "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", " "};
  //public String jks_mod_datafiledir = integration_test_home + "/ssl/jks/data";
  
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSStartupTests.testJKSStartup5" })
  public void testJKSBobTest1(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 1");
    String datafile = integration_test_home + "/ssl/jks/data/jks_startup.ldif";
    String jks_add_args[] = {"-a", "-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest1.txt"); 
    int retCode = LDAPModify.mainModify(jks_add_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest1" })
  public void testJKSBobTest2(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 2");
    String datafile = integration_test_home + "/ssl/jks/data/modrdn/a1_modrdn.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest2.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
/*
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest2" })
  public void testJKSBobTest3(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 3");
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/modrdn/a1_modrdn_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest2" })
  public void testJKSBobTest4(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 4");
    String datafile = integration_test_home + "/ssl/jks/data/add/bin_a1_in.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest4.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
/*
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest4" })
  public void testJKSBobTest5(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 5");
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/add/bin_a1_in_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest4" })
  public void testJKSBobTest6(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 6");
    String datafile = integration_test_home + "/ssl/jks/data/mod/bin_a1_mod.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    ds_output.redirectOutput(logDir, "JKSBobTest6.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
/*
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest6" })
  public void testJKSBobTest7(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 7");
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/mod/bin_a1_mod_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
*/
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest6" })
  public void testJKSBobTest8(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 8");
    String datafile = integration_test_home + "/ssl/jks/data/del/bin_a1_out.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
   
    ds_output.redirectOutput(logDir, "JKSBobTest8.txt"); 
    int retCode = LDAPModify.mainModify(jks_mod_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
/*
  @Parameters({ "hostname", "sport", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSBobTests.testJKSBobTest8" })
  public void testJKSBobTest9(String hostname, String sport, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 9");
    datafile = integration_test_home + "/ssl/jks/data/del/bin_a1_out_2nd.ldif";
    String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", datafile};
    
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/del/bin_a1_out_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
*/
}
