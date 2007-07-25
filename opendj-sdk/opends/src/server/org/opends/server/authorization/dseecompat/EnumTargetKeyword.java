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

/**
 *  This class provides an enumeration of the valid ACI target keywords.
 */
public enum EnumTargetKeyword {

    /**
     * This enumeration is returned when the target keyword is
     * "target".
     */
    KEYWORD_TARGET      ("target"),
    /**
     * This enumeration is returned when the target keyword is
     * "targetattr".
     */
    KEYWORD_TARGETATTR  ("targetattr"),
    /**
     * This enumeration is returned when the target keyword  is
     * "targetscope".
     */
    KEYWORD_TARGETSCOPE ("targetscope"),
    /**
     * This enumeration is returned when the target keyword is
     * "targetfilter".
     */
    KEYWORD_TARGETFILTER ("targetfilter"),
    /**
     * This enumeration is returned when the target keyword is
     * "targattrfilters".
     */
    KEYWORD_TARGATTRFILTERS ("targattrfilters"),
    /**
     * This enumeration is returned when the target keyword is
     * "targetcontrol".
     */
    KEYWORD_TARGETCONTROL ("targetcontrol"),
      /**
     * This enumeration is returned when the target keyword is
     * "extop".
     */
    KEYWORD_EXTOP ("extop");

    /*
     * The target keyword name.
     */
    private final String keyword;

    /**
     * Create a target keyword enumeration of the specified name.
     * @param keyword    The keyword name.
     */
    EnumTargetKeyword(String keyword){
        this.keyword = keyword;
    }

    /**
     * Checks if the keyword name is equal to the enumeration name.
     * @param keyword The keyword name to check.
     * @return  True if the keyword name is equal to the enumeration.
     */
    public boolean isKeyword(String keyword){
        return keyword.equalsIgnoreCase(this.keyword);
    }

    /**
     * Create an enumeration of the provided keyword name.
     * @param keyword The keyword name to create.
     * @return  An enumeration of the specified keyword name or null
     * if the keyword name is invalid.
     */
    public static EnumTargetKeyword createKeyword(String keyword){
        if (keyword != null){
            for (EnumTargetKeyword t : EnumTargetKeyword.values()){
                if (t.isKeyword(keyword)){
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Return the enumeration keyword name.
     * @return The keyword name.
     */
    public String getKeyword() {
      return keyword;
    }
}
