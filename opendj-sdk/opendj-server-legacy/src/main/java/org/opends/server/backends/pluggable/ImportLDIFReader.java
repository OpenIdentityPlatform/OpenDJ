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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Reject;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;

/** This class specializes the LDIFReader for imports. */
final class ImportLDIFReader extends LDIFReader
{
  /**
   * A class holding the entry, its entryID as assigned by the LDIF reader and its suffix as
   * determined by the LDIF reader.
   */
  static final class EntryInformation
  {
    private final Entry entry;
    private final EntryID entryID;
    private final Suffix suffix;

    private EntryInformation(Entry entry, EntryID entryID, Suffix suffix)
    {
      this.entry = entry;
      this.entryID = entryID;
      this.suffix = suffix;
    }

    Entry getEntry()
    {
      return entry;
    }

    EntryID getEntryID()
    {
      return entryID;
    }

    Suffix getSuffix()
    {
      return suffix;
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final RootContainer rootContainer;

  /**
   * Creates a new LDIF reader that will read information from the specified file.
   *
   * @param importConfig
   *          The import configuration for this LDIF reader. It must not be <CODE>null</CODE>.
   * @param rootContainer
   *          The root container needed to get the next entry ID.
   * @throws IOException
   *           If a problem occurs while opening the LDIF file for reading.
   */
  public ImportLDIFReader(LDIFImportConfig importConfig, RootContainer rootContainer) throws IOException
  {
    super(importConfig);
    Reject.ifNull(importConfig, rootContainer);
    this.rootContainer = rootContainer;
  }

  /**
   * Reads the next entry from the LDIF source.
   *
   * @return The next entry information read from the LDIF source, or <CODE>null</CODE> if the end of the LDIF
   *         data is reached.
   * @param suffixesMap
   *          A map of suffixes instances.
   * @throws IOException
   *           If an I/O problem occurs while reading from the file.
   * @throws LDIFException
   *           If the information read cannot be parsed as an LDIF entry.
   */
  public final EntryInformation readEntry(Map<DN, Suffix> suffixesMap) throws IOException, LDIFException
  {
    final boolean checkSchema = importConfig.validateSchema();
    while (true)
    {
      LinkedList<StringBuilder> lines;
      DN entryDN;
      EntryID entryID;
      Suffix suffix;
      synchronized (this)
      {
        // Read the set of lines that make up the next entry.
        lines = readEntryLines();
        if (lines == null)
        {
          return null;
        }
        lastEntryBodyLines = lines;
        lastEntryHeaderLines = new LinkedList<>();

        // Read the DN of the entry and see if it is one that should be included
        // in the import.
        try
        {
          entryDN = readDN(lines);
        }
        catch (LDIFException e)
        {
          logger.traceException(e);
          continue;
        }

        if (entryDN == null)
        {
          // This should only happen if the LDIF starts with the "version:" line
          // and has a blank line immediately after that. In that case, simply
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
        suffix = getMatchSuffix(entryDN, suffixesMap);
        if (suffix == null)
        {
          logger.trace("Skipping entry %s because the DN is not one that "
              + "should be included based on a suffix match check.", entryDN);
          entriesRead.incrementAndGet();
          logToSkipWriter(lines, ERR_LDIF_SKIP.get(entryDN));
          continue;
        }
        entriesRead.incrementAndGet();
        entryID = rootContainer.getNextEntryID();
        suffix.addPending(entryDN);
      }

      // Create the entry and see if it is one that should be included in the import
      final Entry entry = createEntry(lines, entryDN, checkSchema, suffix);
      if (entry == null
          || !isIncludedInImport(entry, suffix, lines)
          || !invokeImportPlugins(entry, suffix, lines)
          || (checkSchema && !isValidAgainstSchema(entry, suffix, lines)))
      {
        continue;
      }
      return new EntryInformation(entry, entryID, suffix);
    }
  }

  private Entry createEntry(List<StringBuilder> lines, DN entryDN, boolean checkSchema, Suffix suffix)
  {
    // Read the set of attributes from the entry.
    Map<ObjectClass, String> objectClasses = new HashMap<>();
    Map<AttributeType, List<AttributeBuilder>> userAttrBuilders = new HashMap<>();
    Map<AttributeType, List<AttributeBuilder>> operationalAttrBuilders = new HashMap<>();
    try
    {
      for (StringBuilder line : lines)
      {
        readAttribute(lines, line, entryDN, objectClasses, userAttrBuilders, operationalAttrBuilders, checkSchema);
      }
    }
    catch (LDIFException e)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("Skipping entry %s because reading" + "its attributes failed.", entryDN);
      }
      logToSkipWriter(lines, ERR_LDIF_READ_ATTR_SKIP.get(entryDN, e.getMessage()));
      suffix.removePending(entryDN);
      return null;
    }

    final Entry entry = new Entry(entryDN, objectClasses,
        toAttributesMap(userAttrBuilders), toAttributesMap(operationalAttrBuilders));
    logger.trace("readEntry(), created entry: %s", entry);
    return entry;
  }

  private boolean isIncludedInImport(Entry entry, Suffix suffix, LinkedList<StringBuilder> lines)
  {
    final DN entryDN = entry.getName();
    try
    {
      if (!importConfig.includeEntry(entry))
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Skipping entry %s because the DN is not one that "
              + "should be included based on the include and exclude filters.", entryDN);
        }
        logToSkipWriter(lines, ERR_LDIF_SKIP.get(entryDN));
        suffix.removePending(entryDN);
        return false;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      suffix.removePending(entryDN);
      logToSkipWriter(lines,
          ERR_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_IMPORT.get(entry.getName(), lastEntryLineNumber, e));
      suffix.removePending(entryDN);
      return false;
    }
    return true;
  }

  private boolean invokeImportPlugins(final Entry entry, Suffix suffix, LinkedList<StringBuilder> lines)
  {
    if (importConfig.invokeImportPlugins())
    {
      PluginResult.ImportLDIF pluginResult = pluginConfigManager.invokeLDIFImportPlugins(importConfig, entry);
      if (!pluginResult.continueProcessing())
      {
        final DN entryDN = entry.getName();
        LocalizableMessage m;
        LocalizableMessage rejectMessage = pluginResult.getErrorMessage();
        if (rejectMessage != null)
        {
          m = ERR_LDIF_REJECTED_BY_PLUGIN.get(entryDN, rejectMessage);
        }
        else
        {
          m = ERR_LDIF_REJECTED_BY_PLUGIN_NOMESSAGE.get(entryDN);
        }

        logToRejectWriter(lines, m);
        suffix.removePending(entryDN);
        return false;
      }
    }
    return true;
  }

  private boolean isValidAgainstSchema(Entry entry, Suffix suffix, LinkedList<StringBuilder> lines)
  {
    final DN entryDN = entry.getName();
    addRDNAttributesIfNecessary(entryDN, entry.getUserAttributes(), entry.getOperationalAttributes());
    // Add any superior objectclass(s) missing in the objectclass map.
    addSuperiorObjectClasses(entry.getObjectClasses());

    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    if (!entry.conformsToSchema(null, false, true, false, invalidReason))
    {
      LocalizableMessage message = ERR_LDIF_SCHEMA_VIOLATION.get(entryDN, lastEntryLineNumber, invalidReason);
      logToRejectWriter(lines, message);
      suffix.removePending(entryDN);
      return false;
    }
    return true;
  }

  /**
   * Return the suffix instance in the specified map that matches the specified DN.
   *
   * @param dn
   *          The DN to search for.
   * @param map
   *          The map to search.
   * @return The suffix instance that matches the DN, or null if no match is found.
   */
  private Suffix getMatchSuffix(DN dn, Map<DN, Suffix> map)
  {
    DN nodeDN = dn;
    while (nodeDN != null)
    {
      final Suffix suffix = map.get(nodeDN);
      if (suffix != null)
      {
        return suffix;
      }
      nodeDN = nodeDN.getParentDNInSuffix();
    }
    return null;
  }
}
