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
package org.opends.server.tools.upgrade;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.util.ChangeOperationType;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests that the issue #851 upgrade task payloads, applied through
 * {@link UpgradeUtils#updateConfigFile}, add the RFC 5805 transaction extended operation handler
 * entries exactly once and match the fresh-install template, and that the issue #1118 payload
 * gives the Referential Integrity plugin the plugin types of the fresh-install template.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "tools" }, sequential = true)
public class UpgradeUtilsTestCase extends DirectoryServerTestCase
{
  /** Unknown config attributes must not fail parsing, as in the upgrade tool's own schema. */
  private final Schema schema = Schema.getCoreSchema().asNonStrictSchema();

  private static final String PLUGIN_TYPE = "ds-cfg-plugin-type";
  private final DN referentialIntegrityPluginDN =
      DN.valueOf("cn=Referential Integrity,cn=Plugins,cn=config", schema);

  /** The config.ldif template a fresh install starts from. */
  private File freshInstallTemplate()
  {
    return new File(TestCaseUtils.getBuildRoot(), "resource/config/config.ldif");
  }

  /** The upgrade task must apply exactly what a fresh install ships. */
  @Test
  public void testTaskPayloadsMirrorFreshInstallTemplate() throws Exception
  {
    final List<Entry> template = readEntries(freshInstallTemplate());
    assertEntryPresentOnce(template, Upgrade.START_TRANSACTION_HANDLER_ENTRY);
    assertEntryPresentOnce(template, Upgrade.END_TRANSACTION_HANDLER_ENTRY);
  }

  @Test
  public void testAddTransactionHandlersAppliesOnceAndIsIdempotent() throws Exception
  {
    final File tempDir = TestCaseUtils.createTemporaryDirectory("upgradeTask851");
    try
    {
      // Simulates an instance upgraded from a pre-4.10.0 version: the fresh-install
      // template with the two transaction handler entries missing.
      final File config = new File(tempDir, "config.ldif");
      writeConfigWithoutTransactionEntries(config);

      assertEquals(applyAdd(config, Upgrade.START_TRANSACTION_HANDLER_ENTRY), 1);
      assertEquals(applyAdd(config, Upgrade.END_TRANSACTION_HANDLER_ENTRY), 1);

      assertEquals(applyAdd(config, Upgrade.START_TRANSACTION_HANDLER_ENTRY), 0);
      assertEquals(applyAdd(config, Upgrade.END_TRANSACTION_HANDLER_ENTRY), 0);

      final List<Entry> entries = readEntries(config);
      assertEntryPresentOnce(entries, Upgrade.START_TRANSACTION_HANDLER_ENTRY);
      assertEntryPresentOnce(entries, Upgrade.END_TRANSACTION_HANDLER_ENTRY);
    }
    finally
    {
      StaticUtils.recursiveDelete(tempDir);
    }
  }

  @DataProvider
  public Object[][] referentialIntegrityPluginTypesBeforeUpgrade()
  {
    return new Object[][] {
      // The entry shipped before issue #1118.
      { new String[] { "postOperationDelete", "postOperationModifyDN", "subordinateModifyDN",
                       "subordinateDelete" } },
      // The same entry with one type already added by hand, in the lower case dsconfig writes.
      { new String[] { "postOperationDelete", "postOperationModifyDN", "subordinateModifyDN",
                       "subordinateDelete", "preoperationadd" } },
    };
  }

  /** Issue #1118: the task leaves the plugin with the plugin types a fresh install ships, each once. */
  @Test(dataProvider = "referentialIntegrityPluginTypesBeforeUpgrade")
  public void testAddReferentialIntegrityPluginTypesMirrorsFreshInstallTemplate(final String[] pluginTypes)
      throws Exception
  {
    final File tempDir = TestCaseUtils.createTemporaryDirectory("upgradeTask1118");
    try
    {
      final File config = new File(tempDir, "config.ldif");
      writeConfigWithReferentialIntegrityPluginTypes(config, pluginTypes);

      assertEquals(applyAddReferentialIntegrityPluginTypes(config), 1);
      assertEquals(applyAddReferentialIntegrityPluginTypes(config), 1);

      assertEquals(referentialIntegrityPluginTypes(config),
                   referentialIntegrityPluginTypes(freshInstallTemplate()));
    }
    finally
    {
      StaticUtils.recursiveDelete(tempDir);
    }
  }

  private int applyAdd(final File config, final String... ldifLines) throws Exception
  {
    return UpgradeUtils.updateConfigFile(config, null, ChangeOperationType.ADD, ldifLines);
  }

  private int applyAddReferentialIntegrityPluginTypes(final File config) throws Exception
  {
    return UpgradeUtils.updateConfigFile(config, Filter.valueOf(Upgrade.REFERENTIAL_INTEGRITY_PLUGIN_FILTER),
        ChangeOperationType.MODIFY, Upgrade.ADD_REFERENTIAL_INTEGRITY_PRE_OPERATION_PLUGIN_TYPES);
  }

  private void writeConfigWithReferentialIntegrityPluginTypes(final File config, final String... pluginTypes)
      throws Exception
  {
    int replaced = 0;
    try (LDIFEntryReader reader =
            new LDIFEntryReader(new FileInputStream(freshInstallTemplate())).setSchema(schema);
        LDIFEntryWriter writer = new LDIFEntryWriter(new FileOutputStream(config)))
    {
      while (reader.hasNext())
      {
        final Entry entry = reader.readEntry();
        if (entry.getName().equals(referentialIntegrityPluginDN))
        {
          entry.replaceAttribute(PLUGIN_TYPE, (Object[]) pluginTypes);
          replaced++;
        }
        writer.writeEntry(entry);
      }
    }
    assertEquals(replaced, 1, "fresh-install template no longer ships the Referential Integrity plugin");
  }

  /**
   * The plugin types of the Referential Integrity plugin, lower-cased and sorted. Duplicates are
   * kept: the schema used to read matches the unknown config attribute case-exactly, so two
   * values differing only in case both show.
   */
  private List<String> referentialIntegrityPluginTypes(final File config) throws Exception
  {
    for (final Entry entry : readEntries(config))
    {
      if (entry.getName().equals(referentialIntegrityPluginDN))
      {
        final List<String> types = new ArrayList<>();
        for (final ByteString value : entry.getAttribute(PLUGIN_TYPE))
        {
          types.add(value.toString().toLowerCase(Locale.ROOT));
        }
        Collections.sort(types);
        return types;
      }
    }
    throw new AssertionError("no entry " + referentialIntegrityPluginDN + " in " + config);
  }

  private void writeConfigWithoutTransactionEntries(final File config) throws Exception
  {
    final DN startDN = dnOf(Upgrade.START_TRANSACTION_HANDLER_ENTRY);
    final DN endDN = dnOf(Upgrade.END_TRANSACTION_HANDLER_ENTRY);
    int removed = 0;
    try (LDIFEntryReader reader =
            new LDIFEntryReader(new FileInputStream(freshInstallTemplate())).setSchema(schema);
        LDIFEntryWriter writer = new LDIFEntryWriter(new FileOutputStream(config)))
    {
      while (reader.hasNext())
      {
        final Entry entry = reader.readEntry();
        if (entry.getName().equals(startDN) || entry.getName().equals(endDN))
        {
          removed++;
          continue;
        }
        writer.writeEntry(entry);
      }
    }
    assertEquals(removed, 2, "fresh-install template no longer ships the transaction entries");
  }

  private void assertEntryPresentOnce(final List<Entry> entries, final String... ldifLines)
      throws Exception
  {
    final DN dn = dnOf(ldifLines);
    final List<Entry> matches = new ArrayList<>();
    for (final Entry entry : entries)
    {
      if (entry.getName().equals(dn))
      {
        matches.add(entry);
      }
    }
    assertEquals(matches.size(), 1, "expected exactly one entry " + dn);
    try (LDIFEntryReader reader = new LDIFEntryReader(ldifLines).setSchema(schema))
    {
      assertEquals(matches.get(0), reader.readEntry());
    }
  }

  private DN dnOf(final String... ldifLines)
  {
    return DN.valueOf(ldifLines[0].replaceFirst("dn: ", ""), schema);
  }

  private List<Entry> readEntries(final File ldifFile) throws Exception
  {
    final List<Entry> entries = new ArrayList<>();
    try (LDIFEntryReader reader = new LDIFEntryReader(new FileInputStream(ldifFile)).setSchema(schema))
    {
      while (reader.hasNext())
      {
        entries.add(reader.readEntry());
      }
    }
    return entries;
  }
}
