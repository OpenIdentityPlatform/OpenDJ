package org.opends.sdk.extensions;



import static com.sun.opends.sdk.messages.Messages.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.requests.AbstractExtendedRequest;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * Created by IntelliJ IDEA. User: boli Date: Jun 23, 2009 Time:
 * 12:10:59 PM To change this template use File | Settings | File
 * Templates.
 */
public final class GetSymmetricKeyRequest extends
    AbstractExtendedRequest<GetSymmetricKeyRequest, Result>
{
  /**
   * The request OID for the get symmetric key extended operation.
   */
  static final String OID_GET_SYMMETRIC_KEY_EXTENDED_OP = "1.3.6.1.4.1.26027.1.6.3";

  private String requestSymmetricKey = null;

  private String instanceKeyID = null;



  public GetSymmetricKeyRequest()
  {
  }



  /**
   * {@inheritDoc}
   */
  public String getRequestName()
  {
    return OID_GET_SYMMETRIC_KEY_EXTENDED_OP;
  }



  public Operation getExtendedOperation()
  {
    return OPERATION;
  }



  public String getInstanceKeyID()
  {
    return instanceKeyID;
  }



  public String getRequestSymmetricKey()
  {
    return requestSymmetricKey;
  }



  public ByteString getRequestValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(buffer);

    try
    {
      writer.writeStartSequence();
      if (requestSymmetricKey != null)
      {
        writer.writeOctetString(TYPE_SYMMETRIC_KEY_ELEMENT,
            requestSymmetricKey);
      }
      if (instanceKeyID != null)
      {
        writer.writeOctetString(TYPE_INSTANCE_KEY_ID_ELEMENT,
            instanceKeyID);
      }
      writer.writeEndSequence();
    }
    catch (IOException ioe)
    {
      // This should never happen unless there is a bug somewhere.
      throw new RuntimeException(ioe);
    }

    return buffer.toByteString();
  }



  public GetSymmetricKeyRequest setInstanceKeyID(String instanceKeyID)
  {
    this.instanceKeyID = instanceKeyID;
    return this;
  }



  public GetSymmetricKeyRequest setRequestSymmetricKey(
      String requestSymmetricKey)
  {
    this.requestSymmetricKey = requestSymmetricKey;
    return this;
  }



  public StringBuilder toString(StringBuilder builder)
  {
    builder.append("GetSymmetricKeyExtendedRequest(requestName=");
    builder.append(getRequestName());
    builder.append(", requestSymmetricKey=");
    builder.append(requestSymmetricKey);
    builder.append(", instanceKeyID=");
    builder.append(instanceKeyID);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder;
  }



  /**
   * The BER type value for the symmetric key element of the operation
   * value.
   */
  private static final byte TYPE_SYMMETRIC_KEY_ELEMENT = (byte) 0x80;

  /**
   * The BER type value for the instance key ID element of the operation
   * value.
   */
  private static final byte TYPE_INSTANCE_KEY_ID_ELEMENT = (byte) 0x81;



  private static final class Operation implements
      ExtendedOperation<GetSymmetricKeyRequest, Result>
  {

    public GetSymmetricKeyRequest decodeRequest(String requestName,
        ByteString requestValue) throws DecodeException
    {
      if (requestValue == null)
      {
        // The request must always have a value.
        Message message = ERR_GET_SYMMETRIC_KEY_NO_VALUE.get();
        throw DecodeException.error(message);
      }

      String requestSymmetricKey = null;
      String instanceKeyID = null;

      try
      {
        ASN1Reader reader = ASN1.getReader(requestValue);
        reader.readStartSequence();
        if (reader.hasNextElement()
            && (reader.peekType() == TYPE_SYMMETRIC_KEY_ELEMENT))
        {
          requestSymmetricKey = reader.readOctetStringAsString();
        }
        if (reader.hasNextElement()
            && (reader.peekType() == TYPE_INSTANCE_KEY_ID_ELEMENT))
        {
          instanceKeyID = reader.readOctetStringAsString();
        }
        reader.readEndSequence();
        return new GetSymmetricKeyRequest().setRequestSymmetricKey(
            requestSymmetricKey).setInstanceKeyID(instanceKeyID);
      }
      catch (IOException ae)
      {
        StaticUtils.DEBUG_LOG.throwing(
            "GetSymmetricKeyRequest.Operation", "decodeRequest", ae);

        Message message = ERR_GET_SYMMETRIC_KEY_ASN1_DECODE_EXCEPTION
            .get(ae.getMessage());
        throw DecodeException.error(message, ae);
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
}
