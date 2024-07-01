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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import static org.forgerock.util.Reject.*;

import org.forgerock.opendj.ldap.AbstractAsynchronousConnection;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;

abstract class AbstractAsynchronousConnectionDecorator extends AbstractAsynchronousConnection {

    protected final Connection delegate;

    AbstractAsynchronousConnectionDecorator(Connection delegate) {
        this.delegate = checkNotNull(delegate, "delegate cannot be null");
    }

    @Override
    public LdapPromise<Void> abandonAsync(AbandonRequest request) {
        return delegate.abandonAsync(request);
    }

    @Override
    public LdapPromise<Result> addAsync(AddRequest request, IntermediateResponseHandler intermediateResponseHandler) {
        return delegate.addAsync(request, intermediateResponseHandler);
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        delegate.addConnectionEventListener(listener);
    }

    @Override
    public LdapPromise<BindResult> bindAsync(BindRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        return delegate.bindAsync(request, intermediateResponseHandler);
    }

    @Override
    public void close(UnbindRequest request, String reason) {
        delegate.close(request, reason);
    }

    @Override
    public LdapPromise<CompareResult> compareAsync(CompareRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        return delegate.compareAsync(request, intermediateResponseHandler);
    }

    @Override
    public LdapPromise<Result> deleteAsync(DeleteRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        return delegate.deleteAsync(request, intermediateResponseHandler);
    }

    @Override
    public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(ExtendedRequest<R> request,
            IntermediateResponseHandler intermediateResponseHandler) {
        return delegate.extendedRequestAsync(request, intermediateResponseHandler);
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public boolean isValid() {
        return delegate.isValid();
    }

    @Override
    public LdapPromise<Result> modifyAsync(ModifyRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        return delegate.modifyAsync(request, intermediateResponseHandler);
    }

    @Override
    public LdapPromise<Result> modifyDNAsync(ModifyDNRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        return delegate.modifyDNAsync(request, intermediateResponseHandler);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        delegate.removeConnectionEventListener(listener);
    }

    @Override
    public LdapPromise<Result> searchAsync(SearchRequest request,
            IntermediateResponseHandler intermediateResponseHandler, SearchResultHandler entryHandler) {
        return delegate.searchAsync(request, intermediateResponseHandler, entryHandler);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
