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
 *      Copyright 2014-2015 ForgeRock AS
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
   * Renames the tree from the old to the new name.
   *
   * @param oldName
   *          the old tree name
   * @param newName
   *          the new tree name
   */
  void renameTree(TreeName oldName, TreeName newName);

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