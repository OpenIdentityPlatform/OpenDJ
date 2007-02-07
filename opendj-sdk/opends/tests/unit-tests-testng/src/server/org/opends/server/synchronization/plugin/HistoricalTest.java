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

package org.opends.server.synchronization.plugin;

import org.opends.server.synchronization.SynchronizationTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Attribute;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import static org.testng.Assert.assertEquals;

import java.util.List;

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
         "description: Initial description"
       );

    // Modify the test entry to give it some history.

    String path = TestCaseUtils.createTempFile(
         "dn: uid=user.1,o=test",
         "changetype: modify",
         "add: cn;lang-en",
         "cn;lang-en: Aaccf Amar",
         "cn;lang-en: Aaccf A Amar",
         "-",
         "replace: description",
         "description: replaced description",
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

    // Read the entry back to get its history operational attribute.
    DN dn = DN.decode("uid=user.1,o=test");
    Entry entry = DirectoryServer.getEntry(dn);

    // Check that encoding and decoding preserves the history information.
    Historical hist = Historical.load(entry);
    Attribute after = hist.encode();

    List<Attribute> attrs = entry.getAttribute(Historical.historicalAttrType);
    Attribute before = attrs.get(0);

    assertEquals(after, before);
  }
}
