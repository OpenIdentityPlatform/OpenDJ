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
 *      Copyright 2013 ForgeRock AS.
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
