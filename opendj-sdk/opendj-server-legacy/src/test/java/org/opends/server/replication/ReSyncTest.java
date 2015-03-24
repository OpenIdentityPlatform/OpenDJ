/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication;

import java.io.File;
import java.util.UUID;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.forgerock.opendj.ldap.ResultCode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test re-synchronization after after backup/restore and LDIF import.
 */
@SuppressWarnings("javadoc")
public class ReSyncTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private void debugInfo(String s)
  {
    logger.error(LocalizableMessage.raw(s));
    logger.trace(s);
  }

  private static final String EXAMPLE_DN = "dc=example,dc=com";
  private File reSyncTempDir;

  /**
   * Set up the environment for performing the tests in this class:
   * <ol>
   * <li>Configure replication</li>
   * <li>Do some changes</li>
   * </ol>
   */
  @BeforeClass
  public void setup() throws Exception
  {
    super.setUp();

    reSyncTempDir = TestCaseUtils.createTemporaryDirectory("resynctest");

    final int replServerPort = TestCaseUtils.findFreePort();

    // This test uses restore task which does not work with memory backend
    // (like the test backend we use in every tests): backend is disabled then
    // re-enabled and this clears the backend reference and thus the underlying
    // data. So for this particular test, we use a classical backend. Let's
    // clear it and create the root entry
    TestCaseUtils.clearJEBackend("userRoot");
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
        + "ds-cfg-replication-db-implementation: " + replicationDbImplementation + "\n"
        + "ds-cfg-replication-server-id: 104\n";

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

    configureReplication(replServerLdif, domainLdif);

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
    Entry entry = TestCaseUtils.entryFromLdifString(entryString);
    AddOperation addOp = connection.processAdd(entry);
    entriesToCleanup.add(entry.getName());
    return addOp.getResultCode();
  }

  /**
   * Test re-synchronization after after backup/restore:
   * <ol>
   * <li>Backup the server</li>
   * <li>ADD an entry</li>
   * <li>Restore the backup taken previously</li>
   * <li>Check that entry has been added again in the LDAP server.</li>
   * </ol>
   */
  @Test(enabled=true, groups="slow")
  public void testResyncAfterRestore() throws Exception
  {
    // Delete the entry we are going to use to make sure that
    // we do test something.
    DN entryDN = DN.valueOf("dc=fooUniqueName1," + EXAMPLE_DN);
    connection.processDelete(entryDN);

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

    task("dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-restore\n"
        + "ds-task-class-name: org.opends.server.tasks.RestoreTask\n"
        + "ds-backup-directory-path: bak" + File.separator
        + "userRoot\n");

    debugInfo("testResyncAfterRestore: restore done");

    assertNotNull(getEntry(entryDN, 30000, true),
        "The Directory has not been resynchronized after the restore.");

    connection.processDelete(entryDN);
  }

  /**
   * Test re-synchronization after after backup/restore:
   * <ol>
   * <li>Do an export to a LDIF file</li>
   * <li>Add an entry</li>
   * <li>Import LDIF file generated above.</li>
   * <li>Check that entry has been added again in the LDAP server.</li>
   * </ol>
   */
  @Test(enabled=true, groups="slow")
  public void testResyncAfterImport() throws Exception
  {
    // delete the entry we are going to use to make sure that
    // we do test something.
    DN entryDN = DN.valueOf("dc=fooUniqueName2," + EXAMPLE_DN);
    connection.processDelete(entryDN);

    String path = reSyncTempDir.getAbsolutePath() + File.separator + "ReSynchTest";

    task("dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks\n"
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

    task("dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks\n"
        + "objectclass: top\n"
        + "objectclass: ds-task\n"
        + "objectclass: ds-task-import\n"
        + "ds-task-class-name: org.opends.server.tasks.ImportTask\n"
        + "ds-task-import-backend-id: userRoot\n"
        + "ds-task-import-ldif-file: " + path + "\n"
        + "ds-task-import-reject-file: " + path + "reject\n");

    debugInfo("testResyncAfterImport: import done");

    assertNotNull(getEntry(entryDN, 30000, true),
        "The Directory has not been resynchronized after the restore.");
  }

  /**
   * Clean up the environment.
   */
  @AfterClass
  @Override
  public void classCleanUp() throws Exception
  {
    callParanoiaCheck = false;

    // Do not try to remove non leaves
    entriesToCleanup.remove(DN.valueOf(EXAMPLE_DN));
    super.classCleanUp();

    TestCaseUtils.clearJEBackend("userRoot");
    TestCaseUtils.deleteDirectory(reSyncTempDir);

    paranoiaCheck();
  }
}
