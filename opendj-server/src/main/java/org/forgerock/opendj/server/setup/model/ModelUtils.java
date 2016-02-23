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
 * Copyright 2014-2015 ForgeRock AS.
 */

package org.forgerock.opendj.server.setup.model;

import java.util.ArrayList;


/**
 * This class contains utilities for the OpenDJ3 setup.
 */
final class ModelUtils {

    static final String OBFUSCATED_VALUE = "******";

    ArrayList<String> getSetupEquivalentCommandLine(final Model configuration) {
        final ArrayList<String> cmdLines = new ArrayList<>();
        final ListenerSettings settings = configuration.getListenerSettings();

        // Starts the server ?
        if (configuration.getType() == Model.Type.STANDALONE
                && !configuration.isStartingServerAfterSetup()) {
            cmdLines.add("--doNotStart");
        }

        if (configuration.isSecure()) {
            if (settings.isTLSEnabled()) {
                cmdLines.add("--enableStartTLS");
            }
            if (settings.isSSLEnabled()) {
                cmdLines.add("--ldapsPort");
                cmdLines.add(String.valueOf(settings.getSSLPortNumber()));
            }

            final Certificate certificate = settings.getCertificate();
            switch (certificate.getType()) {
            case SELF_SIGNED:
                cmdLines.add("--generateSelfSignedCertificate");
                cmdLines.add("--hostName");
                cmdLines.add(settings.getHostName());
                break;
            case JKS:
                appendKeystoreCliOptionsWithPath(cmdLines, "--useJavaKeystore", certificate);
                break;
            case JCEKS:
                appendKeystoreCliOptionsWithPath(cmdLines, "--useJCEKS", certificate);
                break;
            case PKCS12:
                appendKeystoreCliOptionsWithPath(cmdLines, "--usePkcs12keyStore", certificate);
                break;
            case PKCS11:
                cmdLines.add("--usePkcs11Keystore");
                // do not add a file path because this is a hardware store
                appendKeystoreCliOptions(cmdLines, certificate);
                break;
            }
        }
        cmdLines.add("--no-prompt");
        cmdLines.add("--noPropertiesFile");
        return cmdLines;
    }

    private void appendKeystoreCliOptionsWithPath(final ArrayList<String> cmdLines, final String cliOption,
            final Certificate certificate) {
        cmdLines.add(cliOption);
        cmdLines.add(certificate.getKeyStoreFile().getAbsolutePath());
        appendKeystoreCliOptions(cmdLines, certificate);
    }

    private void appendKeystoreCliOptions(final ArrayList<String> cmdLines, final Certificate certificate) {
        if (certificate.getKeyStorePin() != null) {
            cmdLines.add("--keyStorePassword");
            cmdLines.add(OBFUSCATED_VALUE);
        }
        if (!certificate.getCertNickName().isEmpty()) {
            cmdLines.add("--certNickname");
            cmdLines.add(certificate.getCertNickName());
        }
    }
}
