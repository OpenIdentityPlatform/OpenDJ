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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.SearchScope;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP search request
 * protocol op, which is used to locate entries based on a set of criteria.
 */
public class SearchRequestProtocolOp
       extends ProtocolOp
{



  // The typesOnly flag for this search request.
  private boolean typesOnly;

  // The alias dereferencing policy for this search request.
  private DereferencePolicy dereferencePolicy;

  // The base DN for this search request.
  private ASN1OctetString baseDN;

  // The size limit for this search request.
  private int sizeLimit;

  // The time limit for this search request.
  private int timeLimit;

  // The filter for this search request.
  private LDAPFilter filter;

  // The set of requested attributes for this search request.
  private LinkedHashSet<String> attributes;

  // The scope for this search request.
  private SearchScope scope;



  /**
   * Creates a new search request protocol op with the provided information.
   *
   * @param  baseDN             The base DN for this search request.
   * @param  scope              The scope for this search request.
   * @param  dereferencePolicy  The alias dereferencing policy for this search
   *                            request.
   * @param  sizeLimit          The size limit for this search request.
   * @param  timeLimit          The time limit for this search request.
   * @param  typesOnly          The typesOnly flag for this search request.
   * @param  filter             The filter for this search request.
   * @param  attributes         The set of requested attributes for this search
   *                            request.
   */
  public SearchRequestProtocolOp(ASN1OctetString baseDN, SearchScope scope,
                                 DereferencePolicy dereferencePolicy,
                                 int sizeLimit, int timeLimit,
                                 boolean typesOnly, LDAPFilter filter,
                                 LinkedHashSet<String> attributes)
  {


    this.baseDN            = baseDN;
    this.scope             = scope;
    this.dereferencePolicy = dereferencePolicy;
    this.sizeLimit         = sizeLimit;
    this.timeLimit         = timeLimit;
    this.typesOnly         = typesOnly;
    this.filter            = filter;

    if (attributes == null)
    {
      this.attributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.attributes = attributes;
    }
  }



  /**
   * Retrieves the base DN for this search request.
   *
   * @return  The base DN for this search request.
   */
  public ASN1OctetString getBaseDN()
  {

    return baseDN;
  }



  /**
   * Specifies the base DN for this search request.
   *
   * @param  baseDN  The base DN for this search request.
   */
  public void setBaseDN(ASN1OctetString baseDN)
  {

    this.baseDN = baseDN;
  }



  /**
   * Retrieves the scope for this search request.
   *
   * @return  The scope for this search request.
   */
  public SearchScope getScope()
  {

    return scope;
  }



  /**
   * Specifies the scope for this search request.
   *
   * @param  scope  The scope for this search request.
   */
  public void setScope(SearchScope scope)
  {

    this.scope = scope;
  }



  /**
   * Retrieves the alias dereferencing policy for this search request.
   *
   * @return  The alias dereferencing policy for this search request.
   */
  public DereferencePolicy getDereferencePolicy()
  {

    return dereferencePolicy;
  }



  /**
   * Specifies the alias dereferencing policy for this search request.
   *
   * @param  dereferencePolicy  The alias dereferencing policy for this search
   *                            request.
   */
  public void setDereferencePolicy(DereferencePolicy dereferencePolicy)
  {

    this.dereferencePolicy = dereferencePolicy;
  }



  /**
   * Retrieves the size limit for this search request.
   *
   * @return  The size limit for this search request.
   */
  public int getSizeLimit()
  {

    return sizeLimit;
  }



  /**
   * Specifies the size limit for this search request.
   *
   * @param  sizeLimit  The size limit for this search request.
   */
  public void setSizeLimit(int sizeLimit)
  {

    this.sizeLimit = sizeLimit;
  }



  /**
   * Retrieves the time limit for this search request.
   *
   * @return  The time limit for this search request.
   */
  public int getTimeLimit()
  {

    return timeLimit;
  }



  /**
   * Specifies the time limit for this search request.
   *
   * @param  timeLimit  The time limit for this search request.
   */
  public void setTimeLimit(int timeLimit)
  {

    this.timeLimit = timeLimit;
  }



  /**
   * Retrieves the value of the typesOnly flag for this search request.
   *
   * @return  The value of tye typesOnly flag for this search request.
   */
  public boolean getTypesOnly()
  {

    return typesOnly;
  }



  /**
   * Specifies the value of the typesOnly flag for this search request.
   *
   * @param  typesOnly  The value of the typesOnly flag for this search request.
   */
  public void setTypesOnly(boolean typesOnly)
  {

    this.typesOnly = typesOnly;
  }



  /**
   * Retrieves the filter for this search request.
   *
   * @return  The filter for this search request.
   */
  public LDAPFilter getFilter()
  {

    return filter;
  }



  /**
   * Specifies the filter for this search request.
   *
   * @param  filter  The filter for this search request.
   */
  public void setFilter(LDAPFilter filter)
  {

    this.filter = filter;
  }



  /**
   * Retrieves the set of requested attributes for this search request.  The
   * returned list may be modified by the caller.
   *
   * @return  The set of requested attributes for this search request.
   */
  public LinkedHashSet<String> getAttributes()
  {

    return attributes;
  }



  /**
   * Specifies the set of requested attributes for this search request.
   *
   * @param  attributes  The set of requested attributes for this search
   *                     request.
   */
  public void setAttributes(LinkedHashSet<String> attributes)
  {

    if (attributes == null)
    {
      this.attributes.clear();
    }
    else
    {
      this.attributes = attributes;
    }
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {

    return OP_TYPE_SEARCH_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {

    return "Search Request";
  }



  /**
   * Encodes this protocol op to an ASN.1 element suitable for including in an
   * LDAP message.
   *
   * @return  The ASN.1 element containing the encoded protocol op.
   */
  public ASN1Element encode()
  {

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(8);
    elements.add(baseDN);
    elements.add(new ASN1Enumerated(scope.intValue()));
    elements.add(new ASN1Enumerated(dereferencePolicy.intValue()));
    elements.add(new ASN1Integer(sizeLimit));
    elements.add(new ASN1Integer(timeLimit));
    elements.add(new ASN1Boolean(typesOnly));
    elements.add(filter.encode());

    ArrayList<ASN1Element> attrElements =
         new ArrayList<ASN1Element>(attributes.size());
    for (String attribute : attributes)
    {
      attrElements.add(new ASN1OctetString(attribute));
    }
    elements.add(new ASN1Sequence(attrElements));

    return new ASN1Sequence(OP_TYPE_SEARCH_REQUEST, elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP search request protocol op.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded LDAP search request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while decoding the provided
   *                         ASN.1 element as an LDAP search request protocol
   *                         op.
   */
  public static SearchRequestProtocolOp decodeSearchRequest(ASN1Element element)
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

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements != 8)
    {
      int    msgID   = MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, numElements);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    ASN1OctetString baseDN;
    try
    {
      baseDN = elements.get(0).decodeAsOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    SearchScope scope;
    try
    {
      switch (elements.get(1).decodeAsEnumerated().intValue())
      {
        case SCOPE_BASE_OBJECT:
          scope = SearchScope.BASE_OBJECT;
          break;
        case SCOPE_SINGLE_LEVEL:
          scope = SearchScope.SINGLE_LEVEL;
          break;
        case SCOPE_WHOLE_SUBTREE:
          scope = SearchScope.WHOLE_SUBTREE;
          break;
        case SCOPE_SUBORDINATE_SUBTREE:
          scope = SearchScope.SUBORDINATE_SUBTREE;
          break;
        default:
          int    scopeValue = elements.get(1).decodeAsEnumerated().intValue();
          int    msgID      = MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_SCOPE;
          String message    = getMessage(msgID, scopeValue);
          throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_SCOPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    DereferencePolicy dereferencePolicy;
    try
    {
      switch (elements.get(2).decodeAsEnumerated().intValue())
      {
        case DEREF_NEVER:
          dereferencePolicy = DereferencePolicy.NEVER_DEREF_ALIASES;
          break;
        case DEREF_IN_SEARCHING:
          dereferencePolicy = DereferencePolicy.DEREF_IN_SEARCHING;
          break;
        case DEREF_FINDING_BASE:
          dereferencePolicy = DereferencePolicy.DEREF_FINDING_BASE_OBJECT;
          break;
        case DEREF_ALWAYS:
          dereferencePolicy = DereferencePolicy.DEREF_ALWAYS;
          break;
        default:
          int    derefValue = elements.get(2).decodeAsEnumerated().intValue();
          int    msgID      = MSGID_LDAP_SEARCH_REQUEST_DECODE_INVALID_DEREF;
          String message    = getMessage(msgID, derefValue);
          throw new LDAPException(PROTOCOL_ERROR, msgID, message);
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_DEREF;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int sizeLimit;
    try
    {
      sizeLimit = elements.get(3).decodeAsInteger().intValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_SIZE_LIMIT;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int timeLimit;
    try
    {
      timeLimit = elements.get(4).decodeAsInteger().intValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_TIME_LIMIT;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    boolean typesOnly;
    try
    {
      typesOnly = elements.get(5).decodeAsBoolean().booleanValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_TYPES_ONLY;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode(elements.get(6));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_FILTER;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    LinkedHashSet<String> attributes;
    try
    {
      ArrayList<ASN1Element> attrElements =
           elements.get(7).decodeAsSequence().elements();
      attributes = new LinkedHashSet<String>(attrElements.size());
      for (ASN1Element e: attrElements)
      {
        attributes.add(e.decodeAsOctetString().stringValue());
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_SEARCH_REQUEST_DECODE_ATTRIBUTES;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new SearchRequestProtocolOp(baseDN, scope, dereferencePolicy,
                                       sizeLimit, timeLimit, typesOnly, filter,
                                       attributes);
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {

    buffer.append("SearchRequest(baseDN=");
    baseDN.toString(buffer);
    buffer.append(", scope=");
    buffer.append(String.valueOf(scope));
    buffer.append(", derefPolicy=");
    buffer.append(String.valueOf(dereferencePolicy));
    buffer.append(", sizeLimit=");
    buffer.append(sizeLimit);
    buffer.append(", timeLimit=");
    buffer.append(timeLimit);
    buffer.append(", typesOnly=");
    buffer.append(typesOnly);
    buffer.append(", filter=");
    filter.toString(buffer);
    buffer.append(", attributes={");

    if ((attributes != null) && (! attributes.isEmpty()))
    {
      Iterator<String> iterator = attributes.iterator();
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
    buffer.append("Search Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Base DN:  ");
    baseDN.toString(buffer);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Scope:  ");
    buffer.append(String.valueOf(scope));
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Dereference Policy:  ");
    buffer.append(String.valueOf(dereferencePolicy));
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Size Limit:  ");
    buffer.append(sizeLimit);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Time Limit:  ");
    buffer.append(timeLimit);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Types Only:  ");
    buffer.append(typesOnly);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Filter:  ");
    filter.toString(buffer);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Attributes:");
    buffer.append(EOL);

    if (attributes != null)
    {
      for (String attribute : attributes)
      {
        buffer.append(indentBuf);
        buffer.append("    ");
        buffer.append(attribute);
        buffer.append(EOL);
      }
    }
  }
}

