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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Reject;

/**
 * The proxy authorization v2 request control as defined in RFC 4370. This
 * control allows a user to request that an operation be performed using the
 * authorization of another user.
 * <p>
 * The target user is specified using an authorization ID, or {@code authzId},
 * as defined in RFC 4513 section 5.2.1.8.
 * <p>
 * This example shows an application replacing a description on a user entry on
 * behalf of a directory administrator.
 *
 * <pre>
 * Connection connection = ...;
 * String bindDN = "cn=My App,ou=Apps,dc=example,dc=com";          // Client app
 * char[] password = ...;
 * String targetDn = "uid=bjensen,ou=People,dc=example,dc=com";    // Regular user
 * String authzId = "dn:uid=kvaughan,ou=People,dc=example,dc=com"; // Admin user
 *
 * ModifyRequest request =
 *         Requests.newModifyRequest(targetDn)
 *         .addControl(ProxiedAuthV2RequestControl.newControl(authzId))
 *         .addModification(ModificationType.REPLACE, "description",
 *                 "Done with proxied authz");
 *
 * connection.bind(bindDN, password);
 * connection.modify(request);
 * Entry entry = connection.readEntry(targetDn, "description");
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4370">RFC 4370 - Lightweight
 *      Directory Access Protocol (LDAP) Proxied Authorization Control </a>
 * @see <a href="http://tools.ietf.org/html/rfc4513#section-5.2.1.8">RFC 4513 -
 *      SASL Authorization Identities (authzId) </a>
 */
public final class ProxiedAuthV2RequestControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the proxied authorization v2 control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.18";

    private static final ProxiedAuthV2RequestControl ANONYMOUS =
            new ProxiedAuthV2RequestControl("");

    /**
     * A decoder which can be used for decoding the proxied authorization v2
     * request control.
     */
    public static final ControlDecoder<ProxiedAuthV2RequestControl> DECODER =
            new ControlDecoder<ProxiedAuthV2RequestControl>() {

                public ProxiedAuthV2RequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof ProxiedAuthV2RequestControl) {
                        return (ProxiedAuthV2RequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_PROXYAUTH2_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.isCritical()) {
                        final LocalizableMessage message =
                                ERR_PROXYAUTH2_CONTROL_NOT_CRITICAL.get();
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = ERR_PROXYAUTH2_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    String authorizationID;
                    try {
                        if (reader.elementAvailable()) {
                            // Try the legacy encoding where the value is
                            // wrapped by an extra octet string
                            authorizationID = reader.readOctetStringAsString();
                        } else {
                            authorizationID = control.getValue().toString();
                        }
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("Unable to read exceptionID", e));

                        final LocalizableMessage message =
                                ERR_PROXYAUTH2_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }

                    if (authorizationID.length() == 0) {
                        // Anonymous.
                        return ANONYMOUS;
                    }

                    final int colonIndex = authorizationID.indexOf(':');
                    if (colonIndex < 0) {
                        final LocalizableMessage message =
                                ERR_PROXYAUTH2_INVALID_AUTHZID_TYPE.get(authorizationID);
                        throw DecodeException.error(message);
                    }

                    return new ProxiedAuthV2RequestControl(authorizationID);
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new proxy authorization v2 request control with the provided
     * authorization ID. The authorization ID usually has the form "dn:"
     * immediately followed by the distinguished name of the user, or "u:"
     * followed by a user ID string, but other forms are permitted.
     *
     * @param authorizationID
     *            The authorization ID of the user whose authorization is to be
     *            used when performing the operation.
     * @return The new control.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authorizationID} was non-empty and did not contain
     *             a valid authorization ID type.
     * @throws NullPointerException
     *             If {@code authorizationName} was {@code null}.
     */
    public static ProxiedAuthV2RequestControl newControl(final String authorizationID) {
        if (authorizationID.length() == 0) {
            // Anonymous.
            return ANONYMOUS;
        }

        final int colonIndex = authorizationID.indexOf(':');
        if (colonIndex < 0) {
            final LocalizableMessage message =
                    ERR_PROXYAUTH2_INVALID_AUTHZID_TYPE.get(authorizationID);
            throw new LocalizedIllegalArgumentException(message);
        }

        return new ProxiedAuthV2RequestControl(authorizationID);
    }

    /** The authorization ID from the control value. */
    private final String authorizationID;

    private ProxiedAuthV2RequestControl(final String authorizationID) {
        this.authorizationID = authorizationID;
    }

    /**
     * Returns the authorization ID of the user whose authorization is to be
     * used when performing the operation. The authorization ID usually has the
     * form "dn:" immediately followed by the distinguished name of the user, or
     * "u:" followed by a user ID string, but other forms are permitted.
     *
     * @return The authorization ID of the user whose authorization is to be
     *         used when performing the operation.
     */
    public String getAuthorizationID() {
        return authorizationID;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        return ByteString.valueOfUtf8(authorizationID);
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        return true;
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ProxiedAuthorizationV2Control(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", authorizationID=\"");
        builder.append(authorizationID);
        builder.append("\")");
        return builder.toString();
    }
}
