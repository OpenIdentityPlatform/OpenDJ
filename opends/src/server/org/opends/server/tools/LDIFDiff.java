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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;



/**
 * This class provides a program that may be used to determine the differences
 * between two LDIF files, generating the output in LDIF change format.  There
 * are several things to note about the operation of this program:
 * <BR>
 * <UL>
 *   <LI>This program is only designed for cases in which both LDIF files to be
 *       compared will fit entirely in memory at the same time.</LI>
 *   <LI>This program will only compare live data in the LDIF files and will
 *       ignore comments and other elements that do not have any real impact on
 *       the way that the data is interpreted.</LI>
 *   <LI>The differences will be generated in such a way as to provide the
 *       maximum amount of information, so that there will be enough information
 *       for the changes to be reversed (i.e., it will not use the "replace"
 *       modification type but only the "add" and "delete" types, and contents
 *       of deleted entries will be included as comments).</LI>
 * </UL>
 *
 *
 * Note
 * that this is only an option for cases in which both LDIF files can fit in
 * memory.  Also note that this will only compare live data in the LDIF files
 * and will ignore comments and other elements that do not have any real impact
 * on the way that the data is interpreted.
 */
public class LDIFDiff
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDIFDiff";



  /**
   * Provides the command line arguments to the <CODE>mainDiff</CODE> method
   * so that they can be processed.
   *
   * @param  args  The command line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int exitCode = mainDiff(args, false);
    if (exitCode != 0)
    {
      System.exit(exitCode);
    }
  }



  /**
   * Parses the provided command line arguments and performs the appropriate
   * LDIF diff operation.
   *
   * @param  args               The command line arguments provided to this
   *                            program.
   * @param  serverInitialized  Indicates whether the Directory Server has
   *                            already been initialized (and therefore should
   *                            not be initialized a second time).
   *
   * @return  The return code for this operation.  A value of zero indicates
   *          that all processing completed successfully.  A nonzero value
   *          indicates that some problem occurred during processing.
   */
  public static int mainDiff(String[] args, boolean serverInitialized)
  {
    BooleanArgument overwriteExisting;
    BooleanArgument showUsage;
    BooleanArgument singleValueChanges;
    StringArgument  configClass;
    StringArgument  configFile;
    StringArgument  outputLDIF;
    StringArgument  sourceLDIF;
    StringArgument  targetLDIF;


    String toolDescription = getMessage(MSGID_LDIFDIFF_TOOL_DESCRIPTION);
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    try
    {
      sourceLDIF = new StringArgument("sourceldif", 's', "sourceLDIF", true,
                                      false, true, "{filename}", null, null,
                                      MSGID_LDIFDIFF_DESCRIPTION_SOURCE_LDIF);
      argParser.addArgument(sourceLDIF);

      targetLDIF = new StringArgument("targetldif", 't', "targetLDIF", true,
                                      false, true, "{filename}", null, null,
                                      MSGID_LDIFDIFF_DESCRIPTION_TARGET_LDIF);
      argParser.addArgument(targetLDIF);

      outputLDIF = new StringArgument("outputldif", 'o', "outputLDIF", false,
                                      false, true, "{filename}", null, null,
                                      MSGID_LDIFDIFF_DESCRIPTION_OUTPUT_LDIF);
      argParser.addArgument(outputLDIF);

      overwriteExisting =
           new BooleanArgument("overwriteexisting", 'O',
                               "overwriteExisting",
                               MSGID_LDIFDIFF_DESCRIPTION_OVERWRITE_EXISTING);
      argParser.addArgument(overwriteExisting);

      singleValueChanges =
           new BooleanArgument("singlevaluechanges", 'S', "singleValueChanges",
                               MSGID_LDIFDIFF_DESCRIPTION_SINGLE_VALUE_CHANGES);
      argParser.addArgument(singleValueChanges);

      configFile = new StringArgument("configfile", 'c', "configFile", false,
                                      false, true, "{configFile}", null, null,
                                      MSGID_LDIFDIFF_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);

      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                             OPTION_LONG_CONFIG_CLASS, false,
                             false, true, OPTION_VALUE_CONFIG_CLASS,
                             ConfigFileHandler.class.getName(), null,
                             MSGID_LDIFDIFF_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);

      showUsage = new BooleanArgument("showusage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      MSGID_LDIFDIFF_DESCRIPTION_USAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_LDIFDIFF_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());
      System.err.println(message);
      return 1;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_LDIFDIFF_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(message);
      System.err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage information, then print it and exit.
    if (argParser.usageDisplayed())
    {
      return 0;
    }


    boolean checkSchema = configFile.isPresent();
    if (! serverInitialized)
    {
      // Bootstrap the Directory Server configuration for use as a client.
      DirectoryServer directoryServer = DirectoryServer.getInstance();
      directoryServer.bootstrapClient();


      // If we're to use the configuration then initialize it, along with the
      // schema.
      if (checkSchema)
      {
        try
        {
          directoryServer.initializeJMX();
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFDIFF_CANNOT_INITIALIZE_JMX;
          String message = getMessage(msgID,
                                      String.valueOf(configFile.getValue()),
                                      e.getMessage());
          System.err.println(message);
          return 1;
        }

        try
        {
          directoryServer.initializeConfiguration(configClass.getValue(),
                                                  configFile.getValue());
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFDIFF_CANNOT_INITIALIZE_CONFIG;
          String message = getMessage(msgID,
                                      String.valueOf(configFile.getValue()),
                                      e.getMessage());
          System.err.println(message);
          return 1;
        }

        try
        {
          directoryServer.initializeSchema();
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFDIFF_CANNOT_INITIALIZE_SCHEMA;
          String message = getMessage(msgID,
                                      String.valueOf(configFile.getValue()),
                                      e.getMessage());
          System.err.println(message);
          return 1;
        }
      }
    }


    // Open the source LDIF file and read it into a tree map.
    LDIFReader reader;
    LDIFImportConfig importConfig = new LDIFImportConfig(sourceLDIF.getValue());
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFDIFF_CANNOT_OPEN_SOURCE_LDIF;
      String message = getMessage(msgID, sourceLDIF.getValue(),
                                  String.valueOf(e));
      System.err.println(message);
      return 1;
    }

    TreeMap<DN,Entry> sourceMap = new TreeMap<DN,Entry>();
    try
    {
      while (true)
      {
        Entry entry = reader.readEntry(checkSchema);
        if (entry == null)
        {
          break;
        }

        sourceMap.put(entry.getDN(), entry);
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFDIFF_ERROR_READING_SOURCE_LDIF;
      String message = getMessage(msgID, sourceLDIF.getValue(),
                                  String.valueOf(e));
      System.err.println(message);
      return 1;
    }
    finally
    {
      try
      {
        reader.close();
      } catch (Exception e) {}
    }


    // Open the target LDIF file and read it into a tree map.
    importConfig = new LDIFImportConfig(targetLDIF.getValue());
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFDIFF_CANNOT_OPEN_TARGET_LDIF;
      String message = getMessage(msgID, targetLDIF.getValue(),
                                  String.valueOf(e));
      System.err.println(message);
      return 1;
    }

    TreeMap<DN,Entry> targetMap = new TreeMap<DN,Entry>();
    try
    {
      while (true)
      {
        Entry entry = reader.readEntry(checkSchema);
        if (entry == null)
        {
          break;
        }

        targetMap.put(entry.getDN(), entry);
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFDIFF_ERROR_READING_TARGET_LDIF;
      String message = getMessage(msgID, targetLDIF.getValue(),
                                  String.valueOf(e));
      System.err.println(message);
      return 1;
    }
    finally
    {
      try
      {
        reader.close();
      } catch (Exception e) {}
    }


    // Open the output writer that we'll use to write the differences.
    LDIFWriter writer;
    try
    {
      LDIFExportConfig exportConfig;
      if (outputLDIF.isPresent())
      {
        if (overwriteExisting.isPresent())
        {
          exportConfig = new LDIFExportConfig(outputLDIF.getValue(),
                                              ExistingFileBehavior.OVERWRITE);
        }
        else
        {
          exportConfig = new LDIFExportConfig(outputLDIF.getValue(),
                                              ExistingFileBehavior.APPEND);
        }
      }
      else
      {
        exportConfig = new LDIFExportConfig(System.out);
      }

      writer = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFDIFF_CANNOT_OPEN_OUTPUT;
      String message = getMessage(msgID, String.valueOf(e));
      System.err.println(message);
      return 1;
    }


    try
    {
      // Check to see if either or both of the source and target maps are empty.
      if (sourceMap.isEmpty())
      {
        if (targetMap.isEmpty())
        {
          // They're both empty, so there are no differences.
          int    msgID   = MSGID_LDIFDIFF_NO_DIFFERENCES;
          String message = getMessage(msgID);
          writer.writeComment(message, 0);
          return 0;
        }
        else
        {
          // The target isn't empty, so they're all adds.
          Iterator<DN> targetIterator = targetMap.keySet().iterator();
          while (targetIterator.hasNext())
          {
            writeAdd(writer, targetMap.get(targetIterator.next()));
          }
          return 0;
        }
      }
      else if (targetMap.isEmpty())
      {
        // The source isn't empty, so they're all deletes.
        Iterator<DN> sourceIterator = sourceMap.keySet().iterator();
        while (sourceIterator.hasNext())
        {
          writeDelete(writer, sourceMap.get(sourceIterator.next()));
        }
        return 0;
      }
      else
      {
        // Iterate through all the entries in the source and target maps and
        // identify the differences.
        Iterator<DN> sourceIterator  = sourceMap.keySet().iterator();
        Iterator<DN> targetIterator  = targetMap.keySet().iterator();
        DN           sourceDN        = sourceIterator.next();
        DN           targetDN        = targetIterator.next();
        Entry        sourceEntry     = sourceMap.get(sourceDN);
        Entry        targetEntry     = targetMap.get(targetDN);
        boolean      differenceFound = false;

        while (true)
        {
          // Compare the DNs to determine the relative order of the
          // entries.
          int comparatorValue = sourceDN.compareTo(targetDN);
          if (comparatorValue < 0)
          {
            // The source entry should be before the target entry, which means
            // that the source entry has been deleted.
            writeDelete(writer, sourceEntry);
            differenceFound = true;
            if (sourceIterator.hasNext())
            {
              sourceDN    = sourceIterator.next();
              sourceEntry = sourceMap.get(sourceDN);
            }
            else
            {
              // There are no more source entries, so if there are more target
              // entries then they're all adds.
              writeAdd(writer, targetEntry);

              while (targetIterator.hasNext())
              {
                targetDN    = targetIterator.next();
                targetEntry = targetMap.get(targetDN);
                writeAdd(writer, targetEntry);
                differenceFound = true;
              }

              break;
            }
          }
          else if (comparatorValue > 0)
          {
            // The target entry should be before the source entry, which means
            // that the target entry has been added.
            writeAdd(writer, targetEntry);
            differenceFound = true;
            if (targetIterator.hasNext())
            {
              targetDN    = targetIterator.next();
              targetEntry = targetMap.get(targetDN);
            }
            else
            {
              // There are no more target entries so all of the remaining source
              // entries are deletes.
              writeDelete(writer, sourceEntry);
              differenceFound = true;
              while (sourceIterator.hasNext())
              {
                sourceDN = sourceIterator.next();
                sourceEntry = sourceMap.get(sourceDN);
                writeDelete(writer, sourceEntry);
              }

              break;
            }
          }
          else
          {
            // The DNs are the same, so check to see if the entries are the
            // same or have been modified.
            if (writeModify(writer, sourceEntry, targetEntry,
                            singleValueChanges.isPresent()))
            {
              differenceFound = true;
            }

            if (sourceIterator.hasNext())
            {
              sourceDN    = sourceIterator.next();
              sourceEntry = sourceMap.get(sourceDN);
            }
            else
            {
              // There are no more source entries, so if there are more target
              // entries then they're all adds.
              while (targetIterator.hasNext())
              {
                targetDN    = targetIterator.next();
                targetEntry = targetMap.get(targetDN);
                writeAdd(writer, targetEntry);
                differenceFound = true;
              }

              break;
            }

            if (targetIterator.hasNext())
            {
              targetDN    = targetIterator.next();
              targetEntry = targetMap.get(targetDN);
            }
            else
            {
              // There are no more target entries so all of the remaining source
              // entries are deletes.
              writeDelete(writer, sourceEntry);
              differenceFound = true;
              while (sourceIterator.hasNext())
              {
                sourceDN = sourceIterator.next();
                sourceEntry = sourceMap.get(sourceDN);
                writeDelete(writer, sourceEntry);
              }

              break;
            }
          }
        }


        if (! differenceFound)
        {
          int    msgID   = MSGID_LDIFDIFF_NO_DIFFERENCES;
          String message = getMessage(msgID);
          writer.writeComment(message, 0);
        }
      }
    }
    catch (IOException e)
    {
      int    msgID   = MSGID_LDIFDIFF_ERROR_WRITING_OUTPUT;
      String message = getMessage(msgID, String.valueOf(e));
      System.err.println(message);
      return 1;
    }
    finally
    {
      try
      {
        writer.close();
      } catch (Exception e) {}
    }


    // If we've gotten to this point, then everything was successful.
    return 0;
  }



  /**
   * Writes an add change record to the LDIF writer.
   *
   * @param  writer  The writer to which the add record should be written.
   * @param  entry   The entry that has been added.
   *
   * @throws  IOException  If a problem occurs while attempting to write the add
   *                       record.
   */
  private static void writeAdd(LDIFWriter writer, Entry entry)
          throws IOException
  {
    writer.writeAddChangeRecord(entry);
    writer.flush();
  }



  /**
   * Writes a delete change record to the LDIF writer, including a comment
   * with the contents of the deleted entry.
   *
   * @param  writer  The writer to which the delete record should be written.
   * @param  entry   The entry that has been deleted.
   *
   * @throws  IOException  If a problem occurs while attempting to write the
   *                       delete record.
   */
  private static void writeDelete(LDIFWriter writer, Entry entry)
          throws IOException
  {
    writer.writeDeleteChangeRecord(entry, true);
    writer.flush();
  }



  /**
   * Writes a modify change record to the LDIF writer.  Note that this will
   * handle all the necessary logic for determining if the entries are actually
   * different, and if they are the same then no output will be generated.  Also
   * note that this will only look at differences between the objectclasses and
   * user attributes.  It will ignore differences in the DN and operational
   * attributes.
   *
   * @param  writer              The writer to which the modify record should be
   *                             written.
   * @param  sourceEntry         The source form of the entry.
   * @param  targetEntry         The target form of the entry.
   * @param  singleValueChanges  Indicates whether each attribute-level change
   *                             should be written in a separate modification
   *                             per attribute value.
   *
   * @return  <CODE>true</CODE> if there were any differences found between the
   *          source and target entries, or <CODE>false</CODE> if not.
   *
   * @throws  IOException  If a problem occurs while attempting to write the
   *                       change record.
   */
  private static boolean writeModify(LDIFWriter writer, Entry sourceEntry,
                                     Entry targetEntry,
                                     boolean singleValueChanges)
          throws IOException
  {
    // Create a list to hold the modifications that are found.
    LinkedList<Modification> modifications = new LinkedList<Modification>();


    // Look at the set of objectclasses for the entries.
    LinkedHashSet<ObjectClass> sourceClasses =
         new LinkedHashSet<ObjectClass>(
                  sourceEntry.getObjectClasses().keySet());
    LinkedHashSet<ObjectClass> targetClasses =
         new LinkedHashSet<ObjectClass>(
                  targetEntry.getObjectClasses().keySet());
    Iterator<ObjectClass> sourceClassIterator = sourceClasses.iterator();
    while (sourceClassIterator.hasNext())
    {
      ObjectClass sourceClass = sourceClassIterator.next();
      if (targetClasses.remove(sourceClass))
      {
        sourceClassIterator.remove();
      }
    }

    if (! sourceClasses.isEmpty())
    {
      // Whatever is left must have been deleted.
      AttributeType attrType = DirectoryServer.getObjectClassAttributeType();
      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>();
      for (ObjectClass oc : sourceClasses)
      {
        values.add(new AttributeValue(attrType, oc.getNameOrOID()));
      }

      Attribute attr = new Attribute(attrType, attrType.getNameOrOID(), values);
      modifications.add(new Modification(ModificationType.DELETE, attr));
    }

    if (! targetClasses.isEmpty())
    {
      // Whatever is left must have been added.
      AttributeType attrType = DirectoryServer.getObjectClassAttributeType();
      LinkedHashSet<AttributeValue> values =
           new LinkedHashSet<AttributeValue>();
      for (ObjectClass oc : targetClasses)
      {
        values.add(new AttributeValue(attrType, oc.getNameOrOID()));
      }

      Attribute a = new Attribute(attrType, attrType.getNameOrOID(), values);
      modifications.add(new Modification(ModificationType.ADD, a));
    }


    // Look at the user attributes for the entries.
    LinkedHashSet<AttributeType> sourceTypes =
         new LinkedHashSet<AttributeType>(
                  sourceEntry.getUserAttributes().keySet());
    Iterator<AttributeType> sourceTypeIterator = sourceTypes.iterator();
    while (sourceTypeIterator.hasNext())
    {
      AttributeType   type        = sourceTypeIterator.next();
      List<Attribute> sourceAttrs = sourceEntry.getUserAttribute(type);
      List<Attribute> targetAttrs = targetEntry.getUserAttribute(type);
      sourceEntry.removeAttribute(type);

      if (targetAttrs == null)
      {
        // The target entry doesn't have this attribute type, so it must have
        // been deleted.  In order to make the delete reversible, delete each
        // value individually.
        for (Attribute a : sourceAttrs)
        {
          modifications.add(new Modification(ModificationType.DELETE, a));
        }
      }
      else
      {
        // Check the attributes for differences.  We'll ignore differences in
        // the order of the values since that isn't significant.
        targetEntry.removeAttribute(type);

        for (Attribute sourceAttr : sourceAttrs)
        {
          Attribute targetAttr = null;
          Iterator<Attribute> attrIterator = targetAttrs.iterator();
          while (attrIterator.hasNext())
          {
            Attribute a = attrIterator.next();
            if (a.optionsEqual(sourceAttr.getOptions()))
            {
              targetAttr = a;
              attrIterator.remove();
              break;
            }
          }

          if (targetAttr == null)
          {
            // The attribute doesn't exist in the target list, so it has been
            // deleted.
            modifications.add(new Modification(ModificationType.DELETE,
                                               sourceAttr));
          }
          else
          {
            // See if the value lists are equal.
            LinkedHashSet<AttributeValue> sourceValues = sourceAttr.getValues();
            LinkedHashSet<AttributeValue> targetValues = targetAttr.getValues();
            LinkedHashSet<AttributeValue> deletedValues =
                 new LinkedHashSet<AttributeValue>();
            Iterator<AttributeValue> valueIterator = sourceValues.iterator();
            while (valueIterator.hasNext())
            {
              AttributeValue v = valueIterator.next();
              valueIterator.remove();

              if (! targetValues.remove(v))
              {
                // This particular value has been deleted.
                deletedValues.add(v);
              }
            }

            if (! deletedValues.isEmpty())
            {
              Attribute attr = new Attribute(type, sourceAttr.getName(),
                                             sourceAttr.getOptions(),
                                             deletedValues);
              modifications.add(new Modification(ModificationType.DELETE,
                                                 attr));
            }

            // Anything left in the target list has been added.
            if (! targetValues.isEmpty())
            {
              Attribute attr = new Attribute(type, sourceAttr.getName(),
                                             sourceAttr.getOptions(),
                                             targetValues);
              modifications.add(new Modification(ModificationType.ADD, attr));
            }
          }
        }


        // Any remaining target attributes have been added.
        for (Attribute targetAttr: targetAttrs)
        {
          modifications.add(new Modification(ModificationType.ADD, targetAttr));
        }
      }
    }

    // Any remaining target attribute types have been added.
    for (AttributeType type : targetEntry.getUserAttributes().keySet())
    {
      List<Attribute> targetAttrs = targetEntry.getUserAttribute(type);
      for (Attribute a : targetAttrs)
      {
        modifications.add(new Modification(ModificationType.ADD, a));
      }
    }


    // Write the modification change record.
    if (modifications.isEmpty())
    {
      return false;
    }
    else
    {
      if (singleValueChanges)
      {
        for (Modification m : modifications)
        {
          Attribute a = m.getAttribute();
          LinkedHashSet<AttributeValue> values = a.getValues();
          if (values.isEmpty())
          {
            LinkedList<Modification> attrMods = new LinkedList<Modification>();
            attrMods.add(m);
            writer.writeModifyChangeRecord(sourceEntry.getDN(), attrMods);
          }
          else
          {
            LinkedList<Modification> attrMods = new LinkedList<Modification>();
            LinkedHashSet<AttributeValue> valueSet =
                 new LinkedHashSet<AttributeValue>();
            for (AttributeValue v : values)
            {
              valueSet.clear();
              valueSet.add(v);
              Attribute attr = new Attribute(a.getAttributeType(),
                                             a.getName(), valueSet);

              attrMods.clear();
              attrMods.add(new Modification(m.getModificationType(), attr));
              writer.writeModifyChangeRecord(sourceEntry.getDN(), attrMods);
            }
          }
        }
      }
      else
      {
        writer.writeModifyChangeRecord(sourceEntry.getDN(), modifications);
      }

      return true;
    }
  }
}

