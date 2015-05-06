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
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.newUndefinedSet;

import java.util.ArrayList;
import java.util.Collection;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;

/**
 * This class is an implementation of IndexQueryFactory which creates
 * IndexQuery objects as part of the query of the JEB index.
 */
final class IndexQueryFactoryImpl implements IndexQueryFactory<IndexQuery>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String PRESENCE_INDEX_KEY = "presence";

  private final ReadableTransaction txn;
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
  IndexQueryFactoryImpl(ReadableTransaction txn, AttributeIndex attributeIndex)
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
          // Read the tree and get Record for the key.
          // Select the right index to be used.
          final Index index = attributeIndex.getNameToIndexes().get(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID,
                  attributeIndex.getAttributeType().getNameOrOID()));
            }
            return createMatchAllQuery().evaluate(debugMessage);
          }

          final EntryIDSet entrySet = index.get(txn, key);
          if (debugMessage != null && !entrySet.isDefined())
          {
            updateStatsUndefinedResults(debugMessage, index);
          }
          return entrySet;
        }

        @Override
        public String toString()
        {
          return "ExactMatch(" + indexID + "=" + key + ")";
        }
      };
  }

  /** {@inheritDoc} */
  @Override
  public IndexQuery createRangeMatchQuery(final String indexID, final ByteSequence lowerBound,
      final ByteSequence upperBound, final boolean includeLowerBound, final boolean includeUpperBound)
  {
    return new IndexQuery()
    {
      @Override
      public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage)
      {
        final Index index = attributeIndex.getNameToIndexes().get(indexID);
        if (index == null)
        {
          if (debugMessage != null)
          {
            debugMessage.append(INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID,
                  attributeIndex.getAttributeType().getNameOrOID()));
          }
          return createMatchAllQuery().evaluate(debugMessage);
        }

        final EntryIDSet entrySet = readRange(index, txn, lowerBound, upperBound, includeLowerBound, includeUpperBound);
        if (debugMessage != null && !entrySet.isDefined())
        {
          updateStatsUndefinedResults(debugMessage, index);
        }
        return entrySet;
      }

      private final EntryIDSet readRange(Index index, ReadableTransaction txn, ByteSequence lower, ByteSequence upper,
          boolean lowerIncluded, boolean upperIncluded)
      {
        // If this index is not trusted, then just return an undefined id set.
        if (!index.isTrusted())
        {
          return newUndefinedSet();
        }

        try
        {
          // Total number of IDs found so far.
          int totalIDCount = 0;
          ArrayList<EntryIDSet> sets = new ArrayList<EntryIDSet>();
          Cursor<ByteString, EntryIDSet> cursor = index.openCursor(txn);
          try
          {
            boolean success;
            // Set the lower bound if necessary.
            if (lower.length() > 0)
            {
              // Initialize the cursor to the lower bound.
              success = cursor.positionToKeyOrNext(lower);

              // Advance past the lower bound if necessary.
              if (success && !lowerIncluded && cursor.getKey().equals(lower))
              {
                // Do not include the lower value.
                success = cursor.next();
              }
            }
            else
            {
              success = cursor.next();
            }

            if (!success)
            {
              // There are no values.
              return EntryIDSet.newDefinedSet();
            }

            // Step through the keys until we hit the upper bound or the last key.
            while (success)
            {
              // Check against the upper bound if necessary
              if (upper.length() > 0)
              {
                int cmp = cursor.getKey().compareTo(upper);
                if (cmp > 0 || (cmp == 0 && !upperIncluded))
                {
                  break;
                }
              }

              EntryIDSet set = cursor.getValue();
              if (!set.isDefined())
              {
                // There is no point continuing.
                return set;
              }
              totalIDCount += set.size();
              if (totalIDCount > IndexFilter.CURSOR_ENTRY_LIMIT)
              {
                // There are too many. Give up and return an undefined list.
                return newUndefinedSet();
              }
              sets.add(set);
              success = cursor.next();
            }

            return EntryIDSet.newSetFromUnion(sets);
          }
          finally
          {
            cursor.close();
          }
        }
        catch (StorageRuntimeException e)
        {
          logger.traceException(e);
          return newUndefinedSet();
        }
      }

        @Override
        public String toString()
        {
          final StringBuilder sb = new StringBuilder("RangeMatch(");
          sb.append(lowerBound).append(" ");
          sb.append(includeLowerBound ? "<=" : "<").append(" ");
          sb.append(indexID).append(" ");
          sb.append(includeUpperBound ? ">=" : ">").append(" ");
          sb.append(upperBound);
          sb.append(")");
          return sb.toString();
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
          final Index index = attributeIndex.getNameToIndexes().get(indexID);
          if (index == null)
          {
            if(debugMessage != null)
            {
              debugMessage.append(INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID,
                  attributeIndex.getAttributeType().getNameOrOID()));
            }
            return newUndefinedSet();
          }

          final EntryIDSet entrySet = index.get(txn, AttributeIndex.PRESENCE_KEY);
          if (debugMessage != null && !entrySet.isDefined())
          {
            updateStatsUndefinedResults(debugMessage, index);
          }
          return entrySet;
        }

        @Override
        public String toString()
        {
          return "MatchAll(" + PRESENCE_INDEX_KEY + ")";
        }
      };
  }

  private static void updateStatsUndefinedResults(LocalizableMessageBuilder debugMessage, Index index)
  {
    if (!index.isTrusted())
    {
      debugMessage.append(INFO_INDEX_FILTER_INDEX_NOT_TRUSTED.get(index.getName()));
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
    return attributeIndex.getIndexingOptions();
  }
}
