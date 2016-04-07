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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
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
    /** Indicates that the password has expired and must be reset. */
    PASSWORD_EXPIRED(0, "passwordExpired"),

    /** Indicates that the user's account has been locked. */
    ACCOUNT_LOCKED(1, "accountLocked"),

    /**
     * Indicates that the password must be changed before the user will be
     * allowed to perform any operation other than bind and modify.
     */
    CHANGE_AFTER_RESET(2, "changeAfterReset"),

    /** Indicates that a user is restricted from changing her password. */
    PASSWORD_MOD_NOT_ALLOWED(3, "passwordModNotAllowed"),

    /** Indicates that the old password must be supplied in order to modify the password. */
    MUST_SUPPLY_OLD_PASSWORD(4, "mustSupplyOldPassword"),

    /** Indicates that a password doesn't pass quality checking. */
    INSUFFICIENT_PASSWORD_QUALITY(5, "insufficientPasswordQuality"),

    /** Indicates that a password is not long enough. */
    PASSWORD_TOO_SHORT(6, "passwordTooShort"),

    /** Indicates that the age of the password to be modified is not yet old enough. */
    PASSWORD_TOO_YOUNG(7, "passwordTooYoung"),

    /** Indicates that a password has already been used and the user must choose a different one. */
    PASSWORD_IN_HISTORY(8, "passwordInHistory");

    private final int intValue;

    private final String name;

    private PasswordPolicyErrorType(final int intValue, final String name) {
        this.intValue = intValue;
        this.name = name;
    }

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
