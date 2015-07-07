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
package org.opends.server.backends.jeb;

import static org.opends.messages.BackendMessages.*;
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
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.Utils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.DatabaseException;

/**
 * Class representing an attribute index.
 * We have a separate database for each type of indexing, which makes it easy
 * to tell which attribute indexes are configured.  The different types of
 * indexing are equality, presence, substrings and ordering.  The keys in the
 * ordering index are ordered by setting the btree comparator to the ordering
 * matching rule comparator.
 * Note that the values in the equality index are normalized by the equality
 * matching rule, whereas the values in the ordering index are normalized
 * by the ordering matching rule.  If these could be guaranteed to be identical
 * then we would not need a separate ordering index.
 */
public class AttributeIndex
    implements ConfigurationChangeListener<LocalDBIndexCfg>, Closeable
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

  /*
   * FIXME Matthew Swift: Once the matching rules have been migrated we should
   * revisit this class. All of the evaluateXXX methods should go (the Matcher
   * class in the SDK could implement the logic, I hope).
   */

  /** The entryContainer in which this attribute index resides. */
  private final EntryContainer entryContainer;

  /** The attribute index configuration. */
  private LocalDBIndexCfg indexConfig;
  private IndexingOptions indexingOptions;

  /** The mapping from names to indexes. */
  private Map<String, Index> indexIdToIndexes;
  private IndexQueryFactory<IndexQuery> indexQueryFactory;

  /**
   * Create a new attribute index object.
   *
   * @param indexConfig The attribute index configuration.
   * @param entryContainer The entryContainer of this attribute index.
   * @throws ConfigException if a configuration related error occurs.
   */
  public AttributeIndex(LocalDBIndexCfg indexConfig, EntryContainer entryContainer) throws ConfigException
  {
    this.entryContainer = entryContainer;
    this.indexConfig = indexConfig;
    this.indexingOptions = new JEIndexingOptions(indexConfig.getSubstringLength());
    this.indexIdToIndexes = Collections.unmodifiableMap(buildIndexes(entryContainer, indexConfig, indexingOptions));
    this.indexQueryFactory = new IndexQueryFactoryImpl(indexIdToIndexes, indexingOptions, indexConfig.getAttribute());
  }

  private static Map<String, Index> buildIndexes(EntryContainer entryContainer,
                                                 LocalDBIndexCfg config,
                                                 IndexingOptions options) throws ConfigException
  {
    final Map<String, Index> indexes = new HashMap<>();
    final AttributeType attributeType = config.getAttribute();
    final int indexEntryLimit = config.getIndexEntryLimit();

    for(IndexType indexType : config.getIndexType()) {
      Collection<? extends Indexer> indexers;
      switch (indexType)
      {
      case PRESENCE:
        indexes.put(indexType.toString(), newPresenceIndex(entryContainer, config));
        indexers = Collections.emptyList();
        break;
      case EXTENSIBLE:
        indexers = getExtensibleIndexers(config.getAttribute(), config.getIndexExtensibleMatchingRule(), options);
        break;
      case APPROXIMATE:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getApproximateMatchingRule())
              .createIndexers(options);
        break;
      case EQUALITY:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getEqualityMatchingRule())
              .createIndexers(options);
        break;
      case ORDERING:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getOrderingMatchingRule())
              .createIndexers(options);
        break;
      case SUBSTRING:
        indexers =
            throwIfNoMatchingRule(attributeType, indexType, attributeType.getSubstringMatchingRule())
              .createIndexers(options);
        break;
      default:
       throw new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attributeType, indexType.toString()));
      }
      buildAndRegisterIndexesWithIndexers(entryContainer, attributeType, indexEntryLimit, indexers, indexes);
    }

    return indexes;
  }

  private static Index newPresenceIndex(EntryContainer entryContainer, LocalDBIndexCfg cfg)
  {
    final AttributeType attrType = cfg.getAttribute();
    final String indexName = getIndexName(entryContainer, attrType, IndexType.PRESENCE.toString());
    final PresenceIndexer indexer = new PresenceIndexer(attrType);
    return entryContainer.newIndexForAttribute(indexName, indexer, cfg.getIndexEntryLimit());
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

  private static void buildAndRegisterIndexesWithIndexers(EntryContainer entryContainer,
                                                          AttributeType attributeType,
                                                          int indexEntryLimit,
                                                          Collection<? extends Indexer> indexers,
                                                          Map<String, Index> indexes)
  {
    for (Indexer indexer : indexers)
    {
      final String indexID = indexer.getIndexID();
      if (!indexes.containsKey(indexID))
      {
        final Index index = newAttributeIndex(entryContainer, attributeType, indexer, indexEntryLimit);
        indexes.put(indexID, index);
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
      if (rule == null)
      {
        logger.error(ERR_CONFIG_INDEX_TYPE_NEEDS_VALID_MATCHING_RULE, attributeType, ruleName);
        continue;
      }
      indexers.addAll(rule.createIndexers(options));
    }

    return indexers;
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

  private static Index newAttributeIndex(EntryContainer entryContainer, AttributeType attributeType,
      org.forgerock.opendj.ldap.spi.Indexer indexer, int indexEntryLimit)
  {
    final String indexName = getIndexName(entryContainer, attributeType, indexer.getIndexID());
    final AttributeIndexer attrIndexer = new AttributeIndexer(attributeType, indexer);
    return entryContainer.newIndexForAttribute(indexName, attrIndexer, indexEntryLimit);
  }

  private static String getIndexName(EntryContainer entryContainer, AttributeType attrType, String indexID)
  {
    return entryContainer.getDatabasePrefix() + "_" + attrType.getNameOrOID() + "." + indexID;
  }

  /**
   * Open the attribute index.
   *
   * @throws DatabaseException if a JE database error occurs while
   * opening the index.
   */
  public void open() throws DatabaseException
  {
    for (Index index : indexIdToIndexes.values())
    {
      index.open();
    }
    indexConfig.addChangeListener(this);
  }

  /** Closes the attribute index. */
  @Override
  public void close()
  {
    Utils.closeSilently(indexIdToIndexes.values());
    indexConfig.removeChangeListener(this);
    // The entryContainer is responsible for closing the JE databases.
  }

  /**
   * Get the attribute type of this attribute index.
   * @return The attribute type of this attribute index.
   */
  public AttributeType getAttributeType()
  {
    return indexConfig.getAttribute();
  }

  /**
   * Return the indexing options of this AttributeIndex.
   *
   * @return the indexing options of this AttributeIndex.
   */
  public IndexingOptions getIndexingOptions()
  {
    return indexQueryFactory.getIndexingOptions();
  }

  /**
   * Get the JE index configuration used by this index.
   * @return The configuration in effect.
   */
  public LocalDBIndexCfg getConfiguration()
  {
    return indexConfig;
  }

  /**
   * Update the attribute index for a new entry.
   *
   * @param buffer The index buffer to use to store the added keys
   * @param entryID     The entry ID.
   * @param entry       The contents of the new entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry) throws DatabaseException, DirectoryException
  {
    for (Index index : indexIdToIndexes.values())
    {
      index.addEntry(buffer, entryID, entry);
    }
  }

  /**
   * Update the attribute index for a deleted entry.
   *
   * @param buffer The index buffer to use to store the deleted keys
   * @param entryID     The entry ID
   * @param entry       The contents of the deleted entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void removeEntry(IndexBuffer buffer, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException
  {
    for (Index index : indexIdToIndexes.values())
    {
      index.removeEntry(buffer, entryID, entry);
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
   * @param mods The sequence of modifications in the Modify operation.
   * @throws DatabaseException If an error occurs during an operation on a
   * JE database.
   */
  public void modifyEntry(IndexBuffer buffer,
                          EntryID entryID,
                          Entry oldEntry,
                          Entry newEntry,
                          List<Modification> mods)
       throws DatabaseException
  {
    for (Index index : indexIdToIndexes.values())
    {
      index.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }
  }

  /**
   * Makes a byte string representing a substring index key for
   * one substring of a value.
   *
   * @param bytes The byte array containing the value.
   * @param pos The starting position of the substring.
   * @param len The length of the substring.
   * @return A byte string containing a substring key.
   */
  private static ByteString makeSubstringKey(byte[] bytes, int pos, int len)
  {
    byte[] keyBytes = new byte[len];
    System.arraycopy(bytes, pos, keyBytes, 0, len);
    return ByteString.wrap(keyBytes);
  }

  /**
   * Decompose an attribute value into a set of substring index keys.
   * The ID of the entry containing this value should be inserted
   * into the list of each of these keys.
   *
   * @param value A byte array containing the normalized attribute value.
   * @return A set of index keys.
   */
  Set<ByteString> substringKeys(byte[] value)
  { // FIXME replace this code with SDK's
    // AbstractSubstringMatchingRuleImpl.SubstringIndexer.createKeys()

    // Eliminate duplicates by putting the keys into a set.
    // Sorting the keys will ensure database record locks are acquired
    // in a consistent order and help prevent transaction deadlocks between
    // concurrent writers.
    Set<ByteString> set = new HashSet<>();

    int substrLength = indexConfig.getSubstringLength();

    // Example: The value is ABCDE and the substring length is 3.
    // We produce the keys ABC BCD CDE DE E
    // To find values containing a short substring such as DE,
    // iterate through keys with prefix DE. To find values
    // containing a longer substring such as BCDE, read keys BCD and CDE.
    for (int i = 0, remain = value.length; remain > 0; i++, remain--)
    {
      int len = Math.min(substrLength, remain);
      set.add(makeSubstringKey(value, i, len));
    }
    return set;
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
   *          The database environment monitor provider that will keep index
   *          filter usage statistics.
   * @return The candidate entry IDs that might contain the filter assertion
   *         value.
   */
  private EntryIDSet evaluateIndexQuery(IndexQuery indexQuery, String indexName, SearchFilter filter,
      StringBuilder debugBuffer, DatabaseEnvironmentMonitor monitor)
  {
    LocalizableMessageBuilder debugMessage = monitor.isFilterUseEnabled() ? new LocalizableMessageBuilder() : null;
    EntryIDSet results = indexQuery.evaluate(debugMessage);

    if (debugBuffer != null)
    {
      debugBuffer.append("[INDEX:").append(indexConfig.getAttribute().getNameOrOID())
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
   *          The database environment monitor provider that will keep index
   *          filter usage statistics.
   * @return The candidate entry IDs that might contain match both filters.
   */
  public EntryIDSet evaluateBoundedRange(SearchFilter filter1, SearchFilter filter2, StringBuilder debugBuffer,
      DatabaseEnvironmentMonitor monitor)
  {
    // TODO : this implementation is not optimal
    // as it implies two separate evaluations instead of a single one,
    // thus defeating the purpose of the optimization done
    // in IndexFilter#evaluateLogicalAndFilter method.
    // One solution could be to implement a boundedRangeAssertion that combine
    // the two operations in one.
    StringBuilder tmpBuff1 = debugBuffer != null ? new StringBuilder(): null;
    StringBuilder tmpBuff2 = debugBuffer != null ? new StringBuilder(): null;
    EntryIDSet results1 = evaluate(filter1, tmpBuff1, monitor);
    EntryIDSet results2 = evaluate(filter2, tmpBuff2, monitor);
    if (debugBuffer != null)
    {
      debugBuffer
          .append(filter1).append(tmpBuff1).append(results1)
          .append(filter2).append(tmpBuff2).append(results2);
    }
    results1.retainAll(results2);
    return results1;
  }

  private EntryIDSet evaluate(SearchFilter filter, StringBuilder debugBuffer, DatabaseEnvironmentMonitor monitor)
  {
    boolean isLessOrEqual = filter.getFilterType() == FilterType.LESS_OR_EQUAL;
    IndexFilterType indexFilterType = isLessOrEqual ? IndexFilterType.LESS_OR_EQUAL : IndexFilterType.GREATER_OR_EQUAL;
    return evaluateFilter(indexFilterType, filter, debugBuffer, monitor);
  }

  /**
   * Retrieve the entry IDs that might match a filter.
   *
   * @param indexFilterType the index type filter
   * @param filter The filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain a value
   *         that matches the filter type.
   */
  public EntryIDSet evaluateFilter(IndexFilterType indexFilterType, SearchFilter filter, StringBuilder debugBuffer,
      DatabaseEnvironmentMonitor monitor)
  {
    try
    {
      final IndexQuery indexQuery = getIndexQuery(indexFilterType, filter);
      return evaluateIndexQuery(indexQuery, indexFilterType.toString(), filter, debugBuffer, monitor);
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return new EntryIDSet();
    }
  }

  private IndexQuery getIndexQuery(IndexFilterType indexFilterType, SearchFilter filter) throws DecodeException
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
   * Delegator to {@link ByteSequence#BYTE_ARRAY_COMPARATOR}.
   * <p>
   * This intermediate class is necessary to satisfy JE's requirements for a btree comparator.
   *
   * @see com.sleepycat.je.DatabaseConfig#setBtreeComparator(Comparator)
   */
  public static class KeyComparator implements Comparator<byte[]>
  {
    /** The instance. */
    public static final KeyComparator INSTANCE = new KeyComparator();

    /** {@inheritDoc} */
    @Override
    public int compare(byte[] a, byte[] b)
    {
      return ByteSequence.BYTE_ARRAY_COMPARATOR.compare(a, b);
    }
  }

  /**
   * Return the number of values that have exceeded the entry limit since this
   * object was created.
   *
   * @return The number of values that have exceeded the entry limit.
   */
  public long getEntryLimitExceededCount()
  {
    long entryLimitExceededCount = 0;

    for (Index index : indexIdToIndexes.values())
    {
      entryLimitExceededCount += index.getEntryLimitExceededCount();
    }
    return entryLimitExceededCount;
  }

  /**
   * Get a list of the databases opened by this attribute index.
   * @param dbList A list of database containers.
   */
  public void listDatabases(List<DatabaseContainer> dbList)
  {
    dbList.addAll(indexIdToIndexes.values());
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
      LocalDBIndexCfg cfg, List<LocalizableMessage> unacceptableReasons)
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

  private static boolean isIndexAcceptable(LocalDBIndexCfg cfg, IndexType indexType,
      List<LocalizableMessage> unacceptableReasons)
  {
    final AttributeType attrType = cfg.getAttribute();
    if (cfg.getIndexType().contains(indexType)
        && getMatchingRule(indexType, attrType) == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, indexType.toString()));
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized ConfigChangeResult applyConfigurationChange(final LocalDBIndexCfg newConfiguration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    final IndexingOptions newIndexingOptions = new JEIndexingOptions(newConfiguration.getSubstringLength());
    try
    {
      Map<String, Index> newIndexIdToIndexes = buildIndexes(entryContainer, newConfiguration, newIndexingOptions);

      final Map<String, Index> removedIndexes = new HashMap<>(indexIdToIndexes);
      removedIndexes.keySet().removeAll(newIndexIdToIndexes.keySet());

      final Map<String, Index> addedIndexes = new HashMap<>(newIndexIdToIndexes);
      addedIndexes.keySet().removeAll(indexIdToIndexes.keySet());

      final Map<String, Index> updatedIndexes = new HashMap<>(indexIdToIndexes);
      updatedIndexes.keySet().retainAll(newIndexIdToIndexes.keySet());

      // Replace instances of Index created by buildIndexes() with the one already opened and present in the actual
      // indexIdToIndexes
      newIndexIdToIndexes.putAll(updatedIndexes);

      // Open added indexes *before* adding them to indexIdToIndexes
      for (Index addedIndex : addedIndexes.values())
      {
        openIndex(addedIndex, ccr);
      }

      indexConfig = newConfiguration;
      indexingOptions = newIndexingOptions;
      indexIdToIndexes =  Collections.unmodifiableMap(newIndexIdToIndexes);
      indexQueryFactory = new IndexQueryFactoryImpl(indexIdToIndexes, indexingOptions, indexConfig.getAttribute());

      // FIXME: There is no guarantee here that deleted index are not currently involved in a query
      for (Index removedIndex : removedIndexes.values())
      {
        deleteIndex(entryContainer, removedIndex);
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

  private static void openIndex(Index index, ConfigChangeResult ccr)
  {
    index.open();
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

  private static void deleteIndex(EntryContainer entryContainer, Index index)
  {
    entryContainer.exclusiveLock.lock();
    try
    {
      entryContainer.deleteDatabase(index);
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
  public boolean isTrusted()
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
   * Get the JE database name prefix for indexes in this attribute index.
   *
   * @return JE database name for this database container.
   */
  public String getName()
  {
    return entryContainer.getDatabasePrefix()
        + "_"
        + indexConfig.getAttribute().getNameOrOID();
  }

  Index getIndex(String indexID) {
    return indexIdToIndexes.get(indexID);
  }

  /**
   * Retrieves all the indexes used by this attribute index.
   *
   * @return An immutable collection of all indexes in use by this attribute
   * index.
   */
  public Collection<Index> getAllIndexes() {
    return indexIdToIndexes.values();
  }

  /**
   * Retrieve the entry IDs that might match an extensible filter.
   *
   * @param filter The extensible filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateExtensibleFilter(SearchFilter filter,
                                             StringBuilder debugBuffer,
                                             DatabaseEnvironmentMonitor monitor)
  {
    //Get the Matching Rule OID of the filter.
    String matchRuleOID  = filter.getMatchingRuleID();
    /*
     * Use the default equality index in two conditions:
     * 1. There is no matching rule provided
     * 2. The matching rule specified is actually the default equality.
     */
    MatchingRule eqRule = indexConfig.getAttribute().getEqualityMatchingRule();
    if (matchRuleOID == null
        || matchRuleOID.equals(eqRule.getOID())
        || matchRuleOID.equalsIgnoreCase(eqRule.getNameOrOID()))
    {
      //No matching rule is defined; use the default equality matching rule.
      return evaluateFilter(IndexFilterType.EQUALITY, filter, debugBuffer, monitor);
    }

    MatchingRule rule = DirectoryServer.getMatchingRule(matchRuleOID);
    if (!ruleHasAtLeasOneIndex(rule))
    {
      if (monitor.isFilterUseEnabled())
      {
        monitor.updateStats(filter, INFO_INDEX_FILTER_MATCHING_RULE_NOT_INDEXED.get(
            matchRuleOID, indexConfig.getAttribute().getNameOrOID()));
      }
      return IndexQuery.createNullIndexQuery().evaluate(null);
    }

    try
    {
      if (debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.createIndexers(indexingOptions))
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

  private boolean ruleHasAtLeasOneIndex(MatchingRule rule)
  {
    for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.createIndexers(indexingOptions))
    {
      if (indexIdToIndexes.containsKey(indexer.getIndexID()))
      {
        return true;
      }
    }
    return false;
  }

  /** This class extends the IndexConfig for JE Backend. */
  private static final class JEIndexingOptions implements IndexingOptions
  {
    /** The length of the substring index. */
    private int substringLength;

    /**
     * Creates a new JEIndexConfig instance.
     * @param substringLength The length of the substring.
     */
    private JEIndexingOptions(int substringLength)
    {
      this.substringLength = substringLength;
    }

    /** {@inheritDoc} */
    @Override
    public int substringKeySize()
    {
      return substringLength;
    }
  }
}
