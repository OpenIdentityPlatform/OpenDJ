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
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class implements the dns bind rule keyword.
 */
public class DNS implements KeywordBindRule {

    LinkedList<String> patterns=null;
    private EnumBindRuleType type=null;

    /**
     * Create a class representing a dns bind rule keyword.
     * @param patterns List of dns patterns to match against.
     * @param type An enumeration representing the bind rule type.
     */
    private DNS(LinkedList<String> patterns, EnumBindRuleType type) {
        this.patterns=patterns;
        this.type=type;
    }

    /**
     * Decode an string representing a dns bind rule.
     * @param expr A string representation of the bind rule.
     * @param type  An enumeration representing the bind rule type.
     * @return  A keyword bind rule class that can be used to evaluate
     * this bind rule.
     * @throws AciException  If the expression string is invalid.
     */
    public static DNS decode(String expr,  EnumBindRuleType type)
    throws AciException
    {
        String valueRegex = "([a-zA-Z0-9\\.\\-\\*]+)";
        String valuesRegex = valueRegex + "\\s*(,\\s*" + valueRegex + ")*";
        if (!Pattern.matches(valuesRegex, expr)) {
            int msgID = MSGID_ACI_SYNTAX_INVALID_DNS_EXPRESSION;
            String message = getMessage(msgID, expr);
            throw new AciException(msgID, message);
        }
        LinkedList<String>dns=new LinkedList<String>();
        int valuePos = 1;
        Pattern valuePattern = Pattern.compile(valueRegex);
        Matcher valueMatcher = valuePattern.matcher(expr);
        while (valueMatcher.find()) {
            String hn=valueMatcher.group(valuePos);
            String[] hnArray=hn.split("\\.", -1);
            for(int i=1, n=hnArray.length; i < n; i++) {
                if(hnArray[i].equals("*")) {
                    int msgID = MSGID_ACI_SYNTAX_INVALID_DNS_WILDCARD;
                    String message = getMessage(msgID, expr);
                    throw new AciException(msgID, message);
                }
            }
            dns.add(hn);
        }
        return new DNS(dns, type);
    }

    /**
     * Performs evaluation of dns keyword bind rule using the provided
     * evaluation context.
     * @param evalCtx  An evaluation context to use in the evaluation.
     * @return An enumeration evaluation result.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        String[] remoteHost = evalCtx.getHostName().split("\\.", -1);
        for(String p : patterns) {
          String[] pat = p.split("\\.", -1);
          if(evalHostName(remoteHost, pat)) {
              matched=EnumEvalResult.TRUE;
              break;
          }
        }
        return matched.getRet(type, false);
    }

    /*
     * TODO Verify that a DNS pattern of "*" is valid by writing a unit
     * test. Probably isn't.
     *
     * TODO Evaluate if extending the wild-card matching to multiple name
     * components should be supported. Currently wild-cards are only permitted
     * in the leftmost field and the rest of the domain name components must
     * match.
     *
     * TODO Evaluate extending wild-card matching to non-complete name matching.
     *
     * Is it acceptable to have a DNS address of just "*"
     * (which presumably will match any system)?
     *
     * Is it acceptable for a wildcard to match multiple name components?  For
     * example, is "*.example.com" supposed to be considered a match for
     * "host.east.example.com"?  Similarly, would a pattern like
     * "www.*.example.com" match "www.newyork.east.example.com"?  It doesn't
     * appear that the current implementation matches either of them.
     *
     * Is it acceptable for a wildcard to appear as anything other than a
     * complete name component?  For example, if I have three web servers
     * "www1.example.com","www2.example.com", and "www3.example.com", then
     * can I use "www*.example.com"? It doesn't appear that the current
     * implementation allows that.  Further, would "www*.example.com" match
     * cases like "www.example.com" or "www1.east.example.com"?
     */
    /**
     * Checks an array containing the remote client's hostname against
     * patterns specified in the bind rule expression. Wild-cards are
     * only permitted in the leftmost field and the rest of the domain
     * name array components must match.
     * @param remoteHostName  Array containing components of the remote clients
     * hostname (split on ".").
     * @param pat  An array containing the pattern specified in
     * the bind rule expression. The first array slot may be a wild-card "*".
     * @return  True if the remote hostname matches the pattern.
     */
    private boolean evalHostName(String[] remoteHostName, String[] pat) {
        if(remoteHostName.length != pat.length)
            return false;
        for(int i=0;i<remoteHostName.length;i++)
        {
            if(!pat[i].equals("*")) {
                if(!pat[i].equalsIgnoreCase(remoteHostName[i]))
                    return false;
            }
        }
        return true;
    }
}
