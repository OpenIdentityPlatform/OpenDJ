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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.util.Reject.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;

import java.util.Iterator;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Reject;
import org.opends.server.backends.pluggable.EntryIDSet.EntryIDSetCodec;

/**
 * This class manages the set of ID that are to be eventually added to an index
 * database. It is responsible for determining if the number of IDs is above
 * the configured ID limit. If the limit it reached, the class stops tracking
 * individual IDs and marks the set as undefined. This class is not thread safe.
 */
final class ImportIDSet implements Iterable<EntryID> {

  /** The encapsulated entryIDSet where elements are stored until reaching the limit. */
  private EntryIDSet entryIDSet;
  /** Key related to an ID set. */
  private final ByteSequence key;
  /** The index entry limit size. */
  private final int indexEntryLimitSize;
  /** Set to true if a count of ids above the index entry limit should be kept. */
  private final boolean maintainCount;

  /**
   * Create an import ID set managing the entry limit of the provided EntryIDSet.
   *
   * @param key The key associated to this ID set
   * @param entryIDSet The entryIDSet that will be managed by this object
   * @param limit The index entry limit or 0 if unlimited.
   * @param maintainCount whether to maintain the count when size is undefined.
   * @throws NullPointerException if key or entryIDSet is null
   * @throws IllegalArgumentException if limit is < 0
   */
  public ImportIDSet(ByteSequence key, EntryIDSet entryIDSet, int limit, boolean maintainCount)
  {
    checkNotNull(key, "key must not be null");
    checkNotNull(entryIDSet, "entryIDSet must not be null");
    ifFalse(limit >= 0, "limit must be >= 0");

    this.key = key;
    this.entryIDSet = entryIDSet;
    // FIXME: What to do if entryIDSet.size()> limit yet ?
    this.indexEntryLimitSize = limit == 0 ? Integer.MAX_VALUE : limit;
    this.maintainCount = maintainCount;
  }

  /**
   * @return <CODE>True</CODE> if an import ID set is defined.
   */
  boolean isDefined()
  {
    return entryIDSet.isDefined();
  }

  private void setUndefined() {
    entryIDSet = newUndefinedSetWithKey(key);
  }

  /**
   * @param entryID The entry ID to add to an import ID set.
   * @throws NullPointerException if entryID is null
   */
  void addEntryID(EntryID entryID) {
    addEntryID(entryID.longValue());
  }

  /**
   * @param entryID The {@link EntryID} to add to an import ID set.
   */
  void addEntryID(long entryID)
  {
    Reject.ifTrue(entryID < 0);
    if (isDefined() && size() + 1 > indexEntryLimitSize) {
      entryIDSet = maintainCount ? newUndefinedSetWithSize(key, size() + 1) : newUndefinedSetWithKey(key);
    } else if (isDefined() || maintainCount) {
      entryIDSet.add(new EntryID(entryID));
    }
  }

  /**
   * @param importIdSet The import ID set to delete.
   * @throws NullPointerException if importIdSet is null
   */
  void remove(ImportIDSet importIdSet)
  {
    checkNotNull(importIdSet, "importIdSet must not be null");

    if (!importIdSet.isDefined()) {
      setUndefined();
    } else if (isDefined() || maintainCount) {
      entryIDSet.removeAll(importIdSet.entryIDSet);
    }
  }

  /**
   * @param importIdSet The import ID set to merge the byte array with.
   * @return <CODE>true</CODE> if the import ID set reached the limit as a result of the merge.
   * @throws NullPointerException if importIdSet is null
   */
  boolean merge(ImportIDSet importIdSet)
  {
    checkNotNull(importIdSet, "importIdSet must not be null");

    boolean definedBeforeMerge = isDefined();
    final long mergedSize = addWithoutOverflow(entryIDSet.size(), importIdSet.entryIDSet.size());

    if (!definedBeforeMerge || !importIdSet.isDefined() || mergedSize > indexEntryLimitSize)
    {
      entryIDSet = maintainCount ? newUndefinedSetWithSize(key, mergedSize) : newUndefinedSetWithKey(key);
      return definedBeforeMerge;
    }
    else if (isDefined() || maintainCount)
    {
      entryIDSet.addAll(importIdSet.entryIDSet);
    }
    return false;
  }

  private static long addWithoutOverflow(long a, long b) {
    /** a and b must be > 0 */
    final boolean willAdditionOverflow = (~(a ^ b) & (a ^ (a + b))) < 0;
    if (willAdditionOverflow) {
      return Long.MAX_VALUE;
    }
    return a + b;
  }


  /**
   * @return The current size of an import ID set.
   * @throws IllegalStateException if this set is undefined
   */
  int size()
  {
    if (!isDefined()) {
      throw new IllegalStateException("This ImportIDSet is undefined");
    }
    return (int) entryIDSet.size();
  }

  /**
   * @return  The byte string containing the DB key related to this set.
   */
  ByteSequence getKey()
  {
    return key;
  }

  @Override
  public Iterator<EntryID> iterator() {
      return entryIDSet.iterator();
  }

  /**
   * @return Binary representation of this ID set
   */
  ByteString valueToByteString(EntryIDSetCodec codec) {
    checkNotNull(codec, "codec must not be null");
    return codec.encode(entryIDSet);
  }

  @Override
  public String toString()
  {
    return entryIDSet.toString();
  }
}
