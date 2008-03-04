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
package org.opends.dsml.protocol;
import org.opends.messages.Message;



import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.SearchResultReferenceProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.ByteString;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawFilter;
import org.opends.server.types.SearchScope;



/**
 * This class provides the functionality for the performing an
 * LDAP SEARCH operation based on the specified DSML request.
 */
public class DSMLSearchOperation
{
  private LDAPConnection connection;

  /**
   * Create the instance with the specified connection.
   *
   * @param connection    The LDAP connection to send the request on.
   */

  public DSMLSearchOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }

  /**
   * Returns a new AND search filter with the provided filter components.
   *
   * @param filterSet The filter components for this filter
   *
   * @return a new AND search filter with the provided filter components.
   *
   * @throws LDAPException an LDAPException is thrown if the creation of a
   *                       filter component fails.
   */
  private static LDAPFilter createANDFilter(FilterSet filterSet)
          throws LDAPException {
    List<JAXBElement<?>> list = filterSet.getFilterGroup();
    ArrayList<RawFilter> filters = new ArrayList<RawFilter>(list.size());

    for(JAXBElement<?> filter : list) {
      filters.add(createFilter(filter));
    }
    return LDAPFilter.createANDFilter(filters);
  }

  /**
   * Returns a new Approximate search filter with the provided information.
   *
   * @param ava the attribute value assertion for this approximate filter.
   *
   * @return a new Approximate search filter with the provided information.
   */
  private static LDAPFilter createApproximateFilter(AttributeValueAssertion ava)
  {
    return LDAPFilter.createApproximateFilter(ava.getName(),
                                           new ASN1OctetString(ava.getValue()));
  }

  /**
   * Returns a new Equality search filter with the provided information.
   *
   * @param ava the attribute value assertion for this Equality filter.
   *
   * @return a new Equality search filter with the provided information.
   */
  private static LDAPFilter createEqualityFilter(AttributeValueAssertion ava) {
    return LDAPFilter.createEqualityFilter(ava.getName(),
                                          new ASN1OctetString(ava.getValue()));
  }

  /**
   * Returns a new Extensible search filter with the provided information.
   *
   * @param mra the matching rule assertion for this Extensible filter.
   *
   * @return a new Extensible search filter with the provided information.
   */
  private static LDAPFilter createExtensibleFilter(MatchingRuleAssertion mra) {
    return LDAPFilter.createExtensibleFilter(mra.getMatchingRule(),
                                        mra.getName(),
                                        new ASN1OctetString(mra.getValue()),
                                        mra.isDnAttributes());
  }

  /**
   * Returns a new GreaterOrEqual search filter with the provided information.
   *
   * @param ava the attribute value assertion for this GreaterOrEqual filter.
   *
   * @return a new GreaterOrEqual search filter with the provided information.
   */
  private static LDAPFilter createGreaterOrEqualFilter(
                              AttributeValueAssertion ava) {
    return LDAPFilter.createGreaterOrEqualFilter(ava.getName(),
                                          new ASN1OctetString(ava.getValue()));
  }

  /**
   * Returns a new LessOrEqual search filter with the provided information.
   *
   * @param ava the attribute value assertion for this LessOrEqual filter.
   *
   * @return a new LessOrEqual search filter with the provided information.
   */
  private static LDAPFilter createLessOrEqualFilter(
                              AttributeValueAssertion ava) {
    return LDAPFilter.createLessOrEqualFilter(ava.getName(),
                                          new ASN1OctetString(ava.getValue()));
  }

  /**
   * Returns a new NOT search filter with the provided information.
   *
   * @param filter the filter for this NOT filter.
   *
   * @return a new NOT search filter with the provided information.
   *
   * @throws LDAPException an LDAPException is thrown if the creation of the
   *                       provided filter fails.
   */
  private static LDAPFilter createNOTFilter(Filter filter)
          throws LDAPException {
    return LDAPFilter.createNOTFilter(createFilter(filter));
  }

  /**
   * Returns a new OR search filter with the provided filter components.
   *
   * @param filterSet The filter components for this filter
   *
   * @return a new OR search filter with the provided filter components.
   *
   * @throws LDAPException an LDAPException is thrown if the creation of a
   *                       filter component fails.
   */
  private static LDAPFilter createORFilter(FilterSet filterSet)
          throws LDAPException {
    List<JAXBElement<?>> list = filterSet.getFilterGroup();
    ArrayList<RawFilter> filters = new ArrayList<RawFilter>(list.size());

    for(JAXBElement<?> filter : list) {
      filters.add(createFilter(filter));
    }
    return LDAPFilter.createORFilter(filters);
  }

  /**
   * Returns a new Present search filter with the provided information.
   *
   * @param ad the attribute description for this Present filter.
   *
   * @returna new Present search filter with the provided information.
   *
   * @throws LDAPException an LDAPException is thrown if the ASN.1 element
   *                       provided by the attribute description cannot be
   *                       decoded as a raw search filter.
   */
  private static LDAPFilter createPresentFilter(AttributeDescription ad)
          throws LDAPException {
    return LDAPFilter.decode(
             new StringBuilder(ad.getName()).append("=*").toString());
  }

  /**
   * Returns a new Substring search filter with the provided information.
   *
   * @param sf the substring filter for this Substring filter.
   *
   * @return a new Substring search filter with the provided information.
   */
  private static LDAPFilter createSubstringFilter(SubstringFilter sf) {
    List<String> anys = sf.getAny();
    ArrayList<ByteString> subAnyElements =
                                         new ArrayList<ByteString>(anys.size());

    for(String s : anys) {
      subAnyElements.add(new ASN1OctetString(s));
    }
    return LDAPFilter.createSubstringFilter(sf.getName(),
                                        new ASN1OctetString(sf.getInitial()),
                                        subAnyElements,
                                        new ASN1OctetString(sf.getFinal()));
  }

  /**
   * Returns a new LDAPFilter according to the tag name of the provided element
   * that can be "and", "or", "not", "equalityMatch", "substrings",
   * "greaterOrEqual", "lessOrEqual", "present", "approxMatch",
   * "extensibleMatch".
   *
   * @param xmlElement a JAXBElement that contains the name of the filter to
   *                   create and the associated argument.
   *
   * @return a new LDAPFilter according to the tag name of the provided element.
   *
   * @throws LDAPException an LDAPException is thrown if the creation of the
   *                       targeted filter fails.
   */
  private static LDAPFilter createFilter(JAXBElement<?> xmlElement)
          throws LDAPException {
    LDAPFilter result = null;

    String filterName = xmlElement.getName().getLocalPart();

    if ( "and".equals(filterName) ) {
      // <xsd:element name="and" type="FilterSet"/>
      result = createANDFilter((FilterSet)xmlElement.getValue());
    }
    else if ( "or".equals(filterName) ) {
      // <xsd:element name="or" type="FilterSet"/>
      result = createORFilter((FilterSet)xmlElement.getValue());
    }
    else if ( "not".equals(filterName) ) {
      // <xsd:element name="not" type="Filter"/>
      result = createNOTFilter((Filter)xmlElement.getValue());
    }
    else if ( "equalityMatch".equals(filterName) ) {
      // <xsd:element name="equalityMatch" type="AttributeValueAssertion"/>
      result = createEqualityFilter((AttributeValueAssertion)
                                                         xmlElement.getValue());
    }
    else if ( "substrings".equals(filterName) ) {
      // <xsd:element name="substrings" type="SubstringFilter"/>
      result = createSubstringFilter((SubstringFilter)xmlElement.getValue());
    }
    else if ( "greaterOrEqual".equals(filterName) ) {
      // <xsd:element name="greaterOrEqual" type="AttributeValueAssertion"/>
      result = createGreaterOrEqualFilter((AttributeValueAssertion)
                                                         xmlElement.getValue());
    }
    else if ( "lessOrEqual".equals(filterName) ) {
      // <xsd:element name="lessOrEqual" type="AttributeValueAssertion"/>
      result = createLessOrEqualFilter((AttributeValueAssertion)
                                                         xmlElement.getValue());
    }
    else if ( "present".equals(filterName) ) {
      // <xsd:element name="present" type="AttributeDescription"/>
      result = createPresentFilter((AttributeDescription)xmlElement.getValue());
    }
    else if ( "approxMatch".equals(filterName) ) {
      // <xsd:element name="approxMatch" type="AttributeValueAssertion"/>
      result = createApproximateFilter((AttributeValueAssertion)
                                                         xmlElement.getValue());
    }
    else if ( "extensibleMatch".equals(filterName) ) {
      // <xsd:element name="extensibleMatch" type="MatchingRuleAssertion"/>
      result = createExtensibleFilter((MatchingRuleAssertion)
                                                         xmlElement.getValue());
    }
    return result;
  }

  /**
   * Returns a new LDAPFilter according to the filter assigned to the provided
   * filter.
   *
   * @param filter a filter that contains the object filter to create.
   *
   * @return a new LDAPFilter according to the filter assigned to the provided
   *         filter.
   *
   * @throws LDAPException an LDAPException is thrown if the creation of the
   *                       targeted filter fails.
   */
  private static LDAPFilter createFilter(Filter filter)
          throws LDAPException {

    LDAPFilter result = null;

    if ( filter.getAnd() != null ) {
      result = createANDFilter(filter.getAnd());
    }
    else if ( filter.getApproxMatch() != null ) {
      result = createApproximateFilter(filter.getApproxMatch());
    }
    else if ( filter.getEqualityMatch() != null ) {
      result = createEqualityFilter(filter.getEqualityMatch());
    }
    else if ( filter.getExtensibleMatch() != null ) {
      result = createExtensibleFilter(filter.getExtensibleMatch());
    }
    else if ( filter.getGreaterOrEqual() != null ) {
      result = createGreaterOrEqualFilter(filter.getGreaterOrEqual());
    }
    else if ( filter.getLessOrEqual() != null ) {
      result = createLessOrEqualFilter(filter.getLessOrEqual());
    }
    else if ( filter.getNot() != null ) {
      result = createNOTFilter(filter.getNot());
    }
    else if ( filter.getOr() != null ) {
      result = createORFilter(filter.getOr());
    }
    else if ( filter.getPresent() != null ) {
      result = createPresentFilter(filter.getPresent());
    }
    else if ( filter.getSubstrings() != null ) {
      result = createSubstringFilter(filter.getSubstrings());
    }
    return result;
  }

  /**
   * Perform the LDAP SEARCH operation and send the result back to the
   * client.
   *
   * @param  objFactory     The object factory for this operation.
   * @param  searchRequest  The search request for this operation.
   *
   * @return  The result of the add operation.
   *
   * @throws  IOException  If an I/O problem occurs.
   *
   * @throws  LDAPException  If an error occurs while interacting with an LDAP
   *                         element.
   */
  public SearchResponse doSearch(ObjectFactory objFactory,
         SearchRequest searchRequest)
         throws IOException, LDAPException
  {
    SearchResponse searchResponse = objFactory.createSearchResponse();
    searchResponse.setRequestID(searchRequest.getRequestID());

    LDAPFilter filter = createFilter(searchRequest.getFilter());

    DereferencePolicy derefPolicy = DereferencePolicy.NEVER_DEREF_ALIASES;
    String derefStr = searchRequest.getDerefAliases().toLowerCase();
    if (derefStr.equals("derefinsearching"))
    {
      derefPolicy = DereferencePolicy.DEREF_IN_SEARCHING;
    }
    else if (derefStr.equals("dereffindingbaseobj"))
    {
      derefPolicy = DereferencePolicy.DEREF_FINDING_BASE_OBJECT;
    }
    else if (derefStr.equals("derefalways"))
    {
      derefPolicy = DereferencePolicy.DEREF_ALWAYS;
    }

    SearchScope scope = SearchScope.WHOLE_SUBTREE;
    String scopeStr = searchRequest.getScope().toLowerCase();
    if(scopeStr.equals("singlelevel") || scopeStr.equals("one"))
    {
      scope = SearchScope.SINGLE_LEVEL;
    } else if(scopeStr.equals("baseobject") || scopeStr.equals("base"))
    {
      scope = SearchScope.BASE_OBJECT;
    }

    LinkedHashSet<String> attributes = new LinkedHashSet<String>();
    // Get the list of attributes.
    AttributeDescriptions attrDescriptions = searchRequest.getAttributes();
    if(attrDescriptions != null)
    {
      List<AttributeDescription> attrDesc = attrDescriptions.getAttribute();
      for(AttributeDescription desc : attrDesc)
      {
        attributes.add(desc.getName());
      }
    }

    SearchRequestProtocolOp protocolOp = new SearchRequestProtocolOp(
        new ASN1OctetString(searchRequest.getDn()),
        scope, derefPolicy,
                (int) searchRequest.getSizeLimit(),
        (int) searchRequest.getTimeLimit(),
        searchRequest.isTypesOnly(), filter, attributes);
    try
    {
      LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(),
                                        protocolOp);
      connection.getLDAPWriter().writeMessage(msg);

      byte opType;
      do
      {
        int resultCode = 0;
        Message errorMessage = null;
        LDAPMessage responseMessage =
             connection.getLDAPReader().readMessage();

        opType = responseMessage.getProtocolOpType();
        switch(opType)
        {
          case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
            SearchResultEntryProtocolOp searchEntryOp =
              responseMessage.getSearchResultEntryProtocolOp();

            SearchResultEntry entry = objFactory.createSearchResultEntry();
            java.util.List<DsmlAttr> attrList = entry.getAttr();

            LinkedList<LDAPAttribute> attrs = searchEntryOp.getAttributes();

            for(LDAPAttribute attr : attrs)
            {
              String nm = attr.getAttributeType();
              DsmlAttr dsmlAttr = objFactory.createDsmlAttr();

              dsmlAttr.setName(nm);
              List<String> dsmlAttrVal = dsmlAttr.getValue();
              ArrayList<ASN1OctetString> vals = attr.getValues();
              for(ASN1OctetString val : vals)
              {
                dsmlAttrVal.add(val.toString());
              }
              attrList.add(dsmlAttr);
            }

            entry.setDn(searchEntryOp.getDN().toString());
            searchResponse.getSearchResultEntry().add(entry);
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
            SearchResultReferenceProtocolOp searchRefOp =
              responseMessage.getSearchResultReferenceProtocolOp();
            break;

          case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
            SearchResultDoneProtocolOp searchOp =
              responseMessage.getSearchResultDoneProtocolOp();
            resultCode = searchOp.getResultCode();
            errorMessage = searchOp.getErrorMessage();
            LDAPResult result = objFactory.createLDAPResult();
            ResultCode code = objFactory.createResultCode();
            code.setCode(resultCode);
            result.setResultCode(code);
            result.setErrorMessage(
                    errorMessage != null ? errorMessage.toString() : null);
            if(searchOp.getMatchedDN() != null)
            {
               result.setMatchedDN(searchOp.getMatchedDN().toString());
            }
            searchResponse.setSearchResultDone(result);
            break;
          default:
             throw new RuntimeException("Invalid protocol operation:" + opType);
         }
      } while(opType != LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE);

    } catch(ASN1Exception ae)
    {
      ae.printStackTrace();
      throw new IOException(ae.getMessage());
    }

    return searchResponse;
  }
}

