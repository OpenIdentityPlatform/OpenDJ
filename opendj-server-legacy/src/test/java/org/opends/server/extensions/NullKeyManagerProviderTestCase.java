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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.extensions;



import static org.testng.Assert.assertNotNull;

import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * A set of test cases for the null key manager provider.
 */
public class NullKeyManagerProviderTestCase
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
   * Tests the null key manager provider by creating a new instance,
   * initializing it, and getting the key managers.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testNullKeyManagerProvider()
         throws Exception
  {
    NullKeyManagerProvider provider = new NullKeyManagerProvider();
    provider.initializeKeyManagerProvider(null);
    assertNotNull(provider.getKeyManagers());
    provider.finalizeKeyManagerProvider();
  }
}

