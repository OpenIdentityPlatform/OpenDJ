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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.JebMessages.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.newDefinedSet;
import static org.opends.server.util.StaticUtils.byteArrayToHexPlusAscii;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.BackendVLVIndexCfgDefn.Scope;
import org.opends.server.admin.std.server.BackendVLVIndexCfg;
import org.opends.server.backends.pluggable.spi.Cursor;
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
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SortKey;
import org.opends.server.types.SortOrder;
import org.opends.server.util.StaticUtils;

/**
 * This class represents a VLV index. Each database record corresponds to a single entry matching
 * the VLV criteria. Keys are a sequence of the entry's normalized attribute values corresponding to
 * the VLV sort order, followed by the entry's entry ID. Records do not have a "value" since all
 * required information is held within the key. The entry ID is included in the key as a
 * "tie-breaker" and ensures that keys correspond to one and only one entry. This ensures that all
 * database updates can be performed using lock-free operations.
 */
class VLVIndex extends DatabaseContainer implements ConfigurationChangeListener<BackendVLVIndexCfg>, Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /** The VLV vlvIndex configuration. */
  private BackendVLVIndexCfg config;

  /** The cached count of entries in this index. */
  private final AtomicInteger count = new AtomicInteger(0);

  private DN baseDN;
  private SearchScope scope;
  private SearchFilter filter;
  private SortOrder sortOrder;

  /** The storage associated with this index. */
  private final Storage storage;
  private final State state;

  /**
   * A flag to indicate if this vlvIndex should be trusted to be consistent with the entries
   * database.
   */
  private boolean trusted;

  VLVIndex(final BackendVLVIndexCfg config, final State state, final Storage storage,
      final EntryContainer entryContainer, final WriteableTransaction txn) throws StorageRuntimeException,
      ConfigException
  {
    super(new TreeName(entryContainer.getDatabasePrefix(), "vlv." + config.getName()));

    this.config = config;
    this.baseDN = config.getBaseDN();
    this.scope = convertScope(config.getScope());
    this.storage = storage;

    try
    {
      this.filter = SearchFilter.createFilterFromString(config.getFilter());
    }
    catch (final Exception e)
    {
      final LocalizableMessage msg = ERR_JEB_CONFIG_VLV_INDEX_BAD_FILTER.get(
          config.getFilter(), getName(), stackTraceToSingleLineString(e));
      throw new ConfigException(msg);
    }

    this.sortOrder = new SortOrder(parseSortKeys(config.getSortOrder()));
    this.state = state;
    this.trusted = state.getIndexTrustState(txn, this);
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
  void open(final WriteableTransaction txn) throws StorageRuntimeException
  {
    super.open(txn);
    count.set((int) txn.getRecordCount(getName()));
  }

  @Override
  public synchronized boolean isConfigurationChangeAcceptable(final BackendVLVIndexCfg cfg,
      final List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      SearchFilter.createFilterFromString(cfg.getFilter());
    }
    catch (final Exception e)
    {
      final LocalizableMessage msg = ERR_JEB_CONFIG_VLV_INDEX_BAD_FILTER.get(
          cfg.getFilter(), getName(), stackTraceToSingleLineString(e));
      unacceptableReasons.add(msg);
      return false;
    }

    try
    {
      parseSortKeys(cfg.getSortOrder());
    }
    catch (final ConfigException e)
    {
      unacceptableReasons.add(e.getMessageObject());
      return false;
    }

    return true;
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
      try
      {
        this.filter = SearchFilter.createFilterFromString(cfg.getFilter());
        ccr.setAdminActionRequired(true);
      }
      catch (final Exception e)
      {
        ccr.addMessage(ERR_JEB_CONFIG_VLV_INDEX_BAD_FILTER.get(config.getFilter(), getName(),
            stackTraceToSingleLineString(e)));
        ccr.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
      }
    }

    // Update the sort order only if changed
    if (!config.getSortOrder().equals(cfg.getSortOrder()))
    {
      try
      {
        this.sortOrder = new SortOrder(parseSortKeys(cfg.getSortOrder()));
      }
      catch (final ConfigException e)
      {
        ccr.addMessage(e.getMessageObject());
        ccr.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
      }
      ccr.setAdminActionRequired(true);
    }

    if (ccr.adminActionRequired())
    {
      trusted = false;
      ccr.addMessage(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(getName()));
      try
      {
        state.putIndexTrustState(txn, this, false);
      }
      catch (final StorageRuntimeException de)
      {
        ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(de)));
        ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      }
    }

    this.config = cfg;
  }

  private SortKey[] parseSortKeys(final String sortOrder) throws ConfigException
  {
    final String[] sortAttrs = sortOrder.split(" ");
    final SortKey[] sortKeys = new SortKey[sortAttrs.length];
    for (int i = 0; i < sortAttrs.length; i++)
    {
      final boolean ascending;
      try
      {
        if (sortAttrs[i].startsWith("-"))
        {
          ascending = false;
          sortAttrs[i] = sortAttrs[i].substring(1);
        }
        else
        {
          ascending = true;
          if (sortAttrs[i].startsWith("+"))
          {
            sortAttrs[i] = sortAttrs[i].substring(1);
          }
        }
      }
      catch (final Exception e)
      {
        throw new ConfigException(ERR_JEB_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], getName()));
      }

      final AttributeType attrType = DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
      if (attrType == null)
      {
        throw new ConfigException(ERR_JEB_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortAttrs[i], getName()));
      }
      sortKeys[i] = new SortKey(attrType, ascending);
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
    state.putIndexTrustState(txn, this, trusted);
  }

  void addEntry(final IndexBuffer buffer, final EntryID entryID, final Entry entry) throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      buffer.getBufferedVLVIndexValues(this).addValues(encodeVLVKey(entry, entryID.longValue()));
    }
  }

  private boolean shouldInclude(final Entry entry) throws DirectoryException
  {
    return entry.getName().matchesBaseAndScope(baseDN, scope) && filter.matchesEntry(entry);
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
          removeEntry(buffer, entryID, oldEntry);
          addEntry(buffer, entryID, newEntry);
        }
      }
      else
      {
        // The modifications caused the new entry to be unindexed. Remove from vlvIndex.
        removeEntry(buffer, entryID, oldEntry);
      }
    }
    else if (shouldInclude(newEntry))
    {
      // The modifications caused the new entry to be indexed. Add to vlvIndex
      addEntry(buffer, entryID, newEntry);
    }
  }

  private boolean isSortAttributeModified(final List<Modification> mods)
  {
    for (final SortKey sortKey : sortOrder.getSortKeys())
    {
      final AttributeType attributeType = sortKey.getAttributeType();
      final Iterable<AttributeType> subTypes = DirectoryServer.getSchema().getSubTypes(attributeType);
      for (final Modification mod : mods)
      {
        final AttributeType modAttrType = mod.getAttribute().getAttributeType();
        if (modAttrType.equals(attributeType))
        {
          return true;
        }
        for (final AttributeType subType : subTypes)
        {
          if (modAttrType.equals(subType))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  void removeEntry(final IndexBuffer buffer, final EntryID entryID, final Entry entry) throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      buffer.getBufferedVLVIndexValues(this).deleteValues(encodeVLVKey(entry, entryID.longValue()));
    }
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
        txn.put(getName(), nextAddedKey, ByteString.empty());
        nextAddedKey = nextOrNull(ai);
        count.incrementAndGet();
      }
      else
      {
        txn.delete(getName(), nextDeletedKey);
        nextDeletedKey = nextOrNull(di);
        count.decrementAndGet();
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
        !sortControl.getSortOrder().equals(sortOrder))
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
      return evaluateVLVRequestByAssertion(txn, searchOperation, vlvRequest, debugBuilder);
    }
    return evaluateNonVLVRequest(txn, debugBuilder);
  }

  private EntryIDSet evaluateNonVLVRequest(final ReadableTransaction txn, final StringBuilder debugBuilder)
  {
    final Cursor cursor = txn.openCursor(getName());
    try
    {
      final long[] selectedIDs = readRange(cursor, count.get(), debugBuilder);
      return newDefinedSet(selectedIDs);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Reads a page of entries from the VLV which includes the nearest entry corresponding to the VLV
   * assertion, {@code beforeCount} entries leading up to the nearest entry, and {@code afterCount}
   * entries following the nearest entry.
   */
  private EntryIDSet evaluateVLVRequestByAssertion(final ReadableTransaction txn,
      final SearchOperation searchOperation, final VLVRequestControl vlvRequest, final StringBuilder debugBuilder)
      throws DirectoryException
  {
    final int currentCount = count.get();
    final int beforeCount = vlvRequest.getBeforeCount();
    final int afterCount = vlvRequest.getAfterCount();

    final ByteString assertion = vlvRequest.getGreaterThanOrEqualAssertion();
    final ByteSequence encodedTargetAssertion =
        encodeTargetAssertion(sortOrder, assertion, searchOperation, currentCount);
    final Cursor cursor = txn.openCursor(getName());
    try
    {
      if (!cursor.positionToKeyOrNext(encodedTargetAssertion))
      {
        return newDefinedSet();
      }
      int count = afterCount;
      for (int i = 0; cursor.previous() && i < beforeCount; i++, count++)
      {
        // Empty block.
      }
      final long[] selectedIDs = readRange(cursor, count, debugBuilder);
      searchOperation.addResponseControl(new VLVResponseControl(count - afterCount, currentCount,
          LDAPResultCode.SUCCESS));
      return newDefinedSet(selectedIDs);
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Normalize the assertion using the primary key's ordering matching rule.
   */
  static ByteSequence encodeTargetAssertion(final SortOrder sortOrder, final ByteString assertion,
      final SearchOperation searchOperation, final int resultSetSize) throws DirectoryException
  {
    final SortKey primarySortKey = sortOrder.getSortKeys()[0];
    try
    {
      /*
       * Over-allocate the buffer for the primary key since it will be larger than the unnormalized
       * value. For example it will definitely include a trailing separator byte, but may also
       * include some escaped bytes as well. 10 extra bytes should accommodate most inputs.
       */
      final ByteStringBuilder encodedPrimaryKey = new ByteStringBuilder(assertion.length() + 10);
      final MatchingRule matchingRule = primarySortKey.getAttributeType().getOrderingMatchingRule();
      final ByteString normalizedAttributeValue = matchingRule.normalizeAttributeValue(assertion);
      encodeVLVKeyValue(primarySortKey, normalizedAttributeValue, encodedPrimaryKey);
      return encodedPrimaryKey;
    }
    catch (final DecodeException e)
    {
      searchOperation.addResponseControl(new VLVResponseControl(0, resultSetSize, LDAPResultCode.OFFSET_RANGE_ERROR));
      final String attributeName = primarySortKey.getAttributeType().getNameOrOID();
      throw new DirectoryException(ResultCode.VIRTUAL_LIST_VIEW_ERROR, ERR_VLV_BAD_ASSERTION.get(attributeName));
    }
  }

  private EntryIDSet evaluateVLVRequestByOffset(final ReadableTransaction txn, final SearchOperation searchOperation,
      final VLVRequestControl vlvRequest, final StringBuilder debugBuilder) throws DirectoryException
  {
    final int currentCount = count.get();
    int beforeCount = vlvRequest.getBeforeCount();
    int afterCount = vlvRequest.getAfterCount();
    int targetOffset = vlvRequest.getOffset();

    if (targetOffset < 0)
    {
      // The client specified a negative target offset. This should never be allowed.
      searchOperation.addResponseControl(new VLVResponseControl(targetOffset, currentCount,
          LDAPResultCode.OFFSET_RANGE_ERROR));
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

    final int count = 1 + beforeCount + afterCount;
    final Cursor cursor = txn.openCursor(getName());
    try
    {
      if (!cursor.positionToIndex(startPos))
      {
        return newDefinedSet();
      }
      final long[] selectedIDs = readRange(cursor, count, debugBuilder);
      searchOperation.addResponseControl(new VLVResponseControl(targetOffset, currentCount, LDAPResultCode.SUCCESS));
      return newDefinedSet(selectedIDs);
    }
    finally
    {
      cursor.close();
    }
  }

  private long[] readRange(final Cursor cursor, final int count, final StringBuilder debugBuilder)
  {
    long[] selectedIDs = new long[count];
    int selectedPos = 0;
    while (cursor.next() && selectedPos < count)
    {
      final ByteString key = cursor.getKey();
      if (logger.isTraceEnabled())
      {
        logSearchKeyResult(key);
      }
      selectedIDs[selectedPos++] = decodeEntryIDFromVLVKey(key);
    }
    if (selectedPos < count)
    {
      /*
       * We don't have enough entries in the set to meet the requested page size, so we'll need to
       * shorten the array.
       */
      selectedIDs = Arrays.copyOf(selectedIDs, selectedPos);
    }
    if (debugBuilder != null)
    {
      debugBuilder.append("[COUNT:");
      debugBuilder.append(selectedPos);
      debugBuilder.append("]");
    }
    return selectedIDs;
  }

  static long decodeEntryIDFromVLVKey(final ByteString key)
  {
    final int sizeOfEncodedEntryID = 8;
    return key.subSequence(key.length() - sizeOfEncodedEntryID, key.length()).asReader().getLong();
  }

  private void logSearchKeyResult(final ByteString key)
  {
    final StringBuilder searchKeyHex = new StringBuilder();
    byteArrayToHexPlusAscii(searchKeyHex, key.toByteArray(), 4);
    final StringBuilder foundKeyHex = new StringBuilder();
    byteArrayToHexPlusAscii(foundKeyHex, key.toByteArray(), 4);
    logger.trace("Retrieved a sort values set in VLV vlvIndex %s\n" + "Search Key:%s\nFound Key:%s\n",
        config.getName(), searchKeyHex, foundKeyHex);
  }

  boolean verifyEntry(final ReadableTransaction txn, final EntryID entryID, final Entry entry)
      throws DirectoryException
  {
    if (shouldInclude(entry))
    {
      final ByteString key = encodeVLVKey(entry, entryID.longValue());
      return txn.read(getName(), key) != null;
    }
    return false;
  }

  static ByteString encodeVLVKey(final SortOrder sortOrder, final Entry entry, final long entryID)
  {
    final ByteStringBuilder builder = new ByteStringBuilder();
    encodeVLVKey0(sortOrder, entry, builder);
    builder.append(entryID);
    return builder.toByteString();
  }

  ByteString encodeVLVKey(final Entry entry, final long entryID)
  {
    return encodeVLVKey(sortOrder, entry, entryID);
  }

  private static void encodeVLVKey0(final SortOrder sortOrder, final Entry entry, final ByteStringBuilder builder)
  {
    for (final SortKey sortKey : sortOrder.getSortKeys())
    {
      final ByteString value = findBestMatchingValue(sortKey, entry);
      final MatchingRule matchingRule = sortKey.getAttributeType().getOrderingMatchingRule();
      ByteString normalizedAttributeValue;
      try
      {
        normalizedAttributeValue = matchingRule.normalizeAttributeValue(value);
      }
      catch (final DecodeException e)
      {
        /*
         * This shouldn't happen because the attribute should have already been validated. If it
         * does then treat the value as missing.
         */
        normalizedAttributeValue = ByteString.empty();
      }
      encodeVLVKeyValue(sortKey, normalizedAttributeValue, builder);
    }
  }

  /**
   * Returns the value contained in the entry which should be used for the provided sort key. For
   * ascending order we select the highest value from the entry, and for descending order we select
   * the lowest.
   */
  private static ByteString findBestMatchingValue(final SortKey sortKey, final Entry entry)
  {
    final List<Attribute> attrList = entry.getAttribute(sortKey.getAttributeType());
    ByteString sortValue = null;
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (ByteString v : a)
        {
          if (sortValue == null || sortKey.compareValues(v, sortValue) < 0)
          {
            sortValue = v;
          }
        }
      }
    }
    return sortValue != null ? sortValue : ByteString.empty();
  }

  private static void encodeVLVKeyValue(final SortKey sortKey, final ByteString normalizedAttributeValue,
      final ByteStringBuilder builder)
  {
    final boolean ascending = sortKey.ascending();
    final byte separator = ascending ? (byte) 0x00 : (byte) 0xff;
    final byte escape = ascending ? (byte) 0x01 : (byte) 0xfe;
    final byte sortOrderMask = separator;

    final int length = normalizedAttributeValue.length();
    for (int i = 0; i < length; i++)
    {
      final byte b = normalizedAttributeValue.byteAt(i);
      if (b == separator || b == escape)
      {
        builder.append(escape);
      }
      // Invert the bits if this key is in descending order.
      builder.append((byte) (b ^ sortOrderMask));
    }
    builder.append(separator);
  }
}
