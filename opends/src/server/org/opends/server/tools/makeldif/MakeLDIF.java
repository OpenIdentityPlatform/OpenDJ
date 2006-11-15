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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a program that can be used to generate LDIF content based
 * on a template.
 */
public class MakeLDIF
       implements EntryWriter
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.makeldif.MakeLDIF";



  // The LDIF writer that will be used to write the entries.
  private LDIFWriter ldifWriter;

  // The total number of entries that have been written.
  private long entriesWritten;



  /**
   * Invokes the <CODE>makeLDIFMain</CODE> method with the provided set of
   * arguments.
   *
   * @param  args  The command-line arguments provided for this program.
   */
  public static void main(String[] args)
  {
    MakeLDIF makeLDIF = new MakeLDIF();
    int returnCode = makeLDIF.makeLDIFMain(args);
    if (returnCode != 0)
    {
      System.exit(returnCode);
    }
  }



  /**
   * Creates a new instance of this utility.  It should just be used for
   * invoking the <CODE>makeLDIFMain</CODE> method.
   */
  public MakeLDIF()
  {
    ldifWriter     = null;
    entriesWritten = 0L;
  }



  /**
   * Processes the provided set of command-line arguments and begins generating
   * the LDIF content.
   *
   * @param  args  The command-line arguments provided for this program.
   *
   * @return  A result code of zero if all processing completed properly, or
   *          a nonzero result if a problem occurred.
   */
  public int makeLDIFMain(String[] args)
  {
    // Create and initialize the argument parser for this program.
    String toolDescription = getMessage(MSGID_MAKELDIF_TOOL_DESCRIPTION);
    ArgumentParser  argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                   false);
    BooleanArgument showUsage;
    IntegerArgument randomSeed;
    StringArgument  configClass;
    StringArgument  configFile;
    StringArgument  templatePath;
    StringArgument  ldifFile;
    StringArgument  resourcePath;

    try
    {
      configFile = new StringArgument("configfile", 'c', "configFile", true,
                                      false, true, "{configFile}", null, null,
                                      MSGID_MAKELDIF_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      configClass = new StringArgument("configclass", 'C', "configClass", false,
                                       false, true, "{configClass}", null, null,
                                       MSGID_MAKELDIF_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      resourcePath =
           new StringArgument("resourcepath", 'r', "resourcePath", true, false,
                              true, "{path}", null, null,
                              MSGID_MAKELDIF_DESCRIPTION_RESOURCE_PATH);
      resourcePath.setHidden(true);
      argParser.addArgument(resourcePath);


      templatePath = new StringArgument("templatefile", 't', "templateFile",
                                        true, false, true, "{file}", null, null,
                                        MSGID_MAKELDIF_DESCRIPTION_TEMPLATE);
      argParser.addArgument(templatePath);


      ldifFile = new StringArgument("ldiffile", 'o', "ldifFile", true, false,
                                    true, "{file}", null, null,
                                    MSGID_MAKELDIF_DESCRIPTION_LDIF);
      argParser.addArgument(ldifFile);


      randomSeed = new IntegerArgument("randomseed", 's', "randomSeed", false,
                                       false, true, "{seed}", 0, null,
                                       MSGID_MAKELDIF_DESCRIPTION_SEED);
      argParser.addArgument(randomSeed);


      showUsage = new BooleanArgument("help", 'H', "help",
                                      MSGID_MAKELDIF_DESCRIPTION_HELP);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      int msgID = MSGID_MAKELDIF_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_MAKELDIF_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      System.err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage information, then print it and exit.
    if (showUsage.isPresent())
    {
      return 0;
    }


    // Initialize the Directory Server configuration handler using the
    // information that was provided.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    directoryServer.bootstrapClient();

    try
    {
      directoryServer.initializeJMX();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_INITIALIZE_JMX;
      String message = getMessage(msgID,
                                  String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_INITIALIZE_CONFIG;
      String message = getMessage(msgID,
                                  String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      directoryServer.initializeSchema();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_INITIALIZE_SCHEMA;
      String message = getMessage(msgID,
                                  String.valueOf(configFile.getValue()),
                                  e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Create the random number generator that will be used for the generation
    // process.
    Random random;
    if (randomSeed.isPresent())
    {
      try
      {
        random = new Random(randomSeed.getIntValue());
      }
      catch (Exception e)
      {
        random = new Random();
      }
    }
    else
    {
      random = new Random();
    }


    // If a resource path was provided, then make sure it's acceptable.
    File resourceDir = new File(resourcePath.getValue());
    if (! resourceDir.exists())
    {
      int    msgID   = MSGID_MAKELDIF_NO_SUCH_RESOURCE_DIRECTORY;
      String message = getMessage(msgID, resourcePath.getValue());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Load and parse the template file.
    LinkedList<String> warnings = new LinkedList<String>();
    TemplateFile templateFile = new TemplateFile(resourcePath.getValue(),
                                                 random);
    try
    {
      templateFile.parse(templatePath.getValue(), warnings);
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_MAKELDIF_IOEXCEPTION_DURING_PARSE;
      String message = getMessage(msgID, ioe.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_EXCEPTION_DURING_PARSE;
      String message = getMessage(msgID, e.getMessage());
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // If there were any warnings, then print them.
    if (! warnings.isEmpty())
    {
      for (String s : warnings)
      {
        System.err.println(wrapText(s, MAX_LINE_WIDTH));
      }
    }


    // Create the LDIF writer that will be used to actually write the LDIF.
    LDIFExportConfig exportConfig =
         new LDIFExportConfig(ldifFile.getValue(),
                              ExistingFileBehavior.OVERWRITE);
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_MAKELDIF_UNABLE_TO_CREATE_LDIF;
      String message = getMessage(msgID, ldifFile.getValue(),
                                  String.valueOf(ioe));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Generate the LDIF content.
    try
    {
      templateFile.generateLDIF(this);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_ERROR_WRITING_LDIF;
      String message = getMessage(msgID, ldifFile.getValue(),
                                  String.valueOf(e));
      System.err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    finally
    {
      try
      {
        ldifWriter.close();
      } catch (Exception e) {}
    }


    // If we've gotten here, then everything was successful.
    return 0;
  }



  /**
   * Writes the provided entry to the appropriate target.
   *
   * @param  entry  The entry to be written.
   *
   * @return  <CODE>true</CODE> if the entry writer will accept more entries, or
   *          <CODE>false</CODE> if not.
   *
   * @throws  IOException  If a problem occurs while writing the entry to its
   *                       intended destination.
   *
   * @throws  MakeLDIFException  If some other problem occurs.
   */
  public boolean writeEntry(Entry entry)
         throws IOException, MakeLDIFException
  {
    try
    {
      ldifWriter.writeEntry(entry);

      if ((++entriesWritten % 1000) == 0)
      {
        int    msgID   = MSGID_MAKELDIF_PROCESSED_N_ENTRIES;
        String message = getMessage(msgID, entriesWritten);
        System.out.println(wrapText(message, MAX_LINE_WIDTH));
      }

      return true;
    }
    catch (IOException ioe)
    {
      throw ioe;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_WRITE_ENTRY;
      String message = getMessage(msgID, String.valueOf(entry.getDN()),
                                  String.valueOf(e));
      throw new MakeLDIFException(msgID, message, e);
    }
  }



  /**
   * Notifies the entry writer that no more entries will be provided and that
   * any associated cleanup may be performed.
   */
  public void closeEntryWriter()
  {
    int    msgID   = MSGID_MAKELDIF_PROCESSING_COMPLETE;
    String message = getMessage(msgID, entriesWritten);
    System.out.println(wrapText(message, MAX_LINE_WIDTH));
  }
}

