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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.ReplicationMessages.ERR_OPERATION_NOT_FOUND_IN_PENDING;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.RelaxRulesControl;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.Modification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that a change made under the Relax Rules control replicates like any other: the
 * control relaxes the constraints of the schema on the request, it does not make the change
 * a replayed one.
 * <p>
 * The suffix is replicated and a broker listens on its replication server: what the change
 * publishes is read from there.
 */
@SuppressWarnings("javadoc")
public class RelaxRulesReplicationTest extends ReplicationTestCase
{
  private static final String MODIFIED_DN = "cn=relaxed modify," + TEST_ROOT_DN_STRING;
  private static final String ADDED_DN = "cn=relaxed add," + TEST_ROOT_DN_STRING;
  /** An attribute the schema marks NO-USER-MODIFICATION, which only the control lets a client write. */
  private static final String RELAXED_ATTRIBUTE = "pwdChangedTime";
  private static final String RELAXED_VALUE = "20211203224637.000Z";
  /**
   * The record the error logger writes carries the id of the message rather than its text, so
   * what is looked for here does not depend on the locale the tests run under.
   */
  private static final String NOT_IN_PENDING =
      "msgID=" + ERR_OPERATION_NOT_FOUND_IN_PENDING.get("", "").ordinal();

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
        + "ds-cfg-replication-db-directory: RelaxRulesReplicationTest\n"
        + "ds-cfg-replication-server-id: 107\n";
    final String synchroServerLdif =
        "dn: cn=relaxRulesReplicationTest, cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-replication-domain\n"
        + "cn: relaxRulesReplicationTest\n"
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
  public void aRelaxedModifyIsPublishedLikeAnyOtherChange() throws Exception
  {
    TestCaseUtils.addEntry(
        "dn: " + MODIFIED_DN,
        "objectClass: top",
        "objectClass: person",
        "sn: relaxed",
        "cn: relaxed modify");
    assertThat(nextUpdateFor(MODIFIED_DN)).as("the add of the entry to modify was not published")
        .isInstanceOf(AddMsg.class);

    TestCaseUtils.ERROR_TEXT_WRITER.clear();
    connection.modify(Requests.newModifyRequest(MODIFIED_DN)
        .addControl(new RelaxRulesControl())
        .addModification(ModificationType.REPLACE, RELAXED_ATTRIBUTE, RELAXED_VALUE));

    assertThat(attributeOf(MODIFIED_DN, RELAXED_ATTRIBUTE)).isEqualTo(RELAXED_VALUE);

    final LDAPUpdateMsg published = nextUpdateFor(MODIFIED_DN);
    assertThat(published).as("the relaxed modify was not published").isInstanceOf(ModifyMsg.class);
    assertThat(((ModifyMsg) published).getMods())
        .as("the published change does not carry the relaxed attribute")
        .anyMatch(this::modifiesTheRelaxedAttribute);
    // A change with no history cannot be published again from the entry on the next session.
    assertThat(valuesOf(MODIFIED_DN, "ds-sync-hist"))
        .as("the relaxed modify left no historical information")
        .anyMatch(value -> value.toLowerCase().startsWith(RELAXED_ATTRIBUTE.toLowerCase() + ":"));
    final List<String> records = new ArrayList<>(TestCaseUtils.ERROR_TEXT_WRITER.getMessages());
    assertThat(records).as("the relaxed modify was taken for a replayed change")
        .noneMatch(record -> record.contains(NOT_IN_PENDING));
  }

  @Test
  public void aRelaxedAddIsPublishedLikeAnyOtherChange() throws Exception
  {
    TestCaseUtils.ERROR_TEXT_WRITER.clear();
    connection.add(Requests.newAddRequest(
            "dn: " + ADDED_DN,
            "objectClass: top",
            "objectClass: person",
            "sn: relaxed",
            "cn: relaxed add",
            RELAXED_ATTRIBUTE + ": " + RELAXED_VALUE)
        .addControl(new RelaxRulesControl()));

    assertThat(attributeOf(ADDED_DN, RELAXED_ATTRIBUTE)).isEqualTo(RELAXED_VALUE);

    final LDAPUpdateMsg published = nextUpdateFor(ADDED_DN);
    assertThat(published).as("the relaxed add was not published").isInstanceOf(AddMsg.class);
    assertThat(((AddMsg) published).getAttributes())
        .as("the published add does not carry the relaxed attribute")
        .anyMatch(attr -> attr.getAttributeDescription().getAttributeType().hasName(RELAXED_ATTRIBUTE));
    // An add with no history cannot be published again from the entry on the next session.
    assertThat(valuesOf(ADDED_DN, "ds-sync-hist"))
        .as("the relaxed add left no historical information")
        .anyMatch(value -> value.startsWith("dn:") && value.endsWith(":add"));
    final List<String> records = new ArrayList<>(TestCaseUtils.ERROR_TEXT_WRITER.getMessages());
    assertThat(records).as("the relaxed add was taken for a replayed change")
        .noneMatch(record -> record.contains(NOT_IN_PENDING));
  }

  private boolean modifiesTheRelaxedAttribute(Modification mod)
  {
    return mod.getAttribute().getAttributeDescription().getAttributeType().hasName(RELAXED_ATTRIBUTE);
  }

  private String attributeOf(String dn, String attribute) throws Exception
  {
    final SearchResultEntry entry = read(dn);
    return entry.containsAttribute(attribute) ? entry.parseAttribute(attribute).asString() : null;
  }

  private Set<String> valuesOf(String dn, String attribute) throws Exception
  {
    return read(dn).parseAttribute(attribute).asSetOfString();
  }

  private SearchResultEntry read(String dn) throws Exception
  {
    return connection.searchSingleEntry(
        Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT, "(objectClass=*)")
            .addAttribute("*", "+"));
  }

  /**
   * The next update the replication server forwards for the provided entry, or {@code null} if
   * none comes within a few seconds - long enough for a change published by the operation which
   * just returned, which the replication server forwards as soon as it has it.
   * <p>
   * The updates of other entries are skipped: those a failed case leaves behind on the broker
   * the cases share must not be taken for the ones of the next case.
   */
  private LDAPUpdateMsg nextUpdateFor(String dn) throws Exception
  {
    final DN entryDN = DN.valueOf(dn);
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
      if (msg instanceof LDAPUpdateMsg && ((LDAPUpdateMsg) msg).getDN().equals(entryDN))
      {
        return (LDAPUpdateMsg) msg;
      }
    }
    return null;
  }
}
