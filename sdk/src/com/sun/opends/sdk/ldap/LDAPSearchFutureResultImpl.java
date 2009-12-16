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



import org.opends.sdk.ResultCode;
import org.opends.sdk.FutureResult;
import org.opends.sdk.ResultHandler;
import org.opends.sdk.SearchResultHandler;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.responses.SearchResultReference;



/**
 * Search result future implementation.
 */
final class LDAPSearchFutureResultImpl extends
    AbstractLDAPFutureResultImpl<Result> implements FutureResult<Result>
{

  private final SearchResultHandler searchResultHandler;

  private final SearchRequest request;



  LDAPSearchFutureResultImpl(int messageID, SearchRequest request,
      ResultHandler<Result> resultHandler,
      SearchResultHandler searchResultHandler, LDAPConnection connection)
  {
    super(messageID, resultHandler, connection);
    this.request = request;
    this.searchResultHandler = searchResultHandler;
  }



  void handleSearchResultEntry(SearchResultEntry entry)
  {
    // FIXME: there's a potential race condition here - the future could
    // get cancelled between the isDone() call and the handler
    // invocation. We'd need to add support for intermediate handlers in
    // the synchronizer.
    if (!isDone())
    {
      if (searchResultHandler != null)
      {
        searchResultHandler.handleEntry(entry);
      }
    }
  }



  void handleSearchResultReference(SearchResultReference reference)
  {
    // FIXME: there's a potential race condition here - the future could
    // get cancelled between the isDone() call and the handler
    // invocation. We'd need to add support for intermediate handlers in
    // the synchronizer.
    if (!isDone())
    {
      if (searchResultHandler != null)
      {
        searchResultHandler.handleReference(reference);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  Result newErrorResult(ResultCode resultCode,
      String diagnosticMessage, Throwable cause)
  {
    return Responses.newResult(resultCode).setDiagnosticMessage(
        diagnosticMessage).setCause(cause);
  }



  SearchRequest getRequest()
  {
    return request;
  }
}
