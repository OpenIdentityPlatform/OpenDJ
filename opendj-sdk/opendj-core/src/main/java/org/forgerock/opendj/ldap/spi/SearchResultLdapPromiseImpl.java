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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions copyright 2011-2015 ForgeRock AS.
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
