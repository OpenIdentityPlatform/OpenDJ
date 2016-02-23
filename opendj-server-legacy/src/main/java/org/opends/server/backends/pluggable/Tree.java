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
interface Tree extends Comparable<Tree>
{
  /**
   * Opens a tree, optionally creating it. If the provided configuration is transactional,
   * a transaction will be created and used to perform the open.
   *
   * @param txn
   *          a non null transaction
   * @param createOnDemand true if the tree should be created if it does not exist
   * @throws StorageRuntimeException
   *           if an error occurs while opening the index.
   */
  void open(WriteableTransaction txn, boolean createOnDemand) throws StorageRuntimeException;

  /**
   * Deletes this tree and all of its contents.
   *
   * @param txn
   *          a non null transaction
   * @throws StorageRuntimeException
   *           if an error occurs while deleting the index.
   */
  void delete(WriteableTransaction txn) throws StorageRuntimeException;

  /**
   * Returns the number of key/value pairs in this tree.
   *
   * @param txn
   *          a non null transaction
   * @return the number of key/value pairs in the provided tree.
   * @throws StorageRuntimeException
   *           If an error occurs in the storage operation.
   */
  long getRecordCount(ReadableTransaction txn) throws StorageRuntimeException;

  /**
   * Get the name for this tree.
   *
   * @return name for this tree.
   */
  TreeName getName();

  /**
   * Returns a printable, semantically meaningful if possible, representation of a Tree key.
   *
   * @param key a key as used by the Tree
   * @return a printable, semantically meaningful if possible, representation of a Tree key.
   */
  String keyToString(ByteString key);

  /**
   * Returns a printable, semantically meaningful if possible, representation of a Tree key.
   *
   * @param value a key as used by the Tree
   * @return a printable, semantically meaningful if possible, representation of a Tree key.
   */
  String valueToString(ByteString value);

  /**
   * Returns a key given a string representation of a value.
   * Since the key is typically used for cursoring, out of many possible keys only one is needed,
   * potentially the lowest key.
   *
   * @param key the specified key as a string
   * @return a key given a string representation of a value
   */
  ByteString generateKey(String key);
}
