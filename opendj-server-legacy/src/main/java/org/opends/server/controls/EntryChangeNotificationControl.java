/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.controls;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;

/**
 * This class implements the entry change notification control defined in
 * draft-ietf-ldapext-psearch.  It may be included in entries returned in
 * response to a persistent search operation.
 */
public class EntryChangeNotificationControl
       extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder
      implements ControlDecoder<EntryChangeNotificationControl>
  {
    @Override
    public EntryChangeNotificationControl decode(
        boolean isCritical, ByteString value) throws DirectoryException
    {
      if (value == null)
      {
        LocalizableMessage message = ERR_ECN_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }


      DN                         previousDN   = null;
      long                       changeNumber = -1;
      PersistentSearchChangeType changeType;
      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();

        int changeTypeValue = (int)reader.readInteger();
        changeType = PersistentSearchChangeType.valueOf(changeTypeValue);

        if(reader.hasNextElement() &&
            reader.peekType() == ASN1.UNIVERSAL_OCTET_STRING_TYPE)
        {
          if (changeType != PersistentSearchChangeType.MODIFY_DN)
          {
            LocalizableMessage message = ERR_ECN_ILLEGAL_PREVIOUS_DN.get(changeType);
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
          }

          previousDN = DN.valueOf(reader.readOctetStringAsString());
        }
        if(reader.hasNextElement() &&
            reader.peekType() == ASN1.UNIVERSAL_INTEGER_TYPE)
        {
          changeNumber = reader.readInteger();
        }
      }
      catch (DirectoryException de)
      {
        throw de;
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message =
            ERR_ECN_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }


      return new EntryChangeNotificationControl(isCritical, changeType,
          previousDN, changeNumber);
    }

    @Override
    public String getOID()
    {
      return OID_ENTRY_CHANGE_NOTIFICATION;
    }

  }

  /** The ControlDecoder that can be used to decode this control. */
  public static final ControlDecoder<EntryChangeNotificationControl> DECODER =
    new Decoder();
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();




  /** The previous DN for this change notification control. */
  private DN previousDN;

  /** The change number for this change notification control. */
  private long changeNumber;

  /** The change type for this change notification control. */
  private PersistentSearchChangeType changeType;


  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param  changeType    The change type for this change notification control.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   */
  public EntryChangeNotificationControl(boolean isCritical,
                                        PersistentSearchChangeType changeType,
                                        long changeNumber)
  {
    super(OID_ENTRY_CHANGE_NOTIFICATION, isCritical);


    this.changeType   = changeType;
    this.changeNumber = changeNumber;

    previousDN = null;
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param  changeType    The change type for this change notification control.
   * @param  previousDN    The DN that the entry had prior to a modify DN
   *                       operation, or <CODE>null</CODE> if the operation was
   *                       not a modify DN.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   */
  public EntryChangeNotificationControl(boolean isCritical,
                                        PersistentSearchChangeType changeType,
                                        DN previousDN, long changeNumber)
  {
    super(OID_ENTRY_CHANGE_NOTIFICATION, isCritical);


    this.changeType   = changeType;
    this.previousDN   = previousDN;
    this.changeNumber = changeNumber;
  }


  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  changeType    The change type for this change notification control.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   */
  public EntryChangeNotificationControl(PersistentSearchChangeType changeType,
                                        long changeNumber)
  {
    this(false, changeType, changeNumber);
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  changeType    The change type for this change notification control.
   * @param  previousDN    The DN that the entry had prior to a modify DN
   *                       operation, or <CODE>null</CODE> if the operation was
   *                       not a modify DN.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   */
  public EntryChangeNotificationControl(PersistentSearchChangeType changeType,
                                        DN previousDN, long changeNumber)
  {
    this(false, changeType, previousDN, changeNumber);
  }

  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(ASN1.UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    writer.writeEnumerated(changeType.intValue());

    if (previousDN != null)
    {
      writer.writeOctetString(previousDN.toString());
    }

    if (changeNumber > 0)
    {
      writer.writeInteger(changeNumber);
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }



  /**
   * Retrieves the change type for this entry change notification control.
   *
   * @return  The change type for this entry change notification control.
   */
  public PersistentSearchChangeType getChangeType()
  {
    return changeType;
  }


  /**
   * Retrieves the previous DN for this entry change notification control.
   *
   * @return  The previous DN for this entry change notification control, or
   *          <CODE>null</CODE> if there is none.
   */
  public DN getPreviousDN()
  {
    return previousDN;
  }



  /**
   * Retrieves the change number for this entry change notification control.
   *
   * @return  The change number for this entry change notification control, or a
   *          negative value if no change number is available.
   */
  public long getChangeNumber()
  {
    return changeNumber;
  }

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("EntryChangeNotificationControl(changeType=");
    buffer.append(changeType);

    if (previousDN != null)
    {
      buffer.append(",previousDN=\"").append(previousDN).append("\"");
    }

    if (changeNumber > 0)
    {
      buffer.append(",changeNumber=");
      buffer.append(changeNumber);
    }

    buffer.append(")");
  }
}
