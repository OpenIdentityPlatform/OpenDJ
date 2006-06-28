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



import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.ModificationType;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.UtilityMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines a data structure for a change record entry for
 * an modify operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
public class ModifyChangeRecordEntry extends ChangeRecordEntry
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
      "org.opends.server.util.ModifyChangeRecordEntry";


  private LDIFReader reader;

  private ArrayList<LDAPModification> modifications =
      new ArrayList<LDAPModification>();

  /**
   * Creates a new entry with the provided information.
   *
   * @param  dn      The distinguished name for this entry.
   * @param  reader  The LDIFReader instance used to read the entries.
   */
  public ModifyChangeRecordEntry(DN dn, LDIFReader reader)
  {
    super(dn, reader);

    this.reader = reader;
  }


  /**
   * Get the list of modifications from the attributes.
   *
   * @return the list of modifications.
   */
  public ArrayList<LDAPModification> getModifications()
  {
    assert debugEnter(CLASS_NAME, "getModifications");

    return modifications;

  }

  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public ChangeOperationType getChangeOperationType()
  {
    assert debugEnter(CLASS_NAME, "getChangeOperationType");

    return ChangeOperationType.MODIFY;
  }

  /**
   * Parse the lines and populate the internal structures.
   *
   * @param lines         The lines to parse.
   * @param lineNumber    The current line number.
   *
   * @exception LDIFException if there is an error during parsing.
   */

  public void parse(LinkedList<StringBuilder> lines, long lineNumber)
      throws LDIFException
  {
    assert debugEnter(CLASS_NAME, "parse", String.valueOf(lines),
          String.valueOf(lineNumber));

    while(!lines.isEmpty())
    {
      ModificationType modType = null;

      StringBuilder line = lines.remove();
      Attribute attr =
           reader.readSingleValueAttribute(lines, line, getDN(), null);
      String name = attr.getName();
      LinkedHashSet<AttributeValue> values = attr.getValues();
      // Only 1 entry should be present
      if(values.size() != 1)
      {
          int msgID = MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE_VAL;
          String message = getMessage(msgID, name);
          throw new LDIFException(msgID, message, lineNumber, true);
      }

      // Get the attribute description
      String attrDescr = values.iterator().next().getStringValue();

      String lowerName = name.toLowerCase();
      if(lowerName.equals("add"))
      {
        modType = ModificationType.ADD;
      } else if(lowerName.equals("delete"))
      {
        modType = ModificationType.DELETE;
      } else if(lowerName.equals("replace"))
      {
        modType = ModificationType.REPLACE;
      } else if(lowerName.equals("increment"))
      {
        modType = ModificationType.INCREMENT;
      } else
      {
        // Invalid attribute name.
        int msgID = MSGID_LDIF_INVALID_MODIFY_ATTRIBUTE;
        String message = getMessage(msgID, name,
              "add, delete, replace, increment");
        throw new LDIFException(msgID, message, lineNumber, true);
      }

      // Now go through the rest of the attributes till the "-" line is reached.
      Attribute modAttr = LDIFReader.parseAttrDescription(attrDescr);
      while (! lines.isEmpty())
      {
        line = lines.remove();
        if(line.toString().equals("-"))
        {
          break;
        }
        Attribute a =
             reader.readSingleValueAttribute(lines, line, getDN(), attrDescr);
        modAttr.getValues().addAll(a.getValues());
      }

      LDAPAttribute ldapAttr = new LDAPAttribute(modAttr);
      LDAPModification mod = new LDAPModification(modType, ldapAttr);
      modifications.add(mod);
    }
  }


}

