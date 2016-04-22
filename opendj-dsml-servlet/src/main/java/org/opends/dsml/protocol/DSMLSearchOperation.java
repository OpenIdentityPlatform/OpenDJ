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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.dsml.protocol;

import static org.opends.messages.ProtocolMessages.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawFilter;

/**
 * This class provides the functionality for the performing an LDAP
 * SEARCH operation based on the specified DSML request.
 */
class DSMLSearchOperation
{

  private final LDAPConnection connection;



  /**
   * Create the instance with the specified connection.
   *
   * @param connection
   *          The LDAP connection to send the request on.
   */

  public DSMLSearchOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }



  /**
   * Returns a new AND search filter with the provided filter
   * components.
   *
   * @param filterSet
   *          The filter components for this filter
   * @return a new AND search filter with the provided filter
   *         components.
   * @throws LDAPException
   *           an LDAPException is thrown if the creation of a filter
   *           component fails.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createANDFilter(FilterSet filterSet)
      throws LDAPException, IOException
  {
    List<JAXBElement<?>> list = filterSet.getFilterGroup();
    ArrayList<RawFilter> filters = new ArrayList<>(list.size());

    for (JAXBElement<?> filter : list)
    {
      filters.add(createFilter(filter));
    }
    return LDAPFilter.createANDFilter(filters);
  }



  /**
   * Returns a new Approximate search filter with the provided
   * information.
   *
   * @param ava
   *          the attribute value assertion for this approximate
   *          filter.
   * @return a new Approximate search filter with the provided
   *         information.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createApproximateFilter(AttributeValueAssertion ava)
    throws IOException
  {
    return LDAPFilter.createApproximateFilter(ava.getName(),
        ByteStringUtility.convertValue(ava.getValue()));
  }



  /**
   * Returns a new Equality search filter with the provided
   * information.
   *
   * @param ava
   *          the attribute value assertion for this Equality filter.
   * @return a new Equality search filter with the provided
   *         information.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createEqualityFilter(AttributeValueAssertion ava)
    throws IOException
  {
    return LDAPFilter.createEqualityFilter(ava.getName(),
        ByteStringUtility.convertValue(ava.getValue()));
  }



  /**
   * Returns a new Extensible search filter with the provided
   * information.
   *
   * @param mra
   *          the matching rule assertion for this Extensible filter.
   * @return a new Extensible search filter with the provided
   *         information.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createExtensibleFilter(MatchingRuleAssertion mra)
    throws IOException
  {
    return LDAPFilter.createExtensibleFilter(mra.getMatchingRule(), mra
        .getName(), ByteStringUtility.convertValue(mra.getValue()),
        mra.isDnAttributes());
  }



  /**
   * Returns a new GreaterOrEqual search filter with the provided
   * information.
   *
   * @param ava
   *          the attribute value assertion for this GreaterOrEqual
   *          filter.
   * @return a new GreaterOrEqual search filter with the provided
   *         information.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createGreaterOrEqualFilter(
      AttributeValueAssertion ava)
    throws IOException
  {
    return LDAPFilter.createGreaterOrEqualFilter(ava.getName(),
        ByteStringUtility.convertValue(ava.getValue()));
  }



  /**
   * Returns a new LessOrEqual search filter with the provided
   * information.
   *
   * @param ava
   *          the attribute value assertion for this LessOrEqual
   *          filter.
   * @return a new LessOrEqual search filter with the provided
   *         information.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createLessOrEqualFilter(AttributeValueAssertion ava)
    throws IOException
  {
    return LDAPFilter.createLessOrEqualFilter(ava.getName(),
        ByteStringUtility.convertValue(ava.getValue()));
  }



  /**
   * Returns a new NOT search filter with the provided information.
   *
   * @param filter
   *          the filter for this NOT filter.
   * @return a new NOT search filter with the provided information.
   * @throws LDAPException
   *           an LDAPException is thrown if the creation of the
   *           provided filter fails.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createNOTFilter(Filter filter)
    throws LDAPException, IOException
  {
    return LDAPFilter.createNOTFilter(createFilter(filter));
  }



  /**
   * Returns a new OR search filter with the provided filter
   * components.
   *
   * @param filterSet
   *          The filter components for this filter
   * @return a new OR search filter with the provided filter
   *         components.
   * @throws LDAPException
   *           an LDAPException is thrown if the creation of a filter
   *           component fails.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createORFilter(FilterSet filterSet)
      throws LDAPException, IOException
  {
    List<JAXBElement<?>> list = filterSet.getFilterGroup();
    ArrayList<RawFilter> filters = new ArrayList<>(list.size());

    for (JAXBElement<?> filter : list)
    {
      filters.add(createFilter(filter));
    }
    return LDAPFilter.createORFilter(filters);
  }



  /**
   * Returns a new Present search filter with the provided
   * information.
   *
   * @param ad
   *          the attribute description for this Present filter.
   * @return a new Present search filter with the provided information.
   * @throws LDAPException
   *           an LDAPException is thrown if the ASN.1 element
   *           provided by the attribute description cannot be decoded
   *           as a raw search filter.
   */
  private static LDAPFilter createPresentFilter(AttributeDescription ad)
      throws LDAPException
  {
    return LDAPFilter.decode(ad.getName() + "=*");
  }



  /**
   * Returns a new Substring search filter with the provided
   * information.
   *
   * @param sf
   *          the substring filter for this Substring filter.
   * @return a new Substring search filter with the provided
   *         information.
   * @throws LDAPException if the filter could not be decoded.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createSubstringFilter(SubstringFilter sf)
        throws LDAPException, IOException
  {
    List<Object> anyo = sf.getAny();
    ArrayList<ByteString> subAnyElements = new ArrayList<>(anyo.size());

    for (Object o : anyo)
    {
      subAnyElements.add(ByteStringUtility.convertValue(o));
    }
    if(sf.getInitial() == null && subAnyElements.isEmpty()
            && sf.getFinal()==null)
    {
      LocalizableMessage message = ERR_LDAP_FILTER_DECODE_NULL.get();
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
    }
    return LDAPFilter.createSubstringFilter(sf.getName(),
        sf.getInitial() == null ? null : ByteStringUtility
            .convertValue(sf.getInitial()),
        subAnyElements,
        sf.getFinal() == null ? null : ByteStringUtility
            .convertValue(sf.getFinal()));
  }



  /**
   * Returns a new LDAPFilter according to the tag name of the
   * provided element that can be "and", "or", "not", "equalityMatch",
   * "substrings", "greaterOrEqual", "lessOrEqual", "present",
   * "approxMatch", "extensibleMatch".
   *
   * @param xmlElement
   *          a JAXBElement that contains the name of the filter to
   *          create and the associated argument.
   * @return a new LDAPFilter according to the tag name of the
   *         provided element.
   * @throws LDAPException
   *           an LDAPException is thrown if the creation of the
   *           targeted filter fails.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createFilter(JAXBElement<?> xmlElement)
      throws LDAPException, IOException
  {
    String filterName = xmlElement.getName().getLocalPart();
    switch (filterName)
    {
    case "and":
      // <xsd:element name="and" type="FilterSet"/>
      return createANDFilter((FilterSet) xmlElement.getValue());

    case "or":
      // <xsd:element name="or" type="FilterSet"/>
      return createORFilter((FilterSet) xmlElement.getValue());

    case "not":
      // <xsd:element name="not" type="Filter"/>
      return createNOTFilter((Filter) xmlElement.getValue());

    case "equalityMatch":
      // <xsd:element name="equalityMatch"
      // type="AttributeValueAssertion"/>
      return createEqualityFilter((AttributeValueAssertion) xmlElement
          .getValue());

    case "substrings":
      // <xsd:element name="substrings" type="SubstringFilter"/>
      return createSubstringFilter((SubstringFilter) xmlElement.getValue());

    case "greaterOrEqual":
      // <xsd:element name="greaterOrEqual"
      // type="AttributeValueAssertion"/>
      return createGreaterOrEqualFilter((AttributeValueAssertion) xmlElement
          .getValue());

    case "lessOrEqual":
      // <xsd:element name="lessOrEqual"
      // type="AttributeValueAssertion"/>
      return createLessOrEqualFilter((AttributeValueAssertion) xmlElement
          .getValue());

    case "present":
      // <xsd:element name="present" type="AttributeDescription"/>
      return createPresentFilter((AttributeDescription) xmlElement.getValue());

    case "approxMatch":
      // <xsd:element name="approxMatch"
      // type="AttributeValueAssertion"/>
      return createApproximateFilter((AttributeValueAssertion) xmlElement
          .getValue());

    case "extensibleMatch":
      // <xsd:element name="extensibleMatch"
      // type="MatchingRuleAssertion"/>
      return createExtensibleFilter((MatchingRuleAssertion) xmlElement
          .getValue());

    default:
      return null;
    }
  }



  /**
   * Returns a new LDAPFilter according to the filter assigned to the
   * provided filter.
   *
   * @param filter
   *          a filter that contains the object filter to create.
   * @return a new LDAPFilter according to the filter assigned to the
   *         provided filter.
   * @throws LDAPException
   *           an LDAPException is thrown if the creation of the
   *           targeted filter fails.
   * @throws IOException if a value is an anyURI and cannot be fetched.
   */
  private static LDAPFilter createFilter(Filter filter)
    throws LDAPException, IOException
  {
    if (filter.getAnd() != null)
    {
      return createANDFilter(filter.getAnd());
    }
    else if (filter.getApproxMatch() != null)
    {
      return createApproximateFilter(filter.getApproxMatch());
    }
    else if (filter.getEqualityMatch() != null)
    {
      return createEqualityFilter(filter.getEqualityMatch());
    }
    else if (filter.getExtensibleMatch() != null)
    {
      return createExtensibleFilter(filter.getExtensibleMatch());
    }
    else if (filter.getGreaterOrEqual() != null)
    {
      return createGreaterOrEqualFilter(filter.getGreaterOrEqual());
    }
    else if (filter.getLessOrEqual() != null)
    {
      return createLessOrEqualFilter(filter.getLessOrEqual());
    }
    else if (filter.getNot() != null)
    {
      return createNOTFilter(filter.getNot());
    }
    else if (filter.getOr() != null)
    {
      return createORFilter(filter.getOr());
    }
    else if (filter.getPresent() != null)
    {
      return createPresentFilter(filter.getPresent());
    }
    else if (filter.getSubstrings() != null)
    {
      return createSubstringFilter(filter.getSubstrings());
    }
    return null;
  }



  /**
   * Perform the LDAP SEARCH operation and send the result back to the
   * client.
   *
   * @param objFactory
   *          The object factory for this operation.
   * @param searchRequest
   *          The search request for this operation.
   * @param controls
   *          Any required controls (e.g. for proxy authz).
   * @return The result of the search operation.
   * @throws IOException
   *           If an I/O problem occurs.
   * @throws LDAPException
   *           If an error occurs while interacting with an LDAP
   *           element.
   */
  public SearchResponse doSearch(ObjectFactory objFactory,
      SearchRequest searchRequest,
      List<org.opends.server.types.Control> controls)
  throws IOException, LDAPException
  {
    SearchResponse searchResponse = objFactory.createSearchResponse();
    searchResponse.setRequestID(searchRequest.getRequestID());

    LDAPFilter filter = createFilter(searchRequest.getFilter());

    DereferenceAliasesPolicy derefPolicy = DereferenceAliasesPolicy.NEVER;
    String derefStr = searchRequest.getDerefAliases().toLowerCase();
    if (derefStr.equals("derefinsearching"))
    {
      derefPolicy = DereferenceAliasesPolicy.IN_SEARCHING;
    }
    else if (derefStr.equals("dereffindingbaseobj"))
    {
      derefPolicy = DereferenceAliasesPolicy.FINDING_BASE;
    }
    else if (derefStr.equals("derefalways"))
    {
      derefPolicy = DereferenceAliasesPolicy.ALWAYS;
    }

    SearchScope scope = SearchScope.WHOLE_SUBTREE;
    String scopeStr = searchRequest.getScope().toLowerCase();
    if (scopeStr.equals("singlelevel") || scopeStr.equals("one"))
    {
      scope = SearchScope.SINGLE_LEVEL;
    }
    else if (scopeStr.equals("baseobject") || scopeStr.equals("base"))
    {
      scope = SearchScope.BASE_OBJECT;
    }

    LinkedHashSet<String> attributes = new LinkedHashSet<>();
    // Get the list of attributes.
    AttributeDescriptions attrDescriptions = searchRequest.getAttributes();
    if (attrDescriptions != null)
    {
      List<AttributeDescription> attrDesc = attrDescriptions.getAttribute();
      for (AttributeDescription desc : attrDesc)
      {
        attributes.add(desc.getName());
      }
    }

    SearchRequestProtocolOp protocolOp = new SearchRequestProtocolOp(ByteString
        .valueOfUtf8(searchRequest.getDn()), scope, derefPolicy,
        (int) searchRequest.getSizeLimit(), (int) searchRequest.getTimeLimit(),
        searchRequest.isTypesOnly(), filter, attributes);
    try
    {
      LDAPMessage msg =
        new LDAPMessage(DSMLServlet.nextMessageID(), protocolOp, controls);
      connection.getLDAPWriter().writeMessage(msg);

      byte opType;
      do
      {
        int resultCode = 0;
        LocalizableMessage errorMessage = null;
        LDAPMessage responseMessage = connection.getLDAPReader().readMessage();
        if(responseMessage == null)
        {
          //The server disconnected silently. At this point we don't know if it
          // is a protocol error or anything else. Since we didn't hear from
          // the server , we have a reason to believe that the server doesn't
          // want to handle this request. Let us return unavailable error
          // code to the client to cover possible cases.
          LocalizableMessage message = ERR_UNEXPECTED_CONNECTION_CLOSURE.get();
          LDAPResult result = objFactory.createLDAPResult();
          ResultCode code = ResultCodeFactory.create(objFactory,
              LDAPResultCode.UNAVAILABLE);
          result.setResultCode(code);
          result.setErrorMessage(message.toString());
          searchResponse.setSearchResultDone(result);
          return searchResponse;
        }
        opType = responseMessage.getProtocolOpType();
        switch (opType)
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
          SearchResultEntryProtocolOp searchEntryOp = responseMessage
              .getSearchResultEntryProtocolOp();

          SearchResultEntry entry = objFactory.createSearchResultEntry();
          java.util.List<DsmlAttr> attrList = entry.getAttr();

          LinkedList<LDAPAttribute> attrs = searchEntryOp.getAttributes();

          for (LDAPAttribute attr : attrs)
          {
            String nm = attr.getAttributeType();
            DsmlAttr dsmlAttr = objFactory.createDsmlAttr();

            dsmlAttr.setName(nm);
            List<Object> dsmlAttrVal = dsmlAttr.getValue();
            List<ByteString> vals = attr.getValues();
            for (ByteString val : vals)
            {
              dsmlAttrVal.add(ByteStringUtility.convertByteString(val));
            }
            attrList.add(dsmlAttr);
          }

          entry.setDn(searchEntryOp.getDN().toString());
          searchResponse.getSearchResultEntry().add(entry);
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
          responseMessage.getSearchResultReferenceProtocolOp();
          break;

        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          SearchResultDoneProtocolOp searchOp = responseMessage
              .getSearchResultDoneProtocolOp();
          resultCode = searchOp.getResultCode();
          errorMessage = searchOp.getErrorMessage();
          LDAPResult result = objFactory.createLDAPResult();
          ResultCode code = ResultCodeFactory.create(objFactory, resultCode);
          result.setResultCode(code);
          result.setErrorMessage(errorMessage != null ? errorMessage.toString()
              : null);
          if (searchOp.getMatchedDN() != null)
          {
            result.setMatchedDN(searchOp.getMatchedDN().toString());
          }
          searchResponse.setSearchResultDone(result);
          break;
        default:
          throw new RuntimeException("Invalid protocol operation:" + opType);
        }
      }
      while (opType != LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE);

    }
    catch (DecodeException ae)
    {
      ae.printStackTrace();
      throw new IOException(ae.getMessage());
    }

    return searchResponse;
  }
}
