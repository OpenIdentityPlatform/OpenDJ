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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;

/** This class represents a targetfilter keyword of an aci. */
public class TargetFilter {

    /** Enumeration representing the targetfilter operation. */
    private final EnumTargetOperator op;
    /** Filter parsed from the ACI used to match the resource entry. */
    private final SearchFilter filter;

    /**
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
            LocalizableMessage message =
                WARN_ACI_SYNTAX_INVALID_TARGETFILTERKEYWORD_EXPRESSION.get(expr);
            throw new AciException(message);
        }
        return new TargetFilter(op, filter);
    }

    /**
     * Checks if a targetfilter matches an evaluation context.
     * @param matchCtx The evaluation context to use in the matching.
     * @return True if the target filter matched the context.
     */
    public boolean isApplicable(AciTargetMatchContext matchCtx) {
        boolean ret = matchesFilter(matchCtx.getResourceEntry());
        if(op.equals(EnumTargetOperator.NOT_EQUALITY))
        {
          return !ret;
        }
        return ret;
    }

    /**
     * Checks the filter against an entry taken from the match context.
     * @param e The entry from the evaluation context above.
     * @return True if the filter matches the entry.
     */
    private boolean matchesFilter(Entry e) {
        try {
            return filter.matchesEntry(e);
        } catch (DirectoryException ex) {
            //TODO information message?
            return false;
        }
    }
}
