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



import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.BindOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.ByteString;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the ANONYMOUS SASL mechanism handler.
 */
public class AnonymousSASLMechanismHandlerTestCase
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
   * Tests the process of initializing and finalizing the ANONYMOUS SASL
   * mechanism handler.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInitializationAndFinalization()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);
    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>isPasswordBased</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsPasswordBased()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    assertFalse(handler.isPasswordBased(SASL_MECHANISM_ANONYMOUS));

    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>isSecure</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsSecure()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    assertFalse(handler.isSecure(SASL_MECHANISM_ANONYMOUS));

    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>processSASLBind</CODE> method with no credentials.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSASLBindNoCreds()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    BindOperationBasis bindOperation =
         new BindOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                           new ArrayList<Control>(), "3", DN.nullDN(),
                           SASL_MECHANISM_ANONYMOUS, null);
    handler.processSASLBind(bindOperation);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);

    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>processSASLBind</CODE> method with an empty set of
   * credentials.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSASLBindEmptyCreds()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    BindOperationBasis bindOperation =
         new BindOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                           new ArrayList<Control>(), "3", DN.nullDN(),
                           SASL_MECHANISM_ANONYMOUS, ByteString.empty());
    handler.processSASLBind(bindOperation);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);

    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Tests the <CODE>processSASLBind</CODE> method with trace information.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testProcessSASLBindWithTraceInfo()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    BindOperationBasis bindOperation =
         new BindOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                           new ArrayList<Control>(), "3", DN.nullDN(),
                           SASL_MECHANISM_ANONYMOUS,
                           ByteString.valueOf("Internal Trace String"));
    handler.processSASLBind(bindOperation);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);

    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Performs a SASL ANONYMOUS bind over LDAP with no credentials.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPBindNoCreds()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=ANONYMOUS",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);

    handler.finalizeSASLMechanismHandler();
  }



  /**
   * Performs a SASL ANONYMOUS bind over LDAP with trace information.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDAPBindWithTraceInfo()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=ANONYMOUS",
      "-o", "trace=LDAP Trace String",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);

    handler.finalizeSASLMechanismHandler();
  }
}

