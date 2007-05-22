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
package org.opends.server.types;



import java.util.ArrayList;
import java.util.List;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPModification;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the data structures and methods to use when
 * interacting with a raw modification, which describes a change that
 * should be made to an attribute.
 */
public abstract class RawModification
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new raw modification with the provided type and
   * attribute.
   *
   * @param  modificationType  The modification type for this
   *                           modification.
   * @param  attribute         The attribute for this modification.
   *
   * @return  The constructed raw modification.
   */
  public static RawModification
                     create(ModificationType modificationType,
                            RawAttribute attribute)
  {
    return new LDAPModification(modificationType, attribute);
  }



  /**
   * Creates a new raw modification with the provided type and
   * attribute.
   *
   * @param  modificationType  The modification type for this
   *                           modification.
   * @param  attributeType     The name of the attribute type for this
   *                           modification.
   *
   * @return  The constructed raw modification.
   */
  public static RawModification
                     create(ModificationType modificationType,
                            String attributeType)
  {
    RawAttribute rawAttribute = RawAttribute.create(attributeType);

    return new LDAPModification(modificationType, rawAttribute);
  }



  /**
   * Creates a new raw modification with the provided type and
   * attribute.
   *
   * @param  modificationType  The modification type for this
   *                           modification.
   * @param  attributeType     The name of the attribute type for this
   *                           modification.
   * @param  attributeValue    The attribute value for this
   *                           modification.
   *
   * @return  The constructed raw modification.
   */
  public static RawModification
                     create(ModificationType modificationType,
                            String attributeType,
                            String attributeValue)
  {
    RawAttribute rawAttribute =
         RawAttribute.create(attributeType, attributeValue);

    return new LDAPModification(modificationType, rawAttribute);
  }



  /**
   * Creates a new raw modification with the provided type and
   * attribute.
   *
   * @param  modificationType  The modification type for this
   *                           modification.
   * @param  attributeType     The name of the attribute type for this
   *                           modification.
   * @param  attributeValue    The attribute value for this
   *                           modification.
   *
   * @return  The constructed raw modification.
   */
  public static RawModification
                     create(ModificationType modificationType,
                            String attributeType,
                            ByteString attributeValue)
  {
    RawAttribute rawAttribute =
         RawAttribute.create(attributeType, attributeValue);

    return new LDAPModification(modificationType, rawAttribute);
  }



  /**
   * Creates a new raw modification with the provided type and
   * attribute.
   *
   * @param  modificationType  The modification type for this
   *                           modification.
   * @param  attributeType     The name of the attribute type for this
   *                           modification.
   * @param  attributeValues   The set of attribute values for this
   *                           modification.
   *
   * @return  The constructed raw modification.
   */
  public static RawModification
                     create(ModificationType modificationType,
                            String attributeType,
                            List<ByteString> attributeValues)
  {
    RawAttribute rawAttribute =
         RawAttribute.create(attributeType, attributeValues);

    return new LDAPModification(modificationType, rawAttribute);
  }



  /**
   * Retrieves the modification type for this modification.
   *
   * @return  The modification type for this modification.
   */
  public abstract ModificationType getModificationType();



  /**
   * Specifies the modification type for this modification.
   *
   * @param  modificationType  The modification type for this
   *                           modification.
   */
  public abstract void setModificationType(
                            ModificationType modificationType);



  /**
   * Retrieves the attribute for this modification.
   *
   * @return  The attribute for this modification.
   */
  public abstract RawAttribute getAttribute();



  /**
   * Specifies the attribute for this modification.
   *
   * @param  attribute  The attribute for this modification.
   */
  public abstract void setAttribute(RawAttribute attribute);



  /**
   * Encodes this modification to an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded modification.
   */
  public final ASN1Element encode()
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Enumerated(
                          getModificationType().intValue()));
    elements.add(getAttribute().encode());

    return new ASN1Sequence(elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP modification.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded LDAP modification.
   *
   * @throws  LDAPException  If a problem occurs while attempting to
   *                         decode the provided ASN.1 element as a
   *                         raw modification.
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MODIFICATION_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements != 2)
    {
      int msgID =
           MSGID_LDAP_MODIFICATION_DECODE_INVALID_ELEMENT_COUNT;
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
          int intValue =
                   elements.get(0).decodeAsEnumerated().intValue();
          int msgID = MSGID_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE;
          String message = getMessage(msgID, intValue);
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MODIFICATION_DECODE_MOD_TYPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    RawAttribute attribute;
    try
    {
      attribute = RawAttribute.decode(elements.get(1));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_MODIFICATION_DECODE_ATTR;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPModification(modificationType, attribute);
  }



  /**
   * Creates a new core {@code Modification} object from this raw
   * modification.
   *
   * @return  The decoded modification.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         convert the raw modification to a core
   *                         {@code Modification}.
   */
  public abstract Modification toModification()
         throws LDAPException;



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
   * Appends a string representation of this modification to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public abstract void toString(StringBuilder buffer);



  /**
   * Appends a multi-line string representation of this LDAP
   * modification to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   * @param  indent  The number of spaces from the margin that the
   *                 lines should be indented.
   */
  public abstract void toString(StringBuilder buffer, int indent);
}

