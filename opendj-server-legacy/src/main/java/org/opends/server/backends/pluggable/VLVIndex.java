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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.IndexFilter.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.SortKey;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn.Scope;
import org.forgerock.opendj.server.config.server.BackendVLVIndexCfg;
import org.forgerock.util.Reject;
import org.opends.server.backends.pluggable.State.IndexFlag;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.controls.ServerSideSortRequestControl;
import org.opends.server.controls.VLVRequestControl;
import org.opends.server.controls.VLVResponseControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.StaticUtils;

/**
 * This class represents a VLV index.
 * <p>
 * Each record corresponds to a single entry matching the VLV criteria.
 * Keys are a sequence of the entry's normalized attribute values corresponding to the VLV sort order,
 * followed by the entry's entry ID.
 * Records do not have a "value" since all required information is held within the key.
 * The entry ID is included in the key as a "tie-breaker" and ensures that keys correspond to one and only one entry.
 * This ensures that all tree updates can be performed using lock-free operations.
 */
class VLVIndex extends AbstractTree implements ConfigurationChangeListener<BackendVLVIndexCfg>, Closeable
{
  private static final ByteString COUNT_KEY = ByteString.valueOfUtf8("nbRecords");

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The VLV vlvIndex configuration. */
  private BackendVLVIndexCfg config;

  /** The count of entries in this index. */
  private final ShardedCounter counter;

  private DN baseDN;
  private SearchScope scope;
  private SearchFilter filter;
  private List<SortKey> sortKeys;

  /** The storage associated with this index. */
  private final Storage storage;
  private final State state;

  /**
   * A flag to indicate if this vlvIndex should be trusted to be consistent with the entries tree.
   */
  private boolean trusted;

  VLVIndex(final BackendVLVIndexCfg config, final State state, final Storage storage,
      final EntryContainer entryContainer, final WriteableTransaction txn) throws StorageRuntimeException,
      ConfigException
  {
    super(new TreeName(entryContainer.getTreePrefix(), "vlv." + config.getName()));
    this.counter = new ShardedCounter(new TreeName(entryContainer.getTreePrefix(), "counter.vlv." + config.getName()));
    this.config = config;
    this.baseDN = config.getBaseDN();
    this.scope = convertScope(config.getScope());
    this.storage = storage;

    final ConfigChangeResult ccr = new ConfigChangeResult();
    this.filter = parseSearchFilter(config, getName().toString(), ccr);
    this.sortKeys = parseSortKeys(config.getSortOrder(), ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new ConfigException(ccr.getMessages().get(0));
    }

    this.state = state;
    this.trusted = state.getIndexFlags(txn, getName()).contains(IndexFlag.TRUSTED);
    if (!trusted && entryContainer.getHighestEntryID(txn).longValue() == 0)
    {
      /*
       * If there are no entries in the entry container then there is no reason why this vlvIndex
       * can't be upgraded to trusted.
       */
      setTrusted(txn, true);
    }

    this.config.addChangeListener(this);
  }

  private SearchScope convertScope(final Scope cfgScope)
  {
    switch (cfgScope)
    {
    case BASE_OBJECT:
      return SearchScope.BASE_OBJECT;
    case SINGLE_LEVEL:
      return SearchScope.SINGLE_LEVEL;
    case SUBORDINATE_SUBTREE:
      return SearchScope.SUBORDINATES;
    default: // WHOLE_SUBTREE
      return SearchScope.WHOLE_SUBTREE;
    }
  }

  @Override
  void afterOpen(final WriteableTransaction txn, boolean createOnDemand) throws StorageRuntimeException
  {
    counter.open(txn, createOnDemand);
  }

  @Override
  void beforeDelete(WriteableTransaction txn) throws StorageRuntimeException
  {
    counter.delete(txn);
  }

  void importCount(Importer importer, long count)
  {
    counter.importPut(importer, COUNT_KEY, count);
  }

  @Override
  public synchronized boolean isConfigurationChangeAcceptable(final BackendVLVIndexCfg cfg,
      final List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationAcceptable(cfg, getName().toString(), unacceptableReasons);
  }

  static boolean isConfigurationAddAcceptable(BackendVLVIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationAcceptable(cfg, cfg.getName(), unacceptableReasons);
  }

  private static boolean isConfigurationAcceptable(BackendVLVIndexCfg cfg,
      String indexName, List<LocalizableMessage> unacceptableReasons)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    parseSearchFilter(cfg, indexName, ccr);
    parseSortKeys(cfg.getSortOrder(), ccr, indexName);
    if (!ccr.getMessages().isEmpty())
    {
      unacceptableReasons.addAll(ccr.getMessages());
      return false;
    }
    return true;
  }

  private static SearchFilter parseSearchFilter(final BackendVLVIndexCfg cfg, String indexName,
      final ConfigChangeResult ccr)
  {
    try
    {
      SearchFilter result = SearchFilter.createFilterFromString(cfg.getFilter());
      ccr.setAdminActionRequired(true);
      return result;
    }
    catch (final Exception e)
    {
      ccr.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
      ccr.addMessage(ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(cfg.getFilter(), indexName, stackTraceToSingleLineString(e)));
      return null;
    }
  }

  @Override
  public synchronized ConfigChangeResult applyConfigurationChange(final BackendVLVIndexCfg cfg)
  {
    try
    {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(final WriteableTransaction txn) throws Exception
        {
          applyConfigurationChange0(txn, cfg, ccr);
        }
      });
      return ccr;
    }
    catch (final Exception e)
    {
      throw new StorageRuntimeException(e);
    }
  }

  private synchronized void applyConfigurationChange0(
      final WriteableTransaction txn, final BackendVLVIndexCfg cfg, final ConfigChangeResult ccr)
  {
    // Update base DN only if changed
    if (!config.getBaseDN().equals(cfg.getBaseDN()))
    {
      this.baseDN = cfg.getBaseDN();
      ccr.setAdminActionRequired(true);
    }

    // Update scope only if changed
    if (!config.getScope().equals(cfg.getScope()))
    {
      this.scope = convertScope(cfg.getScope());
      ccr.setAdminActionRequired(true);
    }

    // Update the filter only if changed
    if (!config.getFilter().equals(cfg.getFilter()))
    {
      this.filter = parseSearchFilter(cfg, getName().toString(), ccr);
    }

    // Update the sort order only if changed
    if (!config.getSortOrder().equals(cfg.getSortOrder()))
    {
      this.sortKeys = parseSortKeys(cfg.getSortOrder(), ccr);
      ccr.setAdminActionRequired(true);
    }

    if (ccr.adminActionRequired())
    {
      trusted = false;
      ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(getName()));
      try
      {
        state.removeFlagsFromIndex(txn, getName(), IndexFlag.TRUSTED);
      }
      catch (final StorageRuntimeException de)
      {
        ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(de)));
        ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      }
    }

    this.config = cfg;
  }

  private List<SortKey> parseSortKeys(final String sortOrder, ConfigChangeResult ccr)
  {
    return parseSortKeys(sortOrder, ccr, getName().toString());
  }

  private static List<SortKey> parseSortKeys(final String sortOrder, ConfigChangeResult ccr, String indexName)
  {
    final String[] sortAttrs = sortOrder.split(" ");
    final List<SortKey> sortKeys = new ArrayList<>(sortAttrs.length);
    for (String sortAttr : sortAttrs)
    {
      final boolean isReverseOrder;
      try
      {
        isReverseOrder = sortAttr.startsWith("-");

        if (sortAttr.startsWith("-") || sortAttr.startsWith("+"))
        {
          sortAttr = sortAttr.substring(1);
        }
      }
      catch (final Exception e)
      {
        ccr.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
        ccr.addMessage(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortAttr, indexName));
        return Collections.emptyList();
      }

      final AttributeDescription attrDesc = AttributeDescription.valueOf(sortAttr);
      final AttributeType attrType = attrDesc.getAttributeType();
      if (attrType.isPlaceHolder())
      {
        ccr.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
        ccr.addMessage(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortAttr, indexName));
        return Collections.emptyList();
      }
      if (attrType.getOrderingMatchingRule() == null)
      {
        ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(INFO_SORTREQ_CONTROL_NO_ORDERING_RULE_FOR_ATTR.get(attrType.getNameOrOID()));
        return Collections.emptyList();
      }
      sortKeys.add(new SortKey(sortAttr, isReverseOrder));
    }
    return sortKeys;
  }

  @Override
  public void close()
  {
    this.config.removeChangeListener(this);
  }

  boolean isTrusted()
  {
    return trusted;
  }

  synchronized void setTrusted(final WriteableTransaction txn, final boolean trusted) throws StorageRuntimeException
  {
    this.trusted = trusted;
    if ( trusted ) {
      state.addFlagsToIndex(txn, getName(), IndexFlag.TRUSTED);
    } else {
      state.removeFlagsFromIndex(txn, getName(), IndexFlag.TRUSTED);
    }
  }

  void addEntry(final IndexBuffer buffer, final EntryID entryID, final Entry entry) throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      addEntry0(buffer, entryID, entry);
    }
  }

  private void addEntry0(final IndexBuffer buffer, final EntryID entryID, final Entry entry)
  {
    buffer.put(this, toKey(entry, entryID));
  }

  ByteString toKey(final Entry entry, final EntryID entryID)
  {
    return encodeVLVKey(entry, entryID.longValue());
  }

  ByteString toValue()
  {
    return ByteString.empty();
  }

  private boolean shouldInclude(final Entry entry) throws DirectoryException
  {
    return entry.getName().isInScopeOf(baseDN, scope) && filter.matchesEntry(entry);
  }

  void modifyEntry(final IndexBuffer buffer, final EntryID entryID, final Entry oldEntry, final Entry newEntry,
      final List<Modification> mods) throws StorageRuntimeException, DirectoryException
  {
    if (shouldInclude(oldEntry))
    {
      if (shouldInclude(newEntry))
      {
        // The entry should still be indexed. See if any sorted attributes are changed.
        if (isSortAttributeModified(mods))
        {
          // Sorted attributes have changed. Reindex the entry.
          removeEntry0(buffer, entryID, oldEntry);
          addEntry0(buffer, entryID, newEntry);
        }
      }
      else
      {
        // The modifications caused the new entry to be unindexed. Remove from vlvIndex.
        removeEntry0(buffer, entryID, oldEntry);
      }
    }
    else if (shouldInclude(newEntry))
    {
      // The modifications caused the new entry to be indexed. Add to vlvIndex
      addEntry0(buffer, entryID, newEntry);
    }
  }

  private boolean isSortAttributeModified(final List<Modification> mods)
  {
    for (final SortKey sortKey : sortKeys)
    {
      final AttributeDescription attrDesc = AttributeDescription.valueOf(sortKey.getAttributeDescription());
      if (EntryContainer.isAttributeModified(attrDesc.getAttributeType(), mods))
      {
        return true;
      }
    }
    return false;
  }

  void removeEntry(final IndexBuffer buffer, final EntryID entryID, final Entry entry) throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      removeEntry0(buffer, entryID, entry);
    }
  }

  private void removeEntry0(final IndexBuffer buffer, final EntryID entryID, final Entry entry)
  {
    buffer.remove(this, toKey(entry, entryID));
  }

  void updateIndex(final WriteableTransaction txn, final TreeSet<ByteString> addedkeys,
      final TreeSet<ByteString> deletedKeys) throws StorageRuntimeException
  {
    // Perform all updates in key order.
    final Iterator<ByteString> ai = iteratorFor(addedkeys);
    ByteString nextAddedKey = nextOrNull(ai);

    final Iterator<ByteString> di = iteratorFor(deletedKeys);
    ByteString nextDeletedKey = nextOrNull(di);

    while (nextAddedKey != null || nextDeletedKey != null)
    {
      if (nextDeletedKey == null || (nextAddedKey != null && nextAddedKey.compareTo(nextDeletedKey) < 0))
      {
        txn.put(getName(), nextAddedKey, toValue());
        nextAddedKey = nextOrNull(ai);
        counter.addCount(txn, COUNT_KEY, 1);
      }
      else
      {
        txn.delete(getName(), nextDeletedKey);
        nextDeletedKey = nextOrNull(di);
        counter.addCount(txn, COUNT_KEY, -1);
      }
    }
  }

  private Iterator<ByteString> iteratorFor(final TreeSet<ByteString> sortValues)
  {
    return sortValues != null ? sortValues.iterator() : Collections.<ByteString> emptySet().iterator();
  }

  private ByteString nextOrNull(final Iterator<ByteString> i)
  {
    return i.hasNext() ? i.next() : null;
  }

  EntryIDSet evaluate(final ReadableTransaction txn, final SearchOperation searchOperation,
      final ServerSideSortRequestControl sortControl, final VLVRequestControl vlvRequest,
      final StringBuilder debugBuilder) throws DirectoryException, StorageRuntimeException
  {
    if (!trusted ||
        !searchOperation.getBaseDN().equals(baseDN) ||
        !searchOperation.getScope().equals(scope) ||
        !searchOperation.getFilter().equals(filter) ||
        !sortControl.getSortKeys().equals(sortKeys))
    {
      return null;
    }

    if (debugBuilder != null)
    {
      debugBuilder.append("vlv=[INDEX:");
      debugBuilder.append(getName().getIndexId());
      debugBuilder.append("]");
    }

    if (vlvRequest != null)
    {
      if (vlvRequest.getTargetType() == VLVRequestControl.TYPE_TARGET_BYOFFSET)
      {
        return evaluateVLVRequestByOffset(txn, searchOperation, vlvRequest, debugBuilder);
      }
      return evaluateVLVRequestByAssertion(txn, searchOperation, vlvRequest);
    }
    return evaluateNonVLVRequest(txn, debugBuilder);
  }

  private EntryIDSet evaluateNonVLVRequest(final ReadableTransaction txn, final StringBuilder debugBuilder)
  {
    // prevents creating a very large long array holding all the entries stored in the VLV index (see readRange())
    final int entryCount = getEntryCount(txn);
    if (entryCount <= CURSOR_ENTRY_LIMIT)
    {
      try (Cursor<ByteString, ByteString> cursor = txn.openCursor(getName()))
      {
        if (cursor.next())
        {
          // FIXME the array returned by readRange() is not ordered like a defined EntryIDSet expects
          return newDefinedSet(readRange(cursor, entryCount, debugBuilder));
        }
      }
    }
    return null;
  }

  /** Returns the total number of entries (a.k.a records, a.k.a keys) indexed by this VLV index. */
  private int getEntryCount(final ReadableTransaction txn)
  {
    return (int) counter.getCount(txn, COUNT_KEY);
  }

  /**
   * Reads a page of entries from the VLV which includes the nearest entry corresponding to the VLV
   * assertion, {@code beforeCount} entries leading up to the nearest entry, and {@code afterCount}
   * entries following the nearest entry.
   */
  private EntryIDSet evaluateVLVRequestByAssertion(final ReadableTransaction txn,
      final SearchOperation searchOperation, final VLVRequestControl vlvRequest)
      throws DirectoryException
  {
    final int currentCount = getEntryCount(txn);
    final int beforeCount = vlvRequest.getBeforeCount();
    final int afterCount = vlvRequest.getAfterCount();
    final ByteString assertion = vlvRequest.getGreaterThanOrEqualAssertion();
    final ByteSequence encodedTargetAssertion =
        encodeTargetAssertion(sortKeys, assertion, searchOperation, currentCount);
    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(getName()))
    {
      final LinkedList<Long> selectedIDs = new LinkedList<>();
      int targetPosition = 0;

      // Don't waste cycles looking for an assertion that does not match anything.
      if (cursor.positionToKeyOrNext(encodedTargetAssertion) && cursor.positionToIndex(0))
      {
        /*
         * Unfortunately we need to iterate from the start of the index in order to correctly
         * calculate the target position.
         */
        boolean targetFound = false;
        int includedAfter = 0;
        do
        {
          final ByteString key = cursor.getKey();
          if (!targetFound)
          {
            selectedIDs.add(decodeEntryIDFromVLVKey(key));
            if (encodedTargetAssertion.compareTo(key) > 0)
            {
              if (targetPosition >= beforeCount)
              {
                // Strip out unwanted results.
                selectedIDs.removeFirst();
              }
              targetPosition++;
            }
            else
            {
              targetFound = true;
            }
          }
          else if (includedAfter < afterCount)
          {
            selectedIDs.add(decodeEntryIDFromVLVKey(key));
            includedAfter++;
          }
          else
          {
            break;
          }
        }
        while (cursor.next());
      }
      else
      {
        // Treat a non-matching assertion as matching beyond the end of the index.
        targetPosition = currentCount;
      }
      addVLVResponseControl(searchOperation, targetPosition + 1, currentCount, LDAPResultCode.SUCCESS);
      return newDefinedSet(toPrimitiveLongArray(selectedIDs)); // FIXME not ordered like a defined EntryIDSet expects
    }
  }

  private long[] toPrimitiveLongArray(final List<Long> entryIDs)
  {
    final long[] result = new long[entryIDs.size()];
    int i = 0;
    for (Long entryID : entryIDs)
    {
      result[i++] = entryID;
    }
    return result;
  }

  /** Normalize the assertion using the primary key's ordering matching rule. */
  static ByteSequence encodeTargetAssertion(final List<SortKey> sortKeys, final ByteString assertion,
      final SearchOperation searchOperation, final int resultSetSize) throws DirectoryException
  {
    final SortKey primarySortKey = sortKeys.get(0);
    try
    {
      /*
       * Over-allocate the buffer for the primary key since it will be larger than the unnormalized
       * value. For example it will definitely include a trailing separator byte, but may also
       * include some escaped bytes as well. 10 extra bytes should accommodate most inputs.
       */
      final ByteStringBuilder encodedPrimaryKey = new ByteStringBuilder(assertion.length() + 10);
      final MatchingRule matchingRule = getEffectiveOrderingRule(primarySortKey);
      final ByteString normalizedAttributeValue = matchingRule.normalizeAttributeValue(assertion);
      encodeVLVKeyValue(normalizedAttributeValue, encodedPrimaryKey, primarySortKey.isReverseOrder());
      return encodedPrimaryKey;
    }
    catch (final DecodeException e)
    {
      addVLVResponseControl(searchOperation, 0, resultSetSize, LDAPResultCode.OFFSET_RANGE_ERROR);
      final String attrDesc = primarySortKey.getAttributeDescription();
      throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR, ERR_VLV_BAD_ASSERTION.get(attrDesc));
    }
  }

  private EntryIDSet evaluateVLVRequestByOffset(final ReadableTransaction txn, final SearchOperation searchOperation,
      final VLVRequestControl vlvRequest, final StringBuilder debugBuilder) throws DirectoryException
  {
    final int currentCount = getEntryCount(txn);
    int beforeCount = vlvRequest.getBeforeCount();
    int afterCount = vlvRequest.getAfterCount();
    int targetOffset = vlvRequest.getOffset();

    if (targetOffset < 0)
    {
      // The client specified a negative target offset. This should never be allowed.
      addVLVResponseControl(searchOperation, targetOffset, currentCount, LDAPResultCode.OFFSET_RANGE_ERROR);
      final LocalizableMessage message = ERR_ENTRYIDSORTER_NEGATIVE_START_POS.get();
      throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR, message);
    }
    else if (targetOffset == 0)
    {
      /*
       * This is an easy mistake to make, since VLV offsets start at 1 instead of 0. We'll assume
       * the client meant to use 1.
       */
      targetOffset = 1;
    }

    int listOffset = targetOffset - 1; // VLV offsets start at 1, not 0.
    int startPos = listOffset - beforeCount;
    if (startPos < 0)
    {
      /*
       * This can happen if beforeCount >= offset, and in this case we'll just adjust the start
       * position to ignore the range of beforeCount that doesn't exist.
       */
      startPos = 0;
      beforeCount = listOffset;
    }
    else if (startPos >= currentCount)
    {
      /*
       * The start position is beyond the end of the list. In this case, we'll assume that the start
       * position was one greater than the size of the list and will only return the beforeCount
       * entries. The start position is beyond the end of the list. In this case, we'll assume that
       * the start position was one greater than the size of the list and will only return the
       * beforeCount entries.
       */
      targetOffset = currentCount + 1;
      listOffset = currentCount;
      startPos = listOffset - beforeCount;
      afterCount = 0;
    }

    final long[] selectedIDs;
    final int count = 1 + beforeCount + afterCount;
    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(getName()))
    {
      if (cursor.positionToIndex(startPos))
      {
        selectedIDs = readRange(cursor, count, debugBuilder);
      }
      else
      {
        selectedIDs = new long[0];
      }
    }
    addVLVResponseControl(searchOperation, targetOffset, currentCount, LDAPResultCode.SUCCESS);
    return newDefinedSet(selectedIDs); // FIXME not ordered like a defined EntryIDSet expects
  }

  private static void addVLVResponseControl(SearchOperation searchOp, int targetPosition, int contentCount,
      int vlvResultCode)
  {
    searchOp.addResponseControl(new VLVResponseControl(targetPosition, contentCount, vlvResultCode));
  }

  private long[] readRange(final Cursor<ByteString, ByteString> definedCursor, final int count,
      final StringBuilder debugBuilder)
  {
    Reject.ifFalse(definedCursor.isDefined(), "Expected a defined cursor");
    long[] selectedIDs = new long[count];
    int selectedPos = 0;
    if (count > 0)
    {
      do
      {
        final ByteString key = definedCursor.getKey();
        logSearchKeyResult(key);
        selectedIDs[selectedPos++] = decodeEntryIDFromVLVKey(key);
      }
      while (selectedPos < count && definedCursor.next());
      if (selectedPos < count)
      {
        /*
         * We don't have enough entries in the set to meet the requested page size, so we'll need to
         * shorten the array.
         */
        selectedIDs = Arrays.copyOf(selectedIDs, selectedPos);
      }
    }
    if (debugBuilder != null)
    {
      debugBuilder.append("[COUNT:");
      debugBuilder.append(selectedIDs.length);
      debugBuilder.append("]");
    }
    return selectedIDs;
  }

  static long decodeEntryIDFromVLVKey(final ByteString key)
  {
    final int sizeOfEncodedEntryID = 8;
    return key.subSequence(key.length() - sizeOfEncodedEntryID, key.length()).asReader().readLong();
  }

  private void logSearchKeyResult(final ByteString key)
  {
    if (logger.isTraceEnabled())
    {
      final StringBuilder searchKeyHex = new StringBuilder();
      byteArrayToHexPlusAscii(searchKeyHex, key.toByteArray(), 4);
      final StringBuilder foundKeyHex = new StringBuilder();
      byteArrayToHexPlusAscii(foundKeyHex, key.toByteArray(), 4);
      logger.trace("Retrieved a sort values set in VLV vlvIndex %s\n" + "Search Key:%s\nFound Key:%s\n",
          config.getName(), searchKeyHex, foundKeyHex);
    }
  }

  boolean verifyEntry(final ReadableTransaction txn, final EntryID entryID, final Entry entry)
      throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      final ByteString key = toKey(entry, entryID);
      return txn.read(getName(), key) != null;
    }
    return false;
  }

  private ByteString encodeVLVKey(final Entry entry, final long entryID)
  {
    return encodeVLVKey(sortKeys, entry, entryID);
  }

  static ByteString encodeVLVKey(final List<SortKey> sortKeys, final Entry entry, final long entryID)
  {
    final ByteStringBuilder builder = new ByteStringBuilder();
    encodeVLVKey0(sortKeys, entry, builder);
    builder.appendLong(entryID);
    return builder.toByteString();
  }

  private static void encodeVLVKey0(final List<SortKey> sortKeys, final Entry entry, final ByteStringBuilder builder)
  {
    for (final SortKey sortKey : sortKeys)
    {
      ByteString sortValue = getLowestAttributeValue(entry, sortKey);
      encodeVLVKeyValue(sortValue, builder, sortKey.isReverseOrder());
    }
  }

  /**
   * The RFC states that the lowest value of a multi-valued attribute should be used,
   * regardless of the sort order.
   */
  private static ByteString getLowestAttributeValue(final Entry entry, final SortKey sortKey)
  {
    final AttributeDescription attrDesc = AttributeDescription.valueOf(sortKey.getAttributeDescription());
    final MatchingRule matchingRule = getEffectiveOrderingRule(sortKey);
    ByteString sortValue = null;
    for (Attribute a : entry.getAttribute(attrDesc.getAttributeType()))
    {
      for (ByteString v : a)
      {
        try
        {
          final ByteString nv = matchingRule.normalizeAttributeValue(v);
          if (sortValue == null || nv.compareTo(sortValue) < 0)
          {
            sortValue = nv;
          }
        }
        catch (final DecodeException e)
        {
          /*
           * This shouldn't happen because the attribute should have already been validated.
           * If it does then treat the value as missing.
           */
          continue;
        }
      }
    }
    return sortValue;
  }

  private static MatchingRule getEffectiveOrderingRule(SortKey sortKey)
  {
    String mrOid = sortKey.getOrderingMatchingRule();
    if (mrOid != null)
    {
      try
      {
        return getSchema().getMatchingRule(mrOid);
      }
      catch (UnknownSchemaElementException e)
      {
      }
    }
    AttributeDescription attrDesc = AttributeDescription.valueOf(sortKey.getAttributeDescription());
    return attrDesc.getAttributeType().getOrderingMatchingRule();
  }

  /**
   * Package private for testing.
   * <p>
   * Keys are logically encoded as follows:
   * <ul>
   * <li>if the key is {@code null} then append {@code 0xff} in order to ensure that all
   * {@code null} keys sort after non-{@code null} keys in ascending order
   * <li>else
   * <ul>
   * <li>escape any bytes that look like a separator byte ({@code 0x00}) or a separator escape byte
   * ({@code 0x01}) by prefixing the byte with a separator escape byte ({@code 0x01})
   * <li>escape the first byte if it looks like a null key byte ({@code 0xff}) or a null key escape
   * byte ({@code 0xfe}) by prefixing the byte with a null key escape byte ({@code 0xfe})
   * </ul>
   * <li>append a separator byte ({@code 0x00}) which will be used for distinguishing between the
   * end of the key and the start of the next key
   * <li>invert all the bytes if the sort order is descending.
   * </ul>
   */
  static void encodeVLVKeyValue(final ByteString keyBytes, final ByteStringBuilder builder,
      final boolean isReverseOrder)
  {
    final byte separator = !isReverseOrder ? (byte) 0x00 : (byte) 0xff;
    if (keyBytes != null)
    {
      final byte escape = !isReverseOrder ? (byte) 0x01 : (byte) 0xfe;
      final byte sortOrderMask = separator;
      final int length = keyBytes.length();
      for (int i = 0; i < length; i++)
      {
        final byte b = keyBytes.byteAt(i);
        if ((b & (byte) 0x01) == b)
        {
          // Escape bytes that look like a separator.
          builder.appendByte(escape);
        }
        else if (i == 0 && (b & (byte) 0xfe) == (byte) 0xfe)
        {
          /*
           * Ensure that all keys sort before (ascending) or after (descending) null keys, by
           * escaping the first byte if it looks like a null key.
           */
          builder.appendByte(~escape);
        }
        // Invert the bits if this key is in descending order.
        builder.appendByte(b ^ sortOrderMask);
      }
    }
    else
    {
      // Ensure that null keys sort after (ascending) or before (descending) all other keys.
      builder.appendByte(!isReverseOrder ? 0xFF : 0x00);
    }
    builder.appendByte(separator);
  }

  @Override
  public String keyToString(ByteString key)
  {
    return String.valueOf(decodeEntryIDFromVLVKey(key));
  }

  @Override
  public String valueToString(ByteString value)
  {
    return "N/A";
  }

  void closeAndDelete(WriteableTransaction txn)
  {
    close();
    delete(txn);
    state.deleteRecord(txn, getName());
  }
}
