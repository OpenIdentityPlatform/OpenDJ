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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/**
 * This class implements an attribute indexer for matching rules in JE Backend.
 */
public final class AttributeIndexer extends Indexer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The attribute type for which this instance will generate index keys. */
  private final AttributeType attributeType;

  /**
   * The indexer which will generate the keys
   * for the associated extensible matching rule.
   */
  private final org.forgerock.opendj.ldap.spi.Indexer indexer;

  /**
   * Creates a new extensible indexer for JE backend.
   *
   * @param attributeType The attribute type for which an indexer is
   *                                            required.
   * @param extensibleIndexer The extensible indexer to be used.
   */
  public AttributeIndexer(AttributeType attributeType, org.forgerock.opendj.ldap.spi.Indexer extensibleIndexer)
  {
    this.attributeType = attributeType;
    this.indexer = extensibleIndexer;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return attributeType.getNameOrOID() + "." + indexer.getIndexID();
  }

  /** {@inheritDoc} */
  @Override
  public void indexEntry(Entry entry, Set<ByteString> keys)
  {
    final List<Attribute> attrList = entry.getAttribute(attributeType);
    if (attrList != null)
    {
      indexAttribute(attrList, keys);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void modifyEntry(Entry oldEntry, Entry newEntry,
      List<Modification> mods, Map<ByteString, Boolean> modifiedKeys)
  {
    List<Attribute> newAttributes = newEntry.getAttribute(attributeType, true);
    List<Attribute> oldAttributes = oldEntry.getAttribute(attributeType, true);

    indexAttribute(oldAttributes, modifiedKeys, false);
    indexAttribute(newAttributes, modifiedKeys, true);
  }



  /**
   * Generates the set of extensible  index keys for an attribute.
   * @param attrList The attribute for which substring keys are required.
   * @param keys The set into which the generated keys will be inserted.
   */
  private void indexAttribute(List<Attribute> attrList, Set<ByteString> keys)
  {
    if (attrList == null)
    {
      return;
    }

    for (Attribute attr : attrList)
    {
      if (!attr.isVirtual())
      {
        for (ByteString value : attr)
        {
          try
          {
            indexer.createKeys(Schema.getDefaultSchema(), value, keys);
          }
          catch (DecodeException e)
          {
            logger.traceException(e);
          }
        }
      }
    }
  }

  /**
   * Generates the set of index keys for an attribute.
   * @param attrList The attribute to be indexed.
   * @param modifiedKeys The map into which the modified
   * keys will be inserted.
   * @param insert <code>true</code> if generated keys should
   * be inserted or <code>false</code> otherwise.
   */
  private void indexAttribute(List<Attribute> attrList, Map<ByteString, Boolean> modifiedKeys, Boolean insert)
  {
    if (attrList == null)
    {
      return;
    }

    final Set<ByteString> keys = new HashSet<>();
    indexAttribute(attrList, keys);
    computeModifiedKeys(modifiedKeys, insert, keys);
  }

  /**
   * Computes a map of index keys and a boolean flag indicating whether the
   * corresponding key will be inserted or deleted.
   *
   * @param modifiedKeys
   *          A map containing the keys and a boolean. Keys corresponding to the
   *          boolean value <code>true</code> should be inserted and
   *          <code>false</code> should be deleted.
   * @param insert
   *          <code>true</code> if generated keys should be inserted or
   *          <code>false</code> otherwise.
   * @param keys
   *          The index keys to map.
   */
  private static void computeModifiedKeys(Map<ByteString, Boolean> modifiedKeys,
      Boolean insert, Set<ByteString> keys)
  {
    for (ByteString key : keys)
    {
      Boolean cInsert = modifiedKeys.get(key);
      if (cInsert == null)
      {
        modifiedKeys.put(key, insert);
      }
      else if (!cInsert.equals(insert))
      {
        modifiedKeys.remove(key);
      }
    }
  }
}
