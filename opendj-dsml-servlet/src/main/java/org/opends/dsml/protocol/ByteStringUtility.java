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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
   * Cloud metadata endpoint 100.100.100.200), or an IPv6 unique-local address
   * (fc00::/7).
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
      int b0 = bytes[0] & 0xff;
      int b1 = bytes[1] & 0xff;
      return b0 == 0                                        // 0.0.0.0/8 "this network"
          || (b0 == 100 && (b1 & 0xc0) == 0x40)             // 100.64.0.0/10 CGNAT
          || (b0 == 192 && b1 == 0 && (bytes[2] & 0xff) == 0) // 192.0.0.0/24 IETF assignments
          || (b0 == 198 && (b1 & 0xfe) == 18)               // 198.18.0.0/15 benchmarking
          || b0 >= 240;                                     // 240.0.0.0/4 reserved + broadcast
    }
    // IPv6 unique-local addresses (fc00::/7) are not covered by isSiteLocalAddress().
    return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
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
