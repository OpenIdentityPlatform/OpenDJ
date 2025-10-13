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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.replication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.plugin.EntryHistorical;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.RawModification;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TestTimer.CallableVoid;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/** Test for the schema replication. */
@SuppressWarnings("javadoc")
public class SchemaReplicationTest extends ReplicationTestCase
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private List<Modification> rcvdMods;

  private int replServerPort;

  /** Set up the environment for performing the tests in this Class. */
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

  /** Checks that changes done to the schema are pushed to the replicationServer clients. */
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
          "( 2.5.44.77.33 NAME 'dummy' SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )");
      Modification mod = new Modification(ADD, attr);
      processModify(baseDN, mod);

      // See if the client has received the msg
      ModifyMsg modMsg = receiveModifyMsg(broker);
      assertModReceived(mod, baseDN, modMsg);

      /* Now cleanup the schema for the next test */
      processModify(baseDN, new Modification(DELETE, attr));

      // See if the client has received the msg
      receiveModifyMsg(broker);
    }
    finally
    {
      broker.stop();
    }
  }

  private void processModify(final DN baseDN, Modification mod)
  {
    ModifyOperation modOp = connection.processModify(baseDN, newArrayList(mod));
    assertEquals(modOp.getResultCode(), ResultCode.SUCCESS);
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

      checkEntryHasAttributeValue(baseDN, "attributetypes", "( 2.5.44.77.33 NAME 'dummy' )", 10,
          "The modification has not been correctly replayed.");
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
      Modification mod = new Modification(ADD, attr);
      List<Modification> mods = newArrayList(mod);

      for (SynchronizationProvider<?> provider : getSynchronizationProviders())
      {
        provider.processSchemaChange(mods);
      }

      // receive the message on the broker side.
      ModifyMsg modMsg = receiveModifyMsg(broker);
      assertModReceived(mod, baseDN, modMsg);

      // check that the schema files were updated with the new ServerState.
      // by checking that the CSN of msg we just received has been
      // added to the user schema file.

      // build the string to find in the schema file
      final String stateStr = modMsg.getCSN().toString();

      // open the schema file
      final File schemaFile = getSchemaFile();

      // it is necessary to loop on this check because the state is not
      // written immediately but only every so often.
      TestTimer timer = new TestTimer.Builder()
        .maxSleep(10, SECONDS)
        .sleepTimes(100, MILLISECONDS)
        .toTimer();
      timer.repeatUntilSuccess(new CallableVoid()
      {
        @Override
        public void call() throws Exception
        {
          assertTrue(schemaFile.exists());
          String fileStr = readAsString(schemaFile);
          assertTrue(fileStr.contains(stateStr), "The Schema persistentState (CSN:" + stateStr
              + ") has not been saved to " + schemaFile + " : " + fileStr);
        }
      });
    } finally
    {
      broker.stop();
    }
    logger.error(LocalizableMessage.raw("Ending replication test : pushSchemaFilesChange "));
  }

  private File getSchemaFile()
  {
    String sep = File.separator;
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    //String buildDir = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR, buildRoot + sep + "target");
    final String path = TestCaseUtils.paths.testInstanceRoot.getPath()+ sep + "config" + sep + "schema" + sep + "99-user.ldif";
    return new File(path);
  }

  private ModifyMsg receiveModifyMsg(ReplicationBroker broker) throws SocketTimeoutException
  {
    ReplicationMsg msg = broker.receive();
    Assertions.assertThat(msg).isInstanceOf(ModifyMsg.class);
    return (ModifyMsg) msg;
  }

  private void assertModReceived(Modification mod, final DN baseDN, ModifyMsg modMsg) throws Exception
  {
    Operation receivedOp = modMsg.createOperation(connection);
    assertEquals(modMsg.getDN(), baseDN, "The received message is not for cn=schema");
    Assertions.assertThat(receivedOp).isInstanceOf(ModifyOperation.class);
    ModifyOperation receivedModifyOperation = (ModifyOperation) receivedOp;

    this.rcvdMods = new ArrayList<>();
    for (RawModification m : receivedModifyOperation.getRawModifications())
    {
      this.rcvdMods.add(m.toModification());
    }
    Assertions.assertThat(this.rcvdMods)
      .as("The received mod does not contain the original change")
      .contains(mod);
  }

  private String readAsString(File file) throws FileNotFoundException, IOException
  {
    FileInputStream input = new FileInputStream(file);
    byte[] bytes = new byte[input.available()];
    input.read(bytes);
    return new String(bytes);
  }
}
