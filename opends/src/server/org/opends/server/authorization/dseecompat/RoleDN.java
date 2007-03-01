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

import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.types.*;
import org.opends.server.api.Group;
import org.opends.server.core.GroupManager;
import org.opends.server.core.DirectoryServer;

import java.util.LinkedList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A class representing a roledn bind rule keyword. This class is almost
 * an exact copy of groupDN, except for variable names and error messages.
 */
public class RoleDN  implements KeywordBindRule {

    LinkedList<DN> roleDNs=null;
    private EnumBindRuleType type=null;
    private static GroupManager groupManager =
            DirectoryServer.getGroupManager();

    /**
     * Constructor creating a class representing a roledn keyword of a bind
     * rule.
     * @param type An enumeration of the type of the bind rule.
     * @param roleDNs A list of the role dns parsed from the expression string.
     */
    private RoleDN(EnumBindRuleType type, LinkedList<DN> roleDNs ) {
        this.roleDNs=roleDNs;
        this.type=type;
    }

    /**
     * Decodes an expression string representing an roledn bind rule.
     * @param expr A string representation of the bind rule.
     * @param type An enumeration of the type of the bind rule.
     * @return A keyword bind rule class that can be used to evaluate
     * this bind rule.
     * @throws AciException If the expression is invalid.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
    throws AciException {
        String ldapURLRegex = "\\s*(ldap:///[^\\|]+)";
        String ldapURLSRegex =
            ldapURLRegex + "\\s*(\\|\\|\\s*" + ldapURLRegex + ")*";
        if (!Pattern.matches(ldapURLSRegex, expr)) {
            int msgID = MSGID_ACI_SYNTAX_INVALID_ROLEDN_EXPRESSION;
            String message = getMessage(msgID, expr);
            throw new AciException(msgID, message);
        }
        LinkedList<DN>roleDNs=new LinkedList<DN>();
        int ldapURLPos = 1;
        Pattern ldapURLPattern = Pattern.compile(ldapURLRegex);
        Matcher ldapURLMatcher = ldapURLPattern.matcher(expr);
        while (ldapURLMatcher.find()) {
            String val = ldapURLMatcher.group(ldapURLPos);
            val = val.trim();
            DN dn;
            try {
                dn=DN.decode(val);
            } catch (DirectoryException ex) {
                int msgID = MSGID_ACI_SYNTAX_INVALID_ROLEDN_URL;
                String message = getMessage(msgID, ex.getErrorMessage());
                throw new AciException(msgID, message);
            }
            roleDNs.add(dn);
        }
        return new RoleDN(type, roleDNs);
    }


    /**
     * Performs the evaluation of a roledn bind rule based on the
     * evaluation context passed to it. The method uses an exact copy
     * evaluation method as the groupDN.evaluate().  The evaluation stops when
     * there are no more group DNs to evaluate, or if a group DN evaluates to
     * true if it contains the authorization DN.
     * @param evalCtx  An evaluation context to use  in the evaluation.
     * @return  Enumeration evaluation result.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched = EnumEvalResult.FALSE;
       Iterator<DN> it=roleDNs.iterator();
        for(; it.hasNext() && matched != EnumEvalResult.TRUE;) {
            DN groupDN=it.next();
            Group group = groupManager.getGroupInstance(groupDN);
            if(evalCtx.isMemberOf(group))
               matched = EnumEvalResult.TRUE;
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
     * @return Enumeration evaluation result.
     */
    public static EnumEvalResult evaluate (Entry e, AciEvalContext evalCtx,
                                           AttributeType attributeType) {
        EnumEvalResult matched= EnumEvalResult.FALSE;
        List<Attribute> attrs = e.getAttribute(attributeType);
        LinkedHashSet<AttributeValue> vals = attrs.get(0).getValues();
        for(AttributeValue v : vals) {
            try {
                DN groupDN=DN.decode(v.getStringValue());
                Group group = groupManager.getGroupInstance(groupDN);
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
}
