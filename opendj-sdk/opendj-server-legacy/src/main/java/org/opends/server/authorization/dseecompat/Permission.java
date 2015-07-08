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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;

import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;

/**
 * A class representing the permissions of an bind rule. The permissions
 * of an ACI look like deny(search, write).
 */
public class Permission {

    /**
     *  The access type (allow,deny) corresponding to the ACI permission value.
     */
    private EnumAccessType accessType;

    /**
     * The rights (search, add, delete, ...) corresponding to the ACI rights
     * value.
     */
    private int rights;

    /**
     * Regular expression token representing the separator.
     */
    private static final String separatorToken = ",";

    /**
     * Regular expression used to match the ACI rights string.
     */
    private static final String rightsRegex = ZERO_OR_MORE_WHITESPACE +
            WORD_GROUP + ZERO_OR_MORE_WHITESPACE +
            "(," + ZERO_OR_MORE_WHITESPACE + WORD_GROUP +
            ZERO_OR_MORE_WHITESPACE +  ")*";

    /**
     * Constructor creating a class representing a permission part of an bind
     * rule.
     * @param accessType A string representing access type.
     * @param rights  A string representing the rights.
     * @throws AciException If the access type string or rights string
     * is invalid.
     */
    private Permission(String accessType, String rights)
    throws AciException {
        this.accessType = EnumAccessType.decode(accessType);
        if (this.accessType == null){
            LocalizableMessage message =
                WARN_ACI_SYNTAX_INVALID_ACCESS_TYPE_VERSION.get(accessType);
            throw new AciException(message);
        }
        if (!Pattern.matches(rightsRegex, rights)){
            LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_RIGHTS_SYNTAX.get(rights);
            throw new AciException(message);
        }
        else {
            Pattern separatorPattern = Pattern.compile(separatorToken);
            String[] rightsStr =
                separatorPattern.split(rights.replaceAll("\\s", ""));
            for (String r : rightsStr) {
                EnumRight right = EnumRight.decode(r);
                if (right != null) {
                  this.rights|= EnumRight.getMask(right);
                } else {
                    LocalizableMessage message =
                        WARN_ACI_SYNTAX_INVALID_RIGHTS_KEYWORD.get(rights);
                    throw new AciException(message);
                }
            }
        }
    }

    /**
     * Decode an string representation of bind rule permission into a Permission
     * class.
     * @param accessType  A string representing the access type.
     * @param rights   A string representing the rights.
     * @return  A Permission class representing the permissions of the bind
     * rule.
     * @throws AciException  If the accesstype or rights strings are invalid.
     */
    public static Permission decode (String accessType, String rights)
            throws AciException {
        return new Permission(accessType, rights);
    }

    /**
     * Checks if a given access type enumeration is equal to this classes
     * access type.
     * @param accessType An enumeration representing an access type.
     * @return True if the access types are equal.
     */
    public boolean hasAccessType(EnumAccessType accessType) {
        return this.accessType == accessType;
    }

    /**
     * Checks if the permission's rights has the specified rights.
     * @param  rights The rights to check for.
     * @return True if the permission's rights has the specified rights.
     */
    public boolean hasRights(int rights) {
        return (this.rights & rights) != 0;
    }

    /** {@inheritDoc} */
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
    public final void toString(StringBuilder buffer) {
        if (this.accessType != null) {
            buffer.append(accessType.toString().toLowerCase());
            Set<EnumRight> enumRights = EnumRight.getEnumRight(rights);
            if (enumRights != null) {
                buffer.append("(");
                for (Iterator<EnumRight> iter = enumRights.iterator(); iter
                        .hasNext();) {
                    buffer.append(iter.next().getRight());
                    if (iter.hasNext()) {
                        buffer.append(",");
                    }
                }
                buffer.append(")");
            } else {
                buffer.append("(all)");
            }
        }
    }
}
