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

import java.util.Collection;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.types.AttributeType;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;

import static org.opends.messages.BackendMessages.*;

/**
 * This class is an implementation of IndexQueryFactory which creates
 * IndexQuery objects as part of the query of the JEB index.
 */
public final class IndexQueryFactoryImpl implements
    IndexQueryFactory<IndexQuery>
{

  private static final String PRESENCE_INDEX_KEY = "presence";

  /**
   * The Map containing the string type identifier and the corresponding index.
   */
  private final Map<String, Index> indexMap;
  private final IndexingOptions indexingOptions;
  private final AttributeType attribute;

  /**
   * Creates a new IndexQueryFactoryImpl object.
   *
   * @param indexMap
   *          A map containing the index id and the corresponding index.
   * @param indexingOptions
   *          The options to use for indexing
   * @param attribute
   *          The Attribute type of this index, for error messages
   */
  public IndexQueryFactoryImpl(Map<String, Index> indexMap, IndexingOptions indexingOptions, AttributeType attribute)
  {
    this.indexMap = indexMap;
    this.indexingOptions = indexingOptions;
    this.attribute = attribute;
  }



  /** {@inheritDoc} */
  @Override
  public IndexQuery createExactMatchQuery(final String indexID, final ByteSequence value)
  {
    return new IndexQuery()
      {

        @Override
        public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage)
        {
          // Read the database and get Record for the key.
          DatabaseEntry key = new DatabaseEntry(value.toByteArray());

          // Select the right index to be used.
          Index index = indexMap.get(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID, attribute.getNameOrOID()));
            }
            return createMatchAllQuery().evaluate(debugMessage);
          }
          EntryIDSet entrySet = index.readKey(key, null, LockMode.DEFAULT);
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
          Index index = indexMap.get(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID, attribute.getNameOrOID()));
            }
            return createMatchAllQuery().evaluate(debugMessage);
          }
          EntryIDSet entrySet = index.readRange(lowerBound.toByteArray(), upperBound.toByteArray(),
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
        final Index index = indexMap.get(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID, attribute.getNameOrOID()));
            }
            return new EntryIDSet();
          }

          EntryIDSet entrySet = index.readKey(JEBUtils.presenceKey, null, LockMode.DEFAULT);
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
      debugMessage.append(INFO_INDEX_FILTER_INDEX_NOT_TRUSTED.get(index.getName()));
    }
    else if (index.isRebuildRunning())
    {
      debugMessage.append(INFO_INDEX_FILTER_INDEX_REBUILD_IN_PROGRESS.get(index.getName()));
    }
    else
    {
      debugMessage.append(INFO_INDEX_FILTER_INDEX_LIMIT_EXCEEDED.get(index.getName()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public IndexingOptions getIndexingOptions()
  {
    return indexingOptions;
  }
}
