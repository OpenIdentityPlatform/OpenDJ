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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.net.Socket;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.*;
import org.opends.server.tools.dsconfig.DSConfig;

import static org.testng.Assert.*;



/**
 * A set of test cases that involve disconnecting clients due to the idle time
 * limit.
 */
public class IdleTimeLimitTestCase
       extends CoreTestCase
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
   * Tests the server-wide idle time limit for an anonymous connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups="slow")
  public void testServerWideAnonymousIdleTimeLimit()
         throws Exception
  {
    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--set", "idle-time-limit:5 seconds");


    Socket s = null;
    try
    {
      s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      ASN1Writer w = new ASN1Writer(s);
      ASN1Reader r = new ASN1Reader(s);
      r.setIOTimeout(60000);

      LDAPMessage m = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ExtendedResponseProtocolOp extendedResponse =
           m.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getOID(),
                   LDAPConstants.OID_NOTICE_OF_DISCONNECTION);

      assertNull(r.readElement());
    }
    finally
    {
      try
      {
        s.close();
      } catch (Exception e) {}

      TestCaseUtils.dsconfig(
        "set-global-configuration-prop",
        "--set", "idle-time-limit:0 seconds");
    }
  }



  /**
   * Tests the server-wide idle time limit for an authenticated connection.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups="slow")
  public void testServerWideAuthenticatedIdleTimeLimit()
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
      "userPassword: password"
    );


    TestCaseUtils.dsconfig(
      "set-global-configuration-prop",
      "--set", "idle-time-limit:5 seconds");


    Socket s = null;
    try
    {
      s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      ASN1Writer w = new ASN1Writer(s);
      ASN1Reader r = new ASN1Reader(s);
      r.setIOTimeout(60000);


      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage m = new LDAPMessage(1, bindRequest);
      w.writeElement(m.encode());


      m = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = m.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);


      m = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ExtendedResponseProtocolOp extendedResponse =
           m.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getOID(),
                   LDAPConstants.OID_NOTICE_OF_DISCONNECTION);

      assertNull(r.readElement());
    }
    finally
    {
      try
      {
        s.close();
      } catch (Exception e) {}

      TestCaseUtils.dsconfig(
        "set-global-configuration-prop",
        "--set", "idle-time-limit:0 seconds");
    }
  }



  /**
   * Tests a user-specific idle time limit.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups="slow")
  public void testUserSpecificIdleTimeLimit()
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
      "userPassword: password",
      "ds-rlim-idle-time-limit: 5"
    );


    Socket s = null;
    try
    {
      s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      ASN1Writer w = new ASN1Writer(s);
      ASN1Reader r = new ASN1Reader(s);
      r.setIOTimeout(60000);


      BindRequestProtocolOp bindRequest = new BindRequestProtocolOp(
           new ASN1OctetString("uid=test.user,o=test"), 3,
           new ASN1OctetString("password"));
      LDAPMessage m = new LDAPMessage(1, bindRequest);
      w.writeElement(m.encode());


      m = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = m.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);


      m = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ExtendedResponseProtocolOp extendedResponse =
           m.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getOID(),
                   LDAPConstants.OID_NOTICE_OF_DISCONNECTION);

      assertNull(r.readElement());
    }
    finally
    {
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }
}

