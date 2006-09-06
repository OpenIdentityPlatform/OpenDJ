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
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.UtilityMessages.*;

import java.util.LinkedList;

import org.opends.server.types.DN;




/**
 * This class defines a data structure for a change record entry for
 * an delete operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
public final class DeleteChangeRecordEntry extends ChangeRecordEntry
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
      "org.opends.server.util.DeleteChangeRecordEntry";



  /**
   * Creates a new entry with the provided information.
   *
   * @param  dn      The distinguished name for this entry.
   * @param  reader  The LDIFReader instance used to read the entries.
   */
  public DeleteChangeRecordEntry(DN dn, LDIFReader reader)
  {
    super(dn, reader);
    assert debugConstructor(CLASS_NAME, String.valueOf(dn),
                            String.valueOf(reader));

  }



  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public ChangeOperationType getChangeOperationType()
  {
    assert debugEnter(CLASS_NAME, "getChangeOperationType");

    return ChangeOperationType.DELETE;
  }


  /**
   * Parse the lines and populate the internal structures.
   *
   * @param lines       The lines to parse.
   * @param lineNumber  The current line number.
   *
   * @exception LDIFException if there is an error during parsing.
   */

  public void parse(LinkedList<StringBuilder> lines, long lineNumber)
         throws LDIFException
  {
    assert debugEnter(CLASS_NAME, "parse", String.valueOf(lines),
                      String.valueOf(lineNumber));

    if(! lines.isEmpty())
    {
      int msgID = MSGID_LDIF_INVALID_DELETE_ATTRIBUTES;
      String message = getMessage(msgID);

      throw new LDIFException(msgID, message, lineNumber, true);

    }
  }


}

