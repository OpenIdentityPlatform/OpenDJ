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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap;



import org.forgerock.opendj.ldap.requests.*;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;



/**
 * A handler interface for processing client requests.
 * <p>
 * Implementations must always return results using the provided
 * {@link ResultHandler} unless explicitly permitted.
 * <p>
 * For example, an {@link LDAPListener} does not require {@code RequestHandler}
 * implementations to return results, which may be useful when implementing
 * abandon operation functionality. Conversely, an access logger implemented as
 * a {@code RequestHandler} wrapper will require wrapped {@code RequestHandler}s
 * to always return results, even abandoned results, in order for it to log the
 * result status.
 *
 * @param <C>
 *          The type of request context.
 * @see ServerConnectionFactory
 */
public interface RequestHandler<C>
{

  /**
   * Invoked when an add request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The add request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle add requests.
   */
  void handleAdd(C requestContext, AddRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a bind request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param version
   *          The protocol version included with the bind request.
   * @param request
   *          The bind request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle bind requests.
   */
  void handleBind(C requestContext, int version, BindRequest request,
      ResultHandler<? super BindResult> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a compare request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The compare request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle compare requests.
   */
  void handleCompare(C requestContext, CompareRequest request,
      ResultHandler<? super CompareResult> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a delete request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The delete request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle delete requests.
   */
  void handleDelete(C requestContext, DeleteRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when an extended request is received from a client.
   *
   * @param <R>
   *          The type of result returned by the extended request.
   * @param requestContext
   *          The request context.
   * @param request
   *          The extended request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle extended requests.
   */
  <R extends ExtendedResult> void handleExtendedRequest(C requestContext,
      ExtendedRequest<R> request, ResultHandler<? super R> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a modify request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The modify request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle modify requests.
   */
  void handleModify(C requestContext, ModifyRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a modify DN request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The modify DN request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle modify DN requests.
   */
  void handleModifyDN(C requestContext, ModifyDNRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a search request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The search request.
   * @param resultHandler
   *          The handler which should be used to send back the search results
   *          to the client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this request handler does not handle search requests.
   */
  void handleSearch(C requestContext, SearchRequest request,
      SearchResultHandler resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;
}
