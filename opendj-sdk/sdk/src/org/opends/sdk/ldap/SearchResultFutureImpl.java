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



import java.util.concurrent.ExecutorService;

import org.opends.sdk.ResultCode;
import org.opends.sdk.ResultFuture;
import org.opends.sdk.ResultHandler;
import org.opends.sdk.SearchResultHandler;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.*;



/**
 * Search result future implementation.
 */
final class SearchResultFutureImpl<P> extends
    AbstractResultFutureImpl<Result, P> implements ResultFuture<Result>
{

  private final SearchResultHandler<P> searchResultHandler;

  private final P p;

  private final SearchRequest request;



  SearchResultFutureImpl(int messageID, SearchRequest request,
      ResultHandler<Result, P> resultHandler,
      SearchResultHandler<P> searchResultHandler, P p,
      LDAPConnection connection, ExecutorService handlerExecutor)
  {
    super(messageID, resultHandler, p, connection, handlerExecutor);
    this.request = request;
    this.searchResultHandler = searchResultHandler;
    this.p = p;
  }



  synchronized void handleSearchResultEntry(
      final SearchResultEntry entry)
  {
    if (!isDone())
    {
      if (searchResultHandler != null)
      {
        invokeHandler(new Runnable()
        {
          public void run()
          {
            searchResultHandler.handleEntry(p, entry);
          }
        });
      }
    }
  }



  synchronized void handleSearchResultReference(
      final SearchResultReference reference)
  {
    if (!isDone())
    {
      if (searchResultHandler != null)
      {
        invokeHandler(new Runnable()
        {
          public void run()
          {
            searchResultHandler.handleReference(p, reference);
          }
        });
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
