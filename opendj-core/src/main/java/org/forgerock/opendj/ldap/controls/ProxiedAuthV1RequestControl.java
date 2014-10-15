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
 *      Portions copyright 2012-2014 ForgeRock AS.
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
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

/**
 * The proxy authorization v1 request control as defined in
 * draft-weltman-ldapv3-proxy-04. This control allows a user to request that an
 * operation be performed using the authorization of another user. The target
 * user is specified as a DN in the control value, which distinguishes it from
 * later versions of the control (which used a different OID) in which the
 * target user was specified using an authorization ID.
 * <p>
 * This control implementation is based on version 1 of the proxied
 * authorization control as defined in early versions of
 * draft-weltman-ldapv3-proxy (this implementation is based on the "-04"
 * revision) and is intended for use in legacy applications. New applications
 * should use the v2 version of this control in preference.
 *
 * @see <a href="http://tools.ietf.org/html/draft-weltman-ldapv3-proxy-04">
 *      draft-weltman-ldapv3-proxy-04 - LDAP Proxied Authorization Control </a>
 */
public final class ProxiedAuthV1RequestControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the proxied authorization v1 control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.12";

    /**
     * A decoder which can be used for decoding the proxied authorization v1
     * request control.
     */
    public static final ControlDecoder<ProxiedAuthV1RequestControl> DECODER =
            new ControlDecoder<ProxiedAuthV1RequestControl>() {

                public ProxiedAuthV1RequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof ProxiedAuthV1RequestControl) {
                        return (ProxiedAuthV1RequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_PROXYAUTH1_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.isCritical()) {
                        final LocalizableMessage message =
                                ERR_PROXYAUTH1_CONTROL_NOT_CRITICAL.get();
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = ERR_PROXYAUTH1_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    String authorizationDNString;
                    try {
                        reader.readStartSequence();
                        authorizationDNString = reader.readOctetStringAsString();
                        reader.readEndSequence();
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("Unable to read sequence", e));

                        final LocalizableMessage message =
                                ERR_PROXYAUTH1_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }

                    final Schema schema =
                            options.getSchemaResolver().resolveSchema(authorizationDNString);
                    DN authorizationDN;
                    try {
                        authorizationDN = DN.valueOf(authorizationDNString, schema);
                    } catch (final LocalizedIllegalArgumentException e) {
                        final LocalizableMessage message =
                                ERR_PROXYAUTH1_INVALID_AUTHZIDDN.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }

                    return new ProxiedAuthV1RequestControl(authorizationDN);
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new proxy authorization v1 request control with the provided
     * authorization name.
     *
     * @param authorizationName
     *            The distinguished name of the user whose authorization is to
     *            be used when performing the operation.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code authorizationName} was {@code null}.
     */
    public static ProxiedAuthV1RequestControl newControl(final DN authorizationName) {
        Reject.ifNull(authorizationName);
        return new ProxiedAuthV1RequestControl(authorizationName);
    }

    /**
     * Creates a new proxy authorization v1 request control with the provided
     * authorization name decoded using the default schema.
     *
     * @param authorizationName
     *            The distinguished name of the user whose authorization is to
     *            be used when performing the operation.
     * @return The new control.
     * @throws LocalizedIllegalArgumentException
     *             If {@code authorizationName} is not a valid LDAP string
     *             representation of a DN.
     * @throws NullPointerException
     *             If {@code authorizationName} was {@code null}.
     */
    public static ProxiedAuthV1RequestControl newControl(final String authorizationName) {
        Reject.ifNull(authorizationName);
        return new ProxiedAuthV1RequestControl(DN.valueOf(authorizationName));
    }

    private final DN authorizationName;

    private ProxiedAuthV1RequestControl(final DN authorizationName) {
        this.authorizationName = authorizationName;
    }

    /**
     * Returns the distinguished name of the user whose authorization is to be
     * used when performing the operation.
     *
     * @return The distinguished name of the user whose authorization is to be
     *         used when performing the operation.
     */
    public DN getAuthorizationDNName() {
        return authorizationName;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            writer.writeOctetString(authorizationName.toString());
            writer.writeEndSequence();
            return buffer.toByteString();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }
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
        final StringBuilder buffer = new StringBuilder();
        buffer.append("ProxiedAuthorizationV1Control(oid=");
        buffer.append(getOID());
        buffer.append(", criticality=");
        buffer.append(isCritical());
        buffer.append(", proxyDN=\"");
        buffer.append(authorizationName);
        buffer.append("\")");
        return buffer.toString();
    }
}
