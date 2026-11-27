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
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.grizzly;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.forgerock.opendj.io.ProxyProtocolDecoder;
import org.forgerock.opendj.io.ProxyProtocolHeader;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/** Consumes one PROXY header before forwarding the stream to TLS and LDAP. */
final class ProxyProtocolFilter extends BaseFilter {
    private static final byte[] V1 = { 'P', 'R', 'O', 'X', 'Y', ' ' };
    private static final byte[] V2 = { 13, 10, 13, 10, 0, 13, 10, 81, 85, 73, 84, 10 };
    private static final int V1_MAX_LENGTH = 107;
    private static final int V2_PREFIX_LENGTH = 16;
    private static final int MAX_HEADER_LENGTH = V2_PREFIX_LENGTH + 0xffff;

    private final Consumer<ProxyProtocolHeader> onHeader;
    private final Consumer<Throwable> onError;
    private final BooleanSupplier active;
    private boolean complete;
    private int scanned;
    // Retain only header bytes between reads so that receive buffers can go back to the pool.
    private byte[] pendingHeader;
    private int pendingLength;

    ProxyProtocolFilter(Consumer<ProxyProtocolHeader> onHeader, Consumer<Throwable> onError, BooleanSupplier active) {
        this.onHeader = onHeader;
        this.onError = onError;
        this.active = active;
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        if (complete) {
            return ctx.getInvokeAction();
        }
        Buffer input = ctx.getMessage();
        boolean releaseInput = true;
        try {
            int length = frameLength(input);
            if (length < 0) {
                int remaining = input.remaining();
                if (remaining > 0) {
                    // An incomplete frame contains only header bytes and is bounded by MAX_HEADER_LENGTH.
                    pendingHeader = headerBuffer(pendingLength + remaining);
                    input.get(pendingHeader, pendingLength, remaining);
                    pendingLength += remaining;
                }
                return ctx.getStopAction();
            }
            byte[] prefix = headerBuffer(length);
            input.get(prefix, pendingLength, length - pendingLength);
            pendingHeader = null;
            pendingLength = 0;
            ProxyProtocolHeader header = ProxyProtocolDecoder.decode(ByteBuffer.wrap(prefix, 0, length));
            if (header == null) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("incomplete frame");
            }
            complete = true;
            onHeader.accept(header);
            if (active.getAsBoolean() && input.hasRemaining()) {
                releaseInput = false;
                return ctx.getInvokeAction();
            }
        } catch (IOException | RuntimeException e) {
            pendingHeader = null;
            pendingLength = 0;
            onError.accept(e);
            ctx.getConnection().closeSilently();
        } finally {
            if (releaseInput) {
                input.tryDispose();
            }
        }
        return ctx.getStopAction();
    }

    private byte[] headerBuffer(int length) {
        if (pendingHeader == null) {
            return new byte[length];
        }
        if (pendingHeader.length >= length) {
            return pendingHeader;
        }
        int capacity = Math.min(MAX_HEADER_LENGTH, Math.max(length, pendingHeader.length * 2));
        return Arrays.copyOf(pendingHeader, capacity);
    }

    private byte headerByte(Buffer input, int index) {
        return index < pendingLength ? pendingHeader[index] : input.get(input.position() + index - pendingLength);
    }

    /** Finds a complete frame without repeatedly copying an incomplete header. */
    private int frameLength(Buffer input) throws ProxyProtocolDecoder.InvalidHeaderException {
        int count = pendingLength + Math.min(input.remaining(), MAX_HEADER_LENGTH - pendingLength);
        if (count == 0) {
            return -1;
        }
        byte first = headerByte(input, 0);
        byte[] signature = first == V1[0] ? V1 : V2;
        while (scanned < signature.length && scanned < count) {
            if (headerByte(input, scanned) != signature[scanned]) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("invalid signature");
            }
            scanned++;
        }
        if (scanned < signature.length) {
            return -1;
        }
        if (signature == V1) {
            while (scanned < count && scanned < V1_MAX_LENGTH) {
                if (headerByte(input, scanned++) == '\n') {
                    return scanned;
                }
            }
            if (scanned == V1_MAX_LENGTH) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("text header too long");
            }
            return -1;
        }
        if (count < V2_PREFIX_LENGTH) {
            return -1;
        }
        int command = headerByte(input, 12) & 0xff;
        if ((command & 0xf0) != 0x20 || (command & 0x0f) > 1) {
            throw new ProxyProtocolDecoder.InvalidHeaderException("invalid version or command");
        }
        int payloadLength = ((headerByte(input, 14) & 0xff) << 8) + (headerByte(input, 15) & 0xff);
        if ((command & 0x0f) == 1) {
            int family = (headerByte(input, 13) & 0xff) >>> 4;
            int transport = headerByte(input, 13) & 0x0f;
            if (family > 3 || transport > 2) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("invalid address family or transport");
            }
            int minimum = family == 1 ? 12 : family == 2 ? 36 : family == 3 ? 216 : 0;
            if (transport != 0 && payloadLength < minimum) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("short address block");
            }
        }
        int length = V2_PREFIX_LENGTH + payloadLength;
        return count >= length ? length : -1;
    }
}
