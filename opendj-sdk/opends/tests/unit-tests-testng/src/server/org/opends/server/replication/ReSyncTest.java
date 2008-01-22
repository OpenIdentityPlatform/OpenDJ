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
package org.opends.server.replication;

import static org.testng.Assert.fail;

import java.io.File;
import java.net.ServerSocket;
import java.util.UUID;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test re-synchronization after after backup/restore and LDIF import.
 */
@Test(enabled=false)
public class ReSyncTest extends ReplicationTestCase
{
 /**
  * Set up the environment for performing the tests in this Class.
  *
  * @throws Exception
  *           If the environment could not be set up.
  */
 @BeforeClass(enabled=false)
  public void setup() throws Exception
  {
   /*
    * - Start a server and a replicationServer, configure replication
    * - Do some changes.
    */
    TestCaseUtils.startServer();

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    int replServerPort = socket.getLocalPort();
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
    String replServerLdif =
      "dn: " + "cn=Replication Server, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port:" + replServerPort + "\n"
        + "ds-cfg-replication-server-id: 1\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

    // suffix synchronized
    String domainLdif =
      "dn: cn=example, cn=domains, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: example\n"
        + "ds-cfg-base-dn: dc=example,dc=com\n"
        + "ds-cfg-replication-server: localhost:"+ replServerPort + "\n"
        + "ds-cfg-server-id: 123\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(domainLdif);

    configureReplication();

    // Give some time to the replication to setup
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
  @Test(enabled=false, groups="slow")
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
  @Test(enabled=false, groups="slow")
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


}
