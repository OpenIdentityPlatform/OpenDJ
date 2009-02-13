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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.ndb;

import com.mysql.cluster.ndbj.NdbApiException;
import com.mysql.cluster.ndbj.NdbIndexScanOperation;
import com.mysql.cluster.ndbj.NdbOperation;
import com.mysql.cluster.ndbj.NdbOperation.AbortOption;
import com.mysql.cluster.ndbj.NdbResultSet;
import com.mysql.cluster.ndbj.NdbTransaction;
import com.mysql.cluster.ndbj.NdbTransaction.ExecType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.SearchFilter;

/**
 * An index filter is used to apply a search operation to a set of indexes
 * to generate a set of candidate entries.
 */
public class IndexFilter
{
  /**
   * The entry entryContainer holding the attribute indexes.
   */
  private EntryContainer entryContainer;

  /**
   * The search operation provides the search base, scope and filter.
   * It can also be checked periodically for cancellation.
   */
  private SearchOperation searchOp;

  private boolean defined;
  private SearchFilter searchFilter;
  private List<NdbResultSet> rsList;
  private NdbTransaction ndbTxn;
  private AbstractTransaction txn;
  private Iterator<NdbResultSet> resultsIterator;
  private NdbResultSet currentRs;
  private Set<Long> returnedSet;

  /**
   * Construct an index filter for a search operation.
   *
   * @param txn            Abstract transaction.
   * @param entryContainer The entry entryContainer.
   * @param searchOp       The search operation to be evaluated.
   * @throws NdbApiException An error occurred during a database operation.
   */
  public IndexFilter(AbstractTransaction txn,
                     EntryContainer entryContainer,
                     SearchOperation searchOp) throws NdbApiException
  {
    this.txn = txn;
    this.entryContainer = entryContainer;
    this.searchOp = searchOp;
    this.searchFilter = this.searchOp.getFilter();
    this.ndbTxn = txn.getNdbTransaction();
    this.rsList = new ArrayList<NdbResultSet>();
    this.resultsIterator = null;
    this.returnedSet = new HashSet<Long>();
    this.currentRs = null;
    this.defined = false;
  }

  /**
   * Perform index filter scan.
   * @throws NdbApiException An error occurred during a database operation.
   */
  public void scan() throws NdbApiException {

    ndbTxn.execute(ExecType.NoCommit, AbortOption.AbortOnError, true);
    resultsIterator = rsList.iterator();
    currentRs = resultsIterator.next();
  }

  /**
   * Get next entry id from index scan results.
   * @return next entry id or zero if none.
   * @throws NdbApiException An error occurred during a database operation.
   */
  public long getNext() throws NdbApiException {

    long eid = 0;

    while (!currentRs.next()) {
      if (resultsIterator.hasNext()) {
        currentRs = resultsIterator.next();
      } else {
        return eid;
      }
    }

    eid = currentRs.getLong(BackendImpl.EID);
    if (!returnedSet.add(eid)) {
      return getNext();
    }

    return eid;
  }

  /**
   * Evaluate index filter.
   * @return true if the filter is defined, false otherwise.
   * @throws NdbApiException An error occurred during a database operation.
   */
  public boolean evaluate()
    throws NdbApiException
  {
    defined = false;
    return evaluateFilter(searchFilter);
  }

  private boolean evaluateFilter(SearchFilter filter)
    throws NdbApiException
  {
    String attrName = null;

    switch (filter.getFilterType()) {
      case AND:
      case OR:
        for (SearchFilter compFilter :
             filter.getFilterComponents())
        {
          evaluateFilter(compFilter);
        }
        break;

      case EQUALITY:
      case APPROXIMATE_MATCH:
        attrName = filter.getAttributeType().getNameOrOID();
        if (BackendImpl.indexes.contains(attrName)) {
          NdbIndexScanOperation indexScanOp =
            ndbTxn.getSelectIndexScanOperation(BackendImpl.IDX_VAL,
            BackendImpl.IDX_TABLE_PREFIX + attrName,
            NdbOperation.LockMode.LM_CommittedRead);
          indexScanOp.setBoundString(BackendImpl.IDX_VAL,
            NdbIndexScanOperation.BoundType.BoundEQ,
            filter.getAssertionValue().toString());
          indexScanOp.getValue(BackendImpl.EID);
          NdbResultSet rs = indexScanOp.resultData();
          rsList.add(rs);
          defined = true;
        }
        break;

      case GREATER_OR_EQUAL:
        attrName = filter.getAttributeType().getNameOrOID();
        if (BackendImpl.indexes.contains(attrName)) {
          NdbIndexScanOperation indexScanOp =
            ndbTxn.getSelectIndexScanOperation(BackendImpl.IDX_VAL,
            BackendImpl.IDX_TABLE_PREFIX + attrName,
            NdbOperation.LockMode.LM_CommittedRead);
          indexScanOp.setBoundString(BackendImpl.IDX_VAL,
            NdbIndexScanOperation.BoundType.BoundGE,
            filter.getAssertionValue().toString());
          indexScanOp.getValue(BackendImpl.EID);
          NdbResultSet rs = indexScanOp.resultData();
          rsList.add(rs);
          defined = true;
        }
        break;

      case LESS_OR_EQUAL:
        attrName = filter.getAttributeType().getNameOrOID();
        if (BackendImpl.indexes.contains(attrName)) {
          NdbIndexScanOperation indexScanOp =
            ndbTxn.getSelectIndexScanOperation(BackendImpl.IDX_VAL,
            BackendImpl.IDX_TABLE_PREFIX + attrName,
            NdbOperation.LockMode.LM_CommittedRead);
          indexScanOp.setBoundString(BackendImpl.IDX_VAL,
            NdbIndexScanOperation.BoundType.BoundLE,
            filter.getAssertionValue().toString());
          indexScanOp.getValue(BackendImpl.EID);
          NdbResultSet rs = indexScanOp.resultData();
          rsList.add(rs);
          defined = true;
        }
        break;

      case PRESENT:
        attrName = filter.getAttributeType().getNameOrOID();
        if (BackendImpl.indexes.contains(attrName)) {
          NdbIndexScanOperation indexScanOp =
            ndbTxn.getSelectIndexScanOperation(BackendImpl.IDX_VAL,
            BackendImpl.IDX_TABLE_PREFIX + attrName,
            NdbOperation.LockMode.LM_CommittedRead);
          indexScanOp.setBoundString(BackendImpl.IDX_VAL,
            NdbIndexScanOperation.BoundType.BoundLT, "");
          indexScanOp.getValue(BackendImpl.EID);
          NdbResultSet rs = indexScanOp.resultData();
          rsList.add(rs);
          defined = true;
        }
        break;

      case NOT:
      case SUBSTRING:
      case EXTENSIBLE_MATCH:
      default:
        //NYI
        break;
    }

    return defined;
  }

  /**
   * Close index filter.
   */
  public void close() {
    ndbTxn = null;
    txn = null;
  }
}
