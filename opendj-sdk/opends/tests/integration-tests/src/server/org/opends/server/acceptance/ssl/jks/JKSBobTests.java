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
package org.opends.server.acceptance.ssl.jks;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the SSL JKS bob tests.
 */
public class JKSBobTests extends DirectoryServerAcceptanceTestCase
{
  public String jks_cert8 = jks_certdb + "/cert8.db";
  public String jks_add_args[] = {"-a", "-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_mod_args[] = {"-Z", "-X", "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_mod_3args[] = {"-Z", "-X", "-P", jks_cert8, "-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_mod_datafiledir = acceptance_test_home + "/ssl/jks/data";

  public JKSBobTests(String name)
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

  public void testJKSBobTest1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 1");
    
    jks_add_args[12] = jks_mod_datafiledir + "/jks_startup.ldif";
    int retCode = LDAPModify.mainModify(jks_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 2");
    
    jks_mod_args[11] = jks_mod_datafiledir + "/modrdn/a1_modrdn.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 3");
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/modrdn/a1_modrdn_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 4");
    
    jks_mod_args[11] = jks_mod_datafiledir + "/add/bin_a1_in.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 5");
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/add/bin_a1_in_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 6");
    
    jks_mod_args[11] = jks_mod_datafiledir + "/mod/bin_a1_mod.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 7");
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/mod/bin_a1_mod_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 8");
    
    jks_mod_args[11] = jks_mod_datafiledir + "/del/bin_a1_out.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSBobTest9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Bob Test 9");
    
    jks_mod_3args[13] = jks_mod_datafiledir + "/del/bin_a1_out_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
