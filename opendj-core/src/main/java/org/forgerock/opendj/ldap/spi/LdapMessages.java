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
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.responses.Response;

/**
 * Contains static methods to create ldap messages.
 */
public final class LdapMessages {

    private LdapMessages() {
        // Nothing to so
    }

    /**
     * Creates a new {@link     } containing a partially decoded LDAP message.
     *
     * @param messageType
     *            Operation code of the message
     * @param messageId
     *            Unique identifier of this message
     * @param ldapVersion
     *            Protocol version to use (only for Bind requests)
     * @param rawDn
     *            Unparsed name contained in the request (or null if DN is not applicable)
     * @param reader
     *            An {@link ASN1Reader} containing the full encoded ldap message packet.
     * @return A new {@link LdapRequestEnvelope}
     */
    public static LdapRequestEnvelope newRequestEnvelope(final byte messageType, final int messageId,
            final int ldapVersion, final ByteString rawDn, final ASN1Reader reader) {
        return new LdapRequestEnvelope(messageType, messageId, ldapVersion, rawDn, reader);
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
     * Represents a Ldap Request envelope containing an encoded Request.
     */
    public static final class LdapRequestEnvelope extends LdapMessageEnvelope<ASN1Reader> {
        private final ByteString rawDn;
        private final int ldapVersion;

        private LdapRequestEnvelope(final byte messageType, final int messageId, final int ldapVersion,
                final ByteString rawDn, final ASN1Reader content) {
            super(messageType, messageId, content);
            this.ldapVersion = ldapVersion;
            this.rawDn = rawDn;
        }

        /**
         * Get the Ldap version requested by this message (Bind request only).
         *
         * @return The ldap protocol version
         */
        public int getLdapVersion() {
            return ldapVersion;
        }

        /**
         * Get the raw form of the {@link DN} contained in the message (or null if the message doesn't contains a DN).
         *
         * @return The {@link DN} contained in request, or null if the message doesn't contains a DN.
         */
        public ByteString getRawDn() {
            return rawDn;
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
