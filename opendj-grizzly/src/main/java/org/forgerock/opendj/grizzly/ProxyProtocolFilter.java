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
    private final Consumer<ProxyProtocolHeader> onHeader;
    private final Consumer<Throwable> onError;
    private final BooleanSupplier active;
    private boolean complete;
    private int scanned;

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
        try {
            int length = frameLength(input);
            if (length < 0) {
                return ctx.getStopAction(input);
            }
            byte[] prefix = new byte[length];
            input.duplicate().get(prefix);
            ByteBuffer bytes = ByteBuffer.wrap(prefix);
            ProxyProtocolHeader header = ProxyProtocolDecoder.decode(bytes);
            if (header == null) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("incomplete frame");
            }
            input.position(input.position() + bytes.position());
            complete = true;
            onHeader.accept(header);
            if (!active.getAsBoolean()) {
                return ctx.getStopAction();
            }
            return input.hasRemaining() ? ctx.getInvokeAction() : ctx.getStopAction();
        } catch (IOException | RuntimeException e) {
            onError.accept(e);
            ctx.getConnection().closeSilently();
            return ctx.getStopAction();
        }
    }

    /** Finds a complete frame without repeatedly copying an incomplete header. */
    private int frameLength(Buffer input) throws ProxyProtocolDecoder.InvalidHeaderException {
        int count = input.remaining();
        int start = input.position();
        if (count == 0) {
            return -1;
        }
        byte first = input.get(start);
        byte[] signature = first == V1[0] ? V1 : V2;
        while (scanned < signature.length && scanned < count) {
            if (input.get(start + scanned) != signature[scanned]) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("invalid signature");
            }
            scanned++;
        }
        if (scanned < signature.length) {
            return -1;
        }
        if (signature == V1) {
            while (scanned < count && scanned < 107) {
                if (input.get(start + scanned++) == '\n') {
                    return scanned;
                }
            }
            if (scanned == 107) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("text header too long");
            }
            return -1;
        }
        if (count < 16) {
            return -1;
        }
        int command = input.get(start + 12) & 0xff;
        if ((command & 0xf0) != 0x20 || (command & 0x0f) > 1) {
            throw new ProxyProtocolDecoder.InvalidHeaderException("invalid version or command");
        }
        int payloadLength = ((input.get(start + 14) & 0xff) << 8) + (input.get(start + 15) & 0xff);
        if ((command & 0x0f) == 1) {
            int family = (input.get(start + 13) & 0xff) >>> 4;
            int transport = input.get(start + 13) & 0x0f;
            if (family > 3 || transport > 2) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("invalid address family or transport");
            }
            int minimum = family == 1 ? 12 : family == 2 ? 36 : family == 3 ? 216 : 0;
            if (transport != 0 && payloadLength < minimum) {
                throw new ProxyProtocolDecoder.InvalidHeaderException("short address block");
            }
        }
        int length = 16 + payloadLength;
        return count >= length ? length : -1;
    }
}
