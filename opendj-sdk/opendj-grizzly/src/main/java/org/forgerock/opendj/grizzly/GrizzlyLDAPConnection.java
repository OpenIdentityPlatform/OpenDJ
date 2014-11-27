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
 *      Copyright 2014 ForgeRock AS
 */

package org.forgerock.opendj.grizzly;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.TrustManagers;
import org.forgerock.opendj.ldap.requests.AbandonRequest;
import org.forgerock.opendj.ldap.requests.BindClient;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.GenericBindRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.spi.AbstractLdapConnectionImpl;
import org.forgerock.opendj.ldap.spi.BindResultLdapPromiseImpl;
import org.forgerock.opendj.ldap.spi.ResultLdapPromiseImpl;
import org.forgerock.util.Reject;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.spi.LdapPromises.*;

final class GrizzlyLDAPConnection extends AbstractLdapConnectionImpl<GrizzlyLDAPConnectionFactory> {
    /**
     * A dummy SSL client engine configurator as SSLFilter only needs client
     * config. This prevents Grizzly from needlessly using JVM defaults which
     * may be incorrectly configured.
     */
    private static final SSLEngineConfigurator DUMMY_SSL_ENGINE_CONFIGURATOR;
    static {
        try {
            DUMMY_SSL_ENGINE_CONFIGURATOR = new SSLEngineConfigurator(new SSLContextBuilder().setTrustManager(
                    TrustManagers.distrustAll()).getSSLContext());
        } catch (GeneralSecurityException e) {
            // This should never happen.
            throw new IllegalStateException("Unable to create Dummy SSL Engine Configurator", e);
        }
    }

    private final org.glassfish.grizzly.Connection<?> connection;

    @SuppressWarnings("rawtypes")
    public GrizzlyLDAPConnection(final org.glassfish.grizzly.Connection connection,
            final GrizzlyLDAPConnectionFactory attachedFactory) {
        super(attachedFactory);
        this.connection = connection;
    }

    @Override
    protected LdapPromise<Void> abandonAsync0(final int messageID, final AbandonRequest request) {
        final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
        try {
            writer.writeAbandonRequest(messageID, request);
            connection.write(writer.getASN1Writer().getBuffer(), null);
            return newSuccessfulLdapPromise((Void) null, messageID);
        } catch (final IOException e) {
            return newFailedLdapPromise(newLdapException(adaptRequestIOException(e)));
        } finally {
            GrizzlyUtils.recycleWriter(writer);
        }
    }

    @Override
    protected void bindAsync0(final int messageID, final BindRequest request, final BindClient bindClient,
            final IntermediateResponseHandler intermediateResponseHandler) throws LdapException {
        checkConnectionIsValid();
        final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
        try {
            // Use the bind client to get the initial request instead of
            // using the bind request passed to this method.
            final GenericBindRequest initialRequest = bindClient.nextBindRequest();
            writer.writeBindRequest(messageID, 3, initialRequest);
            connection.write(writer.getASN1Writer().getBuffer(), null);
        } catch (final IOException e) {
            throw newLdapException(adaptRequestIOException(e));
        } finally {
            GrizzlyUtils.recycleWriter(writer);
        }
    }

    @Override
    protected void close0(final int messageID, UnbindRequest unbindRequest, String reason) {
        Reject.ifNull(unbindRequest);
        // This is the final client initiated close then release the connection
        // and release resources.
        final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
        try {
            writer.writeUnbindRequest(messageID, unbindRequest);
            connection.write(writer.getASN1Writer().getBuffer(), null);
        } catch (final Exception ignore) {
            /*
             * Underlying channel probably blown up. Ignore all errors,
             * including possibly runtime exceptions (see OPENDJ-672).
             */
        } finally {
            GrizzlyUtils.recycleWriter(writer);
        }
        getFactory().getTimeoutChecker().removeListener(this);
        connection.closeSilently();
    }

    @Override
    protected <R extends Request> void writeRequest(final int messageID, final R request,
            final IntermediateResponseHandler intermediateResponseHandler, final RequestWriter<R> requestWriter) {
        final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter();
        try {
            requestWriter.writeRequest(messageID, writer, request);
            connection.write(writer.getASN1Writer().getBuffer(), null);
        } catch (final IOException e) {
            removePendingResult(messageID).setResultOrError(adaptRequestIOException(e));
        } finally {
            GrizzlyUtils.recycleWriter(writer);
        }
    }

    /**
     * Installs a new Grizzly filter (e.g. SSL/SASL) beneath the top-level LDAP
     * filter.
     *
     * @param filter
     *            The filter to be installed.
     */
    void installFilter(final Filter filter) {
        synchronized (state) {
            GrizzlyUtils.addFilterToConnection(filter, connection);
        }
    }

    @Override
    protected boolean isTLSEnabled() {
        synchronized (state) {
            final FilterChain currentFilterChain = (FilterChain) connection.getProcessor();
            for (final Filter filter : currentFilterChain) {
                if (filter instanceof SSLFilter) {
                    return true;
                }
            }
            return false;
        }
    }

    void startTLS(final SSLContext sslContext, final List<String> protocols, final List<String> cipherSuites,
            final CompletionHandler<SSLEngine> completionHandler) throws IOException {
        synchronized (state) {
            if (isTLSEnabled()) {
                throw new IllegalStateException("TLS already enabled");
            }

            final SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(sslContext, true, false,
                    false);
            sslEngineConfigurator.setEnabledProtocols(protocols.isEmpty() ? null : protocols
                    .toArray(new String[protocols.size()]));
            sslEngineConfigurator.setEnabledCipherSuites(cipherSuites.isEmpty() ? null : cipherSuites
                    .toArray(new String[cipherSuites.size()]));
            final SSLFilter sslFilter = new SSLFilter(DUMMY_SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
            installFilter(sslFilter);
            sslFilter.handshake(connection, completionHandler);
        }
    }

    private Result adaptRequestIOException(final IOException e) {
        // FIXME: what other sort of IOExceptions can be thrown?
        // FIXME: Is this the best result code?
        final Result errorResult = Responses.newResult(ResultCode.CLIENT_SIDE_ENCODING_ERROR).setCause(e);
        connectionErrorOccurred(false, errorResult);
        return errorResult;
    }

    @Override
    protected ResultLdapPromiseImpl<?, ?> getPendingResult(int messageID) {
        return super.getPendingResult(messageID);
    };

    @Override
    protected <R extends Request, S extends Result> ResultLdapPromiseImpl<R, S> removePendingResult(
            final Integer messageID) {
        return super.removePendingResult(messageID);
    }

    @Override
    protected void connectionErrorOccurred(boolean isDisconnectNotification, Result reason) {
        super.connectionErrorOccurred(isDisconnectNotification, reason);
    }

    @Override
    protected void handleUnsolicitedNotification(final ExtendedResult notification) {
        super.handleUnsolicitedNotification(notification);
    }

    int continuePendingBindRequest(BindResultLdapPromiseImpl promise)
            throws LdapException {
        final int newMsgID = newMessageID();
        checkConnectionIsValid();
        addPendingResult(newMsgID, promise);

        return newMsgID;
    }
}
