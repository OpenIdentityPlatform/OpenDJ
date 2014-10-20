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
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */

package org.forgerock.opendj.grizzly;

import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.LdapException;
import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.filterchain.AbstractCodecFilter;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * Connection security layer filter adapter.
 */
final class ConnectionSecurityLayerFilter extends AbstractCodecFilter<Buffer, Buffer> {
    /**
     * <tt>Transformer</tt>, which decodes SASL encrypted data, contained in the
     * input Buffer, to the output Buffer.
     */
    private static final class Decoder extends AbstractTransformer<Buffer, Buffer> {
        private static final int BUFFER_SIZE = 4096;
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private final ConnectionSecurityLayer layer;

        public Decoder(final ConnectionSecurityLayer layer, final MemoryManager<?> memoryManager) {
            this.layer = layer;
            setMemoryManager(memoryManager);
        }

        public String getName() {
            return getClass().getName();
        }

        public boolean hasInputRemaining(final AttributeStorage storage, final Buffer input) {
            return input != null && input.hasRemaining();
        }

        @Override
        public TransformationResult<Buffer, Buffer> transformImpl(final AttributeStorage storage,
                final Buffer input) {
            final MemoryManager<?> memoryManager = obtainMemoryManager(storage);
            final int len = Math.min(buffer.length, input.remaining());
            input.get(buffer, 0, len);

            try {
                final Buffer output = Buffers.wrap(memoryManager, layer.unwrap(buffer, 0, len));
                return TransformationResult.createCompletedResult(output, input);
            } catch (final LdapException e) {
                return TransformationResult.createErrorResult(e.getResult().getResultCode()
                        .intValue(), e.getMessage());
            }
        }
    }

    /**
     * <tt>Transformer</tt>, which encodes SASL encrypted data, contained in the
     * input Buffer, to the output Buffer.
     */
    private static final class Encoder extends AbstractTransformer<Buffer, Buffer> {
        private static final int BUFFER_SIZE = 4096;
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private final ConnectionSecurityLayer layer;

        private Encoder(final ConnectionSecurityLayer layer, final MemoryManager<?> memoryManager) {
            this.layer = layer;
            setMemoryManager(memoryManager);
        }

        public String getName() {
            return getClass().getName();
        }

        public boolean hasInputRemaining(final AttributeStorage storage, final Buffer input) {
            return input != null && input.hasRemaining();
        }

        @Override
        public TransformationResult<Buffer, Buffer> transformImpl(final AttributeStorage storage,
                final Buffer input) {
            final MemoryManager<?> memoryManager = obtainMemoryManager(storage);
            final int len = Math.min(buffer.length, input.remaining());
            input.get(buffer, 0, len);

            try {
                final Buffer output = Buffers.wrap(memoryManager, layer.wrap(buffer, 0, len));
                return TransformationResult.createCompletedResult(output, input);
            } catch (final LdapException e) {
                return TransformationResult.createErrorResult(e.getResult().getResultCode()
                        .intValue(), e.getMessage());
            }
        }
    }

    ConnectionSecurityLayerFilter(final ConnectionSecurityLayer layer,
            final MemoryManager<?> memoryManager) {
        super(new Decoder(layer, memoryManager), new Encoder(layer, memoryManager));
    }
}
