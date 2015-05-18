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
 *      Portions Copyright 2011-2015 ForgeRock AS
 *      Portions Copyright 2014 Manuel Gaupp
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;

/**
 * Class representing an attribute index.
 * We have a separate tree for each type of indexing, which makes it easy
 * to tell which attribute indexes are configured.  The different types of
 * indexing are equality, presence, substrings and ordering.  The keys in the
 * ordering index are ordered by setting the btree comparator to the ordering
 * matching rule comparator.
 * Note that the values in the equality index are normalized by the equality
 * matching rule, whereas the values in the ordering index are normalized
 * by the ordering matching rule.  If these could be guaranteed to be identical
 * then we would not need a separate ordering index.
 */
@SuppressWarnings("javadoc")
class AttributeIndex
    implements ConfigurationChangeListener<BackendIndexCfg>, Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Type of the index filter. */
  static enum IndexFilterType
  {
    /** Equality. */
    EQUALITY(IndexType.EQUALITY),
    /** Presence. */
    PRESENCE(IndexType.PRESENCE),
    /** Ordering. */
    GREATER_OR_EQUAL(IndexType.ORDERING),
    /** Ordering. */
    LESS_OR_EQUAL(IndexType.ORDERING),
    /** Substring. */
    SUBSTRING(IndexType.SUBSTRING),
    /** Approximate. */
    APPROXIMATE(IndexType.APPROXIMATE);

    private final IndexType indexType;

    private IndexFilterType(IndexType indexType)
    {
      this.indexType = indexType;
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return indexType.toString();
    }
  }

  /**
   * This class implements an attribute indexer for matching rules in a Backend.
   */
  static final class MatchingRuleIndex extends DefaultIndex
  {
    private final AttributeType attributeType;
    private final Indexer indexer;

    private MatchingRuleIndex(EntryContainer entryContainer, AttributeType attributeType, State state, Indexer indexer,
        int indexEntryLimit)
    {
      super(getIndexName(entryContainer, attributeType, indexer.getIndexID()), state, indexEntryLimit, entryContainer);
      this.attributeType = attributeType;
      this.indexer = indexer;
    }

    void indexEntry(Entry entry, Set<ByteString> keys)
    {
      List<Attribute> attributes = entry.getAttribute(attributeType, true);
      if (attributes != null)
      {
        indexAttribute(attributes, keys);
      }
    }

    private void modifyEntry(Entry oldEntry, Entry newEntry, Map<ByteString, Boolean> modifiedKeys)
    {
      List<Attribute> oldAttributes = oldEntry.getAttribute(attributeType, true);
      if (oldAttributes != null)
      {
        final Set<ByteString> keys = new HashSet<ByteString>();
        indexAttribute(oldAttributes, keys);
        for (ByteString key : keys)
        {
          modifiedKeys.put(key, false);
        }
      }

      List<Attribute> newAttributes = newEntry.getAttribute(attributeType, true);
      if (newAttributes != null)
      {
        final Set<ByteString> keys = new HashSet<ByteString>();
        indexAttribute(newAttributes, keys);
        for (ByteString key : keys)
        {
          final Boolean needsAdding = modifiedKeys.get(key);
          if (needsAdding == null)
          {
            // This value has been added.
            modifiedKeys.put(key, true);
          }
          else if (!needsAdding)
          {
            // This value has not been added or removed.
            modifiedKeys.remove(key);
          }
        }
      }
    }

    private void indexAttribute(List<Attribute> attributes, Set<ByteString> keys)
    {
      for (Attribute attr : attributes)
      {
        if (!attr.isVirtual())
        {
          for (ByteString value : attr)
          {
            try
            {
              indexer.createKeys(Schema.getDefaultSchema(), value, keys);

              /*
               * Optimization for presence: return immediately after first value since all values
               * have the same key.
               */
              if (indexer == PRESENCE_INDEXER)
              {
                return;
              }
            }
            catch (DecodeException e)
            {
              logger.traceException(e);
            }
          }
        }
      }
    }
  }

  /** The key bytes used for the presence index as a {@link ByteString}. */
  static final ByteString PRESENCE_KEY = ByteString.valueOf("+");

  /**
   * A special indexer for generating presence indexes.
   */
  private static final Indexer PRESENCE_INDEXER = new Indexer()
  {
    @Override
    public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException
    {
      keys.add(PRESENCE_KEY);
    }

    @Override
    public String getIndexID()
    {
      return IndexType.PRESENCE.toString();
    }
  };

  /*
   * FIXME Matthew Swift: Once the matching rules have been migrated we should
   * revisit this class. All of the evaluateXXX methods should go (the Matcher
   * class in the SDK could implement the logic, I hope).
   */

  /** The entryContainer in which this attribute index resides. */
  private final EntryContainer entryContainer;

  /** The attribute index configuration. */
  private BackendIndexCfg config;

  /** The mapping from names to indexes. */
  private Map<String, MatchingRuleIndex> indexIdToIndexes;
  private IndexingOptions indexingOptions;
  private final State state;

  AttributeIndex(BackendIndexCfg config, State state, EntryContainer entryContainer) throws ConfigException
  {
    this.entryContainer = entryContainer;
    this.config = config;
    this.state = state;
    this.indexingOptions = new IndexingOptionsImpl(config.getSubstringLength());
    this.indexIdToIndexes = Collections.unmodifiableMap(buildIndexes(entryContainer, state, config));
  }

  private static Map<String, MatchingRuleIndex> buildIndexes(EntryContainer entryContainer, State state,
      BackendIndexCfg config) throws ConfigException
  {
    final AttributeType attributeType = config.getAttribute();
    final int indexEntryLimit = config.getIndexEntryLimit();
    final IndexingOptions indexingOptions = new IndexingOptionsImpl(config.getSubstringLength());

    final Map<String, MatchingRuleIndex> indexes = new HashMap<>();

    for(IndexType indexType : config.getIndexType()) {
      Collection<? extends Indexer> indexers;
      switch (indexType)
      {
      case PRESENCE:
        indexers = Collections.singleton(PRESENCE_INDEXER);
        break;
      case EXTENSIBLE:
        indexers =
            getExtensibleIndexers(config.getAttribute(), config.getIndexExtensibleMatchingRule(), indexingOptions);
        break;
      case APPROXIMATE:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getApproximateMatchingRule()).createIndexers(
                indexingOptions);
        break;
      case EQUALITY:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getEqualityMatchingRule()).createIndexers(
                indexingOptions);
        break;
      case ORDERING:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getOrderingMatchingRule()).createIndexers(
                indexingOptions);
        break;
      case SUBSTRING:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getSubstringMatchingRule()).createIndexers(
                indexingOptions);
        break;
      default:
       throw new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attributeType, indexType.toString()));
      }
      buildIndexesForIndexers(entryContainer, attributeType, state, indexes, indexEntryLimit, indexers);
    }
    return indexes;
  }

  private static MatchingRule throwIfNoMatchingRule(AttributeType attributeType, IndexType type, MatchingRule rule)
      throws ConfigException
  {
    if (rule == null)
    {
      throw new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attributeType, type.toString()));
    }
    return rule;
  }

  private static void buildIndexesForIndexers(EntryContainer entryContainer, AttributeType attributeType, State state,
      Map<String, MatchingRuleIndex> indexes, int indexEntryLimit, Collection<? extends Indexer> indexers)
  {
    for (Indexer indexer : indexers)
    {
      final String indexID = indexer.getIndexID();
      if (!indexes.containsKey(indexID))
      {
        indexes.put(indexID, new MatchingRuleIndex(entryContainer, attributeType, state, indexer, indexEntryLimit));
      }
    }
  }

  private static Collection<Indexer> getExtensibleIndexers(AttributeType attributeType, Set<String> extensibleRules,
      IndexingOptions options) throws ConfigException
  {
    if (extensibleRules == null || extensibleRules.isEmpty())
    {
      throw new ConfigException(
          ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attributeType, IndexType.EXTENSIBLE.toString()));
    }

    final Collection<Indexer> indexers = new ArrayList<>();
    for (final String ruleName : extensibleRules)
    {
      final MatchingRule rule = DirectoryServer.getMatchingRule(toLowerCase(ruleName));
      throwIfNoMatchingRule(attributeType, IndexType.EXTENSIBLE, rule);
      indexers.addAll(rule.createIndexers(options));
    }

    return indexers;
  }

  private static TreeName getIndexName(EntryContainer entryContainer, AttributeType attrType, String indexID)
  {
    return new TreeName(entryContainer.getTreePrefix(), attrType.getNameOrOID() + "." + indexID);
  }

  /**
   * Open the attribute index.
   *
   * @param txn a non null transaction
   * @throws StorageRuntimeException if an error occurs while opening the index
   */
  void open(WriteableTransaction txn) throws StorageRuntimeException
  {
    for (Index index : indexIdToIndexes.values())
    {
      index.open(txn);
    }
    config.addChangeListener(this);
  }

  @Override
  public void close()
  {
    config.removeChangeListener(this);
  }

  /**
   * Get the attribute type of this attribute index.
   * @return The attribute type of this attribute index.
   */
  AttributeType getAttributeType()
  {
    return config.getAttribute();
  }

  /**
   * Return the indexing options of this AttributeIndex.
   *
   * @return the indexing options of this AttributeIndex.
   */
  IndexingOptions getIndexingOptions()
  {
    return indexingOptions;
  }

  /**
   * Returns {@code true} if this attribute index supports the provided index type.
   *
   * @param indexType
   *          The index type.
   * @return {@code true} if this attribute index supports the provided index type.
   */
  boolean isIndexed(org.opends.server.types.IndexType indexType)
  {
    Set<IndexType> indexTypes = config.getIndexType();
    switch (indexType)
    {
    case PRESENCE:
      return indexTypes.contains(IndexType.PRESENCE);

    case EQUALITY:
      return indexTypes.contains(IndexType.EQUALITY);

    case SUBSTRING:
    case SUBINITIAL:
    case SUBANY:
    case SUBFINAL:
      return indexTypes.contains(IndexType.SUBSTRING);

    case GREATER_OR_EQUAL:
    case LESS_OR_EQUAL:
      return indexTypes.contains(IndexType.ORDERING);

    case APPROXIMATE:
      return indexTypes.contains(IndexType.APPROXIMATE);

    default:
      return false;
    }
  }

  /**
   * Update the attribute index for a new entry.
   *
   * @param buffer The index buffer to use to store the added keys
   * @param entryID     The entry ID.
   * @param entry       The contents of the new entry.
   * @throws StorageRuntimeException If an error occurs in the storage.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry) throws StorageRuntimeException, DirectoryException
  {
    for (MatchingRuleIndex index : indexIdToIndexes.values())
    {
      final Set<ByteString> keys = new HashSet<>();
      index.indexEntry(entry, keys);
      for (ByteString key : keys)
      {
        buffer.put(index, key, entryID);
      }
    }
  }

  /**
   * Update the attribute index for a deleted entry.
   *
   * @param buffer The index buffer to use to store the deleted keys
   * @param entryID     The entry ID
   * @param entry       The contents of the deleted entry.
   * @throws StorageRuntimeException If an error occurs in the storage.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry) throws StorageRuntimeException, DirectoryException
  {
    for (MatchingRuleIndex index : indexIdToIndexes.values())
    {
      HashSet<ByteString> keys = new HashSet<ByteString>();
      index.indexEntry(entry, keys);
      for (ByteString key : keys)
      {
        buffer.remove(index, key, entryID);
      }
    }
  }

  /**
   * Update the index to reflect a sequence of modifications in a Modify
   * operation.
   *
   * @param buffer The index buffer used to buffer up the index changes.
   * @param entryID The ID of the entry that was modified.
   * @param oldEntry The entry before the modifications were applied.
   * @param newEntry The entry after the modifications were applied.
   * @throws StorageRuntimeException If an error occurs during an operation on a
   * storage.
   */
  void modifyEntry(IndexBuffer buffer, EntryID entryID, Entry oldEntry, Entry newEntry) throws StorageRuntimeException
  {
    for (MatchingRuleIndex index : indexIdToIndexes.values())
    {
      TreeMap<ByteString, Boolean> modifiedKeys = new TreeMap<ByteString, Boolean>();
      index.modifyEntry(oldEntry, newEntry, modifiedKeys);
      for (Map.Entry<ByteString, Boolean> modifiedKey : modifiedKeys.entrySet())
      {
        if (modifiedKey.getValue())
        {
          buffer.put(index, modifiedKey.getKey(), entryID);
        }
        else
        {
          buffer.remove(index, modifiedKey.getKey(), entryID);
        }
      }
    }
  }

  /**
   * Retrieve the entry IDs that might match the provided assertion.
   *
   * @param indexQuery
   *            The query used to retrieve entries.
   * @param indexName
   *            The name of index used to retrieve entries.
   * @param filter
   *          The filter on entries.
   * @param debugBuffer
   *          If not null, a diagnostic string will be written which will help
   *          determine how the indexes contributed to this search.
   * @param monitor
   *          The backend monitor provider that will keep index
   *          filter usage statistics.
   * @return The candidate entry IDs that might contain the filter assertion
   *         value.
   */
  private EntryIDSet evaluateIndexQuery(IndexQuery indexQuery, String indexName, SearchFilter filter,
      StringBuilder debugBuffer, BackendMonitor monitor)
  {
    LocalizableMessageBuilder debugMessage = monitor.isFilterUseEnabled() ? new LocalizableMessageBuilder() : null;
    EntryIDSet results = indexQuery.evaluate(debugMessage);

    if (debugBuffer != null)
    {
      debugBuffer.append("[INDEX:").append(config.getAttribute().getNameOrOID())
        .append(".").append(indexName).append("]");
    }

    if (monitor.isFilterUseEnabled())
    {
      if (results.isDefined())
      {
        monitor.updateStats(filter, results.size());
      }
      else
      {
        monitor.updateStats(filter, debugMessage.toMessage());
      }
    }
    return results;
  }

  /**
   * Retrieve the entry IDs that might match two filters that restrict a value
   * to both a lower bound and an upper bound.
   *
   * @param indexQueryFactory
   *          The index query factory to use for the evaluation
   * @param filter1
   *          The first filter, that is either a less-or-equal filter or a
   *          greater-or-equal filter.
   * @param filter2
   *          The second filter, that is either a less-or-equal filter or a
   *          greater-or-equal filter. It must not be of the same type than the
   *          first filter.
   * @param debugBuffer
   *          If not null, a diagnostic string will be written which will help
   *          determine how the indexes contributed to this search.
   * @param monitor
   *          The backend monitor provider that will keep index
   *          filter usage statistics.
   * @return The candidate entry IDs that might contain match both filters.
   */
  EntryIDSet evaluateBoundedRange(IndexQueryFactory<IndexQuery> indexQueryFactory,
      SearchFilter filter1, SearchFilter filter2, StringBuilder debugBuffer, BackendMonitor monitor)
  {
    // TODO : this implementation is not optimal
    // as it implies two separate evaluations instead of a single one,
    // thus defeating the purpose of the optimization done
    // in IndexFilter#evaluateLogicalAndFilter method.
    // One solution could be to implement a boundedRangeAssertion that combine
    // the two operations in one.
    EntryIDSet results = evaluate(indexQueryFactory, filter1, debugBuffer, monitor);
    EntryIDSet results2 = evaluate(indexQueryFactory, filter2, debugBuffer, monitor);
    results.retainAll(results2);
    return results;
  }

  private EntryIDSet evaluate(IndexQueryFactory<IndexQuery> indexQueryFactory, SearchFilter filter,
      StringBuilder debugBuffer, BackendMonitor monitor)
  {
    boolean isLessOrEqual = filter.getFilterType() == FilterType.LESS_OR_EQUAL;
    IndexFilterType indexFilterType = isLessOrEqual ? IndexFilterType.LESS_OR_EQUAL : IndexFilterType.GREATER_OR_EQUAL;
    return evaluateFilter(indexQueryFactory, indexFilterType, filter, debugBuffer, monitor);
  }

  /**
   * Retrieve the entry IDs that might match a filter.
   *
   * @param indexQueryFactory the index query factory to use for the evaluation
   * @param indexFilterType the index type filter
   * @param filter The filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The backend monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain a value
   *         that matches the filter type.
   */
  EntryIDSet evaluateFilter(IndexQueryFactory<IndexQuery> indexQueryFactory, IndexFilterType indexFilterType,
      SearchFilter filter, StringBuilder debugBuffer, BackendMonitor monitor)
  {
    try
    {
      final IndexQuery indexQuery = getIndexQuery(indexQueryFactory, indexFilterType, filter);
      return evaluateIndexQuery(indexQuery, indexFilterType.toString(), filter, debugBuffer, monitor);
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return newUndefinedSet();
    }
  }

  private IndexQuery getIndexQuery(IndexQueryFactory<IndexQuery> indexQueryFactory, IndexFilterType indexFilterType,
      SearchFilter filter) throws DecodeException
  {
    MatchingRule rule;
    Assertion assertion;
    switch (indexFilterType)
    {
    case EQUALITY:
      rule = filter.getAttributeType().getEqualityMatchingRule();
      assertion = rule.getAssertion(filter.getAssertionValue());
      return assertion.createIndexQuery(indexQueryFactory);

    case PRESENCE:
      return indexQueryFactory.createMatchAllQuery();

    case GREATER_OR_EQUAL:
      rule = filter.getAttributeType().getOrderingMatchingRule();
      assertion = rule.getGreaterOrEqualAssertion(filter.getAssertionValue());
      return assertion.createIndexQuery(indexQueryFactory);

    case LESS_OR_EQUAL:
      rule = filter.getAttributeType().getOrderingMatchingRule();
      assertion = rule.getLessOrEqualAssertion(filter.getAssertionValue());
      return assertion.createIndexQuery(indexQueryFactory);

    case SUBSTRING:
      rule = filter.getAttributeType().getSubstringMatchingRule();
      assertion = rule.getSubstringAssertion(
          filter.getSubInitialElement(), filter.getSubAnyElements(), filter.getSubFinalElement());
      return assertion.createIndexQuery(indexQueryFactory);

    case APPROXIMATE:
      rule = filter.getAttributeType().getApproximateMatchingRule();
      assertion = rule.getAssertion(filter.getAssertionValue());
      return assertion.createIndexQuery(indexQueryFactory);

    default:
      return null;
    }
  }

  /**
   * Get a string representation of this object.
   * @return return A string representation of this object.
   */
  @Override
  public String toString()
  {
    return getName();
  }

  /** {@inheritDoc} */
  @Override
  public synchronized boolean isConfigurationChangeAcceptable(
      BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    if (!isIndexAcceptable(cfg, IndexType.EQUALITY, unacceptableReasons)
        || !isIndexAcceptable(cfg, IndexType.SUBSTRING, unacceptableReasons)
        || !isIndexAcceptable(cfg, IndexType.ORDERING, unacceptableReasons)
        || !isIndexAcceptable(cfg, IndexType.APPROXIMATE, unacceptableReasons))
    {
      return false;
    }

    AttributeType attrType = cfg.getAttribute();
    if (cfg.getIndexType().contains(IndexType.EXTENSIBLE))
    {
      Set<String> newRules = cfg.getIndexExtensibleMatchingRule();
      if (newRules == null || newRules.isEmpty())
      {
        unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, "extensible"));
        return false;
      }
    }
    return true;
  }

  private static boolean isIndexAcceptable(BackendIndexCfg cfg, IndexType indexType,
      List<LocalizableMessage> unacceptableReasons)
  {
    final AttributeType attrType = cfg.getAttribute();
    if (cfg.getIndexType().contains(indexType)
        && getMatchingRule(indexType, attrType) == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, indexType));
      return false;
    }
    return true;
  }

  private static MatchingRule getMatchingRule(IndexType indexType, AttributeType attrType)
  {
    switch (indexType)
    {
    case APPROXIMATE:
      return attrType.getApproximateMatchingRule();
    case EQUALITY:
      return attrType.getEqualityMatchingRule();
    case ORDERING:
      return attrType.getOrderingMatchingRule();
    case SUBSTRING:
      return attrType.getSubstringMatchingRule();
    default:
      throw new IllegalArgumentException("Not implemented for index type " + indexType);
    }
  }

  /** {@inheritDoc} */
  @Override
  public synchronized ConfigChangeResult applyConfigurationChange(final BackendIndexCfg newConfiguration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    final IndexingOptions newIndexingOptions = new IndexingOptionsImpl(newConfiguration.getSubstringLength());
    try
    {
      final Map<String, MatchingRuleIndex> newIndexIdToIndexes = buildIndexes(entryContainer, state, newConfiguration);

      final Map<String, MatchingRuleIndex> removedIndexes = new HashMap<>(indexIdToIndexes);
      removedIndexes.keySet().removeAll(newIndexIdToIndexes.keySet());

      final Map<String, MatchingRuleIndex> addedIndexes = new HashMap<>(newIndexIdToIndexes);
      addedIndexes.keySet().removeAll(indexIdToIndexes.keySet());

      final Map<String, MatchingRuleIndex> updatedIndexes = new HashMap<>(indexIdToIndexes);
      updatedIndexes.keySet().retainAll(newIndexIdToIndexes.keySet());

      // Replace instances of Index created by buildIndexes() with the one already opened and present in the actual
      // indexIdToIndexes
      newIndexIdToIndexes.putAll(updatedIndexes);

      // Open added indexes *before* adding them to indexIdToIndexes
      entryContainer.getRootContainer().getStorage().write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          for (MatchingRuleIndex addedIndex : addedIndexes.values())
          {
            openIndex(txn, addedIndex, ccr);
          }
        }
      });

      config = newConfiguration;
      indexingOptions = newIndexingOptions;
      indexIdToIndexes = Collections.unmodifiableMap(newIndexIdToIndexes);

      // We get exclusive lock to ensure that no query is actually using the indexes that will be deleted.
      entryContainer.lock();
      try
      {
        entryContainer.getRootContainer().getStorage().write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            for (MatchingRuleIndex removedIndex : removedIndexes.values())
            {
              deleteIndex(txn, entryContainer, removedIndex);
            }
          }
        });
      }
      finally
      {
        entryContainer.unlock();
      }

      for (Index updatedIndex : updatedIndexes.values())
      {
        updateIndex(updatedIndex, newConfiguration.getIndexEntryLimit(), ccr);
      }

    }
    catch (Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(e)));
    }

    return ccr;
  }

  private static void openIndex(WriteableTransaction txn, MatchingRuleIndex index, ConfigChangeResult ccr)
  {
    index.open(txn);
    if (!index.isTrusted())
    {
      ccr.setAdminActionRequired(true);
      ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(index.getName()));
    }
  }

  private static void updateIndex(Index updatedIndex, int newIndexEntryLimit, ConfigChangeResult ccr)
  {
    if (updatedIndex.setIndexEntryLimit(newIndexEntryLimit))
    {
      // This index can still be used since index size limit doesn't impact validity of the results.
      ccr.setAdminActionRequired(true);
      ccr.addMessage(NOTE_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(updatedIndex.getName()));
    }
  }

  private static void deleteIndex(WriteableTransaction txn, EntryContainer entryContainer, Index index)
  {
    entryContainer.exclusiveLock.lock();
    try
    {
      entryContainer.deleteTree(txn, index);
    }
    finally
    {
      entryContainer.exclusiveLock.unlock();
    }
  }

  /**
   * Return true iff this index is trusted.
   * @return the trusted state of this index
   */
  boolean isTrusted()
  {
    for (Index index : indexIdToIndexes.values())
    {
      if (!index.isTrusted())
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Get the tree name prefix for indexes in this attribute index.
   *
   * @return tree name for this container.
   */
  String getName()
  {
    return entryContainer.getTreePrefix()
        + "_"
        + config.getAttribute().getNameOrOID();
  }

  Map<String, MatchingRuleIndex> getNameToIndexes()
  {
    return indexIdToIndexes;
  }

  /**
   * Retrieve the entry IDs that might match an extensible filter.
   *
   * @param indexQueryFactory the index query factory to use for the evaluation
   * @param filter The extensible filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The backend monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  EntryIDSet evaluateExtensibleFilter(IndexQueryFactory<IndexQuery> indexQueryFactory,
      SearchFilter filter, StringBuilder debugBuffer, BackendMonitor monitor)
  {
    //Get the Matching Rule OID of the filter.
    String matchRuleOID  = filter.getMatchingRuleID();
    /*
     * Use the default equality index in two conditions:
     * 1. There is no matching rule provided
     * 2. The matching rule specified is actually the default equality.
     */
    MatchingRule eqRule = config.getAttribute().getEqualityMatchingRule();
    if (matchRuleOID == null
        || matchRuleOID.equals(eqRule.getOID())
        || matchRuleOID.equalsIgnoreCase(eqRule.getNameOrOID()))
    {
      //No matching rule is defined; use the default equality matching rule.
      return evaluateFilter(indexQueryFactory, IndexFilterType.EQUALITY, filter, debugBuffer, monitor);
    }

    MatchingRule rule = DirectoryServer.getMatchingRule(matchRuleOID);
    if (!ruleHasAtLeastOneIndex(rule))
    {
      if (monitor.isFilterUseEnabled())
      {
        monitor.updateStats(filter,
            INFO_INDEX_FILTER_MATCHING_RULE_NOT_INDEXED.get(matchRuleOID, config.getAttribute().getNameOrOID()));
      }
      return IndexQuery.createNullIndexQuery().evaluate(null);
    }

    try
    {
      if (debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        for (Indexer indexer : rule.createIndexers(indexingOptions))
        {
            debugBuffer.append(" ")
              .append(filter.getAttributeType().getNameOrOID())
              .append(".")
              .append(indexer.getIndexID());
        }
        debugBuffer.append("]");
      }

      final IndexQuery indexQuery = rule.getAssertion(filter.getAssertionValue()).createIndexQuery(indexQueryFactory);
      LocalizableMessageBuilder debugMessage = monitor.isFilterUseEnabled() ? new LocalizableMessageBuilder() : null;
      EntryIDSet results = indexQuery.evaluate(debugMessage);
      if (monitor.isFilterUseEnabled())
      {
        if (results.isDefined())
        {
          monitor.updateStats(filter, results.size());
        }
        else
        {
          monitor.updateStats(filter, debugMessage.toMessage());
        }
      }
      return results;
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return IndexQuery.createNullIndexQuery().evaluate(null);
    }
  }

  private boolean ruleHasAtLeastOneIndex(MatchingRule rule)
  {
    for (Indexer indexer : rule.createIndexers(indexingOptions))
    {
      if (indexIdToIndexes.containsKey(indexer.getIndexID()))
      {
        return true;
      }
    }
    return false;
  }

  /** Indexing options implementation. */
  private static final class IndexingOptionsImpl implements IndexingOptions
  {
    /** The length of substring keys used in substring indexes. */
    private int substringKeySize;

    private IndexingOptionsImpl(int substringKeySize)
    {
      this.substringKeySize = substringKeySize;
    }

    @Override
    public int substringKeySize()
    {
      return substringKeySize;
    }
  }

  void closeAndDelete(WriteableTransaction txn)
  {
    close();
    for (Index index : indexIdToIndexes.values())
    {
      index.delete(txn);
      state.deleteRecord(txn, index.getName());
    }
  }
}
