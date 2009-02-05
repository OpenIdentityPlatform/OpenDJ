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
package org.opends.server.extensions;



import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AbandonOperationBasis;
import org.opends.server.plugins.DelayPreOpPlugin;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.internal.InternalClientConnection;
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
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.types.*;

import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;
import org.opends.messages.Message;


/**
 * A set of test cases for the cancel extended oepration handler.
 */
public class CancelExtendedOperationTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running, and enables the delay plugin
   * so that new requests will be artificially delayed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the ability to cancel an add operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelAddOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create an add request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
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


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read two response messages from the server.  One should be an add
    // response and the other should be an extended response.  They should both
    // have a result code of "cancelled".
    for (int i=0; i < 2; i++)
    {
      message = r.readMessage();
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_ADD_RESPONSE:
          AddResponseProtocolOp addResponse =
               message.getAddResponseProtocolOp();
          assertEquals(addResponse.getResultCode(), LDAPResultCode.CANCELED);
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          ExtendedResponseProtocolOp extendedResponse =
               message.getExtendedResponseProtocolOp();
          assertEquals(extendedResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        default:
      }
    }

    socket.close();
  }



  /**
   * Tests the ability to cancel a compare operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelCompareOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a compare request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
    CompareRequestProtocolOp compareRequest =
         new CompareRequestProtocolOp(ByteString.valueOf("o=test"), "o",
                                      ByteString.valueOf("test"));
    message = new LDAPMessage(2, compareRequest,
        DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read two response messages from the server.  One should be a compare
    // response and the other should be an extended response.  They should both
    // have a result code of "cancelled".
    for (int i=0; i < 2; i++)
    {
      message = r.readMessage();
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_COMPARE_RESPONSE:
          CompareResponseProtocolOp compareResponse =
               message.getCompareResponseProtocolOp();
          assertEquals(compareResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          ExtendedResponseProtocolOp extendedResponse =
               message.getExtendedResponseProtocolOp();
          assertEquals(extendedResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        default:
      }
    }

    socket.close();
  }



  /**
   * Tests the ability to cancel a delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelDeleteOperation()
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


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a delete request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(ByteString.valueOf("cn=test,o=test"));
    message = new LDAPMessage(2, deleteRequest,
        DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read two response messages from the server.  One should be a delete
    // response and the other should be an extended response.  They should both
    // have a result code of "cancelled".
    for (int i=0; i < 2; i++)
    {
      message = r.readMessage();
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_DELETE_RESPONSE:
          DeleteResponseProtocolOp deleteResponse =
               message.getDeleteResponseProtocolOp();
          assertEquals(deleteResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          ExtendedResponseProtocolOp extendedResponse =
               message.getExtendedResponseProtocolOp();
          assertEquals(extendedResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        default:
      }
    }

    socket.close();
  }



  /**
   * Tests the ability to cancel an extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelExtendedOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a "Who Am I?" extended oepration and send it to the server.  Make
    // sure to include the delay request control so it won't complete before we
    // can send the cancel request.
    ExtendedRequestProtocolOp whoAmIRequest =
         new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST, null);
    message = new LDAPMessage(2, whoAmIRequest,
        DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);

                                                          
    // Read two response messages from the server.  They should both be extended
    // responses and they should both have result codes of "cancelled".
    for (int i=0; i < 2; i++)
    {
      message = r.readMessage();
      ExtendedResponseProtocolOp extendedResponse =
           message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.CANCELED);
    }

    socket.close();
  }



  /**
   * Tests the ability to cancel a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelModifyOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a modify request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
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


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read two response messages from the server.  One should be a modify
    // response and the other should be an extended response.  They should both
    // have a result code of "cancelled".
    for (int i=0; i < 2; i++)
    {
      message = r.readMessage();
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_MODIFY_RESPONSE:
          ModifyResponseProtocolOp modifyResponse =
               message.getModifyResponseProtocolOp();
          assertEquals(modifyResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          ExtendedResponseProtocolOp extendedResponse =
               message.getExtendedResponseProtocolOp();
          assertEquals(extendedResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        default:
      }
    }

    socket.close();
  }



  /**
   * Tests the ability to cancel a modify DN operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelModifyDNOperation()
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


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a modify DN request and send it to the server.  Make sure to
    // include the delay request control so it won't complete before we can send
    // the cancel request.
    ModifyDNRequestProtocolOp modifyDNRequest =
         new ModifyDNRequestProtocolOp(ByteString.valueOf("cn=test,o=test"),
                                       ByteString.valueOf("cn=test2"), true);
    message = new LDAPMessage(2, modifyDNRequest,
        DelayPreOpPlugin.createDelayControlList(5000));
    w.writeMessage(message);


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read two response messages from the server.  One should be a modify DN
    // response and the other should be an extended response.  They should both
    // have a result code of "cancelled".
    for (int i=0; i < 2; i++)
    {
      message = r.readMessage();
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_MODIFY_DN_RESPONSE:
          ModifyDNResponseProtocolOp modifyDNResponse =
               message.getModifyDNResponseProtocolOp();
          assertEquals(modifyDNResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          ExtendedResponseProtocolOp extendedResponse =
               message.getExtendedResponseProtocolOp();
          assertEquals(extendedResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        default:
      }
    }

    socket.close();
  }



  /**
   * Tests the ability to cancel a search operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelSearchOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a search request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
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


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read two response messages from the server.  One should be a search
    // result done and the other should be an extended response.  They should
    // both have a result code of "cancelled".
    for (int i=0; i < 2; i++)
    {
      message = r.readMessage();
      switch (message.getProtocolOpType())
      {
        case OP_TYPE_SEARCH_RESULT_DONE:
          SearchResultDoneProtocolOp searchResultDone =
               message.getSearchResultDoneProtocolOp();
          assertEquals(searchResultDone.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        case OP_TYPE_EXTENDED_RESPONSE:
          ExtendedResponseProtocolOp extendedResponse =
               message.getExtendedResponseProtocolOp();
          assertEquals(extendedResponse.getResultCode(),
                       LDAPResultCode.CANCELED);
          break;
        default:
      }
    }

    socket.close();
  }



  /**
   * Tests the attempt to cancel an operation that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelNoSuchOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a cancel request and send it to the server.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read the response message from the server.  It should be an extended
    // response with a result code of "no such operation".
    message = r.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(),
                 LDAPResultCode.NO_SUCH_OPERATION);

    socket.close();
  }



  /**
   * Tests sending a cancel request with no request value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelNoValue()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a cancel request and send it to the server.
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST, null);
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read the response message from the server.  It should be an extended
    // response with a result code of "no such operation".
    message = r.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(),
                 LDAPResultCode.PROTOCOL_ERROR);

    socket.close();
  }



  /**
   * Tests sending a cancel request with a malformed request value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelMalformedValue()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a cancel request and send it to the server.
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
                                       ByteString.valueOf("malformed"));
    message = new LDAPMessage(3, extendedRequest);
    w.writeMessage(message);


    // Read the response message from the server.  It should be an extended
    // response with a result code of "no such operation".
    message = r.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(),
                 LDAPResultCode.PROTOCOL_ERROR);

    socket.close();
  }

  /**
   * Tests the ability to cancel an extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCancelCancelExtendedOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a self cancelling request and send it to the server. Make sure
    // to include the delay request control so it won't complete before we
    // can send the cancel request.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(2);
    writer.writeEndSequence();
    ExtendedRequestProtocolOp extendedRequest =
        new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
             builder.toByteString());
    message = new LDAPMessage(2, extendedRequest);
    w.writeMessage(message);

    message = r.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
        message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(),
        LDAPResultCode.CANNOT_CANCEL);

    socket.close();
  }

}

