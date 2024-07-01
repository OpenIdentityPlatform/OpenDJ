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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.util;

import org.forgerock.opendj.ldap.DN;



/**
 * This class defines a data structure for a change record entry for
 * an delete operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class DeleteChangeRecordEntry extends ChangeRecordEntry
{
  /**
   * Creates a new entry with the provided information.
   *
   * @param  dn  The distinguished name for this entry.  It must not be
   *             <CODE>null</CODE>.
   */
  public DeleteChangeRecordEntry(DN dn)
  {
    super(dn);

  }



  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  @Override
  public ChangeOperationType getChangeOperationType()
  {
    return ChangeOperationType.DELETE;
  }



  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("DeleteChangeRecordEntry(dn=\"");
    buffer.append(getDN());
    buffer.append("\")");

    return buffer.toString();
  }
}

