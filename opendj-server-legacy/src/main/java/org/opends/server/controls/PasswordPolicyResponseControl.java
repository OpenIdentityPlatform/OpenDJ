/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.controls;
import org.forgerock.i18n.LocalizableMessage;



import java.io.IOException;

import org.forgerock.opendj.io.*;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ProtocolMessages.*;
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
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<PasswordPolicyResponseControl>
  {
    @Override
    public PasswordPolicyResponseControl decode(boolean isCritical,
                                                ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        // The response control must always have a value.
        LocalizableMessage message = ERR_PWPOLICYRES_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        PasswordPolicyWarningType warningType  = null;
        PasswordPolicyErrorType   errorType    = null;
        int                       warningValue = -1;

        reader.readStartSequence();

        if(reader.hasNextElement() &&
            reader.peekType() == TYPE_WARNING_ELEMENT)
        {
          // Its a CHOICE element. Read as sequence to retrieve
          // nested element.
          reader.readStartSequence();
          warningType =
              PasswordPolicyWarningType.valueOf(reader.peekType());
          warningValue = (int)reader.readInteger();
          if (warningType == null)
          {
            LocalizableMessage message = ERR_PWPOLICYRES_INVALID_WARNING_TYPE.get(
                byteToHex(reader.peekType()));
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                message);
          }
          reader.readEndSequence();
        }
        if(reader.hasNextElement() &&
            reader.peekType() == TYPE_ERROR_ELEMENT)
        {
          int errorValue = (int)reader.readInteger();
          errorType = PasswordPolicyErrorType.valueOf(errorValue);
          if (errorType == null)
          {
            LocalizableMessage message =
                ERR_PWPOLICYRES_INVALID_ERROR_TYPE.get(errorValue);
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                message);
          }
        }

        reader.readEndSequence();

        return new PasswordPolicyResponseControl(isCritical,
            warningType, warningValue,
            errorType);
      }
      catch (DirectoryException de)
      {
        throw de;
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message =
            ERR_PWPOLICYRES_DECODE_ERROR.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }
    }


    @Override
    public String getOID()
    {
      return OID_ACCOUNT_USABLE_CONTROL;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<PasswordPolicyResponseControl> DECODER =
    new Decoder();
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();




  /** The BER type value for the warning element of the control value. */
  public static final byte TYPE_WARNING_ELEMENT = (byte) 0xA0;



  /** The BER type value for the error element of the control value. */
  public static final byte TYPE_ERROR_ELEMENT = (byte) 0x81;



  /** The warning value for this password policy response control. */
  private int warningValue;

  /** The error type for this password policy response control. */
  private PasswordPolicyErrorType errorType;

  /** The warning type for the password policy response control. */
  private PasswordPolicyWarningType warningType;



  /**
   * Creates a new instance of the password policy response control with the
   * default OID and criticality, and without either a warning or an error flag.
   */
  public PasswordPolicyResponseControl()
  {
    this(false, null, -1, null);
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
    this(false, warningType, warningValue, errorType);
  }



  /**
   * Creates a new instance of the password policy request control with the
   * provided information.
   *
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
  public PasswordPolicyResponseControl(boolean isCritical,
                                       PasswordPolicyWarningType warningType,
                                       int warningValue,
                                       PasswordPolicyErrorType errorType)
  {
    super(OID_PASSWORD_POLICY_CONTROL, isCritical);

    this.warningType  = warningType;
    this.warningValue = warningValue;
    this.errorType    = errorType;
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    if (warningType != null)
    {
      // Just write the CHOICE element as a single element SEQUENCE.
      writer.writeStartSequence(TYPE_WARNING_ELEMENT);
      writer.writeInteger(warningType.getType(), warningValue);
      writer.writeEndSequence();
    }

    if (errorType != null)
    {
      writer.writeInteger(TYPE_ERROR_ELEMENT, errorType.intValue());
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }


  /**
   * Retrieves the password policy warning type contained in this control.
   *
   * @return  The password policy warning type contained in this control, or
   *          <CODE>null</CODE> if there is no warning type.
   */
  public PasswordPolicyWarningType getWarningType()
  {
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
    return errorType;
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordPolicyResponseControl(");

    if (warningType != null)
    {
      buffer.append(warningType);
      buffer.append("=");
      buffer.append(warningValue);

      if (errorType != null)
      {
        buffer.append(", ");
      }
    }

    if (errorType != null)
    {
      buffer.append(errorType);
    }

    buffer.append(")");
  }
}

