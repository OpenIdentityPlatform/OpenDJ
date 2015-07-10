/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.util;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
import org.opends.server.types.RawModification;

/**
 * This class provides the ability to read information from an LDIF file.  It
 * provides support for both standard entries and change entries (as would be
 * used with a tool like ldapmodify).
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class LDIFReader implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The reader that will be used to read the data. */
  private BufferedReader reader;

  /** The import configuration that specifies what should be imported. */
  protected LDIFImportConfig importConfig;

  /** The lines that comprise the body of the last entry read. */
  protected List<StringBuilder> lastEntryBodyLines;

  /**
   * The lines that comprise the header (DN and any comments) for the last entry
   * read.
   */
  protected List<StringBuilder> lastEntryHeaderLines;


  /**
   * The number of entries that have been ignored by this LDIF reader because
   * they didn't match the criteria.
   */
  private final AtomicLong entriesIgnored = new AtomicLong();

  /**
   * The number of entries that have been read by this LDIF reader, including
   * those that were ignored because they didn't match the criteria, and
   * including those that were rejected because they were invalid in some way.
   */
  protected final AtomicLong entriesRead = new AtomicLong();

  /** The number of entries that have been rejected by this LDIF reader. */
  private final AtomicLong entriesRejected = new AtomicLong();

  /** The line number on which the last entry started. */
  protected long lastEntryLineNumber = -1;

  /**
   * The line number of the last line read from the LDIF file, starting with 1.
   */
  private long lineNumber;

  /**
   * The plugin config manager that will be used if we are to invoke plugins on
   * the entries as they are read.
   */
  protected PluginConfigManager pluginConfigManager;

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
    ifNull(importConfig);
    this.importConfig = importConfig;

    reader               = importConfig.getReader();
    lastEntryBodyLines   = new LinkedList<>();
    lastEntryHeaderLines = new LinkedList<>();
    pluginConfigManager  = DirectoryServer.getPluginConfigManager();
    // If we should invoke import plugins, then do so.
    if (importConfig.invokeImportPlugins())
    {
      // Inform LDIF import plugins that an import session is ending
      pluginConfigManager.invokeLDIFImportBeginPlugins(importConfig);
    }
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
      lastEntryHeaderLines = new LinkedList<>();


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
        logger.trace("Skipping entry %s because the DN is not one that "
            + "should be included based on the include and exclude branches.", entryDN);
        entriesRead.incrementAndGet();
        logToSkipWriter(lines, ERR_LDIF_SKIP.get(entryDN));
        continue;
      }
      else
      {
        entriesRead.incrementAndGet();
      }

      // Create the entry and see if it is one that should be included in the import.
      final Entry entry = createEntry(entryDN, lines, checkSchema);
      if (!isIncludedInImport(entry,lines)
          || !invokeImportPlugins(entry, lines))
      {
        continue;
      }
      validateAgainstSchemaIfNeeded(checkSchema, entry, lines);

      // The entry should be included in the import, so return it.
      return entry;
    }
  }

  private Entry createEntry(DN entryDN, List<StringBuilder> lines, boolean checkSchema) throws LDIFException
  {
    Map<ObjectClass, String> objectClasses = new HashMap<>();
    Map<AttributeType, List<AttributeBuilder>> userAttrBuilders = new HashMap<>();
    Map<AttributeType, List<AttributeBuilder>> operationalAttrBuilders = new HashMap<>();
    for (StringBuilder line : lines)
    {
      readAttribute(lines, line, entryDN, objectClasses, userAttrBuilders, operationalAttrBuilders, checkSchema);
    }

    final Entry entry = new Entry(entryDN, objectClasses,
        toAttributesMap(userAttrBuilders), toAttributesMap(operationalAttrBuilders));
    logger.trace("readEntry(), created entry: %s", entry);
    return entry;
  }

  private boolean isIncludedInImport(Entry entry, LinkedList<StringBuilder> lines) throws LDIFException
  {
    try
    {
      if (!importConfig.includeEntry(entry))
      {
        final DN entryDN = entry.getName();
        logger.trace("Skipping entry %s because the DN is not one that "
            + "should be included based on the include and exclude filters.", entryDN);
        logToSkipWriter(lines, ERR_LDIF_SKIP.get(entryDN));
        return false;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_IMPORT.get(entry.getName(), lastEntryLineNumber, e);
      throw new LDIFException(message, lastEntryLineNumber, true, e);
    }
    return true;
  }

  private boolean invokeImportPlugins(Entry entry, LinkedList<StringBuilder> lines)
  {
    if (importConfig.invokeImportPlugins())
    {
      PluginResult.ImportLDIF pluginResult =
          pluginConfigManager.invokeLDIFImportPlugins(importConfig, entry);
      if (!pluginResult.continueProcessing())
      {
        final DN entryDN = entry.getName();
        LocalizableMessage m;
        LocalizableMessage rejectMessage = pluginResult.getErrorMessage();
        if (rejectMessage == null)
        {
          m = ERR_LDIF_REJECTED_BY_PLUGIN_NOMESSAGE.get(entryDN);
        }
        else
        {
          m = ERR_LDIF_REJECTED_BY_PLUGIN.get(entryDN, rejectMessage);
        }

        logToRejectWriter(lines, m);
        return false;
      }
    }
    return true;
  }

  private void validateAgainstSchemaIfNeeded(boolean checkSchema, final Entry entry, LinkedList<StringBuilder> lines)
      throws LDIFException
  {
    if (checkSchema)
    {
      LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
      if (!entry.conformsToSchema(null, false, true, false, invalidReason))
      {
        final DN entryDN = entry.getName();
        LocalizableMessage message = ERR_LDIF_SCHEMA_VIOLATION.get(entryDN, lastEntryLineNumber, invalidReason);
        logToRejectWriter(lines, message);
        throw new LDIFException(message, lastEntryLineNumber, true);
      }
      // Add any superior objectclass(s) missing in an entries objectclass map.
      addSuperiorObjectClasses(entry.getObjectClasses());
    }
  }

  /**
   * Returns a new Map where the provided Map with AttributeBuilders is converted to another Map
   * with Attributes.
   *
   * @param attrBuilders
   *          the provided Map containing AttributeBuilders
   * @return a new Map containing Attributes
   */
  protected Map<AttributeType, List<Attribute>> toAttributesMap(Map<AttributeType, List<AttributeBuilder>> attrBuilders)
  {
    Map<AttributeType, List<Attribute>> attributes = new HashMap<>(attrBuilders.size());
    for (Map.Entry<AttributeType, List<AttributeBuilder>> attrTypeEntry : attrBuilders.entrySet())
    {
      AttributeType attrType = attrTypeEntry.getKey();
      List<Attribute> attrList = toAttributesList(attrTypeEntry.getValue());
      attributes.put(attrType, attrList);
    }
    return attributes;
  }

  /**
   * Converts the provided List of AttributeBuilders to a new list of Attributes.
   *
   * @param builders the list of AttributeBuilders
   * @return a new list of Attributes
   */
  protected List<Attribute> toAttributesList(List<AttributeBuilder> builders)
  {
    List<Attribute> results = new ArrayList<>(builders.size());
    for (AttributeBuilder builder : builders)
    {
      results.add(builder.toAttribute());
    }
    return results;
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

      ChangeRecordEntry entry;

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
          LocalizableMessage message = ERR_LDIF_INVALID_CHANGETYPE_ATTRIBUTE.get(
              changeType, "add, delete, modify, moddn, modrdn");
          throw new LDIFException(message, lastEntryLineNumber, false);
        }
      } else
      {
        // default to "add"?
        if(defaultAdd)
        {
          entry = parseAddChangeRecordEntry(entryDN, lines);
        } else
        {
          LocalizableMessage message = ERR_LDIF_INVALID_CHANGETYPE_ATTRIBUTE.get(
              null, "add, delete, modify, moddn, modrdn");
          throw new LDIFException(message, lastEntryLineNumber, false);
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
  protected LinkedList<StringBuilder> readEntryLines() throws IOException, LDIFException
  {
    // Read the entry lines into a buffer.
    LinkedList<StringBuilder> lines = new LinkedList<>();
    int lastLine = -1;

    if(reader == null)
    {
      return null;
    }

    while (true)
    {
      String line = reader.readLine();
      lineNumber++;

      if (line == null)
      {
        // This must mean that we have reached the end of the LDIF source.
        // If the set of lines read so far is empty, then move onto the next
        // file or return null.  Otherwise, break out of this loop.
        if (!lines.isEmpty())
        {
          break;
        }
        reader = importConfig.nextReader();
        if (reader != null)
        {
          return readEntryLines();
        }
        return null;
      }
      else if (line.length() == 0)
      {
        // This is a blank line.  If the set of lines read so far is empty,
        // then just skip over it.  Otherwise, break out of this loop.
        if (!lines.isEmpty())
        {
          break;
        }
        continue;
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
          LocalizableMessage message =
                  ERR_LDIF_INVALID_LEADING_SPACE.get(lineNumber, line);
          logToRejectWriter(lines, message);
          throw new LDIFException(message, lineNumber, false);
        }
      }
      else
      {
        // This is a new line.
        if (lines.isEmpty())
        {
          lastEntryLineNumber = lineNumber;
        }
        if(((byte)line.charAt(0) == (byte)0xEF) &&
          ((byte)line.charAt(1) == (byte)0xBB) &&
          ((byte)line.charAt(2) == (byte)0xBF))
        {
          // This is a UTF-8 BOM that Java doesn't skip. We will skip it here.
          line = line.substring(3, line.length());
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
  protected DN readDN(LinkedList<StringBuilder> lines) throws LDIFException
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
      LocalizableMessage message =
              ERR_LDIF_NO_ATTR_NAME.get(lastEntryLineNumber, line);

      logToRejectWriter(lines, message);
      throw new LDIFException(message, lastEntryLineNumber, true);
    }

    String attrName = toLowerCase(line.substring(0, colonPos));
    if (attrName.equals("version"))
    {
      // This is the version line, and we can skip it.
      return readDN(lines);
    }
    else if (! attrName.equals("dn"))
    {
      LocalizableMessage message =
              ERR_LDIF_NO_DN.get(lastEntryLineNumber, line);

      logToRejectWriter(lines, message);
      throw new LDIFException(message, lastEntryLineNumber, true);
    }


    // Look at the character immediately after the colon.  If there is none,
    // then assume the null DN.  If it is another colon, then the DN must be
    // base64-encoded.  Otherwise, it may be one or more spaces.
    if (colonPos == line.length() - 1)
    {
      return DN.rootDN();
    }

    if (line.charAt(colonPos+1) == ':')
    {
      // The DN is base64-encoded.  Find the first non-blank character and
      // take the rest of the line, base64-decode it, and parse it as a DN.
      int pos = findFirstNonSpaceCharPosition(line, colonPos + 2);
      String dnStr = base64Decode(line.substring(pos), lines, line);
      return decodeDN(dnStr, lines, line);
    }
    else
    {
      // The rest of the value should be the DN.  Skip over any spaces and
      // attempt to decode the rest of the line as the DN.
      int pos = findFirstNonSpaceCharPosition(line, colonPos + 1);
      return decodeDN(line.substring(pos), lines, line);
    }
  }

  private int findFirstNonSpaceCharPosition(StringBuilder line, int startPos)
  {
    final int length = line.length();
    int pos = startPos;
    while ((pos < length) && (line.charAt(pos) == ' '))
    {
      pos++;
    }
    return pos;
  }

  private String base64Decode(String encodedStr, List<StringBuilder> lines,
      StringBuilder line) throws LDIFException
  {
    try
    {
      return new String(Base64.decode(encodedStr), "UTF-8");
    }
    catch (Exception e)
    {
      // The value did not have a valid base64-encoding.
      final String stackTrace = StaticUtils.stackTraceToSingleLineString(e);
      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Base64 decode failed for dn '%s', exception stacktrace: %s",
            encodedStr, stackTrace);
      }

      LocalizableMessage message = ERR_LDIF_COULD_NOT_BASE64_DECODE_DN.get(
          lastEntryLineNumber, line, stackTrace);
      logToRejectWriter(lines, message);
      throw new LDIFException(message, lastEntryLineNumber, true, e);
    }
  }

  private DN decodeDN(String dnString, List<StringBuilder> lines,
      StringBuilder line) throws LDIFException
  {
    try
    {
      return DN.valueOf(dnString);
    }
    catch (DirectoryException de)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("DN decode failed for: ", dnString);
      }

      LocalizableMessage message = ERR_LDIF_INVALID_DN.get(
          lastEntryLineNumber, line, de.getMessageObject());

      logToRejectWriter(lines, message);
      throw new LDIFException(message, lastEntryLineNumber, true, de);
    }
    catch (Exception e)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("DN decode failed for: ", dnString);
      }
      LocalizableMessage message = ERR_LDIF_INVALID_DN.get(
          lastEntryLineNumber, line, e);

      logToRejectWriter(lines, message);
      throw new LDIFException(message, lastEntryLineNumber, true, e);
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
      LocalizableMessage message = ERR_LDIF_NO_ATTR_NAME.get(lastEntryLineNumber, line);
      logToRejectWriter(lines, message);
      throw new LDIFException(message, lastEntryLineNumber, true);
    }

    String attrName = toLowerCase(line.substring(0, colonPos));
    if (! attrName.equals("changetype"))
    {
      // No changetype attribute - return null
      return null;
    }
    // Remove the line
    lines.remove();


    // Look at the character immediately after the colon.  If there is none,
    // then no value was specified. Throw an exception
    int length = line.length();
    if (colonPos == (length-1))
    {
      LocalizableMessage message = ERR_LDIF_INVALID_CHANGETYPE_ATTRIBUTE.get(
          null, "add, delete, modify, moddn, modrdn");
      throw new LDIFException(message, lastEntryLineNumber, false );
    }

    if (line.charAt(colonPos+1) == ':')
    {
      // The change type is base64-encoded.  Find the first non-blank character
      // and take the rest of the line, and base64-decode it.
      int pos = findFirstNonSpaceCharPosition(line, colonPos + 2);
      return base64Decode(line.substring(pos), lines, line);
    }
    else
    {
      // The rest of the value should be the changetype. Skip over any spaces
      // and attempt to decode the rest of the line as the changetype string.
      int pos = findFirstNonSpaceCharPosition(line, colonPos + 1);
      return line.substring(pos);
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
   * @param userAttrBuilders        The map of user attribute builders decoded
   *                                so far for the current entry.
   * @param  operationalAttrBuilders  The map of operational attribute builders
   *                                  decoded so far for the current entry.
   * @param  checkSchema            Indicates whether to perform schema
   *                                validation for the attribute.
   *
   * @throws  LDIFException  If a problem occurs while trying to decode the
   *                         attribute contained in the provided entry.
   */
  protected void readAttribute(List<StringBuilder> lines,
       StringBuilder line, DN entryDN,
       Map<ObjectClass,String> objectClasses,
       Map<AttributeType,List<AttributeBuilder>> userAttrBuilders,
       Map<AttributeType,List<AttributeBuilder>> operationalAttrBuilders,
       boolean checkSchema)
          throws LDIFException
  {
    // Parse the attribute type description.
    int colonPos = parseColonPosition(lines, line);
    String attrDescr = line.substring(0, colonPos);
    final Attribute attribute = parseAttrDescription(attrDescr);
    final String attrName = attribute.getName();
    final String lowerName = toLowerCase(attrName);

    // Now parse the attribute value.
    ByteString value = parseSingleValue(lines, line, entryDN,
        colonPos, attrName);

    // See if this is an objectclass or an attribute.  Then get the
    // corresponding definition and add the value to the appropriate hash.
    if (lowerName.equals("objectclass"))
    {
      if (! importConfig.includeObjectClasses())
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Skipping objectclass %s for entry %s due to " +
              "the import configuration.", value, entryDN);
        }
        return;
      }

      String ocName      = value.toString().trim();
      String lowerOCName = toLowerCase(ocName);

      ObjectClass objectClass = DirectoryServer.getObjectClass(lowerOCName);
      if (objectClass == null)
      {
        objectClass = DirectoryServer.getDefaultObjectClass(ocName);
      }

      if (objectClasses.containsKey(objectClass))
      {
        logger.warn(WARN_LDIF_DUPLICATE_OBJECTCLASS, entryDN, lastEntryLineNumber, ocName);
      }
      else
      {
        objectClasses.put(objectClass, ocName);
      }
    }
    else
    {
      AttributeType attrType = DirectoryServer.getAttributeTypeOrDefault(lowerName, attrName);
      if (! importConfig.includeAttribute(attrType))
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Skipping attribute %s for entry %s due to the " +
              "import configuration.", attrName, entryDN);
        }
        return;
      }

       //The attribute is not being ignored so check for binary option.
      if(checkSchema
          && !attrType.getSyntax().isBEREncodingRequired()
          && attribute.hasOption("binary"))
      {
        LocalizableMessage message = ERR_LDIF_INVALID_ATTR_OPTION.get(
          entryDN, lastEntryLineNumber, attrName);
        logToRejectWriter(lines, message);
        throw new LDIFException(message, lastEntryLineNumber,true);
      }
      if (checkSchema &&
          (DirectoryServer.getSyntaxEnforcementPolicy() !=
               AcceptRejectWarn.ACCEPT))
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        if (! attrType.getSyntax().valueIsAcceptable(value, invalidReason))
        {
          LocalizableMessage message = WARN_LDIF_VALUE_VIOLATES_SYNTAX.get(
              entryDN, lastEntryLineNumber, value, attrName, invalidReason);
          if (DirectoryServer.getSyntaxEnforcementPolicy() ==
                   AcceptRejectWarn.WARN)
          {
            logger.error(message);
          }
          else
          {
            logToRejectWriter(lines, message);
            throw new LDIFException(message, lastEntryLineNumber, true);
          }
        }
      }

      ByteString attributeValue = value;
      final Map<AttributeType, List<AttributeBuilder>> attrBuilders;
      if (attrType.isOperational())
      {
        attrBuilders = operationalAttrBuilders;
      }
      else
      {
        attrBuilders = userAttrBuilders;
      }
      List<AttributeBuilder> attrList = attrBuilders.get(attrType);
      if (attrList == null)
      {
        AttributeBuilder builder = new AttributeBuilder(attribute, true);
        builder.add(attributeValue);
        attrList = new ArrayList<>();
        attrList.add(builder);
        attrBuilders.put(attrType, attrList);
        return;
      }

      // Check to see if any of the attributes in the list have the same set of
      // options.  If so, then try to add a value to that attribute.
      for (AttributeBuilder a : attrList)
      {
        if (a.optionsEqual(attribute.getOptions()))
        {
          if (!a.add(attributeValue) && checkSchema)
          {
              LocalizableMessage message = WARN_LDIF_DUPLICATE_ATTR.get(
                  entryDN, lastEntryLineNumber, attrName, value);
              logToRejectWriter(lines, message);
            throw new LDIFException(message, lastEntryLineNumber, true);
          }
          if (attrType.isSingleValue() && (a.size() > 1)  && checkSchema)
          {
            LocalizableMessage message = ERR_LDIF_MULTIPLE_VALUES_FOR_SINGLE_VALUED_ATTR
                    .get(entryDN, lastEntryLineNumber, attrName);
            logToRejectWriter(lines, message);
            throw new LDIFException(message, lastEntryLineNumber, true);
          }

          return;
        }
      }

      // No set of matching options was found, so create a new one and
      // add it to the list.
      AttributeBuilder builder = new AttributeBuilder(attribute, true);
      builder.add(attributeValue);
      attrList.add(builder);
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
       List<StringBuilder> lines, StringBuilder line, DN entryDN,
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
        LocalizableMessage message = ERR_LDIF_INVALID_CHANGERECORD_ATTRIBUTE.get(
            attrDescr, attributeName);
        throw new LDIFException(message, lastEntryLineNumber, false);
      }
    }

    //  Now parse the attribute value.
    ByteString value = parseSingleValue(lines, line, entryDN,
        colonPos, attrName);

    AttributeBuilder builder = new AttributeBuilder(attribute, true);
    builder.add(value);
    return builder.toAttribute();
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
  public void rejectLastEntry(LocalizableMessage message)
  {
    entriesRejected.incrementAndGet();

    BufferedWriter rejectWriter = importConfig.getRejectWriter();
    if (rejectWriter != null)
    {
      try
      {
        if ((message != null) && (message.length() > 0))
        {
          rejectWriter.write("# ");
          rejectWriter.write(message.toString());
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
        logger.traceException(e);
      }
    }
  }

  /**
   * Log the specified entry and messages in the reject writer. The method is
   * intended to be used in a threaded environment, where individual import
   * threads need to log an entry and message to the reject file.
   *
   * @param e The entry to log.
   * @param message The message to log.
   */
  public synchronized void rejectEntry(Entry e, LocalizableMessage message) {
    BufferedWriter rejectWriter = importConfig.getRejectWriter();
    entriesRejected.incrementAndGet();
    if (rejectWriter != null) {
      try {
        if ((message != null) && (message.length() > 0)) {
          rejectWriter.write("# ");
          rejectWriter.write(message.toString());
          rejectWriter.newLine();
        }
        rejectWriter.write(e.getName().toString());
        rejectWriter.newLine();
        List<StringBuilder> eLDIF = e.toLDIF();
        for(StringBuilder l : eLDIF) {
          rejectWriter.write(l.toString());
          rejectWriter.newLine();
        }
        rejectWriter.newLine();
      } catch (IOException ex) {
        logger.traceException(ex);
      }
    }
  }



  /**
   * Closes this LDIF reader and the underlying file or input stream.
   */
  @Override
  public void close()
  {
    // If we should invoke import plugins, then do so.
    if (importConfig.invokeImportPlugins())
    {
      // Inform LDIF import plugins that an import session is ending
      pluginConfigManager.invokeLDIFImportEndPlugins(importConfig);
    }
    importConfig.close();
  }



  /**
   * Parse an AttributeDescription (an attribute type name and its
   * options).
   *
   * @param attrDescr
   *          The attribute description to be parsed.
   * @return A new attribute with no values, representing the
   *         attribute type and its options.
   */
  public static Attribute parseAttrDescription(String attrDescr)
  {
    AttributeBuilder builder;
    int semicolonPos = attrDescr.indexOf(';');
    if (semicolonPos > 0)
    {
      builder = new AttributeBuilder(attrDescr.substring(0, semicolonPos));
      int nextPos = attrDescr.indexOf(';', semicolonPos + 1);
      while (nextPos > 0)
      {
        String option = attrDescr.substring(semicolonPos + 1, nextPos);
        if (option.length() > 0)
        {
          builder.setOption(option);
          semicolonPos = nextPos;
          nextPos = attrDescr.indexOf(';', semicolonPos + 1);
        }
      }

      String option = attrDescr.substring(semicolonPos + 1);
      if (option.length() > 0)
      {
        builder.setOption(option);
      }
    }
    else
    {
      builder = new AttributeBuilder(attrDescr);
    }

    if(builder.getAttributeType().getSyntax().isBEREncodingRequired())
    {
      //resetting doesn't hurt and returns false.
      builder.setOption("binary");
    }

    return builder.toAttribute();
  }



  /**
   * Retrieves the total number of entries read so far by this LDIF reader,
   * including those that have been ignored or rejected.
   *
   * @return  The total number of entries read so far by this LDIF reader.
   */
  public long getEntriesRead()
  {
    return entriesRead.get();
  }



  /**
   * Retrieves the total number of entries that have been ignored so far by this
   * LDIF reader because they did not match the import criteria.
   *
   * @return  The total number of entries ignored so far by this LDIF reader.
   */
  public long getEntriesIgnored()
  {
    return entriesIgnored.get();
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
    return entriesRejected.get();
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
    RDN newRDN;
    boolean deleteOldRDN;

    if(lines.isEmpty())
    {
      LocalizableMessage message = ERR_LDIF_NO_MOD_DN_ATTRIBUTES.get();
      throw new LDIFException(message, lineNumber, true);
    }

    StringBuilder line = lines.remove();
    String rdnStr = getModifyDNAttributeValue(lines, line, entryDN, "newrdn");

    try
    {
      newRDN = RDN.decode(rdnStr);
    } catch (DirectoryException de)
    {
      logger.traceException(de);
      LocalizableMessage message = ERR_LDIF_INVALID_DN.get(
          lineNumber, line, de.getMessageObject());
      throw new LDIFException(message, lineNumber, true);
    } catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message =
          ERR_LDIF_INVALID_DN.get(lineNumber, line, e.getMessage());
      throw new LDIFException(message, lineNumber, true);
    }

    if(lines.isEmpty())
    {
      LocalizableMessage message = ERR_LDIF_NO_DELETE_OLDRDN_ATTRIBUTE.get();
      throw new LDIFException(message, lineNumber, true);
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
      LocalizableMessage message = ERR_LDIF_INVALID_DELETE_OLDRDN_ATTRIBUTE.get(delStr);
      throw new LDIFException(message, lineNumber, true);
    }

    if(!lines.isEmpty())
    {
      lineNumber++;

      line = lines.remove();

      String dnStr = getModifyDNAttributeValue(lines, line,
          entryDN, "newsuperior");
      try
      {
        newSuperiorDN = DN.valueOf(dnStr);
      } catch (DirectoryException de)
      {
        logger.traceException(de);
        LocalizableMessage message = ERR_LDIF_INVALID_DN.get(
            lineNumber, line, de.getMessageObject());
        throw new LDIFException(message, lineNumber, true);
      } catch (Exception e)
      {
        logger.traceException(e);
        LocalizableMessage message = ERR_LDIF_INVALID_DN.get(
            lineNumber, line, e.getMessage());
        throw new LDIFException(message, lineNumber, true);
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
  private String getModifyDNAttributeValue(List<StringBuilder> lines,
                                   StringBuilder line,
                                   DN entryDN,
                                   String attributeName) throws LDIFException
  {
    Attribute attr =
      readSingleValueAttribute(lines, line, entryDN, attributeName);
    return attr.iterator().next().toString();
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

    List<RawModification> modifications = new ArrayList<>();
    while(!lines.isEmpty())
    {
      StringBuilder line = lines.remove();
      Attribute attr = readSingleValueAttribute(lines, line, entryDN, null);
      String name = attr.getName();

      // Get the attribute description
      String attrDescr = attr.iterator().next().toString();

      ModificationType modType;
      String lowerName = toLowerCase(name);
      if (lowerName.equals("add"))
      {
        modType = ModificationType.ADD;
      }
      else if (lowerName.equals("delete"))
      {
        modType = ModificationType.DELETE;
      }
      else if (lowerName.equals("replace"))
      {
        modType = ModificationType.REPLACE;
      }
      else if (lowerName.equals("increment"))
      {
        modType = ModificationType.INCREMENT;
      }
      else
      {
        // Invalid attribute name.
        LocalizableMessage message = ERR_LDIF_INVALID_MODIFY_ATTRIBUTE.get(name,
            "add, delete, replace, increment");
        throw new LDIFException(message, lineNumber, true);
      }

      // Now go through the rest of the attributes till the "-" line is reached.
      Attribute modAttr = LDIFReader.parseAttrDescription(attrDescr);
      AttributeBuilder builder = new AttributeBuilder(modAttr, true);
      while (! lines.isEmpty())
      {
        line = lines.remove();
        if(line.toString().equals("-"))
        {
          break;
        }
        Attribute a = readSingleValueAttribute(lines, line, entryDN, attrDescr);
        builder.addAll(a);
      }

      LDAPAttribute ldapAttr = new LDAPAttribute(builder.toAttribute());
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
      List<StringBuilder> lines) throws LDIFException
  {
    if (!lines.isEmpty())
    {
      LocalizableMessage message = ERR_LDIF_INVALID_DELETE_ATTRIBUTES.get();
      throw new LDIFException(message, lineNumber, true);
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
      List<StringBuilder> lines) throws LDIFException
  {
    Map<ObjectClass, String> objectClasses = new HashMap<>();
    Map<AttributeType, List<AttributeBuilder>> attrBuilders = new HashMap<>();
    for(StringBuilder line : lines)
    {
      readAttribute(lines, line, entryDN, objectClasses,
          attrBuilders, attrBuilders, importConfig.validateSchema());
    }

    // Reconstruct the object class attribute.
    AttributeType ocType = DirectoryServer.getObjectClassAttributeType();
    AttributeBuilder builder = new AttributeBuilder(ocType, "objectClass");
    builder.addAllStrings(objectClasses.values());
    Map<AttributeType, List<Attribute>> attributes = toAttributesMap(attrBuilders);
    if (attributes.get(ocType) == null)
    {
      attributes.put(ocType, builder.toAttributeList());
    }

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
  private int parseColonPosition(List<StringBuilder> lines,
      StringBuilder line) throws LDIFException {
    int colonPos = line.indexOf(":");
    if (colonPos <= 0)
    {
      LocalizableMessage message = ERR_LDIF_NO_ATTR_NAME.get(
              lastEntryLineNumber, line);
      logToRejectWriter(lines, message);
      throw new LDIFException(message, lastEntryLineNumber, true);
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
  private ByteString parseSingleValue(
      List<StringBuilder> lines,
      StringBuilder line,
      DN entryDN,
      int colonPos,
      String attrName) throws LDIFException {

    // Look at the character immediately after the colon. If there is
    // none, then assume an attribute with an empty value. If it is another
    // colon, then the value must be base64-encoded. If it is a less-than
    // sign, then assume that it is a URL. Otherwise, it is a regular value.
    int length = line.length();
    ByteString value;
    if (colonPos == (length-1))
    {
      value = ByteString.empty();
    }
    else
    {
      char c = line.charAt(colonPos+1);
      if (c == ':')
      {
        // The value is base64-encoded. Find the first non-blank
        // character, take the rest of the line, and base64-decode it.
        int pos = findFirstNonSpaceCharPosition(line, colonPos + 2);

        try
        {
          value = ByteString.wrap(Base64.decode(line.substring(pos)));
        }
        catch (Exception e)
        {
          // The value did not have a valid base64-encoding.
          logger.traceException(e);

          LocalizableMessage message = ERR_LDIF_COULD_NOT_BASE64_DECODE_ATTR.get(
              entryDN, lastEntryLineNumber, line, e);
          logToRejectWriter(lines, message);
          throw new LDIFException(message, lastEntryLineNumber, true, e);
        }
      }
      else if (c == '<')
      {
        // Find the first non-blank character, decode the rest of the
        // line as a URL, and read its contents.
        int pos = findFirstNonSpaceCharPosition(line, colonPos + 2);

        URL contentURL;
        try
        {
          contentURL = new URL(line.substring(pos));
        }
        catch (Exception e)
        {
          // The URL was malformed or had an invalid protocol.
          logger.traceException(e);

          LocalizableMessage message = ERR_LDIF_INVALID_URL.get(
              entryDN, lastEntryLineNumber, attrName, e);
          logToRejectWriter(lines, message);
          throw new LDIFException(message, lastEntryLineNumber, true, e);
        }


        InputStream inputStream = null;
        try
        {
          ByteStringBuilder builder = new ByteStringBuilder(4096);
          inputStream  = contentURL.openConnection().getInputStream();

          while (builder.append(inputStream, 4096) != -1) { /* Do nothing */ }

          value = builder.toByteString();
        }
        catch (Exception e)
        {
          // We were unable to read the contents of that URL for some reason.
          logger.traceException(e);

          LocalizableMessage message = ERR_LDIF_URL_IO_ERROR.get(
              entryDN, lastEntryLineNumber, attrName, contentURL, e);
          logToRejectWriter(lines, message);
          throw new LDIFException(message, lastEntryLineNumber, true, e);
        }
        finally
        {
          StaticUtils.close(inputStream);
        }
      }
      else
      {
        // The rest of the line should be the value. Skip over any
        // spaces and take the rest of the line as the value.
        int pos = findFirstNonSpaceCharPosition(line, colonPos + 1);
        value = ByteString.valueOf(line.substring(pos));
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
  protected void logToRejectWriter(List<StringBuilder> lines, LocalizableMessage message)
  {
    entriesRejected.incrementAndGet();
    BufferedWriter rejectWriter = importConfig.getRejectWriter();
    if (rejectWriter != null)
    {
      logToWriter(rejectWriter, lines, message);
    }
  }

  /**
   * Log a message to the reject writer if one is configured.
   *
   * @param lines
   *          The set of rejected lines.
   * @param message
   *          The associated error message.
   */
  protected void logToSkipWriter(List<StringBuilder> lines, LocalizableMessage message)
  {
    entriesIgnored.incrementAndGet();
    BufferedWriter skipWriter = importConfig.getSkipWriter();
    if (skipWriter != null)
    {
      logToWriter(skipWriter, lines, message);
    }
  }

  /**
   * Log a message to the given writer.
   *
   * @param writer
   *          The writer to write to.
   * @param lines
   *          The set of rejected lines.
   * @param message
   *          The associated error message.
   */
  private void logToWriter(BufferedWriter writer, List<StringBuilder> lines,
      LocalizableMessage message)
  {
    if (writer != null)
    {
      try
      {
        writer.write("# ");
        writer.write(String.valueOf(message));
        writer.newLine();
        for (StringBuilder sb : lines)
        {
          writer.write(sb.toString());
          writer.newLine();
        }

        writer.newLine();
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }


  /**
   * Adds any missing RDN attributes to the entry that is being imported.
   * @param entryDN the entry DN
   * @param userAttributes the user attributes
   * @param operationalAttributes the operational attributes
   */
  protected void addRDNAttributesIfNecessary(DN entryDN,
          Map<AttributeType,List<Attribute>>userAttributes,
          Map<AttributeType,List<Attribute>> operationalAttributes)
  {
    RDN rdn = entryDN.rdn();
    int numAVAs = rdn.getNumValues();
    for (int i=0; i < numAVAs; i++)
    {
      AttributeType  t = rdn.getAttributeType(i);
      ByteString v = rdn.getAttributeValue(i);
      String         n = rdn.getAttributeName(i);
      if (t.isOperational())
      {
        addRDNAttributesIfNecessary(operationalAttributes, t, v, n);
      }
      else
      {
        addRDNAttributesIfNecessary(userAttributes, t, v, n);
      }
    }
  }


  private void addRDNAttributesIfNecessary(
      Map<AttributeType, List<Attribute>> attributes, AttributeType t,
      ByteString v, String n)
  {
    List<Attribute> attrList = attributes.get(t);
    if (attrList == null)
    {
      attrList = new ArrayList<>();
      attrList.add(Attributes.create(t, n, v));
      attributes.put(t, attrList);
    }
    else
    {
      boolean found = false;
      for (int j = 0; j < attrList.size(); j++)
      {
        Attribute a = attrList.get(j);

        if (a.hasOptions())
        {
          continue;
        }

        if (!a.contains(v))
        {
          AttributeBuilder builder = new AttributeBuilder(a);
          builder.add(v);
          attrList.set(j, builder.toAttribute());
        }

        found = true;
        break;
      }

      if (!found)
      {
        attrList.add(Attributes.create(t, n, v));
      }
    }
  }
}
