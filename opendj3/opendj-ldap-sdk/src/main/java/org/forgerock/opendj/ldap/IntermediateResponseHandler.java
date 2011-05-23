/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
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



import org.forgerock.opendj.ldap.responses.IntermediateResponse;



/**
 * A completion handler for consuming intermediate responses returned from
 * extended operations, or other operations for which an appropriate control was
 * sent.
 * <p>
 * {@link Connection} objects support intermediate responses for extended
 * operations only via the {@link Connection#extendedRequest}
 * method. {@link AsynchronousConnection} objects support intermediate responses
 * for extended operations, and all other operation types for which appropriate
 * controls were used. When no handler is provided any intermediate responses
 * will be discarded.
 * <p>
 * The {@link #handleIntermediateResponse} method is invoked each time a
 * Intermediate Response is returned from the Directory Server.
 * <p>
 * Implementations of these methods should complete in a timely manner so as to
 * avoid keeping the invoking thread from dispatching to other completion
 * handlers.
 */
public interface IntermediateResponseHandler
{
  /**
   * Invoked each time an intermediate response is returned from the Directory
   * Server.
   *
   * @param response
   *          The intermediate response.
   * @return {@code true} if this handler should continue to be notified of any
   *         remaining intermediate responses, or {@code false} if the remaining
   *         responses should be skipped for some reason (e.g. a client side
   *         size limit has been reached).
   */
  boolean handleIntermediateResponse(IntermediateResponse response);
}
