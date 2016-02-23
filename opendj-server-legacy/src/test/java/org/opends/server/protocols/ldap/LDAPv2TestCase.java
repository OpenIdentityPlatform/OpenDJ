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
 * Portions Copyright 2010-2015 ForgeRock AS.
 */
package org.opends.server.protocols.ldap ;

import java.net.Socket;
import java.util.ArrayList;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * This class provides a number of tests to ensure that the server interacts
 * with LDAPv2 clients as expected.
 */
public class LDAPv2TestCase
       extends LdapTestCase
{
  /**
   * Ensure that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 bind request if
   * configured to do so.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectBindRequest()
         throws Exception
  {
    TestCaseUtils.applyModifications(true,
      "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
      "changetype: modify",
      "replace: ds-cfg-allow-ldap-v2",
      "ds-cfg-allow-ldap-v2: false");

    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "changetype: modify",
        "replace: ds-cfg-allow-ldap-v2",
        "ds-cfg-allow-ldap-v2: true");

      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an extended request from an
   * LDAPv2 client.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectExtendedRequest()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ExtendedRequestProtocolOp extendedRequest =
           new ExtendedRequestProtocolOp(OID_START_TLS_REQUEST);
      message = new LDAPMessage(2, extendedRequest);
      w.writeMessage(message);

      assertNull(r.readMessage());
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 add request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectAddControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ArrayList<RawAttribute> addAttrs = new ArrayList<>();
      addAttrs.add(RawAttribute.create("objectClass", "organizationalUnit"));
      addAttrs.add(RawAttribute.create("ou", "People"));

      AddRequestProtocolOp addRequest =
           new AddRequestProtocolOp(ByteString.valueOfUtf8("ou=People,o=test"), addAttrs);
      ArrayList<Control> controls = new ArrayList<>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, addRequest, controls);
      w.writeMessage(message);

      message = r.readMessage();
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertEquals(addResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 bind request if it
   * contains any controls and LDAPv2 binds are allowed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectBindControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      ArrayList<Control> controls = new ArrayList<>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      LDAPMessage message = new LDAPMessage(1, bindRequest, controls);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 compare request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectCompareControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      CompareRequestProtocolOp compareRequest =
           new CompareRequestProtocolOp(ByteString.valueOfUtf8("o=test"),
                                        "o", ByteString.valueOfUtf8("test"));
      ArrayList<Control> controls = new ArrayList<>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, compareRequest, controls);
      w.writeMessage(message);

      message = r.readMessage();
      CompareResponseProtocolOp compareResponse =
           message.getCompareResponseProtocolOp();
      assertEquals(compareResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 delete request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectDeleteControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      DeleteRequestProtocolOp deleteRequest =
           new DeleteRequestProtocolOp(ByteString.valueOfUtf8("o=test"));
      ArrayList<Control> controls = new ArrayList<>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, deleteRequest, controls);
      w.writeMessage(message);

      message = r.readMessage();
      DeleteResponseProtocolOp deleteResponse =
           message.getDeleteResponseProtocolOp();
      assertEquals(deleteResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 modify request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectModifyControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ArrayList<RawModification> mods = new ArrayList<>();
      mods.add(RawModification.create(ModificationType.REPLACE, "description", "foo"));

      ModifyRequestProtocolOp modifyRequest =
           new ModifyRequestProtocolOp(ByteString.valueOfUtf8("o=test"), mods);
      ArrayList<Control> controls = new ArrayList<>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, modifyRequest, controls);
      w.writeMessage(message);

      message = r.readMessage();
      ModifyResponseProtocolOp modifyResponse =
           message.getModifyResponseProtocolOp();
      assertEquals(modifyResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 modify DN request if
   * it contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectModifyDNControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ModifyDNRequestProtocolOp modifyDNRequest =
           new ModifyDNRequestProtocolOp(ByteString.valueOfUtf8("o=test"),
                                         ByteString.valueOfUtf8("cn=test"), false);
      ArrayList<Control> controls = new ArrayList<>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, modifyDNRequest, controls);
      w.writeMessage(message);

      message = r.readMessage();
      ModifyDNResponseProtocolOp modifyDNResponse =
           message.getModifyDNResponseProtocolOp();
      assertEquals(modifyDNResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 search request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRejectSearchControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOfUtf8("cn=Directory Manager"), 2,
                    ByteString.valueOfUtf8("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      SearchRequestProtocolOp searchRequest =
           new SearchRequestProtocolOp(ByteString.empty(),
                    SearchScope.BASE_OBJECT,
                    DereferenceAliasesPolicy.NEVER, 0, 0, false,
                    LDAPFilter.objectClassPresent(), null);
      ArrayList<Control> controls = new ArrayList<>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, searchRequest, controls);
      w.writeMessage(message);

      message = r.readMessage();
      SearchResultDoneProtocolOp searchDone =
           message.getSearchResultDoneProtocolOp();
      assertEquals(searchDone.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      StaticUtils.close(r, w);
      StaticUtils.close(s);
    }
  }
}

