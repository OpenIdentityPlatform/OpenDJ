/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.spi;

import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.controls.ADNotificationRequestControl;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.promise.PromiseImpl;

/**
 * Search result promise implementation.
 */
public final class SearchResultLdapPromiseImpl extends ResultLdapPromiseImpl<SearchRequest, Result> implements
        SearchResultHandler {
    private SearchResultHandler searchResultHandler;
    private final boolean isPersistentSearch;

    SearchResultLdapPromiseImpl(
            final PromiseImpl<Result, LdapException> impl,
            final int requestID,
            final SearchRequest request,
            final SearchResultHandler resultHandler,
            final IntermediateResponseHandler intermediateResponseHandler) {
        super(impl, requestID, request, intermediateResponseHandler);
        this.searchResultHandler = resultHandler;
        this.isPersistentSearch = request.containsControl(PersistentSearchRequestControl.OID)
                || request.containsControl(ADNotificationRequestControl.OID);
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleEntry(final SearchResultEntry entry) {
        // FIXME: there's a potential race condition here - the promise could
        // get cancelled between the isDone() call and the handler
        // invocation. We'd need to add support for intermediate handlers in
        // the synchronizer.
        if (!isDone()) {
            updateTimestamp();
            if (searchResultHandler != null && !searchResultHandler.handleEntry(entry)) {
                searchResultHandler = null;
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleReference(final SearchResultReference reference) {
        // FIXME: there's a potential race condition here - the promise could
        // get cancelled between the isDone() call and the handler
        // invocation. We'd need to add support for intermediate handlers in
        // the synchronizer.
        if (!isDone()) {
            updateTimestamp();
            if (searchResultHandler != null && !searchResultHandler.handleReference(reference)) {
                searchResultHandler = null;
            }
        }
        return true;
    }

    @Override
    Result newErrorResult(final ResultCode resultCode, final String diagnosticMessage, final Throwable cause) {
        return Responses.newResult(resultCode).setDiagnosticMessage(diagnosticMessage).setCause(cause);
    }

    @Override
    public boolean checkForTimeout() {
        // Persistent searches should not time out.
        return !isPersistentSearch;
    }

}
