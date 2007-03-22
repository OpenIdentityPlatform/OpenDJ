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
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;

/**
 * This class represents a targetfilter keyword of an aci.
 *
 */
public class TargetFilter {

    /*
     * Enumeration representing the targetfilter operation.
     */
    private EnumTargetOperator op = EnumTargetOperator.EQUALITY;

    /*
     * Filter parsed from the ACI used to match the resource entry.
     */
    private SearchFilter filter;

    /*
     * Class representing a targetfilter keyword.
     * @param op The operation of the targetfilter expression (=, !=)
     * @param filter The filter itself.
     */
    private TargetFilter(EnumTargetOperator op, SearchFilter filter) {
        this.op=op;
        this.filter=filter;
    }

    /**
     * Decode a aci's targetfilter string.
     * @param op The operation enumeration of the expression.
     * @param expr A string representing the target filter.
     * @return A TargetFilter class suitable for using in a match.
     * @throws AciException If the expression string is invalid.
     */
    public static TargetFilter decode(EnumTargetOperator op, String expr)
    throws AciException {
        SearchFilter filter;
        try {
            filter = SearchFilter.createFilterFromString(expr);
        } catch (DirectoryException ex) {
            int msgID =
                MSGID_ACI_SYNTAX_INVALID_TARGETFILTERKEYWORD_EXPRESSION;
            String message = getMessage(msgID, expr);
            throw new AciException(msgID, message);
        }
        return new TargetFilter(op, filter);
    }

    /**
     * Checks if a targetfilter matches an evaluation context.
     * @param matchCtx The evaluation context to use in the matching.
     * @return True if the target filter matched the context.
     */
    public boolean isApplicable(AciTargetMatchContext matchCtx) {
        boolean ret;
        ret=matchesFilter(matchCtx.getResourceEntry());
        if(op.equals(EnumTargetOperator.NOT_EQUALITY))
            ret = !ret;
        return ret;
    }

    /**
     * Checks the filter against an entry taken from the match context.
     * @param e The entry from the evaluation context above.
     * @return True if the filter matches the entry.
     */
    private boolean matchesFilter(Entry e) {
        boolean ret;
        try {
            ret=filter.matchesEntry(e);
        } catch (DirectoryException ex) {
            //TODO information message?
            return false;
        }
        return ret;
    }
}
