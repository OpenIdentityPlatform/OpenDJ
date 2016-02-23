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
   * Returns the number of key/value pairs in the provided tree.
   *
   * @param treeName
   *          the tree name
   * @return the number of key/value pairs in the provided tree.
   */
  long getRecordCount(TreeName treeName);
}
