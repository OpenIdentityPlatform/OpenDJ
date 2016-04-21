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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable.spi;

import java.io.Closeable;

import net.jcip.annotations.ThreadSafe;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

/**
 * Allows to run an import. For performance reasons, imports are run without transactions.
 * <p>
 * Since import is multi threaded, implementations must be thread-safe.
 */
@ThreadSafe
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
   * method, the record is visible by {@link #read(TreeName, ByteSequence)} and {@link #openCursor(TreeName)} methods of
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
   * {@link #put(TreeName, ByteSequence, ByteSequence)} operations. Indeed, once opened, cursors might not reflect
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

