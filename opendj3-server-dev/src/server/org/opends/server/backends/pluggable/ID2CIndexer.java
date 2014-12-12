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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/**
 * Implementation of an Indexer for the children index.
 */
public class ID2CIndexer extends Indexer
{
  /**
   * Create a new indexer for a children index.
   */
  public ID2CIndexer()
  {
    // No implementation required.
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "id2children";
  }

  /** {@inheritDoc} */
  @Override
  public void indexEntry(Entry entry, Set<ByteString> addKeys, IndexingOptions options)
  {
    // The superior entry IDs are in the entry attachment.
    ArrayList<EntryID> ids = (ArrayList<EntryID>) entry.getAttachment();

    // Skip the entry's own ID.
    Iterator<EntryID> iter = ids.iterator();
    iter.next();

    // Get the parent ID.
    if (iter.hasNext())
    {
      addKeys.add(iter.next().toByteString());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
                           Map<ByteString, Boolean> modifiedKeys, IndexingOptions options)
  {
    // Nothing to do.
  }

  /** {@inheritDoc} */
  @Override
  public void modifyEntry(Entry oldEntry, Entry newEntry,
                          List<Modification> mods,
                          Map<ByteString, Boolean> modifiedKeys, IndexingOptions options)
  {
    // Nothing to do.
  }
}
