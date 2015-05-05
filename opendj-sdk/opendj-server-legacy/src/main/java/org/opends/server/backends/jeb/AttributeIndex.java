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
import static org.opends.server.util.ServerConstants.*;
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
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.Utils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.monitors.DatabaseEnvironmentMonitor;
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

  /** The mapping from names to indexes. */
  private final Map<String, Index> nameToIndexes = new HashMap<String, Index>();
  private final IndexQueryFactory<IndexQuery> indexQueryFactory;

  /**
   * The mapping from extensible index types (e.g. "substring" or "shared") to list of indexes.
   */
  private Map<String, Collection<Index>> extensibleIndexesMapping;

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

    buildPresenceIndex();
    buildIndexes(IndexType.EQUALITY);
    buildIndexes(IndexType.SUBSTRING);
    buildIndexes(IndexType.ORDERING);
    buildIndexes(IndexType.APPROXIMATE);
    buildExtensibleIndexes();

    final JEIndexConfig config = new JEIndexConfig(indexConfig.getSubstringLength());
    indexQueryFactory = new IndexQueryFactoryImpl(nameToIndexes, config, indexConfig.getAttribute());
    extensibleIndexesMapping = computeExtensibleIndexesMapping();
  }

  private void buildPresenceIndex()
  {
    final IndexType indexType = IndexType.PRESENCE;
    if (indexConfig.getIndexType().contains(indexType))
    {
      String indexID = indexType.toString();
      nameToIndexes.put(indexID, newPresenceIndex(indexConfig));
    }
  }

  private Index newPresenceIndex(LocalDBIndexCfg cfg)
  {
    final AttributeType attrType = cfg.getAttribute();
    final String indexName = getIndexName(attrType, IndexType.PRESENCE.toString());
    final PresenceIndexer indexer = new PresenceIndexer(attrType);
    return entryContainer.newIndexForAttribute(indexName, indexer, cfg.getIndexEntryLimit());
  }

  private void buildExtensibleIndexes() throws ConfigException
  {
    final IndexType indexType = IndexType.EXTENSIBLE;
    if (indexConfig.getIndexType().contains(indexType))
    {
      final AttributeType attrType = indexConfig.getAttribute();
      Set<String> extensibleRules = indexConfig.getIndexExtensibleMatchingRule();
      if (extensibleRules == null || extensibleRules.isEmpty())
      {
        throw new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, indexType.toString()));
      }

      // Iterate through the Set and create the index only if necessary.
      // Collation equality and Ordering matching rules share the same indexer and index
      // A Collation substring matching rule is treated differently
      // as it uses a separate indexer and index.
      for (final String ruleName : extensibleRules)
      {
        MatchingRule rule = DirectoryServer.getMatchingRule(toLowerCase(ruleName));
        if (rule == null)
        {
          logger.error(ERR_CONFIG_INDEX_TYPE_NEEDS_VALID_MATCHING_RULE, attrType, ruleName);
          continue;
        }
        for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.getIndexers())
        {
          final String indexId = indexer.getIndexID();
          if (!nameToIndexes.containsKey(indexId))
          {
            // There is no index available for this index id. Create a new index
            nameToIndexes.put(indexId, newAttributeIndex(indexConfig, indexer));
          }
        }
      }
    }
  }

  private void buildIndexes(IndexType indexType) throws ConfigException
  {
    if (indexConfig.getIndexType().contains(indexType))
    {
      final AttributeType attrType = indexConfig.getAttribute();
      final String indexID = indexType.toString();
      final MatchingRule rule = getMatchingRule(indexType, attrType);
      if (rule == null)
      {
        throw new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, indexID));
      }

      for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.getIndexers())
      {
        nameToIndexes.put(indexID, newAttributeIndex(indexConfig, indexer));
      }
    }
  }

  private MatchingRule getMatchingRule(IndexType indexType, AttributeType attrType)
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

  private Index newAttributeIndex(LocalDBIndexCfg indexConfig, org.forgerock.opendj.ldap.spi.Indexer indexer)
  {
    final AttributeType attrType = indexConfig.getAttribute();
    final String indexName = getIndexName(attrType, indexer.getIndexID());
    final AttributeIndexer attrIndexer = new AttributeIndexer(attrType, indexer);
    return entryContainer.newIndexForAttribute(indexName, attrIndexer, indexConfig.getIndexEntryLimit());
  }

  private String getIndexName(AttributeType attrType, String indexID)
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
    for (Index index : nameToIndexes.values())
    {
      index.open();
    }
    indexConfig.addChangeListener(this);
  }

  /** Closes the attribute index. */
  @Override
  public void close()
  {
    Utils.closeSilently(nameToIndexes.values());
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
  public void addEntry(IndexBuffer buffer, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException
  {
    final IndexingOptions options = indexQueryFactory.getIndexingOptions();
    for (Index index : nameToIndexes.values())
    {
      index.addEntry(buffer, entryID, entry, options);
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
    final IndexingOptions options = indexQueryFactory.getIndexingOptions();
    for (Index index : nameToIndexes.values())
    {
      index.removeEntry(buffer, entryID, entry, options);
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
    final IndexingOptions options = indexQueryFactory.getIndexingOptions();
    for (Index index : nameToIndexes.values())
    {
      index.modifyEntry(buffer, entryID, oldEntry, newEntry, mods, options);
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
  private ByteString makeSubstringKey(byte[] bytes, int pos, int len)
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
    Set<ByteString> set = new HashSet<ByteString>();

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
    EntryIDSet results = evaluate(filter1, debugBuffer, monitor);
    EntryIDSet results2 = evaluate(filter2, debugBuffer, monitor);
    results.retainAll(results2);
    return results;
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

    for (Index index : nameToIndexes.values())
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
    dbList.addAll(nameToIndexes.values());
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

  private boolean isIndexAcceptable(LocalDBIndexCfg cfg, IndexType indexType,
      List<LocalizableMessage> unacceptableReasons)
  {
    final String indexId = indexType.toString();
    final AttributeType attrType = cfg.getAttribute();
    if (cfg.getIndexType().contains(indexType)
        && nameToIndexes.get(indexId) == null
        && getMatchingRule(indexType, attrType) == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, indexId));
      return false;
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized ConfigChangeResult applyConfigurationChange(LocalDBIndexCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      applyChangeToPresenceIndex(cfg, ccr);
      applyChangeToIndex(IndexType.EQUALITY, cfg, ccr);
      applyChangeToIndex(IndexType.SUBSTRING, cfg, ccr);
      applyChangeToIndex(IndexType.ORDERING, cfg, ccr);
      applyChangeToIndex(IndexType.APPROXIMATE, cfg, ccr);
      applyChangeToExtensibleIndexes(cfg, ccr);

      extensibleIndexesMapping = computeExtensibleIndexesMapping();
      indexConfig = cfg;

      return ccr;
    }
    catch(Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(e)));
      return ccr;
    }
  }

  private void applyChangeToExtensibleIndexes(LocalDBIndexCfg cfg, final ConfigChangeResult ccr)
  {
    final AttributeType attrType = cfg.getAttribute();
    if (!cfg.getIndexType().contains(IndexType.EXTENSIBLE))
    {
      final Set<MatchingRule> validRules = Collections.emptySet();
      final Set<String> validIndexIds = Collections.emptySet();
      removeIndexesForExtensibleMatchingRules(validRules, validIndexIds);
      return;
    }

    final Set<String> extensibleRules = cfg.getIndexExtensibleMatchingRule();
    final Set<MatchingRule> validRules = new HashSet<MatchingRule>();
    final Set<String> validIndexIds = new HashSet<String>();
    final int indexEntryLimit = cfg.getIndexEntryLimit();

    for (String ruleName : extensibleRules)
    {
      MatchingRule rule = DirectoryServer.getMatchingRule(toLowerCase(ruleName));
      if (rule == null)
      {
        logger.error(ERR_CONFIG_INDEX_TYPE_NEEDS_VALID_MATCHING_RULE, attrType, ruleName);
        continue;
      }
      validRules.add(rule);
      for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.getIndexers())
      {
        String indexId = indexer.getIndexID();
        validIndexIds.add(indexId);
        if (!nameToIndexes.containsKey(indexId))
        {
          Index index = newAttributeIndex(cfg, indexer);
          openIndex(index, ccr);
          nameToIndexes.put(indexId, index);
        }
        else
        {
          Index index = nameToIndexes.get(indexId);
          if (index.setIndexEntryLimit(indexEntryLimit))
          {
            ccr.setAdminActionRequired(true);
            ccr.addMessage(NOTE_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(index.getName()));
          }
          if (indexConfig.getSubstringLength() != cfg.getSubstringLength())
          {
            index.setIndexer(new AttributeIndexer(attrType, indexer));
          }
        }
      }
    }
    removeIndexesForExtensibleMatchingRules(validRules, validIndexIds);
  }

  /** Remove indexes which do not correspond to valid rules. */
  private void removeIndexesForExtensibleMatchingRules(Set<MatchingRule> validRules, Set<String> validIndexIds)
  {
    final Set<MatchingRule> rulesToDelete = getCurrentExtensibleMatchingRules();
    rulesToDelete.removeAll(validRules);
    if (!rulesToDelete.isEmpty())
    {
      entryContainer.exclusiveLock.lock();
      try
      {
        for (MatchingRule rule: rulesToDelete)
        {
          final List<String> indexIdsToRemove = new ArrayList<String>();
          for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.getIndexers())
          {
            final String indexId = indexer.getIndexID();
            if (!validIndexIds.contains(indexId))
            {
              indexIdsToRemove.add(indexId);
            }
          }
          // Delete indexes which are not used
          for (String indexId : indexIdsToRemove)
          {
            Index index = nameToIndexes.get(indexId);
            if (index != null)
            {
              entryContainer.deleteDatabase(index);
              nameToIndexes.remove(index);
            }
          }
        }
      }
      finally
      {
        entryContainer.exclusiveLock.unlock();
      }
    }
  }

  private Set<MatchingRule> getCurrentExtensibleMatchingRules()
  {
    final Set<MatchingRule> rules = new HashSet<MatchingRule>();
    for (String ruleName : indexConfig.getIndexExtensibleMatchingRule())
    {
        final MatchingRule rule = DirectoryServer.getMatchingRule(toLowerCase(ruleName));
        if (rule != null)
        {
          rules.add(rule);
        }
    }
    return rules;
  }

  private void applyChangeToIndex(IndexType indexType, LocalDBIndexCfg cfg, final ConfigChangeResult ccr)
  {
    String indexId = indexType.toString();
    Index index = nameToIndexes.get(indexId);
    if (!cfg.getIndexType().contains(indexType))
    {
      removeIndex(index, indexType);
      return;
    }

    if (index == null)
    {
      final MatchingRule matchingRule = getMatchingRule(indexType, cfg.getAttribute());
      for (org.forgerock.opendj.ldap.spi.Indexer indexer : matchingRule.getIndexers())
      {
        index = newAttributeIndex(cfg, indexer);
        openIndex(index, ccr);
        nameToIndexes.put(indexId, index);
      }
    }
    else
    {
      // already exists. Just update index entry limit.
      if (index.setIndexEntryLimit(cfg.getIndexEntryLimit()))
      {
        ccr.setAdminActionRequired(true);
        ccr.addMessage(NOTE_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(index.getName()));
      }
    }
  }

  private void applyChangeToPresenceIndex(LocalDBIndexCfg cfg, final ConfigChangeResult ccr)
  {
    final IndexType indexType = IndexType.PRESENCE;
    final String indexID = indexType.toString();
    Index index = nameToIndexes.get(indexID);
    if (!cfg.getIndexType().contains(indexType))
    {
      removeIndex(index, indexType);
      return;
    }

    if (index == null)
    {
      index = newPresenceIndex(cfg);
      openIndex(index, ccr);
      nameToIndexes.put(indexID, index);
    }
    else
    {
      // already exists. Just update index entry limit.
      if (index.setIndexEntryLimit(cfg.getIndexEntryLimit()))
      {
        ccr.setAdminActionRequired(true);
        ccr.addMessage(NOTE_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(index.getName()));
      }
    }
  }

  private void removeIndex(Index index, IndexType indexType)
  {
    if (index != null)
    {
      entryContainer.exclusiveLock.lock();
      try
      {
        nameToIndexes.remove(indexType.toString());
        entryContainer.deleteDatabase(index);
      }
      finally
      {
        entryContainer.exclusiveLock.unlock();
      }
    }
  }

  private void openIndex(Index index, final ConfigChangeResult ccr)
  {
    index.open();

    if (!index.isTrusted())
    {
      ccr.setAdminActionRequired(true);
      ccr.addMessage(NOTE_INDEX_ADD_REQUIRES_REBUILD.get(index.getName()));
    }
  }

  /**
   * Return true iff this index is trusted.
   * @return the trusted state of this index
   */
  public boolean isTrusted()
  {
    for (Index index : nameToIndexes.values())
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

  /**
   * Return the equality index.
   *
   * @return The equality index.
   */
  public Index getEqualityIndex() {
    return nameToIndexes.get(IndexType.EQUALITY.toString());
  }

  /**
   * Return the approximate index.
   *
   * @return The approximate index.
   */
  public Index getApproximateIndex() {
    return nameToIndexes.get(IndexType.APPROXIMATE.toString());
  }

  /**
   * Return the ordering index.
   *
   * @return  The ordering index.
   */
  public Index getOrderingIndex() {
    return nameToIndexes.get(IndexType.ORDERING.toString());
  }

  /**
   * Return the substring index.
   *
   * @return The substring index.
   */
  public Index getSubstringIndex() {
    return nameToIndexes.get(IndexType.SUBSTRING.toString());
  }

  /**
   * Return the presence index.
   *
   * @return The presence index.
   */
  public Index getPresenceIndex() {
    return nameToIndexes.get(IndexType.PRESENCE.toString());
  }

  /**
   * Return the mapping of extensible index types and indexes.
   *
   * @return The map containing entries (extensible index type, list of indexes)
   */
  public Map<String, Collection<Index>> getExtensibleIndexes()
  {
    return extensibleIndexesMapping;
  }

  private Map<String, Collection<Index>> computeExtensibleIndexesMapping()
  {
    final Collection<Index> substring = new ArrayList<Index>();
    final Collection<Index> shared = new ArrayList<Index>();
    for (Map.Entry<String, Index> entry : nameToIndexes.entrySet())
    {
      final String indexId = entry.getKey();
      if (isDefaultIndex(indexId)) {
        continue;
      }
      if (indexId.endsWith(EXTENSIBLE_INDEXER_ID_SUBSTRING))
      {
        substring.add(entry.getValue());
      }
      else
      {
        shared.add(entry.getValue());
      }
    }
    final Map<String, Collection<Index>> indexMap = new HashMap<String,Collection<Index>>();
    indexMap.put(EXTENSIBLE_INDEXER_ID_SUBSTRING, substring);
    indexMap.put(EXTENSIBLE_INDEXER_ID_SHARED, shared);
    return Collections.unmodifiableMap(indexMap);
  }

  private boolean isDefaultIndex(String indexId)
  {
    return indexId.equals(IndexType.EQUALITY.toString())
        || indexId.equals(IndexType.PRESENCE.toString())
        || indexId.equals(IndexType.SUBSTRING.toString())
        || indexId.equals(IndexType.ORDERING.toString())
        || indexId.equals(IndexType.APPROXIMATE.toString());
  }

  /**
   * Retrieves all the indexes used by this attribute index.
   *
   * @return A collection of all indexes in use by this attribute
   * index.
   */
  public Collection<Index> getAllIndexes() {
    return new LinkedHashSet<Index>(nameToIndexes.values());
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
        for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.getIndexers())
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
    for (org.forgerock.opendj.ldap.spi.Indexer indexer : rule.getIndexers())
    {
      if (nameToIndexes.containsKey(indexer.getIndexID()))
      {
        return true;
      }
    }
    return false;
  }

  /** This class extends the IndexConfig for JE Backend. */
  private final class JEIndexConfig implements IndexingOptions
  {
    /** The length of the substring index. */
    private int substringLength;

    /**
     * Creates a new JEIndexConfig instance.
     * @param substringLength The length of the substring.
     */
    private JEIndexConfig(int substringLength)
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
