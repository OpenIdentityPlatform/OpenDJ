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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.LDAPAuthenticationHandler;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.ClientException;

import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the "Who Am I?" extended operation.
 */
public class WhoAmIExtendedOperationTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
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
   * Tests the use of the Who Am I? extended operation with an internal
   * connection authenticated as a root user.
   */
  @Test
  public void testAsInternalRootUser()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_WHO_AM_I_REQUEST, null);
    assertEquals(extOp.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(extOp.getResponseValue());
  }



  /**
   * Tests the use of the Who Am I? extended operation with an internal
   * unauthenticated connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsInternalAnonymous()
         throws Exception
  {
    InternalClientConnection conn = new InternalClientConnection(DN.rootDN());
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_WHO_AM_I_REQUEST, null);
    assertEquals(extOp.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(extOp.getResponseValue());
  }



  /**
   * Tests the use of the Who Am I? extended operation with an internal
   * connection authenticated as a normal user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsInternalNormalUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry e = TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");


    InternalClientConnection conn = new InternalClientConnection(new AuthenticationInfo(e, false));
    ExtendedOperation extOp =
         conn.processExtendedOperation(OID_WHO_AM_I_REQUEST, null);
    assertEquals(extOp.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(extOp.getResponseValue());
  }



  /**
   * Tests the use of the Who Am I? extended operation with an LDAP connection
   * authenticated as a root user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsLDAPRootUser()
         throws Exception
  {
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      LDAPAuthenticationHandler authHandler = conn.newLDAPAuthenticationHandler();
      doSimpleBind(authHandler, "cn=Directory Manager", "password");
      assertNotNull(authHandler.requestAuthorizationIdentity());

      conn.unbind();
    }
  }

  /**
   * Tests the use of the Who Am I? extended operation with an unauthenticated
   * LDAP connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsLDAPAnonymous()
         throws Exception
  {
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      LDAPAuthenticationHandler authHandler = conn.newLDAPAuthenticationHandler();
      assertNull(authHandler.requestAuthorizationIdentity());
      conn.unbind();
    }
  }



  /**
   * Tests the use of the Who Am I? extended operation with an LDAP connection
   * authenticated as a normal user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAsLDAPNormalUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");


    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      LDAPAuthenticationHandler authHandler = conn.newLDAPAuthenticationHandler();

      doSimpleBind(authHandler, "uid=test.user,o=test", "password");
      assertNotNull(authHandler.requestAuthorizationIdentity());

      conn.unbind();
    }
  }



  /**
   * Tests the use of the "Who Am I?" extended operation when used by a client
   * that has authenticated using a SASL mechanism and specified an alternate
   * authorization identity.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithAlternateSASLAuthzID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "",
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

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      LDAPAuthenticationHandler authHandler = conn.newLDAPAuthenticationHandler();

      // Bind as the proxy user with an alternate authorization identity, and use
      // the "Who Am I?" operation.
      HashMap<String,List<String>> saslProperties = new HashMap<>(2);
      saslProperties.put("authID", newArrayList("dn:uid=proxy.user,o=test"));
      saslProperties.put("authzID", newArrayList("dn:uid=test.user,o=test"));

      authHandler.doSASLPlain(ByteString.empty(),
          ByteString.valueOfUtf8("password"), saslProperties,
          new ArrayList<Control>(),
          new ArrayList<Control>());
      assertAuthzID(authHandler.requestAuthorizationIdentity(), "dn:uid=test.user,o=test");

      conn.unbind();
    }
  }



  /**
   * Tests the use of the Who Am I? extended operation in conjunction with the
   * proxied authorization control by an appropriately authorized user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithAllowedProxiedAuthControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "",
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

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      LDAPAuthenticationHandler authHandler = conn.newLDAPAuthenticationHandler();

      // Bind as the proxy user and use the "Who Am I?" operation, but without the
      // proxied auth control.
      doSimpleBind(authHandler, "uid=proxy.user,o=test", "password");
      assertAuthzID(authHandler.requestAuthorizationIdentity(), "dn:uid=proxy.user,o=test");

      // Use the "Who Am I?" operation again, this time with the proxy control.
      conn.writeMessage(
          new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST),
          new ProxiedAuthV2Control(ByteString.valueOfUtf8("dn:uid=test.user,o=test")));

      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
      assertAuthzID(extendedResponse.getValue(), "dn:uid=test.user,o=test");

      conn.unbind();
    }
  }

  private void assertAuthzID(ByteString authzID, String expected)
  {
    assertNotNull(authzID);
    assertEquals(authzID.toString(), expected);
  }

  /**
   * Tests the use of the Who Am I? extended operation in conjunction with the
   * proxied authorization control by a user who doesn't have the rights to use
   * that control.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testWithDisallowedProxiedAuthControl()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntries(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "",
         "dn: uid=cantproxy.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: proxy.user",
         "givenName: Cantproxy",
         "sn: User",
         "cn: Cantproxy User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl");

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      LDAPAuthenticationHandler authHandler = conn.newLDAPAuthenticationHandler();

      // Bind as the proxy user and use the "Who Am I?" operation, but without the
      // proxied auth control.
      doSimpleBind(authHandler, "uid=cantproxy.user,o=test", "password");
      assertAuthzID(authHandler.requestAuthorizationIdentity(), "dn:uid=cantproxy.user,o=test");

      // Use the "Who Am I?" operation again, this time with the proxy control.
      conn.writeMessage(
          new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST),
          new ProxiedAuthV2Control(ByteString.valueOfUtf8("dn:uid=test.user,o=test")));

      LDAPMessage message = conn.readMessage();
      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.AUTHORIZATION_DENIED);
      assertNull(extendedResponse.getValue());

      conn.unbind();
    }
  }

  private void doSimpleBind(LDAPAuthenticationHandler authHandler, String bindDn, String bindPwd)
      throws ClientException, LDAPException
  {
    authHandler.doSimpleBind(3, ByteString.valueOfUtf8(bindDn), ByteString.valueOfUtf8(bindPwd),
        new ArrayList<Control>(), new ArrayList<Control>());
  }
}
