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
 * Portions Copyright 2012-2016 ForgeRock AS.
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

        @Override
        public String getName() {
            return getClass().getName();
        }

        @Override
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

        @Override
        public String getName() {
            return getClass().getName();
        }

        @Override
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
