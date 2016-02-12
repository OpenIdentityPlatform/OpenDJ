/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.testng.Assert.*;

import java.io.IOException;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.LDAPException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** A set of test cases that involve disconnecting clients due to the idle time limit. */
public class IdleTimeLimitTestCase
       extends CoreTestCase
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

    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      readNoticeOfDisconnectionMessage(conn);
    }
    finally
    {
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


    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      readNoticeOfDisconnectionMessage(conn);
    }
    finally
    {
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


    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      conn.bind("uid=test.user,o=test", "password");

      readNoticeOfDisconnectionMessage(conn);
    }
  }

  private void readNoticeOfDisconnectionMessage(RemoteConnection conn) throws IOException, LDAPException
  {
    ExtendedResponseProtocolOp extendedResponse = conn.readMessage().getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getOID(), LDAPConstants.OID_NOTICE_OF_DISCONNECTION);

    assertNull(conn.readMessage());
  }
}
