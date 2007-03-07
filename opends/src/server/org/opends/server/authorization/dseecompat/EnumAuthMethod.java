/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;
/*
 * TODO Evaluate moving this to a non-enumeration class that can add
 * SASL mechanisms dynamically.
 *
 *  Given our previous discussion about needing to support any kind of SASL
 *  mechanism that may be registered with the server, perhaps an enum isn't
 *  the right way to handle this because we don't know ahead of time what
 *  auth methods might be available (certainly not at compile time, but
 *  potentially not even at runtime since I can add support for a new SASL
 *  mechanism on the fly without restarting the server).
 */
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
     *  simple.
     */
    AUTHMETHOD_SIMPLE        ("simple"),
    /**
      * The enumeration type when the bind rule has specified authentication of
     *  ssl client auth.
     */
    AUTHMETHOD_SSL           ("ssl"),
    /**
     * The enumeration type when the bind rule has specified authentication of
     * sasl DIGEST-MD5.
     */
    AUTHMETHOD_SASL_MD5      ("sasl DIGEST-MD5"),
    /**
     * The enumeration type when the bind rule has specified authentication of
     * sasl EXTERNAL.
     */
    AUTHMETHOD_SASL_EXTERNAL ("sasl EXTERNAL"),
    /**
     * The enumeration type when the bind rule has specified authentication of
     * sasl GSSAPI.
     */
    AUTHMETHOD_SASL_GSSAPI   ("sasl GSSAPI"),
    /**
     * Special internal enumeration for when there is no match.
     */
    AUTHMETHOD_NOMATCH       ("nomatch");

    /*
     * The name of the authmethod.
     */
    private String authmethod = null;

    /**
     * Creates a new enumeration type for this authmethod.
     * @param authmethod The authemethod name.
     */
    EnumAuthMethod (String authmethod){
        this.authmethod = authmethod;
    }

    /**
     * Checks if a authmethod name is equal to this enumeration.
     * @param myauthmethod  The name to test for.
     * @return  True if the names match.
     */
    public boolean isAuthMethod(String myauthmethod){
        return myauthmethod.equalsIgnoreCase(this.authmethod);
    }

    /**
     * Creates an authmethod enumeration from the name passed in.
     * @param myauthmethod The name to create.
     * @return An authmethod enumeration if the name was found or null if not.
     */
    public static EnumAuthMethod createAuthmethod(String myauthmethod){
        if (myauthmethod != null){
            for (EnumAuthMethod t : EnumAuthMethod.values()){
                if (t.isAuthMethod(myauthmethod)){
                    return t;
                }
            }
        }
        return null;
    }
}
