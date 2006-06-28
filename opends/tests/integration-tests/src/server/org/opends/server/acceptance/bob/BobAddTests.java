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
 * This class contains the JUnit tests for the Bob adds.
 */
public class BobAddTests extends DirectoryServerAcceptanceTestCase
{
  public String bob_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String bob_add_datafiledir = acceptance_test_home + "/bob/data/add";

  public BobAddTests(String name)
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


  public void testBobAdd1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 1");
    String datafile = bob_add_datafiledir + "/bin_a1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 2");
    String datafile = bob_add_datafiledir + "/bin_a2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 3");
    String datafile = bob_add_datafiledir + "/bin_b1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 4");
    String datafile = bob_add_datafiledir + "/bin_b2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 5");
    String datafile = bob_add_datafiledir + "/bin_c1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 6");
    String datafile = bob_add_datafiledir + "/bin_c2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 7");
    String datafile = bob_add_datafiledir + "/bin_d1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 8");
    String datafile = bob_add_datafiledir + "/bin_d2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 9");
    String datafile = bob_add_datafiledir + "/ces_a1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 10");
    String datafile = bob_add_datafiledir + "/ces_a2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 11");
    String datafile = bob_add_datafiledir + "/ces_b1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd12() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 12");
    String datafile = bob_add_datafiledir + "/ces_b2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd13() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 13");
    String datafile = bob_add_datafiledir + "/ces_c1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd14() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 14");
    String datafile = bob_add_datafiledir + "/ces_c2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd15() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 15");
    String datafile = bob_add_datafiledir + "/ces_d1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd16() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 16");
    String datafile = bob_add_datafiledir + "/ces_d2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd17() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 17");
    String datafile = bob_add_datafiledir + "/cis_a1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd18() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 18");
    String datafile = bob_add_datafiledir + "/cis_a2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd19() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 19");
    String datafile = bob_add_datafiledir + "/cis_b1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd20() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 20");
    String datafile = bob_add_datafiledir + "/cis_b2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd21() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 21");
    String datafile = bob_add_datafiledir + "/cis_c1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd22() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 22");
    String datafile = bob_add_datafiledir + "/cis_c2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd23() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 23");
    String datafile = bob_add_datafiledir + "/cis_d1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd24() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 24");
    String datafile = bob_add_datafiledir + "/cis_d2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd25() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 25");
    String datafile = bob_add_datafiledir + "/dn_a1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd26() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 26");
    String datafile = bob_add_datafiledir + "/dn_a2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd27() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 27");
    String datafile = bob_add_datafiledir + "/dn_b1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd28() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 28");
    String datafile = bob_add_datafiledir + "/dn_b2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd29() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 29");
    String datafile = bob_add_datafiledir + "/dn_c1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd30() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 30");
    String datafile = bob_add_datafiledir + "/dn_c2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd31() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 31");
    String datafile = bob_add_datafiledir + "/dn_d1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd32() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 32");
    String datafile = bob_add_datafiledir + "/dn_d2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd33() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 33");
    String datafile = bob_add_datafiledir + "/tel_a1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd34() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 34");
    String datafile = bob_add_datafiledir + "/tel_a2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd35() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 35");
    String datafile = bob_add_datafiledir + "/tel_b1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd36() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 36");
    String datafile = bob_add_datafiledir + "/tel_b2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd37() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 37");
    String datafile = bob_add_datafiledir + "/tel_c1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd38() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 38");
    String datafile = bob_add_datafiledir + "/tel_c2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd39() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 39");
    String datafile = bob_add_datafiledir + "/tel_d1_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd40() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 40");
    String datafile = bob_add_datafiledir + "/tel_d2_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd41() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 41");
    String datafile = bob_add_datafiledir + "/bin_a3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd42() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 42");
    String datafile = bob_add_datafiledir + "/bin_a4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd43() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 43");
    String datafile = bob_add_datafiledir + "/bin_b3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd44() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 44");
    String datafile = bob_add_datafiledir + "/bin_b4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd45() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 45");
    String datafile = bob_add_datafiledir + "/bin_c3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    retCode = LDAPModify.mainModify(bob_args);
    int expCode = 20;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd46() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 46");
    String datafile = bob_add_datafiledir + "/bin_c4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    retCode = LDAPModify.mainModify(bob_args);
    int expCode = 20;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd47() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 47");
    String datafile = bob_add_datafiledir + "/bin_d3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd48() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 48");
    String datafile = bob_add_datafiledir + "/bin_d4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd49() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 49");
    String datafile = bob_add_datafiledir + "/cis_a3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 21;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd50() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 50");
    String datafile = bob_add_datafiledir + "/cis_a4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd51() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 51");
    String datafile = bob_add_datafiledir + "/cis_b3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd52() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 52");
    String datafile = bob_add_datafiledir + "/cis_b4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd53() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 53");
    String datafile = bob_add_datafiledir + "/cis_c3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 20;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd54() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 54");
    String datafile = bob_add_datafiledir + "/cis_c4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd55() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 55");
    String datafile = bob_add_datafiledir + "/cis_d3_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }

  public void testBobAdd56() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Add test 56");
    String datafile = bob_add_datafiledir + "/cis_d4_in.ldif";
    bob_args[10] = datafile;

    int retCode = LDAPModify.mainModify(bob_args);
    int expCode = 68;

    compareExitCode(retCode, expCode);
  }
}
