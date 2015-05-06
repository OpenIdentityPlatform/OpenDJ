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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.util.Reject.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Reject;

import com.forgerock.opendj.util.Iterators;

/**
 * Represents a set of Entry IDs. It can represent a set where the IDs are not defined, for example when the index entry
 * limit has been exceeded.
 */
final class EntryIDSet implements Iterable<EntryID>
{
  public static final EntryIDSetCodec CODEC_V1 = new EntryIDSetCodecV1();
  public static final EntryIDSetCodec CODEC_V2 = new EntryIDSetCodecV2();

  private static final ByteSequence NO_KEY = ByteString.valueOf("<none>");
  private static final long[] EMPTY_LONG_ARRAY = new long[0];
  private static final long[] NO_ENTRY_IDS_RANGE = new long[] { 0, 0 };

  /** Interface for EntryIDSet concrete implementations. */
  private interface EntryIDSetImplementor extends Iterable<EntryID>
  {
    long size();

    void toString(StringBuilder buffer);

    boolean isDefined();

    long[] getRange();

    long[] getIDs();

    boolean add(EntryID entryID);

    boolean remove(EntryID entryID);

    boolean contains(EntryID entryID);

    void addAll(EntryIDSet that);

    void removeAll(EntryIDSet that);

    @Override
    Iterator<EntryID> iterator();

    Iterator<EntryID> iterator(EntryID begin);
  }

  /** Define serialization contract for EntryIDSet. */
  interface EntryIDSetCodec {

    ByteString encode(EntryIDSet idSet);

    EntryIDSet decode(ByteSequence key, ByteString value);
  }

  /**
   * Concrete implements representing a set of EntryIDs, sorted in ascending order.
   */
  private static final class DefinedImpl implements EntryIDSetImplementor
  {
    /**
     * The IDs are stored here in an array in ascending order. A null array implies not defined, rather than zero IDs.
     */
    private long[] entryIDs;

    DefinedImpl(long... entryIDs)
    {
      this.entryIDs = entryIDs;
    }

    @Override
    public long size()
    {
      return entryIDs.length;
    }

    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("[COUNT:").append(size()).append("]");
    }

    @Override
    public boolean isDefined()
    {
      return true;
    }

    @Override
    public boolean add(EntryID entryID)
    {
      long id = entryID.longValue();
      if (entryIDs.length == 0)
      {
        entryIDs = new long[] { id };
      }
      else if (id > entryIDs[entryIDs.length - 1])
      {
        long[] updatedValues = Arrays.copyOf(entryIDs, entryIDs.length + 1);
        updatedValues[entryIDs.length] = id;
        entryIDs = updatedValues;
      }
      else
      {
        int pos = Arrays.binarySearch(entryIDs, id);
        if (pos >= 0)
        {
          // The ID is already present.
          return false;
        }

        // For a negative return value r, the index -(r+1) gives the array
        // index at which the specified value can be inserted to maintain
        // the sorted order of the array.
        pos = -(pos + 1);

        long[] updatedValues = new long[entryIDs.length + 1];
        System.arraycopy(entryIDs, 0, updatedValues, 0, pos);
        System.arraycopy(entryIDs, pos, updatedValues, pos + 1, entryIDs.length - pos);
        updatedValues[pos] = id;
        entryIDs = updatedValues;
      }
      return true;
    }

    @Override
    public boolean remove(EntryID entryID)
    {
      // Binary search to locate the ID.
      final int pos = Arrays.binarySearch(entryIDs, entryID.longValue());
      if (pos >= 0)
      {
        // Found it.
        final long[] updatedValues = new long[entryIDs.length - 1];
        System.arraycopy(entryIDs, 0, updatedValues, 0, pos);
        System.arraycopy(entryIDs, pos + 1, updatedValues, pos, entryIDs.length - pos - 1);
        entryIDs = updatedValues;
        return true;
      }
      // Not found.
      return false;
    }

    @Override
    public boolean contains(EntryID entryID)
    {
      return Arrays.binarySearch(entryIDs, entryID.longValue()) >= 0;
    }

    @Override
    public void addAll(EntryIDSet anotherEntryIDSet)
    {
      if (anotherEntryIDSet.size() == 0)
      {
        return;
      }

      if (entryIDs.length == 0)
      {
        entryIDs = anotherEntryIDSet.getIDs();
        return;
      }

      final int overlap = compareForOverlap(getRange(), anotherEntryIDSet.getRange());
      if (overlap < 0)
      {
        entryIDs = concatIdsFrom(entryIDs, anotherEntryIDSet.getIDs());
      }
      else if (overlap > 0)
      {
        entryIDs = concatIdsFrom(anotherEntryIDSet.getIDs(), entryIDs);
      }
      else
      {
        entryIDs = mergeOverlappingEntryIDSet(entryIDs, anotherEntryIDSet.getIDs());
      }
    }

    @Override
    public void removeAll(EntryIDSet that)
    {
      if (compareForOverlap(getRange(), that.getRange()) == 0)
      {
        // Set overlaps
        final long[] newEntryIds = new long[entryIDs.length];
        final long[] entriesToRemove = that.getIDs();

        int sourceIndex, toRemoveIndex, targetIndex;
        for (sourceIndex = 0, targetIndex = 0, toRemoveIndex = 0; sourceIndex < entryIDs.length
            && toRemoveIndex < entriesToRemove.length;)
        {
          if (entryIDs[sourceIndex] < entriesToRemove[toRemoveIndex])
          {
            newEntryIds[targetIndex++] = entryIDs[sourceIndex++];
          }
          else if (entriesToRemove[toRemoveIndex] < entryIDs[sourceIndex])
          {
            toRemoveIndex++;
          }
          else
          {
            sourceIndex++;
            toRemoveIndex++;
          }
        }

        System.arraycopy(entryIDs, sourceIndex, newEntryIds, targetIndex, entryIDs.length - sourceIndex);
        targetIndex += entryIDs.length - sourceIndex;

        if (targetIndex < entryIDs.length)
        {
          entryIDs = Arrays.copyOf(newEntryIds, targetIndex);
        }
        else
        {
          entryIDs = newEntryIds;
        }
      }
    }

    @Override
    public Iterator<EntryID> iterator()
    {
      return new IDSetIterator(entryIDs);
    }

    @Override
    public Iterator<EntryID> iterator(EntryID begin)
    {
      return new IDSetIterator(entryIDs, begin == null ? 0 : begin.longValue());
    }

    @Override
    public long[] getRange()
    {
      if (entryIDs.length != 0)
      {
        return new long[] { entryIDs[0], entryIDs[entryIDs.length - 1] };
      }
      return NO_ENTRY_IDS_RANGE;
    }

    @Override
    public long[] getIDs()
    {
      return entryIDs;
    }
  }

  /**
   * Concrete implementation the EntryIDs are not defined, for example when the index entry limit has been exceeded.
   */
  private static final class UndefinedImpl implements EntryIDSetImplementor
  {
    /** The key containing this set, if the set was constructed directly from the tree. */
    private final ByteSequence treeKey;

    UndefinedImpl(ByteSequence key)
    {
      treeKey = checkNotNull(key, "key must not be null");
    }

    @Override
    public long size()
    {
      return Long.MAX_VALUE;
    }

    @Override
    public void toString(StringBuilder buffer)
    {
      if (treeKey == NO_KEY)
      {
        buffer.append("[NOT-INDEXED]");
      }
      else
      {
        buffer.append("[LIMIT-EXCEEDED]");
      }
    }

    @Override
    public boolean isDefined()
    {
      return false;
    }

    @Override
    public boolean add(EntryID entryID)
    {
      return true;
    }

    @Override
    public boolean remove(EntryID entryID)
    {
      return true;
    }

    @Override
    public boolean contains(EntryID entryID)
    {
      return true;
    }

    @Override
    public void addAll(EntryIDSet that)
    {
    }

    @Override
    public void removeAll(EntryIDSet that)
    {
    }

    @Override
    public Iterator<EntryID> iterator()
    {
      return Iterators.emptyIterator();
    }

    @Override
    public Iterator<EntryID> iterator(EntryID begin)
    {
      return Iterators.emptyIterator();
    }

    @Override
    public long[] getRange()
    {
      return NO_ENTRY_IDS_RANGE;
    }

    @Override
    public long[] getIDs()
    {
      return EMPTY_LONG_ARRAY;
    }
  }

  /**
   * Iterator for a set of Entry IDs. It must return values in order of ID.
   */
  private static final class IDSetIterator implements Iterator<EntryID>
  {
    private final long[] entryIDSet;
    private int currentIndex;

    IDSetIterator(long[] entryIDSet)
    {
      this.entryIDSet = entryIDSet;
    }

    IDSetIterator(long[] entryIDSet, long begin)
    {
      this(entryIDSet);
      currentIndex = Math.max(0, Arrays.binarySearch(entryIDSet, begin));
    }

    @Override
    public boolean hasNext()
    {
      return currentIndex < entryIDSet.length;
    }

    @Override
    public EntryID next()
    {
      if (hasNext())
      {
        return new EntryID(entryIDSet[currentIndex++]);
      }
      throw new NoSuchElementException();
    }

    @Override
    public void remove()
    {
      throw new UnsupportedOperationException();
    }
  }

  /** Legacy EntryIDSet codec implementation. */
  private static final class EntryIDSetCodecV1 implements EntryIDSetCodec
  {
    @Override
    public ByteString encode(EntryIDSet idSet)
    {
      return ByteString.wrap(append(
          new ByteStringBuilder(getEstimatedSize(idSet)), idSet).trimToSize().getBackingArray());
    }

    @Override
    public EntryIDSet decode(ByteSequence key, ByteString value)
    {
      checkNotNull(key, "key must not be null");
      checkNotNull(value, "value must not be null");

      if (value.isEmpty())
      {
        // Entry limit has exceeded and there is no encoded undefined set size.
        return newDefinedSet();
      }
      else if ((value.byteAt(0) & 0x80) == 0x80)
      {
        // Entry limit has exceeded and there is an encoded undefined set size.
        return newUndefinedSetWithKey(key);
      }
      else
      {
        // Seems like entry limit has not been exceeded and the bytes is a list of entry IDs.
        return newDefinedSet(decodeRaw(value.asReader(), value.length() / LONG_SIZE));
      }
    }

    private static int getEstimatedSize(EntryIDSet idSet)
    {
      return idSet.isDefined() ? idSet.getIDs().length * LONG_SIZE : LONG_SIZE;
    }

    private static long[] decodeRaw(ByteSequenceReader reader, int nbEntriesToDecode)
    {
      checkNotNull(reader, "builder must not be null");
      Reject.ifFalse(nbEntriesToDecode >= 0, "nbEntriesToDecode must be >= 0");

      final long ids[] = new long[nbEntriesToDecode];
      for(int i = 0 ; i < nbEntriesToDecode ; i++) {
        ids[i] = reader.getLong();
      }
      return ids;
    }

    private static ByteStringBuilder append(ByteStringBuilder builder, EntryIDSet idSet)
    {
      checkNotNull(idSet, "idSet must not be null");
      checkNotNull(builder, "builder must not be null");

      if (idSet.isDefined())
      {
        for (long value : idSet.getIDs())
        {
          builder.append(value);
        }
        return builder;
      }
      // Set top bit.
      return builder.append((byte) 0x80);
    }
  }

  /**
   * Compacted EntryIDSet codec implementation. Idea is to take advantages of
   * org.forgerock.opendj.ldap.ByteStringBuilder#appendCompact() able to write small values of long in fewer bytes.
   * Rather than storing the full list of IDs, we store only the difference of the Nth ID with the N-1th one in the hope
   * that the result will be small enough to be compacted by appendCompact().
   */
  private static final class EntryIDSetCodecV2 implements EntryIDSetCodec
  {
    private static final byte UNDEFINED_SET = (byte) 0xFF;

    @Override
    public ByteString encode(EntryIDSet idSet)
    {
      checkNotNull(idSet, "idSet must not be null");
      ByteStringBuilder builder = new ByteStringBuilder(getEstimatedSize(idSet));
      return append(builder, idSet).toByteString();
    }

    @Override
    public EntryIDSet decode(ByteSequence key, ByteString value)
    {
      checkNotNull(key, "key must not be null");
      checkNotNull(value, "value must not be null");
      if (value.byteAt(0) == UNDEFINED_SET)
      {
        return newUndefinedSetWithKey(key);
      }
      final ByteSequenceReader reader = value.asReader();
      return newDefinedSet(decodeRaw(reader, (int) reader.getCompactUnsigned()));
    }

    private static ByteStringBuilder append(ByteStringBuilder builder, EntryIDSet idSet)
    {
      checkNotNull(idSet, "idSet must not be null");
      checkNotNull(builder, "builder must not be null");

      if (idSet.isDefined())
      {
        builder.appendCompactUnsigned(idSet.size());
        long basis = 0;
        for (long value : idSet.getIDs())
        {
          builder.appendCompactUnsigned(value - basis);
          basis = value;
        }
      }
      else
      {
        builder.append(UNDEFINED_SET);
      }
      return builder;
    }

    private static int getEstimatedSize(EntryIDSet idSet)
    {
      checkNotNull(idSet, "idSet must not be null");
      return idSet.getIDs().length * LONG_SIZE + INT_SIZE;
    }

    private static long[] decodeRaw(ByteSequenceReader reader, int nbEntriesToDecode)
    {
      checkNotNull(reader, "reader must not be null");
      Reject.ifFalse(nbEntriesToDecode >= 0, "nbEntriesToDecode must be >= 0");

      if ( nbEntriesToDecode == 0 ) {
        return EMPTY_LONG_ARRAY;
      }
      final long ids[] = new long[nbEntriesToDecode];
      ids[0] = reader.getCompactUnsigned();
      for(int i = 1 ; i < nbEntriesToDecode ; i++) {
        ids[i] = ids[i-1] + reader.getCompactUnsigned();
      }
      return ids;
    }
  }

  static EntryIDSet newUndefinedSet()
  {
    return newUndefinedSetWithKey(NO_KEY);
  }

  static EntryIDSet newUndefinedSetWithKey(ByteSequence key)
  {
    return new EntryIDSet(new UndefinedImpl(key));
  }

  /**
   * Creates a new defined entry ID set with the specified ids.
   *
   * @param ids
   *          Entry IDs contained in the set.
   * @throws NullPointerException
   *           if ids is null
   */
  static EntryIDSet newDefinedSet(long... ids)
  {
    checkNotNull(ids, "ids must not be null");
    return new EntryIDSet(new DefinedImpl(ids));
  }

  private static long[] intersection(long[] set1, long[] set2)
  {
    long[] target = new long[Math.min(set1.length, set2.length)];

    int index1, index2, ci;
    for (index1 = 0, index2 = 0, ci = 0; index1 < set1.length && index2 < set2.length;)
    {
      if (set1[index1] == set2[index2])
      {
        target[ci++] = set1[index1++];
        index2++;
      }
      else if (set1[index1] > set2[index2])
      {
        index2++;
      }
      else
      {
        index1++;
      }
    }

    if (ci < target.length)
    {
      target = Arrays.copyOf(target, ci);
    }
    return target;
  }

  /**
   * Creates a new set of entry IDs that is the union of several entry ID sets.
   *
   * @param sets
   *          A list of entry ID sets.
   * @return The union of the provided entry ID sets.
   */
  static EntryIDSet newSetFromUnion(List<EntryIDSet> sets)
  {
    checkNotNull(sets, "sets must not be null");

    int count = 0;

    boolean containsUndefinedSet = false;
    for (EntryIDSet l : sets)
    {
      if (!l.isDefined())
      {
        if (l.size() == Long.MAX_VALUE)
        {
          return newUndefinedSet();
        }
        containsUndefinedSet = true;
      }
      count += l.size();
    }

    if (containsUndefinedSet)
    {
      return newUndefinedSet();
    }

    boolean needSort = false;
    long[] n = new long[count];
    int pos = 0;
    for (EntryIDSet l : sets)
    {
      if (l.size() != 0)
      {
        needSort |= pos > 0 && l.iterator().next().longValue() < n[pos - 1];
        System.arraycopy(l.getIDs(), 0, n, pos, l.getIDs().length);
        pos += l.size();
      }
    }
    if (needSort)
    {
      Arrays.sort(n);
    }

    long[] n1 = new long[n.length];
    long last = -1;
    int j = 0;
    for (long l : n)
    {
      if (l != last)
      {
        last = n1[j++] = l;
      }
    }
    if (j == n1.length)
    {
      return newDefinedSet(n1);
    }
    return newDefinedSet(Arrays.copyOf(n1, j));
  }

  private EntryIDSetImplementor concreteImpl;

  private EntryIDSet(EntryIDSetImplementor concreteImpl)
  {
    this.concreteImpl = concreteImpl;
  }

  /**
   * Get the size of this entry ID set.
   *
   * @return The number of IDs in the set.
   */
  public long size()
  {
    return concreteImpl.size();
  }

  /**
   * Convert to a short string to aid with debugging.
   *
   * @param buffer
   *          The string is appended to this string builder.
   * @throws NullPointerException
   *           if buffer is null
   */
  public void toString(StringBuilder buffer)
  {
    checkNotNull(buffer, "buffer must not be null");
    concreteImpl.toString(buffer);
  }

  @Override
  public String toString() {
    StringBuilder builder  = new StringBuilder(16);
    toString(builder);
    return builder.toString();
  }

  /**
   * Determine whether this set of IDs is defined.
   *
   * @return true if the set of IDs is defined.
   */
  public boolean isDefined()
  {
    return concreteImpl.isDefined();
  }

  /**
   * Insert an ID into this set.
   *
   * @param entryID
   *          The ID to be inserted.
   * @return true if the set was changed, false if it was not changed, for example if the set is undefined or the ID was
   *         already present.
   * @throws NullPointerException
   *           if entryID is null
   */
  public boolean add(EntryID entryID)
  {
    checkNotNull(entryID, "entryID must not be null");
    return concreteImpl.add(entryID);
  }

  /**
   * Remove an ID from this set.
   *
   * @param entryID
   *          The ID to be removed
   * @return true if the set was changed, false if it was not changed, for example if the set was undefined or the ID
   *         was not present.
   * @throws NullPointerException
   *           if entryID is null
   */
  public boolean remove(EntryID entryID)
  {
    checkNotNull(entryID, "entryID must not be null");
    return concreteImpl.remove(entryID);
  }

  /**
   * Check whether this set of entry IDs contains a given ID.
   *
   * @param entryID
   *          The ID to be checked.
   * @return true if this set contains the given ID, or if the set is undefined.
   * @throws NullPointerException
   *           if entryID is null
   */
  public boolean contains(EntryID entryID)
  {
    checkNotNull(entryID, "entryID must not be null");
    return concreteImpl.contains(entryID);
  }

  /**
   * Add all the IDs from a given set that are not already present.
   *
   * @param that
   *          The set of IDs to be added. It MUST be defined
   * @throws NullPointerException
   *           if that is null
   * @throws IllegalArgumentException
   *           if that is undefined.
   */
  public void addAll(EntryIDSet that)
  {
    checkNotNull(that, "that must not be null");
    Reject.ifFalse(that.isDefined(), "that must be defined");
    concreteImpl.addAll(that);
  }

  /**
   * Takes the intersection of this set with another. Retain those IDs that appear in the given set.
   *
   * @param that
   *          The set of IDs that are to be retained from this object.
   * @throws NullPointerException
   *           if that is null
   */
  public void retainAll(EntryIDSet that)
  {
    checkNotNull(that, "that must not be null");
    if (!concreteImpl.isDefined())
    {
      if ( that.isDefined() ) {
        // NOTE: It's ok to share the same array instance here thanks to the copy-on-write
        // performed by the implementation.
        concreteImpl = new DefinedImpl(that.getIDs());
      } else {
        concreteImpl = new UndefinedImpl(NO_KEY);
      }
      return;
    }

    if ( !that.isDefined() ) {
      return;
    }

    final boolean thatSetOverlap = compareForOverlap(getRange(), that.getRange()) == 0;
    if (thatSetOverlap)
    {
      concreteImpl = new DefinedImpl(intersection(concreteImpl.getIDs(), that.getIDs()));
    }
    else if (size() != 0)
    {
      concreteImpl = new DefinedImpl();
    }
  }

  /**
   * Remove all IDs in this set that are in a given set.
   *
   * @param that
   *          The set of IDs to be deleted. It MUST be defined.
   * @throws NullPointerException
   *           if that is null
   * @throws IllegalArgumentException
   *           if that is undefined.
   */
  public void removeAll(EntryIDSet that)
  {
    checkNotNull(that, "that must not be null");
    Reject.ifFalse(that.isDefined(), "that must be defined");
    concreteImpl.removeAll(that);
  }

  /**
   * Creates an iterator over the set or an empty iterator if the set is not defined.
   *
   * @return An EntryID iterator.
   */
  @Override
  public Iterator<EntryID> iterator()
  {
    return concreteImpl.iterator();
  }

  /**
   * Creates an iterator over the set or an empty iterator if the set is not defined.
   *
   * @param begin
   *          The entry ID of the first entry to return in the list.
   * @return An EntryID iterator.
   */
  public Iterator<EntryID> iterator(EntryID begin)
  {
    return concreteImpl.iterator(begin);
  }

  private long[] getIDs()
  {
    return concreteImpl.getIDs();
  }

  private long[] getRange()
  {
    return concreteImpl.getRange();
  }

  static long addWithoutOverflow(long a, long b) {
    /** a and b must be > 0 */
    final long result = a + b;
    return result >= 0 ? result : Long.MAX_VALUE;
  }

  private static long[] mergeOverlappingEntryIDSet(long set1[], long set2[])
  {
    final long[] a, b;
    if (set1.length >= set2.length)
    {
      a = set1;
      b = set2;
    }
    else
    {
      a = set2;
      b = set1;
    }

    final long newEntryIDs[] = new long[a.length + b.length];
    int sourceAIndex, sourceBIndex, targetIndex;
    for (sourceAIndex = 0, sourceBIndex = 0, targetIndex = 0; sourceAIndex < a.length && sourceBIndex < b.length;)
    {
      if (a[sourceAIndex] < b[sourceBIndex])
      {
        newEntryIDs[targetIndex++] = a[sourceAIndex++];
      }
      else if (b[sourceBIndex] < a[sourceAIndex])
      {
        newEntryIDs[targetIndex++] = b[sourceBIndex++];
      }
      else
      {
        newEntryIDs[targetIndex++] = a[sourceAIndex];
        sourceAIndex++;
        sourceBIndex++;
      }
    }

    targetIndex = copyRemainder(a, newEntryIDs, sourceAIndex, targetIndex);
    targetIndex = copyRemainder(b, newEntryIDs, sourceBIndex, targetIndex);

    if (targetIndex < newEntryIDs.length)
    {
      return Arrays.copyOf(newEntryIDs, targetIndex);
    }
    return newEntryIDs;
  }

  private static int copyRemainder(long[] sourceIDSet, final long[] newEntryIDs, int offset, int remainerIndex)
  {
    final int currentRemainder = sourceIDSet.length - offset;
    if (currentRemainder > 0)
    {
      System.arraycopy(sourceIDSet, offset, newEntryIDs, remainerIndex, currentRemainder);
      return remainerIndex + currentRemainder;
    }
    return remainerIndex;
  }

  private static long[] concatIdsFrom(long[] first, long[] second)
  {
    long[] ids = new long[first.length + second.length];
    System.arraycopy(first, 0, ids, 0, first.length);
    System.arraycopy(second, 0, ids, first.length, second.length);
    return ids;
  }

  /**
   * @return -1 if o1 < o2, 0 if o1 overlap o2, +1 if o1 > o2
   */
  private static int compareForOverlap(long[] o1, long[] o2)
  {
    if (o1 == null && o2 == null)
    {
      return 0;
    }
    else if (o1 == null)
    {
      return 1;
    }
    else if (o2 == null)
    {
      return -1;
    }
    else if (o1[1] < o2[0])
    {
      return -1;
    }
    else if (o1[0] > o2[1])
    {
      return 1;
    }
    else
    {
      return 0;
    }
  }

}
