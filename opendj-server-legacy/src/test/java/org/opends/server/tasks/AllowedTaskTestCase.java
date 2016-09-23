/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import com.forgerock.opendj.ldap.tools.LDAPModify;
import org.forgerock.opendj.ldap.DN;

import static org.testng.Assert.*;

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
  @BeforeClass
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
  @Test
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
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.run(System.out, System.err, args), LDAPResultCode.UNWILLING_TO_PERFORM);


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
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.run(System.out, System.err, args), LDAPResultCode.SUCCESS);

    waitTaskCompletedSuccessfully(DN.valueOf(
         "ds-task-id=testAllowedTask 2,cn=Scheduled Tasks,cn=Tasks"));


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
      "-p", String.valueOf(TestCaseUtils.getServerAdminPort()),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.run(System.out, System.err, args), LDAPResultCode.UNWILLING_TO_PERFORM);
  }
}

