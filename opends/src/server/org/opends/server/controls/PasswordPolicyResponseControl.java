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

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the password policy response control defined in
 * draft-behera-ldap-password-policy.  The value may have zero, one, or two
 * elements, which may include flags to indicate a warning and/or an error.
 */
public class PasswordPolicyResponseControl
       extends Control
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.controls.PasswordPolicyResponseControl";



  /**
   * The BER type value for the warning element of the control value.
   */
  public static final byte TYPE_WARNING_ELEMENT = (byte) 0xA0;



  /**
   * The BER type value for the error element of the control value.
   */
  public static final byte TYPE_ERROR_ELEMENT = (byte) 0x81;



  // The warning value for this password policy response control.
  private int warningValue;

  // The error type for this password policy response control.
  private PasswordPolicyErrorType errorType;

  // The warning type for the password policy response control.
  private PasswordPolicyWarningType warningType;



  /**
   * Creates a new instance of the password policy response control with the
   * default OID and criticality, and without either a warning or an error flag.
   */
  public PasswordPolicyResponseControl()
  {
    super(OID_PASSWORD_POLICY_CONTROL, false, encodeValue(null, -1, null));

    assert debugConstructor(CLASS_NAME);

    warningType  = null;
    errorType    = null;
    warningValue = -1;
  }



  /**
   * Creates a new instance of this password policy response control with the
   * default OID and criticality, and with the provided warning and/or error
   * flag information.
   *
   * @param  warningType   The warning type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be a warning flag.
   * @param  warningValue  The warning value to use for this password policy
   *                       response control, if applicable.
   * @param  errorType     The error type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be an error flag.
   */
  public PasswordPolicyResponseControl(PasswordPolicyWarningType warningType,
                                       int warningValue,
                                       PasswordPolicyErrorType errorType)
  {
    super(OID_PASSWORD_POLICY_CONTROL, false,
          encodeValue(warningType, warningValue, errorType));

    assert debugConstructor(CLASS_NAME, String.valueOf(warningType),
                            String.valueOf(warningValue),
                            String.valueOf(errorType));

    this.warningType  = warningType;
    this.warningValue = warningValue;
    this.errorType    = errorType;
  }



  /**
   * Creates a new instance of the password policy request control with the
   * provided information.
   *
   * @param  oid           The OID to use for this control.
   * @param  isCritical    Indicates whether support for this control should be
   *                       considered a critical part of the client processing.
   * @param  warningType   The warning type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be a warning flag.
   * @param  warningValue  The warning value to use for this password policy
   *                       response control, if applicable.
   * @param  errorType     The error type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be an error flag.
   */
  public PasswordPolicyResponseControl(String oid, boolean isCritical,
                                       PasswordPolicyWarningType warningType,
                                       int warningValue,
                                       PasswordPolicyErrorType errorType)
  {
    super(oid, isCritical, encodeValue(warningType, warningValue, errorType));

    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(isCritical),
                            String.valueOf(warningType),
                            String.valueOf(warningValue),
                            String.valueOf(errorType));

    this.warningType  = warningType;
    this.warningValue = warningValue;
    this.errorType    = errorType;
  }



  /**
   * Creates a new instance of the password policy request control with the
   * provided information.
   *
   * @param  oid           The OID to use for this control.
   * @param  isCritical    Indicates whether support for this control should be
   *                       considered a critical part of the client processing.
   * @param  warningType   The warning type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be a warning flag.
   * @param  warningValue  The warning value to use for this password policy
   *                       response control, if applicable.
   * @param  errorType     The error type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be an error flag.
   * @param  encodedValue  The pre-encoded value to use for this control.
   */
  private PasswordPolicyResponseControl(String oid, boolean isCritical,
                                        PasswordPolicyWarningType warningType,
                                        int warningValue,
                                        PasswordPolicyErrorType errorType,
                                        ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);

    assert debugConstructor(CLASS_NAME, String.valueOf(oid),
                            String.valueOf(isCritical),
                            String.valueOf(warningType),
                            String.valueOf(warningValue),
                            String.valueOf(errorType));

    this.warningType  = warningType;
    this.warningValue = warningValue;
    this.errorType    = errorType;
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the value for this control.
   *
   * @param  warningType   The warning type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be a warning flag.
   * @param  warningValue  The warning value to use for this password policy
   *                       response control, if applicable.
   * @param  errorType     The error type to use for this password policy
   *                       response control, or <CODE>null</CODE> if there
   *                       should not be an error flag.
   *
   * @return  An ASN.1 octet string containing the encoded control value.
   */
  private static ASN1OctetString encodeValue(
                                      PasswordPolicyWarningType warningType,
                                      int warningValue,
                                      PasswordPolicyErrorType errorType)
  {
    assert debugEnter(CLASS_NAME, "encodeValue", String.valueOf(warningType),
                      String.valueOf(warningValue), String.valueOf(errorType));

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);

    if (warningType != null)
    {
      ASN1Integer warningInteger = new ASN1Integer(warningType.getType(),
                                                   warningValue);
      elements.add(new ASN1Element(TYPE_WARNING_ELEMENT,
                                   warningInteger.encode()));
    }

    if (errorType != null)
    {
      elements.add(new ASN1Enumerated(TYPE_ERROR_ELEMENT,
                                      errorType.intValue()));
    }

    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    return new ASN1OctetString(valueSequence.encode());
  }



  /**
   * Creates a new password policy response control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this password policy response control.
   *
   * @return  The password policy response control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         password policy response control.
   */
  public static PasswordPolicyResponseControl decodeControl(Control control)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeControl", String.valueOf(control));


    ASN1OctetString controlValue = control.getValue();
    if (controlValue == null)
    {
      // The response control must always have a value.
      int    msgID   = MSGID_PWPOLICYRES_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    try
    {
      PasswordPolicyWarningType warningType  = null;
      PasswordPolicyErrorType   errorType    = null;
      int                       warningValue = -1;

      ASN1Sequence valueSequence =
           ASN1Sequence.decodeAsSequence(controlValue.value());
      for (ASN1Element e : valueSequence.elements())
      {
        switch (e.getType())
        {
          case TYPE_WARNING_ELEMENT:
            ASN1Integer integerElement = ASN1Integer.decodeAsInteger(e.value());
            warningValue = integerElement.intValue();
            warningType =
                 PasswordPolicyWarningType.valueOf(integerElement.getType());
            if (warningType == null)
            {
              int    msgID   = MSGID_PWPOLICYRES_INVALID_WARNING_TYPE;
              String message = getMessage(msgID,
                                          byteToHex(integerElement.getType()));
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
            }
            break;

          case TYPE_ERROR_ELEMENT:
            int errorValue = e.decodeAsEnumerated().intValue();
            errorType = PasswordPolicyErrorType.valueOf(errorValue);
            if (errorType == null)
            {
              int    msgID   = MSGID_PWPOLICYRES_INVALID_ERROR_TYPE;
              String message = getMessage(msgID, errorValue);
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
            }
            break;

          default:
            int    msgID   = MSGID_PWPOLICYRES_INVALID_ELEMENT_TYPE;
            String message = getMessage(msgID, byteToHex(e.getType()));
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                    message);
        }
      }

      return new PasswordPolicyResponseControl(control.getOID(),
                                               control.isCritical(),
                                               warningType, warningValue,
                                               errorType, controlValue);
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (ASN1Exception ae)
    {
      assert debugException(CLASS_NAME, "decodeControl", ae);

      int    msgID   = MSGID_PWPOLICYRES_DECODE_ERROR;
      String message = getMessage(msgID, ae.getMessage());
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeControl", e);

      int    msgID   = MSGID_PWPOLICYRES_DECODE_ERROR;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }
  }



  /**
   * Retrieves the password policy warning type contained in this control.
   *
   * @return  The password policy warning type contained in this control, or
   *          <CODE>null</CODE> if there is no warning type.
   */
  public PasswordPolicyWarningType getWarningType()
  {
    assert debugEnter(CLASS_NAME, "getWarningType");

    return warningType;
  }



  /**
   * Retrieves the password policy warning value for this control.  The value is
   * undefined if there is no warning type.
   *
   * @return  The password policy warning value for this control.
   */
  public int getWarningValue()
  {
    assert debugEnter(CLASS_NAME, "getWarningValue");

    return warningValue;
  }



  /**
   * Retrieves the password policy error type contained in this control.
   *
   * @return  The password policy error type contained in this control, or
   *          <CODE>null</CODE> if there is no error type.
   */
  public PasswordPolicyErrorType getErrorType()
  {
    assert debugEnter(CLASS_NAME, "getErrorType");

    return errorType;
  }



  /**
   * Retrieves a string representation of this password policy response control.
   *
   * @return  A string representation of this password policy response control.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this password policy response control to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("PasswordPolicyResponseControl(");

    if (warningType != null)
    {
      buffer.append(warningType.toString());
      buffer.append("=");
      buffer.append(warningValue);

      if (errorType != null)
      {
        buffer.append(", ");
      }
    }

    if (errorType != null)
    {
      buffer.append(errorType.toString());
    }

    buffer.append(")");
  }
}

