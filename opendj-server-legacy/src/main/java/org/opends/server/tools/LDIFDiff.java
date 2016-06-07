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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.StaticUtils;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;

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
    int exitCode = mainDiff(args, false, System.out, System.err);
    if (exitCode != 0)
    {
      System.exit(filterExitCode(exitCode));
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
   * @param  outStream          The output stream to use for standard output, or
   *                            {@code null} if standard output is not needed.
   * @param  errStream          The output stream to use for standard error, or
   *                            {@code null} if standard error is not needed.
   *
   * @return  The return code for this operation.  A value of zero indicates
   *          that all processing completed successfully.  A nonzero value
   *          indicates that some problem occurred during processing.
   */
  public static int mainDiff(String[] args, boolean serverInitialized,
                             OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    BooleanArgument overwriteExisting;
    BooleanArgument showUsage;
    BooleanArgument useCompareResultCode;
    BooleanArgument singleValueChanges;
    BooleanArgument doCheckSchema;
    StringArgument  configFile;
    StringArgument  outputLDIF;
    StringArgument  sourceLDIF;
    StringArgument  targetLDIF;
    StringArgument  ignoreAttrsFile;
    StringArgument  ignoreEntriesFile;


    LocalizableMessage toolDescription = INFO_LDIFDIFF_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    argParser.setShortToolDescription(REF_SHORT_DESC_LDIFDIFF.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());
    try
    {
      sourceLDIF =
              StringArgument.builder("sourceLDIF")
                      .shortIdentifier('s')
                      .description(INFO_LDIFDIFF_DESCRIPTION_SOURCE_LDIF.get())
                      .required()
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      targetLDIF =
              StringArgument.builder("targetLDIF")
                      .shortIdentifier('t')
                      .description(INFO_LDIFDIFF_DESCRIPTION_TARGET_LDIF.get())
                      .required()
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      outputLDIF =
              StringArgument.builder("outputLDIF")
                      .shortIdentifier('o')
                      .description(INFO_LDIFDIFF_DESCRIPTION_OUTPUT_LDIF.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      ignoreAttrsFile =
              StringArgument.builder("ignoreAttrs")
                      .shortIdentifier('a')
                      .description(INFO_LDIFDIFF_DESCRIPTION_IGNORE_ATTRS.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      ignoreEntriesFile =
              StringArgument.builder("ignoreEntries")
                      .shortIdentifier('e')
                      .description(INFO_LDIFDIFF_DESCRIPTION_IGNORE_ENTRIES.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      overwriteExisting =
              BooleanArgument.builder("overwriteExisting")
                      .shortIdentifier('O')
                      .description(INFO_LDIFDIFF_DESCRIPTION_OVERWRITE_EXISTING.get())
                      .buildAndAddToParser(argParser);
      singleValueChanges =
              BooleanArgument.builder("singleValueChanges")
                      .shortIdentifier('S')
                      .description(INFO_LDIFDIFF_DESCRIPTION_SINGLE_VALUE_CHANGES.get())
                      .buildAndAddToParser(argParser);
      doCheckSchema =
              BooleanArgument.builder("checkSchema")
                      .description(INFO_LDIFDIFF_DESCRIPTION_CHECK_SCHEMA.get())
                      .buildAndAddToParser(argParser);
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('c')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);

      useCompareResultCode =
              BooleanArgument.builder("useCompareResultCode")
                      .shortIdentifier('r')
                      .description(INFO_LDIFDIFF_DESCRIPTION_USE_COMPARE_RESULT.get())
                      .buildAndAddToParser(argParser);

      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return OPERATIONS_ERROR;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return SUCCESS;
    }

    if (doCheckSchema.isPresent() && !configFile.isPresent())
    {
      String scriptName = System.getProperty(PROPERTY_SCRIPT_NAME);
      if (scriptName == null)
      {
        scriptName = "ldif-diff";
      }
      LocalizableMessage message = WARN_LDIFDIFF_NO_CONFIG_FILE.get(scriptName);
      err.println(message);
    }


    boolean checkSchema = configFile.isPresent() && doCheckSchema.isPresent();
    if (! serverInitialized)
    {
      // Bootstrap the Directory Server configuration for use as a client.
      DirectoryServer directoryServer = DirectoryServer.getInstance();
      DirectoryServer.bootstrapClient();


      // If we're to use the configuration then initialize it, along with the
      // schema.
      if (checkSchema)
      {
        try
        {
          DirectoryServer.initializeJMX();
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDIFDIFF_CANNOT_INITIALIZE_JMX.get(configFile.getValue(), e.getMessage()));
          return OPERATIONS_ERROR;
        }

        try
        {
          directoryServer.initializeConfiguration(configFile.getValue());
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDIFDIFF_CANNOT_INITIALIZE_CONFIG.get(configFile.getValue(), e.getMessage()));
          return OPERATIONS_ERROR;
        }

        try
        {
          directoryServer.initializeSchema();
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDIFDIFF_CANNOT_INITIALIZE_SCHEMA.get(configFile.getValue(), e.getMessage()));
          return OPERATIONS_ERROR;
        }
      }
    }

    // Read in ignored entries and attributes if any
    BufferedReader ignReader = null;
    Collection<DN> ignoreEntries = new HashSet<>();
    Collection<String> ignoreAttrs = new HashSet<>();

    if (ignoreAttrsFile.getValue() != null)
    {
      try
      {
        ignReader = new BufferedReader(
          new FileReader(ignoreAttrsFile.getValue()));
        String line = null;
        while ((line = ignReader.readLine()) != null)
        {
          ignoreAttrs.add(line.toLowerCase());
        }
        ignReader.close();
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_LDIFDIFF_CANNOT_READ_FILE_IGNORE_ATTRIBS.get(ignoreAttrsFile.getValue(), e));
        return OPERATIONS_ERROR;
      }
      finally
      {
        StaticUtils.close(ignReader);
      }
    }

    if (ignoreEntriesFile.getValue() != null)
    {
      try
      {
        ignReader = new BufferedReader(
          new FileReader(ignoreEntriesFile.getValue()));
        String line = null;
        while ((line = ignReader.readLine()) != null)
        {
          try
          {
            DN dn = DN.valueOf(line);
            ignoreEntries.add(dn);
          }
          catch (LocalizedIllegalArgumentException e)
          {
            LocalizableMessage message = INFO_LDIFDIFF_CANNOT_PARSE_STRING_AS_DN.get(
                    line, ignoreEntriesFile.getValue());
            err.println(message);
          }
        }
        ignReader.close();
      }
      catch (Exception e)
      {
        printWrappedText(err, ERR_LDIFDIFF_CANNOT_READ_FILE_IGNORE_ENTRIES.get(ignoreEntriesFile.getValue(), e));
        return OPERATIONS_ERROR;
      }
      finally
      {
        StaticUtils.close(ignReader);
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
      printWrappedText(err, ERR_LDIFDIFF_CANNOT_OPEN_SOURCE_LDIF.get(sourceLDIF.getValue(), e));
      return OPERATIONS_ERROR;
    }

    TreeMap<DN,Entry> sourceMap = new TreeMap<>();
    try
    {
      while (true)
      {
        Entry entry = reader.readEntry(checkSchema);
        if (entry == null)
        {
          break;
        }

        if (! ignoreEntries.contains(entry.getName()))
        {
          sourceMap.put(entry.getName(), entry);
        }
      }
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_LDIFDIFF_ERROR_READING_SOURCE_LDIF.get(sourceLDIF.getValue(), e));
      return OPERATIONS_ERROR;
    }
    finally
    {
      StaticUtils.close(reader);
    }


    // Open the target LDIF file and read it into a tree map.
    importConfig = new LDIFImportConfig(targetLDIF.getValue());
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_LDIFDIFF_CANNOT_OPEN_TARGET_LDIF.get(targetLDIF.getValue(), e));
      return OPERATIONS_ERROR;
    }

    TreeMap<DN,Entry> targetMap = new TreeMap<>();
    try
    {
      while (true)
      {
        Entry entry = reader.readEntry(checkSchema);
        if (entry == null)
        {
          break;
        }

        if (! ignoreEntries.contains(entry.getName()))
        {
          targetMap.put(entry.getName(), entry);
        }
      }
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_LDIFDIFF_ERROR_READING_TARGET_LDIF.get(targetLDIF.getValue(), e));
      return OPERATIONS_ERROR;
    }
    finally
    {
      StaticUtils.close(reader);
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
        exportConfig = new LDIFExportConfig(out);
      }

      writer = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_LDIFDIFF_CANNOT_OPEN_OUTPUT.get(e));
      return OPERATIONS_ERROR;
    }


    try
    {
      boolean differenceFound;

      // Check to see if either or both of the source and target maps are empty.
      if (sourceMap.isEmpty())
      {
        if (targetMap.isEmpty())
        {
          // They're both empty, so there are no differences.
          differenceFound = false;
        }
        else
        {
          // The target isn't empty, so they're all adds.
          Iterator<DN> targetIterator = targetMap.keySet().iterator();
          while (targetIterator.hasNext())
          {
            writeAdd(writer, targetMap.get(targetIterator.next()));
          }
          differenceFound = true;
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
        differenceFound = true;
      }
      else
      {
        differenceFound = false;
        // Iterate through all the entries in the source and target maps and
        // identify the differences.
        Iterator<DN> sourceIterator  = sourceMap.keySet().iterator();
        Iterator<DN> targetIterator  = targetMap.keySet().iterator();
        DN           sourceDN        = sourceIterator.next();
        DN           targetDN        = targetIterator.next();
        Entry        sourceEntry     = sourceMap.get(sourceDN);
        Entry        targetEntry     = targetMap.get(targetDN);

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
            if (writeModify(writer, sourceEntry, targetEntry, ignoreAttrs,
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
      }

      if (!differenceFound)
      {
        LocalizableMessage message = INFO_LDIFDIFF_NO_DIFFERENCES.get();
        writer.writeComment(message, 0);
      }
      if (useCompareResultCode.isPresent())
      {
        return !differenceFound ? COMPARE_TRUE : COMPARE_FALSE;
      }
    }
    catch (IOException e)
    {
      printWrappedText(err, ERR_LDIFDIFF_ERROR_WRITING_OUTPUT.get(e));
      return OPERATIONS_ERROR;
    }
    finally
    {
      StaticUtils.close(writer);
    }


    // If we've gotten to this point, then everything was successful.
    return SUCCESS;
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
   * @param  ignoreAttrs         Attributes that are ignored while calculating
   *                             the differences.
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
      Entry targetEntry, Collection<String> ignoreAttrs, boolean singleValueChanges)
          throws IOException
  {
    // Create a list to hold the modifications that are found.
    LinkedList<Modification> modifications = new LinkedList<>();


    // Look at the set of objectclasses for the entries.
    LinkedHashSet<ObjectClass> sourceClasses = new LinkedHashSet<>(sourceEntry.getObjectClasses().keySet());
    LinkedHashSet<ObjectClass> targetClasses = new LinkedHashSet<>(targetEntry.getObjectClasses().keySet());
    Iterator<ObjectClass> sourceClassIterator = sourceClasses.iterator();
    while (sourceClassIterator.hasNext())
    {
      ObjectClass sourceClass = sourceClassIterator.next();
      if (targetClasses.remove(sourceClass))
      {
        sourceClassIterator.remove();
      }
    }

    if (!sourceClasses.isEmpty())
    {
      // Whatever is left must have been deleted.
      modifications.add(new Modification(DELETE, toObjectClassAttribute(sourceClasses)));
    }

    if (! targetClasses.isEmpty())
    {
      // Whatever is left must have been added.
      modifications.add(new Modification(ADD, toObjectClassAttribute(targetClasses)));
    }


    // Look at the user attributes for the entries.
    LinkedHashSet<AttributeType> sourceTypes = new LinkedHashSet<>(sourceEntry.getUserAttributes().keySet());
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
          modifications.add(new Modification(DELETE, a));
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
            if (a.getAttributeDescription().equals(sourceAttr.getAttributeDescription()))
            {
              targetAttr = a;
              attrIterator.remove();
              break;
            }
          }

          if (targetAttr == null)
          {
            // The attribute doesn't exist in the target list, so it has been deleted.
            modifications.add(new Modification(DELETE, sourceAttr));
          }
          else
          {
            // Compare the values.
            Attribute addedValues = minusAttribute(targetAttr, sourceAttr);
            if (!addedValues.isEmpty())
            {
              modifications.add(new Modification(ADD, addedValues));
            }

            Attribute deletedValues = minusAttribute(sourceAttr, targetAttr);
            if (!deletedValues.isEmpty())
            {
              modifications.add(new Modification(DELETE, deletedValues));
            }
          }
        }


        // Any remaining target attributes have been added.
        for (Attribute targetAttr: targetAttrs)
        {
          modifications.add(new Modification(ADD, targetAttr));
        }
      }
    }

    // Any remaining target attribute types have been added.
    for (AttributeType type : targetEntry.getUserAttributes().keySet())
    {
      for (Attribute a : targetEntry.getUserAttribute(type))
      {
        modifications.add(new Modification(ADD, a));
      }
    }

    // Remove ignored attributes
    if (! ignoreAttrs.isEmpty())
    {
      ListIterator<Modification> modIter = modifications.listIterator();
      while (modIter.hasNext())
      {
        String name = modIter.next().getAttribute().getAttributeDescription().getNameOrOID().toLowerCase();
        if (ignoreAttrs.contains(name))
        {
            modIter.remove();
        }
      }
    }

    // Write the modification change record.
    if (modifications.isEmpty())
    {
      return false;
    }

    if (singleValueChanges)
    {
      for (Modification m : modifications)
      {
        Attribute a = m.getAttribute();
        if (a.isEmpty())
        {
          writer.writeModifyChangeRecord(sourceEntry.getName(), newLinkedList(m));
        }
        else
        {
          LinkedList<Modification> attrMods = new LinkedList<>();
          for (ByteString v : a)
          {
            AttributeBuilder builder = new AttributeBuilder(a.getAttributeDescription());
            builder.add(v);
            Attribute attr = builder.toAttribute();

            attrMods.clear();
            attrMods.add(new Modification(m.getModificationType(), attr));
            writer.writeModifyChangeRecord(sourceEntry.getName(), attrMods);
          }
        }
      }
    }
    else
    {
      writer.writeModifyChangeRecord(sourceEntry.getName(), modifications);
    }

    return true;
  }

  private static Attribute toObjectClassAttribute(Collection<ObjectClass> objectClasses)
  {
    AttributeBuilder builder = new AttributeBuilder(getObjectClassAttributeType());
    for (ObjectClass oc : objectClasses)
    {
      builder.add(oc.getNameOrOID());
    }
    return builder.toAttribute();
  }

  private static Attribute minusAttribute(Attribute sourceAttr, Attribute removeAttr)
  {
    AttributeBuilder builder = new AttributeBuilder(sourceAttr);
    builder.removeAll(removeAttr);
    return builder.toAttribute();
  }
}
