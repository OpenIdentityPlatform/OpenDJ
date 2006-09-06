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

import java.util.ArrayList;
import java.util.LinkedList;

import org.opends.server.types.DN;
import org.opends.server.types.RDN;



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
   * Creates a new entry with the provided information.
   *
   * @param  dn      The distinguished name for this entry.
   * @param  reader  The LDIF reader.
   */
  protected ChangeRecordEntry(DN dn, LDIFReader reader)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(dn),
                            String.valueOf(reader));

    if (dn == null)
    {
      this.dn = new DN(new ArrayList<RDN>(0));
    }
    else
    {
      this.dn = dn;
    }

  }



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
   * Specifies the distinguished name for this entry.
   *
   * @param  dn  The distinguished name for this entry.
   */
  public final void setDN(DN dn)
  {
    assert debugEnter(CLASS_NAME, "setDN", String.valueOf(dn));

    if (dn == null)
    {
      this.dn = new DN(new ArrayList<RDN>(0));
    }
    else
    {
      this.dn = dn;
    }
  }



  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public abstract ChangeOperationType getChangeOperationType();



  /**
   * Parse and populate internal structures from the specified lines.
   * @param lines The list of lines to parse from.
   * @param lineNumber The current line number during the parsing.
   *
   * @exception LDIFException If there is a parsing error.
   */
  public abstract void parse(LinkedList<StringBuilder> lines, long lineNumber)
         throws LDIFException;

}

