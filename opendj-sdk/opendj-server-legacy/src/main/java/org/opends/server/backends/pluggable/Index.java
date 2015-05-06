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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

/**
 * Represents an index implemented by a tree in which each key maps to a set of entry IDs. The key
 * is a byte array, and is constructed from some normalized form of an attribute value (or fragment
 * of a value) appearing in the entry.
 */
interface Index extends Tree
{
  EntryIDSet get(ReadableTransaction txn, ByteSequence key);

  int getIndexEntryLimit();

  // Ignores trusted state.
  void importPut(Importer importer, ImportIDSet idsToBeAdded);

  // Ignores trusted state.
  void importRemove(Importer importer, ImportIDSet idsToBeRemoved);

  boolean isTrusted();

  Cursor<ByteString, EntryIDSet> openCursor(ReadableTransaction txn);

  boolean setIndexEntryLimit(int indexEntryLimit);

  void setTrusted(WriteableTransaction txn, boolean trusted);

  void update(WriteableTransaction txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs);
}
