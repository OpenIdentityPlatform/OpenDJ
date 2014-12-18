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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.backends.pluggable.spi.ReadableStorage;

import static org.opends.messages.JebMessages.*;

/**
 * This class is an implementation of IndexQueryFactory which creates
 * IndexQuery objects as part of the query of the JEB index.
 */
public final class IndexQueryFactoryImpl implements IndexQueryFactory<IndexQuery>
{

  private static final String PRESENCE_INDEX_KEY = "presence";

  private final ReadableStorage txn;
  /** The Map containing the string type identifier and the corresponding index. */
  private final AttributeIndex attributeIndex;

  /**
   * Creates a new IndexQueryFactoryImpl object.
   *
   * @param txn
   *          The readable storage
   * @param attributeIndex
   *          The targeted attribute index
   */
  public IndexQueryFactoryImpl(ReadableStorage txn, AttributeIndex attributeIndex)
  {
    this.txn = txn;
    this.attributeIndex = attributeIndex;
  }

  /** {@inheritDoc} */
  @Override
  public IndexQuery createExactMatchQuery(final String indexID, final ByteSequence key)
  {
    return new IndexQuery()
      {
        @Override
        public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage)
        {
          // Read the database and get Record for the key.
          // Select the right index to be used.
          final Index index = attributeIndex.getIndexById(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID, ""));
            }
            return createMatchAllQuery().evaluate(debugMessage);
          }

          final EntryIDSet entrySet = index.readKey(key, null);
          if(debugMessage != null && !entrySet.isDefined())
          {
            updateStatsUndefinedResults(debugMessage, index);
          }
          return entrySet;
        }
      };
  }

  /** {@inheritDoc} */
  @Override
  public IndexQuery createRangeMatchQuery(final String indexID,
      final ByteSequence lowerBound, final ByteSequence upperBound,
      final boolean includeLowerBound, final boolean includeUpperBound)
  {
    return new IndexQuery()
      {
        @Override
        public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage)
        {
          // Find the right index.
          final Index index = attributeIndex.getIndexById(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID, ""));
            }
            return createMatchAllQuery().evaluate(debugMessage);
          }

          final EntryIDSet entrySet = index.readRange(txn, lowerBound, upperBound,
              includeLowerBound, includeUpperBound);
          if(debugMessage != null && !entrySet.isDefined())
          {
            updateStatsUndefinedResults(debugMessage, index);
          }
          return entrySet;
        }
      };
  }

  /** {@inheritDoc} */
  @Override
  public IndexQuery createIntersectionQuery(Collection<IndexQuery> subqueries)
  {
    return IndexQuery.createIntersectionIndexQuery(subqueries);
  }

  /** {@inheritDoc} */
  @Override
  public IndexQuery createUnionQuery(Collection<IndexQuery> subqueries)
  {
    return IndexQuery.createUnionIndexQuery(subqueries);
  }

  /**
   * {@inheritDoc}
   * <p>
   * It returns an empty EntryIDSet object when either all or no record
   * sets are requested.
   */
  @Override
  public IndexQuery createMatchAllQuery()
  {
    return new IndexQuery()
      {
        @Override
        public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage)
        {
          final String indexID = PRESENCE_INDEX_KEY;
          final Index index = attributeIndex.getIndexById(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_JEB_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID, ""));
            }
            return new EntryIDSet();
          }

          final EntryIDSet entrySet = index.readKey(PresenceIndexer.presenceKey, null);
          if (debugMessage != null && !entrySet.isDefined())
          {
            updateStatsUndefinedResults(debugMessage, index);
          }
          return entrySet;
        }
      };
  }

  private static void updateStatsUndefinedResults(LocalizableMessageBuilder debugMessage, Index index)
  {
    if (!index.isTrusted())
    {
      debugMessage.append(INFO_JEB_INDEX_FILTER_INDEX_NOT_TRUSTED.get(index.getName()));
    }
    else if (index.isRebuildRunning())
    {
      debugMessage.append(INFO_JEB_INDEX_FILTER_INDEX_REBUILD_IN_PROGRESS.get(index.getName()));
    }
    else
    {
      debugMessage.append(INFO_JEB_INDEX_FILTER_INDEX_LIMIT_EXCEEDED.get(index.getName()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public IndexingOptions getIndexingOptions()
  {
    return attributeIndex.getIndexingOptions();
  }
}
