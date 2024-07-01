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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

/** A class representing a permission-bind rule pair. There can be multiple of these in an ACI. */
public class PermBindRulePair {
    /** The Bind Rule part. */
    private final BindRule bindRule;
    /** The permission part. */
    private final Permission perm;

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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    /**
     * Appends a string representation of this object to the provided buffer.
     *
     * @param buffer
     *          The buffer into which a string representation of this object
     *          should be appended.
     */
    final void toString(StringBuilder buffer) {
        if (this.perm != null) {
            this.perm.toString(buffer);
        }
        buffer.append(" ");
        if (this.bindRule != null) {
            this.bindRule.toString(buffer);
        }
        buffer.append(")"); // not sure why, but we need this extra parenthesis
    }
}
