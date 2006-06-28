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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import org.opends.server.types.Modification;


/**
 * This class if used to build fake Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is usefull when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them
 *
 * @author Gilles Bellaton
 */
public abstract class FakeOperation
{
  private ChangeNumber changeNumber;

  /**
   * Creates a new FakeOperation using the provided ChangeNumber.
   *
   * @param changeNumber The ChangeNumber to use to build the FakeOperation.
   */
  public FakeOperation(ChangeNumber changeNumber)
  {
    this.changeNumber = changeNumber;
  }

  /**
   * Get the ChangeNumber.
   *
   * @return Returns the changeNumber.
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * Generate a SynchronizationMessage from this fake operation.
   * The SynchronizationMessage is used to send the informations about
   * this operation to the other servers.
   *
   * @return A SynchronizationMessage that can be used to send information
   *         about this operation to remote servers.
   */
  abstract public SynchronizationMessage generateMessage();

  /**
   * Add a modification to the list of modification included
   * in this fake operation.
   *
   * @param mod A modification that must be adde to the list of modifications
   *            included in this fake operation.
   * @throws Exception when the addition of this type of modification
   *         is not valid for this FakeOperation.
   */
  abstract public void addModification(Modification mod) throws Exception;

}
