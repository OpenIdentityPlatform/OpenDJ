/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */

package org.opends.server.tasks;

import org.opends.server.tools.makeldif.EntryWriter;
import org.opends.server.tools.makeldif.MakeLDIFException;
import org.opends.server.tools.makeldif.TemplateEntry;
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.LDIFException;
import org.opends.messages.Message;

import java.io.IOException;
import java.util.Random;
import java.util.ArrayList;

/**
 * This class makes test LDIF files using the makeldif package.
 */
public class LdifFileWriter implements EntryWriter
{
  /**
   * The LDIF writer used to write the entries to the file.
   */
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
    ArrayList<Message> warnings = new ArrayList<Message>();
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
    ArrayList<Message> warnings = new ArrayList<Message>();
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

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
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
