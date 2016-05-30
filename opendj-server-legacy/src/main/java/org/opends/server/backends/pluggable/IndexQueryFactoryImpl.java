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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.IndexFilter.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.Utils;
import org.opends.server.backends.pluggable.AttributeIndex.IndexFilterType;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;

/**
 * This class is an implementation of IndexQueryFactory which creates
 * IndexQuery objects as part of the query to the index.
 */
final class IndexQueryFactoryImpl implements IndexQueryFactory<IndexQuery>
{
  /**
   * This class creates a Null IndexQuery. It is used when there is no
   * record in the index. It may also be used when the index contains
   * all the records but an empty EntryIDSet should be returned as part
   * of the optimization.
   */
  private static final class NullIndexQuery implements IndexQuery
  {
    @Override
    public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut)
    {
      return newUndefinedSet();
    }

    @Override
    public String toString()
    {
      return "Null";
    }
  }

  /** This class creates an intersection IndexQuery from a collection of IndexQuery objects. */
  private static final class IntersectionIndexQuery implements IndexQuery
  {
    /** Collection of IndexQuery objects. */
    private final Collection<IndexQuery> subIndexQueries;

    /**
     * Creates an instance of IntersectionIndexQuery.
     *
     * @param subIndexQueries
     *          Collection of IndexQuery objects.
     */
    private IntersectionIndexQuery(Collection<IndexQuery> subIndexQueries)
    {
      this.subIndexQueries = subIndexQueries;
    }

    @Override
    public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut)
    {
      final EntryIDSet entryIDs = newUndefinedSet();
      for (IndexQuery query : subIndexQueries)
      {
        entryIDs.retainAll(query.evaluate(debugMessage, indexNameOut));
        if (isBelowFilterThreshold(entryIDs))
        {
          break;
        }
      }
      return entryIDs;
    }

    @Override
    public String toString()
    {
      return "Intersection(" + SEPARATOR + Utils.joinAsString(SEPARATOR, subIndexQueries) + ")";
    }
  }

  /** This class creates a union of IndexQuery objects. */
  private static final class UnionIndexQuery implements IndexQuery
  {
    /** Collection containing IndexQuery objects. */
    private final Collection<IndexQuery> subIndexQueries;

    /**
     * Creates an instance of UnionIndexQuery.
     *
     * @param subIndexQueries
     *          The Collection of IndexQuery objects.
     */
    private UnionIndexQuery(Collection<IndexQuery> subIndexQueries)
    {
      this.subIndexQueries = subIndexQueries;
    }

    @Override
    public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut)
    {
      final List<EntryIDSet> candidateSets = new ArrayList<>(subIndexQueries.size());
      for (final IndexQuery query : subIndexQueries)
      {
        final EntryIDSet set = query.evaluate(debugMessage, indexNameOut);
        if (!set.isDefined())
        {
          // There is no point continuing.
          return set;
        }
        candidateSets.add(set);
      }
      return newSetFromUnion(candidateSets);
    }

    @Override
    public String toString()
    {
      return "Union(" + SEPARATOR + Utils.joinAsString(SEPARATOR, subIndexQueries) + ")";
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String PRESENCE_INDEX_KEY = "presence";
  private static final String SEPARATOR = "\n  ";

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

  @Override
  public IndexQuery createExactMatchQuery(final String indexID, final ByteSequence key)
  {
    return new IndexQuery()
      {

        @Override
        public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut)
        {
          // Read the tree and get Record for the key.
          // Select the right index to be used.
          Index index = attributeIndex.getNameToIndexes().get(indexID);
          ByteSequence indexKey = key;
          if (index == null)
          {
            index = attributeIndex.getNameToIndexes().get(indexID + AttributeIndex.PROTECTED_INDEX_ID);
            if (index == null)
            {
              appendDisabledIndexType(debugMessage, indexID, attributeIndex.getAttributeType());
              return createMatchAllQuery().evaluate(debugMessage, indexNameOut);
            }
            try
            {
              indexKey = attributeIndex.getCryptoSuite().hash48(key);
            }
            catch (DecodeException de)
            {
              appendExceptionError(debugMessage, de.getMessageObject());
              return createMatchAllQuery().evaluate(debugMessage, indexNameOut);
            }
          }

          final EntryIDSet entrySet = index.get(txn, indexKey);
          updateStatsForUndefinedResults(debugMessage, entrySet, index);
          return entrySet;
        }

        @Override
        public String toString()
        {
          return "ExactMatch(" + indexID + "=" + key + ")";
        }
      };
  }

  @Override
  public IndexQuery createRangeMatchQuery(final String indexID, final ByteSequence lowerBound,
      final ByteSequence upperBound, final boolean includeLowerBound, final boolean includeUpperBound)
  {
    return new IndexQuery()
    {
      @Override
      public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut)
      {
        final Index index = attributeIndex.getNameToIndexes().get(indexID);
        if (index == null)
        {
          appendDisabledIndexType(debugMessage, indexID, attributeIndex.getAttributeType());
          return createMatchAllQuery().evaluate(debugMessage, indexNameOut);
        }

        final EntryIDSet entrySet = readRange(index, txn, lowerBound, upperBound, includeLowerBound, includeUpperBound);
        updateStatsForUndefinedResults(debugMessage, entrySet, index);
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
          ArrayList<EntryIDSet> sets = new ArrayList<>();
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
                // Use any key to have debugsearchindex return LIMIT-EXCEEDED instead of NOT-INDEXED.
                return newUndefinedSetWithKey(cursor.getKey());
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

  @Override
  public IndexQuery createIntersectionQuery(Collection<IndexQuery> subqueries)
  {
    return new IntersectionIndexQuery(subqueries);
  }

  @Override
  public IndexQuery createUnionQuery(Collection<IndexQuery> subqueries)
  {
    return new UnionIndexQuery(subqueries);
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
        public EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut)
        {
          final String indexID = PRESENCE_INDEX_KEY;
          final Index index = attributeIndex.getNameToIndexes().get(indexID);
          if (index == null)
          {
            appendDisabledIndexType(debugMessage, indexID, attributeIndex.getAttributeType());
            return newUndefinedSet();
          }

          final EntryIDSet entrySet = index.get(txn, AttributeIndex.PRESENCE_KEY);
          updateStatsForUndefinedResults(debugMessage, entrySet, index);
          if (indexNameOut != null)
          {
            indexNameOut.append(IndexFilterType.PRESENCE);
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

  private static void appendExceptionError(LocalizableMessageBuilder debugMessage, LocalizableMessage msg)
  {
    if (debugMessage != null)
    {
      debugMessage.append(msg);
    }
  }

  private static void appendDisabledIndexType(LocalizableMessageBuilder debugMessage, String indexID,
      AttributeType attrType)
  {
    if (debugMessage != null)
    {
      debugMessage.append(INFO_INDEX_FILTER_INDEX_TYPE_DISABLED.get(indexID, attrType.getNameOrOID()));
    }
  }

  private static void updateStatsForUndefinedResults(
      LocalizableMessageBuilder debugMessage, EntryIDSet idSet, Index index)
  {
    if (debugMessage != null && !idSet.isDefined())
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
  }

  @Override
  public IndexingOptions getIndexingOptions()
  {
    return attributeIndex.getIndexingOptions();
  }

  /**
   * Creates an empty IndexQuery object.
   *
   * @return A NullIndexQuery object.
   */
  static IndexQuery createNullIndexQuery()
  {
    return new NullIndexQuery();
  }
}
