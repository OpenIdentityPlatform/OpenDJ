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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.persistit;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.api.CompressedSchema;
import org.opends.server.backends.jeb.DataConfig;
import org.opends.server.backends.jeb.EntryID;
import org.opends.server.backends.jeb.ID2Entry;
import org.opends.server.backends.pluggable.KeyValueStore;
import org.opends.server.backends.pluggable.NotImplementedException;
import org.opends.server.backends.pluggable.SuffixContainer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;

import com.persistit.Exchange;
import com.persistit.Value;
import com.persistit.exception.PersistitException;

/**
 * Persistit implementation of the ID2entry index.
 */
class PersistitID2Entry implements KeyValueStore<EntryID, Entry, Void, Void>
{

  // TODO JNR use com.persistit.encoding.ObjectCache when decoding attributes?

  private static final String INDEX_NAME = SuffixContainer.ID2ENTRY_INDEX_NAME;
  private final String fullyQualifiedIndexName;
  private final PersistitSuffixContainer suffixContainer;
  /** TODO JNR remove. */
  private final DataConfig dataConfig = new DataConfig(false, false, new CompressedSchema());

  /**
   * Creates a new id2entry index.
   *
   * @param suffixContainer
   *          the suffix container holding this id2entry index
   */
  PersistitID2Entry(PersistitSuffixContainer suffixContainer)
  {
    this.suffixContainer = suffixContainer;
    this.fullyQualifiedIndexName = suffixContainer.getFullyQualifiedIndexName(INDEX_NAME);
  }

  /** {@inheritDoc} */
  @Override
  public void open() throws DirectoryException
  {
    try
    {
      this.suffixContainer.createTree(INDEX_NAME);
    }
    catch (PersistitException e)
    {
      throw new NotImplementedException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean insert(Void txn, EntryID entryID, Entry entry) throws DirectoryException
  {
    Exchange ex = null;
    try
    {
      ex = suffixContainer.getExchange(INDEX_NAME);
      ex.getKey().append(entryID.longValue());
      ex.getValue().putByteArray(toByteArray(entry));
      ex.store();
      return true;
    }
    catch (PersistitException e)
    {
      throw new NotImplementedException(e);
    }
    finally
    {
      suffixContainer.releaseExchange(ex);
    }
  }

  private byte[] toByteArray(Entry entry) throws DirectoryException
  {
    ByteString bs = ID2Entry.entryToDatabase(entry, dataConfig);
    return bs.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public boolean put(Void txn, EntryID entryID, Entry entry) throws DirectoryException
  {
    throw new NotImplementedException();
  }

  /** {@inheritDoc} */
  @Override
  public Entry get(Void txn, EntryID entryID, Void mode) throws DirectoryException
  {
    Exchange ex = null;
    try
    {
      ex = suffixContainer.getExchange(INDEX_NAME);
      ex.getKey().append(entryID.longValue());
      ex.fetch();
      final Value value = ex.getValue();
      if (value.isDefined())
      {
        ByteString bytes = ByteString.wrap(value.getByteArray());
        CompressedSchema compressedSchema = dataConfig.getEntryEncodeConfig().getCompressedSchema();
        return ID2Entry.entryFromDatabase(bytes, compressedSchema);
      }
      return null;
    }
    catch (Exception e)
    {
      throw new NotImplementedException(e);
    }
    finally
    {
      suffixContainer.releaseExchange(ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean remove(Void txn, EntryID entryID) throws DirectoryException
  {
    throw new NotImplementedException();
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    // nothing to do
  }
}
