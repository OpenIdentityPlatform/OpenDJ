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
package org.forgerock.opendj.ldap.spi;

import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.responses.Response;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * Contains statics methods to create ldap messages.
 */
public final class LdapMessages {

    private LdapMessages() {
        // Nothing to so
    }

    /**
     * Creates a new {@link LdapRawMessage} containing a partially decoded LDAP message.
     *
     * @param messageType
     *            Operation code of the message
     * @param messageId
     *            Unique identifier of this message
     * @param protocolVersion
     *            Protocol version to use (only for Bind requests)
     * @param rawDn
     *            Unparsed name contained in the request (or null if DN is not applicable)
     * @param schema
     *            Schema to use to parse the DN
     * @param reader
     *            An {@link ASN1Reader} containing the full encoded ldap message packet.
     * @return A new {@link LdapRawMessage}
     */
    public static LdapRawMessage newRawMessage(final byte messageType, final int messageId, final int protocolVersion,
            final String rawDn, final Schema schema, final ASN1Reader reader) {
        return new LdapRawMessage(messageType, messageId, protocolVersion, rawDn, schema, reader);
    }

    /**
     * Creates a new {@link LdapResponseMessage}, adding low-level ldap protocol specific informations to a
     * {@link Response}.
     *
     * @param messageType
     *            Operation code of the response
     * @param messageId
     *            Message identifier of the request owning this response.
     * @param response
     *            The response
     * @return A new {@link LdapResponseMessage}
     */
    public static LdapResponseMessage newResponseMessage(final byte messageType, final int messageId,
            final Response response) {
        return new LdapResponseMessage(messageType, messageId, response);
    }

    /**
     * Represents an encoded LDAP message with it's envelope.
     */
    public static final class LdapRawMessage extends LdapMessageEnvelope<ASN1Reader> {
        private final String rawDn;
        private final Schema schema;
        private final int version;
        private DN dn;

        private LdapRawMessage(final byte messageType, final int messageId, final int version, final String rawDn,
                final Schema schema, final ASN1Reader content) {
            super(messageType, messageId, content);
            this.version = version;
            this.rawDn = rawDn;
            this.schema = schema;
        }

        /**
         * Get the Ldap version requested by this message (Bind request only).
         *
         * @return The ldap protocol version
         */
        public int getVersion() {
            return version;
        }

        /**
         * Get the raw form of the {@link DN} contained in the message (or null if the message doesn't contains a DN).
         *
         * @return The {@link DN} contained in request, or null if the message doesn't contains a DN.
         */
        public String getRawDn() {
            return rawDn;
        }

        /**
         * Get the decoded form of the {@link DN} contained in the message (or null if the message doesn't contains a
         * DN).
         *
         * @return The decoded {@link DN} contained in the request, or null if the message doesn't contains a DN.
         */
        public DN getDn() {
            if (rawDn == null) {
                return null;
            }
            if (dn != null) {
                return dn;
            }
            dn = DN.valueOf(rawDn.toString(), schema);
            return dn;
        }
    }

    /**
     * Represents a {@link Response} and its envelope.
     */
    public static final class LdapResponseMessage extends LdapMessageEnvelope<Response> {
        private LdapResponseMessage(final byte messageType, final int messageId, final Response content) {
            super(messageType, messageId, content);
        }
    }

    private static abstract class LdapMessageEnvelope<T> {

        private final T content;
        private final int messageId;
        private final byte messageType;

        public LdapMessageEnvelope(final byte messageType, final int messageId, final T content) {
            this.messageType = messageType;
            this.messageId = messageId;
            this.content = content;
        }

        public byte getMessageType() {
            return messageType;
        }

        public int getMessageId() {
            return messageId;
        }

        public T getContent() {
            return content;
        }
    }
}
