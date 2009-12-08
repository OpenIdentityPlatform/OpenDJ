package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.DN;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.Validator;



/**
 * This class implements version 2 of the proxied authorization control
 * as defined in RFC 4370. It makes it possible for one user to request
 * that an operation be performed under the authorization of another.
 * The target user is specified using an authorization ID, which may be
 * in the form "dn:" immediately followed by the DN of that user, or
 * "u:" followed by a user ID string.
 */
public class ProxiedAuthV2Control extends Control
{
  /**
   * The OID for the proxied authorization v2 control.
   */
  public static final String OID_PROXIED_AUTH_V2 = "2.16.840.1.113730.3.4.18";



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private static final class Decoder implements
      ControlDecoder<ProxiedAuthV2Control>
  {
    /**
     * {@inheritDoc}
     */
    public ProxiedAuthV2Control decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (!isCritical)
      {
        LocalizableMessage message = ERR_PROXYAUTH2_CONTROL_NOT_CRITICAL.get();
        throw DecodeException.error(message);
      }

      if (value == null)
      {
        LocalizableMessage message = ERR_PROXYAUTH2_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      String authorizationID;

      try
      {
        if (reader.elementAvailable())
        {
          // Try the legacy encoding where the value is wrapped by an
          // extra octet string
          authorizationID = reader.readOctetStringAsString();
        }
        else
        {
          authorizationID = value.toString();
        }
      }
      catch (IOException e)
      {
        StaticUtils.DEBUG_LOG.throwing("ProxiedAuthV2Control.Decoder",
            "decode", e);

        LocalizableMessage message = ERR_PROXYAUTH2_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }

      return new ProxiedAuthV2Control(authorizationID);
    }



    public String getOID()
    {
      return OID_PROXIED_AUTH_V2;
    }

  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ProxiedAuthV2Control> DECODER = new Decoder();

  // The authorization ID from the control value.
  private String authorizationID;



  /**
   * Creates a new instance of the proxied authorization v2 control with
   * the provided information.
   * 
   * @param authorizationDN
   *          The authorization DN.
   */
  public ProxiedAuthV2Control(DN authorizationDN)
  {
    super(OID_PROXIED_AUTH_V2, true);

    Validator.ensureNotNull(authorizationID);
    this.authorizationID = "dn:" + authorizationDN.toString();
  }



  /**
   * Creates a new instance of the proxied authorization v2 control with
   * the provided information.
   * 
   * @param authorizationID
   *          The authorization ID.
   */
  public ProxiedAuthV2Control(String authorizationID)
  {
    super(OID_PROXIED_AUTH_V2, true);

    Validator.ensureNotNull(authorizationID);
    this.authorizationID = authorizationID;
  }



  /**
   * Retrieves the authorization ID for this proxied authorization V2
   * control.
   * 
   * @return The authorization ID for this proxied authorization V2
   *         control.
   */
  public String getAuthorizationID()
  {
    return authorizationID;
  }



  @Override
  public ByteString getValue()
  {
    return ByteString.valueOf(authorizationID);
  }



  @Override
  public boolean hasValue()
  {
    return true;
  }



  public ProxiedAuthV2Control setAuthorizationID(DN authorizationDN)
  {
    Validator.ensureNotNull(authorizationDN);
    this.authorizationID = "dn:" + authorizationDN.toString();
    return this;
  }



  public ProxiedAuthV2Control setAuthorizationID(String authorizationID)
  {
    Validator.ensureNotNull(authorizationID);
    this.authorizationID = authorizationID;
    return this;
  }



  /**
   * Appends a string representation of this proxied auth v2 control to
   * the provided buffer.
   * 
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ProxiedAuthorizationV2Control(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", authorizationDN=\"");
    buffer.append(authorizationID);
    buffer.append("\")");
  }
}
