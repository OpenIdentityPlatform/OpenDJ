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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.UnsupportedEncodingException;
import java.io.IOException;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.DN;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ResultCode;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.tools.*;
import static org.testng.Assert.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * A set of testcases for configuration attribute
 * "ds-cfg-reject-unauthenticated-requests".
 */

public class RejectUnauthReqTests extends CoreTestCase
{

  /**
   * Utility method which is called by the testcase sending an ADD
   * request.
   *
   * @param authentication
   *          The flag to set the authentication on and off.
   * @return The error code of operation performed.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  private int performAddOperation(boolean authentication) throws Exception
  {
    String filePath = TestCaseUtils.createTempFile(
        "dn: o=rejectTestCase,o=test", "objectclass: top",
        "objectclass: organization", "o: rejectTestCase",
        "description: Reject Test Case");
    String[] args = null;
    if (authentication)
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D",
          "cn=directory manager",
          "-w",
          "password",
          "-a",
          "-f",
          filePath,
      };
    else
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-a",
          "-f",
          filePath,
      };
    return LDAPModify.mainModify(args, false, null, null);
  }



  /**
   * Utility method which is called by the testcase sending a MODIFY
   * request.
   *
   * @param authentication
   *          The flag to set the authentication on and off.
   * @return The error code of operation performed.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  private int performModifyOperation(boolean authentication) throws Exception
  {
    String path = TestCaseUtils.createTempFile("dn: o=rejectTestCase,o=test",
        "changetype: modify", "replace: description",
        "description: New Description");
    String[] args = null;
    if (authentication)
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D",
          "cn=directory manager",
          "-w",
          "password",
          "-f",
          path
      };
    else
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-f",
          path
      };
    return LDAPModify.mainModify(args, false, null, null);
  }



  /**
   * Utility method which is called by the testcase sending a COMPARE
   * request.
   *
   * @param authentication
   *          The flag to set the authentication on and off.
   * @return The error code of operation performed.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  private int performCompareOperation(boolean authentication) throws Exception
  {
    String[] args = null;
    if (authentication)
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D",
          "cn=Directory Manager",
          "-w",
          "password",
          "o:test",
          "o=test"
      };
    else
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "o:test",
          "o=test"
      };

    return LDAPCompare.mainCompare(args, false, null, null);
  }



  /**
   * Utility method which is called by the testcase sending a MODRDN
   * request.
   *
   * @param authentication
   *          The flag to set the authentication on and off.
   * @return The error code of operation performed.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  private int performModRdnOperation(boolean authentication) throws Exception
  {
    String path = TestCaseUtils
        .createTempFile("dn: o=rejectTestCase,o=Test", "changetype: modrdn",
            "newrdn: o=mod_rejectTestCase", "deleteoldrdn: 0");
    String[] args = null;
    if (authentication)
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D",
          "cn=directory manager",
          "-w",
          "password",
          "-f",
          path
      };
    else
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-f",
          path
      };
    return LDAPModify.mainModify(args, false, null, null);
  }



  /**
   * Utility method which is called by the testcase sending a DELETE
   * request.
   *
   * @param authentication
   *          The flag to set the authentication on and off.
   * @return The error code of operation performed.
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  private int performDeleteOperation(boolean authentication) throws Exception
  {
    String[] args = null;
    if (authentication)
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-V",
          "3",
          "-D",
          "cn=Directory Manager",
          "-w",
          "password",
          "o=mod_rejectTestCase,o=test"
      };
    else
      args = new String[]
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "o=mod_rejectTestCase,o=test"
      };
    return LDAPDelete.mainDelete(args, false, null, null);
  }



  /**
   * Ensures that the Directory Server is running before executing the
   * testcases.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
  }



  /**
   * Tests whether an authenticated SEARCH request will be allowed
   * with the default configuration settings for
   * "ds-cfg-reject-unauthenticated-requests".
   */
  @Test()
  public void testAuthSearchDefCfg()
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    String[] args =
    {
        "--noPropertiesFile",
        "-h",
        "127.0.0.1",
        "-p",
        String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D",
        "cn=Directory Manager",
        "-w",
        "password",
        "-b",
        "",
        "-s",
        "base",
        "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests whether an unauthenticated SEARCH request will be allowed
   * with the default configuration settings for
   * "ds-cfg-reject-unauthenticated-requests".
   */
  @Test()
  public void testUnauthSearchDefCfg()
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    String[] args =
    {
        "--noPropertiesFile",
        "-h",
        "127.0.0.1",
        "-p",
        String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-b",
        "",
        "-s",
        "base",
        "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests whether an authenticated BIND request will be allowed with
   * the default configuration settings for
   * "ds-cfg-reject-unauthenticated-requests" .
   */
  @Test()
  public void testAuthBindDefCfg()
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    InternalClientConnection conn = new InternalClientConnection(
        new AuthenticationInfo());
    ASN1OctetString user = new ASN1OctetString("cn=Directory Manager");
    ASN1OctetString password = new ASN1OctetString("password");
    BindOperation bindOperation = conn.processSimpleBind(user, password);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests whether an Unauthenticated BIND request will be allowed
   * with the default configuration settings for
   * "ds-cfg-reject-unauthenticated-requests".
   */
  @Test()
  public void testUnauthBindDefCfg()
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    InternalClientConnection conn = new InternalClientConnection(
        new AuthenticationInfo());
    BindOperation bindOperation = conn.processSimpleBind(DN.nullDN(), null);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests whether the Who Am I? extended operation with an internal
   * authenticated connection succeeds with default setting of
   * "ds-cfg-reject-unauthenticated-requests".
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testAuthWAIDefCfg() throws Exception
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler = new LDAPAuthenticationHandler(
        reader, writer, "localhost", nextMessageID);
    authHandler.doSimpleBind(3, new ASN1OctetString("cn=Directory Manager"),
        new ASN1OctetString("password"), new ArrayList<LDAPControl>(),
        new ArrayList<LDAPControl>());
    ASN1OctetString authzID = authHandler.requestAuthorizationIdentity();
    assertNotNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(
        nextMessageID.getAndIncrement(), new UnbindRequestProtocolOp());
    writer.writeMessage(unbindMessage);
    s.close();
  }



  /**
   * Tests whether the who am I? extended operation with an internal
   * unauthenticated connection succeeds with default setting of
   * "ds-cfg-reject-unauthenticated-requests".
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testUnauthWAIDefCfg() throws Exception
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    LDAPReader reader = new LDAPReader(s);
    LDAPWriter writer = new LDAPWriter(s);

    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPAuthenticationHandler authHandler = new LDAPAuthenticationHandler(
        reader, writer, "localhost", nextMessageID);
    ASN1OctetString authzID = authHandler.requestAuthorizationIdentity();
    assertNull(authzID);

    LDAPMessage unbindMessage = new LDAPMessage(
        nextMessageID.getAndIncrement(), new UnbindRequestProtocolOp());
    writer.writeMessage(unbindMessage);
    s.close();
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate
   * with the server in conjunction with no authentication and using
   * blind trust with the default configuration settings.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testStartTLSUnauthDefCfg() throws Exception
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    String[] argSearch =
    {
        "--noPropertiesFile",
        "-h",
        "127.0.0.1",
        "-p",
        String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D",
        "cn=directory manager",
        "-w",
        "password",
        "-q",
        "-X",
        "-b",
        "",
        "-s",
        "base",
        "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(argSearch, false, null, System.err), 0);
  }



  /**
   * Tests the whether the authenticated ADD,MODIFY,COMPARE,MODRDN and
   * DELETE requests succeed with the default configuration settings.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test()
  public void testOtherOpsAuthDefCfg() throws Exception
  {
    DirectoryServer.setRejectUnauthenticatedRequests(false);

    assertEquals(performAddOperation(true), 0);

    assertEquals(performModifyOperation(true), 0);

    assertEquals(performCompareOperation(true), 0);

    assertEquals(performModRdnOperation(true), 0);

    assertEquals(performDeleteOperation(true), 0);
  }



  /**
   * Tests the whether the unauthenticated ADD,MODIFY,COMPARE,MODRDN
   * and DELETE requests succeed with the default configuration
   * settings. FIXME: This test is disabled because it is unreasonable
   * to expect unauthenticated writes to succeed when access control
   * is enabled.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test(enabled = false)
  public void testOtherOpsUnauthDefCfg() throws Exception
  {
    assertEquals(performAddOperation(false), 0);

    assertEquals(performModifyOperation(false), 0);

    assertEquals(performCompareOperation(false), 0);

    assertEquals(performModRdnOperation(false), 0);

    assertEquals(performDeleteOperation(false), 0);
  }



  /**
   * Tests whether both authenticated and unauthenticated SEARCH
   * requests will be allowed with the new configuration settings for
   * "ds-cfg-reject-unauthenticated-requests" .
   */
  @Test
  public void testSearchNewCfg()
  {
    try
    {
      DirectoryServer.setRejectUnauthenticatedRequests(true);

      String[] args =
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-b",
          "",
          "-s",
          "base",
          "(objectClass=*)"
      };

      assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);

      String[] authArgs =
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D",
          "cn=Directory Manager",
          "-w",
          "password",
          "-b",
          "",
          "-s",
          "base",
          "(objectClass=*)"
      };
      assertEquals(LDAPSearch.mainSearch(authArgs, false, null, System.err), 0);
    }
    finally
    {
      DirectoryServer.setRejectUnauthenticatedRequests(false);
    }
  }



  /**
   * Tests whether authenticated and unauthenticated BIND requests
   * will be allowed with the new configuration settings for
   * "ds-cfg-reject-unauthenticated-requests" .
   */
  @Test
  public void testBindNewCfg()
  {
    try
    {
      DirectoryServer.setRejectUnauthenticatedRequests(true);

      InternalClientConnection conn = new InternalClientConnection(
          new AuthenticationInfo());
      ASN1OctetString user = new ASN1OctetString("cn=Directory Manager");
      ASN1OctetString password = new ASN1OctetString("password");
      // Unauthenticated BIND request.
      BindOperation bindOperation = conn.processSimpleBind(DN.nullDN(), null);
      assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
      // Authenticated BIND request.
      bindOperation = conn.processSimpleBind(user, password);
      assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
    }
    finally
    {
      DirectoryServer.setRejectUnauthenticatedRequests(false);
    }
  }



  /**
   * Tests the use of the StartTLS extended operation to communicate
   * with the server in conjunction with no authentication and using
   * blind trust.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testStartTLSNoAuthTrustAll() throws Exception
  {
    try
    {
      DirectoryServer.setRejectUnauthenticatedRequests(true);

      String[] argSearch =
      {
          "--noPropertiesFile",
          "-h",
          "127.0.0.1",
          "-p",
          String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D",
          "cn=directory manager",
          "-w",
          "password",
          "-q",
          "-X",
          "-b",
          "",
          "-s",
          "base",
          "(objectClass=*)"
      };
      assertEquals(LDAPSearch.mainSearch(argSearch, false, null, System.err), 0);
    }
    finally
    {
      DirectoryServer.setRejectUnauthenticatedRequests(false);
    }
  }



  /**
   * Tests whether the Who Am I? extended operation with an internal
   * authenticated connection succeeds with new setting of
   * "ds-cfg-reject-unauthenticated-requests".
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testAuthWAINewCfg() throws Exception
  {
    try
    {
      DirectoryServer.setRejectUnauthenticatedRequests(true);

      InternalClientConnection conn = InternalClientConnection
          .getRootConnection();
      ExtendedOperation extOp = conn.processExtendedOperation(
          OID_WHO_AM_I_REQUEST, null);
      assertEquals(extOp.getResultCode(), ResultCode.SUCCESS);
      assertNotNull(extOp.getResponseValue());
    }
    finally
    {
      DirectoryServer.setRejectUnauthenticatedRequests(false);
    }
  }



  /**
   * Tests whether the who am I? extended operation with an
   * unauthenticated connection fails with new setting of
   * "ds-cfg-reject-unauthenticated-requests".
   *
   * @throws UnsupportedEncodingException
   *           If an unexpected problem occurs.
   * @throws IOException
   *           If an unexpected problem occurs.
   * @throws ClientException
   *           If an unexpected problem occurs.
   */
  @Test
  public void testUnauthWAINewCfg() throws UnsupportedEncodingException,
      IOException, ClientException
  {
    try
    {
      DirectoryServer.setRejectUnauthenticatedRequests(true);

      Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      LDAPReader reader = new LDAPReader(s);
      LDAPWriter writer = new LDAPWriter(s);
      AtomicInteger nextMessageID = new AtomicInteger(1);
      LDAPAuthenticationHandler authHandler = new LDAPAuthenticationHandler(
          reader, writer, "localhost", nextMessageID);
      ASN1OctetString authzID = null;
      try
      {
        authzID = authHandler.requestAuthorizationIdentity();
      }
      catch (LDAPException e)
      {
        assertNull(authzID);
      }
      finally
      {
        LDAPMessage unbindMessage = new LDAPMessage(nextMessageID
            .getAndIncrement(), new UnbindRequestProtocolOp());
        writer.writeMessage(unbindMessage);
        s.close();
      }
    }
    finally
    {
      DirectoryServer.setRejectUnauthenticatedRequests(false);
    }
  }



  /**
   * Tests the whether the authenticated ADD,MODIFY,COMPARE,MODRDN and
   * DELETE requests succeed with the new configuration settings.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testOtherOpsAuthNewCfg() throws Exception
  {
    try
    {
      DirectoryServer.setRejectUnauthenticatedRequests(true);

      assertEquals(performAddOperation(true), 0);

      assertEquals(performModifyOperation(true), 0);

      assertEquals(performCompareOperation(true), 0);

      assertEquals(performModRdnOperation(true), 0);

      assertEquals(performDeleteOperation(true), 0);
    }
    finally
    {
      DirectoryServer.setRejectUnauthenticatedRequests(false);
    }
  }



  /**
   * Tests the whether the unauthenticated ADD,MODIFY,COMPARE,MODRDN
   * and DELETE requests fail with the new configuration settings.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testOtherOpsUnauthNewCfg() throws Exception
  {
    try
    {
      DirectoryServer.setRejectUnauthenticatedRequests(true);

      assertFalse(performAddOperation(false) == 0);

      assertFalse(performModifyOperation(false) == 0);

      assertFalse(performCompareOperation(false) == 0);

      assertFalse(performModRdnOperation(false) == 0);

      assertFalse(performDeleteOperation(false) == 0);
    }
    finally
    {
      DirectoryServer.setRejectUnauthenticatedRequests(false);
    }
  }
}
