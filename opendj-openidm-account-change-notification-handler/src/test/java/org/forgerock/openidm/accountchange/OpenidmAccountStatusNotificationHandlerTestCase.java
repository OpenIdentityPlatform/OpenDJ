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
package org.forgerock.openidm.accountchange;

import static org.forgerock.openidm.accountchange.OpenidmAccountStatusNotificationHandler.findCertificateBySubject;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests how the handler finds the OpenIDM certificate named by {@code certificate-subject-dn}
 * among the certificates of its truststore (issue #1154).
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit" })
public class OpenidmAccountStatusNotificationHandlerTestCase extends ForgeRockTestCase {

    private KeyPair keyPair;

    @BeforeClass
    public void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @DataProvider
    public Object[][] sameName() {
        return new Object[][] {
            // The subject of the sample configuration
            { "CN=localhost, O=OpenIDM Self-Signed Certificate, OU=None, L=None, ST=None, C=None" },
            // DN.toString() escapes '=' in a value, the JDK's canonical form does not
            { "CN=idm=1,O=Example" },
            // The JDK's canonical form collapses internal spaces
            { "CN=idm  node,O=Example" },
            // The JDK's canonical form sorts the AVAs of a multi-valued RDN
            { "OU=sync+CN=idm,O=Example" },
            // The JDK's canonical form writes EMAILADDRESS as an OID with a BER hex string
            { "EMAILADDRESS=idm@example.com,CN=idm,O=Example" },
            // ... and DC, which it encodes as an IA5String, as a BER hex string
            { "UID=idm,DC=example,DC=com" },
        };
    }

    @Test(dataProvider = "sameName")
    public void findsTheCertificateWhoseSubjectIsTheConfiguredDN(String name) throws Exception {
        X509Certificate cert = certificate(name);

        assertSame(findCertificateBySubject(DN.valueOf(name), certificate("CN=other,O=Example"), cert), cert);
    }

    @Test
    public void findsTheCertificateWhateverTheCaseAndTheAVAOrderOfTheConfiguredDN() throws Exception {
        X509Certificate cert = certificate("CN=idm+OU=sync,O=Example");

        assertSame(findCertificateBySubject(DN.valueOf("ou=SYNC+cn=IDM,o=example"), cert), cert);
    }

    @Test
    public void findsNoCertificateWhenNoSubjectIsTheConfiguredDN() throws Exception {
        assertNull(findCertificateBySubject(DN.valueOf("CN=idm,O=Example"),
                certificate("CN=idm,O=Other"), certificate("CN=idm2,O=Example")));
    }

    @Test
    public void findsNoCertificateWhenTheJDKCannotParseTheConfiguredDN() throws Exception {
        // X500Principal does not know the "mail" keyword: the DN cannot name a certificate subject
        assertNull(findCertificateBySubject(DN.valueOf("mail=idm@example.com,O=Example"),
                certificate("CN=idm,O=Example")));
    }

    /** Returns a self-signed certificate whose subject is encoded as keytool encodes it. */
    private X509Certificate certificate(String subject) throws Exception {
        X500Principal name = new X500Principal(subject);
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(1, ChronoUnit.DAYS)), name,
                keyPair.getPublic());
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
    }
}
