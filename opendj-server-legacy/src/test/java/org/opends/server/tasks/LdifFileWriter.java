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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.tools.makeldif.EntryWriter;
import org.opends.server.tools.makeldif.MakeLDIFException;
import org.opends.server.tools.makeldif.TemplateEntry;
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFWriter;

/** This class makes test LDIF files using the makeldif package. */
public class LdifFileWriter implements EntryWriter
{
  /** The LDIF writer used to write the entries to the file. */
  private LDIFWriter ldifWriter;

  /**
   * Construct an LdifFileWriter from an LDIF Writer.
   * @param ldifWriter The LDIF writer that should be used to write the entries.
   */
  private LdifFileWriter(LDIFWriter ldifWriter)
  {
    this.ldifWriter = ldifWriter;
  }

  /**
   * Make an LDIF file containing test data.  It uses a fixed value for the
   * random seed.
   * @param ldifPath The path to the LDIF file to be written.
   * @param resourcePath The path to the makeldif resource directory.
   * @param templatePath The path to the makeldif template file.
   * @throws IOException If there is an exception parsing the template or
   * generating the LDIF.
   * @throws InitializationException If there is an exception parsing the
   * template.
   * @throws MakeLDIFException If there is an exception parsing the template or
   * generating the LDIF.
   */
  public static void makeLdif(String ldifPath,
                              String resourcePath,
                              String templatePath)
       throws IOException, InitializationException, MakeLDIFException
  {
    TemplateFile template = new TemplateFile(resourcePath, new Random(1));
    ArrayList<LocalizableMessage> warnings = new ArrayList<>();
    template.parse(templatePath, warnings);
    makeLdif(ldifPath, template);
  }

  /**
   * Make an LDIF file containing test data.  It uses a fixed value for the
   * random seed.
   * @param ldifPath The path to the LDIF file to be written.
   * @param resourcePath The path to the makeldif resource directory.
   * @param templateLines The lines making up the template.
   * @throws IOException If there is an exception parsing the template or
   * generating the LDIF.
   * @throws InitializationException If there is an exception parsing the
   * template.
   * @throws MakeLDIFException If there is an exception parsing the template or
   * generating the LDIF.
   */
  public static void makeLdif(String ldifPath,
                              String resourcePath,
                              String[] templateLines)
       throws IOException, InitializationException, MakeLDIFException
  {
    TemplateFile template = new TemplateFile(resourcePath, new Random(1));
    ArrayList<LocalizableMessage> warnings = new ArrayList<>();
    template.parse(templateLines, warnings);
    makeLdif(ldifPath, template);
  }

  /**
   * Make an LDIF file containing test data.
   * @param ldifPath The path to the LDIF file to be written.
   * @param template The makeldif template.
   * @throws IOException If there is an exception parsing the template or
   * generating the LDIF.
   * @throws MakeLDIFException If there is an exception parsing the template or
   * generating the LDIF.
   */
  public static void makeLdif(String ldifPath, TemplateFile template)
       throws IOException, MakeLDIFException
  {
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(ldifPath, ExistingFileBehavior.OVERWRITE);
    LDIFWriter ldifWriter = new LDIFWriter(exportConfig);
    template.generateLDIF(new LdifFileWriter(ldifWriter));
  }

  @Override
  public boolean writeEntry(TemplateEntry entry)
       throws IOException, MakeLDIFException
  {
    try
    {
      return ldifWriter.writeTemplateEntry(entry);
    } catch (LDIFException e)
    {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public void closeEntryWriter()
  {
    try
    {
      ldifWriter.close();
    } catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}
