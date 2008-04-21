/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;
import org.opends.messages.Message;

import java.util.*;

import com.sleepycat.je.*;

import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.config.ConfigException;
import static org.opends.messages.JebMessages.*;

import org.opends.server.core.DirectoryServer;
import org.opends.server.util.StaticUtils;

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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



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

  private State state;

  private int cursorEntryLimit = 100000;

  /**
   * Create a new attribute index object.
   * @param entryContainer The entryContainer of this attribute index.
   * @param state The state database to persist index state info.
   * @param env The JE environment handle.
   * @param indexConfig The attribute index configuration.
   * @throws DatabaseException if a JE database error occurs.
   * @throws ConfigException if a configuration related error occurs.
   */
  public AttributeIndex(LocalDBIndexCfg indexConfig, State state,
                        Environment env,
                        EntryContainer entryContainer)
      throws DatabaseException, ConfigException
  {
    this.entryContainer = entryContainer;
    this.env = env;
    this.indexConfig = indexConfig;
    this.state = state;

    AttributeType attrType = indexConfig.getAttribute();
    String name =
        entryContainer.getDatabasePrefix() + "_" + attrType.getNameOrOID();
    int indexEntryLimit = indexConfig.getIndexEntryLimit();

    if (indexConfig.getIndexType().contains(
            LocalDBIndexCfgDefn.IndexType.EQUALITY))
    {
      if (attrType.getEqualityMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
            String.valueOf(attrType), "equality");
        throw new ConfigException(message);
      }

      Indexer equalityIndexer = new EqualityIndexer(attrType);
      this.equalityIndex = new Index(name + ".equality",
                                     equalityIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     false,
                                     env,
                                     entryContainer);
    }

    if (indexConfig.getIndexType().contains(
            LocalDBIndexCfgDefn.IndexType.PRESENCE))
    {
      Indexer presenceIndexer = new PresenceIndexer(attrType);
      this.presenceIndex = new Index(name + ".presence",
                                     presenceIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     false,
                                     env,
                                     entryContainer);
    }

    if (indexConfig.getIndexType().contains(
            LocalDBIndexCfgDefn.IndexType.SUBSTRING))
    {
      if (attrType.getSubstringMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
            String.valueOf(attrType), "substring");
        throw new ConfigException(message);
      }

      Indexer substringIndexer = new SubstringIndexer(attrType,
                                         indexConfig.getSubstringLength());
      this.substringIndex = new Index(name + ".substring",
                                     substringIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     false,
                                     env,
                                     entryContainer);
    }

    if (indexConfig.getIndexType().contains(
            LocalDBIndexCfgDefn.IndexType.ORDERING))
    {
      if (attrType.getOrderingMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
            String.valueOf(attrType), "ordering");
        throw new ConfigException(message);
      }

      Indexer orderingIndexer = new OrderingIndexer(attrType);
      this.orderingIndex = new Index(name + ".ordering",
                                     orderingIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     false,
                                     env,
                                     entryContainer);
    }
    if (indexConfig.getIndexType().contains(
        LocalDBIndexCfgDefn.IndexType.APPROXIMATE))
    {
      if (attrType.getApproximateMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
            String.valueOf(attrType), "approximate");
        throw new ConfigException(message);
      }

      Indexer approximateIndexer = new ApproximateIndexer(attrType);
      this.approximateIndex = new Index(name + ".approximate",
                                        approximateIndexer,
                                        state,
                                        indexEntryLimit,
                                        cursorEntryLimit,
                                        false,
                                        env,
                                        entryContainer);
    }

    this.indexConfig.addChangeListener(this);
  }

  /**
   * Open the attribute index.
   *
   * @throws DatabaseException if a JE database error occurs while
   * openning the index.
   */
  public void open() throws DatabaseException
  {
    if (equalityIndex != null)
    {
      equalityIndex.open();
    }

    if (presenceIndex != null)
    {
      presenceIndex.open();
    }

    if (substringIndex != null)
    {
      substringIndex.open();
    }

    if (orderingIndex != null)
    {
      orderingIndex.open();
    }

    if (approximateIndex != null)
    {
      approximateIndex.open();
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
    if (equalityIndex != null)
    {
      equalityIndex.close();
    }

    if (presenceIndex != null)
    {
      presenceIndex.close();
    }

    if (substringIndex != null)
    {
      substringIndex.close();
    }

    if (orderingIndex != null)
    {
      orderingIndex.close();
    }

    if (approximateIndex != null)
    {
      approximateIndex.close();
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
   * @throws JebException If an error occurs in the JE backend.
   */
  public boolean addEntry(IndexBuffer buffer, EntryID entryID,
                          Entry entry)
       throws DatabaseException, DirectoryException, JebException
  {
    boolean success = true;

    if (equalityIndex != null)
    {
      if(!equalityIndex.addEntry(buffer, entryID, entry))
      {
        success = false;
      }
    }

    if (presenceIndex != null)
    {
      if(!presenceIndex.addEntry(buffer, entryID, entry))
      {
        success = false;
      }
    }

    if (substringIndex != null)
    {
      if(!substringIndex.addEntry(buffer, entryID, entry))
      {
        success = false;
      }
    }

    if (orderingIndex != null)
    {
      if(!orderingIndex.addEntry(buffer, entryID, entry))
      {
        success = false;
      }
    }

    if (approximateIndex != null)
    {
      if(!approximateIndex.addEntry(buffer, entryID, entry))
      {
        success = false;
      }
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
   * @throws JebException If an error occurs in the JE backend.
   */
  public boolean addEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException, JebException
  {
    boolean success = true;

    if (equalityIndex != null)
    {
      if(!equalityIndex.addEntry(txn, entryID, entry))
      {
        success = false;
      }
    }

    if (presenceIndex != null)
    {
      if(!presenceIndex.addEntry(txn, entryID, entry))
      {
        success = false;
      }
    }

    if (substringIndex != null)
    {
      if(!substringIndex.addEntry(txn, entryID, entry))
      {
        success = false;
      }
    }

    if (orderingIndex != null)
    {
      if(!orderingIndex.addEntry(txn, entryID, entry))
      {
        success = false;
      }
    }

    if (approximateIndex != null)
    {
      if(!approximateIndex.addEntry(txn, entryID, entry))
      {
        success = false;
      }
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
   * @throws JebException If an error occurs in the JE backend.
   */
  public void removeEntry(IndexBuffer buffer, EntryID entryID,
                          Entry entry)
       throws DatabaseException, DirectoryException, JebException
  {
    if (equalityIndex != null)
    {
      equalityIndex.removeEntry(buffer, entryID, entry);
    }

    if (presenceIndex != null)
    {
      presenceIndex.removeEntry(buffer, entryID, entry);
    }

    if (substringIndex != null)
    {
      substringIndex.removeEntry(buffer, entryID, entry);
    }

    if (orderingIndex != null)
    {
      orderingIndex.removeEntry(buffer, entryID, entry);
    }

    if(approximateIndex != null)
    {
      approximateIndex.removeEntry(buffer, entryID, entry);
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
   * @throws JebException If an error occurs in the JE backend.
   */
  public void removeEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException, JebException
  {
    if (equalityIndex != null)
    {
      equalityIndex.removeEntry(txn, entryID, entry);
    }

    if (presenceIndex != null)
    {
      presenceIndex.removeEntry(txn, entryID, entry);
    }

    if (substringIndex != null)
    {
      substringIndex.removeEntry(txn, entryID, entry);
    }

    if (orderingIndex != null)
    {
      orderingIndex.removeEntry(txn, entryID, entry);
    }

    if(approximateIndex != null)
    {
      approximateIndex.removeEntry(txn, entryID, entry);
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
    if (equalityIndex != null)
    {
      equalityIndex.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
    }

    if (presenceIndex != null)
    {
      presenceIndex.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
    }

    if (substringIndex != null)
    {
      substringIndex.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
    }

    if (orderingIndex != null)
    {
      orderingIndex.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
    }

    if (approximateIndex != null)
    {
      approximateIndex.modifyEntry(txn, entryID, oldEntry, newEntry, mods);
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
    if (equalityIndex != null)
    {
      equalityIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }

    if (presenceIndex != null)
    {
      presenceIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }

    if (substringIndex != null)
    {
      substringIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }

    if (orderingIndex != null)
    {
      orderingIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
    }

    if (approximateIndex != null)
    {
      approximateIndex.modifyEntry(buffer, entryID, oldEntry, newEntry, mods);
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
  {
    // Eliminate duplicates by putting the keys into a set.
    // Sorting the keys will ensure database record locks are acquired
    // in a consistent order and help prevent transaction deadlocks between
    // concurrent writers.
    Set<ByteString> set = new HashSet<ByteString>();

    int substrLength = indexConfig.getSubstringLength();
    byte[] keyBytes;

    // Example: The value is ABCDE and the substring length is 3.
    // We produce the keys ABC BCD CDE DE E
    // To find values containing a short substring such as DE,
    // iterate through keys with prefix DE. To find values
    // containing a longer substring such as BCDE, read keys
    // BCD and CDE.
    for (int i = 0, remain = value.length; remain > 0; i++, remain--)
    {
      int len = Math.min(substrLength, remain);
      keyBytes = makeSubstringKey(value, i, len);
      set.add(new ASN1OctetString(keyBytes));
    }

    return set;
  }

  /**
   * Retrieve the entry IDs that might contain a given substring.
   * @param bytes A normalized substring of an attribute value.
   * @return The candidate entry IDs.
   */
  private EntryIDSet matchSubstring(byte[] bytes)
  {
    int substrLength = indexConfig.getSubstringLength();

    // There are two cases, depending on whether the user-provided
    // substring is smaller than the configured index substring length or not.
    if (bytes.length < substrLength)
    {
      // Iterate through all the keys that have this value as the prefix.

      // Set the lower bound for a range search.
      byte[] lower = makeSubstringKey(bytes, 0, bytes.length);

      // Set the upper bound for a range search.
      // We need a key for the upper bound that is of equal length
      // but slightly greater than the lower bound.
      byte[] upper = makeSubstringKey(bytes, 0, bytes.length);
      for (int i = upper.length-1; i >= 0; i--)
      {
        if (upper[i] == 0xFF)
        {
          // We have to carry the overflow to the more significant byte.
          upper[i] = 0;
        }
        else
        {
          // No overflow, we can stop.
          upper[i] = (byte) (upper[i] + 1);
          break;
        }
      }

      // Read the range: lower <= keys < upper.
      return substringIndex.readRange(lower, upper, true, false);
    }
    else
    {
      // Break the value up into fragments of length equal to the
      // index substring length, and read those keys.

      // Eliminate duplicates by putting the keys into a set.
      Set<byte[]> set =
          new TreeSet<byte[]>(substringIndex.indexer.getComparator());

      // Example: The value is ABCDE and the substring length is 3.
      // We produce the keys ABC BCD CDE.
      for (int first = 0, last = substrLength;
           last <= bytes.length; first++, last++)
      {
        byte[] keyBytes;
        keyBytes = makeSubstringKey(bytes, first, substrLength);
        set.add(keyBytes);
      }

      EntryIDSet results = new EntryIDSet();
      DatabaseEntry key = new DatabaseEntry();
      for (byte[] keyBytes : set)
      {
        // Read the key.
        key.setData(keyBytes);
        EntryIDSet list = substringIndex.readKey(key, null, LockMode.DEFAULT);

        // Incorporate them into the results.
        results.retainAll(list);

        // We may have reached the point of diminishing returns where
        // it is quicker to stop now and process the current small number of
        // candidates.
        if (results.isDefined() &&
             results.size() <= IndexFilter.FILTER_CANDIDATE_THRESHOLD)
        {
          break;
        }
      }

      return results;
    }
  }

  /**
   * Uses an equality index to retrieve the entry IDs that might contain a
   * given initial substring.
   * @param bytes A normalized initial substring of an attribute value.
   * @return The candidate entry IDs.
   */
  private EntryIDSet matchInitialSubstring(byte[] bytes)
  {
    // Iterate through all the keys that have this value as the prefix.

    // Set the lower bound for a range search.
    byte[] lower = bytes;

    // Set the upper bound for a range search.
    // We need a key for the upper bound that is of equal length
    // but slightly greater than the lower bound.
    byte[] upper = new byte[bytes.length];
    System.arraycopy(bytes,0, upper, 0, bytes.length);

    for (int i = upper.length-1; i >= 0; i--)
    {
      if (upper[i] == 0xFF)
      {
        // We have to carry the overflow to the more significant byte.
        upper[i] = 0;
      }
      else
      {
        // No overflow, we can stop.
        upper[i] = (byte) (upper[i] + 1);
        break;
      }
    }

    // Read the range: lower <= keys < upper.
    return equalityIndex.readRange(lower, upper, true, false);
  }

  /**
   * Retrieve the entry IDs that might match an equality filter.
   *
   * @param equalityFilter The equality filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateEqualityFilter(SearchFilter equalityFilter,
                                           StringBuilder debugBuffer)
  {
    if (equalityIndex == null)
    {
      return new EntryIDSet();
    }

    try
    {
      // Make a key from the normalized assertion value.
      byte[] keyBytes =
           equalityFilter.getAssertionValue().getNormalizedValue().value();
      DatabaseEntry key = new DatabaseEntry(keyBytes);

      if(debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        debugBuffer.append(indexConfig.getAttribute().getNameOrOID());
        debugBuffer.append(".");
        debugBuffer.append("equality]");
      }

      // Read the key.
      return equalityIndex.readKey(key, null, LockMode.DEFAULT);
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
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
   * @return The candidate entry IDs that might contain one or more
   *         values of the attribute type in the filter.
   */
  public EntryIDSet evaluatePresenceFilter(SearchFilter filter,
                                           StringBuilder debugBuffer)
  {
    if (presenceIndex == null)
    {
      return new EntryIDSet();
    }

    if(debugBuffer != null)
    {
      debugBuffer.append("[INDEX:");
      debugBuffer.append(indexConfig.getAttribute().getNameOrOID());
      debugBuffer.append(".");
      debugBuffer.append("presence]");
    }

    // Read the presence key
    return presenceIndex.readKey(presenceKey, null, LockMode.DEFAULT);
  }

  /**
   * Retrieve the entry IDs that might match a greater-or-equal filter.
   *
   * @param filter The greater-or-equal filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @return The candidate entry IDs that might contain a value
   *         greater than or equal to the filter assertion value.
   */
  public EntryIDSet evaluateGreaterOrEqualFilter(SearchFilter filter,
                                                 StringBuilder debugBuffer)
  {
    if (orderingIndex == null)
    {
      return new EntryIDSet();
    }

    try
    {
      // Set the lower bound for a range search.
      // Use the ordering matching rule to normalize the value.
      OrderingMatchingRule orderingRule =
           filter.getAttributeType().getOrderingMatchingRule();
      byte[] lower = orderingRule.normalizeValue(
           filter.getAssertionValue().getValue()).value();

      // Set the upper bound to 0 to search all keys greater then the lower
      // bound.
      byte[] upper = new byte[0];

      if(debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        debugBuffer.append(indexConfig.getAttribute().getNameOrOID());
        debugBuffer.append(".");
        debugBuffer.append("ordering]");
      }

      // Read the range: lower <= keys < upper.
      return orderingIndex.readRange(lower, upper, true, false);
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return new EntryIDSet();
    }
  }

  /**
   * Retrieve the entry IDs that might match a less-or-equal filter.
   *
   * @param filter The less-or-equal filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @return The candidate entry IDs that might contain a value
   *         less than or equal to the filter assertion value.
   */
  public EntryIDSet evaluateLessOrEqualFilter(SearchFilter filter,
                                              StringBuilder debugBuffer)
  {
    if (orderingIndex == null)
    {
      return new EntryIDSet();
    }

    try
    {
      // Set the lower bound to 0 to start the range search from the smallest
      // key.
      byte[] lower = new byte[0];

      // Set the upper bound for a range search.
      // Use the ordering matching rule to normalize the value.
      OrderingMatchingRule orderingRule =
           filter.getAttributeType().getOrderingMatchingRule();
      byte[] upper = orderingRule.normalizeValue(
           filter.getAssertionValue().getValue()).value();

      if(debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        debugBuffer.append(indexConfig.getAttribute().getNameOrOID());
        debugBuffer.append(".");
        debugBuffer.append("ordering]");
      }

      // Read the range: lower < keys <= upper.
      return orderingIndex.readRange(lower, upper, false, true);
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
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
   * @return The candidate entry IDs that might contain a value
   *         that matches the filter substrings.
   */
  public EntryIDSet evaluateSubstringFilter(SearchFilter filter,
                                            StringBuilder debugBuffer)
  {
    SubstringMatchingRule matchRule =
         filter.getAttributeType().getSubstringMatchingRule();

    try
    {
      ArrayList<ByteString> elements = new ArrayList<ByteString>();
      EntryIDSet results = new EntryIDSet();

      if (filter.getSubInitialElement() != null)
      {
        // Use the equality index for initial substrings if possible.
        if (equalityIndex != null)
        {
          ByteString normValue =
               matchRule.normalizeSubstring(filter.getSubInitialElement());
          byte[] normBytes = normValue.value();

          EntryIDSet list = matchInitialSubstring(normBytes);
          results.retainAll(list);

          if (results.isDefined() &&
               results.size() <= IndexFilter.FILTER_CANDIDATE_THRESHOLD)
          {

            if(debugBuffer != null)
            {
              debugBuffer.append("[INDEX:");
              debugBuffer.append(indexConfig.getAttribute().
                  getNameOrOID());
              debugBuffer.append(".");
              debugBuffer.append("equality]");
            }

            return results;
          }
        }
        else
        {
          elements.add(filter.getSubInitialElement());
        }
      }

      if (substringIndex == null)
      {
        return results;
      }

      // We do not distinguish between sub and final elements
      // in the substring index. Put all the elements into a single list.
      elements.addAll(filter.getSubAnyElements());
      if (filter.getSubFinalElement() != null)
      {
        elements.add(filter.getSubFinalElement());
      }

      // Iterate through each substring element.
      for (ByteString element : elements)
      {
        // Normalize the substring according to the substring matching rule.
        ByteString normValue = matchRule.normalizeSubstring(element);
        byte[] normBytes = normValue.value();

        // Get the candidate entry IDs from the index.
        EntryIDSet list = matchSubstring(normBytes);

        // Incorporate them into the results.
        results.retainAll(list);

        // We may have reached the point of diminishing returns where
        // it is quicker to stop now and process the current small number of
        // candidates.
        if (results.isDefined() &&
             results.size() <= IndexFilter.FILTER_CANDIDATE_THRESHOLD)
        {
          break;
        }
      }

      if(debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        debugBuffer.append(indexConfig.getAttribute().getNameOrOID());
        debugBuffer.append(".");
        debugBuffer.append("substring]");
      }

      return results;
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return new EntryIDSet();
    }
  }

  /**
   * Retrieve the entry IDs that might have a value greater than or
   * equal to the lower bound value, and less than or equal to the
   * upper bound value.
   *
   * @param lowerValue The lower bound value
   * @param upperValue The upper bound value
   * @return The candidate entry IDs.
   */
  public EntryIDSet evaluateBoundedRange(AttributeValue lowerValue,
                                          AttributeValue upperValue)
  {
    if (orderingIndex == null)
    {
      return new EntryIDSet();
    }

    try
    {
      // Use the ordering matching rule to normalize the values.
      OrderingMatchingRule orderingRule =
           getAttributeType().getOrderingMatchingRule();

      // Set the lower bound for a range search.
      byte[] lower = orderingRule.normalizeValue(lowerValue.getValue()).value();

      // Set the upper bound for a range search.
      byte[] upper = orderingRule.normalizeValue(upperValue.getValue()).value();

      // Read the range: lower <= keys <= upper.
      return orderingIndex.readRange(lower, upper, true, true);
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return new EntryIDSet();
    }
  }

  /**
   * The default lexicographic byte array comparator.
   * Is there one available in the Java platform?
   */
  static public class KeyComparator implements Comparator<byte[]>
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
   * Retrieve the entry IDs that might match an approximate filter.
   *
   * @param approximateFilter The approximate filter.
   * @param debugBuffer If not null, a diagnostic string will be written
   *                     which will help determine how the indexes contributed
   *                     to this search.
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateApproximateFilter(SearchFilter approximateFilter,
                                              StringBuilder debugBuffer)
  {
    if (approximateIndex == null)
    {
      return new EntryIDSet();
    }

    try
    {
      ApproximateMatchingRule approximateMatchingRule =
          approximateFilter.getAttributeType().getApproximateMatchingRule();
      // Make a key from the normalized assertion value.
      byte[] keyBytes =
           approximateMatchingRule.normalizeValue(
               approximateFilter.getAssertionValue().getValue()).value();
      DatabaseEntry key = new DatabaseEntry(keyBytes);

      if(debugBuffer != null)
      {
        debugBuffer.append("[INDEX:");
        debugBuffer.append(indexConfig.getAttribute().getNameOrOID());
        debugBuffer.append(".");
        debugBuffer.append("approximate]");
      }

      // Read the key.
      return approximateIndex.readKey(key, null, LockMode.DEFAULT);
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return new EntryIDSet();
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

    return entryLimitExceededCount;
  }

  /**
   * Get a list of the databases opened by this attribute index.
   * @param dbList A list of database containers.
   */
  public void listDatabases(List<DatabaseContainer> dbList)
  {
    if (equalityIndex != null)
    {
      dbList.add(equalityIndex);
    }

    if (presenceIndex != null)
    {
      dbList.add(presenceIndex);
    }

    if (substringIndex != null)
    {
      dbList.add(substringIndex);
    }

    if (orderingIndex != null)
    {
      dbList.add(orderingIndex);
    }

    if (approximateIndex != null)
    {
      dbList.add(approximateIndex);
    }
  }

  /**
   * Get a string representation of this object.
   * @return return A string representation of this object.
   */
  public String toString()
  {
    return getName();
  }

  /**
   * {@inheritDoc}
   */
  public synchronized boolean isConfigurationChangeAcceptable(
      LocalDBIndexCfg cfg,
      List<Message> unacceptableReasons)
  {
    AttributeType attrType = cfg.getAttribute();

    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.EQUALITY))
    {
      if (equalityIndex == null && attrType.getEqualityMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
                String.valueOf(String.valueOf(attrType)), "equality");
        unacceptableReasons.add(message);
        return false;
      }
    }

    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.SUBSTRING))
    {
      if (substringIndex == null && attrType.getSubstringMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
                String.valueOf(attrType), "substring");
        unacceptableReasons.add(message);
        return false;
      }

    }

    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.ORDERING))
    {
      if (orderingIndex == null && attrType.getOrderingMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
                String.valueOf(attrType), "ordering");
        unacceptableReasons.add(message);
        return false;
      }
    }
    if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.APPROXIMATE))
    {
      if (approximateIndex == null &&
          attrType.getApproximateMatchingRule() == null)
      {
        Message message = ERR_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE.get(
                String.valueOf(attrType), "approximate");
        unacceptableReasons.add(message);
        return false;
      }
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized ConfigChangeResult applyConfigurationChange(
      LocalDBIndexCfg cfg)
  {
    ConfigChangeResult ccr;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();
    try
    {
      AttributeType attrType = cfg.getAttribute();
      String name =
        entryContainer.getDatabasePrefix() + "_" + attrType.getNameOrOID();
      int indexEntryLimit = cfg.getIndexEntryLimit();

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.EQUALITY))
      {
        if (equalityIndex == null)
        {
          // Adding equality index
          Indexer equalityIndexer = new EqualityIndexer(attrType);
          equalityIndex = new Index(name + ".equality",
                                    equalityIndexer,
                                    state,
                                    indexEntryLimit,
                                    cursorEntryLimit,
                                    false,
                                    env,
                                    entryContainer);
          equalityIndex.open();

          adminActionRequired = true;
          messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
                  name + ".equality"));

        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.equalityIndex.setIndexEntryLimit(indexEntryLimit))
          {

            adminActionRequired = true;
            Message message =
                    NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(
                            name + ".equality");
            messages.add(message);
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
            entryContainer.deleteDatabase(equalityIndex);
            equalityIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(Message.raw(
                    StaticUtils.stackTraceToSingleLineString(de)));
            ccr = new ConfigChangeResult(
                DirectoryServer.getServerErrorResultCode(), false, messages);
            return ccr;
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
          presenceIndex = new Index(name + ".presence",
                                    presenceIndexer,
                                    state,
                                    indexEntryLimit,
                                    cursorEntryLimit,
                                    false,
                                    env,
                                    entryContainer);
          presenceIndex.open();

          adminActionRequired = true;

          messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
                  name + ".presence"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.presenceIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;

            Message message =
                    NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(
                            name + ".presence");
            messages.add(message);
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
            entryContainer.deleteDatabase(presenceIndex);
            presenceIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(Message.raw(
                    StaticUtils.stackTraceToSingleLineString(de)));
            ccr = new ConfigChangeResult(
                DirectoryServer.getServerErrorResultCode(), false, messages);
            return ccr;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      if (cfg.getIndexType().contains(LocalDBIndexCfgDefn.IndexType.SUBSTRING))
      {
        if(substringIndex == null)
        {
          Indexer substringIndexer = new SubstringIndexer(
              attrType, cfg.getSubstringLength());
          substringIndex = new Index(name + ".substring",
                                     substringIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     false,
                                     env,
                                     entryContainer);
          substringIndex.open();

          adminActionRequired = true;
          messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
                  name + ".substring"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.substringIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;
            Message message =
                    NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(
                            name + ".substring");
            messages.add(message);
          }

          if(indexConfig.getSubstringLength() !=
              cfg.getSubstringLength())
          {
            Indexer substringIndexer = new SubstringIndexer(
                attrType, cfg.getSubstringLength());
            this.substringIndex.setIndexer(substringIndexer);
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
            entryContainer.deleteDatabase(substringIndex);
            substringIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(Message.raw(
                    StaticUtils.stackTraceToSingleLineString(de)));
            ccr = new ConfigChangeResult(
                DirectoryServer.getServerErrorResultCode(), false, messages);
            return ccr;
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
          Indexer orderingIndexer = new OrderingIndexer(attrType);
          orderingIndex = new Index(name + ".ordering",
                                    orderingIndexer,
                                    state,
                                    indexEntryLimit,
                                    cursorEntryLimit,
                                    false,
                                    env,
                                    entryContainer);
          orderingIndex.open();

          adminActionRequired = true;
          messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
                  name + ".ordering"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.orderingIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;

            Message message =
                    NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(
                            name + ".ordering");
            messages.add(message);
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
            entryContainer.deleteDatabase(orderingIndex);
            orderingIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(Message.raw(
                    StaticUtils.stackTraceToSingleLineString(de)));
            ccr = new ConfigChangeResult(
                DirectoryServer.getServerErrorResultCode(), false, messages);
            return ccr;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      if (cfg.getIndexType().contains(
              LocalDBIndexCfgDefn.IndexType.APPROXIMATE))
      {
        if(approximateIndex == null)
        {
          Indexer approximateIndexer = new ApproximateIndexer(attrType);
          approximateIndex = new Index(name + ".approximate",
                                       approximateIndexer,
                                       state,
                                       indexEntryLimit,
                                       cursorEntryLimit,
                                       false,
                                       env,
                                       entryContainer);
          approximateIndex.open();

          adminActionRequired = true;

          messages.add(NOTE_JEB_INDEX_ADD_REQUIRES_REBUILD.get(
                  name + ".approximate"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.approximateIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;

            Message message =
                    NOTE_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD.get(
                            name + ".approximate");
            messages.add(message);
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
            entryContainer.deleteDatabase(approximateIndex);
            approximateIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(
                    Message.raw(StaticUtils.stackTraceToSingleLineString(de)));
            ccr = new ConfigChangeResult(
                DirectoryServer.getServerErrorResultCode(), false, messages);
            return ccr;
          }
          finally
          {
            entryContainer.exclusiveLock.unlock();
          }
        }
      }

      indexConfig = cfg;

      return new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
                                    messages);
    }
    catch(Exception e)
    {
      messages.add(Message.raw(StaticUtils.stackTraceToSingleLineString(e)));
      ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                   adminActionRequired,
                                   messages);
      return ccr;
    }
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
    if (equalityIndex != null)
    {
      equalityIndex.setTrusted(txn, trusted);
    }

    if (presenceIndex != null)
    {
      presenceIndex.setTrusted(txn, trusted);
    }

    if (substringIndex != null)
    {
      substringIndex.setTrusted(txn, trusted);
    }

    if (orderingIndex != null)
    {
      orderingIndex.setTrusted(txn, trusted);
    }

    if (approximateIndex != null)
    {
      approximateIndex.setTrusted(txn, trusted);
    }
  }

  /**
   * Set the rebuild status of this index.
   * @param rebuildRunning True if a rebuild process on this index
   *                       is running or False otherwise.
   */
  public synchronized void setRebuildStatus(boolean rebuildRunning)
  {
    if (equalityIndex != null)
    {
      equalityIndex.setRebuildStatus(rebuildRunning);
    }

    if (presenceIndex != null)
    {
      presenceIndex.setRebuildStatus(rebuildRunning);
    }

    if (substringIndex != null)
    {
      substringIndex.setRebuildStatus(rebuildRunning);
    }

    if (orderingIndex != null)
    {
      orderingIndex.setRebuildStatus(rebuildRunning);
    }

    if (approximateIndex != null)
    {
      approximateIndex.setRebuildStatus(rebuildRunning);
    }
  }

  /**
   * Get the JE database name prefix for indexes in this attribute
   * index.
   *
   * @return JE database name for this database container.
   */
  public String getName()
  {
    StringBuilder builder = new StringBuilder();
    builder.append(entryContainer.getDatabasePrefix());
    builder.append("_");
    builder.append(indexConfig.getAttribute().getNameOrOID());
    return builder.toString();
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
   * Retrieves all the indexes used by this attribute index.
   *
   * @return A collection of all indexes in use by this attribute
   * index.
   */
  public Collection<Index> getAllIndexes() {
    LinkedHashSet<Index> indexes = new LinkedHashSet<Index>();

    if (equalityIndex != null)
    {
      indexes.add(equalityIndex);
    }

    if (presenceIndex != null)
    {
      indexes.add(presenceIndex);
    }

    if (substringIndex != null)
    {
      indexes.add(substringIndex);
    }

    if (orderingIndex != null)
    {
      indexes.add(orderingIndex);
    }

    if (approximateIndex != null)
    {
      indexes.add(approximateIndex);
    }

    return indexes;
  }
}
