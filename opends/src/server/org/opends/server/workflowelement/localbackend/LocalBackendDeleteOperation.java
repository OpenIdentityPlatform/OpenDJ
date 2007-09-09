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


import org.opends.server.core.DeleteOperationWrapper;
import org.opends.server.core.DeleteOperation;
import org.opends.server.types.Entry;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PostSynchronizationDeleteOperation;

/**
 * This class defines an operation used to delete an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendDeleteOperation extends DeleteOperationWrapper
  implements PreOperationDeleteOperation,
             PostOperationDeleteOperation,
             PostResponseDeleteOperation,
             PostSynchronizationDeleteOperation
{
  // The entry to be deleted.
  private Entry entry;

  /**
   * Creates a new operation that may be used to delete an entry from a
   * local backend of the Directory Server.
   *
   * @param delete The operation to enhance.
   */
  public LocalBackendDeleteOperation(DeleteOperation delete)
  {
    super(delete);
    LocalBackendWorkflowElement.attachLocalOperation (delete, this);
  }


  /**
   * Retrieves the entry to be deleted.
   *
   * @return  The entry to be deleted, or <CODE>null</CODE> if the entry is not
   *          yet available.
   */
  public Entry getEntryToDelete()
  {
    return entry;
  }

  /**
   * Sets the entry to be deleted.
   *
   * @param  entry - The entry to be deleted
   */
  public void setEntryToDelete(Entry entry){
    this.entry = entry;
  }

}
