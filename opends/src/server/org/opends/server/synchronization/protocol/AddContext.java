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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.protocol;

import org.opends.server.synchronization.common.ChangeNumber;

/**
 * This class describe the Synchronization contexte that is attached to
 * Add Operation.
 */
public class AddContext extends OperationContext
{
  /**
   * The Unique Id of the parent entry od the added entry.
   */
  private String parentUid;

  /**
   * Creates a new AddContext with the provided information.
   *
   * @param changeNumber The change number of the add operation.
   * @param uid the Unique Id of the added entry.
   * @param parentUid The unique Id of the parent of the added entry.
   */
  public AddContext(ChangeNumber changeNumber, String uid, String parentUid)
  {
    super(changeNumber, uid);
    this.parentUid = parentUid;
  }

  /**
   * Get the Unique Id of the parent of the added entry.
   *
   * @return Returns the Unique Id of the parent of the added entry.
   */
  public String getParentUid()
  {
    return parentUid;
  }
}
