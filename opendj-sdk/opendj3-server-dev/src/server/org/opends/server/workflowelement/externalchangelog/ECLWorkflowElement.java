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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.workflowelement.externalchangelog;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.admin.std.server.WorkflowElementCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;
import org.opends.server.workflowelement.LeafWorkflowElement;

/**
 * This class defines a workflow element for the external changelog (ECL);
 * e-g an entity that handles the processing of an operation against the ECL.
 */
public class ECLWorkflowElement extends LeafWorkflowElement<WorkflowElementCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * A string indicating the type of the workflow element.
   */
  public static final String ECL_WORKFLOW_ELEMENT = "EXTERNAL CHANGE LOG";

  /**
   * The replication server object to which we will submits request
   * on the ECL. Retrieved from the local DirectoryServer.
   */
  private final ReplicationServer replicationServer;

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

  /** {@inheritDoc} */
  @Override
  public void finalizeWorkflowElement()
  {
    // null all fields so that any use of the finalized object will raise a NPE
    super.initialize(ECL_WORKFLOW_ELEMENT, null);
  }

  /** {@inheritDoc} */
  @Override
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
   * Returns the associated replication server.
   * @return the rs.
   */
  public ReplicationServer getReplicationServer()
  {
    return this.replicationServer;
  }
}

