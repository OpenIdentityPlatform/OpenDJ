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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap;

import java.util.Collection;

import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.GenericExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ChangeRecord;
import org.forgerock.opendj.ldif.ChangeRecordVisitor;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.util.Reject;
import org.forgerock.util.Function;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;

/**
 * This class provides a skeletal implementation of the {@code Connection}
 * interface, to minimize the effort required to implement this interface.
 */
public abstract class AbstractConnection implements Connection {

    private static final class SingleEntryHandler implements SearchResultHandler {
        private volatile SearchResultEntry firstEntry;
        private volatile SearchResultReference firstReference;
        private volatile int entryCount;

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            if (firstEntry == null) {
                firstEntry = entry;
            }
            entryCount++;
            return true;
        }

        @Override
        public boolean handleReference(final SearchResultReference reference) {
            if (firstReference == null) {
                firstReference = reference;
            }
            return true;
        }

        /**
         * Filter the provided error in order to transform size limit exceeded
         * error to a client side error, or leave it as is for any other error.
         *
         * @param error
         *            to filter
         * @return provided error in most case, or
         *         <code>ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED</code>
         *         error if provided error is
         *         <code>ResultCode.SIZE_LIMIT_EXCEEDED</code>
         */
        private LdapException filterError(final LdapException error) {
            if (error.getResult().getResultCode().equals(ResultCode.SIZE_LIMIT_EXCEEDED)) {
                return newLdapException(ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
                        ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES_NO_COUNT.get().toString());
            } else {
                return error;
            }
        }

        /**
         * Check for any error related to number of search result at client-side
         * level: no result, too many result, search result reference. This
         * method should be called only after search operation is finished.
         *
         * @return The single search result entry.
         * @throws LdapException
         *             If an error is detected.
         */
        private SearchResultEntry getSingleEntry() throws LdapException {
            if (entryCount == 0) {
                // Did not find any entries.
                throw newLdapException(ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED,
                        ERR_NO_SEARCH_RESULT_ENTRIES.get().toString());
            } else if (entryCount > 1) {
                // Got more entries than expected.
                throw newLdapException(ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
                        ERR_UNEXPECTED_SEARCH_RESULT_ENTRIES.get(entryCount).toString());
            } else if (firstReference != null) {
                // Got an unexpected search result reference.
                throw newLdapException(ResultCode.CLIENT_SIDE_UNEXPECTED_RESULTS_RETURNED,
                        ERR_UNEXPECTED_SEARCH_RESULT_REFERENCES.get(firstReference.getURIs().iterator().next())
                        .toString());
            } else {
                return firstEntry;
            }
        }
    }

    /** Visitor used for processing synchronous change requests. */
    private static final ChangeRecordVisitor<Object, Connection> SYNC_VISITOR =
            new ChangeRecordVisitor<Object, Connection>() {

                @Override
                public Object visitChangeRecord(final Connection p, final AddRequest change) {
                    try {
                        return p.add(change);
                    } catch (final LdapException e) {
                        return e;
                    }
                }

                @Override
                public Object visitChangeRecord(final Connection p, final DeleteRequest change) {
                    try {
                        return p.delete(change);
                    } catch (final LdapException e) {
                        return e;
                    }
                }

                @Override
                public Object visitChangeRecord(final Connection p, final ModifyDNRequest change) {
                    try {
                        return p.modifyDN(change);
                    } catch (final LdapException e) {
                        return e;
                    }
                }

                @Override
                public Object visitChangeRecord(final Connection p, final ModifyRequest change) {
                    try {
                        return p.modify(change);
                    } catch (final LdapException e) {
                        return e;
                    }
                }
            };

    /**
     * Creates a new abstract connection.
     */
    protected AbstractConnection() {
        // No implementation required.
    }

    @Override
    public Result add(final Entry entry) throws LdapException {
        return add(Requests.newAddRequest(entry));
    }

    @Override
    public Result add(final String... ldifLines) throws LdapException {
        return add(Requests.newAddRequest(ldifLines));
    }

    @Override
    public LdapPromise<Result> addAsync(final AddRequest request) {
        return addAsync(request, null);
    }

    @Override
    public Result applyChange(final ChangeRecord request) throws LdapException {
        final Object result = request.accept(SYNC_VISITOR, this);
        if (result instanceof Result) {
            return (Result) result;
        } else {
            throw (LdapException) result;
        }
    }

    @Override
    public LdapPromise<Result> applyChangeAsync(ChangeRecord request) {
        return applyChangeAsync(request, null);
    }

    @Override
    public LdapPromise<Result> applyChangeAsync(final ChangeRecord request,
            final IntermediateResponseHandler intermediateResponseHandler) {
        final ChangeRecordVisitor<LdapPromise<Result>, Connection> visitor =
            new ChangeRecordVisitor<LdapPromise<Result>, Connection>() {

                @Override
                public LdapPromise<Result> visitChangeRecord(final Connection p, final AddRequest change) {
                    return p.addAsync(change, intermediateResponseHandler);
                }

                @Override
                public LdapPromise<Result> visitChangeRecord(final Connection p, final DeleteRequest change) {
                    return p.deleteAsync(change, intermediateResponseHandler);
                }

                @Override
                public LdapPromise<Result> visitChangeRecord(final Connection p, final ModifyDNRequest change) {
                    return p.modifyDNAsync(change, intermediateResponseHandler);
                }

                @Override
                public LdapPromise<Result> visitChangeRecord(final Connection p, final ModifyRequest change) {
                    return p.modifyAsync(change, intermediateResponseHandler);
                }
            };
        return request.accept(visitor, this);
    }

    @Override
    public BindResult bind(final String name, final char[] password) throws LdapException {
        return bind(Requests.newSimpleBindRequest(name, password));
    }

    @Override
    public LdapPromise<BindResult> bindAsync(final BindRequest request) {
        return bindAsync(request, null);
    }

    @Override
    public void close() {
        close(Requests.newUnbindRequest(), null);
    }

    @Override
    public CompareResult compare(final String name, final String attributeDescription, final String assertionValue)
            throws LdapException {
        return compare(Requests.newCompareRequest(name, attributeDescription, assertionValue));
    }

    @Override
    public LdapPromise<CompareResult> compareAsync(final CompareRequest request) {
        return compareAsync(request, null);
    }

    @Override
    public Result delete(final String name) throws LdapException {
        return delete(Requests.newDeleteRequest(name));
    }

    @Override
    public LdapPromise<Result> deleteAsync(final DeleteRequest request) {
        return deleteAsync(request, null);
    }

    @Override
    public Result deleteSubtree(final String name) throws LdapException {
        return delete(Requests.newDeleteRequest(name).addControl(SubtreeDeleteRequestControl.newControl(true)));
    }

    @Override
    public <R extends ExtendedResult> R extendedRequest(final ExtendedRequest<R> request) throws LdapException {
        return extendedRequest(request, null);
    }

    @Override
    public GenericExtendedResult extendedRequest(final String requestName, final ByteString requestValue)
            throws LdapException {
        return extendedRequest(Requests.newGenericExtendedRequest(requestName, requestValue));
    }

    @Override
    public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request) {
        return extendedRequestAsync(request, null);
    }

    @Override
    public Result modify(final String... ldifLines) throws LdapException {
        return modify(Requests.newModifyRequest(ldifLines));
    }

    @Override
    public LdapPromise<Result> modifyAsync(final ModifyRequest request) {
        return modifyAsync(request, null);
    }

    @Override
    public Result modifyDN(final String name, final String newRDN) throws LdapException {
        return modifyDN(Requests.newModifyDNRequest(name, newRDN));
    }

    @Override
    public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request) {
        return modifyDNAsync(request, null);
    }

    @Override
    public SearchResultEntry readEntry(final DN baseObject, final String... attributeDescriptions)
            throws LdapException {
        final SearchRequest request =
            Requests.newSingleEntrySearchRequest(baseObject, SearchScope.BASE_OBJECT, Filter.objectClassPresent(),
                attributeDescriptions);
        return searchSingleEntry(request);
    }

    @Override
    public SearchResultEntry readEntry(final String baseObject, final String... attributeDescriptions)
            throws LdapException {
        return readEntry(DN.valueOf(baseObject), attributeDescriptions);
    }

    @Override
    public LdapPromise<SearchResultEntry> readEntryAsync(final DN name,
            final Collection<String> attributeDescriptions) {
        final SearchRequest request = Requests.newSingleEntrySearchRequest(name, SearchScope.BASE_OBJECT,
                Filter.objectClassPresent());
        if (attributeDescriptions != null) {
            request.getAttributes().addAll(attributeDescriptions);
        }
        return searchSingleEntryAsync(request);
    }

    @Override
    public ConnectionEntryReader search(final SearchRequest request) {
        return new ConnectionEntryReader(this, request);
    }

    @Override
    public Result search(final SearchRequest request, final Collection<? super SearchResultEntry> entries)
            throws LdapException {
        return search(request, entries, null);
    }

    @Override
    public Result search(final SearchRequest request, final Collection<? super SearchResultEntry> entries,
        final Collection<? super SearchResultReference> references) throws LdapException {
        Reject.ifNull(request, entries);
        // FIXME: does this need to be thread safe?
        final SearchResultHandler handler = new SearchResultHandler() {
            @Override
            public boolean handleEntry(final SearchResultEntry entry) {
                entries.add(entry);
                return true;
            }

            @Override
            public boolean handleReference(final SearchResultReference reference) {
                if (references != null) {
                    references.add(reference);
                }
                return true;
            }
        };

        return search(request, handler);
    }

    @Override
    public ConnectionEntryReader search(final String baseObject, final SearchScope scope, final String filter,
        final String... attributeDescriptions) {
        return search(newSearchRequest(baseObject, scope, filter, attributeDescriptions));
    }

    @Override
    public LdapPromise<Result> searchAsync(final SearchRequest request, final SearchResultHandler resultHandler) {
        return searchAsync(request, null, resultHandler);
    }

    @Override
    public SearchResultEntry searchSingleEntry(final SearchRequest request) throws LdapException {
        final SingleEntryHandler handler = new SingleEntryHandler();
        try {
            search(enforceSingleEntrySearchRequest(request), handler);
            return handler.getSingleEntry();
        } catch (final LdapException e) {
            throw handler.filterError(e);
        }
    }

    @Override
    public SearchResultEntry searchSingleEntry(final String baseObject, final SearchScope scope, final String filter,
        final String... attributeDescriptions) throws LdapException {
        final SearchRequest request =
            Requests.newSingleEntrySearchRequest(baseObject, scope, filter, attributeDescriptions);
        return searchSingleEntry(request);
    }

    @Override
    public LdapPromise<SearchResultEntry> searchSingleEntryAsync(final SearchRequest request) {
        final SingleEntryHandler handler = new SingleEntryHandler();
        return asPromise(searchAsync(enforceSingleEntrySearchRequest(request), handler).then(
                new Function<Result, SearchResultEntry, LdapException>() {
                    @Override
                    public SearchResultEntry apply(final Result value) throws LdapException {
                        return handler.getSingleEntry();
                    }
                }, new Function<LdapException, SearchResultEntry, LdapException>() {
                    @Override
                    public SearchResultEntry apply(final LdapException error) throws LdapException {
                        throw handler.filterError(error);
                    }
                }));
    }

    /**
     * Ensure that a single entry search request is returned, based on provided request.
     *
     * @param request
     *            to be checked
     * @return a single entry search request, equal to or based on the provided request
     */
    private SearchRequest enforceSingleEntrySearchRequest(final SearchRequest request) {
        if (request.isSingleEntrySearch()) {
            return request;
        } else {
            return Requests.copyOfSearchRequest(request).setSizeLimit(1);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sub-classes should provide an implementation which returns an appropriate
     * description of the connection which may be used for debugging purposes.
     * </p>
     */
    @Override
    public abstract String toString();

}
