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
 * Placeholder. This class is partially complete. The TargAttrFilters class
 * will represent an targAttrFitlers rule.
 */
public class TargAttrFilters {

    /**
     * Represents an targAttrFilters rule.
     */
    public TargAttrFilters() {

    }

    /**
     * Decode an string representing a targattrfilters keyword.
     * @param operator The operator of the rule.
     * @param expression The string parsed from the ACI representing the
     * targattrfilters rule.
     * @return  An object representing an targattrfilters rule.
     * @throws  AciException if the expression string cannot be parsed.
     */
    public static TargAttrFilters decode(EnumTargetOperator operator,
                                  String expression) throws AciException {
        return new TargAttrFilters();
    }
}
