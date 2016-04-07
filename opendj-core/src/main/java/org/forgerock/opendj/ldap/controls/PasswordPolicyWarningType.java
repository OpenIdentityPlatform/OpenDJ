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
    /** Indicates the number of seconds before a password will expire. */
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
