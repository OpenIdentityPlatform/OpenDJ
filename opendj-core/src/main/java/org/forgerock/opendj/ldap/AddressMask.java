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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.BitSet;
import java.util.Collection;

import org.forgerock.i18n.LocalizedIllegalArgumentException;

/**
 * An address mask can be used to perform efficient comparisons against IP
 * addresses to determine whether a particular IP address is in a given range.
 */
public final class AddressMask {
    /**
     * Types of rules we have. IPv4 - ipv4 rule IPv6 - ipv6 rule (begin with '['
     * or contains an ':'). HOST - hostname match (foo.sun.com) HOSTPATTERN -
     * host pattern match (begin with '.') ALLWILDCARD - *.*.*.* (first HOST is
     * applied then ipv4)
     */
    enum RuleType {
        ALLWILDCARD, HOST, HOSTPATTERN, IPv4, IPv6
    }

    /** IPv4 values for number of bytes and max CIDR prefix. */
    private static final int IN4ADDRSZ = 4;
    private static final int IPV4MAXPREFIX = 32;

    /** IPv6 values for number of bytes and max CIDR prefix. */
    private static final int IN6ADDRSZ = 16;
    private static final int IPV6MAXPREFIX = 128;

    /**
     * Returns {@code true} if an address matches any of the provided address
     * masks.
     *
     * @param address
     *            The address.
     * @param masks
     *            A collection of address masks to check.
     * @return {@code true} if an address matches any of the provided address
     *         masks.
     */
    public static boolean matchesAny(final Collection<AddressMask> masks, final InetAddress address) {
        if (address != null) {
            for (final AddressMask mask : masks) {
                if (mask.matches(address)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Parses the provided string as an address mask.
     *
     * @param mask
     *            The address mask string to be parsed.
     * @return The parsed address mask.
     * @throws LocalizedIllegalArgumentException
     *             If the provided string cannot be decoded as an address mask.
     */
    public static AddressMask valueOf(final String mask) {
        return new AddressMask(mask);
    }

    /** Array that holds each component of a hostname. */
    private String[] hostName;

    /** Holds a hostname pattern (ie, rule that begins with '.');'. */
    private String hostPattern;

    /** Holds binary representations of rule and mask respectively. */
    private byte[] ruleMask, prefixMask;

    /** Holds string passed into the constructor. */
    private final String ruleString;

    /** Type of rule determined. */
    private RuleType ruleType;

    /** Bit array that holds wildcard info for above binary arrays. */
    private final BitSet wildCard = new BitSet();

    private AddressMask(final String rule) {
        determineRuleType(rule);
        switch (ruleType) {
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
        ruleString = rule;
    }

    /**
     * Returns {@code true} if this address mask matches the provided address.
     *
     * @param address
     *            The address.
     * @return {@code true} if this address mask matches the provided address.
     */
    public boolean matches(final InetAddress address) {
        boolean ret = false;

        switch (ruleType) {
        case IPv6:
        case IPv4:
            // this Address mask is an IPv4 rule
            ret = matchAddress(address.getAddress());
            break;

        case HOST:
            // HOST rule use hostname
            ret = matchHostName(address.getHostName());
            break;

        case HOSTPATTERN:
            // HOSTPATTERN rule
            ret = matchPattern(address.getHostName());
            break;

        case ALLWILDCARD:
            // first try ipv4 addr match, then hostname
            ret = matchAddress(address.getAddress());
            if (!ret) {
                ret = matchHostName(address.getHostName());
            }
            break;
        }
        return ret;
    }

    /**
     * Returns the string representation of this address mask.
     *
     * @return The string representation of this address mask.
     */
    @Override
    public String toString() {
        return ruleString;
    }

    /**
     * Try to determine what type of rule string this is. See RuleType above for
     * valid types.
     *
     * @param ruleString
     *            The rule string to be examined.
     * @throws LocalizedIllegalArgumentException
     *             If the rule type cannot be determined from the rule string.
     */
    private void determineRuleType(final String ruleString) {
        // Rule ending with '.' is invalid'
        if (ruleString.endsWith(".")) {
            throw genericDecodeError();
        } else if (ruleString.startsWith(".")) {
            ruleType = RuleType.HOSTPATTERN;
        } else if (ruleString.startsWith("[") || ruleString.indexOf(':') != -1) {
            ruleType = RuleType.IPv6;
        } else {
            int wildcardsCount = 0;
            final String[] s = ruleString.split("\\.", -1);
            /*
             * Try to figure out how many wildcards and if the rule is hostname
             * (can't begin with digit) or ipv4 address. Default to IPv4 ruletype.
             */
            ruleType = RuleType.HOST;
            for (final String value : s) {
                if ("*".equals(value)) {
                    wildcardsCount++;
                    continue;
                }
                // Looks like an ipv4 address
                if (Character.isDigit(value.charAt(0))) {
                    ruleType = RuleType.IPv4;
                    break;
                }
            }
            // All wildcards (*.*.*.*)
            if (wildcardsCount == s.length) {
                ruleType = RuleType.ALLWILDCARD;
            }
        }
    }

    /**
     * Try to match remote client address using prefix mask and rule mask.
     *
     * @param remoteMask
     *            The byte array with remote client address.
     * @return <CODE>true</CODE> if remote client address matches or
     *         <CODE>false</CODE>if not.
     */
    private boolean matchAddress(final byte[] remoteMask) {
        if (ruleType == RuleType.ALLWILDCARD) {
            return true;
        }
        if (prefixMask == null) {
            return false;
        }
        if (remoteMask.length != prefixMask.length) {
            return false;
        }
        for (int i = 0; i < prefixMask.length; i++) {
            if (!wildCard.get(i)
                    && (ruleMask[i] & prefixMask[i]) != (remoteMask[i] & prefixMask[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Try to match remote client host name against rule host name.
     *
     * @param remoteHostName
     *            The remote host name string.
     * @return <CODE>true</CODE>if the remote client host name matches
     *         <CODE>false</CODE> if it does not.
     */
    private boolean matchHostName(final String remoteHostName) {
        final String[] s = remoteHostName.split("\\.", -1);
        if (s.length != hostName.length) {
            return false;
        }
        if (ruleType == RuleType.ALLWILDCARD) {
            return true;
        }
        for (int i = 0; i < s.length; i++) {
            // skip if wildcard
            if (!"*".equals(hostName[i])
                    && !s[i].equalsIgnoreCase(hostName[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Try to match remote host name string against the pattern rule.
     *
     * @param remoteHostName
     *            The remote client host name.
     * @return <CODE>true</CODE>if the remote host name matches or
     *         <CODE>false</CODE>if not.
     */
    private boolean matchPattern(final String remoteHostName) {
        final int len = remoteHostName.length() - hostPattern.length();
        return len > 0
                && remoteHostName.regionMatches(true, len, hostPattern, 0, hostPattern.length());
    }

    /**
     * Build the prefix mask of prefix len bits set in the array.
     *
     * @param prefix
     *            The len of the prefix to use.
     */
    private void prefixMask(int prefix) {
        int i;
        for (i = 0; prefix > 8; i++) {
            this.prefixMask[i] = (byte) 0xff;
            prefix -= 8;
        }
        this.prefixMask[i] = (byte) (0xff << 8 - prefix);
    }

    /**
     * The rule string is all wildcards. Set both address wildcard bitmask and
     * hostname wildcard array.
     *
     * @param rule
     *            The rule string containing all wildcards.
     */
    private void processAllWilds(final String rule) {
        final String[] s = rule.split("\\.", -1);
        if (s.length == IN4ADDRSZ) {
            for (int i = 0; i < IN4ADDRSZ; i++) {
                wildCard.set(i);
            }
        }
        hostName = rule.split("\\.", -1);
    }

    /**
     * Examine rule string and build a hostname string array of its parts.
     *
     * @param rule
     *            The rule string.
     * @throws LocalizedIllegalArgumentException
     *             If the rule string is not a valid host name.
     */
    private void processHost(final String rule) {
        // Note that '*' is valid in host rule
        final String[] s = rule.split("^[0-9a-zA-z-.*]+");
        if (s.length > 0) {
            throw genericDecodeError();
        }
        hostName = rule.split("\\.", -1);
    }

    /**
     * Examine the rule string of a host pattern and set the host pattern from
     * the rule.
     *
     * @param rule
     *            The rule string to examine.
     * @throws LocalizedIllegalArgumentException
     *             If the rule string is not a valid host pattern rule.
     */
    private void processHostPattern(final String rule) {
        // quick check for invalid chars like " "
        final String[] s = rule.split("^[0-9a-zA-z-.]+");
        if (s.length > 0) {
            throw genericDecodeError();
        }
        hostPattern = rule;
    }

    /**
     * The rule string is an IPv4 rule. Build both the prefix mask array and
     * rule mask from the string.
     *
     * @param rule
     *            The rule string containing the IPv4 rule.
     * @throws LocalizedIllegalArgumentException
     *             If the rule string is not a valid IPv4 rule.
     */
    private void processIpv4(final String rule) {
        final String[] s = rule.split("/", -1);
        this.ruleMask = new byte[IN4ADDRSZ];
        this.prefixMask = new byte[IN4ADDRSZ];
        prefixMask(processPrefix(s, IPV4MAXPREFIX));
        processIPv4Subnet(s.length == 0 ? rule : s[0]);
    }

    /**
     * Examine the subnet part of a rule string and build a byte array
     * representation of it.
     *
     * @param subnet
     *            The subnet string part of the rule.
     * @throws LocalizedIllegalArgumentException
     *             If the subnet string is not a valid IPv4 subnet string.
     */
    private void processIPv4Subnet(final String subnet) {
        final String[] s = subnet.split("\\.", -1);
        try {
            // Make sure we have four parts
            if (s.length != IN4ADDRSZ) {
                throw genericDecodeError();
            }
            for (int i = 0; i < IN4ADDRSZ; i++) {
                final String quad = s[i].trim();
                if ("*".equals(quad)) {
                    wildCard.set(i); // see wildcard mark bitset
                } else {
                    final long val = Integer.parseInt(quad);
                    // must be between 0-255
                    if (val < 0 || val > 0xff) {
                        throw genericDecodeError();
                    }
                    ruleMask[i] = (byte) (val & 0xff);
                }
            }
        } catch (final NumberFormatException nfex) {
            throw genericDecodeError();
        }
    }

    /**
     * The rule string is an IPv6 rule. Build both the prefix mask array and
     * rule mask from the string.
     *
     * @param rule
     *            The rule string containing the IPv6 rule.
     * @throws LocalizedIllegalArgumentException
     *             If the rule string is not a valid IPv6 rule.
     */
    private void processIPv6(final String rule) {
        final String[] s = rule.split("/", -1);
        final String address = s[0];

        // Try to avoid calling InetAddress.getByName() because it may do a reverse lookup.
        final String ipv6Literal;
        if (address.charAt(0) == '[' && address.charAt(address.length() - 1) == ']') {
            // isIPv6LiteralAddress must be invoked without surrounding brackets.
            ipv6Literal = address.substring(1, address.length() - 1);
        } else {
            ipv6Literal = address;
        }

        boolean isValid;
        try {
            // Use reflection to avoid dependency on Sun JRE.
            final Class<?> ipUtils = Class.forName("sun.net.util.IPAddressUtil");
            final Method method = ipUtils.getMethod("isIPv6LiteralAddress", String.class);
            isValid = (Boolean) method.invoke(null, ipv6Literal);
        } catch (Exception e) {
            /*
             * Unable to invoke Sun private API. Assume it's ok, but accept that
             * a DNS query may be performed if it is not valid.
             */
            isValid = true;
        }
        if (!isValid) {
            throw genericDecodeError();
        }

        final InetAddress addr;
        try {
            addr = InetAddress.getByName(address);
        } catch (final UnknownHostException ex) {
            throw genericDecodeError();
        }
        if (addr instanceof Inet6Address) {
            this.ruleType = RuleType.IPv6;
            final Inet6Address addr6 = (Inet6Address) addr;
            this.ruleMask = addr6.getAddress();
            this.prefixMask = new byte[IN6ADDRSZ];
            prefixMask(processPrefix(s, IPV6MAXPREFIX));
        } else {
            /*
             * The address might be an IPv4-compat address. Throw an error if
             * the rule has a prefix.
             */
            if (s.length == 2) {
                throw genericDecodeError();
            }
            this.ruleMask = addr.getAddress();
            this.ruleType = RuleType.IPv4;
            this.prefixMask = new byte[IN4ADDRSZ];
            prefixMask(processPrefix(s, IPV4MAXPREFIX));
        }
    }

    /**
     * Examine rule string for correct prefix usage.
     *
     * @param s
     *            The string array with rule string add and prefix strings.
     * @param maxPrefix
     *            The max value the prefix can be.
     * @return The prefix integer value.
     * @throws LocalizedIllegalArgumentException
     *             If the string array and prefix are not valid.
     */
    private int processPrefix(final String[] s, final int maxPrefix) {
        int prefix = maxPrefix;
        try {
            // can only have one prefix value and a subnet string
            if (s.length < 1 || s.length > 2) {
                throw genericDecodeError();
            } else if (s.length == 2) {
                // can't have wildcard with a prefix
                if (s[0].indexOf('*') > -1) {
                    throw new LocalizedIllegalArgumentException(
                            ERR_ADDRESSMASK_WILDCARD_DECODE_ERROR.get());
                }
                prefix = Integer.parseInt(s[1]);
            }
            // must be between 0-maxprefix
            if (prefix < 0 || prefix > maxPrefix) {
                throw new LocalizedIllegalArgumentException(ERR_ADDRESSMASK_PREFIX_DECODE_ERROR
                        .get());
            }
        } catch (final NumberFormatException nfex) {
            throw genericDecodeError();
        }
        return prefix;
    }

    private LocalizedIllegalArgumentException genericDecodeError() {
        return new LocalizedIllegalArgumentException(ERR_ADDRESSMASK_FORMAT_DECODE_ERROR.get());
    }
}
