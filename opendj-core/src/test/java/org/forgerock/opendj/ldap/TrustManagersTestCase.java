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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;

import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.logging.Level;

/**
 * This class defines a set of tests for the
 * {@link org.forgerock.opendj.ldap.TrustManagers} class.
 */
@SuppressWarnings("javadoc")
public class TrustManagersTestCase extends SdkTestCase  {
    /**
     * X509TrustManager test data provider with a variety of hostnames in subjects and subject alt names.
     *
     * @return The array of test data.
     */
    @DataProvider(name = "hostnameTestData")
    public Object[][] createHostnameTestData() throws Exception {
        // see comments in test cases for descriptions
        X509Certificate cert1 = getTestCertificate("cert1.pem");
        X509Certificate cert2 = getTestCertificate("cert2.pem");
        X509Certificate cert3 = getTestCertificate("cert3.pem");
        X509Certificate cert4 = getTestCertificate("cert4.pem");
        X509Certificate cert5 = getTestCertificate("cert5.pem");
        X509Certificate cert6 = getTestCertificate("cert6.pem");
        X509Certificate cert7 = getTestCertificate("cert7.pem");
        X509Certificate cert8 = getTestCertificate("cert8.pem");
        X509Certificate cert9 = getTestCertificate("cert9.pem");
        X509Certificate cert10 = getTestCertificate("cert10.pem");
        X509Certificate cert11 = getTestCertificate("cert11.pem");
        X509Certificate cert12 = getTestCertificate("cert12.pem");
        X509Certificate cert13 = getTestCertificate("cert13.pem");
        // @formatter:off
        return new Object[][] {
             /*
              * cert1:
              *   subject "cn=ldap.example.com,o=OpenDJ"
              *   san (none)
              */
            { cert1, "ldap.example.com", true },
            { cert1, "ldap2.example.com", false },
            { cert1, "192.168.0.1", false },
            { cert1, "2001:db8::1:0:0:1", false },
            { cert1, "*.example.com", false },
             /*
              * cert2:
              *   subject "email=info,cn=ldap.example.com,o=OpenDJ"
              *   san (none)
              */
            { cert2, "ldap.example.com", true },
            { cert2, "ldap2.example.com", false },
            { cert2, "192.168.0.1", false },
            { cert2, "2001:db8::1:0:0:1", false },
            { cert2, "info", false },
             /*
              * cert3:
              *   subject "cn=ldap.example.com,o=OpenDJ"
              *   san [ dnsName "ldap.example.org"] critical
              */
            { cert3, "ldap.example.org", true },
            { cert3, "ldap.example.com", false }, // critical so no fall back to testing subject DN
            { cert3, "ldap2.example.org", false },
            { cert3, "192.168.0.1", false },
            { cert3, "2001:db8::1:0:0:1", false },
             /*
              * cert4:
              *   subject "cn=ldap.example.com,o=OpenDJ"
              *   san [ dnsName "ldap.example.org"] non-critical
              */
            { cert4, "ldap.example.org", true },
            { cert4, "ldap.example.com", true }, // falls back to testing subject DN
            { cert4, "ldap2.example.org", false },
            { cert4, "192.168.0.1", false },
            { cert4, "2001:db8::1:0:0:1", false },
             /*
              * cert5:
              *   subject "cn=server,o=OpenDJ"
              *   san [ dnsName "ldap1.example.com", "ldap2.example.com" ] critical
              */
            { cert5, "ldap.example.com", false },
            { cert5, "server", false },
            { cert5, "ldap1.example.com", true },
            { cert5, "ldap2.example.com", true },
             /*
              * cert6:
              *   subject "cn=server,o=OpenDJ"
              *   san [ dnsName "*.example.com" ] critical
              */
            { cert6, "ldap.example.com", true },
            { cert6, "ldap10.example.com", true },
            { cert6, "ldap.dev.example.com", false },
            { cert6, "server", false },
             /*
              * cert7:
              *   subject "cn=*.example.com,o=OpenDJ"
              *   san (none)
              */
            { cert7, "ldap1.example.com", true },
            { cert7, "ldap2.example.com", true },
            { cert7, "ldap.dev.example.com", false },
            { cert7, "192.168.0.1", false },
            { cert7, "2001:db8::1:0:0:1", false },
            { cert7, "ldap.example.org", false },
             /*
              * cert8:
              *   subject "cn=server,o=OpenDJ"
              *   san [ dnsName "ldap.example.com", ip "192.168.0.1" ] critical
              */
            { cert8, "ldap.example.com", true },
            { cert8, "192.168.0.1", true },
            { cert8, "ldap2.example.com", false },
            { cert8, "192.168.0.2", false },
            { cert8, "2001:db8::1:0:0:1", false },
            { cert8, "server", false },
             /*
              * cert9:
              *   subject "cn=server,o=OpenDJ"
              *   san [ ip "2001:db8::1:0:0:1" ] critical
              */
            { cert9, "2001:db8::1:0:0:1", true },
            { cert9, "ldap.example.com", false },
            { cert9, "server", false },
             /*
              * cert10:
              *   subject "cn=server,o=OpenDJ"
              *   san [ email "info@forgerock.org" ] critical
              */
            { cert10, "ldap.example.com", false },
            { cert10, "server", false },
             /*
              * cert11:
              *   subject "cn=ldap.example.com,o=OpenDJ"
              *   san [ uri "ldap://ldap.example.com ] critical
              */
            { cert11, "ldap.example.com", false },
             /*
              * cert12:
              *   subject "cn=server,o=OpenDJ"
              *   san [ dns "ldap.example.com", uri "ldap://ldap.example.com" ] non-critical
              */
            { cert12, "ldap.example.com", true },
            { cert12, "server", true },
             /*
              * cert13:
              *  subject ""
              *  san [ dns "ldap.example.com" ] critical
              */
            { cert13, "ldap.example.com", true },
            { cert13, "server", false },

        };
        // @formatter:on
    }

    /**
     * Disables logging before the tests.
     */
    @BeforeClass
    public void disableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.SEVERE);
    }

    /**
     * Re-enable logging after the tests.
     */
    @AfterClass
    public void enableLogging() {
        TestCaseUtils.setDefaultLogLevel(Level.INFO);
    }

    private X509Certificate getTestCertificate(String filename) throws Exception {
        String path = TestCaseUtils.getTestFilePath("org.forgerock.opendj.ldap.TrustManagers" + File.separator
                + filename);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = new FileInputStream(path)) {
            return (X509Certificate) cf.generateCertificate(is);
        }
    }

    @Test(dataProvider = "hostnameTestData")
    public void testHostnames(X509Certificate subject, String hostname, boolean expectedResult)
            throws Exception {
        X509TrustManager mgr = TrustManagers.checkHostName(hostname, TrustManagers.trustAll());
        try {
            mgr.checkServerTrusted(new X509Certificate[] { subject }, "RSA");
            Assert.assertTrue(expectedResult);
        } catch (CertificateException ce) {
            Assert.assertFalse(expectedResult);
        }
    }
}
