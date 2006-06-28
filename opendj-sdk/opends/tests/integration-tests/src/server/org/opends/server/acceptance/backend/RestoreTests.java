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
package org.opends.server.acceptance.backend;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;
import java.util.Calendar;
import java.util.GregorianCalendar;


/**
 * This class contains the JUnit tests for the Backend functional tests for restore
 */
public class RestoreTests extends DirectoryServerAcceptanceTestCase
{
  public String restore_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backupDirectory", " "};


  public RestoreTests(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();
    prepDBEnv();
  }

  public void tearDown() throws Exception
  {
    undoDBEnv();
    super.tearDown();
  }

  public void testRestore1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Restore Test 1");
    restore_args[5] = acceptance_test_home + "/backend/data/restore";

    stopDirectoryServer();

    int retCode = RestoreDB.mainRestoreDB(restore_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);

    startDirectoryServer();

    // necessary cleanup
    stopDirectoryServer();

    String exec_cmd = "mv db/00000000.jdb " + dsee_home + "/db";
    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec(exec_cmd);
    child.waitFor();

    exec_cmd = "rmdir db";
    rtime = Runtime.getRuntime();
    child = rtime.exec(exec_cmd);
    child.waitFor();

    startDirectoryServer();
  }

  public void testRestore2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Restore Test 2");
    restore_args[5] = acceptance_test_home + "/backend/data/restore2";

    stopDirectoryServer();

    int retCode = RestoreDB.mainRestoreDB(restore_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);

    startDirectoryServer();

    // necessary cleanup
    stopDirectoryServer();

    String exec_cmd = "mv db/00000000.jdb " + dsee_home + "/db";
    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec(exec_cmd);
    child.waitFor();

    exec_cmd = "rmdir db";
    rtime = Runtime.getRuntime();
    child = rtime.exec(exec_cmd);
    child.waitFor();

    startDirectoryServer();
  }

}
