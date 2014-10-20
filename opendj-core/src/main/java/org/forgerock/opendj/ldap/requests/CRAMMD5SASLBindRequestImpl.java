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

import static com.forgerock.opendj.util.StaticUtils.copyOfBytes;

import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.responses.Responses.*;

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
 * CRAM-MD5 SASL bind request implementation.
 */
final class CRAMMD5SASLBindRequestImpl extends AbstractSASLBindRequest<CRAMMD5SASLBindRequest>
        implements CRAMMD5SASLBindRequest {
    private static final class Client extends SASLBindClientImpl {
        private final String authenticationID;
        private final ByteString password;
        private final SaslClient saslClient;

        private Client(final CRAMMD5SASLBindRequestImpl initialBindRequest, final String serverName)
                throws LdapException {
            super(initialBindRequest);

            this.authenticationID = initialBindRequest.getAuthenticationID();
            this.password = ByteString.wrap(initialBindRequest.getPassword());

            try {
                saslClient =
                        Sasl.createSaslClient(new String[] { SASL_MECHANISM_NAME }, null,
                                SASL_DEFAULT_PROTOCOL, serverName, null, this);
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
        public boolean evaluateResult(final BindResult result) throws LdapException {
            if (saslClient.isComplete()) {
                return true;
            }

            try {
                setNextSASLCredentials(saslClient.evaluateChallenge(result
                        .getServerSASLCredentials() == null ? new byte[0] : result
                        .getServerSASLCredentials().toByteArray()));
                return saslClient.isComplete();
            } catch (final SaslException e) {
                // FIXME: I18N need to have a better error message.
                // FIXME: Is this the best result code?
                throw newLdapException(newResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
                        "An error occurred during multi-stage authentication").setCause(e));
            }
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
    private byte[] password;

    CRAMMD5SASLBindRequestImpl(final CRAMMD5SASLBindRequest cramMD5SASLBindRequest) {
        super(cramMD5SASLBindRequest);
        this.authenticationID = cramMD5SASLBindRequest.getAuthenticationID();
        this.password = copyOfBytes(cramMD5SASLBindRequest.getPassword());
    }

    CRAMMD5SASLBindRequestImpl(final String authenticationID, final byte[] password) {
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
    public byte[] getPassword() {
        return password;
    }

    @Override
    public String getSASLMechanism() {
        return SASL_MECHANISM_NAME;
    }

    @Override
    public CRAMMD5SASLBindRequest setAuthenticationID(final String authenticationID) {
        Reject.ifNull(authenticationID);
        this.authenticationID = authenticationID;
        return this;
    }

    @Override
    public CRAMMD5SASLBindRequest setPassword(final byte[] password) {
        Reject.ifNull(password);
        this.password = password;
        return this;
    }

    @Override
    public CRAMMD5SASLBindRequest setPassword(final char[] password) {
        Reject.ifNull(password);
        this.password = StaticUtils.getBytes(password);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("CRAMMD5SASLBindRequest(bindDN=");
        builder.append(getName());
        builder.append(", authentication=SASL");
        builder.append(", saslMechanism=");
        builder.append(getSASLMechanism());
        builder.append(", authenticationID=");
        builder.append(authenticationID);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
