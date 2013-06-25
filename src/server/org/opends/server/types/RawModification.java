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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;

import java.io.IOException;
import java.util.ArrayList;

import org.opends.messages.Message;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPModification;



/**
 * This class defines the data structures and methods to use when
 * interacting with a raw modification, which describes a change that
 * should be made to an attribute.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
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
                            ArrayList<ByteString> attributeValues)
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
   * Writes this modification to an ASN.1 stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the
   *                     stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence();
    stream.writeEnumerated(getModificationType().intValue());
    getAttribute().write(stream);
    stream.writeEndSequence();
  }


  /**
   * Decodes the elements from the provided ASN.1 reader as an
   * LDAP modification.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP modification.
   *
   * @throws  LDAPException  If a problem occurs while attempting to
   *                         decode the provided ASN.1 element as a
   *                         raw modification.
   */
  public static LDAPModification decode(ASN1Reader reader)
         throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_MODIFICATION_DECODE_SEQUENCE.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ModificationType modificationType;
    try
    {
      int type = (int)reader.readInteger();
      switch (type)
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
          Message message =
              ERR_LDAP_MODIFICATION_DECODE_INVALID_MOD_TYPE.
                get(type);
          throw new LDAPException(PROTOCOL_ERROR, message);
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

      Message message = ERR_LDAP_MODIFICATION_DECODE_MOD_TYPE.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    RawAttribute attribute;
    try
    {
      attribute = RawAttribute.decode(reader);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_MODIFICATION_DECODE_ATTR.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_MODIFICATION_DECODE_SEQUENCE.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
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

