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
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.config.ConfigConstants.CONFIG_DIR_NAME;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.testng.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.opendj.server.config.meta.GSSAPISASLMechanismHandlerCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests that a rejected GSSAPI configuration leaves no JAAS login configuration behind. */
@SuppressWarnings("javadoc")
public class GSSAPISASLMechanismHandlerTestCase extends ExtensionsTestCase
{
  private static final String JAAS_CONFIG_PROPERTY = "java.security.auth.login.config";

  /** An existing keytab, so that only the property under test is wrong. */
  private File keytab;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    keytab = File.createTempFile("gssapi-test", ".keytab");
  }

  @AfterClass
  public void deleteKeytab()
  {
    if (keytab != null)
    {
      keytab.delete();
    }
  }

  @DataProvider(name = "rejectedKdcRealm")
  public Object[][] rejectedKdcRealm()
  {
    return new Object[][] {
      { "ds-cfg-kdc-address: kdc.example.com" },
      { "ds-cfg-realm: EXAMPLE.COM" },
    };
  }

  /** Only one of the KDC address and the realm is set: startup must fail before writing the file. */
  @Test(dataProvider = "rejectedKdcRealm")
  public void testRejectedKdcRealmLeavesNoJaasFile(String kdcOrRealm) throws Exception
  {
    assertRejectedWithoutJaasFile(
        handlerEntry("GSSAPI", keytab.getAbsolutePath(), kdcOrRealm), ERR_SASLGSSAPI_KDC_REALM_NOT_DEFINED.ordinal());
  }

  /** The keytab does not exist: startup must fail before writing the file. */
  @Test
  public void testMissingKeytabLeavesNoJaasFile() throws Exception
  {
    final File missingKeytab = new File(keytab.getParentFile(), keytab.getName() + ".missing");
    assertFalse(missingKeytab.exists());

    assertRejectedWithoutJaasFile(
        handlerEntry("GSSAPI", missingKeytab.getAbsolutePath()), ERR_SASL_GSSAPI_KEYTAB_INVALID.ordinal());
  }

  /**
   * A second enabled handler would replace the first one in the server's registry, and shutdown would
   * then never delete the first one's JAAS file: it must be rejected before writing a file.
   */
  @Test
  public void testSecondHandlerIsRejectedWithoutJaasFile() throws Exception
  {
    final Set<String> filesBefore = loginConfFiles();
    final GSSAPISASLMechanismHandler first = InitializationUtils.initializeSASLMechanismHandler(
        new GSSAPISASLMechanismHandler(), handlerEntry("GSSAPI", keytab.getAbsolutePath()),
        GSSAPISASLMechanismHandlerCfgDefn.getInstance());
    try
    {
      final Set<String> filesOfFirst = loginConfFiles();
      assertEquals(filesOfFirst.size(), filesBefore.size() + 1, "The first handler must write its JAAS file");

      assertRejectedWithoutJaasFile(
          handlerEntry("GSSAPI 2", keytab.getAbsolutePath()), ERR_SASLGSSAPI_HANDLER_ALREADY_ENABLED.ordinal());
    }
    finally
    {
      first.finalizeSASLMechanismHandler();
    }
    assertEquals(loginConfFiles(), filesBefore, "Finalizing the handler must delete its JAAS file");
  }

  private void assertRejectedWithoutJaasFile(Entry e, int expectedOrdinal) throws Exception
  {
    final Set<String> filesBefore = loginConfFiles();
    final String jaasConfigBefore = System.getProperty(JAAS_CONFIG_PROPERTY);
    try
    {
      InitializationUtils.initializeSASLMechanismHandler(
          new GSSAPISASLMechanismHandler(), e, GSSAPISASLMechanismHandlerCfgDefn.getInstance());
      fail("The configuration " + e.getName() + " must be rejected");
    }
    catch (InitializationException expected)
    {
      assertEquals(expected.getMessageObject().ordinal(), expectedOrdinal, expected.getMessage());
    }

    assertEquals(loginConfFiles(), filesBefore, "A rejected configuration left a JAAS login file behind");
    assertEquals(System.getProperty(JAAS_CONFIG_PROPERTY), jaasConfigBefore,
        "A rejected configuration changed the JAAS login configuration property");
  }

  private static Entry handlerEntry(String cn, String keytabPath, String... extraLines) throws Exception
  {
    final List<String> lines = new ArrayList<>(Arrays.asList(
        "dn: cn=" + cn + ",cn=SASL Mechanisms,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-sasl-mechanism-handler",
        "objectClass: ds-cfg-gssapi-sasl-mechanism-handler",
        "cn: " + cn,
        "ds-cfg-java-class: org.opends.server.extensions.GSSAPISASLMechanismHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-identity-mapper: cn=Exact Match,cn=Identity Mappers,cn=config",
        "ds-cfg-server-fqdn: localhost",
        "ds-cfg-keytab: " + keytabPath));
    lines.addAll(Arrays.asList(extraLines));
    return TestCaseUtils.makeEntry(lines.toArray(new String[0]));
  }

  private static Set<String> loginConfFiles()
  {
    final String[] names = getFileForPath(CONFIG_DIR_NAME).list(
        (dir, name) -> name.startsWith("login") && name.endsWith(".conf"));
    return names != null ? new TreeSet<>(Arrays.asList(names)) : new TreeSet<String>();
  }
}
