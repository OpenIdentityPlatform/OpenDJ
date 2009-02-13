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
import com.mysql.cluster.ndbj.NdbApiPermanentException;
import com.mysql.cluster.ndbj.NdbApiTemporaryException;
import com.mysql.cluster.ndbj.NdbError;
import com.mysql.cluster.ndbj.NdbOperation;
import org.opends.messages.Message;

import org.opends.server.api.Backend;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;
import org.opends.server.util.ServerConstants;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.opends.messages.NdbMessages.*;

import org.opends.messages.MessageBuilder;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.ServerConstants.*;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.NdbBackendCfg;
import org.opends.server.backends.ndb.OperationContainer.DN2IDSearchCursor;
import org.opends.server.backends.ndb.OperationContainer.SearchCursorResult;
import org.opends.server.config.ConfigException;

/**
 * Storage container for LDAP entries.  Each base DN of a NDB backend is given
 * its own entry container.  The entry container is the object that implements
 * the guts of the backend API methods for LDAP operations.
 */
public class EntryContainer
    implements ConfigurationChangeListener<NdbBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The backend to which this entry entryContainer belongs.
   */
  private Backend backend;

  /**
   * The root container in which this entryContainer belongs.
   */
  private RootContainer rootContainer;

  /**
   * The baseDN this entry container is responsible for.
   */
  private DN baseDN;

  /**
   * The backend configuration.
   */
  private NdbBackendCfg config;

  /**
   * The operation container.
   */
  private OperationContainer dn2id;

  /**
   * Cached values from config so they don't have to be retrieved per operation.
   */
  private int deadlockRetryLimit;

  private int subtreeDeleteSizeLimit;

  private int subtreeDeleteBatchSize;

  private String databasePrefix;

  /**
   * A read write lock to handle schema changes and bulk changes.
   */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  final Lock sharedLock = lock.readLock();
  final Lock exclusiveLock = lock.writeLock();

  /**
   * Create a new entry entryContainer object.
   *
   * @param baseDN  The baseDN this entry container will be responsible for
   *                storing on disk.
   * @param databasePrefix The prefix to use in the database names used by
   *                       this entry container.
   * @param backend A reference to the NDB backend that is creating this entry
   *                container.
   * @param config The configuration of the NDB backend.
   * @param rootContainer The root container this entry container is in.
   * @throws ConfigException if a configuration related error occurs.
   */
  public EntryContainer(DN baseDN, String databasePrefix, Backend backend,
                        NdbBackendCfg config, RootContainer rootContainer)
      throws ConfigException
  {
    this.backend = backend;
    this.baseDN = baseDN;
    this.config = config;
    this.rootContainer = rootContainer;

    StringBuilder builder = new StringBuilder(databasePrefix.length());
    for (int i = 0; i < databasePrefix.length(); i++)
    {
      char ch = databasePrefix.charAt(i);
      if (Character.isLetterOrDigit(ch))
      {
        builder.append(ch);
      }
      else
      {
        builder.append('_');
      }
    }
    this.databasePrefix = builder.toString();

    this.deadlockRetryLimit = config.getDeadlockRetryLimit();

    config.addNdbChangeListener(this);
  }

  /**
   * Opens the entryContainer for reading and writing.
   *
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws ConfigException if a configuration related error occurs.
   */
  public void open()
      throws NdbApiException, ConfigException
  {
    try
    {
      dn2id = new OperationContainer(BackendImpl.DN2ID_TABLE, this);
    }
    catch (NdbApiException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }
      close();
      throw de;
    }
  }

  /**
   * Closes the entry entryContainer.
   *
   * @throws NdbApiException If an error occurs in the NDB database.
   */
  public void close()
      throws NdbApiException
  {
    config.removeNdbChangeListener(this);

    rootContainer = null;
    backend = null;
    config = null;
    dn2id = null;
  }

  /**
   * Retrieves a reference to the root container in which this entry container
   * exists.
   *
   * @return  A reference to the root container in which this entry container
   *          exists.
   */
  public RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Get the DN database used by this entry entryContainer. The entryContainer
   * must have been opened.
   *
   * @return The DN database.
   */
  public OperationContainer getDN2ID()
  {
    return dn2id;
  }

  /**
   * Processes the specified search in this entryContainer.
   * Matching entries should be provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param searchOperation The search operation to be processed.
   * @throws DirectoryException If a problem occurs while processing
   *         the search.
   * @throws CanceledOperationException If operation is canceled
   *         while in progress.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  public void search(SearchOperation searchOperation)
       throws CanceledOperationException, DirectoryException,
       NdbApiException, NDBException
  {
    DN baseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();

    AbstractTransaction txn = new AbstractTransaction(rootContainer);

    int txnRetries = 0;
    boolean completed = false;
    while (!completed) {
      try {
        // Handle base-object search first.
        if (searchScope == SearchScope.BASE_OBJECT) {
          // Fetch the base entry.
          Entry baseEntry = dn2id.get(txn, baseDN,
            NdbOperation.LockMode.LM_CommittedRead);

          // The base entry must exist for a successful result.
          if (baseEntry == null) {
            // Check for referral entries above the base entry.
            targetEntryReferrals(txn, baseDN, searchScope);

            Message message =
              ERR_NDB_SEARCH_NO_SUCH_OBJECT.get(baseDN.toString());
            DN matchedDN = getMatchedDN(txn, baseDN);
            throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              message, matchedDN, null);
          }

          if (!isManageDsaITOperation(searchOperation)) {
            checkTargetForReferral(baseEntry, searchOperation.getScope());
          }

          if (searchOperation.getFilter().matchesEntry(baseEntry)) {
            searchOperation.returnEntry(baseEntry, null);
          }

          completed = true;
          return;
        }

        IndexFilter indexFilter = new IndexFilter(txn, this, searchOperation);
        if (indexFilter.evaluate()) {
          searchIndexed(searchOperation, indexFilter);
          completed = true;
        } else {
          DN2IDSearchCursor cursor = dn2id.getSearchCursor(txn, baseDN);
          searchNotIndexed(searchOperation, cursor);
          completed = true;
        }
      } catch (NdbApiTemporaryException databaseException) {
        if (txnRetries < BackendImpl.TXN_RETRY_LIMIT) {
          txnRetries++;
          continue;
        }
        throw databaseException;
      } finally {
        if (txn != null) {
          txn.close();
        }
      }
    }
  }

  /**
   * We were able to obtain a set of candidate entries for the
   * search from the indexes.
   */
  private void searchIndexed(SearchOperation searchOperation,
    IndexFilter indexFilter)
       throws CanceledOperationException, NdbApiException,
       DirectoryException, NDBException
  {
    DN baseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);

    AbstractTransaction txn = new AbstractTransaction(rootContainer);
    try {
      // Fetch the base entry.
      Entry baseEntry = null;
      baseEntry = dn2id.get(txn, baseDN, NdbOperation.LockMode.LM_Read);

      // The base entry must exist for a successful result.
      if (baseEntry == null) {
        // Check for referral entries above the base entry.
        targetEntryReferrals(txn, baseDN, searchScope);

        Message message = ERR_NDB_SEARCH_NO_SUCH_OBJECT.get(baseDN.toString());
        DN matchedDN = getMatchedDN(txn, baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          message, matchedDN, null);
      }

      if (!manageDsaIT) {
        checkTargetForReferral(baseEntry, searchScope);
      }

      /*
       * The base entry is only included for whole subtree search.
       */
      if (searchScope == SearchScope.WHOLE_SUBTREE) {
        if (searchOperation.getFilter().matchesEntry(baseEntry)) {
          searchOperation.returnEntry(baseEntry, null);
        }
      }

      int lookthroughCount = 0;
      int lookthroughLimit =
        searchOperation.getClientConnection().getLookthroughLimit();

      indexFilter.scan();
      try {
        long eid = indexFilter.getNext();

        while (eid != 0) {
          if (lookthroughLimit > 0 && lookthroughCount > lookthroughLimit) {
            //Lookthrough limit exceeded
            searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
            searchOperation.appendErrorMessage(
              NOTE_NDB_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
            return;
          }

          // Fetch the candidate entry from the database.
          Entry entry = dn2id.get(txn, eid, NdbOperation.LockMode.LM_Read);

          // We have found a subordinate entry.
          DN dn = entry.getDN();

          boolean isInScope = true;
          if (searchScope == SearchScope.SINGLE_LEVEL) {
            // Check if this entry is an immediate child.
            if ((dn.getNumComponents() !=
              baseDN.getNumComponents() + 1)) {
              isInScope = false;
            }
          }

          if (isInScope) {
            // Process the candidate entry.
            if (entry != null) {
              lookthroughCount++;
              if (manageDsaIT || !entry.isReferral()) {
                // Filter the entry.
                if (searchOperation.getFilter().matchesEntry(entry)) {
                  if (!searchOperation.returnEntry(entry, null)) {
                    // We have been told to discontinue processing of the
                    // search. This could be due to size limit exceeded or
                    // operation cancelled.
                    return;
                  }
                }
              } else {
                if (entry.isReferral()) {
                  try {
                    checkTargetForReferral(entry, searchScope);
                  } catch (DirectoryException refException) {
                    if (refException.getResultCode() == ResultCode.REFERRAL) {
                      SearchResultReference reference =
                        new SearchResultReference(
                        refException.getReferralURLs());
                      if (!searchOperation.returnReference(dn, reference)) {
                        // We have been told to discontinue processing of the
                        // search. This could be due to size limit exceeded or
                        // operation cancelled.
                        return;
                      }
                    } else {
                      throw refException;
                    }
                  }
                }
              }
            }
          }

          searchOperation.checkIfCanceled(false);

          // Move to the next record.
          eid = indexFilter.getNext();
        }
      } finally {
        indexFilter.close();
      }
    } finally {
      if (txn != null) {
        txn.close();
      }
    }
  }

  /**
   * We were not able to obtain a set of candidate entries for the
   * search from the indexes.
   */
  private void searchNotIndexed(SearchOperation searchOperation,
    DN2IDSearchCursor cursor)
       throws CanceledOperationException, NdbApiException,
       DirectoryException, NDBException
  {
    DN baseDN = searchOperation.getBaseDN();
    SearchScope searchScope = searchOperation.getScope();
    boolean manageDsaIT = isManageDsaITOperation(searchOperation);

    AbstractTransaction txn = new AbstractTransaction(rootContainer);
    try {
      // Fetch the base entry.
      Entry baseEntry = null;
      baseEntry = dn2id.get(txn, baseDN, NdbOperation.LockMode.LM_Read);

      // The base entry must exist for a successful result.
      if (baseEntry == null) {
        // Check for referral entries above the base entry.
        targetEntryReferrals(txn, baseDN, searchScope);

        Message message = ERR_NDB_SEARCH_NO_SUCH_OBJECT.get(baseDN.toString());
        DN matchedDN = getMatchedDN(txn, baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          message, matchedDN, null);
      }

      if (!manageDsaIT) {
        checkTargetForReferral(baseEntry, searchScope);
      }

      /*
       * The base entry is only included for whole subtree search.
       */
      if (searchScope == SearchScope.WHOLE_SUBTREE) {
        if (searchOperation.getFilter().matchesEntry(baseEntry)) {
          searchOperation.returnEntry(baseEntry, null);
        }
      }

      int lookthroughCount = 0;
      int lookthroughLimit =
        searchOperation.getClientConnection().getLookthroughLimit();

      cursor.open();
      try {
        SearchCursorResult result = cursor.getNext();

        while (result != null) {
          if (lookthroughLimit > 0 && lookthroughCount > lookthroughLimit) {
            //Lookthrough limit exceeded
            searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
            searchOperation.appendErrorMessage(
              NOTE_NDB_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
            return;
          }

          // We have found a subordinate entry.
          DN dn = DN.decode(result.dn);

          boolean isInScope = true;
          if (searchScope == SearchScope.SINGLE_LEVEL) {
            // Check if this entry is an immediate child.
            if ((dn.getNumComponents() !=
              baseDN.getNumComponents() + 1)) {
              isInScope = false;
            }
          }

          if (isInScope) {
            // Fetch the candidate entry from the database.
            Entry entry = dn2id.get(txn, dn, NdbOperation.LockMode.LM_Read);
            // Process the candidate entry.
            if (entry != null) {
              lookthroughCount++;
              if (manageDsaIT || !entry.isReferral()) {
                // Filter the entry.
                if (searchOperation.getFilter().matchesEntry(entry)) {
                  if (!searchOperation.returnEntry(entry, null)) {
                    // We have been told to discontinue processing of the
                    // search. This could be due to size limit exceeded or
                    // operation cancelled.
                    return;
                  }
                }
              } else {
                if (entry.isReferral()) {
                  try {
                    checkTargetForReferral(entry, searchScope);
                  } catch (DirectoryException refException) {
                    if (refException.getResultCode() == ResultCode.REFERRAL) {
                      SearchResultReference reference =
                        new SearchResultReference(
                        refException.getReferralURLs());
                      if (!searchOperation.returnReference(dn, reference)) {
                        // We have been told to discontinue processing of the
                        // search. This could be due to size limit exceeded or
                        // operation cancelled.
                        return;
                      }
                    } else {
                      throw refException;
                    }
                  }
                }
              }
            }
          }

          searchOperation.checkIfCanceled(false);

          // Move to the next record.
          result = cursor.getNext();
        }
      } finally {
        cursor.close();
      }
    } finally {
      if (txn != null) {
        txn.close();
      }
    }
  }

  /**
   * Adds the provided entry to this database.  This method must ensure that the
   * entry is appropriate for the database and that no entry already exists with
   * the same DN.  The caller must hold a write lock on the DN of the provided
   * entry.
   *
   * @param entry        The entry to add to this database.
   * @param addOperation The add operation with which the new entry is
   *                     associated.  This may be <CODE>null</CODE> for adds
   *                     performed internally.
   * @param txn          Abstract transaction for this operation.
   * @throws DirectoryException If a problem occurs while trying to add the
   *                            entry.
   * @throws CanceledOperationException If operation is canceled
   *         while in progress.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  public void addEntry(Entry entry, AddOperation addOperation,
    AbstractTransaction txn)
      throws CanceledOperationException, NdbApiException,
      DirectoryException, NDBException
  {
    TransactedOperation operation = new AddEntryTransaction(entry);
    invokeTransactedOperation(txn, operation, addOperation, true, false);
  }

  /**
   * Adds the provided entry to this database.  This method must ensure that the
   * entry is appropriate for the database and that no entry already exists with
   * the same DN.  The caller must hold a write lock on the DN of the provided
   * entry.
   *
   * @param entry        The entry to add to this database.
   * @param addOperation The add operation with which the new entry is
   *                     associated.  This may be <CODE>null</CODE> for adds
   *                     performed internally.
   * @param txn          Abstract transaction for this operation.
   * @throws DirectoryException If a problem occurs while trying to add the
   *                            entry.
   * @throws CanceledOperationException If operation is canceled
   *         while in progress.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  public void addEntryNoCommit(Entry entry, AddOperation addOperation,
    AbstractTransaction txn)
      throws CanceledOperationException, NdbApiException,
      DirectoryException, NDBException
  {
    TransactedOperation operation = new AddEntryTransaction(entry);
    invokeTransactedOperation(txn, operation, addOperation, false, false);
  }

  /**
   * This method is common to all operations invoked under a database
   * transaction. It retries the operation if the transaction is
   * aborted due to a deadlock condition, up to a configured maximum
   * number of retries.
   *
   * @param operation An object implementing the TransactedOperation interface.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  private void invokeTransactedOperation(AbstractTransaction txn,
    TransactedOperation operation, Operation ldapOperation,
    boolean commit, boolean locked)
      throws CanceledOperationException, NdbApiException,
      DirectoryException, NDBException
  {
    // Attempt the operation under a transaction until it fails or completes.
    int txnRetries = 0;
    boolean completed = false;

    while (!completed)
    {
      try
      {
        // Invoke the operation.
        operation.invokeOperation(txn);

        // One last check before committing.
        if (ldapOperation != null) {
          ldapOperation.checkIfCanceled(true);
        }

        // Commit the transaction.
        if (commit) {
          txn.commit();
        }
        completed = true;
      }
      catch (NdbApiTemporaryException databaseException)
      {
        if (!locked) {
          if (txnRetries < BackendImpl.TXN_RETRY_LIMIT) {
            if (txn != null) {
              txn.close();
            }
            txnRetries++;
            continue;
          }
        }
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR,
            databaseException);
        }
        throw databaseException;
      }
      catch (NdbApiPermanentException databaseException)
      {
        throw databaseException;
      }
      catch (DirectoryException directoryException)
      {
        throw directoryException;
      }
      catch (NDBException NDBException)
      {
        throw NDBException;
      }
      catch (Exception e)
      {
        Message message = ERR_NDB_UNCHECKED_EXCEPTION.get();
        throw new NDBException(message, e);
      }
      finally {
        if (commit) {
          if (txn != null) {
            txn.close();
          }
        }
      }
    }

    // Do any actions necessary after successful commit,
    // usually to update the entry cache.
    operation.postCommitAction();
  }

  /**
   * This interface represents any kind of operation on the database
   * that must be performed under a transaction. A class which implements
   * this interface does not need to be concerned with creating the
   * transaction nor retrying the transaction after deadlock.
   */
  private interface TransactedOperation
  {
    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws NdbApiException If an error occurs in the NDB database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NDBException If an error occurs in the NDB backend.
     */
    public abstract void invokeOperation(AbstractTransaction txn)
        throws NdbApiException, DirectoryException,
        CanceledOperationException, NDBException;

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public abstract void postCommitAction();
  }

  /**
   * This inner class implements the Add Entry operation through
   * the TransactedOperation interface.
   */
  private class AddEntryTransaction implements TransactedOperation
  {
    /**
     * The entry to be added.
     */
    private Entry entry;

    /**
     * The DN of the superior entry of the entry to be added.  This can be
     * null if the entry to be added is a base entry.
     */
    DN parentDN;

    /**
     * The ID of the entry once it has been assigned.
     */
    long entryID;

    /**
     * Create a new Add Entry NdbTransaction.
     * @param entry The entry to be added.
     */
    public AddEntryTransaction(Entry entry)
    {
      this.entry = entry;
      this.parentDN = getParentWithinBase(entry.getDN());
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws NdbApiException If an error occurs in the NDB database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NDBException If an error occurs in the NDB backend.
     */
    public void invokeOperation(AbstractTransaction txn)
        throws NdbApiException, DirectoryException, NDBException
    {
      // Check that the parent entry exists.
      if (parentDN != null) {
        // Check for referral entries above the target.
        targetEntryReferrals(txn, entry.getDN(), null);
        long parentID = dn2id.getID(txn, parentDN,
          NdbOperation.LockMode.LM_Read);
        if (parentID == 0) {
          Message message = ERR_NDB_ADD_NO_SUCH_OBJECT.get(
                  entry.getDN().toString());
          DN matchedDN = getMatchedDN(txn, baseDN);
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              message, matchedDN, null);
        }
      }

      // First time through, assign the next entryID.
      if (entryID == 0)
      {
        entryID = rootContainer.getNextEntryID(txn.getNdb());
      }

      // Insert.
      try {
        dn2id.insert(txn, entry.getDN(), entryID, entry);
        txn.execute();
      } catch (NdbApiException ne) {
        if (ne.getErrorObj().getClassification() ==
          NdbError.Classification.ConstraintViolation)
        {
          Message message =
            ERR_NDB_ADD_ENTRY_ALREADY_EXISTS.get(entry.getDN().toString());
          throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
            message);
        } else {
          throw ne;
        }
      }
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {

    }
  }

  /**
   * Removes the specified entry from this database.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the database supports a subtree delete operation and the client
   * included the appropriate information in the request).
   * The caller must hold a write lock on the provided entry DN.
   *
   * @param entryDN         The DN of the entry to remove from this database.
   * @param entry           The entry to delete.
   * @param deleteOperation The delete operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        deletes performed internally.
   * @param txn             Abstract transaction for this operation.
   * @throws DirectoryException If a problem occurs while trying to remove the
   *                            entry.
   * @throws CanceledOperationException If operation is canceled
   *         while in progress.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  public void deleteEntry(DN entryDN, Entry entry,
    DeleteOperation deleteOperation, AbstractTransaction txn)
    throws CanceledOperationException, DirectoryException,
    NdbApiException, NDBException
  {
    DeleteEntryTransaction operation =
        new DeleteEntryTransaction(entryDN, entry, deleteOperation);

    boolean isComplete = false;

    while(!isComplete)
    {
      invokeTransactedOperation(txn, operation, deleteOperation, true, true);

      if (operation.adminSizeLimitExceeded())
      {
        Message message = NOTE_NDB_SUBTREE_DELETE_SIZE_LIMIT_EXCEEDED.get(
                operation.getDeletedEntryCount());
        throw new DirectoryException(
          ResultCode.ADMIN_LIMIT_EXCEEDED,
          message);
      }
      if(operation.batchSizeExceeded())
      {
        operation.resetBatchSize();
        continue;
      }
      isComplete = true;
      Message message =
          NOTE_NDB_DELETED_ENTRY_COUNT.get(operation.getDeletedEntryCount());
      MessageBuilder errorMessage = new MessageBuilder();
      errorMessage.append(message);
      deleteOperation.setErrorMessage(errorMessage);
    }
  }

  /**
   * Removes the specified entry from this database.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the database supports a subtree delete operation and the client
   * included the appropriate information in the request).
   * The caller must hold a write lock on the provided entry DN.
   *
   * @param entryDN         The DN of the entry to remove from this database.
   * @param entry           The entry to delete.
   * @param deleteOperation The delete operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        deletes performed internally.
   * @param txn             Abstract transaction for this operation.
   * @throws DirectoryException If a problem occurs while trying to remove the
   *                            entry.
   * @throws CanceledOperationException If operation is canceled
   *         while in progress.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  public void deleteEntryNoCommit(DN entryDN, Entry entry,
    DeleteOperation deleteOperation, AbstractTransaction txn)
    throws CanceledOperationException, DirectoryException,
    NdbApiException, NDBException
  {
    DeleteEntryTransaction operation =
        new DeleteEntryTransaction(entryDN, entry, deleteOperation);

    boolean isComplete = false;

    while(!isComplete)
    {
      invokeTransactedOperation(txn, operation, deleteOperation, false, true);

      if (operation.adminSizeLimitExceeded())
      {
        Message message = NOTE_NDB_SUBTREE_DELETE_SIZE_LIMIT_EXCEEDED.get(
                operation.getDeletedEntryCount());
        throw new DirectoryException(
          ResultCode.ADMIN_LIMIT_EXCEEDED,
          message);
      }
      if(operation.batchSizeExceeded())
      {
        operation.resetBatchSize();
        continue;
      }
      isComplete = true;
      Message message =
          NOTE_NDB_DELETED_ENTRY_COUNT.get(operation.getDeletedEntryCount());
      MessageBuilder errorMessage = new MessageBuilder();
      errorMessage.append(message);
      deleteOperation.setErrorMessage(errorMessage);
    }
  }

  /**
   * Delete a leaf entry.
   * The caller must be sure that the entry is indeed a leaf.
   *
   * @param txn    The abstract transaction.
   * @param leafDN The DN of the leaf entry to be deleted.
   * @param leafID The ID of the leaf entry.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  private void deleteLeaf(AbstractTransaction txn,
                          DN leafDN,
                          long leafID,
                          DeleteOperation operation)
      throws NdbApiException, DirectoryException, NDBException
  {
    Entry entry = dn2id.get(txn, leafDN, NdbOperation.LockMode.LM_Exclusive);

    // Check that the entry exists.
    if (entry == null)
    {
      Message msg = ERR_NDB_MISSING_ID2ENTRY_RECORD.get(Long.toString(leafID));
      throw new NDBException(msg);
    }

    if (!isManageDsaITOperation(operation))
    {
      checkTargetForReferral(entry, null);
    }

    // Remove from dn2id.
    if (!dn2id.remove(txn, entry))
    {
      Message msg = ERR_NDB_MISSING_ID2ENTRY_RECORD.get(Long.toString(leafID));
      throw new NDBException(msg);
    }
  }

  /**
   * Delete the target entry of a delete operation, with appropriate handling
   * of referral entries. The caller must be sure that the entry is indeed a
   * leaf.
   *
   * @param txn    The abstract transaction.
   * @param leafDN The DN of the target entry to be deleted.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  private void deleteTarget(AbstractTransaction txn,
                            DN leafDN, Entry entry,
                            DeleteOperation operation)
      throws NdbApiException, DirectoryException, NDBException
  {
    // Check that the entry exists.
    if (entry == null)
    {
      Message message = ERR_NDB_DELETE_NO_SUCH_OBJECT.get(leafDN.toString());
      DN matchedDN = getMatchedDN(txn, baseDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          message, matchedDN, null);
    }

    if (!isManageDsaITOperation(operation))
    {
      checkTargetForReferral(entry, null);
    }

    // Remove from dn2id.
    if (!dn2id.remove(txn, entry))
    {
      Message msg = ERR_NDB_MISSING_DN2ID_RECORD.get(leafDN.toString());
      throw new NDBException(msg);
    }
  }

  /**
   * This inner class implements the Delete Entry operation through
   * the TransactedOperation interface.
   */
  private class DeleteEntryTransaction implements TransactedOperation
  {
    /**
     * The DN of the entry or subtree to be deleted.
     */
    private DN entryDN;

    /**
     * The entry itself.
     */
    private Entry entry;

    /**
     * The Delete operation.
     */
    private DeleteOperation deleteOperation;

    /**
     * A list of the DNs of all entries deleted by this operation in a batch.
     * The subtree delete control can cause multiple entries to be deleted.
     */
    private ArrayList<DN> deletedDNList;


    /**
     * Indicates whether the subtree delete size limit has been exceeded.
     */
    private boolean adminSizeLimitExceeded = false;


    /**
     * Indicates whether the subtree delete batch size has been exceeded.
     */
    private boolean batchSizeExceeded = false;


    /**
     * Indicates the count of deleted DNs in the Delete Operation.
     */
    private int countDeletedDN;

    /**
     * Create a new Delete Entry NdbTransaction.
     * @param entryDN The entry or subtree to be deleted.
     * @param deleteOperation The Delete operation.
     */
    public DeleteEntryTransaction(DN entryDN, Entry entry,
      DeleteOperation deleteOperation)
    {
      this.entryDN = entryDN;
      this.entry = entry;
      this.deleteOperation = deleteOperation;
      deletedDNList = new ArrayList<DN>();
    }

    /**
     * Determine whether the subtree delete size limit has been exceeded.
     * @return true if the size limit has been exceeded.
     */
    public boolean adminSizeLimitExceeded()
    {
      return adminSizeLimitExceeded;
    }

    /**
     * Determine whether the subtree delete batch size has been exceeded.
     * @return true if the batch size has been exceeded.
     */
    public boolean batchSizeExceeded()
    {
      return batchSizeExceeded;
    }

    /**
     * Resets the batchSizeExceeded parameter to reuse the object
     * for multiple batches.
     */
    public void resetBatchSize()
    {
      batchSizeExceeded=false;
      deletedDNList.clear();
    }

    /**
     * Get the number of entries deleted during the operation.
     * @return The number of entries deleted.
     */
    public int getDeletedEntryCount()
    {
      return countDeletedDN;
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws NdbApiException If an error occurs in the NDB database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NDBException If an error occurs in the NDB backend.
     */
    public void invokeOperation(AbstractTransaction txn)
        throws CanceledOperationException, NdbApiException,
        DirectoryException, NDBException
    {
      // Check for referral entries above the target entry.
      targetEntryReferrals(txn, entryDN, null);

      // Determine whether this is a subtree delete.
      int adminSizeLimit = subtreeDeleteSizeLimit;
      int deleteBatchSize = subtreeDeleteBatchSize;
      boolean isSubtreeDelete = false;
      List<Control> controls = deleteOperation.getRequestControls();
      if (controls != null)
      {
        for (Control control : controls)
        {
          if (control.getOID().equals(OID_SUBTREE_DELETE_CONTROL))
          {
            isSubtreeDelete = true;
          }
        }
      }

      if (dn2id.hasSubordinates(txn, entryDN) && !isSubtreeDelete) {
        // The subtree delete control was not specified and
        // the target entry is not a leaf.
        Message message =
          ERR_NDB_DELETE_NOT_ALLOWED_ON_NONLEAF.get(entryDN.toString());
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
          message);
      }

      if (isSubtreeDelete) {
        AbstractTransaction cursorTxn =
          new AbstractTransaction(rootContainer);
        DN2IDSearchCursor cursor = dn2id.getSearchCursor(cursorTxn, entryDN);
        cursor.open();
        try {
          SearchCursorResult result = cursor.getNext();

          while (result != null) {
            // We have found a subordinate entry.
            if (!isSubtreeDelete) {
              // The subtree delete control was not specified and
              // the target entry is not a leaf.
              Message message =
                ERR_NDB_DELETE_NOT_ALLOWED_ON_NONLEAF.get(entryDN.toString());
              throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
                message);
            }

            // Enforce any subtree delete size limit.
            if (adminSizeLimit > 0 && countDeletedDN >= adminSizeLimit) {
              adminSizeLimitExceeded = true;
              break;
            }

            // Enforce any subtree delete batch size.
            if (deleteBatchSize > 0 &&
              deletedDNList.size() >= deleteBatchSize) {
              batchSizeExceeded = true;
              break;
            }

            /*
             * Delete this entry which by now must be a leaf because
             * we have been deleting from the bottom of the tree upwards.
             */
            long entryID = result.id;
            DN subordinateDN = DN.decode(result.dn);

            deleteLeaf(txn, subordinateDN, entryID, deleteOperation);

            deletedDNList.add(subordinateDN);
            countDeletedDN++;

            if (deleteOperation != null) {
              deleteOperation.checkIfCanceled(false);
            }

            result = cursor.getNext();
          }
        } finally {
          cursor.close();
          cursorTxn.close();
        }
      }

      // Finally delete the target entry as it was not included
      // in the dn2id iteration.
      if (!adminSizeLimitExceeded && !batchSizeExceeded)
      {
        // Enforce any subtree delete size limit.
        if (adminSizeLimit > 0 && countDeletedDN >= adminSizeLimit)
        {
          adminSizeLimitExceeded = true;
        }
        else if (deleteBatchSize > 0 &&
                                      deletedDNList.size() >= deleteBatchSize)
        {
          batchSizeExceeded = true;
        }
        else
        {
          deleteTarget(txn, entryDN, entry, deleteOperation);
          deletedDNList.add(entryDN);
          countDeletedDN++;
        }
      }
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {

    }
  }

  /**
   * Indicates whether an entry with the specified DN exists.
   *
   * @param  txn      Abstract transaction for this operation.
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists,
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   * @throws  NdbApiException     An error occurred during a database operation.
   */
  public boolean entryExists(AbstractTransaction txn, DN entryDN)
      throws DirectoryException, NdbApiException
  {
    // Read the ID from dn2id.
    long id = 0;

    try
    {
      id = dn2id.getID(txn, entryDN, NdbOperation.LockMode.LM_CommittedRead);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return id != 0;
  }

  /**
   * Fetch an entry by DN retrieves the requested entry.
   * Note that the caller must hold a read or write lock
   * on the specified DN.
   *
   * @param entryDN  The distinguished name of the entry to retrieve.
   * @param txn      Abstract transaction for this operation.
   * @param lockMode Operation lock mode.
   * @return The requested entry, or <CODE>null</CODE> if the entry does not
   *         exist.
   * @throws DirectoryException If a problem occurs while trying to retrieve
   *                            the entry.
   * @throws NDBException If an error occurs in the NDB backend.
   * @throws NdbApiException An error occurred during a database operation.
   */
  public Entry getEntryNoCommit(DN entryDN, AbstractTransaction txn,
    NdbOperation.LockMode lockMode)
      throws NDBException, NdbApiException, DirectoryException
  {
    Entry entry = null;

    GetEntryByDNOperation operation =
      new GetEntryByDNOperation(entryDN, lockMode);

    try {
      // Fetch the entry from the database.
      invokeTransactedOperation(txn, operation, null, false, false);
    } catch (CanceledOperationException ex) {
      // No LDAP operation, ignore.
    }

    entry = operation.getEntry();

    return entry;
  }

  /**
   * Fetch an entry by DN retrieves the requested entry.
   * Note that the caller must hold a read or write lock
   * on the specified DN.
   *
   * @param entryDN The distinguished name of the entry to retrieve.
   * @return The requested entry, or <CODE>null</CODE> if the entry does not
   *         exist.
   * @throws DirectoryException If a problem occurs while trying to retrieve
   *                            the entry.
   * @throws NDBException If an error occurs in the NDB backend.
   * @throws NdbApiException An error occurred during a database operation.
   */
  public Entry getEntry(DN entryDN)
      throws NDBException, NdbApiException, DirectoryException
  {
    Entry entry = null;

    GetEntryByDNOperation operation = new GetEntryByDNOperation(entryDN,
      NdbOperation.LockMode.LM_CommittedRead);
    AbstractTransaction txn = new AbstractTransaction(rootContainer);

    try {
      // Fetch the entry from the database.
      invokeTransactedOperation(txn, operation, null, true, false);
    } catch (CanceledOperationException ex) {
      // No LDAP operation, ignore.
    }

    entry = operation.getEntry();

    return entry;
  }

  /**
   * This inner class gets an entry by DN through
   * the TransactedOperation interface.
   */
  private class GetEntryByDNOperation implements TransactedOperation
  {
    /**
     * The retrieved entry.
     */
    private Entry entry = null;

    /**
     * The ID of the retrieved entry.
     */
    private long entryID = 0;

    /**
     * Operation lock mode.
     */
    private NdbOperation.LockMode lockMode;

    /**
     * The DN of the entry to be retrieved.
     */
    DN entryDN;

    /**
     * Create a new transacted operation to retrieve an entry by DN.
     * @param entryDN The DN of the entry to be retrieved.
     */
    public GetEntryByDNOperation(DN entryDN, NdbOperation.LockMode lockMode)
    {
      this.entryDN = entryDN;
      this.lockMode = lockMode;
    }

    /**
     * Get the retrieved entry.
     * @return The retrieved entry.
     */
    public Entry getEntry()
    {
      return entry;
    }

    /**
     * Get the ID of the retrieved entry.
     * @return The ID of the retrieved entry.
     */
    public long getEntryID()
    {
      return entryID;
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation
     * @throws NdbApiException If an error occurs in the NDB database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NDBException If an error occurs in the NDB backend.
     */
    public void invokeOperation(AbstractTransaction txn)
      throws NdbApiException, DirectoryException, NDBException
    {
      entry = dn2id.get(txn, entryDN, lockMode);

      if (entry == null) {
        // Check for referral entries above the target entry.
        targetEntryReferrals(txn, entryDN, null);
      }
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // No implementation required.
    }
  }

  /**
   * The simplest case of replacing an entry in which the entry DN has
   * not changed.
   *
   * @param oldEntry           The old contents of the entry.
   * @param newEntry           The new contents of the entry
   * @param modifyOperation The modify operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        modifications performed internally.
   * @param txn             Abstract transaction for this operation.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws DirectoryException If a Directory Server error occurs.
   * @throws CanceledOperationException If operation is canceled
   *         while in progress.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  public void replaceEntry(Entry oldEntry, Entry newEntry,
    ModifyOperation modifyOperation, AbstractTransaction txn)
       throws CanceledOperationException, NdbApiException,
       DirectoryException, NDBException
  {
    TransactedOperation operation =
         new ReplaceEntryTransaction(oldEntry, newEntry, modifyOperation);

    invokeTransactedOperation(txn, operation, modifyOperation, true, true);
  }

  /**
   * This inner class implements the Replace Entry operation through
   * the TransactedOperation interface.
   */
  private class ReplaceEntryTransaction implements TransactedOperation
  {
    /**
     * The new contents of the entry.
     */
    private Entry newEntry;

    /**
     * The old contents of the entry.
     */
    private Entry oldEntry;

    /**
     * The Modify operation, or null if the replace is not due to a Modify
     * operation.
     */
    private ModifyOperation modifyOperation;

    /**
     * The ID of the entry that was replaced.
     */
    private Long entryID;

    /**
     * Create a new transacted operation to replace an entry.
     * @param entry The new contents of the entry.
     * @param modifyOperation The Modify operation, or null if the replace is
     * not due to a Modify operation.
     */
    public ReplaceEntryTransaction(Entry oldEntry, Entry newEntry,
                                   ModifyOperation modifyOperation)
    {
      this.oldEntry = oldEntry;
      this.newEntry = newEntry;
      this.modifyOperation = modifyOperation;
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws NdbApiException If an error occurs in the NDB database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NDBException If an error occurs in the NDB backend.
     */
    public void invokeOperation(AbstractTransaction txn) throws NdbApiException,
                                                        DirectoryException,
                                                        NDBException
    {
      DN entryDN = newEntry.getDN();
      entryID = (Long) oldEntry.getAttachment();
      if (entryID == 0)
      {
        // The entry does not exist.
        Message message =
                ERR_NDB_MODIFY_NO_SUCH_OBJECT.get(entryDN.toString());
        DN matchedDN = getMatchedDN(txn, baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
            message, matchedDN, null);
      }

      if (!isManageDsaITOperation(modifyOperation))
      {
        // Check if the entry is a referral entry.
        checkTargetForReferral(oldEntry, null);
      }

      // List<Modification> modsList = modifyOperation.getModifications();

      // Replace.
      if (!dn2id.put(txn, entryDN, entryID, newEntry, oldEntry))
      {
        // The entry does not exist.
        Message msg = ERR_NDB_MISSING_ID2ENTRY_RECORD.get(
          Long.toString(entryID));
        throw new NDBException(msg);
      }
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {

    }
  }

  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.  The caller must hold write locks on both
   * the current DN and the new DN for the entry.
   *
   * @param currentDN         The current DN of the entry to be replaced.
   * @param entry             The new content to use for the entry.
   * @param modifyDNOperation The modify DN operation with which this action
   *                          is associated.  This may be <CODE>null</CODE>
   *                          for modify DN operations performed internally.
   * @param txn               Abstract transaction for this operation.
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs while trying to perform
   *          the rename.
   * @throws org.opends.server.types.CanceledOperationException
   *          If this backend noticed and reacted
   *          to a request to cancel or abandon the
   *          modify DN operation.
   * @throws NdbApiException If an error occurs in the NDB database.
   * @throws NDBException If an error occurs in the NDB backend.
   */
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation,
                          AbstractTransaction txn)
      throws NdbApiException, NDBException, DirectoryException,
      CanceledOperationException {
    TransactedOperation operation =
        new RenameEntryTransaction(currentDN, entry, modifyDNOperation);

    invokeTransactedOperation(txn, operation, modifyDNOperation, true, true);
  }

  /**
   * This inner class implements the Modify DN operation through
   * the TransactedOperation interface.
   */
  private class RenameEntryTransaction implements TransactedOperation
  {
    /**
     * The DN of the entry to be renamed.
     */
    private DN oldApexDN;

    /**
     * The DN of the superior entry of the entry to be renamed.
     * This is null if the entry to be renamed is a base entry.
     */
    private DN oldSuperiorDN;

    /**
     * The DN of the new superior entry, which can be the same
     * as the current superior entry.
     */
    private DN newSuperiorDN;

    /**
     * The new contents of the entry to be renamed.
     */
    private Entry newApexEntry;

    /**
     * The Modify DN operation.
     */
    private ModifyDNOperation modifyDNOperation;

    /**
     * Create a new transacted operation for a Modify DN operation.
     * @param currentDN The DN of the entry to be renamed.
     * @param entry The new contents of the entry.
     * @param modifyDNOperation The Modify DN operation to be performed.
     */
    public RenameEntryTransaction(DN currentDN, Entry entry,
                                  ModifyDNOperation modifyDNOperation)
    {
      this.oldApexDN = currentDN;
      this.oldSuperiorDN = getParentWithinBase(currentDN);
      this.newSuperiorDN = getParentWithinBase(entry.getDN());
      this.newApexEntry = entry;
      this.modifyDNOperation = modifyDNOperation;
    }

    /**
     * Invoke the operation under the given transaction.
     *
     * @param txn The transaction to be used to perform the operation.
     * @throws NdbApiException If an error occurs in the NDB database.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NDBException If an error occurs in the NDB backend.
     */
    public void invokeOperation(AbstractTransaction txn)
      throws NdbApiException, DirectoryException,
      CanceledOperationException, NDBException
    {
      DN requestedNewSuperiorDN = null;

      if (modifyDNOperation != null)
      {
        requestedNewSuperiorDN = modifyDNOperation.getNewSuperior();
      }

      // Check whether the renamed entry already exists.
      if (dn2id.getID(txn, newApexEntry.getDN(),
        NdbOperation.LockMode.LM_Exclusive) != 0)
      {
        Message message = ERR_NDB_MODIFYDN_ALREADY_EXISTS.get(
            newApexEntry.getDN().toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message);
      }

      Entry oldApexEntry = dn2id.get(txn, oldApexDN,
        NdbOperation.LockMode.LM_Exclusive);
      if (oldApexEntry == null)
      {
        // Check for referral entries above the target entry.
        targetEntryReferrals(txn, oldApexDN, null);

        Message message =
                ERR_NDB_MODIFYDN_NO_SUCH_OBJECT.get(oldApexDN.toString());
        DN matchedDN = getMatchedDN(txn, baseDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
            message, matchedDN, null);
      }

      if (!isManageDsaITOperation(modifyDNOperation))
      {
        checkTargetForReferral(oldApexEntry, null);
      }

      long oldApexID = (Long) oldApexEntry.getAttachment();
      long newApexID = oldApexID;

      if (newSuperiorDN != null)
      {
        long newSuperiorID = dn2id.getID(txn, newSuperiorDN,
          NdbOperation.LockMode.LM_Exclusive);
        if (newSuperiorID == 0)
        {
          Message msg =
                  ERR_NDB_NEW_SUPERIOR_NO_SUCH_OBJECT.get(
                          newSuperiorDN.toString());
          DN matchedDN = getMatchedDN(txn, baseDN);
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
              msg, matchedDN, null);
        }
        newApexID = rootContainer.getNextEntryID(txn.getNdb());
      }

      // Move or rename the apex entry.
      if (requestedNewSuperiorDN != null)
      {
        moveApexEntry(txn, newApexID, oldApexEntry, newApexEntry);
      }
      else
      {
        long newID = rootContainer.getNextEntryID(txn.getNdb());
        renameApexEntry(txn, newID, oldApexEntry, newApexEntry);
      }

      AbstractTransaction cursorTxn =
          new AbstractTransaction(rootContainer);
      DN2IDSearchCursor cursor = dn2id.getSearchCursor(cursorTxn, oldApexDN);
      cursor.open();

      try {
        SearchCursorResult result = cursor.getNext();
        // Step forward until we pass the ending value.
        while (result != null) {
          // We have found a subordinate entry.
          long oldID = result.id;
          String oldDN = result.dn;
          Entry oldEntry = dn2id.get(txn, DN.decode(oldDN),
            NdbOperation.LockMode.LM_Exclusive);

          if (!isManageDsaITOperation(modifyDNOperation)) {
            checkTargetForReferral(oldEntry, null);
          }

          // Construct the new DN of the entry.
          DN newDN = modDN(oldEntry.getDN(),
            oldApexDN.getNumComponents(),
            newApexEntry.getDN());

          if (requestedNewSuperiorDN != null) {
            // Assign a new entry ID if we are renumbering.
            long newID = oldID;
            if (newApexID != oldApexID) {
              newID = rootContainer.getNextEntryID(txn.getNdb());
            }

            // Move this entry.
            moveSubordinateEntry(txn, newID, oldEntry, newDN);
          } else {
            // Rename this entry.
            renameSubordinateEntry(txn, oldID, oldEntry, newDN);
          }

          if (modifyDNOperation != null) {
            modifyDNOperation.checkIfCanceled(false);
          }

          result = cursor.getNext();
        }
      } finally {
        cursor.close();
        cursorTxn.close();
      }
    }

    /**
     * Update the database for the target entry of a ModDN operation
     * specifying a new superior.
     *
     * @param txn The abstract transaction to be used for the updates.
     * @param newID The new ID of the target entry, or the original ID if
     *              the ID has not changed.
     * @param oldEntry The original contents of the target entry.
     * @param newEntry The new contents of the target entry.
     * @throws NDBException If an error occurs in the NDB backend.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NdbApiException If an error occurs in the NDB database.
     */
    private void moveApexEntry(AbstractTransaction txn,
                               long newID, Entry oldEntry, Entry newEntry)
        throws NDBException, DirectoryException, NdbApiException
    {
      // DN oldDN = oldEntry.getDN();
      DN newDN = newEntry.getDN();

      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldEntry);

      // Insert the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, newID, newEntry))
      {
        Message message = ERR_NDB_MODIFYDN_ALREADY_EXISTS.get(newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message);
      }
    }

    /**
     * Update the database for the target entry of a Modify DN operation
     * not specifying a new superior.
     *
     * @param txn The abstract transaction to be used for the updates.
     * @param newID The new ID of the target entry.
     * @param oldEntry The original contents of the target entry.
     * @param newEntry The new contents of the target entry.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NdbApiException If an error occurs in the NDB database.
     * @throws NDBException if an error occurs in the NDB Backend.
     */
    private void renameApexEntry(AbstractTransaction txn, long newID,
                                 Entry oldEntry, Entry newEntry)
        throws DirectoryException, NdbApiException, NDBException
    {
      // DN oldDN = oldEntry.getDN();
      DN newDN = newEntry.getDN();

      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldEntry);

      // Insert the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, newID, newEntry))
      {
        Message message = ERR_NDB_MODIFYDN_ALREADY_EXISTS.get(newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message);
      }
    }

    /**
     * Update the database for a subordinate entry of the target entry
     * of a Modify DN operation specifying a new superior.
     *
     * @param txn The abstract transaction to be used for the updates.
     * @param newID The new ID of the subordinate entry, or the original ID if
     *              the ID has not changed.
     * @param oldEntry The original contents of the subordinate entry.
     * @param newDN The new DN of the subordinate entry.
     * @throws NDBException If an error occurs in the NDB backend.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NdbApiException If an error occurs in the NDB database.
     */
    private void moveSubordinateEntry(AbstractTransaction txn,
                                      long newID,
                                      Entry oldEntry, DN newDN)
        throws NDBException, DirectoryException, NdbApiException
    {
      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldEntry);

      // Create a new entry that is a copy of the old entry but with the new DN.
      Entry newEntry = oldEntry.duplicate(false);
      newEntry.setDN(newDN);

      // Put the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, newID, newEntry))
      {
        Message message = ERR_NDB_MODIFYDN_ALREADY_EXISTS.get(newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message);
      }
    }

    /**
     * Update the database for a subordinate entry of the target entry
     * of a Modify DN operation not specifying a new superior.
     *
     * @param txn The abstract transaction to be used for the updates.
     * @param entryID The ID of the subordinate entry.
     * @param oldEntry The original contents of the subordinate entry.
     * @param newDN The new DN of the subordinate entry.
     * @throws DirectoryException If a Directory Server error occurs.
     * @throws NdbApiException If an error occurs in the NDB database.
     */
    private void renameSubordinateEntry(AbstractTransaction txn, long entryID,
                                        Entry oldEntry, DN newDN)
        throws DirectoryException, NDBException, NdbApiException
    {
      // Remove the old DN from dn2id.
      dn2id.remove(txn, oldEntry);

      // Create a new entry that is a copy of the old entry but with the new DN.
      Entry newEntry = oldEntry.duplicate(false);
      newEntry.setDN(newDN);

      // Insert the new DN in dn2id.
      if (!dn2id.insert(txn, newDN, entryID, newEntry))
      {
        Message message = ERR_NDB_MODIFYDN_ALREADY_EXISTS.get(newDN.toString());
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message);
      }
    }

    /**
     * This method is called after the transaction has successfully
     * committed.
     */
    public void postCommitAction()
    {
      // No implementation needed.
    }
  }

  /**
   * Make a new DN for a subordinate entry of a renamed or moved entry.
   *
   * @param oldDN The current DN of the subordinate entry.
   * @param oldSuffixLen The current DN length of the renamed or moved entry.
   * @param newSuffixDN The new DN of the renamed or moved entry.
   * @return The new DN of the subordinate entry.
   */
  public static DN modDN(DN oldDN, int oldSuffixLen, DN newSuffixDN)
  {
    int oldDNNumComponents    = oldDN.getNumComponents();
    int oldDNKeepComponents   = oldDNNumComponents - oldSuffixLen;
    int newSuffixDNComponents = newSuffixDN.getNumComponents();

    RDN[] newDNComponents = new RDN[oldDNKeepComponents+newSuffixDNComponents];
    for (int i=0; i < oldDNKeepComponents; i++)
    {
      newDNComponents[i] = oldDN.getRDN(i);
    }

    for (int i=oldDNKeepComponents, j=0; j < newSuffixDNComponents; i++,j++)
    {
      newDNComponents[i] = newSuffixDN.getRDN(j);
    }

    return new DN(newDNComponents);
  }

  /**
   * Get a count of the number of entries stored in this entry entryContainer.
   *
   * @return The number of entries stored in this entry entryContainer.
   * @throws NdbApiException If an error occurs in the NDB database.
   */
  public long getEntryCount() throws NdbApiException
  {
    return dn2id.getRecordCount();
  }

  /**
   * Get the number of values for which the entry limit has been exceeded
   * since the entry entryContainer was opened.
   * @return The number of values for which the entry limit has been exceeded.
   */
  public int getEntryLimitExceededCount()
  {
    int count = 0;
    return count;
  }

  /**
   * Get a list of the databases opened by this entryContainer.
   * @param dbList A list of database containers.
   */
  public void listDatabases(List<DatabaseContainer> dbList)
  {
    dbList.add(dn2id);
  }

  /**
   * Determine whether the provided operation has the ManageDsaIT request
   * control.
   * @param operation The operation for which the determination is to be made.
   * @return true if the operation has the ManageDsaIT request control, or false
   * if not.
   */
  public static boolean isManageDsaITOperation(Operation operation)
  {
    if(operation != null)
    {
      List<Control> controls = operation.getRequestControls();
      if (controls != null)
      {
        for (Control control : controls)
        {
          if (control.getOID().equals(ServerConstants.OID_MANAGE_DSAIT_CONTROL))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * This method constructs a container name from a base DN. Only alphanumeric
   * characters are preserved, all other characters are replaced with an
   * underscore.
   *
   * @return The container name for the base DN.
   */
  public String getDatabasePrefix()
  {
    return databasePrefix;
  }

  /**
   * Get the baseDN this entry container is responsible for.
   *
   * @return The Base DN for this entry container.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Get the parent of a DN in the scope of the base DN.
   *
   * @param dn A DN which is in the scope of the base DN.
   * @return The parent DN, or null if the given DN is the base DN.
   */
  public DN getParentWithinBase(DN dn)
  {
    if (dn.equals(baseDN))
    {
      return null;
    }
    return dn.getParent();
  }

  /**
   * {@inheritDoc}
   */
  public synchronized boolean isConfigurationChangeAcceptable(
      NdbBackendCfg cfg, List<Message> unacceptableReasons)
  {
    // This is always true because only all config attributes used
    // by the entry container should be validated by the admin framework.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized ConfigChangeResult applyConfigurationChange(
      NdbBackendCfg cfg)
  {
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    this.config = cfg;
    this.deadlockRetryLimit = config.getDeadlockRetryLimit();

    return new ConfigChangeResult(ResultCode.SUCCESS,
                                  adminActionRequired, messages);
  }

  /**
   * Checks whether the target of an operation is a referral entry and throws
   * a Directory referral exception if it is.
   * @param entry The target entry of the operation, or the base entry of a
   * search operation.
   * @param searchScope The scope of the search operation, or null if the
   * operation is not a search operation.
   * @throws DirectoryException If a referral is found at or above the target
   * DN.  The referral URLs will be set appropriately for the references found
   * in the referral entry.
   */
  public void checkTargetForReferral(Entry entry, SearchScope searchScope)
       throws DirectoryException
  {
    Set<String> referralURLs = entry.getReferralURLs();
    if (referralURLs != null)
    {
      throwReferralException(entry.getDN(), entry.getDN(), referralURLs,
                             searchScope);
    }
  }

  /**
   * Throws a Directory referral exception for the case where a referral entry
   * exists at or above the target DN of an operation.
   * @param targetDN The target DN of the operation, or the base object of a
   * search operation.
   * @param referralDN The DN of the referral entry.
   * @param labeledURIs The set of labeled URIs in the referral entry.
   * @param searchScope The scope of the search operation, or null if the
   * operation is not a search operation.
   * @throws DirectoryException If a referral is found at or above the target
   * DN.  The referral URLs will be set appropriately for the references found
   * in the referral entry.
   */
  public void throwReferralException(DN targetDN, DN referralDN,
                                     Set<String> labeledURIs,
                                     SearchScope searchScope)
       throws DirectoryException
  {
    ArrayList<String> URIList = new ArrayList<String>(labeledURIs.size());
    for (String labeledURI : labeledURIs)
    {
      // Remove the label part of the labeled URI if there is a label.
      String uri = labeledURI;
      int i = labeledURI.indexOf(' ');
      if (i != -1)
      {
        uri = labeledURI.substring(0, i);
      }

      try
      {
        LDAPURL ldapurl = LDAPURL.decode(uri, false);

        if (ldapurl.getScheme().equalsIgnoreCase("ldap"))
        {
          DN urlBaseDN = targetDN;
          if (!referralDN.equals(ldapurl.getBaseDN()))
          {
            urlBaseDN =
                 EntryContainer.modDN(targetDN,
                                      referralDN.getNumComponents(),
                                      ldapurl.getBaseDN());
          }
          ldapurl.setBaseDN(urlBaseDN);
          if (searchScope == null)
          {
            // RFC 3296, 5.2.  Target Object Considerations:
            // In cases where the URI to be returned is a LDAP URL, the server
            // SHOULD trim any present scope, filter, or attribute list from the
            // URI before returning it.  Critical extensions MUST NOT be trimmed
            // or modified.
            StringBuilder builder = new StringBuilder(uri.length());
            ldapurl.toString(builder, true);
            uri = builder.toString();
          }
          else
          {
            // RFC 3296, 5.3.  Base Object Considerations:
            // In cases where the URI to be returned is a LDAP URL, the server
            // MUST provide an explicit scope specifier from the LDAP URL prior
            // to returning it.
            ldapurl.getAttributes().clear();
            ldapurl.setScope(searchScope);
            ldapurl.setFilter(null);
            uri = ldapurl.toString();
          }
        }
      }
      catch (DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        // Return the non-LDAP URI as is.
      }

      URIList.add(uri);
    }

    // Throw a directory referral exception containing the URIs.
    Message msg =
        NOTE_NDB_REFERRAL_RESULT_MESSAGE.get(String.valueOf(referralDN));
    throw new DirectoryException(
            ResultCode.REFERRAL, msg, referralDN, URIList, null);
  }

  /**
   * Process referral entries that are above the target DN of an operation.
   * @param txn      Abstract transaction for this operation.
   * @param targetDN The target DN of the operation, or the base object of a
   * search operation.
   * @param searchScope The scope of the search operation, or null if the
   * operation is not a search operation.
   * @throws DirectoryException If a referral is found at or above the target
   * DN.  The referral URLs will be set appropriately for the references found
   * in the referral entry.
   * @throws NdbApiException An error occurred during a database operation.
   */
  public void targetEntryReferrals(AbstractTransaction txn,
    DN targetDN, SearchScope searchScope)
       throws DirectoryException, NdbApiException
  {
    try {
      // Go up through the DIT hierarchy until we find a referral.
      for (DN dn = getParentWithinBase(targetDN); dn != null;
        dn = getParentWithinBase(dn)) {
        // Construct a set of all the labeled URIs in the referral.
        long id = dn2id.getID(txn, dn, NdbOperation.LockMode.LM_Read);
        Set<String> labeledURIs = dn2id.getReferrals(txn, id);
        if (!labeledURIs.isEmpty()) {
          throwReferralException(targetDN, dn, labeledURIs, searchScope);
        }
      }
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }


  /**
   * Finds an existing entry whose DN is the closest ancestor of a given baseDN.
   *
   * @param baseDN  the DN for which we are searching a matched DN
   * @return the DN of the closest ancestor of the baseDN
   * @throws DirectoryException If an error prevented the check of an
   * existing entry from being performed
   */
  private DN getMatchedDN(AbstractTransaction txn, DN baseDN)
    throws DirectoryException, NdbApiException
  {
    DN matchedDN = null;
    DN parentDN  = baseDN.getParentDNInSuffix();
    while ((parentDN != null) && parentDN.isDescendantOf(getBaseDN()))
    {
      if (entryExists(txn, parentDN))
      {
        matchedDN = parentDN;
        break;
      }
      parentDN = parentDN.getParentDNInSuffix();
    }
    return matchedDN;
  }
}
