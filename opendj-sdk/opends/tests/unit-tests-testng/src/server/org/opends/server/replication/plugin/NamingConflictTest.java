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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import static org.opends.server.TestCaseUtils.TEST_ROOT_DN_STRING;

import java.util.TreeSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


/**
 * Test the naming conflict resolution code.
 */
public class NamingConflictTest extends ReplicationTestCase
{
  /**
   * Test for issue 3402 : test, that a modrdn that is older than an other
   * modrdn but that is applied later is ignored.
   *
   * In this test, the local server act both as an LDAP server and
   * a replicationServer that are inter-connected.
   *
   * The test creates an other session to the replicationServer using
   * directly the ReplicationBroker API.
   * It then uses this session to simulate conflicts and therefore
   * test the naming conflict resolution code.
   */
  @Test(enabled=true)
  public void simultaneousModrdnConflict() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DN baseDn = DN.decode(TEST_ROOT_DN_STRING);

    TestSynchronousReplayQueue queue = new TestSynchronousReplayQueue();
    DomainFakeCfg conf = new DomainFakeCfg(baseDn, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);

    LDAPReplicationDomain domain =
      MultimasterReplication.createNewDomain(conf, queue);

    /*
     * Create a Change number generator to generate new ChangeNumbers
     * when we need to send operations messages to the replicationServer.
     */
    ChangeNumberGenerator gen = new ChangeNumberGenerator((short) 201, 0);

    String parentUUID = getEntryUUID(DN.decode(TEST_ROOT_DN_STRING));

    Entry entry = TestCaseUtils.entryFromLdifString(
        "dn: cn=simultaneousModrdnConflict, "+ TEST_ROOT_DN_STRING + "\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "homePhone: 951-245-7634\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "mobile: 027-085-0537\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
        + "street: 17984 Thirteenth Street\n"
        + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
        + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n");

    TestCaseUtils.addEntry(entry);
    String entryUUID = getEntryUUID(entry.getDN());

    // generate two consecutive ChangeNumber that will be used in backward order
    ChangeNumber cn1 = gen.newChangeNumber();
    ChangeNumber cn2 = gen.newChangeNumber();

    ModifyDNMsg  modDnMsg = new ModifyDNMsg(
        entry.getDN().toNormalizedString(), cn2,
        entryUUID, parentUUID, false,
        TEST_ROOT_DN_STRING,
        "uid=simultaneous2");

    domain.processUpdate(modDnMsg);
    domain.replay(queue.take().getUpdateMessage());

    // This MODIFY DN uses an older DN and should therefore be cancelled
    // at replay time.
    modDnMsg = new ModifyDNMsg(
        entry.getDN().toNormalizedString(), cn1,
        entryUUID, parentUUID, false,
        TEST_ROOT_DN_STRING,
        "uid=simulatneouswrong");

    domain.processUpdate(modDnMsg);
    domain.replay(queue.take().getUpdateMessage());

    assertFalse(DirectoryServer.entryExists(entry.getDN()),
        "The modDN conflict was not resolved as expected.");

    MultimasterReplication.deleteDomain(baseDn);
  }
}
