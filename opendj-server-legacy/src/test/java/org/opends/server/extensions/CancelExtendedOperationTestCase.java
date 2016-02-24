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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.plugins.DelayPreOpPlugin;
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
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/** A set of test cases for the cancel extended operation handler. */
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
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the ability to cancel an add operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelAddOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      // Create an add request and send it to the server. Make sure to include
      // the delay request control so it won't complete before we can send the
      // cancel request.
      List<RawAttribute> attributes = newArrayList(
          newRawAttribute("objectClass", "top", "organizationalUnit"),
          newRawAttribute("ou", "People"));
      AddRequestProtocolOp addRequest =
          new AddRequestProtocolOp(ByteString.valueOfUtf8("ou=People,o=test"), attributes);
      conn.writeMessage(addRequest, DelayPreOpPlugin.createDelayControlList(5000));

      conn.writeMessage(cancelRequestExtendedOp(2));

      assertEquals(getCancelledResponseMessageType(conn), OP_TYPE_ADD_RESPONSE);
    }
  }

  private RawAttribute newRawAttribute(String attrName, String... attrValues)
  {
    return new LDAPAttribute(attrName, newArrayList(attrValues));
  }

  /**
   * Tests the ability to cancel a compare operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelCompareOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      // Create a compare request and send it to the server. Make sure to include
      // the delay request control so it won't complete before we can send the
      // cancel request.
      CompareRequestProtocolOp compareRequest =
          new CompareRequestProtocolOp(ByteString.valueOfUtf8("o=test"), "o", ByteString.valueOfUtf8("test"));
      conn.writeMessage(compareRequest, DelayPreOpPlugin.createDelayControlList(5000));

      conn.writeMessage(cancelRequestExtendedOp(2));

      assertEquals(getCancelledResponseMessageType(conn), OP_TYPE_COMPARE_RESPONSE);
    }
  }



  /**
   * Tests the ability to cancel a delete operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelDeleteOperation() throws Exception
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

      // Create a delete request and send it to the server.  Make sure to include
      // the delay request control so it won't complete before we can send the
      // cancel request.
      DeleteRequestProtocolOp deleteRequest =
          new DeleteRequestProtocolOp(ByteString.valueOfUtf8("cn=test,o=test"));
      conn.writeMessage(deleteRequest, DelayPreOpPlugin.createDelayControlList(5000));

      conn.writeMessage(cancelRequestExtendedOp(2));

      assertEquals(getCancelledResponseMessageType(conn), OP_TYPE_DELETE_RESPONSE);
    }
  }



  /**
   * Tests the ability to cancel an extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelExtendedOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      // Create a "Who Am I?" extended operation and send it to the server. Make
      // sure to include the delay request control so it won't complete before we
      // can send the cancel request.
      ExtendedRequestProtocolOp whoAmIRequest = new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST, null);
      conn.writeMessage(whoAmIRequest, DelayPreOpPlugin.createDelayControlList(5000));

      conn.writeMessage(cancelRequestExtendedOp(2));

      // Read two response messages from the server. They should both be extended
      // responses, one with the result code CANCELED and one with SUCCESS.
      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.CANCELED);

      message = conn.readMessage();
      extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
    }
  }



  /**
   * Tests the ability to cancel a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelModifyOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      // Create a modify request and send it to the server.  Make sure to include
      // the delay request control so it won't complete before we can send the
      // cancel request.
      List<RawModification> mods = newArrayList(
          (RawModification) new LDAPModification(REPLACE, new LDAPAttribute("description", "foo")));

      conn.writeMessage(
          new ModifyRequestProtocolOp(ByteString.valueOfUtf8("o=test"), mods),
          DelayPreOpPlugin.createDelayControlList(5000));

      // Create a cancel request and send it to the server.
      conn.writeMessage(cancelRequestExtendedOp(2));

      assertEquals(getCancelledResponseMessageType(conn), OP_TYPE_MODIFY_RESPONSE);
    }
  }

  private ExtendedRequestProtocolOp cancelRequestExtendedOp(int messageNb) throws IOException
  {
    return new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST, cancelRequest(messageNb));
  }

  private ByteString cancelRequest(int messageNb) throws IOException
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeInteger(messageNb);
    writer.writeEndSequence();
    return builder.toByteString();
  }



  /**
   * Tests the ability to cancel a modify DN operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelModifyDNOperation() throws Exception
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

      // Create a modify DN request and send it to the server. Make sure to
      // include the delay request control so it won't complete before we can send
      // the cancel request.
      ModifyDNRequestProtocolOp modifyDNRequest = new ModifyDNRequestProtocolOp(
          ByteString.valueOfUtf8("cn=test,o=test"), ByteString.valueOfUtf8("cn=test2"), true);
      conn.writeMessage(modifyDNRequest, DelayPreOpPlugin.createDelayControlList(5000));

      conn.writeMessage(cancelRequestExtendedOp(2));

      assertEquals(getCancelledResponseMessageType(conn), OP_TYPE_MODIFY_DN_RESPONSE);
    }
  }



  private byte getCancelledResponseMessageType(RemoteConnection conn) throws IOException, LDAPException
  {
    boolean cancelSuccessful = false;
    byte cancelledMessageType = 0;

    for (int i = 0; i < 2; i++)
    {
      LDAPMessage message = conn.readMessage();
      switch (message.getProtocolOpType())
      {
      case OP_TYPE_ADD_RESPONSE:
        AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
        assertEquals(addResponse.getResultCode(), LDAPResultCode.CANCELED);
        cancelledMessageType = OP_TYPE_ADD_RESPONSE;
        break;
      case OP_TYPE_MODIFY_RESPONSE:
        ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
        assertEquals(modifyResponse.getResultCode(), LDAPResultCode.CANCELED);
        cancelledMessageType = OP_TYPE_MODIFY_RESPONSE;
        break;
      case OP_TYPE_MODIFY_DN_RESPONSE:
        ModifyDNResponseProtocolOp modifyDNResponse = message.getModifyDNResponseProtocolOp();
        assertEquals(modifyDNResponse.getResultCode(), LDAPResultCode.CANCELED);
        cancelledMessageType = OP_TYPE_MODIFY_DN_RESPONSE;
        break;
      case OP_TYPE_DELETE_RESPONSE:
        DeleteResponseProtocolOp deleteResponse = message.getDeleteResponseProtocolOp();
        assertEquals(deleteResponse.getResultCode(), LDAPResultCode.CANCELED);
        cancelledMessageType = OP_TYPE_DELETE_RESPONSE;
        break;
      case OP_TYPE_SEARCH_RESULT_DONE:
        SearchResultDoneProtocolOp searchResultDone = message.getSearchResultDoneProtocolOp();
        assertEquals(searchResultDone.getResultCode(), LDAPResultCode.CANCELED);
        cancelledMessageType = OP_TYPE_SEARCH_RESULT_DONE;
        break;
      case OP_TYPE_COMPARE_RESPONSE:
        CompareResponseProtocolOp compareResponse = message.getCompareResponseProtocolOp();
        assertEquals(compareResponse.getResultCode(), LDAPResultCode.CANCELED);
        cancelledMessageType = OP_TYPE_COMPARE_RESPONSE;
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
        assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
        cancelSuccessful = true;
        break;
      default:
      }
    }

    assertTrue(cancelSuccessful);
    return cancelledMessageType;
  }

  /**
   * Tests the ability to cancel a search operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelSearchOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

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
      conn.writeMessage(searchRequest, DelayPreOpPlugin.createDelayControlList(5000));
      conn.writeMessage(cancelRequestExtendedOp(2));
      assertEquals(getCancelledResponseMessageType(conn), OP_TYPE_SEARCH_RESULT_DONE);
    }
  }



  /**
   * Tests the attempt to cancel an operation that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelNoSuchOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      ExtendedRequestProtocolOp extendedRequest = cancelRequestExtendedOp(2);
      conn.getLdapWriter().writeMessage(new LDAPMessage(3, extendedRequest));

      // Read the response message from the server. It should be an extended
      // response with a result code of "no such operation".
      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.NO_SUCH_OPERATION);
    }
  }



  /**
   * Tests sending a cancel request with no request value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelNoValue() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      // Create a cancel request and send it to the server.
      ExtendedRequestProtocolOp extendedRequest = new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST, null);
      conn.writeMessage(extendedRequest);

      // Read the response message from the server. It should be an extended
      // response with a result code of "no such operation".
      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
  }



  /**
   * Tests sending a cancel request with a malformed request value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelMalformedValue() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      // Create a cancel request and send it to the server.
      ExtendedRequestProtocolOp extendedRequest =
          new ExtendedRequestProtocolOp(OID_CANCEL_REQUEST, ByteString.valueOfUtf8("malformed"));
      conn.writeMessage(extendedRequest);

      // Read the response message from the server. It should be an extended
      // response with a result code of "no such operation".
      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
  }

  /**
   * Tests the ability to cancel an extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCancelCancelExtendedOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("cn=Directory Manager", "password");

      conn.writeMessage(cancelRequestExtendedOp(2));

      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.CANNOT_CANCEL);
    }
  }
}
