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
import java.util.concurrent.locks.Lock;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockType;

import static org.testng.Assert.*;



/**
 * A set of test cases for the internal connection security provider.
 */
public class InternalConnectionSecurityProviderTestCase
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
   * Tests the default constructor for this class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConstructor()
         throws Exception
  {
    InternalConnectionSecurityProvider provider =
         new InternalConnectionSecurityProvider();
    provider.initializeConnectionSecurityProvider(null);
    provider.finalizeConnectionSecurityProvider();
  }



  /**
   * Tests the <CODE>getSecurityMechanismName</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetSecurityMechanismName()
         throws Exception
  {
    InternalConnectionSecurityProvider provider =
         new InternalConnectionSecurityProvider();
    provider.initializeConnectionSecurityProvider(null);

    assertNotNull(provider.getSecurityMechanismName());

    provider.finalizeConnectionSecurityProvider();
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
    InternalConnectionSecurityProvider provider =
         new InternalConnectionSecurityProvider();
    provider.initializeConnectionSecurityProvider(null);

    assertTrue(provider.isSecure());

    provider.finalizeConnectionSecurityProvider();
  }
}

