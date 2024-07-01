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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.List;

import com.forgerock.opendj.ldap.tools.LDAPDelete;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.LocalBackend;
import org.opends.server.plugins.DisconnectClientPlugin;
import org.opends.server.plugins.ShortCircuitPlugin;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Operation;
import org.opends.server.types.WritabilityMode;
import org.opends.server.workflowelement.localbackend.LocalBackendDeleteOperation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.core.AddOperationTestCase.setWritabilityMode;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.testng.Assert.*;

/**
 * A set of test cases for delete operations.
 */
@SuppressWarnings("javadoc")
public class DeleteOperationTestCase extends OperationTestCase
{

  /** Some of the tests disable the backends, so we reenable them here. */
  @AfterMethod(alwaysRun=true)
  public void reenableBackend() throws DirectoryException {
    LocalBackend<?> b =
        TestCaseUtils.getServerContext().getBackendConfigManager().findLocalBackendForEntry(DN.valueOf("o=test"));
    b.setWritabilityMode(WritabilityMode.ENABLED);
  }

  /** {@inheritDoc} */
  @Override
  protected Operation[] createTestOperations() throws Exception
  {
    List<Control> noControls = new ArrayList<>();
    return new Operation[]
    {
      newDeleteOperation(noControls, ByteString.empty()),
      newDeleteOperation(null, ByteString.empty()),
      newDeleteOperation(noControls, ByteString.valueOfUtf8("o=test")),
      newDeleteOperation(null, ByteString.valueOfUtf8("o=test")),
      newDeleteOperation(noControls, DN.rootDN()),
      newDeleteOperation(null, DN.rootDN()),
      newDeleteOperation(noControls, DN.valueOf("o=test")),
      newDeleteOperation(null, DN.valueOf("o=test"))
    };
  }

  private DeleteOperation newDeleteOperation(
      List<Control> requestControls, ByteString rawEntryDn)
  {
    return new DeleteOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        requestControls, rawEntryDn);
  }

  private DeleteOperation newDeleteOperation(
      List<Control> requestControls, DN entryDn)
  {
    return new DeleteOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(),
        requestControls, entryDn);
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

    deleteOperation.setRawEntryDN(ByteString.valueOfUtf8("dc=example,dc=com"));
    assertEquals(deleteOperation.getRawEntryDN(),
                 ByteString.valueOfUtf8("dc=example,dc=com"));

    deleteOperation.setRawEntryDN(originalRawDN);
    assertEquals(deleteOperation.getRawEntryDN(), originalRawDN);
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method that should decode the rawEntryDN
   * to compute the entryDN.
   */
  @Test
  public void testGetEntryDNNull()
  {
    DeleteOperation deleteOperation =
        newDeleteOperation(null, ByteString.valueOfUtf8("o=test"));
    assertNotNull(deleteOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method when it should not be null.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetEntryDNNotNull() throws Exception
  {
    DeleteOperation deleteOperation =
        newDeleteOperation(null, DN.valueOf("o=test"));
    assertNotNull(deleteOperation.getEntryDN());
  }



  /**
   * Tests the <CODE>getEntryDN</CODE> method when it originally started as
   * non-null, then was changed to null; because of the call to the
   * <CODE>setRawEntry<CODE> method, and becomes non-null again because
   * of the call to the <CODE>getEntryDN</CODE> again.
   *
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetEntryDNChangedToNull() throws Exception
  {
    DeleteOperation deleteOperation =
        newDeleteOperation(null, DN.valueOf("o=test"));
    assertNotNull(deleteOperation.getEntryDN());

    deleteOperation.setRawEntryDN(ByteString.valueOfUtf8("dc=example,dc=com"));
    assertNotNull(deleteOperation.getEntryDN());
  }



  /**
   * Retrieves a number of generic elements from a completed delete operation.
   * It should have completed successfully.
   */
  private void retrieveCompletedOperationElements(
                    DeleteOperation deleteOperation)
         throws Exception
  {
    assertTrue(deleteOperation.getProcessingStartTime() > 0);
    assertTrue(deleteOperation.getProcessingStopTime() >=
               deleteOperation.getProcessingStartTime());
    assertTrue(deleteOperation.getProcessingTime() >= 0);
  }



  /**
   * Tests the <CODE>getEntryToDelete</CODE> method for a successful delete
   * operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetEntryToDeleteExists() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
    @SuppressWarnings("unchecked")
    List<LocalBackendDeleteOperation> localOps =
        (List<LocalBackendDeleteOperation>) deleteOperation.getAttachment(Operation.LOCALBACKENDOPERATIONS);
    assertNotNull(localOps);
    for (LocalBackendDeleteOperation curOp : localOps)
    {
      assertNotNull(curOp.getEntryToDelete());
    }
  }

  private DeleteOperation processDeleteRaw(String entryDN)
  {
    return getRootConnection().processDelete(ByteString.valueOfUtf8(entryDN));
  }

  private DeleteOperation processDelete(String entryDN) throws DirectoryException
  {
    return getRootConnection().processDelete(DN.valueOf(entryDN));
  }

  /**
   * Tests the <CODE>getEntryToDelete</CODE> method for a delete operation that
   * fails because the target entry doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetEntryToDeleteNonExistent() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDeleteRaw("ou=People,o=test");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    @SuppressWarnings("unchecked")
    List<LocalBackendDeleteOperation> localOps =
        (List<LocalBackendDeleteOperation>) deleteOperation.getAttachment(Operation.LOCALBACKENDOPERATIONS);
    assertNotNull(localOps);
    for (LocalBackendDeleteOperation curOp : localOps)
    {
      assertNull(curOp.getEntryToDelete());
    }
  }



  /**
   * Tests an external delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testExternalDelete() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args = getArgs("o=test");
    assertEquals(LDAPDelete.run(nullPrintStream(), nullPrintStream(), args), 0);
  }



  /**
   * Tests the delete operation with a valid raw DN that is a suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithValidRawDNSuffix() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a valid processed DN that is a suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithValidProcessedDNSuffix() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDelete("o=test");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a valid raw DN that is a leaf.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithValidRawDNLeaf() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
               "objectClass: top",
               "objectClass: device",
               "cn: test");

    DeleteOperation deleteOperation = processDeleteRaw("cn=test,o=test");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a valid processed DN that is a leaf.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithValidProcessedDNLeaf() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
               "objectClass: top",
               "objectClass: device",
               "cn: test");

    DeleteOperation deleteOperation = processDelete("cn=test,o=test");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    retrieveCompletedOperationElements(deleteOperation);
  }



  /**
   * Tests the delete operation with a malformed raw DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithMalformedRawDN() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDeleteRaw("malformed");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent raw DN that should be a
   * suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithNonExistentSuffixRawDN() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDeleteRaw("o=does not exist");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent processed DN that should be a
   * suffix.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithNonExistentSuffixProcessedDN() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDelete("o=does not exist");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a raw DN below a suffix that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithRawDNBelowNonExistentSuffix() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDeleteRaw("cn=entry,o=does not exist");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a processed DN below a suffix that doesn't
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithProcessedDNBelowNonExistentSuffix() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDelete("cn=entry,o=does not exist");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent raw DN below a suffix that
   * does exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithNonExistentRawDNBelowExistingSuffix() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDeleteRaw("cn=entry,o=test");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation with a nonexistent processed DN below a suffix
   * that does exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithNonExistentProcessedDNBelowExistingSuffix() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation = processDelete("cn=entry,o=test");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation for a nonleaf raw DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithNonLeafRawDN() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
               "objectClass: top",
               "objectClass: device",
               "cn: test");

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation for a nonleaf processed DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithNonLeafProcessedDN() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry("dn: cn=test,o=test",
               "objectClass: top",
               "objectClass: device",
               "cn: test");

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the delete operation when the server has a writability mode of
   * disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithServerWritabilityDisabled() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setWritabilityMode(GlobalCfgDefn.WritabilityMode.DISABLED);

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    setWritabilityMode(GlobalCfgDefn.WritabilityMode.ENABLED);
  }



  /**
   * Tests an internal delete operation when the server has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testInternalDeleteWithServerWritabilityInternalOnly() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setWritabilityMode(GlobalCfgDefn.WritabilityMode.INTERNAL_ONLY);

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    setWritabilityMode(GlobalCfgDefn.WritabilityMode.ENABLED);
  }



  /**
   * Tests an external delete operation when the server has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testExternalDeleteWithServerWritabilityInternalOnly() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    setWritabilityMode(GlobalCfgDefn.WritabilityMode.INTERNAL_ONLY);

    String[] args = getArgs("o=test");
    assertFalse(LDAPDelete.run(nullPrintStream(), nullPrintStream(), args) == 0);

    setWritabilityMode(GlobalCfgDefn.WritabilityMode.ENABLED);
  }



  /**
   * Tests the delete operation when the backend has a writability mode of
   * disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteWithBackendWritabilityDisabled() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    LocalBackend<?> backend =
        TestCaseUtils.getServerContext().getBackendConfigManager().findLocalBackendForEntry(DN.valueOf("o=test"));
    backend.setWritabilityMode(WritabilityMode.DISABLED);

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    backend.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests an internal delete operation when the backend has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testInternalDeleteWithBackendWritabilityInternalOnly() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    LocalBackend<?> backend =
        TestCaseUtils.getServerContext().getBackendConfigManager().findLocalBackendForEntry(DN.valueOf("o=test"));
    backend.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    DeleteOperation deleteOperation = processDeleteRaw("o=test");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    backend.setWritabilityMode(WritabilityMode.ENABLED);
  }



  /**
   * Tests an external delete operation when the backend has a writability mode
   * of internal-only.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testExternalDeleteWithBackendWritabilityInternalOnly() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    LocalBackend<?> backend =
        TestCaseUtils.getServerContext().getBackendConfigManager().findLocalBackendForEntry(DN.valueOf("o=test"));
    backend.setWritabilityMode(WritabilityMode.INTERNAL_ONLY);

    String[] args = getArgs("o=test");
    assertFalse(LDAPDelete.run(nullPrintStream(), nullPrintStream(), args) == 0);

    backend.setWritabilityMode(WritabilityMode.ENABLED);
  }

  private String[] getArgs(String entryDn)
  {
    return new String[] {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      entryDn
    };
  }



  /**
   * Tests a delete operation that gets canceled before startup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelBeforeStartup() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation =
        newDeleteOperation(null, ByteString.valueOfUtf8("o=test"));

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    LocalizableMessage.raw("testCancelBeforeStartup"));
    deleteOperation.abort(cancelRequest);
    deleteOperation.run();
    assertEquals(deleteOperation.getResultCode(), ResultCode.CANCELLED);
  }

  /**
   * Tests a delete operation that gets canceled before startup.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelAfterOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    DeleteOperation deleteOperation =
        newDeleteOperation(null, ByteString.valueOfUtf8("o=test"));
    deleteOperation.run();

    CancelRequest cancelRequest = new CancelRequest(false,
                                                    LocalizableMessage.raw("testCancelAfterOperation"));
    CancelResult cancelResult = deleteOperation.cancel(cancelRequest);

    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(cancelResult.getResultCode(), ResultCode.TOO_LATE);
  }



  /**
   * Tests a delete operation in which the server cannot obtain a lock on the
   * target entry because there is already a read lock held on it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" })
  public void testCannotLockEntry() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    final DNLock entryLock = DirectoryServer.getLockManager().tryReadLockEntry(DN.valueOf("o=test"));
    try
    {
      DeleteOperation deleteOperation = processDeleteRaw("o=test");
      assertEquals(deleteOperation.getResultCode(), ResultCode.BUSY);
    }
    finally
    {
      entryLock.unlock();
    }
  }



  /**
   * Tests a delete operation that should be disconnected in a pre-parse plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDisconnectInPreParseDelete() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(ByteString.valueOfUtf8("o=test"));
      conn.writeMessage(deleteRequest, DisconnectClientPlugin.createDisconnectControlList("PreParse"));

      LDAPMessage message = conn.readMessage();
      if (message != null)
      {
        // If we got an element back, then it must be a notice of disconnect
        // unsolicited notification.
        assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
      }
    }
  }



  /**
   * Tests a delete operation that should be disconnected in a pre-operation
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDisconnectInPreOperationDelete() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(ByteString.valueOfUtf8("o=test"));
      conn.writeMessage(deleteRequest, DisconnectClientPlugin.createDisconnectControlList("PreOperation"));

      LDAPMessage message = conn.readMessage();
      if (message != null)
      {
        // If we got an element back, then it must be a notice of disconnect
        // unsolicited notification.
        assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
      }
    }
  }



  /**
   * Tests a delete operation that should be disconnected in a post-operation
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDisconnectInPostOperationDelete() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(ByteString.valueOfUtf8("o=test"));
      conn.writeMessage(deleteRequest,
          DisconnectClientPlugin.createDisconnectControlList("PostOperation"));

      LDAPMessage message = conn.readMessage();
      if (message != null)
      {
        // If we got an element back, then it must be a notice of disconnect
        // unsolicited notification.
        assertEquals(message.getProtocolOpType(), OP_TYPE_EXTENDED_RESPONSE);
      }
    }
  }



  /**
   * Tests a delete operation that should be disconnected in a post-response
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDisconnectInPostResponseDelete() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(ByteString.valueOfUtf8("o=test"));
      conn.writeMessage(deleteRequest, DisconnectClientPlugin.createDisconnectControlList("PostResponse"));

responseLoop:
    while (true)
    {
      LDAPMessage message = conn.readMessage();
      if (message == null)
      {
        // The connection has been closed.
        break responseLoop;
      }

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
          throw new Exception("Unexpected response message " + message +
                              " encountered in " +
                              "testDisconnectInPostResponseDelete");
      }
    }
    }
  }



  /**
   * Tests to ensure that any registered notification listeners are invoked for
   * a successful delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessWithNotificationListener() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerInternalPlugin(changeListener);
    try
    {
      assertEquals(changeListener.getAddCount(), 0);

      DeleteOperation deleteOperation = processDeleteRaw("o=test");
      assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
      retrieveCompletedOperationElements(deleteOperation);

      assertEquals(changeListener.getDeleteCount(), 1);
    }
    finally
    {
      DirectoryServer.deregisterInternalPlugin(changeListener);
    }
  }



  /**
   * Tests to ensure that any registered notification listeners are not invoked
   * for a failed delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailureWithNotificationListener() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestChangeNotificationListener changeListener =
         new TestChangeNotificationListener();
    DirectoryServer.registerInternalPlugin(changeListener);
    try
    {
      assertEquals(changeListener.getAddCount(), 0);

      DeleteOperation deleteOperation = processDeleteRaw("cn=nonexistent,o=test");
      assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

      assertEquals(changeListener.getDeleteCount(), 0);
    }
    finally
    {
      DirectoryServer.deregisterInternalPlugin(changeListener);
    }
  }



  /**
   * Tests the behavior of the server when short-circuiting out of a delete
   * operation in the pre-parse phase with a success result code.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testShortCircuitInPreParse() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    List<Control> controls =
         ShortCircuitPlugin.createShortCircuitControlList(0, "PreParse");

    DeleteOperation deleteOperation =
        newDeleteOperation(controls, ByteString.valueOfUtf8("o=test"));
    deleteOperation.run();
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));
  }
}
