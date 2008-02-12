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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;



import java.net.InetAddress;

import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.SchemaTestMatchingRule;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.DN;

import static org.testng.Assert.*;



/**
 * Tests the enter and leave lockdown mode tasks.
 */
public class LockdownModeTaskTestCase
       extends TasksTestCase
{
  /**
   * Make sure that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Make sure that no matter what, when these tests are done the server is no
   * longer in lockdown mode.
   */
  @AfterClass()
  public void disableLockdownMode()
  {
    DirectoryServer.setLockdownMode(false);
  }



  /**
   * Test to ensure that the enter and leave lockdown tasks work as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLockdownModeTasks()
         throws Exception
  {
    // Add a test user that has the bypass-acl privilege but isn't a root user.
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: cn=Admin,o=test",
      "objectClass: top",
      "objectClass: person",
      "cn: Admin",
      "sn: Admin",
      "userPassword: password",
      "ds-privilege-name: bypass-acl");


    // Make sure that the server isn't currently in lockdown mode.
    assertFalse(DirectoryServer.lockdownMode());


    // Make sure that we can retrieve the server's root DSE over an
    // unauthenticated client connection.
    InetAddress localAddress = InetAddress.getLocalHost();
    String localIP = localAddress.getHostAddress();
    boolean isLoopback = localAddress.isLoopbackAddress();
    String[] args =
    {
      "-h", localIP,
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);


    // Create a file that holds the LDIF for putting the server in lockdown
    // mode.
    String taskFile = TestCaseUtils.createTempFile(
      "dn: ds-task-id=Enter Lockdown Mode,cn=Scheduled Tasks,cn=tasks",
      "changetype: add",
      "objectClass: top",
      "objectClass: ds-task",
      "ds-task-id: Enter Lockdown Mode",
      "ds-task-class-name: org.opends.server.tasks.EnterLockdownModeTask");

    DN taskDN = DN.decode(
         "ds-task-id=Enter Lockdown Mode,cn=Scheduled Tasks,cn=tasks");


    // Ensure that we can't put the server in lockdown mode as a non-root user.
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Admin,o=test",
      "-w", "password",
      "-f", taskFile
    };
    assertFalse(LDAPModify.mainModify(args, false, null, System.err) == 0);


    // If the local address isn't a loopback address, then verify that we can't
    // put the server in lockdown mode using it.
    if (! isLoopback)
    {
      args = new String[]
      {
        "-h", localIP,
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", taskFile
      };
      assertFalse(LDAPModify.mainModify(args, false, null, System.err) == 0);
    }


    // Verify that we can put the server in lockdown mode as a root user over
    // a loopback address.
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", taskFile
    };
    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    Task task = getCompletedTask(taskDN);
    assertNotNull(task);
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
    assertTrue(DirectoryServer.lockdownMode());


    // If the local IP isn't the loopback address, then verify that we can't
    // connect using it even as a root user.
    if (! isLoopback)
    {
      args = new String[]
      {
        "-h", localIP,
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-b", "",
        "-s", "base",
        "(objectClass=*)"
      };
      assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
    }


    // Make sure that we can no longer retrieve the server's root DSE over an
    // unauthenticated connection.  In this case, we'll make sure to use a
    // loopback connection.
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);


    // Make sure that we can no longer retrieve the server's root DSE over an
    // authenticated connection.  In this case, we'll make sure to use a
    // loopback connection.
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Admin,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);


    // Make sure that we can retrieve the server's root DSE over a
    // root-authenticated loopback connection.
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, null), 0);


    // Use another task to take the server out of lockdown mode and make sure it
    // works.
    taskFile = TestCaseUtils.createTempFile(
      "dn: ds-task-id=Leave Lockdown Mode,cn=Scheduled Tasks,cn=tasks",
      "changetype: add",
      "objectClass: top",
      "objectClass: ds-task",
      "ds-task-id: Leave Lockdown Mode",
      "ds-task-class-name: org.opends.server.tasks.LeaveLockdownModeTask");

    taskDN = DN.decode(
         "ds-task-id=Leave Lockdown Mode,cn=Scheduled Tasks,cn=tasks");

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", taskFile
    };
    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    task = getCompletedTask(taskDN);
    assertNotNull(task);
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
    assertFalse(DirectoryServer.lockdownMode());


    // Make sure that we can once again retrieve the server's root DSE over an
    // anonymous connection.
    args = new String[]
    {
      "-h", localIP,
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }
}

