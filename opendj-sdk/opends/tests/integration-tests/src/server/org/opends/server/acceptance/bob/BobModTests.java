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
package org.opends.server.acceptance.bob;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the Bob modifies.
 */
public class BobModTests extends DirectoryServerAcceptanceTestCase
{
  public String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String bob_mod_datafiledir = acceptance_test_home + "/bob/data/mod";

  public BobModTests(String name)
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


  public void testBobMod1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 1");
    String datafile = bob_mod_datafiledir + "/bin_a1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 2");
    String datafile = bob_mod_datafiledir + "/bin_a2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 3");
    String datafile = bob_mod_datafiledir + "/bin_c1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 4");
    String datafile = bob_mod_datafiledir + "/bin_c2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 5");
    String datafile = bob_mod_datafiledir + "/ces_a1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 6");
    String datafile = bob_mod_datafiledir + "/ces_a2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 7");
    String datafile = bob_mod_datafiledir + "/ces_c1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 8");
    String datafile = bob_mod_datafiledir + "/ces_c2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 9");
    String datafile = bob_mod_datafiledir + "/cis_a1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 10");
    String datafile = bob_mod_datafiledir + "/cis_a2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 11");
    String datafile = bob_mod_datafiledir + "/cis_c1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod12() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 12");
    String datafile = bob_mod_datafiledir + "/cis_c2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod13() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 13");
    String datafile = bob_mod_datafiledir + "/dn_a1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod14() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 14");
    String datafile = bob_mod_datafiledir + "/dn_a2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod15() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 15");
    String datafile = bob_mod_datafiledir + "/dn_c1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod16() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 16");
    String datafile = bob_mod_datafiledir + "/dn_c2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod17() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 17");
    String datafile = bob_mod_datafiledir + "/tel_a1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod18() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 18");
    String datafile = bob_mod_datafiledir + "/tel_a2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod19() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 19");
    String datafile = bob_mod_datafiledir + "/tel_c1_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod20() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 20");
    String datafile = bob_mod_datafiledir + "/tel_c2_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod21() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 21");
    String datafile = bob_mod_datafiledir + "/bin_a3_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod22() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 22");
    String datafile = bob_mod_datafiledir + "/bin_a4_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod23() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 23");
    String datafile = bob_mod_datafiledir + "/bin_c3_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod24() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 24");
    String datafile = bob_mod_datafiledir + "/bin_c4_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod25() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 25");
    String datafile = bob_mod_datafiledir + "/cis_a3_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 21;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod26() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 26");
    String datafile = bob_mod_datafiledir + "/cis_a4_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod27() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 27");
    String datafile = bob_mod_datafiledir + "/cis_c3_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobMod28() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Modify test 28");
    String datafile = bob_mod_datafiledir + "/cis_c4_mod.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }
}


