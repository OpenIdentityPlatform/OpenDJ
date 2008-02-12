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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;
import org.opends.messages.Message;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LDAPException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with a generic LDAP control or set of controls.
 */
public class LDAPControl
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The control wrapped by this LDAP control.
  private Control control;



  /**
   * Creates a new LDAP control with the information in the provided control.
   *
   * @param  control  The control to use to create this LDAP control.
   */
  public LDAPControl(Control control)
  {
    this.control = control;
  }



  /**
   * Creates a new LDAP control with the specified OID.  It will not be
   * critical, and will not have a value.
   *
   * @param  oid  The OID for this LDAP control.
   */
  public LDAPControl(String oid)
  {
    control = new Control(oid, false);
  }



  /**
   * Creates a new LDAP control with the specified OID and criticality.  It will
   * not have a value.
   *
   * @param  oid         The OID for this LDAP control.
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical.
   */
  public LDAPControl(String oid, boolean isCritical)
  {
    control = new Control(oid, isCritical);
  }



  /**
   * Creates a new LDAP control with the specified OID, criticality, and value.
   *
   * @param  oid         The OID for this LDAP control.
   * @param  isCritical  Indicates whether this control should be considered
   *                     critical.
   * @param  value       The value for this LDAP control.
   */
  public LDAPControl(String oid, boolean isCritical, ASN1OctetString value)
  {
    control = new Control(oid, isCritical, value);
  }



  /**
   * Retrieves the control wrapped by this LDAP control.
   *
   * @return  The control wrapped by this LDAP control.
   */
  public Control getControl()
  {
    return control;
  }



  /**
   * Encodes this control to an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded control.
   */
  public ASN1Element encode()
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(3);
    elements.add(new ASN1OctetString(control.getOID()));

    if (control.isCritical())
    {
      elements.add(new ASN1Boolean(control.isCritical()));
    }

    ASN1OctetString value = control.getValue();
    if (value != null)
    {
      elements.add(value);
    }

    return new ASN1Sequence(elements);
  }



  /**
   * Encodes the provided set of controls into an ASN.1 sequence.
   *
   * @param  controls  The set of controls to encode.
   *
   * @return  The ASN.1 element containing the encoded set of controls.
   */
  public static ASN1Element encodeControls(ArrayList<LDAPControl> controls)
  {
    ArrayList<ASN1Element> elements =
         new ArrayList<ASN1Element>(controls.size());
    for (LDAPControl c : controls)
    {
      elements.add(c.encode());
    }

    return new ASN1Sequence(TYPE_CONTROL_SEQUENCE, elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP control.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded LDAP control.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         provided ASN.1 element as an LDAP control.
   */
  public static LDAPControl decode(ASN1Element element)
         throws LDAPException
  {
    if (element == null)
    {
      Message message = ERR_LDAP_CONTROL_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }


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

      Message message = ERR_LDAP_CONTROL_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    int numElements = elements.size();
    if ((numElements < 1) || (numElements > 3))
    {
      Message message =
          ERR_LDAP_CONTROL_DECODE_INVALID_ELEMENT_COUNT.get(numElements);
      throw new LDAPException(PROTOCOL_ERROR, message);
    }


    String oid;
    try
    {
      oid = elements.get(0).decodeAsOctetString().stringValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_CONTROL_DECODE_OID.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    if (numElements == 1)
    {
      return new LDAPControl(oid);
    }
    else if (numElements == 2)
    {
      boolean         isCritical;
      ASN1OctetString value;

      ASN1Element e = elements.get(1);
      switch (e.getType())
      {
        case UNIVERSAL_BOOLEAN_TYPE:
          value = null;

          try
          {
            isCritical = e.decodeAsBoolean().booleanValue();
          }
          catch (Exception e2)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e2);
            }

            Message message =
                ERR_LDAP_CONTROL_DECODE_CRITICALITY.get(String.valueOf(e));
            throw new LDAPException(PROTOCOL_ERROR, message, e2);
          }
          break;
        case UNIVERSAL_OCTET_STRING_TYPE:
          isCritical = false;

          try
          {
            value = e.decodeAsOctetString();
          }
          catch (Exception e2)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e2);
            }

            Message message =
                ERR_LDAP_CONTROL_DECODE_VALUE.get(String.valueOf(e));
            throw new LDAPException(PROTOCOL_ERROR, message, e2);
          }
          break;
        default:
          Message message =
              ERR_LDAP_CONTROL_DECODE_INVALID_TYPE.get(e.getType());
          throw new LDAPException(PROTOCOL_ERROR, message);
      }

      return new LDAPControl(oid, isCritical, value);
    }
    else
    {
      boolean isCritical;
      try
      {
        isCritical = elements.get(1).decodeAsBoolean().booleanValue();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_LDAP_CONTROL_DECODE_CRITICALITY.get(String.valueOf(e));
        throw new LDAPException(PROTOCOL_ERROR, message, e);
      }

      ASN1OctetString value;
      try
      {
        value = elements.get(2).decodeAsOctetString();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_LDAP_CONTROL_DECODE_VALUE.get(String.valueOf(e));
        throw new LDAPException(PROTOCOL_ERROR, message, e);
      }

      return new LDAPControl(oid, isCritical, value);
    }
  }



  /**
   * Decodes the provided ASN.1 element as a set of controls.
   *
   * @param  element  The ASN.1 element containing the encoded set of controls.
   *
   * @return  The decoded set of controls.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         controls.
   */
  public static ArrayList<LDAPControl> decodeControls(ASN1Element element)
         throws LDAPException
  {
    if (element == null)
    {
      Message message = ERR_LDAP_CONTROL_DECODE_CONTROLS_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }


    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      Message message =
          ERR_LDAP_CONTROL_DECODE_CONTROLS_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ArrayList<LDAPControl> controls =
         new ArrayList<LDAPControl>(elements.size());
    for (ASN1Element e : elements)
    {
      controls.add(decode(e));
    }

    return controls;
  }



  /**
   * Retrieves the OID for this control.
   *
   * @return  The OID for this control.
   */
  public String getOID()
  {
    return control.getOID();
  }



  /**
   * Indicates whether this control should be considered critical.
   *
   * @return  <CODE>true</CODE> if this control should be considered critical,
   *          or <CODE>false</CODE> if not.
   */
  public boolean isCritical()
  {
    return control.isCritical();
  }



  /**
   * Retrieves the value for this control.
   *
   * @return  The value for this control, or <CODE>null</CODE> if there is none.
   */
  public ASN1OctetString getValue()
  {
    return control.getValue();
  }



  /**
   * Retrieves a string representation of this LDAP control.
   *
   * @return  A string representation of this LDAP control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this LDAP control to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPControl(oid=");
    buffer.append(control.getOID());
    buffer.append(", criticality=");
    buffer.append(control.isCritical());

    ASN1OctetString value = control.getValue();
    if (value != null)
    {
      buffer.append(", value=");
      buffer.append(String.valueOf(value));
    }

    buffer.append(")");
  }



  /**
   * Appends a multi-line string representation of this LDAP control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces to indent the information.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("LDAP Control");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  OID:  ");
    buffer.append(control.getOID());
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Criticality:  ");
    buffer.append(control.isCritical());
    buffer.append(EOL);

    ASN1OctetString value = control.getValue();
    if (value != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Value:");
      value.toString(buffer, indent+4);
    }
  }
}

