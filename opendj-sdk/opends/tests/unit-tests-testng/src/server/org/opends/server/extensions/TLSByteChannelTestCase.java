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
 *      Copyright 2012-2013 ForgeRock AS
 */
package org.opends.server.extensions;

import static org.testng.Assert.*;

import java.io.BufferedInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Tests for {@link TLSByteChannel} class.
 */
@Test(groups = { "slow" }, sequential = true)
public class TLSByteChannelTestCase extends DirectoryServerTestCase
{

  /**
   * Cipher suite hardcoded from the IANA registry on internet
   */
  static final String[][] HARDCODED_CIPHER_SUITE = new String[][] {
        { "TLS_NULL_WITH_NULL_NULL" },
        { "TLS_RSA_WITH_NULL_MD5" },
        { "TLS_RSA_WITH_NULL_SHA" },
        { "TLS_RSA_EXPORT_WITH_RC4_40_MD5" },
        { "TLS_RSA_WITH_RC4_128_MD5" },
        { "TLS_RSA_WITH_RC4_128_SHA" },
        { "TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5" },
        { "TLS_RSA_WITH_IDEA_CBC_SHA" },
        { "TLS_RSA_EXPORT_WITH_DES40_CBC_SHA" },
        { "TLS_RSA_WITH_DES_CBC_SHA" },
        { "TLS_RSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA" },
        { "TLS_DH_DSS_WITH_DES_CBC_SHA" },
        { "TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA" },
        { "TLS_DH_RSA_WITH_DES_CBC_SHA" },
        { "TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA" },
        { "TLS_DHE_DSS_WITH_DES_CBC_SHA" },
        { "TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_DES_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_DH_anon_EXPORT_WITH_RC4_40_MD5" },
        { "TLS_DH_anon_WITH_RC4_128_MD5" },
        { "TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA" },
        { "TLS_DH_anon_WITH_DES_CBC_SHA" },
        { "TLS_DH_anon_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_KRB5_WITH_DES_CBC_SHA" },
        { "TLS_KRB5_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_KRB5_WITH_RC4_128_SHA" },
        { "TLS_KRB5_WITH_IDEA_CBC_SHA" },
        { "TLS_KRB5_WITH_DES_CBC_MD5" },
        { "TLS_KRB5_WITH_3DES_EDE_CBC_MD5" },
        { "TLS_KRB5_WITH_RC4_128_MD5" },
        { "TLS_KRB5_WITH_IDEA_CBC_MD5" },
        { "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA" },
        { "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA" },
        { "TLS_KRB5_EXPORT_WITH_RC4_40_SHA" },
        { "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5" },
        { "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5" },
        { "TLS_KRB5_EXPORT_WITH_RC4_40_MD5" },
        { "TLS_PSK_WITH_NULL_SHA" },
        { "TLS_DHE_PSK_WITH_NULL_SHA" },
        { "TLS_RSA_PSK_WITH_NULL_SHA" },
        { "TLS_RSA_WITH_AES_128_CBC_SHA" },
        { "TLS_DH_DSS_WITH_AES_128_CBC_SHA" },
        { "TLS_DH_RSA_WITH_AES_128_CBC_SHA" },
        { "TLS_DHE_DSS_WITH_AES_128_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" },
        { "TLS_DH_anon_WITH_AES_128_CBC_SHA" },
        { "TLS_RSA_WITH_AES_256_CBC_SHA" },
        { "TLS_DH_DSS_WITH_AES_256_CBC_SHA" },
        { "TLS_DH_RSA_WITH_AES_256_CBC_SHA" },
        { "TLS_DHE_DSS_WITH_AES_256_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_AES_256_CBC_SHA" },
        { "TLS_DH_anon_WITH_AES_256_CBC_SHA" },
        { "TLS_RSA_WITH_NULL_SHA256" },
        { "TLS_RSA_WITH_AES_128_CBC_SHA256" },
        { "TLS_RSA_WITH_AES_256_CBC_SHA256" },
        { "TLS_DH_DSS_WITH_AES_128_CBC_SHA256" },
        { "TLS_DH_RSA_WITH_AES_128_CBC_SHA256" },
        { "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256" },
        { "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA" },
        { "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA" },
        { "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA" },
        { "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA" },
        { "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256" },
        { "TLS_DH_DSS_WITH_AES_256_CBC_SHA256" },
        { "TLS_DH_RSA_WITH_AES_256_CBC_SHA256" },
        { "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256" },
        { "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256" },
        { "TLS_DH_anon_WITH_AES_128_CBC_SHA256" },
        { "TLS_DH_anon_WITH_AES_256_CBC_SHA256" },
        { "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA" },
        { "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA" },
        { "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA" },
        { "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA" },
        { "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA" },
        { "TLS_PSK_WITH_RC4_128_SHA" },
        { "TLS_PSK_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_PSK_WITH_AES_128_CBC_SHA" },
        { "TLS_PSK_WITH_AES_256_CBC_SHA" },
        { "TLS_DHE_PSK_WITH_RC4_128_SHA" },
        { "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_DHE_PSK_WITH_AES_128_CBC_SHA" },
        { "TLS_DHE_PSK_WITH_AES_256_CBC_SHA" },
        { "TLS_RSA_PSK_WITH_RC4_128_SHA" },
        { "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_RSA_PSK_WITH_AES_128_CBC_SHA" },
        { "TLS_RSA_PSK_WITH_AES_256_CBC_SHA" },
        { "TLS_RSA_WITH_SEED_CBC_SHA" },
        { "TLS_DH_DSS_WITH_SEED_CBC_SHA" },
        { "TLS_DH_RSA_WITH_SEED_CBC_SHA" },
        { "TLS_DHE_DSS_WITH_SEED_CBC_SHA" },
        { "TLS_DHE_RSA_WITH_SEED_CBC_SHA" },
        { "TLS_DH_anon_WITH_SEED_CBC_SHA" },
        { "TLS_RSA_WITH_AES_128_GCM_SHA256" },
        { "TLS_RSA_WITH_AES_256_GCM_SHA384" },
        { "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256" },
        { "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384" },
        { "TLS_DH_RSA_WITH_AES_128_GCM_SHA256" },
        { "TLS_DH_RSA_WITH_AES_256_GCM_SHA384" },
        { "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256" },
        { "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384" },
        { "TLS_DH_DSS_WITH_AES_128_GCM_SHA256" },
        { "TLS_DH_DSS_WITH_AES_256_GCM_SHA384" },
        { "TLS_DH_anon_WITH_AES_128_GCM_SHA256" },
        { "TLS_DH_anon_WITH_AES_256_GCM_SHA384" },
        { "TLS_PSK_WITH_AES_128_GCM_SHA256" },
        { "TLS_PSK_WITH_AES_256_GCM_SHA384" },
        { "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256" },
        { "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384" },
        { "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256" },
        { "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384" },
        { "TLS_PSK_WITH_AES_128_CBC_SHA256" },
        { "TLS_PSK_WITH_AES_256_CBC_SHA384" },
        { "TLS_PSK_WITH_NULL_SHA256" },
        { "TLS_PSK_WITH_NULL_SHA384" },
        { "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256" },
        { "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384" },
        { "TLS_DHE_PSK_WITH_NULL_SHA256" },
        { "TLS_DHE_PSK_WITH_NULL_SHA384" },
        { "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256" },
        { "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384" },
        { "TLS_RSA_PSK_WITH_NULL_SHA256" },
        { "TLS_RSA_PSK_WITH_NULL_SHA384" },
        { "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256" },
        { "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256" },
        { "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256" },
        { "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256" },
        { "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256" },
        { "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256" },
        { "TLS_ECDH_ECDSA_WITH_NULL_SHA" },
        { "TLS_ECDH_ECDSA_WITH_RC4_128_SHA" },
        { "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA" },
        { "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA" },
        { "TLS_ECDHE_ECDSA_WITH_NULL_SHA" },
        { "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA" },
        { "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA" },
        { "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA" },
        { "TLS_ECDH_RSA_WITH_NULL_SHA" },
        { "TLS_ECDH_RSA_WITH_RC4_128_SHA" },
        { "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA" },
        { "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA" },
        { "TLS_ECDHE_RSA_WITH_NULL_SHA" },
        { "TLS_ECDHE_RSA_WITH_RC4_128_SHA" },
        { "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA" },
        { "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA" },
        { "TLS_ECDH_anon_WITH_NULL_SHA" },
        { "TLS_ECDH_anon_WITH_RC4_128_SHA" },
        { "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_ECDH_anon_WITH_AES_128_CBC_SHA" },
        { "TLS_ECDH_anon_WITH_AES_256_CBC_SHA" },
        { "TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_SRP_SHA_WITH_AES_128_CBC_SHA" },
        { "TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA" },
        { "TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA" },
        { "TLS_SRP_SHA_WITH_AES_256_CBC_SHA" },
        { "TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA" },
        { "TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA" },
        { "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256" },
        { "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384" },
        { "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256" },
        { "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384" },
        { "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256" },
        { "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384" },
        { "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256" },
        { "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384" },
        { "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256" },
        { "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" },
        { "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256" },
        { "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384" },
        { "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" },
        { "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384" },
        { "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256" },
        { "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384" },
        { "TLS_ECDHE_PSK_WITH_RC4_128_SHA" },
        { "TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA" },
        { "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA" },
        { "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA" },
        { "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256" },
        { "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384" },
        { "TLS_ECDHE_PSK_WITH_NULL_SHA" },
        { "TLS_ECDHE_PSK_WITH_NULL_SHA256" },
        { "TLS_ECDHE_PSK_WITH_NULL_SHA384" },
        { "TLS_RSA_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_RSA_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_DH_anon_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_DH_anon_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_ECDHE_ECDSA_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_ECDHE_ECDSA_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_ECDHE_RSA_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_ECDHE_RSA_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_RSA_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_RSA_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_DHE_RSA_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_DHE_RSA_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_DHE_DSS_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_DHE_DSS_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_DH_anon_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_DH_anon_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_ECDHE_ECDSA_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_ECDHE_ECDSA_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_ECDHE_RSA_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_ECDHE_RSA_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_PSK_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_PSK_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_PSK_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_PSK_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_DHE_PSK_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_DHE_PSK_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256" },
        { "TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384" },
        { "TLS_ECDHE_PSK_WITH_ARIA_128_CBC_SHA256" },
        { "TLS_ECDHE_PSK_WITH_ARIA_256_CBC_SHA384" },
        { "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_DHE_RSA_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_DHE_RSA_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_DHE_DSS_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_DHE_DSS_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_ECDHE_RSA_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_ECDHE_RSA_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_PSK_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_PSK_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_DHE_PSK_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_DHE_PSK_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256" },
        { "TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384" },
        { "TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256" },
        { "TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384" },
        { "TLS_RSA_WITH_AES_128_CCM" },
        { "TLS_RSA_WITH_AES_256_CCM" },
        { "TLS_DHE_RSA_WITH_AES_128_CCM" },
        { "TLS_DHE_RSA_WITH_AES_256_CCM" },
        { "TLS_RSA_WITH_AES_128_CCM_8" },
        { "TLS_RSA_WITH_AES_256_CCM_8" },
        { "TLS_DHE_RSA_WITH_AES_128_CCM_8" },
        { "TLS_DHE_RSA_WITH_AES_256_CCM_8" },
        { "TLS_PSK_WITH_AES_128_CCM" },
        { "TLS_PSK_WITH_AES_256_CCM" },
        { "TLS_DHE_PSK_WITH_AES_128_CCM" },
        { "TLS_DHE_PSK_WITH_AES_256_CCM" },
        { "TLS_PSK_WITH_AES_128_CCM_8" },
        { "TLS_PSK_WITH_AES_256_CCM_8" },
        { "TLS_PSK_DHE_WITH_AES_128_CCM_8" },
        { "TLS_PSK_DHE_WITH_AES_256_CCM_8" },
        };

  /**
   * Retrieves the IANA TLS Cipher Suites Registry from internet.
   *
   * @return the IANA TLS Cipher Suites Registry
   */
  private String[][] retrieveIanaTlsCipherSuitesRegistryFromInternet()
      throws Exception
  {
    String url =
        "http://www.iana.org/assignments/tls-parameters/tls-parameters.xml";
    URLConnection conn = new URL(url).openConnection();
    BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());

    try
    {
      // JAXP boilerplate
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = dbFactory.newDocumentBuilder();
      Document doc = builder.parse(bis);
      XPathFactory xpathFactory = XPathFactory.newInstance();
      XPath xpath = xpathFactory.newXPath();

      // Collect cipher suite
      String xPathExpr =
          "//registry[@id='tls-parameters-4']/record/description/text()";
      List<String> realCiphers = retrieveRealCiphers(doc, xpath, xPathExpr);
      return toDataProviderResult(realCiphers);
    }
    finally
    {
      StaticUtils.close(bis);
    }
  }

  private String[][] toDataProviderResult(List<String> realCiphers)
  {
    String[][] results = new String[realCiphers.size()][1];
    for (ListIterator<String> iter = realCiphers.listIterator(); iter.hasNext();)
    {
      final String cipherString = iter.next();
      int i = iter.nextIndex();
      results[i][0] = cipherString;
    }
    return results;
  }

  private List<String> retrieveRealCiphers(Document doc, XPath xpath,
      String xPathExpr) throws XPathExpressionException
  {
    NodeList nodes =
        (NodeList) xpath.evaluate(xPathExpr, doc, XPathConstants.NODESET);
    List<String> cipherStrings = new ArrayList<String>(nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++)
    {
      String cipherString = nodes.item(i).getNodeValue();
      // ignore things like "unknown", "reserved", etc.
      // also ignore the TLS Secure Renegotiation protocol message
      if (cipherString.startsWith("TLS_")
          && !"TLS_EMPTY_RENEGOTIATION_INFO_SCSV".equals(cipherString))
      {
        cipherStrings.add(cipherString);
      }
    }
    return cipherStrings;
  }

  @DataProvider(name = "ianaTlsCipherSuitesRegistry")
  public String[][] getIanaTlsCipherSuitesRegistry() throws Exception
  {
    try
    {
      return retrieveIanaTlsCipherSuitesRegistryFromInternet();
    }
    catch (Exception e)
    {
      // we could not get access to the internet registry,
      // return the hardcoded registry
      return HARDCODED_CIPHER_SUITE;
    }
  }

  /**
   * Checks that all the IANA registered ciphers are supported by
   * {@link TLSByteChannel#getSSF()}.
   *
   * @param cipherString
   *          cipher to be tested
   */
  @Test(dataProvider = "ianaTlsCipherSuitesRegistry")
  public void getSsfForIanaRegistryCipherSuites(String cipherString)
  {
    assertNotNull(TLSByteChannel.getSSF(cipherString));
  }

  /**
   * Checks that the IANA cipher suite registry on internet and the hardcoded
   * cipher suite in this class have not diverged.
   */
  @Test
  public void compareInternetAndHardcodedRegistry() throws Exception
  {
    Set<String> internetCiphers = toSet(getIanaTlsCipherSuitesRegistry());
    Set<String> hardcodedCiphers = toSet(HARDCODED_CIPHER_SUITE);
    assertEquals(internetCiphers, hardcodedCiphers);
  }

  private Set<String> toSet(String[][] data)
  {
    Set<String> set = new HashSet<String>();
    for (String[] array : data)
    {
      for (String s : array)
      {
        set.add(s);
      }
    }
    return set;
  }

  /**
   * Ensures that there is no overlapping in the DES ciphers strings and that
   * the expected Security Strength Factor are returned.
   */
  @Test
  public void checkDesSSFAreAsExpected()
  {
    assertEquals((Integer) 56, TLSByteChannel
        .getSSF("TLS_KRB5_WITH_DES_CBC_SHA"));
    assertEquals((Integer) 40, TLSByteChannel
        .getSSF("TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA"));
  }

  /**
   * Ensures that no new overlapping cipher strings are added to the cipher map.
   */
  @Test
  public void checkNoUnknownOverlappingCiphers()
  {
    List<String> ciphers =
        new ArrayList<String>(TLSByteChannel.CIPHER_MAP.keySet());
    for (int i = 0; i < ciphers.size(); i++)
    {
      for (int j = 0; j < i; j++)
      {
        String s1 = ciphers.get(i);
        String s2 = ciphers.get(j);
        if (s1.contains(s2) || s2.contains(s1))
        {
          if (not(s1, s2, "_WITH_DES_", "_WITH_DES_CBC_40_"))
          {
            fail("Overlapping cipher strings" + s1 + "\t" + s2);
          }
        }
      }
    }
  }

  /**
   * Ensure the set (cipher1, cipher2) is different from the set (match1,
   * match2).
   */
  private boolean not(String cipher1, String cipher2, String match1,
      String match2)
  {
    return (!cipher1.equals(match1) || !cipher2.equals(match2))
        && (!cipher2.equals(match1) || !cipher1.equals(match2));
  }

}
