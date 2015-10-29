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

package com.forgerock.opendj.ldap.extensions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.requests.AbstractExtendedRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequestDecoder;
import org.forgerock.opendj.ldap.responses.AbstractExtendedResultDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.ldap.extensions.PasswordPolicyStateOperationType.*;
import static com.forgerock.opendj.util.StaticUtils.*;

/**
 * This class implements an LDAP extended operation that can be used to query
 * and update elements of the Directory Server password policy state for a given
 * user. The ASN.1 definition for the value of the extended request is: <BR>
 *
 * <PRE>
 * PasswordPolicyStateValue ::= SEQUENCE {
 *      targetUser     LDAPDN
 *      operations     SEQUENCE OF PasswordPolicyStateOperation OPTIONAL }
 * PasswordPolicyStateOperation ::= SEQUENCE {
 *      opType       ENUMERATED {
 *           getPasswordPolicyDN                          (0),
 *           getAccountDisabledState                      (1),
 *           setAccountDisabledState                      (2),
 *           clearAccountDisabledState                    (3),
 *           getAccountExpirationTime                     (4),
 *           setAccountExpirationTime                     (5),
 *           clearAccountExpirationTime                   (6),
 *           getSecondsUntilAccountExpiration             (7),
 *           getPasswordChangedTime                       (8),
 *           setPasswordChangedTime                       (9),
 *           clearPasswordChangedTime                     (10),
 *           getPasswordExpirationWarnedTime              (11),
 *           setPasswordExpirationWarnedTime              (12),
 *           clearPasswordExpirationWarnedTime            (13),
 *           getSecondsUntilPasswordExpiration            (14),
 *           getSecondsUntilPasswordExpirationWarning     (15),
 *           getAuthenticationFailureTimes                (16),
 *           addAuthenticationFailureTime                 (17),
 *           setAuthenticationFailureTimes                (18),
 *           clearAuthenticationFailureTimes              (19),
 *           getSecondsUntilAuthenticationFailureUnlock   (20),
 *           getRemainingAuthenticationFailureCount       (21),
 *           getLastLoginTime                             (22),
 *           setLastLoginTime                             (23),
 *           clearLastLoginTime                           (24),
 *           getSecondsUntilIdleLockout                   (25),
 *           getPasswordResetState                        (26),
 *           setPasswordResetState                        (27),
 *           clearPasswordResetState                      (28),
 *           getSecondsUntilPasswordResetLockout          (29),
 *           getGraceLoginUseTimes                        (30),
 *           addGraceLoginUseTime                         (31),
 *           setGraceLoginUseTimes                        (32),
 *           clearGraceLoginUseTimes                      (33),
 *           getRemainingGraceLoginCount                  (34),
 *           getPasswordChangedByRequiredTime             (35),
 *           setPasswordChangedByRequiredTime             (36),
 *           clearPasswordChangedByRequiredTime           (37),
 *           getSecondsUntilRequiredChangeTime            (38),
 *           getPasswordHistory                           (39),
 *           clearPasswordHistory                         (40),
 *           ... },
 *      opValues     SEQUENCE OF OCTET STRING OPTIONAL }
 * </PRE>
 *
 * <BR>
 * Both the request and response values use the same encoded form, and they both
 * use the same OID of "1.3.6.1.4.1.26027.1.6.1". The response value will only
 * include get* elements. If the request did not include any operations, then
 * the response will include all get* elements; otherwise, the response will
 * only include the get* elements that correspond to the state fields referenced
 * in the request (regardless of whether that operation was included in a get*,
 * set*, add*, remove*, or clear* operation).
 */
public final class PasswordPolicyStateExtendedRequest
        extends
        AbstractExtendedRequest<PasswordPolicyStateExtendedRequest, PasswordPolicyStateExtendedResult>
        implements PasswordPolicyStateOperationContainer {
    private static final class MultiValueOperation implements PasswordPolicyStateOperation {
        private final PasswordPolicyStateOperationType property;

        private final List<ByteString> values;

        private MultiValueOperation(final PasswordPolicyStateOperationType property,
                final ByteString value) {
            this.property = property;
            this.values = Collections.singletonList(value);
        }

        private MultiValueOperation(final PasswordPolicyStateOperationType property,
                final List<ByteString> values) {
            this.property = property;
            this.values = values;
        }

        @Override
        public PasswordPolicyStateOperationType getOperationType() {
            return property;
        }

        @Override
        public Iterable<ByteString> getValues() {
            return values;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return property + ": " + values;
        }
    }

    private static final class RequestDecoder
            implements
            ExtendedRequestDecoder<PasswordPolicyStateExtendedRequest, PasswordPolicyStateExtendedResult> {

        @Override
        public PasswordPolicyStateExtendedRequest decodeExtendedRequest(
                final ExtendedRequest<?> request, final DecodeOptions options)
                throws DecodeException {
            final ByteString requestValue = request.getValue();
            if (requestValue == null || requestValue.length() <= 0) {
                throw DecodeException.error(ERR_PWPSTATE_EXTOP_NO_REQUEST_VALUE.get());
            }
            try {
                final ASN1Reader reader = ASN1.getReader(requestValue);
                reader.readStartSequence();

                // Read the target user DN
                final PasswordPolicyStateExtendedRequest newRequest =
                        new PasswordPolicyStateExtendedRequest();
                newRequest.setTargetUser(reader.readOctetStringAsString());

                decodeOperations(reader, newRequest);
                reader.readEndSequence();

                for (final Control control : request.getControls()) {
                    newRequest.addControl(control);
                }

                return newRequest;
            } catch (final IOException ioe) {
                final LocalizableMessage message =
                        ERR_PWPSTATE_EXTOP_DECODE_FAILURE.get(getExceptionMessage(ioe));
                throw DecodeException.error(message, ioe);
            }
        }
    }

    private static final class ResultDecoder extends
            AbstractExtendedResultDecoder<PasswordPolicyStateExtendedResult> {

        /** {@inheritDoc} */
        @Override
        public PasswordPolicyStateExtendedResult newExtendedErrorResult(
                final ResultCode resultCode, final String matchedDN, final String diagnosticMessage) {
            if (!resultCode.isExceptional()) {
                // A successful response must contain a response name and
                // value.
                throw new IllegalArgumentException("No response name and value for result code "
                        + resultCode.intValue());
            }

            return new PasswordPolicyStateExtendedResult(resultCode).setMatchedDN(
                    matchedDN).setDiagnosticMessage(diagnosticMessage);
        }

        @Override
        public PasswordPolicyStateExtendedResult decodeExtendedResult(final ExtendedResult result,
                final DecodeOptions options) throws DecodeException {
            final ResultCode resultCode = result.getResultCode();
            final PasswordPolicyStateExtendedResult newResult =
                    new PasswordPolicyStateExtendedResult(resultCode).setMatchedDN(
                            result.getMatchedDN()).setDiagnosticMessage(
                            result.getDiagnosticMessage());

            final ByteString responseValue = result.getValue();
            if (!resultCode.isExceptional() && responseValue == null) {
                throw DecodeException.error(ERR_PWPSTATE_EXTOP_NO_REQUEST_VALUE.get());
            }
            if (responseValue != null) {
                try {
                    final ASN1Reader reader = ASN1.getReader(responseValue);
                    reader.readStartSequence();
                    newResult.setTargetUser(reader.readOctetStringAsString());
                    decodeOperations(reader, newResult);
                    reader.readEndSequence();
                } catch (final IOException ioe) {
                    final LocalizableMessage message =
                            ERR_PWPSTATE_EXTOP_DECODE_FAILURE.get(getExceptionMessage(ioe));
                    throw DecodeException.error(message, ioe);
                }
            }
            for (final Control control : result.getControls()) {
                newResult.addControl(control);
            }
            return newResult;
        }
    }

    /**
     * The OID for the password policy state extended operation (both the
     * request and response types).
     */
    public static final String OID = "1.3.6.1.4.1.26027.1.6.1";

    private String targetUser = "";

    private final List<PasswordPolicyStateOperation> operations = new ArrayList<>();

    static final String PASSWORD_POLICY_DN_NAME = "Password Policy DN";
    static final String ACCOUNT_DISABLED_STATE_NAME = "Account Disabled State";
    static final String ACCOUNT_EXPIRATION_TIME_NAME = "Account Expiration Time";
    static final String SECONDS_UNTIL_ACCOUNT_EXPIRATION_NAME = "Seconds Until Account Expiration";
    static final String PASSWORD_CHANGED_TIME_NAME = "Password Changed Time";
    static final String PASSWORD_EXPIRATION_WARNED_TIME_NAME = "Password Expiration Warned Time";
    static final String SECONDS_UNTIL_PASSWORD_EXPIRATION_NAME =
            "Seconds Until Password Expiration";
    static final String SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING_NAME =
            "Seconds Until Password Expiration Warning";
    static final String AUTHENTICATION_FAILURE_TIMES_NAME = "Authentication Failure Times";
    static final String SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK_NAME =
            "Seconds Until Authentication Failure Unlock";
    static final String REMAINING_AUTHENTICATION_FAILURE_COUNT_NAME =
            "Remaining Authentication Failure Count";
    static final String LAST_LOGIN_TIME_NAME = "Last Login Time";
    static final String SECONDS_UNTIL_IDLE_LOCKOUT_NAME = "Seconds Until Idle Lockout";
    static final String PASSWORD_RESET_STATE_NAME = "Password Reset State";
    static final String SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT_NAME =
            "Seconds Until Password Reset Lockout";
    static final String GRACE_LOGIN_USE_TIMES_NAME = "Grace Login Use Times";
    static final String REMAINING_GRACE_LOGIN_COUNT_NAME = "Remaining Grace Login Count";
    static final String PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME =
            "Password Changed By Required Time";
    static final String SECONDS_UNTIL_REQUIRED_CHANGE_TIME_NAME =
            "Seconds Until Required Change Time";
    static final String PASSWORD_HISTORY_NAME = "Password History";

    /**
     * A decoder which can be used to decode password policy state extended
     * operation requests.
     */
    public static final RequestDecoder REQUEST_DECODER = new RequestDecoder();

    /** No need to expose this. */
    private static final ResultDecoder RESULT_DECODER = new ResultDecoder();

    static ByteString encode(final String targetUser,
            final List<PasswordPolicyStateOperation> operations) {
        final ByteStringBuilder buffer = new ByteStringBuilder(6);
        final ASN1Writer writer = ASN1.getWriter(buffer);

        try {
            writer.writeStartSequence();
            writer.writeOctetString(targetUser);
            if (!operations.isEmpty()) {
                writer.writeStartSequence();
                for (final PasswordPolicyStateOperation operation : operations) {
                    writer.writeStartSequence();
                    writer.writeEnumerated(operation.getOperationType().ordinal());
                    if (operation.getValues() != null) {
                        writer.writeStartSequence();
                        for (final ByteString value : operation.getValues()) {
                            writer.writeOctetString(value);
                        }
                        writer.writeEndSequence();
                    }
                    writer.writeEndSequence();
                }
                writer.writeEndSequence();
            }
            writer.writeEndSequence();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }

        return buffer.toByteString();
    }

    private static void decodeOperations(final ASN1Reader reader,
            final PasswordPolicyStateOperationContainer container) throws IOException,
            DecodeException {
        // See if we have operations
        if (reader.hasNextElement()) {
            reader.readStartSequence();
            int opType;
            PasswordPolicyStateOperationType type;
            while (reader.hasNextElement()) {
                reader.readStartSequence();
                // Read the opType
                opType = reader.readEnumerated();
                try {
                    type = PasswordPolicyStateOperationType.values()[opType];
                } catch (final IndexOutOfBoundsException iobe) {
                    throw DecodeException.error(ERR_PWPSTATE_EXTOP_UNKNOWN_OP_TYPE.get(String
                            .valueOf(opType)), iobe);
                }

                // See if we have any values
                if (reader.hasNextElement()) {
                    reader.readStartSequence();
                    final ArrayList<ByteString> values = new ArrayList<>();
                    while (reader.hasNextElement()) {
                        values.add(reader.readOctetString());
                    }
                    reader.readEndSequence();
                    container.addOperation(new MultiValueOperation(type, values));
                } else {
                    container.addOperation(type);
                }
                reader.readEndSequence();
            }
            reader.readEndSequence();
        }
    }

    /**
     * Creates a new password policy state extended request.
     */
    public PasswordPolicyStateExtendedRequest() {
        // Nothing to do.
    }

    /**
     * Adds the provided authentication failure time to this request.
     *
     * @param date
     *            The authentication failure time.
     */
    public void addAuthenticationFailureTime(final Date date) {
        setDateProperty(ADD_AUTHENTICATION_FAILURE_TIMES, date);
    }

    /**
     * Adds the provided grace login use time to this request.
     *
     * @param date
     *            The grace login use time.
     */
    public void addGraceLoginUseTime(final Date date) {
        setDateProperty(ADD_GRACE_LOGIN_USE_TIME, date);
    }

    /** {@inheritDoc} */
    @Override
    public void addOperation(final PasswordPolicyStateOperation operation) {
        operations.add(operation);
    }

    /**
     * Clears the account disabled state.
     */
    public void clearAccountDisabledState() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_ACCOUNT_DISABLED_STATE);
    }

    /**
     * Clears the account expiration time.
     */
    public void clearAccountExpirationTime() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_ACCOUNT_EXPIRATION_TIME);
    }

    /**
     * Clears the authentication failure times.
     */
    public void clearAuthenticationFailureTimes() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_AUTHENTICATION_FAILURE_TIMES);
    }

    /**
     * Clears the grace login use times.
     */
    public void clearGraceLoginUseTimes() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_GRACE_LOGIN_USE_TIMES);
    }

    /**
     * Clears the last login time.
     */
    public void clearLastLoginTime() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_LAST_LOGIN_TIME);
    }

    /**
     * Clears the password changed by required time.
     */
    public void clearPasswordChangedByRequiredTime() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME);
    }

    /**
     * Clears the password changed time.
     */
    public void clearPasswordChangedTime() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_PASSWORD_CHANGED_TIME);
    }

    /**
     * Clears the password expiration warned time.
     */
    public void clearPasswordExpirationWarnedTime() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_PASSWORD_EXPIRATION_WARNED_TIME);
    }

    /**
     * Clears the password history.
     */
    public void clearPasswordHistory() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_PASSWORD_HISTORY);
    }

    /**
     * Clears the password reset state.
     */
    public void clearPasswordResetState() {
        operations.add(PasswordPolicyStateOperationType.CLEAR_PASSWORD_RESET_STATE);
    }

    /** {@inheritDoc} */
    @Override
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    @Override
    public Iterable<PasswordPolicyStateOperation> getOperations() {
        return operations;
    }

    /** {@inheritDoc} */
    @Override
    public ExtendedResultDecoder<PasswordPolicyStateExtendedResult> getResultDecoder() {
        return RESULT_DECODER;
    }

    /** {@inheritDoc} */
    @Override
    public String getTargetUser() {
        return targetUser;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString getValue() {
        return encode(targetUser, operations);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasValue() {
        return true;
    }

    /**
     * Returns the account disabled state.
     */
    public void requestAccountDisabledState() {
        operations.add(PasswordPolicyStateOperationType.GET_ACCOUNT_DISABLED_STATE);
    }

    /**
     * Returns the account expiration time.
     */
    public void requestAccountExpirationTime() {
        operations.add(PasswordPolicyStateOperationType.GET_ACCOUNT_EXPIRATION_TIME);
    }

    /**
     * Returns the authentication failure times.
     */
    public void requestAuthenticationFailureTimes() {
        operations.add(PasswordPolicyStateOperationType.GET_AUTHENTICATION_FAILURE_TIMES);
    }

    /**
     * Returns the grace login use times.
     */
    public void requestGraceLoginUseTimes() {
        operations.add(PasswordPolicyStateOperationType.GET_GRACE_LOGIN_USE_TIMES);
    }

    /**
     * Returns the last login time.
     */
    public void requestLastLoginTime() {
        operations.add(PasswordPolicyStateOperationType.GET_LAST_LOGIN_TIME);
    }

    /**
     * Returns the password changed by required time.
     */
    public void requestPasswordChangedByRequiredTime() {
        operations.add(PasswordPolicyStateOperationType.GET_PASSWORD_CHANGED_BY_REQUIRED_TIME);
    }

    /**
     * Returns the password changed time.
     */
    public void requestPasswordChangedTime() {
        operations.add(PasswordPolicyStateOperationType.GET_PASSWORD_CHANGED_TIME);
    }

    /**
     * Returns the password expiration warned time.
     */
    public void requestPasswordExpirationWarnedTime() {
        operations.add(PasswordPolicyStateOperationType.GET_PASSWORD_EXPIRATION_WARNED_TIME);
    }

    /**
     * Returns the password history.
     */
    public void requestPasswordHistory() {
        operations.add(PasswordPolicyStateOperationType.GET_PASSWORD_HISTORY);
    }

    /**
     * Returns the password policy DN.
     */
    public void requestPasswordPolicyDN() {
        operations.add(PasswordPolicyStateOperationType.GET_PASSWORD_POLICY_DN);
    }

    /**
     * Returns the password reset state.
     */
    public void requestPasswordResetState() {
        operations.add(PasswordPolicyStateOperationType.GET_PASSWORD_RESET_STATE);
    }

    /**
     * Returns the remaining authentication failure count.
     */
    public void requestRemainingAuthenticationFailureCount() {
        operations.add(PasswordPolicyStateOperationType.GET_REMAINING_AUTHENTICATION_FAILURE_COUNT);
    }

    /**
     * Returns the remaining grace login count.
     */
    public void requestRemainingGraceLoginCount() {
        operations.add(PasswordPolicyStateOperationType.GET_REMAINING_GRACE_LOGIN_COUNT);
    }

    /**
     * Returns the seconds until account expiration.
     */
    public void requestSecondsUntilAccountExpiration() {
        operations.add(PasswordPolicyStateOperationType.GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION);
    }

    /**
     * Returns the seconds until authentication failure unlock.
     */
    public void requestSecondsUntilAuthenticationFailureUnlock() {
        operations
                .add(PasswordPolicyStateOperationType.GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK);
    }

    /**
     * Returns the seconds until idle lockout.
     */
    public void requestSecondsUntilIdleLockout() {
        operations.add(PasswordPolicyStateOperationType.GET_SECONDS_UNTIL_IDLE_LOCKOUT);
    }

    /**
     * Returns the seconds until password expiration.
     */
    public void requestSecondsUntilPasswordExpiration() {
        operations.add(PasswordPolicyStateOperationType.GET_SECONDS_UNTIL_PASSWORD_EXPIRATION);
    }

    /**
     * Returns the seconds until password expiration warning.
     */
    public void requestSecondsUntilPasswordExpirationWarning() {
        operations
                .add(PasswordPolicyStateOperationType.GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING);
    }

    /**
     * Returns the seconds until password reset lockout.
     */
    public void requestSecondsUntilPasswordResetLockout() {
        operations.add(PasswordPolicyStateOperationType.GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT);
    }

    /**
     * Returns the seconds until required change time.
     */
    public void requestSecondsUntilRequiredChangeTime() {
        operations.add(PasswordPolicyStateOperationType.GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME);
    }

    /**
     * Sets the account disabled state.
     *
     * @param state
     *            The account disabled state.
     */
    public void setAccountDisabledState(final boolean state) {
        setBooleanProperty(SET_ACCOUNT_DISABLED_STATE, state);
    }

    /**
     * Sets the account expiration time.
     *
     * @param date
     *            The account expiration time.
     */
    public void setAccountExpirationTime(final Date date) {
        setDateProperty(SET_ACCOUNT_EXPIRATION_TIME, date);
    }

    /**
     * Sets the authentication failure times.
     *
     * @param dates
     *            The authentication failure times.
     */
    public void setAuthenticationFailureTimes(final Date... dates) {
        setDateProperties(SET_AUTHENTICATION_FAILURE_TIMES, dates);
    }

    /**
     * Sets the grace login use times.
     *
     * @param dates
     *            The grace login use times.
     */
    public void setGraceLoginUseTimes(final Date... dates) {
        setDateProperties(SET_GRACE_LOGIN_USE_TIMES, dates);
    }

    /**
     * Sets the last login time.
     *
     * @param date
     *            The last login time.
     */
    public void setLastLoginTime(final Date date) {
        setDateProperty(SET_LAST_LOGIN_TIME, date);
    }

    /**
     * Sets the password changed by required time.
     *
     * @param state
     *            The password changed by required time.
     */
    public void setPasswordChangedByRequiredTime(final boolean state) {
        setBooleanProperty(SET_PASSWORD_CHANGED_BY_REQUIRED_TIME, state);
    }

    /**
     * Sets the password changed time.
     *
     * @param date
     *            The password changed time.
     */
    public void setPasswordChangedTime(final Date date) {
        setDateProperty(SET_PASSWORD_CHANGED_TIME, date);
    }

    /**
     * Sets the password expiration warned time.
     *
     * @param date
     *            The password expiration warned time.
     */
    public void setPasswordExpirationWarnedTime(final Date date) {
        setDateProperty(SET_PASSWORD_EXPIRATION_WARNED_TIME, date);
    }

    /**
     * Sets the password reset state.
     *
     * @param state
     *            The password reset state.
     */
    public void setPasswordResetState(final boolean state) {
        setBooleanProperty(SET_PASSWORD_RESET_STATE, state);
    }

    private void setBooleanProperty(PasswordPolicyStateOperationType property, final boolean state) {
        operations.add(new MultiValueOperation(property, ByteString.valueOfUtf8(String.valueOf(state))));
    }

    private void setDateProperty(PasswordPolicyStateOperationType property, final Date date) {
        if (date != null) {
            operations.add(new MultiValueOperation(property, toByteString(date)));
        } else {
            operations.add(property);
        }
    }

    private void setDateProperties(PasswordPolicyStateOperationType property, final Date... dates) {
        if (dates == null) {
            operations.add(property);
        } else {
            final ArrayList<ByteString> times = new ArrayList<>(dates.length);
            for (final Date date : dates) {
                times.add(toByteString(date));
            }
            operations.add(new MultiValueOperation(property, times));
        }
    }

    private ByteString toByteString(final Date date) {
        return ByteString.valueOfUtf8(formatAsGeneralizedTime(date));
    }

    /** {@inheritDoc} */
    @Override
    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser != null ? targetUser : "";
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PasswordPolicyStateExtendedRequest(requestName=");
        builder.append(getOID());
        builder.append(", targetUser=");
        builder.append(targetUser);
        builder.append(", operations=");
        builder.append(operations);
        builder.append(", controls=");
        builder.append(getControls());
        builder.append(")");
        return builder.toString();
    }
}
