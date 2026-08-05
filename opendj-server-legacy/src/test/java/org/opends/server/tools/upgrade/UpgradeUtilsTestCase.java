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
import java.util.List;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.util.ChangeOperationType;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests that the issue #851 upgrade task payloads, applied through
 * {@link UpgradeUtils#updateConfigFile}, add the RFC 5805 transaction extended operation handler
 * entries exactly once and match the fresh-install template.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "tools" }, sequential = true)
public class UpgradeUtilsTestCase extends DirectoryServerTestCase
{
  /** Unknown config attributes must not fail parsing, as in the upgrade tool's own schema. */
  private final Schema schema = Schema.getCoreSchema().asNonStrictSchema();

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

  private int applyAdd(final File config, final String... ldifLines) throws Exception
  {
    return UpgradeUtils.updateConfigFile(config, null, ChangeOperationType.ADD, ldifLines);
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
