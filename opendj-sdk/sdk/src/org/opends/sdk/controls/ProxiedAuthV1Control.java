package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.DN;
import org.opends.sdk.DecodeException;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.Validator;



/**
 * This class implements version 1 of the proxied authorization control
 * as defined in early versions of draft-weltman-ldapv3-proxy (this
 * implementation is based on the "-04" revision). It makes it possible
 * for one user to request that an operation be performed under the
 * authorization of another. The target user is specified as a DN in the
 * control value, which distinguishes it from later versions of the
 * control (which used a different OID) in which the target user was
 * specified using an authorization ID.
 */
public class ProxiedAuthV1Control extends Control
{
  /**
   * The OID for the proxied authorization v1 control.
   */
  public static final String OID_PROXIED_AUTH_V1 = "2.16.840.1.113730.3.4.12";



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class Decoder implements
      ControlDecoder<ProxiedAuthV1Control>
  {
    /**
     * {@inheritDoc}
     */
    public ProxiedAuthV1Control decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (!isCritical)
      {
        Message message = ERR_PROXYAUTH1_CONTROL_NOT_CRITICAL.get();
        throw DecodeException.error(message);
      }

      if (value == null)
      {
        Message message = ERR_PROXYAUTH1_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      String authorizationDN;
      try
      {
        reader.readStartSequence();
        authorizationDN = reader.readOctetStringAsString();
        reader.readEndSequence();
      }
      catch (IOException e)
      {
        StaticUtils.DEBUG_LOG.throwing("ProxiedAuthV1Control.Decoder",
            "decode", e);

        Message message = ERR_PROXYAUTH1_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }

      return new ProxiedAuthV1Control(authorizationDN);
    }



    public String getOID()
    {
      return OID_PROXIED_AUTH_V1;
    }

  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ProxiedAuthV1Control> DECODER = new Decoder();

  // The raw, unprocessed authorization DN from the control value.
  private String authorizationDN;



  /**
   * Creates a new instance of the proxied authorization v1 control with
   * the provided information.
   * 
   * @param authorizationDN
   *          The authorization DN from the control value. It must not
   *          be {@code null}.
   */
  public ProxiedAuthV1Control(DN authorizationDN)
  {
    super(OID_PROXIED_AUTH_V1, true);

    Validator.ensureNotNull(authorizationDN);
    this.authorizationDN = authorizationDN.toString();
  }



  /**
   * Creates a new instance of the proxied authorization v1 control with
   * the provided information.
   * 
   * @param authorizationDN
   *          The raw, unprocessed authorization DN from the control
   *          value. It must not be {@code null}.
   */
  public ProxiedAuthV1Control(String authorizationDN)
  {
    super(OID_PROXIED_AUTH_V1, true);

    Validator.ensureNotNull(authorizationDN);
    this.authorizationDN = authorizationDN;
  }



  /**
   * Retrieves the raw, unprocessed authorization DN from the control
   * value.
   * 
   * @return The raw, unprocessed authorization DN from the control
   *         value.
   */
  public String getAuthorizationDN()
  {
    return authorizationDN;
  }



  @Override
  public ByteString getValue()
  {
    return ByteString.valueOf(authorizationDN);
  }



  @Override
  public boolean hasValue()
  {
    return true;
  }



  public ProxiedAuthV1Control setAuthorizationDN(DN authorizationDN)
  {
    Validator.ensureNotNull(authorizationDN);
    this.authorizationDN = authorizationDN.toString();
    return this;
  }



  public ProxiedAuthV1Control setAuthorizationDN(String authorizationDN)
  {
    Validator.ensureNotNull(authorizationDN);
    this.authorizationDN = authorizationDN;
    return this;
  }



  /**
   * Appends a string representation of this proxied auth v1 control to
   * the provided buffer.
   * 
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ProxiedAuthorizationV1Control(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", authorizationDN=\"");
    buffer.append(authorizationDN);
    buffer.append("\")");
  }
}
