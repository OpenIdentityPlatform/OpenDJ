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

import static org.opends.server.messages.AciMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;

/**
 * This class is used by USERDN and GROUPDN userattr types
 * to determine what parent inheritance checks to make.
 */
public class ParentInheritance {

    /*
     * The maximum number of parent inheritance levels supported.
     */
    private static final int MAX_LEVELS=10;

    /*
     * Pattern to match for parent inheritance.
     */
    private String parentPat="parent[";

    /*
     * Array used to hold the level information. Each slot corresponds to a
     * level parsed from the rule.
     */
    private int[] levels=new int[MAX_LEVELS];

    /*
     * The number of levels parsed.
     */
    private int numLevels;

    /*
     * The attribute type parsed from the rule.
     */
    private AttributeType attributeType;


    /**
     * Construct a class from the inheritance pattern. The skipParsing boolean
     * specifies that parent parsing should be skipped and sets up the class:
     * with numLevels=1, level[0]=0 and an attribute type from the
     * specified pattern.
     *
     * @param pattern The string pattern containing the inheritance
     * information.
     * @param skipParse Specify if the parent inheritance parsing should be
     * skipped or not.
     * @throws AciException  If the pattern is invalid.
     */
    ParentInheritance (String pattern, boolean skipParse)  throws AciException {
        if (skipParse) {
            //The "parent[" pattern is invalid for ROLEDN user attr keyword.
            if(pattern.startsWith(parentPat)) {
                int msgID =
                   MSGID_ACI_SYNTAX_INVALID_USERATTR_ROLEDN_INHERITANCE_PATTERN;
                String message = getMessage(msgID, pattern);
                throw new AciException(msgID, message);
            }  else {
                pattern=pattern.trim();
                Pattern pattern1=Pattern.compile(ATTR_NAME);
                Matcher matcher=pattern1.matcher(pattern);
               //Check if valid attribute type name.
               if(!matcher.find() || matcher.groupCount() != 1) {
                int msgID =
                        MSGID_ACI_SYNTAX_INVALID_ATTRIBUTE_TYPE_NAME;
                String message = getMessage(msgID, pattern);
                throw new AciException(msgID, message);
               }
               if((this.attributeType =
                    DirectoryServer.getAttributeType(pattern)) == null)
                this.attributeType =
                        DirectoryServer.getDefaultAttributeType(pattern);
               numLevels=1;
              levels[0]=0;
            }
    } else parse(pattern);
}

    /**
     * Performs all parsing of the specified pattern string.
     * @param pattern The string pattern containing the inheritance
     * information.
     * @throws AciException  If the pattern is invalid.
     */
    private void parse (String pattern) throws AciException {
        pattern=pattern.trim();
        /**
         * Check if we have a "parent[" string.
         */
        if(pattern.startsWith(parentPat)) {
            numLevels=0;
            levels[0]=0;
            String p=pattern.substring(parentPat.length());
            /**
             * Format needs to be parent[XX].attribute -- everything after the
             * '.' is the attribute type.
             */
            String[] toks=p.split("\\.");
            if(toks.length != 2) {
                int msgID =
                    MSGID_ACI_SYNTAX_INVALID_USERATTR_INHERITANCE_PATTERN;
                String message = getMessage(msgID, pattern);
                throw new AciException(msgID, message);
            }
            Pattern pattern1=Pattern.compile(ATTR_NAME);
            Matcher matcher=pattern1.matcher(toks[1]);
            //Check if valid attribute type name.
            if(!matcher.find() || matcher.groupCount() != 1) {
                int msgID =
                    MSGID_ACI_SYNTAX_INVALID_ATTRIBUTE_TYPE_NAME;
                String message = getMessage(msgID, toks[1]);
                throw new AciException(msgID, message);
            }
            if((this.attributeType =
                DirectoryServer.getAttributeType(toks[1])) == null)
                this.attributeType =
                    DirectoryServer.getDefaultAttributeType(toks[1]);
            StringTokenizer tok=new StringTokenizer(toks[0],"],",false);
            while(tok.hasMoreTokens()) {
                String v=tok.nextToken();
                /**
                 * Everything between the brackets must be an integer or it's
                 * an error.
                 */
                try {
                    if(numLevels < MAX_LEVELS) {
                        levels[numLevels++]=Integer.decode(v);
                    } else {
                        int msgID =
                      MSGID_ACI_SYNTAX_MAX_USERATTR_INHERITANCE_LEVEL_EXCEEDED;
                        String message = getMessage(msgID, pattern,
                                               Integer.toString(MAX_LEVELS));
                        throw new AciException(msgID, message);
                    }
                } catch (NumberFormatException ex) {
                    int msgID = MSGID_ACI_SYNTAX_INVALID_INHERITANCE_VALUE;
                    String message = getMessage(msgID, pattern);
                    throw new AciException(msgID, message);
                }
            }
        } else {
            if((this.attributeType =
                DirectoryServer.getAttributeType(pattern)) == null)
                this.attributeType =
                    DirectoryServer.getDefaultAttributeType(pattern);
            numLevels=1;
            levels[0]=0;
        }
    }

    /**
     * Returns the number of levels counted.
     * @return The number of levels.
     */
    public int getNumLevels() {
        return numLevels;
    }

    /**
     * Returns an array of levels, where levels are integers.
     * @return Return an array of levels.
     */
    public int[] getLevels() {
        return levels;
    }

    /**
     * Return the attribute type.
     * @return The attribute type.
     */
    public AttributeType getAttributeType() {
        return attributeType;
    }
}

