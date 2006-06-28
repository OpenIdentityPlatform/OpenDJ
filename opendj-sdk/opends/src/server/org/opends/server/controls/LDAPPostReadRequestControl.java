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
package org.opends.server.controls;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Control;
import org.opends.server.types.ObjectClass;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the post-read request control as defined in RFC 4527.
 * This control makes it possible to retrieve an entry in the state that it held
 * immediately after an add, modify, or modify DN operation.  It may specify a
 * specific set of attributes that should be included in that entry.  The entry
 * will be encoded in a corresponding response control.
 */
public class LDAPPostReadRequestControl
       extends Control
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.controls.LDAPPostReadRequestControl";



  // Indicates whether the request indicates that all operational attributes
  // should be returned.
  private boolean returnAllOperationalAttrs;

  // Indicates whether the request indicates that all user attributes should be
  // returned.
  private boolean returnAllUserAttrs;

  // The set of raw attributes to return in the entry.
  private LinkedHashSet<String> rawAttributes;

  // The set of processed attributes to return in the entry.
  private LinkedHashSet<AttributeType> requestedAttributes;



  /**
   * Creates a new instance of this LDAP post-read request control with the
   * provided information.
   *
   * @param  isCritical     Indicates whether support for this control should be
   *                        considered a critical part of the server processing.
   * @param  rawAttributes  The set of raw attributes to return in the entry.
   *                        A null or empty set will indicates that all user
   *                        attributes should be returned.
   */
  public LDAPPostReadRequestControl(boolean isCritical,
                                    LinkedHashSet<String> rawAttributes)
  {
    super(OID_LDAP_READENTRY_POSTREAD, isCritical,
          encodeAttributes(rawAttributes));

    assert debugConstructor(CLASS_NAME, String.valueOf(isCritical),
                            String.valueOf(rawAttributes));

    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }

    requestedAttributes       = null;
    returnAllOperationalAttrs = false;
    returnAllUserAttrs        = false;
  }



  /**
   * Creates a new instance of this LDAP post-read request control with the
   * provided information.
   *
   * @param  oid            The OID to use for this control.
   * @param  isCritical     Indicates whether support for this control should be
   *                        considered a critical part of the server processing.
   * @param  rawAttributes  The set of raw attributes to return in the entry.
   *                        A null or empty set will indicates that all user
   *                        attributes should be returned.
   */
  public LDAPPostReadRequestControl(String oid, boolean isCritical,
                                    LinkedHashSet<String> rawAttributes)
  {
    super(oid, isCritical, encodeAttributes(rawAttributes));

    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(isCritical),
                            String.valueOf(rawAttributes));

    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }

    requestedAttributes       = null;
    returnAllOperationalAttrs = false;
    returnAllUserAttrs        = false;
  }



  /**
   * Creates a new instance of this LDAP post-read request control with the
   * provided information.
   *
   * @param  oid            The OID to use for this control.
   * @param  isCritical     Indicates whether support for this control should be
   *                        considered a critical part of the server processing.
   * @param  rawAttributes  The set of raw attributes to return in the entry.
   *                        A null or empty set will indicates that all user
   *                        attributes should be returned.
   * @param  encodedValue   The post-encoded value for this control.
   */
  private LDAPPostReadRequestControl(String oid, boolean isCritical,
                                     LinkedHashSet<String> rawAttributes,
                                     ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);

    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(isCritical),
                            String.valueOf(rawAttributes),
                            String.valueOf(encodedValue));

    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }

    requestedAttributes       = null;
    returnAllOperationalAttrs = false;
    returnAllUserAttrs        = false;
  }



  /**
   * Creates a new LDAP post-read request control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this LDAP post-read request control.
   *
   * @return  The LDAP post-read request control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid LDAP
   *                         post-read request control.
   */
  public static LDAPPostReadRequestControl decodeControl(Control control)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeControl", String.valueOf(control));

    if (! control.hasValue())
    {
      int    msgID   = MSGID_POSTREADREQ_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    LinkedHashSet<String> rawAttributes = new LinkedHashSet<String>();
    try
    {
      ASN1Sequence attrSequence =
           ASN1Sequence.decodeAsSequence(control.getValue().value());
      for (ASN1Element e : attrSequence.elements())
      {
        rawAttributes.add(e.decodeAsOctetString().stringValue());
      }
    }
    catch (ASN1Exception ae)
    {
      assert debugException(CLASS_NAME, "decodeControl", ae);

      int    msgID   = MSGID_POSTREADREQ_CANNOT_DECODE_VALUE;
      String message = getMessage(msgID, ae.getMessage());
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message,
                              ae);
    }


    return new LDAPPostReadRequestControl(control.getOID(),
                                          control.isCritical(),
                                          rawAttributes, control.getValue());
  }



  /**
   * Encodes the provided set of raw, unprocessed attribute names to an
   * ASN.1 octet string suitable for use as the value of this control.
   *
   * @param  rawAttributes  The set of attributes to encoded in the encoded
   *                        control value.
   *
   * @return  The ASN.1 octet string containing the encoded attribute set.
   */
  private static ASN1OctetString encodeAttributes(LinkedHashSet<String>
                                                       rawAttributes)
  {
    assert debugEnter(CLASS_NAME, "encodeAttributes",
                      String.valueOf(rawAttributes));

    if (rawAttributes == null)
    {
      return new ASN1OctetString(new ASN1Sequence().encode());
    }

    ArrayList<ASN1Element> elements =
         new ArrayList<ASN1Element>(rawAttributes.size());
    for (String attr : rawAttributes)
    {
      elements.add(new ASN1OctetString(attr));
    }

    return new ASN1OctetString(new ASN1Sequence(elements).encode());
  }



  /**
   * Retrieves the raw, unprocessed set of requested attributes.  It must not
   * be altered by the caller without calling <CODE>setRawAttributes</CODE> with
   * the updated set.
   *
   * @return  The raw, unprocessed set of attributes.
   */
  public LinkedHashSet<String> getRawAttributes()
  {
    assert debugEnter(CLASS_NAME, "getRawAttributes");

    return rawAttributes;
  }



  /**
   * Specifies the raw, unprocessed set of requested attributes.  A null or
   * empty set indicates that all user attributes should be returned.
   *
   * @param  rawAttributes  The raw, unprocessed set of requested attributes.
   */
  public void setRawAttributes(LinkedHashSet<String> rawAttributes)
  {
    assert debugEnter(CLASS_NAME, "setRawAttributes",
                      String.valueOf(rawAttributes));

    if (rawAttributes == null)
    {
      this.rawAttributes = new LinkedHashSet<String>();
    }
    else
    {
      this.rawAttributes = rawAttributes;
    }

    setValue(encodeAttributes(rawAttributes));
    requestedAttributes = null;
  }



  /**
   * Retrieves the set of processed attributes that have been requested for
   * inclusion in the entry that is returned.
   *
   * @return  The set of processed attributes that have been requested for
   *          inclusion in the entry that is returned.
   */
  public LinkedHashSet<AttributeType> getRequestedAttributes()
  {
    assert debugEnter(CLASS_NAME, "getRequestedAttributes");

    if (requestedAttributes == null)
    {
      returnAllOperationalAttrs = false;
      returnAllUserAttrs        = (rawAttributes.size() == 0);

      requestedAttributes =
           new LinkedHashSet<AttributeType>(rawAttributes.size());
      for (String attr : rawAttributes)
      {
        attr = attr.toLowerCase();

        if (attr.equals("*"))
        {
          returnAllUserAttrs = true;
        }
        else if (attr.equals("+"))
        {
          returnAllOperationalAttrs = true;
        }
        else if (attr.startsWith("@"))
        {
          String ocName = attr.substring(1);
          ObjectClass oc = DirectoryServer.getObjectClass(ocName);
          if (oc != null)
          {
            requestedAttributes.addAll(oc.getOptionalAttributeChain());
            requestedAttributes.addAll(oc.getRequiredAttributeChain());
          }
        }
        else
        {
          AttributeType at = DirectoryServer.getAttributeType(attr);
          if (at == null)
          {
            at = DirectoryServer.getDefaultAttributeType(attr);
          }

          requestedAttributes.add(at);
        }
      }
    }

    return requestedAttributes;
  }



  /**
   * Indicates whether the entry returned should include all user attributes
   * that the requester has permission to see.
   *
   * @return  <CODE>true</CODE> if the entry returned should include all user
   *          attributes that the requester has permission to see, or
   *          <CODE>false</CODE> if it should only include user attributes that
   *          have been explicitly included in the requested attribute list.
   */
  public boolean returnAllUserAttributes()
  {
    assert debugEnter(CLASS_NAME, "returnAllUserAttributes");

    if (requestedAttributes == null)
    {
      getRequestedAttributes();
    }

    return returnAllUserAttrs;
  }



  /**
   * Indicates whether the entry returned should include all operational
   * attributes that the requester has permission to see.
   *
   * @return  <CODE>true</CODE> if the entry returned should include all
   *          operational attributes that the requester has permission to see,
   *          or <CODE>false</CODE> if it should only include user attributes
   *          that have been explicitly included in the requested attribute
   *          list.
   */
  public boolean returnAllOperationalAttributes()
  {
    assert debugEnter(CLASS_NAME, "returnAllOperationalAttributes");

    if (requestedAttributes == null)
    {
      getRequestedAttributes();
    }

    return returnAllOperationalAttrs;
  }



  /**
   * Indicates whether the specified attribute type should be included in the
   * entry for the corresponding response control.
   *
   * @param  attrType  The attribute type for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the specified attribute type should be
   *          included in the entry for the corresponding response control, or
   *          <CODE>false</CODE> if not.
   */
  public boolean allowsAttribute(AttributeType attrType)
  {
    assert debugEnter(CLASS_NAME, "allowsAttribute",
                      String.valueOf(attrType));

    if (requestedAttributes == null)
    {
      getRequestedAttributes();
    }

    if (requestedAttributes.contains(attrType))
    {
      return true;
    }

    if (attrType.isOperational())
    {
      return returnAllOperationalAttrs;
    }
    else
    {
      return returnAllUserAttrs;
    }
  }



  /**
   * Retrieves a string representation of this LDAP post-read request control.
   *
   * @return  A string representation of this LDAP post-read request control.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this LDAP post-read request control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("LDAPPostReadRequestControl(criticality=");
    buffer.append(isCritical());
    buffer.append(",attrs=\"");

    if (! rawAttributes.isEmpty())
    {
      Iterator<String> iterator = rawAttributes.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }
    }

    buffer.append("\")");
  }
}

