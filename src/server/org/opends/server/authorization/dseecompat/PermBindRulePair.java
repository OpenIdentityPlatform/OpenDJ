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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

/**
 * A class representing a permission-bind rule pair. There can be multiple
 * of these in an ACI.
 */
public class PermBindRulePair {

    /*
     * The Bind Rule part.
     */
    private BindRule bindRule;

    /*
     * The permission part.
     */
    private Permission perm=null;

    /**
     * This constructor calls the permission and bind rule decodes
     * with the appropriate strings.
     * @param perm  A string representing the permissions.
     * @param rights  A string representing the rights.
     * @param bindRule A string representing the bind rule.
     * @throws AciException  If any of the strings fail to decode.
     */
    private  PermBindRulePair(String perm, String rights, String bindRule)
    throws AciException {
     this.perm=Permission.decode(perm, rights);
     this.bindRule=BindRule.decode(bindRule);
    }

    /**
     * Decodes a permission bind rule pair.
     * @param perm  A string representing the permissions.
     * @param rights  A string representing the rights.
     * @param bRule A string representing the bind rule.
     * @return An permission bind rule pair class representing this pair.
     * @throws AciException  If any of the strings fail to decode.
     */
    public static PermBindRulePair decode(String perm, String rights,
                                          String bRule) throws AciException {
       return new PermBindRulePair(perm, rights, bRule);
    }

    /**
     * Gets the bind rule part of this pair.
     * @return  The bind rule part of this pair.
     */
    public BindRule getBindRule () {
        return bindRule;
    }

    /**
     * Checks the permission to see if it has this access type.
     * @param accessType An enumeration of the desired access type.
     * @return True if the access type equals the permission access type.
     */
    public boolean hasAccessType(EnumAccessType accessType) {
        return perm.hasAccessType(accessType);
    }

    /**
     * Try and match one or more of the specified rights against a rights set
     * of the permission class.
     * @param right  The rights to match.
     * @return True if one or more of the specified rights match a right in
     * the rights set of the permission class.
     */
    public boolean hasRights(int right) {
        return perm.hasRights(right);
    }
}
