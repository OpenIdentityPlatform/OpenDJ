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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;

import org.opends.server.config.ConfigException;
import static org.opends.messages.ProtocolMessages.*;
import java.util.BitSet;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This class defines an address mask, which can be used to perform
 * efficient comparisons against IP addresses to determine whether a
 * particular IP address is in a given range.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class AddressMask
{
  /**
     * Types of rules we have.
     *
     * IPv4 - ipv4 rule
     * IPv6 - ipv6 rule (begin with '[' or contains an ':').
     * HOST - hostname match (foo.sun.com)
     * HOSTPATTERN - host pattern match (begin with '.')
     * ALLWILDCARD - *.*.*.* (first HOST is applied then ipv4)
     *
     */

     enum RuleType
    {
        IPv4, IPv6, HOSTPATTERN, ALLWILDCARD, HOST
    }

    // Type of rule determined
    private  RuleType ruleType;

    // IPv4 values for number of bytes and max CIDR prefix
    /**
     * IPv4 address size.
     */
    private static final int IN4ADDRSZ = 4;
    private static final int IPV4MAXPREFIX = 32;

    // IPv6 values for number of bytes and max CIDR prefix
    private static final int IN6ADDRSZ = 16;
    private static final int IPV6MAXPREFIX = 128;

    //Holds binary representations of rule and mask respectively.
    private  byte[] ruleMask, prefixMask;

    //Bit array that holds wildcard info for above binary arrays.
    private final BitSet wildCard = new BitSet();

    //Array that holds each component of a hostname.
    private  String[] hostName;

    //Holds a hostname pattern (ie, rule that begins with '.');'
    private  String hostPattern;

    //Holds string passed into the constructor.
    private  String ruleString;

    /**
     * Address mask constructor.
     * @param rule The rule string to process.
     * @throws ConfigException If the rule string is not valid.
     */
    private AddressMask( String rule)
    throws ConfigException
    {
        determineRuleType(rule);
        switch (ruleType)
        {
        case IPv6:
            processIPv6(rule);
             break;

        case IPv4:
            processIpv4(rule);
            break;

        case HOST:
            processHost(rule);
            break;

        case HOSTPATTERN:
            processHostPattern(rule);
            break;

        case ALLWILDCARD:
            processAllWilds(rule);
        }
        ruleString=rule;
    }

    /**
     * Try to determine what type of rule string this is. See
     * RuleType above for valid types.
     * @param ruleString The rule string to be examined.
     * @throws ConfigException If the rule type cannot be
     *         determined from the rule string.
     */
    private void  determineRuleType(String ruleString)
    throws ConfigException
    {

        //Rule ending with '.' is invalid'
        if(ruleString.endsWith("."))
        {
            Message message =
                ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
            throw new ConfigException(message);
        }
        else if(ruleString.startsWith("."))
        {
            ruleType=RuleType.HOSTPATTERN;
        }
        else if(ruleString.startsWith("[") ||
                (ruleString.indexOf(':') != -1))
        {
           ruleType=RuleType.IPv6;
        }
        else
        {
            int wildCount=0;
            String[] s = ruleString.split("\\.", -1);
            //Try to figure out how many wildcards and if the rule is
            // hostname (can't begin with digit) or ipv4 address.
            //Default to IPv4 ruletype.
            ruleType=RuleType.HOST;
            for (String value : s) {
                if (value.equals("*")) {
                    wildCount++;
                    continue;
                }
                //Looks like an ipv4 address
                if (Character.isDigit(value.charAt(0))) {
                    ruleType = RuleType.IPv4;
                    break;
                }
            }
            //All wildcards (*.*.*.*)
            if(wildCount == s.length)
            {
                ruleType=RuleType.ALLWILDCARD;
            }
        }
    }

    /**
     * The rule string is an IPv4 rule. Build both the prefix
     * mask array and rule mask from the string.
     * @param rule The rule string containing the IPv4 rule.
     * @throws ConfigException If the rule string is not a valid
     *         IPv4 rule.
     */
    private void processIpv4(String rule)
    throws ConfigException {
        String[] s = rule.split("/", -1);
        this.ruleMask=new byte[IN4ADDRSZ];
        this.prefixMask=new byte[IN4ADDRSZ];
        prefixMask(processPrefix(s,IPV4MAXPREFIX));
        processIPv4Subnet((s.length == 0) ? rule : s[0]);
    }

    /**
     * The rule string is all wildcards. Set both address wildcard
     * bitmask and hostname wildcard array.
     * @param rule The rule string containing all wildcards.
     */
    private void processAllWilds(String rule)
    {
        String s[]=rule.split("\\.", -1);
        if(s.length == IN4ADDRSZ)
        {
            for(int i=0;i<IN4ADDRSZ;i++)
                wildCard.set(i);
        }
        hostName=rule.split("\\.", -1);
    }

    /**
     * Examine the rule string of a host pattern and set the
     * host pattern from the rule.
     * @param rule The rule string to examine.
     * @throws ConfigException If the rule string is not a valid
     *         host pattern rule.
     */
    private void processHostPattern(String rule)
    throws ConfigException
    {
        //quick check for invalid chars like " "
        String s[]=rule.split("^[0-9a-zA-z-.]+");
        if(s.length > 0)
        {
            Message message =
                ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
            throw new ConfigException(message);
        }
        hostPattern=rule;
    }

    /**
     * Examine rule string and build a hostname string array
     * of its parts.
     * @param rule The rule string.
     * @throws ConfigException If the rule string is not a valid
     *         host name.
     */
    private void processHost(String rule)
    throws ConfigException
    {
        //Note that '*' is valid in host rule
        String s[]=rule.split("^[0-9a-zA-z-.*]+");
        if(s.length > 0)
        {
            Message message =
                ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
            throw new ConfigException(message);
        }
        hostName=rule.split("\\.", -1);
    }

    /**
     * Build the prefix mask of prefix len bits set in the array.
     * @param prefix The len of the prefix to use.
     */
    private void prefixMask(int prefix)
    {
        int i;
        for( i=0;prefix > 8 ; i++)
        {
            this.prefixMask[i] = (byte) 0xff;
            prefix -= 8;
        }
        this.prefixMask[i] = (byte) ((0xff) << (8 - prefix));
    }

    /**
     * Examine the subnet part of a rule string and build a
     * byte array representation of it.
     * @param subnet The subnet string part of the rule.
     * @throws ConfigException If the subnet string is not a valid
     *         IPv4 subnet string.
     */
    private void  processIPv4Subnet(String subnet)
    throws ConfigException {
        String[] s = subnet.split("\\.", -1);
        try {
            //Make sure we have four parts
            if(s.length != IN4ADDRSZ) {
                Message message =
                    ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
                throw new ConfigException(message);
            }
            for(int i=0; i < IN4ADDRSZ; i++)
            {
                String quad=s[i].trim();
                if(quad.equals("*"))
                    wildCard.set(i) ; //see wildcard mark bitset
                else
                {
                    long val=Integer.parseInt(quad);
                    //must be between 0-255
                    if((val < 0) ||  (val > 0xff))
                    {
                        Message message =
                            ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
                        throw new ConfigException(message);
                    }
                    ruleMask[i] = (byte) (val & 0xff);
                }
            }
        } catch (NumberFormatException nfex)
        {
            Message message =
                ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
            throw new ConfigException(message);
        }
    }

    /**
     * Examine rule string for correct prefix usage.
     * @param s The string array with rule string add and prefix
     *          strings.
     * @param maxPrefix The max value the prefix can be.
     * @return The prefix integer value.
     * @throws ConfigException If the string array and prefix
     *         are not valid.
     */
    private int  processPrefix(String[] s, int maxPrefix)
    throws ConfigException {
        int prefix=maxPrefix;
        try {
            //can only have one prefix value and a subnet string
            if((s.length  < 1) || (s.length > 2) )
            {
                Message message =
                    ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
                throw new ConfigException(message);
            }
            else  if(s.length == 2)
            {
                //can't have wildcard with a prefix
                if(s[0].indexOf('*') > -1)
                {
                    Message message =
                        ERR_ADDRESSMASK_WILDCARD_DECODE_ERROR.get();
                    throw new ConfigException(message);
                }
                prefix = Integer.parseInt(s[1]);
            }
            //must be between 0-maxprefix
            if((prefix < 0) || (prefix > maxPrefix))
            {
                Message message =
                    ERR_ADDRESSMASK_PREFIX_DECODE_ERROR.get();
                throw new ConfigException(message);
            }
        }
        catch(NumberFormatException nfex)
        {
            Message msg = ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
            throw new ConfigException(msg);
        }
        return prefix;
    }


    /**
     * Decodes the provided string as an address mask.
     *
     * @param  maskString  The string to decode as an address mask.
     *
     * @return  AddressMask  The address mask decoded from the
     *                       provided string.
     *
     * @throws  ConfigException  If the provided string cannot be
     *                           decoded as an address mask.
     */


    public static AddressMask decode(String maskString)
    throws ConfigException {
        return new AddressMask(maskString);
    }

    /**
     * Indicates whether provided address or hostname matches one of
     * the address masks in the provided array.
     *
     * @param remoteAddr The remote address byte array.
     * @param remoteName The remote host name string.
     * @param masks      An array of address masks to check.
     * @return <CODE>true</CODE> if the provided address or hostname
     *          does match one of the given address masks, or
     *         <CODE>false</CODE> if it does not.
     */
    public  static boolean maskListContains(byte[] remoteAddr,
                                            String remoteName,
                                            AddressMask[] masks)
    {
        for (AddressMask mask : masks) {
            if(mask.match(remoteAddr, remoteName))
                return true;
        }
        return false;
    }

    /**
     * Retrieves a string representation of this address mask.
     *
     * @return  A string representation of this address mask.
     */
    public String toString()
    {
        return ruleString;
    }

    /**
     * Main match function that determines which rule-type match
     * function to use.
     * @param remoteAddr The remote client address byte array.
     * @param remoteName The remote client host name.
     * @return <CODE>true</CODE>if one of the match functions found
     *         a match or <CODE>false</CODE>if not.
     */
    private boolean match(byte[] remoteAddr, String remoteName)
    {
        boolean ret=false;

        switch(ruleType) {
        case IPv6:
        case IPv4:
            //this Address mask is an IPv4 rule
            ret=matchAddress(remoteAddr);
            break;

        case HOST:
            // HOST rule use hostname
            ret=matchHostName(remoteName);
            break;

        case HOSTPATTERN:
            //HOSTPATTERN rule
            ret=matchPattern(remoteName);
            break;

        case ALLWILDCARD:
            //first try  ipv4 addr match, then hostname
            ret=matchAddress(remoteAddr);
            if(!ret)
                ret=matchHostName(remoteName);
            break;
        }
        return ret;
    }

    /**
     * Try to match remote host name string against the pattern rule.
     * @param remoteHostName The remote client host name.
     * @return <CODE>true</CODE>if the remote host name matches or
     *         <CODE>false</CODE>if not.
     */
    private boolean matchPattern(String remoteHostName) {
        int len=remoteHostName.length() - hostPattern.length();
        return len > 0 && remoteHostName.regionMatches(true, len,
                hostPattern, 0, hostPattern.length());
    }

    /**
     * Try to match remote client host name against rule host name.
     * @param remoteHostName The remote host name string.
     * @return <CODE>true</CODE>if the remote client host name matches
     *         <CODE>false</CODE> if it does not.
     */
    private boolean matchHostName(String remoteHostName) {
        String[] s = remoteHostName.split("\\.", -1);
        if(s.length != hostName.length)
            return false;
        if(ruleType == RuleType.ALLWILDCARD)
            return true;
        for(int i=0;i<s.length;i++)
        {
            if(!hostName[i].equals("*")) //skip if wildcard
            {
                if(!s[i].equalsIgnoreCase(hostName[i]))
                    return false;
            }
        }
        return true;
    }


    /**
     * Try to match remote client address using prefix mask and
     * rule mask.
     * @param remoteMask The byte array with remote client address.
     * @return <CODE>true</CODE> if remote client address matches or
     *         <CODE>false</CODE>if not.
     */
    private boolean matchAddress(byte[] remoteMask)
    {
        if(prefixMask== null)
            return false;
        if(remoteMask.length != prefixMask.length)
            return false;
        if(ruleType  == RuleType.ALLWILDCARD)
            return true;
        for(int i=0;i < prefixMask.length; i++)
        {
            if(!wildCard.get(i))
            {
                if((ruleMask[i] & prefixMask[i]) !=
                    (remoteMask[i] & prefixMask[i]))
                    return false;
            }
        }
        return true;
    }

    /**
     * The rule string is an IPv6 rule. Build both the prefix
     * mask array and rule mask from the string.
     *
     * @param rule The rule string containing the IPv6 rule.
     * @throws ConfigException If the rule string is not a valid
     *         IPv6 rule.
     */
     private void processIPv6(String rule) throws ConfigException {
        String[] s = rule.split("/", -1);
        InetAddress addr;
        try {
            addr = InetAddress.getByName(s[0]);
        } catch (UnknownHostException ex) {
            Message message =
                ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
            throw new ConfigException(message);
        }
        if(addr instanceof Inet6Address) {
            this.ruleType=RuleType.IPv6;
            Inet6Address addr6 = (Inet6Address) addr;
            this.ruleMask=addr6.getAddress();
            this.prefixMask=new byte[IN6ADDRSZ];
            prefixMask(processPrefix(s,IPV6MAXPREFIX));
        } else {
           //The address might be an IPv4-compat address.
           //Throw an error if the rule has a prefix.
            if(s.length == 2) {
                Message message =
                    ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get();
                throw new ConfigException(message);
            }
            this.ruleMask=addr.getAddress();
            this.ruleType=RuleType.IPv4;
            this.prefixMask=new byte[IN4ADDRSZ];
            prefixMask(processPrefix(s,IPV4MAXPREFIX));
        }
    }
}


