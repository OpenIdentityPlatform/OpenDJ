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

import static org.forgerock.opendj.io.LDAP.*;

import java.io.IOException;

import org.forgerock.opendj.io.LDAPWriter;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.IntermediateResponse;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.spi.LdapMessages;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapRawMessage;
import org.forgerock.opendj.ldap.spi.LdapMessages.LdapResponseMessage;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

abstract class LdapCodec extends LDAPBaseFilter {

    private static final Attribute<Boolean> IS_LDAP_V2 = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute(LdapCodec.class.getName() + ".IsLdapV2", Boolean.FALSE);

    private static final Attribute<Boolean> IS_LDAP_V2_PENDING = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute(LdapCodec.class.getName() + ".PendingLdapV2", Boolean.FALSE);

    LdapCodec(final int maxElementSize, final DecodeOptions decodeOptions) {
        super(decodeOptions, maxElementSize);
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        // Default value mechanism of Grizzly's attribute doesn't seems to work.
        IS_LDAP_V2.set(ctx.getConnection(), Boolean.FALSE);
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        try {
            final Buffer buffer = ctx.getMessage();
            final LdapRawMessage message;

            message = readMessage(buffer, ctx.getConnection());
            if (message != null) {
                ctx.setMessage(message);
                return ctx.getInvokeAction(getRemainingBuffer(buffer));
            }
            return ctx.getStopAction(getRemainingBuffer(buffer));
        } catch (Exception e) {
            onLdapCodecError(ctx, e);
            // make the connection deaf to any following input
            // onLdapDecodeError call will take care of error processing
            // and closing the connection
            final NextAction suspendAction = ctx.getSuspendAction();
            ctx.completeAndRecycle();
            return suspendAction;
        }
    }

    protected abstract void onLdapCodecError(FilterChainContext ctx, Throwable error);

    private LdapRawMessage readMessage(final Buffer buffer, final AttributeStorage attributeStorage)
            throws IOException {
        try (final ASN1BufferReader reader = new ASN1BufferReader(maxASN1ElementSize, buffer)) {
            final int packetStart = buffer.position();
            if (!reader.elementAvailable()) {
                buffer.position(packetStart);
                return null;
            }
            final int length = reader.peekLength();
            if (length > maxASN1ElementSize) {
                buffer.position(packetStart);
                throw new IllegalStateException();
            }

            final Buffer packet = buffer.slice(packetStart, buffer.position() + length);
            buffer.position(buffer.position() + length);

            return decodePacket(new ASN1BufferReader(maxASN1ElementSize, packet), attributeStorage);
        }
    }

    private LdapRawMessage decodePacket(final ASN1BufferReader reader, final AttributeStorage attributeStorage)
            throws IOException {
        reader.mark();
        try {
            reader.readStartSequence();
            final int messageId = (int) reader.readInteger();
            final byte messageType = reader.peekType();

            final ByteString rawDn;
            final int protocolVersion;
            switch (messageType) {
            case OP_TYPE_BIND_REQUEST:
                reader.readStartSequence(messageType);
                protocolVersion = (int) reader.readInteger();
                rawDn = reader.readOctetString();
                IS_LDAP_V2_PENDING.set(attributeStorage, protocolVersion == 2);
                break;
            case OP_TYPE_DELETE_REQUEST:
                rawDn = reader.readOctetString(messageType);
                protocolVersion = -1;
                break;
            case OP_TYPE_ADD_REQUEST:
            case OP_TYPE_COMPARE_REQUEST:
            case OP_TYPE_MODIFY_DN_REQUEST:
            case OP_TYPE_MODIFY_REQUEST:
            case OP_TYPE_SEARCH_REQUEST:
                reader.readStartSequence(messageType);
                rawDn = reader.readOctetString();
                protocolVersion = -1;
                break;
            default:
                rawDn = null;
                protocolVersion = -1;
            }
            return LdapMessages.newRawMessage(messageType, messageId, protocolVersion, rawDn, reader);
        } finally {
            reader.reset();
        }
    }

    private Buffer getRemainingBuffer(final Buffer buffer) {
        return buffer.hasRemaining() ? buffer : null;
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final LdapResponseMessage response = ctx.<LdapResponseMessage>getMessage();
        if (response.getMessageType() == OP_TYPE_BIND_RESPONSE) {
            final Boolean isLdapV2 = IS_LDAP_V2_PENDING.remove(ctx.getConnection());
            IS_LDAP_V2.set(ctx.getConnection(), isLdapV2);
        }
        final int protocolVersion = IS_LDAP_V2.get(ctx.getConnection()) ? 2 : 3;

        final LDAPWriter<ASN1BufferWriter> writer = GrizzlyUtils.getWriter(ctx.getMemoryManager(), protocolVersion);
        try {
            final Buffer buffer = toBuffer(writer, ctx.<LdapResponseMessage> getMessage());
            ctx.setMessage(buffer);
            return ctx.getInvokeAction();
        } catch (Exception e) {
            onLdapCodecError(ctx, e);
            // make the connection deaf to any following input
            // onLdapDecodeError call will take care of error processing
            // and closing the connection
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
