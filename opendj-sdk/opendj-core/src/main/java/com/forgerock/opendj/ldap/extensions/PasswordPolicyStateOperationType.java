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
 */

package com.forgerock.opendj.ldap.extensions;

import org.forgerock.opendj.ldap.ByteString;

/**
 * Password policy state operation type.
 */
public enum PasswordPolicyStateOperationType implements PasswordPolicyStateOperation {
    /**
     * Get password policy DN operation.
     */
    GET_PASSWORD_POLICY_DN(PasswordPolicyStateExtendedRequest.PASSWORD_POLICY_DN_NAME),

    /**
     * Get account disabled state operation.
     */
    GET_ACCOUNT_DISABLED_STATE(PasswordPolicyStateExtendedRequest.ACCOUNT_DISABLED_STATE_NAME),

    /**
     * Set account disabled state operation.
     */
    SET_ACCOUNT_DISABLED_STATE(PasswordPolicyStateExtendedRequest.ACCOUNT_DISABLED_STATE_NAME),

    /**
     * Clear account disabled state operation.
     */
    CLEAR_ACCOUNT_DISABLED_STATE(PasswordPolicyStateExtendedRequest.ACCOUNT_DISABLED_STATE_NAME),

    /**
     * Get account expiration time operation.
     */
    GET_ACCOUNT_EXPIRATION_TIME(PasswordPolicyStateExtendedRequest.ACCOUNT_EXPIRATION_TIME_NAME),

    /**
     * Set account expiration time operation.
     */
    SET_ACCOUNT_EXPIRATION_TIME(PasswordPolicyStateExtendedRequest.ACCOUNT_EXPIRATION_TIME_NAME),

    /**
     * Clear account expiration time operation.
     */
    CLEAR_ACCOUNT_EXPIRATION_TIME(PasswordPolicyStateExtendedRequest.ACCOUNT_EXPIRATION_TIME_NAME),

    /**
     * Get seconds until account expiration operation.
     */
    GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION(
            PasswordPolicyStateExtendedRequest.SECONDS_UNTIL_ACCOUNT_EXPIRATION_NAME),

    /**
     * Get password changed time operation.
     */
    GET_PASSWORD_CHANGED_TIME(PasswordPolicyStateExtendedRequest.PASSWORD_CHANGED_TIME_NAME),

    /**
     * Set password changed time operation.
     */
    SET_PASSWORD_CHANGED_TIME(PasswordPolicyStateExtendedRequest.PASSWORD_CHANGED_TIME_NAME),

    /**
     * Clear password changed time operation.
     */
    CLEAR_PASSWORD_CHANGED_TIME(PasswordPolicyStateExtendedRequest.PASSWORD_CHANGED_TIME_NAME),

    /**
     * Get password expiration warned time operation.
     */
    GET_PASSWORD_EXPIRATION_WARNED_TIME(
            PasswordPolicyStateExtendedRequest.PASSWORD_EXPIRATION_WARNED_TIME_NAME),

    /**
     * Set password expiration warned time operation.
     */
    SET_PASSWORD_EXPIRATION_WARNED_TIME(
            PasswordPolicyStateExtendedRequest.PASSWORD_EXPIRATION_WARNED_TIME_NAME),

    /**
     * Clear password expiration warned time operation.
     */
    CLEAR_PASSWORD_EXPIRATION_WARNED_TIME(
            PasswordPolicyStateExtendedRequest.PASSWORD_EXPIRATION_WARNED_TIME_NAME),

    /**
     * Get seconds until password expiration operation.
     */
    GET_SECONDS_UNTIL_PASSWORD_EXPIRATION(
            PasswordPolicyStateExtendedRequest.SECONDS_UNTIL_PASSWORD_EXPIRATION_NAME),

    /**
     * Get seconds until password expiration warning operation.
     */
    GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING(
            PasswordPolicyStateExtendedRequest.SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING_NAME),

    /**
     * Get authentication failure times operation.
     */
    GET_AUTHENTICATION_FAILURE_TIMES(
            PasswordPolicyStateExtendedRequest.AUTHENTICATION_FAILURE_TIMES_NAME),

    /**
     * Add authentication failure times operation.
     */
    ADD_AUTHENTICATION_FAILURE_TIMES(
            PasswordPolicyStateExtendedRequest.AUTHENTICATION_FAILURE_TIMES_NAME),

    /**
     * Set authentication failure times operation.
     */
    SET_AUTHENTICATION_FAILURE_TIMES(
            PasswordPolicyStateExtendedRequest.AUTHENTICATION_FAILURE_TIMES_NAME),

    /**
     * Clear authentication failure times operation.
     */
    CLEAR_AUTHENTICATION_FAILURE_TIMES(
            PasswordPolicyStateExtendedRequest.AUTHENTICATION_FAILURE_TIMES_NAME),

    /**
     * Get seconds until authentication failure unlock operation.
     */
    GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK(
            PasswordPolicyStateExtendedRequest.SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK_NAME),

    /**
     * Get remaining authentication failure count operation.
     */
    GET_REMAINING_AUTHENTICATION_FAILURE_COUNT(
            PasswordPolicyStateExtendedRequest.REMAINING_AUTHENTICATION_FAILURE_COUNT_NAME),

    /**
     * Get last login time operation.
     */
    GET_LAST_LOGIN_TIME(PasswordPolicyStateExtendedRequest.LAST_LOGIN_TIME_NAME),

    /**
     * Set last login time operation.
     */
    SET_LAST_LOGIN_TIME(PasswordPolicyStateExtendedRequest.LAST_LOGIN_TIME_NAME),

    /**
     * Clear last login time operation.
     */
    CLEAR_LAST_LOGIN_TIME(PasswordPolicyStateExtendedRequest.LAST_LOGIN_TIME_NAME),

    /**
     * Get seconds until idle lockout operation.
     */
    GET_SECONDS_UNTIL_IDLE_LOCKOUT(
            PasswordPolicyStateExtendedRequest.SECONDS_UNTIL_IDLE_LOCKOUT_NAME),

    /**
     * Get password reset state operation.
     */
    GET_PASSWORD_RESET_STATE(PasswordPolicyStateExtendedRequest.PASSWORD_RESET_STATE_NAME),

    /**
     * Set password reset state operation.
     */
    SET_PASSWORD_RESET_STATE(PasswordPolicyStateExtendedRequest.PASSWORD_RESET_STATE_NAME),

    /**
     * Clear password reset state operation.
     */
    CLEAR_PASSWORD_RESET_STATE(PasswordPolicyStateExtendedRequest.PASSWORD_RESET_STATE_NAME),

    /**
     * Get seconds until password reset lockout operation.
     */
    GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT(
            PasswordPolicyStateExtendedRequest.SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT_NAME),

    /**
     * Get grace login use times operation.
     */
    GET_GRACE_LOGIN_USE_TIMES(PasswordPolicyStateExtendedRequest.GRACE_LOGIN_USE_TIMES_NAME),

    /**
     * Add grace login use times operation.
     */
    ADD_GRACE_LOGIN_USE_TIME(PasswordPolicyStateExtendedRequest.GRACE_LOGIN_USE_TIMES_NAME),

    /**
     * Set grace login use times operation.
     */
    SET_GRACE_LOGIN_USE_TIMES(PasswordPolicyStateExtendedRequest.GRACE_LOGIN_USE_TIMES_NAME),

    /**
     * Clear grace login use times operation.
     */
    CLEAR_GRACE_LOGIN_USE_TIMES(PasswordPolicyStateExtendedRequest.GRACE_LOGIN_USE_TIMES_NAME),

    /**
     * Get remaining grace login count operation.
     */
    GET_REMAINING_GRACE_LOGIN_COUNT(
            PasswordPolicyStateExtendedRequest.REMAINING_GRACE_LOGIN_COUNT_NAME),

    /**
     * Get password changed by required time operation.
     */
    GET_PASSWORD_CHANGED_BY_REQUIRED_TIME(
            PasswordPolicyStateExtendedRequest.PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME),

    /**
     * Set password changed by required time operation.
     */
    SET_PASSWORD_CHANGED_BY_REQUIRED_TIME(
            PasswordPolicyStateExtendedRequest.PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME),

    /**
     * Clear password changed by required time operation.
     */
    CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME(
            PasswordPolicyStateExtendedRequest.PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME),

    /**
     * Get seconds until required change time operation.
     */
    GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME(
            PasswordPolicyStateExtendedRequest.SECONDS_UNTIL_REQUIRED_CHANGE_TIME_NAME),

    /**
     * Get password history operation.
     */
    GET_PASSWORD_HISTORY(PasswordPolicyStateExtendedRequest.PASSWORD_HISTORY_NAME),

    /**
     * Clear password history operation.
     */
    CLEAR_PASSWORD_HISTORY(PasswordPolicyStateExtendedRequest.PASSWORD_HISTORY_NAME);

    private String propertyName;

    private PasswordPolicyStateOperationType(final String propertyName) {
        this.propertyName = propertyName;
    }

    /** {@inheritDoc} */
    public PasswordPolicyStateOperationType getOperationType() {
        return this;
    }

    /** {@inheritDoc} */
    public Iterable<ByteString> getValues() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return propertyName;
    }
}
