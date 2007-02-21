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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.authorization.dseecompat.AciMessages.*;
import java.util.regex.Pattern;

/**
 * A class representing the permissions of an bind rule. The permissions
 * of an ACI look like deny(search, write).
 */
public class Permission {
    //the access type (allow,deny)
    private EnumAccessType accessType = null;
    private int rights;
    private static final String separatorToken = ",";
    private static final String rightsRegex =
        "\\s*(\\w+)\\s*(,\\s*(\\w+)\\s*)*";

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
        if ((this.accessType =
            EnumAccessType.decode(accessType)) == null){
            int msgID = MSGID_ACI_SYNTAX_INVALID_ACCESS_TYPE_VERSION;
            String message = getMessage(msgID, accessType);
            throw new AciException(msgID, message);
        }
        if (!Pattern.matches(rightsRegex, rights)){
            int msgID = MSGID_ACI_SYNTAX_INVALID_RIGHTS_SYNTAX;
            String message = getMessage(msgID, rights);
            throw new AciException(msgID, message);
        }
        else {
            Pattern separatorPattern = Pattern.compile(separatorToken);
            String[] rightsStr =
                separatorPattern.split(rights.replaceAll("\\s", ""));
            for (String r : rightsStr) {
                EnumRight right = EnumRight.decode(r);
                if (right != null)
                    this.rights|= EnumRight.getMask(right);
                else {
                    int msgID = MSGID_ACI_SYNTAX_INVALID_RIGHTS_KEYWORD;
                    String message = getMessage(msgID, rights);
                    throw new AciException(msgID, message);
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
    public static
    Permission decode (String accessType, String rights)
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
}
