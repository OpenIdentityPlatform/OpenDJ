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
 * Portions copyright 2016 ForgeRock AS.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.forgerock.opendj.ldap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p><b>InetAddress</b> validation and conversion routines (<code>java.net.InetAddress</code>).</p>
 *
 * <p>This class provides methods to validate a candidate IP address.
 *
 * @since Validator 1.4
 */
final class InetAddressValidator {

    private static final int IPV4_MAX_OCTET_VALUE = 255;

    private static final int MAX_UNSIGNED_SHORT = 0xffff;

    private static final int BASE_16 = 16;

    private static final String IPV4_REGEX = "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$";

    // Max number of hex groups (separated by :) in an IPV6 address
    private static final int IPV6_MAX_HEX_GROUPS = 8;

    // Max hex digits in each IPv6 group
    private static final int IPV6_MAX_HEX_DIGITS_PER_GROUP = 4;

    /** IPv4 regex pattern. */
    private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);

    /** Private constructor. */
    private InetAddressValidator() {
    }

    /**
     * Checks if the specified string is a valid IP address.
     * @param inetAddress the string to validate
     * @return true if the string validates as an IP address
     */
    public static boolean isValid(String inetAddress) {
        return isValidInet4Address(inetAddress) || isValidInet6Address(inetAddress);
    }

    /**
     * Validates an IPv4 address. Returns true if valid.
     * @param inet4Address the IPv4 address to validate
     * @return true if the argument contains a valid IPv4 address
     */
    public static boolean isValidInet4Address(String inet4Address) {
        // verify that address conforms to generic IPv4 format
        String[] groups = match(inet4Address);

        if (groups == null) {
            return false;
        }

        // verify that address subgroups are legal
        for (String ipSegment : groups) {
            if (ipSegment == null || ipSegment.length() == 0) {
                return false;
            }

            int iIpSegment;

            try {
                iIpSegment = Integer.parseInt(ipSegment);
            } catch (NumberFormatException e) {
                return false;
            }

            if (iIpSegment > IPV4_MAX_OCTET_VALUE) {
                return false;
            }

            if (ipSegment.length() > 1 && ipSegment.startsWith("0")) {
                return false;
            }

        }

        return true;
    }

    /**
     * Validate a value against the set of regular expressions returning the array of matched groups. From the Apache
     * RegexValidator class.
     *
     * @param value  The value to validate
     * @return  String array of the matched groups, of {@code null} if invalid.
     */
    private static String[] match(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = IPV4_PATTERN.matcher(value);
        if (matcher.matches()) {
            int count = matcher.groupCount();
            String[] groups = new String[count];
            for (int j = 0; j < count; j++) {
                groups[j] = matcher.group(j + 1);
            }
            return groups;
        }
        return null;
    }

    /**
     * Validates an IPv6 address. Returns true if valid.
     * @param inet6Address the IPv6 address to validate
     * @return true if the argument contains a valid IPv6 address
     *
     * @since 1.4.1
     */
    public static boolean isValidInet6Address(String inet6Address) {
        boolean containsCompressedZeroes = inet6Address.contains("::");
        if (containsCompressedZeroes && (inet6Address.indexOf("::") != inet6Address.lastIndexOf("::"))) {
            return false;
        }
        if ((inet6Address.startsWith(":") && !inet6Address.startsWith("::"))
                || (inet6Address.endsWith(":") && !inet6Address.endsWith("::"))) {
            return false;
        }
        String[] octets = inet6Address.split(":");
        if (containsCompressedZeroes) {
            List<String> octetList = new ArrayList<>(Arrays.asList(octets));
            if (inet6Address.endsWith("::")) {
                // String.split() drops ending empty segments
                octetList.add("");
            } else if (inet6Address.startsWith("::") && !octetList.isEmpty()) {
                octetList.remove(0);
            }
            octets = octetList.toArray(new String[octetList.size()]);
        }
        if (octets.length > IPV6_MAX_HEX_GROUPS) {
            return false;
        }
        int validOctets = 0;
        int emptyOctets = 0;
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.length() == 0) {
                emptyOctets++;
                if (emptyOctets > 1) {
                    return false;
                }
            } else {
                emptyOctets = 0;
                if (octet.contains(".")) { // contains is Java 1.5+
                    if (!inet6Address.endsWith(octet)) {
                        return false;
                    }
                    if (index > octets.length - 1 || index > 6) {  // CHECKSTYLE IGNORE MagicNumber
                        // IPV4 occupies last two octets
                        return false;
                    }
                    if (!isValidInet4Address(octet)) {
                        return false;
                    }
                    validOctets += 2;
                    continue;
                }
                if (octet.length() > IPV6_MAX_HEX_DIGITS_PER_GROUP) {
                    return false;
                }
                int octetInt;
                try {
                    octetInt = Integer.valueOf(octet, BASE_16);
                } catch (NumberFormatException e) {
                    return false;
                }
                if (octetInt < 0 || octetInt > MAX_UNSIGNED_SHORT) {
                    return false;
                }
            }
            validOctets++;
        }
        if (validOctets < IPV6_MAX_HEX_GROUPS && !containsCompressedZeroes) {
            return false;
        }
        return true;
    }
}
