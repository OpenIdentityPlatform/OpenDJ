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

package com.sun.opends.sdk.ldap;



import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.ldap.ResolvedSchema;
import org.opends.sdk.controls.Control;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.*;
import org.opends.sdk.sasl.SASLBindRequest;
import org.opends.sdk.schema.Schema;



/**
 * LDAP message handler interface.
 */
public interface LDAPMessageHandler
{
  ResolvedSchema resolveSchema(String dn) throws DecodeException;



  Schema getDefaultSchema();



  void handleException(Throwable throwable);



  void handleUnrecognizedMessage(int messageID, byte messageTag,
      ByteString messageBytes) throws UnsupportedMessageException;



  void handleAbandonRequest(int messageID, AbandonRequest request)
      throws UnexpectedRequestException;



  void handleAddRequest(int messageID, AddRequest request)
      throws UnexpectedRequestException;



  void handleCompareRequest(int messageID, CompareRequest request)
      throws UnexpectedRequestException;



  void handleDeleteRequest(int messageID, DeleteRequest request)
      throws UnexpectedRequestException;



  void handleExtendedRequest(int messageID,
      GenericExtendedRequest request) throws UnexpectedRequestException;



  void handleBindRequest(int messageID, int version,
      GenericBindRequest request) throws UnexpectedRequestException;



  void handleBindRequest(int messageID, int version,
      SASLBindRequest<?> request) throws UnexpectedRequestException;



  void handleBindRequest(int messageID, int version,
      SimpleBindRequest request) throws UnexpectedRequestException;



  void handleModifyDNRequest(int messageID, ModifyDNRequest request)
      throws UnexpectedRequestException;



  void handleModifyRequest(int messageID, ModifyRequest request)
      throws UnexpectedRequestException;



  void handleSearchRequest(int messageID, SearchRequest request)
      throws UnexpectedRequestException;



  void handleUnbindRequest(int messageID, UnbindRequest request)
      throws UnexpectedRequestException;



  void handleAddResult(int messageID, Result result)
      throws UnexpectedResponseException;



  void handleBindResult(int messageID, BindResult result)
      throws UnexpectedResponseException;



  void handleCompareResult(int messageID, CompareResult result)
      throws UnexpectedResponseException;



  void handleDeleteResult(int messageID, Result result)
      throws UnexpectedResponseException;



  void handleExtendedResult(int messageID, GenericExtendedResult result)
      throws UnexpectedResponseException;



  void handleIntermediateResponse(int messageID,
      GenericIntermediateResponse response)
      throws UnexpectedResponseException;



  void handleModifyDNResult(int messageID, Result result)
      throws UnexpectedResponseException;



  void handleModifyResult(int messageID, Result result)
      throws UnexpectedResponseException;



  void handleSearchResult(int messageID, Result result)
      throws UnexpectedResponseException;



  void handleSearchResultEntry(int messageID, SearchResultEntry entry)
      throws UnexpectedResponseException;



  void handleSearchResultReference(int messageID,
      SearchResultReference reference)
      throws UnexpectedResponseException;



  Control decodeResponseControl(int messageID, String oid,
      boolean isCritical, ByteString value, Schema schema)
      throws DecodeException;



  Control decodeRequestControl(int messageID, String oid,
      boolean isCritical, ByteString value, Schema schema)
      throws DecodeException;
}
