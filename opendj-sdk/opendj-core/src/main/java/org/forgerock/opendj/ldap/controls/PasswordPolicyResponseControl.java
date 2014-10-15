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
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.byteToHex;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Reject;

/**
 * The password policy response control as defined in
 * draft-behera-ldap-password-policy.
 *
 * <pre>
 * Connection connection = ...;
 * String DN = ...;
 * char[] password = ...;
 *
 * try {
 *     BindRequest request = Requests.newSimpleBindRequest(DN, password)
 *             .addControl(PasswordPolicyRequestControl.newControl(true));
 *
 *     BindResult result = connection.bind(request);
 *
 *     PasswordPolicyResponseControl control =
 *             result.getControl(PasswordPolicyResponseControl.DECODER,
 *                     new DecodeOptions());
 *     if (!(control == null) && !(control.getWarningType() == null)) {
 *         // Password policy warning, use control.getWarningType(),
 *         // and control.getWarningValue().
 *     }
 * } catch (LdapException e) {
 *     Result result = e.getResult();
 *     try {
 *         PasswordPolicyResponseControl control =
 *                 result.getControl(PasswordPolicyResponseControl.DECODER,
 *                         new DecodeOptions());
 *         if (!(control == null)) {
 *             // Password policy error, use control.getErrorType().
 *         }
 *     } catch (DecodeException de) {
 *         // Failed to decode the response control.
 *     }
 * } catch (DecodeException e) {
 *     // Failed to decode the response control.
 * }
 * </pre>
 *
 * If the client has sent a passwordPolicyRequest control, the server (when
 * solicited by the inclusion of the request control) sends this control with
 * the following operation responses: bindResponse, modifyResponse, addResponse,
 * compareResponse and possibly extendedResponse, to inform of various
 * conditions, and MAY be sent with other operations (in the case of the
 * changeAfterReset error).
 *
 * @see PasswordPolicyRequestControl
 * @see PasswordPolicyWarningType
 * @see PasswordPolicyErrorType
 * @see <a href="http://tools.ietf.org/html/draft-behera-ldap-password-policy">
 *      draft-behera-ldap-password-policy - Password Policy for LDAP Directories
 *      </a>
 */
public final class PasswordPolicyResponseControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the password policy control from
     * draft-behera-ldap-password-policy.
     */
    public static final String OID = PasswordPolicyRequestControl.OID;

    private final int warningValue;

    private final PasswordPolicyErrorType errorType;

    private final PasswordPolicyWarningType warningType;

    /**
     * A decoder which can be used for decoding the password policy response
     * control.
     */
    public static final ControlDecoder<PasswordPolicyResponseControl> DECODER =
            new ControlDecoder<PasswordPolicyResponseControl>() {

                public PasswordPolicyResponseControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof PasswordPolicyResponseControl) {
                        return (PasswordPolicyResponseControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_PWPOLICYRES_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = ERR_PWPOLICYRES_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        PasswordPolicyWarningType warningType = null;
                        PasswordPolicyErrorType errorType = null;
                        int warningValue = -1;

                        reader.readStartSequence();

                        if (reader.hasNextElement() && (reader.peekType() == TYPE_WARNING_ELEMENT)) {
                            // Its a CHOICE element. Read as sequence to
                            // retrieve
                            // nested element.
                            reader.readStartSequence();
                            final int warningChoiceValue = (0x7F & reader.peekType());
                            if (warningChoiceValue < 0
                                    || warningChoiceValue >= PasswordPolicyWarningType.values().length) {
                                final LocalizableMessage message =
                                        ERR_PWPOLICYRES_INVALID_WARNING_TYPE.get(byteToHex(reader
                                                .peekType()));
                                throw DecodeException.error(message);
                            } else {
                                warningType =
                                        PasswordPolicyWarningType.values()[warningChoiceValue];
                            }
                            warningValue = (int) reader.readInteger();
                            reader.readEndSequence();
                        }

                        if (reader.hasNextElement() && (reader.peekType() == TYPE_ERROR_ELEMENT)) {
                            final int errorValue = reader.readEnumerated();
                            if (errorValue < 0
                                    || errorValue >= PasswordPolicyErrorType.values().length) {
                                final LocalizableMessage message =
                                        ERR_PWPOLICYRES_INVALID_ERROR_TYPE.get(errorValue);
                                throw DecodeException.error(message);
                            } else {
                                errorType = PasswordPolicyErrorType.values()[errorValue];
                            }
                        }

                        reader.readEndSequence();

                        return new PasswordPolicyResponseControl(control.isCritical(), warningType,
                                warningValue, errorType);
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("%s", e));

                        final LocalizableMessage message =
                                ERR_PWPOLICYRES_DECODE_ERROR.get(getExceptionMessage(e));
                        throw DecodeException.error(message);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new password policy response control with the provided error.
     *
     * @param errorType
     *            The password policy error type.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code errorType} was {@code null}.
     */
    public static PasswordPolicyResponseControl newControl(final PasswordPolicyErrorType errorType) {
        Reject.ifNull(errorType);

        return new PasswordPolicyResponseControl(false, null, -1, errorType);
    }

    /**
     * Creates a new password policy response control with the provided warning.
     *
     * @param warningType
     *            The password policy warning type.
     * @param warningValue
     *            The password policy warning value.
     * @return The new control.
     * @throws IllegalArgumentException
     *             If {@code warningValue} was negative.
     * @throws NullPointerException
     *             If {@code warningType} was {@code null}.
     */
    public static PasswordPolicyResponseControl newControl(
            final PasswordPolicyWarningType warningType, final int warningValue) {
        Reject.ifNull(warningType);
        Reject.ifFalse(warningValue >= 0, "warningValue is negative");

        return new PasswordPolicyResponseControl(false, warningType, warningValue, null);
    }

    /**
     * Creates a new password policy response control with the provided warning
     * and error.
     *
     * @param warningType
     *            The password policy warning type.
     * @param warningValue
     *            The password policy warning value.
     * @param errorType
     *            The password policy error type.
     * @return The new control.
     * @throws IllegalArgumentException
     *             If {@code warningValue} was negative.
     * @throws NullPointerException
     *             If {@code warningType} or {@code errorType} was {@code null}.
     */
    public static PasswordPolicyResponseControl newControl(
            final PasswordPolicyWarningType warningType, final int warningValue,
            final PasswordPolicyErrorType errorType) {
        Reject.ifNull(warningType);
        Reject.ifNull(errorType);
        Reject.ifFalse(warningValue >= 0, "warningValue is negative");

        return new PasswordPolicyResponseControl(false, warningType, warningValue, errorType);
    }

    private final boolean isCritical;

    /**
     * The BER type value for the warning element of the control value.
     */
    private static final byte TYPE_WARNING_ELEMENT = (byte) 0xA0;

    /**
     * The BER type value for the error element of the control value.
     */
    private static final byte TYPE_ERROR_ELEMENT = (byte) 0x81;

    private PasswordPolicyResponseControl(final boolean isCritical,
            final PasswordPolicyWarningType warningType, final int warningValue,
            final PasswordPolicyErrorType errorType) {
        this.isCritical = isCritical;
        this.warningType = warningType;
        this.warningValue = warningValue;
        this.errorType = errorType;
    }

    /**
     * Returns the password policy error type, if available.
     *
     * @return The password policy error type, or {@code null} if this control
     *         does not contain a error.
     */
    public PasswordPolicyErrorType getErrorType() {
        return errorType;
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
            if (warningType != null) {
                // Just write the CHOICE element as a single element SEQUENCE.
                writer.writeStartSequence(TYPE_WARNING_ELEMENT);
                writer.writeInteger((byte) (0x80 | warningType.intValue()), warningValue);
                writer.writeEndSequence();
            }

            if (errorType != null) {
                writer.writeInteger(TYPE_ERROR_ELEMENT, errorType.intValue());
            }
            writer.writeEndSequence();
            return buffer.toByteString();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Returns the password policy warning type, if available.
     *
     * @return The password policy warning type, or {@code null} if this control
     *         does not contain a warning.
     */
    public PasswordPolicyWarningType getWarningType() {
        return warningType;
    }

    /**
     * Returns the password policy warning value, if available. The value is
     * undefined if this control does not contain a warning.
     *
     * @return The password policy warning value, or {@code -1} if this control
     *         does not contain a warning.
     */
    public int getWarningValue() {
        return warningValue;
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
        builder.append("PasswordPolicyResponseControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        if (warningType != null) {
            builder.append(", warningType=");
            builder.append(warningType);
            builder.append(", warningValue=");
            builder.append(warningValue);
        }
        if (errorType != null) {
            builder.append(", errorType=");
            builder.append(errorType);
        }
        builder.append(")");
        return builder.toString();
    }
}
