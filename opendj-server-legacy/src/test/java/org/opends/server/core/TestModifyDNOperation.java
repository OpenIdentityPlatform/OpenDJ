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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions copyright 2013 Manuel Gaupp
 */
package org.opends.server.core;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Operation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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
    TestCaseUtils.clearBackend("userRoot");

    // Add the example.com entry
    TestCaseUtils.addEntry(
      "dn: dc=example,dc=com",
      "objectclass: top",
      "objectclass: domain",
      "dc: example",
      "aci: (targetattr=\"*\")(version 3.0; acl \"Proxy Rights\"; " +
           "allow(proxy) userdn=\"ldap:///uid=proxy.user,o=test\";)"
    );

    // Add the people entry
    TestCaseUtils.addEntry(
      "dn: ou=People,dc=example,dc=com",
      "objectclass: top",
      "objectclass: organizationalUnit",
      "ou: People"
    );

    // Add a test entry.
    entry = TestCaseUtils.addEntry(
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

    proxyUserConn = new InternalClientConnection(dn("uid=proxy.user,o=test"));
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
  private void examineUnparsedOperation(ModifyDNOperation modifyDNOperation, ResultCode resultCode)
  {
    assertEquals(modifyDNOperation.getResultCode(), resultCode);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

    assertTrue(modifyDNOperation.getProcessingStartTime() > 0);
    assertTrue(modifyDNOperation.getProcessingStopTime() > 0);
    assertTrue(modifyDNOperation.getProcessingTime() >= 0);
    assertTrue(modifyDNOperation.getErrorMessage().length() > 0);

//    assertEquals(InvocationCounterPlugin.getPreParseCount(), 1);
//    assertEquals(InvocationCounterPlugin.getPreOperationCount(), 0);
//    assertEquals(InvocationCounterPlugin.getPostOperationCount(), 0);
    ensurePostReponseHasRun();
//    assertEquals(InvocationCounterPlugin.getPostResponseCount(), 1);
  }

  @Override
  protected Operation[] createTestOperations() throws Exception
  {
    return new ModifyDNOperation[] {
      newModifyDNOpRaw("cn=test,ou=test", "cn=test2", true, "dc=example,dc=com"),
      newModifyDNOp("cn=test,ou=test", "cn=test2", true, "dc=example,dc=com")
    };
  }

  private ModifyDNOperation runModifyDNOp(
      String entryDN, String newRDN, boolean deleteOldRDN, String newSuperior) throws DirectoryException
  {
    ModifyDNOperation op = newModifyDNOp(entryDN, newRDN, deleteOldRDN, newSuperior);
    op.run();
    return op;
  }

  private ModifyDNOperation runModifyDNOpRaw(
      String entryDN, String newRDN, boolean deleteOldRDN, String newSuperior)
  {
    ModifyDNOperation op = newModifyDNOpRaw(entryDN, newRDN, deleteOldRDN, newSuperior);
    op.run();
    return op;
  }

  private ModifyDNOperationBasis newModifyDNOp(
      String entryDN, String newRDN, boolean deleteOldRDN, String newSuperior) throws DirectoryException
  {
    return new ModifyDNOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
        Collections.<Control> emptyList(), dn(entryDN), rdn(newRDN), deleteOldRDN, dn(newSuperior));
  }

  private ModifyDNOperationBasis newModifyDNOpRaw(
      String entryDN, String newRDN, boolean deleteOldRDN, String newSuperior)
  {
    return new ModifyDNOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
        Collections.<Control> emptyList(), b(entryDN), b(newRDN), deleteOldRDN, b(newSuperior));
  }

  private ModifyDNOperation runModifyDNOp(
      InternalClientConnection conn, String entryDN, String newRDN, boolean deleteOldRDN, Control control)
          throws DirectoryException
  {
    ModifyDNOperation op = new ModifyDNOperationBasis(conn, nextOperationID(), nextMessageID(),
        Collections.singletonList(control), dn(entryDN), rdn(newRDN), deleteOldRDN, null);
    op.run();
    return op;
  }

  private ModifyDNOperation runModifyDNOpRaw(
      String entryDN, String newRDN, boolean deleteOldRDN, String newSuperior, Control control)
  {
    ModifyDNOperation op = new ModifyDNOperationBasis(proxyUserConn, nextOperationID(), nextMessageID(),
        Collections.singletonList(control), b(entryDN), b(newRDN), deleteOldRDN, b(newSuperior));
    op.run();
    return op;
  }

  private ByteString b(String s)
  {
    return s != null ? ByteString.valueOfUtf8(s) : null;
  }

  private DN dn(String s) throws DirectoryException
  {
    return s != null ? DN.valueOf(s) : null;
  }

  private RDN rdn(String s) throws DirectoryException
  {
    return s != null ? RDN.valueOf(s) : null;
  }

  private void assertSuccessAndEntryExists(ModifyDNOperation modifyDNOperation,
      String entryDN, boolean user0Exists, boolean userTest0Exists)
      throws DirectoryException
  {
    assertSuccess(modifyDNOperation);
    final Entry newEntry = DirectoryServer.getEntry(dn(entryDN));
    assertNotNull(newEntry);

    for (AVA ava : newEntry.getName().rdn())
    {
      AttributeType attrType = ava.getAttributeType();
      assertEquals(newEntry.hasValue(attrType, b("user.0")), user0Exists);
      assertEquals(newEntry.hasValue(attrType, b("user.test0")), userTest0Exists);
    }
  }

  @Test
  public void testRawModify() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOpRaw(oldEntryDN, "uid=user.test0", false, null);
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, true, true);
    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOpRaw(newEntryDN, "uid=user.0", true, null);
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedModify() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(oldEntryDN, "uid=user.test0", false, null);
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, true, true);
    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOp(newEntryDN, "uid=user.0", true, null);
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  /**
   * Test if it's possible to modify an rdn to a value that matches the current value
   * by changing the case of some characters.
   */
  @Test
  public void testModifySameDN() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=USER.0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(oldEntryDN, "uid=USER.0", true, null);
    assertSuccess(modifyDNOperation);

    Entry newEntry = DirectoryServer.getEntry(dn(oldEntryDN));
    assertNotNull(newEntry);
    assertEquals(newEntry.getName().toString(), newEntryDN);
    assertAttrValue(newEntry, "uid", "USER.0");

    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOp(newEntryDN, "uid=user.0", true, null);
    assertSuccess(modifyDNOperation);
    assertNotNull(DirectoryServer.getEntry(dn(oldEntryDN)));
    examineCompletedOperation(modifyDNOperation);
  }

  /** Add another attribute to the RDN and change case of the existing value. */
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

    String oldEntryDN = "uid=userid.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=UserID.0+cn=Test,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(oldEntryDN, "uid=UserID.0+cn=Test", false, null);
    assertSuccess(modifyDNOperation);
    assertEntryAttrValue(newEntryDN, "uid", "UserID.0");
    examineCompletedOperation(modifyDNOperation);

    TestCaseUtils.deleteEntry(dn(newEntryDN));
  }

  private void assertEntryAttrValue(String entryDN, String attrName, String expectedAttrValue)
      throws DirectoryException
  {
    Entry newEntry = DirectoryServer.getEntry(dn(entryDN));
    assertNotNull(newEntry);
    assertEquals(newEntry.getName().toString(), entryDN);

    assertAttrValue(newEntry, attrName, expectedAttrValue);
  }

  private void assertAttrValue(Entry newEntry, String attrName, String expectedAttrValue)
  {
    AttributeType at = DirectoryServer.getSchema().getAttributeType(attrName);
    List<Attribute> attrList = newEntry.getAttribute(at);
    assertThat(attrList).hasSize(1);

    // Because deleteOldRDN is true, the values from RDN and the entry have to be identical
    ByteString valueFromEntry = attrList.get(0).iterator().next();
    ByteString valueFromRDN = newEntry.getName().rdn().getAttributeValue(at);
    assertEquals(valueFromEntry, valueFromRDN);
    assertEquals(valueFromEntry, b(expectedAttrValue));
  }

  /**
   * Add a value to the RDN which is already part of the entry, but with another string representation.
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

    String oldEntryDN = "uid=userid.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=userid.0+sn=JENSEN,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(oldEntryDN, "uid=userid.0+sn=JENSEN", false, null);
    assertSuccess(modifyDNOperation);
    assertEntryAttrValue(newEntryDN, "sn", "JENSEN");
    examineCompletedOperation(modifyDNOperation);

    TestCaseUtils.deleteEntry(dn(newEntryDN));
  }

  private void assertSuccess(ModifyDNOperation op)
  {
    assertEquals(op.getResultCode(), SUCCESS);
    assertEquals(op.getErrorMessage().length(), 0);
  }

  @Test
  public void testRawDeleteOldRDNModify() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOpRaw(oldEntryDN, "uid=user.test0", true, null);
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, false, true);
    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOpRaw(newEntryDN, "uid=user.0", true, null);
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedDeleteOldRDNModify() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(oldEntryDN, "uid=user.test0", true, null);

    CancelRequest cancelRequest = new CancelRequest(false, LocalizableMessage.raw("testCancelBeforeStartup"));
    CancelResult cancelResult = modifyDNOperation.cancel(cancelRequest);
    assertEquals(cancelResult.getResultCode(), TOO_LATE);

    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, false, true);
    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOp(newEntryDN, "uid=user.0", true, null);
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testRawNewSuperiorModify() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation =
        runModifyDNOpRaw(oldEntryDN, "uid=user.test0", true, "dc=example,dc=com");
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, false, true);
    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOpRaw(newEntryDN, "uid=user.0", true, "ou=People,dc=example,dc=com");
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
  }

  @Test
  public void testProcessedNewSuperiorModify() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(oldEntryDN, "uid=user.test0", true, "dc=example,dc=com");
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, false, true);
    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOp(newEntryDN, "uid=user.0", true, "ou=People,dc=example,dc=com");
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
  }

  @Test
  public void testRawRDNModify() throws Exception
  {
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "cn=Aaccf Amar Test,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation =
        runModifyDNOp(oldEntryDN, "cn=Aaccf Amar Test", true, "dc=example,dc=com");
    assertSuccess(modifyDNOperation);
    Entry entry = assertCnAttrValues(newEntryDN, oldEntryDN);
    assertThat(entry.getAttribute("uid")).isEmpty();
    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOp(newEntryDN, "uid=user.0", false, "ou=People,dc=example,dc=com");
    assertSuccess(modifyDNOperation);
    Entry newOldEntry = assertCnAttrValues(oldEntryDN, newEntryDN);
    for(Attribute attribute : newOldEntry.getAttribute("uid"))
    {
      assertTrue(attribute.contains(b("user.0")));
    }
    examineCompletedOPNoExtraPluginCounts(modifyDNOperation);
  }

  private Entry assertCnAttrValues(String entryDN1, String entryDN2) throws DirectoryException
  {
    Entry entry = DirectoryServer.getEntry(dn(entryDN1));
    assertNotNull(entry);
    assertNull(DirectoryServer.getEntry(dn(entryDN2)));

    for (Attribute attribute : entry.getAttribute("cn"))
    {
      assertTrue(attribute.contains(b("Aaccf Amar Test")));
      assertTrue(attribute.contains(b("Aaccf Amar")));
    }
    return entry;
  }

  @Test
  public void testInvalidEntryModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(
        "uid=user.invalid,ou=People,dc=example,dc=com", "uid=user.test0", true, "dc=example,dc=com");
    examineIncompleteOperation(modifyDNOperation, NO_SUCH_OBJECT);
  }

  @Test
  public void testInvalidRDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(
        "uid=user.0,ou=People,dc=example,dc=com", "invalid=invalid", true, "dc=example,dc=com");
    examineIncompleteOperation(modifyDNOperation, OBJECTCLASS_VIOLATION);
  }

  @Test
  public void testInvalidSuperiorModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp(
        "uid=user.0,ou=People,dc=example,dc=com", "uid=user.test0", true, "dc=invalid,dc=com");
    examineIncompleteOperation(modifyDNOperation, NO_SUCH_OBJECT);
  }

  @Test
  public void testRawNoSuchDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation =
        runModifyDNOpRaw("invalid DN", "uid=user.test0", true, "dc=example,dc=com");
    examineUnparsedOperation(modifyDNOperation, INVALID_DN_SYNTAX);
  }

  @Test
  public void testRawNoSuchRDNModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation =
        runModifyDNOpRaw("uid=user.0,ou=People,dc=example,dc=com", "invalid RDN", true, "dc=example,dc=com");
    examineUnparsedOperation(modifyDNOperation, INVALID_DN_SYNTAX);
  }

  @Test
  public void testRawInvalidSuperiorModify() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation =
        runModifyDNOpRaw("uid=user.0,ou=People,dc=example,dc=com", "uid=user.test0", true, "invalid superior");
    examineUnparsedOperation(modifyDNOperation, INVALID_DN_SYNTAX);
  }

  @Test
  public void testModifySuffix() throws Exception
  {
    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOp("dc=example,dc=com", "dc=exampletest", true, null);
    examineIncompleteOperation(modifyDNOperation, UNWILLING_TO_PERFORM);
  }

  @Test
  public void testRawProxyAuthV1Modify() throws Exception
  {
    ProxiedAuthV1Control authV1Control = new ProxiedAuthV1Control(b("cn=Directory Manager,cn=Root DNs,cn=config"));
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation = runModifyDNOpRaw(oldEntryDN, "uid=user.test0", false, null, authV1Control);
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, true, true);
    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOpRaw(newEntryDN, "uid=user.0", true, null, authV1Control);
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV1Modify() throws Exception
  {
    ProxiedAuthV1Control authV1Control = new ProxiedAuthV1Control(b("cn=Directory Manager,cn=Root DNs,cn=config"));
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation =
        runModifyDNOp(proxyUserConn, oldEntryDN, "uid=user.test0", false, authV1Control);
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, true, true);
    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOp(proxyUserConn, newEntryDN, "uid=user.0", true, authV1Control);
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV1DeniedModify() throws Exception
  {
    ProxiedAuthV1Control authV1Control = new ProxiedAuthV1Control(b("cn=nonexistent,o=test"));
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
        runModifyDNOp(proxyUserConn, "uid=user.0,ou=People,dc=example,dc=com", "uid=user.test0", false, authV1Control);
    examineIncompleteOperation(modifyDNOperation, AUTHORIZATION_DENIED);
  }

  @Test
  public void testProcessedProxyAuthV2Modify() throws Exception
  {
    ProxiedAuthV2Control authV2Control = new ProxiedAuthV2Control(b("dn:cn=Directory Manager,cn=Root DNs,cn=config"));
    String oldEntryDN = "uid=user.0,ou=People,dc=example,dc=com";
    String newEntryDN = "uid=user.test0,ou=People,dc=example,dc=com";

    InvocationCounterPlugin.resetAllCounters();
    ModifyDNOperation modifyDNOperation =
        runModifyDNOp(proxyUserConn, oldEntryDN, "uid=user.test0", false, authV2Control);
    assertSuccessAndEntryExists(modifyDNOperation, newEntryDN, true, true);
    examineCompletedOperation(modifyDNOperation);

    InvocationCounterPlugin.resetAllCounters();
    modifyDNOperation = runModifyDNOp(proxyUserConn, newEntryDN, "uid=user.0", true, authV2Control);
    assertSuccessAndEntryExists(modifyDNOperation, oldEntryDN, true, false);
    examineCompletedOperation(modifyDNOperation);
  }

  @Test
  public void testProcessedProxyAuthV2DeniedModify() throws Exception
  {
    ProxiedAuthV2Control authV2Control = new ProxiedAuthV2Control(b("dn:cn=nonexistent,o=test"));
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
        runModifyDNOp(proxyUserConn, "uid=user.0,ou=People,dc=example,dc=com", "uid=user.test0", false, authV2Control);
    examineIncompleteOperation(modifyDNOperation, AUTHORIZATION_DENIED);
  }

  @Test
  public void testProcessedProxyAuthV2CriticalityModify() throws Exception
  {
    Control authV2Control = new LDAPControl(OID_PROXIED_AUTH_V2, false, b("dn:cn=nonexistent,o=test"));
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation =
        runModifyDNOp(proxyUserConn, "uid=user.0,ou=People,dc=example,dc=com", "uid=user.test0", false, authV2Control);
    examineIncompleteOperation(modifyDNOperation, PROTOCOL_ERROR);
  }

  @Test
  public void testProcessedUnsupportedControlModify() throws Exception
  {
    LDAPControl assertControl = new LDAPControl("1.1.1.1.1.1", true);
    InvocationCounterPlugin.resetAllCounters();

    ModifyDNOperation modifyDNOperation = runModifyDNOp(
        getRootConnection(), "uid=user.0,ou=People,dc=example,dc=com", "uid=user.test0", false, assertControl);
    examineIncompleteOperation(modifyDNOperation, UNAVAILABLE_CRITICAL_EXTENSION);
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      InvocationCounterPlugin.resetAllCounters();

      conn.bind("cn=Directory Manager", "password");

      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));
      InvocationCounterPlugin.resetAllCounters();
      ModifyDNRequestProtocolOp modifyRequest =
          new ModifyDNRequestProtocolOp(b(entry.getName().toString()), b("uid=user.test0"), false);
      conn.writeMessage(modifyRequest, ShortCircuitPlugin.createShortCircuitControlList(80, "PreOperation"));

      LDAPMessage message = conn.readMessage();
      ModifyDNResponseProtocolOp modifyResponse = message.getModifyDNResponseProtocolOp();
      assertEquals(modifyResponse.getResultCode(), 80);
      // assertEquals(InvocationCounterPlugin.waitForPostResponse(), 1);
    }
  }

  @Test(groups = "slow")
  public void testWriteLockModify() throws Exception
  {
    // We need the operation to be run in a separate thread because we are going
    // to write lock the entry in the test case thread and check that the
    // modify DN operation does not proceed.

    // Establish a connection to the server.
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      // Since we are going to be watching the post-response count, we need to
      // wait for the server to become idle before kicking off the next request
      // to ensure that any remaining post-response processing from the previous
      // operation has completed.
      assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));

      final DNLock writeLock = DirectoryServer.getLockManager().tryWriteLockEntry(entry.getName());
      assertNotNull(writeLock);

      try
      {
        InvocationCounterPlugin.resetAllCounters();

        //long modifyDNRequests  = ldapStatistics.getModifyDNRequests();
        //long modifyDNResponses = ldapStatistics.getModifyDNResponses();

        ModifyDNResponseProtocolOp modifyResponse = conn.modifyDN(entry.getName().toString(), "uid=user.test0", false);
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
        writeLock.unlock();
      }
    }
  }

  /**
   * Tests performing a modify DN operation in which the new RDN contains an
   * attribute type marked OBSOLETE in the server schema.
   */
  @Test
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
  @Test
  public void testSubordinateModifyDNPluginsForSubtreeRename()
         throws Exception
  {
    try
    {
      InvocationCounterPlugin.resetAllCounters();
      TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");

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
                      dn("uid=first.test,ou=People,dc=example,dc=com")));
      assertFalse(DirectoryServer.entryExists(
                      dn("uid=first.test,ou=Users,dc=example,dc=com")));

      ModifyDNRequest modifyDNRequest =
          newModifyDNRequest("ou=People,dc=example,dc=com", "ou=Users").setDeleteOldRDN(true);
      ModifyDNOperation modifyDNOperation = getRootConnection().processModifyDN(modifyDNRequest);
      assertSuccess(modifyDNOperation);
//      assertEquals(InvocationCounterPlugin.getSubordinateModifyDNCount(), 2);

      assertFalse(DirectoryServer.entryExists(
                       dn("uid=first.test,ou=People,dc=example,dc=com")));
      assertTrue(DirectoryServer.entryExists(
                      dn("uid=first.test,ou=Users,dc=example,dc=com")));
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
  @Test
  public void testSubordinateModifyDNPluginsForSubtreeMove()
         throws Exception
  {
    try
    {
      InvocationCounterPlugin.resetAllCounters();
      TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");

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
           dn("uid=first.test,ou=Org 1.1,ou=Org 1,dc=example,dc=com")));
      assertFalse(DirectoryServer.entryExists(
           dn("uid=first.test,ou=Org 2.1,ou=Org 2,dc=example,dc=com")));

      ModifyDNOperation modifyDNOperation =
          getRootConnection().processModifyDN("ou=Org 1.1,ou=Org 1,dc=example,dc=com",
                                "ou=Org 2.1", true,
                                "ou=Org 2,dc=example,dc=com");
      assertSuccess(modifyDNOperation);
//      assertEquals(InvocationCounterPlugin.getSubordinateModifyDNCount(), 2);

      assertFalse(DirectoryServer.entryExists(
           dn("uid=first.test,ou=Org 1.1,ou=Org 1,dc=example,dc=com")));
      assertTrue(DirectoryServer.entryExists(
           dn("uid=first.test,ou=Org 2.1,ou=Org 2,dc=example,dc=com")));
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

    ModifyDNOperation modifyDNOperation = newModifyDNOp(
        "uid=user.invalid,ou=People,dc=example,dc=com", "uid=user.test0", true, "dc=example,dc=com");

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    LocalizableMessage.raw("testCancelBeforeStartup"));
    modifyDNOperation.abort(cancelRequest);
    modifyDNOperation.run();
    assertEquals(modifyDNOperation.getResultCode(), CANCELLED);
  }

  /**
   * Tests whether an invalid rdn is allowed during an modrdn operation.
   * This test uses a valid attribute type with an empty value.
   *
   * @throws Exception
   */
  @Test
  public void testInvalidModRDN() throws Exception
  {
    try (RemoteConnection c = new RemoteConnection("localhost", getServerLdapPort()))
    {
      c.bind("cn=Directory Manager", "password");

      ModifyDNResponseProtocolOp modifyDNResponse =
          c.modifyDN("uid=user.0,ou=People,dc=example,dc=com", "uid=,ou=People,dc=example,dc=com", true);
      assertEquals(modifyDNResponse.getResultCode(), ResultCode.INVALID_DN_SYNTAX.intValue());
    }
  }
}
