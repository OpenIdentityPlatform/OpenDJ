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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tasks;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.TestTaskListener;
import org.opends.server.backends.task.TaskState;
import org.opends.server.backends.jeb.JebTestCase;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;

import java.util.UUID;

public class TestRebuildTask extends TasksTestCase
{
  private static String suffix="dc=rebuild,dc=jeb";
  private static  String vBranch="ou=rebuild tests," + suffix;

    private static String[] template = new String[] {
      "define suffix="+suffix,
      "define maildomain=example.com",
      "define numusers= #numEntries#",
      "",
      "branch: [suffix]",
      "",
      "branch: " + vBranch,
      "subordinateTemplate: person:[numusers]",
      "",
      "template: person",
      "rdnAttr: uid",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "givenName: ABOVE LIMIT",
      "sn: <last>",
      "cn: {givenName} {sn}",
      "initials: {givenName:1}<random:chars:" +
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
      "employeeNumber: <sequential:0>",
      "uid: user.{employeeNumber}",
      "mail: {uid}@[maildomain]",
      "userPassword: password",
      "telephoneNumber: <random:telephone>",
      "homePhone: <random:telephone>",
      "pager: <random:telephone>",
      "mobile: <random:telephone>",
      "street: <random:numeric:5> <file:streets> Street",
      "l: <file:cities>",
      "st: <file:states>",
      "postalCode: <random:numeric:5>",
      "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
      "description: This is the description for {cn}.",
      ""};

  @BeforeClass
  public void setup() throws Exception {
    TestCaseUtils.startServer();
  }

  @AfterClass
  public void cleanUp() throws Exception {
  }

  @DataProvider(name = "taskentry")
  public Object[][] createData() throws Exception
  {
    return new Object[][] {
// A fairly simple, valid rebuild task.
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-rebuild",
                   "ds-task-class-name: org.opends.server.tasks.RebuildTask",
                   "ds-task-rebuild-base-dn: " + suffix,
                   "ds-task-rebuild-index: mail"
              ),
              TaskState.COMPLETED_SUCCESSFULLY
         },
         {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-rebuild",
                   "ds-task-class-name: org.opends.server.tasks.RebuildTask",
                   "ds-task-rebuild-base-dn: " + suffix,
                   "ds-task-rebuild-index: dn2id",
                   "ds-task-rebuild-index: dn2uri",
                   "ds-task-rebuild-index: id2children",
                   "ds-task-rebuild-index: id2subtree",
                   "ds-task-rebuild-index: mail",
                   "ds-task-rebuild-max-threads: 3"
              ),
              TaskState.COMPLETED_SUCCESSFULLY
         },
                 {
              TestCaseUtils.makeEntry(
                   "dn: ds-task-id=" + UUID.randomUUID() +
                        ",cn=Scheduled Tasks,cn=Tasks",
                   "objectclass: top",
                   "objectclass: ds-task",
                   "objectclass: ds-task-rebuild",
                   "ds-task-class-name: org.opends.server.tasks.RebuildTask",
                   "ds-task-rebuild-base-dn: ou=bad," + suffix,
                   "ds-task-rebuild-index: dn2id",
                   "ds-task-rebuild-index: dn2uri"
              ),
              TaskState.STOPPED_BY_ERROR
         },
    };
  }

   @Test(dataProvider = "taskentry", groups = "slow")
  public void testRebuildTask(Entry taskEntry, TaskState expectedState)
       throws Exception
  {
    testTask(taskEntry, expectedState, 60);    
 }
}
