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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.annotations.AfterMethod;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.plugins.DisconnectClientPlugin;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.tools.LDAPDelete;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager;
import org.opends.server.types.ResultCode;
import org.opends.server.types.WritabilityMode;
import org.opends.server.types.DirectoryException;

import static org.testng.Assert.*;

import static org.opends.server.protocols.ldap.LDAPConstants.*;



/**
 * A set of test cases for delete operations
 */
public class DeleteOperationTestCase
       extends OperationTestCase
{
  // Some of the tests disable the backends, so we reenable them here.
  @AfterMethod(alwaysRun=true)
  public void reenableBackend() throws DirectoryException {
    Backend b = DirectoryServer.getBackend(DN.decode("o=test"));
    b.setWritabilityMode(WritabilityMode.ENABLED);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public Operation[] createTestOperations()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          new ArrayList<Control>(), new ASN1OctetString()),
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, new ASN1OctetString()),
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          new ArrayList<Control>(),
                          new ASN1OctetString("o=test")),
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, new ASN1OctetString("o=test")),
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          new ArrayList<Control>(), DN.nullDN()),
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, DN.nullDN()),
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          new ArrayList<Control>(), DN.decode("o=test")),
      new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                          null, DN.decode("o=test"))
    };
  }



  /**
   * Tests the <CODE>getRawEntryDN</CODE> and <CODE>setRawEntryDN</CODE>
   * methods.
   *
   * @param  deleteOperation  The delete operation to use in the test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetAndSetRawEntryDN(DeleteOperation deleteOperation)
  {
    ByteString originalRawDN = deleteOperation.getRawEntryDN();
    assertNotNull(originalRawDN);

    deleteOperation.setRawEntryDN(new ASN1OctetString("dc=example,dc=com"));
    assertEquals(deleteOperation.getRawEntryDN(),
                 new ASN1OctetString("dc=example,dc=com"));

    deleteOperation.setRawEntryDN(originalRawDN);
    assertEquals(deleteOperation.getRawEntryDN(), originalRawDN);
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method when it should be null.
   */
  @Test()
  public void testGetEntryDNNull()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, new ASN1OctetString("o=test"));
    assertNull(deleteOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method when it should not be null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntryDNNotNull()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, DN.decode("o=test"));
    assertNotNull(deleteOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method when it originally started as
   * non-null but then was changed to null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntryDNChangedToNull()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, DN.decode("o=test"));
    assertNotNull(deleteOperation.getEntryDN());

    deleteOperation.setRawEntryDN(new ASN1OctetString("dc=example,dc=com"));
    assertNull(deleteOperation.getEntryDN());
  }



  /**
   * Retrieves a number of generic elements from a completed delete operation.
   * It should have completed successfully.
   */
  private void retrieveCompletedOperationElements(
                    DeleteOperation deleteOperation)
         throws Exception
  {
    assertNotNull(deleteOperation.getEntryToDelete());
    assertTrue(deleteOperation.getProcessingStartTime() > 0);
    assertTrue(deleteOperation.getProcessingStopTime() >=
               deleteOperation.getProcessingStartTime());
    assertTrue(deleteOperation.getProcessingTime() >= 0);
    assertNotNull(deleteOperation.getResponseLogElements());


    long changeNumber = deleteOperation.getChangeNumber();
    deleteOperation.setChangeNumber(changeNumber);
  }



  /**
   * Tests the <CODE>getEntryToDelete</CODE> method for a successful delete
   * operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntryToDeleteExists()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);

    assertNotNull(deleteOperation.getEntryToDelete());
  }



  /**
   * Tests the <CODE>getEntryToDelete</CODE> method for a delete operation that
   * fails because the target entry doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetEntryToDeleteNonExistent()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("ou=People,o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
    assertNull(deleteOperation.getEntryToDelete());
  }



  /**
   * Tests an external delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExternalDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };
    assertEquals(LDAPDelete.mainDelete(args, false, null, null), 0);
  }



  /**
   * Tests the delete operation with a valid raw DN that is a suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithValidRawDNSuffix()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a valid processed DN that is a suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithValidProcessedDNSuffix()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a valid raw DN that is a leaf.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithValidRawDNLeaf()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("cn=test,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a valid processed DN that is a leaf.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithValidProcessedDNLeaf()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=test,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a malformed raw DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithMalformedRawDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("malformed"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent raw DN that should be a
   * suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithNonExistentSuffixRawDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=does not exist"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent processed DN that should be a
   * suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithNonExistentSuffixProcessedDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("o=does not exist"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a raw DN below a suffix that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithRawDNBelowNonExistentSuffix()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("cn=entry,o=does not exist"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a processed DN below a suffix that doesn't
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithProcessedDNBelowNonExistentSuffix()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=entry,o=does not exist"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent raw DN below a suffix that
   * does exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithNonExistentRawDNBelowExistingSuffix()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("cn=entry,o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent processed DN below a suffix
   * that does exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithNonExistentProcessedDNBelowExistingSuffix()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(DN.decode("cn=entry,o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation for a nonleaf raw DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithNonLeafRawDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation for a nonleaf processed DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithNonLeafProcessedDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry("dn: cn=test,o=test",
                                      "objectClass: top",
                                      "objectClass: device",
                                      "cn: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(),
                         e.getUserAttributes(), e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation when the server has a writability mode of
   * disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithServerWritabilityDisabled()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DirectoryServer.setWritabilityMode(WritabilityMode.DISABLED);

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests an internal delete operation when the server has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInternalDeleteWithServerWritabilityInternalOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests an external delete operation when the server has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExternalDeleteWithServerWritabilityInternalOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DirectoryServer.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };
    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);

    DirectoryServer.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests the delete operation when the backend has a writability mode of
   * disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteWithBackendWritabilityDisabled()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    Backend backend = DirectoryServer.getBackend(DN.decode("o=test"));
    backend.setWritabilityMode(WritabilityMode.DISABLED);

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);

    backend.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests an internal delete operation when the backend has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInternalDeleteWithBackendWritabilityInternalOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    Backend backend = DirectoryServer.getBackend(DN.decode("o=test"));
    backend.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    backend.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests an external delete operation when the backend has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExternalDeleteWithBackendWritabilityInternalOnly()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Backend backend = DirectoryServer.getBackend(DN.decode("o=test"));
    backend.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "o=test"
    };
    assertFalse(LDAPDelete.mainDelete(args, false, null, null) == 0);

    backend.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests a delete operation that gets canceled before startup.
   *
   * @throws  Exception  If an unexpected probem occurs.
   */
  @Test()
  public void testCancelBeforeStartup()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         new DeleteOperation(conn, conn.nextOperationID(), conn.nextMessageID(),
                             null, new ASN1OctetString("o=test"));

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    "testCancelBeforeStartup");
    deleteOperation.setCancelRequest(cancelRequest);
    deleteOperation.run();
    assertEquals(deleteOperation.getResultCode(), ResultCode.CANCELED);
  }



  /**
   * Tests a delete operation in which the server cannot obtain a lock on the
   * target entry because there is already a read lock held on it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" })
  public void testCannotLockEntry()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Lock entryLock = LockManager.lockRead(DN.decode("o=test"));

    try
    {
      InternalClientConnection conn =
           InternalClientConnection.getRootConnection();

      DeleteOperation deleteOperation =
           conn.processDelete(new ASN1OctetString("o=test"));
      assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);
    }
    finally
    {
      LockManager.unlock(DN.decode("o=test"), entryLock);
    }
  }



  /**
   * Tests a delete operation that should be disconnected in a pre-parse plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDisconnectInPreParseDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(new ASN1OctetString("o=test"));
    message = new LDAPMessage(2, deleteRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList("PreParse"));
    w.writeElement(message.encode());

    ASN1Element element = r.readElement();
    if (element != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      message = LDAPMessage.decode(element.decodeAsSequence());
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a delete operation that should be disconnected in a pre-operation
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDisconnectInPreOperationDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(new ASN1OctetString("o=test"));
    message = new LDAPMessage(2, deleteRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList(
              "PreOperation"));
    w.writeElement(message.encode());

    ASN1Element element = r.readElement();
    if (element != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      message = LDAPMessage.decode(element.decodeAsSequence());
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a delete operation that should be disconnected in a post-operation
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDisconnectInPostOperationDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(new ASN1OctetString("o=test"));
    message = new LDAPMessage(2, deleteRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList(
              "PostOperation"));
    w.writeElement(message.encode());

    ASN1Element element = r.readElement();
    if (element != null)
    {
      // If we got an element back, then it must be a notice of disconnect
      // unsolicited notification.
      message = LDAPMessage.decode(element.decodeAsSequence());
      assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests a delete operation that should be disconnected in a post-response
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDisconnectInPostResponseDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(5000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(new ASN1OctetString("cn=Directory Manager"),
                                   3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse =
         message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);


    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(new ASN1OctetString("o=test"));
    message = new LDAPMessage(2, deleteRequest,
         DisconnectClientPlugin.createDisconnectLDAPControlList(
              "PostResponse"));
    w.writeElement(message.encode());

responseLoop:
    while (true)
    {
      ASN1Element element = r.readElement();
      if (element == null)
      {
        // The connection has been closed.
        break responseLoop;
      }

      message = LDAPMessage.decode(element.decodeAsSequence());
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_DELETE_RESPONSE:
          // This was expected.  The disconnect didn't happen until after the
          // response was sent.
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          // The server is notifying us that it will be closing the connection.
          break responseLoop;
        default:
          // This is a problem.  It's an unexpected response.
          try
          {
            s.close();
          } catch (Exception e) {}

          throw new Exception("Unexpected response message " + message +
                              " encountered in " +
                              "testDisconnectInPostResponseDelete");
      }
    }

    try
    {
      s.close();
    } catch (Exception e) {}
  }



  /**
   * Tests to ensure that any registered notification listeners are invoked for
   * a successful delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessWithNotificationListener()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerChangeNotificationListener(changeListener);
    assertEquals(changeListener.getAddCount(), 0);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);

    assertEquals(changeListener.getDeleteCount(), 1);
    DirectoryServer.deregisterChangeNotificationListener(changeListener);
  }



  /**
   * Tests to ensure that any registered notification listeners are not invoked
   * for a failed delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailureWithNotificationListener()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerChangeNotificationListener(changeListener);
    assertEquals(changeListener.getAddCount(), 0);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(new ASN1OctetString("cn=nonexistent,o=test"));
    assertFalse(deleteOperation.getResultCode() == ResultCode.SUCCESS);

    assertEquals(changeListener.getDeleteCount(), 0);
    DirectoryServer.deregisterChangeNotificationListener(changeListener);
  }
}

