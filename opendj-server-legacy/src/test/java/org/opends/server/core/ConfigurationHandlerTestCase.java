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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.opends.server.ServerContextBuilder.aServerContext;
import static org.testng.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ConfigurationHandlerTestCase extends CoreTestCase
{
  private static final DN DN_CONFIG = DN.valueOf(ConfigConstants.DN_CONFIG_ROOT);
  private static final DN DN_SCHEMA_PROVIDERS = DN.valueOf("cn=Schema Providers,cn=config");
  private static final DN DN_CORE_SCHEMA = DN.valueOf("cn=Core Schema,cn=Schema Providers,cn=config");

  private ConfigurationHandler configHandler;

  @BeforeMethod
  public void initializeTest() throws Exception
  {
    // Use a copy of configuration for tests to avoid updating the original configuration file.
    File originalConfigFile = TestCaseUtils.getTestResource("configForTests/config-small.ldif");
    File copyConfigFile = new File(TestCaseUtils.getUnitTestRootPath(), "config-small-copy.ldif");
    copyConfigFile.deleteOnExit();
    if (copyConfigFile.exists())
    {
      copyConfigFile.delete();
    }
    TestCaseUtils.copyFile(originalConfigFile, copyConfigFile);
    configHandler = getConfigurationHandler(copyConfigFile);
  }

  @AfterClass
  public void cleanup()
  {
    File copyConfigFile = new File(TestCaseUtils.getUnitTestRootPath(), "config-small-copy.ldif");
    if (copyConfigFile.exists())
    {
      copyConfigFile.delete();
    }
  }

  /** Returns the configuration handler fully initialized from configuration file. */
  private ConfigurationHandler getConfigurationHandler(File configFile) throws Exception
  {
    final ServerContext context = aServerContext().
        schemaDirectory(new File(TestCaseUtils.getBuildRoot(), "resource/schema")).
        configFile(configFile).
        build();

    return ConfigurationHandler.bootstrapConfiguration(context, ConfigurationHandler.class);
  }

  @Test
    public void testConfigurationBootstrap() throws Exception
    {
      assertTrue(configHandler.hasEntry(DN_CONFIG));
    }

  @Test
  public void testGetEntry() throws Exception
  {
    Entry entry = configHandler.getEntry(DN_SCHEMA_PROVIDERS);
    assertTrue(entry.containsAttribute("objectclass", "top", "ds-cfg-branch"));
  }

  @Test
  public void testGetChildren() throws Exception
  {
    Set<DN> dns = configHandler.getChildren(DN_SCHEMA_PROVIDERS);
    assertTrue(dns.contains(DN_CORE_SCHEMA));
  }

  @Test
  public void testNumSubordinates() throws Exception
  {
    long numSubordinates = configHandler.numSubordinates(DN_SCHEMA_PROVIDERS, false);
    assertEquals(numSubordinates, 1);

    numSubordinates = configHandler.numSubordinates(DN_CONFIG, true);
    assertEquals(numSubordinates, 2);
  }

  @Test
  public void testRegisterChangeListener() throws Exception
  {
    ConfigChangeListener listener1 = mock(ConfigChangeListener.class);
    ConfigChangeListener listener2 = mock(ConfigChangeListener.class);

    configHandler.registerChangeListener(DN_SCHEMA_PROVIDERS, listener1);
    configHandler.registerChangeListener(DN_SCHEMA_PROVIDERS, listener2);

    assertEquals(configHandler.getChangeListeners(DN_SCHEMA_PROVIDERS), Arrays.asList(listener1 ,listener2));
  }

  @Test
  public void testRegisterDeregisterChangeListener() throws Exception
  {
    ConfigChangeListener listener1 = mock(ConfigChangeListener.class);
    ConfigChangeListener listener2 = mock(ConfigChangeListener.class);

    configHandler.registerChangeListener(DN_SCHEMA_PROVIDERS, listener1);
    configHandler.registerChangeListener(DN_SCHEMA_PROVIDERS, listener2);
    configHandler.deregisterChangeListener(DN_SCHEMA_PROVIDERS, listener1);

    assertEquals(configHandler.getChangeListeners(DN_SCHEMA_PROVIDERS), Arrays.asList(listener2));
  }

  @Test
  public void testRegisterAddListener() throws Exception
  {
    ConfigAddListener listener1 = mock(ConfigAddListener.class);
    ConfigAddListener listener2 = mock(ConfigAddListener.class);

    configHandler.registerAddListener(DN_SCHEMA_PROVIDERS, listener1);
    configHandler.registerAddListener(DN_SCHEMA_PROVIDERS, listener2);

    assertEquals(configHandler.getAddListeners(DN_SCHEMA_PROVIDERS), Arrays.asList(listener1 ,listener2));
  }

  @Test
  public void testRegisterDeleteListener() throws Exception
  {
    ConfigDeleteListener listener1 = mock(ConfigDeleteListener.class);
    ConfigDeleteListener listener2 = mock(ConfigDeleteListener.class);

    configHandler.registerDeleteListener(DN_SCHEMA_PROVIDERS, listener1);
    configHandler.registerDeleteListener(DN_SCHEMA_PROVIDERS, listener2);

    assertEquals(configHandler.getDeleteListeners(DN_SCHEMA_PROVIDERS), Arrays.asList(listener1 ,listener2));
  }

  @Test
  public void testAddEntry() throws Exception
  {
    String dn = "cn=Another schema provider,cn=Schema Providers,cn=config";

    configHandler.addEntry(new LinkedHashMapEntry(dn));

    assertTrue(configHandler.hasEntry(DN.valueOf(dn)));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testAddEntryExistingEntry() throws Exception
  {
    configHandler.addEntry(new LinkedHashMapEntry(DN_CORE_SCHEMA));
  }

  /** TODO : disabled because fail when converting to server DN. Re-enable once migrated to SDK DN. */
  @Test(enabled=false, expectedExceptions=DirectoryException.class)
  public void testAddEntryParentUnknown() throws Exception
  {
    configHandler.addEntry(new LinkedHashMapEntry("cn=Core Schema,cn=Schema Providers,cn=Providers,cn=config"));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testAddEntryNoParent() throws Exception
  {
    configHandler.addEntry(new LinkedHashMapEntry(DN.rootDN()));
  }

  @Test
  public void testAddListenerWithAddEntry() throws Exception
  {
    String dn = "cn=Yet another schema provider,cn=Schema Providers,cn=config";

    ConfigAddListener listener = mock(ConfigAddListener.class);
    configHandler.registerAddListener(DN_SCHEMA_PROVIDERS, listener);
    when(listener.configAddIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(true);
    when(listener.applyConfigurationAdd(any(Entry.class))).thenReturn(new ConfigChangeResult());

    configHandler.addEntry(new LinkedHashMapEntry(dn));

    // ensure apply is called for listener
    verify(listener).applyConfigurationAdd(configHandler.getEntry(DN.valueOf(dn)));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testAddListenerWithAddEntryWhenConfigNotAcceptable() throws Exception
  {
    ConfigAddListener listener = mock(ConfigAddListener.class);
    configHandler.registerAddListener(DN_SCHEMA_PROVIDERS, listener);
    when(listener.configAddIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(false);

    configHandler.addEntry(new LinkedHashMapEntry("cn=New schema provider,cn=Schema Providers,cn=config"));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testAddListenerWithAddEntryWhenFailureApplyingConfig() throws Exception
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    ccr.setResultCode(ResultCode.OTHER);
    ConfigAddListener listener = mock(ConfigAddListener.class);
    configHandler.registerAddListener(DN_SCHEMA_PROVIDERS, listener);
    when(listener.configAddIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(true);
    when(listener.applyConfigurationAdd(any(Entry.class))).thenReturn(ccr);

    configHandler.addEntry(new LinkedHashMapEntry("cn=New schema provider,cn=Schema Providers,cn=config"));
  }

  @Test
  public void testDeleteEntry() throws Exception
  {
    configHandler.deleteEntry(DN_CORE_SCHEMA);

    assertFalse(configHandler.hasEntry(DN_CORE_SCHEMA));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testDeleteEntryUnexistingEntry() throws Exception
  {
    configHandler.deleteEntry(DN.valueOf("cn=Unexisting provider,cn=Schema Providers,cn=config"));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testDeleteEntryWithChildren() throws Exception
  {
    configHandler.deleteEntry(DN_SCHEMA_PROVIDERS);
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testDeleteEntryUnknownParent() throws Exception
  {
    configHandler.deleteEntry(DN.valueOf("cn=Core Schema,cn=Schema Providers,cn=Providers,cn=config"));
  }

  @Test
  public void testDeleteListenerWithDeleteEntry() throws Exception
  {
    ConfigDeleteListener listener = mock(ConfigDeleteListener.class);
    configHandler.registerDeleteListener(DN_SCHEMA_PROVIDERS, listener);
    Entry entryToDelete = configHandler.getEntry(DN_CORE_SCHEMA);
    when(listener.configDeleteIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(true);
    when(listener.applyConfigurationDelete(any(Entry.class))).thenReturn(new ConfigChangeResult());

    configHandler.deleteEntry(DN_CORE_SCHEMA);

    // ensure apply is called for listener
    verify(listener).applyConfigurationDelete(entryToDelete);
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testDeleteListenerWithDeleteEntryWhenConfigNotAcceptable() throws Exception
  {
    ConfigDeleteListener listener = mock(ConfigDeleteListener.class);
    configHandler.registerDeleteListener(DN_SCHEMA_PROVIDERS, listener);
    when(listener.configDeleteIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(false);

    configHandler.deleteEntry(DN_CORE_SCHEMA);
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testDeleteListenerWithDeleteEntryWhenFailureApplyingConfig() throws Exception
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    ccr.setResultCode(ResultCode.OTHER);

    ConfigDeleteListener listener = mock(ConfigDeleteListener.class);
    configHandler.registerDeleteListener(DN_SCHEMA_PROVIDERS, listener);
    when(listener.configDeleteIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(true);
    when(listener.applyConfigurationDelete(any(Entry.class))).thenReturn(ccr);

    configHandler.deleteEntry(DN_CORE_SCHEMA);
  }

  @Test
  public void testReplaceEntry() throws Exception
  {
    String dn = DN_CORE_SCHEMA.toString();

    configHandler.replaceEntry(
        new LinkedHashMapEntry("dn: " + dn, "objectclass: ds-cfg-schema-provider", "ds-cfg-enabled: true"),
        new LinkedHashMapEntry("dn: " + dn, "objectclass: ds-cfg-schema-provider", "ds-cfg-enabled: false"));

    assertTrue(configHandler.hasEntry(DN_CORE_SCHEMA));
    assertEquals(configHandler.getEntry(DN_CORE_SCHEMA).getAttribute("ds-cfg-enabled").firstValueAsString(), "false");
  }

  @Test
  public void testChangeListenerIsDeletedWhenConfigEntryIsDeleted() throws Exception
  {
    ConfigChangeListener listener = mock(ConfigChangeListener.class);
    configHandler.registerChangeListener(DN_CORE_SCHEMA, listener);
    when(listener.configChangeIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(true);
    when(listener.applyConfigurationChange(any(Entry.class))).thenReturn(new ConfigChangeResult());

    configHandler.deleteEntry(DN_CORE_SCHEMA);

    assertThat(configHandler.getChangeListeners(DN_CORE_SCHEMA)).isEmpty();
  }

  @Test
  public void testChangeListenerWithReplaceEntry() throws Exception
  {
    ConfigChangeListener listener = mock(ConfigChangeListener.class);
    configHandler.registerChangeListener(DN_CORE_SCHEMA, listener);
    when(listener.configChangeIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(true);
    when(listener.applyConfigurationChange(any(Entry.class))).thenReturn(new ConfigChangeResult());
    Entry oldEntry = configHandler.getEntry(DN_CORE_SCHEMA);

    configHandler.replaceEntry(oldEntry,
        new LinkedHashMapEntry("dn: " + DN_CORE_SCHEMA,
            "objectclass: ds-cfg-schema-provider",
            "ds-cfg-enabled: false"));

    // ensure apply is called for listener
    verify(listener).applyConfigurationChange(configHandler.getEntry(DN_CORE_SCHEMA));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testChangeListenerWithReplaceEntryWhenConfigNotAcceptable() throws Exception
  {
    ConfigChangeListener listener = mock(ConfigChangeListener.class);
    configHandler.registerChangeListener(DN_CORE_SCHEMA, listener);
    when(listener.configChangeIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(false);
    Entry oldEntry = configHandler.getEntry(DN_CORE_SCHEMA);

    configHandler.replaceEntry(oldEntry,
        new LinkedHashMapEntry("dn: " + DN_CORE_SCHEMA,
            "objectclass: ds-cfg-schema-provider",
            "ds-cfg-enabled: false"));
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testChangeListenerWithReplaceEntryWhenFailureApplyingConfig() throws Exception
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    ccr.setResultCode(ResultCode.OTHER);

    ConfigChangeListener listener = mock(ConfigChangeListener.class);
    configHandler.registerChangeListener(DN_CORE_SCHEMA, listener);
    when(listener.configChangeIsAcceptable(any(Entry.class), any(LocalizableMessageBuilder.class))).thenReturn(true);
    when(listener.applyConfigurationChange(any(Entry.class))).thenReturn(ccr);
    Entry oldEntry = configHandler.getEntry(DN_CORE_SCHEMA);

    configHandler.replaceEntry(oldEntry,
        new LinkedHashMapEntry("dn: " + DN_CORE_SCHEMA,
            "objectclass: ds-cfg-schema-provider",
            "ds-cfg-enabled: false"));
  }

}
