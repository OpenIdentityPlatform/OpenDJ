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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.server.backends.pluggable.CursorTransformer.*;
import static org.opends.server.backends.pluggable.DnKeyFormat.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.promise.Function;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

/**
 * This class represents the dn2id index, which has one record
 * for each entry.  The key is the normalized entry DN and the value
 * is the entry ID.
 */
@SuppressWarnings("javadoc")
class DN2ID extends AbstractTree
{
  private static final Function<ByteString, Void, DirectoryException> TO_VOID_KEY =
      new Function<ByteString, Void, DirectoryException>()
      {
        @Override
        public Void apply(ByteString value) throws DirectoryException
        {
          return null;
        }
      };

  private static final CursorTransformer.ValueTransformer<ByteString, ByteString, EntryID, Exception> TO_ENTRY_ID =
      new CursorTransformer.ValueTransformer<ByteString, ByteString, EntryID, Exception>()
      {
        @Override
        public EntryID transform(ByteString key, ByteString value) throws Exception
        {
          return new EntryID(value);
        }
      };

  private final DN baseDN;

  /**
   * Create a DN2ID instance for in a given entryContainer.
   *
   * @param treeName The name of the DN tree.
   * @param baseDN The base DN of the tree.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  DN2ID(TreeName treeName, DN baseDN) throws StorageRuntimeException
  {
    super(treeName);
    this.baseDN = baseDN;
  }

  /**
   * Adds a new record into the DN tree replacing any existing record having the same DN.
   *
   * @param txn a non null transaction
   * @param dn The entry DN, which is the key to the record.
   * @param entryID The entry ID, which is the value of the record.
   * @throws StorageRuntimeException If an error occurred while attempting to insert the new record.
   */
  void put(final WriteableTransaction txn, DN dn, final EntryID entryID) throws StorageRuntimeException
  {
    txn.put(getName(), toKey(dn), toValue(entryID));
  }

  boolean insert(final WriteableTransaction txn, DN dn, final EntryID entryID) throws StorageRuntimeException
  {
    return txn.update(getName(), toKey(dn), new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(ByteSequence oldEntryID)
      {
        if (oldEntryID != null)
        {
          // no change
          return oldEntryID;
        }
        // it did not exist before, insert the new value
        return toValue(entryID);
      }
    });
  }

  ByteString toKey(DN dn)
  {
    return dnToDNKey(dn, baseDN.size());
  }

  ByteString toValue(final EntryID entryID)
  {
    // TODO JNR do we want to use compacted longs?
    return entryID.toByteString();
  }

  /**
   * Remove a record from the DN tree.
   * @param txn a non null transaction
   * @param dn The entry DN, which is the key to the record.
   * @return true if the record was removed, false if it was not removed.
   * @throws StorageRuntimeException If an error occurred while attempting to remove
   * the record.
   */
  boolean remove(WriteableTransaction txn, DN dn) throws StorageRuntimeException
  {
    return txn.delete(getName(), toKey(dn));
  }

  /**
   * Fetch the entry ID for a given DN.
   * @param txn a non null transaction
   * @param dn The DN for which the entry ID is desired.
   * @return The entry ID, or null if the given DN is not in the DN tree.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  EntryID get(ReadableTransaction txn, DN dn) throws StorageRuntimeException
  {
    final ByteString value = txn.read(getName(), toKey(dn));
    return value != null ? new EntryID(value) : null;
  }

  Cursor<Void, EntryID> openCursor(ReadableTransaction txn, DN dn)
  {
    return transformKeysAndValues(openCursor0(txn, dn), TO_VOID_KEY, TO_ENTRY_ID);
  }

  private Cursor<ByteString, ByteString> openCursor0(ReadableTransaction txn, DN dn) {
    final Cursor<ByteString, ByteString> cursor = txn.openCursor(getName());
    cursor.positionToKey(toKey(dn));
    return cursor;
  }

  SequentialCursor<Void, EntryID> openChildrenCursor(ReadableTransaction txn, DN dn)
  {
    return new ChildrenCursor(openCursor0(txn, dn));
  }

  SequentialCursor<Void, EntryID> openSubordinatesCursor(ReadableTransaction txn, DN dn) {
    return new SubtreeCursor(openCursor0(txn, dn));
  }

  /**
   * Check if two DN have a parent-child relationship.
   *
   * @param parent
   *          The potential parent
   * @param child
   *          The potential child of parent
   * @return true if child is a direct children of parent, false otherwise.
   */
  static boolean isChild(ByteSequence parent, ByteSequence child)
  {
    if (!child.startsWith(parent))
    {
      return false;
    }
    // Immediate children should only have one RDN separator past the parent length
    int nbSeparator = 0;
    for (int i = parent.length() ; i < child.length(); i++)
    {
      if (child.byteAt(i) == DN.NORMALIZED_RDN_SEPARATOR)
      {
        nbSeparator++;
        if (nbSeparator > 1)
        {
          return false;
        }
      }
    }
    return nbSeparator == 1;
  }

  /**
   * Decorator overriding the next() behavior to iterate through children of the entry pointed by the given cursor at
   * creation.
   */
  private static final class ChildrenCursor extends SequentialCursorForwarding {
    private final ByteStringBuilder builder;
    private final ByteString limit;
    private boolean cursorOnParent;

    ChildrenCursor(Cursor<ByteString, ByteString> delegate)
    {
      super(delegate);
      builder = new ByteStringBuilder(128);
      limit = delegate.isDefined() ? afterKey(delegate.getKey()).toByteString() : null;
      cursorOnParent = true;
    }

    @Override
    public boolean next()
    {
      if (cursorOnParent) {
        // Go to the first children
        delegate.next();
        cursorOnParent = false;
      } else {
        // Go to the next sibling
        delegate.positionToKeyOrNext(nextSibling());
      }
      return isDefined() && delegate.getKey().compareTo(limit) < 0;
    }

    private ByteStringBuilder nextSibling()
    {
      return builder.clear().append(delegate.getKey()).append((byte) 0x1);
    }
  }

  /**
   * Decorator overriding the next() behavior to iterate through subordinates of the entry pointed by the given cursor
   * at creation.
   */
  private static final class SubtreeCursor extends SequentialCursorForwarding {
    private final ByteString limit;

    SubtreeCursor(Cursor<ByteString, ByteString> delegate)
    {
      super(delegate);
      limit = delegate.isDefined() ? afterKey(delegate.getKey()).toByteString() : null;
    }

    @Override
    public boolean next()
    {
      return delegate.next() && delegate.getKey().compareTo(limit) < 0;
    }
  }

  /**
   * Decorator allowing to partially overrides methods of a given cursor while keeping the default behavior for other
   * methods.
   */
  private static class SequentialCursorForwarding implements SequentialCursor<Void, EntryID> {
    final Cursor<ByteString, ByteString> delegate;

    SequentialCursorForwarding(Cursor<ByteString, ByteString> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean isDefined()
    {
      return delegate.isDefined();
    }

    @Override
    public boolean next()
    {
      return delegate.next();
    }

    @Override
    public Void getKey()
    {
      return null;
    }

    @Override
    public EntryID getValue()
    {
      return new EntryID(delegate.getValue());
    }

    @Override
    public void close()
    {
      delegate.close();
    }
  }
}
