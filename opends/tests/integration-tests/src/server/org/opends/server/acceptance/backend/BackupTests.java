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
 * This class contains the JUnit tests for the Backend functional tests for backup
 */
public class BackupTests extends DirectoryServerAcceptanceTestCase
{
  public String backup_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--backupDirectory", "/tmp/backup1"};
  public String backup_args_1param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--backupDirectory", "/tmp/backup1", " "};
  public String backup_args_2param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--backupDirectory", "/tmp/backup1", " ", " "};
  public String backup_args_3param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--backupDirectory", "/tmp/backup1", " ", " ", " "};

  public String backup_mod_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String backup_mod_datafiledir = acceptance_test_home + "/backend/data";

  public String backup_id = null;

  public BackupTests(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();

    String exec_cmd = "ln -s " + dsee_home + "/db db";
    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec(exec_cmd);
    child.waitFor();
  }

  public void tearDown() throws Exception
  {
    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec("rm db");
    child.waitFor();

    super.tearDown();
  }

  public void testBackup1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 1");

    int retCode = BackUpDB.mainBackUpDB(backup_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBackup2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 2");
    String datafile = backup_mod_datafiledir + "/mods.ldif";
    backup_mod_args[10] = datafile;

    LDAPModify.mainModify(backup_mod_args);

    backup_args_1param[8]="--incremental";

    int retCode = BackUpDB.mainBackUpDB(backup_args_1param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBackup3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 3");
    GregorianCalendar cal = new GregorianCalendar();
    
    backup_args_2param[7]="/tmp/backup2";
    backup_args_2param[8]="--backupID";
    backup_id = Integer.toString(cal.get(Calendar.MILLISECOND));
    backup_args_2param[9]=backup_id;

    int retCode = BackUpDB.mainBackUpDB(backup_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBackup4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 4");
    GregorianCalendar cal = new GregorianCalendar();

    String datafile = backup_mod_datafiledir + "/mods2.ldif";
    backup_mod_args[10] = datafile;

    LDAPModify.mainModify(backup_mod_args);

    backup_args_3param[7]="/tmp/backup2";
    backup_args_3param[8]="--incremental";
    backup_args_3param[9]="--incrementalBaseID";
    backup_args_3param[10]=backup_id;

    int retCode = BackUpDB.mainBackUpDB(backup_args_3param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testBackup5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backup Test 5");

    backup_args_1param[8]="--compress";

    int retCode = BackUpDB.mainBackUpDB(backup_args_1param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }


}
