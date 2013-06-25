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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 *      Portions copyright 2013 Manuel Gaupp
 */
package org.opends.server.core;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import static org.testng.Assert.*;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.*;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

import java.net.Socket;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

@SuppressWarnings("javadoc")
public class TestModifyDNOperation extends OperationTestCase
{

  private Entry entry;
  private InternalClientConnection proxyUserConn;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearJEBackend(false, "userRoot", "dc=example,dc=com");

    InternalClientConnection connection =
         InternalClientConnection.getRootConnection();

    // Add the example.com entry
    Entry exampleCom = TestCaseUtils.makeEntry(
      "dn: dc=example,dc=com",
      "objectclass: top",
      "objectclass: domain",
      "dc: example",
      "aci: (targetattr=\"*\")(version 3.0; acl \"Proxy Rights\"; " +
           "allow(proxy) userdn=\"ldap:///uid=proxy.user,o=test\";)"
    );

    // Add the people entry
    Entry people = TestCaseUtils.makeEntry(
      "dn: ou=People,dc=example,dc=com",
      "objectclass: top",
      "objectclass: organizationalUnit",
      "ou: People"
    );

    // Add a test entry.
    entry = TestCaseUtils.makeEntry(
      "dn: uid=user.0,ou=People,dc=example,dc=com",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "givenName: Aaccf",
      "sn: Amar",
      "cn: Aaccf Amar",
      "initials: AQA",
      "employeeNumber: 0",
      "uid: user.0",
      "mail: user.0@example.com",
      "userPassword: password",
      "telephoneNumber: 380-535-2354",
      "homePhone: 707-626-3913",
      "pager: 456-345-7750",
      "mobile: 366-674-7274",
      "street: 99262 Eleventh Street",
      "l: Salem",
      "st: NM",
      "postalCode: 36530",
      "postalAddress: Aaccf Amar$99262 Eleventh Street$Salem, NM  36530",
      "description: This is the description for Aaccf Amar."
    );

    AddOperation addOperation =
         connection.processAdd(exampleCom.getDN(),
                               exampleCom.getObjectClasses(),
                               exampleCom.getUserAttributes(),
                               exampleCom.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(exampleCom.getDN()));

    addOperation =
         connection.processAdd(people.getDN(),
                               people.getObjectClasses(),
                               people.getUserAttributes(),
                               people.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(people.getDN()));

    addOperation =
         connection.processAdd(entry.getDN(),
                               entry.getObjectClasses(),
                               entry.getUserAttributes(),
                               entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(DirectoryServer.getEntry(entry.getDN()));

    // Add a user capable of using the proxied authorization control.
    TestCaseUtils.addEntry(
         "dn: uid=proxy.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: proxy.user",
         "givenName: Proxy",
         "sn: User",
         "cn: Proxy User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl",
         "ds-privilege-name: proxied-auth");

    proxyUserConn =
         new InternalClientConnection(DN.decode("uid=proxy.user,o=test"));
  }



  /**
   * Invokes a number of operation methods on the provided modify operation
   * for which all processing has been completed. This method is used for
   * tests that bypass the referential integrity plugin for whatever reason.
   *
   * @param  modifyDNOperation  The operation to be tested.
   */
  private void
  examineCompletedOPNoExtraPluginCounts(ModifyDNOperation modifyDNOperation)
  {
    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertNotNull(modifyDNOperation.getResponseLogElements());

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }


  /**
   * Invokes a number of operation methods on the provided modify operation
   * for which all processing has been completed.  The counters
   * postResponseCount and preParseCount are incremented twice when
   * referential integrity plugin is enabled.
   *
   * @param  modifyDNOperation  The operation to be tested.
   */
  private void examineCompletedOperation(ModifyDNOperation modifyDNOperation)
  {
    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertNotNull(modifyDNOperation.getResponseLogElements());

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 2);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 2);
  }

  /**
   * Invokes a number of operation methods on the provided modify operation
   * for which the pre-operation plugin was not called.
   *
   * @param  modifyDNOperation  The operation to be tested.
   */
  private void examineIncompleteOperation(ModifyDNOperation modifyDNOperation,
      ResultCode resultCode)
  {
    assertEquals(modifyDNOperation.getResultCode(), resultCode);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertNotNull(modifyDNOperation.getResponseLogElements());
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 1);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  /**
   * Invokes a number of operation methods on the provided modify operation
   * for which an error was found during parsing.
   *
   * @param  modifyDNOperation  The operation to be tested.
   */
  private void examineUnparsedOperation(ModifyDNOperation modifyDNOperation,
      ResultCode resultCode)
  {
    assertEquals(modifyDNOperation.getResultCode(), resultCode);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertNotNull(modifyDNOperation.getResponseLogElements());
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  protected Operation[] createTestOperations() throws Exception
  {
    return new ModifyDNOperationBasis[] {
      newModifyDNOperation( ByteString.valueOf("cn=test,ou=test"),
                            ByteString.valueOf("cn=test2"), true,
                            ByteString.valueOf("dc=example,dc=com")),
      newModifyDNOperation( DN.decode("cn=test,ou=test"),
                            RDN.decode("cn=test2"), true,
                            DN.decode("dc=example,dc=com"))
    };
  }

  private ModifyDNOperationBasis newModifyDNOperation(DN rawEntryDN,
      RDN newRDN, boolean deleteOldRDN, DN newSuperior)
  {
    final ClientConnection conn = InternalClientConnection.getRootConnection();
    return new ModifyDNOperationBasis(conn, InternalClientConnection
        .nextOperationID(), InternalClientConnection.nextMessageID(),
        Collections.<Control> emptyList(), rawEntryDN, newRDN, deleteOldRDN,
        newSuperior);
  }

  private ModifyDNOperationBasis newModifyDNOperation(ByteString rawEntryDN,
      ByteString newRDN, boolean deleteOldRDN, ByteString newSuperior)
  {
    final ClientConnection conn = InternalClientConnection.getRootConnection();
    return new ModifyDNOperationBasis(conn, InternalClientConnection
        .nextOperationID(), InternalClientConnection.nextMessageID(),
        Collections.<Control> emptyList(), rawEntryDN, newRDN, deleteOldRDN,
        newSuperior);
  }

  private void assertSuccessAndEntryExists(ModifyDNOperation modifyDNOperation,
      String entryDN, boolean user0Exists, boolean userTest0Exists)
      throws DirectoryException
  {
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    final Entry newEntry = DirectoryServer.getEntry(DN.decode(entryDN));
    assertNotNull(newEntry);

    final RDN rdn = newEntry.getDN().getRDN();
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      AttributeType attribute = rdn.getAttributeType(i);
      assertEquals(newEntry.hasValue(attribute, null, AttributeValues.create(
          attribute, "user.0")), user0Exists);
      assertEquals(newEntry.hasValue(attribute, null, AttributeValues.create(
          attribute, "user.test0")), userTest0Exists);
    }
  }

  @Test
  public void testRawModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.test0"), false,
                               null);
    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,ou=People,dc=example,dc=com", true, true);
    examineCompletedOperation(modifyDNOperation);


    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.test0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.0"), true,
                               null);
    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);
    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,ou=People,dc=example,dc=com", true, true);
    examineCompletedOperation(modifyDNOperation);


    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);
    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  /**
   * Test if it's possible to modify an rdn to a value that matches the current value
   * by changing the case of some characters
   */
  @Test
  public void testModifySameDN() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=USER.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);

    assertEquals(newEntry.getDN().toString(),
        "uid=USER.0,ou=People,dc=example,dc=com");

    AttributeType at = DirectoryServer.getAttributeType("uid");
    List<Attribute> attrList = newEntry.getAttribute(at);

    // There should be only one value for "uid"
    assertEquals(attrList.size(),1);

    // Because deleteOldRDN is true, the values from RDN and the entry have to be identical
    ByteString valueFromEntry = attrList.get(0).iterator().next().getValue();
    ByteString valueFromRDN = newEntry.getDN().getRDN().getAttributeValue(at).getValue();
    assertEquals(valueFromEntry,valueFromRDN);

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=USER.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);

    examineCompletedOperation(modifyDNOperation);
  }

  /**
   * Add another attribute to the RDN and change case of the existing value
   */
  @Test
  public void testModifyDNchangeCaseAndAddValue() throws Exception
  {
    TestCaseUtils.addEntry(
         "dn: uid=userid.0,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: userid.0",
         "givenName: Babs",
         "sn: Jensen",
         "cn: Babs Jensen");

    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=userid.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=UserID.0+cn=Test"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=userid.0+cn=test,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);

    assertEquals(newEntry.getDN().toString(),
        "uid=UserID.0+cn=Test,ou=People,dc=example,dc=com");

    AttributeType at = DirectoryServer.getAttributeType("uid");
    List<Attribute> attrList = newEntry.getAttribute(at);

    // There should be only one value for "uid"
    assertEquals(attrList.size(),1);

    // Even though the value of the RDN changed, the representation of the entry's value should be preserved
    ByteString valueFromEntry = attrList.get(0).iterator().next().getValue();
    ByteString valueFromRDN = newEntry.getDN().getRDN().getAttributeValue(at).getValue();
    assertEquals(valueFromEntry,ByteString.valueOf("userid.0"));

    examineCompletedOperation(modifyDNOperation);
    TestCaseUtils.deleteEntry(DN.decode("uid=UserID.0+cn=Test,ou=People,dc=example,dc=com"));
  }

  /**
   * Add a value to the RDN which is already part of the entry, but with another string representation
   */
  @Test
  public void testModifyDNchangeCaseOfExistingEntryValue() throws Exception
  {
    TestCaseUtils.addEntry(
         "dn: uid=userid.0,ou=People,dc=example,dc=com",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: userid.0",
         "givenName: Babs",
         "sn: Jensen",
         "cn: Babs Jensen");

    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=userid.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=userid.0+sn=JENSEN"), false,
                               null);

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "uid=userid.0+sn=jensen,ou=People,dc=example,dc=com"));
    assertNotNull(newEntry);

    assertEquals(newEntry.getDN().toString(),
        "uid=userid.0+sn=JENSEN,ou=People,dc=example,dc=com");

    AttributeType at = DirectoryServer.getAttributeType("sn");
    List<Attribute> attrList = newEntry.getAttribute(at);

    // There should be only one value for "sn"
    assertEquals(attrList.size(),1);

    // Even though the representation of the sn value differs in the RDN,
    // the representation of the entry's value should be preserved
    ByteString valueFromEntry = attrList.get(0).iterator().next().getValue();
    ByteString valueFromRDN = newEntry.getDN().getRDN().getAttributeValue(at).getValue();
    assertEquals(valueFromEntry,ByteString.valueOf("Jensen"));

    examineCompletedOperation(modifyDNOperation);
    TestCaseUtils.deleteEntry(DN.decode("uid=userid.0+sn=Jensen,ou=People,dc=example,dc=com"));
  }

  @Test
  public void testRawDeleteOldRDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.test0"), true,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,ou=People,dc=example,dc=com", false, true);

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.test0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedDeleteOldRDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               null);

    modifyDNOperation.run();

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    Message.raw("testCancelBeforeStartup"));
    CancelResult cancelResult = modifyDNOperation.cancel(cancelRequest);

    assertEquals(cancelResult.getResultCode(), ResultCode.TOO_LATE);
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,ou=People,dc=example,dc=com", false, true);

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testRawNewSuperiorModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.test0"), true,
                               ByteString.valueOf("dc=example,dc=com"));

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,dc=example,dc=com", false, true);

    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.test0,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.0"), true,
                               ByteString.valueOf("ou=People,dc=example,dc=com"));

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);

    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
  }

  @Test
  public void testProcessedNewSuperiorModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,dc=example,dc=com", false, true);

    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.test0,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               DN.decode("ou=People,dc=example,dc=com"));

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);

    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
  }

  @Test
  public void testRawRDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("cn=Aaccf Amar Test"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newEntry = DirectoryServer.getEntry(DN.decode(
        "cn=Aaccf Amar Test,dc=example,dc=com"));
    assertNotNull(newEntry);
    assertNull(DirectoryServer.getEntry(DN.decode("uid=user.0,ou=People,dc=example,dc=com")));
    assertNull(newEntry.getAttribute("uid"));

    for(Attribute attribute : newEntry.getAttribute("cn"))
    {
      assertTrue(attribute.contains(AttributeValues.create(attribute.getAttributeType(), "Aaccf Amar Test")));
      assertTrue(attribute.contains(AttributeValues.create(attribute.getAttributeType(), "Aaccf Amar")));
    }

    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation = newModifyDNOperation(
                               DN.decode("cn=Aaccf Amar Test,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), false,
                               DN.decode("ou=People,dc=example,dc=com"));

    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.SUCCESS);
    assertEquals(modifyDNOperation.getErrorMessage().length(), 0);
    Entry newOldEntry = DirectoryServer.getEntry(DN.decode(
        "uid=user.0,ou=People,dc=example,dc=com"));
    assertNotNull(newOldEntry);
    assertNull(DirectoryServer.getEntry(DN.decode("cn=Aaccf Amar Test,dc=example,dc=com")));
    for(Attribute attribute : newOldEntry.getAttribute("cn"))
    {
      assertTrue(attribute.contains(AttributeValues.create(attribute.getAttributeType(), "Aaccf Amar Test")));
      assertTrue(attribute.contains(AttributeValues.create(attribute.getAttributeType(), "Aaccf Amar")));
    }
    for(Attribute attribute : newOldEntry.getAttribute("uid"))
    {
      assertTrue(attribute.contains(AttributeValues.create(attribute.getAttributeType(), "user.0")));
    }
    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
  }

  @Test
  public void testInvalidEntryModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.invalid,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation, ResultCode.NO_SUCH_OBJECT);
  }

  @Test
  public void testInvalidRDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("invalid=invalid"), true,
                               DN.decode("dc=example,dc=com"));

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation,
        ResultCode.OBJECTCLASS_VIOLATION);
  }

  @Test
  public void testInvalidSuperiorModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               DN.decode("dc=invalid,dc=com"));

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation, ResultCode.NO_SUCH_OBJECT);
  }

  @Test
  public void testRawNoSuchDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("invalid DN"),
                               ByteString.valueOf("uid=user.test0"), true,
                               ByteString.valueOf("dc=example,dc=com"));

    modifyDNOperation.run();
    examineUnparsedOperation(modifyDNOperation, ResultCode.INVALID_DN_SYNTAX);
  }

  @Test
  public void testRawNoSuchRDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("invalid RDN"), true,
                               ByteString.valueOf("dc=example,dc=com"));

    modifyDNOperation.run();
    examineUnparsedOperation(modifyDNOperation, ResultCode.INVALID_DN_SYNTAX);
  }

  @Test
  public void testRawInvalidSuperiorModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               ByteString.valueOf("uid=user.0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.test0"), true,
                               ByteString.valueOf("invalid superior"));

    modifyDNOperation.run();
    examineUnparsedOperation(modifyDNOperation, ResultCode.INVALID_DN_SYNTAX);
  }

  @Test
  public void testModifySuffix() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("dc=example,dc=com"),
                               RDN.decode("dc=exampletest"), true,
                               null);

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation,
        ResultCode.UNWILLING_TO_PERFORM);
  }

  @Test
  public void testRawProxyAuthV1Modify() throws Exception
  {
    ProxiedAuthV1Control authV1Control = new ProxiedAuthV1Control(
         ByteString.valueOf("cn=Directory Manager,cn=Root DNs,cn=config"));
    List<Control> controls = Collections.<Control> singletonList(authV1Control);
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               ByteString.valueOf("uid=user.0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,ou=People,dc=example,dc=com", true, true);

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               ByteString.valueOf("uid=user.test0,ou=People,dc=example,dc=com"),
                               ByteString.valueOf("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV1Modify() throws Exception
  {
    ProxiedAuthV1Control authV1Control = new ProxiedAuthV1Control(ByteString.valueOf(
      "cn=Directory Manager,cn=Root DNs,cn=config"));
    List<Control> controls = Collections.<Control> singletonList(authV1Control);
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,ou=People,dc=example,dc=com", true, true);

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(),
                               controls,
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV1DeniedModify() throws Exception
  {
    ProxiedAuthV1Control authV1Control =
         new ProxiedAuthV1Control(ByteString.valueOf("cn=nonexistent,o=test"));
    List<Control> controls = Collections.<Control> singletonList(authV1Control);
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation,
        ResultCode.AUTHORIZATION_DENIED);
  }

  @Test
  public void testProcessedProxyAuthV2Modify() throws Exception
  {
    ProxiedAuthV2Control authV2Control =
         new ProxiedAuthV2Control(ByteString.valueOf(
              "dn:cn=Directory Manager,cn=Root DNs,cn=config"));
    List<Control> controls = Collections.<Control> singletonList(authV2Control);
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.test0,ou=People,dc=example,dc=com", true, true);

    examineCompletedOperation(modifyDNOperation);
    InvocationCounterPlugin.resetAllCounters();

    modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               DN.decode("uid=user.test0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.0"), true,
                               null);

    modifyDNOperation.run();
    assertSuccessAndEntryExists(modifyDNOperation,
        "uid=user.0,ou=People,dc=example,dc=com", true, false);

    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV2DeniedModify() throws Exception
  {
    ProxiedAuthV2Control authV2Control = new ProxiedAuthV2Control(
         ByteString.valueOf("dn:cn=nonexistent,o=test"));
    List<Control> controls = Collections.<Control> singletonList(authV2Control);
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation,
        ResultCode.AUTHORIZATION_DENIED);
  }

  @Test
  public void testProcessedProxyAuthV2CriticalityModify() throws Exception
  {
    Control authV2Control =
         new LDAPControl(ServerConstants.OID_PROXIED_AUTH_V2, false,
                     ByteString.valueOf("dn:cn=nonexistent,o=test"));

    List<Control> controls = Collections.<Control> singletonList(authV2Control);
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperationBasis(proxyUserConn, InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation, ResultCode.PROTOCOL_ERROR);
  }

  @Test
  public void testProcessedUnsupportedControlModify() throws Exception
  {
    LDAPControl assertControl =
         new LDAPControl("1.1.1.1.1.1", true);
    List<Control> controls = Collections.<Control> singletonList(assertControl);
    InvocationCounterPlugin.resetAllCounters();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperationBasis(conn, InternalClientConnection.nextOperationID(), InternalClientConnection.nextMessageID(),
                               controls,
                               DN.decode("uid=user.0,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), false,
                               null);

    modifyDNOperation.run();
    examineIncompleteOperation(modifyDNOperation,
        ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
  }

  @Test
  public void testShortCircuitModify() throws Exception
  {
    // Since we are going to be watching the post-response count, we need to
    // wait for the server to become idle before kicking off the next request to
    // ensure that any remaining post-response processing from the previous
    // operation has completed.
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


    // Establish a connection to the server.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());

    InvocationCounterPlugin.resetAllCounters();

    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);
    BindRequestProtocolOp bindRequest =
              new BindRequestProtocolOp(
                      ByteString.valueOf("cn=Directory Manager"),
                      3, ByteString.valueOf("password"));
    LDAPMessage bindMessage = new LDAPMessage(1, bindRequest);
    w.writeMessage(bindMessage);

    bindMessage = r.readMessage();
    BindResponseProtocolOp bindResponse = bindMessage.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);

    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNRequestProtocolOp modifyRequest =
        new ModifyDNRequestProtocolOp(
            ByteString.valueOf(entry.getDN().toString()),
            ByteString.valueOf("uid=user.test0"), false);
    LDAPMessage message = new LDAPMessage(2, modifyRequest,
                                          ShortCircuitPlugin.createShortCircuitControlList(80, "PreOperation"));
    w.writeMessage(message);

    message = r.readMessage();
    ModifyDNResponseProtocolOp modifyResponse =
        message.getModifyDNResponseProtocolOp();

    assertEquals(modifyResponse.getResultCode(), 80);
//    assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);

    StaticUtils.close(s);
  }

  @Test(groups = "slow")
  public void testWriteLockModify() throws Exception
  {
    // We need the operation to be run in a separate thread because we are going
    // to write lock the entry in the test case thread and check that the
    // modify DN operation does not proceed.

    // Establish a connection to the server.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    try
    {
      org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
      LDAPWriter w = new LDAPWriter(s);
      TestCaseUtils.configureSocket(s);

      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                ByteString.valueOf("cn=Directory Manager"),
                3, ByteString.valueOf("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));


      Lock writeLock = LockManager.lockWrite(entry.getDN());
      assertNotNull(writeLock);

      try
      {
        InvocationCounterPlugin.resetAllCounters();

        //long modifyDNRequests  = ldapStatistics.getModifyDNRequests();
        //long modifyDNResponses = ldapStatistics.getModifyDNResponses();

        ModifyDNRequestProtocolOp modifyRequest =
          new ModifyDNRequestProtocolOp(
               ByteString.valueOf(entry.getDN().toString()),
               ByteString.valueOf("uid=user.test0"), false);
        message = new LDAPMessage(2, modifyRequest);
        w.writeMessage(message);

        message = r.readMessage();
        ModifyDNResponseProtocolOp modifyResponse =
             message.getModifyDNResponseProtocolOp();

        assertEquals(modifyResponse.getResultCode(), LDAPResultCode.BUSY);

//        assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//        assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
//        assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
//        // The post response might not have been called yet.
//        assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);

//        assertEquals(ldapStatistics.getModifyDNRequests(), modifyDNRequests+1);
//        assertEquals(ldapStatistics.getModifyDNResponses(),
//                     modifyDNResponses+1);
      } finally
      {
        LockManager.unlock(entry.getDN(), writeLock);
      }
    } finally
    {
      StaticUtils.close(s);
    }
  }



  /**
   * Tests performing a modify DN operation in which the new RDN contains an
   * attribute type marked OBSOLETE in the server schema.
   */
  @Test()
  public void testModifyDNWithObsoleteAttribute()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
         "dn: cn=schema",
         "changetype: modify",
         "add: attributeTypes",
         "attributeTypes: ( testmodifydnwithobsoleteattribute-oid " +
              "NAME 'testModifyDNWithObsoleteAttribute' OBSOLETE " +
              "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
              "X-ORGIN 'SchemaBackendTestCase' )");

    String attrName = "testmodifydnwithobsoleteattribute";
    assertFalse(DirectoryServer.getSchema().hasAttributeType(attrName));

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    assertTrue(DirectoryServer.getSchema().hasAttributeType(attrName));

    path = TestCaseUtils.createTempFile(
         "dn: cn=oldrdn,o=test",
         "changetype: add",
         "objectClass: top",
         "objectClass: device",
         "objectClass: extensibleObject",
         "cn: oldrdn",
         "",
         "dn: cn=oldrdn,o=test",
         "changetype: moddn",
         "newRDN: testModifyDNWithObsoleteAttribute=foo",
         "deleteOldRDN: 0"
    );

    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, null, null) == 0);
  }



  /**
   * Tests a subtree rename operation to ensure that subordinate modify DN
   * plugins will be invoked as expected.
   */
  @Test()
  public void testSubordinateModifyDNPluginsForSubtreeRename()
         throws Exception
  {
    try
    {
      InvocationCounterPlugin.resetAllCounters();
      TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

      TestCaseUtils.addEntries(
        "dn: ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: People",
        "",
        "dn: uid=first.test,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: first.test",
        "givenName: First",
        "sn: Test",
        "cn: First Test",
        "userPassword: Password",
        "ou: People",
        "",
        "dn: uid=second.test,ou=People,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: second.test",
        "givenName: Second",
        "sn: Test",
        "cn: Second Test",
        "userPassword: Password");

      assertTrue(DirectoryServer.entryExists(
                      DN.decode("uid=first.test,ou=People,dc=example,dc=com")));
      assertFalse(DirectoryServer.entryExists(
                      DN.decode("uid=first.test,ou=Users,dc=example,dc=com")));


      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyDNOperation modifyDNOperation =
           conn.processModifyDN("ou=People,dc=example,dc=com", "ou=Users",
                                true);
      assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
//      assertEquals(InvocationCounterPlugin.getSubordinateModifyDNCount(), 2);


      assertFalse(DirectoryServer.entryExists(
                       DN.decode("uid=first.test,ou=People,dc=example,dc=com")));
      assertTrue(DirectoryServer.entryExists(
                      DN.decode("uid=first.test,ou=Users,dc=example,dc=com")));
    }
    finally
    {
      // Other tests in this class rely on a predefined structure, so we need to
      // make sure to put it back to the way it should be.
      setUp();
      InvocationCounterPlugin.resetAllCounters();
    }
  }



  /**
   * Tests a subtree move operation to ensure that subordinate modify DN
   * plugins will be invoked as expected.
   */
  @Test()
  public void testSubordinateModifyDNPluginsForSubtreeMove()
         throws Exception
  {
    try
    {
      InvocationCounterPlugin.resetAllCounters();
      TestCaseUtils.clearJEBackend(true, "userRoot", "dc=example,dc=com");

      TestCaseUtils.addEntries(
        "dn: ou=Org 1,dc=example,dc=com",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: Org 1",
        "",
        "dn: ou=Org 2,dc=example,dc=com",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: Org 2",
        "",
        "dn: ou=Org 1.1,ou=Org 1,dc=example,dc=com",
        "objectClass: top",
        "objectClass: organizationalUnit",
        "ou: Org 1.1",
        "",
        "dn: uid=first.test,ou=Org 1.1,ou=Org 1,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: first.test",
        "givenName: First",
        "sn: Test",
        "cn: First Test",
        "userPassword: Password",
        "ou: Org 1.1",
        "",
        "dn: uid=second.test,ou=Org 1.1,ou=Org 1,dc=example,dc=com",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: second.test",
        "givenName: Second",
        "sn: Test",
        "cn: Second Test",
        "userPassword: Password");

      assertTrue(DirectoryServer.entryExists(
           DN.decode("uid=first.test,ou=Org 1.1,ou=Org 1,dc=example,dc=com")));
      assertFalse(DirectoryServer.entryExists(
           DN.decode("uid=first.test,ou=Org 2.1,ou=Org 2,dc=example,dc=com")));


      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();
      ModifyDNOperation modifyDNOperation =
           conn.processModifyDN("ou=Org 1.1,ou=Org 1,dc=example,dc=com",
                                "ou=Org 2.1", true,
                                "ou=Org 2,dc=example,dc=com");
      assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
//      assertEquals(InvocationCounterPlugin.getSubordinateModifyDNCount(), 2);


      assertFalse(DirectoryServer.entryExists(
           DN.decode("uid=first.test,ou=Org 1.1,ou=Org 1,dc=example,dc=com")));
      assertTrue(DirectoryServer.entryExists(
           DN.decode("uid=first.test,ou=Org 2.1,ou=Org 2,dc=example,dc=com")));
    }
    finally
    {
      // Other tests in this class rely on a predefined structure, so we need to
      // make sure to put it back to the way it should be.
      setUp();
      InvocationCounterPlugin.resetAllCounters();
    }
  }

  @Test
  public void testCancelBeforeStartup() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = newModifyDNOperation(
                               DN.decode("uid=user.invalid,ou=People,dc=example,dc=com"),
                               RDN.decode("uid=user.test0"), true,
                               DN.decode("dc=example,dc=com"));

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    Message.raw("testCancelBeforeStartup"));
    modifyDNOperation.abort(cancelRequest);
    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.CANCELED);
  }


  /**
   * Tests whether an invalid rdn is allowed during an modrdn operation.
   * This test uses a valid attribute type with an empty value.
   *
   * @throws Exception
   */
  @Test(expectedExceptions=InvalidNameException.class)
  public void testInvalidModRDN() throws Exception
  {
    Hashtable<String,String> env = new Hashtable<String,String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
    String url = "ldap://localhost:" + TestCaseUtils.getServerLdapPort()
            +"/dc=example,dc=com";
    env.put(Context.PROVIDER_URL,url);
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=directory manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    env.put("java.naming.ldap.deleteRDN", "true");  // default is 'true'
    /* Create the initial context */
    DirContext ctx = new InitialDirContext(env);
    try
    {
      ctx.rename("uid=user.0,ou=People,dc=example,dc=com",
                   "uid=,ou=People,dc=example,dc=com");
    }
    finally
    {
      /* Close the context when it's done */
      ctx.close();
    }
  }
}
