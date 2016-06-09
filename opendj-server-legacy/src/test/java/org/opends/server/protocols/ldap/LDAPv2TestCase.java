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
 * Portions Copyright 2010-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap ;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.controls.GenericControl.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.opends.server.TestCaseUtils;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password", LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "changetype: modify",
        "replace: ds-cfg-allow-ldap-v2",
        "ds-cfg-allow-ldap-v2: true");
    }
  }



  /**
   * Tests to ensure that the server will reject an extended request from an
   * LDAPv2 client.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = EOFException.class)
  public void testRejectExtendedRequest() throws Exception
  {
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");
      conn.writeMessage(new ExtendedRequestProtocolOp(OID_START_TLS_REQUEST));
      conn.readMessage();
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");

      AddRequest addRequest = Requests.newAddRequest("ou=People,o=test")
          .addAttribute("objectClass", "organizationalUnit")
          .addAttribute("ou", "People")
          .addControl(newControl(OID_MANAGE_DSAIT_CONTROL, true));

      LDAPMessage message = conn.add(addRequest, false);
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertEquals(addResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
  }

  private void bindLdapV2(RemoteConnection conn, String bindDN, String bindPwd) throws IOException, LDAPException
  {
    bindLdapV2(conn, bindDN, bindPwd, LDAPResultCode.SUCCESS);
  }

  private void bindLdapV2(RemoteConnection conn, String bindDN, String bindPwd, int expectedRC, Control... controls)
      throws IOException, LDAPException
  {
    conn.writeMessage(new BindRequestProtocolOp(ByteString.valueOfUtf8(bindDN), 2, ByteString.valueOfUtf8(bindPwd)),
        Arrays.asList(controls));

    LDAPMessage message = conn.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), expectedRC);
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password",
          LDAPResultCode.PROTOCOL_ERROR, new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");

      CompareRequest compareRequest = newCompareRequest("o=test", "o", "test")
          .addControl(newControl(OID_MANAGE_DSAIT_CONTROL, true));
      LDAPMessage message = conn.compare(compareRequest, false);
      CompareResponseProtocolOp compareResponse = message.getCompareResponseProtocolOp();
      assertEquals(compareResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");

      DeleteRequest deleteRequest = newDeleteRequest("o=test")
          .addControl(newControl(OID_MANAGE_DSAIT_CONTROL, true));
      LDAPMessage message = conn.delete(deleteRequest, false);
      DeleteResponseProtocolOp deleteResponse = message.getDeleteResponseProtocolOp();
      assertEquals(deleteResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");

      ModifyRequest modifyRequest = newModifyRequest("o=test")
          .addModification(REPLACE, "description", "foo")
          .addControl(newControl(OID_MANAGE_DSAIT_CONTROL, true));
      LDAPMessage message = conn.modify(modifyRequest, false);
      ModifyResponseProtocolOp modifyResponse = message.getModifyResponseProtocolOp();
      assertEquals(modifyResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");

      ModifyDNRequest modifyDNRequest = newModifyDNRequest("o=test", "cn=test")
          .addControl(newControl(OID_MANAGE_DSAIT_CONTROL, true));
      LDAPMessage message = conn.modifyDN(modifyDNRequest, false);
      ModifyDNResponseProtocolOp modifyDNResponse = message.getModifyDNResponseProtocolOp();
      assertEquals(modifyDNResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
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
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");

      SearchRequest searchRequest = newSearchRequest(DN.rootDN(), BASE_OBJECT, Filter.objectClassPresent())
          .addControl(newControl(OID_MANAGE_DSAIT_CONTROL, true));
      conn.search(searchRequest);
      LDAPMessage message = conn.readMessage();
      SearchResultDoneProtocolOp searchDone = message.getSearchResultDoneProtocolOp();
      assertEquals(searchDone.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
  }
}
