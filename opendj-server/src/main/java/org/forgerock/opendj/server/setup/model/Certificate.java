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

import java.io.File;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;

/**
 * This class is to create a certificate configuration.
 */
class Certificate {
    public enum CertificateType {
        /**
         * Self-signed certificate.
         */
        SELF_SIGNED,
        /**
         * Java KeyStore.
         */
        JKS,
        /**
         * Java Cryptography Extension.
         */
        JCEKS,
        /**
         * Public-Key Cryptography Standards 12.
         */
        PKCS12,
        /**
         * Public-Key Cryptography Standards 11 token.
         */
        PKCS11
    }

    private CertificateType type;
    private String certNickName;
    private File keyStoreFile;
    private String keyStorePin;

    /**
     * Default constructor.
     */
    Certificate() {
        type = CertificateType.SELF_SIGNED;
        certNickName = "";
        keyStorePin = "";
    }

    /**
     * Returns the certificate nickname.
     *
     * @return The certificate nickname.
     */
    public String getCertNickName() {
        return certNickName;
    }

    /**
     * Sets the certificate nickname.
     *
     * @param certNickName
     *            The certificate nickname.
     */
    public void setCertNickName(String certNickName) {
        this.certNickName = certNickName;
    }


    /**
     * Returns the type of this certificate.
     *
     * @return The type of this certificate.
     */
    public CertificateType getType() {
        return type;
    }

    /**
     * Sets the type of this certificate.
     *
     * @param type
     *            The type of this certificate (JKS, self-signed...)
     */
    public void setType(CertificateType type) {
        this.type = type;
    }

    /**
     * Returns the key store file.
     *
     * @return The key store file.
     */
    public File getKeyStoreFile() {
        return keyStoreFile;
    }

    /**
     * Sets the key store file.
     *
     * @param keyStoreFile
     *            The key store file.
     */
    public void setKeyStoreFile(File keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    /**
     * Returns the key store PIN.
     *
     * @return The key store PIN.
     */
    public String getKeyStorePin() {
        return keyStorePin;
    }

    /**
     * Sets the key store PIN.
     *
     * @param keyStorePin
     *            The key store PIN.
     */
    public void setKeyStorePin(String keyStorePin) {
        this.keyStorePin = keyStorePin;
    }

    /**
     * Validates the actual configuration for this certificate.
     *
     * @throws ConfigException
     *             If this certificate configuration is invalid.
     */
    public void validate() throws ConfigException {
        if (type == CertificateType.JKS || type == CertificateType.JCEKS || type == CertificateType.PKCS12) {
            if (keyStoreFile == null || !keyStoreFile.exists()) {
                throw new ConfigException(LocalizableMessage.raw("Invalid keystore file"));
            }
            if (keyStorePin.isEmpty()) {
                throw new ConfigException(LocalizableMessage.raw("Invalid key pin"));
            }
        } else if (type == CertificateType.PKCS11) {
            if (keyStorePin.isEmpty()) {
                throw new ConfigException(LocalizableMessage.raw("Invalid key pin"));
            }
        }
    }
}
