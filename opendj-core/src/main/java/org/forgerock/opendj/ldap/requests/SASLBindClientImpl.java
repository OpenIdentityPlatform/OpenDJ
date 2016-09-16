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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_SASL_BIND_MULTI_STAGE;
import static com.forgerock.opendj.ldap.CoreMessages.INFO_SASL_UNSUPPORTED_CALLBACK;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.ChoiceCallback;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.LanguageCallback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.BindResult;

/**
 * SASL bind client implementation.
 */
class SASLBindClientImpl extends BindClientImpl implements CallbackHandler {
    /**
     * The name of the default protocol used.
     */
    static final String SASL_DEFAULT_PROTOCOL = "ldap";

    private final String saslMechanism;

    /**
     * Creates a new abstract SASL bind client. The next bind request will be a
     * copy of the provided initial bind request which should be updated in
     * subsequent bind requests forming part of this authentication.
     *
     * @param initialBindRequest
     *            The initial bind request.
     */
    SASLBindClientImpl(final SASLBindRequest initialBindRequest) {
        super(initialBindRequest);
        this.saslMechanism = initialBindRequest.getSASLMechanism();
    }

    @Override
    public final void handle(final Callback[] callbacks) throws IOException,
            UnsupportedCallbackException {
        for (final Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                handle((NameCallback) callback);
            } else if (callback instanceof PasswordCallback) {
                handle((PasswordCallback) callback);
            } else if (callback instanceof AuthorizeCallback) {
                handle((AuthorizeCallback) callback);
            } else if (callback instanceof RealmCallback) {
                handle((RealmCallback) callback);
            } else if (callback instanceof RealmChoiceCallback) {
                handle((RealmChoiceCallback) callback);
            } else if (callback instanceof ChoiceCallback) {
                handle((ChoiceCallback) callback);
            } else if (callback instanceof ConfirmationCallback) {
                handle((ConfirmationCallback) callback);
            } else if (callback instanceof LanguageCallback) {
                handle((LanguageCallback) callback);
            } else if (callback instanceof TextInputCallback) {
                handle((TextInputCallback) callback);
            } else if (callback instanceof TextOutputCallback) {
                handle((TextOutputCallback) callback);
            } else {
                final org.forgerock.i18n.LocalizableMessage message =
                        INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
                throw new UnsupportedCallbackException(callback, message.toString());
            }
        }
    }

    void handle(final AuthorizeCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final ChoiceCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final ConfirmationCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final LanguageCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final NameCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final PasswordCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final RealmCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final RealmChoiceCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final TextInputCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    void handle(final TextOutputCallback callback) throws UnsupportedCallbackException {
        final org.forgerock.i18n.LocalizableMessage message =
                INFO_SASL_UNSUPPORTED_CALLBACK.get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
    }

    /**
     * Sets the SASL credentials to be used in the next bind request.
     *
     * @param saslCredentials
     *            The SASL credentials to be used in the next bind request.
     * @return A reference to this SASL bind client.
     */
    final BindClient setNextSASLCredentials(final byte[] saslCredentials) {
        final ByteString value =
                (saslCredentials != null) ? ByteString.wrap(saslCredentials) : null;
        return setNextSASLCredentials(value);
    }

    /**
     * Sets the SASL credentials to be used in the next bind request.
     *
     * @param saslCredentials
     *            The SASL credentials to be used in the next bind request.
     * @return A reference to this SASL bind client.
     */
    final BindClient setNextSASLCredentials(final ByteString saslCredentials) {
        final ByteStringBuilder builder = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(builder);

        try {
            writer.writeOctetString(saslMechanism);
            if (saslCredentials != null) {
                writer.writeOctetString(saslCredentials);
            }
        } catch (final IOException ioe) {
            throw new RuntimeException("Error encoding SaslCredentials");
        }

        return setNextAuthenticationValue(builder.toByteString().toByteArray());
    }


    /**
     * Evaluates the {@link BindResult} returned by the server and returns {@code true} iff the server
     * has sent a challenge and the client needs to send additional data.
     * <p>
     * If the server has sent a challenge, this method evaluates it and prepare data to send in the next request.
     *
     * @param saslClient
     *          The {@link SaslClient} to use to evaluate the challenge.
     * @param result
     *          The last {@link BindResult} returned by the server.
     * @return {@code true} iff the server has sent a challenge and the client needs to send additional data.
     * @throws LdapException
     *          If an error occurred when the {@link SaslClient} evaluates the challenge.
     */
    boolean evaluateSaslBindResult(final SaslClient saslClient, final BindResult result) throws LdapException {
        if (saslClient.isComplete()) {
            return true;
        }

        try {
            final ByteString serverSASLCredentials = result.getServerSASLCredentials();
            final byte[] nextResponse = saslClient.evaluateChallenge(
                    serverSASLCredentials == null ? new byte[0]
                                                  : serverSASLCredentials.toByteArray());
            if (nextResponse == null) {
                return true;
            }
            setNextSASLCredentials(nextResponse);
            return false;
        } catch (final SaslException e) {
            throw newLdapException(ResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                   ERR_SASL_BIND_MULTI_STAGE.get(e.getLocalizedMessage()),
                                   e);
        }
    }
}
