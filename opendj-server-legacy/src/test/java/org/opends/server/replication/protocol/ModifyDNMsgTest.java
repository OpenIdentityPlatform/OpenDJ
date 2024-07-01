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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import static org.testng.Assert.*;

import java.util.UUID;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the methods from {@link ModifyDNMsg}. */
@SuppressWarnings("javadoc")
public class ModifyDNMsgTest extends ReplicationTestCase
{
  private String randomUUID()
  {
    return UUID.randomUUID().toString();
  }

  @DataProvider
  Object[][] serializeDeserializeMsgDataProvider()
  {
    CSN csn = new CSN(System.currentTimeMillis(), 1, 42);
    // @formatter:off
    return new Object[][] {
      // dn, csn, entryUUID, newSuperiorEntryUUID, deleteOldRdn, newSuperior, newRDN
      { DN.valueOf("dc=com"), csn, null, null, false, null, null },
      { DN.valueOf("dc=com"), csn, randomUUID(), randomUUID(), true, "dc=org", "ou=People" },
    };
    // @formatter:on
  }

  @Test(dataProvider = "serializeDeserializeMsgDataProvider")
  public void serializeDeserializeMsg(DN dn, CSN csn, String entryUUID, String newSuperiorEntryUUID,
      boolean deleteOldRdn, String newSuperior, String newRDN) throws Exception
  {
    ModifyDNMsg msg = new ModifyDNMsg(dn, csn, entryUUID, newSuperiorEntryUUID, deleteOldRdn, newSuperior, newRDN);
    ModifyDNMsg newMsg = new ModifyDNMsg(msg.getBytes());

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat((Object) msg.getDN()).isEqualTo(newMsg.getDN());
    softly.assertThat(msg.getCSN()).isEqualTo(newMsg.getCSN());
    softly.assertThat(msg.getEntryUUID()).isEqualTo(newMsg.getEntryUUID());
    softly.assertThat(msg.getNewSuperiorEntryUUID()).isEqualTo(newMsg.getNewSuperiorEntryUUID());
    softly.assertThat(msg.getDeleteOldRdn()).isEqualTo(newMsg.getDeleteOldRdn());
    softly.assertThat(msg.getNewSuperior()).isEqualTo(newMsg.getNewSuperior());
    softly.assertThat(msg.getNewRDN()).isEqualTo(newMsg.getNewRDN());
    softly.assertAll();
  }

  @Test
  public void withNewSuperior()
  {
    ModifyDNMsg msg = newModifyDNMsg("dc=com", true, "dc=org", "dc=example");
    assertFalse(msg.newDNIsParent(DN.rootDN()));
    assertTrue(msg.newDNIsParent(DN.valueOf("dc=example,dc=org")));
    assertTrue(msg.newDNIsParent(DN.valueOf("ou=people,dc=example,dc=org")));

    assertFalse(msg.newDNIsEqual(DN.rootDN()));
    assertTrue(msg.newDNIsEqual(DN.valueOf("dc=example,dc=org")));
    assertFalse(msg.newDNIsEqual(DN.valueOf("ou=people,dc=example,dc=org")));

    assertFalse(msg.newParentIsEqual(DN.rootDN()));
    assertTrue(msg.newParentIsEqual(DN.valueOf("dc=org")));
  }

  @Test
  public void noNewSuperior()
  {
    ModifyDNMsg msg = newModifyDNMsg("dc=com", false, null, "dc=example");

    assertFalse(msg.newDNIsParent(DN.rootDN()));
    assertTrue(msg.newDNIsParent(DN.valueOf("dc=example")));
    assertTrue(msg.newDNIsParent(DN.valueOf("ou=people,dc=example")));

    assertFalse(msg.newDNIsEqual(DN.rootDN()));
    assertTrue(msg.newDNIsEqual(DN.valueOf("dc=example")));
    assertFalse(msg.newDNIsEqual(DN.valueOf("ou=people,dc=example")));

    assertTrue(msg.newParentIsEqual(DN.rootDN()));
    assertFalse(msg.newParentIsEqual(DN.valueOf("dc=org")));
  }

  @Test
  public void bogusNewSuperior()
  {
    ModifyDNMsg msg = newModifyDNMsg("dc=com", true, ":::::incorrect:::DN", ":::::incorrect:::RDN");
    assertFalse(msg.newDNIsParent(DN.rootDN()));
    assertFalse(msg.newDNIsEqual(DN.rootDN()));
    assertFalse(msg.newParentIsEqual(DN.rootDN()));
  }

  private ModifyDNMsg newModifyDNMsg(String dn, boolean deleteOldRdn, String newSuperior, String newRDN)
  {
    CSN csn = new CSN(System.currentTimeMillis(), 1, 42);
    return new ModifyDNMsg(DN.valueOf(dn), csn, randomUUID(), randomUUID(), deleteOldRdn, newSuperior, newRDN);
  }
}
