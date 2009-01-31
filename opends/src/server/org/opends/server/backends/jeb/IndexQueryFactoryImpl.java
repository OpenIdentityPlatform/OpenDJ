/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */


package org.opends.server.backends.jeb;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import java.util.Collection;
import java.util.Map;
import org.opends.server.api.IndexQueryFactory;

/**
 * This class is an implementation of IndexQueryFactory which creates
 * IndexQuery objects as part of the query of the JEB index.
*/
public final class IndexQueryFactoryImpl
        implements IndexQueryFactory<IndexQuery>
{
  /**
   * The Map containing the string type identifier and the corresponding index.
   */
  private Map<String,Index> indexMap;



  /**
   * Creates a new IndexQueryFactoryImpl object.
   * @param indexMap A map containing the index id and the corresponding index.
   */
  public IndexQueryFactoryImpl(Map<String,Index> indexMap)
  {
    this.indexMap = indexMap;
  }



  /**
   *{@inheritDoc}
   */
  public IndexQuery createExactMatchQuery(final String indexID,
          final byte[] value)
  {
    return new IndexQuery()
    {

      @Override
      public EntryIDSet evaluate()
      {
        //Read the database and get Record for the key.
        DatabaseEntry key = new DatabaseEntry(value);
        //Select the right index to be used.
        Index index = indexMap.get(indexID);
        EntryIDSet entrySet = index.readKey(key,null,LockMode.DEFAULT);
        return entrySet;
      }
    };
  }



  /**
   *{@inheritDoc}
   */
  public IndexQuery createRangeMatchQuery(
                                              final String indexID,
                                              final byte[] lowerBound,
                                              final byte[] upperBound,
                                              final boolean includeLowerBound,
                                              final boolean includeUpperBound)
  {
    return new IndexQuery()
    {

      @Override
      public EntryIDSet evaluate()
      {
        //Find the right index.
        Index index = indexMap.get(indexID);
        EntryIDSet entrySet =   index.readRange(lowerBound,upperBound,
                includeLowerBound,
            includeUpperBound);
        return entrySet;
      }
    };
  }



  /**
   *{@inheritDoc}
   */
  public IndexQuery  createIntersectionQuery(Collection<IndexQuery>
                                                                subqueries)
  {
    return IndexQuery.createIntersectionIndexQuery(subqueries);
  }



  /**
   *{@inheritDoc}
   */
  public IndexQuery createUnionQuery(Collection<IndexQuery> subqueries)
  {
    return IndexQuery.createUnionIndexQuery(subqueries);
  }



  /**
   *{@inheritDoc}
   * It returns an empty EntryIDSet object  when either all or no record sets
   * are requested.
   */
  public IndexQuery createMatchAllQuery()
  {
    return new IndexQuery()
    {

      @Override
      public EntryIDSet evaluate()
      {
        return new EntryIDSet();
      }
    };
  }
}