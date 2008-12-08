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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication;

import static org.testng.Assert.fail;

import java.io.File;
import java.net.ServerSocket;
import java.util.UUID;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

/**
 * Test re-synchronization after after backup/restore and LDIF import.
 */
public class ReSyncTest extends ReplicationTestCase
{
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();

  private void debugInfo(String s)
  {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo(s);
    }
  }

  protected static final String EXAMPLE_DN = "dc=example,dc=com";

 /**
  * Set up the environment for performing the tests in this Class.
  *
  * @throws Exception
  *           If the environment could not be set up.
  */
 @BeforeClass
  public void setup() throws Exception
  {
   super.setUp();

   /*
    * - Configure replication
    * - Do some changes.
    */

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    int replServerPort = socket.getLocalPort();
    socket.close();

    // This test uses restore task which does not work with memory backend
    // (like the test backend we use in every tests): backend is disabled then
    // re-enabled and this clears the backend reference and thus the underlying
    // data. So for this particular test, we use a classical backend. Let's
    // clear it and create the root entry

    LDAPReplicationDomain.clearJEBackend(false, "userRoot", EXAMPLE_DN);
    addEntry("dn: dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: domain\n");

    // Change log
    String replServerLdif =
      "dn: " + "cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port:" + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: ReSyncTest\n"
        + "ds-cfg-replication-server-id: 104\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

    // suffix synchronized
    String reSyncTest = "reSyncTest";
    String domainLdif =
      "dn: cn=" + reSyncTest + ", cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + reSyncTest + "\n"
        + "ds-cfg-base-dn: " + EXAMPLE_DN + "\n"
        + "ds-cfg-replication-server: localhost:"+ replServerPort + "\n"
        + "ds-cfg-server-id: 123\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(domainLdif);

    configureReplication();

    // Give some time to the replication to setup
    Thread.sleep(1000);

    // Create a dummy entry
    addEntry("dn: dc=dummy," + EXAMPLE_DN + "\n"
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
    AddOperationBasis addOp;
    entry = TestCaseUtils.entryFromLdifString(entryString);
    addOp = new AddOperationBasis(connection,
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

    connection.processDelete(DN.decode("dc=fooUniqueName1," + EXAMPLE_DN));

    task("dn: ds-task-id=" + UUID.randomUUID()
        +  ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-backup\n"
        + "ds-task-class-name: org.opends.server.tasks.BackupTask\n"
        + "ds-backup-directory-path: bak\n"
        + "ds-task-backup-all: TRUE\n");

    debugInfo("testResyncAfterRestore: backup done");
    addEntry("dn: dc=fooUniqueName1," + EXAMPLE_DN + "\n"
        + "objectClass: top\n" + "objectClass: domain\n");
    debugInfo("testResyncAfterRestore: entry added");

    task("dn: ds-task-id=" + UUID.randomUUID()
        + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-restore\n"
        + "ds-task-class-name: org.opends.server.tasks.RestoreTask\n"
        + "ds-backup-directory-path: bak" + File.separator
        + "userRoot\n");

    debugInfo("testResyncAfterRestore: restore done");

   if (getEntry(DN.decode("dc=fooUniqueName1," + EXAMPLE_DN), 30000, true) == null)
     fail("The Directory has not been resynchronized after the restore.");

   connection.processDelete(DN.decode("dc=fooUniqueName1," + EXAMPLE_DN));
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
    connection.processDelete(DN.decode("dc=fooUniqueName2," + EXAMPLE_DN));

    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = buildRoot + File.separator + "build" +
                  File.separator + "unit-tests" + File.separator +
                  "package-instance"+ File.separator + "ReSynchTest";

    task("dn: ds-task-id=" + UUID.randomUUID()
        + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-export\n"
        + "ds-task-class-name: org.opends.server.tasks.ExportTask\n"
        + "ds-task-export-backend-id: userRoot\n"
        + "ds-task-export-ldif-file: " + path + "\n");

    debugInfo("testResyncAfterImport: export done");
    addEntry("dn: dc=fooUniqueName2," + EXAMPLE_DN + "\n"
        + "objectClass: top\n" + "objectClass: domain\n");
    debugInfo("testResyncAfterImport: entry added");

    task("dn: ds-task-id=" + UUID.randomUUID()
        + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-import\n"
        + "ds-task-class-name: org.opends.server.tasks.ImportTask\n"
        + "ds-task-import-backend-id: userRoot\n"
        + "ds-task-import-ldif-file: " + path + "\n"
        + "ds-task-import-reject-file: " + path + "reject\n");

    debugInfo("testResyncAfterImport: import done");

   if (getEntry(DN.decode("dc=fooUniqueName2," + EXAMPLE_DN), 30000, true) == null)
     fail("The Directory has not been resynchronized after the restore.");
  }

  /**
   * Clean up the environment.
   *
   * @throws Exception If the environment could not be set up.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;
    super.classCleanUp();

    // Clear the backend
    LDAPReplicationDomain.clearJEBackend(false, "userRoot", EXAMPLE_DN);

    paranoiaCheck();
  }
}
