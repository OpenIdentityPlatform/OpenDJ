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

import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;

import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;

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
{


  /**
   * A database key for the presence index.
   */
  public static final DatabaseEntry presenceKey =
       new DatabaseEntry("+".getBytes());

  /**
   * The entryContainer in which this attribute index resides.
   */
  EntryContainer entryContainer;

  /**
   * The attribute index configuration.
   */
  IndexConfig indexConfig;

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
   * Create a new attribute index object.
   * @param entryContainer The entryContainer of this attribute index.
   * @param indexConfig The attribute index configuration.
   */
  public AttributeIndex(EntryContainer entryContainer, IndexConfig indexConfig)
  {
    this.entryContainer = entryContainer;
    this.indexConfig = indexConfig;

    AttributeType attrType = indexConfig.getAttributeType();
    String name = attrType.getNameOrOID();

    if (indexConfig.isEqualityIndex())
    {
      Indexer equalityIndexer = new EqualityIndexer(indexConfig);
      this.equalityIndex = new Index(this.entryContainer, name + ".equality",
                                     equalityIndexer,
                                     indexConfig.getEqualityEntryLimit(),
                                     indexConfig.getCursorEntryLimit());
    }

    if (indexConfig.isPresenceIndex())
    {
      Indexer presenceIndexer = new PresenceIndexer(indexConfig);
      this.presenceIndex = new Index(this.entryContainer, name + ".presence",
                                     presenceIndexer,
                                     indexConfig.getPresenceEntryLimit(),
                                     indexConfig.getCursorEntryLimit());
    }

    if (indexConfig.isSubstringIndex())
    {
      Indexer substringIndexer = new SubstringIndexer(indexConfig);
      this.substringIndex = new Index(this.entryContainer, name + ".substring",
                                     substringIndexer,
                                     indexConfig.getSubstringEntryLimit(),
                                     indexConfig.getCursorEntryLimit());
    }

    if (indexConfig.isOrderingIndex())
    {
      Indexer orderingIndexer = new OrderingIndexer(indexConfig);
      this.orderingIndex = new Index(this.entryContainer, name + ".ordering",
                                     orderingIndexer,
                                     indexConfig.getEqualityEntryLimit(),
                                     indexConfig.getCursorEntryLimit());
    }
  }

  /**
   * Open the attribute index.
   *
   * @param dbConfig The JE Database Config that will be used to open
   *                 underlying JE databases.
   *
   * @throws DatabaseException If an error occurs opening the underlying
   *                           databases.
   */
  public void open(DatabaseConfig dbConfig) throws DatabaseException
  {
    if (equalityIndex != null)
    {
      equalityIndex.open(dbConfig);
    }

    if (presenceIndex != null)
    {
      presenceIndex.open(dbConfig);
    }

    if (substringIndex != null)
    {
      substringIndex.open(dbConfig);
    }

    if (orderingIndex != null)
    {
      orderingIndex.open(dbConfig);
    }
  }

  /**
   * Close the attribute index.
   */
  public void close()
  {
    // The entryContainer is responsible for closing the JE databases.
  }

  /**
   * Get the attribute type of this attribute index.
   * @return The attribute type of this attribute index.
   */
  public AttributeType getAttributeType()
  {
    return indexConfig.getAttributeType();
  }

  /**
   * Update the attribute index for a new entry.
   *
   * @param txn         The database transaction to be used for the insertions.
   * @param entryID     The entry ID.
   * @param entry       The contents of the new entry.
   * @throws DatabaseException If an error occurs in the JE database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws JebException If an error occurs in the JE backend.
   */
  public void addEntry(Transaction txn, EntryID entryID, Entry entry)
       throws DatabaseException, DirectoryException, JebException
  {
    if (equalityIndex != null)
    {
      equalityIndex.addEntry(txn, entryID, entry);
    }

    if (presenceIndex != null)
    {
      presenceIndex.addEntry(txn, entryID, entry);
    }

    if (substringIndex != null)
    {
      substringIndex.addEntry(txn, entryID, entry);
    }

    if (orderingIndex != null)
    {
      orderingIndex.addEntry(txn, entryID, entry);
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
    if (!indexConfig.isEqualityIndex())
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
        debugCought(DebugLogLevel.ERROR, e);
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
    if (!indexConfig.isPresenceIndex())
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
    if (!indexConfig.isOrderingIndex() || orderingIndex == null)
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
        debugCought(DebugLogLevel.ERROR, e);
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
    if (!indexConfig.isOrderingIndex() || orderingIndex == null)
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
        debugCought(DebugLogLevel.ERROR, e);
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
        if (indexConfig.isEqualityIndex())
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

      if (!indexConfig.isSubstringIndex())
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
        debugCought(DebugLogLevel.ERROR, e);
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
        debugCought(DebugLogLevel.ERROR, e);
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
   * Remove the index from disk. The index must not be open.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void removeIndex() throws DatabaseException
  {
    AttributeType attrType = indexConfig.getAttributeType();
    String name = attrType.getNameOrOID();
    if (indexConfig.isEqualityIndex())
    {
      entryContainer.removeDatabase(name + ".equality");
    }
    if (indexConfig.isPresenceIndex())
    {
      entryContainer.removeDatabase(name + ".presence");
    }
    if (indexConfig.isSubstringIndex())
    {
      entryContainer.removeDatabase(name + ".substring");
    }
    if (indexConfig.isOrderingIndex())
    {
      entryContainer.removeDatabase(name + ".ordering");
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

    return entryLimitExceededCount;
  }
}
