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

import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED;
import static org.forgerock.opendj.io.LDAP.*;
import static org.forgerock.opendj.ldap.spi.LdapMessages.newRequestEnvelope;

import java.io.IOException;

import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRequestEnvelope;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapResponseMessage;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Decodes {@link LdapRequestEnvelope} and encodes {@link Response}. This class keeps a state to handler the Ldap V2
 * detection, a LdapCodec instance cannot be shared accross different connection
 */
abstract class LdapCodec extends LDAPBaseFilter {
    private boolean isLdapV2Pending;
    private boolean isLdapV2;

    LdapCodec(final int maxElementSize, final DecodeOptions decodeOptions) {
        super(decodeOptions, maxElementSize);
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        try {
            final Buffer buffer = ctx.getMessage();
            try (final ASN1BufferReader reader = new ASN1BufferReader(maxASN1ElementSize, buffer)) {
                // Due to a bug in grizzly's ByteBufferWrapper.split(), we can't use byteBuffer.mark()
                final int mark = buffer.position();
                if (!reader.elementAvailable()) {
                    buffer.position(mark);
                    // We need to create a duplicate because buffer will be closed by the reader (try-with-resources)
                    return ctx.getStopAction(buffer.duplicate());
                }
                final int length = reader.peekLength();
                if (length > maxASN1ElementSize) {
                    buffer.position(mark);
                    throw DecodeException.fatalError(
                            ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(length, maxASN1ElementSize));
                }
                final Buffer remainder = (buffer.remaining() > length)
                        ? buffer.split(buffer.position() + length)
                        : null;
                buffer.position(mark);
                ctx.setMessage(decodePacket(new ASN1BufferReader(maxASN1ElementSize, buffer.asReadOnlyBuffer())));
                buffer.tryDispose();
                return ctx.getInvokeAction(remainder);
            }
        } catch (Exception e) {
            onLdapCodecError(ctx, e);
            ctx.getConnection().closeSilently();
            final NextAction suspendAction = ctx.getSuspendAction();
            ctx.completeAndRecycle();
            return suspendAction;
        }
    }

    protected abstract void onLdapCodecError(FilterChainContext ctx, Throwable error);

    private LdapRequestEnvelope decodePacket(final ASN1BufferReader reader)
            throws IOException {
        reader.mark();
        try {
            reader.readStartSequence();
            final int messageId = (int) reader.readInteger();
            final byte messageType = reader.peekType();

            final ByteString rawDn;
            final int ldapVersion;
            switch (messageType) {
            case OP_TYPE_BIND_REQUEST:
                reader.readStartSequence(messageType);
                ldapVersion = (int) reader.readInteger();
                rawDn = reader.readOctetString();
                isLdapV2Pending = ldapVersion == 2;
                break;
            case OP_TYPE_DELETE_REQUEST:
                rawDn = reader.readOctetString(messageType);
                ldapVersion = -1;
                break;
            case OP_TYPE_ADD_REQUEST:
            case OP_TYPE_COMPARE_REQUEST:
            case OP_TYPE_MODIFY_DN_REQUEST:
            case OP_TYPE_MODIFY_REQUEST:
            case OP_TYPE_SEARCH_REQUEST:
                reader.readStartSequence(messageType);
                rawDn = reader.readOctetString();
                ldapVersion = -1;
                break;
            default:
                rawDn = null;
                ldapVersion = -1;
            }
            return newRequestEnvelope(messageType, messageId, ldapVersion, rawDn, reader);
        } finally {
            reader.reset();
        }
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final LdapResponseMessage response = ctx.<LdapResponseMessage>getMessage();
        if (response.getMessageType() == OP_TYPE_BIND_RESPONSE && ((BindResult) response.getContent()).isSuccess()) {
            isLdapV2 = isLdapV2Pending;
        }
        final int protocolVersion = isLdapV2 ? 2 : 3;

        final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter(ctx.getMemoryManager(), protocolVersion);
        try {
            final Buffer buffer = toBuffer(writer, ctx.<LdapResponseMessage> getMessage());
            ctx.setMessage(buffer);
            return ctx.getInvokeAction();
        } catch (Exception e) {
            onLdapCodecError(ctx, e);
            ctx.getConnection().closeSilently();
            final NextAction suspendAction = ctx.getSuspendAction();
            ctx.completeAndRecycle();
            return suspendAction;
        } finally {
            GrizzlyUtils.recycleWriter(writer);
        }
    }

    private Buffer toBuffer(final LDAPWriter<ASN1BufferWriter> writer, final LdapResponseMessage message)
            throws IOException {
        final int msgId = message.getMessageId();
        final Response msgContent = message.getContent();
        switch (message.getMessageType()) {
        case OP_TYPE_ADD_RESPONSE:
            writer.writeAddResult(msgId, (Result) msgContent);
            break;
        case OP_TYPE_BIND_RESPONSE:
            writer.writeBindResult(msgId, (BindResult) msgContent);
            break;
        case OP_TYPE_COMPARE_RESPONSE:
            writer.writeCompareResult(msgId, (CompareResult) msgContent);
            break;
        case OP_TYPE_DELETE_RESPONSE:
            writer.writeDeleteResult(msgId, (Result) msgContent);
            break;
        case OP_TYPE_EXTENDED_RESPONSE:
            writer.writeExtendedResult(msgId, (ExtendedResult) msgContent);
            break;
        case OP_TYPE_INTERMEDIATE_RESPONSE:
            writer.writeIntermediateResponse(msgId, (IntermediateResponse) msgContent);
            break;
        case OP_TYPE_MODIFY_DN_RESPONSE:
            writer.writeModifyDNResult(msgId, (Result) msgContent);
            break;
        case OP_TYPE_MODIFY_RESPONSE:
            writer.writeModifyResult(msgId, (Result) msgContent);
            break;
        case OP_TYPE_SEARCH_RESULT_DONE:
            writer.writeSearchResult(msgId, (Result) msgContent);
            break;
        case OP_TYPE_SEARCH_RESULT_ENTRY:
            writer.writeSearchResultEntry(msgId, (SearchResultEntry) msgContent);
            break;
        case OP_TYPE_SEARCH_RESULT_REFERENCE:
            writer.writeSearchResultReference(msgId, (SearchResultReference) msgContent);
            break;
        default:
            throw new IOException("Unsupported message type '" + message.getMessageType() + "'");
        }
        return writer.getASN1Writer().getBuffer();
    }
}
