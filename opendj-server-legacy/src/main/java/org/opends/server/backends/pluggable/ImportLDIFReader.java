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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.messages.BackendMessages.ERR_IMPORT_DUPLICATE_ENTRY;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.util.Pair;
import org.forgerock.util.Reject;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;

/** This class specializes the LDIFReader for imports. */
final class ImportLDIFReader extends LDIFReader
{
  private final ConcurrentHashMap<DN, CountDownLatch> pendingMap = new ConcurrentHashMap<>();

  /**
   * A class holding the entry, its entryID as assigned by the LDIF reader and its suffix as
   * determined by the LDIF reader.
   */
  static final class EntryInformation
  {
    private final Entry entry;
    private final EntryID entryID;
    private final EntryContainer entryContainer;

    private EntryInformation(Entry entry, EntryID entryID, EntryContainer entryContainer)
    {
      this.entry = entry;
      this.entryID = entryID;
      this.entryContainer = entryContainer;
    }

    Entry getEntry()
    {
      return entry;
    }

    EntryID getEntryID()
    {
      return entryID;
    }

    EntryContainer getEntryContainer()
    {
      return entryContainer;
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
   *         data is reached of if the import has been cancelled.
   * @param suffixesMap
   *          A map of entry containers instances.
   * @throws IOException
   *           If an I/O problem occurs while reading from the file.
   * @throws LDIFException
   *           If the information read cannot be parsed as an LDIF entry.
   */
  public final EntryInformation readEntry(Map<DN, EntryContainer> suffixesMap) throws IOException, LDIFException
  {
    final boolean checkSchema = importConfig.validateSchema();
    while (true)
    {
      LinkedList<StringBuilder> lines;
      DN entryDN;
      EntryID entryID;
      final EntryContainer entryContainer;
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

        entriesRead.incrementAndGet();

        final Pair<Boolean, LocalizableMessage> includeResult = importConfig.includeEntry(entryDN);
        if (!includeResult.getFirst())
        {
          logToSkipWriter(lines, includeResult.getSecond());
          continue;
        }
        entryContainer = getEntryContainer(entryDN, suffixesMap);
        if (entryContainer == null)
        {
          logger.trace("Skipping entry %s because the DN is not one that "
              + "should be included based on a suffix match check.", entryDN);
          logToSkipWriter(lines, ERR_LDIF_SKIP.get(entryDN));
          continue;
        }
        entryID = rootContainer.getNextEntryID();

        if (!addPending(entryDN))
        {
          logger.trace("Skipping entry %s because the DN already exists.", entryDN);
          logToSkipWriter(lines, ERR_IMPORT_DUPLICATE_ENTRY.get(entryDN));
          continue;
        }
      }

      // Create the entry and see if it is one that should be included in the import
      final Entry entry = createEntry(lines, entryDN, checkSchema);
      if (entry == null
          || !isIncludedInImport(entry, lines)
          || !invokeImportPlugins(entry, lines)
          || (checkSchema && !isValidAgainstSchema(entry, lines)))
      {
        removePending(entryDN);
        continue;
      }
      return new EntryInformation(entry, entryID, entryContainer);
    }
  }

  private Entry createEntry(List<StringBuilder> lines, DN entryDN, boolean checkSchema)
  {
    // Read the set of attributes from the entry.
    Map<ObjectClass, String> objectClasses = new HashMap<>();
    Map<AttributeType, List<AttributeBuilder>> userAttrBuilders = new HashMap<>(lines.size());
    Map<AttributeType, List<AttributeBuilder>> operationalAttrBuilders = new HashMap<>(lines.size());
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
      return null;
    }

    final Entry entry = new Entry(entryDN, objectClasses,
        toAttributesMap(userAttrBuilders), toAttributesMap(operationalAttrBuilders));
    logger.trace("readEntry(), created entry: %s", entry);
    return entry;
  }

  private boolean isIncludedInImport(Entry entry, LinkedList<StringBuilder> entryLines)
  {
    final DN entryDN = entry.getName();
    try
    {
      final Pair<Boolean, LocalizableMessage> includeResult = importConfig.includeEntry(entry);
      if (!includeResult.getFirst())
      {
        logToSkipWriter(entryLines, includeResult.getSecond());
        return false;
      }
      return true;
    }
    catch (Exception e)
    {
      logToSkipWriter(entryLines,
          ERR_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_IMPORT.get(entryDN, lastEntryLineNumber, e));
      return false;
    }
  }

  private boolean invokeImportPlugins(final Entry entry, LinkedList<StringBuilder> lines)
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
        return false;
      }
    }
    return true;
  }

  private boolean isValidAgainstSchema(Entry entry, LinkedList<StringBuilder> lines)
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
   * @return The entry container instance that matches the DN, or null if no match is found.
   */
  private EntryContainer getEntryContainer(DN dn, Map<DN, EntryContainer> map)
  {
    DN nodeDN = dn;
    while (nodeDN != null)
    {
      final EntryContainer entryContainer = map.get(nodeDN);
      if (entryContainer != null)
      {
        return entryContainer;
      }
      nodeDN = DirectoryServer.getInstance().getServerContext().getBackendConfigManager().getParentDNInSuffix(nodeDN);
    }
    return null;
  }

  /**
   * Make sure the specified parent DN is not in the pending map.
   *
   * @param parentDN The DN of the parent.
   */
  void waitIfPending(DN parentDN)  throws InterruptedException
  {
    final CountDownLatch l = pendingMap.get(parentDN);
    if (l != null)
    {
      l.await();
    }
  }

  /**
   * Add specified DN to the pending map.
   *
   * @param dn The DN to add to the map.
   * @return true if the DN was added, false if the DN is already present.
   */
  private boolean addPending(DN dn)
  {
    return pendingMap.putIfAbsent(dn, new CountDownLatch(1)) == null;
  }

  /**
   * Remove the specified DN from the pending map, it may not exist if the
   * entries are being migrated so just return.
   *
   * @param dn The DN to remove from the map.
   */
  void removePending(DN dn)
  {
    CountDownLatch l = pendingMap.remove(dn);
    if(l != null)
    {
      l.countDown();
    }
  }
}
