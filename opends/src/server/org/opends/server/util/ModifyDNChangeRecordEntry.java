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



import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.opends.server.core.DirectoryException;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.RDN;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.UtilityMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for a change record entry for
 * an modifyDN operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
public class ModifyDNChangeRecordEntry extends ChangeRecordEntry
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
      "org.opends.server.util.ModifyDNChangeRecordEntry";

  private LDIFReader reader;

  // The new RDN.
  private RDN newRDN = null;

  // The new superior DN.
  private DN newSuperiorDN = null;

  // Delete the old RDN?
  private boolean deleteOldRDN = false;


  /**
   * Creates a new entry with the provided information.
   *
   * @param  dn      The distinguished name for this entry.
   * @param  reader  The LDIFReader instance used to read the entries.
   */
  public ModifyDNChangeRecordEntry(DN dn, LDIFReader reader)
  {
    super(dn, reader);
    assert debugConstructor(CLASS_NAME, String.valueOf(dn),
                            String.valueOf(reader));

    this.reader = reader;

  }


  /**
   * Get the new RDN for the requested modify DN operation.
   *
   * @return the new RDN.
   *
   */
  public RDN getNewRDN()
  {
    assert debugEnter(CLASS_NAME, "getNewRDN");
    return newRDN;
  }


  /**
   * Get the new superior RDN for the requested modify DN operation.
   *
   * @return the new superior RDN.
   *
   */
  public DN getNewSuperiorRDN()
  {
    assert debugEnter(CLASS_NAME, "getNewSuperiorRDN");
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
    assert debugEnter(CLASS_NAME, "deleteOldRDN");
    return deleteOldRDN;
  }


  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public ChangeOperationType getChangeOperationType()
  {
    assert debugEnter(CLASS_NAME, "getChangeOperationType");

    return ChangeOperationType.MODIFY_DN;
  }

  /**
   * Parse the lines and populate the internal structures.
   *
   * @param lines        The lines to parse.
   * @param lineNumber   The current line number.
   *
   * @exception LDIFException if there is an error during parsing.
   */

  public void parse(LinkedList<StringBuilder> lines, long lineNumber)
         throws LDIFException
  {
    assert debugEnter(CLASS_NAME, "parse", String.valueOf(lines),
                      String.valueOf(lineNumber));

    if(lines.isEmpty())
    {
      int msgID = MSGID_LDIF_NO_MOD_DN_ATTRIBUTES;
      String message = getMessage(msgID);
      throw new LDIFException(msgID, message, lineNumber, true);
    }

    StringBuilder line = lines.remove();
    String rdnStr = getAttributeValue(lines, line, "newrdn");

    try
    {
      newRDN = RDN.decode(rdnStr);
    } catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "parse", de);
      int    msgID   = MSGID_LDIF_INVALID_DN;
      String message = getMessage(msgID, lineNumber, line.toString(),
                                  de.getErrorMessage());
      throw new LDIFException(msgID, message, lineNumber, true);
    } catch (Exception e)
    {
      assert debugException(CLASS_NAME, "parse", e);
      int    msgID   = MSGID_LDIF_INVALID_DN;
      String message = getMessage(msgID, lineNumber, line.toString(),
                                  e.getMessage());
      throw new LDIFException(msgID, message, lineNumber, true);
    }

    if(lines.isEmpty())
    {
      int msgID = MSGID_LDIF_NO_DELETE_OLDRDN_ATTRIBUTE;
      String message = getMessage(msgID);
      throw new LDIFException(msgID, message, lineNumber, true);
    }
    lineNumber++;

    line = lines.remove();
    String delStr = getAttributeValue(lines, line, "deleteoldrdn");

    if(delStr.equalsIgnoreCase("false") ||
    delStr.equalsIgnoreCase("no") ||
    delStr.equalsIgnoreCase("0"))
    {
      deleteOldRDN = false;
    } else if(delStr.equalsIgnoreCase("true") ||
        delStr.equalsIgnoreCase("yes") ||
        delStr.equalsIgnoreCase("1"))
    {
      deleteOldRDN = true;
    } else
    {
      int msgID = MSGID_LDIF_INVALID_DELETE_OLDRDN_ATTRIBUTE;
      String message = getMessage(msgID, delStr);
      throw new LDIFException(msgID, message, lineNumber, true);
    }

    if(!lines.isEmpty())
    {
      lineNumber++;

      line = lines.remove();

      String dnStr = getAttributeValue(lines, line, "newsuperior");
      try
      {
        newSuperiorDN = DN.decode(dnStr);
      } catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "parse", de);
        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lineNumber, line.toString(),
                                    de.getErrorMessage());
        throw new LDIFException(msgID, message, lineNumber, true);
      } catch (Exception e)
      {
        assert debugException(CLASS_NAME, "parse", e);
        int    msgID   = MSGID_LDIF_INVALID_DN;
        String message = getMessage(msgID, lineNumber, line.toString(),
                                    e.getMessage());
        throw new LDIFException(msgID, message, lineNumber, true);
      }

    }

  }

  /**
   * Return the string value for the specified attribute name which
   * only has one value.
   *
   * @param  lines          The set of lines for this change record entry.
   * @param  line           The line currently being examined.
   * @param  attributeName  The attribute name
   *
   * @return the string value for the attribute name.
   *
   * @throws  LDIFException  If a problem occurs while attempting to determine
   *                         the attribute value.
   */

  private String getAttributeValue(LinkedList<StringBuilder> lines,
                                   StringBuilder line,
                                   String attributeName) throws LDIFException
  {
    assert debugEnter(CLASS_NAME, "getAttributeValue", String.valueOf(lines),
                      String.valueOf(line), String.valueOf(attributeName));

    Attribute attr =
      reader.readSingleValueAttribute(lines, line, getDN(), attributeName);
    LinkedHashSet<AttributeValue> values = attr.getValues();

    // Only 1 entry should be present
    if(values.size() != 1)
    {
      int msgID = MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE_VAL;
      String message = getMessage(msgID, attributeName);
      throw new LDIFException(msgID, message);
    }

    // Get the attribute value

    Object[] vals = values.toArray();
    return (((AttributeValue)vals[0]).getStringValue());
  }

}

