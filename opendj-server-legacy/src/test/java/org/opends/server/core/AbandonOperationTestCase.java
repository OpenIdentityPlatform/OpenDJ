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

import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.plugins.DelayPreOpPlugin;
import org.opends.server.plugins.DisconnectClientPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.AbandonRequestProtocolOp;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.CompareRequestProtocolOp;
import org.opends.server.protocols.ldap.CompareResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.Control;
import org.opends.server.types.Operation;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A set of test cases for abandon operations.
 */
public class AbandonOperationTestCase
       extends OperationTestCase
{
  /** {@inheritDoc} */
  @Override
  protected Operation[] createTestOperations()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
      new AbandonOperationBasis(conn, InternalClientConnection.nextOperationID(),
                           InternalClientConnection.nextMessageID(),
                           new ArrayList<Control>(), 1)
    };
  }


  /**
   * For some reason, the @BeforeClass method in the super class is not called.
   */
  @Override
  @BeforeClass
  public void startServer() throws Exception {
    super.startServer();
  }

  /**
   * Tests the <CODE>getIDToAbandon</CODE> method.
   */
  @Test
  public void testGetIDToAbandon()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, InternalClientConnection.nextOperationID(),
                  InternalClientConnection.nextMessageID(), new ArrayList<Control>(), 1);
    assertEquals(abandonOperation.getIDToAbandon(), 1);
  }



  /**
   * Tests the <CODE>cancel</CODE> method.
   */
  @Test
  public void testCancel()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, InternalClientConnection.nextOperationID(),
                  InternalClientConnection.nextMessageID(), new ArrayList<Control>(), 1);

    CancelRequest cancelRequest = new CancelRequest(true,
            LocalizableMessage.raw("Test Cancel"));

    assertEquals(abandonOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);
  }



  /**
   * Tests the <CODE>getCancelRequest</CODE> method.
   */
  @Test
  public void testGetCancelRequest()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, InternalClientConnection.nextOperationID(),
                  InternalClientConnection.nextMessageID(), new ArrayList<Control>(), 1);
    assertNull(abandonOperation.getCancelRequest());
  }



  /**
   * Invokes a number of operation methods on the provided abandon operation
   * for which all processing has been completed.
   *
   * @param  abandonOperation  The operation to be tested.
   */
  private void examineCompletedOperation(AbandonOperation abandonOperation)
  {
    assertTrue(abandonOperation.getIDToAbandon() > 0);
    assertTrue(abandonOperation.getProcessingStartTime() > 0);
    assertTrue(abandonOperation.getProcessingStopTime() > 0);
    assertTrue(abandonOperation.getProcessingTime() >= 0);
  }



  /**
   * Attempts an internal abandon operation, which will fail because internal
   * operations cannot be abandoned.
   */
  @Test
  public void testAbandonInternal()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, InternalClientConnection.nextOperationID(),
                  InternalClientConnection.nextMessageID(), new ArrayList<Control>(), 1);
    abandonOperation.run();
    assertEquals(abandonOperation.getResultCode(),
                 ResultCode.CANNOT_CANCEL);
    examineCompletedOperation(abandonOperation);
  }



  /**
   * Tests performing an abandon operation on a client connection that gets
   * terminated during pre-parse plugin processing.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" })
  public void testDisconnectInPreParse()
         throws Exception
  {
    // Establish a connection to the server.  It can be unauthenticated for the
    // purpose of this test.
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(1),
                        DisconnectClientPlugin.createDisconnectControlList("PreParse"));

      Thread.sleep(3000);
    }

    // NOTE:  We can't check to see if pre-parse plugins were called yet
    //        because there's no plugin ordering.  It's possible that the
    //        disconnect plugin was called before the invocation counter plugin,
    //        in which case the pre-parse count wouldn't be incremented.
  }



  /**
   * Tests the use of the abandon operation with a target operation that doesn't
   * exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" })
  public void testNoSuchOperation()
         throws Exception
  {
    // Establish a connection to the server.  It can be unauthenticated for the
    // purpose of this test.
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(1));

      Thread.sleep(3000);
    }
  }



  /**
   * Tests the ability to abandon an add operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAbandonAdd()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long abandonRequests = ldapStatistics.getAbandonRequests();
      long abandonsCompleted = ldapStatistics.getOperationsAbandoned();

      // Create an add request and send it to the server. Make sure to include
      // the delay request control so it won't complete before we can send the
      // abandon request.
      ArrayList<RawAttribute> attributes = newArrayList(
          newRawAttribute("objectClass", "top", "organizationalUnit"),
          newRawAttribute("ou", "People"));

      AddRequestProtocolOp addRequest =
          new AddRequestProtocolOp(ByteString.valueOfUtf8("ou=People,o=test"), attributes);
      conn.writeMessage(addRequest, DelayPreOpPlugin.createDelayControlList(5000));

      // Send the abandon request to the server.
      conn.writeMessage(new AbandonRequestProtocolOp(2));

      // Normally, abandoned operations don't receive a response. However, the
      // testing configuration has been updated to ensure that if an operation
      // does get abandoned, the server will return a response for it with a
      // result code of "cancelled".
      LDAPMessage message = conn.readMessage();
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertEquals(addResponse.getResultCode(), LDAPResultCode.CANCELED);

      assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests + 1);
      waitForAbandon(abandonsCompleted + 1);
    }
  }



  private RawAttribute newRawAttribute(String attrType, String... attrValues)
  {
    return new LDAPAttribute(attrType, newArrayList(attrValues));
  }

  /**
   * Tests the ability to abandon a compare operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAbandonCompare()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long abandonRequests   = ldapStatistics.getAbandonRequests();
      long abandonsCompleted = ldapStatistics.getOperationsAbandoned();

      // Create a compare request and send it to the server.  Make sure to include
      // the delay request control so it won't complete before we can send the
      // abandon request.
      CompareRequestProtocolOp compareRequest =
          new CompareRequestProtocolOp(ByteString.valueOfUtf8("o=test"), "o",
              ByteString.valueOfUtf8("test"));
      conn.writeMessage(compareRequest, DelayPreOpPlugin.createDelayControlList(5000));


      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(2));


      // Normally, abandoned operations don't receive a response.  However, the
      // testing configuration has been updated to ensure that if an operation
      // does get abandoned, the server will return a response for it with a
      // result code of "cancelled".
      LDAPMessage message = conn.readMessage();
      CompareResponseProtocolOp compareResponse = message.getCompareResponseProtocolOp();
      assertEquals(compareResponse.getResultCode(), LDAPResultCode.CANCELED);

      assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
      waitForAbandon(abandonsCompleted+1);
    }
  }



  /**
   * Tests the ability to abandon a delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAbandonDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    // Add an entry to the server that we can delete.
    TestCaseUtils.addEntry(
         "dn: cn=test,o=test",
         "objectClass: top",
         "objectClass: device",
         "cn: test");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long abandonRequests = ldapStatistics.getAbandonRequests();
      long abandonsCompleted = ldapStatistics.getOperationsAbandoned();

      // Create a delete request and send it to the server. Make sure to include
      // the delay request control so it won't complete before we can send the
      // abandon request.
      DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(ByteString.valueOfUtf8("cn=test,o=test"));
      conn.writeMessage(deleteRequest, DelayPreOpPlugin.createDelayControlList(5000));

      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(2));

      // Normally, abandoned operations don't receive a response. However, the
      // testing configuration has been updated to ensure that if an operation
      // does get abandoned, the server will return a response for it with a
      // result code of "cancelled".
      LDAPMessage message = conn.readMessage();
      DeleteResponseProtocolOp deleteResponse = message.getDeleteResponseProtocolOp();
      assertEquals(deleteResponse.getResultCode(), LDAPResultCode.CANCELED);

      assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests + 1);
      waitForAbandon(abandonsCompleted + 1);
    }
  }



  /**
   * Tests the ability to abandon an extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAbandonExtended()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long abandonRequests = ldapStatistics.getAbandonRequests();
      long abandonsCompleted = ldapStatistics.getOperationsAbandoned();

      // Create a "Who Am I?" extended operation and send it to the server. Make
      // sure to include the delay request control so it won't complete before we
      // can send the abandon request.
      ExtendedRequestProtocolOp whoAmIRequest = new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST, null);
      conn.writeMessage(whoAmIRequest, DelayPreOpPlugin.createDelayControlList(5000));

      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(2));

      // Normally, abandoned operations don't receive a response. However, the
      // testing configuration has been updated to ensure that if an operation
      // does get abandoned, the server will return a response for it with a
      // result code of "cancelled".
      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.CANCELED);

      assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests + 1);
      waitForAbandon(abandonsCompleted + 1);
    }
  }



  /**
   * Tests the ability to abandon a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAbandonModify()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long abandonRequests = ldapStatistics.getAbandonRequests();
      long abandonsCompleted = ldapStatistics.getOperationsAbandoned();

      // Create a modify request and send it to the server. Make sure to include
      // the delay request control so it won't complete before we can send the
      // abandon request.
      ArrayList<RawModification> mods = new ArrayList<>(1);
      mods.add(new LDAPModification(ModificationType.REPLACE, new LDAPAttribute("description", "foo")));

      ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(ByteString.valueOfUtf8("o=test"), mods);
      conn.writeMessage(modifyRequest, DelayPreOpPlugin.createDelayControlList(5000));

      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(2));

      // Normally, abandoned operations don't receive a response. However, the
      // testing configuration has been updated to ensure that if an operation
      // does get abandoned, the server will return a response for it with a
      // result code of "cancelled".
      LDAPMessage message = conn.readMessage();
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertEquals(modifyResponse.getResultCode(), LDAPResultCode.CANCELED);

      assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests + 1);
      waitForAbandon(abandonsCompleted + 1);
    }
  }



  /**
   * Tests the ability to abandon a modify DN operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAbandonModifyDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    // Add an entry to the server that we can rename.
    TestCaseUtils.addEntry(
         "dn: cn=test,o=test",
         "objectClass: top",
         "objectClass: device",
         "cn: test");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long abandonRequests = ldapStatistics.getAbandonRequests();
      long abandonsCompleted = ldapStatistics.getOperationsAbandoned();

      // Create a modify DN request and send it to the server. Make sure to
      // include the delay request control so it won't complete before we can send
      // the abandon request.
      ModifyDNRequestProtocolOp modifyDNRequest = new ModifyDNRequestProtocolOp(
          ByteString.valueOfUtf8("cn=test,o=test"), ByteString.valueOfUtf8("cn=test2"), true);
      conn.writeMessage(modifyDNRequest, DelayPreOpPlugin.createDelayControlList(5000));

      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(2));

      // Normally, abandoned operations don't receive a response. However, the
      // testing configuration has been updated to ensure that if an operation
      // does get abandoned, the server will return a response for it with a
      // result code of "cancelled".
      LDAPMessage message = conn.readMessage();
      ModifyDNResponseProtocolOp modifyDNResponse = message.getModifyDNResponseProtocolOp();
      assertEquals(modifyDNResponse.getResultCode(), LDAPResultCode.CANCELED);

      assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests + 1);
      waitForAbandon(abandonsCompleted + 1);
    }
  }



  /**
   * Tests the ability to abandon a search operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAbandonSearch()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      long abandonRequests = ldapStatistics.getAbandonRequests();
      long abandonsCompleted = ldapStatistics.getOperationsAbandoned();

      // Create a search request and send it to the server. Make sure to include
      // the delay request control so it won't complete before we can send the
      // abandon request.
      SearchRequestProtocolOp searchRequest =
          new SearchRequestProtocolOp(ByteString.valueOfUtf8("o=test"), SearchScope.BASE_OBJECT,
              DereferenceAliasesPolicy.NEVER, 0, 0, false, LDAPFilter.decode("(match=false)"),
              new LinkedHashSet<String>());
      conn.writeMessage(searchRequest, DelayPreOpPlugin.createDelayControlList(5000));

      // Send the abandon request to the server and wait a few seconds to ensure
      // it has completed before closing the connection.
      conn.writeMessage(new AbandonRequestProtocolOp(2));

      // Normally, abandoned operations don't receive a response. However, the
      // testing configuration has been updated to ensure that if an operation
      // does get abandoned, the server will return a response for it with a
      // result code of "cancelled".
      LDAPMessage message = conn.readMessage();
      SearchResultDoneProtocolOp searchDone = message.getSearchResultDoneProtocolOp();
      assertEquals(searchDone.getResultCode(), LDAPResultCode.CANCELED);

      assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests + 1);
      waitForAbandon(abandonsCompleted + 1);
    }
  }



  /**
   * Waits up to ten seconds for the abandoned operation count to reach the
   * expected value.
   *
   * @param  expectedCount  The abandon count the server is expected to reach.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void waitForAbandon(long expectedCount)
          throws Exception
  {
    long stopTime = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < stopTime)
    {
      if (ldapStatistics.getOperationsAbandoned() == expectedCount)
      {
        return;
      }

      Thread.sleep(10);
    }

    throw new AssertionError("Expected abandon count of " + expectedCount +
                   " but got " + ldapStatistics.getOperationsAbandoned());
  }
}

