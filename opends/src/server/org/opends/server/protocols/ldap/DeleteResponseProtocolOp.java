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
package org.opends.server.protocols.ldap;
import org.opends.messages.Message;


import java.util.Iterator;
import java.util.List;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.DN;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP delete response
 * protocol op, which is used to provide information about the result of
 * processing a delete request.
 */
public class DeleteResponseProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The matched DN for this response.
  private DN matchedDN;

  // The result code for this response.
  private int resultCode;

  // The set of referral URLs for this response.
  private List<String> referralURLs;

  // The error message for this response.
  private Message errorMessage;



  /**
   * Creates a new delete response protocol op with the provided result code.
   *
   * @param  resultCode  The result code for this response.
   */
  public DeleteResponseProtocolOp(int resultCode)
  {
    this.resultCode = resultCode;

    errorMessage = null;
    matchedDN = null;
    referralURLs = null;
  }



  /**
   * Creates a new delete response protocol op with the provided result code and
   * error message.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   */
  public DeleteResponseProtocolOp(int resultCode, Message errorMessage)
  {
    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;

    matchedDN    = null;
    referralURLs = null;
  }



  /**
   * Creates a new delete response protocol op with the provided information.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   * @param  matchedDN     The matched DN for this response.
   * @param  referralURLs  The referral URLs for this response.
   */
  public DeleteResponseProtocolOp(int resultCode, Message errorMessage,
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
   * Retrieves the error message for this response.
   *
   * @return  The error message for this response, or <CODE>null</CODE> if none
   *          is available.
   */
  public Message getErrorMessage()
  {
    return errorMessage;
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
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_DELETE_RESPONSE;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Delete Response";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_DELETE_RESPONSE);
    stream.writeEnumerated(resultCode);

    if(matchedDN == null)
    {
      stream.writeOctetString((String)null);
    }
    else
    {
      stream.writeOctetString(matchedDN.toString());
    }

    if(errorMessage == null)
    {
      stream.writeOctetString((String)null);
    }
    else
    {
      stream.writeOctetString(errorMessage.toString());
    }

    if ((referralURLs != null) && (! referralURLs.isEmpty()))
    {
      stream.writeStartSequence(TYPE_REFERRAL_SEQUENCE);
      for (String s : referralURLs)
      {
        stream.writeOctetString(s);
      }
      stream.writeEndSequence();
    }

    stream.writeEndSequence();
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("DeleteResponse(resultCode=");
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
    buffer.append("Delete Response");
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

