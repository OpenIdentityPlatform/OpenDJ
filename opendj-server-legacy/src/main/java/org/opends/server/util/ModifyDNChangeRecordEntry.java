/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util;

import static org.forgerock.util.Reject.*;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;



/**
 * This class defines a data structure for a change record entry for
 * an modifyDN operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class ModifyDNChangeRecordEntry extends ChangeRecordEntry
{
  /** The new RDN. */
  private final RDN newRDN;

  /** The new superior DN. */
  private final DN newSuperiorDN;

  /** Delete the old RDN? */
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

    ifNull(newRDN);

    this.newSuperiorDN = newSuperiorDN;
    this.newRDN = newRDN;
    this.deleteOldRDN = deleteOldRDN;
  }


  /**
   * Get the new RDN for the requested modify DN operation.
   *
   * @return the new RDN.
   */
  public RDN getNewRDN()
  {
    return newRDN;
  }


  /**
   * Get the new superior DN for the requested modify DN operation.
   *
   * @return the new superior DN, or <CODE>null</CODE> if there is none.
   */
  public DN getNewSuperiorDN()
  {
    return newSuperiorDN;
  }


  /**
   * Get the new RDN for the requested modify DN operation.
   *
   * @return the new RDN.
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
  @Override
  public ChangeOperationType getChangeOperationType()
  {
    return ChangeOperationType.MODIFY_DN;
  }



  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("ModifyDNChangeRecordEntry(dn=\"");
    buffer.append(getDN());
    buffer.append("\", newRDN=\"");
    buffer.append(newRDN);
    buffer.append("\", deleteOldRDN=");
    buffer.append(deleteOldRDN);

    if (newSuperiorDN != null)
    {
      buffer.append(", newSuperior=\"");
      buffer.append(newSuperiorDN);
      buffer.append("\"");
    }

    buffer.append(")");

    return buffer.toString();
  }
}

