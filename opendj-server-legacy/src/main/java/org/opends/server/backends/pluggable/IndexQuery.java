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

import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.IndexFilter.*;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.util.Utils;

/**
 * This class represents a Backend Query.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
abstract class IndexQuery
{
  private static final String SEPARATOR = "\n  ";

  /**
   * Evaluates the index query and returns the EntryIDSet.
   *
   * @param debugMessage If not null, diagnostic message will be written
   *                      which will help to determine why the returned
   *                      EntryIDSet is not defined.
   * @param indexNameOut If not null, output parameter for the name of the index type actually used to return
   *                      index results.
   * @return The non null EntryIDSet as a result of evaluating this query
   */
  public abstract EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut);

  /**
   * Creates an IntersectionIndexQuery object from a collection of
   * IndexQuery objects.
   *
   * @param subIndexQueries
   *          A collection of IndexQuery objects.
   * @return An IntersectionIndexQuery object.
   */
  static IndexQuery createIntersectionIndexQuery(Collection<IndexQuery> subIndexQueries)
  {
    return new IntersectionIndexQuery(subIndexQueries);
  }

  /**
   * Creates a union IndexQuery object from a collection of IndexQuery
   * objects.
   *
   * @param subIndexQueries
   *          Collection of IndexQuery objects.
   * @return A UnionIndexQuery object.
   */
  static IndexQuery createUnionIndexQuery(Collection<IndexQuery> subIndexQueries)
  {
    return new UnionIndexQuery(subIndexQueries);
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

  /**
   * This class creates a Null IndexQuery. It is used when there is no
   * record in the index. It may also be used when the index contains
   * all the records but an empty EntryIDSet should be returned as part
   * of the optimization.
   */
  private static final class NullIndexQuery extends IndexQuery
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

  /**
   * This class creates an intersection IndexQuery from a collection of
   * IndexQuery objects.
   */
  private static final class IntersectionIndexQuery extends IndexQuery
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
  private static final class UnionIndexQuery extends IndexQuery
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
      final EntryIDSet entryIDs = newDefinedSet();
      for (IndexQuery query : subIndexQueries)
      {
        entryIDs.addAll(query.evaluate(debugMessage, indexNameOut));
        if (entryIDs.isDefined() && entryIDs.size() >= CURSOR_ENTRY_LIMIT)
        {
          break;
        }
      }
      return entryIDs;
    }

    @Override
    public String toString()
    {
      return "Union(" + SEPARATOR + Utils.joinAsString(SEPARATOR, subIndexQueries) + ")";
    }
  }
}
