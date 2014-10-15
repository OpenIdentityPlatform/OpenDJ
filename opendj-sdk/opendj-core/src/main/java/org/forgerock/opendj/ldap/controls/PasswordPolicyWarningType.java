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
 * A password policy warning type as defined in
 * draft-behera-ldap-password-policy is used to indicate the current state of a
 * user's password. More specifically, the number of seconds before a password
 * will expire, or the remaining number of times a user will be allowed to
 * authenticate with an expired password.
 *
 * @see PasswordPolicyRequestControl
 * @see PasswordPolicyResponseControl
 * @see PasswordPolicyErrorType
 * @see <a href="http://tools.ietf.org/html/draft-behera-ldap-password-policy">
 *      draft-behera-ldap-password-policy - Password Policy for LDAP Directories
 *      </a>
 */
public enum PasswordPolicyWarningType {
    /**
     * Indicates the number of seconds before a password will expire.
     */
    TIME_BEFORE_EXPIRATION(0, "timeBeforeExpiration"),

    /**
     * Indicates the remaining number of times a user will be allowed to
     * authenticate with an expired password.
     */
    GRACE_LOGINS_REMAINING(1, "graceAuthNsRemaining");

    private final int intValue;

    private final String name;

    private PasswordPolicyWarningType(final int intValue, final String name) {
        this.intValue = intValue;
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the integer value for this password policy warning type.
     *
     * @return The integer value for this password policy warning type.
     */
    int intValue() {
        return intValue;
    }
}
