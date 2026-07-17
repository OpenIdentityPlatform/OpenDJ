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
