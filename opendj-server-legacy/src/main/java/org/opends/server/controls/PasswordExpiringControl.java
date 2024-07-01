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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.controls;
import org.forgerock.i18n.LocalizableMessage;



import org.forgerock.opendj.io.ASN1Writer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;


/**
 * This class implements the Netscape password expiring control, which serves as
 * a warning to clients that the user's password is about to expire. The only
 * element contained in the control value is a string representation of the
 * number of seconds until expiration.
 */
public class PasswordExpiringControl
       extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<PasswordExpiringControl>
  {
    @Override
    public PasswordExpiringControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_PWEXPIRING_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      int secondsUntilExpiration;
      try
      {
        secondsUntilExpiration =
            Integer.parseInt(value.toString());
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION.
            get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      return new PasswordExpiringControl(isCritical,
          secondsUntilExpiration);
    }

    @Override
    public String getOID()
    {
      return OID_NS_PASSWORD_EXPIRING;
    }

  }

  /** The Control Decoder that can be used to decode this control. */
  public static final ControlDecoder<PasswordExpiringControl> DECODER =
    new Decoder();
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();




  /** The length of time in seconds until the password actually expires. */
  private int secondsUntilExpiration;



  /**
   * Creates a new instance of the password expiring control with the provided
   * information.
   *
   * @param  secondsUntilExpiration  The length of time in seconds until the
   *                                 password actually expires.
   */
  public PasswordExpiringControl(int secondsUntilExpiration)
  {
    this(false, secondsUntilExpiration);
  }



  /**
   * Creates a new instance of the password expiring control with the provided
   * information.
   *
   * @param  isCritical              Indicates whether support for this control
   *                                 should be considered a critical part of the
   *                                 client processing.
   * @param  secondsUntilExpiration  The length of time in seconds until the
   *                                 password actually expires.
   */
  public PasswordExpiringControl(boolean isCritical, int secondsUntilExpiration)
  {
    super(OID_NS_PASSWORD_EXPIRING, isCritical);


    this.secondsUntilExpiration = secondsUntilExpiration;
  }

  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeOctetString(String.valueOf(secondsUntilExpiration));
  }



  /**
   * Retrieves the length of time in seconds until the password actually
   * expires.
   *
   * @return  The length of time in seconds until the password actually expires.
   */
  public int getSecondsUntilExpiration()
  {
    return secondsUntilExpiration;
  }



  /**
   * Appends a string representation of this password expiring control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordExpiringControl(secondsUntilExpiration=");
    buffer.append(secondsUntilExpiration);
    buffer.append(")");
  }
}

