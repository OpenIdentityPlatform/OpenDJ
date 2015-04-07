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
 *      Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.server.backends.pluggable.EntryIDSet.newUndefinedSet;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

/**
 * A null index which replaces id2children and id2subtree when they have been disabled.
 */
final class NullIndex implements Index
{
  private final TreeName name;

  NullIndex(TreeName name)
  {
    this.name = name;
  }

  @Override
  public void update(WriteableTransaction txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs)
      throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  public EntryIDSet get(ReadableTransaction txn, ByteSequence key)
  {
    return newUndefinedSet();
  }

  @Override
  public boolean setIndexEntryLimit(int indexEntryLimit)
  {
    return false;
  }

  @Override
  public int getIndexEntryLimit()
  {
    return 0;
  }

  @Override
  public void setTrusted(WriteableTransaction txn, boolean trusted) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  public boolean isTrusted()
  {
    return true;
  }

  @Override
  public boolean getMaintainCount()
  {
    return false;
  }

  @Override
  public long getRecordCount(ReadableTransaction txn) throws StorageRuntimeException
  {
    return 0;
  }

  @Override
  public Cursor<ByteString, EntryIDSet> openCursor(ReadableTransaction txn)
  {
    return new Cursor<ByteString, EntryIDSet>()
    {

      @Override
      public boolean positionToKey(ByteSequence key)
      {
        return false;
      }

      @Override
      public boolean positionToKeyOrNext(ByteSequence key)
      {
        return false;
      }

      @Override
      public boolean positionToLastKey()
      {
        return false;
      }

      @Override
      public boolean positionToIndex(int index)
      {
        return false;
      }

      @Override
      public boolean next()
      {
        return false;
      }

      @Override
      public ByteString getKey()
      {
        return null;
      }

      @Override
      public EntryIDSet getValue()
      {
        return null;
      }

      @Override
      public void close()
      {
        // Nothing to do.
      }

    };
  }

  @Override
  public void importRemove(WriteableTransaction txn, ImportIDSet idsToBeRemoved) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  public void importPut(WriteableTransaction txn, ImportIDSet idsToBeAdded) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  public TreeName getName()
  {
    return name;
  }

  @Override
  public void open(WriteableTransaction txn) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  public void delete(WriteableTransaction txn) throws StorageRuntimeException
  {
    // Do nothing.
  }

  @Override
  public void setName(TreeName name)
  {
    // Do nothing.
  }

}
