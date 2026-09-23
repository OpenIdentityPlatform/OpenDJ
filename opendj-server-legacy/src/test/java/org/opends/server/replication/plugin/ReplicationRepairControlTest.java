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
 * Portions Copyright 2015-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.ReplicationMessages.ERR_OPERATION_NOT_FOUND_IN_PENDING;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.replication.plugin.ReplicationRepairRequestControl.OID_REPLICATION_REPAIR_CONTROL;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.testng.Assert.assertEquals;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.tools.LDAPModify;

/**
 * Tests the replication repair request control, which lets an administrator write the
 * attributes replication owns - {@code entryUUID}, {@code ds-sync-hist} - on the replica the
 * request is sent to, and on that replica only.
 * <p>
 * The suffix is replicated and a broker listens on its replication server: what a repair
 * publishes, if anything, is read from there.
 */
@SuppressWarnings("javadoc")
public class ReplicationRepairControlTest extends ReplicationTestCase
{
  private static final String REPAIRED_DN = "cn=repair me," + TEST_ROOT_DN_STRING;
  private static final String HISTORY = "ds-sync-hist";
  /**
   * The values the repair gives the entry - not the ones {@link #testRepairControl()} gives to
   * an entry of its own: two entries under one {@code entryUUID} is the corruption this control
   * can cause, and a case reading by UUID would then pick either of them.
   */
  private static final String REPAIRED_UUID = "3d2a6b4c-7f1e-4c62-9a0d-5e8b2c1f4a77";
  private static final String REPAIRED_NS_UNIQUE_ID = "3d2a6b4c-7f1e4c62-9a0d5e8b-2c1f4a77";
  /**
   * The record the error logger writes carries the id of the message rather than its text, so
   * what is looked for here does not depend on the locale the tests run under. The publishers
   * on {@link TestCaseUtils#ERROR_TEXT_WRITER} write the plain ordinal, which the trailing
   * space - the record always has {@code msg=} after the id - keeps from matching the
   * ordinals of other messages which begin with the same digits.
   */
  private static final String NOT_IN_PENDING =
      "msgID=" + ERR_OPERATION_NOT_FOUND_IN_PENDING.get("", "").ordinal() + " ";

  private ReplicationBroker broker;
  private LDAPConnectionFactory factory;
  private Connection connection;

  @Override
  @BeforeClass(alwaysRun = true)
  public void setUp() throws Exception
  {
    super.setUp();

    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);

    final int replServerPort = TestCaseUtils.findFreePort();
    final String replServerLdif =
        "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-server\n"
        + "cn: Replication Server\n"
        + "ds-cfg-replication-port: " + replServerPort + "\n"
        + "ds-cfg-replication-db-directory: ReplicationRepairControlTest\n"
        + "ds-cfg-replication-server-id: 106\n";
    final String synchroServerLdif =
        "dn: cn=replicationRepairControlTest, cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: replicationRepairControlTest\n"
        + "ds-cfg-base-dn: " + baseDN + "\n"
        + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
        + "ds-cfg-server-id: 1\n"
        + "ds-cfg-receive-status: true\n";
    configureReplication(replServerLdif, synchroServerLdif);

    broker = openReplicationSession(baseDN, 2, 100, replServerPort, 1000);

    factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
    connection = factory.getConnection();
    connection.bind("cn=Directory Manager", "password".toCharArray());
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception
  {
    if (connection != null)
    {
      connection.close();
    }
    if (factory != null)
    {
      factory.close();
    }
    stop(broker);
  }

  @Test
  public void testRepairControl()
         throws Exception
  {
    // Test that we can't add an entry with the entryuuid attribute
    // without specifying the replication repair control.
    String path = TestCaseUtils.createTempFile(
        "dn: uid=test.repair," + TEST_ROOT_DN_STRING + "\n" +
        "changetype: add\n" +
        "objectClass: top\n" +
        "objectClass: person\n" +
        "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" +
        "uid: test.repair\n" +
        "givenName: Test\n" +
        "sn: User\n" +
        "cn: Test User\n" +
        "userPassword: password\n" +
        "entryuuid: d5b910d8-47cb-4ac0-9e5f-0f4a77de58d4\n");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.run(nullPrintStream(), nullPrintStream(), args), 53);

    // Test that we can't add an entry with the ds-sync-hist attribute
    // without specifying the replication repair control.
    String path1 = TestCaseUtils.createTempFile(
        "dn: uid=test.repair," + TEST_ROOT_DN_STRING + "\n" +
        "changetype: add\n" +
        "objectClass: top\n" +
        "objectClass: person\n" +
        "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" +
        "uid: test.repair\n" +
        "givenName: Test\n" +
        "sn: User\n" +
        "cn: Test User\n" +
        "userPassword: password\n" +
        "ds-sync-hist: description:00000108b3a6cbb800000001:del:deleted_value\n");

    String[] args1 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path1
    };


    assertEquals(LDAPModify.run(nullPrintStream(), nullPrintStream(), args1), 53);

    // Now Test specifying the replication repair control makes
    // possible to add an entry with the entryuuid and ds-sync-hist attributes
    // (notice the -J repairControlOid in the ldapmodify arguments)
    String path2 = TestCaseUtils.createTempFile(
        "dn: uid=test.repair," + TEST_ROOT_DN_STRING + "\n" +
        "changetype: add\n" +
        "objectClass: top\n" +
        "objectClass: person\n" +
        "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" +
        "uid: test.repair\n" +
        "givenName: Test\n" +
        "sn: User\n" +
        "cn: Test User\n" +
        "userPassword: password\n" +
        "ds-sync-hist: description:00000108b3a6cbb800000001:del:deleted_value\n" +
        "entryuuid: d5b910d8-47cb-4ac0-9e5f-0f4a77de58d4\n");

    String[] args2 =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", "1.3.6.1.4.1.26027.1.5.2",
      "-f", path2
    };

    assertEquals(LDAPModify.run(nullPrintStream(), nullPrintStream(), args2), 0);

    // A repair is a change to this replica alone: the add was not published.
    assertThat(nextUpdate()).as("the repaired add was published to the topology").isNull();
  }

  /**
   * An administrator setting the entryUUID of an entry - to give it back the value another
   * directory had, say - does it with the repair control, on every replica in turn: the
   * change is not published, and replication does not treat it as a change of its own either.
   */
  @Test
  public void aRepairedModifySetsTheEntryUUIDOnThisReplicaOnly() throws Exception
  {
    TestCaseUtils.addEntry(
        "dn: " + REPAIRED_DN,
        "objectClass: top",
        "objectClass: person",
        "sn: repair",
        "cn: repair me");
    assertThat(nextUpdate()).as("the add of the entry to repair was not published").isNotNull();

    assertThat(attributeOfRepairedEntry("entryUUID")).isNotNull().isNotEqualTo(REPAIRED_UUID);

    // Without the control, entryUUID is NO-USER-MODIFICATION for an administrator too.
    try
    {
      connection.modify(repairRequest(false));
      throw new AssertionError("entryUUID was modified without the repair control");
    }
    catch (LdapException e)
    {
      assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CONSTRAINT_VIOLATION);
    }

    // The add of the entry was a replicated change, so the entry carries a history already:
    // what the repair must leave alone is something rather than nothing.
    final Set<String> historyBeforeTheRepair = valuesOfRepairedEntry(HISTORY);
    assertThat(historyBeforeTheRepair).as("the entry to repair carries no history").isNotEmpty();

    TestCaseUtils.ERROR_TEXT_WRITER.clear();
    try
    {
      connection.modify(repairRequest(true));
    }
    finally
    {
      // Read the history back on every road out of the modify, including the ones where it
      // failed: the entry keeps the history it had, and a repair which ends badly must not
      // have left a record of itself there either.
      assertThat(valuesOfRepairedEntry(HISTORY))
          .as("the repair was recorded in the history of the entry")
          .isEqualTo(historyBeforeTheRepair);
    }

    assertThat(attributeOfRepairedEntry("entryUUID")).isEqualTo(REPAIRED_UUID);
    assertThat(attributeOfRepairedEntry("nsUniqueId")).isEqualTo(REPAIRED_NS_UNIQUE_ID);
    assertThat(nextUpdate()).as("the repair was published to the topology").isNull();
    // The change is not a replayed one either: replication is not left looking for it among
    // the changes it was replaying.
    final List<String> records = new ArrayList<>(TestCaseUtils.ERROR_TEXT_WRITER.getMessages());
    assertThat(records).as("the repair was reported as a change missing from the pending list")
        .noneMatch(record -> record.contains(NOT_IN_PENDING));
  }

  /**
   * The procedure has the control sent as not critical, and on a modify it has to be: the
   * backend refuses a critical control it does not know, and it does so before the replication
   * plugin takes the control off the request.
   */
  @Test(dependsOnMethods = "aRepairedModifySetsTheEntryUUIDOnThisReplicaOnly")
  public void aCriticalRepairControlIsRefusedOnAModify() throws Exception
  {
    final ModifyRequest critical = Requests.newModifyRequest(REPAIRED_DN)
        .addModification(ModificationType.REPLACE, "entryUUID", REPAIRED_UUID)
        .addControl(GenericControl.newControl(OID_REPLICATION_REPAIR_CONTROL, true));
    try
    {
      connection.modify(critical);
      throw new AssertionError("a critical repair control was accepted on a modify");
    }
    catch (LdapException e)
    {
      assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
    }
  }

  /** A repaired entry goes on replicating as any other, under its repaired entryUUID. */
  @Test(dependsOnMethods = "aRepairedModifySetsTheEntryUUIDOnThisReplicaOnly")
  public void aChangeAfterTheRepairIsPublishedUnderTheRepairedEntryUUID() throws Exception
  {
    connection.modify(Requests.newModifyRequest(REPAIRED_DN)
        .addModification(ModificationType.REPLACE, "description", "changed after the repair"));

    final LDAPUpdateMsg published = nextUpdate();
    assertThat(published).as("a change after the repair was not published").isInstanceOf(ModifyMsg.class);
    assertThat(published.getEntryUUID()).isEqualTo(attributeOfRepairedEntry("entryUUID"));
  }

  private static ModifyRequest repairRequest(boolean withRepairControl)
  {
    final ModifyRequest request = Requests.newModifyRequest(REPAIRED_DN)
        .addModification(ModificationType.REPLACE, "entryUUID", REPAIRED_UUID)
        .addModification(ModificationType.REPLACE, "nsUniqueId", REPAIRED_NS_UNIQUE_ID);
    if (withRepairControl)
    {
      // Not critical: the control is taken off the request by the replication plugin, which
      // runs after the backend has refused a critical control it does not know.
      request.addControl(GenericControl.newControl(OID_REPLICATION_REPAIR_CONTROL));
    }
    return request;
  }

  private String attributeOfRepairedEntry(String attribute) throws Exception
  {
    final Set<String> values = valuesOfRepairedEntry(attribute);
    return values.isEmpty() ? null : values.iterator().next();
  }

  /**
   * The attribute is asked for by name as well as by {@code *} and {@code +}: an operational
   * attribute is not always among the ones those two stand for, and a read which came back
   * empty would leave the assertions on it comparing nothing with nothing.
   */
  private Set<String> valuesOfRepairedEntry(String attribute) throws Exception
  {
    final SearchResultEntry entry = connection.searchSingleEntry(
        Requests.newSearchRequest(REPAIRED_DN, SearchScope.BASE_OBJECT, "(objectClass=*)")
            .addAttribute("*", "+", attribute));
    return entry.containsAttribute(attribute)
        ? new HashSet<>(entry.parseAttribute(attribute).asSetOfString())
        : Collections.<String> emptySet();
  }

  /**
   * The next update the replication server forwards, or {@code null} if none comes within a
   * few seconds - long enough for a change published by the operation which just returned,
   * which the replication server forwards as soon as it has it.
   */
  private LDAPUpdateMsg nextUpdate() throws Exception
  {
    final long deadline = System.nanoTime() + SECONDS.toNanos(4);
    while (deadline - System.nanoTime() > 0)
    {
      final ReplicationMsg msg;
      try
      {
        msg = broker.receive();
      }
      catch (SocketTimeoutException e)
      {
        // The broker reads under a timeout of its own, shorter than the budget here.
        continue;
      }
      if (msg == null)
      {
        throw new AssertionError("the broker session is gone");
      }
      if (msg instanceof LDAPUpdateMsg)
      {
        return (LDAPUpdateMsg) msg;
      }
    }
    return null;
  }
}
