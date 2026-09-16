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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.tools;

import static org.opends.server.TestCaseUtils.withoutJceService;
import static org.testng.Assert.*;

import java.security.GeneralSecurityException;

import org.testng.annotations.Test;

/** Tests the setup-time configuration done by {@link ConfigureDS}. */
@SuppressWarnings("javadoc")
public class ConfigureDSTestCase extends ToolsTestCase
{
  private static final String DEFAULT_KEY_WRAPPING_TRANSFORMATION = "RSA/ECB/OAEPWITHSHA-1ANDMGF1PADDING";

  /** A runtime which has the default transformation keeps it. */
  @Test
  public void testKeyWrappingTransformationStaysTheDefaultWhereTheRuntimeHasIt() throws Exception
  {
    assertEquals(ConfigureDS.supportedKeyWrappingTransformation(DEFAULT_KEY_WRAPPING_TRANSFORMATION),
        DEFAULT_KEY_WRAPPING_TRANSFORMATION);
  }

  /**
   * A runtime without RSA-OAEP under either spelling gets no transformation at all, rather than
   * a weaker one (#776): setup is to say so, and the administrator is to choose.
   */
  @Test
  public void testNoKeyWrappingTransformationIsChosenWhereTheRuntimeHasNoRsaOaep() throws Exception
  {
    withoutJceService("Cipher", "RSA", () ->
    {
      try
      {
        final String chosen = ConfigureDS.supportedKeyWrappingTransformation(DEFAULT_KEY_WRAPPING_TRANSFORMATION);
        fail("a transformation was chosen on a runtime without RSA-OAEP: " + chosen);
      }
      catch (GeneralSecurityException expected)
      {
        assertTrue(expected.getMessage().contains(DEFAULT_KEY_WRAPPING_TRANSFORMATION), expected.getMessage());
      }
      return null;
    });
  }
}
