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

import org.opends.server.types.DN;



/**
 * This class defines a data structure for a change record entry for
 * an delete operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
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
  public ChangeOperationType getChangeOperationType()
  {
    return ChangeOperationType.DELETE;
  }

}

