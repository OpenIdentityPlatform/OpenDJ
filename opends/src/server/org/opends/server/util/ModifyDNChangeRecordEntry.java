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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;

import static org.opends.server.util.Validator.*;

import org.opends.server.types.DN;
import org.opends.server.types.RDN;



/**
 * This class defines a data structure for a change record entry for
 * an modifyDN operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
public final class ModifyDNChangeRecordEntry extends ChangeRecordEntry
{

  // The new RDN.
  private final RDN newRDN;

  // The new superior DN.
  private final DN newSuperiorDN;

  // Delete the old RDN?
  private final boolean deleteOldRDN;


  /**
   * Creates a new entry with the provided information.
   *
   * @param dn
   *          The distinguished name for this entry.  It must not be
   *          <CODE>null</CODE>.
   * @param newRDN
   *          The new RDN.  It must not be <CODE>null</CODE>.
   * @param deleteOldRDN
   *          Delete the old RDN?
   * @param newSuperiorDN
   *          The new superior DN.  It may be <CODE>null</CODE> if the entry is
   *          not to be moved below a new parent.
   */
  public ModifyDNChangeRecordEntry(DN dn, RDN newRDN, boolean deleteOldRDN,
                                   DN newSuperiorDN)
  {
    super(dn);

    ensureNotNull(newRDN);

    this.newSuperiorDN = newSuperiorDN;
    this.newRDN = newRDN;
    this.deleteOldRDN = deleteOldRDN;
  }


  /**
   * Get the new RDN for the requested modify DN operation.
   *
   * @return the new RDN.
   *
   */
  public RDN getNewRDN()
  {
    return newRDN;
  }


  /**
   * Get the new superior DN for the requested modify DN operation.
   *
   * @return the new superior DN, or <CODE>null</CODE> if there is none.
   *
   */
  public DN getNewSuperiorDN()
  {
    return newSuperiorDN;
  }


  /**
   * Get the new RDN for the requested modify DN operation.
   *
   * @return the new RDN.
   *
   */
  public boolean deleteOldRDN()
  {
    return deleteOldRDN;
  }


  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public ChangeOperationType getChangeOperationType()
  {
    return ChangeOperationType.MODIFY_DN;
  }
}

