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

import com.mysql.cluster.ndbj.Ndb;
import com.mysql.cluster.ndbj.NdbApiException;
import com.mysql.cluster.ndbj.NdbOperation.AbortOption;
import com.mysql.cluster.ndbj.NdbTransaction;
import com.mysql.cluster.ndbj.NdbTransaction.ExecType;


/**
 * This class represents abstract transaction.
 */
public class AbstractTransaction {

  private Ndb ndb;

  private NdbTransaction ndbTxn;

  private NdbTransaction ndbDATxn;

  private RootContainer rootContainer;

  /**
   * Default constructor.
   * @param rootContainer root container to associate transaction with.
   */
  public AbstractTransaction(RootContainer rootContainer) {
    this.ndb = null;
    this.ndbTxn = null;
    this.ndbDATxn = null;
    this.rootContainer = rootContainer;
  }

  /**
   * Get Ndb handle associated with this abstract transaction.
   * @return Ndb handle.
   */
  public Ndb getNdb()
  {
    if (ndb == null) {
      ndb = rootContainer.getNDB();
    }

    return ndb;
  }

  /**
   * Get transaction.
   * @return A transaction handle.
   * @throws NdbApiException If an error occurs while attempting to begin
   * a new transaction.
   */
  public NdbTransaction getNdbTransaction()
      throws NdbApiException
  {
    if (ndb == null) {
      ndb = rootContainer.getNDB();
    }
    if (ndbTxn == null) {
      ndbTxn = ndb.startTransaction();
    }

    return ndbTxn;
  }

  /**
   * Get DA transaction.
   * @param tableName table name for DA.
   * @param partitionKey partition key for DA.
   * @return A transaction handle.
   * @throws NdbApiException If an error occurs while attempting to begin
   * a new transaction.
   */
  public NdbTransaction
  getNdbDATransaction(String tableName, long partitionKey)
    throws NdbApiException
  {
    if (ndb == null) {
      ndb = rootContainer.getNDB();
    }
    if (ndbDATxn == null) {
      ndbDATxn = ndb.startTransactionBig(tableName, partitionKey);
    }

    return ndbDATxn;
  }

  /**
   * Commit transaction.
   * @throws NdbApiException If an error occurs while attempting to commit
   * the transaction.
   */
  public void commit()
    throws NdbApiException {
    try {
      if (ndbDATxn != null) {
        try {
          ndbDATxn.execute(ExecType.Commit, AbortOption.AbortOnError, true);
        } finally {
          if (ndbDATxn != null) {
            ndbDATxn.close();
          }
        }
      }
      if (ndbTxn != null) {
        try {
          ndbTxn.execute(ExecType.Commit, AbortOption.AbortOnError, true);
        } finally {
          if (ndbTxn != null) {
            ndbTxn.close();
          }
        }
      }
    } finally {
      if (ndb != null) {
        rootContainer.releaseNDB(ndb);
      }
      ndbDATxn = null;
      ndbTxn = null;
      ndb = null;
    }
  }

  /**
   * Execute transaction.
   * @throws NdbApiException If an error occurs while attempting to execute
   * the transaction.
   */
  public void execute()
    throws NdbApiException {
    if (ndbDATxn != null) {
      ndbDATxn.execute(ExecType.NoCommit, AbortOption.AbortOnError, true);
    }
    if (ndbTxn != null) {
      ndbTxn.execute(ExecType.NoCommit, AbortOption.AbortOnError, true);
    }
  }

  /**
   * Close transaction.
   * @throws NdbApiException If an error occurs while attempting to close the
   * transaction.
   */
  public void close()
    throws NdbApiException {
    try {
      if (ndbDATxn != null) {
        ndbDATxn.close();
      }
      if (ndbTxn != null) {
        ndbTxn.close();
      }
    } finally {
      if (ndb != null) {
        rootContainer.releaseNDB(ndb);
      }
      ndbDATxn = null;
      ndbTxn = null;
      ndb = null;
    }
  }
}
