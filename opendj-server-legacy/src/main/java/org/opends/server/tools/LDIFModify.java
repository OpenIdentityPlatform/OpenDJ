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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.StaticUtils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawModification;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.util.DeleteChangeRecordEntry;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ModifyChangeRecordEntry;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This class provides a program that may be used to apply a set of changes (in
 * LDIF change format) to an LDIF file.  It will first read all of the changes
 * into memory, and then will iterate through an LDIF file and apply them to the
 * entries contained in it.  Note that because of the manner in which it
 * processes the changes, certain types of operations will not be allowed,
 * including:
 * <BR>
 * <UL>
 *   <LI>Modify DN operations</LI>
 *   <LI>Deleting an entry that has been added</LI>
 *   <LI>Modifying an entry that has been added</LI>
 * </UL>
 */
public class LDIFModify
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDIFModify";



  /**
   * Applies the specified changes to the source LDIF, writing the modified
   * file to the specified target.  Neither the readers nor the writer will be
   * closed.
   *
   * @param  sourceReader  The LDIF reader that will be used to read the LDIF
   *                       content to be modified.
   * @param  changeReader  The LDIF reader that will be used to read the changes
   *                       to be applied.
   * @param  targetWriter  The LDIF writer that will be used to write the
   *                       modified LDIF.
   * @param  errorList     A list into which any error messages generated while
   *                       processing changes may be added.
   *
   * @return  <CODE>true</CODE> if all updates were successfully applied, or
   *          <CODE>false</CODE> if any errors were encountered.
   *
   * @throws  IOException  If a problem occurs while attempting to read the
   *                       source or changes, or write the target.
   *
   * @throws  LDIFException  If a problem occurs while attempting to decode the
   *                         source or changes, or trying to determine whether
   *                         to include the entry in the output.
   */
  public static boolean modifyLDIF(LDIFReader sourceReader,
                                   LDIFReader changeReader,
                                   LDIFWriter targetWriter,
                                   List<LocalizableMessage> errorList)
         throws IOException, LDIFException
  {
    // Read the changes into memory.
    TreeMap<DN,AddChangeRecordEntry> adds = new TreeMap<>();
    TreeMap<DN,Entry> ldifEntries = new TreeMap<>();
    HashMap<DN,DeleteChangeRecordEntry> deletes = new HashMap<>();
    HashMap<DN,LinkedList<Modification>> modifications = new HashMap<>();

    while (true)
    {
      ChangeRecordEntry changeRecord;
      try
      {
        changeRecord = changeReader.readChangeRecord(false);
      }
      catch (LDIFException le)
      {
        if (le.canContinueReading())
        {
          errorList.add(le.getMessageObject());
          continue;
        }
        else
        {
          throw le;
        }
      }

      if (changeRecord == null)
      {
        break;
      }

      DN changeDN = changeRecord.getDN();
      switch (changeRecord.getChangeOperationType())
      {
        case ADD:
          // The entry must not exist in the add list.
          if (adds.containsKey(changeDN))
          {
            errorList.add(ERR_LDIFMODIFY_CANNOT_ADD_ENTRY_TWICE.get(changeDN));
            continue;
          }
          else
          {
            adds.put(changeDN, (AddChangeRecordEntry) changeRecord);
          }
          break;

        case DELETE:
          // The entry must not exist in the add list.  If it exists in the
          // modify list, then remove the changes since we won't need to apply
          // them.
          if (adds.containsKey(changeDN))
          {
            errorList.add(ERR_LDIFMODIFY_CANNOT_DELETE_AFTER_ADD.get(changeDN));
            continue;
          }
          else
          {
            modifications.remove(changeDN);
            deletes.put(changeDN, (DeleteChangeRecordEntry) changeRecord);
          }
          break;

        case MODIFY:
          // The entry must not exist in the add or delete lists.
          if (adds.containsKey(changeDN) || deletes.containsKey(changeDN))
          {
            errorList.add(ERR_LDIFMODIFY_CANNOT_MODIFY_ADDED_OR_DELETED.get(changeDN));
            continue;
          }
          else
          {
            LinkedList<Modification> mods =
                 modifications.get(changeDN);
            if (mods == null)
            {
              mods = new LinkedList<>();
              modifications.put(changeDN, mods);
            }

            for (RawModification mod :
                 ((ModifyChangeRecordEntry) changeRecord).getModifications())
            {
              try
              {
                mods.add(mod.toModification());
              }
              catch (LDAPException le)
              {
                errorList.add(le.getMessageObject());
                continue;
              }
            }
          }
          break;

        case MODIFY_DN:
          errorList.add(ERR_LDIFMODIFY_MODDN_NOT_SUPPORTED.get(changeDN));
          continue;

        default:
          errorList.add(ERR_LDIFMODIFY_UNKNOWN_CHANGETYPE.get(changeDN, changeRecord.getChangeOperationType()));
          continue;
      }
    }


    // Read the source an entry at a time and apply any appropriate changes
    // before writing to the target LDIF.
    while (true)
    {
      Entry entry;
      try
      {
        entry = sourceReader.readEntry();
      }
      catch (LDIFException le)
      {
        if (le.canContinueReading())
        {
          errorList.add(le.getMessageObject());
          continue;
        }
        else
        {
          throw le;
        }
      }

      if (entry == null)
      {
        break;
      }


      // If the entry is to be deleted, then just skip over it without writing
      // it to the output.
      DN entryDN = entry.getName();
      if (deletes.remove(entryDN) != null)
      {
        continue;
      }


      // If the entry is to be added, then that's an error, since it already
      // exists.
      if (adds.remove(entryDN) != null)
      {
        errorList.add(ERR_LDIFMODIFY_ADD_ALREADY_EXISTS.get(entryDN));
        continue;
      }


      // If the entry is to be modified, then process the changes.
      LinkedList<Modification> mods = modifications.remove(entryDN);
      if (mods != null && !mods.isEmpty())
      {
        try
        {
          entry.applyModifications(mods);
        }
        catch (DirectoryException de)
        {
          errorList.add(de.getMessageObject());
          continue;
        }
      }


      // If we've gotten here, then the (possibly updated) entry should be
      // written to the LDIF entry Map.
      ldifEntries.put(entry.getName(),entry);
    }


    // Perform any adds that may be necessary.
    for (AddChangeRecordEntry add : adds.values())
    {
      Map<ObjectClass,String> objectClasses = new LinkedHashMap<>();
      Map<AttributeType,List<Attribute>> userAttributes = new LinkedHashMap<>();
      Map<AttributeType,List<Attribute>> operationalAttributes = new LinkedHashMap<>();

      for (Attribute a : add.getAttributes())
      {
        AttributeType t = a.getAttributeDescription().getAttributeType();
        if (t.isObjectClass())
        {
          for (ByteString v : a)
          {
            String ocName = v.toString();
            objectClasses.put(DirectoryServer.getSchema().getObjectClass(ocName), ocName);
          }
        }
        else if (t.isOperational())
        {
          List<Attribute> attrList = operationalAttributes.get(t);
          if (attrList == null)
          {
            attrList = new LinkedList<>();
            operationalAttributes.put(t, attrList);
          }
          attrList.add(a);
        }
        else
        {
          List<Attribute> attrList = userAttributes.get(t);
          if (attrList == null)
          {
            attrList = new LinkedList<>();
            userAttributes.put(t, attrList);
          }
          attrList.add(a);
        }
      }

      Entry e = new Entry(add.getDN(), objectClasses, userAttributes,
                          operationalAttributes);
      //Put the entry to be added into the LDIF entry map.
      ldifEntries.put(e.getName(),e);
    }


    // If there are any entries left in the delete or modify lists, then that's
    // a problem because they didn't exist.
    if (! deletes.isEmpty())
    {
      for (DN dn : deletes.keySet())
      {
        errorList.add(ERR_LDIFMODIFY_DELETE_NO_SUCH_ENTRY.get(dn));
      }
    }

    if (! modifications.isEmpty())
    {
      for (DN dn : modifications.keySet())
      {
        errorList.add(ERR_LDIFMODIFY_MODIFY_NO_SUCH_ENTRY.get(dn));
      }
    }
    return targetWriter.writeEntries(ldifEntries.values()) &&
            errorList.isEmpty();
  }



  /**
   * Invokes <CODE>ldifModifyMain</CODE> to perform the appropriate processing.
   *
   * @param  args  The command-line arguments provided to the client.
   */
  public static void main(String[] args)
  {
    int returnCode = ldifModifyMain(args, false, System.out, System.err);
    if (returnCode != 0)
    {
      System.exit(filterExitCode(returnCode));
    }
  }



  /**
   * Processes the command-line arguments and makes the appropriate updates to
   * the LDIF file.
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
   * @return  A value of zero if everything completed properly, or nonzero if
   *          any problem(s) occurred.
   */
  public static int ldifModifyMain(String[] args, boolean serverInitialized,
                                   OutputStream outStream,
                                   OutputStream errStream)
  {
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Prepare the argument parser.
    BooleanArgument showUsage;
    StringArgument  changesFile;
    StringArgument  configFile;
    StringArgument  sourceFile;
    StringArgument  targetFile;

    LocalizableMessage toolDescription = INFO_LDIFMODIFY_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    argParser.setShortToolDescription(REF_SHORT_DESC_LDIFMODIFY.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    try
    {
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('c')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      sourceFile =
              StringArgument.builder("sourceLDIF")
                      .shortIdentifier('s')
                      .description(INFO_LDIFMODIFY_DESCRIPTION_SOURCE.get())
                      .required()
                      .valuePlaceholder(INFO_LDIFFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      changesFile =
              StringArgument.builder("changesLDIF")
                      .shortIdentifier('m')
                      .description(INFO_LDIFMODIFY_DESCRIPTION_CHANGES.get())
                      .required()
                      .valuePlaceholder(INFO_LDIFFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      targetFile =
              StringArgument.builder("targetLDIF")
                      .shortIdentifier('t')
                      .description(INFO_LDIFMODIFY_DESCRIPTION_TARGET.get())
                      .required()
                      .valuePlaceholder(INFO_LDIFFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
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
      return 0;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessage());
      return 1;
    }

    if (! serverInitialized)
    {
      // Bootstrap the Directory Server configuration for use as a client.
      DirectoryServer directoryServer = DirectoryServer.getInstance();
      DirectoryServer.bootstrapClient();


      // If we're to use the configuration then initialize it, along with the
      // schema.
      boolean checkSchema = configFile.isPresent();
      if (checkSchema)
      {
        try
        {
          DirectoryServer.initializeJMX();
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDIFMODIFY_CANNOT_INITIALIZE_JMX.get(configFile.getValue(), e.getMessage()));
          return 1;
        }

        try
        {
          directoryServer.initializeConfiguration(configFile.getValue());
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDIFMODIFY_CANNOT_INITIALIZE_CONFIG.get(configFile.getValue(), e.getMessage()));
          return 1;
        }

        try
        {
          directoryServer.initializeSchema();
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDIFMODIFY_CANNOT_INITIALIZE_SCHEMA.get(configFile.getValue(), e.getMessage()));
          return 1;
        }
      }
    }


    // Create the LDIF readers and writer from the arguments.
    File source = new File(sourceFile.getValue());
    if (! source.exists())
    {
      printWrappedText(err, ERR_LDIFMODIFY_SOURCE_DOES_NOT_EXIST.get(sourceFile.getValue()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    LDIFImportConfig importConfig = new LDIFImportConfig(sourceFile.getValue());
    LDIFReader sourceReader;
    try
    {
      sourceReader = new LDIFReader(importConfig);
    }
    catch (IOException ioe)
    {
      printWrappedText(err, ERR_LDIFMODIFY_CANNOT_OPEN_SOURCE.get(sourceFile.getValue(), ioe));
      return CLIENT_SIDE_LOCAL_ERROR;
    }


    File changes = new File(changesFile.getValue());
    if (! changes.exists())
    {
      printWrappedText(err, ERR_LDIFMODIFY_CHANGES_DOES_NOT_EXIST.get(changesFile.getValue()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    importConfig = new LDIFImportConfig(changesFile.getValue());
    LDIFReader changeReader;
    try
    {
      changeReader = new LDIFReader(importConfig);
    }
    catch (IOException ioe)
    {
      printWrappedText(err, ERR_LDIFMODIFY_CANNOT_OPEN_CHANGES.get(sourceFile.getValue(), ioe.getMessage()));
      return CLIENT_SIDE_LOCAL_ERROR;
    }


    LDIFExportConfig exportConfig =
         new LDIFExportConfig(targetFile.getValue(),
                              ExistingFileBehavior.OVERWRITE);
    LDIFWriter targetWriter;
    try
    {
      targetWriter = new LDIFWriter(exportConfig);
    }
    catch (IOException ioe)
    {
      printWrappedText(err, ERR_LDIFMODIFY_CANNOT_OPEN_TARGET.get(sourceFile.getValue(), ioe.getMessage()));
      return CLIENT_SIDE_LOCAL_ERROR;
    }


    // Actually invoke the LDIF processing.
    LinkedList<LocalizableMessage> errorList = new LinkedList<>();
    boolean successful;
    try
    {
      successful = modifyLDIF(sourceReader, changeReader, targetWriter, errorList);
    }
    catch (Exception e)
    {
      err.println(ERR_LDIFMODIFY_ERROR_PROCESSING_LDIF.get(e));
      successful = false;
    }

    close(sourceReader, changeReader, targetWriter);

    for (LocalizableMessage s : errorList)
    {
      err.println(s);
    }
    return successful ? 0 : 1;
  }
}
