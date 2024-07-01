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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2014 Manuel Gaupp
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.FilterType;
import org.opends.server.types.SearchFilter;
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
class AttributeIndex implements ConfigurationChangeListener<BackendIndexCfg>, Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Type of the index filter. */
  enum IndexFilterType
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

    IndexFilterType(IndexType indexType)
    {
      this.indexType = indexType;
    }

    @Override
    public String toString()
    {
      return indexType.toString();
    }
  }

  static final String PROTECTED_INDEX_ID = ":hash";

  /** This class implements an attribute indexer for matching rules in a Backend. */
  static final class MatchingRuleIndex extends DefaultIndex
  {
    private final AttributeType attributeType;
    private final Indexer indexer;

    private MatchingRuleIndex(EntryContainer entryContainer, AttributeType attributeType, State state, Indexer indexer,
        int indexEntryLimit, CryptoSuite cryptoSuite)
    {
      super(getIndexName(entryContainer, attributeType, indexer.getIndexID()),
          state, indexEntryLimit, entryContainer, cryptoSuite);
      this.attributeType = attributeType;
      this.indexer = indexer;
    }

    Set<ByteString> indexEntry(Entry entry)
    {
      final Set<ByteString> keys = new HashSet<>();
      indexEntry(entry, keys);
      return keys;
    }

    private void modifyEntry(Entry oldEntry, Entry newEntry, Map<ByteString, Boolean> modifiedKeys)
    {
      for (ByteString key : indexEntry(oldEntry))
      {
        modifiedKeys.put(key, false);
      }

      for (ByteString key : indexEntry(newEntry))
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

    void indexEntry(Entry entry, Set<ByteString> keys)
    {
      for (Attribute attr : entry.getAllAttributes(attributeType))
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

    @Override
    public String keyToString(ByteString key)
    {
      return indexer.keyToHumanReadableString(key);
    }

    @Override
    public ByteString generateKey(String key)
    {
      try
      {
        SortedSet<ByteString> keys = new TreeSet<>();
        indexer.createKeys(Schema.getDefaultSchema(), ByteString.valueOfUtf8(key), keys);
        return keys.first();
      }
      catch (DecodeException e)
      {
        return super.generateKey(key);
      }
    }
  }

  /**
   * Decorates an Indexer so that we can post process key and change index name for
   * those attributes declared as protected in the configuration.
   */
  private static class HashedKeyEqualityIndexer implements Indexer {

    private final Indexer delegate;
    private CryptoSuite cryptoSuite;

    private HashedKeyEqualityIndexer(Indexer delegate, CryptoSuite cryptoSuite)
    {
      this.delegate = delegate;
      this.cryptoSuite = cryptoSuite;
    }

    @Override
    public String getIndexID()
    {
      return delegate.getIndexID() + PROTECTED_INDEX_ID;
    }

    @Override
    public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException
    {
      Collection<ByteString> hashKeys = new ArrayList<>(1);
      delegate.createKeys(schema, value, hashKeys);
      for (ByteString key : hashKeys)
      {
        keys.add(cryptoSuite.hash48(key).toByteString());
      }
    }

    @Override
    public String keyToHumanReadableString(ByteSequence key)
    {
      return key.toByteString().toHexString();
    }
  }

  /** The key bytes used for the presence index as a {@link ByteString}. */
  static final ByteString PRESENCE_KEY = ByteString.valueOfUtf8("+");

  /** A special indexer for generating presence indexes. */
  private static final Indexer PRESENCE_INDEXER = new Indexer()
  {
    @Override
    public void createKeys(Schema schema, ByteSequence value, Collection<ByteString> keys) throws DecodeException
    {
      keys.add(PRESENCE_KEY);
    }

    @Override
    public String keyToHumanReadableString(ByteSequence key)
    {
      return "PRESENCE";
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
  private final CryptoSuite cryptoSuite;

  AttributeIndex(BackendIndexCfg config, State state, EntryContainer entryContainer, CryptoSuite cryptoSuite)
      throws ConfigException
  {
    this.entryContainer = entryContainer;
    this.config = config;
    this.state = state;
    this.cryptoSuite = cryptoSuite;
    this.indexingOptions = new IndexingOptionsImpl(config.getSubstringLength());
    this.indexIdToIndexes = Collections.unmodifiableMap(buildIndexes(entryContainer, state, config, cryptoSuite));
  }

  private Map<String, MatchingRuleIndex> buildIndexes(EntryContainer entryContainer, State state,
      BackendIndexCfg config, CryptoSuite cryptoSuite) throws ConfigException
  {
    final AttributeType attributeType = config.getAttribute();
    final int indexEntryLimit = config.getIndexEntryLimit();
    final IndexingOptions indexingOptions = new IndexingOptionsImpl(config.getSubstringLength());

    Map<Indexer, Boolean> indexers = new HashMap<>();
    for(IndexType indexType : config.getIndexType()) {
      switch (indexType)
      {
      case PRESENCE:
        indexers.put(PRESENCE_INDEXER, false);
        break;
      case EXTENSIBLE:
        indexers.putAll(
            getExtensibleIndexers(config.getAttribute(), config.getIndexExtensibleMatchingRule(), indexingOptions));
        break;
      case EQUALITY:
        indexers.putAll(buildBaseIndexers(config.isConfidentialityEnabled(), false, indexType, attributeType,
            indexingOptions));
        break;
      case SUBSTRING:
        indexers.putAll(buildBaseIndexers(false, config.isConfidentialityEnabled(), indexType, attributeType,
            indexingOptions));
        break;
      case APPROXIMATE:
      case ORDERING:
        indexers.putAll(buildBaseIndexers(false, false, indexType, attributeType, indexingOptions));
        break;
      default:
        throw noMatchingRuleForIndexType(attributeType, indexType);
      }
    }
    return buildIndexesForIndexers(entryContainer, attributeType, state, indexEntryLimit, indexers, cryptoSuite);
  }

  private Map<Indexer, Boolean> buildBaseIndexers(boolean protectIndexKeys, boolean protectIndexValues,
      IndexType indexType, AttributeType attributeType, IndexingOptions indexingOptions) throws ConfigException
  {
    Map<Indexer, Boolean> indexers = new HashMap<>();
    MatchingRule rule = getMatchingRule(indexType, attributeType);
    if (rule == null)
    {
      throw noMatchingRuleForIndexType(attributeType, indexType);
    }
    throwIfProtectKeysAndValues(attributeType, protectIndexKeys, protectIndexValues);
    Collection<? extends Indexer> ruleIndexers = rule.createIndexers(indexingOptions);
    for (Indexer indexer: ruleIndexers)
    {
      if (protectIndexKeys)
      {
        indexers.put(new HashedKeyEqualityIndexer(indexer, cryptoSuite), false);
      }
      else
      {
        indexers.put(indexer, protectIndexValues);
      }
    }
    return indexers;
  }

  private static ConfigException noMatchingRuleForIndexType(AttributeType attributeType, IndexType indexType)
  {
    return new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attributeType, indexType));
  }

  private void throwIfProtectKeysAndValues(AttributeType attributeType, boolean protectKeys, boolean protectValues)
      throws ConfigException
  {
    if (protectKeys && protectValues)
    {
      throw new ConfigException(ERR_CONFIG_INDEX_CANNOT_PROTECT_BOTH.get(attributeType));
    }
  }

  private static Map<String, MatchingRuleIndex> buildIndexesForIndexers(EntryContainer entryContainer,
      AttributeType attributeType, State state, int indexEntryLimit, Map<Indexer, Boolean> indexers,
      CryptoSuite cryptoSuite)
  {
    final Map<String, MatchingRuleIndex> indexes = new HashMap<>();
    for (Map.Entry<Indexer, Boolean> indexerEntry : indexers.entrySet())
    {
      final String indexID = indexerEntry.getKey().getIndexID();
      if (!indexes.containsKey(indexID))
      {
        indexes.put(indexID,
            new MatchingRuleIndex(entryContainer, attributeType, state, indexerEntry.getKey(),
                indexEntryLimit, cryptoSuite));
      }
    }
    return indexes;
  }

  private static Map<Indexer, Boolean> getExtensibleIndexers(AttributeType attributeType, Set<String> extensibleRules,
      IndexingOptions options) throws ConfigException
  {
    IndexType indexType = IndexType.EXTENSIBLE;
    if (extensibleRules == null || extensibleRules.isEmpty())
    {
      throw noMatchingRuleForIndexType(attributeType, indexType);
    }

    final Map<Indexer, Boolean> indexers = new HashMap<>();
    for (final String ruleName : extensibleRules)
    {
      try
      {
        final MatchingRule rule = getSchema().getMatchingRule(ruleName);
        for (Indexer indexer : rule.createIndexers(options))
        {
          indexers.put(indexer, false);
        }
      }
      catch (UnknownSchemaElementException e)
      {
        throw noMatchingRuleForIndexType(attributeType, indexType);
      }
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
   * @param createOnDemand true if the tree should be created if it does not exist
   * @throws StorageRuntimeException if an error occurs while opening the index
   */
  void open(WriteableTransaction txn, boolean createOnDemand) throws StorageRuntimeException
  {
    for (Index index : indexIdToIndexes.values())
    {
      index.open(txn, createOnDemand);
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

  public CryptoSuite getCryptoSuite()
  {
    return cryptoSuite;
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
    switch (indexType)
    {
    case PRESENCE:
      return isIndexed(IndexType.PRESENCE);

    case EQUALITY:
      return isIndexed(IndexType.EQUALITY);

    case SUBSTRING:
    case SUBINITIAL:
    case SUBANY:
    case SUBFINAL:
      return isIndexed(IndexType.SUBSTRING);

    case GREATER_OR_EQUAL:
    case LESS_OR_EQUAL:
      return isIndexed(IndexType.ORDERING);

    case APPROXIMATE:
      return isIndexed(IndexType.APPROXIMATE);

    default:
      return false;
    }
  }

  boolean isIndexed(IndexType indexType)
  {
    return config.getIndexType().contains(indexType);
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
      for (ByteString key : index.indexEntry(entry))
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
      for (ByteString key : index.indexEntry(entry))
      {
        buffer.remove(index, key, entryID);
      }
    }
  }

  /**
   * Update the index to reflect a sequence of modifications in a Modify operation.
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
      TreeMap<ByteString, Boolean> modifiedKeys = new TreeMap<>();
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
   * @return The candidate entry IDs that might contain the filter assertion value.
   */
  private static EntryIDSet evaluateIndexQuery(IndexQuery indexQuery, String indexName, SearchFilter filter,
      StringBuilder debugBuffer, BackendMonitor monitor)
  {
    // FIXME equivalent code exists in evaluateExtensibleFilter()
    LocalizableMessageBuilder debugMessage = monitor.isFilterUseEnabled() ? new LocalizableMessageBuilder() : null;
    StringBuilder indexNameOut = debugBuffer == null ? null : new StringBuilder();
    EntryIDSet results = indexQuery.evaluate(debugMessage, indexNameOut);

    if (debugBuffer != null)
    {
      appendDebugIndexInformation(debugBuffer, filter.getAttributeType(), indexName);
      appendDebugUnindexedInformation(debugBuffer, filter.getAttributeType(), indexNameOut);
    }

    updateStats(monitor, filter, results, debugMessage);
    return results;
  }

  private static void updateStats(BackendMonitor monitor, SearchFilter filter, EntryIDSet idSet,
      LocalizableMessageBuilder debugMessage)
  {
    if (monitor.isFilterUseEnabled())
    {
      if (idSet.isDefined())
      {
        monitor.updateStats(filter, idSet.size());
      }
      else
      {
        monitor.updateStats(filter, debugMessage.toMessage());
      }
    }
  }

  /**
   * Appends additional traces to {@code debugsearchindex} when a filter successfully used
   * an auxiliary index type during index query.
   *
   * @param debugBuffer the current debugsearchindex buffer
   * @param indexName the name of the index type
   */
  private static void appendDebugUnindexedInformation(StringBuilder debugBuffer, AttributeType attrType,
      StringBuilder indexName)
  {
    if (indexName.length() > 0)
    {
      debugBuffer.append(newUndefinedSet());
      appendDebugIndexInformation(debugBuffer, attrType, indexName);
    }
  }

  private static void appendDebugIndexInformation(StringBuilder debugBuffer, AttributeType attrType,
      CharSequence indexName)
  {
    String attrNameOrOID = attrType.getNameOrOID();
    debugBuffer.append("[INDEX:").append(attrNameOrOID).append(".").append(indexName).append("]");
  }

  private static void appendDebugIndexesInformation(StringBuilder debugBuffer, AttributeType attrType,
      Collection<? extends Indexer> indexers)
  {
    final String attrNameOrOID = attrType.getNameOrOID();
    debugBuffer.append("[INDEX:");
    boolean isFirst = true;
    for (Indexer indexer : indexers)
    {
      if (isFirst)
      {
        isFirst = false;
      }
      else
      {
        debugBuffer.append(" ");
      }
      debugBuffer.append(attrNameOrOID).append(".").append(indexer.getIndexID());
    }
    debugBuffer.append("]");
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
  static EntryIDSet evaluateBoundedRange(IndexQueryFactory<IndexQuery> indexQueryFactory,
      SearchFilter filter1, SearchFilter filter2, StringBuilder debugBuffer, BackendMonitor monitor)
  {
    // TODO : this implementation is not optimal
    // as it implies two separate evaluations instead of a single one, thus defeating the purpose of
    // the optimization done in IndexFilter#evaluateLogicalAndFilter method.
    // One solution could be to implement a boundedRangeAssertion that combine the two operations in one.
    // Such an optimization can only work for attributes declared as SINGLE-VALUE, though, since multiple
    // values may match both filters with values outside the range. See OPENDJ-2194.
    StringBuilder tmpBuff1 = debugBuffer != null ? new StringBuilder() : null;
    StringBuilder tmpBuff2 = debugBuffer != null ? new StringBuilder() : null;
    EntryIDSet results1 = evaluate(indexQueryFactory, filter1, tmpBuff1, monitor);
    EntryIDSet results2 = evaluate(indexQueryFactory, filter2, tmpBuff2, monitor);
    if (debugBuffer != null)
    {
      debugBuffer
          .append(filter1).append(tmpBuff1).append(results1)
          .append(filter2).append(tmpBuff2).append(results2);
    }
    results1.retainAll(results2);
    return results1;
  }

  private static EntryIDSet evaluate(IndexQueryFactory<IndexQuery> indexQueryFactory, SearchFilter filter,
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
  static EntryIDSet evaluateFilter(IndexQueryFactory<IndexQuery> indexQueryFactory, IndexFilterType indexFilterType,
      SearchFilter filter, StringBuilder debugBuffer, BackendMonitor monitor)
  {
    try
    {
      final IndexQuery indexQuery = getIndexQuery(indexQueryFactory, indexFilterType, filter);
      return evaluateIndexQuery(indexQuery, indexFilterType.toString(), filter, debugBuffer, monitor);
    }
    catch (DecodeException e)
    {
      // See OPENDJ-3034 for further information on why an empty set is returned here
      logger.traceException(e);
      return newDefinedSet();
    }
  }

  private static IndexQuery getIndexQuery(IndexQueryFactory<IndexQuery> indexQueryFactory,
      IndexFilterType indexFilterType, SearchFilter filter) throws DecodeException
  {
    MatchingRule rule;
    switch (indexFilterType)
    {
    case EQUALITY:
      rule = filter.getAttributeType().getEqualityMatchingRule();
      if (rule != null) {
        Assertion assertion = rule.getAssertion(filter.getAssertionValue());
        return assertion.createIndexQuery(indexQueryFactory);
      }
      break;

    case PRESENCE:
      return indexQueryFactory.createMatchAllQuery();

    case GREATER_OR_EQUAL:
      rule = filter.getAttributeType().getOrderingMatchingRule();
      if (rule != null) {
        Assertion assertion = rule.getGreaterOrEqualAssertion(filter.getAssertionValue());
        return assertion.createIndexQuery(indexQueryFactory);
      }
      break;

    case LESS_OR_EQUAL:
      rule = filter.getAttributeType().getOrderingMatchingRule();
      if (rule != null) {
        Assertion assertion = rule.getLessOrEqualAssertion(filter.getAssertionValue());
        return assertion.createIndexQuery(indexQueryFactory);
      }
      break;

    case SUBSTRING:
      rule = filter.getAttributeType().getSubstringMatchingRule();
      if (rule != null) {
        Assertion assertion = rule.getSubstringAssertion(filter.getSubInitialElement(),
                                                         filter.getSubAnyElements(),
                                                         filter.getSubFinalElement());
        return assertion.createIndexQuery(indexQueryFactory);
      }
      break;

    case APPROXIMATE:
      rule = filter.getAttributeType().getApproximateMatchingRule();
      if (rule != null) {
        Assertion assertion = rule.getAssertion(filter.getAssertionValue());
        return assertion.createIndexQuery(indexQueryFactory);
      }
      break;

    default:
      break;
    }

    // The filter is undefined.
    return indexQueryFactory.createMatchAllQuery();
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

  @Override
  public synchronized boolean isConfigurationChangeAcceptable(
      BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return isIndexConfidentialityAcceptable(cfg, unacceptableReasons)
        && isIndexAcceptable(cfg, IndexType.EQUALITY, unacceptableReasons)
        && isIndexAcceptable(cfg, IndexType.SUBSTRING, unacceptableReasons)
        && isIndexAcceptable(cfg, IndexType.ORDERING, unacceptableReasons)
        && isIndexAcceptable(cfg, IndexType.APPROXIMATE, unacceptableReasons)
        && isExtensibleIndexAcceptable(cfg, unacceptableReasons);
  }

  private boolean isIndexConfidentialityAcceptable(BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    if (!entryContainer.isConfidentialityEnabled() && cfg.isConfidentialityEnabled())
    {
      unacceptableReasons.add(ERR_CLEARTEXT_BACKEND_FOR_INDEX_CONFIDENTIALITY.get(cfg.getAttribute().getNameOrOID()));
      return false;
    }
    return true;
  }

  private boolean isExtensibleIndexAcceptable(BackendIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    IndexType indexType = IndexType.EXTENSIBLE;
    AttributeType attrType = cfg.getAttribute();
    if (cfg.getIndexType().contains(indexType))
    {
      Set<String> newRules = cfg.getIndexExtensibleMatchingRule();
      if (newRules == null || newRules.isEmpty())
      {
        unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, indexType));
        return false;
      }
    }
    return true;
  }

  private static boolean isIndexAcceptable(BackendIndexCfg cfg, IndexType indexType,
      List<LocalizableMessage> unacceptableReasons)
  {
    final AttributeType attrType = cfg.getAttribute();
    if (cfg.getIndexType().contains(indexType) && getMatchingRule(indexType, attrType) == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, indexType));
      return false;
    }
    return true;
  }

  static MatchingRule getMatchingRule(IndexType indexType, AttributeType attrType)
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

  @Override
  public synchronized ConfigChangeResult applyConfigurationChange(final BackendIndexCfg newConfiguration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    final IndexingOptions newIndexingOptions = new IndexingOptionsImpl(newConfiguration.getSubstringLength());
    try
    {
      final Map<String, MatchingRuleIndex> newIndexIdToIndexes = buildIndexes(entryContainer, state, newConfiguration,
          cryptoSuite);

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
            createIndex(txn, addedIndex, ccr);
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

      entryContainer.getRootContainer().getStorage().write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          for (final Index updatedIndex : updatedIndexes.values())
          {
            updateIndex(updatedIndex, newConfiguration, ccr, txn);
          }
        }
      });
    }
    catch (Exception e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(e)));
    }

    return ccr;
  }

  private static void createIndex(WriteableTransaction txn, MatchingRuleIndex index, ConfigChangeResult ccr)
  {
    index.open(txn, true);
    if (!index.isTrusted())
    {
      ccr.setAdminActionRequired(true);
      ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(index.getName()));
    }
  }

  private static void updateIndex(Index updatedIndex, BackendIndexCfg newConfig, ConfigChangeResult ccr,
      WriteableTransaction txn)
  {
    // This index could still be used since a new smaller index size limit doesn't impact validity of the results.
    boolean newLimitRequiresRebuild = updatedIndex.setIndexEntryLimit(newConfig.getIndexEntryLimit());
    if (newLimitRequiresRebuild)
    {
      ccr.setAdminActionRequired(true);
      ccr.addMessage(NOTE_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(updatedIndex.getName()));
    }
    // This index could still be used when disabling confidentiality.
    boolean newConfidentialityRequiresRebuild = updatedIndex.setConfidential(newConfig.isConfidentialityEnabled());
    if (newConfidentialityRequiresRebuild)
    {
      ccr.setAdminActionRequired(true);
      ccr.addMessage(NOTE_CONFIG_INDEX_CONFIDENTIALITY_REQUIRES_REBUILD.get(updatedIndex.getName()));
    }
    if (newLimitRequiresRebuild || newConfidentialityRequiresRebuild)
    {
      updatedIndex.setTrusted(txn, false);
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

  boolean isConfidentialityEnabled()
  {
    return config.isConfidentialityEnabled();
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
   * @return The candidate entry IDs that might contain the filter assertion value.
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

    MatchingRule rule = getSchema().getMatchingRule(matchRuleOID);
    if (!ruleHasAtLeastOneIndex(rule))
    {
      if (monitor.isFilterUseEnabled())
      {
        monitor.updateStats(filter,
            INFO_INDEX_FILTER_MATCHING_RULE_NOT_INDEXED.get(matchRuleOID, config.getAttribute().getNameOrOID()));
      }
      return IndexQueryFactoryImpl.createNullIndexQuery().evaluate(null, null);
    }

    try
    {
      // FIXME equivalent code exists in evaluateIndexQuery()
      final IndexQuery indexQuery = rule.getAssertion(filter.getAssertionValue()).createIndexQuery(indexQueryFactory);
      LocalizableMessageBuilder debugMessage = monitor.isFilterUseEnabled() ? new LocalizableMessageBuilder() : null;
      StringBuilder indexNameOut = debugBuffer == null ? null : new StringBuilder();
      EntryIDSet results = indexQuery.evaluate(debugMessage, indexNameOut);

      if (debugBuffer != null)
      {
        appendDebugIndexesInformation(debugBuffer, filter.getAttributeType(), rule.createIndexers(indexingOptions));
        appendDebugUnindexedInformation(debugBuffer, filter.getAttributeType(), indexNameOut);
      }

      updateStats(monitor, filter, results, debugMessage);
      return results;
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return IndexQueryFactoryImpl.createNullIndexQuery().evaluate(null, null);
    }
  }

  private static Schema getSchema()
  {
    return DirectoryServer.getInstance().getServerContext().getSchema();
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
