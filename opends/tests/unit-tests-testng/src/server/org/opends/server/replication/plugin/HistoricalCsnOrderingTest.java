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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.net.ServerSocket;
import java.util.List;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.opends.server.TestCaseUtils.*;

/**
 * Test the usage of the historical data of the replication.
 */
public class HistoricalCsnOrderingTest
extends ReplicationTestCase
{
  /**
   * A "person" entry
   */
  protected Entry personEntry;
  private int replServerPort;

  /**
   * Set up the environment for performing the tests in this Class.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    // Create necessary backend top level entry
    String topEntry = "dn: ou=People," + TEST_ROOT_DN_STRING + "\n"
        + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "entryUUID: 11111111-1111-1111-1111-111111111111\n";
    addEntry(TestCaseUtils.entryFromLdifString(topEntry));

    // find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();

    // replication server
    String replServerLdif =
      "dn: cn=Replication Server, " + SYNCHRO_PLUGIN_DN + "\n"
      + "objectClass: top\n"
      + "objectClass: ds-cfg-replication-server\n"
      + "cn: Replication Server\n"
      + "ds-cfg-replication-port: " + replServerPort + "\n"
      + "ds-cfg-replication-db-directory: HistoricalCsnOrderingTestDb\n"
      + "ds-cfg-replication-server-id: 101\n";
    replServerEntry = TestCaseUtils.entryFromLdifString(replServerLdif);

    // suffix synchronized
    String testName = "historicalCsnOrderingTest";
    String synchroServerLdif =
      "dn: cn=" + testName + ", cn=domains, " + SYNCHRO_PLUGIN_DN + "\n"
      + "objectClass: top\n"
      + "objectClass: ds-cfg-replication-domain\n"
      + "cn: " + testName + "\n"
      + "ds-cfg-base-dn: ou=People," + TEST_ROOT_DN_STRING + "\n"
      + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
      + "ds-cfg-server-id: 1\n" + "ds-cfg-receive-status: true\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    String personLdif = "dn: uid=user.1,ou=People," + TEST_ROOT_DN_STRING + "\n"
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
      + "userPassword: password\n" + "initials: AA\n";
    personEntry = TestCaseUtils.entryFromLdifString(personLdif);

    configureReplication();
  }

  /**
   * Add an entry in the database
   *
   */
  private void addEntry(Entry entry) throws Exception
  {
    AddOperationBasis addOp = new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(), InternalClientConnection
        .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
        entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }

  /**
   * Check the basic comparator on the HistoricalCsnOrderingMatchingRule
   */
  @Test()
  public void basicRuleTest()
  throws Exception
  {
    // Creates a rule
    HistoricalCsnOrderingMatchingRule r = 
      new HistoricalCsnOrderingMatchingRule();

    ChangeNumber del1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber del2 = new ChangeNumber(1, (short) 1, (short) 1);

    ByteString v1 = new ASN1OctetString("a"+":"+del1.toString());
    ByteString v2 = new ASN1OctetString("a"+":"+del2.toString());

    int cmp = r.compareValues(v1, v1);
    assertTrue(cmp == 0);

    cmp = r.compareValues(v1, v2);
    assertTrue(cmp == -1);

    cmp = r.compareValues(v2, v1);
    assertTrue(cmp == 1);
  }

  /**
   * Test that we can retrieve the entries that were missed by 
   * a replication server and can  re-build operations from the historical
   * informations.
   */
  @Test()
  public void changesCmpTest()
  throws Exception
  {
    final DN baseDn = DN.decode("ou=People," + TEST_ROOT_DN_STRING);
    final DN dn1 = DN.decode("cn=test1," + baseDn.toString());
    final AttributeType histType =
      DirectoryServer.getAttributeType(Historical.HISTORICALATTRIBUTENAME);

    logError(Message.raw(Category.SYNC, Severity.INFORMATION,
    "Starting replication test : changesCmpTest"));

    // Add the first test entry.
    TestCaseUtils.addEntry(
        "dn: cn=test1," + baseDn.toString(),
        "displayname: Test1",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );

    // Perform a first modification to update the historical attribute
    int resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDn.toString(),
        "changetype: modify",
        "add: description",
    "description: foo");
    assertEquals(resultCode, 0);

    // Read the entry back to get its historical and included changeNumber
    Entry entry = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs1 = entry.getAttribute(histType);

    assertTrue(attrs1 != null);
    assertTrue(attrs1.isEmpty() != true);

    String histValue =
      attrs1.get(0).iterator().next().getStringValue();

    logError(Message.raw(Category.SYNC, Severity.INFORMATION,
        "First historical value:" + histValue));

    // Perform a 2nd modification to update the hist attribute with 
    // a second value
    resultCode = TestCaseUtils.applyModifications(false,
        "dn: cn=test1," + baseDn.toString(),
        "changetype: modify",
        "add: description",
    "description: bar");
    assertEquals(resultCode, 0);

    Entry entry2 = DirectoryServer.getEntry(dn1);
    List<Attribute> attrs2 = entry2.getAttribute(histType);

    assertTrue(attrs2 != null);
    assertTrue(attrs2.isEmpty() != true);

    for (AttributeValue av : attrs2.get(0)) {
      logError(Message.raw(Category.SYNC, Severity.INFORMATION,
          "Second historical value:" + av.getStringValue()));
    }

    // Build a change number from the first modification
    String hv[] = histValue.split(":");
    logError(Message.raw(Category.SYNC, Severity.INFORMATION,
        hv[1]));
    ChangeNumber fromChangeNumber =
      new ChangeNumber(hv[1]);

    // Retrieves the entries that have changed since the first modification
    InternalSearchOperation op =
      ReplicationBroker.searchForChangedEntries(baseDn, fromChangeNumber, null);

    // The expected result is one entry .. the one previously modified
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    assertEquals(op.getSearchEntries().size(), 1);

    // From the historical of this entry, rebuild operations
    // Since there have been 2 modifications and 1 add, there should be 3
    // operations rebuild from this state.
    int updatesCnt = 0;
    for (SearchResultEntry searchEntry : op.getSearchEntries())
    {
      logError(Message.raw(Category.SYNC, Severity.INFORMATION,
          searchEntry.toString()));
      Iterable<FakeOperation> updates =
        Historical.generateFakeOperations(searchEntry);
      for (FakeOperation fop : updates)
      {
        logError(Message.raw(Category.SYNC, Severity.INFORMATION,
            fop.generateMessage().toString()));
        updatesCnt++;
      }
    }
    assertTrue(updatesCnt == 3);    
  }
}
