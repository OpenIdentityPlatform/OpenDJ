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
 *      Portions Copyright 2012 ForgeRock AS
 */
package org.opends.server.workflowelement.externalchangelog;



import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.admin.std.server.WorkflowElementCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;
import org.opends.server.workflowelement.LeafWorkflowElement;




/**
 * This class defines a workflow element for the external changelog (ECL);
 * e-g an entity that handles the processing of an operation against the ECL.
 */
public class ECLWorkflowElement extends
    LeafWorkflowElement<WorkflowElementCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   *The set of persistent searches registered with this work flow element.
   */
  private final List<PersistentSearch> persistentSearches =
    new CopyOnWriteArrayList<PersistentSearch>();

  /**
   * A string indicating the type of the workflow element.
   */
  public static final String ECL_WORKFLOW_ELEMENT = "EXTERNAL CHANGE LOG";

  /**
   * The replication server object to which we will submits request
   * on the ECL. Retrieved from the local DirectoryServer.
   */
  private ReplicationServer replicationServer;

  /**
   * Creates a new instance of the External Change Log workflow element.
   * @param rs the provided replication server
   * @throws DirectoryException  If the ECL workflow is already registered.
   */
  public ECLWorkflowElement(ReplicationServer rs)
  throws DirectoryException
  {
    this.replicationServer =rs;
    super.initialize(ECL_WORKFLOW_ELEMENT, ECL_WORKFLOW_ELEMENT);
    super.setPrivate(true);
    DirectoryServer.registerWorkflowElement(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeWorkflowElement()
  {
    // null all fields so that any use of the finalized object will raise
    // an NPE
    super.initialize(ECL_WORKFLOW_ELEMENT, null);

    // Cancel all persistent searches.
    for (PersistentSearch psearch : persistentSearches) {
      psearch.cancel();
    }
    persistentSearches.clear();
  }

  /**
   * {@inheritDoc}
   */
  public void execute(Operation operation) throws CanceledOperationException {
    switch (operation.getOperationType())
    {
      case SEARCH:
        ECLSearchOperation searchOperation =
             new ECLSearchOperation((SearchOperation) operation);
        searchOperation.processECLSearch(this);
        break;
      case ABANDON:
        // There is no processing for an abandon operation.
        break;

      case BIND:
      case ADD:
      case DELETE:
      case MODIFY:
      case MODIFY_DN:
      case COMPARE:
      default:
        throw new AssertionError("Attempted to execute an invalid operation " +
                                 "type:  " + operation.getOperationType() +
                                 " (" + operation + ")");
    }
  }



  /**
   * Attaches the current local operation to the global operation so that
   * operation runner can execute local operation post response later on.
   *
   * @param <O>              subtype of Operation
   * @param <L>              subtype of LocalBackendOperation
   * @param globalOperation  the global operation to which local operation
   *                         should be attached to
   * @param currentLocalOperation  the local operation to attach to the global
   *                               operation
   */
  @SuppressWarnings("unchecked")
  public static <O extends Operation,L> void
              attachLocalOperation (O globalOperation, L currentLocalOperation)
  {
    List<?> existingAttachment =
      (List<?>) globalOperation.getAttachment(Operation.LOCALBACKENDOPERATIONS);

    List<L> newAttachment = new ArrayList<L>();

    if (existingAttachment != null)
    {
      // This line raises an unchecked conversion warning.
      // There is nothing we can do to prevent this warning
      // so let's get rid of it since we know the cast is safe.
      newAttachment.addAll ((List<L>) existingAttachment);
    }
    newAttachment.add (currentLocalOperation);
    globalOperation.setAttachment(Operation.LOCALBACKENDOPERATIONS,
                                  newAttachment);
  }

  /**
   * Registers the provided persistent search operation with this
   * workflow element so that it will be notified of any
   * add, delete, modify, or modify DN operations that are performed.
   *
   * @param persistentSearch
   *          The persistent search operation to register with this
   *          workflow element.
   */
  void registerPersistentSearch(PersistentSearch persistentSearch)
  {
    PersistentSearch.CancellationCallback callback =
      new PersistentSearch.CancellationCallback()
    {
      public void persistentSearchCancelled(PersistentSearch psearch)
      {
        psearch.getSearchOperation().cancel(null);
        persistentSearches.remove(psearch);
      }
    };

    persistentSearches.add(persistentSearch);
    persistentSearch.registerCancellationCallback(callback);
  }



  /**
   * Gets the list of persistent searches currently active against
   * this workflow element.
   *
   * @return The list of persistent searches currently active against
   *         this workflow element.
   */
  public List<PersistentSearch> getPersistentSearches()
  {
    return persistentSearches;
  }

  /**
   * Returns the associated replication server.
   * @return the rs.
   */
  public ReplicationServer getReplicationServer()
  {
    return this.replicationServer;
  }
}

