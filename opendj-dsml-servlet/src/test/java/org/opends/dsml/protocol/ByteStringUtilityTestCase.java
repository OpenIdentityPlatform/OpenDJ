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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.dsml.protocol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the anyURI dereferencing hardening added for GHSA-68r5-9hpg-7qw9:
 * secure-by-default (no fetch), scheme allowlist, internal-address filter,
 * redirect refusal and content size cap. None of these tests performs network
 * or DNS I/O: hosts are IP literals and streams are in-memory.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "dsml" })
public class ByteStringUtilityTestCase extends ForgeRockTestCase
{
  @AfterMethod
  public void restoreDefaults()
  {
    ByteStringUtility.setDereferenceUri(false);
    ByteStringUtility.setAllowedUriSchemes("http,https");
    ByteStringUtility.setMaxUriContentLength(10L * 1024 * 1024);
    ByteStringUtility.setNat64Prefixes("");
  }

  @Test
  public void testUriIsStoredVerbatimByDefault() throws Exception
  {
    ByteString value = ByteStringUtility.convertValue(new URI("http://203.0.113.10/secret"));
    assertEquals(value, ByteString.valueOfUtf8("http://203.0.113.10/secret"));
  }

  @DataProvider
  public Object[][] disallowedSchemeUris()
  {
    return new Object[][] {
      { "file:///etc/passwd" },
      { "ftp://203.0.113.10/file" },
      { "gopher://203.0.113.10/1" },
      { "jar:http://203.0.113.10/a.jar!/b" },
      { "urn:isbn:0451450523" },        // opaque URI, no scheme match
      { "/relative/path" },             // no scheme at all
    };
  }

  @Test(dataProvider = "disallowedSchemeUris", expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*disallowed scheme.*")
  public void testDisallowedSchemesAreRejected(String uri) throws Exception
  {
    ByteStringUtility.setDereferenceUri(true);
    ByteStringUtility.convertUri(new URI(uri));
  }

  @Test(expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*disallowed scheme.*")
  public void testSchemeAllowlistIsConfigurable() throws Exception
  {
    ByteStringUtility.setDereferenceUri(true);
    ByteStringUtility.setAllowedUriSchemes(" HTTPS , ");
    // http is no longer on the allowlist, so it is rejected before any I/O.
    ByteStringUtility.convertUri(new URI("http://203.0.113.10/"));
  }

  @DataProvider
  public Object[][] internalUris()
  {
    return new Object[][] {
      { "http://127.0.0.1/" },
      { "http://0.0.0.0/" },
      { "http://10.1.2.3/" },
      { "http://172.16.5.5/" },
      { "http://192.168.1.1/" },
      { "http://169.254.169.254/latest/meta-data/" },
      { "http://100.100.100.200/latest/meta-data/" },
      { "http://198.18.0.1/" },
      { "http://240.0.0.1/" },
      { "http://[::1]/" },
      { "http://[fe80::1]/" },
      { "http://[fc00::1]/" },
      { "http://[fd12:3456::1]/" },
      // IPv6 transition addresses embedding an internal IPv4 (GHSA-97x5-8pm7-xfp7).
      { "http://[64:ff9b::c0a8:0101]/" },    // NAT64 well-known -> 192.168.1.1
      { "http://[64:ff9b::0a00:0001]/" },    // NAT64 well-known -> 10.0.0.1
      { "http://[64:ff9b:1::7f00:1]/" },     // NAT64 local-use (RFC 8215) -> 127.0.0.1
      { "http://[64:ff9b:1:0:c0:a801:100:0]/" }, // NAT64 local-use, /64 layout -> 192.168.1.1
      { "http://[2002:0a00:0001::]/" },      // 6to4 -> 10.0.0.1
      { "http://[2001:0000::3f57:fefe]/" },  // Teredo -> 192.168.1.1 (obfuscated)
    };
  }

  @Test(dataProvider = "internalUris", expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*(internal|no host).*")
  public void testInternalAddressesAreRejected(String uri) throws Exception
  {
    ByteStringUtility.assertHostAllowed(new URI(uri));
  }

  @Test(expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*no host.*")
  public void testUriWithoutHostIsRejected() throws Exception
  {
    ByteStringUtility.assertHostAllowed(new URI("http:///path"));
  }

  @DataProvider
  public Object[][] addressClassification()
  {
    return new Object[][] {
      // address, expected isInternalAddress
      { "8.8.8.8", false },
      { "1.1.1.1", false },
      { "203.0.113.10", false },
      { "2001:4860:4860::8888", false },
      { "127.0.0.1", true },
      { "0.1.2.3", true },
      { "169.254.169.254", true },
      { "239.255.255.255", true },      // multicast
      { "255.255.255.255", true },      // broadcast
      // 100.64.0.0/10 boundaries
      { "100.63.255.255", false },
      { "100.64.0.0", true },
      { "100.100.100.200", true },
      { "100.127.255.255", true },
      { "100.128.0.0", false },
      // 192.0.0.0/24 boundaries
      { "192.0.0.1", true },
      { "192.0.1.1", false },
      // 192.88.99.0/24 (deprecated 6to4 relay anycast, RFC 7526) boundaries
      { "192.88.98.255", false },
      { "192.88.99.1", true },
      { "192.88.100.0", false },
      // 198.18.0.0/15 boundaries
      { "198.17.255.255", false },
      { "198.18.0.0", true },
      { "198.19.255.255", true },
      { "198.20.0.0", false },
      // 240.0.0.0/4 boundary
      { "239.0.0.1", true },            // multicast anyway
      { "240.0.0.0", true },
      // IPv6 unique-local fc00::/7 boundaries
      { "fbff::1", false },
      { "fc00::1", true },
      { "fdff::1", true },
      { "fe00::1", false },
      // IPv6 transition addresses embedding an internal IPv4 (GHSA-97x5-8pm7-xfp7).
      { "64:ff9b::c0a8:0101", true },   // NAT64 well-known -> 192.168.1.1
      { "64:ff9b::0a00:0001", true },   // NAT64 well-known -> 10.0.0.1
      { "64:ff9b::7f00:0001", true },   // NAT64 well-known -> 127.0.0.1
      { "64:ff9b::0808:0808", false },  // NAT64 well-known -> 8.8.8.8 (public)
      // The NAT64 local-use prefix 64:ff9b:1::/48 (RFC 8215) is blocked outright,
      // whichever RFC 6052 sub-prefix layout the operator picked inside it.
      { "64:ff9b:1::7f00:1", true },    // /96 layout -> 127.0.0.1
      { "64:ff9b:1::a00:1", true },     // /96 layout -> 10.0.0.1
      { "64:ff9b:1:abcd::c0a8:101", true },  // /96 layout in a subnet of the /48 -> 192.168.1.1
      { "64:ff9b:1:0:c0:a801:100:0", true }, // /64 layout -> 192.168.1.1
      { "64:ff9b:1:0:a:102:300:0", true },   // /64 layout -> 10.1.2.3
      { "64:ff9b:1:7f00:0:100::", true },    // /48 layout -> 127.0.0.1
      { "64:ff9b:1::808:808", true },   // 8.8.8.8: public, but not reachable through this prefix
      { "64:ff9b:1:ffff:ffff:ffff:ffff:ffff", true }, // no layout matches, still in the /48
      // 64:ff9b:1::/48 boundaries.
      { "64:ff9b:0:1::7f00:1", false }, // 64:ff9b:0:1, not the local-use prefix
      { "64:ff9b:2::7f00:1", false },   // outside 64:ff9b::/96 and 64:ff9b:1::/48
      { "64:ff9c:1::7f00:1", false },   // 64:ff9c:1, not the local-use prefix
      { "2002:0a00:0001::", true },     // 6to4 -> 10.0.0.1
      { "2002:c0a8:0101::", true },     // 6to4 -> 192.168.1.1
      { "2002:c058:6301::", true },     // 6to4 -> 192.88.99.1 (blocked IPv4 via transition)
      { "2002:0808:0808::", false },    // 6to4 -> 8.8.8.8 (public)
      { "2001:0000::3f57:fefe", true }, // Teredo -> 192.168.1.1 (obfuscated)
      { "2001:0000::f7f7:f7f7", false },// Teredo -> 8.8.8.8 (public, obfuscated)
      // A regular global-unicast address under 2001::/16 must not be misread as Teredo/6to4.
      { "2001:4860:4860::8844", false },
    };
  }

  @Test(dataProvider = "addressClassification")
  public void testIsInternalAddress(String literal, boolean expectedInternal) throws Exception
  {
    // IP literals do not trigger DNS resolution.
    InetAddress address = InetAddress.getByName(literal);
    assertEquals(ByteStringUtility.isInternalAddress(address), expectedInternal,
        "isInternalAddress(" + literal + ")");
  }

  @Test
  public void testNat64NetworkSpecificPrefixIsNotBlockedByDefault() throws Exception
  {
    // An NSP is ordinary operator address space: without configuration it
    // cannot be told apart from a global address, so it must stay allowed.
    assertFalse(isInternal("2001:db8:122:344::c0a8:101"));
  }

  @Test
  public void testConfiguredNat64PrefixFiltersEmbeddedIPv4() throws Exception
  {
    ByteStringUtility.setNat64Prefixes("2001:db8:122:344::/96");
    assertTrue(isInternal("2001:db8:122:344::c0a8:101"));   // -> 192.168.1.1
    assertTrue(isInternal("2001:db8:122:344::7f00:1"));     // -> 127.0.0.1
    assertFalse(isInternal("2001:db8:122:344::808:808"));   // -> 8.8.8.8 (public)
    assertFalse(isInternal("2001:db8:122:345::c0a8:101"));  // outside the prefix
  }

  @Test
  public void testConfiguredNat64PrefixWith64BitLength() throws Exception
  {
    // RFC 6052 /64 format: the IPv4 address sits in bytes 9..12, after the "u" byte.
    ByteStringUtility.setNat64Prefixes("2001:db8:122:344::/64");
    assertTrue(isInternal("2001:db8:122:344:a:0:100:0"));   // -> 10.0.0.1
    assertFalse(isInternal("2001:db8:122:344:8:808:800:0"));// -> 8.8.8.8 (public)
  }

  @Test
  public void testMultipleNat64PrefixesAreSupported() throws Exception
  {
    ByteStringUtility.setNat64Prefixes(" 2001:db8:122:344::/96 , 2001:db8:9::/96 ");
    assertTrue(isInternal("2001:db8:122:344::a00:1"));      // -> 10.0.0.1
    assertTrue(isInternal("2001:db8:9::a9fe:a9fe"));        // -> 169.254.169.254
    assertFalse(isInternal("2001:db8:9::101:101"));         // -> 1.1.1.1 (public)
  }

  @DataProvider
  public Object[][] invalidNat64Prefixes()
  {
    return new Object[][] {
      { "2001:db8::" },            // no /length
      { "2001:db8::/95" },         // not an RFC 6052 prefix length
      { "2001:db8::/abc" },        // length is not a number
      { "not-an-address/96" },     // not an IP literal (must not trigger DNS)
      { "192.0.2.0/96" },          // IPv4, not IPv6
    };
  }

  @Test(dataProvider = "invalidNat64Prefixes",
      expectedExceptions = IllegalArgumentException.class)
  public void testInvalidNat64PrefixesAreRejected(String prefixes)
  {
    ByteStringUtility.setNat64Prefixes(prefixes);
  }

  private boolean isInternal(String literal) throws Exception
  {
    return ByteStringUtility.isInternalAddress(InetAddress.getByName(literal));
  }

  @DataProvider
  public Object[][] redirectCodes()
  {
    return new Object[][] { { 300 }, { 301 }, { 302 }, { 303 }, { 307 }, { 308 } };
  }

  @Test(dataProvider = "redirectCodes", expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*redirect.*")
  public void testRedirectsAreRefused(int responseCode) throws Exception
  {
    ByteStringUtility.assertNotRedirect(responseCode, new URI("http://203.0.113.10/"));
  }

  @Test
  public void testNonRedirectCodesAreAccepted() throws Exception
  {
    ByteStringUtility.assertNotRedirect(200, new URI("http://203.0.113.10/"));
    ByteStringUtility.assertNotRedirect(404, new URI("http://203.0.113.10/"));
  }

  @Test
  public void testContentWithinCapIsReadFully() throws Exception
  {
    byte[] content = new byte[8192];
    for (int i = 0; i < content.length; i++)
    {
      content[i] = (byte) i;
    }
    ByteStringUtility.setMaxUriContentLength(content.length);
    ByteString value = ByteStringUtility.readCapped(
        new ByteArrayInputStream(content), new URI("http://203.0.113.10/"));
    assertEquals(value, ByteString.wrap(content));
  }

  @Test(expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*maximum allowed size.*")
  public void testContentOverCapIsRejected() throws Exception
  {
    ByteStringUtility.setMaxUriContentLength(1024);
    ByteStringUtility.readCapped(
        new ByteArrayInputStream(new byte[1025]), new URI("http://203.0.113.10/"));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testZeroMaxSizeIsRejected()
  {
    ByteStringUtility.setMaxUriContentLength(0);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNegativeMaxSizeIsRejected()
  {
    ByteStringUtility.setMaxUriContentLength(-1);
  }

  @DataProvider
  public Object[][] convertibleValues()
  {
    return new Object[][] {
      { "a string value", ByteString.valueOfUtf8("a string value") },
      { new byte[] { 1, 2, 3 }, ByteString.wrap(new byte[] { 1, 2, 3 }) },
      { URI.create("http://203.0.113.10/secret"),
        ByteString.valueOfUtf8("http://203.0.113.10/secret") },
    };
  }

  @Test(dataProvider = "convertibleValues")
  public void testRequiredValueIsConverted(Object value, ByteString expected) throws Exception
  {
    assertEquals(ByteStringUtility.convertRequiredValue(value), expected);
  }

  /**
   * The XML binding turns an anyURI whose lexical form java.net.URI rejects
   * into a null value. Storing that would silently drop the value from the
   * request, so it is reported to the client instead.
   */
  @Test(expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*value is missing.*")
  public void testMissingRequiredValueIsRejected() throws Exception
  {
    ByteStringUtility.convertRequiredValue(null);
  }

  @Test(expectedExceptions = IOException.class,
      expectedExceptionsMessageRegExp = ".*value is missing.*")
  public void testUnconvertibleRequiredValueIsRejected() throws Exception
  {
    ByteStringUtility.convertRequiredValue(Integer.valueOf(42));
  }

  @Test
  public void testSchemeCheckPrecedesHostCheck() throws Exception
  {
    ByteStringUtility.setDereferenceUri(true);
    // A disallowed scheme must be rejected even for an internal host,
    // and the internal host must be rejected for an allowed scheme.
    try
    {
      ByteStringUtility.convertUri(new URI("file://127.0.0.1/etc/passwd"));
      assertFalse(true, "expected IOException for disallowed scheme");
    }
    catch (IOException e)
    {
      assertTrue(e.getMessage().contains("disallowed scheme"), e.getMessage());
    }
    try
    {
      ByteStringUtility.convertUri(new URI("http://127.0.0.1/"));
      assertFalse(true, "expected IOException for internal address");
    }
    catch (IOException e)
    {
      assertTrue(e.getMessage().contains("internal"), e.getMessage());
    }
  }
}
