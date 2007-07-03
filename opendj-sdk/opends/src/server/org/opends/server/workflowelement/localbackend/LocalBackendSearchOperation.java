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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.localbackend;


import org.opends.server.api.Backend;
import org.opends.server.core.SearchOperationWrapper;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;

/**
 * This class defines an operation used to search for entries in a local backend
 * of the Directory Server.
 */
public class LocalBackendSearchOperation extends SearchOperationWrapper
  implements PreOperationSearchOperation,
             PostOperationSearchOperation,
             SearchEntrySearchOperation,
             SearchReferenceSearchOperation
{

  /**
   * Creates a new operation that may be used to search for entries in a
   * local backend of the Directory Server.
   *
   * @param search The operation to enhance.
   */
  public LocalBackendSearchOperation(SearchOperation search){
    super(search);
    LocalBackendWorkflowElement.attachLocalOperation(search, this);
  }

  /**
   * Processes the search in the provided backend and recursively through its
   * subordinate backends.
   *
   * @param  backend  The backend in which to process the search.
   *
   * @throws  DirectoryException  If a problem occurs while processing the
   *                              search.
   *
   * @throws  CancelledOperationException  If the backend noticed and reacted
   *                                       to a request to cancel or abandon the
   *                                       search operation.
   */
  public final void searchBackend(Backend backend)
          throws DirectoryException, CancelledOperationException
  {
    // Check for and handle a request to cancel this operation.
    if (getCancelRequest() != null)
    {
      setCancelResult(CancelResult.CANCELED);
      setProcessingStopTime();
      return;
    }

    // Perform the search in the provided backend.
    backend.search(this);

    // Search in the subordinate backends is now done by the workflows.

    // If there are any subordinate backends, then process the search there as
    // well.
    // FIXME jdemendi - From now on, do not search in the subordinate backends
    // because this is done by the workflow topology.
//    Backend[] subBackends = backend.getSubordinateBackends();
//    for (Backend b : subBackends)
//    {
//      DN[] baseDNs = b.getBaseDNs();
//      for (DN dn : baseDNs)
//      {
//        if (dn.isDescendantOf(getBaseDN()))
//        {
//          searchBackend(b);
//          break;
//        }
//      }
//    }

  }
}
