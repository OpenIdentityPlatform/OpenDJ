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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.pluggable.spi;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

/**
 * Represents a readable transaction on a storage engine.
 */
public interface ReadableTransaction
{
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
   * Opens a cursor on the tree whose name is provided.
   *
   * @param treeName
   *          the tree name
   * @return a new cursor
   */
  Cursor<ByteString, ByteString> openCursor(TreeName treeName);

  /**
   * Opens a cursor on the tree whose name is provided, for a walk of that whole tree with no
   * client operation waiting on it: an export, a verify, a rebuild, or the load of a tree while
   * the backend opens.
   * <p>
   * A storage engine that bounds how long a statement may take must not bound such a walk as it
   * bounds the work of a client operation: what this legitimately takes follows the size of the
   * tree, and cutting it short fails an administrative task that would otherwise have run to the
   * end. An engine with no such bound - every one but the JDBC backend - answers this exactly as
   * {@link #openCursor(TreeName)} does.
   *
   * @param treeName
   *          the tree name
   * @return a new cursor
   */
  default Cursor<ByteString, ByteString> openBulkCursor(TreeName treeName)
  {
    return openCursor(treeName);
  }

  /**
   * Returns the number of key/value pairs in the provided tree.
   *
   * @param treeName
   *          the tree name
   * @return the number of key/value pairs in the provided tree.
   */
  long getRecordCount(TreeName treeName);

  /**
   * Returns whether the tree whose name is provided is present in the storage.
   * <p>
   * This is not the same question as whether the tree is empty, and it cannot be answered by
   * reading from the tree: a storage is free to materialize a tree on first access - the JE backend
   * opens its databases with {@code setAllowCreate(true)} - or to reject the access outright, as the
   * JDBC backend does when no table of that name exists. Callers that must distinguish "never
   * written" from "written and since emptied", such as the compressed schema deciding whether it
   * has anything to migrate, need this instead.
   * <p>
   * A storage whose trees have no existence of their own cannot keep that distinction: the
   * Cassandra backend holds every tree of a backend as a partition of the one table named after
   * the backend id, so it answers whether the partition holds a record and a tree that was emptied
   * reports itself absent. Nothing may be inferred from a {@code false} beyond "there is nothing
   * to read here".
   *
   * @param treeName
   *          the tree name
   * @return {@code true} if the tree exists, {@code false} otherwise
   */
  boolean treeExists(TreeName treeName);
}
