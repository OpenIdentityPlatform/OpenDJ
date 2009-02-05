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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import static org.opends.server.util.ServerConstants.OID_WHO_AM_I_REQUEST;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.tools.LDAPReader;
import org.opends.messages.Message;
import org.opends.server.plugins.DelayPreOpPlugin;
import org.opends.server.plugins.DisconnectClientPlugin;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.AbandonRequestProtocolOp;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
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
import org.opends.server.types.*;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;


/**
 * A set of test cases for abandon operations
 */
public class AbandonOperationTestCase
       extends OperationTestCase
{
  /**
   * {@inheritDoc}
   */
  @Override()
  protected Operation[] createTestOperations()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    return new Operation[]
    {
      new AbandonOperationBasis(conn, conn.nextOperationID(),
                           conn.nextMessageID(),
                           new ArrayList<Control>(), 1)
    };
  }


  /**
   * For some reason, the @BeforeClass method in the super class is not called.
   */
  @BeforeClass()
  public void startServer() throws Exception {
    super.startServer();
  }

  /**
   * Tests the <CODE>getIDToAbandon</CODE> method.
   */
  @Test()
  public void testGetIDToAbandon()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, conn.nextOperationID(),
                  conn.nextMessageID(), new ArrayList<Control>(), 1);
    assertEquals(abandonOperation.getIDToAbandon(), 1);
  }



  /**
   * Tests the <CODE>cancel</CODE> method.
   */
  @Test()
  public void testCancel()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, conn.nextOperationID(),
                  conn.nextMessageID(), new ArrayList<Control>(), 1);

    CancelRequest cancelRequest = new CancelRequest(true,
            Message.raw("Test Cancel"));

    assertEquals(abandonOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);
  }



  /**
   * Tests the <CODE>getCancelRequest</CODE> method.
   */
  @Test()
  public void testGetCancelRequest()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, conn.nextOperationID(),
                  conn.nextMessageID(), new ArrayList<Control>(), 1);
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
    assertNotNull(abandonOperation.getResponseLogElements());
  }



  /**
   * Attempts an internal abandon operation, which will fail because internal
   * operations cannot be abandoned.
   */
  @Test()
  public void testAbandonInternal()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AbandonOperationBasis abandonOperation =
         new AbandonOperationBasis(conn, conn.nextOperationID(),
                  conn.nextMessageID(), new ArrayList<Control>(), 1);
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
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPWriter w = new LDAPWriter(s);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(1);
    LDAPMessage message = new LDAPMessage(2, abandonRequest,
         DisconnectClientPlugin.createDisconnectControlList("PreParse"));
    w.writeMessage(message);

    Thread.sleep(3000);

    try
    {
      s.close();
    } catch (Exception e) {}

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
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPWriter w = new LDAPWriter(s);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(1);
    w.writeMessage(new LDAPMessage(2, abandonRequest));

    Thread.sleep(3000);

    s.close();
  }



  /**
   * Tests the ability to abandon an add operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbandonAdd()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Establish a connection to the server and bind as a root user.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    s.setSoTimeout(6000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    long abandonRequests   = ldapStatistics.getAbandonRequests();
    long abandonsCompleted = ldapStatistics.getOperationsAbandoned();


    // Create an add request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // abandon request.
    ArrayList<RawAttribute> attributes = new ArrayList<RawAttribute>();

    ArrayList<ByteString> values = new ArrayList<ByteString>(2);
    values.add(ByteString.valueOf("top"));
    values.add(ByteString.valueOf("organizationalUnit"));
    attributes.add(new LDAPAttribute("objectClass", values));

    values = new ArrayList<ByteString>(1);
    values.add(ByteString.valueOf("People"));
    attributes.add(new LDAPAttribute("ou", values));

    AddRequestProtocolOp addRequest =
         new AddRequestProtocolOp(ByteString.valueOf("ou=People,o=test"),
                                  attributes);
    message = new LDAPMessage(2, addRequest,
                       DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Send the abandon request to the server.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(2);
    w.writeMessage(new LDAPMessage(3, abandonRequest));


    // Normally, abandoned operations don't receive a response.  However, the
    // testing configuration has been updated to ensure that if an operation
    // does get abandoned, the server will return a response for it with a
    // result code of "cancelled".
    message = r.readMessage();
    AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
    assertEquals(addResponse.getResultCode(), LDAPResultCode.CANCELED);

    assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
    waitForAbandon(abandonsCompleted+1);

    s.close();
  }



  /**
   * Tests the ability to abandon a compare operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbandonCompare()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Establish a connection to the server and bind as a root user.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    s.setSoTimeout(6000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    long abandonRequests   = ldapStatistics.getAbandonRequests();
    long abandonsCompleted = ldapStatistics.getOperationsAbandoned();


    // Create a compare request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // abandon request.
    CompareRequestProtocolOp compareRequest =
      new CompareRequestProtocolOp(ByteString.valueOf("o=test"), "o",
                                   ByteString.valueOf("test"));
    message = new LDAPMessage(2, compareRequest,
                       DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(2);
    w.writeMessage(new LDAPMessage(3, abandonRequest));


    // Normally, abandoned operations don't receive a response.  However, the
    // testing configuration has been updated to ensure that if an operation
    // does get abandoned, the server will return a response for it with a
    // result code of "cancelled".
    message = r.readMessage();
    CompareResponseProtocolOp compareResponse =
         message.getCompareResponseProtocolOp();
    assertEquals(compareResponse.getResultCode(), LDAPResultCode.CANCELED);

    assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
    waitForAbandon(abandonsCompleted+1);

    s.close();
  }



  /**
   * Tests the ability to abandon a delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbandonDelete()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Add an entry to the server that we can delete.
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=test,o=test",
         "objectClass: top",
         "objectClass: device",
         "cn: test");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Establish a connection to the server and bind as a root user.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    s.setSoTimeout(6000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    long abandonRequests   = ldapStatistics.getAbandonRequests();
    long abandonsCompleted = ldapStatistics.getOperationsAbandoned();


    // Create a delete request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // abandon request.
    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(ByteString.valueOf("cn=test,o=test"));
    message = new LDAPMessage(2, deleteRequest,
                       DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(2);
    w.writeMessage(new LDAPMessage(3, abandonRequest));


    // Normally, abandoned operations don't receive a response.  However, the
    // testing configuration has been updated to ensure that if an operation
    // does get abandoned, the server will return a response for it with a
    // result code of "cancelled".
    message = r.readMessage();
    DeleteResponseProtocolOp deleteResponse =
         message.getDeleteResponseProtocolOp();
    assertEquals(deleteResponse.getResultCode(), LDAPResultCode.CANCELED);

    assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
    waitForAbandon(abandonsCompleted+1);

    s.close();
  }



  /**
   * Tests the ability to abandon an extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbandonExtended()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Establish a connection to the server and bind as a root user.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    s.setSoTimeout(6000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    long abandonRequests   = ldapStatistics.getAbandonRequests();
    long abandonsCompleted = ldapStatistics.getOperationsAbandoned();


    // Create a "Who Am I?" extended operation and send it to the server.  Make
    // sure to include the delay request control so it won't complete before we
    // can send the abandon request.
    ExtendedRequestProtocolOp whoAmIRequest =
         new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST, null);
    message = new LDAPMessage(2, whoAmIRequest,
                       DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(2);
    w.writeMessage(new LDAPMessage(3, abandonRequest));


    // Normally, abandoned operations don't receive a response.  However, the
    // testing configuration has been updated to ensure that if an operation
    // does get abandoned, the server will return a response for it with a
    // result code of "cancelled".
    message = r.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.CANCELED);

    assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
    waitForAbandon(abandonsCompleted+1);

    s.close();
  }



  /**
   * Tests the ability to abandon a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbandonModify()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Establish a connection to the server and bind as a root user.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    s.setSoTimeout(6000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    long abandonRequests   = ldapStatistics.getAbandonRequests();
    long abandonsCompleted = ldapStatistics.getOperationsAbandoned();


    // Create a modify request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // abandon request.
    ArrayList<ByteString> values = new ArrayList<ByteString>(1);
    values.add(ByteString.valueOf("foo"));

    ArrayList<RawModification> mods = new ArrayList<RawModification>(1);
    mods.add(new LDAPModification(ModificationType.REPLACE,
                                  new LDAPAttribute("description", values)));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(ByteString.valueOf("o=test"), mods);
    message = new LDAPMessage(2, modifyRequest,
                       DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(2);
    w.writeMessage(new LDAPMessage(3, abandonRequest));


    // Normally, abandoned operations don't receive a response.  However, the
    // testing configuration has been updated to ensure that if an operation
    // does get abandoned, the server will return a response for it with a
    // result code of "cancelled".
    message = r.readMessage();
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), LDAPResultCode.CANCELED);

    assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
    waitForAbandon(abandonsCompleted+1);

    s.close();
  }



  /**
   * Tests the ability to abandon a modify DN operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbandonModifyDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Add an entry to the server that we can rename.
    Entry e = TestCaseUtils.makeEntry(
         "dn: cn=test,o=test",
         "objectClass: top",
         "objectClass: device",
         "cn: test");
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(e.getDN(), e.getObjectClasses(), e.getUserAttributes(),
                         e.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    // Establish a connection to the server and bind as a root user.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    s.setSoTimeout(6000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    long abandonRequests   = ldapStatistics.getAbandonRequests();
    long abandonsCompleted = ldapStatistics.getOperationsAbandoned();


    // Create a modify DN request and send it to the server.  Make sure to
    // include the delay request control so it won't complete before we can send
    // the abandon request.
    ModifyDNRequestProtocolOp modifyDNRequest =
         new ModifyDNRequestProtocolOp(ByteString.valueOf("cn=test,o=test"),
                                       ByteString.valueOf("cn=test2"), true);
    message = new LDAPMessage(2, modifyDNRequest,
                       DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(2);
    w.writeMessage(new LDAPMessage(3, abandonRequest));


    // Normally, abandoned operations don't receive a response.  However, the
    // testing configuration has been updated to ensure that if an operation
    // does get abandoned, the server will return a response for it with a
    // result code of "cancelled".
    message = r.readMessage();
    ModifyDNResponseProtocolOp modifyDNResponse =
         message.getModifyDNResponseProtocolOp();
    assertEquals(modifyDNResponse.getResultCode(), LDAPResultCode.CANCELED);

    assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
    waitForAbandon(abandonsCompleted+1);

    s.close();
  }



  /**
   * Tests the ability to abandon a search operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbandonSearch()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Establish a connection to the server and bind as a root user.
    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader r = new LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    s.setSoTimeout(6000);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    long abandonRequests   = ldapStatistics.getAbandonRequests();
    long abandonsCompleted = ldapStatistics.getOperationsAbandoned();


    // Create a search request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // abandon request.
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(ByteString.valueOf("o=test"),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false,
                                     LDAPFilter.decode("(match=false)"),
                                     new LinkedHashSet<String>());
    message = new LDAPMessage(2, searchRequest,
                       DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Send the abandon request to the server and wait a few seconds to ensure
    // it has completed before closing the connection.
    AbandonRequestProtocolOp abandonRequest = new AbandonRequestProtocolOp(2);
    w.writeMessage(new LDAPMessage(3, abandonRequest));


    // Normally, abandoned operations don't receive a response.  However, the
    // testing configuration has been updated to ensure that if an operation
    // does get abandoned, the server will return a response for it with a
    // result code of "cancelled".
    message = r.readMessage();
    SearchResultDoneProtocolOp searchDone =
         message.getSearchResultDoneProtocolOp();
    assertEquals(searchDone.getResultCode(), LDAPResultCode.CANCELED);

    assertEquals(ldapStatistics.getAbandonRequests(), abandonRequests+1);
    waitForAbandon(abandonsCompleted+1);

    s.close();
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

