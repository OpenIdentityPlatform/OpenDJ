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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import static org.opends.server.loggers.Error.logError;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.protocol.ModifyMsg;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test for the schema synchronization.
 */
public class SchemaSynchronizationTest extends SynchronizationTestCase
{

  ArrayList<Modification> rcvdMods = null;

  /**
   * Set up the environment for performing the tests in this Class.
   * synchronization
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();

    // Disable schema check
    schemaCheck = DirectoryServer.checkSchema();
    DirectoryServer.setCheckSchema(false);

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();

    // top level synchro provider
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster Synchro plugin
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
        + synchroStringDN;
    String synchroPluginLdif = "dn: "
        + synchroPluginStringDN
        + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider\n"
        + "ds-cfg-synchronization-provider-enabled: true\n"
        + "ds-cfg-synchronization-provider-class: org.opends.server.synchronization.MultimasterSynchronization\n";
    synchroPluginEntry = TestCaseUtils.entryFromLdifString(synchroPluginLdif);

    // Change log
    String changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
        + "cn: Changelog Server\n" + "ds-cfg-changelog-port: 8989\n"
        + "ds-cfg-changelog-server-id: 1\n";
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);

    // suffix synchronized
    String synchroServerLdif = "dn: cn=example, " + synchroPluginStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: cn=schema\n"
        + "ds-cfg-changelog-server: localhost:8989\n"
        + "ds-cfg-directory-server-id: 1\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    configureSynchronization();
  }

  /**
   * Checks that changes done to the schema are pushed to the changelog
   * clients.
   */
  @Test()
  public void pushSchemaChange() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting synchronization test : pushSchemaChange ", 1);

    final DN baseDn = DN.decode("cn=schema");

    ChangelogBroker broker =
      openChangelogSession(baseDn, (short) 2, 100, 8989, 1000, true);

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
      SynchronizationMessage msg = broker.receive();

      assertTrue(msg instanceof ModifyMsg,
                 "The received synchronization message is not a MODIFY msg");
      ModifyMsg modMsg = (ModifyMsg) msg;

      Operation receivedOp = modMsg.createOperation(connection);
      assertTrue(DN.decode(modMsg.getDn()).compareTo(baseDn) == 0,
                 "The received message is not for cn=schema");

      assertTrue(receivedOp instanceof ModifyOperation,
                 "The received synchronization message is not a MODIFY msg");
      ModifyOperation receivedModifyOperation = (ModifyOperation) receivedOp;

      List<LDAPModification> rcvdRawMods =
        receivedModifyOperation.getRawModifications();

      this.rcvdMods = new ArrayList<Modification>();
      for (LDAPModification m : rcvdRawMods)
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
   * Checks that changes to the schema pushed to the changelog
   * are received and correctly replayed by synchronization plugin.
   */
  @Test(dependsOnMethods = { "pushSchemaChange" })
  public void replaySchemaChange() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting synchronization test : pushSchemaChange ", 1);

    final DN baseDn = DN.decode("cn=schema");

    ChangelogBroker broker =
      openChangelogSession(baseDn, (short) 2, 100, 8989, 1000, true);

    ModifyMsg modMsg = new ModifyMsg(new ChangeNumber((long) 10, 1, (short) 2),
                                     baseDn, rcvdMods, "cn=schema");
    broker.publish(modMsg);

    boolean found = checkEntryHasAttribute(baseDn, "attributetypes",
                                           "( 2.5.44.77.33 NAME 'dummy' )",
                                           10000, true);

    if (found == false)
      fail("The modification has not been correctly replayed.");
  }
}
