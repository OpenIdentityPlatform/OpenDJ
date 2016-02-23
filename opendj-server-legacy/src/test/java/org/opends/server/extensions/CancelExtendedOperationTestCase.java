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
 * Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.plugins.DelayPreOpPlugin;
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
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the cancel extended operation handler.
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
  @BeforeClass
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create an add request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
    ArrayList<RawAttribute> attributes = new ArrayList<>();
    attributes.add(new LDAPAttribute("objectClass", newArrayList("top", "organizationalUnit")));
    attributes.add(new LDAPAttribute("ou", "People"));

    AddRequestProtocolOp addRequest =
         new AddRequestProtocolOp(ByteString.valueOfUtf8("ou=People,o=test"), attributes);
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
    // response and the other should be an extended response.
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
                       LDAPResultCode.SUCCESS);
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a compare request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
    CompareRequestProtocolOp compareRequest =
         new CompareRequestProtocolOp(ByteString.valueOfUtf8("o=test"), "o",
                                      ByteString.valueOfUtf8("test"));
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
    // response and the other should be an extended response.
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
                       LDAPResultCode.SUCCESS);
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
  @Test
  public void testCancelDeleteOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    // Add an entry to the server that we can delete.
    TestCaseUtils.addEntry(
         "dn: cn=test,o=test",
         "objectClass: top",
         "objectClass: device",
         "cn: test");

    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a delete request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(ByteString.valueOfUtf8("cn=test,o=test"));
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
    // response and the other should be an extended response.
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
                       LDAPResultCode.SUCCESS);
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a "Who Am I?" extended operation and send it to the server.  Make
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
    // responses, one with the result code CANCELED and one with SUCCESS.
    message = r.readMessage();
    ExtendedResponseProtocolOp extendedResponse =
            message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.CANCELED);

    message = r.readMessage();
    extendedResponse = message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);

    socket.close();
  }



  /**
   * Tests the ability to cancel a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a modify request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
    ArrayList<RawModification> mods = new ArrayList<>(1);
    mods.add(new LDAPModification(ModificationType.REPLACE,
        new LDAPAttribute("description", "foo")));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(ByteString.valueOfUtf8("o=test"), mods);
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
    // response and the other should be an extended response.
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
                       LDAPResultCode.SUCCESS);
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
  @Test
  public void testCancelModifyDNOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    // Add an entry to the server that we can rename.
    TestCaseUtils.addEntry(
         "dn: cn=test,o=test",
         "objectClass: top",
         "objectClass: device",
         "cn: test");

    // Create a new connection to the Directory Server and authenticate as
    // the Directory Manager.
    Socket socket = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r =
        new org.opends.server.tools.LDAPReader(socket);
    org.opends.server.tools.LDAPWriter w =
        new org.opends.server.tools.LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a modify DN request and send it to the server.  Make sure to
    // include the delay request control so it won't complete before we can send
    // the cancel request.
    ModifyDNRequestProtocolOp modifyDNRequest =
         new ModifyDNRequestProtocolOp(ByteString.valueOfUtf8("cn=test,o=test"),
                                       ByteString.valueOfUtf8("cn=test2"), true);
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
    // response and the other should be an extended response.
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
                       LDAPResultCode.SUCCESS);
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a search request and send it to the server.  Make sure to include
    // the delay request control so it won't complete before we can send the
    // cancel request.
    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(ByteString.valueOfUtf8("o=test"),
                                     SearchScope.BASE_OBJECT,
                                     DereferenceAliasesPolicy.NEVER, 0,
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
    // result done and the other should be an extended response.
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
                       LDAPResultCode.SUCCESS);
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);


    // Create a cancel request and send it to the server.
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST,
                                       ByteString.valueOfUtf8("malformed"));
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
  @Test
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
         new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"),
                                   3, ByteString.valueOfUtf8("password"));
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

