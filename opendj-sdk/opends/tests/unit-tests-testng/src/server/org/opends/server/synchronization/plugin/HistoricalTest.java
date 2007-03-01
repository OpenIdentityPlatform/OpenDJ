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

package org.opends.server.synchronization.plugin;

import org.opends.server.synchronization.SynchronizationTestCase;
import org.opends.server.synchronization.protocol.ModifyMsg;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Attribute;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.AttributeType;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.ArrayList;

/**
 * Tests the Historical class.
 */
public class HistoricalTest
     extends SynchronizationTestCase
{
  /**
   * Set up synchronization on the test backend.
   * @throws Exception If an error occurs.
   */
  @BeforeClass
  @Override
  public void setUp()
       throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    // Create an internal connection.
    connection = InternalClientConnection.getRootConnection();

    // Top level synchronization provider.
    String synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster synchronization plugin.
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
         + synchroStringDN;
    String synchroPluginLdif = "dn: "
         + synchroPluginStringDN
         + "\n"
         + "objectClass: top\n"
         + "objectClass: ds-cfg-synchronization-provider\n"
         + "ds-cfg-synchronization-provider-enabled: true\n"
         + "ds-cfg-synchronization-provider-class: " +
         "org.opends.server.synchronization.MultimasterSynchronization\n";
    synchroPluginEntry = TestCaseUtils.entryFromLdifString(synchroPluginLdif);

    // The synchronization server.
    String changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
         + "objectClass: top\n"
         + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
         + "cn: Changelog Server\n" + "ds-cfg-changelog-port: 8989\n"
         + "ds-cfg-changelog-server-id: 1\n";
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);

    // The suffix to be synchronized.
    String synchroServerStringDN = "o=test, " + synchroPluginStringDN;
    String synchroServerLdif = "dn: " + synchroServerStringDN + "\n"
         + "objectClass: top\n"
         + "objectClass: ds-cfg-synchronization-provider-config\n"
         + "cn: example\n"
         + "ds-cfg-synchronization-dn: o=test\n"
         + "ds-cfg-changelog-server: localhost:8989\n"
         + "ds-cfg-directory-server-id: 1\n"
         + "ds-cfg-receive-status: true\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    configureSynchronization();
  }

  /**
   * Tests that the attribute modification history is correctly read from
   * and written to an operational attribute of the entry.
   * @throws Exception If the test fails.
   */
  @Test
  public void testEncoding()
       throws Exception
  {
    //  Add a test entry.
    TestCaseUtils.addEntry(
         "dn: uid=user.1,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: user.1",
         "cn: Aaccf Amar",
         "sn: Amar",
         "givenName: Aaccf",
         "userPassword: password",
         "description: Initial description",
         "displayName: 1"
       );

    // Modify the test entry to give it some history.
    // Test both single and multi-valued attributes.

    String path = TestCaseUtils.createTempFile(
         "dn: uid=user.1,o=test",
         "changetype: modify",
         "add: cn;lang-en",
         "cn;lang-en: Aaccf Amar",
         "cn;lang-en: Aaccf A Amar",
         "-",
         "replace: description",
         "description: replaced description",
         "-",
         "add: displayName",
         "displayName: 2",
         "-",
         "delete: displayName",
         "displayName: 1",
         "-"
    );

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    args[9] = TestCaseUtils.createTempFile(
         "dn: uid=user.1,o=test",
         "changetype: modify",
         "replace: displayName",
         "displayName: 2",
         "-"
    );

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);

    // Read the entry back to get its history operational attribute.
    DN dn = DN.decode("uid=user.1,o=test");
    Entry entry = DirectoryServer.getEntry(dn);

    List<Attribute> attrs = entry.getAttribute(Historical.historicalAttrType);
    Attribute before = attrs.get(0);

    // Check that encoding and decoding preserves the history information.
    Historical hist = Historical.load(entry);
    Attribute after = hist.encode();

    assertEquals(after, before);
  }

  /**
   * The scenario for this test case is that two modify operations occur at
   * two different servers at nearly the same time, each operation adding a
   * different value for a single-valued attribute.  Synchronization then
   * replays the operations and we expect the conflict to be resolved on both
   * servers by keeping whichever value was actually added first.
   * For the unit test, we employ a single directory server.  We use the
   * broker API to simulate the ordering that would happen on the first server
   * on one entry, and the reverse ordering that would happen on the
   * second server on a different entry.  Confused yet?
   * @throws Exception If the test fails.
   */
  @Test(enabled=false, groups="slow")
  public void conflictSingleValue()
       throws Exception
  {
    final DN dn1 = DN.decode("cn=test1,o=test");
    final DN dn2 = DN.decode("cn=test2,o=test");
    final DN baseDn = DN.decode("o=test");
    final AttributeType attrType =
         DirectoryServer.getAttributeType("displayname");
    final AttributeType entryuuidType =
         DirectoryServer.getAttributeType("entryuuid");

    /*
     * Open a session to the changelog server using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    ChangelogBroker broker =
      openChangelogSession(baseDn, (short) 2, 100, 8989, 1000, true);


    // Clear the backend.
    TestCaseUtils.initializeTestBackend(true);

    // Add the first test entry.
    TestCaseUtils.addEntry(
         "dn: cn=test1,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: test1",
         "sn: test"
       );

    // Read the entry back to get its UUID.
    Entry entry = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs = entry.getAttribute(entryuuidType);
    String entryuuid =
         attrs.get(0).getValues().iterator().next().getStringValue();

    // Add the second test entry.
    TestCaseUtils.addEntry(
         "dn: cn=test2,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: test2",
         "sn: test",
         "description: Description"
       );

    // Read the entry back to get its UUID.
    entry = DirectoryServer.getEntry(dn2);
    attrs = entry.getAttribute(entryuuidType);
    String entryuuid2 =
         attrs.get(0).getValues().iterator().next().getStringValue();

    // A change on a first server.
    ChangeNumber t1 = new ChangeNumber(1, (short) 0, (short) 3);

    // A change on a second server.
    ChangeNumber t2 = new ChangeNumber(2, (short) 0, (short) 4);

    // Simulate the ordering t1:add:A followed by t2:add:B that would
    // happen on one server.

    // Replay an add of a value A at time t1 on a first server.
    Attribute attr = new Attribute(attrType.getNormalizedPrimaryName(), "A");
    Modification mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t1, dn1, entryuuid, mod);

    // It would be nice to avoid these sleeps.
    // We need to preserve the replay order but the order could be changed
    // due to the multi-threaded nature of the synchronization replay.
    // Putting a sentinel value in the modification is not foolproof since
    // the operation might not get replayed at all.
    Thread.sleep(2000);

    // Replay an add of a value B at time t2 on a second server.
    attr = new Attribute(attrType.getNormalizedPrimaryName(), "B");
    mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t2, dn1, entryuuid, mod);

    Thread.sleep(2000);

    // Simulate the reverse ordering t2:add:B followed by t1:add:A that
    // would happen on the other server.

    t1 = new ChangeNumber(3, (short) 0, (short) 3);
    t2 = new ChangeNumber(4, (short) 0, (short) 4);

    // Replay an add of a value B at time t2 on a second server.
    attr = new Attribute(attrType.getNormalizedPrimaryName(), "B");
    mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t2, dn2, entryuuid2, mod);

    Thread.sleep(2000);

    // Replay an add of a value A at time t1 on a first server.
    attr = new Attribute(attrType.getNormalizedPrimaryName(), "A");
    mod = new Modification(ModificationType.ADD, attr);
    publishModify(broker, t1, dn2, entryuuid2, mod);

    Thread.sleep(2000);

    // Read the first entry to see how the conflict was resolved.
    entry = DirectoryServer.getEntry(dn1);
    attrs = entry.getAttribute(attrType);
    String attrValue1 =
         attrs.get(0).getValues().iterator().next().getStringValue();

    // Read the second entry to see how the conflict was resolved.
    entry = DirectoryServer.getEntry(dn2);
    attrs = entry.getAttribute(attrType);
    String attrValue2 =
         attrs.get(0).getValues().iterator().next().getStringValue();

    // The two values should be the first value added.
    assertEquals(attrValue1, "A");
    assertEquals(attrValue2, "A");
  }

  private static
  void publishModify(ChangelogBroker broker, ChangeNumber changeNum,
                     DN dn, String entryuuid, Modification mod)
  {
    List<Modification> mods = new ArrayList<Modification>(1);
    mods.add(mod);
    ModifyMsg modMsg = new ModifyMsg(changeNum, dn, mods, entryuuid);
    broker.publish(modMsg);
  }
}
