package org.opends.sdk.extensions;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.requests.AbstractExtendedRequest;

import com.sun.opends.sdk.util.Message;



/**
 * This class implements the password modify extended operation response
 * defined in RFC 3062. It includes support for requiring the user's
 * current password as well as for generating a new password if none was
 * provided.
 */
public final class PasswordModifyRequest extends
    AbstractExtendedRequest<PasswordModifyRequest, PasswordModifyResult>
{
  /**
   * The request OID for the password modify extended operation.
   */
  public static final String OID_PASSWORD_MODIFY_REQUEST = "1.3.6.1.4.1.4203.1.11.1";

  /**
   * The ASN.1 element type that will be used to encode the userIdentity
   * component in a password modify extended request.
   */
  static final byte TYPE_PASSWORD_MODIFY_USER_ID = (byte) 0x80;

  /**
   * The ASN.1 element type that will be used to encode the oldPasswd
   * component in a password modify extended request.
   */
  static final byte TYPE_PASSWORD_MODIFY_OLD_PASSWORD = (byte) 0x81;

  /**
   * The ASN.1 element type that will be used to encode the newPasswd
   * component in a password modify extended request.
   */
  static final byte TYPE_PASSWORD_MODIFY_NEW_PASSWORD = (byte) 0x82;

  /**
   * The ASN.1 element type that will be used to encode the genPasswd
   * component in a password modify extended response.
   */
  static final byte TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD = (byte) 0x80;

  private String userIdentity;

  private ByteString oldPassword;

  private ByteString newPassword;



  public PasswordModifyRequest()
  {

  }



  /**
   * {@inheritDoc}
   */
  public String getRequestName()
  {
    return OID_PASSWORD_MODIFY_REQUEST;
  }


  public ExtendedOperation<PasswordModifyRequest, PasswordModifyResult>
  getExtendedOperation()
  {
    return OPERATION;
  }

  public ByteString getNewPassword()
  {
    return newPassword;
  }



  public ByteString getOldPassword()
  {
    return oldPassword;
  }



  public ByteString getRequestValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(buffer);

    try
    {
      writer.writeStartSequence();
      if (userIdentity != null)
      {
        writer.writeOctetString(TYPE_PASSWORD_MODIFY_USER_ID,
            userIdentity);
      }
      if (oldPassword != null)
      {
        writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
            oldPassword);
      }
      if (newPassword != null)
      {
        writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
            newPassword);
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



  public String getUserIdentity()
  {
    return userIdentity;
  }



  public PasswordModifyRequest setNewPassword(ByteString newPassword)
  {
    this.newPassword = newPassword;
    return this;
  }



  public PasswordModifyRequest setOldPassword(ByteString oldPassword)
  {
    this.oldPassword = oldPassword;
    return this;
  }



  public PasswordModifyRequest setUserIdentity(String userIdentity)
  {
    this.userIdentity = userIdentity;
    return this;
  }



  public StringBuilder toString(StringBuilder builder)
  {
    builder.append("PasswordModifyExtendedRequest(requestName=");
    builder.append(getRequestName());
    builder.append(", userIdentity=");
    builder.append(userIdentity);
    builder.append(", oldPassword=");
    builder.append(oldPassword);
    builder.append(", newPassword=");
    builder.append(newPassword);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder;
  }



  private static final class Operation implements
      ExtendedOperation<PasswordModifyRequest, PasswordModifyResult>
  {

    public PasswordModifyRequest decodeRequest(String requestName,
        ByteString requestValue) throws DecodeException
    {
      PasswordModifyRequest request = new PasswordModifyRequest();
      if (requestValue != null)
      {
        try
        {
          ASN1Reader reader = ASN1.getReader(requestValue);
          reader.readStartSequence();
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_PASSWORD_MODIFY_USER_ID))
          {
            request.setUserIdentity(reader.readOctetStringAsString());
          }
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_PASSWORD_MODIFY_OLD_PASSWORD))
          {
            request.setOldPassword(reader.readOctetString());
          }
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_PASSWORD_MODIFY_NEW_PASSWORD))
          {
            request.setNewPassword(reader.readOctetString());
          }
          reader.readEndSequence();
        }
        catch (IOException e)
        {
          Message message = ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST
              .get(getExceptionMessage(e));
          throw DecodeException.error(message, e);
        }
      }
      return request;
    }



    public PasswordModifyResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage,
        String responseName, ByteString responseValue)
        throws DecodeException
    {
      // TODO: Should we check to make sure OID is null?
      PasswordModifyResult result = new PasswordModifyResult(resultCode)
          .setMatchedDN(matchedDN).setDiagnosticMessage(
              diagnosticMessage);
      if (resultCode == ResultCode.SUCCESS && responseValue != null)
      {
        try
        {
          ASN1Reader asn1Reader = ASN1.getReader(responseValue);
          asn1Reader.readStartSequence();
          if (asn1Reader.peekType() == TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD)
          {
            result.setGenPassword(asn1Reader.readOctetString());
          }
          asn1Reader.readEndSequence();
        }
        catch (IOException e)
        {
          Message message = ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST
              .get(getExceptionMessage(e));
          throw DecodeException.error(message, e);
        }
      }
      return result;
    }



    public PasswordModifyResult decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage)
    {
      return new PasswordModifyResult(resultCode).setMatchedDN(
          matchedDN).setDiagnosticMessage(diagnosticMessage);
    }
  }



  // Singleton instance.
  private static final Operation OPERATION = new Operation();
}
