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

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.api.Group;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.GroupManager;
import org.opends.server.types.*;

/**
 * This class implements the groupdn bind rule keyword.
 */
public class GroupDN implements KeywordBindRule {

    /** List of group DNs. */
    private List<DN> groupDNs;

    /** Enumeration representing the groupdn operator type. */
    private EnumBindRuleType type;

    /**
     * Regular expression matching one or more LDAP URLs separated by
     * "||".
     */
    public static final String LDAP_URLS = LDAP_URL +
            ZERO_OR_MORE_WHITESPACE + "(" + LOGICAL_OR +
            ZERO_OR_MORE_WHITESPACE + LDAP_URL + ")*";

    /**
     * Create a class representing a groupdn bind rule keyword.
     * @param type An enumeration representing the bind rule type.
     * @param groupDNs A list of the dns representing groups.
     */
    private GroupDN(EnumBindRuleType type, List<DN> groupDNs ) {
        this.groupDNs=groupDNs;
        this.type=type;
    }

    /**
     * Decode an string expression representing a groupdn bind rule.
     * @param expr  A string representation of the bind rule.
     * @param type An enumeration of the type of the bind rule.
     * @return  A keyword bind rule class that can be used to evaluate
     * this bind rule.
     * @throws AciException   If the expression string is invalid.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
    throws AciException  {
        if (!Pattern.matches(LDAP_URLS, expr)) {
            LocalizableMessage message =
                WARN_ACI_SYNTAX_INVALID_GROUPDN_EXPRESSION.get(expr);
            throw new AciException(message);
        }
        List<DN> groupDNs = new LinkedList<>();
        int ldapURLPos = 1;
        Pattern ldapURLPattern = Pattern.compile(LDAP_URL);
        Matcher ldapURLMatcher = ldapURLPattern.matcher(expr);
        while (ldapURLMatcher.find()) {
            try {
               String value = ldapURLMatcher.group(ldapURLPos).trim();
               DN dn=LDAPURL.decode(value, true).getBaseDN();
               groupDNs.add(dn);
            } catch (DirectoryException ex) {
                LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_GROUPDN_URL.get(
                    ex.getMessageObject());
                throw new AciException(message);
            }
        }
        return new GroupDN(type, groupDNs);
    }

    /**
     * Performs the evaluation of a groupdn bind rule based on the
     * evaluation context passed to it. The evaluation stops when there
     * are no more group DNs to evaluate, or if a group DN evaluates to true
     * if it contains the client DN.
     * @param evalCtx  An evaluation context to use  in the evaluation.
     * @return  Enumeration evaluation result.
     */
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched = EnumEvalResult.FALSE;
        for (DN groupDN : groupDNs) {
            Group<?> group = getGroupManager().getGroupInstance(groupDN);
            if((group != null) && (evalCtx.isMemberOf(group))) {
               matched = EnumEvalResult.TRUE;
               break;
            }
        }
        return matched.getRet(type, false);
    }

    /**
     * Performs an evaluation of a group that was specified in an attribute
     * type value of the specified entry and attribute type. Each
     * value of the attribute type is assumed to be a group DN and evaluation
     * stops when there are no more values or if the group DN evaluates to
     * true if it contains the client DN.
     * @param e The entry to use in the evaluation.
     * @param evalCtx  The evaluation context to use in the evaluation.
     * @param attributeType The attribute type of the entry to use to get the
     * values for the groupd DNs.
     * @param suffixDN The suffix that the groupDN must be under. If it's null,
     *                 then the groupDN can be anywhere in the DIT.
     * @return Enumeration evaluation result.
     */
    public static EnumEvalResult evaluate (Entry e, AciEvalContext evalCtx,
                                           AttributeType attributeType,
                                           DN suffixDN) {
        EnumEvalResult matched= EnumEvalResult.FALSE;
        List<Attribute> attrs = e.getAttribute(attributeType);
        for(ByteString v : attrs.get(0)) {
            try {
                DN groupDN = DN.valueOf(v.toString());
                if(suffixDN != null && !groupDN.isDescendantOf(suffixDN))
                {
                  continue;
                }
                Group<?> group = getGroupManager().getGroupInstance(groupDN);
                if((group != null) && (evalCtx.isMemberOf(group))) {
                    matched=EnumEvalResult.TRUE;
                    break;
                }
            } catch (DirectoryException ex) {
                break;
            }
        }
        return matched;
    }

    private static GroupManager getGroupManager() {
        return DirectoryServer.getGroupManager();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public final void toString(StringBuilder buffer) {
        buffer.append(super.toString());
    }

}
