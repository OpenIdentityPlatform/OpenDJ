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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.backends.pluggable.spi.Cursor;
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

  boolean isTrusted();

  Cursor<ByteString, EntryIDSet> openCursor(ReadableTransaction txn);

  boolean setIndexEntryLimit(int indexEntryLimit);

  boolean setConfidential(boolean indexConfidential);

  void setTrusted(WriteableTransaction txn, boolean trusted);

  void update(WriteableTransaction txn, ByteString key, EntryIDSet deletedIDs, EntryIDSet addedIDs);
}
