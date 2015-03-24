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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

/**
 * This class provides an enumeration of the allowed authmethod types.
 */
public enum EnumAuthMethod {

    /**
     * The enumeration type when the bind rule has specified authentication of
     * none.
     */
    AUTHMETHOD_NONE          ("none"),

    /**
     * The enumeration type when the bind rule has specified authentication of
     * simple.
     */
    AUTHMETHOD_SIMPLE        ("simple"),

    /**
     * The enumeration type when the bind rule has specified authentication of
     * ssl client auth.
     */
    AUTHMETHOD_SSL           ("ssl"),

    /**
     * The enumeration type when the bind rule has specified authentication of
     * a sasl mechanism.
     */
    AUTHMETHOD_SASL          ("sasl");

    /**
     * Creates a new enumeration type for this authmethod.
     * @param authmethod The authemethod name.
     */
    EnumAuthMethod (String authmethod){
    }

}
