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
package org.forgerock.opendj.ldap.controls;

/**
 * A password policy error type as defined in draft-behera-ldap-password-policy
 * is used to indicate problems concerning a user's account or password.
 *
 * @see PasswordPolicyRequestControl
 * @see PasswordPolicyResponseControl
 * @see PasswordPolicyWarningType
 * @see <a href="http://tools.ietf.org/html/draft-behera-ldap-password-policy">
 *      draft-behera-ldap-password-policy - Password Policy for LDAP Directories
 *      </a>
 */
public enum PasswordPolicyErrorType {

    /**
     * Indicates that the password has expired and must be reset.
     */
    PASSWORD_EXPIRED(0, "passwordExpired"),

    /**
     * Indicates that the user's account has been locked.
     */
    ACCOUNT_LOCKED(1, "accountLocked"),

    /**
     * Indicates that the password must be changed before the user will be
     * allowed to perform any operation other than bind and modify.
     */
    CHANGE_AFTER_RESET(2, "changeAfterReset"),

    /**
     * Indicates that a user is restricted from changing her password.
     */
    PASSWORD_MOD_NOT_ALLOWED(3, "passwordModNotAllowed"),

    /**
     * Indicates that the old password must be supplied in order to modify the
     * password.
     */
    MUST_SUPPLY_OLD_PASSWORD(4, "mustSupplyOldPassword"),

    /**
     * Indicates that a password doesn't pass quality checking.
     */
    INSUFFICIENT_PASSWORD_QUALITY(5, "insufficientPasswordQuality"),

    /**
     * Indicates that a password is not long enough.
     */
    PASSWORD_TOO_SHORT(6, "passwordTooShort"),

    /**
     * Indicates that the age of the password to be modified is not yet old
     * enough.
     */
    PASSWORD_TOO_YOUNG(7, "passwordTooYoung"),

    /**
     * Indicates that a password has already been used and the user must choose
     * a different one.
     */
    PASSWORD_IN_HISTORY(8, "passwordInHistory");

    private final int intValue;

    private final String name;

    private PasswordPolicyErrorType(final int intValue, final String name) {
        this.intValue = intValue;
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the integer value for this password policy error type.
     *
     * @return The integer value for this password policy error type.
     */
    int intValue() {
        return intValue;
    }

}
