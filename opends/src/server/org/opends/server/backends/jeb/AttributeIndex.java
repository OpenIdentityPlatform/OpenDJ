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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sleepycat.je.*;

import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.admin.std.server.JEIndexCfg;
import org.opends.server.admin.std.meta.JEIndexCfgDefn;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.config.ConfigException;
import static org.opends.server.messages.JebMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
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
    implements ConfigurationChangeListener<JEIndexCfg>
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
  private JEIndexCfg indexConfig;

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

  private int backendIndexEntryLimit = 4000;

  /**
   * Create a new attribute index object.
   * @param entryContainer The entryContainer of this attribute index.
   * @param state The state database to persist index state info.
   * @param env The JE environment handle.
   * @param indexConfig The attribute index configuration.
   * @param backendIndexEntryLimit The backend index entry limit to use
   *        if none is specified for this attribute index.
   * @throws DatabaseException if a JE database error occurs.
   * @throws ConfigException if a configuration related error occurs.
   */
  public AttributeIndex(JEIndexCfg indexConfig, State state,
                        int backendIndexEntryLimit,
                        Environment env,
                        EntryContainer entryContainer)
      throws DatabaseException, ConfigException
  {
    this.entryContainer = entryContainer;
    this.env = env;
    this.indexConfig = indexConfig;
    this.backendIndexEntryLimit = backendIndexEntryLimit;
    this.state = state;

    AttributeType attrType = indexConfig.getIndexAttribute();
    String name = attrType.getNameOrOID();
    int indexEntryLimit = backendIndexEntryLimit;

    if(indexConfig.getIndexEntryLimit() != null)
    {
      indexEntryLimit = indexConfig.getIndexEntryLimit();
    }

    if (indexConfig.getIndexType().contains(JEIndexCfgDefn.IndexType.EQUALITY))
    {
      if (attrType.getEqualityMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "equality");
        throw new ConfigException(messageID, message);
      }

      Indexer equalityIndexer = new EqualityIndexer(attrType);
      this.equalityIndex = new Index(name + ".equality",
                                     equalityIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     env,
                                     entryContainer);
    }

    if (indexConfig.getIndexType().contains(JEIndexCfgDefn.IndexType.PRESENCE))
    {
      Indexer presenceIndexer = new PresenceIndexer(attrType);
      this.presenceIndex = new Index(name + ".presence",
                                     presenceIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     env,
                                     entryContainer);
    }

    if (indexConfig.getIndexType().contains(JEIndexCfgDefn.IndexType.SUBSTRING))
    {
      if (attrType.getSubstringMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "substring");
        throw new ConfigException(messageID, message);
      }

      Indexer substringIndexer = new SubstringIndexer(attrType,
                                         indexConfig.getIndexSubstringLength());
      this.substringIndex = new Index(name + ".substring",
                                     substringIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     env,
                                     entryContainer);
    }

    if (indexConfig.getIndexType().contains(JEIndexCfgDefn.IndexType.ORDERING))
    {
      if (attrType.getOrderingMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "ordering");
        throw new ConfigException(messageID, message);
      }

      Indexer orderingIndexer = new OrderingIndexer(attrType);
      this.orderingIndex = new Index(name + ".ordering",
                                     orderingIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     env,
                                     entryContainer);
    }
    if (indexConfig.getIndexType().contains(
        JEIndexCfgDefn.IndexType.APPROXIMATE))
    {
      if (attrType.getApproximateMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "approximate");
        throw new ConfigException(messageID, message);
      }

      Indexer approximateIndexer = new ApproximateIndexer(attrType);
      this.approximateIndex = new Index(name + ".approximate",
                                        approximateIndexer,
                                        state,
                                        indexEntryLimit,
                                        cursorEntryLimit,
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
   * openning the index.
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
    return indexConfig.getIndexAttribute();
  }

  /**
   * Get the JE index configuration used by this index.
   * @return The configuration in effect.
   */
  public JEIndexCfg getConfiguration()
  {
    return indexConfig;
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

    int substrLength = indexConfig.getIndexSubstringLength();
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
    int substrLength = indexConfig.getIndexSubstringLength();

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
      Set<byte[]> set = new HashSet<byte[]>();

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
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateEqualityFilter(SearchFilter equalityFilter)
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
   * @return The candidate entry IDs that might contain one or more
   *         values of the attribute type in the filter.
   */
  public EntryIDSet evaluatePresenceFilter(SearchFilter filter)
  {
    if (presenceIndex == null)
    {
      return new EntryIDSet();
    }

    // Read the presence key
    return presenceIndex.readKey(presenceKey, null, LockMode.DEFAULT);
  }

  /**
   * Retrieve the entry IDs that might match a greater-or-equal filter.
   *
   * @param filter The greater-or-equal filter.
   * @return The candidate entry IDs that might contain a value
   *         greater than or equal to the filter assertion value.
   */
  public EntryIDSet evaluateGreaterOrEqualFilter(SearchFilter filter)
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
   * @return The candidate entry IDs that might contain a value
   *         less than or equal to the filter assertion value.
   */
  public EntryIDSet evaluateLessOrEqualFilter(SearchFilter filter)
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
   * @return The candidate entry IDs that might contain a value
   *         that matches the filter substrings.
   */
  public EntryIDSet evaluateSubstringFilter(SearchFilter filter)
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
   * @return The candidate entry IDs that might contain the filter
   *         assertion value.
   */
  public EntryIDSet evaluateApproximateFilter(SearchFilter approximateFilter)
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
   * Set the index entry limit used by the backend using this attribute index.
   * This index will use the backend entry limit only if there is not one
   * specified for this index.
   *
   * @param backendIndexEntryLimit The backend index entry limit.
   * @return True if a rebuild is required or false otherwise.
   */
  public synchronized boolean setBackendIndexEntryLimit(
      int backendIndexEntryLimit)
  {
    // Only update if there is no limit specified for this index.
    boolean rebuildRequired = false;
    if(indexConfig.getIndexEntryLimit() == null)
    {
      if(equalityIndex != null)
      {
        rebuildRequired |=
            equalityIndex.setIndexEntryLimit(backendIndexEntryLimit);
      }

      if(presenceIndex != null)
      {
        rebuildRequired |=
            presenceIndex.setIndexEntryLimit(backendIndexEntryLimit);
      }

      if(substringIndex != null)
      {
        rebuildRequired |=
            substringIndex.setIndexEntryLimit(backendIndexEntryLimit);
      }

      if(orderingIndex != null)
      {
        rebuildRequired |=
            orderingIndex.setIndexEntryLimit(backendIndexEntryLimit);
      }

      if(approximateIndex != null)
      {
        rebuildRequired |=
            approximateIndex.setIndexEntryLimit(backendIndexEntryLimit);
      }
    }

    this.backendIndexEntryLimit = backendIndexEntryLimit;

    return rebuildRequired;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized boolean isConfigurationChangeAcceptable(
      JEIndexCfg cfg,
      List<String> unacceptableReasons)
  {
    AttributeType attrType = cfg.getIndexAttribute();

    if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.EQUALITY))
    {
      if (equalityIndex == null && attrType.getEqualityMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "equality");
        unacceptableReasons.add(message);
        return false;
      }
    }

    if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.SUBSTRING))
    {
      if (substringIndex == null && attrType.getSubstringMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "substring");
        unacceptableReasons.add(message);
        return false;
      }

    }

    if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.ORDERING))
    {
      if (orderingIndex == null && attrType.getOrderingMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "ordering");
        unacceptableReasons.add(message);
        return false;
      }
    }
    if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.APPROXIMATE))
    {
      if (approximateIndex == null &&
          attrType.getApproximateMatchingRule() == null)
      {
        int messageID = MSGID_CONFIG_INDEX_TYPE_NEEDS_MATCHING_RULE;
        String message = getMessage(messageID, attrType, "approximate");
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
      JEIndexCfg cfg)
  {
    ConfigChangeResult ccr;
    boolean adminActionRequired = false;
    ArrayList<String> messages = new ArrayList<String>();
    try
    {
      AttributeType attrType = cfg.getIndexAttribute();
      String name = attrType.getNameOrOID();
      int indexEntryLimit = backendIndexEntryLimit;

      if(cfg.getIndexEntryLimit() != null)
      {
        indexEntryLimit = cfg.getIndexEntryLimit();
      }

      if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.EQUALITY))
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
                                    env,
                                    entryContainer);
          equalityIndex.open();

          adminActionRequired = true;
          int msgID = MSGID_JEB_INDEX_ADD_REQUIRES_REBUILD;
          messages.add(getMessage(msgID, name + ".equality"));

        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.equalityIndex.setIndexEntryLimit(indexEntryLimit))
          {

            adminActionRequired = true;
            int msgID = MSGID_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD;
            String message = getMessage(msgID, name + ".equality");
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
            entryContainer.removeDatabase(equalityIndex);
            equalityIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(StaticUtils.stackTraceToSingleLineString(de));
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

      if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.PRESENCE))
      {
        if(presenceIndex == null)
        {
          Indexer presenceIndexer = new PresenceIndexer(attrType);
          presenceIndex = new Index(name + ".presence",
                                    presenceIndexer,
                                    state,
                                    indexEntryLimit,
                                    cursorEntryLimit,
                                    env,
                                    entryContainer);
          presenceIndex.open();

          adminActionRequired = true;
          int msgID = MSGID_JEB_INDEX_ADD_REQUIRES_REBUILD;
          messages.add(getMessage(msgID, name + ".presence"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.presenceIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;
            int msgID = MSGID_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD;
            String message = getMessage(msgID, name + ".presence");
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
            entryContainer.removeDatabase(presenceIndex);
            presenceIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(StaticUtils.stackTraceToSingleLineString(de));
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

      if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.SUBSTRING))
      {
        if(substringIndex == null)
        {
          Indexer substringIndexer = new SubstringIndexer(
              attrType, cfg.getIndexSubstringLength());
          substringIndex = new Index(name + ".substring",
                                     substringIndexer,
                                     state,
                                     indexEntryLimit,
                                     cursorEntryLimit,
                                     env,
                                     entryContainer);
          substringIndex.open();

          adminActionRequired = true;
          int msgID = MSGID_JEB_INDEX_ADD_REQUIRES_REBUILD;
          messages.add(getMessage(msgID, name + ".substring"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.substringIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;
            int msgID = MSGID_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD;
            String message = getMessage(msgID, name + ".substring");
            messages.add(message);
          }

          if(indexConfig.getIndexSubstringLength() !=
              cfg.getIndexSubstringLength())
          {
            Indexer substringIndexer = new SubstringIndexer(
                attrType, cfg.getIndexSubstringLength());
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
            entryContainer.removeDatabase(substringIndex);
            substringIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(StaticUtils.stackTraceToSingleLineString(de));
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

      if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.ORDERING))
      {
        if(orderingIndex == null)
        {
          Indexer orderingIndexer = new OrderingIndexer(attrType);
          orderingIndex = new Index(name + ".ordering",
                                    orderingIndexer,
                                    state,
                                    indexEntryLimit,
                                    cursorEntryLimit,
                                    env,
                                    entryContainer);
          orderingIndex.open();

          adminActionRequired = true;
          int msgID = MSGID_JEB_INDEX_ADD_REQUIRES_REBUILD;
          messages.add(getMessage(msgID, name + ".ordering"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.orderingIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;
            int msgID = MSGID_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD;
            String message = getMessage(msgID, name + ".ordering");
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
            entryContainer.removeDatabase(orderingIndex);
            orderingIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(StaticUtils.stackTraceToSingleLineString(de));
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

      if (cfg.getIndexType().contains(JEIndexCfgDefn.IndexType.APPROXIMATE))
      {
        if(approximateIndex == null)
        {
          Indexer approximateIndexer = new ApproximateIndexer(attrType);
          approximateIndex = new Index(name + ".approximate",
                                       approximateIndexer,
                                       state,
                                       indexEntryLimit,
                                       cursorEntryLimit,
                                       env,
                                       entryContainer);
          approximateIndex.open();

          adminActionRequired = true;
          int msgID = MSGID_JEB_INDEX_ADD_REQUIRES_REBUILD;
          messages.add(getMessage(msgID, name + ".approximate"));
        }
        else
        {
          // already exists. Just update index entry limit.
          if(this.approximateIndex.setIndexEntryLimit(indexEntryLimit))
          {
            adminActionRequired = true;
            int msgID = MSGID_JEB_CONFIG_INDEX_ENTRY_LIMIT_REQUIRES_REBUILD;
            String message = getMessage(msgID, name + ".approximate");
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
            entryContainer.removeDatabase(approximateIndex);
            approximateIndex = null;
          }
          catch(DatabaseException de)
          {
            messages.add(StaticUtils.stackTraceToSingleLineString(de));
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
      messages.add(StaticUtils.stackTraceToSingleLineString(e));
      ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                   adminActionRequired,
                                   messages);
      return ccr;
    }
  }

  /**
   * Set the index trust state.
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
    builder.append(entryContainer.getContainerName());
    builder.append("_");
    builder.append(indexConfig.getIndexAttribute().getNameOrOID());
    return builder.toString();
  }
}
