package org.opends.sdk.controls;



import static com.sun.opends.sdk.util.Messages.ERR_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION;
import static com.sun.opends.sdk.util.Messages.ERR_PWEXPIRING_NO_CONTROL_VALUE;
import static org.opends.sdk.util.StaticUtils.getExceptionMessage;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.DecodeException;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.StaticUtils;



/**
 * This class implements the Netscape password expiring control, which
 * serves as a warning to clients that the user's password is about to
 * expire. The only element contained in the control value is a string
 * representation of the number of seconds until expiration.
 */
public class PasswordExpiringControl extends Control
{
  /**
   * The OID for the Netscape password expiring control.
   */
  public static final String OID_NS_PASSWORD_EXPIRING = "2.16.840.1.113730.3.4.5";



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class Decoder implements
      ControlDecoder<PasswordExpiringControl>
  {
    /**
     * {@inheritDoc}
     */
    public PasswordExpiringControl decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_PWEXPIRING_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      int secondsUntilExpiration;
      try
      {
        secondsUntilExpiration = Integer.parseInt(value.toString());
      }
      catch (Exception e)
      {
        StaticUtils.DEBUG_LOG.throwing(
            "PasswordExpiringControl.Decoder", "decode", e);

        Message message = ERR_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION
            .get(getExceptionMessage(e));
        throw DecodeException.error(message);
      }

      return new PasswordExpiringControl(isCritical,
          secondsUntilExpiration);
    }



    public String getOID()
    {
      return OID_NS_PASSWORD_EXPIRING;
    }

  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<PasswordExpiringControl> DECODER = new Decoder();

  // The length of time in seconds until the password actually expires.
  private final int secondsUntilExpiration;



  /**
   * Creates a new instance of the password expiring control with the
   * provided information.
   * 
   * @param isCritical
   *          Indicates whether support for this control should be
   *          considered a critical part of the client processing.
   * @param secondsUntilExpiration
   *          The length of time in seconds until the password actually
   *          expires.
   */
  public PasswordExpiringControl(boolean isCritical,
      int secondsUntilExpiration)
  {
    super(OID_NS_PASSWORD_EXPIRING, isCritical);

    this.secondsUntilExpiration = secondsUntilExpiration;
  }



  /**
   * Creates a new instance of the password expiring control with the
   * provided information.
   * 
   * @param secondsUntilExpiration
   *          The length of time in seconds until the password actually
   *          expires.
   */
  public PasswordExpiringControl(int secondsUntilExpiration)
  {
    this(false, secondsUntilExpiration);
  }



  /**
   * Retrieves the length of time in seconds until the password actually
   * expires.
   * 
   * @return The length of time in seconds until the password actually
   *         expires.
   */
  public int getSecondsUntilExpiration()
  {
    return secondsUntilExpiration;
  }



  @Override
  public ByteString getValue()
  {
    return ByteString.valueOf(String.valueOf(secondsUntilExpiration));
  }



  @Override
  public boolean hasValue()
  {
    return true;
  }



  /**
   * Appends a string representation of this password expiring control
   * to the provided buffer.
   * 
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PasswordExpiringControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", secondsUntilExpiration=");
    buffer.append(secondsUntilExpiration);
    buffer.append(")");
  }
}
