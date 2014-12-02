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

import org.opends.server.backends.jeb.EntryID;
import org.opends.server.backends.pluggable.KeyValueStore;
import org.opends.server.backends.pluggable.NotImplementedException;
import org.opends.server.backends.pluggable.SuffixContainer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.Transaction;
import com.persistit.Value;
import com.persistit.exception.PersistitException;

import static org.opends.server.backends.jeb.JebFormat.*;

/**
 * Persistit implementation of the DN2ID index.
 */
class PersistitDN2ID implements KeyValueStore<DN, EntryID, Transaction, Void>
{

  private static final String INDEX_NAME = SuffixContainer.ID2ENTRY_INDEX_NAME;
  private final String fullyQualifiedIndexName;
  private final PersistitSuffixContainer suffixContainer;
  private final int prefixRDNComponents;

  /**
   * Creates a new dn2id index.
   *
   * @param suffixContainer
   *          the suffix container holding this dn2id index
   */
  PersistitDN2ID(PersistitSuffixContainer suffixContainer)
  {
    this.suffixContainer = suffixContainer;
    this.fullyQualifiedIndexName = suffixContainer.getFullyQualifiedIndexName(INDEX_NAME);
    this.prefixRDNComponents = suffixContainer.getBaseDN().size();
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
  public boolean insert(Transaction txn, DN dn, EntryID entryID) throws DirectoryException
  {
    Exchange ex = null;
    try
    {
      ex = suffixContainer.getExchange(INDEX_NAME);
      appendAll(ex.getKey(), dnToDNKey(dn, prefixRDNComponents));
      ex.getValue().put(entryID.longValue());
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

  private void appendAll(Key key, byte[] bytes)
  {
    if (bytes.length == 0)
    {
      // TODO JNR which is best here?
      // key.append(null);
      key.append((byte) ',');
    }
    else
    {
      // FIXME JNR this way to append is really not efficient
      for (byte b : bytes)
      {
        key.append(b);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean put(Transaction txn, DN dn, EntryID entryID) throws DirectoryException
  {
    throw new NotImplementedException();
  }

  /** {@inheritDoc} */
  @Override
  public EntryID get(Transaction txn, DN dn, Void mode) throws DirectoryException
  {
    Exchange ex = null;
    try
    {
      ex = suffixContainer.getExchange(INDEX_NAME);
      appendAll(ex.getKey(), dnToDNKey(dn, prefixRDNComponents));
      ex.fetch();
      final Value value = ex.getValue();
      if (value.isDefined())
      {
        return new EntryID(value.getLong());
      }
      return null;
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

  /** {@inheritDoc} */
  @Override
  public boolean remove(Transaction txn, DN dn) throws DirectoryException
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
