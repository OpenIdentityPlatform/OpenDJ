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
 * Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable.spi;

import org.forgerock.opendj.ldap.ByteSequence;

/**
 * Represents a writeable transaction on a storage engine.
 */
public interface WriteableTransaction extends ReadableTransaction
{
  /**
   * Opens the tree identified by the provided name.
   *
   * @param name
   *          the tree name
   * @param createOnDemand true if the tree should be created if it does not exist
   */
  void openTree(TreeName name, boolean createOnDemand);

  /**
   * Deletes the tree identified by the provided name.
   *
   * @param name
   *          the tree name
   */
  void deleteTree(TreeName name);

  /**
   * Adds a record with the provided key and value, replacing any existing record having the same
   * key.
   *
   * @param treeName
   *          the tree name
   * @param key
   *          the key of the new record
   * @param value
   *          the value of the new record
   */
  void put(TreeName treeName, ByteSequence key, ByteSequence value);

  /**
   * Atomically adds, deletes, or replaces a record with the provided key according to the new value
   * computed by the update function.
   *
   * @param treeName
   *          the tree name
   * @param key
   *          the key of the new record
   * @param f
   *          the update function
   * @return {@code true} if an update was performed, {@code false} otherwise
   * @see UpdateFunction#computeNewValue(ByteSequence)
   */
  boolean update(TreeName treeName, ByteSequence key, UpdateFunction f);

  /**
   * Deletes the record with the provided key, in the tree whose name is provided.
   *
   * @param treeName
   *          the tree name
   * @param key
   *          the key of the record to delete
   * @return {@code true} if the record could be deleted, {@code false} otherwise
   */
  boolean delete(TreeName treeName, ByteSequence key);
}
