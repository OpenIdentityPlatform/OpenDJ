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
 * This class contains the JUnit tests for the SSL JKS TLS tests.
 */
public class JKSTLSTests extends DirectoryServerAcceptanceTestCase
{
  public String jks_cert8 = jks_certdb + "/cert8.db";
  public String jks_add_args[] = {" ", "-a", "-X", "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_add_3args[] = {" ", "-a", "-X", "-P", jks_cert8, "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_mod_args[] = {" ", "-X", "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_mod_3args[] = {" ", "-X", "-P", jks_cert8, "-h", hostname, "-p", " ", "-D", bindDN, "-w", bindPW, "-f", " "};

public String tls_test_search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-q", "-X", "-b", "dc=com", "-s", "base", "\'(objectclass=*)\'"};

  public String jks_mod_datafiledir = acceptance_test_home + "/ssl/jks/data";

  public JKSTLSTests(String name)
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

  public void testJKSTLSTest1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 1");
    
    jks_mod_args[0] = "-Z";
    jks_mod_args[5] = sport;
    jks_mod_args[11] = jks_mod_datafiledir + "/startup/enable_TLS.ldif";

    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 2");

    System.out.println(cmdArrayToString(tls_test_search_args));
    int retCode = LDAPSearch.mainSearch(tls_test_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

/*
  public void testJKSTLSTest3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 3");

    jks_add_args[0] = "-q";
    jks_add_args[6] = port;
    jks_add_args[12] = jks_mod_datafiledir + "/add/bin_a1_tls_in.ldif";
    int retCode = LDAPModify.mainModify(jks_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 4");

    jks_add_3args[0] = "-q";
    jks_add_3args[8] = port;
    jks_add_3args[14] = jks_mod_datafiledir + "/add/bin_a1_tls_in_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_add_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 5");

    jks_mod_args[0] = "-q";
    jks_mod_args[5] = port;
    jks_mod_args[11] = jks_mod_datafiledir + "/mod/bin_a1_tls_mod.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 6");

    jks_mod_3args[0] = "-q";
    jks_mod_3args[7] = port;
    jks_mod_3args[13] = jks_mod_datafiledir + "/mod/bin_a1_tls_mod_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 7");

    jks_mod_args[0] = "-q";
    jks_mod_args[5] = port;
    jks_mod_args[11] = jks_mod_datafiledir + "/del/bin_a1_tls_out.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 8");

    jks_mod_3args[0] = "-q";
    jks_mod_3args[7] = port;
    jks_mod_3args[13] = jks_mod_datafiledir + "/del/bin_a1_tls_out_2nd.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_3args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 9");

    jks_mod_args[0] = "-Z";
    jks_mod_args[5] = sport;
    jks_mod_args[11] = jks_mod_datafiledir + "/shutdown/disable_TLS.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSTLSTest10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL TLS Test 10");

    int retCode = LDAPSearch.mainSearch(tls_test_search_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }
*/

}
