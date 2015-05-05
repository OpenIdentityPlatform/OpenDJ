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

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
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
import org.opends.server.backends.pluggable.State.IndexFlag;
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
@SuppressWarnings("javadoc")
class VLVIndex extends AbstractDatabaseContainer implements ConfigurationChangeListener<BackendVLVIndexCfg>, Closeable
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
      throw new ConfigException(ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(
          config.getFilter(), getName(), stackTraceToSingleLineString(e)));
    }

    this.sortOrder = new SortOrder(parseSortKeys(config.getSortOrder()));
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
  void open0(final ReadableTransaction txn) throws StorageRuntimeException
  {
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
      final LocalizableMessage msg = ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(
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
        ccr.addMessage(ERR_CONFIG_VLV_INDEX_BAD_FILTER.get(config.getFilter(), getName(),
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
        throw new ConfigException(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortKeys[i], getName()));
      }

      final AttributeType attrType = DirectoryServer.getAttributeType(sortAttrs[i].toLowerCase());
      if (attrType == null)
      {
        throw new ConfigException(ERR_CONFIG_VLV_INDEX_UNDEFINED_ATTR.get(sortAttrs[i], getName()));
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
      buffer.put(this, encodeVLVKey(entry, entryID.longValue()));
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
      buffer.remove(this, encodeVLVKey(entry, entryID.longValue()));
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
      return evaluateVLVRequestByAssertion(txn, searchOperation, vlvRequest);
    }
    return evaluateNonVLVRequest(txn, debugBuilder);
  }

  private EntryIDSet evaluateNonVLVRequest(final ReadableTransaction txn, final StringBuilder debugBuilder)
  {
    final Cursor<ByteString, ByteString> cursor = txn.openCursor(getName());
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
      final SearchOperation searchOperation, final VLVRequestControl vlvRequest)
      throws DirectoryException
  {
    final int currentCount = count.get();
    final int beforeCount = vlvRequest.getBeforeCount();
    final int afterCount = vlvRequest.getAfterCount();
    final ByteString assertion = vlvRequest.getGreaterThanOrEqualAssertion();
    final ByteSequence encodedTargetAssertion =
        encodeTargetAssertion(sortOrder, assertion, searchOperation, currentCount);
    final Cursor<ByteString, ByteString> cursor = txn.openCursor(getName());
    try
    {
      final LinkedList<Long> selectedIDs = new LinkedList<Long>();
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
      searchOperation.addResponseControl(new VLVResponseControl(targetPosition + 1, currentCount,
          LDAPResultCode.SUCCESS));
      return newDefinedSet(toPrimitiveLongArray(selectedIDs));
    }
    finally
    {
      cursor.close();
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
      encodeVLVKeyValue(normalizedAttributeValue, encodedPrimaryKey, primarySortKey.ascending());
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
    final Cursor<ByteString, ByteString> cursor = txn.openCursor(getName());
    try
    {
      final long[] selectedIDs;
      if (cursor.positionToIndex(startPos))
      {
        selectedIDs = readRange(cursor, count, debugBuilder);
      }
      else
      {
        selectedIDs = new long[0];
      }
      searchOperation.addResponseControl(new VLVResponseControl(targetOffset, currentCount, LDAPResultCode.SUCCESS));
      return newDefinedSet(selectedIDs);
    }
    finally
    {
      cursor.close();
    }
  }

  private long[] readRange(final Cursor<ByteString, ByteString> cursor, final int count,
      final StringBuilder debugBuilder)
  {
    long[] selectedIDs = new long[count];
    int selectedPos = 0;
    if (count > 0)
    {
      do
      {
        final ByteString key = cursor.getKey();
        if (logger.isTraceEnabled())
        {
          logSearchKeyResult(key);
        }
        selectedIDs[selectedPos++] = decodeEntryIDFromVLVKey(key);
      }
      while (selectedPos < count && cursor.next());
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
      final AttributeType attributeType = sortKey.getAttributeType();
      final MatchingRule matchingRule = attributeType.getOrderingMatchingRule();
      final List<Attribute> attrList = entry.getAttribute(attributeType);
      ByteString sortValue = null;
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (ByteString v : a)
          {
            try
            {
              /*
               * The RFC states that the lowest value of a multi-valued attribute should be used,
               * regardless of the sort order.
               */
              final ByteString nv = matchingRule.normalizeAttributeValue(v);
              if (sortValue == null || nv.compareTo(sortValue) < 0)
              {
                sortValue = nv;
              }
            }
            catch (final DecodeException e)
            {
              /*
               * This shouldn't happen because the attribute should have already been validated. If
               * it does then treat the value as missing.
               */
              continue;
            }
          }
        }
      }
      encodeVLVKeyValue(sortValue, builder, sortKey.ascending());
    }
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
      final boolean ascending)
  {
    final byte separator = ascending ? (byte) 0x00 : (byte) 0xff;
    if (keyBytes != null)
    {
      final byte escape = ascending ? (byte) 0x01 : (byte) 0xfe;
      final byte sortOrderMask = separator;
      final int length = keyBytes.length();
      for (int i = 0; i < length; i++)
      {
        final byte b = keyBytes.byteAt(i);
        if ((b & (byte) 0x01) == b)
        {
          // Escape bytes that look like a separator.
          builder.append(escape);
        }
        else if (i == 0 && (b & (byte) 0xfe) == (byte) 0xfe)
        {
          /*
           * Ensure that all keys sort before (ascending) or after (descending) null keys, by
           * escaping the first byte if it looks like a null key.
           */
          builder.append((byte) ~escape);
        }
        // Invert the bits if this key is in descending order.
        builder.append((byte) (b ^ sortOrderMask));
      }
    }
    else
    {
      // Ensure that null keys sort after (ascending) or before (descending) all other keys.
      builder.append(ascending ? (byte) 0xff : (byte) 0x00);
    }
    builder.append(separator);
  }

  void closeAndDelete(WriteableTransaction txn)
  {
    close();
    delete(txn);
    state.deleteRecord(txn, getName());
  }
}
