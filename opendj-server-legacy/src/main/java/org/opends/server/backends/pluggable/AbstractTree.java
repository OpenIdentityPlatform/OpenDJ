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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

/**
 * This class is a wrapper around the tree object and provides basic
 * read and write methods for entries.
 */
abstract class AbstractTree implements Tree
{
  /** The name of the tree within the entryContainer. */
  private final TreeName name;

  AbstractTree(final TreeName name)
  {
    this.name = name;
  }

  @Override
  public final void open(WriteableTransaction txn, boolean createOnDemand) throws StorageRuntimeException
  {
    txn.openTree(name, createOnDemand);
    afterOpen(txn, createOnDemand);
  }

  /** Override in order to perform any additional initialization after the index has opened. */
  void afterOpen(WriteableTransaction txn, boolean createOnDemand) throws StorageRuntimeException
  {
    // Do nothing by default.
  }

  @Override
  public final void delete(WriteableTransaction txn) throws StorageRuntimeException
  {
    beforeDelete(txn);
    txn.deleteTree(name);
  }

  /** Override in order to perform any additional operation before index tree deletion. */
  void beforeDelete(WriteableTransaction txn) throws StorageRuntimeException
  {
    // Do nothing by default.
  }

  @Override
  public final long getRecordCount(ReadableTransaction txn) throws StorageRuntimeException
  {
    return txn.getRecordCount(name);
  }

  @Override
  public final TreeName getName()
  {
    return name;
  }

  @Override
  public final String toString()
  {
    return name.toString();
  }

  @Override
  public int compareTo(Tree o)
  {
    return name.compareTo(o.getName());
  }

  @Override
  public String keyToString(ByteString key)
  {
    return key.toString();
  }

  @Override
  public String valueToString(ByteString value)
  {
    return value.toString();
  }

  @Override
  public ByteString generateKey(String key)
  {
    return ByteString.valueOfUtf8(key);
  }
}

