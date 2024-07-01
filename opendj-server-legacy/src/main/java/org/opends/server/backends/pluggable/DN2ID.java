/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.server.backends.pluggable.CursorTransformer.*;
import static org.opends.server.backends.pluggable.DnKeyFormat.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Functions;
import org.forgerock.util.Function;
import org.forgerock.util.Pair;
import org.forgerock.util.promise.NeverThrowsException;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.SequentialCursorDecorator;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Operation;

/**
 * This class represents the dn2id index, which has one record
 * for each entry.  The key is the normalized entry DN and the value
 * is the entry ID.
 */
@SuppressWarnings("javadoc")
class DN2ID extends AbstractTree
{
  private static final Function<ByteString, Void, NeverThrowsException> TO_VOID_KEY = Functions.returns(null);

  private static final CursorTransformer.ValueTransformer<ByteString, ByteString, EntryID, NeverThrowsException>
     TO_ENTRY_ID =
          new CursorTransformer.ValueTransformer<ByteString, ByteString, EntryID, NeverThrowsException>()
          {
            @Override
            public EntryID transform(ByteString key, ByteString value)
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

  private ByteString toKey(DN dn)
  {
    return dnToDNKey(dn, baseDN.size());
  }

  private ByteString toValue(final EntryID entryID)
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

  <V> SequentialCursor<ByteString, ByteString> openCursor(SequentialCursor<ByteString, ByteString> dn2IdCursor,
      TreeVisitor<V> treeVisitor)
  {
    return new TreeVisitorCursor<>(dn2IdCursor, treeVisitor);
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
    return transformKeysAndValues(new ChildrenCursor(openCursor0(txn, dn)), TO_VOID_KEY, TO_ENTRY_ID);
  }

  SequentialCursor<Void, EntryID> openSubordinatesCursor(ReadableTransaction txn, DN dn) {
    return transformKeysAndValues(new SubtreeCursor(openCursor0(txn, dn)), TO_VOID_KEY, TO_ENTRY_ID);
  }

  List<Pair<Long, Long>> renameSubtree(WriteableTransaction txn,
                                       DN oldName,
                                       DN newName,
                                       RootContainer rootContainer,
                                       boolean renumberEntryIDs,
                                       Operation operation)
          throws CanceledOperationException
  {
    try (SequentialCursor<ByteString, ByteString> cursor = new SubtreeCursor(openCursor0(txn, oldName)))
    {
      List<Pair<Long, Long>> renamedEntryIDs = new ArrayList<>();
      int oldTargetDnKeyLength = toKey(oldName).length();
      ByteString newTargetDnKey = toKey(newName);

      do
      {
        ByteString currentDnKey = cursor.getKey();
        EntryID oldID = new EntryID(cursor.getValue());
        cursor.delete();

        ByteString newDnKeySuffix = currentDnKey.subSequence(oldTargetDnKeyLength, currentDnKey.length());
        ByteSequence newDnKey = new ByteStringBuilder(newTargetDnKey).appendBytes(newDnKeySuffix);
        EntryID newID = renumberEntryIDs ? rootContainer.getNextEntryID() : oldID;
        txn.put(getName(), newDnKey, newID.toByteString());

        renamedEntryIDs.add(Pair.of(oldID.longValue(), newID.longValue()));

        if (operation != null)
        {
          operation.checkIfCanceled(false);
        }
      }
      while (cursor.next());

      return renamedEntryIDs;
    }
  }

  @Override
  public String keyToString(ByteString key)
  {
    return key.length() > 0 ? keyToDNString(key) : baseDN.toString();
  }

  @Override
  public String valueToString(ByteString value)
  {
    return new EntryID(value).toString();
  }

  @Override
  public ByteString generateKey(String key)
  {
    try
    {
      return toKey(DN.valueOf(key));
    }
    catch (Exception e)
    {
      return ByteString.valueOfBytes(key.getBytes());
    }
  }

  /**
   * Decorator overriding the next() behavior to iterate through children of the entry pointed by the given cursor at
   * creation.
   */
  private static final class ChildrenCursor extends
      SequentialCursorDecorator<Cursor<ByteString, ByteString>, ByteString, ByteString>
  {
    private final ByteStringBuilder builder;
    private final ByteSequence limit;
    private boolean cursorCurrentlyOnParent;

    ChildrenCursor(Cursor<ByteString, ByteString> delegate)
    {
      super(delegate);
      builder = new ByteStringBuilder(128);
      limit = delegate.isDefined() ? afterLastChildOf(delegate.getKey()) : ByteString.empty();
      cursorCurrentlyOnParent = true;
    }

    @Override
    public boolean next()
    {
      if (cursorCurrentlyOnParent) {
        // Go to the first children
        delegate.next();
        cursorCurrentlyOnParent = false;
      } else {
        // Go to the next sibling
        delegate.positionToKeyOrNext(nextSibling());
      }
      return isDefined() && delegate.getKey().compareTo(limit) < 0;
    }

    private ByteSequence nextSibling()
    {
      return builder.clear().appendBytes(delegate.getKey()).appendByte(0x1);
    }
  }

  /**
   * Decorator overriding the next() behavior to iterate through subordinates of the entry pointed by the given cursor
   * at creation.
   */
  private static final class SubtreeCursor extends
      SequentialCursorDecorator<Cursor<ByteString, ByteString>, ByteString, ByteString>
  {
    private final ByteSequence limit;

    SubtreeCursor(Cursor<ByteString, ByteString> delegate)
    {
      super(delegate);
      limit = delegate.isDefined() ? afterLastChildOf(delegate.getKey()) : ByteString.empty();
    }

    @Override
    public boolean next()
    {
      return delegate.next() && delegate.getKey().compareTo(limit) < 0;
    }
  }

  /** Keep track of information during the visit. */
  private static final class ParentInfo<V>
  {
    private final ByteString parentDN;
    private final V visitorData;

    ParentInfo(ByteString parentDN, V visitorData)
    {
      this.parentDN = parentDN;
      this.visitorData = visitorData;
    }
  }

  /** Allows to visit dn2id tree without exposing internal encoding. */
  static interface TreeVisitor<V>
  {
    V beginParent(EntryID parentID);

    void onChild(V parent, EntryID childID);

    void endParent(V parent);
  }

  /** Perform dn2id cursoring to expose parent and children to the {@link TreeVisitor}. */
  private static final class TreeVisitorCursor<V> implements SequentialCursor<ByteString, ByteString>
  {
    private final SequentialCursor<ByteString, ByteString> delegate;
    private final LinkedList<ParentInfo<V>> parentsInfoStack;
    private final TreeVisitor<V> visitor;

    TreeVisitorCursor(SequentialCursor<ByteString, ByteString> delegate, TreeVisitor<V> visitor)
    {
      this.delegate = delegate;
      this.parentsInfoStack = new LinkedList<>();
      this.visitor = visitor;
    }

    @Override
    public boolean next()
    {
      if (delegate.next())
      {
        final ByteString dn = delegate.getKey();
        final EntryID entryID = new EntryID(delegate.getValue());
        popCompleteParents(dn);
        notifyChild(entryID);
        pushNewParent(dn, entryID);
        return true;
      }
      popCompleteParents(DN.rootDN().toNormalizedByteString());
      return false;
    }

    private void pushNewParent(final ByteString dn, final EntryID entryID)
    {
      parentsInfoStack.push(new ParentInfo<>(dn, visitor.beginParent(entryID)));
    }

    private void notifyChild(final EntryID entryID)
    {
      if (!parentsInfoStack.isEmpty())
      {
        visitor.onChild(parentsInfoStack.peek().visitorData, entryID);
      }
    }

    private void popCompleteParents(ByteString dn)
    {
      ParentInfo<V> currentParent;
      while ((currentParent = parentsInfoStack.peek()) != null && !DnKeyFormat.isChild(currentParent.parentDN, dn))
      {
        visitor.endParent(parentsInfoStack.pop().visitorData);
      }
    }

    @Override
    public boolean isDefined()
    {
      return delegate.isDefined();
    }

    @Override
    public ByteString getKey() throws NoSuchElementException
    {
      return delegate.getKey();
    }

    @Override
    public ByteString getValue() throws NoSuchElementException
    {
      return delegate.getValue();
    }

    @Override
    public void delete() throws NoSuchElementException, UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close()
    {
      delegate.close();
    }
  }
}
