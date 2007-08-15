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
import org.opends.messages.Message;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import java.util.StringTokenizer;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.DirectoryException;

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
    private final String parentPat="parent[";

    /*
     * Array used to hold the level information. Each slot corresponds to a
     * level parsed from the rule.
     */
    private final int[] levels=new int[MAX_LEVELS];

    /*
     * The number of levels parsed.
     */
    private int numLevels;

    /*
     * The attribute type parsed from the rule.
     */
    private AttributeType attributeType;

    /*
     * The attribute type string parsed from the rule. Only used in
     * inheritance search.
     */
    private String attrTypeStr;

    /*
     * The base DN of a URL parsed from the rule. Used to make sure groupdn
     * are under this suffix. Originally a way to search all nested groups
     * under this suffix, so the behavior is slightly different.
     */
    private DN baseDN=null;


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
                Message message =
                  WARN_ACI_SYNTAX_INVALID_USERATTR_ROLEDN_INHERITANCE_PATTERN
                          .get(pattern);
                throw new AciException(message);
            }  else {
                pattern=pattern.trim();
                Pattern pattern1=Pattern.compile(ATTR_NAME);
                Matcher matcher=pattern1.matcher(pattern);
               //Check if valid attribute type name.
               if(!matcher.find() || matcher.groupCount() != 1) {
                Message message =
                    WARN_ACI_SYNTAX_INVALID_ATTRIBUTE_TYPE_NAME.get(pattern);
                throw new AciException(message);
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
                Message message =
                  WARN_ACI_SYNTAX_INVALID_USERATTR_INHERITANCE_PATTERN
                          .get(pattern);
                throw new AciException(message);
            }
            Pattern pattern1=Pattern.compile(ATTR_NAME);
            Matcher matcher=pattern1.matcher(toks[1]);
            //Check if valid attribute type name.
            if(!matcher.find() || matcher.groupCount() != 1) {
                Message message =
                    WARN_ACI_SYNTAX_INVALID_ATTRIBUTE_TYPE_NAME.get(toks[1]);
                throw new AciException(message);
            }
            if((this.attributeType =
                DirectoryServer.getAttributeType(toks[1])) == null)
                this.attributeType =
                    DirectoryServer.getDefaultAttributeType(toks[1]);
            attrTypeStr=toks[1];
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
                        Message message =
                        WARN_ACI_SYNTAX_MAX_USERATTR_INHERITANCE_LEVEL_EXCEEDED.
                              get(pattern, Integer.toString(MAX_LEVELS));
                        throw new AciException(message);
                    }
                } catch (NumberFormatException ex) {
                    Message message =
                        WARN_ACI_SYNTAX_INVALID_INHERITANCE_VALUE.get(pattern);
                    throw new AciException(message);
                }
            }
        } else {
          attrTypeStr=pattern;
          if(pattern.startsWith(NULL_LDAP_URL)) {
            try {
              LDAPURL url=LDAPURL.decode(pattern, true);
              LinkedHashSet<String>attrs=url.getAttributes();
              if(attrs.size() != 1) {
                Message message =
                    WARN_ACI_SYNTAX_INVALID_USERATTR_ATTR_URL.get(pattern);
                throw new AciException(message);
              }
              baseDN=url.getBaseDN();
              if(baseDN.isNullDN()){
                Message message =
                    WARN_ACI_SYNTAX_INVALID_USERATTR_BASEDN_URL.get(pattern);
                throw new AciException(message);
              }
              attrTypeStr=attrs.iterator().next();
            } catch (DirectoryException ex) {
              Message message = WARN_ACI_SYNTAX_INVALID_USERATTR_URL.get(
                  ex.getMessageObject());
              throw new AciException(message);
            }
          }
          if((this.attributeType =
                  DirectoryServer.getAttributeType(attrTypeStr)) == null)
            this.attributeType =
                    DirectoryServer.getDefaultAttributeType(attrTypeStr);
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
        int[] levelsCopy = new int[levels.length];
        System.arraycopy(levels, 0, levelsCopy, 0, levels.length);
        return levelsCopy;
    }

    /**
     * Return the attribute type.
     * @return The attribute type.
     */
    public AttributeType getAttributeType() {
        return attributeType;
    }

    /**
     * Return the string representation of the attribute type.
     * @return   The attribute type string.
     */
    public String getAttrTypeStr() {
        return attrTypeStr;
    }

  /**
   * Return the DN that groupdn must be under.
   *
   * @return DN that groupdn must be under.
   */
  public DN getBaseDN() {
      return baseDN;
    }
}

