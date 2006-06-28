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
 * This class contains the JUnit tests for the SSL JKS startup.
 */
public class JKSStartupTests extends DirectoryServerAcceptanceTestCase
{
  public String jks_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};

  public String jks_mod_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_add_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String jks_test_home = acceptance_test_home + "/ssl/jks";
  public String jks_mod_datafiledir = jks_test_home + "/data/startup";

  public String jks_test_search_args[] = {"-h", hostname, "-p", sport, "-D", bindDN, "-w", bindPW, "-Z", "-X", "-b", "dc=com", "-s", "base", "\'(objectclass=*)\'"};


  public JKSStartupTests(String name)
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

/**
 *  Setup for jks tests
*/
  public void testJKSStartup1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 1");
    
    String exec_cmd = jks_test_home + "/generate_server_cert.sh";
    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec(exec_cmd);
    child.waitFor();

    exec_cmd = "cp " + "keystore " + dsee_home + "/config";
    rtime = Runtime.getRuntime();
    child = rtime.exec(exec_cmd);
    child.waitFor();

    jks_mod_args[9] = jks_mod_datafiledir + "/enable_key_mgr_provider.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSStartup2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 2");

    jks_mod_args[9] = jks_mod_datafiledir + "/enable_trust_mgr_provider.ldif";
    int retCode = LDAPModify.mainModify(jks_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSStartup3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 3");

    jks_add_args[10] = jks_mod_datafiledir + "/enable_ldaps_conn_handler.ldif";
    int retCode = LDAPModify.mainModify(jks_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testJKSStartup4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("JKS SSL Startup test 4");

    int retCode = LDAPSearch.mainSearch(jks_test_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
