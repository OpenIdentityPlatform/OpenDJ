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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

import com.forgerock.opendj.util.StaticUtils;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

/**
 * An LDAP URL as defined in RFC 4516. In addition, the secure ldap (ldaps://)
 * is also supported. LDAP URLs have the following format:
 *
 * <PRE>
 * "ldap[s]://" [ <I>hostName</I> [":" <I>portNumber</I>] ]
 *          "/" <I>distinguishedName</I>
 *          ["?" <I>attributeList</I>
 *              ["?" <I>scope</I> "?" <I>filterString</I> ] ]
 * </PRE>
 *
 * Where:
 * <UL>
 * <LI>all text within double-quotes are literal
 * <LI><CODE><I>hostName</I></CODE> and <CODE><I>portNumber</I></CODE> identify
 * the location of the LDAP server.
 * <LI><CODE><I>distinguishedName</I></CODE> is the name of an entry within the
 * given directory (the entry represents the starting point of the search).
 * <LI><CODE><I>attributeList</I></CODE> contains a list of attributes to
 * retrieve (if null, fetch all attributes). This is a comma-delimited list of
 * attribute names.
 * <LI><CODE><I>scope</I></CODE> is one of the following:
 * <UL>
 * <LI><CODE>base</CODE> indicates that this is a search only for the specified
 * entry
 * <LI><CODE>one</CODE> indicates that this is a search for matching entries one
 * level under the specified entry (and not including the entry itself)
 * <LI><CODE>sub</CODE> indicates that this is a search for matching entries at
 * all levels under the specified entry (including the entry itself)
 * <LI><CODE>subordinates</CODE> indicates that this is a search for matching
 * entries all levels under the specified entry (excluding the entry itself)
 * </UL>
 * If not specified, <CODE><I>scope</I></CODE> is <CODE>base</CODE> by default.
 * <LI><CODE><I>filterString</I></CODE> is a human-readable representation of
 * the search criteria. If no filter is provided, then a default of "
 * {@code (objectClass=*)}" should be assumed.
 * </UL>
 * The same encoding rules for other URLs (e.g. HTTP) apply for LDAP URLs.
 * Specifically, any "illegal" characters are escaped with
 * <CODE>%<I>HH</I></CODE>, where <CODE><I>HH</I></CODE> represent the two hex
 * digits which correspond to the ASCII value of the character. This encoding is
 * only legal (or necessary) on the DN and filter portions of the URL.
 * <P>
 * Note that this class does not implement extensions.
 *
 * @see <a href="http://www.ietf.org/rfc/rfc4516">RFC 4516 - Lightweight
 *      Directory Access Protocol (LDAP): Uniform Resource Locator</a>
 */
public final class LDAPUrl {
    /**
     * The scheme corresponding to an LDAP URL. RFC 4516 mandates only ldap
     * scheme but we support "ldaps" too.
     */
    private final boolean isSecured;

    /**
     * The host name corresponding to an LDAP URL.
     */
    private final String host;

    /**
     * The port number corresponding to an LDAP URL.
     */
    private final int port;

    /**
     * The distinguished name corresponding to an LDAP URL.
     */
    private final DN name;

    /**
     * The search scope corresponding to an LDAP URL.
     */
    private final SearchScope scope;

    /**
     * The search filter corresponding to an LDAP URL.
     */
    private final Filter filter;

    /**
     * The attributes that need to be searched.
     */
    private final List<String> attributes;

    /**
     * The String value of LDAP URL.
     */
    private final String urlString;

    /**
     * Normalized ldap URL.
     */
    private String normalizedURL;

    /**
     * The default scheme to be used with LDAP URL.
     */
    private static final String DEFAULT_URL_SCHEME = "ldap";

    /**
     * The SSL-based scheme allowed to be used with LDAP URL.
     */
    private static final String SSL_URL_SCHEME = "ldaps";

    /**
     * The default host.
     */
    private static final String DEFAULT_HOST = "localhost";

    /**
     * The default non-SSL port.
     */
    private static final int DEFAULT_PORT = 389;

    /**
     * The default SSL port.
     */
    private static final int DEFAULT_SSL_PORT = 636;

    /**
     * The default filter.
     */
    private static final Filter DEFAULT_FILTER = Filter.objectClassPresent();

    /**
     * The default search scope.
     */
    private static final SearchScope DEFAULT_SCOPE = SearchScope.BASE_OBJECT;

    /**
     * The default distinguished name.
     */
    private static final DN DEFAULT_DN = DN.rootDN();

    /**
     * The % encoding character.
     */
    private static final char PERCENT_ENCODING_CHAR = '%';

    /**
     * The ? character.
     */
    private static final char QUESTION_CHAR = '?';

    /**
     * The slash (/) character.
     */
    private static final char SLASH_CHAR = '/';

    /**
     * The comma (,) character.
     */
    private static final char COMMA_CHAR = ',';

    /**
     * The colon (:) character.
     */
    private static final char COLON_CHAR = ':';

    /**
     * Set containing characters that do not need to be encoded.
     */
    private static final Set<Character> VALID_CHARS = new HashSet<>();

    static {
        // Refer to RFC 3986 for more details.
        final char[] delims = {
            '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', '.', '-', '_', '~'
        };
        for (final char c : delims) {
            VALID_CHARS.add(c);
        }

        for (char c = 'a'; c <= 'z'; c++) {
            VALID_CHARS.add(c);
        }

        for (char c = 'A'; c <= 'Z'; c++) {
            VALID_CHARS.add(c);
        }

        for (char c = '0'; c <= '9'; c++) {
            VALID_CHARS.add(c);
        }
    }

    /**
     * Parses the provided LDAP string representation of an LDAP URL using the
     * default schema.
     *
     * @param url
     *            The LDAP string representation of an LDAP URL.
     * @return The parsed LDAP URL.
     * @throws LocalizedIllegalArgumentException
     *             If {@code url} is not a valid LDAP string representation of
     *             an LDAP URL.
     * @throws NullPointerException
     *             If {@code url} was {@code null}.
     */
    public static LDAPUrl valueOf(final String url) {
        return valueOf(url, Schema.getDefaultSchema());
    }

    /**
     * Parses the provided LDAP string representation of an LDAP URL using the
     * provided schema.
     *
     * @param url
     *            The LDAP string representation of an LDAP URL.
     * @param schema
     *            The schema to use when parsing the LDAP URL.
     * @return The parsed LDAP URL.
     * @throws LocalizedIllegalArgumentException
     *             If {@code url} is not a valid LDAP string representation of
     *             an LDAP URL.
     * @throws NullPointerException
     *             If {@code url} or {@code schema} was {@code null}.
     */
    public static LDAPUrl valueOf(final String url, final Schema schema) {
        Reject.ifNull(url, schema);
        return new LDAPUrl(url, schema);
    }

    private static int decodeHex(final String url, final int index, final char hexChar) {
        if (hexChar >= '0' && hexChar <= '9') {
            return hexChar - '0';
        } else if (hexChar >= 'A' && hexChar <= 'F') {
            return hexChar - 'A' + 10;
        } else if (hexChar >= 'a' && hexChar <= 'f') {
            return hexChar - 'a' + 10;
        }

        final LocalizableMessage msg = ERR_LDAPURL_INVALID_HEX_BYTE.get(url, index);
        throw new LocalizedIllegalArgumentException(msg);
    }

    private static void percentDecoder(final String urlString, final int index, final String s,
            final StringBuilder decoded) {
        Reject.ifNull(s);
        Reject.ifNull(decoded);
        decoded.append(s);

        int srcPos = 0, dstPos = 0;

        while (srcPos < decoded.length()) {
            if (decoded.charAt(srcPos) != '%') {
                if (srcPos != dstPos) {
                    decoded.setCharAt(dstPos, decoded.charAt(srcPos));
                }
                srcPos++;
                dstPos++;
                continue;
            }
            int i = decodeHex(urlString, index + srcPos + 1, decoded.charAt(srcPos + 1)) << 4;
            int j = decodeHex(urlString, index + srcPos + 2, decoded.charAt(srcPos + 2));
            decoded.setCharAt(dstPos, (char) (i | j));
            dstPos++;
            srcPos += 3;
        }
        decoded.setLength(dstPos);
    }

    /**
     * This method performs the percent-encoding as defined in section 2.1 of
     * RFC 3986.
     *
     * @param urlElement
     *            The element of the URL that needs to be percent encoded.
     * @param encodedBuffer
     *            The buffer that contains the final percent encoded value.
     */
    private static void percentEncoder(final String urlElement, final StringBuilder encodedBuffer) {
        Reject.ifNull(urlElement);
        for (int count = 0; count < urlElement.length(); count++) {
            final char c = urlElement.charAt(count);
            if (VALID_CHARS.contains(c)) {
                encodedBuffer.append(c);
            } else {
                encodedBuffer.append(PERCENT_ENCODING_CHAR);
                encodedBuffer.append(Integer.toHexString(c));
            }
        }
    }

    /**
     * Creates a new LDAP URL referring to a single entry on the specified
     * server. The LDAP URL with have base object scope and the filter
     * {@code (objectClass=*)}.
     *
     * @param isSecured
     *            {@code true} if this LDAP URL should use LDAPS or
     *            {@code false} if it should use LDAP.
     * @param host
     *            The name or IP address in dotted format of the LDAP server.
     *            For example, {@code ldap.server1.com} or
     *            {@code 192.202.185.90}. Use {@code null} for the local host.
     * @param port
     *            The port number of the LDAP server, or {@code null} to use the
     *            default port (389 for LDAP and 636 for LDAPS).
     * @param name
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed, or {@code null} to specify the root
     *            DSE.
     * @throws LocalizedIllegalArgumentException
     *             If {@code port} was less than 1 or greater than 65535.
     */
    public LDAPUrl(final boolean isSecured, final String host, final Integer port, final DN name) {
        this(isSecured, host, port, name, DEFAULT_SCOPE, DEFAULT_FILTER);
    }

    /**
     * Creates a new LDAP URL including the full set of parameters for a search
     * request.
     *
     * @param isSecured
     *            {@code true} if this LDAP URL should use LDAPS or
     *            {@code false} if it should use LDAP.
     * @param host
     *            The name or IP address in dotted format of the LDAP server.
     *            For example, {@code ldap.server1.com} or
     *            {@code 192.202.185.90}. Use {@code null} for the local host.
     * @param port
     *            The port number of the LDAP server, or {@code null} to use the
     *            default port (389 for LDAP and 636 for LDAPS).
     * @param name
     *            The distinguished name of the base entry relative to which the
     *            search is to be performed, or {@code null} to specify the root
     *            DSE.
     * @param scope
     *            The search scope, or {@code null} to specify base scope.
     * @param filter
     *            The search filter, or {@code null} to specify the filter
     *            {@code (objectClass=*)}.
     * @param attributes
     *            The list of attributes to be included in the search results.
     * @throws LocalizedIllegalArgumentException
     *             If {@code port} was less than 1 or greater than 65535.
     */
    public LDAPUrl(final boolean isSecured, final String host, final Integer port, final DN name,
            final SearchScope scope, final Filter filter, final String... attributes) {
        // The buffer storing the encoded url.
        final StringBuilder urlBuffer = new StringBuilder();

        // build the scheme.
        this.isSecured = isSecured;
        if (this.isSecured) {
            urlBuffer.append(SSL_URL_SCHEME);
        } else {
            urlBuffer.append(DEFAULT_URL_SCHEME);
        }
        urlBuffer.append("://");

        if (host == null) {
            this.host = DEFAULT_HOST;
        } else {
            this.host = host;
            urlBuffer.append(this.host);
        }

        int listenPort = DEFAULT_PORT;
        if (port == null) {
            listenPort = isSecured ? DEFAULT_SSL_PORT : DEFAULT_PORT;
        } else {
            listenPort = port.intValue();
            if (listenPort < 1 || listenPort > 65535) {
                final LocalizableMessage msg = ERR_LDAPURL_BAD_PORT.get(listenPort);
                throw new LocalizedIllegalArgumentException(msg);
            }
            urlBuffer.append(COLON_CHAR);
            urlBuffer.append(listenPort);
        }

        this.port = listenPort;

        // We need a slash irrespective of dn is defined or not.
        urlBuffer.append(SLASH_CHAR);
        if (name != null) {
            this.name = name;
            percentEncoder(name.toString(), urlBuffer);
        } else {
            this.name = DEFAULT_DN;
        }

        // Add attributes.
        urlBuffer.append(QUESTION_CHAR);
        switch (attributes.length) {
        case 0:
            this.attributes = Collections.emptyList();
            break;
        case 1:
            this.attributes = Collections.singletonList(attributes[0]);
            urlBuffer.append(attributes[0]);
            break;
        default:
            this.attributes = Collections.unmodifiableList(Arrays.asList(attributes));
            urlBuffer.append(attributes[0]);
            for (int i = 1; i < attributes.length; i++) {
                urlBuffer.append(COMMA_CHAR);
                urlBuffer.append(attributes[i]);
            }
            break;
        }

        // Add the scope.
        urlBuffer.append(QUESTION_CHAR);
        if (scope != null) {
            this.scope = scope;
            urlBuffer.append(scope);
        } else {
            this.scope = DEFAULT_SCOPE;
        }

        // Add the search filter.
        urlBuffer.append(QUESTION_CHAR);
        if (filter != null) {
            this.filter = filter;
            urlBuffer.append(this.filter);
        } else {
            this.filter = DEFAULT_FILTER;
        }

        urlString = urlBuffer.toString();
    }

    private LDAPUrl(final String urlString, final Schema schema) {
        this.urlString = urlString;

        // Parse the url and build the LDAP URL.
        final int schemeIdx = urlString.indexOf("://");
        if (schemeIdx < 0) {
            throw new LocalizedIllegalArgumentException(ERR_LDAPURL_NO_SCHEME.get(urlString));
        }

        final String scheme = StaticUtils.toLowerCase(urlString.substring(0, schemeIdx));
        if (DEFAULT_URL_SCHEME.equalsIgnoreCase(scheme)) {
            // Default ldap scheme.
            isSecured = false;
        } else if (SSL_URL_SCHEME.equalsIgnoreCase(scheme)) {
            isSecured = true;
        } else {
            throw new LocalizedIllegalArgumentException(ERR_LDAPURL_BAD_SCHEME.get(urlString, scheme));
        }

        final int urlLength = urlString.length();
        final int hostPortIdx = urlString.indexOf(SLASH_CHAR, schemeIdx + 3);
        final StringBuilder builder = new StringBuilder();
        if (hostPortIdx < 0) {
            // We got anything here like the host and port?
            if (urlLength > schemeIdx + 3) {
                final String hostAndPort = urlString.substring(schemeIdx + 3, urlLength);
                port = parseHostPort(urlString, hostAndPort, builder);
                host = builder.toString();
                builder.setLength(0);
            } else {
                // Nothing else is specified apart from the scheme.
                // Use the default settings and return from here.
                host = DEFAULT_HOST;
                port = isSecured ? DEFAULT_SSL_PORT : DEFAULT_PORT;
            }
            name = DEFAULT_DN;
            scope = DEFAULT_SCOPE;
            filter = DEFAULT_FILTER;
            attributes = Collections.emptyList();
            return;
        }

        final String hostAndPort = urlString.substring(schemeIdx + 3, hostPortIdx);
        // assign the host and port.
        port = parseHostPort(urlString, hostAndPort, builder);
        host = builder.toString();
        builder.setLength(0);

        // Parse the dn.
        DN parsedDN = null;
        final int dnIdx = urlString.indexOf(QUESTION_CHAR, hostPortIdx + 1);

        if (dnIdx < 0) {
            // Whatever we have here is the dn.
            final String dnStr = urlString.substring(hostPortIdx + 1, urlLength);
            percentDecoder(urlString, hostPortIdx + 1, dnStr, builder);
            try {
                parsedDN = DN.valueOf(builder.toString(), schema);
            } catch (final LocalizedIllegalArgumentException e) {
                final LocalizableMessage msg =
                        ERR_LDAPURL_INVALID_DN.get(urlString, e.getMessageObject());
                throw new LocalizedIllegalArgumentException(msg);
            }
            builder.setLength(0);
            name = parsedDN;
            scope = DEFAULT_SCOPE;
            filter = DEFAULT_FILTER;
            attributes = Collections.emptyList();
            return;
        }

        final String dnStr = urlString.substring(hostPortIdx + 1, dnIdx);
        if (dnStr.length() == 0) {
            parsedDN = DEFAULT_DN;
        } else {
            percentDecoder(urlString, hostPortIdx + 1, dnStr, builder);
            try {
                parsedDN = DN.valueOf(builder.toString(), schema);
            } catch (final LocalizedIllegalArgumentException e) {
                final LocalizableMessage msg =
                        ERR_LDAPURL_INVALID_DN.get(urlString, e.getMessageObject());
                throw new LocalizedIllegalArgumentException(msg);
            }
            builder.setLength(0);
        }
        name = parsedDN;

        // Find out the attributes.
        final int attrIdx = urlString.indexOf(QUESTION_CHAR, dnIdx + 1);
        if (attrIdx < 0) {
            attributes = Collections.emptyList();
            scope = DEFAULT_SCOPE;
            filter = DEFAULT_FILTER;
            return;
        }
        attributes = parseAttributes(urlString.substring(dnIdx + 1, attrIdx));

        // Find the scope.
        final int scopeIdx = urlString.indexOf(QUESTION_CHAR, attrIdx + 1);
        if (scopeIdx < 0) {
            scope = DEFAULT_SCOPE;
            filter = DEFAULT_FILTER;
            return;
        }
        scope = parseScope(urlString.substring(attrIdx + 1, scopeIdx));

        // Last one is filter.
        final String parsedFilter = urlString.substring(scopeIdx + 1, urlLength);
        if (parsedFilter.length() > 0) {
            // Clear what we already have.
            builder.setLength(0);
            percentDecoder(urlString, scopeIdx + 1, parsedFilter, builder);
            try {
                this.filter = Filter.valueOf(builder.toString());
            } catch (final LocalizedIllegalArgumentException e) {
                final LocalizableMessage msg =
                        ERR_LDAPURL_INVALID_FILTER.get(urlString, e.getMessageObject());
                throw new LocalizedIllegalArgumentException(msg);
            }
        } else {
            this.filter = DEFAULT_FILTER;
        }
    }

    private List<String> parseAttributes(final String attrDesc) {
        final StringTokenizer token = new StringTokenizer(attrDesc, String.valueOf(COMMA_CHAR));
        final List<String> parsedAttrs = new ArrayList<>(token.countTokens());
        while (token.hasMoreElements()) {
            parsedAttrs.add(token.nextToken());
        }
        return Collections.unmodifiableList(parsedAttrs);
    }

    private SearchScope parseScope(String scopeDef) {
        final String scope = toLowerCase(scopeDef);
        for (final SearchScope sscope : SearchScope.values()) {
            if (sscope.toString().equals(scope)) {
                return sscope;
            }
        }
        return SearchScope.BASE_OBJECT;
    }

    /**
     * Creates a new search request containing the parameters of this LDAP URL.
     *
     * @return A new search request containing the parameters of this LDAP URL.
     */
    public SearchRequest asSearchRequest() {
        final SearchRequest request = Requests.newSearchRequest(name, scope, filter);
        for (final String a : attributes) {
            request.addAttribute(a);
        }
        return request;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof LDAPUrl) {
            final String s1 = toNormalizedString();
            final String s2 = ((LDAPUrl) o).toNormalizedString();
            return s1.equals(s2);
        } else {
            return false;
        }
    }

    /**
     * Returns an unmodifiable list containing the attributes to be included
     * with each entry that matches the search criteria. Attributes that are
     * sub-types of listed attributes are implicitly included. If the returned
     * list is empty then all user attributes will be included by default.
     *
     * @return An unmodifiable list containing the attributes to be included
     *         with each entry that matches the search criteria.
     */
    public List<String> getAttributes() {
        return attributes;
    }

    /**
     * Returns the search filter associated with this LDAP URL.
     *
     * @return The search filter associated with this LDAP URL.
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * Returns the name or IP address in dotted format of the LDAP server
     * referenced by this LDAP URL. For example, {@code ldap.server1.com} or
     * {@code 192.202.185.90}. Use {@code null} for the local host.
     *
     * @return A name or IP address in dotted format of the LDAP server
     *         referenced by this LDAP URL.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the distinguished name of the base entry relative to which the
     * search is to be performed.
     *
     * @return The distinguished name of the base entry relative to which the
     *         search is to be performed.
     */
    public DN getName() {
        return name;
    }

    /**
     * Returns the port number of the LDAP server referenced by this LDAP URL.
     *
     * @return The port number of the LDAP server referenced by this LDAP URL.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the search scope associated with this LDAP URL.
     *
     * @return The search scope associated with this LDAP URL.
     */
    public SearchScope getScope() {
        return scope;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final String s = toNormalizedString();
        return s.hashCode();
    }

    /**
     * Returns {@code true} if this LDAP URL should use LDAPS or {@code false}
     * if it should use LDAP.
     *
     * @return {@code true} if this LDAP URL should use LDAPS or {@code false}
     *         if it should use LDAP.
     */
    public boolean isSecure() {
        return isSecured;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return urlString;
    }

    private int parseHostPort(final String urlString, final String hostAndPort,
            final StringBuilder host) {
        Reject.ifNull(urlString);
        Reject.ifNull(hostAndPort);
        Reject.ifNull(host);
        int urlPort = isSecured ? DEFAULT_SSL_PORT : DEFAULT_PORT;
        if (hostAndPort.length() == 0) {
            host.append(DEFAULT_HOST);
            return urlPort;
        }
        final int colonIdx = hostAndPort.indexOf(':');
        if (colonIdx < 0) {
            // port is not specified.
            host.append(hostAndPort);
            return urlPort;
        }

        String s = hostAndPort.substring(0, colonIdx);
        if (s.length() == 0) {
            // Use the default host as we allow only the port to be
            // specified.
            host.append(DEFAULT_HOST);
        } else {
            host.append(s);
        }
        s = hostAndPort.substring(colonIdx + 1, hostAndPort.length());
        try {
            urlPort = Integer.parseInt(s);
        } catch (final NumberFormatException e) {
            throw new LocalizedIllegalArgumentException(ERR_LDAPURL_CANNOT_DECODE_PORT.get(urlString, s));
        }

        // Check the validity of the port.
        if (urlPort < 1 || urlPort > 65535) {
            throw new LocalizedIllegalArgumentException(ERR_LDAPURL_INVALID_PORT.get(urlString, urlPort));
        }
        return urlPort;
    }

    private String toNormalizedString() {
        if (normalizedURL == null) {
            final StringBuilder builder = new StringBuilder();
            if (this.isSecured) {
                builder.append(SSL_URL_SCHEME);
            } else {
                builder.append(DEFAULT_URL_SCHEME);
            }
            builder.append("://");
            builder.append(host);
            builder.append(COLON_CHAR);
            builder.append(port);
            builder.append(SLASH_CHAR);
            percentEncoder(name.toString(), builder);
            builder.append(QUESTION_CHAR);
            final int sz = attributes.size();
            for (int i = 0; i < sz; i++) {
                if (i > 0) {
                    builder.append(COMMA_CHAR);
                }
                builder.append(attributes.get(i));
            }
            builder.append(QUESTION_CHAR);
            builder.append(scope);
            builder.append(QUESTION_CHAR);
            percentEncoder(filter.toString(), builder);
            normalizedURL = builder.toString();
        }
        return normalizedURL;
    }
}
