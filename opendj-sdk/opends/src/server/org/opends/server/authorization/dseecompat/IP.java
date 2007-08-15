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
import java.util.regex.Pattern;
import java.util.*;
import java.net.InetAddress;

/**
 * This class represents a single ACI's IP bind rule expression. It is possble
 * for that expression to contain several IP addresses to evaluate, so the
 * class contains a list of classes that can evaluate a remote clients IP
 * address for each IP address parsed from the bind rule.
 */
public class IP implements KeywordBindRule {

    /*
      Regular expression used to do a quick check on the characters in a
      bind rule address. These are all of the valid characters that may
      appear in an bind rule address part.
    */
    private  static final String ipRegEx =
            "((?i)[\\.{1}[a-f]\\d:\\+{1}\\*/{1}\\t\\[{1}\\]{1}]+(?-i))";

    /*
      List of the pattern classes, one for each address decoded from the
      bind rule.
    */
    private List<PatternIP> patternIPList=null;

    /*
      The type of the bind rule (!= or =).
     */
    private EnumBindRuleType type=null;

    /**
     * Create a class representing the IP bind rule expressions for this ACI.
     * @param patternIPList A list of PatternIP objects representing the IP
     *                      bind rule expressions decoded from ACI.
     * @param type An enumeration representing the expression type.
     */
    private IP(List<PatternIP> patternIPList, EnumBindRuleType type) {
        this.patternIPList=patternIPList;
        this.type=type;
    }

    /**
     * Decodes the provided IP bind rule expression string and returns an
     * IP class the can be used to evaluate remote clients IP addresses.
     *
     * @param expr The expression string from the ACI IP bind rule.
     * @param type An enmumeration representing the expression type.
     * @return  A class that can be used to evaluate remote clients IP
     *          addresses.
     * @throws AciException  If there is a parsing error.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
            throws AciException  {
        //Split on the ','.
        String[] ipStrs=expr.split("\\,", -1);
        List<PatternIP> patternIPList= new LinkedList<PatternIP>();
        for (String ipStr : ipStrs) {
            if (!Pattern.matches(ipRegEx, ipStr)) {
                Message message =
                    WARN_ACI_SYNTAX_INVALID_IP_EXPRESSION.get(expr);
                throw new AciException(message);
            }
            PatternIP ipPattern = PatternIP.decode(ipStr);
            patternIPList.add(ipPattern);
        }
        return new IP(patternIPList, type);
    }

    /**
     * Perform an evaluation using the provided evaluation context's remote
     * IP address information.
     *
     * @param evalCtx An evaluation context containing the remote clients
     * IP address information.
     *
     * @return An enumeration representing if the address matched.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        InetAddress remoteAddr=evalCtx.getRemoteAddress();
        return evaluate(remoteAddr);
    }

    /**
     * Perform an evaluation using the InetAddress.
     *
     * @param addr  The InetAddress to evaluate against PatternIP classes.
     * @return  An enumeration representing if the address matched one
     *          of the patterns.
     */
    EnumEvalResult evaluate(InetAddress addr) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        Iterator<PatternIP> it=patternIPList.iterator();
        for(; it.hasNext() && matched != EnumEvalResult.TRUE &&
                matched != EnumEvalResult.ERR;) {
            PatternIP patternIP=it.next();
            matched=patternIP.evaluate(addr);
        }
        return matched.getRet(type, false);
    }
}
