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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.model;

import static org.fest.assertions.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.server.setup.model.Certificate.CertificateType;
import org.testng.annotations.Test;

public class CertificateTestCase extends AbstractSetupTestCase {

    /**
     * Default is a self-signed certificate.
     */
    @Test
    public void testGetDefault() {
        final Certificate cert = new Certificate();
        assertThat(cert.getType()).isEqualTo(CertificateType.SELF_SIGNED);
        assertThat(cert.getKeyStoreFile()).isNull();
        assertThat(cert.getKeyStorePin()).isEmpty();
    }

    @Test
    public void testValidateDefaultCertificate() throws ConfigException {
        final Certificate cert = new Certificate();
        cert.validate();
    }

    /**
     * Certificates which are not self-signed should fail when no key store is provided.
     *
     * @throws ConfigException
     */
    @Test(expectedExceptions = ConfigException.class)
    public void testValidateCertificateFailsWhenNoKeystoreProvided() throws ConfigException {
        final Certificate cert = new Certificate();
        cert.setType(CertificateType.JKS);
        cert.validate();
    }

    /**
     * Certificates which are not self-signed should fail when no key store is provided.
     *
     * @throws ConfigException
     */
    @Test(expectedExceptions = ConfigException.class)
    public void testValidatePKCS11CertificateFailsWhenNoKeyPinProvided() throws ConfigException {
        final Certificate cert = new Certificate();
        cert.setType(CertificateType.PKCS11);
        cert.validate();
    }

    /**
     * Certificates which are not self-signed should fail when no key pin is provided.
     *
     * @throws ConfigException
     *             Occurs if this configuration is invalid.
     * @throws IOException
     *             If an exception occurs when creating the keystore.
     */
    @Test(expectedExceptions = ConfigException.class)
    public void testValidateCertificateFailsWhenNoKeyPinProvided() throws ConfigException, IOException {
        final Certificate cert = new Certificate();
        cert.setType(CertificateType.JKS);
        File keystore = null;
        try {
            keystore = File.createTempFile("keystore", ".keystore");
            cert.setKeyStoreFile(keystore);
            cert.validate();
        } catch (IOException e) {
            throw e;
        } finally {
            if (keystore != null) {
                keystore.delete();
            }
        }
    }

    /**
     * Builds a new JKS certificate.
     *
     * @throws ConfigException
     *             Occurs if this configuration is invalid.
     * @throws IOException
     *             If an exception occurs when creating the temp keystore.
     */
    @Test
    public void testValidateJKSCertificate() throws ConfigException, IOException {
        final Certificate cert = new Certificate();
        cert.setType(CertificateType.JKS);
        File keystore = null;
        try {
            keystore = File.createTempFile("keystore", ".keystore");
            cert.setKeyStoreFile(keystore);
            cert.setKeyStorePin("key pin");
            cert.validate();
        } finally {
            if (keystore != null) {
                keystore.delete();
            }
        }
    }

}
