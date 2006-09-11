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
package org.opends.server.util;



import static org.opends.server.loggers.Debug.debugConstructor;
import static org.opends.server.loggers.Debug.debugEnter;

import org.opends.server.types.DN;



/**
 * This abstract class defines methods for a change record entry.  It
 * includes operations to get the DN, as well as methods to
 * decode the entry.
 */
public abstract class ChangeRecordEntry
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
      "org.opends.server.util.ChangeRecordEntry";


  // The DN for this entry.
  private DN dn;


  /**
   * Creates a new change record entry with the provided information.
   *
   * @param  dn  The distinguished name for this change record entry.
   */
  protected ChangeRecordEntry(DN dn)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(dn));

    if (dn == null)
    {
      this.dn = new DN();
    }
    else
    {
      this.dn = dn;
    }
  }


  /**
   * Retrieves the distinguished name for this entry.
   *
   * @return  The distinguished name for this entry.
   */
  public final DN getDN()
  {
    assert debugEnter(CLASS_NAME, "getDN");

    return dn;
  }



  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public abstract ChangeOperationType getChangeOperationType();
}

