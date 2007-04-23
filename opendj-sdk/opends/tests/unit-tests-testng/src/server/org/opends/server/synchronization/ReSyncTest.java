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
package org.opends.server.synchronization;

import static org.opends.server.config.ConfigConstants.ATTR_TASK_COMPLETION_TIME;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_STATE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.File;
import java.net.ServerSocket;
import java.util.UUID;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test re-synchronization after after backup/restore and LDIF import.
 */
public class ReSyncTest extends SynchronizationTestCase
{
 /**
  * Set up the environment for performing the tests in this Class.
  *
  * @throws Exception
  *           If the environment could not be set up.
  */
 @BeforeClass
  public void setup() throws Exception
  {
   /*
    * - Start a server and a changelog server, configure synchronization
    * - Do some changes.
    */
    TestCaseUtils.startServer();

    // find  a free port for the changelog server
    ServerSocket socket = TestCaseUtils.bindFreePort();
    int changelogPort = socket.getLocalPort();
    socket.close();

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();

    //  Create backend top level entries
    addEntry("dn: dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: domain\n");

    // top level synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster Synchro plugin
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
        + synchroStringDN;

    // Change log
    String changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
        + "cn: Changelog Server\n"
        + "ds-cfg-changelog-port:" + changelogPort + "\n"
        + "ds-cfg-changelog-server-id: 1\n";
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);

    // suffix synchronized
    String synchroServerLdif =
      "dn: cn=example, cn=domains, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: dc=example,dc=com\n"
        + "ds-cfg-changelog-server: localhost:"+ changelogPort + "\n"
        + "ds-cfg-directory-server-id: 123\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    configureSynchronization();

    // Give some time to the synchronization to setup 
    Thread.sleep(1000);
    
    // Create a dummy entry
    addEntry("dn: dc=dummy, dc=example,dc=com\n"
        + "objectClass: top\n" + "objectClass: domain\n");
  }

  /**
   * Utility function. Can be used to create and add and entry
   * in the local DS from its ldif description.
   *
   * @param entryString  The entry in ldif from.
   * @return             The ResultCode of the operation.
   * @throws Exception   If something went wrong.
   */
  private ResultCode addEntry(String entryString) throws Exception
  {
    Entry entry;
    AddOperation addOp;
    entry = TestCaseUtils.entryFromLdifString(entryString);
    addOp = new AddOperation(connection,
       InternalClientConnection.nextOperationID(), InternalClientConnection
       .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
       entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    entryList.add(entry.getDN());
    return addOp.getResultCode();
  }

  /**
   * Test re-synchronization after after backup/restore
   */
  @Test(enabled=true, groups="slow")
  public void testResyncAfterRestore() throws Exception
  {
    /*
     * - Backup the server
     * - ADD an entry
     * - Restore the backup taken previously
     * - Check that entry has been added again in the LDAP server.
     */

    // Delete the entry we are going to use to make sure that
    // we do test something.
    connection.processDelete(DN.decode("dc=foo, dc=example,dc=com"));

    task("dn: ds-task-id=" + UUID.randomUUID()
        +  ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-backup\n"
        + "ds-task-class-name: org.opends.server.tasks.BackupTask\n"
        + "ds-backup-directory-path: bak\n"
        + "ds-task-backup-all: TRUE\n");

    addEntry("dn: dc=foo, dc=example,dc=com\n"
        + "objectClass: top\n" + "objectClass: domain\n");

    task("dn: ds-task-id=" + UUID.randomUUID()
        + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-restore\n"
        + "ds-task-class-name: org.opends.server.tasks.RestoreTask\n"
        + "ds-backup-directory-path: bak" + File.separator
        + "userRoot\n");

   if (getEntry(DN.decode("dc=foo, dc=example,dc=com"), 30000, true) == null)
     fail("The Directory has not been resynchronized after the restore.");

   connection.processDelete(DN.decode("dc=foo, dc=example,dc=com"));
  }

  /**
   * Test re-synchronization after after backup/restore
   */
  @Test(enabled=true, groups="slow")
  public void testResyncAfterImport() throws Exception
  {
    /*
     * - Do an export to a LDIF file
     * - Add an entry
     * - Import LDIF file generated above.
     * - Check that entry has been added again in the LDAP server.
     */

    // delete the entry we are going to use to make sure that
    // we do test something.
    connection.processDelete(DN.decode("dc=foo, dc=example,dc=com"));

    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = buildRoot + File.separator + "build" +
                  File.separator + "unit-tests" + File.separator +
                  "package"+ File.separator + "ReSynchTest";
    
    task("dn: ds-task-id=" + UUID.randomUUID()
        + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-export\n"
        + "ds-task-class-name: org.opends.server.tasks.ExportTask\n"
        + "ds-task-export-backend-id: userRoot\n"
        + "ds-task-export-ldif-file: " + path + "\n");

    addEntry("dn: dc=foo, dc=example,dc=com\n"
        + "objectClass: top\n" + "objectClass: domain\n");

    task("dn: ds-task-id=" + UUID.randomUUID()
        + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-import\n"
        + "ds-task-class-name: org.opends.server.tasks.ImportTask\n"
        + "ds-task-import-backend-id: userRoot\n"
        + "ds-task-import-ldif-file: " + path + "\n"
        + "ds-task-import-reject-file: " + path + "reject\n");

   if (getEntry(DN.decode("dc=foo, dc=example,dc=com"), 30000, true) == null)
     fail("The Directory has not been resynchronized after the restore.");
  }


  /**
   * Utility method to create, run a task and check its result.
   */
  private void task(String task) throws Exception
  {
    Entry taskEntry = TestCaseUtils.makeEntry(task);

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add the task.
    AddOperation addOperation =
         connection.processAdd(taskEntry.getDN(),
                               taskEntry.getObjectClasses(),
                               taskEntry.getUserAttributes(),
                               taskEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
                 "Add of the task definition was not successful");

    // Wait until the task completes.
    AttributeType completionTimeType = DirectoryServer.getAttributeType(
         ATTR_TASK_COMPLETION_TIME.toLowerCase());
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectclass=*)");
    Entry resultEntry = null;
    String completionTime = null;
    long startMillisecs = System.currentTimeMillis();
    do
    {
      InternalSearchOperation searchOperation =
           connection.processSearch(taskEntry.getDN(),
                                    SearchScope.BASE_OBJECT,
                                    filter);
      try
      {
        resultEntry = searchOperation.getSearchEntries().getFirst();
      } catch (Exception e)
      {
        continue;
      }
      completionTime =
           resultEntry.getAttributeValue(completionTimeType,
                                         DirectoryStringSyntax.DECODER);

      if (completionTime == null)
      {
        if (System.currentTimeMillis() - startMillisecs > 1000*30)
        {
          break;
        }
        Thread.sleep(10);
      }
    } while (completionTime == null);

    if (completionTime == null)
    {
      fail("The task has not completed after 30 seconds.");
    }

    // Check that the task state is as expected.
    AttributeType taskStateType =
         DirectoryServer.getAttributeType(ATTR_TASK_STATE.toLowerCase());
    String stateString =
         resultEntry.getAttributeValue(taskStateType,
                                       DirectoryStringSyntax.DECODER);
    TaskState taskState = TaskState.fromString(stateString);
    assertEquals(taskState, TaskState.COMPLETED_SUCCESSFULLY,
                 "The task completed in an unexpected state");
  }
}
