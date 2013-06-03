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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.copyOfBytes;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.util.StaticUtils.joinCollection;
import static org.forgerock.opendj.ldap.CoreMessages.ERR_SASL_PROTOCOL_ERROR;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.BindResult;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * Digest-MD5 SASL bind request implementation.
 */
final class DigestMD5SASLBindRequestImpl extends AbstractSASLBindRequest<DigestMD5SASLBindRequest>
        implements DigestMD5SASLBindRequest {
    private final static class Client extends SASLBindClientImpl {
        private final String authenticationID;
        private final ByteString password;
        private final String realm;
        private final SaslClient saslClient;

        private Client(final DigestMD5SASLBindRequestImpl initialBindRequest,
                final String serverName) throws ErrorResultException {
            super(initialBindRequest);

            this.authenticationID = initialBindRequest.getAuthenticationID();
            this.password = ByteString.wrap(initialBindRequest.getPassword());
            this.realm = initialBindRequest.getRealm();

            // Create property map containing all the parameters.
            final Map<String, String> props = new HashMap<String, String>();

            final List<String> qopValues = initialBindRequest.getQOPs();
            if (!qopValues.isEmpty()) {
                props.put(Sasl.QOP, joinCollection(qopValues, ","));
            }

            final String cipher = initialBindRequest.getCipher();
            if (cipher != null) {
                if (cipher.equalsIgnoreCase(CIPHER_LOW)) {
                    props.put(Sasl.STRENGTH, "high,medium,low");
                } else if (cipher.equalsIgnoreCase(CIPHER_MEDIUM)) {
                    props.put(Sasl.STRENGTH, "high,medium");
                } else if (cipher.equalsIgnoreCase(CIPHER_HIGH)) {
                    props.put(Sasl.STRENGTH, "high");
                } else {
                    /*
                     * Default strength allows all ciphers, so specifying a
                     * single cipher cannot be incompatible with the strength.
                     */
                    props.put("com.sun.security.sasl.digest.cipher", cipher);
                }
            }

            final Boolean serverAuth = initialBindRequest.isServerAuth();
            if (serverAuth != null) {
                props.put(Sasl.SERVER_AUTH, String.valueOf(serverAuth));
            }

            Integer size = initialBindRequest.getMaxReceiveBufferSize();
            if (size != null) {
                props.put(Sasl.MAX_BUFFER, String.valueOf(size));
            }

            size = initialBindRequest.getMaxSendBufferSize();
            if (size != null) {
                props.put("javax.security.sasl.sendmaxbuffer", String.valueOf(size));
            }

            for (final Map.Entry<String, String> e : initialBindRequest.getAdditionalAuthParams()
                    .entrySet()) {
                props.put(e.getKey(), e.getValue());
            }

            // Now create the client.
            try {
                saslClient =
                        Sasl.createSaslClient(new String[] { SASL_MECHANISM_NAME },
                                initialBindRequest.getAuthorizationID(), SASL_DEFAULT_PROTOCOL,
                                serverName, props, this);
                if (saslClient.hasInitialResponse()) {
                    setNextSASLCredentials(saslClient.evaluateChallenge(new byte[0]));
                } else {
                    setNextSASLCredentials((ByteString) null);
                }
            } catch (final SaslException e) {
                throw newErrorResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR, e);
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
        public boolean evaluateResult(final BindResult result) throws ErrorResultException {
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
                throw newErrorResult(ResultCode.CLIENT_SIDE_LOCAL_ERROR,
                        "An error occurred during multi-stage authentication", e);
            }
        }

        @Override
        public ConnectionSecurityLayer getConnectionSecurityLayer() {
            final String qop = (String) saslClient.getNegotiatedProperty(Sasl.QOP);
            if (qop.equalsIgnoreCase("auth-int") || qop.equalsIgnoreCase("auth-conf")) {
                return this;
            } else {
                return null;
            }
        }

        @Override
        public byte[] unwrap(final byte[] incoming, final int offset, final int len)
                throws ErrorResultException {
            try {
                return saslClient.unwrap(incoming, offset, len);
            } catch (final SaslException e) {
                final LocalizableMessage msg =
                        ERR_SASL_PROTOCOL_ERROR.get(SASL_MECHANISM_NAME, getExceptionMessage(e));
                throw newErrorResult(ResultCode.CLIENT_SIDE_DECODING_ERROR, msg.toString(), e);
            }
        }

        @Override
        public byte[] wrap(final byte[] outgoing, final int offset, final int len)
                throws ErrorResultException {
            try {
                return saslClient.wrap(outgoing, offset, len);
            } catch (final SaslException e) {
                final LocalizableMessage msg =
                        ERR_SASL_PROTOCOL_ERROR.get(SASL_MECHANISM_NAME, getExceptionMessage(e));
                throw newErrorResult(ResultCode.CLIENT_SIDE_ENCODING_ERROR, msg.toString(), e);
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

        @Override
        void handle(final RealmCallback callback) throws UnsupportedCallbackException {
            if (realm == null) {
                callback.setText(callback.getDefaultText());
            } else {
                callback.setText(realm);
            }
        }

    }

    private final Map<String, String> additionalAuthParams = new LinkedHashMap<String, String>();
    private String authenticationID;
    private String authorizationID = null;

    private String cipher = null;
    private Integer maxReceiveBufferSize = null;
    private Integer maxSendBufferSize = null;
    private byte[] password;
    private final List<String> qopValues = new LinkedList<String>();
    private String realm = null;
    /*
     * Don't use primitives for these so that we can distinguish between default
     * settings (null) and values set by the caller.
     */
    private Boolean serverAuth = null;

    DigestMD5SASLBindRequestImpl(final DigestMD5SASLBindRequest digestMD5SASLBindRequest) {
        super(digestMD5SASLBindRequest);
        this.additionalAuthParams.putAll(digestMD5SASLBindRequest.getAdditionalAuthParams());
        this.qopValues.addAll(digestMD5SASLBindRequest.getQOPs());
        this.cipher = digestMD5SASLBindRequest.getCipher();

        this.serverAuth = digestMD5SASLBindRequest.isServerAuth();
        this.maxReceiveBufferSize = digestMD5SASLBindRequest.getMaxReceiveBufferSize();
        this.maxSendBufferSize = digestMD5SASLBindRequest.getMaxSendBufferSize();

        this.authenticationID = digestMD5SASLBindRequest.getAuthenticationID();
        this.authorizationID = digestMD5SASLBindRequest.getAuthorizationID();
        this.password = copyOfBytes(digestMD5SASLBindRequest.getPassword());
        this.realm = digestMD5SASLBindRequest.getRealm();
    }

    DigestMD5SASLBindRequestImpl(final String authenticationID, final byte[] password) {
        Validator.ensureNotNull(authenticationID, password);
        this.authenticationID = authenticationID;
        this.password = password;
    }

    @Override
    public DigestMD5SASLBindRequest addAdditionalAuthParam(final String name, final String value) {
        Validator.ensureNotNull(name, value);
        additionalAuthParams.put(name, value);
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest addQOP(final String... qopValues) {
        for (final String qopValue : qopValues) {
            this.qopValues.add(Validator.ensureNotNull(qopValue));
        }
        return this;
    }

    @Override
    public BindClient createBindClient(final String serverName) throws ErrorResultException {
        return new Client(this, serverName);
    }

    @Override
    public Map<String, String> getAdditionalAuthParams() {
        return additionalAuthParams;
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
    public String getCipher() {
        return cipher;
    }

    @Override
    public int getMaxReceiveBufferSize() {
        return maxReceiveBufferSize == null ? 65536 : maxReceiveBufferSize;
    }

    @Override
    public int getMaxSendBufferSize() {
        return maxSendBufferSize == null ? 65536 : maxSendBufferSize;
    }

    @Override
    public byte[] getPassword() {
        return password;
    }

    @Override
    public List<String> getQOPs() {
        return qopValues;
    }

    @Override
    public String getRealm() {
        return realm;
    }

    @Override
    public String getSASLMechanism() {
        return SASL_MECHANISM_NAME;
    }

    @Override
    public boolean isServerAuth() {
        return serverAuth == null ? false : serverAuth;
    }

    @Override
    public DigestMD5SASLBindRequest setAuthenticationID(final String authenticationID) {
        Validator.ensureNotNull(authenticationID);
        this.authenticationID = authenticationID;
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setAuthorizationID(final String authorizationID) {
        this.authorizationID = authorizationID;
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setCipher(final String cipher) {
        this.cipher = cipher;
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setMaxReceiveBufferSize(final int size) {
        maxReceiveBufferSize = size;
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setMaxSendBufferSize(final int size) {
        maxSendBufferSize = size;
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setPassword(final byte[] password) {
        Validator.ensureNotNull(password);
        this.password = password;
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setPassword(final char[] password) {
        Validator.ensureNotNull(password);
        this.password = StaticUtils.getBytes(password);
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setRealm(final String realm) {
        this.realm = realm;
        return this;
    }

    @Override
    public DigestMD5SASLBindRequest setServerAuth(final boolean serverAuth) {
        this.serverAuth = serverAuth;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("DigestMD5SASLBindRequest(bindDN=");
        builder.append(getName());
        builder.append(", authentication=SASL");
        builder.append(", saslMechanism=");
        builder.append(getSASLMechanism());
        builder.append(", authenticationID=");
        builder.append(authenticationID);
        builder.append(", authorizationID=");
        builder.append(authorizationID);
        builder.append(", realm=");
        builder.append(realm);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
