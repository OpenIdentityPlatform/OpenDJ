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
 *      Copyright 2008 Sun Microsystems, Inc.
 */


package org.opends.server.authorization.dseecompat;
import org.opends.messages.Message;

import static org.opends.messages.AccessControlMessages.*;
import java.util.BitSet;
import java.util.HashMap;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.Inet6Address;

/**
 * A class representing a single IP address parsed from a IP bind rule
 * expression. The class can be used to evaluate a remote clients IP address
 * using the information parsed from the IP bind rule expression.
 */
public class PatternIP {

    /**
     * Enumeration that represents if the pattern is IPv5 or
     * IPv4.
     */
     enum IPType {
        IPv4, IPv6
    }

    /*
      The IP address type (v6 or v4).
     */
    private IPType ipType;

    /*
      IPv4 sizes of addresses and prefixes.
     */
    private static int IN4ADDRSZ = 4;
    private static int IPV4MAXPREFIX = 32;

    /*
      IPv6 sizes of addresses and prefixes.
     */
    private static int IN6ADDRSZ = 16;
    private static int IPV6MAXPREFIX = 128;

    /*
      Byte arrays used to match the remote IP address. The ruleAddrByte array
      contains the bytes of the address from the ACI IP bind rule. The
      rulePrefixBytes array contains the bytes of the cidr prefix or netmask
      representation.
     */
    private byte[] ruleAddrBytes, rulePrefixBytes;

    /*
      Bit set that holds the wild-card information of processed IPv4 addresses.
     */
    private BitSet wildCardBitSet;

    /*
      Hash map of valid netmask strings. Used in parsing netmask values.
     */
    private static HashMap<String,String> validNetMasks =
                                               new HashMap<String, String>();

    /*
     Initialize valid netmask hash map.
     */
    static {
        initNetMask(
                "255.255.255.255",
                "255.255.255.254",
                "255.255.255.252",
                "255.255.255.248",
                "255.255.255.240",
                "255.255.255.224",
                "255.255.255.192",
                "255.255.255.128",
                "255.255.255.0",
                "255.255.254.0",
                "255.255.252.0",
                "255.255.248.0",
                "255.255.240.0",
                "255.255.224.0",
                "255.255.192.0",
                "255.255.128.0",
                "255.255.0.0",
                "255.254.0.0",
                "255.252.0.0",
                "255.248.0.0",
                "255.240.0.0",
                "255.224.0.0",
                "255.192.0.0",
                "255.128.0.0",
                "255.0.0.0",
                "254.0.0.0",
                "252.0.0.0",
                "248.0.0.0",
                "240.0.0.0",
                "224.0.0.0",
                "192.0.0.0",
                "128.0.0.0",
                "0.0.0.0"
        );
    }

    /**
     * Load the valid netmask hash map with the 33 possible valid netmask
     * strings.
     *
      * @param lines The strings representing the valid netmasks.
     */
    private static void initNetMask(String... lines) {
        for(String line : lines) {
            validNetMasks.put(line, line);
        }
    }

    /**
     * Create a class that can be used to evaluate an IP address using the
     * information decoded from the ACI IP bind rule expression.
     *
     * @param ipType The type of the ACI IP address (IPv4 or 6).
     * @param ruleAddrBytes Byte array representing the ACI IP address.
     * @param rulePrefixBytes Prefix byte array corresponding to the bits set
     *                        by the cidr prefix or netmask.
     * @param wildCardBitSet Bit set holding IPv4 wild-card information.
     */
    private PatternIP(IPType ipType, byte[] ruleAddrBytes,
                      byte[] rulePrefixBytes, BitSet wildCardBitSet) {
       this.ipType=ipType;
       this.ruleAddrBytes=ruleAddrBytes;
       this.rulePrefixBytes=rulePrefixBytes;
       this.wildCardBitSet=wildCardBitSet;
    }

    /**
     * Decode the provided address expression string and create a class that
     * can be used to perform an evaluation of an IP address based on the
     * decoded expression string information.
     *
     * @param expr The address expression string from the ACI IP bind rule.
     * @return A class that can evaluate a remote clients IP address using the
     *         expression's information.
     * @throws AciException If the address expression is invalid.
     */
    public static
    PatternIP decode(String expr)  throws AciException {
        IPType ipType=IPType.IPv4;
        byte[] prefixBytes;
        String addrStr;
        if(expr.indexOf(':') != -1)
            ipType = IPType.IPv6;
        if(expr.indexOf('/') != -1) {
            String prefixStr=null;
            String[] s = expr.split("[/]", -1);
            if(s.length == 2) prefixStr=s[1];
            int prefix = getPrefixValue(ipType, s.length, expr, prefixStr);
            prefixBytes=getPrefixBytes(prefix, ipType);
            addrStr=s[0];
        } else if(expr.indexOf('+') != -1) {
            String netMaskStr=null;
            String[] s = expr.split("[+]", -1);
            if(s.length == 2)
                netMaskStr=s[1];
            prefixBytes=getNetmaskBytes(netMaskStr, s.length, expr);
            addrStr=s[0];
        } else {
            int prefix = getPrefixValue(ipType, 1, expr, null);
            prefixBytes=getPrefixBytes(prefix, ipType);
            addrStr=expr;
        }
        //Set the bit set size fo IN6ADDRSZ even though only 4 positions are
        //used.
        BitSet wildCardBitSet = new BitSet(IN6ADDRSZ);
        byte[] addrBytes;
        if(ipType == IPType.IPv4)
            addrBytes = procIPv4Addr(addrStr, wildCardBitSet, expr);
        else {
            addrBytes=procIPv6Addr(addrStr, expr);
            //The IPv6 address processed above might be a IPv4-compatible
            //address, in which case only 4 bytes will be returned in the
            //address byte  array. Ignore any IPv6 prefix.
            if(addrBytes.length == IN4ADDRSZ) {
                ipType=IPType.IPv4;
                prefixBytes=getPrefixBytes(IPV4MAXPREFIX, ipType);
            }
        }
        return new PatternIP(ipType, addrBytes, prefixBytes, wildCardBitSet);
    }

    /**
     * Process the IP address prefix part of the expression. Handles if there is
     * no prefix in the expression.
     *
     * @param ipType The type of the expression, either IPv6 or IPv4.
     * @param numParts The number of parts in the IP address expression.
     *                 1 if there isn't a prefix, and 2 if there is. Anything
     *                 else is an error (i.e., 254.244.123.234/7/6).
     * @param expr The original expression from the bind rule.
     * @param prefixStr The string representation of the prefix part of the
     *                  IP address.
     * @return  An integer value determined from the prefix string.
     * @throws AciException If the prefix string is invalid.
     */
    private static int
    getPrefixValue(IPType ipType, int numParts, String expr, String prefixStr)
    throws AciException {

        int prefix = IPV4MAXPREFIX;
        int maxPrefix= IPV4MAXPREFIX;
        if(ipType == IPType.IPv6) {
            prefix= IPV6MAXPREFIX;
            maxPrefix=IPV6MAXPREFIX;
        }
        try {
            //Can only have one prefix value and one address string.
            if((numParts  < 1) || (numParts > 2) ) {
                Message message =
                    WARN_ACI_SYNTAX_INVALID_PREFIX_FORMAT.get(expr);
                throw new AciException(message);
            }
            if(prefixStr != null)
                prefix = Integer.parseInt(prefixStr);
            //Must be between 0 to maxprefix.
            if((prefix < 0) || (prefix > maxPrefix)) {
                Message message =
                    WARN_ACI_SYNTAX_INVALID_PREFIX_VALUE.get(expr);
                throw new AciException(message);
            }
        } catch(NumberFormatException nfex) {
            Message msg = WARN_ACI_SYNTAX_PREFIX_NOT_NUMERIC.get(expr);
            throw new AciException(msg);
        }
        return prefix;
    }

    /**
     * Determine the prefix bit mask based on the provided prefix value. Handles
     * both IPv4 and IPv6 prefix values.
     *
     * @param prefix  The value of the prefix parsed from the address
     *                expression.
     * @param ipType  The type of the prefix, either IPv6 or IPv4.
     * @return A byte array representing the prefix bit mask used to match
     *         IP addresses.
     */
    private static byte[] getPrefixBytes(int prefix, IPType ipType) {
        int i;
        int maxSize=IN4ADDRSZ;
        if(ipType==IPType.IPv6)
            maxSize= IN6ADDRSZ;
        byte[] prefixBytes=new byte[maxSize];
        for(i=0;prefix > 8 ; i++) {
            prefixBytes[i] = (byte) 0xff;
            prefix -= 8;
        }
        prefixBytes[i] = (byte) ((0xff) << (8 - prefix));
        return prefixBytes;
    }

    /**
     * Process the specified netmask string. Only pertains to IPv4 address
     * expressions.
     *
     * @param netmaskStr String represntation of the netmask parsed from the
     *                   address expression.
     * @param numParts The number of parts in the IP address expression.
     *                 1 if there isn't a netmask, and 2 if there is. Anything
     *                 else is an error (i.e., 254.244.123.234++255.255.255.0).
     * @param expr The original expression from the bind rule.
     * @return A byte array representing the netmask bit mask used to match
     *         IP addresses.
     * @throws AciException If the netmask string is invalid.
     */
    private static
    byte[] getNetmaskBytes(String netmaskStr, int numParts, String expr)
    throws AciException {
        byte[] netmaskBytes=new byte[IN4ADDRSZ];
        //Look up the string in the valid netmask hash table. If it isn't
        //there it is an error.
        if(!validNetMasks.containsKey(netmaskStr)) {
            Message message = WARN_ACI_SYNTAX_INVALID_NETMASK.get(expr);
            throw new AciException(message);
        }
        //Can only have one netmask value and one address string.
        if((numParts  < 1) || (numParts > 2) ) {
            Message message = WARN_ACI_SYNTAX_INVALID_NETMASK_FORMAT.get(expr);
            throw new AciException(message);
        }
        String[] s = netmaskStr.split("\\.", -1);
        try {
            for(int i=0; i < IN4ADDRSZ; i++) {
                String quad=s[i].trim();
                long val=Integer.parseInt(quad);
                netmaskBytes[i] = (byte) (val & 0xff);
            }
        } catch (NumberFormatException nfex) {
            Message message = WARN_ACI_SYNTAX_IPV4_NOT_NUMERIC.get(expr);
            throw new AciException(message);
        }
        return netmaskBytes;
    }

    /**
     * Process the provided IPv4 address string parsed from the IP bind rule
     * address expression. It returns a byte array corresponding to the
     * address string.  The specified bit set represents wild-card characters
     * '*' found in the string.
     *
     * @param addrStr  A string representing an IPv4 address.
     * @param wildCardBitSet A bit set used to save wild-card information.
     * @param expr The original expression from the IP bind rule.
     * @return A address byte array that can be used along with the prefix bit
     *         mask to evaluate an IPv4 address.
     *
     * @throws AciException If the address string is not a valid IPv4 address
     *                      string.
     */
    private static byte[]
    procIPv4Addr(String addrStr, BitSet wildCardBitSet, String expr)
    throws AciException {
        byte[] addrBytes=new byte[IN4ADDRSZ];
        String[] s = addrStr.split("\\.", -1);
        try {
            if(s.length != IN4ADDRSZ) {
                Message message = WARN_ACI_SYNTAX_INVALID_IPV4_FORMAT.get(expr);
                throw new AciException(message);
            }
            for(int i=0; i < IN4ADDRSZ; i++) {
                String quad=s[i].trim();
                if(quad.equals("*"))
                    wildCardBitSet.set(i) ;
                else {
                    long val=Integer.parseInt(quad);
                    //must be between 0-255
                    if((val < 0) ||  (val > 0xff)) {
                        Message message =
                            WARN_ACI_SYNTAX_INVALID_IPV4_VALUE.get(expr);
                        throw new AciException(message);
                    }
                    addrBytes[i] = (byte) (val & 0xff);
                }
            }
        } catch (NumberFormatException nfex) {
            Message message = WARN_ACI_SYNTAX_IPV4_NOT_NUMERIC.get(expr);
            throw new AciException(message);
        }
        return addrBytes;
    }

    /**
     * Process the provided IPv6  address string parsed from the IP bind rule
     * IP expression. It returns a byte array corresponding to the
     * address string. Wild-cards are not allowed in IPv6 addresses.
     *
     * @param addrStr A string representing an IPv6 address.
     * @param expr The original expression from the IP bind rule.
     * @return A address byte array that can be used along with the prefix bit
     *         mask to evaluate an IPv6 address.
     * @throws AciException If the address string is not a valid IPv6 address
     *                      string.
     */
    private static byte[]
    procIPv6Addr(String addrStr, String expr) throws AciException {
        if(addrStr.indexOf('*') > -1) {
            Message message = WARN_ACI_SYNTAX_IPV6_WILDCARD_INVALID.get(expr);
            throw new AciException(message);
        }
        byte[] addrBytes;
        try {
            addrBytes=InetAddress.getByName(addrStr).getAddress();
        } catch (UnknownHostException ex) {
            Message message =
                WARN_ACI_SYNTAX_INVALID_IPV6_FORMAT.get(expr, ex.getMessage());
            throw new AciException(message);
        }
        return addrBytes;
    }

    /**
     * Evaluate the provided IP address against the information processed during
     * the IP bind rule expression decode.
     *
     * @param remoteAddr  A IP address to evaluate.
     * @return An enumeration representing the result of the evaluation.
     */
    public EnumEvalResult evaluate(InetAddress remoteAddr) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        IPType ipType=IPType.IPv4;
        byte[] addressBytes=remoteAddr.getAddress();
        if(remoteAddr instanceof Inet6Address) {
            ipType=IPType.IPv6;
            Inet6Address addr6 = (Inet6Address) remoteAddr;
            addressBytes= addr6.getAddress();
            if(addr6.isIPv4CompatibleAddress())
                ipType=IPType.IPv4;
        }
        if(ipType != this.ipType)
            return EnumEvalResult.FALSE;
        if(matchAddress(addressBytes))
            matched=EnumEvalResult.TRUE;
        return matched;
    }

    /**
     * Attempt to match the address byte array  using the  prefix bit mask array
     * and the address byte array processed in the decode. Wild-cards take
     * priority over the mask.
     *
     * @param addrBytes IP address byte array.
     * @return True if the remote address matches based on the information
     *         parsed from the IP bind rule expression.
     */
    private boolean matchAddress(byte[] addrBytes) {
        if(wildCardBitSet.cardinality() == IN4ADDRSZ)
            return true;
        for(int i=0;i <rulePrefixBytes.length; i++) {
            if(!wildCardBitSet.get(i)) {
                if((ruleAddrBytes[i] & rulePrefixBytes[i]) !=
                        (addrBytes[i] & rulePrefixBytes[i]))
                    return false;
            }
        }
        return true;
    }
}
