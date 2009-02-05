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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.controls;
import org.opends.messages.Message;

import org.opends.server.types.*;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import static org.opends.server.util.ServerConstants.OID_GET_EFFECTIVE_RIGHTS;
import org.opends.server.core.DirectoryServer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import java.util.List;
import java.util.LinkedList;
import java.io.IOException;

/**
 * This class partially implements the geteffectiverights control as defined
 * in draft-ietf-ldapext-acl-model-08.txt. The main differences are:
 *
 *  - The response control is not supported. Instead the dseecompat
 *    geteffectiverights control implementation creates attributes containing
 *    right information strings and adds those attributes to the
 *    entry being returned. The attribute type names are dynamically created;
 *    see the dseecompat's AciGetEffectiveRights class for details.
 *
 *  - The dseecompat implementation allows additional attribute types
 *    in the request control for which rights information can be returned.
 *    These are known as the specified attribute types.
 *
 * The dseecompat request control value is the following:
 *
 * <BR>
 * <PRE>
 *  GetRightsControl ::= SEQUENCE {
 *    authzId    authzId
 *    attributes  SEQUENCE OF AttributeType
 *  }
 *
 *   -- Only the "dn:DN form is supported.
 *
 * </PRE>
 *
 **/
public class GetEffectiveRightsRequestControl extends Control
{
  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private static final class Decoder
      implements ControlDecoder<GetEffectiveRightsRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public GetEffectiveRightsRequestControl decode(boolean isCritical,
        ByteString value) throws DirectoryException
    {
      // If the value is null create a GetEffectiveRightsRequestControl
      // class with null authzDN and attribute list, else try to
      // decode the value.
      if (value == null)
        return new GetEffectiveRightsRequestControl(isCritical, (DN)null,
            (List<AttributeType>)null);
      else
      {
        ASN1Reader reader = ASN1.getReader(value);
        DN authzDN;
        List<AttributeType> attrs=null;
        String authzIDString="";
        try {
          reader.readStartSequence();
          authzIDString = reader.readOctetStringAsString();
          String lowerAuthzIDString = authzIDString.toLowerCase();
          //Make sure authzId starts with "dn:" and is a valid DN.
          if (lowerAuthzIDString.startsWith("dn:"))
            authzDN = DN.decode(authzIDString.substring(3));
          else {
            Message message = INFO_GETEFFECTIVERIGHTS_INVALID_AUTHZID.get(
                lowerAuthzIDString);
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
          }
          //There is an sequence containing an attribute list, try to decode it.
          if(reader.hasNextElement()) {
            AttributeType attributeType;
            attrs = new LinkedList<AttributeType>();
            reader.readStartSequence();
            while(reader.hasNextElement()) {
              //Decode as an octet string.
              String attrStr = reader.readOctetStringAsString();
              //Get an attribute type for it and add to the list.
              if((attributeType =
                  DirectoryServer.getAttributeType(attrStr)) == null)
                attributeType =
                    DirectoryServer.getDefaultAttributeType(attrStr);
              attrs.add(attributeType);
            }
            reader.readEndSequence();
          }
          reader.readEndSequence();
        } catch (ASN1Exception e) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message =
              INFO_GETEFFECTIVERIGHTS_DECODE_ERROR.get(e.getMessage());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
        }

        return new GetEffectiveRightsRequestControl(isCritical,
            authzDN, attrs);
      }
    }

    public String getOID()
    {
      return OID_GET_EFFECTIVE_RIGHTS;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<GetEffectiveRightsRequestControl> DECODER =
    new Decoder();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  //The DN representing the authzId. May be null.
  private DN authzDN=null;

  //The raw DN representing the authzId. May be null.
  private String rawAuthzDN=null;

  //The list of additional attribute types to return rights for. May be null.
  private List<AttributeType> attrs=null;

  //The raw DN representing the authzId. May be null.
  private List<String> rawAttrs=null;

  /**
   * Create a new geteffectiverights control with the specified authzDN and
   * an attribute list.
   *
   * @param authzDN  The authzDN.
   *
   * @param attrs  The list of additional attributes to be returned.
   */
  public GetEffectiveRightsRequestControl(DN authzDN,
                            List<AttributeType> attrs) {
    this(true, authzDN, attrs);
  }

  /**
   * Create a new geteffectiverights control with the specified authzDN and
   * an attribute list.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param authzDN  The authzDN.
   * @param attrs  The list of additional attributes to be returned.
   */
  public GetEffectiveRightsRequestControl(boolean isCritical, DN authzDN,
                                          List<AttributeType> attrs) {
    super(OID_GET_EFFECTIVE_RIGHTS, isCritical);
    this.authzDN=authzDN;
    this.attrs=attrs;
  }

  /**
   * Create a new geteffectiverights control with the specified raw
   * authzDN and an attribute list.
   *
   * @param  isCritical  Indicates whether this control should be
   *                     considered critical in processing the
   *                     request.
   * @param authzDN  The authzDN.
   * @param attrs  The list of additional attributes to be returned.
   */
  public GetEffectiveRightsRequestControl(boolean isCritical,
                                          String authzDN,
                                          List<String> attrs)
  {
    super(OID_GET_EFFECTIVE_RIGHTS, isCritical);
    this.rawAuthzDN=authzDN;
    this.rawAttrs=attrs;
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must be
   * written as an ASN1OctetString.
   *
   * @param writer The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  @Override
  public void writeValue(ASN1Writer writer) throws IOException {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    if(authzDN != null)
    {
      writer.writeOctetString("dn:" + authzDN.toString());
    }
    else if(rawAuthzDN != null)
    {
      writer.writeOctetString("dn:" + rawAuthzDN);
    }

    if(attrs != null)
    {
      writer.writeStartSequence();
      for(AttributeType attr : attrs)
      {
        writer.writeOctetString(attr.getNameOrOID());
      }
      writer.writeEndSequence();
    }
    else if(rawAttrs != null)
    {
      writer.writeStartSequence();
      for(String attr : rawAttrs)
      {
        writer.writeOctetString(attr);
      }
      writer.writeEndSequence();
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }

  /**
   * Return the authzDN parsed from the control.
   *
   * @return The DN representing the authzId.
   */
  public DN getAuthzDN () {
    return authzDN;
    // TODO: what if rawAuthzDN is not null?
  }

  /**
   * Return the requested additional attributes parsed from the control. Known
   * as the specified attributes.
   *
   * @return  The list containing any additional attributes to return rights
   *          about.
   */
  public List<AttributeType> getAttributes() {
    return attrs;
  }
}
