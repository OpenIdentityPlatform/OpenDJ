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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import java.io.IOException;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.util.Utils;
import org.forgerock.opendj.ldap.DN;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The matched DN for this response. */
  private DN matchedDN;
  /** The result code for this response. */
  private int resultCode;
  /** The set of referral URLs for this response. */
  private List<String> referralURLs;
  /** The error message for this response. */
  private LocalizableMessage errorMessage;

  /**
   * Creates a new delete response protocol op with the provided result code.
   *
   * @param  resultCode  The result code for this response.
   */
  public DeleteResponseProtocolOp(int resultCode)
  {
    this.resultCode = resultCode;
  }

  /**
   * Creates a new delete response protocol op with the provided result code and
   * error message.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   */
  public DeleteResponseProtocolOp(int resultCode, LocalizableMessage errorMessage)
  {
    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
  }

  /**
   * Creates a new delete response protocol op with the provided information.
   *
   * @param  resultCode    The result code for this response.
   * @param  errorMessage  The error message for this response.
   * @param  matchedDN     The matched DN for this response.
   * @param  referralURLs  The referral URLs for this response.
   */
  public DeleteResponseProtocolOp(int resultCode, LocalizableMessage errorMessage,
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
  public LocalizableMessage getErrorMessage()
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

  @Override
  public byte getType()
  {
    return OP_TYPE_DELETE_RESPONSE;
  }

  @Override
  public String getProtocolOpName()
  {
    return "Delete Response";
  }

  @Override
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

    if (referralURLs != null && !referralURLs.isEmpty())
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

  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("DeleteResponse(resultCode=");
    buffer.append(resultCode);

    if (errorMessage != null && errorMessage.length() > 0)
    {
      buffer.append(", errorMessage=");
      buffer.append(errorMessage);
    }
    if (matchedDN != null)
    {
      buffer.append(", matchedDN=");
      buffer.append(matchedDN);
    }
    if (referralURLs != null && !referralURLs.isEmpty())
    {
      buffer.append(", referralURLs={");
      Utils.joinAsString(buffer, ", ", referralURLs);
      buffer.append("}");
    }

    buffer.append(")");
  }

  @Override
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
      buffer.append("  Error LocalizableMessage:  ");
      buffer.append(errorMessage);
      buffer.append(EOL);
    }

    if (matchedDN != null)
    {
      buffer.append(indentBuf);
      buffer.append("  Matched DN:  ");
      buffer.append(matchedDN);
      buffer.append(EOL);
    }

    if (referralURLs != null && !referralURLs.isEmpty())
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
