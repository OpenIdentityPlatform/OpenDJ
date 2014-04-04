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
 *      Portions Copyright 2011-2014 ForgeRock AS
 *      Portions Copyright 2014 Manuel Gaupp
 */
package org.opends.server.backends.jeb;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.Utils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.api.ExtensibleIndexer;
import org.opends.server.api.ExtensibleMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.monitors.DatabaseEnvironmentMonitor;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

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
    implements ConfigurationChangeListener<LocalDBIndexCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * A database key for the presence index.
   */
  public static final DatabaseEntry presenceKey =
       new DatabaseEntry("+".getBytes());

  /**
   * The entryContainer in which this attribute index resides.
   */
  private EntryContainer entryContainer;

  private Environment env;

  /**
   * The attribute index configuration.
   */
  private LocalDBIndexCfg indexConfig;

  /**
   * The index database for attribute equality.
   */
  Index equalityIndex = null;

  /**
   * The index database for attribute presence.
   */
  Index presenceIndex = null;

  /**
   * The index database for attribute substrings.
   */
  Index substringIndex = null;

  /**
   * The index database for attribute ordering.
   */
  Index orderingIndex = null;

  /**
   * The index database for attribute approximate.
   */
  Index approximateIndex = null;

  /** The mapping from names to indexes. */
  private Map<String, Index> nameToIndexes;

  private IndexQueryFactory<IndexQuery> indexQueryFactory;

  /**
   * The ExtensibleMatchingRuleIndex instance for ExtensibleMatchingRule
   * indexes.
   */
  private  ExtensibleMatchingRuleIndex extensibleIndexes = null;

  private State state;

  private int cursorEntryLimit = 100000;

  /**
   * Create a new attribute index object.
   *
   * @param entryContainer The entryContainer of this attribute index.
   * @param state The state database to persist index state info.
   * @param env The JE environment handle.
   * @param indexConfig The attribute index configuration.
   * @throws DatabaseException if a JE database error occurs.
   * @throws ConfigException if a configuration related error occurs.
   */
  public AttributeIndex(LocalDBIndexCfg indexConfig, State state,
                        Environment env, EntryContainer entryContainer)
      throws DatabaseException, ConfigException
  {
    this.entryContainer = entryContainer;
    this.env = env;
    this.indexConfig = indexConfig;
    this.state = state;
    nameToIndexes = new HashMap<String, Index>();

    AttributeType attrType = indexConfig.getAttribute();
    String name = entryContainer.getDatabasePrefix() + "_" + attrType.getNameOrOID();
    final JEIndexConfig config = new JEIndexConfig(indexConfig.getSubstringLength());

    if (indexConfig.getIndexType().contains(IndexType.EQUALITY))
    {
      this.equalityIndex = buildExtIndex(
          name, attrType, attrType.getEqualityMatchingRule(), new EqualityIndexer(attrType));
      nameToIndexes.put(IndexType.EQUALITY.toString(), equalityIndex);
    }

    if (indexConfig.getIndexType().contains(IndexType.PRESENCE))
    {
      this.presenceIndex = newIndex(name + ".presence",
          new PresenceIndexer(attrType), indexConfig.getIndexEntryLimit());
      nameToIndexes.put(IndexType.PRESENCE.toString(), presenceIndex);
    }

    if (indexConfig.getIndexType().contains(IndexType.SUBSTRING))
    {
      this.substringIndex = buildExtIndex(
          name, attrType, attrType.getSubstringMatchingRule(), new SubstringIndexer(attrType, config));
      nameToIndexes.put(IndexType.SUBSTRING.toString(), substringIndex);
    }

    if (indexConfig.getIndexType().contains(IndexType.ORDERING))
    {
      this.orderingIndex = buildExtIndex(
          name, attrType, attrType.getOrderingMatchingRule(), new OrderingIndexer(attrType));
      nameToIndexes.put(IndexType.ORDERING.toString(), orderingIndex);
    }

    if (indexConfig.getIndexType().contains(IndexType.APPROXIMATE))
    {
      this.approximateIndex = buildExtIndex(
          name, attrType, attrType.getApproximateMatchingRule(), new ApproximateIndexer(attrType));
      nameToIndexes.put(IndexType.APPROXIMATE.toString(), approximateIndex);
    }

    indexQueryFactory = new IndexQueryFactoryImpl(nameToIndexes, config);

    if (indexConfig.getIndexType().contains(IndexType.EXTENSIBLE))
    {
      Set<String> extensibleRules = indexConfig.getIndexExtensibleMatchingRule();
      if(extensibleRules == null || extensibleRules.isEmpty())
      {
        throw new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, "extensible"));
      }
      extensibleIndexes = new ExtensibleMatchingRuleIndex();

      // Iterate through the Set and create the index only if necessary.
      // Collation equality and Ordering matching rules share the same
      // indexer and index.
      // A Collation substring matching rule is treated differently
      // as it uses a separate indexer and index.
      for(String ruleName:extensibleRules)
      {
        ExtensibleMatchingRule rule = DirectoryServer.getExtensibleMatchingRule(toLowerCase(ruleName));
        if(rule == null)
        {
          logger.error(ERR_CONFIG_INDEX_TYPE_NEEDS_VALID_MATCHING_RULE, attrType, ruleName);
          continue;
        }
        Map<String,Index> indexMap = new HashMap<String,Index>();
        for (ExtensibleIndexer indexer : rule.getIndexers())
        {
          String indexID = attrType.getNameOrOID() + "." + indexer.getIndexID();
          if(!extensibleIndexes.isIndexPresent(indexID))
          {
            //There is no index available for this index id. Create a new index.
            String indexName = entryContainer.getDatabasePrefix() + "_" + indexID;
            Index extIndex = newExtensibleIndex(indexName, attrType, indexer);
            extensibleIndexes.addIndex(extIndex, indexID);
          }
          extensibleIndexes.addRule(indexID, rule);
          indexMap.put(indexer.getExtensibleIndexID(), extensibleIndexes.getIndex(indexID));
        }
        IndexQueryFactory<IndexQuery> factory = new IndexQueryFactoryImpl(indexMap, config);
        extensibleIndexes.addQueryFactory(rule, factory);
      }
    }
  }

  private Index newIndex(String indexName, Indexer indexer,
      int indexEntryLimit)
  {
    return new Index(indexName, indexer, state, indexEntryLimit,
        cursorEntryLimit, false, env, entryContainer);
  }

  private Index buildExtIndex(String name, AttributeType attrType,
      MatchingRule rule, ExtensibleIndexer extIndexer) throws ConfigException
  {
    if (rule == null)
    {
      throw new ConfigException(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
          attrType, extIndexer.getExtensibleIndexID()));
    }

    final String indexName = name + "." + extIndexer.getExtensibleIndexID();
    return newExtensibleIndex(indexName, attrType, extIndexer);
  }

  private Index newExtensibleIndex(String indexName, AttributeType attrType,
      ExtensibleIndexer extIndexer)
  {
    return newIndex(indexName, new JEExtensibleIndexer(attrType, extIndexer),
        indexConfig.getIndexEntryLimit());
  }

  /**
   * Open the attribute index.
   *
   * @throws DatabaseException if a JE database error occurs while
   * opening the index.
   */
  public void open() throws DatabaseException
  {
    open(equalityIndex);
    open(presenceIndex);
    open(substringIndex);
    open(orderingIndex);
    open(approximateIndex);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.open();
      }
    }

    indexConfig.addChangeListener(this);
  }

  private void open(Index index)
  {
    if (index != null)
    {
      index.open();
    }
  }

  /**
   * Close the attribute index.
   *
   * @throws DatabaseException if a JE database error occurs while
   * closing the index.
   */
  public void close() throws DatabaseException
  {
    Utils.closeSilently(equalityIndex, presenceIndex, substringIndex,
        orderingIndex, approximateIndex);

    if(extensibleIndexes!=null)
    {
      Utils.closeSilently(extensibleIndexes.getIndexes());
    }

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
   * @return True if all the index keys for the entry are added. False if the
   *         entry ID already exists for some keys.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public boolean addEntry(IndexBuffer buffer, EntryID entryID,
                          Entry entry)
       throws DatabaseException, DirectoryException
  {
    boolean success = true;

    success = addEntry(equalityIndex, buffer, entryID, entry, success);
    success = addEntry(presenceIndex, buffer, entryID, entry, success);
    success = addEntry(substringIndex, buffer, entryID, entry, success);
    success = addEntry(orderingIndex, buffer, entryID, entry, success);
    success = addEntry(approximateIndex, buffer, entryID, entry, success);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        if(!extensibleIndex.addEntry(buffer, entryID,entry))
        {
          success = false;
        }
      }
    }

    return success;
  }

  private boolean addEntry(Index index, IndexBuffer buffer, EntryID entryID,
      Entry entry, boolean success) throws DirectoryException, DatabaseException
  {
    if (index != null && !index.addEntry(buffer, entryID, entry))
    {
      return false;
    }
    return success;
  }

  /**
   * Update the attribute index for a new entry.
   *
   * @param txn         The database transaction to be used for the insertions.
   * @param entryID     The entry ID.
   * @param entry       The contents of the new entry.
   * @return True if all the index keys for the entry are added. False if the
   *         entry ID already exists for some keys.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public boolean addEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException
  {
    boolean success = true;

    success = addEntry(equalityIndex, txn, entryID, entry, success);
    success = addEntry(presenceIndex, txn, entryID, entry, success);
    success = addEntry(substringIndex, txn, entryID, entry, success);
    success = addEntry(orderingIndex, txn, entryID, entry, success);
    success = addEntry(approximateIndex, txn, entryID, entry, success);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        if(!extensibleIndex.addEntry(txn, entryID,entry))
        {
          success = false;
        }
      }
    }

    return success;
  }

  private boolean addEntry(Index index, Transaction txn, EntryID entryID,
      Entry entry, boolean success) throws DirectoryException, DatabaseException
  {
    if (index != null && !index.addEntry(txn, entryID, entry))
    {
      return false;
    }
    return success;
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
  public void removeEntry(IndexBuffer buffer, EntryID entryID,
                          Entry entry)
       throws DatabaseException, DirectoryException
  {
    removeEntry(equalityIndex, buffer, entryID, entry);
    removeEntry(presenceIndex, buffer, entryID, entry);
    removeEntry(substringIndex, buffer, entryID, entry);
    removeEntry(orderingIndex, buffer, entryID, entry);
    removeEntry(approximateIndex, buffer, entryID, entry);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.removeEntry(buffer, entryID, entry);
      }
    }
  }

  private void removeEntry(Index index, IndexBuffer buffer, EntryID entryID,
      Entry entry) throws DirectoryException, DatabaseException
  {
    if (index != null)
    {
      index.removeEntry(buffer, entryID, entry);
    }
  }

  /**
   * Update the attribute index for a deleted entry.
   *
   * @param txn         The database transaction to be used for the deletions
   * @param entryID     The entry ID
   * @param entry       The contents of the deleted entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void removeEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException
  {
    removeEntry(equalityIndex, txn, entryID, entry);
    removeEntry(presenceIndex, txn, entryID, entry);
    removeEntry(substringIndex, txn, entryID, entry);
    removeEntry(orderingIndex, txn, entryID, entry);
    removeEntry(approximateIndex, txn, entryID, entry);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.removeEntry(txn, entryID, entry);
      }
    }
  }

  private void removeEntry(Index index, Transaction txn, EntryID entryID,
      Entry entry) throws DirectoryException, DatabaseException
  {
    if (index != null)
    {
      index.removeEntry(txn, entryID, entry);
    }
  }

  /**
   * Update the index to reflect a sequence of modifications in a Modify
   * operation.
   *
   * @param txn The JE transaction to use for database updates.
   * @param entryID The ID of the entry that was modified.
   * @param oldEntry The entry before the modifications were applied.
   * @param newEntry The entry after the modifications were applied.
   * @param mods The sequence of modifications in the Modify operation.
   * @throws DatabaseException If an error occurs during an operation on a
   * JE database.
   */
  public void modifyEntry(Transaction txn,
                          EntryID entryID,
                          Entry oldEntry,
                          Entry newEntry,
                          List<Modification> mods)
       throws DatabaseException
  {
    modifyEntry(equalityIndex, txn, entryID, oldEntry, newEntry, mods);
    modifyEntry(presenceIndex, txn, entryID, oldEntry, newEntry, mods);
    modifyEntry(substringIndex, txn, entryID, oldEntry, newEntry, mods);
    modifyEntry(orderingIndex, txn, entryID, oldEntry, newEntry, mods);
    modifyEntry(approximateIndex, txn, entryID, oldEntry, newEntry, mods);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
      }
    }
  }

  private void modifyEntry(Index index, Transaction txn, EntryID entryID,
      Entry oldEntry, Entry newEntry, List<Modification> mods)
      throws DatabaseException
  {
    if (index != null)
    {
      index.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
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
    modifyEntry(equalityIndex, buffer, entryID, oldEntry, newEntry, mods);
    modifyEntry(presenceIndex, buffer, entryID, oldEntry, newEntry, mods);
    modifyEntry(substringIndex, buffer, entryID, oldEntry, newEntry, mods);
    modifyEntry(orderingIndex, buffer, entryID, oldEntry, newEntry, mods);
    modifyEntry(approximateIndex, buffer, entryID, oldEntry, newEntry, mods);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
      }
    }
  }

  private void modifyEntry(Index index, IndexBuffer buffer, EntryID entryID,
      Entry oldEntry, Entry newEntry, List<Modification> mods)
      throws DatabaseException
  {
    if (index != null)
    {
      index.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }
  }

  /**
   * Makes a byte array representing a substring index key for
   * one substring of a value.
   *
   * @param bytes The byte array containing the value.
   * @param pos The starting position of the substring.
   * @param len The length of the substring.
   * @return A byte array containing a substring key.
   */
  private byte[] makeSubstringKey(byte[] bytes, int pos, int len)
  {
    byte[] keyBytes = new byte[len];
    System.arraycopy(bytes, pos, keyBytes, 0, len);
    return keyBytes;
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
    // containing a longer substring such as BCDE, read keys
    // BCD and CDE.
    for (int i = 0, remain = value.length; remain > 0; i++, remain--)
    {
      int len = Math.min(substrLength, remain);
      byte[] keyBytes = makeSubstringKey(value, i, len);
      set.add(ByteString.wrap(keyBytes));
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
   * Retrieve the entry IDs that might match an equality filter.
   *
   * @param equalityFilter The equality filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateEqualityFilter(SearchFilter equalityFilter,
                                           StringBuilder debugBuffer,
                                           DatabaseEnvironmentMonitor monitor)
  {
    try {
      final MatchingRule matchRule = equalityFilter.getAttributeType().getEqualityMatchingRule();
      final IndexQuery indexQuery = matchRule.getAssertion(equalityFilter.getAssertionValue())
          .createIndexQuery(indexQueryFactory);
      return evaluateIndexQuery(indexQuery, "equality", equalityFilter, debugBuffer, monitor);
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return new EntryIDSet();
    }
  }

  /**
   * Retrieve the entry IDs that might match a presence filter.
   *
   * @param filter The presence filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain one or more
   *         values of the attribute type in the filter.
   */
  public EntryIDSet evaluatePresenceFilter(SearchFilter filter,
                                           StringBuilder debugBuffer,
                                           DatabaseEnvironmentMonitor monitor)
  {
    final IndexQuery indexQuery = indexQueryFactory.createMatchAllQuery();
    return evaluateIndexQuery(indexQuery, "presence", filter, debugBuffer, monitor);
  }

  /**
   * Retrieve the entry IDs that might match a greater-or-equal filter.
   *
   * @param filter The greater-or-equal filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain a value
   *         greater than or equal to the filter assertion value.
   */
  public EntryIDSet evaluateGreaterOrEqualFilter(
      SearchFilter filter, StringBuilder debugBuffer,
      DatabaseEnvironmentMonitor monitor)
  {
    return evaluateOrderingFilter(filter, true, debugBuffer, monitor);
  }


  /**
   * Retrieve the entry IDs that might match a less-or-equal filter.
   *
   * @param filter The less-or-equal filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain a value
   *         less than or equal to the filter assertion value.
   */
  public EntryIDSet evaluateLessOrEqualFilter(SearchFilter filter, StringBuilder debugBuffer,
      DatabaseEnvironmentMonitor monitor)
  {
    return evaluateOrderingFilter(filter, false, debugBuffer, monitor);
  }

  private EntryIDSet evaluateOrderingFilter(SearchFilter filter, boolean greater, StringBuilder debugBuffer,
      DatabaseEnvironmentMonitor monitor)
  {
    try {
      final MatchingRule matchRule = filter.getAttributeType().getOrderingMatchingRule();
      final Assertion assertion = greater ?
          matchRule.getGreaterOrEqualAssertion(filter.getAssertionValue()) :
          matchRule.getLessOrEqualAssertion(filter.getAssertionValue());
      final IndexQuery indexQuery = assertion.createIndexQuery(indexQueryFactory);
      return evaluateIndexQuery(indexQuery, "ordering", filter, debugBuffer, monitor);
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return new EntryIDSet();
    }
  }

  /**
   * Retrieve the entry IDs that might match a substring filter.
   *
   * @param filter The substring filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain a value
   *         that matches the filter substrings.
   */
  public EntryIDSet evaluateSubstringFilter(SearchFilter filter,
                                            StringBuilder debugBuffer,
                                            DatabaseEnvironmentMonitor monitor)
  {
    try {
      final MatchingRule matchRule = filter.getAttributeType().getSubstringMatchingRule();
      final IndexQuery indexQuery = matchRule.getSubstringAssertion(
          filter.getSubInitialElement(), filter.getSubAnyElements(), filter.getSubFinalElement())
          .createIndexQuery(indexQueryFactory);
      return evaluateIndexQuery(indexQuery, "substring", filter, debugBuffer, monitor);
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return new EntryIDSet();
    }
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
    // thus defeating the purpose of the optimisation done
    // in IndexFilter#evaluateLogicalAndFilter method.
    // One solution could be to implement a boundedRangeAssertion that combine
    // the two operations in one.
    EntryIDSet results = filter1.getFilterType() == FilterType.LESS_OR_EQUAL ?
        evaluateLessOrEqualFilter(filter1, debugBuffer, monitor) :
        evaluateGreaterOrEqualFilter(filter1, debugBuffer, monitor);
    EntryIDSet results2 = filter2.getFilterType() == FilterType.LESS_OR_EQUAL ?
        evaluateLessOrEqualFilter(filter2, debugBuffer, monitor) :
        evaluateGreaterOrEqualFilter(filter2, debugBuffer, monitor);
    results.retainAll(results2);
    return results;
  }

  /**
   * The default lexicographic byte array comparator.
   * Is there one available in the Java platform?
   */
  public static class KeyComparator implements Comparator<byte[]>
  {
    /**
     * Compares its two arguments for order.  Returns a negative integer,
     * zero, or a positive integer as the first argument is less than, equal
     * to, or greater than the second.
     *
     * @param a the first object to be compared.
     * @param b the second object to be compared.
     * @return a negative integer, zero, or a positive integer as the
     *         first argument is less than, equal to, or greater than the
     *         second.
     */
    @Override
    public int compare(byte[] a, byte[] b)
    {
      int i;
      for (i = 0; i < a.length && i < b.length; i++)
      {
        if (a[i] > b[i])
        {
          return 1;
        }
        else if (a[i] < b[i])
        {
          return -1;
        }
      }
      if (a.length == b.length)
      {
        return 0;
      }
      if (a.length > b.length)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
  }

  /**
   * Byte string key comparator. The default lexicographic byte string
   * comparator.
   * <p>
   * This is the byte string equivalent of {@link KeyComparator}.
   * <p>
   * Note: Matt reckons we could simply use ByteString.compareTo(),
   * but I am using this for now as an intermediate step.
   */
  public static class BSKeyComparator implements Comparator<ByteString>
  {
    /**
     * Compares its two arguments for order. Returns a negative integer, zero,
     * or a positive integer as the first argument is less than, equal to, or
     * greater than the second.
     *
     * @param a
     *          the first object to be compared.
     * @param b
     *          the second object to be compared.
     * @return a negative integer, zero, or a positive integer as the first
     *         argument is less than, equal to, or greater than the second.
     */
    @Override
    public int compare(ByteString a, ByteString b)
    {
      int i;
      for (i = 0; i < a.length() && i < b.length(); i++)
      {
        if (a.byteAt(i) > b.byteAt(i))
        {
          return 1;
        }
        else if (a.byteAt(i) < b.byteAt(i))
        {
          return -1;
        }
      }
      if (a.length() == b.length())
      {
        return 0;
      }
      if (a.length() > b.length())
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
  }

  /**
   * Retrieve the entry IDs that might match an approximate filter.
   *
   * @param approximateFilter The approximate filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateApproximateFilter(SearchFilter approximateFilter, StringBuilder debugBuffer,
      DatabaseEnvironmentMonitor monitor)
  {
    try {
      MatchingRule matchRule = approximateFilter.getAttributeType().getApproximateMatchingRule();
      IndexQuery indexQuery = matchRule.getAssertion(approximateFilter.getAssertionValue())
          .createIndexQuery(indexQueryFactory);
      return evaluateIndexQuery(indexQuery, "approximate", approximateFilter, debugBuffer, monitor);
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return new EntryIDSet();
    }
  }

  /**
   * Close cursors related to the attribute indexes.
   *
   * @throws DatabaseException If a database error occurs.
   */
  public void closeCursors() throws DatabaseException {
    if (equalityIndex != null)
    {
      equalityIndex.closeCursor();
    }

    if (presenceIndex != null)
    {
      presenceIndex.closeCursor();
    }

    if (substringIndex != null)
    {
      substringIndex.closeCursor();
    }

    if (orderingIndex != null)
    {
      orderingIndex.closeCursor();
    }

    if (approximateIndex != null)
    {
      approximateIndex.closeCursor();
    }

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.closeCursor();
      }
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

    if (equalityIndex != null)
    {
      entryLimitExceededCount += equalityIndex.getEntryLimitExceededCount();
    }

    if (presenceIndex != null)
    {
      entryLimitExceededCount += presenceIndex.getEntryLimitExceededCount();
    }

    if (substringIndex != null)
    {
      entryLimitExceededCount += substringIndex.getEntryLimitExceededCount();
    }

    if (orderingIndex != null)
    {
      entryLimitExceededCount += orderingIndex.getEntryLimitExceededCount();
    }

    if (approximateIndex != null)
    {
      entryLimitExceededCount +=
          approximateIndex.getEntryLimitExceededCount();
    }

     if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        entryLimitExceededCount +=
                extensibleIndex.getEntryLimitExceededCount();
      }
    }
    return entryLimitExceededCount;
  }

  /**
   * Get a list of the databases opened by this attribute index.
   * @param dbList A list of database containers.
   */
  public void listDatabases(List<DatabaseContainer> dbList)
  {
    addIfNotNull(dbList, equalityIndex);
    addIfNotNull(dbList, presenceIndex);
    addIfNotNull(dbList, substringIndex);
    addIfNotNull(dbList, orderingIndex);
    addIfNotNull(dbList, approximateIndex);

    if(extensibleIndexes!=null)
    {
      dbList.addAll(extensibleIndexes.getIndexes());
    }
  }

  private void addIfNotNull(Collection<? super Index> dbList, Index index)
  {
    if (index != null)
    {
      dbList.add(index);
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
      LocalDBIndexCfg cfg,
      List<LocalizableMessage> unacceptableReasons)
  {
    AttributeType attrType = cfg.getAttribute();

    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.EQUALITY)
        && equalityIndex == null
        && attrType.getEqualityMatchingRule() == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, "equality"));
      return false;
    }

    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.SUBSTRING)
        && substringIndex == null
        && attrType.getSubstringMatchingRule() == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, "substring"));
      return false;
    }

    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.ORDERING)
        && orderingIndex == null
        && attrType.getOrderingMatchingRule() == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, "ordering"));
      return false;
    }
    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.APPROXIMATE)
        && approximateIndex == null
        && attrType.getApproximateMatchingRule() == null)
    {
      unacceptableReasons.add(ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(attrType, "approximate"));
      return false;
    }
    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.EXTENSIBLE))
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

  /** {@inheritDoc} */
  @Override
  public synchronized ConfigChangeResult applyConfigurationChange(
      LocalDBIndexCfg cfg)
  {
    // this method is not perf sensitive, using an AtomicBoolean will not hurt
    AtomicBoolean adminActionRequired = new AtomicBoolean(false);
    ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();
    try
    {
      AttributeType attrType = cfg.getAttribute();
      String name = entryContainer.getDatabasePrefix() + "_" + attrType.getNameOrOID();
      final int indexEntryLimit = cfg.getIndexEntryLimit();
      final JEIndexConfig config = new JEIndexConfig(cfg.getSubstringLength());

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.EQUALITY))
      {
        if (equalityIndex == null)
        {
          equalityIndex = openNewIndex(name, attrType,
              new EqualityIndexer(attrType), adminActionRequired, messages);
          nameToIndexes.put(LocalDBIndexCfgDefn.IndexType.EQUALITY.toString(), equalityIndex);
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.equalityIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired.set(true);
            messages.add(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(equalityIndex.getName()));
            this.equalityIndex.setIndexEntryLimit(indexEntryLimit);
          }
        }
      }
      else
      {
        if (equalityIndex != null)
        {
          entryContainer.exclusiveLock.lock();
          try
          {
            nameToIndexes.remove(LocalDBIndexCfgDefn.IndexType.EQUALITY.toString());
            entryContainer.deleteDatabase(equalityIndex);
            equalityIndex = null;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.PRESENCE))
      {
        if(presenceIndex == null)
        {
          Indexer presenceIndexer = new PresenceIndexer(attrType);
          presenceIndex = newIndex(name + ".presence", presenceIndexer, indexEntryLimit);
          openIndex(presenceIndex, adminActionRequired, messages);
          nameToIndexes.put(LocalDBIndexCfgDefn.IndexType.PRESENCE.toString(), presenceIndex);
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.presenceIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired.set(true);
            messages.add(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(presenceIndex.getName()));
          }
        }
      }
      else
      {
        if (presenceIndex != null)
        {
          entryContainer.exclusiveLock.lock();
          try
          {
            nameToIndexes.remove(LocalDBIndexCfgDefn.IndexType.PRESENCE.toString());
            entryContainer.deleteDatabase(presenceIndex);
            presenceIndex = null;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.SUBSTRING))
      {
        SubstringIndexer indexer = new SubstringIndexer(attrType, config);
        Indexer extIndexer = new JEExtensibleIndexer(attrType, indexer);
        if(substringIndex == null)
        {
          Index index = newIndex(name + "." + indexer.getExtensibleIndexID(),
              extIndexer, indexEntryLimit);
          substringIndex = openIndex(index, adminActionRequired, messages);
          nameToIndexes.put(LocalDBIndexCfgDefn.IndexType.SUBSTRING.toString(), substringIndex);
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.substringIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired.set(true);
            messages.add(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(substringIndex.getName()));
          }

          if (indexConfig.getSubstringLength() != cfg.getSubstringLength())
          {
            this.substringIndex.setIndexer(extIndexer);
          }
        }
      }
      else
      {
        if (substringIndex != null)
        {
          entryContainer.exclusiveLock.lock();
          try
          {
            nameToIndexes.remove(LocalDBIndexCfgDefn.IndexType.SUBSTRING.toString());
            entryContainer.deleteDatabase(substringIndex);
            substringIndex = null;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.ORDERING))
      {
        if(orderingIndex == null)
        {
          orderingIndex = openNewIndex(name, attrType,
              new OrderingIndexer(attrType), adminActionRequired, messages);
          nameToIndexes.put(LocalDBIndexCfgDefn.IndexType.ORDERING.toString(), orderingIndex);
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.orderingIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired.set(true);
            messages.add(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(orderingIndex.getName()));
          }
        }
      }
      else
      {
        if (orderingIndex != null)
        {
          entryContainer.exclusiveLock.lock();
          try
          {
            nameToIndexes.remove(LocalDBIndexCfgDefn.IndexType.ORDERING.toString());
            entryContainer.deleteDatabase(orderingIndex);
            orderingIndex = null;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.APPROXIMATE))
      {
        if(approximateIndex == null)
        {
          approximateIndex = openNewIndex(name, attrType,
              new ApproximateIndexer(attrType), adminActionRequired, messages);
          nameToIndexes.put(LocalDBIndexCfgDefn.IndexType.APPROXIMATE.toString(), approximateIndex);
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.approximateIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired.set(true);
            messages.add(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(approximateIndex.getName()));
          }
        }
      }
      else
      {
        if (approximateIndex != null)
        {
          entryContainer.exclusiveLock.lock();
          try
          {
            nameToIndexes.remove(LocalDBIndexCfgDefn.IndexType.APPROXIMATE.toString());
            entryContainer.deleteDatabase(approximateIndex);
            approximateIndex = null;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.EXTENSIBLE))
      {
        Set<String> extensibleRules = cfg.getIndexExtensibleMatchingRule();
        Set<ExtensibleMatchingRule> validRules = new HashSet<ExtensibleMatchingRule>();
        if(extensibleIndexes == null)
        {
          extensibleIndexes = new ExtensibleMatchingRuleIndex();
        }
        for(String ruleName:extensibleRules)
        {
          ExtensibleMatchingRule rule = DirectoryServer.getExtensibleMatchingRule(toLowerCase(ruleName));
          if(rule == null)
          {
            logger.error(ERR_CONFIG_INDEX_TYPE_NEEDS_VALID_MATCHING_RULE, attrType, ruleName);
            continue;
          }
          validRules.add(rule);
          Map<String,Index> indexMap = new HashMap<String,Index>();
          for (ExtensibleIndexer indexer : rule.getIndexers())
          {
            String indexID = attrType.getNameOrOID() + "." + indexer.getIndexID();
            if(!extensibleIndexes.isIndexPresent(indexID))
            {
              String indexName =  entryContainer.getDatabasePrefix() + "_" + indexID;
              Index extIndex = newExtensibleIndex(indexName, attrType, indexer);
              extensibleIndexes.addIndex(extIndex,indexID);
              openIndex(extIndex, adminActionRequired, messages);
            }
            else
            {
              Index extensibleIndex = extensibleIndexes.getIndex(indexID);
              if(extensibleIndex.setIndexEntryLimit(indexEntryLimit))
              {
                adminActionRequired.set(true);
                messages.add(NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(extensibleIndex.getName()));
              }
              if (indexConfig.getSubstringLength() != cfg.getSubstringLength())
              {
                extensibleIndex.setIndexer(
                    new JEExtensibleIndexer(attrType, indexer));
              }
            }
            extensibleIndexes.addRule(indexID, rule);
            indexMap.put(indexer.getExtensibleIndexID(), extensibleIndexes.getIndex(indexID));
          }
          IndexQueryFactory<IndexQuery> factory = new IndexQueryFactoryImpl(indexMap, config);
          extensibleIndexes.addQueryFactory(rule, factory);
        }
        //Some rules might have been removed from the configuration.
        Set<ExtensibleMatchingRule> deletedRules =
            new HashSet<ExtensibleMatchingRule>(extensibleIndexes.getRules());
        deletedRules.removeAll(validRules);
        if(deletedRules.size() > 0)
        {
          entryContainer.exclusiveLock.lock();
          try
          {
            for(ExtensibleMatchingRule rule:deletedRules)
            {
              Set<ExtensibleMatchingRule> rules =
                      new HashSet<ExtensibleMatchingRule>();
              List<String> ids = new ArrayList<String>();
              for (ExtensibleIndexer indexer : rule.getIndexers())
              {
                String id = attrType.getNameOrOID()  + "." + indexer.getIndexID();
                rules.addAll(extensibleIndexes.getRules(id));
                ids.add(id);
              }
              if(rules.isEmpty())
              {
                //Rule has been already deleted.
                continue;
              }
              //If all the rules are part of the deletedRules, delete this index
              if(deletedRules.containsAll(rules))
              {
                //it is safe to delete this index as it is not shared.
                for(String indexID : ids)
                {
                  Index extensibleIndex = extensibleIndexes.getIndex(indexID);
                  entryContainer.deleteDatabase(extensibleIndex);
                  extensibleIndexes.deleteIndex(indexID);
                  extensibleIndexes.deleteRule(indexID);
                }
              }
              else
              {
                for(String indexID : ids)
                {
                  extensibleIndexes.deleteRule(rule, indexID);
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
      else
      {
        if(extensibleIndexes != null)
        {
          entryContainer.exclusiveLock.lock();
          try
          {
            for(Index extensibleIndex:extensibleIndexes.getIndexes())
            {
              entryContainer.deleteDatabase(extensibleIndex);
            }
            extensibleIndexes.deleteAll();
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      indexConfig = cfg;

      return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired.get(), messages);
    }
    catch(Exception e)
    {
      messages.add(LocalizableMessage.raw(StaticUtils.stackTraceToSingleLineString(e)));
      return new ConfigChangeResult(
          DirectoryServer.getServerErrorResultCode(), adminActionRequired.get(), messages);
    }
  }

  private Index openNewIndex(String name, AttributeType attrType,
      ExtensibleIndexer indexer, AtomicBoolean adminActionRequired,
      ArrayList<LocalizableMessage> messages)
  {
    String indexName = name + "." + indexer.getExtensibleIndexID();
    Index index = newExtensibleIndex(indexName, attrType, indexer);
    return openIndex(index, adminActionRequired, messages);
  }

  private Index openIndex(Index index, AtomicBoolean adminActionRequired,
      ArrayList<LocalizableMessage> messages)
  {
    index.open();

    if (!index.isTrusted())
    {
      adminActionRequired.set(true);
      messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(index.getName()));
    }
    return index;
  }

  /**
   * Set the index truststate.
   * @param txn A database transaction, or null if none is required.
   * @param trusted True if this index should be trusted or false
   *                otherwise.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public synchronized void setTrusted(Transaction txn, boolean trusted)
      throws DatabaseException
  {
    setTrusted(equalityIndex, txn, trusted);
    setTrusted(presenceIndex, txn, trusted);
    setTrusted(substringIndex, txn, trusted);
    setTrusted(orderingIndex, txn, trusted);
    setTrusted(approximateIndex, txn, trusted);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.setTrusted(txn, trusted);
      }
    }
  }

  private void setTrusted(Index index, Transaction txn, boolean trusted)
  {
    if (index != null)
    {
      index.setTrusted(txn, trusted);
    }
  }

  /**
   * Return true iff this index is trusted.
   * @return the trusted state of this index
   */
  public boolean isTrusted()
  {
    if ((equalityIndex != null && !equalityIndex.isTrusted())
        || (presenceIndex != null && !presenceIndex.isTrusted())
        || (substringIndex != null && !substringIndex.isTrusted())
        || (orderingIndex != null && !orderingIndex.isTrusted())
        || (approximateIndex != null && !approximateIndex.isTrusted()))
    {
      return false;
    }

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        if(extensibleIndex !=null && !extensibleIndex.isTrusted())
        {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Set the rebuild status of this index.
   * @param rebuildRunning True if a rebuild process on this index
   *                       is running or False otherwise.
   */
  public synchronized void setRebuildStatus(boolean rebuildRunning)
  {
    setRebuildStatus(rebuildRunning, equalityIndex);
    setRebuildStatus(rebuildRunning, presenceIndex);
    setRebuildStatus(rebuildRunning, substringIndex);
    setRebuildStatus(rebuildRunning, orderingIndex);
    setRebuildStatus(rebuildRunning, approximateIndex);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        extensibleIndex.setRebuildStatus(rebuildRunning);
      }
    }
  }

  private void setRebuildStatus(boolean rebuildRunning, Index index)
  {
    if (index != null)
    {
      index.setRebuildStatus(rebuildRunning);
    }
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
    return  equalityIndex;
  }

  /**
   * Return the approximate index.
   *
   * @return The approximate index.
   */
  public Index getApproximateIndex() {
    return approximateIndex;
  }

  /**
   * Return the ordering index.
   *
   * @return  The ordering index.
   */
  public Index getOrderingIndex() {
    return orderingIndex;
  }

  /**
   * Return the substring index.
   *
   * @return The substring index.
   */
  public Index getSubstringIndex() {
    return substringIndex;
  }

  /**
   * Return the presence index.
   *
   * @return The presence index.
   */
  public Index getPresenceIndex() {
    return presenceIndex;
  }

  /**
   * Return the mapping of  extensible index types and indexes.
   *
   * @return The Map of extensible index types and indexes.
   */
  public Map<String,Collection<Index>> getExtensibleIndexes()
  {
    if(extensibleIndexes == null)
    {
      return Collections.emptyMap();
    }
    return extensibleIndexes.getIndexMap();
  }


  /**
   * Retrieves all the indexes used by this attribute index.
   *
   * @return A collection of all indexes in use by this attribute
   * index.
   */
  public Collection<Index> getAllIndexes() {
    LinkedHashSet<Index> indexes = new LinkedHashSet<Index>();

    addIfNotNull(indexes, equalityIndex);
    addIfNotNull(indexes, presenceIndex);
    addIfNotNull(indexes, substringIndex);
    addIfNotNull(indexes, orderingIndex);
    addIfNotNull(indexes, approximateIndex);

    if(extensibleIndexes!=null)
    {
      for(Index extensibleIndex:extensibleIndexes.getIndexes())
      {
        indexes.add(extensibleIndex);
      }
    }
    return indexes;
  }


  /**
   * Retrieve the entry IDs that might match an extensible filter.
   *
   * @param extensibleFilter The extensible filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @param monitor The database environment monitor provider that will keep
   *                index filter usage statistics.
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateExtensibleFilter(SearchFilter extensibleFilter,
                                             StringBuilder debugBuffer,
                                             DatabaseEnvironmentMonitor monitor)
  {
    //Get the Matching Rule OID of the filter.
    String matchRuleOID  = extensibleFilter.getMatchingRuleID();
    /**
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
      return evaluateEqualityFilter(extensibleFilter, debugBuffer, monitor);
    }

    ExtensibleMatchingRule rule = DirectoryServer.getExtensibleMatchingRule(matchRuleOID);
    IndexQueryFactory<IndexQuery> factory = null;
    if (extensibleIndexes == null || (factory = extensibleIndexes.getQueryFactory(rule)) == null)
    {
      // There is no index on this matching rule.
      if (monitor.isFilterUseEnabled())
      {
        monitor.updateStats(extensibleFilter, INFO_JEB_INDEX_FILTER_MATCHING_RULE_NOT_INDEXED.get(
            matchRuleOID, indexConfig.getAttribute().getNameOrOID()));
      }
      return IndexQuery.createNullIndexQuery().evaluate(null);
    }

    try
    {
      if (debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        for (ExtensibleIndexer indexer : rule.getIndexers())
        {
          debugBuffer.append(" ")
                     .append(extensibleFilter.getAttributeType().getNameOrOID())
                     .append(".")
                     .append(indexer.getIndexID());
        }
        debugBuffer.append("]");
      }
      ByteString assertionValue = extensibleFilter.getAssertionValue();
      IndexQuery expression = rule.createIndexQuery(assertionValue, factory);
      LocalizableMessageBuilder debugMessage = monitor.isFilterUseEnabled() ? new LocalizableMessageBuilder() : null;
      EntryIDSet idSet = expression.evaluate(debugMessage);
      if (monitor.isFilterUseEnabled())
      {
        if (idSet.isDefined())
        {
          monitor.updateStats(extensibleFilter, idSet.size());
        }
        else
        {
          monitor.updateStats(extensibleFilter, debugMessage.toMessage());
        }
      }
      return idSet;
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return IndexQuery.createNullIndexQuery().evaluate(null);
    }
  }

  /**
   * This class manages all the configured extensible matching rules and
   * their corresponding indexes.
   */
  private static class ExtensibleMatchingRuleIndex
  {
    /**
      * The mapping of index ID and Index database.
      */
    private final Map<String,Index> id2IndexMap;

    /**
     * The mapping of Index ID and Set the matching rules.
     */
    private final Map<String,Set<ExtensibleMatchingRule>> id2RulesMap;

    /**
     * The Map of configured ExtensibleMatchingRule and the corresponding
     * IndexQueryFactory.
     */
    private final Map<ExtensibleMatchingRule,
            IndexQueryFactory<IndexQuery>> rule2FactoryMap;

    /**
     * Creates a new instance of ExtensibleMatchingRuleIndex.
     */
    private ExtensibleMatchingRuleIndex()
    {
      id2IndexMap = new HashMap<String,Index>();
      id2RulesMap = new HashMap<String,Set<ExtensibleMatchingRule>>();
      rule2FactoryMap = new HashMap<ExtensibleMatchingRule,
              IndexQueryFactory<IndexQuery>>();
    }

    /**
     * Returns all configured ExtensibleMatchingRule instances.
     * @return A Set  of extensible matching rules.
     */
    private Set<ExtensibleMatchingRule> getRules()
    {
      return rule2FactoryMap.keySet();
    }

    /**
     * Returns  ExtensibleMatchingRule instances for an index.
     * @param indexID The index ID of an extensible matching rule index.
     * @return A Set of extensible matching rules corresponding to
     *                 an index ID.
     */
    private Set<ExtensibleMatchingRule> getRules(String indexID)
    {
      Set<ExtensibleMatchingRule> rules = id2RulesMap.get(indexID);
      if (rules != null)
      {
        return Collections.unmodifiableSet(rules);
      }
      return Collections.emptySet();
    }

    /**
     * Returns whether an index is present or not.
     * @param indexID The index ID of an extensible matching rule index.
     * @return True if an index is present. False if there is no matching index.
     */
    private boolean isIndexPresent(String indexID)
    {
      return id2IndexMap.containsKey(indexID);
    }


    /**
     * Returns the index corresponding to an index ID.
     * @param indexID The ID of an index.
     * @return The extensible rule index corresponding to the index ID.
     */
    private Index getIndex(String indexID)
    {
      return id2IndexMap.get(indexID);
    }


    /**
     * Adds a new matching Rule and the name of the associated index.
     * @indexName Name of the index.
     * @rule An ExtensibleMatchingRule instance that needs to be indexed.
     */
    private void addRule(String indexName,ExtensibleMatchingRule rule)
    {
      Set<ExtensibleMatchingRule> rules = id2RulesMap.get(indexName);
      if(rules == null)
      {
        rules = new HashSet<ExtensibleMatchingRule>();
        id2RulesMap.put(indexName, rules);
      }
      rules.add(rule);
    }

    /**
     * Adds a new Index and its name.
     * @param index The extensible matching rule index.
     * @indexName The name of the index.
     */
    private void addIndex(Index index,String indexName)
    {
      id2IndexMap.put(indexName, index);
    }

    /**
     * Returns all the configured extensible indexes.
     * @return All the available extensible matching rule indexes.
     */
    private Collection<Index> getIndexes()
    {
      return Collections.unmodifiableCollection(id2IndexMap.values());
    }


    /**
     * Returns a map of all the configured extensible indexes and their types.
     * @return A map of all the available extensible matching rule indexes
     *             and their types.
     */
    private Map<String,Collection<Index>> getIndexMap()
    {
      if(id2IndexMap.isEmpty())
      {
        return Collections.emptyMap();
      }
      Collection<Index> substring = new ArrayList<Index>();
      Collection<Index> shared = new ArrayList<Index>();
      for(Map.Entry<String,Index> entry :  id2IndexMap.entrySet())
      {
        String indexID = entry.getKey();
        if(indexID.endsWith(EXTENSIBLE_INDEXER_ID_SUBSTRING))
        {
          substring.add(entry.getValue());
        }
        else
        {
          shared.add(entry.getValue());
        }
      }
      Map<String,Collection<Index>> indexMap =
              new HashMap<String,Collection<Index>>();
      indexMap.put(EXTENSIBLE_INDEXER_ID_SUBSTRING, substring);
      indexMap.put(EXTENSIBLE_INDEXER_ID_SHARED, shared);
      return Collections.unmodifiableMap(indexMap);
    }


    /**
     * Deletes an index corresponding to the index ID.
     * @param indexID Name of the index.
     */
    private void deleteIndex(String indexID)
    {
      id2IndexMap.remove(indexID);
    }


    /**
     * Deletes an extensible matching rule from the list of available rules.
     * @param rule The ExtensibleMatchingRule that needs to be removed.
     * @param indexID The name of the index corresponding to the rule.
     */
    private void deleteRule(ExtensibleMatchingRule rule,String indexID)
    {
      Set<ExtensibleMatchingRule> rules = id2RulesMap.get(indexID);
      rules.remove(rule);
      if(rules.isEmpty())
      {
        id2RulesMap.remove(indexID);
      }
      rule2FactoryMap.remove(rule);
    }


    /**
     * Adds an ExtensibleMatchingRule and its corresponding IndexQueryFactory.
     * @param rule An ExtensibleMatchingRule that needs to be added.
     * @param query A query factory matching the rule.
     */
    private void addQueryFactory(ExtensibleMatchingRule rule,
            IndexQueryFactory<IndexQuery> query)
    {
      rule2FactoryMap.put(rule, query);
    }


    /**
     * Returns the query factory associated with the rule.
     * @param rule An ExtensibleMatchingRule that needs to be searched.
     * @return An IndexQueryFactory corresponding to the matching rule.
     */
    private IndexQueryFactory<IndexQuery> getQueryFactory(
            ExtensibleMatchingRule rule)
    {
      return rule2FactoryMap.get(rule);
    }


    /**
     * Deletes  extensible matching rules from the list of available rules.
     * @param indexID The name of the index corresponding to the rules.
     */
    private void deleteRule(String indexID)
    {
      Set<ExtensibleMatchingRule> rules  = id2RulesMap.get(indexID);
      for (ExtensibleMatchingRule rule : rules)
      {
        rule2FactoryMap.remove(rule);
      }
      rules.clear();
      id2RulesMap.remove(indexID);
    }


    /**
     * Deletes all references to matching rules and the indexes.
     */
    private void deleteAll()
    {
      id2IndexMap.clear();
      id2RulesMap.clear();
      rule2FactoryMap.clear();
    }
  }

  /**
   * This class extends the IndexConfig for JE Backend.
   */
  private class JEIndexConfig implements IndexingOptions
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
