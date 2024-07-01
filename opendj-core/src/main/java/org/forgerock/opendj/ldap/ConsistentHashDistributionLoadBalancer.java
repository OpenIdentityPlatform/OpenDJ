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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static org.forgerock.opendj.ldap.Connections.dnOfRequest;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.ResultCode.CLIENT_SIDE_USER_CANCELLED;
import static org.forgerock.opendj.ldap.ResultCode.PROTOCOL_ERROR;
import static org.forgerock.opendj.ldap.ResultCode.UNWILLING_TO_PERFORM;
import static org.forgerock.opendj.ldap.SearchScope.SUBORDINATES;
import static org.forgerock.opendj.ldap.requests.Requests.copyOfSearchRequest;
import static org.forgerock.opendj.ldap.spi.LdapPromises.newFailedLdapPromise;
import static org.forgerock.util.Utils.closeSilently;
import static org.forgerock.util.Utils.joinAsString;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.promise.Promises.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.forgerock.opendj.ldap.controls.VirtualListViewRequestControl;
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
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.spi.ConnectionState;
import org.forgerock.opendj.ldap.spi.LdapPromises;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;
import org.forgerock.util.promise.RuntimeExceptionHandler;

/**
 * A simple distribution algorithm which distributes requests equally across a fixed number of partitions based on a
 * hash of the request's target DN. See {@link Connections#newFixedSizeDistributionLoadBalancer}.
 */
final class ConsistentHashDistributionLoadBalancer implements ConnectionFactory {
    private static final String NAME = ConsistentHashDistributionLoadBalancer.class.getSimpleName();
    private final ConsistentHashMap<? extends ConnectionFactory> partitions;
    private final DN partitionBaseDN;

    ConsistentHashDistributionLoadBalancer(final DN partitionBaseDN,
                                           final ConsistentHashMap<? extends ConnectionFactory> partitions) {
        this.partitionBaseDN = partitionBaseDN;
        this.partitions = partitions;
    }

    @Override
    public final void close() {
        closeSilently(partitions.getAll());
    }

    @Override
    public final String toString() {
        return NAME + '(' + joinAsString(",", partitions) + ')';
    }

    @Override
    public final Connection getConnection() throws LdapException {
        return new ConnectionImpl();
    }

    @Override
    public final Promise<Connection, LdapException> getConnectionAsync() {
        return newResultPromise((Connection) new ConnectionImpl());
    }

    private class ConnectionImpl extends AbstractAsynchronousConnection {
        private final ConnectionState state = new ConnectionState();

        @Override
        public String toString() {
            return NAME + "Connection";
        }

        @Override
        public LdapPromise<Void> abandonAsync(final AbandonRequest request) {
            // We cannot possibly route these correctly, so just drop them.
            return LdapPromises.newSuccessfulLdapPromise(null);
        }

        @Override
        public LdapPromise<Result> addAsync(final AddRequest request,
                                            final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionFactory partition = getPartition(request.getName());
            return connectAndSendRequest(partition, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.addAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public void addConnectionEventListener(final ConnectionEventListener listener) {
            state.addConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<BindResult> bindAsync(final BindRequest request,
                                                 final IntermediateResponseHandler intermediateResponseHandler) {
            final DN dn = dnOfRequest(request);
            if (dn == null) {
                return newFailedLdapPromise(newLdapException(UNWILLING_TO_PERFORM,
                                                             DISTRIBUTION_UNRESOLVABLE_PARTITION_ID_FOR_BIND.get()));
            }
            final ConnectionFactory partition = getPartition(dn);
            return connectAndSendRequest(partition, new AsyncFunction<Connection, BindResult, LdapException>() {
                @Override
                public Promise<BindResult, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.bindAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public void close(final UnbindRequest request, final String reason) {
            state.notifyConnectionClosed();
        }

        @Override
        public LdapPromise<CompareResult> compareAsync(final CompareRequest request,
                                                       final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionFactory partition = getPartition(request.getName());
            return connectAndSendRequest(partition, new AsyncFunction<Connection, CompareResult, LdapException>() {
                @Override
                public Promise<CompareResult, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.compareAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public LdapPromise<Result> deleteAsync(final DeleteRequest request,
                                               final IntermediateResponseHandler intermediateResponseHandler) {
            // We could reject requests that attempt to delete entries superior to the partition base DN. However,
            // this is really the responsibility of the backend servers to enforce. In some cases such requests
            // should be allowed, e.g. if the partitions are all empty.
            final ConnectionFactory partition = getPartition(request.getName());
            return connectAndSendRequest(partition, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.deleteAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public <R extends ExtendedResult> LdapPromise<R> extendedRequestAsync(final ExtendedRequest<R> request,
                                                                              final IntermediateResponseHandler
                                                                                      intermediateResponseHandler) {
            final DN dn = dnOfRequest(request);
            if (dn == null) {
                return newFailedLdapPromise(newLdapException(UNWILLING_TO_PERFORM,
                                                             DISTRIBUTION_UNRESOLVABLE_PARTITION_ID_FOR_EXT_OP.get()));
            }
            final ConnectionFactory partition = getPartition(dn);
            return connectAndSendRequest(partition, new AsyncFunction<Connection, R, LdapException>() {
                @Override
                public Promise<R, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.extendedRequestAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public boolean isClosed() {
            return state.isClosed();
        }

        @Override
        public boolean isValid() {
            return state.isValid();
        }

        @Override
        public LdapPromise<Result> modifyAsync(final ModifyRequest request,
                                               final IntermediateResponseHandler intermediateResponseHandler) {
            final ConnectionFactory partition = getPartition(request.getName());
            return connectAndSendRequest(partition, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.modifyAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public LdapPromise<Result> modifyDNAsync(final ModifyDNRequest request,
                                                 final IntermediateResponseHandler intermediateResponseHandler) {
            final DN oldDN = request.getName();
            final ConnectionFactory oldPartition = getPartition(oldDN);

            final DN newParent = request.getNewSuperior() != null ? request.getNewSuperior() : oldDN.parent();
            final DN newDN = newParent.child(request.getNewRDN());
            final ConnectionFactory newPartition = getPartition(newDN);

            // Acceptable if the request is completely outside of the partitions or entirely within a single partition.
            if (oldPartition != newPartition) {
                return unwillingToPerform(DISTRIBUTION_MODDN_SPANS_MULTIPLE_PARTITIONS.get());
            }

            return connectAndSendRequest(oldPartition, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.modifyDNAsync(request, intermediateResponseHandler);
                }
            });
        }

        @Override
        public void removeConnectionEventListener(final ConnectionEventListener listener) {
            state.removeConnectionEventListener(listener);
        }

        @Override
        public LdapPromise<Result> searchAsync(final SearchRequest request,
                                               final IntermediateResponseHandler intermediateResponseHandler,
                                               final SearchResultHandler entryHandler) {
            // Broadcast if the search targets an entry which is superior to the base DN and has a scope which
            // reaches into the partitions.
            final DN dn = request.getName();
            switch (request.getScope().asEnum()) {
            case BASE_OBJECT:
                // Request can always be handled by a single partition.
                return searchSinglePartition(request, intermediateResponseHandler, entryHandler);
            case SINGLE_LEVEL:
                // Needs broadcasting if the search DN is equal to the partition DN.
                if (dn.equals(partitionBaseDN)) {
                    return searchAllPartitions(request, intermediateResponseHandler, entryHandler);
                } else {
                    return searchSinglePartition(request, intermediateResponseHandler, entryHandler);
                }
            case WHOLE_SUBTREE:
                // Needs broadcasting if the search DN is superior or equal to the partition DN.
                if (dn.isSuperiorOrEqualTo(partitionBaseDN)) {
                    // Split into primary search against search DN and secondary searches against partition DN.
                    return splitAndSearchAllPartitions(request, intermediateResponseHandler, entryHandler);
                } else {
                    return searchSinglePartition(request, intermediateResponseHandler, entryHandler);
                }
            case SUBORDINATES:
                // Needs broadcasting if the search DN is superior or equal to the partition DN.
                if (dn.equals(partitionBaseDN)) {
                    return searchAllPartitions(request, intermediateResponseHandler, entryHandler);
                } else if (dn.isSuperiorOrEqualTo(partitionBaseDN)) {
                    // Split into primary search against search DN and secondary searches against partition DN.
                    return splitAndSearchAllPartitions(request, intermediateResponseHandler, entryHandler);
                } else {
                    return searchSinglePartition(request, intermediateResponseHandler, entryHandler);
                }
            default: // UNKNOWN
                return unwillingToPerform(DISTRIBUTION_UNSUPPORTED_SEARCH_SCOPE.get(request.getScope().intValue()));
            }
        }

        /**
         * Forwards a search request to each partition. The search request is scoped such that duplicate results are
         * not possible.
         */
        private LdapPromise<Result> searchAllPartitions(final SearchRequest request,
                                                        final IntermediateResponseHandler irh,
                                                        final SearchResultHandler srh) {
            return broadcastSearch(request, request, irh, srh);
        }

        /**
         * In order to avoid duplicates, search one partition using the request's DN and the remaining partitions using
         * the partition base DN with scope "SUBORDINATES".
         * <p>
         * TODO: may return results that are not in hierarchical order.
         */
        private LdapPromise<Result> splitAndSearchAllPartitions(final SearchRequest primarySearch,
                                                                final IntermediateResponseHandler irh,
                                                                final SearchResultHandler srh) {
            final SearchRequest secondarySearch =
                    copyOfSearchRequest(primarySearch).setName(partitionBaseDN).setScope(SUBORDINATES);
            return broadcastSearch(primarySearch, secondarySearch, irh, srh);
        }

        /**
         * Take care to ensure that all searches are cancelled when one of them fails (important if the
         * searches are persistent).
         */
        private LdapPromise<Result> broadcastSearch(final SearchRequest primarySearch,
                                                    final SearchRequest secondarySearch,
                                                    final IntermediateResponseHandler irh,
                                                    final SearchResultHandler srh) {
            // First reject requests that contain controls that we do not support when broadcast.
            if (primarySearch.containsControl(VirtualListViewRequestControl.OID)) {
                return unwillingToPerform(DISTRIBUTION_VLV_CONTROL_NOT_SUPPORTED.get());
            } else if (primarySearch.containsControl(SimplePagedResultsControl.OID)) {
                return unwillingToPerform(DISTRIBUTION_SPR_CONTROL_NOT_SUPPORTED.get());
            } else if (primarySearch.containsControl(ServerSideSortRequestControl.OID)) {
                return unwillingToPerform(DISTRIBUTION_SSS_CONTROL_NOT_SUPPORTED.get());
            } else if (primarySearch.containsControl(PersistentSearchRequestControl.OID)) {
                // Persistent searches return results in two phases: the initial search results and then the changes.
                // Unfortunately, there's no way to determine when the first phase has completed, so it's not
                // possible to prevent the two phases from being interleaved when broadcast.
                try {
                    final PersistentSearchRequestControl control =
                            primarySearch.getControl(PersistentSearchRequestControl.DECODER, new DecodeOptions());
                    if (!control.isChangesOnly()) {
                        return unwillingToPerform(DISTRIBUTION_PSEARCH_CONTROL_NOT_SUPPORTED.get());
                    }
                } catch (final DecodeException e) {
                    return newFailedLdapPromise(newLdapException(PROTOCOL_ERROR, e.getMessage(), e));
                }
            }

            final List<Promise<Result, LdapException>> promises = new ArrayList<>(partitions.size());

            // Launch all searches with the primary search targeting a random partition.
            final ConnectionFactory primaryPartition = getPartition(primarySearch.getName());
            final IntermediateResponseHandler sirh = synchronize(irh);
            final SearchResultHandler ssrh = synchronize(srh);
            for (final ConnectionFactory partition : partitions.getAll()) {
                final SearchRequest searchRequest = partition == primaryPartition ? primarySearch : secondarySearch;
                promises.add(searchSinglePartition(searchRequest, sirh, ssrh, partition));
            }

            // FIXME: chained PromiseImpl and Promises.when() don't chain cancellation requests.
            final PromiseImpl<Result, LdapException> reducedPromise = new PromiseImpl<Result, LdapException>() {
                @Override
                protected LdapException tryCancel(final boolean mayInterruptIfRunning) {
                    for (Promise<Result, LdapException> promise : promises) {
                        promise.cancel(mayInterruptIfRunning);
                    }
                    return newLdapException(CLIENT_SIDE_USER_CANCELLED);
                }
            };
            when(promises).thenOnResult(new ResultHandler<List<Result>>() {
                @Override
                public void handleResult(final List<Result> results) {
                    // TODO: Depending on controls we may want to merge these results in some way.
                    reducedPromise.handleResult(results.get(0));
                }
            }).thenOnException(new ExceptionHandler<LdapException>() {
                @Override
                public void handleException(final LdapException exception) {
                    reducedPromise.handleException(exception);
                }
            }).thenOnRuntimeException(new RuntimeExceptionHandler() {
                @Override
                public void handleRuntimeException(final RuntimeException exception) {
                    reducedPromise.handleRuntimeException(exception);
                }
            }).thenFinally(new Runnable() {
                @Override
                public void run() {
                    // Ensure that any remaining searches are terminated.
                    for (Promise<Result, LdapException> promise : promises) {
                        promise.cancel(true);
                    }
                }
            });

            return LdapPromises.asPromise(reducedPromise);
        }

        private LdapPromise<Result> unwillingToPerform(final LocalizableMessage msg) {
            return newFailedLdapPromise(newLdapException(UNWILLING_TO_PERFORM, msg));
        }

        /**
         * Forwards a search request to a single partition based on the search's base DN. If the DN does not target a
         * specific partition then a random partition will be selected.
         */
        private LdapPromise<Result> searchSinglePartition(final SearchRequest request,
                                                          final IntermediateResponseHandler irh,
                                                          final SearchResultHandler srh) {
            final ConnectionFactory partition = getPartition(request.getName());
            return searchSinglePartition(request, irh, srh, partition);
        }

        private LdapPromise<Result> searchSinglePartition(final SearchRequest request,
                                                          final IntermediateResponseHandler irh,
                                                          final SearchResultHandler srh,
                                                          final ConnectionFactory partition) {
            return connectAndSendRequest(partition, new AsyncFunction<Connection, Result, LdapException>() {
                @Override
                public Promise<Result, LdapException> apply(final Connection connection) throws LdapException {
                    return connection.searchAsync(request, irh, srh);
                }
            });
        }

        /** Returns a search result handler that ensures that only one response is processed at a time. */
        private SearchResultHandler synchronize(final SearchResultHandler srh) {
            return new SearchResultHandler() {
                @Override
                public synchronized boolean handleEntry(final SearchResultEntry entry) {
                    return srh.handleEntry(entry);
                }

                @Override
                public synchronized boolean handleReference(final SearchResultReference reference) {
                    return srh.handleReference(reference);
                }
            };
        }

        /** Returns an intermediate response handler that ensures that only one response is processed at a time. */
        private IntermediateResponseHandler synchronize(final IntermediateResponseHandler irh) {
            return new IntermediateResponseHandler() {
                @Override
                public synchronized boolean handleIntermediateResponse(final IntermediateResponse response) {
                    return irh.handleIntermediateResponse(response);
                }
            };
        }

        /**
         * Returns the partition for requests having the provided DN. If the DN is not associated with a specific
         * partition then a random partition will be returned.
         */
        private ConnectionFactory getPartition(final DN dn) {
            final DN partitionDN = getPartitionDN(dn);
            return partitions.get((partitionDN != null ? partitionDN : dn).toNormalizedUrlSafeString());
        }

        /**
         * Returns the DN which is a child of the partition base DN and which is equal to or superior to the provided
         * DN, otherwise {@code null}.
         */
        private DN getPartitionDN(final DN dn) {
            final int depthBelowBaseDN = dn.size() - partitionBaseDN.size();
            if (depthBelowBaseDN > 0 && dn.isSubordinateOrEqualTo(partitionBaseDN)) {
                return dn.parent(depthBelowBaseDN - 1);
            }
            return null;
        }

        private <R> LdapPromise<R> connectAndSendRequest(final ConnectionFactory partition,
                                                         final AsyncFunction<Connection, R, LdapException> doRequest) {
            if (state.isClosed()) {
                throw new IllegalStateException("Connection is already closed");
            }
            final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
            return connectAsync(partition).thenOnResult(new ResultHandler<Connection>() {
                @Override
                public void handleResult(final Connection connection) {
                    connectionHolder.set(connection);
                }
                // FIXME: how do we support cancellation of the request?
            }).thenAsync(doRequest).thenFinally(new Runnable() {
                @Override
                public void run() {
                    closeSilently(connectionHolder.get());
                }
            });
        }

        private LdapPromise<Connection> connectAsync(final ConnectionFactory partition) {
            return LdapPromises.asPromise(partition.getConnectionAsync()
                                                   .thenOnException(new ExceptionHandler<LdapException>() {
                                                       @Override
                                                       public void handleException(final LdapException e) {
                                                           state.notifyConnectionError(false, e);
                                                       }
                                                   }));
        }
    }
}
