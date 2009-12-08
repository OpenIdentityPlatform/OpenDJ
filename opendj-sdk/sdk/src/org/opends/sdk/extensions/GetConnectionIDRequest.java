package org.opends.sdk.extensions;



import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.ResultCode;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.requests.AbstractExtendedRequest;




/**
 * Created by IntelliJ IDEA. User: boli Date: Jun 23, 2009 Time:
 * 11:43:53 AM To change this template use File | Settings | File
 * Templates.
 */
public final class GetConnectionIDRequest
    extends
    AbstractExtendedRequest<GetConnectionIDRequest, GetConnectionIDResult>
{
  /**
   * The OID for the extended operation that can be used to get the
   * client connection ID. It will be both the request and response OID.
   */
  static final String OID_GET_CONNECTION_ID_EXTOP = "1.3.6.1.4.1.26027.1.6.2";



  public GetConnectionIDRequest()
  {
  }



  public Operation getExtendedOperation()
  {
    return OPERATION;
  }



  public ByteString getRequestValue()
  {
    return null;
  }



  public StringBuilder toString(StringBuilder builder)
  {
    builder.append("GetConnectionIDExtendedRequest(requestName=");
    builder.append(getRequestName());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder;
  }



  private static final class Operation implements
      ExtendedOperation<GetConnectionIDRequest, GetConnectionIDResult>
  {

    public GetConnectionIDRequest decodeRequest(String requestName,
        ByteString requestValue) throws DecodeException
    {
      return new GetConnectionIDRequest();
    }



    public GetConnectionIDResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage,
        String responseName, ByteString responseValue)
        throws DecodeException
    {
      if (!resultCode.isExceptional()
          && ((responseValue == null) || (responseValue.length() <= 0)))
      {
        throw DecodeException.error(LocalizableMessage
            .raw("Empty response value"));
      }

      try
      {
        ASN1Reader reader = ASN1.getReader(responseValue);
        int connectionID = (int) reader.readInteger();
        return new GetConnectionIDResult(resultCode, connectionID)
            .setMatchedDN(matchedDN).setDiagnosticMessage(
                diagnosticMessage);
      }
      catch (IOException e)
      {
        throw DecodeException.error(LocalizableMessage
            .raw("Error decoding response value"), e);
      }
    }



    /**
     * {@inheritDoc}
     */
    public GetConnectionIDResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage)
    {
      if (!resultCode.isExceptional())
      {
        // A successful response must contain a response name and
        // value.
        throw new IllegalArgumentException(
            "No response name and value for result code "
                + resultCode.intValue());
      }
      return new GetConnectionIDResult(resultCode, -1).setMatchedDN(
          matchedDN).setDiagnosticMessage(diagnosticMessage);
    }
  }



  // Singleton instance.
  private static final Operation OPERATION = new Operation();



  /**
   * {@inheritDoc}
   */
  public String getRequestName()
  {
    return OID_GET_CONNECTION_ID_EXTOP;
  }
}
