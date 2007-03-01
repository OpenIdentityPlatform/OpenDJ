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
package org.opends.server.protocols.ldap;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with an LDAP modification, which describes a change that should be made to an
 * attribute.
 */
public class LDAPModification
{



  // The attribute for this modification.
  private LDAPAttribute attribute;

  // The modification type for this modification.
  private ModificationType modificationType;



  /**
   * Creates a new LDAP modification with the provided type and attribute.
   *
   * @param  modificationType  The modification type for this modification.
   * @param  attribute         The attribute for this modification.
   */
  public LDAPModification(ModificationType modificationType,
                          LDAPAttribute attribute)
  {
    this.modificationType = modificationType;
    this.attribute        = attribute;
  }



  /**
   * Retrieves the modification type for this modification.
   *
   * @return  The modification type for this modification.
   */
  public ModificationType getModificationType()
  {
    return modificationType;
  }



  /**
   * Specifies the modification type for this modification.
   *
   * @param  modificationType  The modification type for this modification.
   */
  public void setModificationType(ModificationType modificationType)
  {
    this.modificationType = modificationType;
  }



  /**
   * Retrieves the attribute for this modification.
   *
   * @return  The attribute for this modification.
   */
  public LDAPAttribute getAttribute()
  {
    return attribute;
  }



  /**
   * Specifies the attribute for this modification.
   *
   * @param  attribute  The attribute for this modification.
   */
  public void setAttribute(LDAPAttribute attribute)
  {
    this.attribute = attribute;
  }



  /**
   * Encodes this modification to an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded modification.
   */
  public ASN1Element encode()
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Enumerated(modificationType.intValue()));
    elements.add(attribute.encode());

    return new ASN1Sequence(elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP modification.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded LDAP modification.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided ASN.1 element as an LDAP modification.
   */
  public static LDAPModification decode(ASN1Element element)
         throws LDAPException
  {
    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MODIFICATION_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements != 2)
    {
      int    msgID   = MSGID_LDAP_MODIFICATION_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    ModificationType modificationType;
    try
    {
      switch (elements.get(0).decodeAsEnumerated().intValue())
      {
        case MOD_TYPE_ADD:
          modificationType = ModificationType.ADD;
          break;
        case MOD_TYPE_DELETE:
          modificationType = ModificationType.DELETE;
          break;
        case MOD_TYPE_REPLACE:
          modificationType = ModificationType.REPLACE;
          break;
        case MOD_TYPE_INCREMENT:
          modificationType = ModificationType.INCREMENT;
          break;
        default:
          int    intValue = elements.get(0).decodeAsEnumerated().intValue();
          int    msgID    = MSGID_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE;
          String message  = getMessage(msgID, intValue);
          throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MODIFICATION_DECODE_MOD_TYPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    LDAPAttribute attribute;
    try
    {
      attribute = LDAPAttribute.decode(elements.get(1));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MODIFICATION_DECODE_ATTR;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPModification(modificationType, attribute);
  }



  /**
   * Creates a new core <CODE>Modification</CODE> object from this LDAP
   * modification.
   *
   * @return  The decoded modification.
   *
   * @throws  LDAPException  If a problem occurs while trying to convert the
   *                         LDAP attribute to a core <CODE>Attribute</CODE>.
   */
  public Modification toModification()
         throws LDAPException
  {
    return new Modification(modificationType, attribute.toAttribute());
  }



  /**
   * Retrieves a string representation of this modification.
   *
   * @return  A string representation of this modification.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this modification to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPModification(type=");
    buffer.append(String.valueOf(modificationType));
    buffer.append(", attr=");
    attribute.toString(buffer);
    buffer.append("})");
  }



  /**
   * Appends a multi-line string representation of this LDAP modification to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("LDAP Modification");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Modification Type:  ");
    buffer.append(String.valueOf(modificationType));
    buffer.append(" (");
    buffer.append(modificationType.intValue());
    buffer.append(")");
    buffer.append(EOL);

    buffer.append("  Attribute:");
    buffer.append(EOL);
    attribute.toString(buffer, indent+4);
  }
}

