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

import java.io.Closeable;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

/**
 * Allows to run an import. For performance reasons, imports are run without transactions.
 * <p>
 * Since import is multi threaded, implementations must be thread-safe.
 * <p>
 *
 * @ThreadSafe
 */
public interface Importer extends Closeable
{
  /**
   * Clear the tree whose name is provided. Ensure that an empty tree with the given name exists. If the tree already
   * exists, all the data it contains will be deleted. If not, an empty tree will be created.
   *
   * @param treeName name of the tree to clear
   */
  void clearTree(TreeName treeName);

  /**
   * Creates a record with the provided key and value in the tree identified by the provided name. At the end of this
   * method, the record is visible by {@link read(TreeName, ByteSequence)} and {@link openCursor(TreeName)} methods of
   * this instance. The record is guaranteed to be persisted only after {@link #close()}.
   *
   * @param treeName
   *          the tree name
   * @param key
   *          the new record's key
   * @param value
   *          the new record's value
   */
  void put(TreeName treeName, ByteSequence key, ByteSequence value);

  /**
   * Reads the record's value associated to the provided key, in the tree whose name is provided.
   *
   * @param treeName
   *          the tree name
   * @param key
   *          the record's key
   * @return the record's value, or {@code null} if none exists
   */
  ByteString read(TreeName treeName, ByteSequence key);

  /**
   * Opens a cursor on the tree whose name is provided. Cursors are predictable only if there is no pending
   * {@link put(TreeName, ByteSequence, ByteSequence)} operations. Indeed, once opened, cursors might not reflect
   * changes.
   *
   * @param treeName
   *          the tree name
   * @return a new cursor
   */
  SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName);

  @Override
  void close();
}