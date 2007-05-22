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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.controls;

import org.opends.server.types.*;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.LDAPResultCode;
import static org.opends.server.util.ServerConstants.OID_GET_EFFECTIVE_RIGHTS;
import static org.opends.server.util.Validator.ensureNotNull;
import static org.opends.server.util.StaticUtils.toLowerCase;
import org.opends.server.core.DirectoryServer;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

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
public class GetEffectiveRights extends Control {
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  //The DN representing the authzId. May be null.
  private DN authzDN=null;

  //The list of additional attribute types to return rights for. May be null.
  private List<AttributeType> attrs=null;

  /**
   *  Create a new geteffectiverights control with null authzDN and null
   *  attribute list.
   */
  public GetEffectiveRights() {
    super(OID_GET_EFFECTIVE_RIGHTS, true, null);
  }

  /**
   * Create a new geteffectiverights control with the specified raw octet
   * string, an authzDN and an attribute list.
   *
   * @param val  The octet string repsentation of the control value.
   *
   * @param authzDN  The authzDN.
   *
   * @param attrs  The list of additional attributes to be returned.
   */
  public GetEffectiveRights(ASN1OctetString val, DN authzDN,
                            List<AttributeType> attrs) {
    super(OID_GET_EFFECTIVE_RIGHTS, true, val);
    this.authzDN=authzDN;
    this.attrs=attrs;
  }

  /**
   * Return the authzDN parsed from the control.
   *
   * @return The DN representing the authzId.
   */
  public DN getAuthzDN () {
    return authzDN;
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


  /**
   * Decodes the provided ASN1 element.  Assume that it is a ASN1 sequence
   * of attributetypes.
   *
   * @param attributeElement   The ASN1 element to be decoded.
   *
   * @return  A list of attribute types to process rights of.
   *
   * @throws ASN1Exception If the attribute element cannot be decoded as a
   *                       sequence.
   */
  private static
  List<AttributeType> decodeAttributeSequence(ASN1Element attributeElement)
          throws ASN1Exception {
    List<AttributeType>  attributeList = new LinkedList<AttributeType>();
    //Decode the sequence element and put the individual elements in array.
    ArrayList<ASN1Element> attrElems =
            attributeElement.decodeAsSequence().elements();
    int numAttrElements = attrElems.size();
    for(int i=0; i < numAttrElements; i++) {
      //Decode as an octet string.
      ASN1OctetString tmp=attrElems.get(i).decodeAsOctetString();
      //Get an attribute type for it and add to the list.
      AttributeType attributeType;
      if((attributeType =
              DirectoryServer.getAttributeType(tmp.toString())) == null)
        attributeType =
                DirectoryServer.getDefaultAttributeType(tmp.toString());
      attributeList.add(attributeType);
    }
    return attributeList;
  }

  /**
   * Decodes the request control's value octet string into a GetEffectiveRights
   * class. It assumes that it is an ASN1 sequence as described in class
   * description.
   *
   * @param val The octet string repsentation of the control value.
   *
   * @return  A decoded GetEffectiveRights class representing the request
   *          control.
   *
   * @throws LDAPException   If the request control's value contains errors
   *                         causing a valid GetEffectiveRights class to not
   *                         be created.
   */
  private static
  GetEffectiveRights decodeValueSequence(ASN1OctetString val )
          throws LDAPException {
    DN authzDN;
    List<AttributeType> attrs=null;
    String authzIDString="";
    try {
      ASN1Element sequence = ASN1Element.decode(val.value());
      ArrayList<ASN1Element> elements =
              sequence.decodeAsSequence().elements();
      ASN1OctetString authzID = elements.get(0).decodeAsOctetString();
      //There is an sequence containing an attribute list, try to decode it.
      if(elements.size() == 2)
        attrs=decodeAttributeSequence(elements.get(1));
      authzIDString = authzID.stringValue();
      String lowerAuthzIDString = toLowerCase(authzIDString);
      //Make sure authzId starts with "dn:" and is a valid DN.
      if (lowerAuthzIDString.startsWith("dn:"))
         authzDN = DN.decode(authzIDString.substring(3));
      else {
         int  msgID = MSGID_GETEFFECTIVERIGHTS_INVALID_AUTHZID;
         String message = getMessage(msgID, authzID);
         throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }
    } catch (ASN1Exception e) {
         if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
         }

         int msgID = MSGID_GETEFFECTIVERIGHTS_DECODE_ERROR;
         String message = getMessage(msgID, e.getMessage());
         throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    } catch (DirectoryException de) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        int msgID  = MSGID_CANNOT_DECODE_GETEFFECTIVERIGHTS_AUTHZID_DN;
        String message =
        getMessage(msgID, authzIDString.substring(3), de.getErrorMessage());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }
    return new GetEffectiveRights(val, authzDN, attrs);
  }

  /**
   * Decodes the request control's value into a GetEffectiveRights class.
   *
   * @param control  The control class representing the request control.
   *
   * @return  A decoded GetEffectiveRights class representing the request
   *          control.
   *
   * @throws LDAPException   If the request control's value contains errors
   *                         causing a valid GetEffectiveRights class to not
   *                         be created.
   */
  public static
  GetEffectiveRights decodeControl(Control control) throws LDAPException {
    ensureNotNull(control);
    ASN1OctetString value=control.getValue();
    //If the value is null create a GetEffectiveRights class with null
    //authzDN and attribute list, else try to decode the value.
    if(value == null)
      return new GetEffectiveRights();
    else
      return GetEffectiveRights.decodeValueSequence(value);
  }
}
