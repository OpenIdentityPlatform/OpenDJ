package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;
import static org.opends.sdk.asn1.ASN1Constants.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DN;
import org.opends.sdk.DecodeException;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.Validator;



/**
 * This class implements the entry change notification control defined
 * in draft-ietf-ldapext-psearch. It may be included in entries returned
 * in response to a persistent search operation.
 */
public class EntryChangeNotificationControl extends Control
{
  /**
   * The OID for the entry change notification control.
   */
  public static final String OID_ENTRY_CHANGE_NOTIFICATION = "2.16.840.1.113730.3.4.7";



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class Decoder implements
      ControlDecoder<EntryChangeNotificationControl>
  {
    /**
     * {@inheritDoc}
     */
    public EntryChangeNotificationControl decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_ECN_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      String previousDN = null;
      long changeNumber = -1;
      PersistentSearchChangeType changeType;
      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        changeType = PersistentSearchChangeType.valueOf(reader
            .readEnumerated());

        if (reader.hasNextElement()
            && (reader.peekType() == UNIVERSAL_OCTET_STRING_TYPE))
        {
          if (changeType != PersistentSearchChangeType.MODIFY_DN)
          {
            Message message = ERR_ECN_ILLEGAL_PREVIOUS_DN.get(String
                .valueOf(changeType));
            throw DecodeException.error(message);
          }

          previousDN = reader.readOctetStringAsString();
        }
        if (reader.hasNextElement()
            && (reader.peekType() == UNIVERSAL_INTEGER_TYPE))
        {
          changeNumber = reader.readInteger();
        }
      }
      catch (IOException e)
      {
        StaticUtils.DEBUG_LOG.throwing(
            "EntryChangeNotificationControl.Decoder", "decode", e);

        Message message = ERR_ECN_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }

      return new EntryChangeNotificationControl(isCritical, changeType,
          previousDN, changeNumber);
    }



    public String getOID()
    {
      return OID_ENTRY_CHANGE_NOTIFICATION;
    }

  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<EntryChangeNotificationControl> DECODER = new Decoder();

  // The previous DN for this change notification control.
  private String previousDN;

  // The change number for this change notification control.
  private long changeNumber;

  // The change type for this change notification control.
  private final PersistentSearchChangeType changeType;



  /**
   * Creates a new entry change notification control with the provided
   * information.
   * 
   * @param isCritical
   *          Indicates whether this control should be considered
   *          critical in processing the request.
   * @param changeType
   *          The change type for this change notification control.
   */
  public EntryChangeNotificationControl(boolean isCritical,
      PersistentSearchChangeType changeType)
  {
    super(OID_ENTRY_CHANGE_NOTIFICATION, isCritical);

    Validator.ensureNotNull(changeType);
    this.changeType = changeType;

    previousDN = null;
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   * 
   * @param isCritical
   *          Indicates whether this control should be considered
   *          critical in processing the request.
   * @param changeType
   *          The change type for this change notification control.
   * @param previousDN
   *          The DN that the entry had prior to a modify DN operation,
   *          or <CODE>null</CODE> if the operation was not a modify DN.
   * @param changeNumber
   *          The change number for the associated change, or a negative
   *          value if no change number is available.
   */
  public EntryChangeNotificationControl(boolean isCritical,
      PersistentSearchChangeType changeType, DN previousDN,
      long changeNumber)
  {
    super(OID_ENTRY_CHANGE_NOTIFICATION, isCritical);

    Validator.ensureNotNull(changeType);
    this.changeType = changeType;

    if (previousDN != null)
    {
      this.previousDN = previousDN.toString();
    }
    this.changeNumber = changeNumber;
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   * 
   * @param isCritical
   *          Indicates whether this control should be considered
   *          critical in processing the request.
   * @param changeType
   *          The change type for this change notification control.
   * @param previousDN
   *          The DN that the entry had prior to a modify DN operation,
   *          or <CODE>null</CODE> if the operation was not a modify DN.
   * @param changeNumber
   *          The change number for the associated change, or a negative
   *          value if no change number is available.
   */
  public EntryChangeNotificationControl(boolean isCritical,
      PersistentSearchChangeType changeType, String previousDN,
      long changeNumber)
  {
    super(OID_ENTRY_CHANGE_NOTIFICATION, isCritical);

    Validator.ensureNotNull(changeType);
    this.changeType = changeType;
    this.previousDN = previousDN;
    this.changeNumber = changeNumber;
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   * 
   * @param changeType
   *          The change type for this change notification control.
   */
  public EntryChangeNotificationControl(
      PersistentSearchChangeType changeType)
  {
    this(false, changeType);
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   * 
   * @param changeType
   *          The change type for this change notification control.
   * @param previousDN
   *          The DN that the entry had prior to a modify DN operation,
   *          or <CODE>null</CODE> if the operation was not a modify DN.
   * @param changeNumber
   *          The change number for the associated change, or a negative
   *          value if no change number is available.
   */
  public EntryChangeNotificationControl(
      PersistentSearchChangeType changeType, DN previousDN,
      long changeNumber)
  {
    this(false, changeType, previousDN, changeNumber);
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   * 
   * @param changeType
   *          The change type for this change notification control.
   * @param previousDN
   *          The DN that the entry had prior to a modify DN operation,
   *          or <CODE>null</CODE> if the operation was not a modify DN.
   * @param changeNumber
   *          The change number for the associated change, or a negative
   *          value if no change number is available.
   */
  public EntryChangeNotificationControl(
      PersistentSearchChangeType changeType, String previousDN,
      long changeNumber)
  {
    this(false, changeType, previousDN, changeNumber);
  }



  /**
   * Retrieves the change number for this entry change notification
   * control.
   * 
   * @return The change number for this entry change notification
   *         control, or a negative value if no change number is
   *         available.
   */
  public long getChangeNumber()
  {
    return changeNumber;
  }



  /**
   * Retrieves the change type for this entry change notification
   * control.
   * 
   * @return The change type for this entry change notification control.
   */
  public PersistentSearchChangeType getChangeType()
  {
    return changeType;
  }



  /**
   * Retrieves the previous DN for this entry change notification
   * control.
   * 
   * @return The previous DN for this entry change notification control,
   *         or <CODE>null</CODE> if there is none.
   */
  public String getPreviousDN()
  {
    return previousDN;
  }



  @Override
  public ByteString getValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(buffer);
    try
    {
      writeValue(writer);
      return buffer.toByteString();
    }
    catch (IOException ioe)
    {
      // This should never happen unless there is a bug somewhere.
      throw new RuntimeException(ioe);
    }
  }



  @Override
  public boolean hasValue()
  {
    return true;
  }



  /**
   * Appends a string representation of this entry change notification
   * control to the provided buffer.
   * 
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("EntryChangeNotificationControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", changeType=");
    buffer.append(changeType.toString());
    buffer.append(", previousDN=\"");
    buffer.append(previousDN);
    buffer.append("\"");
    buffer.append(", changeNumber=");
    buffer.append(changeNumber);
    buffer.append(")");
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any)
   * must be written as an ASN1OctetString.
   * 
   * @param writer
   *          The ASN.1 output stream to write to.
   * @throws java.io.IOException
   *           If a problem occurs while writing to the stream.
   */
  public void writeValue(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence();
    writer.writeInteger(changeType.intValue());

    if (previousDN != null)
    {
      writer.writeOctetString(previousDN);
    }

    if (changeNumber > 0)
    {
      writer.writeInteger(changeNumber);
    }
    writer.writeEndSequence();
  }
}
