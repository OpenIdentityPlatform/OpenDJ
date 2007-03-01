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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.tools.LDAPAuthenticationHandler;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



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
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the use of the Who Am I? extended operation with an internal
   * connection authenticated as a root user.
   */
  @Test()
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
   */
  @Test()
  public void testAsInternalAnonymous()
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
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
  @Test()
  public void testAsInternalNormalUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
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

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOp = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                         e.getUserAttributes(),
                                         e.getOperationalAttributes());
    assertEquals(addOp.getResultCode(), ResultCode.SUCCESS);


    conn = new InternalClientConnection(new AuthenticationInfo(e, false));
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
  @Test()
  public void testAsLDAPRootUser()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader reader = new ASN1Reader(s);
    ASN1Writer writer = new ASN1Writer(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    authHandler.doSimpleBind(3, new ASN1OctetString("cn=Directory Manager"),
                             new ASN1OctetString("password"),
                             new ArrayList<LDAPControl>(),
                             new ArrayList<LDAPControl>());
    ASN1OctetString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
    writer.writeElement(unbindMessage.encode());
    s.close();
  }



  /**
   * Tests the use of the Who Am I? extended operation with an unauthenticated
   * LDAP connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAsLDAPAnonymous()
         throws Exception
  {
    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader reader = new ASN1Reader(s);
    ASN1Writer writer = new ASN1Writer(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    ASN1OctetString authzID = authHandler.requestAuthorizationIdentity();
    assertNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
    writer.writeElement(unbindMessage.encode());
    s.close();
  }



  /**
   * Tests the use of the Who Am I? extended operation with an LDAP connection
   * authenticated as a normal user.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAsLDAPNormalUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    Entry e = TestCaseUtils.makeEntry(
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

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOp = conn.processAdd(e.getDN(), e.getObjectClasses(),
                                         e.getUserAttributes(),
                                         e.getOperationalAttributes());
    assertEquals(addOp.getResultCode(), ResultCode.SUCCESS);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader reader = new ASN1Reader(s);
    ASN1Writer writer = new ASN1Writer(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler =
         new LDAPAuthenticationHandler(reader, writer, "localhost",
                                       nextMessageID);
    authHandler.doSimpleBind(3, new ASN1OctetString("uid=test.user,o=test"),
                             new ASN1OctetString("password"),
                             new ArrayList<LDAPControl>(),
                             new ArrayList<LDAPControl>());
    ASN1OctetString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
    writer.writeElement(unbindMessage.encode());
    s.close();
  }
}

