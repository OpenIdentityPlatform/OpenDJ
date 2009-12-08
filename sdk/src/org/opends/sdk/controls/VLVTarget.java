package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;

import com.sun.opends.sdk.util.Validator;



/**
 * Created by IntelliJ IDEA. User: boli Date: Jun 30, 2009 Time: 5:40:28
 * PM To change this template use File | Settings | File Templates.
 */
public abstract class VLVTarget
{
  public static final class ByOffset extends VLVTarget
  {
    private final int offset;

    private final int contentCount;



    private ByOffset(int offset, int contentCount)
    {
      this.offset = offset;
      this.contentCount = contentCount;
    }



    public int getContentCount()
    {
      return contentCount;
    }



    public int getOffset()
    {
      return offset;
    }



    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("byOffset(offset=");
      buffer.append(offset);
      buffer.append(", contentCount=");
      buffer.append(contentCount);
      buffer.append(")");
    }



    @Override
    void encode(ASN1Writer writer) throws IOException
    {
      writer.writeStartSequence(TYPE_TARGET_BYOFFSET);
      writer.writeInteger(offset);
      writer.writeInteger(contentCount);
      writer.writeEndSequence();
    }
  }



  public static final class GreaterThanOrEqual extends VLVTarget
  {
    private final ByteString assertionValue;



    private GreaterThanOrEqual(ByteString assertionValue)
    {
      this.assertionValue = assertionValue;
    }



    public ByteString getAssertionValue()
    {
      return assertionValue;
    }



    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("greaterThanOrEqual(assertionValue=");
      buffer.append(assertionValue);
      buffer.append(")");
    }



    @Override
    void encode(ASN1Writer writer) throws IOException
    {
      writer.writeOctetString(TYPE_TARGET_GREATERTHANOREQUAL,
          assertionValue);
    }
  }



  /**
   * The BER type to use when encoding the byOffset target element.
   */
  private static final byte TYPE_TARGET_BYOFFSET = (byte) 0xA0;

  /**
   * The BER type to use when encoding the greaterThanOrEqual target
   * element.
   */
  private static final byte TYPE_TARGET_GREATERTHANOREQUAL = (byte) 0x81;



  static VLVTarget byOffset(int offset, int contentCount)
  {
    return new ByOffset(offset, contentCount);
  }



  static VLVTarget decode(ASN1Reader reader) throws IOException,
      DecodeException
  {
    byte targetType = reader.peekType();
    switch (targetType)
    {
    case TYPE_TARGET_BYOFFSET:
      reader.readStartSequence();
      int offset = (int) reader.readInteger();
      int contentCount = (int) reader.readInteger();
      reader.readEndSequence();
      return new ByOffset(offset, contentCount);

    case TYPE_TARGET_GREATERTHANOREQUAL:
      ByteString assertionValue = reader.readOctetString();
      return new GreaterThanOrEqual(assertionValue);

    default:
      LocalizableMessage message = INFO_VLVREQ_CONTROL_INVALID_TARGET_TYPE
          .get(byteToHex(targetType));
      throw DecodeException.error(message);
    }
  }



  static VLVTarget greaterThanOrEqual(ByteString assertionValue)
  {
    Validator.ensureNotNull(assertionValue);
    return new GreaterThanOrEqual(assertionValue);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  public abstract void toString(StringBuilder buffer);



  abstract void encode(ASN1Writer writer) throws IOException;
}
