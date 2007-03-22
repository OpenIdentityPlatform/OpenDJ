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
import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a ip bind rule keyword.
 */
public class IpCriteria implements KeywordBindRule {
    private EnumBindRuleType type=null;

    // private token to express that any address is accepted
    private final static String ANY_ADDRESSES = "ALL";
    private boolean matchAnyAddress = false;

    private IpBitsNetworkCriteria[] ipBitsCriteria = null;
    private IpMaskNetworkCriteria[] ipMaskCriteria = null;

    private static final String valueRegex =
                                   "([^," + ZERO_OR_MORE_WHITESPACE + "]+)";

    private static final String valuesRegex =
            valueRegex + ZERO_OR_MORE_WHITESPACE + "(," +
            ZERO_OR_MORE_WHITESPACE + valueRegex + ")*";

    /*
     * TODO Verifiy IpCriteria constructor adheres to DS 5.2 ip keyword
     * syntax.
     *
     * Based on the contents of the constructor, it doesn't appear that the
     * IpCriteria class uses the same set of allowed values as DS 5.2 does
     * with the "ip" keyword (as documented at
     * http://docs.sun.com/source/817-7613/aci.html#wp20242).  Was that the
     * intention?  It looks like it doesn't allow asterisks as wild-ccards or
     * plus signs to specify netmasks (although it appears that it expects a
     * slash to be  used if you want a netmask).  While I don't mind allowing
     * alternate formats (e.g., CIDR-style addresses), we can't drop support
     * for the existing ones if we're trying to maintain compatibility.
     */
    /**
     * Constructor that creates an IpCriteria from an array of values and
     * an enumeration bind rule type.
     * @param values An array of address values.
     * @param type An enumeration of the bind rule type.
     * @throws UnknownHostException If the host address cannot be resolved to
     * a hostname.
     * @throws AciException  If a part of the address is invalid.
     * @throws IndexOutOfBoundsException  If an index is incremented past an
     * array bounds when copying or evaluating an address.
     */
    public IpCriteria(String[] values, EnumBindRuleType type)
    throws UnknownHostException, IndexOutOfBoundsException, AciException {
        IpBitsNetworkCriteria[] ipBitsCriteria_2 = null;
        IpMaskNetworkCriteria[] ipMaskCriteria_2 = null;
        try
        {
            for (String value : values)
            {
                if (value.equalsIgnoreCase(ANY_ADDRESSES))
                {
                    matchAnyAddress = true;
                    continue;
                }
                // determine what format it is to instantiate
                // the right criteria object
                int slash = value.indexOf("/");
                if (slash == -1)
                {
                    // simple raw IP address
                    IpBitsNetworkCriteria newInstance;
                    if (InetAddress.getByName(value)
                            instanceof Inet6Address)
                    {
                        newInstance = new IpBitsNetworkCriteria(value, 128);
                    } else
                    {
                        newInstance = new IpBitsNetworkCriteria(value, 32);
                    }
                    if (ipBitsCriteria_2 == null)
                    {
                        ipBitsCriteria_2 = new IpBitsNetworkCriteria[1];
                    } else
                    {
                        IpBitsNetworkCriteria[] newIpBitsCriteria =
                         new IpBitsNetworkCriteria[ipBitsCriteria_2.length + 1];
                        System.arraycopy(ipBitsCriteria_2, 0,
                                newIpBitsCriteria, 0, ipBitsCriteria_2.length);
                        ipBitsCriteria_2 = newIpBitsCriteria;
                    }
                    ipBitsCriteria_2[ipBitsCriteria_2.length - 1] = newInstance;
                } else
                {
                    // Extract data following the / and figure out whether it
                    // is a bit number or a mask
                    try
                    {
                        int bits =
                                Integer.parseInt(value.substring(slash + 1));
                        // Well, no exception, so this is a bit
                        // Let's instantiate the corresponding criterion
                        if (ipBitsCriteria_2 == null)
                        {
                            ipBitsCriteria_2 = new IpBitsNetworkCriteria[1];
                        } else
                        {
                         IpBitsNetworkCriteria[] newIpBitsCriteria =
                         new IpBitsNetworkCriteria[ipBitsCriteria_2.length + 1];
                            System.arraycopy(ipBitsCriteria_2, 0,
                                 newIpBitsCriteria, 0, ipBitsCriteria_2.length);
                            ipBitsCriteria_2 = newIpBitsCriteria;
                        }
                        ipBitsCriteria_2[ipBitsCriteria_2.length - 1] =
                                new IpBitsNetworkCriteria(value.
                                        substring(0, slash), bits);
                    }
                    catch (IndexOutOfBoundsException e1)
                    {
                        throw e1;
                    }
                    catch (Exception e2)
                    {
                        // Looks like this is a network mask.
                        if (ipMaskCriteria_2 == null)
                        {
                            ipMaskCriteria_2 = new IpMaskNetworkCriteria[1];
                        } else
                        {
                        IpMaskNetworkCriteria[] newIpMaskCriteria =
                         new IpMaskNetworkCriteria[ipMaskCriteria_2.length + 1];
                            System.arraycopy(ipMaskCriteria_2, 0,
                                 newIpMaskCriteria, 0, ipMaskCriteria_2.length);
                            ipMaskCriteria_2 = newIpMaskCriteria;
                        }
                        try
                        {
                            ipMaskCriteria_2[ipMaskCriteria_2.length - 1] =
                              new IpMaskNetworkCriteria(value.
                              substring(0, slash), value.substring(slash + 1));
                        }
                        catch (IndexOutOfBoundsException e3)
                        {
                            throw e3;
                        }
                    }
                }
            }
        }
        catch (UnknownHostException ue)
        {
            throw ue;
        }
        ipBitsCriteria = ipBitsCriteria_2;
        ipMaskCriteria = ipMaskCriteria_2;
        this.type=type;
    }

    /**
     * Return the ipBitsNetworkCriteria of this  IpCriteria.
     * @return Returns the ipBitsNetworkCriteria.
     */
    public IpBitsNetworkCriteria[] getIpBitsNetworkCriteria() {
        return ipBitsCriteria;
    }

    /**
     * Return the ipMaskNetworkCriteria of this IpCriteria.
     * @return Returns the ipMaskNetworkCriteria.
     */
    public IpMaskNetworkCriteria[] getIpMaskNetworkCriteria() {
        return ipMaskCriteria;
    }

    /**
     * Compare an IP address with the network rule.
     *
     * @param  theSourceAddress   IP source address of the client.
     * @return  <CODE>true</CODE> if client matches the network rule or
     *          <CODE>false</CODE> if they may not.
     */
    public boolean match (InetAddress theSourceAddress)
    {
        if (matchAnyAddress){
            return true;
        }
        if (ipMaskCriteria != null)
        {
            for (IpMaskNetworkCriteria anIpMaskCriteria : ipMaskCriteria)
            {
                if (anIpMaskCriteria.match(theSourceAddress))
                {
                    return true;
                }
            }
        }

        if (ipBitsCriteria != null)
        {
            for (IpBitsNetworkCriteria anIpBitsCriteria : ipBitsCriteria)
            {
                if (anIpBitsCriteria.match(theSourceAddress))
                {
                    return true;
                }
            }
        }

        return (ipBitsCriteria == null) && (ipMaskCriteria == null);
    }

    /**
     * Decode an expression string representing a ip keyword bind rule
     * expression.
     * @param expr A string representing the expression.
     * @param type An enumeration representing the bind rule type.
     * @return  An keyword bind rule that can be used to evaluate the
     * expression.
     * @throws AciException  If the expression string is invalid.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
    throws AciException  {
        if (!Pattern.matches(valuesRegex, expr)) {
            int msgID = MSGID_ACI_SYNTAX_INVALID_IP_EXPRESSION;
            String message = getMessage(msgID, expr);
            throw new AciException(msgID, message);
        }

        int valuePos = 1;
        Pattern valuePattern = Pattern.compile(valueRegex);
        Matcher valueMatcher = valuePattern.matcher(expr);
        HashSet<String> values = new HashSet<String>();
        while (valueMatcher.find()) {
            String value = valueMatcher.group(valuePos);
            values.add(value);
        }
        IpCriteria ipCriteria;
        String[] strValues = null;
        if (!values.isEmpty()) {
            strValues = values.toArray(new String[values.size()]);
        }
        try {
            ipCriteria = new IpCriteria(strValues, type);
        } catch (Exception e) {
            int msgID = MSGID_ACI_SYNTAX_INVALID_IP_CRITERIA_DECODE;
            String message = getMessage(msgID, e.getMessage());
            throw new AciException(msgID, message);
        }
        return ipCriteria;
    }

    /**
     * Evaluate the evaluation context against this ip criteria.
     * @param evalCtx An evaluation context to use.
     * @return An enumeration evaluation result.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        if(match(evalCtx.getRemoteAddress()))
              matched=EnumEvalResult.TRUE;
        return matched.getRet(type, false);
    }
}

