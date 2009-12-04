package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;

import org.opends.sdk.ByteString;
import org.opends.sdk.DN;
import org.opends.sdk.DecodeException;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.Validator;



/**
 * This class implements the authorization identity control as defined
 * in RFC 3829.
 */
public class AuthorizationIdentityControl
{
  /**
   * The OID for the authorization identity request control.
   */
  public static final String OID_AUTHZID_REQUEST = "2.16.840.1.113730.3.4.16";

  /**
   * The OID for the authorization identity response control.
   */
  public static final String OID_AUTHZID_RESPONSE = "2.16.840.1.113730.3.4.15";



  /**
   * This class implements the authorization identity request control as
   * defined in RFC 3829.
   */
  public static class Request extends Control
  {
    public Request()
    {
      super(OID_AUTHZID_RESPONSE, false);
    }



    public Request(boolean isCritical)
    {
      super(OID_AUTHZID_RESPONSE, isCritical);
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
      buffer.append("AuthorizationIdentityRequestControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(")");
    }
  }



  public static class Response extends Control
  {
    // The authorization ID for this control.
    private String authorizationID;



    /**
     * Creates a new authorization identity response control with the
     * provided information.
     * 
     * @param isCritical
     *          Indicates whether this control should be considered
     *          critical in processing the request.
     * @param authorizationDN
     *          The authorization DN for this control.
     */
    public Response(boolean isCritical, DN authorizationDN)
    {
      super(OID_AUTHZID_REQUEST, isCritical);

      Validator.ensureNotNull(authorizationDN);
      if (authorizationDN == null)
      {
        this.authorizationID = "dn:";
      }
      else
      {
        this.authorizationID = "dn:" + authorizationDN.toString();
      }
    }



    /**
     * Creates a new authorization identity response control with the
     * provided information.
     * 
     * @param isCritical
     *          Indicates whether this control should be considered
     *          critical in processing the request.
     * @param authorizationID
     *          The authorization ID for this control.
     */
    public Response(boolean isCritical, String authorizationID)
    {
      super(OID_AUTHZID_RESPONSE, isCritical);

      Validator.ensureNotNull(authorizationID);
      this.authorizationID = authorizationID;
    }



    /**
     * Creates a new authorization identity response control with the
     * provided information.
     * 
     * @param authorizationDN
     *          The authorization DN for this control.
     */
    public Response(DN authorizationDN)
    {
      this(false, authorizationDN);
    }



    /**
     * Creates a new authorization identity response control with the
     * provided information.
     * 
     * @param authorizationID
     *          The authorization ID for this control.
     */
    public Response(String authorizationID)
    {
      this(false, authorizationID);
    }



    /**
     * Retrieves the authorization ID for this authorization identity
     * response control.
     * 
     * @return The authorization ID for this authorization identity
     *         response control.
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



    /**
     * Appends a string representation of this authorization identity
     * response control to the provided buffer.
     * 
     * @param buffer
     *          The buffer to which the information should be appended.
     */
    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("AuthorizationIdentityResponseControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", authzID=\"");
      buffer.append(authorizationID);
      buffer.append("\")");
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
        Message message = ERR_AUTHZIDREQ_CONTROL_HAS_VALUE.get();
        throw DecodeException.error(message);
      }

      return new Request(isCritical);
    }



    public String getOID()
    {
      return OID_AUTHZID_REQUEST;
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
        Message message = ERR_AUTHZIDRESP_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      String authID = value.toString();
      return new Response(isCritical, authID);

    }



    public String getOID()
    {
      return OID_AUTHZID_RESPONSE;
    }
  }



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
