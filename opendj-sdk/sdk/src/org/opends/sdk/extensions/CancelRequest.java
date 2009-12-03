package org.opends.sdk.extensions;



import static com.sun.opends.sdk.util.Messages.ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE;
import static com.sun.opends.sdk.util.Messages.ERR_EXTOP_CANCEL_NO_REQUEST_VALUE;
import static org.opends.sdk.util.StaticUtils.getExceptionMessage;

import java.io.IOException;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.DecodeException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.requests.AbstractExtendedRequest;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.ByteStringBuilder;



/**
 * Created by IntelliJ IDEA. User: boli Date: Jun 22, 2009 Time: 4:44:51
 * PM To change this template use File | Settings | File Templates.
 */
public final class CancelRequest extends
    AbstractExtendedRequest<CancelRequest, Result>
{
  /**
   * The request OID for the cancel extended operation.
   */
  static final String OID_CANCEL_REQUEST = "1.3.6.1.1.8";

  private int cancelID;



  public CancelRequest(int cancelID)
  {
    this.cancelID = cancelID;
  }



  public int getCancelID()
  {
    return cancelID;
  }



  public Operation getExtendedOperation()
  {
    return OPERATION;
  }



  public ByteString getRequestValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder(6);
    ASN1Writer writer = ASN1.getWriter(buffer);

    try
    {
      writer.writeStartSequence();
      writer.writeInteger(cancelID);
      writer.writeEndSequence();
    }
    catch (IOException ioe)
    {
      // This should never happen unless there is a bug somewhere.
      throw new RuntimeException(ioe);
    }

    return buffer.toByteString();
  }



  public CancelRequest setCancelID(int cancelID)
  {
    this.cancelID = cancelID;
    return this;
  }



  public StringBuilder toString(StringBuilder builder)
  {
    builder.append("CancelExtendedRequest(requestName=");
    builder.append(getRequestName());
    builder.append(", cancelID=");
    builder.append(cancelID);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder;
  }



  private static final class Operation implements
      ExtendedOperation<CancelRequest, Result>
  {

    public CancelRequest decodeRequest(String requestName,
        ByteString requestValue) throws DecodeException
    {
      if ((requestValue == null) || (requestValue.length() <= 0))
      {
        throw DecodeException
            .error(ERR_EXTOP_CANCEL_NO_REQUEST_VALUE.get());
      }

      try
      {
        ASN1Reader reader = ASN1.getReader(requestValue);
        reader.readStartSequence();
        int idToCancel = (int) reader.readInteger();
        reader.readEndSequence();
        return new CancelRequest(idToCancel);
      }
      catch (IOException e)
      {
        Message message = ERR_EXTOP_CANCEL_CANNOT_DECODE_REQUEST_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }
    }



    public Result decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage)
    {
      return Responses.newResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
    }



    public Result decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage,
        String responseName, ByteString responseValue)
        throws DecodeException
    {
      // TODO: Should we check to make sure OID and value is null?
      return Responses.newResult(resultCode).setMatchedDN(matchedDN)
          .setDiagnosticMessage(diagnosticMessage);
    }
  }



  // Singleton instance.
  private static final Operation OPERATION = new Operation();



  /**
   * {@inheritDoc}
   */
  public String getRequestName()
  {
    return OID_CANCEL_REQUEST;
  }
}
