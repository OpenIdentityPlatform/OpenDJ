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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class represents a single ACI's IP bind rule expression. It is possible
 * for that expression to contain several IP addresses to evaluate, so the
 * class contains a list of classes that can evaluate a remote clients IP
 * address for each IP address parsed from the bind rule.
 */
public class IP implements KeywordBindRule {
    /**
     * Regular expression used to do a quick check on the characters in a
     * bind rule address. These are all of the valid characters that may
     * appear in an bind rule address part.
     */
    private static final Pattern ipRegEx =
        Pattern.compile("((?i)[\\.{1}[a-f]\\d:\\+{1}\\*/{1}\\t\\[{1}\\]{1}]+(?-i))");

    /** List of the pattern classes, one for each address decoded from the bind rule. */
    private final List<PatternIP> patternIPList;
    /** The type of the bind rule (!= or =). */
    private final EnumBindRuleType type;

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
     * @param type An enumeration representing the expression type.
     * @return  A class that can be used to evaluate remote clients IP
     *          addresses.
     * @throws AciException  If there is a parsing error.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
            throws AciException  {
        //Split on the ','.
        String[] ipStrs=expr.split("\\,", -1);
        List<PatternIP> patternIPList= new LinkedList<>();
        for (String ipStr : ipStrs) {
            if (!ipRegEx.matcher(ipStr).matches()) {
                throw new AciException(WARN_ACI_SYNTAX_INVALID_IP_EXPRESSION.get(expr));
            }
            patternIPList.add(PatternIP.decode(ipStr));
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
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        return evaluate(evalCtx.getRemoteAddress());
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    @Override
    public final void toString(StringBuilder buffer) {
        buffer.append(super.toString());
    }
}
