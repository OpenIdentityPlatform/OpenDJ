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
 *      Portions copyright 2013-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Reject;

/**
 * The Netscape password expiring response control as defined in
 * draft-vchu-ldap-pwd-policy. This control serves as a warning to clients that
 * the user's password is about to expire. The only element contained in the
 * control value is a string representation of the number of seconds until
 * expiration.
 *
 * <pre>
 * Connection connection = ...;
 * String DN = ...;
 * char[] password = ...;
 *
 * BindResult result = connection.bind(DN, password);
 * try {
 *     PasswordExpiringResponseControl control =
 *             result.getControl(PasswordExpiringResponseControl.DECODER,
 *                     new DecodeOptions());
 *     if (!(control == null) && control.hasValue()) {
 *         // Password expires in control.getSecondsUntilExpiration() seconds
 *     }
 * } catch (DecodeException de) {
 *     // Failed to decode the response control.
 * }
 * </pre>
 *
 * @see <a
 *      href="http://tools.ietf.org/html/draft-vchu-ldap-pwd-policy">draft-vchu-ldap-pwd-policy
 *      - Password Policy for LDAP Directories </a>
 */
public final class PasswordExpiringResponseControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the Netscape password expiring response control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.5";

    /**
     * A decoder which can be used for decoding the password expiring response
     * control.
     */
    public static final ControlDecoder<PasswordExpiringResponseControl> DECODER =
            new ControlDecoder<PasswordExpiringResponseControl>() {

                public PasswordExpiringResponseControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof PasswordExpiringResponseControl) {
                        return (PasswordExpiringResponseControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_PWEXPIRING_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        final LocalizableMessage message = ERR_PWEXPIRING_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    int secondsUntilExpiration;
                    try {
                        secondsUntilExpiration = Integer.parseInt(control.getValue().toString());
                    } catch (final Exception e) {
                        logger.debug(LocalizableMessage.raw("%s", e));

                        final LocalizableMessage message =
                                ERR_PWEXPIRING_CANNOT_DECODE_SECONDS_UNTIL_EXPIRATION
                                        .get(getExceptionMessage(e));
                        throw DecodeException.error(message);
                    }

                    return new PasswordExpiringResponseControl(control.isCritical(),
                            secondsUntilExpiration);
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new Netscape password expiring response control with the
     * provided amount of time until expiration.
     *
     * @param secondsUntilExpiration
     *            The length of time in seconds until the password actually
     *            expires.
     * @return The new control.
     */
    public static PasswordExpiringResponseControl newControl(final int secondsUntilExpiration) {
        return new PasswordExpiringResponseControl(false, secondsUntilExpiration);
    }

    /** The length of time in seconds until the password actually expires. */
    private final int secondsUntilExpiration;

    private final boolean isCritical;

    private PasswordExpiringResponseControl(final boolean isCritical,
            final int secondsUntilExpiration) {
        this.isCritical = isCritical;
        this.secondsUntilExpiration = secondsUntilExpiration;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /**
     * Returns the length of time in seconds until the password actually
     * expires.
     *
     * @return The length of time in seconds until the password actually
     *         expires.
     */
    public int getSecondsUntilExpiration() {
        return secondsUntilExpiration;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        return ByteString.valueOfUtf8(String.valueOf(secondsUntilExpiration));
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        return true;
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PasswordExpiringResponseControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", secondsUntilExpiration=");
        builder.append(secondsUntilExpiration);
        builder.append(")");
        return builder.toString();
    }
}
