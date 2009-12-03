package org.opends.sdk.controls;



import static com.sun.opends.sdk.util.Messages.ERR_ACCTUSABLEREQ_CONTROL_HAS_VALUE;
import static com.sun.opends.sdk.util.Messages.ERR_ACCTUSABLERES_DECODE_ERROR;
import static com.sun.opends.sdk.util.Messages.ERR_ACCTUSABLERES_NO_CONTROL_VALUE;
import static com.sun.opends.sdk.util.Messages.ERR_ACCTUSABLERES_UNKNOWN_VALUE_ELEMENT_TYPE;
import static org.opends.sdk.util.StaticUtils.byteToHex;
import static org.opends.sdk.util.StaticUtils.getExceptionMessage;

import java.io.IOException;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.DecodeException;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.ByteStringBuilder;
import org.opends.sdk.util.StaticUtils;



/**
 * This class implements the Sun-defined account usable control.
 */
public class AccountUsabilityControl
{
  /**
   * The OID for the account usable request and response controls.
   */
  public static final String OID_ACCOUNT_USABLE_CONTROL = "1.3.6.1.4.1.42.2.27.9.5.8";



  /**
   * This class implements the Sun-defined account usable request
   * control. The OID for this control is 1.3.6.1.4.1.42.2.27.9.5.8, and
   * it does not have a value.
   */
  public static class Request extends Control
  {
    public Request()
    {
      super(OID_ACCOUNT_USABLE_CONTROL, false);
    }



    public Request(boolean isCritical)
    {
      super(OID_ACCOUNT_USABLE_CONTROL, isCritical);
    }



    @Override
    public ByteString getValue()
    {
      return null;
    }



    @Override
    public boolean hasValue()
    {
      return false;
    }



    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("AccountUsableRequestControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(")");
    }
  }



  /**
   * This class implements the account usable response control. This is
   * a Sun-defined control with OID 1.3.6.1.4.1.42.2.27.9.5.8. The value
   * of this control is composed according to the following BNF: <BR>
   * 
   * <PRE>
   * ACCOUNT_USABLE_RESPONSE ::= CHOICE {
   *      is_available           [0] INTEGER, -- Seconds before expiration --
   *      is_not_available       [1] MORE_INFO }
   * MORE_INFO ::= SEQUENCE {
   *      inactive               [0] BOOLEAN DEFAULT FALSE,
   *      reset                  [1] BOOLEAN DEFAULT FALSE,
   *      expired                [2] BOOLEAN DEFAULT_FALSE,
   *      remaining_grace        [3] INTEGER OPTIONAL,
   *      seconds_before_unlock  [4] INTEGER OPTIONAL }
   * </PRE>
   */
  public static class Response extends Control
  {
    // Indicates whether the user's account is usable.
    private final boolean isUsable;

    // Indicates whether the user's password is expired.
    private final boolean isExpired;

    // Indicates whether the user's account is inactive.
    private final boolean isInactive;

    // Indicates whether the user's account is currently locked.
    private final boolean isLocked;

    // Indicates whether the user's password has been reset and must be
    // changed
    // before anything else can be done.
    private final boolean isReset;

    // The number of remaining grace logins, if available.
    private final int remainingGraceLogins;

    // The length of time in seconds before the user's password expires,
    // if
    // available.
    private final int secondsBeforeExpiration;

    // The length of time before the user's account is unlocked, if
    // available.
    private final int secondsBeforeUnlock;



    /**
     * Creates a new account usability response control that may be used
     * to indicate that the account is not available and provide
     * information about the underlying reason. It will use the default
     * OID and criticality.
     * 
     * @param isCritical
     *          Indicates whether this control should be considered
     *          critical in processing the request.
     * @param isInactive
     *          Indicates whether the user's account has been
     *          inactivated by an administrator.
     * @param isReset
     *          Indicates whether the user's password has been reset by
     *          an administrator.
     * @param isExpired
     *          Indicates whether the user's password is expired.
     * @param remainingGraceLogins
     *          The number of grace logins remaining. A value of zero
     *          indicates that there are none remaining. A value of -1
     *          indicates that grace login functionality is not enabled.
     * @param isLocked
     *          Indicates whether the user's account is currently locked
     *          out.
     * @param secondsBeforeUnlock
     *          The length of time in seconds until the account is
     *          unlocked. A value of -1 indicates that the account will
     *          not be automatically unlocked and must be reset by an
     *          administrator.
     */
    public Response(boolean isCritical, boolean isInactive,
        boolean isReset, boolean isExpired, int remainingGraceLogins,
        boolean isLocked, int secondsBeforeUnlock)
    {
      super(OID_ACCOUNT_USABLE_CONTROL, isCritical);

      this.isInactive = isInactive;
      this.isReset = isReset;
      this.isExpired = isExpired;
      this.remainingGraceLogins = remainingGraceLogins;
      this.isLocked = isLocked;
      this.secondsBeforeUnlock = secondsBeforeUnlock;

      isUsable = false;
      secondsBeforeExpiration = -1;
    }



    /**
     * Creates a new account usability response control that may be used
     * to indicate that the account is not available and provide
     * information about the underlying reason. It will use the default
     * OID and criticality.
     * 
     * @param isInactive
     *          Indicates whether the user's account has been
     *          inactivated by an administrator.
     * @param isReset
     *          Indicates whether the user's password has been reset by
     *          an administrator.
     * @param isExpired
     *          Indicates whether the user's password is expired.
     * @param remainingGraceLogins
     *          The number of grace logins remaining. A value of zero
     *          indicates that there are none remaining. A value of -1
     *          indicates that grace login functionality is not enabled.
     * @param isLocked
     *          Indicates whether the user's account is currently locked
     *          out.
     * @param secondsBeforeUnlock
     *          The length of time in seconds until the account is
     *          unlocked. A value of -1 indicates that the account will
     *          not be automatically unlocked and must be reset by an
     *          administrator.
     */
    public Response(boolean isInactive, boolean isReset,
        boolean isExpired, int remainingGraceLogins, boolean isLocked,
        int secondsBeforeUnlock)
    {
      this(false, isInactive, isReset, isExpired, remainingGraceLogins,
          isLocked, secondsBeforeUnlock);
    }



    /**
     * Creates a new account usability response control that may be used
     * to indicate that the account is available and provide the number
     * of seconds until expiration. It will use the default OID and
     * criticality.
     * 
     * @param isCritical
     *          Indicates whether this control should be considered
     *          critical in processing the request.
     * @param secondsBeforeExpiration
     *          The length of time in seconds until the user's password
     *          expires, or -1 if the user's password will not expire or
     *          the expiration time is unknown.
     */
    public Response(boolean isCritical, int secondsBeforeExpiration)
    {
      super(OID_ACCOUNT_USABLE_CONTROL, isCritical);

      this.secondsBeforeExpiration = secondsBeforeExpiration;

      isUsable = true;
      isInactive = false;
      isReset = false;
      isExpired = false;
      remainingGraceLogins = -1;
      isLocked = false;
      secondsBeforeUnlock = 0;
    }



    /**
     * Creates a new account usability response control that may be used
     * to indicate that the account is available and provide the number
     * of seconds until expiration. It will use the default OID and
     * criticality.
     * 
     * @param secondsBeforeExpiration
     *          The length of time in seconds until the user's password
     *          expires, or -1 if the user's password will not expire or
     *          the expiration time is unknown.
     */
    public Response(int secondsBeforeExpiration)
    {
      this(false, secondsBeforeExpiration);
    }



    /**
     * Retrieves the number of remaining grace logins for the user. This
     * value is unreliable if the user's password is not expired.
     * 
     * @return The number of remaining grace logins for the user, or -1
     *         if the grace logins feature is not enabled for the user.
     */
    public int getRemainingGraceLogins()
    {
      return remainingGraceLogins;
    }



    /**
     * Retrieves the length of time in seconds before the user's
     * password expires. This value is unreliable if the account is not
     * available.
     * 
     * @return The length of time in seconds before the user's password
     *         expires, or -1 if it is unknown or password expiration is
     *         not enabled for the user.
     */
    public int getSecondsBeforeExpiration()
    {
      return secondsBeforeExpiration;
    }



    /**
     * Retrieves the length of time in seconds before the user's account
     * is automatically unlocked. This value is unreliable is the user's
     * account is not locked.
     * 
     * @return The length of time in seconds before the user's account
     *         is automatically unlocked, or -1 if it requires
     *         administrative action to unlock the account.
     */
    public int getSecondsBeforeUnlock()
    {
      return secondsBeforeUnlock;
    }



    @Override
    public ByteString getValue()
    {
      ByteStringBuilder buffer = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(buffer);
      try
      {
        writeValue(writer);
        return buffer.toByteString();
      }
      catch (IOException ioe)
      {
        // This should never happen unless there is a bug somewhere.
        throw new RuntimeException(ioe);
      }
    }



    @Override
    public boolean hasValue()
    {
      return true;
    }



    /**
     * Indicates whether the user's password is expired.
     * 
     * @return <CODE>true</CODE> if the user's password is expired, or
     *         <CODE>false</CODE> if not.
     */
    public boolean isExpired()
    {
      return isExpired;
    }



    /**
     * Indicates whether the user's account has been inactivated by an
     * administrator.
     * 
     * @return <CODE>true</CODE> if the user's account has been
     *         inactivated by an administrator, or <CODE>false</CODE> if
     *         not.
     */
    public boolean isInactive()
    {
      return isInactive;
    }



    /**
     * Indicates whether the user's account is locked for some reason.
     * 
     * @return <CODE>true</CODE> if the user's account is locked, or
     *         <CODE>false</CODE> if it is not.
     */
    public boolean isLocked()
    {
      return isLocked;
    }



    /**
     * Indicates whether the user's password has been administratively
     * reset and the user must change that password before any other
     * operations will be allowed.
     * 
     * @return <CODE>true</CODE> if the user's password has been
     *         administratively reset, or <CODE>false</CODE> if not.
     */
    public boolean isReset()
    {
      return isReset;
    }



    /**
     * Indicates whether the associated user account is available for
     * use.
     * 
     * @return <CODE>true</CODE> if the associated user account is
     *         available, or <CODE>false</CODE> if not.
     */
    public boolean isUsable()
    {
      return isUsable;
    }



    /**
     * Appends a string representation of this password policy response
     * control to the provided buffer.
     * 
     * @param buffer
     *          The buffer to which the information should be appended.
     */
    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("AccountUsableResponseControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", isUsable=");
      buffer.append(isUsable);
      if (isUsable)
      {
        buffer.append(",secondsBeforeExpiration=");
        buffer.append(secondsBeforeExpiration);
      }
      else
      {
        buffer.append(",isInactive=");
        buffer.append(isInactive);
        buffer.append(",isReset=");
        buffer.append(isReset);
        buffer.append(",isExpired=");
        buffer.append(isExpired);
        buffer.append(",remainingGraceLogins=");
        buffer.append(remainingGraceLogins);
        buffer.append(",isLocked=");
        buffer.append(isLocked);
        buffer.append(",secondsBeforeUnlock=");
        buffer.append(secondsBeforeUnlock);
      }

      buffer.append(")");
    }



    /**
     * Writes this control's value to an ASN.1 writer.
     * 
     * @param writer
     *          The ASN.1 output stream to write to.
     * @throws IOException
     *           If a problem occurs while writing to the stream.
     */
    public void writeValue(ASN1Writer writer) throws IOException
    {
      if (secondsBeforeExpiration < 0)
      {
        writer.writeInteger(TYPE_SECONDS_BEFORE_EXPIRATION,
            secondsBeforeExpiration);
      }
      else
      {
        writer.writeStartSequence(TYPE_MORE_INFO);
        if (isInactive)
        {
          writer.writeBoolean(TYPE_INACTIVE, true);
        }

        if (isReset)
        {
          writer.writeBoolean(TYPE_RESET, true);
        }

        if (isExpired)
        {
          writer.writeBoolean(TYPE_EXPIRED, true);

          if (remainingGraceLogins >= 0)
          {
            writer.writeInteger(TYPE_REMAINING_GRACE_LOGINS,
                remainingGraceLogins);
          }
        }

        if (isLocked)
        {
          writer.writeInteger(TYPE_SECONDS_BEFORE_UNLOCK,
              secondsBeforeUnlock);
        }
        writer.writeEndSequence();
      }
    }
  }



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private static final class RequestDecoder implements
      ControlDecoder<Request>
  {
    /**
     * {@inheritDoc}
     */
    public Request decode(boolean isCritical, ByteString value, Schema schema)
        throws DecodeException
    {
      if (value != null)
      {
        Message message = ERR_ACCTUSABLEREQ_CONTROL_HAS_VALUE.get();
        throw DecodeException.error(message);
      }

      return new Request(isCritical);
    }



    public String getOID()
    {
      return OID_ACCOUNT_USABLE_CONTROL;
    }

  }



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class ResponseDecoder implements
      ControlDecoder<Response>
  {
    /**
     * {@inheritDoc}
     */
    public Response decode(boolean isCritical, ByteString value, Schema schema)
        throws DecodeException
    {
      if (value == null)
      {
        // The response control must always have a value.
        Message message = ERR_ACCTUSABLERES_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      try
      {
        ASN1Reader reader = ASN1.getReader(value);
        switch (reader.peekType())
        {
        case TYPE_SECONDS_BEFORE_EXPIRATION:
          int secondsBeforeExpiration = (int) reader.readInteger();
          return new Response(isCritical, secondsBeforeExpiration);
        case TYPE_MORE_INFO:
          boolean isInactive = false;
          boolean isReset = false;
          boolean isExpired = false;
          boolean isLocked = false;
          int remainingGraceLogins = -1;
          int secondsBeforeUnlock = 0;

          reader.readStartSequence();
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_INACTIVE))
          {
            isInactive = reader.readBoolean();
          }
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_RESET))
          {
            isReset = reader.readBoolean();
          }
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_EXPIRED))
          {
            isExpired = reader.readBoolean();
          }
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_REMAINING_GRACE_LOGINS))
          {
            remainingGraceLogins = (int) reader.readInteger();
          }
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_SECONDS_BEFORE_UNLOCK))
          {
            isLocked = true;
            secondsBeforeUnlock = (int) reader.readInteger();
          }
          reader.readEndSequence();

          return new Response(isCritical, isInactive, isReset,
              isExpired, remainingGraceLogins, isLocked,
              secondsBeforeUnlock);

        default:
          Message message = ERR_ACCTUSABLERES_UNKNOWN_VALUE_ELEMENT_TYPE
              .get(byteToHex(reader.peekType()));
          throw DecodeException.error(message);
        }
      }
      catch (IOException e)
      {
        StaticUtils.DEBUG_LOG.throwing(
            "AccountUsabilityControl.ResponseDecoder", "decode", e);

        Message message = ERR_ACCTUSABLERES_DECODE_ERROR
            .get(getExceptionMessage(e));
        throw DecodeException.error(message);
      }
    }



    public String getOID()
    {
      return OID_ACCOUNT_USABLE_CONTROL;
    }

  }



  /**
   * The BER type to use for the seconds before expiration when the
   * account is available.
   */
  private static final byte TYPE_SECONDS_BEFORE_EXPIRATION = (byte) 0x80;

  /**
   * The BER type to use for the MORE_INFO sequence when the account is
   * not available.
   */
  private static final byte TYPE_MORE_INFO = (byte) 0xA1;

  /**
   * The BER type to use for the MORE_INFO element that indicates that
   * the account has been inactivated.
   */
  private static final byte TYPE_INACTIVE = (byte) 0x80;

  /**
   * The BER type to use for the MORE_INFO element that indicates that
   * the password has been administratively reset.
   */
  private static final byte TYPE_RESET = (byte) 0x81;

  /**
   * The BER type to use for the MORE_INFO element that indicates that
   * the user's password is expired.
   */
  private static final byte TYPE_EXPIRED = (byte) 0x82;

  /**
   * The BER type to use for the MORE_INFO element that provides the
   * number of remaining grace logins.
   */
  private static final byte TYPE_REMAINING_GRACE_LOGINS = (byte) 0x83;

  /**
   * The BER type to use for the MORE_INFO element that indicates that
   * the password has been administratively reset.
   */
  private static final byte TYPE_SECONDS_BEFORE_UNLOCK = (byte) 0x84;

  /**
   * The Control Decoder that can be used to decode the request control.
   */
  public static final ControlDecoder<Request> REQUEST_DECODER = new RequestDecoder();

  /**
   * The Control Decoder that can be used to decode the response
   * control.
   */
  public static final ControlDecoder<Response> RESPONSE_DECODER = new ResponseDecoder();
}
