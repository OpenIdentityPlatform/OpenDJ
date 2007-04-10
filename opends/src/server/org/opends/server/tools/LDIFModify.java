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



import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ObjectClass;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.util.DeleteChangeRecordEntry;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



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
                                   List<String> errorList)
         throws IOException, LDIFException
  {
    // Read the changes into memory.
    TreeMap<DN,AddChangeRecordEntry> adds =
          new TreeMap<DN,AddChangeRecordEntry>();
    TreeMap<DN,Entry> ldifEntries =
          new TreeMap<DN,Entry>();
    HashMap<DN,DeleteChangeRecordEntry> deletes =
         new HashMap<DN,DeleteChangeRecordEntry>();
    HashMap<DN,LinkedList<Modification>> modifications =
         new HashMap<DN,LinkedList<Modification>>();

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
          errorList.add(le.getMessage());
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
            int msgID = MSGID_LDIFMODIFY_CANNOT_ADD_ENTRY_TWICE;
            errorList.add(getMessage(msgID, String.valueOf(changeDN)));
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
            int msgID = MSGID_LDIFMODIFY_CANNOT_DELETE_AFTER_ADD;
            errorList.add(getMessage(msgID, String.valueOf(changeDN)));
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
            int msgID = MSGID_LDIFMODIFY_CANNOT_MODIFY_ADDED_OR_DELETED;
            errorList.add(getMessage(msgID, String.valueOf(changeDN)));
            continue;
          }
          else
          {
            LinkedList<Modification> mods =
                 modifications.get(changeDN);
            if (mods == null)
            {
              mods = new LinkedList<Modification>();
              modifications.put(changeDN, mods);
            }

            for (LDAPModification mod :
                 ((ModifyChangeRecordEntry) changeRecord).getModifications())
            {
              try
              {
                mods.add(mod.toModification());
              }
              catch (LDAPException le)
              {
                errorList.add(le.getMessage());
                continue;
              }
            }
          }
          break;

        case MODIFY_DN:
          int msgID = MSGID_LDIFMODIFY_MODDN_NOT_SUPPORTED;
          errorList.add(getMessage(msgID, String.valueOf(changeDN)));
          continue;

        default:
          msgID = MSGID_LDIFMODIFY_UNKNOWN_CHANGETYPE;
          errorList.add(getMessage(msgID, String.valueOf(changeDN),
               String.valueOf(changeRecord.getChangeOperationType())));
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
          errorList.add(le.getMessage());
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
      DN entryDN = entry.getDN();
      if (deletes.remove(entryDN) != null)
      {
        continue;
      }


      // If the entry is to be added, then that's an error, since it already
      // exists.
      if (adds.remove(entryDN) != null)
      {
        int msgID = MSGID_LDIFMODIFY_ADD_ALREADY_EXISTS;
        errorList.add(getMessage(msgID, String.valueOf(entryDN)));
        continue;
      }


      // If the entry is to be modified, then process the changes.
      LinkedList<Modification> mods = modifications.remove(entryDN);
      if ((mods != null) && (! mods.isEmpty()))
      {
        try
        {
          entry.applyModifications(mods);
        }
        catch (DirectoryException de)
        {
          errorList.add(de.getErrorMessage());
          continue;
        }
      }


      // If we've gotten here, then the (possibly updated) entry should be
      // written to the LDIF entry Map.
      ldifEntries.put(entry.getDN(),entry);
    }


    // Perform any adds that may be necessary.
    for (AddChangeRecordEntry add : adds.values())
    {
      Map<ObjectClass,String> objectClasses =
           new LinkedHashMap<ObjectClass,String>();
      Map<AttributeType,List<Attribute>> userAttributes =
           new LinkedHashMap<AttributeType,List<Attribute>>();
      Map<AttributeType,List<Attribute>> operationalAttributes =
           new LinkedHashMap<AttributeType,List<Attribute>>();

      for (Attribute a : add.getAttributes())
      {
        AttributeType t = a.getAttributeType();
        if (t.isObjectClassType())
        {
          for (AttributeValue v : a.getValues())
          {
            String stringValue = v.getStringValue();
            String lowerValue  = toLowerCase(stringValue);
            ObjectClass oc = DirectoryServer.getObjectClass(lowerValue, true);
            objectClasses.put(oc, stringValue);
          }
        }
        else if (t.isOperational())
        {
          List<Attribute> attrList = operationalAttributes.get(t);
          if (attrList == null)
          {
            attrList = new LinkedList<Attribute>();
            operationalAttributes.put(t, attrList);
          }
          attrList.add(a);
        }
        else
        {
          List<Attribute> attrList = userAttributes.get(t);
          if (attrList == null)
          {
            attrList = new LinkedList<Attribute>();
            userAttributes.put(t, attrList);
          }
          attrList.add(a);
        }
      }

      Entry e = new Entry(add.getDN(), objectClasses, userAttributes,
                          operationalAttributes);
      //Put the entry to be added into the LDIF entry map.
      ldifEntries.put(e.getDN(),e);
    }


    // If there are any entries left in the delete or modify lists, then that's
    // a problem because they didn't exist.
    if (! deletes.isEmpty())
    {
      for (DN dn : deletes.keySet())
      {
        int msgID = MSGID_LDIFMODIFY_DELETE_NO_SUCH_ENTRY;
        errorList.add(getMessage(msgID, String.valueOf(dn)));
      }
    }

    if (! modifications.isEmpty())
    {
      for (DN dn : modifications.keySet())
      {
        int msgID = MSGID_LDIFMODIFY_MODIFY_NO_SUCH_ENTRY;
        errorList.add(getMessage(msgID, String.valueOf(dn)));
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
    int returnCode = ldifModifyMain(args, false);
    if (returnCode != 0)
    {
      System.exit(returnCode);
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
   *
   * @return  A value of zero if everything completed properly, or nonzero if
   *          any problem(s) occurred.
   */
  public static int ldifModifyMain(String[] args, boolean serverInitialized)
  {
    // Prepare the argument parser.
    BooleanArgument showUsage;
    StringArgument  changesFile;
    StringArgument  configClass;
    StringArgument  configFile;
    StringArgument  sourceFile;
    StringArgument  targetFile;

    String toolDescription = getMessage(MSGID_LDIFMODIFY_TOOL_DESCRIPTION);
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);

    try
    {
      configFile = new StringArgument("configfile", 'c', "configFile", true,
                                      false, true, "{configFile}", null, null,
                                      MSGID_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                             OPTION_LONG_CONFIG_CLASS, false,
                             false, true, OPTION_VALUE_CONFIG_CLASS,
                             ConfigFileHandler.class.getName(), null,
                             MSGID_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      sourceFile = new StringArgument("sourceldif", 's', "sourceLDIF", true,
                                      false, true, "{file}", null, null,
                                      MSGID_LDIFMODIFY_DESCRIPTION_SOURCE);
      argParser.addArgument(sourceFile);


      changesFile = new StringArgument("changesldif", 'm', "changesLDIF", true,
                                       false, true, "{file}", null, null,
                                       MSGID_LDIFMODIFY_DESCRIPTION_CHANGES);
      argParser.addArgument(changesFile);


      targetFile = new StringArgument("targetldif", 't', "targetLDIF", true,
                                      false, true, "{file}", null, null,
                                      MSGID_LDIFMODIFY_DESCRIPTION_TARGET);
      argParser.addArgument(targetFile);


      showUsage = new BooleanArgument("help", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      MSGID_LDIFMODIFY_DESCRIPTION_HELP);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
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
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
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


    if (! serverInitialized)
    {
      // Bootstrap the Directory Server configuration for use as a client.
      DirectoryServer directoryServer = DirectoryServer.getInstance();
      directoryServer.bootstrapClient();


      // If we're to use the configuration then initialize it, along with the
      // schema.
      boolean checkSchema = configFile.isPresent();
      if (checkSchema)
      {
        try
        {
          directoryServer.initializeJMX();
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LDIFMODIFY_CANNOT_INITIALIZE_JMX;
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
          int    msgID   = MSGID_LDIFMODIFY_CANNOT_INITIALIZE_CONFIG;
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
          int    msgID   = MSGID_LDIFMODIFY_CANNOT_INITIALIZE_SCHEMA;
          String message = getMessage(msgID,
                                      String.valueOf(configFile.getValue()),
                                      e.getMessage());
          System.err.println(message);
          return 1;
        }
      }
    }


    // Create the LDIF readers and writer from the arguments.
    File source = new File(sourceFile.getValue());
    if (! source.exists())
    {
      int    msgID   = MSGID_LDIFMODIFY_SOURCE_DOES_NOT_EXIST;
      String message = getMessage(msgID, sourceFile.getValue());
      System.err.println(message);
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }

    LDIFImportConfig importConfig = new LDIFImportConfig(sourceFile.getValue());
    LDIFReader sourceReader;
    try
    {
      sourceReader = new LDIFReader(importConfig);
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDIFMODIFY_CANNOT_OPEN_SOURCE;
      String message = getMessage(msgID, sourceFile.getValue(),
                                  String.valueOf(ioe));
      System.err.println(message);
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
    }


    File changes = new File(changesFile.getValue());
    if (! changes.exists())
    {
      int    msgID   = MSGID_LDIFMODIFY_CHANGES_DOES_NOT_EXIST;
      String message = getMessage(msgID, changesFile.getValue());
      System.err.println(message);
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }

    importConfig = new LDIFImportConfig(changesFile.getValue());
    LDIFReader changeReader;
    try
    {
      changeReader = new LDIFReader(importConfig);
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDIFMODIFY_CANNOT_OPEN_CHANGES;
      String message = getMessage(msgID, sourceFile.getValue());
      System.err.println(message);
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
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
      int    msgID   = MSGID_LDIFMODIFY_CANNOT_OPEN_TARGET;
      String message = getMessage(msgID, sourceFile.getValue());
      System.err.println(message);
      return LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
    }


    // Actually invoke the LDIF procesing.
    LinkedList<String> errorList = new LinkedList<String>();
    boolean successful;
    try
    {
      successful = modifyLDIF(sourceReader, changeReader, targetWriter,
                              errorList);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFMODIFY_ERROR_PROCESSING_LDIF;
      String message = getMessage(msgID, String.valueOf(e));
      System.err.println(message);

      successful = false;
    }

    try
    {
      sourceReader.close();
    } catch (Exception e) {}

    try
    {
      changeReader.close();
    } catch (Exception e) {}

    try
    {
      targetWriter.close();
    } catch (Exception e) {}

    for (String s : errorList)
    {
      System.err.println(s);
    }
    return (successful ? 0 : 1);
  }
}

