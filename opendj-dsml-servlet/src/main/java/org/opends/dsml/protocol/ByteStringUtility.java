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
 * Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.dsml.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.w3c.dom.Element;

/**
 * A utility class to assist in converting DsmlValues (in Objects) into
 * the required ByteStrings, and back again.
 */
class ByteStringUtility
{
  /** Number of bytes read from a dereferenced URI per iteration. */
  private static final int READ_CHUNK = 2048;

  /** Connect timeout (ms) applied when dereferencing an anyURI value. */
  private static final int CONNECT_TIMEOUT_MS = 10000;

  /** Read timeout (ms) applied when dereferencing an anyURI value. */
  private static final int READ_TIMEOUT_MS = 30000;

  /**
   * Whether xsd:anyURI values are dereferenced (fetched) server-side.
   * <p>
   * Disabled by default: fetching an attacker-supplied URI on the server is a
   * server-side request forgery, local-file disclosure and unbounded-read DoS
   * primitive (GHSA-68r5-9hpg-7qw9). When disabled, an anyURI value is stored
   * verbatim (its string form) instead of being dereferenced.
   */
  private static volatile boolean dereferenceUri;

  /** URI schemes that may be dereferenced when {@link #dereferenceUri} is enabled. */
  private static volatile Set<String> allowedUriSchemes =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("http", "https")));

  /** Maximum number of bytes read from a dereferenced URI (10 MiB by default). */
  private static volatile long maxUriContentLength = 10L * 1024 * 1024;

  /**
   * NAT64 Network-Specific Prefixes (RFC 6052 section 2.2) configured by the
   * deployer. An NSP is chosen by an operator out of their own address space
   * and is indistinguishable from an ordinary global address, so it cannot be
   * detected here; deployments using NAT64 with an NSP must declare it so that
   * the IPv4 address it embeds goes through the same filter as the built-in
   * transition prefixes. Empty by default.
   */
  private static volatile List<Nat64Prefix> nat64Prefixes = Collections.emptyList();

  /**
   * Enables or disables server-side dereferencing of xsd:anyURI values.
   *
   * @param enabled {@code true} to fetch anyURI values (subject to the scheme
   *                allowlist, address filter and size cap), {@code false} to
   *                store them verbatim.
   */
  static void setDereferenceUri(boolean enabled)
  {
    dereferenceUri = enabled;
  }

  /**
   * Sets the schemes that may be dereferenced from a comma-separated list.
   *
   * @param schemes comma-separated scheme names (e.g. {@code "http,https"}).
   */
  static void setAllowedUriSchemes(String schemes)
  {
    Set<String> parsed = new HashSet<>();
    for (String scheme : schemes.split(","))
    {
      String trimmed = scheme.trim();
      if (!trimmed.isEmpty())
      {
        parsed.add(trimmed.toLowerCase(Locale.ROOT));
      }
    }
    allowedUriSchemes = Collections.unmodifiableSet(parsed);
  }

  /**
   * Sets the maximum number of bytes read from a dereferenced URI.
   *
   * @param maxLength the maximum size, in bytes; must be positive.
   * @throws IllegalArgumentException if the size is zero or negative.
   */
  static void setMaxUriContentLength(long maxLength)
  {
    if (maxLength <= 0)
    {
      throw new IllegalArgumentException(
          "The maximum anyURI content length must be positive, but was: " + maxLength);
    }
    maxUriContentLength = maxLength;
  }

  /**
   * Sets the NAT64 Network-Specific Prefixes from a comma-separated list of
   * {@code prefix/length} entries (e.g. {@code "2001:db8:122:344::/96"}). The
   * length must be one of the RFC 6052 section 2.2 prefix lengths: 32, 40, 48,
   * 56, 64 or 96. An empty string clears the list.
   *
   * @param prefixes comma-separated NAT64 prefixes in {@code prefix/length} form.
   * @throws IllegalArgumentException if an entry is not a valid IPv6 literal
   *         with an RFC 6052 prefix length.
   */
  static void setNat64Prefixes(String prefixes)
  {
    List<Nat64Prefix> parsed = new ArrayList<>();
    for (String entry : prefixes.split(","))
    {
      String trimmed = entry.trim();
      if (!trimmed.isEmpty())
      {
        parsed.add(Nat64Prefix.valueOf(trimmed));
      }
    }
    nat64Prefixes = Collections.unmodifiableList(parsed);
  }

  /**
   * Returns a ByteString from a DsmlValue Object.
   *
   * @param obj
   *           the DsmlValue object.
   * @return a new ByteString object with the value, or null if val was null,
   *         or if it could not be converted.
   * @throws IOException if any problems occurred retrieving an anyURI value.
   */
  public static ByteString convertValue(Object obj) throws IOException
  {
    if (obj == null)
    {
      return null;
    }
    else if (obj instanceof String)
    {
      return ByteString.valueOfUtf8((String) obj);
    }
    else if (obj instanceof byte[])
    {
      return ByteString.wrap((byte[]) obj);
    }
    else if (obj instanceof URI)
    {
      return convertUri((URI) obj);
    }
    else if (obj instanceof Element)
    {
      Element element = (Element) obj;
      return ByteString.valueOfUtf8(element.getTextContent());
    }
    return null;
  }

  /**
   * Returns a ByteString from a DsmlValue Object which the request requires to
   * carry a value.
   * <p>
   * A value the binding could not produce arrives here as {@code null} rather
   * than as a parse failure: when {@code ldap.dsml.dereference.anyuri} is
   * enabled, {@code xsd:anyURI} is bound to {@link URI}, and the JAXB runtime
   * turns a lexical form {@link URI} rejects - one holding a space, a pipe or a
   * brace, all of them valid {@code xsd:anyURI} - into a {@code null} element.
   * Passing that on would silently drop the value from an add or modify
   * request, or build a search filter with no assertion value, so it is
   * reported to the client instead (GHSA-jr68-5c58-j463).
   *
   * @param obj
   *           the DsmlValue object.
   * @return a new ByteString object with the value, never {@code null}.
   * @throws IOException if the value is missing or could not be converted, or
   *                     if any problem occurred retrieving an anyURI value.
   */
  static ByteString convertRequiredValue(Object obj) throws IOException
  {
    ByteString value = convertValue(obj);
    if (value == null)
    {
      throw new IOException("Unable to convert a DSML value into an attribute "
          + "value: the value is missing, or it is an anyURI which is not a "
          + "valid URI and was dropped by the XML binding");
    }
    return value;
  }

  /**
   * Converts an xsd:anyURI value into a ByteString.
   * <p>
   * Unless dereferencing has been explicitly enabled, the URI is treated as an
   * opaque string and is not fetched. When enabled, the scheme must be on the
   * allowlist, the target must not resolve to a loopback/link-local/private
   * address (SSRF hardening), HTTP redirects are refused (following one would
   * connect to a host that never went through the address filter) and the
   * amount of data read is capped.
   * <p>
   * Known limitation: the address filter and the subsequent connection resolve
   * the host name independently, so DNS records changing between the two
   * lookups (DNS rebinding) can still direct the fetch at an internal address.
   * The JVM's positive DNS cache ({@code networkaddress.cache.ttl}, 30 seconds
   * by default) narrows this window but does not close it; only enable
   * dereferencing for trusted clients.
   *
   * @param uri the anyURI value.
   * @return the resulting ByteString.
   * @throws IOException if the URI is rejected or an I/O error occurs.
   */
  static ByteString convertUri(URI uri) throws IOException
  {
    if (!dereferenceUri)
    {
      // Secure default: do not fetch remote or local content.
      return ByteString.valueOfUtf8(uri.toString());
    }

    String scheme = uri.getScheme();
    if (scheme == null || !allowedUriSchemes.contains(scheme.toLowerCase(Locale.ROOT)))
    {
      throw new IOException(
          "Refusing to dereference anyURI value with disallowed scheme: " + uri);
    }
    assertHostAllowed(uri);

    URLConnection connection = uri.toURL().openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    if (connection instanceof HttpURLConnection)
    {
      HttpURLConnection httpConnection = (HttpURLConnection) connection;
      httpConnection.setInstanceFollowRedirects(false);
      assertNotRedirect(httpConnection.getResponseCode(), uri);
    }
    try (InputStream is = connection.getInputStream())
    {
      return readCapped(is, uri);
    }
  }

  /**
   * Rejects HTTP 3xx responses: the redirect target has not been validated by
   * {@link #assertHostAllowed}, so following it would let an allowlisted public
   * host bounce the gateway onto an internal or metadata address.
   *
   * @param responseCode the HTTP response code.
   * @param uri the anyURI value being dereferenced.
   * @throws IOException if the response is a redirect.
   */
  static void assertNotRedirect(int responseCode, URI uri) throws IOException
  {
    if (responseCode >= 300 && responseCode < 400)
    {
      throw new IOException("Refusing to follow redirect (HTTP " + responseCode
          + ") while dereferencing anyURI value: " + uri);
    }
  }

  /**
   * Reads a stream into a ByteString, enforcing the configured size cap.
   *
   * @param is the stream to read.
   * @param uri the anyURI value being dereferenced, for diagnostics.
   * @return the stream content.
   * @throws IOException on I/O error, or if the content exceeds the cap.
   */
  static ByteString readCapped(InputStream is, URI uri) throws IOException
  {
    ByteStringBuilder bsb = new ByteStringBuilder();
    while (bsb.appendBytes(is, READ_CHUNK) != -1)
    {
      if (bsb.length() > maxUriContentLength)
      {
        throw new IOException("anyURI content exceeds the maximum allowed size of "
            + maxUriContentLength + " bytes: " + uri);
      }
    }
    return bsb.toByteString();
  }

  /**
   * Rejects a URI whose host resolves (in whole or in part) to a non-routable
   * or otherwise internal address, to prevent server-side request forgery
   * against internal services and the cloud metadata endpoint.
   *
   * @param uri the anyURI value being dereferenced.
   * @throws IOException if the host is missing, unresolvable or internal.
   */
  static void assertHostAllowed(URI uri) throws IOException
  {
    String host = uri.getHost();
    if (host == null)
    {
      throw new IOException("Refusing to dereference anyURI value with no host: " + uri);
    }
    InetAddress[] addresses;
    try
    {
      addresses = InetAddress.getAllByName(host);
    }
    catch (UnknownHostException e)
    {
      throw new IOException("Unable to resolve anyURI host: " + host, e);
    }
    for (InetAddress address : addresses)
    {
      if (isInternalAddress(address))
      {
        throw new IOException("Refusing to dereference anyURI value targeting a "
            + "non-routable/internal address: " + host + " -> " + address.getHostAddress());
      }
    }
  }

  /**
   * Returns {@code true} if the address is one the gateway must not connect to
   * on behalf of a request: loopback, wildcard, link-local (covers the
   * 169.254.169.254 metadata endpoint), site-local/private, multicast, an
   * IANA-reserved IPv4 range (including 100.64.0.0/10, home of the Alibaba
   * Cloud metadata endpoint 100.100.100.200), an IPv6 unique-local address
   * (fc00::/7), an address in the NAT64 local-use prefix 64:ff9b:1::/48
   * (RFC 8215), or an IPv6 transition address (NAT64 well-known prefix
   * 64:ff9b::/96, any NAT64 prefix declared via
   * {@link #setNat64Prefixes(String)}, 6to4 2002::/16, Teredo 2001:0000::/32,
   * IPv4-mapped/-compatible) that embeds an internal IPv4 address
   * (GHSA-97x5-8pm7-xfp7).
   */
  static boolean isInternalAddress(InetAddress address)
  {
    if (address.isLoopbackAddress() || address.isAnyLocalAddress()
        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isMulticastAddress())
    {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (bytes.length == 4)
    {
      return isInternalIPv4(bytes);
    }
    if (bytes.length == 16)
    {
      // IPv6 unique-local addresses (fc00::/7) are not covered by isSiteLocalAddress().
      if ((bytes[0] & 0xfe) == 0xfc)
      {
        return true;
      }
      // The NAT64 local-use prefix 64:ff9b:1::/48 (RFC 8215) is translation-only
      // space reserved for use within a site, and the operator chooses the
      // sub-prefix inside it, so the offset of the embedded IPv4 address is not
      // knowable here (a /64 sub-prefix puts it in bytes 9..12 rather than
      // 12..15). RFC 6052 section 3.1 forbids representing non-global IPv4
      // addresses with the well-known prefix and RFC 8215 exists to provide a
      // prefix that may be used for exactly that, so this /48 is more likely to
      // carry an internal target than the well-known prefix is. Nothing in it is
      // a legitimate fetch target, so block the whole /48 rather than only the
      // sub-prefixes whose layout can be guessed.
      if (bytes[0] == 0x00 && bytes[1] == 0x64
          && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b
          && bytes[4] == 0x00 && bytes[5] == 0x01)
      {
        return true;
      }
      // A transition address may embed an internal IPv4 address that would
      // otherwise bypass the IPv4 filter (GHSA-97x5-8pm7-xfp7).
      byte[] embedded = embeddedIPv4(bytes);
      return embedded != null && isInternalIPv4(embedded);
    }
    return false;
  }

  /**
   * Returns {@code true} if the given four-byte IPv4 address is loopback,
   * private, link-local, multicast or otherwise reserved/non-routable. Unlike
   * the {@link InetAddress} predicates, this operates on raw bytes so it can
   * also classify an IPv4 address extracted from an IPv6 transition address.
   */
  private static boolean isInternalIPv4(byte[] v4)
  {
    int b0 = v4[0] & 0xff;
    int b1 = v4[1] & 0xff;
    int b2 = v4[2] & 0xff;
    return b0 == 0                                   // 0.0.0.0/8 "this network"
        || b0 == 10                                  // 10.0.0.0/8 private
        || b0 == 127                                 // 127.0.0.0/8 loopback
        || (b0 == 100 && (b1 & 0xc0) == 0x40)        // 100.64.0.0/10 CGNAT
        || (b0 == 169 && b1 == 254)                  // 169.254.0.0/16 link-local
        || (b0 == 172 && (b1 & 0xf0) == 0x10)        // 172.16.0.0/12 private
        || (b0 == 192 && b1 == 168)                  // 192.168.0.0/16 private
        || (b0 == 192 && b1 == 0 && b2 == 0)         // 192.0.0.0/24 IETF assignments
        || (b0 == 192 && b1 == 88 && b2 == 99)       // 192.88.99.0/24 6to4 relay anycast (deprecated, RFC 7526)
        || (b0 == 198 && (b1 & 0xfe) == 18)          // 198.18.0.0/15 benchmarking
        || (b0 & 0xf0) == 0xe0                        // 224.0.0.0/4 multicast
        || b0 >= 240;                                // 240.0.0.0/4 reserved + broadcast
  }

  /**
   * Extracts the IPv4 address embedded in an IPv6 transition address, or
   * {@code null} if none is embedded. Covers 6to4 (2002::/16), Teredo
   * (2001:0000::/32, client address obfuscated), the NAT64 well-known prefix
   * (64:ff9b::/96), any NAT64 Network-Specific Prefix declared via
   * {@link #setNat64Prefixes(String)}, and IPv4-mapped/-compatible addresses
   * (::ffff:0:0/96, ::/96). The NAT64 local-use prefix 64:ff9b:1::/48 (RFC 8215)
   * is not decoded here: {@link #isInternalAddress(InetAddress)} rejects that
   * whole /48, whatever sub-prefix layout the operator picked inside it.
   */
  private static byte[] embeddedIPv4(byte[] b)
  {
    int b0 = b[0] & 0xff;
    int b1 = b[1] & 0xff;
    // 6to4: 2002:V4ADDR::/16 -- IPv4 in bytes 2..5.
    if (b0 == 0x20 && b1 == 0x02)
    {
      return new byte[] { b[2], b[3], b[4], b[5] };
    }
    // Teredo: 2001:0000::/32 -- client IPv4 in bytes 12..15, obfuscated (one's complement).
    if (b0 == 0x20 && b1 == 0x01 && (b[2] & 0xff) == 0x00 && (b[3] & 0xff) == 0x00)
    {
      return new byte[] { (byte) ~b[12], (byte) ~b[13], (byte) ~b[14], (byte) ~b[15] };
    }
    // NAT64 well-known prefix: 64:ff9b::/96 -- IPv4 in bytes 12..15. RFC 6052
    // defines the well-known prefix with a /96 length only, so this is complete;
    // the local-use prefix 64:ff9b:1::/48 needs no decoding because
    // isInternalAddress() rejects that whole /48 outright.
    if (b0 == 0x00 && b1 == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b
        && isZero(b, 4, 12))
    {
      return new byte[] { b[12], b[13], b[14], b[15] };
    }
    // IPv4-mapped (::ffff:0:0/96) and IPv4-compatible (::/96) -- IPv4 in bytes 12..15.
    if (isZero(b, 0, 10)
        && (((b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff)      // ::ffff:0:0/96 mapped
            || ((b[10] & 0xff) == 0x00 && (b[11] & 0xff) == 0x00))) // ::/96 compatible
    {
      return new byte[] { b[12], b[13], b[14], b[15] };
    }
    // NAT64 Network-Specific Prefixes declared by the deployer (RFC 6052).
    for (Nat64Prefix prefix : nat64Prefixes)
    {
      if (prefix.matches(b))
      {
        return prefix.extractIPv4(b);
      }
    }
    return null;
  }

  /**
   * A NAT64 translation prefix (RFC 6052 section 2.2): a 16-byte IPv6 prefix
   * and its length, one of 32, 40, 48, 56, 64 or 96 bits. The prefix length
   * determines where the translated IPv4 address sits in the IPv6 address
   * (bits 64..71, the "u" byte, never carry IPv4 bits).
   */
  private static final class Nat64Prefix
  {
    private final byte[] prefix;
    private final int length;

    private Nat64Prefix(byte[] prefix, int length)
    {
      this.prefix = prefix;
      this.length = length;
    }

    /**
     * Parses a {@code prefix/length} entry, e.g. {@code "2001:db8:122:344::/96"}.
     *
     * @throws IllegalArgumentException if the entry is malformed, the address
     *         part is not an IPv6 literal, or the length is not an RFC 6052
     *         prefix length.
     */
    static Nat64Prefix valueOf(String entry)
    {
      int slash = entry.indexOf('/');
      if (slash < 0)
      {
        throw new IllegalArgumentException(
            "NAT64 prefix must be in prefix/length form, but was: " + entry);
      }
      int length;
      try
      {
        length = Integer.parseInt(entry.substring(slash + 1).trim());
      }
      catch (NumberFormatException e)
      {
        throw new IllegalArgumentException(
            "NAT64 prefix length is not a number: " + entry);
      }
      if (length != 32 && length != 40 && length != 48 && length != 56 && length != 64
          && length != 96)
      {
        throw new IllegalArgumentException("NAT64 prefix length must be one of "
            + "32, 40, 48, 56, 64 or 96 (RFC 6052), but was: " + entry);
      }
      InetAddress address;
      try
      {
        // The bracketed form only accepts an IP literal, so this cannot
        // trigger a DNS lookup.
        address = InetAddress.getByName("[" + entry.substring(0, slash).trim() + "]");
      }
      catch (UnknownHostException e)
      {
        throw new IllegalArgumentException(
            "NAT64 prefix is not an IPv6 literal: " + entry);
      }
      byte[] bytes = address.getAddress();
      if (bytes.length != 16)
      {
        throw new IllegalArgumentException(
            "NAT64 prefix is not an IPv6 address: " + entry);
      }
      return new Nat64Prefix(bytes, length);
    }

    /** Returns {@code true} if the first {@code length} bits of {@code b} match this prefix. */
    boolean matches(byte[] b)
    {
      for (int i = 0; i < length / 8; i++)
      {
        if (b[i] != prefix[i])
        {
          return false;
        }
      }
      return true;
    }

    /**
     * Extracts the embedded IPv4 address as laid out by RFC 6052 section 2.2
     * for this prefix length: the 32 IPv4 bits follow the prefix directly,
     * except that byte 8 (the "u" byte) is skipped.
     */
    byte[] extractIPv4(byte[] b)
    {
      switch (length)
      {
        case 32: return new byte[] { b[4], b[5], b[6], b[7] };
        case 40: return new byte[] { b[5], b[6], b[7], b[9] };
        case 48: return new byte[] { b[6], b[7], b[9], b[10] };
        case 56: return new byte[] { b[7], b[9], b[10], b[11] };
        case 64: return new byte[] { b[9], b[10], b[11], b[12] };
        default: return new byte[] { b[12], b[13], b[14], b[15] };  // 96
      }
    }
  }

  /** Returns {@code true} if bytes {@code [from, to)} of {@code b} are all zero. */
  private static boolean isZero(byte[] b, int from, int to)
  {
    for (int i = from; i < to; i++)
    {
      if (b[i] != 0)
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a DsmlValue (Object) from an LDAP ByteString. The conversion is
   * simplistic - try and convert it to UTF-8 and if that fails return a byte[].
   *
   * @param bs the ByteString returned from LDAP.
   * @return a String or a byte[].
   */
  public static Object convertByteString(ByteString bs)
  {
    try
    {
      return new String(bs.toCharArray());
    }
    catch (Exception e)
    {
      return bs.toByteArray();
    }
  }
}
