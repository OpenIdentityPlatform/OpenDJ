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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugVerbose;
import static org.opends.server.loggers.debug.DebugLogger.debugInfo;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugProtocolElement;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.toLowerCase;
import static org.opends.server.util.Validator.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.plugin.LDIFPluginResult;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;


/**
 * This class provides the ability to read information from an LDIF file.  It
 * provides support for both standard entries and change entries (as would be
 * used with a tool like ldapmodify).
 */
public final class LDIFReader
{



  // The reader that will be used to read the data.
  private BufferedReader reader;

  // The buffer to use to read data from a URL.
  private byte[] buffer;

  // The import configuration that specifies what should be imported.
  private LDIFImportConfig importConfig;

  // The lines that comprise the body of the last entry read.
  private LinkedList<StringBuilder> lastEntryBodyLines;

  // The lines that comprise the header (DN and any comments) for the last entry
  // read.
  private LinkedList<StringBuilder> lastEntryHeaderLines;

  // The number of entries that have been ignored by this LDIF reader because
  // they didn't match the criteria.
  private long entriesIgnored;

  // The number of entries that have been read by this LDIF reader, including
  // those that were ignored because they didn't match the criteria, and
  // including those that were rejected because they were invalid in some way.
  private long entriesRead;

  // The number of entries that have been rejected by this LDIF reader.
  private long entriesRejected;

  // The line number on which the last entry started.
  private long lastEntryLineNumber;

  // The line number of the last line read from the LDIF file, starting with 1.
  private long lineNumber;

  // The plugin config manager that will be used if we are to invoke plugins
  // on the entries as they are read.
  private PluginConfigManager pluginConfigManager;



  /**
   * Creates a new LDIF reader that will read information from the specified
   * file.
   *
   * @param  importConfig  The import configuration for this LDIF reader.  It
   *                       must not be <CODE>null</CODE>.
   *
   * @throws  IOException  If a problem occurs while opening the LDIF file for
   *                       reading.
   */
  public LDIFReader(LDIFImportConfig importConfig)
         throws IOException
  {

    ensureNotNull(importConfig);
    this.importConfig = importConfig;

    reader               = importConfig.getReader();
    buffer               = new byte[4096];
    entriesRead          = 0;
    entriesIgnored       = 0;
    entriesRejected      = 0;
    lineNumber           = 0;
    lastEntryLineNumber  = -1;
    lastEntryBodyLines   = new LinkedList<StringBuilder>();
    lastEntryHeaderLines = new LinkedList<StringBuilder>();
    pluginConfigManager  = DirectoryServer.getPluginConfigManager();
  }



  /**
   * Reads the next entry from the LDIF source.
   *
   * @return  The next entry read from the LDIF source, or <CODE>null</CODE> if
   *          the end of the LDIF data is reached.
   *
   * @throws  IOException  If an I/O problem occurs while reading from the file.
   *
   * @throws  LDIFException  If the information read cannot be parsed as an LDIF
   *                         entry.
   */
  public Entry readEntry()
         throws IOException, LDIFException
  {

    return readEntry(importConfig.validateSchema());
  }



  /**
   * Reads the next entry from the LDIF source.
   *
   * @param  checkSchema  Indicates whether this reader should perform schema
   *                      checking on the entry before returning it to the
   *                      caller.  Note that some basic schema checking (like
   *                      refusing multiple values for a single-valued
   *                      attribute) may always be performed.
   *
   *
   * @return  The next entry read from the LDIF source, or <CODE>null</CODE> if
   *          the end of the LDIF data is reached.
   *
   * @throws  IOException  If an I/O problem occurs while reading from the file.
   *
   * @throws  LDIFException  If the information read cannot be parsed as an LDIF
   *                         entry.
   */
  public Entry readEntry(boolean checkSchema)
         throws IOException, LDIFException
  {


    while (true)
    {
      // Read the set of lines that make up the next entry.
      LinkedList<StringBuilder> lines = readEntryLines();
      if (lines == null)
      {
        return null;
      }
      lastEntryBodyLines   = lines;
      lastEntryHeaderLines = new LinkedList<StringBuilder>();


      // Read the DN of the entry and see if it is one that should be included
      // in the import.
      DN entryDN = readDN(lines);
      if (entryDN == null)
      {
        // This should only happen if the LDIF starts with the "version:" line
        // and has a blank line immediately after that.  In that case, simply
        // read and return the next entry.
        continue;
      }
      else if (!importConfig.includeEntry(entryDN))
      {
        if (debugEnabled())
        {
          debugInfo("Skipping entry %s because the DN is not one that should " +
              "be included based on the include and exclude branches.",
                    entryDN);
        }
        entriesRead++;
        entriesIgnored++;
        continue;
      }
      else
      {
        entriesRead++;
      }

      // Read the set of attributes from the entry.
      HashMap<ObjectClass,String> objectClasses =
           new HashMap<ObjectClass,String>();
      HashMap<AttributeType,List<Attribute>> userAttributes =
           new HashMap<AttributeType,List<Attribute>>();
      HashMap<AttributeType,List<Attribute>> operationalAttributes =
           new HashMap<AttributeType,List<Attribute>>();
      try
      {
        for (StringBuilder line : lines)
        {
          readAttribute(lines, line, entryDN, objectClasses, userAttributes,
                        operationalAttributes);
        }
      }
      catch (LDIFException e)
      {
        entriesRejected++;
        throw e;
      }

      // Create the entry and see if it is one that should be included in the
      // import.
      Entry entry =  new Entry(entryDN, objectClasses, userAttributes,
                               operationalAttributes);
      debugProtocolElement(DebugLogLevel.VERBOSE, entry);

      try
      {
        if (! importConfig.includeEntry(entry))
        {
          if (debugEnabled())
          {
            debugInfo("Skipping entry %s because the DN is not one that " +
                "should be included based on the include and exclude filters.",
                      entryDN);
          }
          entriesIgnored++;
          continue;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_IMPORT;
        String message = getMessage(msgID, String.valueOf(entry.getDN()),
                                    lastEntryLineNumber, String.valueOf(e));
        throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
      }


      // If we should invoke import plugins, then do so.
      if (importConfig.invokeImportPlugins())
      {
        LDIFPluginResult pluginResult =
             pluginConfigManager.invokeLDIFImportPlugins(importConfig, entry);
        if (! pluginResult.continueEntryProcessing())
        {
          entriesIgnored++;
          continue;
        }
      }


      // Make sure that the entry is valid as per the server schema if it is
      // appropriate to do so.
      if (checkSchema)
      {
        StringBuilder invalidReason = new StringBuilder();
        if (! entry.conformsToSchema(null, false, true, false, invalidReason))
        {
          int    msgID   = MSGID_LDIF_SCHEMA_VIOLATION;
          String message = getMessage(msgID, String.valueOf(entryDN),
                                      lastEntryLineNumber,
                                      invalidReason.toString());
          logToRejectWriter(lines, message);
          entriesRejected++;
          throw new LDIFException(msgID, message, lastEntryLineNumber, true);
        }
      }


      // The entry should be included in the import, so return it.
      return entry;
    }
  }

  /**
   * Reads the next change record from the LDIF source.
   *
   * @param  defaultAdd  Indicates whether the change type should default to
   *                     "add" if none is explicitly provided.
   *
   * @return  The next change record from the LDIF source, or <CODE>null</CODE>
   *          if the end of the LDIF data is reached.
   *
   * @throws  IOException  If an I/O problem occurs while reading from the file.
   *
   * @throws  LDIFException  If the information read cannot be parsed as an LDIF
   *                         entry.
   */
  public ChangeRecordEntry readChangeRecord(boolean defaultAdd)
         throws IOException, LDIFException
  {

    while (true)
    {
      // Read the set of lines that make up the next entry.
      LinkedList<StringBuilder> lines = readEntryLines();
      if (lines == null)
      {
        return null;
      }


      // Read the DN of the entry and see if it is one that should be included
      // in the import.
      DN entryDN = readDN(lines);
      if (entryDN == null)
      {
        // This should only happen if the LDIF starts with the "version:" line
        // and has a blank line immediately after that.  In that case, simply
        // read and return the next entry.
        continue;
      }

      String changeType = readChangeType(lines);

      ChangeRecordEntry entry = null;

      if(changeType != null)
      {
        if(changeType.equals("add"))
        {
          entry = parseAddChangeRecordEntry(entryDN, lines);
        } else if (changeType.equals("delete"))
        {
          entry = parseDeleteChangeRecordEntry(entryDN, lines);
        } else if (changeType.equals("modify"))
        {
          entry = parseModifyChangeRecordEntry(entryDN, lines);
        } else if (changeType.equals("modrdn"))
        {
          entry = parseModifyDNChangeRecordEntry(entryDN, lines);
        } else if (changeType.equals("moddn"))
        {
          entry = parseModifyDNChangeRecordEntry(entryDN, lines);
        } else
        {
          int msgID = MSGID_LDIF_INVALID_CHANGETYPE_ATTRIBUTE;
          String message = getMessage(msgID, changeType,
            "add, delete, modify, moddn, modrdn");
          throw new LDIFException(msgID, message, lastEntryLineNumber, false);
        }
      } else
      {
        // default to "add"?
        if(defaultAdd)
        {
          entry = parseAddChangeRecordEntry(entryDN, lines);
        } else
        {
          int msgID = MSGID_LDIF_INVALID_CHANGETYPE_ATTRIBUTE;
          String message = getMessage(msgID, null,
            "add, delete, modify, moddn, modrdn");
          throw new LDIFException(msgID, message, lastEntryLineNumber, false);
        }
      }

      return entry;
    }
  }



  /**
   * Reads a set of lines from the next entry in the LDIF source.
   *
   * @return  A set of lines from the next entry in the LDIF source.
   *
   * @throws  IOException  If a problem occurs while reading from the LDIF
   *                       source.
   *
   * @throws  LDIFException  If the information read is not valid LDIF.
   */
  private LinkedList<StringBuilder> readEntryLines()
          throws IOException, LDIFException
  {

    // Read the entry lines into a buffer.
    LinkedList<StringBuilder> lines = new LinkedList<StringBuilder>();
    int lastLine = -1;

    while (true)
    {
      String line = reader.readLine();
      lineNumber++;

      if (line == null)
      {
        // This must mean that we have reached the end of the LDIF source.
        // If the set of lines read so far is empty, then move onto the next
        // file or return null.  Otherwise, break out of this loop.
        if (lines.isEmpty())
        {
          reader = importConfig.nextReader();
          if (reader == null)
          {
            return null;
          }
          else
          {
            return readEntryLines();
          }
        }
        else
        {
          break;
        }
      }
      else if (line.length() == 0)
      {
        // This is a blank line.  If the set of lines read so far is empty,
        // then just skip over it.  Otherwise, break out of this loop.
        if (lines.isEmpty())
        {
          continue;
        }
        else
        {
          break;
        }
      }
      else if (line.charAt(0) == '#')
      {
        // This is a comment.  Ignore it.
        continue;
      }
      else if ((line.charAt(0) == ' ') || (line.charAt(0) == '\t'))
      {
        // This is a continuation of the previous line.  If there is no
        // previous line, then that's a problem.  Note that while RFC 2849
        // technically only allows a space in this position, both OpenLDAP and
        // the Sun Java System Directory Server allow a tab as well, so we will
        // too for compatibility reasons.  See issue #852 for details.
        if (lastLine >= 0)
        {
          lines.get(lastLine).append(line.substring(1));
        }
        else
        {
          int    msgID   = MSGID_LDIF_INVALID_LEADING_SPACE;
          String message = getMessage(msgID, lineNumber, line);
          logToRejectWriter(lines, message);
          throw new LDIFException(msgID, message, lineNumber, false);
        }
      }
      else
      {
        // This is a new line.
        if (lines.isEmpty())
        {
          lastEntryLineNumber = lineNumber;
        }
        lines.add(new StringBuilder(line));
        lastLine++;
      }
    }


    return lines;
  }



  /**
   * Reads the DN of the entry from the provided list of lines.  The DN must be
   * the first line in the list, unless the first line starts with "version",
   * in which case the DN should be the second line.
   *
   * @param  lines  The set of lines from which the DN should be read.
   *
   * @return  The decoded entry DN.
   *
   * @throws  LDIFException  If DN is not the first element in the list (or the
   *                         second after the LDIF version), or if a problem
   *                         occurs while trying to parse it.
   */
  private DN readDN(LinkedList<StringBuilder> lines)
          throws LDIFException
  {

    if (lines.isEmpty())
    {
      // This is possible if the contents of the first "entry" were just
      // the version identifier.  If that is the case, then return null and
      // use that as a signal to the caller to go ahead and read the next entry.
      return null;
    }

    StringBuilder line = lines.remove();
    lastEntryHeaderLines.add(line);
    int colonPos = line.indexOf(":");
    if (colonPos <= 0)
    {
      int    msgID   = MSGID_LDIF_NO_ATTR_NAME;
      String message = getMessage(msgID, lastEntryLineNumber, line.toString());

      logToRejectWriter(lines, message);

      throw new LDIFException(msgID, message, lastEntryLineNumber, true);
    }

    String attrName = toLowerCase(line.substring(0, colonPos));
    if (attrName.equals("version"))
    {
      // This is the version line, and we can skip it.
      return readDN(lines);
    }
    else if (! attrName.equals("dn"))
    {
      int    msgID   = MSGID_LDIF_NO_DN;
      String message = getMessage(msgID, lastEntryLineNumber, line.toString());

      logToRejectWriter(lines, message);

      throw new LDIFException(msgID, message, lastEntryLineNumber, true);
    }


    // Look at the character immediately after the colon.  If there is none,
    // then assume the null DN.  If it is another colon, then the DN must be
    // base64-encoded.  Otherwise, it may be one or more spaces.
    int length = line.length();
    if (colonPos == (length-1))
    {
      return DN.nullDN();
    }

    if (line.charAt(colonPos+1) == ':')
    {
      // The DN is base64-encoded.  Find the first non-blank character and
      // take the rest of the line, base64-decode it, and parse it as a DN.
      int pos = colonPos+2;
      while ((pos < length) && (line.charAt(pos) == ' '))
      {
        pos++;
      }

      String encodedDNStr = line.substring(pos);

      String dnStr;
      try
      {
        dnStr = new String(Base64.decode(encodedDNStr), "UTF-8");
      }
      catch (Exception e)
      {
        // The value did not have a valid base64-encoding.
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_LDIF_COULD_NOT_BASE64_DECODE_DN;
        String message = getMessage(msgID, lastEntryLineNumber, line,
                                    String.valueOf(e));

        logToRejectWriter(lines, message);

        throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
      }

      try
      {
        return DN.decode(dnStr);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }

        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lastEntryLineNumber, line.toString(),
                                    de.getErrorMessage());

        logToRejectWriter(lines, message);

        throw new LDIFException(msgID, message, lastEntryLineNumber, true, de);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lastEntryLineNumber, line.toString(),
                                    String.valueOf(e));

        logToRejectWriter(lines, message);

        throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
      }
    }
    else
    {
      // The rest of the value should be the DN.  Skip over any spaces and
      // attempt to decode the rest of the line as the DN.
      int pos = colonPos+1;
      while ((pos < length) && (line.charAt(pos) == ' '))
      {
        pos++;
      }

      String dnString = line.substring(pos);

      try
      {
        return DN.decode(dnString);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }

        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lastEntryLineNumber, line.toString(),
                                    de.getErrorMessage());

        logToRejectWriter(lines, message);

        throw new LDIFException(msgID, message, lastEntryLineNumber, true, de);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lastEntryLineNumber, line.toString(),
                                    String.valueOf(e));

        logToRejectWriter(lines, message);

        throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
      }
    }
  }



  /**
   * Reads the changetype of the entry from the provided list of lines.  If
   * there is no changetype attribute then an add is assumed.
   *
   * @param  lines  The set of lines from which the DN should be read.
   *
   * @return  The decoded entry DN.
   *
   * @throws  LDIFException  If DN is not the first element in the list (or the
   *                         second after the LDIF version), or if a problem
   *                         occurs while trying to parse it.
   */
  private String readChangeType(LinkedList<StringBuilder> lines)
          throws LDIFException
  {

    if (lines.isEmpty())
    {
      // Error. There must be other entries.
      return null;
    }

    StringBuilder line = lines.get(0);
    lastEntryHeaderLines.add(line);
    int colonPos = line.indexOf(":");
    if (colonPos <= 0)
    {
      int    msgID   = MSGID_LDIF_NO_ATTR_NAME;
      String message = getMessage(msgID, lastEntryLineNumber, line.toString());
      logToRejectWriter(lines, message);
      throw new LDIFException(msgID, message, lastEntryLineNumber, true);
    }

    String attrName = toLowerCase(line.substring(0, colonPos));
    if (! attrName.equals("changetype"))
    {
      // No changetype attribute - return null
      return null;
    } else
    {
      // Remove the line
      lines.remove();
    }


    // Look at the character immediately after the colon.  If there is none,
    // then no value was specified. Throw an exception
    int length = line.length();
    if (colonPos == (length-1))
    {
      int msgID = MSGID_LDIF_INVALID_CHANGETYPE_ATTRIBUTE;
      String message = getMessage(msgID, null,
        "add, delete, modify, moddn, modrdn");
      throw new LDIFException(msgID, message, lastEntryLineNumber, false );
    }

    if (line.charAt(colonPos+1) == ':')
    {
      // The change type is base64-encoded.  Find the first non-blank
      // character and
      // take the rest of the line, and base64-decode it.
      int pos = colonPos+2;
      while ((pos < length) && (line.charAt(pos) == ' '))
      {
        pos++;
      }

      String encodedChangeTypeStr = line.substring(pos);

      String changeTypeStr;
      try
      {
        changeTypeStr = new String(Base64.decode(encodedChangeTypeStr),
            "UTF-8");
      }
      catch (Exception e)
      {
        // The value did not have a valid base64-encoding.
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_LDIF_COULD_NOT_BASE64_DECODE_DN;
        String message = getMessage(msgID, lastEntryLineNumber, line,
                                    String.valueOf(e));
        logToRejectWriter(lines, message);
        throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
      }

      return changeTypeStr;
    }
    else
    {
      // The rest of the value should be the changetype.
      // Skip over any spaces and
      // attempt to decode the rest of the line as the changetype string.
      int pos = colonPos+1;
      while ((pos < length) && (line.charAt(pos) == ' '))
      {
        pos++;
      }

      String changeTypeString = line.substring(pos);

      return changeTypeString;
    }
  }


  /**
   * Decodes the provided line as an LDIF attribute and adds it to the
   * appropriate hash.
   *
   * @param  lines                  The full set of lines that comprise the
   *                                entry (used for writing reject information).
   * @param  line                   The line to decode.
   * @param  entryDN                The DN of the entry being decoded.
   * @param  objectClasses          The set of objectclasses decoded so far for
   *                                the current entry.
   * @param  userAttributes         The set of user attributes decoded so far
   *                                for the current entry.
   * @param  operationalAttributes  The set of operational attributes decoded so
   *                                far for the current entry.
   *
   * @throws  LDIFException  If a problem occurs while trying to decode the
   *                         attribute contained in the provided entry.
   */
  private void readAttribute(LinkedList<StringBuilder> lines,
       StringBuilder line, DN entryDN,
       HashMap<ObjectClass,String> objectClasses,
       HashMap<AttributeType,List<Attribute>> userAttributes,
       HashMap<AttributeType,List<Attribute>> operationalAttributes)
          throws LDIFException
  {

    // Parse the attribute type description.
    int colonPos = parseColonPosition(lines, line);
    String attrDescr = line.substring(0, colonPos);
    Attribute attribute = parseAttrDescription(attrDescr);
    String attrName = attribute.getName();
    String lowerName = toLowerCase(attrName);
    LinkedHashSet<String> options = attribute.getOptions();

    // Now parse the attribute value.
    ASN1OctetString value = parseSingleValue(lines, line, entryDN,
        colonPos, attrName);

    // See if this is an objectclass or an attribute.  Then get the
    // corresponding definition and add the value to the appropriate hash.
    if (lowerName.equals("objectclass"))
    {
      if (! importConfig.includeObjectClasses())
      {
        if (debugEnabled())
        {
          debugVerbose("Skipping objectclass %s for entry %s due to the " +
              "import configuration.", value, entryDN);
        }
        return;
      }

      String ocName      = value.stringValue();
      String lowerOCName = toLowerCase(ocName);

      ObjectClass objectClass = DirectoryServer.getObjectClass(lowerOCName);
      if (objectClass == null)
      {
        objectClass = DirectoryServer.getDefaultObjectClass(ocName);
      }

      if (objectClasses.containsKey(objectClass))
      {
        logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.MILD_WARNING,
                 MSGID_LDIF_DUPLICATE_OBJECTCLASS, String.valueOf(entryDN),
                 lastEntryLineNumber, ocName);
      }
      else
      {
        objectClasses.put(objectClass, ocName);
      }
    }
    else
    {
      AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
      if (attrType == null)
      {
        attrType = DirectoryServer.getDefaultAttributeType(attrName);
      }


      if (! importConfig.includeAttribute(attrType))
      {
        if (debugEnabled())
        {
          debugVerbose("Skipping attribute %s for entry %s due to the import " +
              "configuration.", attrName, entryDN);
        }
        return;
      }


      AttributeValue attributeValue = new AttributeValue(attrType, value);
      List<Attribute> attrList;
      if (attrType.isOperational())
      {
        attrList = operationalAttributes.get(attrType);
        if (attrList == null)
        {
          LinkedHashSet<AttributeValue> valueSet =
               new LinkedHashSet<AttributeValue>();
          valueSet.add(attributeValue);

          attrList = new ArrayList<Attribute>();
          attrList.add(new Attribute(attrType, attrName, options, valueSet));
          operationalAttributes.put(attrType, attrList);
          return;
        }
      }
      else
      {
        attrList = userAttributes.get(attrType);
        if (attrList == null)
        {
          LinkedHashSet<AttributeValue> valueSet =
               new LinkedHashSet<AttributeValue>();
          valueSet.add(attributeValue);

          attrList = new ArrayList<Attribute>();
          attrList.add(new Attribute(attrType, attrName, options, valueSet));
          userAttributes.put(attrType, attrList);
          return;
        }
      }


      // Check to see if any of the attributes in the list have the same set of
      // options.  If so, then try to add a value to that attribute.
      for (Attribute a : attrList)
      {
        if (a.optionsEqual(options))
        {
          LinkedHashSet<AttributeValue> valueSet = a.getValues();
          if (valueSet.contains(attributeValue))
          {
            int    msgID   = MSGID_LDIF_DUPLICATE_ATTR;
            String message = getMessage(msgID, String.valueOf(entryDN),
                                        lastEntryLineNumber, attrName,
                                        value.stringValue());
            logToRejectWriter(lines, message);
            throw new LDIFException(msgID, message, lastEntryLineNumber, true);
          }
          else if (attrType.isSingleValue() && (! valueSet.isEmpty()))
          {
            int    msgID   = MSGID_LDIF_MULTIPLE_VALUES_FOR_SINGLE_VALUED_ATTR;
            String message = getMessage(msgID, String.valueOf(entryDN),
                                        lastEntryLineNumber, attrName);
            logToRejectWriter(lines, message);
            throw new LDIFException(msgID, message, lastEntryLineNumber, true);
          }
          else
          {
            valueSet.add(attributeValue);
            return;
          }
        }
      }


      // No set of matching options was found, so create a new one and add it to
      // the list.
      LinkedHashSet<AttributeValue> valueSet =
           new LinkedHashSet<AttributeValue>();
      valueSet.add(attributeValue);
      attrList.add(new Attribute(attrType, attrName, options, valueSet));
      return;
    }
  }



  /**
   * Decodes the provided line as an LDIF attribute and returns the
   * Attribute (name and values) for the specified attribute name.
   *
   * @param  lines                  The full set of lines that comprise the
   *                                entry (used for writing reject information).
   * @param  line                   The line to decode.
   * @param  entryDN                The DN of the entry being decoded.
   * @param  attributeName          The name and options of the attribute to
   *                                return the values for.
   *
   * @return                        The attribute in octet string form.
   * @throws  LDIFException         If a problem occurs while trying to decode
   *                                the attribute contained in the provided
   *                                entry or if the parsed attribute name does
   *                                not match the specified attribute name.
   */
  private Attribute readSingleValueAttribute(
       LinkedList<StringBuilder> lines, StringBuilder line, DN entryDN,
       String attributeName) throws LDIFException
  {

    // Parse the attribute type description.
    int colonPos = parseColonPosition(lines, line);
    String attrDescr = line.substring(0, colonPos);
    Attribute attribute = parseAttrDescription(attrDescr);
    String attrName = attribute.getName();

    if (attributeName != null)
    {
      Attribute expectedAttr = parseAttrDescription(attributeName);

      if (!attribute.equals(expectedAttr))
      {
        int msgID = MSGID_LDIF_INVALID_CHANGERECORD_ATTRIBUTE;
        String message = getMessage(msgID, attrDescr, attributeName);
        throw new LDIFException(msgID, message, lastEntryLineNumber, false);
      }
    }

    //  Now parse the attribute value.
    ASN1OctetString value = parseSingleValue(lines, line, entryDN,
        colonPos, attrName);

    AttributeType attrType = attribute.getAttributeType();
    AttributeValue attributeValue = new AttributeValue(attrType, value);
    attribute.getValues().add(attributeValue);

    return attribute;
  }


  /**
   * Retrieves the starting line number for the last entry read from the LDIF
   * source.
   *
   * @return  The starting line number for the last entry read from the LDIF
   *          source.
   */
  public long getLastEntryLineNumber()
  {

    return lastEntryLineNumber;
  }



  /**
   * Rejects the last entry read from the LDIF.  This method is intended for use
   * by components that perform their own validation of entries (e.g., backends
   * during import processing) in which the entry appeared valid to the LDIF
   * reader but some other problem was encountered.
   *
   * @param  message  A human-readable message providing the reason that the
   *                  last entry read was not acceptable.
   */
  public void rejectLastEntry(String message)
  {

    entriesRejected++;

    BufferedWriter rejectWriter = importConfig.getRejectWriter();
    if (rejectWriter != null)
    {
      try
      {
        if ((message != null) && (message.length() > 0))
        {
          rejectWriter.write("# ");
          rejectWriter.write(message);
          rejectWriter.newLine();
        }

        for (StringBuilder sb : lastEntryHeaderLines)
        {
          rejectWriter.write(sb.toString());
          rejectWriter.newLine();
        }

        for (StringBuilder sb : lastEntryBodyLines)
        {
          rejectWriter.write(sb.toString());
          rejectWriter.newLine();
        }

        rejectWriter.newLine();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Closes this LDIF reader and the underlying file or input stream.
   */
  public void close()
  {

    importConfig.close();
  }



  /**
   * Parse an AttributeDescription (an attribute type name and its options).
   * @param attrDescr The attribute description to be parsed.
   * @return A new attribute with no values, representing the attribute type
   * and its options.
   */
  private static Attribute parseAttrDescription(String attrDescr)
  {

    String attrName;
    String lowerName;
    LinkedHashSet<String> options;
    int semicolonPos = attrDescr.indexOf(';');
    if (semicolonPos > 0)
    {
      attrName = attrDescr.substring(0, semicolonPos);
      options = new LinkedHashSet<String>();
      int nextPos = attrDescr.indexOf(';', semicolonPos+1);
      while (nextPos > 0)
      {
        String option = attrDescr.substring(semicolonPos+1, nextPos);
        if (option.length() > 0)
        {
          options.add(option);
          semicolonPos = nextPos;
          nextPos = attrDescr.indexOf(';', semicolonPos+1);
        }
      }

      String option = attrDescr.substring(semicolonPos+1);
      if (option.length() > 0)
      {
        options.add(option);
      }
    }
    else
    {
      attrName  = attrDescr;
      options   = null;
    }

    lowerName = toLowerCase(attrName);
    AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(attrName);
    }

    return new Attribute(attrType, attrName, options, null);
  }



  /**
   * Retrieves the total number of entries read so far by this LDIF reader,
   * including those that have been ignored or rejected.
   *
   * @return  The total number of entries read so far by this LDIF reader.
   */
  public long getEntriesRead()
  {

    return entriesRead;
  }



  /**
   * Retrieves the total number of entries that have been ignored so far by this
   * LDIF reader because they did not match the import criteria.
   *
   * @return  The total number of entries ignored so far by this LDIF reader.
   */
  public long getEntriesIgnored()
  {

    return entriesIgnored;
  }



  /**
   * Retrieves the total number of entries rejected so far by this LDIF reader.
   * This  includes both entries that were rejected because  of internal
   * validation failure (e.g., they didn't conform to the defined  server
   * schema) or an external validation failure (e.g., the component using this
   * LDIF reader didn't accept the entry because it didn't have a parent).
   *
   * @return  The total number of entries rejected so far by this LDIF reader.
   */
  public long getEntriesRejected()
  {

    return entriesRejected;
  }



  /**
   * Parse a modifyDN change record entry from LDIF.
   *
   * @param entryDN
   *          The name of the entry being modified.
   * @param lines
   *          The lines to parse.
   * @return Returns the parsed modifyDN change record entry.
   * @throws LDIFException
   *           If there was an error when parsing the change record.
   */
  private ChangeRecordEntry parseModifyDNChangeRecordEntry(DN entryDN,
      LinkedList<StringBuilder> lines) throws LDIFException {

    DN newSuperiorDN = null;
    RDN newRDN = null;
    boolean deleteOldRDN = false;

    if(lines.isEmpty())
    {
      int msgID = MSGID_LDIF_NO_MOD_DN_ATTRIBUTES;
      String message = getMessage(msgID);
      throw new LDIFException(msgID, message, lineNumber, true);
    }

    StringBuilder line = lines.remove();
    String rdnStr = getModifyDNAttributeValue(lines, line, entryDN, "newrdn");

    try
    {
      newRDN = RDN.decode(rdnStr);
    } catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, de);
      }
      int    msgID   = MSGID_LDIF_INVALID_DN;
      String message = getMessage(msgID, lineNumber, line.toString(),
          de.getErrorMessage());
      throw new LDIFException(msgID, message, lineNumber, true);
    } catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
      int    msgID   = MSGID_LDIF_INVALID_DN;
      String message = getMessage(msgID, lineNumber, line.toString(),
          e.getMessage());
      throw new LDIFException(msgID, message, lineNumber, true);
    }

    if(lines.isEmpty())
    {
      int msgID = MSGID_LDIF_NO_DELETE_OLDRDN_ATTRIBUTE;
      String message = getMessage(msgID);
      throw new LDIFException(msgID, message, lineNumber, true);
    }
    lineNumber++;

    line = lines.remove();
    String delStr = getModifyDNAttributeValue(lines, line,
        entryDN, "deleteoldrdn");

    if(delStr.equalsIgnoreCase("false") ||
        delStr.equalsIgnoreCase("no") ||
        delStr.equalsIgnoreCase("0"))
    {
      deleteOldRDN = false;
    } else if(delStr.equalsIgnoreCase("true") ||
        delStr.equalsIgnoreCase("yes") ||
        delStr.equalsIgnoreCase("1"))
    {
      deleteOldRDN = true;
    } else
    {
      int msgID = MSGID_LDIF_INVALID_DELETE_OLDRDN_ATTRIBUTE;
      String message = getMessage(msgID, delStr);
      throw new LDIFException(msgID, message, lineNumber, true);
    }

    if(!lines.isEmpty())
    {
      lineNumber++;

      line = lines.remove();

      String dnStr = getModifyDNAttributeValue(lines, line,
          entryDN, "newsuperior");
      try
      {
        newSuperiorDN = DN.decode(dnStr);
      } catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }
        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lineNumber, line.toString(),
            de.getErrorMessage());
        throw new LDIFException(msgID, message, lineNumber, true);
      } catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lineNumber, line.toString(),
            e.getMessage());
        throw new LDIFException(msgID, message, lineNumber, true);
      }
    }

    return new ModifyDNChangeRecordEntry(entryDN, newRDN, deleteOldRDN,
                                         newSuperiorDN);
  }



  /**
   * Return the string value for the specified attribute name which only
   * has one value.
   *
   * @param lines
   *          The set of lines for this change record entry.
   * @param line
   *          The line currently being examined.
   * @param entryDN
   *          The name of the entry being modified.
   * @param attributeName
   *          The attribute name
   * @return the string value for the attribute name.
   * @throws LDIFException
   *           If a problem occurs while attempting to determine the
   *           attribute value.
   */

  private String getModifyDNAttributeValue(LinkedList<StringBuilder> lines,
                                   StringBuilder line,
                                   DN entryDN,
                                   String attributeName) throws LDIFException
  {

    Attribute attr =
      readSingleValueAttribute(lines, line, entryDN, attributeName);
    LinkedHashSet<AttributeValue> values = attr.getValues();

    // Get the attribute value
    Object[] vals = values.toArray();
    return (((AttributeValue)vals[0]).getStringValue());
  }



  /**
   * Parse a modify change record entry from LDIF.
   *
   * @param entryDN
   *          The name of the entry being modified.
   * @param lines
   *          The lines to parse.
   * @return Returns the parsed modify change record entry.
   * @throws LDIFException
   *           If there was an error when parsing the change record.
   */
  private ChangeRecordEntry parseModifyChangeRecordEntry(DN entryDN,
      LinkedList<StringBuilder> lines) throws LDIFException {

    List<LDAPModification> modifications = new ArrayList<LDAPModification>();
    while(!lines.isEmpty())
    {
      ModificationType modType = null;

      StringBuilder line = lines.remove();
      Attribute attr =
        readSingleValueAttribute(lines, line, entryDN, null);
      String name = attr.getName();
      LinkedHashSet<AttributeValue> values = attr.getValues();

      // Get the attribute description
      String attrDescr = values.iterator().next().getStringValue();

      String lowerName = toLowerCase(name);
      if(lowerName.equals("add"))
      {
        modType = ModificationType.ADD;
      } else if(lowerName.equals("delete"))
      {
        modType = ModificationType.DELETE;
      } else if(lowerName.equals("replace"))
      {
        modType = ModificationType.REPLACE;
      } else if(lowerName.equals("increment"))
      {
        modType = ModificationType.INCREMENT;
      } else
      {
        // Invalid attribute name.
        int msgID = MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE;
        String message = getMessage(msgID, name,
            "add, delete, replace, increment");
        throw new LDIFException(msgID, message, lineNumber, true);
      }

      // Now go through the rest of the attributes till the "-" line is
      // reached.
      Attribute modAttr = LDIFReader.parseAttrDescription(attrDescr);
      while (! lines.isEmpty())
      {
        line = lines.remove();
        if(line.toString().equals("-"))
        {
          break;
        }
        Attribute a =
          readSingleValueAttribute(lines, line, entryDN, attrDescr);
        modAttr.getValues().addAll(a.getValues());
      }

      LDAPAttribute ldapAttr = new LDAPAttribute(modAttr);
      LDAPModification mod = new LDAPModification(modType, ldapAttr);
      modifications.add(mod);
    }

    return new ModifyChangeRecordEntry(entryDN, modifications);
  }



  /**
   * Parse a delete change record entry from LDIF.
   *
   * @param entryDN
   *          The name of the entry being deleted.
   * @param lines
   *          The lines to parse.
   * @return Returns the parsed delete change record entry.
   * @throws LDIFException
   *           If there was an error when parsing the change record.
   */
  private ChangeRecordEntry parseDeleteChangeRecordEntry(DN entryDN,
      LinkedList<StringBuilder> lines) throws LDIFException {

    if (!lines.isEmpty())
    {
      int msgID = MSGID_LDIF_INVALID_DELETE_ATTRIBUTES;
      String message = getMessage(msgID);

      throw new LDIFException(msgID, message, lineNumber, true);
    }

    return new DeleteChangeRecordEntry(entryDN);
  }



  /**
   * Parse an add change record entry from LDIF.
   *
   * @param entryDN
   *          The name of the entry being added.
   * @param lines
   *          The lines to parse.
   * @return Returns the parsed add change record entry.
   * @throws LDIFException
   *           If there was an error when parsing the change record.
   */
  private ChangeRecordEntry parseAddChangeRecordEntry(DN entryDN,
      LinkedList<StringBuilder> lines) throws LDIFException {

    HashMap<ObjectClass,String> objectClasses =
      new HashMap<ObjectClass,String>();
    HashMap<AttributeType,List<Attribute>> attributes =
      new HashMap<AttributeType, List<Attribute>>();
    for(StringBuilder line : lines)
    {
      readAttribute(lines, line, entryDN, objectClasses,
          attributes, attributes);
    }

    // Reconstruct the object class attribute.
    AttributeType ocType = DirectoryServer.getObjectClassAttributeType();
    LinkedHashSet<AttributeValue> ocValues =
      new LinkedHashSet<AttributeValue>(objectClasses.size());
    for (String value : objectClasses.values()) {
      AttributeValue av = new AttributeValue(ocType, value);
      ocValues.add(av);
    }
    Attribute ocAttr = new Attribute(ocType, "objectClass", ocValues);
    List<Attribute> ocAttrList = new ArrayList<Attribute>(1);
    ocAttrList.add(ocAttr);
    attributes.put(ocType, ocAttrList);

    return new AddChangeRecordEntry(entryDN, attributes);
  }



  /**
   * Parse colon position in an attribute description.
   *
   * @param lines
   *          The current set of lines.
   * @param line
   *          The current line.
   * @return The colon position.
   * @throws LDIFException
   *           If the colon was badly placed or not found.
   */
  private int parseColonPosition(LinkedList<StringBuilder> lines,
      StringBuilder line) throws LDIFException {

    int colonPos = line.indexOf(":");
    if (colonPos <= 0)
    {
      int    msgID   = MSGID_LDIF_NO_ATTR_NAME;
      String message = getMessage(msgID, lastEntryLineNumber, line.toString());
      logToRejectWriter(lines, message);
      throw new LDIFException(msgID, message, lastEntryLineNumber, true);
    }
    return colonPos;
  }



  /**
   * Parse a single attribute value from a line of LDIF.
   *
   * @param lines
   *          The current set of lines.
   * @param line
   *          The current line.
   * @param entryDN
   *          The DN of the entry being parsed.
   * @param colonPos
   *          The position of the separator colon in the line.
   * @param attrName
   *          The name of the attribute being parsed.
   * @return The parsed attribute value.
   * @throws LDIFException
   *           If an error occurred when parsing the attribute value.
   */
  private ASN1OctetString parseSingleValue(
      LinkedList<StringBuilder> lines,
      StringBuilder line,
      DN entryDN,
      int colonPos,
      String attrName) throws LDIFException {

    // Look at the character immediately after the colon. If there is
    // none, then assume an attribute with an empty value. If it is another
    // colon, then the value must be base64-encoded. If it is a less-than
    // sign, then assume that it is a URL. Otherwise, it is a regular value.
    int length = line.length();
    ASN1OctetString value;
    if (colonPos == (length-1))
    {
      value = new ASN1OctetString();
    }
    else
    {
      char c = line.charAt(colonPos+1);
      if (c == ':')
      {
        // The value is base64-encoded. Find the first non-blank
        // character, take the rest of the line, and base64-decode it.
        int pos = colonPos+2;
        while ((pos < length) && (line.charAt(pos) == ' '))
        {
          pos++;
        }

        try
        {
          value = new ASN1OctetString(Base64.decode(line.substring(pos)));
        }
        catch (Exception e)
        {
          // The value did not have a valid base64-encoding.
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_LDIF_COULD_NOT_BASE64_DECODE_ATTR;
          String message = getMessage(msgID, String.valueOf(entryDN),
                                      lastEntryLineNumber, line,
                                      String.valueOf(e));
          logToRejectWriter(lines, message);
          throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
        }
      }
      else if (c == '<')
      {
        // Find the first non-blank character, decode the rest of the
        // line as a URL, and read its contents.
        int pos = colonPos+2;
        while ((pos < length) && (line.charAt(pos) == ' '))
        {
          pos++;
        }

        URL contentURL;
        try
        {
          contentURL = new URL(line.substring(pos));
        }
        catch (Exception e)
        {
          // The URL was malformed or had an invalid protocol.
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_LDIF_INVALID_URL;
          String message = getMessage(msgID, String.valueOf(entryDN),
                                      lastEntryLineNumber,
                                      String.valueOf(attrName),
                                      String.valueOf(e));
          logToRejectWriter(lines, message);
          throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
        }


        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try
        {
          outputStream = new ByteArrayOutputStream();
          inputStream  = contentURL.openConnection().getInputStream();

          int bytesRead;
          while ((bytesRead = inputStream.read(buffer)) > 0)
          {
            outputStream.write(buffer, 0, bytesRead);
          }

          value = new ASN1OctetString(outputStream.toByteArray());
        }
        catch (Exception e)
        {
          // We were unable to read the contents of that URL for some
          // reason.
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_LDIF_URL_IO_ERROR;
          String message = getMessage(msgID, String.valueOf(entryDN),
                                      lastEntryLineNumber,
                                      String.valueOf(attrName),
                                      String.valueOf(contentURL),
                                      String.valueOf(e));
          logToRejectWriter(lines, message);
          throw new LDIFException(msgID, message, lastEntryLineNumber, true, e);
        }
        finally
        {
          if (outputStream != null)
          {
            try
            {
              outputStream.close();
            } catch (Exception e) {}
          }

          if (inputStream != null)
          {
            try
            {
              inputStream.close();
            } catch (Exception e) {}
          }
        }
      }
      else
      {
        // The rest of the line should be the value. Skip over any
        // spaces and take the rest of the line as the value.
        int pos = colonPos+1;
        while ((pos < length) && (line.charAt(pos) == ' '))
        {
          pos++;
        }

        value = new ASN1OctetString(line.substring(pos));
      }
    }
    return value;
  }



  /**
   * Log a message to the reject writer if one is configured.
   *
   * @param lines
   *          The set of rejected lines.
   * @param message
   *          The associated error message.
   */
  private void logToRejectWriter(LinkedList<StringBuilder> lines,
      String message) {

    BufferedWriter rejectWriter = importConfig.getRejectWriter();
    if (rejectWriter != null)
    {
      try
      {
        rejectWriter.write("# ");
        rejectWriter.write(message);
        rejectWriter.newLine();
        for (StringBuilder sb : lines)
        {
          rejectWriter.write(sb.toString());
          rejectWriter.newLine();
        }

        rejectWriter.newLine();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }
}

