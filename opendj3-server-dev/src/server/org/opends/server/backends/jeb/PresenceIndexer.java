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
package org.opends.server.backends.jeb;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/**
 * An implementation of an Indexer for attribute presence.
 */
public class PresenceIndexer extends Indexer
{

  /**
   * The attribute type for which this instance will
   * generate index keys.
   */
  private AttributeType attributeType;

  /**
   * Create a new attribute presence indexer.
   * @param attributeType The attribute type for which the indexer
   * is required.
   */
  public PresenceIndexer(AttributeType attributeType)
  {
    this.attributeType = attributeType;
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  @Override
  public String toString()
  {
    return attributeType.getNameOrOID() + ".presence";
  }

  /**
   * Generate the set of index keys for an entry.
   *
   * @param entry The entry.
   * @param keys The set into which the generated keys will be inserted.
   */
  @Override
  public void indexEntry(Entry entry, Set<ByteString> keys)
  {
    List<Attribute> attrList =
         entry.getAttribute(attributeType);
    if (attrList != null)
    {
      if (!attrList.isEmpty())
      {
        keys.add(getPresenceKeyData());
      }
    }
  }



  /**
   * Generate the set of index keys to be added and the set of index keys
   * to be deleted for an entry that has been replaced.
   *
   * @param oldEntry The original entry contents.
   * @param newEntry The new entry contents.
   * @param modifiedKeys The map into which the modified keys will be inserted.
   */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
                           Map<ByteString, Boolean> modifiedKeys)
  {
    List<Attribute> newAttributes = newEntry.getAttribute(attributeType, true);
    List<Attribute> oldAttributes = oldEntry.getAttribute(attributeType, true);
    if(oldAttributes == null)
    {
      if(newAttributes != null)
      {
        modifiedKeys.put(getPresenceKeyData(), true);
      }
    }
    else
    {
      if(newAttributes == null)
      {
        modifiedKeys.put(getPresenceKeyData(), false);
      }
    }
  }



  /**
   * Generate the set of index keys to be added and the set of index keys
   * to be deleted for an entry that was modified.
   *
   * @param oldEntry The original entry contents.
   * @param newEntry The new entry contents.
   * @param mods The set of modifications that were applied to the entry.
   * @param modifiedKeys The map into which the modified keys will be inserted.
   */
  @Override
  public void modifyEntry(Entry oldEntry, Entry newEntry,
                          List<Modification> mods,
                          Map<ByteString, Boolean> modifiedKeys)
  {
    List<Attribute> newAttributes = newEntry.getAttribute(attributeType, true);
    List<Attribute> oldAttributes = oldEntry.getAttribute(attributeType, true);
    if(oldAttributes == null)
    {
      if(newAttributes != null)
      {
        modifiedKeys.put(getPresenceKeyData(), true);
      }
    }
    else
    {
      if(newAttributes == null)
      {
        modifiedKeys.put(getPresenceKeyData(), false);
      }
    }
  }

  private ByteString getPresenceKeyData()
  {
    return ByteString.wrap(AttributeIndex.presenceKey.getData());
  }
}
