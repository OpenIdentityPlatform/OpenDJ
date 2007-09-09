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

import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationWrapper;
import org.opends.server.types.Entry;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PostSynchronizationModifyDNOperation;

/**
 * This class defines an operation used to move an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendModifyDNOperation
  extends ModifyDNOperationWrapper
  implements PreOperationModifyDNOperation,
             PostOperationModifyDNOperation,
             PostResponseModifyDNOperation,
             PostSynchronizationModifyDNOperation
{
  // The current entry, before it is renamed.
  private Entry currentEntry;

  // The new entry, as it will appear after it has been renamed.
  private Entry newEntry;

  /**
   * Creates a new operation that may be used to move an entry in a
   * local backend of the Directory Server.
   *
   * @param operation The operation to enhance.
   */
  public LocalBackendModifyDNOperation (ModifyDNOperation operation)
  {
    super(operation);
    LocalBackendWorkflowElement.attachLocalOperation (operation, this);
  }

  /**
   * Retrieves the current entry, before it is renamed.  This will not be
   * available to pre-parse plugins or during the conflict resolution portion of
   * the synchronization processing.
   *
   * @return  The current entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public final Entry getOriginalEntry()
  {
    return currentEntry;
  }

  /**
   * Retrieves the new entry, as it will appear after it is renamed.  This will
   * not be  available to pre-parse plugins or during the conflict resolution
   * portion of the synchronization processing.
   *
   * @return  The updated entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public final Entry getUpdatedEntry()
  {
    return newEntry;
  }

  /**
   * Sets the current entry, before it is renamed.  This will not be
   * available to pre-parse plugins or during the conflict resolution portion of
   * the synchronization processing.
   *
   * @param entry  The current entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public final void setOriginalEntry(Entry entry)
  {
    this.currentEntry = entry;
  }

  /**
   * Sets the new entry, as it will appear after it is renamed.  This will
   * not be  available to pre-parse plugins or during the conflict resolution
   * portion of the synchronization processing.
   *
   * @param entry  The updated entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public final void setUpdatedEntry(Entry entry)
  {
    this.newEntry = entry;
  }


}
