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
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.util.Reject.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.util.StaticUtils;

/**
 * This class is responsible for storing the configuration state of
 * the backend for a particular suffix.
 */
class State extends AbstractTree
{

  /**
   * Use COMPACTED serialization for new indexes.
   * @see {@link EntryIDSet.EntryIDSetCompactCodec}
   */
  private static final Collection<IndexFlag> DEFAULT_FLAGS = Collections.unmodifiableCollection(Arrays
      .asList(IndexFlag.COMPACTED));

  /**
   * Bit-field containing possible flags that an index can have
   * When adding flags, ensure that its value fits on a single bit.
   */
  static enum IndexFlag
  {
    TRUSTED(0x01),

    /**
     * Use compact encoding for indexes' ID storage.
     */
    COMPACTED(0x02);

    static final EnumSet<IndexFlag> ALL_FLAGS = EnumSet.allOf(IndexFlag.class);

    final byte mask;

    IndexFlag(int mask) {
      this.mask=(byte) mask;
    }
  }

  /**
   * Create a new State object.
   *
   * @param name The name of the entry tree.
   */
  State(TreeName name)
  {
    super(name);
  }

  private static ByteString keyForIndex(TreeName indexTreeName) throws StorageRuntimeException
  {
    return ByteString.wrap(StaticUtils.getBytes(indexTreeName.toString()));
  }

  /**
   * Fetch index flags from the tree.
   * @param txn The transaction or null if none.
   * @param indexTreeName The tree's name of the index
   * @return The flags of the index in the tree or an empty set if no index has no flags.
   * @throws NullPointerException if tnx or index is null
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  EnumSet<IndexFlag> getIndexFlags(ReadableTransaction txn, TreeName indexTreeName) throws StorageRuntimeException {
    checkNotNull(txn, "txn must not be null");
    checkNotNull(indexTreeName, "indexTreeName must not be null");

    final ByteString value = txn.read(getName(), keyForIndex(indexTreeName));
    return decodeFlagsOrGetDefault(value);
  }

  /**
   * Ensure that the specified flags are set for the given index
   * @param txn a non null transaction
   * @param index The index storing the trusted state info.
   * @return true if the flags have been updated
   * @throws NullPointerException if txn, index or flags is null
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  boolean addFlagsToIndex(WriteableTransaction txn, TreeName indexTreeName, final IndexFlag... flags)
  {
    checkNotNull(txn, "txn must not be null");
    checkNotNull(indexTreeName, "indexTreeName must not be null");
    checkNotNull(flags, "flags must not be null");

    return txn.update(getName(), keyForIndex(indexTreeName), new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(ByteSequence oldValue)
      {
        final EnumSet<IndexFlag> currentFlags = decodeFlagsOrGetDefault(oldValue);
        currentFlags.addAll(Arrays.asList(flags));
        return encodeFlags(currentFlags);
      }
    });
  }

  private static EnumSet<IndexFlag> decodeFlagsOrGetDefault(ByteSequence sequence) {
    if ( sequence == null ) {
      return EnumSet.copyOf(DEFAULT_FLAGS);
    }
    final EnumSet<IndexFlag> indexState = EnumSet.noneOf(IndexFlag.class);
    final byte indexValue = sequence.byteAt(0);
    for (IndexFlag state : IndexFlag.ALL_FLAGS)
    {
      if ((indexValue & state.mask) == state.mask)
      {
        indexState.add(state);
      }
    }
    return indexState;
  }

  private static ByteString encodeFlags(EnumSet<IndexFlag> flags) {
    byte value = 0;
    for(IndexFlag flag : flags) {
      value |= flag.mask;
    }
    return ByteString.valueOf(new byte[] { value });
  }


  /**
   * Ensure that the specified flags are not set for the given index
   * @param txn a non null transaction
   * @param index The index storing the trusted state info.
   * @throws NullPointerException if txn, index or flags is null
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  void removeFlagsFromIndex(WriteableTransaction txn, TreeName indexTreeName, final IndexFlag... flags) {
    checkNotNull(txn, "txn must not be null");
    checkNotNull(indexTreeName, "indexTreeName must not be null");
    checkNotNull(flags, "flags must not be null");

    txn.update(getName(), keyForIndex(indexTreeName), new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(ByteSequence oldValue)
      {
        final EnumSet<IndexFlag> currentFlags = decodeFlagsOrGetDefault(oldValue);
        currentFlags.removeAll(Arrays.asList(flags));
        return encodeFlags(currentFlags);
      }
    });
  }

  /**
   * Remove a record from the entry tree.
   *
   * @param txn a non null transaction
   * @param index The index storing the trusted state info.
   * @return true if the entry was removed, false if it was not.
   * @throws NullPointerException if txn, index is null
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  boolean deleteRecord(WriteableTransaction txn, TreeName indexTreeName) throws StorageRuntimeException
  {
    checkNotNull(txn, "txn must not be null");
    checkNotNull(indexTreeName, "indexTreeName must not be null");

    return txn.delete(getName(), keyForIndex(indexTreeName));
  }
}
