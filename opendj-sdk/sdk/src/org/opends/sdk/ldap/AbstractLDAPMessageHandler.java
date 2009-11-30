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

package org.opends.sdk.ldap;



import org.opends.sdk.DecodeException;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.GenericControl;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.sasl.SASLBindRequest;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;



/**
 * Abstract LDAP message handler.
 */
abstract class AbstractLDAPMessageHandler implements LDAPMessageHandler
{
  public void handleUnrecognizedMessage(int messageID, byte messageTag,
      ByteString messageBytes) throws UnsupportedMessageException
  {
    throw new UnsupportedMessageException(messageID, messageTag,
        messageBytes);
  }



  public void handleAbandonRequest(int messageID, AbandonRequest request)
      throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleAddRequest(int messageID, AddRequest request)
      throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleCompareRequest(int messageID, CompareRequest request)
      throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleDeleteRequest(int messageID, DeleteRequest request)
      throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleExtendedRequest(int messageID,
      GenericExtendedRequest request) throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleBindRequest(int messageID, int version,
      GenericBindRequest request) throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleBindRequest(int messageID, int version,
      SASLBindRequest<?> request) throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleBindRequest(int messageID, int version,
      SimpleBindRequest request) throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleModifyDNRequest(int messageID,
      ModifyDNRequest request) throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleModifyRequest(int messageID, ModifyRequest request)
      throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleSearchRequest(int messageID, SearchRequest request)
      throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleUnbindRequest(int messageID, UnbindRequest request)
      throws UnexpectedRequestException
  {
    throw new UnexpectedRequestException(messageID, request);
  }



  public void handleAddResult(int messageID, Result result)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleBindResult(int messageID, BindResult result)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleCompareResult(int messageID, CompareResult result)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleDeleteResult(int messageID, Result result)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleExtendedResult(int messageID,
      GenericExtendedResult result) throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleIntermediateResponse(int messageID,
      GenericIntermediateResponse response)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, response);
  }



  public void handleModifyDNResult(int messageID, Result result)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleModifyResult(int messageID, Result result)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleSearchResult(int messageID, Result result)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, result);
  }



  public void handleSearchResultEntry(int messageID,
      SearchResultEntry entry) throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, entry);
  }



  public void handleSearchResultReference(int messageID,
      SearchResultReference reference)
      throws UnexpectedResponseException
  {
    throw new UnexpectedResponseException(messageID, reference);
  }



  public Control decodeResponseControl(int messageID, String oid,
      boolean isCritical, ByteString value, Schema schema)
      throws DecodeException
  {
    return new GenericControl(oid, isCritical, value);
  }



  public Control decodeRequestControl(int messageID, String oid,
      boolean isCritical, ByteString value, Schema schema)
      throws DecodeException
  {
    return new GenericControl(oid, isCritical, value);
  }
}
