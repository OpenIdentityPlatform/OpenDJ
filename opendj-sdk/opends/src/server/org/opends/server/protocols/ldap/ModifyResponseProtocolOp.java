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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP modify response
 * protocol op, which is used to provide information about the result of
 * processing a modify request.
 */
public class ModifyResponseProtocolOp
       extends ProtocolOp
{



  // The matched DN for this response.
  private DN matchedDN;

  // The result code for this response.
  private int resultCode;

  // The set of referral URLs for this response.
  private List<String> referralURLs;

  // The error message for this response.
  private String errorMessage;



  /**
   * Creates a new modify response protocol op with the provided result code.
   *
   * @param  resultCode  The result code for this response.
   */
  public ModifyResponseProtocolOp(int resultCode)
  {

    this.resultCode = resultCode;

    errorMessage = null;
    matchedDN = null;
    referralURLs = null;
  }



  /**
   * Creates a new modify response protocol op with the provided result code and
   * error message.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   */
  public ModifyResponseProtocolOp(int resultCode, String errorMessage)
  {

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;

    matchedDN    = null;
    referralURLs = null;
  }



  /**
   * Creates a new modify response protocol op with the provided information.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   * @param  matchedDN     The matched DN for this response.
   * @param  referralURLs  The referral URLs for this response.
   */
  public ModifyResponseProtocolOp(int resultCode, String errorMessage,
                                  DN matchedDN, List<String> referralURLs)
  {

    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
    this.matchedDN    = matchedDN;
    this.referralURLs = referralURLs;
  }



  /**
   * Retrieves the result code for this response.
   *
   * @return  The result code for this response.
   */
  public int getResultCode()
  {

    return resultCode;
  }



  /**
   * Specifies the result code for this response.
   *
   * @param  resultCode  The result code for this response.
   */
  public void setResultCode(int resultCode)
  {

    this.resultCode = resultCode;
  }



  /**
   * Retrieves the error message for this response.
   *
   * @return  The error message for this response, or <CODE>null</CODE> if none
   *          is available.
   */
  public String getErrorMessage()
  {

    return errorMessage;
  }



  /**
   * Specifies the error message for this response.
   *
   * @param  errorMessage  The error message for this response.
   */
  public void setErrorMessage(String errorMessage)
  {

    this.errorMessage = errorMessage;
  }



  /**
   * Retrieves the matched DN for this response.
   *
   * @return  The matched DN for this response, or <CODE>null</CODE> if none is
   *          available.
   */
  public DN getMatchedDN()
  {

    return matchedDN;
  }



  /**
   * Specifies the matched DN for this response.
   *
   * @param  matchedDN  The matched DN for this response.
   */
  public void setMatchedDN(DN matchedDN)
  {

    this.matchedDN = matchedDN;
  }



  /**
   * Retrieves the set of referral URLs for this response.
   *
   * @return  The set of referral URLs for this response, or <CODE>null</CODE>
   *          if none are available.
   */
  public List<String> getReferralURLs()
  {

    return referralURLs;
  }



  /**
   * Specifies the set of referral URLs for this response.
   *
   * @param  referralURLs  The set of referral URLs for this response.
   */
  public void setReferralURLs(List<String> referralURLs)
  {

    this.referralURLs = referralURLs;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {

    return OP_TYPE_MODIFY_RESPONSE;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {

    return "Modify Response";
  }



  /**
   * Encodes this protocol op to an ASN.1 element suitable for including in an
   * LDAP message.
   *
   * @return  The ASN.1 element containing the encoded protocol op.
   */
  public ASN1Element encode()
  {

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(4);
    elements.add(new ASN1Enumerated(resultCode));

    if (matchedDN == null)
    {
      elements.add(new ASN1OctetString());
    }
    else
    {
      elements.add(new ASN1OctetString(matchedDN.toString()));
    }

    elements.add(new ASN1OctetString(errorMessage));

    if ((referralURLs != null) && (! referralURLs.isEmpty()))
    {
      ArrayList<ASN1Element> referralElements =
           new ArrayList<ASN1Element>(referralURLs.size());

      for (String s : referralURLs)
      {
        referralElements.add(new ASN1OctetString(s));
      }

      elements.add(new ASN1Sequence(TYPE_REFERRAL_SEQUENCE, referralElements));
    }

    return new ASN1Sequence(OP_TYPE_MODIFY_RESPONSE, elements);
  }



  /**
   * Decodes the provided ASN.1 element as a modify response protocol op.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded modify response protocol op.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         ASN.1 element to a protocol op.
   */
  public static ModifyResponseProtocolOp decodeModifyResponse(ASN1Element
                                                                   element)
         throws LDAPException
  {

    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_RESULT_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if ((numElements < 3) || (numElements > 4))
    {
      int msgID = MSGID_LDAP_RESULT_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    int resultCode;
    try
    {
      resultCode = elements.get(0).decodeAsInteger().intValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_RESULT_DECODE_RESULT_CODE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    DN matchedDN;
    try
    {
      String dnString = elements.get(1).decodeAsOctetString().stringValue();
      if (dnString.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = DN.decode(dnString);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_RESULT_DECODE_MATCHED_DN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    String errorMessage;
    try
    {
      errorMessage = elements.get(2).decodeAsOctetString().stringValue();
      if (errorMessage.length() == 0)
      {
        errorMessage = null;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_RESULT_DECODE_ERROR_MESSAGE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ArrayList<String> referralURLs;
    if (numElements == 3)
    {
      referralURLs = null;
    }
    else
    {
      try
      {
        ArrayList<ASN1Element> referralElements =
             elements.get(3).decodeAsSequence().elements();
        referralURLs = new ArrayList<String>(referralElements.size());

        for (ASN1Element e : referralElements)
        {
          referralURLs.add(e.decodeAsOctetString().stringValue());
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_LDAP_RESULT_DECODE_REFERRALS;
        String message = getMessage(msgID, String.valueOf(e));
        throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
      }
    }


    return new ModifyResponseProtocolOp(resultCode, errorMessage, matchedDN,
                                        referralURLs);
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {

    buffer.append("ModifyResponse(resultCode=");
    buffer.append(resultCode);

    if ((errorMessage != null) && (errorMessage.length() > 0))
    {
      buffer.append(", errorMessage=");
      buffer.append(errorMessage);
    }

    if (matchedDN != null)
    {
      buffer.append(", matchedDN=");
      buffer.append(matchedDN.toString());
    }

    if ((referralURLs != null) && (! referralURLs.isEmpty()))
    {
      buffer.append(", referralURLs={");

      Iterator<String> iterator = referralURLs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      buffer.append("}");
    }

    buffer.append(")");
  }



  /**
   * Appends a multi-line string representation of this LDAP protocol op to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  public void toString(StringBuilder buffer, int indent)
  {

    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Modify Response");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Result Code:  ");
    buffer.append(resultCode);
    buffer.append(EOL);

    if (errorMessage != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Error Message:  ");
      buffer.append(errorMessage);
      buffer.append(EOL);
    }

    if (matchedDN != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Matched DN:  ");
      matchedDN.toString(buffer);
      buffer.append(EOL);
    }

    if ((referralURLs != null) && (! referralURLs.isEmpty()))
    {
      buffer.append(indentBuf);
      buffer.append("  Referral URLs:  ");
      buffer.append(EOL);

      for (String s : referralURLs)
      {
        buffer.append(indentBuf);
        buffer.append("  ");
        buffer.append(s);
        buffer.append(EOL);
      }
    }
  }
}

