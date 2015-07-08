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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.replication;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.plugin.EntryHistorical;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test for the schema replication.
 */
@SuppressWarnings("javadoc")
public class SchemaReplicationTest extends ReplicationTestCase
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  private List<Modification> rcvdMods;

  private int replServerPort;

  /**
   * Set up the environment for performing the tests in this Class.
   */
  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    // This test suite depends on having the schema available.
    replServerPort = TestCaseUtils.findFreePort();

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();

    // Change log
    String replServerLdif =
      "dn: " + "cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: SchemaReplicationTest\n"
        + "ds-cfg-replication-db-implementation: " + replicationDbImplementation + "\n"
        + "ds-cfg-replication-server-id: 105\n";

    // suffix synchronized
    String testName = "schemaReplicationTest";
    String domainLdif =
      "dn: cn=" + testName + ", cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: " + testName + "\n"
        + "ds-cfg-base-dn: cn=schema\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-server-id: 1\n";

    configureReplication(replServerLdif, domainLdif);
  }

  /**
   * Checks that changes done to the schema are pushed to the replicationServer
   * clients.
   */
  @Test
  public void pushSchemaChange() throws Exception
  {
    logger.error(LocalizableMessage.raw("Starting replication test : pushSchemaChange "));

    cleanUpReplicationServersDB();

    final DN baseDN = DN.valueOf("cn=schema");

    ReplicationBroker broker =
      openReplicationSession(baseDN, 2, 100, replServerPort, 5000);

    try
    {
      // Modify the schema
      Attribute attr = Attributes.create("attributetypes",
          "( 2.5.44.77.33 NAME 'dummy' )");
      List<Modification> mods = new ArrayList<>();
      Modification mod = new Modification(ModificationType.ADD, attr);
      mods.add(mod);
      ModifyOperation modOp = connection.processModify(baseDN, mods);
      assertEquals(modOp.getResultCode(), ResultCode.SUCCESS,
          "The original operation failed");

      // See if the client has received the msg
      ReplicationMsg msg = broker.receive();
      Assertions.assertThat(msg).isInstanceOf(ModifyMsg.class);
      ModifyMsg modMsg = (ModifyMsg) msg;

      Operation receivedOp = modMsg.createOperation(connection);
      assertEquals(modMsg.getDN(), baseDN, "The received message is not for cn=schema");
      Assertions.assertThat(receivedOp).isInstanceOf(ModifyOperation.class);
      ModifyOperation receivedModifyOperation = (ModifyOperation) receivedOp;

      this.rcvdMods = new ArrayList<>();
      for (RawModification m : receivedModifyOperation.getRawModifications())
      {
        this.rcvdMods.add(m.toModification());
      }

      assertTrue(this.rcvdMods.contains(mod),
                 "The received mod does not contain the original change");

      /*
       * Now cleanup the schema for the next test
       */
      mod = new Modification(ModificationType.DELETE, attr);
      mods.clear();
      mods.add(mod);
      modOp = connection.processModify(baseDN, mods);
      assertEquals(modOp.getResultCode(), ResultCode.SUCCESS,
          "The original operation failed");

      // See if the client has received the msg
      msg = broker.receive();
      Assertions.assertThat(msg).isInstanceOf(ModifyMsg.class);
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Checks that changes to the schema pushed to the replicationServer
   * are received and correctly replayed by replication plugin.
   */
  @Test(enabled=true,dependsOnMethods = { "pushSchemaChange" })
  public void replaySchemaChange() throws Exception
  {
    logger.error(LocalizableMessage.raw("Starting replication test : replaySchemaChange "));

    cleanUpReplicationServersDB();

    final DN baseDN = DN.valueOf("cn=schema");

    ReplicationBroker broker =
      openReplicationSession(baseDN, 2, 100, replServerPort, 5000);

    try
    {
      CSNGenerator gen = new CSNGenerator( 2, 0);

      ModifyMsg modMsg = new ModifyMsg(gen.newCSN(), baseDN, rcvdMods,
          EntryHistorical.getEntryUUID(DirectoryServer.getEntry(baseDN)));
      broker.publish(modMsg);

      boolean found = checkEntryHasAttribute(baseDN, "attributetypes",
        "( 2.5.44.77.33 NAME 'dummy' )",
        10000, true);
      assertTrue(found, "The modification has not been correctly replayed.");
    }
    finally
    {
      broker.stop();
    }
  }

  /**
   * Checks that changes done to the schema files are pushed to the
   * ReplicationServers and that the ServerState is updated in the schema
   * file.
   */
  @Test(enabled=true, dependsOnMethods = { "replaySchemaChange" })
  public void pushSchemaFilesChange() throws Exception
  {
    logger.error(LocalizableMessage.raw("Starting replication test : pushSchemaFilesChange "));

    cleanUpReplicationServersDB();

    final DN baseDN = DN.valueOf("cn=schema");

    ReplicationBroker broker =
      openReplicationSession(baseDN, 3, 100, replServerPort, 5000);

    try
    {
      // create a schema change Notification
      Attribute attr = Attributes.create("attributetypes",
        "( 2.5.44.76.35 NAME 'push' )");
      List<Modification> mods = new ArrayList<>();
      Modification mod = new Modification(ModificationType.ADD, attr);
      mods.add(mod);

      for (SynchronizationProvider<SynchronizationProviderCfg> provider : DirectoryServer.
        getSynchronizationProviders())
      {
        provider.processSchemaChange(mods);
      }

      // receive the message on the broker side.
      ReplicationMsg msg = broker.receive();
      Assertions.assertThat(msg).isInstanceOf(ModifyMsg.class);
      ModifyMsg modMsg = (ModifyMsg) msg;

      Operation receivedOp = modMsg.createOperation(connection);
      assertEquals(modMsg.getDN(), baseDN, "The received message is not for cn=schema");
      Assertions.assertThat(receivedOp).isInstanceOf(ModifyOperation.class);
      ModifyOperation receivedModifyOperation = (ModifyOperation) receivedOp;

      this.rcvdMods = new ArrayList<>();
      for (RawModification m : receivedModifyOperation.getRawModifications())
      {
        this.rcvdMods.add(m.toModification());
      }

      assertTrue(this.rcvdMods.contains(mod),
        "The received mod does not contain the original change");

      // check that the schema files were updated with the new ServerState.
      // by checking that the CSN of msg we just received has been
      // added to the user schema file.

      // build the string to find in the schema file
      String stateStr = modMsg.getCSN().toString();

      // open the schema file
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String buildDir = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR,
              buildRoot + File.separator + "build");
      String path = buildDir + File.separator +
        "unit-tests" + File.separator + "package-instance" + File.separator +
        "config" + File.separator + "schema" + File.separator +
        "99-user.ldif";

      // it is necessary to loop on this check because the state is not
      // written immediately but only every so often.
      int count = 0;
      while (true)
      {
        File file = new File(path);
        FileInputStream input = new FileInputStream(file);
        byte[] bytes = new byte[input.available()];
        input.read(bytes);
        String fileStr = new String(bytes);
        if (fileStr.contains(stateStr))
        {
          break;
        }
        assertTrue(count++ <= 50, "The Schema persistentState (CSN:" + stateStr
            + ") has not been saved to " + path + " : " + fileStr);
        TestCaseUtils.sleep(100);
      }
    } finally
    {
      broker.stop();
    }
    logger.error(LocalizableMessage.raw("Ending replication test : pushSchemaFilesChange "));
  }
}
