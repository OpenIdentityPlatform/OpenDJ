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
package org.forgerock.opendj.grizzly;

import java.io.IOException;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.BuffersBuffer;
import org.glassfish.grizzly.memory.HeapMemoryManager;

final class SaslFilter extends BaseFilter {

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer message = ctx.getMessage();
        if (message.remaining() < 4) {
            return ctx.getStopAction(message);
        }

        final int length = message.mark().getInt();
        if (message.remaining() < length) {
            return ctx.getStopAction(message.reset());
        }

        ctx.setMessage(unwrap(ctx, message, length));
        if (message.position(message.position() + length).hasRemaining()) {
            return ctx.getInvokeAction(message);
        }
        message.dispose();
        return ctx.getInvokeAction();
    }

    private Buffer unwrap(final FilterChainContext ctx, final Buffer buffer, final int length) throws SaslException {
        final SaslServer server = SaslUtils.getSaslServer(ctx.getConnection());
        if (buffer.hasArray()) {
            return Buffers.wrap(ctx.getMemoryManager(),
                    server.unwrap(buffer.array(), buffer.arrayOffset() + buffer.position(), length));
        }
        final Buffer heapBuffer = toHeapBuffer(buffer, length);
        try {
            return Buffers.wrap(ctx.getMemoryManager(),
                    server.unwrap(heapBuffer.array(), heapBuffer.arrayOffset() + heapBuffer.position(), length));
        } finally {
            heapBuffer.dispose();
        }
    }

    private final Buffer toHeapBuffer(final Buffer buffer, final int length) {
        return HeapMemoryManager
                         .DEFAULT_MEMORY_MANAGER.allocate(length)
                         .put(buffer, buffer.position(), length)
                         .position(buffer.position() + length)
                         .flip();
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Buffer message = ctx.getMessage();
        ctx.setMessage(wrap(ctx, message));
        message.dispose();
        return ctx.getInvokeAction();
    }

    private Buffer wrap(final FilterChainContext ctx, final Buffer buffer) throws SaslException {
        final SaslServer server = SaslUtils.getSaslServer(ctx.getConnection());
        final Buffer contentBuffer;
        if (buffer.hasArray()) {
            contentBuffer = Buffers.wrap(ctx.getMemoryManager(),
                    server.wrap(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
        } else {
            final Buffer heapBuffer = toHeapBuffer(buffer, buffer.remaining());
            try {
                contentBuffer = Buffers.wrap(
                        ctx.getMemoryManager(),
                        server.wrap(heapBuffer.array(), heapBuffer.arrayOffset() + heapBuffer.position(),
                                heapBuffer.remaining()));

            } finally {
                heapBuffer.dispose();
            }
        }
        final Buffer headerBuffer = ctx.getMemoryManager().allocate(4);
        headerBuffer.putInt(contentBuffer.limit());
        headerBuffer.flip();
        return BuffersBuffer.create(ctx.getMemoryManager(), headerBuffer, contentBuffer);
    }
}
