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

import java.util.List;

import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationWrapper;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

/**
 * This class defines an operation used to modify an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendModifyOperation extends ModifyOperationWrapper
  implements PreOperationModifyOperation,
             PostOperationModifyOperation,
             PostResponseModifyOperation
{
  // The current entry, before any changes are applied.
  private Entry currentEntry = null;

  // The modified entry that will be stored in the backend.
  private Entry modifiedEntry = null;

  // The set of clear-text current passwords (if any were provided).
  private List<AttributeValue> currentPasswords = null;

  // The set of clear-text new passwords (if any were provided).
  private List<AttributeValue> newPasswords = null;

  /**
   * Creates a new operation that may be used to modify an entry in a
   * local backend of the Directory Server.
   *
   * @param modify The operation to enhance.
   */
  public LocalBackendModifyOperation(ModifyOperation modify)
  {
    super(modify);
    LocalBackendWorkflowElement.attachLocalOperation (modify, this);
  }

  /**
   * Retrieves the current entry before any modifications are applied.  This
   * will not be available to pre-parse plugins.
   *
   * @return  The current entry, or <CODE>null</CODE> if it is not yet
   *          available.
   */
  public final Entry getCurrentEntry()
  {
    return currentEntry;
  }

  /**
   * Retrieves the set of clear-text current passwords for the user, if
   * available.  This will only be available if the modify operation contains
   * one or more delete elements that target the password attribute and provide
   * the values to delete in the clear.  It will not be available to pre-parse
   * plugins.
   *
   * @return  The set of clear-text current password values as provided in the
   *          modify request, or <CODE>null</CODE> if there were none or this
   *          information is not yet available.
   */
  public final List<AttributeValue> getCurrentPasswords()
  {
    return currentPasswords;
  }

  /**
   * Retrieves the modified entry that is to be written to the backend.  This
   * will be available to pre-operation plugins, and if such a plugin does make
   * a change to this entry, then it is also necessary to add that change to
   * the set of modifications to ensure that the update will be consistent.
   *
   * @return  The modified entry that is to be written to the backend, or
   *          <CODE>null</CODE> if it is not yet available.
   */
  public final Entry getModifiedEntry()
  {
    return modifiedEntry;
  }

  /**
   * Retrieves the set of clear-text new passwords for the user, if available.
   * This will only be available if the modify operation contains one or more
   * add or replace elements that target the password attribute and provide the
   * values in the clear.  It will not be available to pre-parse plugins.
   *
   * @return  The set of clear-text new passwords as provided in the modify
   *          request, or <CODE>null</CODE> if there were none or this
   *          information is not yet available.
   */
  public final List<AttributeValue> getNewPasswords()
  {
    return newPasswords;
  }

  /**
   * Retrieves the current entry before any modifications are applied.  This
   * will not be available to pre-parse plugins.
   *
   * @param currentEntry The current entry.
   */
  public final void setCurrentEntry(Entry currentEntry)
  {
    this.currentEntry = currentEntry;
  }

  /**
   * Register the set of clear-text current passwords for the user, if
   * available.  This will only be available if the modify operation contains
   * one or more delete elements that target the password attribute and provide
   * the values to delete in the clear.
   *
   * @param currentPasswords The set of clear-text current password values as
   *                         provided in the modify request.
   */
  public final void setCurrentPasswords(List<AttributeValue> currentPasswords)
  {
    this.currentPasswords = currentPasswords;
  }

  /**
   * Register the modified entry that is to be written to the backend.
   *
   * @param modifiedEntry  The modified entry that is to be written to the
   *                       backend, or <CODE>null</CODE> if it is not yet
   *                       available.
   */
  public final void setModifiedEntry(Entry modifiedEntry)
  {
    this.modifiedEntry = modifiedEntry;
  }

  /**
   * Register the set of clear-text new passwords for the user, if available.
   * This will only be available if the modify operation contains one or more
   * add or replace elements that target the password attribute and provide the
   * values in the clear.
   *
   * @param newPasswords  The set of clear-text new passwords as provided in
   *                      the modify request, or <CODE>null</CODE> if there
   *                      were none or this information is not yet available.
   */
  public final void setNewPasswords(List<AttributeValue> newPasswords)
  {
    this.newPasswords = newPasswords;
  }


  /**
   * Adds the provided modification to the set of modifications to this modify
   * operation.
   * In addition, the modification is applied to the modified entry.
   *
   * This may only be called by pre-operation plugins.
   *
   * @param  modification  The modification to add to the set of changes for
   *                       this modify operation.
   *
   * @throws  DirectoryException  If an unexpected problem occurs while applying
   *                              the modification to the entry.
   */
  public void addModification(Modification modification)
    throws DirectoryException
  {
    modifiedEntry.applyModification(modification);
    super.addModification(modification);
  }
}
