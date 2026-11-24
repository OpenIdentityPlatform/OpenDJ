package org.opends.server.extensions;

import static org.opends.server.config.ConfigConstants.CONFIG_DIR_NAME;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.testng.Assert.*;

import java.io.File;
import java.util.Arrays;
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

  /** An existing keytab, so that only the KDC and realm pair is wrong. */
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

  @DataProvider(name = "rejectedConfigs")
  public Object[][] rejectedConfigs()
  {
    return new Object[][] {
      { "ds-cfg-kdc-address: kdc.example.com" },
      { "ds-cfg-realm: EXAMPLE.COM" },
    };
  }

  /** Only one of the KDC address and the realm is set: startup must fail before writing the file. */
  @Test(dataProvider = "rejectedConfigs")
  public void testRejectedKdcRealmLeavesNoJaasFile(String kdcOrRealm) throws Exception
  {
    final Set<String> filesBefore = loginConfFiles();
    final String jaasConfigBefore = System.getProperty(JAAS_CONFIG_PROPERTY);

    Entry e = TestCaseUtils.makeEntry(
        "dn: cn=GSSAPI,cn=SASL Mechanisms,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-sasl-mechanism-handler",
        "objectClass: ds-cfg-gssapi-sasl-mechanism-handler",
        "cn: GSSAPI",
        "ds-cfg-java-class: org.opends.server.extensions.GSSAPISASLMechanismHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-identity-mapper: cn=Exact Match,cn=Identity Mappers,cn=config",
        "ds-cfg-server-fqdn: localhost",
        "ds-cfg-keytab: " + keytab.getAbsolutePath(),
        kdcOrRealm);

    try
    {
      InitializationUtils.initializeSASLMechanismHandler(
          new GSSAPISASLMechanismHandler(), e, GSSAPISASLMechanismHandlerCfgDefn.getInstance());
      fail("A configuration with only one of the KDC address and the realm must be rejected");
    }
    catch (InitializationException expected)
    {
      // Expected.
    }

    assertEquals(loginConfFiles(), filesBefore, "A rejected configuration left a JAAS login file behind");
    assertEquals(System.getProperty(JAAS_CONFIG_PROPERTY), jaasConfigBefore,
        "A rejected configuration published the JAAS login configuration property");
  }

  private static Set<String> loginConfFiles()
  {
    final String[] names = getFileForPath(CONFIG_DIR_NAME).list(
        (dir, name) -> name.startsWith("login") && name.endsWith(".conf"));
    return names != null ? new TreeSet<>(Arrays.asList(names)) : new TreeSet<String>();
  }
}
