package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.StaticUtils;



/**
 * This class implements the persistent search control defined in
 * draft-ietf-ldapext-psearch. It makes it possible for clients to be
 * notified of changes to information in the Directory Server as they
 * occur.
 */
public class PersistentSearchControl extends Control
{
  /**
   * The OID for the persistent search control.
   */
  public static final String OID_PERSISTENT_SEARCH = "2.16.840.1.113730.3.4.3";



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class Decoder implements
      ControlDecoder<PersistentSearchControl>
  {
    /**
     * {@inheritDoc}
     */
    public PersistentSearchControl decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_PSEARCH_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      boolean changesOnly;
      boolean returnECs;
      int changeTypes;
      try
      {
        reader.readStartSequence();

        changeTypes = (int) reader.readInteger();
        changesOnly = reader.readBoolean();
        returnECs = reader.readBoolean();

        reader.readEndSequence();
      }
      catch (IOException e)
      {
        StaticUtils.DEBUG_LOG.throwing(
            "PersistentSearchControl.Decoder", "decode", e);

        LocalizableMessage message = ERR_PSEARCH_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }

      return new PersistentSearchControl(isCritical, changeTypes,
          changesOnly, returnECs);
    }



    public String getOID()
    {
      return OID_PERSISTENT_SEARCH;
    }

  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<PersistentSearchControl> DECODER = new Decoder();

  // Indicates whether to only return entries that have been updated
  // since the
  // beginning of the search.
  private final boolean changesOnly;

  // Indicates whether entries returned as a result of changes to
  // directory data
  // should include the entry change notification control.
  private final boolean returnECs;

  // The logical OR of change types associated with this control.
  private int changeTypes;



  /**
   * Creates a new persistent search control with the provided
   * information.
   * 
   * @param isCritical
   *          Indicates whether the control should be considered
   *          critical for the operation processing.
   * @param changesOnly
   *          Indicates whether to only return changes that match the
   *          associated search criteria, or to also return all existing
   *          entries that match the filter.
   * @param returnECs
   *          Indicates whether to include the entry change notification
   *          control in updated entries that match the associated
   *          search criteria.
   * @param changeTypes
   *          The change types for which to provide notification to the
   *          client.
   */
  public PersistentSearchControl(boolean isCritical,
      boolean changesOnly, boolean returnECs,
      PersistentSearchChangeType... changeTypes)
  {
    super(OID_PERSISTENT_SEARCH, isCritical);

    this.changeTypes = 0;
    this.changesOnly = changesOnly;
    this.returnECs = returnECs;

    if (changeTypes != null)
    {
      for (PersistentSearchChangeType type : changeTypes)
      {
        this.changeTypes |= type.intValue();
      }
    }
  }



  /**
   * Creates a new persistent search control with the provided
   * information.
   * 
   * @param changesOnly
   *          Indicates whether to only return changes that match the
   *          associated search criteria, or to also return all existing
   *          entries that match the filter.
   * @param returnECs
   *          Indicates whether to include the entry change notification
   *          control in updated entries that match the associated
   *          search criteria.
   * @param changeTypes
   *          The set of change types for which to provide notification
   *          to the client.
   */
  public PersistentSearchControl(boolean changesOnly,
      boolean returnECs, PersistentSearchChangeType... changeTypes)
  {
    this(true, changesOnly, returnECs, changeTypes);
  }



  private PersistentSearchControl(boolean isCritical, int changeTypes,
      boolean changesOnly, boolean returnECs)
  {
    super(OID_PERSISTENT_SEARCH, isCritical);

    this.changeTypes = changeTypes;
    this.changesOnly = changesOnly;
    this.returnECs = returnECs;
  }



  public PersistentSearchControl addChangeType(
      PersistentSearchChangeType type)
  {
    changeTypes |= type.intValue();
    return this;
  }



  /**
   * Indicates if the change type is included in this persistent search
   * control.
   * 
   * @param type
   *          The change type whose presence is to be tested.
   * @return <code>true</code> if the change type is included or
   *         <code>false</code> otherwise.
   */
  public boolean containsChangeType(PersistentSearchChangeType type)
  {
    return (changeTypes & type.intValue()) == type.intValue();
  }



  /**
   * Indicates whether to only return changes that match the associated
   * search criteria, or to also return all existing entries that match
   * the filter.
   * 
   * @return <CODE>true</CODE> if only changes to matching entries
   *         should be returned, or <CODE>false</CODE> if existing
   *         matches should also be included.
   */
  public boolean getChangesOnly()
  {
    return changesOnly;
  }



  /**
   * Indicates whether to include the entry change notification control
   * in entries returned to the client as the result of a change in the
   * Directory Server data.
   * 
   * @return <CODE>true</CODE> if entry change notification controls
   *         should be included in applicable entries, or
   *         <CODE>false</CODE> if not.
   */
  public boolean getReturnECs()
  {
    return returnECs;
  }



  @Override
  public ByteString getValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(buffer);
    try
    {
      writer.writeStartSequence();
      writer.writeInteger(changeTypes);
      writer.writeBoolean(changesOnly);
      writer.writeBoolean(returnECs);
      writer.writeEndSequence();
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



  public PersistentSearchControl removeChangeType(
      PersistentSearchChangeType type)
  {
    changeTypes &= ~type.intValue();
    return this;
  }



  /**
   * Appends a string representation of this persistent search control
   * to the provided buffer.
   * 
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PersistentSearchControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", changeTypes=[");

    boolean comma = false;
    for (PersistentSearchChangeType type : PersistentSearchChangeType
        .values())
    {
      if (containsChangeType(type))
      {
        if (comma)
        {
          buffer.append(", ");
        }
        buffer.append(type);
        comma = true;
      }
    }

    buffer.append("](");
    buffer.append(changeTypes);
    buffer.append("), changesOnly=");
    buffer.append(changesOnly);
    buffer.append(", returnECs=");
    buffer.append(returnECs);
    buffer.append(")");
  }
}
