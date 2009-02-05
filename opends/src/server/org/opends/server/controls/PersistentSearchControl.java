/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.controls;
import org.opends.messages.Message;


import java.util.Set;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the persistent search control defined in
 * draft-ietf-ldapext-psearch.  It makes it possible for clients to be notified
 * of changes to information in the Directory Server as they occur.
 */
public class PersistentSearchControl
       extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<PersistentSearchControl>
  {
    /**
     * {@inheritDoc}
     */
    public PersistentSearchControl decode(boolean isCritical, ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = ERR_PSEARCH_NO_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      boolean                         changesOnly;
      boolean                         returnECs;
      Set<PersistentSearchChangeType> changeTypes;
      try
      {
        reader.readStartSequence();

        int changeTypesValue = (int)reader.readInteger();
        changeTypes = PersistentSearchChangeType.intToTypes(changeTypesValue);
        changesOnly = reader.readBoolean();
        returnECs   = reader.readBoolean();

        reader.readEndSequence();
      }
      catch (LDAPException le)
      {
        throw new DirectoryException(ResultCode.valueOf(le.getResultCode()), le
            .getMessageObject());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message =
            ERR_PSEARCH_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }


      return new PersistentSearchControl(isCritical,
          changeTypes, changesOnly, returnECs);
    }

    public String getOID()
    {
      return OID_PERSISTENT_SEARCH;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<PersistentSearchControl> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // Indicates whether to only return entries that have been updated since the
  // beginning of the search.
  private boolean changesOnly;

  // Indicates whether entries returned as a result of changes to directory data
  // should include the entry change notification control.
  private boolean returnECs;

  // The set of change types associated with this control.
  private Set<PersistentSearchChangeType> changeTypes;



  /**
   * Creates a new persistent search control with the provided information.
   *
   * @param  changeTypes  The set of change types for which to provide
   *                      notification to the client.
   * @param  changesOnly  Indicates whether to only return changes that match
   *                      the associated search criteria, or to also return all
   *                      existing entries that match the filter.
   * @param  returnECs    Indicates whether to include the entry change
   *                      notification control in updated entries that match the
   *                      associated search criteria.
   */
  public PersistentSearchControl(Set<PersistentSearchChangeType> changeTypes,
                                 boolean changesOnly, boolean returnECs)
  {
    this(true, changeTypes, changesOnly, returnECs);
  }



  /**
   * Creates a new persistent search control with the provided information.
   *
   * @param  isCritical   Indicates whether the control should be considered
   *                      critical for the operation processing.
   * @param  changeTypes  The set of change types for which to provide
   *                      notification to the client.
   * @param  changesOnly  Indicates whether to only return changes that match
   *                      the associated search criteria, or to also return all
   *                      existing entries that match the filter.
   * @param  returnECs    Indicates whether to include the entry change
   *                      notification control in updated entries that match the
   *                      associated search criteria.
   */
  public PersistentSearchControl(boolean isCritical,
                                 Set<PersistentSearchChangeType> changeTypes,
                                 boolean changesOnly, boolean returnECs)
  {
    super(OID_PERSISTENT_SEARCH, isCritical);


    this.changeTypes = changeTypes;
    this.changesOnly = changesOnly;
    this.returnECs   = returnECs;
  }



  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    writer.writeInteger(
        PersistentSearchChangeType.changeTypesToInt(changeTypes));
    writer.writeBoolean(changesOnly);
    writer.writeBoolean(returnECs);
    writer.writeEndSequence();

    writer.writeEndSequence();
  }



  /**
   * Retrieves the set of change types for this persistent search control.
   *
   * @return  The set of change types for this persistent search control.
   */
  public Set<PersistentSearchChangeType> getChangeTypes()
  {
    return changeTypes;
  }



  /**
   * Indicates whether to only return changes that match the associated search
   * criteria, or to also return all existing entries that match the filter.
   *
   * @return  <CODE>true</CODE> if only changes to matching entries should be
   *          returned, or <CODE>false</CODE> if existing matches should also be
   *          included.
   */
  public boolean getChangesOnly()
  {
    return changesOnly;
  }



  /**
   * Indicates whether to include the entry change notification control in
   * entries returned to the client as the result of a change in the Directory
   * Server data.
   *
   * @return  <CODE>true</CODE> if entry change notification controls should be
   *          included in applicable entries, or <CODE>false</CODE> if not.
   */
  public boolean getReturnECs()
  {
    return returnECs;
  }



  /**
   * Appends a string representation of this persistent search control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("PersistentSearchControl(changeTypes=\"");
    PersistentSearchChangeType.changeTypesToString(changeTypes, buffer);
    buffer.append("\",changesOnly=");
    buffer.append(changesOnly);
    buffer.append(",returnECs=");
    buffer.append(returnECs);
    buffer.append(")");
  }
}

