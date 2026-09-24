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
package org.opends.server.types;

import static org.testng.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.makeldif.MakeLDIFInputStream;
import org.opends.server.tools.makeldif.TemplateFile;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tests which resources closing an {@link LDIFImportConfig} releases. */
public class LDIFImportConfigTestCase extends TypesTestCase
{
  /**
   * Far more entries than the ten the MakeLDIF input stream queues, so that the generator cannot
   * finish on its own and waits for a reader which never comes.
   */
  private static final String[] TEMPLATE = {
    "define suffix=dc=example,dc=com",
    "",
    "branch: [suffix]",
    "subordinateTemplate: person:100",
    "",
    "template: person",
    "rdnAttr: uid",
    "objectClass: top",
    "objectClass: person",
    "uid: user.<sequential:0>",
    "cn: user",
    "sn: user",
    "" };

  private String resourcePath;

  @BeforeClass
  public void setUp() throws Exception
  {
    // The template file resolves its resource directory against the server root.
    TestCaseUtils.startServer();
    resourcePath = DirectoryServer.getInstanceRoot() + File.separator + "config" + File.separator + "MakeLDIF";
  }

  /**
   * A template import which ends before any backend asked for its reader - the reject file cannot
   * be opened, the backend cannot be locked - must still stop the generator thread the config
   * started when it was built: nothing else ever closes the stream that thread feeds.
   */
  @Test
  public void testClosingATemplateConfigWhichWasNeverReadStopsItsGenerator() throws Exception
  {
    TemplateFile templateFile = new TemplateFile(resourcePath, new Random(1));
    templateFile.parse(TEMPLATE, new ArrayList<LocalizableMessage>());

    LDIFImportConfig importConfig = new LDIFImportConfig(templateFile);
    Thread generator = generatorOf(importConfig);
    generator.join(1000);
    assertTrue(generator.isAlive(),
        "The generator finished on its own, so the template does not show whether closing stops it");

    importConfig.close();

    generator.join(10000);
    assertFalse(generator.isAlive(), "The generator is still running after the import config was closed");
  }

  /**
   * A config which was handed its input stream - a replication domain passes the stream the total
   * update arrives on - does not own it, and must leave it open for the one who does.
   */
  @Test
  public void testClosingAConfigLeavesOpenTheStreamItWasHanded() throws Exception
  {
    final boolean[] closed = { false };
    ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0])
    {
      @Override
      public void close()
      {
        closed[0] = true;
      }
    };

    new LDIFImportConfig(inputStream).close();

    assertFalse(closed[0], "The config closed an input stream it did not create");
  }

  /** The thread generating the entries the config was built on, which neither class exposes. */
  private static Thread generatorOf(LDIFImportConfig importConfig) throws Exception
  {
    Field ldifInputStream = LDIFImportConfig.class.getDeclaredField("ldifInputStream");
    ldifInputStream.setAccessible(true);
    Field generatorThread = MakeLDIFInputStream.class.getDeclaredField("generatorThread");
    generatorThread.setAccessible(true);
    return (Thread) generatorThread.get(ldifInputStream.get(importConfig));
  }
}
