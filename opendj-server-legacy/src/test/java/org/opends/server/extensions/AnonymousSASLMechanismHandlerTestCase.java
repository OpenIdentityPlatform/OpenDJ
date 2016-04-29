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

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.BindOperationBasis;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.Control;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/** A set of test cases for the ANONYMOUS SASL mechanism handler. */
public class AnonymousSASLMechanismHandlerTestCase
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
   * Tests the process of initializing and finalizing the ANONYMOUS SASL
   * mechanism handler.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
  @Test
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
  @Test
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
  @Test
  public void testProcessSASLBindNoCreds()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    BindOperationBasis bindOperation =
         new BindOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
                           new ArrayList<Control>(), "3", DN.rootDN(),
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
  @Test
  public void testProcessSASLBindEmptyCreds()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    BindOperationBasis bindOperation =
         new BindOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
                           new ArrayList<Control>(), "3", DN.rootDN(),
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
  @Test
  public void testProcessSASLBindWithTraceInfo()
         throws Exception
  {
    AnonymousSASLMechanismHandler handler = new AnonymousSASLMechanismHandler();
    handler.initializeSASLMechanismHandler(null);

    BindOperationBasis bindOperation =
         new BindOperationBasis(getRootConnection(), nextOperationID(), nextMessageID(),
                           new ArrayList<Control>(), "3", DN.rootDN(),
                           SASL_MECHANISM_ANONYMOUS,
                           ByteString.valueOfUtf8("Internal Trace String"));
    handler.processSASLBind(bindOperation);
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);

    handler.finalizeSASLMechanismHandler();
  }

  /**
   * Performs a SASL ANONYMOUS bind over LDAP with no credentials.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
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
  @Test
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
