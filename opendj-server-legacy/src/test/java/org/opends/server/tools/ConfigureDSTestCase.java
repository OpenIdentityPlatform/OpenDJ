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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;

import javax.crypto.Cipher;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tests the setup-time configuration done by {@link ConfigureDS}. */
@SuppressWarnings("javadoc")
public class ConfigureDSTestCase extends ToolsTestCase
{
  private static final String DEFAULT_KEY_WRAPPING_TRANSFORMATION = "RSA/ECB/OAEPWITHSHA-1ANDMGF1PADDING";

  /** The tool locates the server lock file when it is created, from the server environment. */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /** A runtime which has the default transformation keeps it. */
  @Test
  public void testKeyWrappingTransformationStaysTheDefaultWhereTheRuntimeHasIt() throws Exception
  {
    assertEquals(ConfigureDS.supportedKeyWrappingTransformation(DEFAULT_KEY_WRAPPING_TRANSFORMATION),
        DEFAULT_KEY_WRAPPING_TRANSFORMATION);
  }

  /**
   * A runtime without RSA-OAEP under either spelling gets no transformation at all, rather than
   * a weaker one: setup is to say so, and the administrator is to choose.
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

  /**
   * A runtime whose only RSA cipher is PKCS#1 v1.5, as a SunPKCS11 provider on its own, does not
   * get that transformation as the fallback either (#776). Withdrawing {@code Cipher.RSA} alone
   * cannot show it, since the PKCS#1 v1.5 transformation goes through that service as well.
   */
  @Test
  public void testNoKeyWrappingTransformationIsChosenWhereTheRuntimeHasOnlyPkcs1() throws Exception
  {
    withoutJceService("Cipher", "RSA", () ->
    {
      final Provider pkcs1Only = new Provider("Pkcs1OnlyRsa", "1.0", "RSA with PKCS#1 v1.5 padding only") {};
      pkcs1Only.put("Cipher.RSA/ECB/PKCS1Padding", "com.sun.crypto.provider.RSACipher");
      Security.insertProviderAt(pkcs1Only, 1);
      try
      {
        assertEquals(Cipher.getInstance("RSA/ECB/PKCS1Padding").getProvider().getName(), pkcs1Only.getName(),
            "the fixture offers no PKCS#1 v1.5 transformation");
        try
        {
          final String chosen = ConfigureDS.supportedKeyWrappingTransformation(DEFAULT_KEY_WRAPPING_TRANSFORMATION);
          fail("a transformation was chosen on a runtime whose only RSA cipher is PKCS#1 v1.5: " + chosen);
        }
        catch (GeneralSecurityException expected)
        {
          assertTrue(expected.getMessage().contains(DEFAULT_KEY_WRAPPING_TRANSFORMATION), expected.getMessage());
        }
      }
      finally
      {
        Security.removeProvider(pkcs1Only.getName());
      }
      return null;
    });
  }

  /** Where the runtime has the default transformation, setup has nothing to warn about. */
  @Test
  public void testNoWarningWhereTheRuntimeHasTheDefaultKeyWrappingTransformation() throws Exception
  {
    assertNull(ConfigureDS.unsupportedKeyWrappingTransformationWarning());
    assertEquals(updateCryptoCipher(), "");
  }

  /**
   * Where the runtime has no RSA-OAEP, the warning names the default transformation and the
   * property to set, both the one the installer gives and the one configure-ds writes; and the
   * configuration keeps the default (the configuration handler is not even there to change it).
   */
  @Test
  public void testWarningNamesTheTransformationAndThePropertyWhereTheRuntimeHasNoRsaOaep() throws Exception
  {
    withoutJceService("Cipher", "RSA", () ->
    {
      final LocalizableMessage warning = ConfigureDS.unsupportedKeyWrappingTransformationWarning();
      assertNotNull(warning, "no warning on a runtime without RSA-OAEP");
      assertNamesTransformationAndProperty(warning.toString());

      assertNamesTransformationAndProperty(updateCryptoCipher());
      return null;
    });
  }

  private static void assertNamesTransformationAndProperty(final String warning)
  {
    final String text = warning.replaceAll("\\s+", " ");
    assertTrue(text.startsWith("This Java runtime supports neither the default key wrapping transformation "
        + DEFAULT_KEY_WRAPPING_TRANSFORMATION + " "), text);
    assertTrue(text.contains("the key-wrapping-transformation property"), text);
  }

  /**
   * Runs the key wrapping step of configure-ds on its own, and returns what it wrote to its error
   * stream. The tool has no configuration handler here, so a step which tried to change the
   * configuration would fail.
   */
  private static String updateCryptoCipher() throws Exception
  {
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
    final Constructor<ConfigureDS> constructor =
        ConfigureDS.class.getDeclaredConstructor(String[].class, OutputStream.class, OutputStream.class);
    constructor.setAccessible(true);
    final ConfigureDS tool = constructor.newInstance(new String[0], new ByteArrayOutputStream(), err);
    final Method updateCryptoCipher = ConfigureDS.class.getDeclaredMethod("updateCryptoCipher");
    updateCryptoCipher.setAccessible(true);
    updateCryptoCipher.invoke(tool);
    return new String(err.toByteArray(), StandardCharsets.UTF_8);
  }
}
