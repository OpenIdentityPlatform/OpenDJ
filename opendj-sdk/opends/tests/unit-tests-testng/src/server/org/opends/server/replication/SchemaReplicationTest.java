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

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.SynchronizationProviderCfg;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test for the schema replication.
 */
public class SchemaReplicationTest extends ReplicationTestCase
{

  private ArrayList<Modification> rcvdMods = null;

  private int replServerPort;

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();

    // top level synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster Synchro plugin
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
        + synchroStringDN;

    // Change log
    String replServerLdif =
      "dn: " + "cn=Replication Server, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server-config\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-server-port: " + replServerPort + "\n"
        + "ds-cfg-replication-server-id: 1\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

    // suffix synchronized
    String domainLdif =
      "dn: cn=example, cn=domains, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: cn=schema\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-directory-server-id: 1\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(domainLdif);

    configureReplication();
  }

  /**
   * Checks that changes done to the schema are pushed to the replicationServer
   * clients.
   */
  @Test()
  public void pushSchemaChange() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : pushSchemaChange ", 1);

    final DN baseDn = DN.decode("cn=schema");

    ReplicationBroker broker =
      openReplicationSession(baseDn, (short) 2, 100, replServerPort, 5000, true);

    try
    {
      // Modify the schema
      AttributeType attrType =
        DirectoryServer.getAttributeType("attributetypes", true);
      LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
      values.add(new AttributeValue(attrType, "( 2.5.44.77.33 NAME 'dummy' )"));
      Attribute attr = new Attribute(attrType, "attributetypes", values);
      List<Modification> mods = new ArrayList<Modification>();
      Modification mod = new Modification(ModificationType.ADD, attr);
      mods.add(mod);
      ModifyOperation modOp = new ModifyOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, baseDn, mods);
      modOp.setInternalOperation(true);
      modOp.run();

      ResultCode code = modOp.getResultCode();
      assertTrue(code.equals(ResultCode.SUCCESS),
                 "The original operation failed");

      // See if the client has received the msg
      ReplicationMessage msg = broker.receive();

      assertTrue(msg instanceof ModifyMsg,
                 "The received replication message is not a MODIFY msg");
      ModifyMsg modMsg = (ModifyMsg) msg;

      Operation receivedOp = modMsg.createOperation(connection);
      assertTrue(DN.decode(modMsg.getDn()).compareTo(baseDn) == 0,
                 "The received message is not for cn=schema");

      assertTrue(receivedOp instanceof ModifyOperation,
                 "The received replication message is not a MODIFY msg");
      ModifyOperation receivedModifyOperation = (ModifyOperation) receivedOp;

      List<RawModification> rcvdRawMods =
        receivedModifyOperation.getRawModifications();

      this.rcvdMods = new ArrayList<Modification>();
      for (RawModification m : rcvdRawMods)
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
      modOp = new ModifyOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, baseDn, mods);
      modOp.setInternalOperation(true);
      modOp.run();

      code = modOp.getResultCode();
      assertTrue(code.equals(ResultCode.SUCCESS),
                 "The original operation failed");
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
  @Test(dependsOnMethods = { "pushSchemaChange" })
  public void replaySchemaChange() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : pushSchemaChange ", 1);

    final DN baseDn = DN.decode("cn=schema");

    ReplicationBroker broker =
      openReplicationSession(baseDn, (short) 2, 100, replServerPort, 5000, true);

    ChangeNumberGenerator gen = new ChangeNumberGenerator((short)2, 0);

    ModifyMsg modMsg = new ModifyMsg(gen.NewChangeNumber(),
                                     baseDn, rcvdMods, "cn=schema");
    broker.publish(modMsg);

    boolean found = checkEntryHasAttribute(baseDn, "attributetypes",
                                           "( 2.5.44.77.33 NAME 'dummy' )",
                                           10000, true);

    if (found == false)
      fail("The modification has not been correctly replayed.");
  }

  /**
   * Checks that changes done to the schema files are pushed to the
   * ReplicationServers and that the ServerState is updated in the schema
   * file.
   * FIXME: This test is disabled because it has side effects.
   * It causes schema tests in org.opends.server.core.AddOperationTestCase
   * to fail when running the build test target.
   */
  @Test(enabled=true, dependsOnMethods = { "replaySchemaChange" })
  public void pushSchemaFilesChange() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting replication test : pushSchemaFilesChange ", 1);

    final DN baseDn = DN.decode("cn=schema");

    ReplicationBroker broker =
      openReplicationSession(baseDn, (short) 3, 100, replServerPort, 5000, true);

    // create a schema change Notification
    AttributeType attrType =
      DirectoryServer.getAttributeType("attributetypes", true);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(attrType, "( 2.5.44.76.35 NAME 'push' )"));
    Attribute attr = new Attribute(attrType, "attributetypes", values);
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(ModificationType.ADD, attr);
    mods.add(mod);

    for (SynchronizationProvider<SynchronizationProviderCfg> provider :
         DirectoryServer.getSynchronizationProviders())
    {
      provider.processSchemaChange(mods);
    }

    // receive the message on the broker side.
    ReplicationMessage msg = broker.receive();

    assertTrue(msg instanceof ModifyMsg,
               "The received replication message is not a MODIFY msg");
    ModifyMsg modMsg = (ModifyMsg) msg;

    Operation receivedOp = modMsg.createOperation(connection);
    assertTrue(DN.decode(modMsg.getDn()).compareTo(baseDn) == 0,
               "The received message is not for cn=schema");

    assertTrue(receivedOp instanceof ModifyOperation,
               "The received replication message is not a MODIFY msg");
    ModifyOperation receivedModifyOperation = (ModifyOperation) receivedOp;

    List<RawModification> rcvdRawMods =
      receivedModifyOperation.getRawModifications();

    this.rcvdMods = new ArrayList<Modification>();
    for (RawModification m : rcvdRawMods)
    {
      this.rcvdMods.add(m.toModification());
    }

    assertTrue(this.rcvdMods.contains(mod),
               "The received mod does not contain the original change");

    // check that the schema files were updated with the new ServerState.
    // by checking that the ChangeNUmber of msg we just received has been
    // added to the user schema file.

    // build the string to find in the schema file
    String stateStr = modMsg.getChangeNumber().toString();

    // open the schema file
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = buildRoot + File.separator + "build" + File.separator +
                  "unit-tests" + File.separator + "package" + File.separator +
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
      if (fileStr.indexOf(stateStr) != -1)
      {
        break;
      }
      else
      {
        if (count++ > 50)
        {
          fail("The Schema persistentState (changenumber:"
             + stateStr + ") has not been saved to " + path + " : " + fileStr);
        }
        else
          TestCaseUtils.sleep(100);
      }
    }
  }
}
