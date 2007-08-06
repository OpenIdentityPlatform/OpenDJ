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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;



import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.GetConnectionIDExtendedOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.dsconfig.DSConfig;
import org.opends.server.types.DN;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * Tests the ability of the server to control the set of tasks that are allowed
 * to be executed.
 */
public class AllowedTaskTestCase
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
   * Tests the function of the ds-cfg-allowed-task configuration attribute
   * using a dummy task.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowedTask()
         throws Exception
  {
    // Try to add the dummy task and expect it to fail because it's not allowed.
    String path = TestCaseUtils.createTempFile(
      "dn: ds-task-id=testAllowedTask 1,cn=Scheduled Tasks,cn=Tasks",
      "changetype: add",
      "objectClass: top",
      "objectClass: ds-task",
      "ds-task-id: testAllowedTask 1",
      "ds-task-class-name: org.opends.server.tasks.DummyTask");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                 LDAPResultCode.UNWILLING_TO_PERFORM);


    // Update the set of allowed tasks to include the dummy task.
    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--add", "allowed-task:org.opends.server.tasks.DummyTask");


    // Now verify that we can add the task and have it complete successfully.
    path = TestCaseUtils.createTempFile(
      "dn: ds-task-id=testAllowedTask 2,cn=Scheduled Tasks,cn=Tasks",
      "changetype: add",
      "objectClass: top",
      "objectClass: ds-task",
      "ds-task-id: testAllowedTask 2",
      "ds-task-class-name: org.opends.server.tasks.DummyTask");

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                 LDAPResultCode.SUCCESS);

    Task task = getCompletedTask(DN.decode(
         "ds-task-id=testAllowedTask 2,cn=Scheduled Tasks,cn=Tasks"));
    assertNotNull(task);
    assertEquals(task.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);


    // Remove the task class from the set of allowed tasks and verify that we
    // can no longer schedule the task.
    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--remove", "allowed-task:org.opends.server.tasks.DummyTask");


    // Now verify that we can add the task and have it complete successfully.
    path = TestCaseUtils.createTempFile(
      "dn: ds-task-id=testAllowedTask 3,cn=Scheduled Tasks,cn=Tasks",
      "changetype: add",
      "objectClass: top",
      "objectClass: ds-task",
      "ds-task-id: testAllowedTask 3",
      "ds-task-class-name: org.opends.server.tasks.DummyTask");

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                 LDAPResultCode.UNWILLING_TO_PERFORM);
  }
}

