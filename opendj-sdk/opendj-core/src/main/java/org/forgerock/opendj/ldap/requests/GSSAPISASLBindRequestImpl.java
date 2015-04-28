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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDAPAUTH_GSSAPI_LOCAL_AUTHENTICATION_FAILED;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SASL_CONTEXT_CREATE_ERROR;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_SASL_PROTOCOL_ERROR;
import static com.forgerock.opendj.util.StaticUtils.copyOfBytes;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;

import com.forgerock.opendj.util.StaticUtils;
import com.sun.security.auth.callback.TextCallbackHandler;
import com.sun.security.auth.module.Krb5LoginModule;

/**
 * GSSAPI SASL bind request implementation.
 */
@SuppressWarnings("restriction")
final class GSSAPISASLBindRequestImpl extends AbstractSASLBindRequest<GSSAPISASLBindRequest>
        implements GSSAPISASLBindRequest {
    private static final class Client extends SASLBindClientImpl {
        private static Subject kerberos5Login(final String authenticationID,
                final ByteString password, final String realm, final String kdc) throws LdapException {
            if (authenticationID == null) {
                // FIXME: I18N need to have a better error message.
                // FIXME: Is this the best result code?
                throw newLdapException(Responses.newResult(
                        ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
                        "No authentication ID specified for GSSAPI SASL authentication"));
            }

            if (password == null) {
                // FIXME: I18N need to have a better error message.
                // FIXME: Is this the best result code?
                throw newLdapException(Responses.newResult(
                        ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
                        "No password specified for GSSAPI SASL authentication"));
            }

            final Map<String, Object> state = new HashMap<>();
            state.put("javax.security.auth.login.name", authenticationID);
            state.put("javax.security.auth.login.password", password.toString().toCharArray());
            state.put("javax.security.auth.useSubjectCredsOnly", "true");
            state.put("java.security.krb5.realm", realm);
            state.put("java.security.krb5.kdc", kdc);

            final Map<String, Object> options = new HashMap<>();
            options.put("tryFirstPass", "true");
            options.put("useTicketCache", "true");
            options.put("doNotPrompt", "true");
            options.put("storePass", "false");
            options.put("forwardable", "true");

            final Subject subject = new Subject();
            final Krb5LoginModule login = new Krb5LoginModule();
            login.initialize(subject, new TextCallbackHandler(), state, options);
            try {
                if (login.login()) {
                    login.commit();
                }
            } catch (final LoginException e) {
                // FIXME: Is this the best result code?
                final LocalizableMessage message =
                        ERR_LDAPAUTH_GSSAPI_LOCAL_AUTHENTICATION_FAILED.get(StaticUtils
                                .getExceptionMessage(e));
                throw newLdapException(Responses.newResult(
                        ResultCode.CLIENT_SIDE_LOCAL_ERROR)
                        .setDiagnosticMessage(message.toString()).setCause(e));
            }
            return subject;
        }

        private final String authorizationID;
        private final PrivilegedExceptionAction<Boolean> evaluateAction =
                new PrivilegedExceptionAction<Boolean>() {
                    @Override
                    public Boolean run() throws LdapException {
                        if (saslClient.isComplete()) {
                            return true;
                        }

                        try {
                            setNextSASLCredentials(saslClient.evaluateChallenge(lastResult
                                    .getServerSASLCredentials() == null ? new byte[0] : lastResult
                                    .getServerSASLCredentials().toByteArray()));
                            return saslClient.isComplete();
                        } catch (final SaslException e) {
                            // FIXME: I18N need to have a better error message.
                            // FIXME: Is this the best result code?
                            throw newLdapException(Responses.newResult(
                                    ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
                                    "An error occurred during multi-stage authentication")
                                    .setCause(e));
                        }
                    }
                };
        private BindResult lastResult;

        private final SaslClient saslClient;

        private final Subject subject;

        private Client(final GSSAPISASLBindRequestImpl initialBindRequest, final String serverName)
                throws LdapException {
            super(initialBindRequest);

            this.authorizationID = initialBindRequest.getAuthorizationID();
            if (initialBindRequest.getSubject() != null) {
                this.subject = initialBindRequest.getSubject();
            } else {
                this.subject =
                        kerberos5Login(initialBindRequest.getAuthenticationID(), ByteString
                                .wrap(initialBindRequest.getPassword()), initialBindRequest
                                .getRealm(), initialBindRequest.getKDCAddress());
            }

            try {
                this.saslClient =
                        Subject.doAs(subject, new PrivilegedExceptionAction<SaslClient>() {
                            @Override
                            public SaslClient run() throws LdapException {
                                // Create property map containing all the parameters.
                                final Map<String, String> props = new HashMap<>();

                                final List<String> qopValues = initialBindRequest.getQOPs();
                                if (!qopValues.isEmpty()) {
                                    props.put(Sasl.QOP, Utils.joinAsString(",", qopValues));
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
                                    props.put("javax.security.sasl.sendmaxbuffer", String
                                            .valueOf(size));
                                }

                                for (final Map.Entry<String, String> e : initialBindRequest
                                        .getAdditionalAuthParams().entrySet()) {
                                    props.put(e.getKey(), e.getValue());
                                }

                                try {
                                    final SaslClient saslClient =
                                            Sasl.createSaslClient(
                                                    new String[] { SASL_MECHANISM_NAME },
                                                    authorizationID, SASL_DEFAULT_PROTOCOL,
                                                    serverName, props, Client.this);
                                    if (saslClient.hasInitialResponse()) {
                                        setNextSASLCredentials(saslClient
                                                .evaluateChallenge(new byte[0]));
                                    } else {
                                        setNextSASLCredentials((ByteString) null);
                                    }
                                    return saslClient;
                                } catch (final SaslException e) {
                                    throw newLdapException(ResultCode.CLIENT_SIDE_LOCAL_ERROR, e);
                                }
                            }
                        });
            } catch (final PrivilegedActionException e) {
                if (e.getCause() instanceof LdapException) {
                    throw (LdapException) e.getCause();
                } else {
                    // This should not happen. Must be a bug.
                    final LocalizableMessage msg =
                            ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_NAME,
                                    getExceptionMessage(e));
                    throw newLdapException(ResultCode.CLIENT_SIDE_LOCAL_ERROR, msg.toString(), e);
                }
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
            this.lastResult = result;
            try {
                return Subject.doAs(subject, evaluateAction);
            } catch (final PrivilegedActionException e) {
                if (e.getCause() instanceof LdapException) {
                    throw (LdapException) e.getCause();
                } else {
                    // This should not happen. Must be a bug.
                    final LocalizableMessage msg =
                            ERR_SASL_PROTOCOL_ERROR
                                    .get(SASL_MECHANISM_NAME, getExceptionMessage(e));
                    throw newLdapException(ResultCode.CLIENT_SIDE_LOCAL_ERROR, msg.toString(), e);
                }
            }
        }

        @Override
        public ConnectionSecurityLayer getConnectionSecurityLayer() {
            final String qop = (String) saslClient.getNegotiatedProperty(Sasl.QOP);
            if ("auth-int".equalsIgnoreCase(qop) || "auth-conf".equalsIgnoreCase(qop)) {
                return this;
            }
            return null;
        }

        @Override
        public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws LdapException {
            try {
                return saslClient.unwrap(incoming, offset, len);
            } catch (final SaslException e) {
                final LocalizableMessage msg =
                        ERR_SASL_PROTOCOL_ERROR.get(SASL_MECHANISM_NAME, getExceptionMessage(e));
                throw newLdapException(ResultCode.CLIENT_SIDE_DECODING_ERROR, msg.toString(), e);
            }
        }

        @Override
        public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws LdapException {
            try {
                return saslClient.wrap(outgoing, offset, len);
            } catch (final SaslException e) {
                final LocalizableMessage msg =
                        ERR_SASL_PROTOCOL_ERROR.get(SASL_MECHANISM_NAME, getExceptionMessage(e));
                throw newLdapException(ResultCode.CLIENT_SIDE_ENCODING_ERROR, msg.toString(), e);
            }
        }

    }

    private final Map<String, String> additionalAuthParams = new LinkedHashMap<>();

    /** Ignored if subject is non-null. */
    private String authenticationID;
    /** Optional authorization ID. */
    private String authorizationID;
    private String kdcAddress;

    private Integer maxReceiveBufferSize;
    private Integer maxSendBufferSize;

    private byte[] password;
    private final List<String> qopValues = new LinkedList<>();
    private String realm;
    /**
     * Don't use primitives for these so that we can distinguish between default
     * settings (null) and values set by the caller.
     */
    private Boolean serverAuth;
    /** If null then authenticationID and password must be present. */
    private Subject subject;

    GSSAPISASLBindRequestImpl(final GSSAPISASLBindRequest gssapiSASLBindRequest) {
        super(gssapiSASLBindRequest);
        this.subject = gssapiSASLBindRequest.getSubject();

        this.authenticationID = gssapiSASLBindRequest.getAuthenticationID();
        this.password = copyOfBytes(gssapiSASLBindRequest.getPassword());
        this.realm = gssapiSASLBindRequest.getRealm();

        this.kdcAddress = gssapiSASLBindRequest.getKDCAddress();

        this.authorizationID = gssapiSASLBindRequest.getAuthorizationID();

        this.additionalAuthParams.putAll(gssapiSASLBindRequest.getAdditionalAuthParams());
        this.qopValues.addAll(gssapiSASLBindRequest.getQOPs());

        this.serverAuth = gssapiSASLBindRequest.isServerAuth();
        this.maxReceiveBufferSize = gssapiSASLBindRequest.getMaxReceiveBufferSize();
        this.maxSendBufferSize = gssapiSASLBindRequest.getMaxSendBufferSize();
    }

    GSSAPISASLBindRequestImpl(final String authenticationID, final byte[] password) {
        Reject.ifNull(authenticationID, password);
        this.authenticationID = authenticationID;
        this.password = password;
    }

    GSSAPISASLBindRequestImpl(final Subject subject) {
        Reject.ifNull(subject);
        this.subject = subject;
    }

    @Override
    public GSSAPISASLBindRequest addAdditionalAuthParam(final String name, final String value) {
        Reject.ifNull(name, value);
        additionalAuthParams.put(name, value);
        return this;
    }

    @Override
    public GSSAPISASLBindRequest addQOP(final String... qopValues) {
        for (final String qopValue : qopValues) {
            this.qopValues.add(Reject.checkNotNull(qopValue));
        }
        return this;
    }

    @Override
    public BindClient createBindClient(final String serverName) throws LdapException {
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
    public String getKDCAddress() {
        return kdcAddress;
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
    public Subject getSubject() {
        return subject;
    }

    @Override
    public boolean isServerAuth() {
        return serverAuth == null ? false : serverAuth;
    }

    @Override
    public GSSAPISASLBindRequest setAuthenticationID(final String authenticationID) {
        Reject.ifNull(authenticationID);
        this.authenticationID = authenticationID;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setAuthorizationID(final String authorizationID) {
        this.authorizationID = authorizationID;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setKDCAddress(final String address) {
        this.kdcAddress = address;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setMaxReceiveBufferSize(final int size) {
        maxReceiveBufferSize = size;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setMaxSendBufferSize(final int size) {
        maxSendBufferSize = size;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setPassword(final byte[] password) {
        Reject.ifNull(password);
        this.password = password;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setPassword(final char[] password) {
        Reject.ifNull(password);
        this.password = StaticUtils.getBytes(password);
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setRealm(final String realm) {
        this.realm = realm;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setServerAuth(final boolean serverAuth) {
        this.serverAuth = serverAuth;
        return this;
    }

    @Override
    public GSSAPISASLBindRequest setSubject(final Subject subject) {
        this.subject = subject;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GSSAPISASLBindRequest(bindDN=");
        builder.append(getName());
        builder.append(", authentication=SASL");
        builder.append(", saslMechanism=");
        builder.append(getSASLMechanism());
        if (subject != null) {
            builder.append(", subject=");
            builder.append(subject);
        } else {
            builder.append(", authenticationID=");
            builder.append(authenticationID);
            builder.append(", authorizationID=");
            builder.append(authorizationID);
            builder.append(", realm=");
            builder.append(realm);
        }
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }

}
