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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static org.forgerock.opendj.ldap.LdapException.newLdapException;

import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.util.Reject;

import com.forgerock.opendj.util.StaticUtils;

/**
 * Plain SASL bind request implementation.
 */
final class PlainSASLBindRequestImpl extends AbstractSASLBindRequest<PlainSASLBindRequest>
        implements PlainSASLBindRequest {
    private static final class Client extends SASLBindClientImpl {
        private final String authenticationID;
        private final ByteString password;
        private final SaslClient saslClient;

        private Client(final PlainSASLBindRequestImpl initialBindRequest, final String serverName)
                throws LdapException {
            super(initialBindRequest);

            this.authenticationID = initialBindRequest.getAuthenticationID();
            this.password = ByteString.wrap(initialBindRequest.getPassword());

            try {
                saslClient =
                        Sasl.createSaslClient(new String[] { SASL_MECHANISM_NAME },
                                initialBindRequest.getAuthorizationID(), SASL_DEFAULT_PROTOCOL,
                                serverName, null, this);

                if (saslClient.hasInitialResponse()) {
                    setNextSASLCredentials(saslClient.evaluateChallenge(new byte[0]));
                } else {
                    setNextSASLCredentials((ByteString) null);
                }
            } catch (final SaslException e) {
                throw newLdapException(ResultCode.CLIENT_SIDE_LOCAL_ERROR, e);
            }
        }

        @Override
        public void dispose() {
            try {
                saslClient.dispose();
            } catch (final SaslException ignored) {
                // Ignore the SASL exception.
            }
        }

        @Override
        public boolean evaluateResult(final BindResult result) {
            return saslClient.isComplete();
        }

        @Override
        void handle(final NameCallback callback) throws UnsupportedCallbackException {
            callback.setName(authenticationID);
        }

        @Override
        void handle(final PasswordCallback callback) throws UnsupportedCallbackException {
            callback.setPassword(password.toString().toCharArray());
        }
    }

    private String authenticationID;
    private String authorizationID;

    private byte[] password;

    PlainSASLBindRequestImpl(final PlainSASLBindRequest plainSASLBindRequest) {
        super(plainSASLBindRequest);
        this.authenticationID = plainSASLBindRequest.getAuthenticationID();
        this.authorizationID = plainSASLBindRequest.getAuthorizationID();
        this.password = StaticUtils.copyOfBytes(plainSASLBindRequest.getPassword());
    }

    PlainSASLBindRequestImpl(final String authenticationID, final byte[] password) {
        Reject.ifNull(authenticationID, password);
        this.authenticationID = authenticationID;
        this.password = password;
    }

    @Override
    public BindClient createBindClient(final String serverName) throws LdapException {
        return new Client(this, serverName);
    }

    @Override
    public String getAuthenticationID() {
        return authenticationID;
    }

    @Override
    public String getAuthorizationID() {
        return authorizationID;
    }

    @Override
    public byte[] getPassword() {
        return password;
    }

    @Override
    public String getSASLMechanism() {
        return SASL_MECHANISM_NAME;
    }

    @Override
    public PlainSASLBindRequest setAuthenticationID(final String authenticationID) {
        Reject.ifNull(authenticationID);
        this.authenticationID = authenticationID;
        return this;
    }

    @Override
    public PlainSASLBindRequest setAuthorizationID(final String authorizationID) {
        this.authorizationID = authorizationID;
        return this;
    }

    @Override
    public PlainSASLBindRequest setPassword(final byte[] password) {
        Reject.ifNull(password);
        this.password = password;
        return this;
    }

    @Override
    public PlainSASLBindRequest setPassword(final char[] password) {
        Reject.ifNull(password);
        this.password = StaticUtils.getBytes(password);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PlainSASLBindRequest(bindDN=");
        builder.append(getName());
        builder.append(", authentication=SASL");
        builder.append(", saslMechanism=");
        builder.append(getSASLMechanism());
        builder.append(", authenticationID=");
        builder.append(authenticationID);
        builder.append(", authorizationID=");
        builder.append(authorizationID);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
