package org.opends.sdk.extensions;



import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.requests.ExtendedRequest;
import org.opends.sdk.responses.Result;



/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Jun 19, 2009 Time:
 * 8:39:52 PM To change this template use File | Settings | File
 * Templates.
 */
public interface ExtendedOperation<R extends ExtendedRequest<S>, S extends Result>
{
  R decodeRequest(String requestName, ByteString requestValue)
      throws DecodeException;



  S decodeResponse(ResultCode resultCode, String matchedDN,
      String diagnosticMessage);



  S decodeResponse(ResultCode resultCode, String matchedDN,
      String diagnosticMessage, String responseName,
      ByteString responseValue) throws DecodeException;

}
