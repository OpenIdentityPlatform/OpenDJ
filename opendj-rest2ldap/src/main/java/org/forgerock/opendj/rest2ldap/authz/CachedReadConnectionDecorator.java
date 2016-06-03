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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.util.promise.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache entries by intercepting the result of base search requests. Entries in cache are automatically evicted if their
 * DN is involved in a modify/delete operation. This cache is used to prevent multiple read operations on the same DN.
 * This happens frequently when we have to resolve entry references in a collection.
 */
final class CachedReadConnectionDecorator extends AbstractAsynchronousConnectionDecorator {

    private static final Logger logger = LoggerFactory.getLogger(CachedReadConnectionDecorator.class);

    private static final ResultHandler<Response> RESPONSE_LOGGER = new ResultHandler<Response>() {
        @Override
        public void handleResult(final Response response) {
            traceLog(response);
        }
    };

    private static void traceLog(final Object o) {
        if (logger.isTraceEnabled()) {
            logger.trace(o.toString());
        }
    }

    @SuppressWarnings("serial")
    private final Map<DN, CachedRead> cachedReads = new LinkedHashMap<DN, CachedRead>() {
        private static final int MAX_CACHED_ENTRIES = 32;

        @Override
        protected boolean removeEldestEntry(final Map.Entry<DN, CachedRead> eldest) {
            return size() > MAX_CACHED_ENTRIES;
        }
    };

    /** A cached read request - see cachedReads for more information. */
    private static final class CachedRead implements SearchResultHandler, LdapResultHandler<Result> {
        private SearchResultEntry cachedEntry;
        private final String cachedFilterString;

        /** Promise of the pending read operation. @GuardedBy("cachedPromiseLatch"). */
        private LdapPromise<Result> cachedPromise;
        private final CountDownLatch cachedPromiseLatch = new CountDownLatch(1);
        private final SearchRequest cachedRequest;
        private volatile Result cachedResult;
        private final ConcurrentLinkedQueue<SearchResultHandler> waitingResultHandlers = new ConcurrentLinkedQueue<>();

        CachedRead(final SearchRequest request, final SearchResultHandler resultHandler) {
            this.cachedRequest = request;
            this.cachedFilterString = request.getFilter().toString();
            this.waitingResultHandlers.add(resultHandler);
        }

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            cachedEntry = entry;
            return true;
        }

        @Override
        public void handleException(final LdapException exception) {
            handleResult(exception.getResult());
        }

        @Override
        public boolean handleReference(final SearchResultReference reference) {
            // Ignore - should never happen for a base object search.
            return true;
        }

        @Override
        public void handleResult(final Result result) {
            cachedResult = result;
            drainQueue();
        }

        void addResultHandler(final SearchResultHandler resultHandler) {
            // Fast path.
            if (cachedResult != null) {
                invokeResultHandler(resultHandler);
                return;
            }
            // Enqueue and re-check.
            waitingResultHandlers.add(resultHandler);
            if (cachedResult != null) {
                drainQueue();
            }
        }

        LdapPromise<Result> getPromise() {
            // Perform uninterrupted wait since this method is unlikely to block for a long time.
            boolean wasInterrupted = false;
            while (true) {
                try {
                    cachedPromiseLatch.await();
                    if (wasInterrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return cachedPromise;
                } catch (final InterruptedException e) {
                    wasInterrupted = true;
                }
            }
        }

        boolean isMatchingRead(final SearchRequest request) {
            // Cached reads are always base object.
            return request.getScope().equals(SearchScope.BASE_OBJECT)
                    // Filters must match.
                    && request.getFilter().toString().equals(cachedFilterString)
                    // List of requested attributes must match.
                    && request.getAttributes().equals(cachedRequest.getAttributes());
        }

        void setPromise(final LdapPromise<Result> promise) {
            cachedPromise = promise;
            cachedPromiseLatch.countDown();
        }

        private void drainQueue() {
            SearchResultHandler resultHandler;
            while ((resultHandler = waitingResultHandlers.poll()) != null) {
                invokeResultHandler(resultHandler);
            }
        }

        private void invokeResultHandler(final SearchResultHandler searchResultHandler) {
            if (cachedEntry != null) {
                searchResultHandler.handleEntry(cachedEntry);
            }
        }
    }

    CachedReadConnectionDecorator(Connection delegate) {
        super(delegate);
    }

    @Override
    public LdapPromise<BindResult> bindAsync(BindRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        /*
         * Simple brute force implementation in case the bind operation
         * modifies an entry: clear the cachedReads.
         */
        traceLog(request);
        evictAll();
        return delegate.bindAsync(request, intermediateResponseHandler).thenOnResult(RESPONSE_LOGGER);
    }

    private void evictAll() {
        synchronized (cachedReads) {
            cachedReads.clear();
        }
    }

    @Override
    public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(ExtendedRequest<R> request,
            IntermediateResponseHandler intermediateResponseHandler) {
        /*
         * Simple brute force implementation in case the extended
         * operation modifies an entry: clear the cachedReads.
         */
        traceLog(request);
        evictAll();
        return delegate.extendedRequestAsync(request, intermediateResponseHandler).thenOnResult(RESPONSE_LOGGER);
    }

    @Override
    public LdapPromise<Result> modifyAsync(ModifyRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        traceLog(request);
        evict(request.getName());
        return delegate.modifyAsync(request, intermediateResponseHandler).thenOnResult(RESPONSE_LOGGER);
    }

    private void evict(final DN name) {
        synchronized (cachedReads) {
            cachedReads.remove(name);
        }
    }

    @Override
    public LdapPromise<Result> deleteAsync(DeleteRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        traceLog(request);
        // Simple brute force implementation: clear the cachedReads.
        if (request.containsControl(SubtreeDeleteRequestControl.OID)) {
            evictAll();
        } else {
            evict(request.getName());
        }
        return delegate.deleteAsync(request, intermediateResponseHandler).thenOnResult(RESPONSE_LOGGER);
    }

    @Override
    public LdapPromise<Result> modifyDNAsync(ModifyDNRequest request,
            IntermediateResponseHandler intermediateResponseHandler) {
        traceLog(request);
        // Simple brute force implementation: clear the cachedReads.
        evictAll();
        return delegate.modifyDNAsync(request, intermediateResponseHandler);
    }

    @Override
    public LdapPromise<Result> searchAsync(SearchRequest request,
            IntermediateResponseHandler intermediateResponseHandler, SearchResultHandler entryHandler) {
        /*
         * Don't attempt caching if this search is not a read (base
         * object), or if the search request passed in an intermediate
         * response handler.
         */
        traceLog(request);
        if (!request.getScope().equals(SearchScope.BASE_OBJECT) || intermediateResponseHandler != null) {
            return delegate.searchAsync(request, intermediateResponseHandler, entryHandler)
                           .thenOnResult(RESPONSE_LOGGER);
        }

        // This is a read request and a candidate for caching.
        final CachedRead cachedRead;
        synchronized (cachedReads) {
            cachedRead = cachedReads.get(request.getName());
        }
        if (cachedRead != null && cachedRead.isMatchingRead(request)) {
            // The cached read matches this read request.
            cachedRead.addResultHandler(entryHandler);
            return cachedRead.getPromise();
        } else {
            // Cache the read, possibly evicting a non-matching cached read.
            final CachedRead pendingCachedRead = new CachedRead(request, entryHandler);
            synchronized (cachedReads) {
                cachedReads.put(request.getName(), pendingCachedRead);
            }
            final LdapPromise<Result> promise = delegate
                    .searchAsync(request, intermediateResponseHandler, pendingCachedRead)
                    .thenOnResult(pendingCachedRead).thenOnException(pendingCachedRead);
            pendingCachedRead.setPromise(promise);
            return promise.thenOnResult(RESPONSE_LOGGER);
        }
    }
}
