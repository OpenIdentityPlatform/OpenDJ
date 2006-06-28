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
 * This class contains the JUnit tests for the Bob deletes.
 */
public class BobDeleteTests extends DirectoryServerAcceptanceTestCase
{
  public String bob_args_delete[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String bob_del_datafiledir = acceptance_test_home + "/bob/data/del";

  public BobDeleteTests(String name)
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


  public void testBobDelete1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 1");
    String datafile = bob_del_datafiledir + "/a1_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPModify.mainModify(bob_args_delete);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 2");
    String datafile = bob_del_datafiledir + "/a1_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPModify.mainModify(bob_args_delete);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 3");
    String datafile = bob_del_datafiledir + "/a2_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPModify.mainModify(bob_args_delete);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 4");
    String datafile = bob_del_datafiledir + "/a3_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPModify.mainModify(bob_args_delete);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 5");
    String datafile = bob_del_datafiledir + "/a4_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPModify.mainModify(bob_args_delete);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 6");
    String datafile = bob_del_datafiledir + "/a5_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPModify.mainModify(bob_args_delete);
    int expCode = 1;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 7");
    String datafile = bob_del_datafiledir + "/b1_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPDelete.mainDelete(bob_args_delete);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 8");
    String datafile = bob_del_datafiledir + "/b1_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPDelete.mainDelete(bob_args_delete);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 9");
    String datafile = bob_del_datafiledir + "/b2_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPDelete.mainDelete(bob_args_delete);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 10");
    String datafile = bob_del_datafiledir + "/b3_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPDelete.mainDelete(bob_args_delete);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 11");
    String datafile = bob_del_datafiledir + "/b4_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPDelete.mainDelete(bob_args_delete);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testBobDelete12() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Bob Delete test 12");
    String datafile = bob_del_datafiledir + "/b5_out.ldif";
    bob_args_delete[9] = datafile;

    int retCode = LDAPDelete.mainDelete(bob_args_delete);
    int expCode = 34;

    compareExitCode(retCode, expCode);
  }

}
