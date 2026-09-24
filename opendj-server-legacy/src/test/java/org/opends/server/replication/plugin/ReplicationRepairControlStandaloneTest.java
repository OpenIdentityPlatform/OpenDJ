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

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;
import static org.opends.server.replication.plugin.ReplicationRepairRequestControl.OID_REPLICATION_REPAIR_CONTROL;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.testng.annotations.Test;

/**
 * The replication repair request control on a server with no replication domain configured -
 * which is where an administrator giving entries back the {@code entryUUID} another directory
 * assigned them often is, before replication is enabled.
 * <p>
 * {@link ReplicationRepairControlTest} configures a domain before any of its cases runs, so it
 * cannot see whether the control is still honoured when the replication plugin has no domain to
 * look for.
 */
@SuppressWarnings("javadoc")
public class ReplicationRepairControlStandaloneTest extends ReplicationTestCase
{
  private static final String DN = "cn=standalone repair," + TEST_ROOT_DN_STRING;
  private static final String UUID = "5e7b9c1d-2a4f-4d6e-8b3c-9f0a1b2c3d4e";

  @Test
  public void aRepairedModifySetsTheEntryUUIDWithNoReplicationDomain() throws Exception
  {
    assertThat(MultimasterReplication.getNumberOfDomains())
        .as("a replication domain is configured, so this case does not run where it means to")
        .isZero();
    TestCaseUtils.addEntry(
        "dn: " + DN,
        "objectClass: top",
        "objectClass: person",
        "sn: standalone",
        "cn: standalone repair");

    try (LDAPConnectionFactory factory =
            new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        Connection connection = factory.getConnection())
    {
      connection.bind("cn=Directory Manager", "password".toCharArray());

      try
      {
        connection.modify(entryUUIDRequest());
        throw new AssertionError("entryUUID was modified without the repair control");
      }
      catch (LdapException e)
      {
        assertThat(e.getResult().getResultCode()).isEqualTo(ResultCode.CONSTRAINT_VIOLATION);
      }

      connection.modify(entryUUIDRequest()
          .addControl(GenericControl.newControl(OID_REPLICATION_REPAIR_CONTROL)));

      assertThat(connection.searchSingleEntry(
              Requests.newSearchRequest(DN, SearchScope.BASE_OBJECT, "(objectClass=*)").addAttribute("entryUUID"))
          .parseAttribute("entryUUID").asString()).isEqualTo(UUID);
    }
  }

  private static ModifyRequest entryUUIDRequest()
  {
    return Requests.newModifyRequest(DN).addModification(ModificationType.REPLACE, "entryUUID", UUID);
  }
}
