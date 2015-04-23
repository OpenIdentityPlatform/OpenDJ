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