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



import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LDAPException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP search result
 * reference protocol op, which is used to indicate to the client that an
 * alternate location or server may hold more matching entries.
 */
public class SearchResultReferenceProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of referral URLs for this search result reference.
  private List<String> referralURLs;



  /**
   * Creates a new search result reference protocol op with the provided set of
   * referral URLs.
   *
   * @param  referralURLs  The set of URLs for this search result reference.
   */
  public SearchResultReferenceProtocolOp(List<String> referralURLs)
  {
    if (referralURLs == null)
    {
      this.referralURLs = new ArrayList<String>();
    }
    else
    {
      this.referralURLs = referralURLs;
    }
  }



  /**
   * Creates a new search result reference protocol op from the provided search
   * result reference object.
   *
   * @param  searchReference  The search result reference object to use to
   *                          create this search result reference protocol op.
   */
  public SearchResultReferenceProtocolOp(SearchResultReference searchReference)
  {
    referralURLs = searchReference.getReferralURLs();
    if (referralURLs == null)
    {
      referralURLs = new ArrayList<String>();
    }
  }



  /**
   * Retrieves the set of referral URLs for this search result reference
   * protocol op.  The returned list may be altered by the caller.
   *
   * @return  The set of referral URLs for this search result reference protocol
   *          op.
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
    return OP_TYPE_SEARCH_RESULT_REFERENCE;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Search Result Reference";
  }



  /**
   * Encodes this protocol op to an ASN.1 element suitable for including in an
   * LDAP message.
   *
   * @return  The ASN.1 element containing the encoded protocol op.
   */
  public ASN1Element encode()
  {
    ArrayList<ASN1Element> elements =
         new ArrayList<ASN1Element>(referralURLs.size());
    for (String url : referralURLs)
    {
      elements.add(new ASN1OctetString(url));
    }

    return new ASN1Sequence(OP_TYPE_SEARCH_RESULT_REFERENCE, elements);
  }



  /**
   * Decodes the provided ASN.1 element as a search result reference protocol
   * op.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded search result reference protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP search result reference
   *                         protocol op.
   */
  public static SearchResultReferenceProtocolOp
                     decodeSearchReference(ASN1Element element)
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REFERENCE_DECODE_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ArrayList<String> referralURLs = new ArrayList<String>(elements.size());
    try
    {
      for (ASN1Element e : elements)
      {
        referralURLs.add(e.decodeAsOctetString().stringValue());
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_SEARCH_REFERENCE_DECODE_URLS.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new SearchResultReferenceProtocolOp(referralURLs);
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("SearchReference(referralURLs={");

    if (! referralURLs.isEmpty())
    {
      Iterator<String> iterator = referralURLs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }
    }

    buffer.append("})");
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
    buffer.append("Search Result Reference");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Referral URLs:");
    buffer.append(EOL);

    for (String url : referralURLs)
    {
      buffer.append(indentBuf);
      buffer.append("    ");
      buffer.append(url);
      buffer.append(EOL);
    }
  }
}

