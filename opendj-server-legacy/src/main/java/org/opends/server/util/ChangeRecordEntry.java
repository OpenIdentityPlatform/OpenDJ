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

/**
 * This abstract class defines methods for a change record entry.  It
 * includes operations to get the DN, as well as methods to
 * decode the entry.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class ChangeRecordEntry
{
  /** The DN for this entry. */
  private DN dn;

  /**
   * Creates a new change record entry with the provided information.
   *
   * @param  dn  The distinguished name for this change record entry.  It must
   *             not be <CODE>null</CODE>.
   */
  protected ChangeRecordEntry(DN dn)
  {
    ifNull(dn);
    this.dn = dn;
  }

  /**
   * Retrieves the distinguished name for this entry.
   *
   * @return  The distinguished name for this entry.
   */
  public final DN getDN()
  {
    return dn;
  }

  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public abstract ChangeOperationType getChangeOperationType();

  @Override
  public abstract String toString();
}
