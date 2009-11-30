package org.opends.sdk.extensions;



import org.opends.sdk.DecodeException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.requests.AbstractExtendedRequest;
import org.opends.sdk.util.ByteString;



/**
 * Created by IntelliJ IDEA. User: boli Date: Jun 22, 2009 Time: 6:40:06
 * PM To change this template use File | Settings | File Templates.
 */
public final class WhoAmIRequest extends
    AbstractExtendedRequest<WhoAmIRequest, WhoAmIResult>
{
  /**
   * The request OID for the "Who Am I?" extended operation.
   */
  static final String OID_WHO_AM_I_REQUEST = "1.3.6.1.4.1.4203.1.11.3";



  public WhoAmIRequest()
  {

  }



  /**
   * {@inheritDoc}
   */
  public String getRequestName()
  {
    return OID_WHO_AM_I_REQUEST;
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
    builder.append("WhoAmIExtendedRequest(requestName=");
    builder.append(getRequestName());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder;
  }



  private static final class Operation implements
      ExtendedOperation<WhoAmIRequest, WhoAmIResult>
  {

    public WhoAmIRequest decodeRequest(String requestName,
        ByteString requestValue) throws DecodeException
    {
      return new WhoAmIRequest();
    }



    public WhoAmIResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage,
        String responseName, ByteString responseValue)
        throws DecodeException
    {
      // TODO: Should we check oid is null?
      String authzId = null;
      if (responseValue != null)
      {
        authzId = responseValue.toString();
      }
      return new WhoAmIResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage).setAuthzId(authzId);
    }



    public WhoAmIResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage)
    {
      return new WhoAmIResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
    }
  }



  // Singleton instance.
  private static final Operation OPERATION = new Operation();
}
