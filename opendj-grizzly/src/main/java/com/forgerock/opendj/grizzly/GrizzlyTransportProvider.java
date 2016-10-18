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
 * Copyright 2013-2016 ForgeRock AS.
 */
package com.forgerock.opendj.grizzly;

import static com.forgerock.reactive.RxJavaStreams.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;

import org.forgerock.opendj.grizzly.GrizzlyLDAPConnectionFactory;
import org.forgerock.opendj.grizzly.GrizzlyLDAPListener;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.AbstractLDAPMessageHandler;
import org.forgerock.opendj.io.LDAP;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.ldap.CommonLDAPOptions;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPClientContext.DisconnectListener;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ServerConnection;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;
import org.forgerock.opendj.ldap.spi.LDAPListenerImpl;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRawMessage;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.util.Function;
import org.forgerock.util.Options;

import com.forgerock.reactive.ReactiveHandler;
import com.forgerock.reactive.Single;
import com.forgerock.reactive.Stream;

import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableEmitter.BackpressureMode;
import io.reactivex.FlowableOnSubscribe;

/**
 * Grizzly transport provider implementation.
 */
public final class GrizzlyTransportProvider implements TransportProvider {

    @Override
    public LDAPConnectionFactoryImpl getLDAPConnectionFactory(String host, int port, Options options) {
        return new GrizzlyLDAPConnectionFactory(host, port, options);
    }

    @Override
    public LDAPListenerImpl getLDAPListener(final Set<? extends SocketAddress> addresses,
            final ServerConnectionFactory<LDAPClientContext, Integer> factory, final Options options)
            throws IOException {
        return new GrizzlyLDAPListener(addresses, options,
                new Function<LDAPClientContext,
                             ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>>,
                             LdapException>() {
                    @Override
                    public ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>> apply(
                            final LDAPClientContext clientContext) throws LdapException {
                        return newHandler(clientContext, factory, options);
                    }
                });
    }

    @Override
    public String getName() {
        return "Grizzly";
    }

    private ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>> newHandler(
            final LDAPClientContext clientContext, final ServerConnectionFactory<LDAPClientContext, Integer> factory,
            final Options options) throws LdapException {
        final ServerConnection<Integer> serverConnection = factory.handleAccept(clientContext);
        final ServerConnectionAdaptor<Integer> adapter = new ServerConnectionAdaptor<>(serverConnection);
        clientContext.onDisconnect(new DisconnectListener() {
            @Override
            public void connectionDisconnected(LDAPClientContext context, ResultCode resultCode, String message) {
                serverConnection.handleConnectionDisconnected(resultCode, message);
            }
        });
        return new ReactiveHandler<LDAPClientContext, LdapRawMessage, Stream<Response>>() {
            @Override
            public Single<Stream<Response>> handle(final LDAPClientContext context,
                    final LdapRawMessage rawRequest) throws Exception {
                final LDAPReader<ASN1Reader> reader = LDAP.getReader(rawRequest.getContent(),
                        options.get(CommonLDAPOptions.LDAP_DECODE_OPTIONS));
                return singleFrom(streamFromPublisher(Flowable.create(new FlowableOnSubscribe<Response>() {
                    @Override
                    public void subscribe(final FlowableEmitter<Response> emitter) throws Exception {
                        reader.readMessage(new AbstractLDAPMessageHandler() {
                            @Override
                            public void abandonRequest(int messageID, AbandonRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleAbandon(messageID, request, emitter);
                            }

                            @Override
                            public void addRequest(int messageID, AddRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleAdd(messageID, request, emitter);
                            }

                            @Override
                            public void deleteRequest(final int messageID, final DeleteRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleDelete(messageID, request, emitter);
                            }

                            @Override
                            public void bindRequest(int messageID, int version, GenericBindRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleBind(messageID, version, request, emitter);
                            }

                            @Override
                            public void compareRequest(int messageID, CompareRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleCompare(messageID, request, emitter);
                            }

                            @Override
                            public <R extends ExtendedResult> void extendedRequest(int messageID,
                                    ExtendedRequest<R> request) throws DecodeException, IOException {
                                adapter.handleExtendedRequest(messageID, request, emitter);
                            }

                            @Override
                            public void modifyDNRequest(int messageID, ModifyDNRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleModifyDN(messageID, request, emitter);
                            }

                            @Override
                            public void modifyRequest(int messageID, ModifyRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleModify(messageID, request, emitter);
                            }

                            @Override
                            public void searchRequest(int messageID, SearchRequest request)
                                    throws DecodeException, IOException {
                                adapter.handleSearch(messageID, request, emitter);
                            }

                            @Override
                            public void unbindRequest(int messageID, UnbindRequest request)
                                    throws DecodeException, IOException {
                                serverConnection.handleConnectionClosed(messageID, request);
                            }
                        });
                        emitter.onComplete();
                    }
                }, BackpressureMode.ERROR)));
            }
        };
    }
}
