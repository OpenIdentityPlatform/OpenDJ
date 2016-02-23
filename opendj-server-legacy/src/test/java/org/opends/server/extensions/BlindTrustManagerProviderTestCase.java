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



import java.security.cert.X509Certificate;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;

import static org.testng.Assert.*;



/**
 * A set of test cases for the blind trust manager provider.
 */
public class BlindTrustManagerProviderTestCase
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
   * Tests the blind trust manager provider by creating a new instance,
   * initializing it, and getting the trust managers and issuers.  In this case,
   * since we know that all certificates will always be trusted then we can also
   * invoke the checkClientTrusted and checkServerTrusted methods with empty
   * certificate chains.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testBlindTrustManagerProvider()
         throws Exception
  {
    BlindTrustManagerProvider provider = new BlindTrustManagerProvider();
    provider.initializeTrustManagerProvider(null);
    assertNotNull(provider.getTrustManagers());
    assertNotNull(provider.getAcceptedIssuers());

    provider.checkClientTrusted(new X509Certificate[0], "");
    provider.checkServerTrusted(new X509Certificate[0], "");

    provider.finalizeTrustManagerProvider();
  }
}

