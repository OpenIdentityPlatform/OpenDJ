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
 * Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import java.io.IOException;

import org.forgerock.opendj.io.LDAPMessageHandler;
import org.forgerock.opendj.io.LDAPReader;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Base class for LDAP-enabled filter.
 * <p>
 * Provides a common {@code handleRead()} method for both client and server
 * filters.
 */
abstract class LDAPBaseFilter extends BaseFilter {

    /**
     * The maximum BER element size, or <code>0</code> to indicate that there is
     * no limit.
     */
    final int maxASN1ElementSize;

    /**
     * Allow to control how to decode requests and responses.
     */
    final DecodeOptions decodeOptions;

    /**
     * Creates a filter with provided decode options and max size of
     * ASN1 element.
     *
     * @param options
     *            control how to decode requests and responses
     * @param maxASN1ElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     */
    LDAPBaseFilter(final DecodeOptions options, final int maxASN1ElementSize) {
        this.decodeOptions = options;
        this.maxASN1ElementSize = maxASN1ElementSize;
    }

    @Override
    public final NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final LDAPBaseHandler handler = getLDAPHandler(ctx);
        final LDAPReader<ASN1BufferReader> reader = handler.getReader();
        final ASN1BufferReader asn1Reader = reader.getASN1Reader();
        final Buffer buffer = (Buffer) ctx.getMessage();

        asn1Reader.appendBytesRead(buffer);
        try {
            while (reader.hasMessageAvailable()) {
                reader.readMessage(handler);
            }
        } catch (IOException e) {
            handleReadException(ctx, e);
            throw e;
        } finally {
            asn1Reader.disposeBytesRead();
        }

        return ctx.getStopAction();
    }

    /**
     * Handle an exception occuring during a read within the
     * {@code handleRead()} method.
     *
     * @param ctx
     *            context when reading
     * @param e
     *            exception occuring while reading
     */
    abstract void handleReadException(FilterChainContext ctx, IOException e);

    /**
     * Interface for the {@code LDAPMessageHandler} used in the filter, that
     * must be able to retrieve a Grizzly reader.
     */
    interface LDAPBaseHandler extends LDAPMessageHandler {
        /**
         * Returns the LDAP reader for this handler.
         * @return the reader
         */
        LDAPReader<ASN1BufferReader> getReader();
    }

    /**
     * Returns the LDAP message handler associated to the underlying connection
     * of the provided context.
     * <p>
     * If no handler exists yet for the underlying connection, a new one is
     * created and recorded for the connection.
     *
     * @param ctx
     *            current filter chain context
     * @return the response handler associated to the connection, which can be a
     *         new one if no handler have been created yet
     */
    abstract LDAPBaseHandler getLDAPHandler(final FilterChainContext ctx);

}
