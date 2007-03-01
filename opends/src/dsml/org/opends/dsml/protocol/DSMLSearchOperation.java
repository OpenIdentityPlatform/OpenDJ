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
package org.opends.dsml.protocol;



import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.SearchResultReferenceProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.DereferencePolicy;
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

    String requestID = searchRequest.getRequestID();
    int reqID = 1;
    try
    {
      reqID = Integer.parseInt(requestID);
    } catch (NumberFormatException nfe)
    {
      throw new IOException(nfe.getMessage());
    }

    searchResponse.setRequestID(requestID);

    ArrayList<LDAPFilter> filters = new ArrayList<LDAPFilter> ();
    LDAPFilter f = null;
    if(searchRequest.getFilter().getPresent() != null)
    {
      f = LDAPFilter.decode(searchRequest.getFilter().getPresent().getName() +
          "=*");
    } else if(searchRequest.getFilter().getEqualityMatch() != null)
    {
      AttributeValueAssertion fgem =
           searchRequest.getFilter().getEqualityMatch();

      f = LDAPFilter.createEqualityFilter(fgem.getName(),
      new ASN1OctetString(fgem.getValue()));
    }
    if(f != null)
    {
      filters.add(f);
    }
    DereferencePolicy derefPolicy = DereferencePolicy.DEREF_IN_SEARCHING;
    SearchScope scope = SearchScope.WHOLE_SUBTREE;
    if(searchRequest.getScope().equals("singleLevel"))
    {
      scope = SearchScope.SINGLE_LEVEL;
    } else if(searchRequest.getScope().equals("baseObject"))
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

    for (LDAPFilter filter: filters)
    {

      SearchRequestProtocolOp protocolOp = new SearchRequestProtocolOp(
          new ASN1OctetString(searchRequest.getDn()),
          scope, derefPolicy,
                  (int) searchRequest.getSizeLimit(),
          (int) searchRequest.getTimeLimit(),
          false, filter, attributes);
      try
      {
        LDAPMessage msg = new LDAPMessage(reqID, protocolOp);
        int numBytes = connection.getASN1Writer().writeElement(msg.encode());

        byte opType;
        do
        {
          int resultCode = 0;
          String errorMessage = null;
          ASN1Element element = connection.getASN1Reader().readElement();
          LDAPMessage responseMessage = LDAPMessage.decode(
          ASN1Sequence.decodeAsSequence(element));

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
        /*
              StringBuilder sb = new StringBuilder();
              searchEntryOp.toLDIF(sb, 80);
              System.out.println(sb.toString());
        */
              break;

            case LDAPConstants.OP_TYPE_SEARCH_RESULT_REFERENCE:
              SearchResultReferenceProtocolOp searchRefOp =
                responseMessage.getSearchResultReferenceProtocolOp();
              System.out.println(searchRefOp.toString());
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
              result.setErrorMessage(errorMessage);
              if(searchOp.getMatchedDN() != null)
              {
                 result.setMatchedDN(searchOp.getMatchedDN().toString());
              }
              searchResponse.setSearchResultDone(result);
              break;
            default:
               // FIXME - throw exception
               System.err.println("Invalid protocol operation:" + opType);
               break;
           }

           if(resultCode != 0 && resultCode != 10)
           {
             org.opends.server.types.ResultCode rc =
                  org.opends.server.types.ResultCode.valueOf(resultCode);

             // FIXME.
             int msgID = 0;
             throw new LDAPException(resultCode, msgID, rc.toString());
           }

        } while(opType != LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE);

      } catch(ASN1Exception ae)
      {
        ae.printStackTrace();
        throw new IOException(ae.getMessage());
      }

      reqID++;
    }
    return searchResponse;
  }

}

