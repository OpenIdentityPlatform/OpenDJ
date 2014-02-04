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

import java.util.ArrayList;


/**
 * This class contains utilities for the OpenDJ3 setup.
 */
final class ModelUtils {

    static final String OBFUSCATED_VALUE = "******";
    /*void original() {

        ArrayList<String> cmdLine = new ArrayList<String>();
        String setupFile;
        if (Utils.isWindows())
        {
          setupFile = Installation.WINDOWS_SETUP_FILE_NAME;
        }
        else
        {
          setupFile = Installation.UNIX_SETUP_FILE_NAME;
        }
        cmdLine.add(getInstallDir(userData) + setupFile);
        cmdLine.add("--cli");

        for (String baseDN : getBaseDNs(userData))
        {
          cmdLine.add("--baseDN");
          cmdLine.add(baseDN);
        }

        switch (userData.getNewSuffixOptions().getType())
        {
        case CREATE_BASE_ENTRY:
          cmdLine.add("--addBaseEntry");
          break;
        case IMPORT_AUTOMATICALLY_GENERATED_DATA:
          cmdLine.add("--sampleData");
          cmdLine.add(String.valueOf(
              userData.getNewSuffixOptions().getNumberEntries()));
          break;
        case IMPORT_FROM_LDIF_FILE:
          for (String ldifFile : userData.getNewSuffixOptions().getLDIFPaths())
          {
            cmdLine.add("--ldifFile");
            cmdLine.add(ldifFile);
          }
          String rejectFile = userData.getNewSuffixOptions().getRejectedFile();
          if (rejectFile != null)
          {
            cmdLine.add("--rejectFile");
            cmdLine.add(rejectFile);
          }
          String skipFile = userData.getNewSuffixOptions().getSkippedFile();
          if (skipFile != null)
          {
            cmdLine.add("--skipFile");
            cmdLine.add(skipFile);
          }
          break;
        }

        cmdLine.add("--ldapPort");
        cmdLine.add(String.valueOf(userData.getServerPort()));
        cmdLine.add("--adminConnectorPort");
        cmdLine.add(String.valueOf(userData.getAdminConnectorPort()));
        if (userData.getServerJMXPort() != -1)
        {
          cmdLine.add("--jmxPort");
          cmdLine.add(String.valueOf(userData.getServerJMXPort()));
        }
        cmdLine.add("--rootUserDN");
        cmdLine.add(userData.getDirectoryManagerDn());
        cmdLine.add("--rootUserPassword");
        cmdLine.add(OBFUSCATED_VALUE);

        if (Utils.isWindows() && userData.getEnableWindowsService())
        {
          cmdLine.add("--enableWindowsService");
        }
        if (userData.getReplicationOptions().getType() ==
          DataReplicationOptions.Type.STANDALONE &&
          !userData.getStartServer())
        {
          cmdLine.add("--doNotStart");
        }

        if (userData.getSecurityOptions().getEnableStartTLS())
        {
          cmdLine.add("--enableStartTLS");
        }
        if (userData.getSecurityOptions().getEnableSSL())
        {
          cmdLine.add("--ldapsPort");
          cmdLine.add(String.valueOf(userData.getSecurityOptions().getSslPort()));
        }
        switch (userData.getSecurityOptions().getCertificateType())
        {
        case SELF_SIGNED_CERTIFICATE:
          cmdLine.add("--generateSelfSignedCertificate");
          cmdLine.add("--hostName");
          cmdLine.add(userData.getHostName());
          break;
        case JKS:
          cmdLine.add("--useJavaKeystore");
          cmdLine.add(userData.getSecurityOptions().getKeystorePath());
          if (userData.getSecurityOptions().getKeystorePassword() != null)
          {
            cmdLine.add("--keyStorePassword");
            cmdLine.add(OBFUSCATED_VALUE);
          }
          if (userData.getSecurityOptions().getAliasToUse() != null)
          {
            cmdLine.add("--certNickname");
            cmdLine.add(userData.getSecurityOptions().getAliasToUse());
          }
          break;
        case JCEKS:
          cmdLine.add("--useJCEKS");
          cmdLine.add(userData.getSecurityOptions().getKeystorePath());
          if (userData.getSecurityOptions().getKeystorePassword() != null)
          {
            cmdLine.add("--keyStorePassword");
            cmdLine.add(OBFUSCATED_VALUE);
          }
          if (userData.getSecurityOptions().getAliasToUse() != null)
          {
            cmdLine.add("--certNickname");
            cmdLine.add(userData.getSecurityOptions().getAliasToUse());
          }
          break;
        case PKCS12:
          cmdLine.add("--usePkcs12keyStore");
          cmdLine.add(userData.getSecurityOptions().getKeystorePath());
          if (userData.getSecurityOptions().getKeystorePassword() != null)
          {
            cmdLine.add("--keyStorePassword");
            cmdLine.add(OBFUSCATED_VALUE);
          }
          if (userData.getSecurityOptions().getAliasToUse() != null)
          {
            cmdLine.add("--certNickname");
            cmdLine.add(userData.getSecurityOptions().getAliasToUse());
          }
          break;
        case PKCS11:
          cmdLine.add("--usePkcs11Keystore");
          if (userData.getSecurityOptions().getKeystorePassword() != null)
          {
            cmdLine.add("--keyStorePassword");
            cmdLine.add(OBFUSCATED_VALUE);
          }
          if (userData.getSecurityOptions().getAliasToUse() != null)
          {
            cmdLine.add("--certNickname");
            cmdLine.add(userData.getSecurityOptions().getAliasToUse());
          }
          break;
        }

        cmdLine.add("--no-prompt");
        cmdLine.add("--noPropertiesFile");
        return cmdLine;

    }*/

    ArrayList<String> getSetupEquivalentCommandLine(final Model configuration) {
        final ArrayList<String> cmdLines = new ArrayList<String>();
        final ListenerSettings settings = configuration.getListenerSettings();

        // Starts the server ?
        if (configuration.getType() == Model.Type.STANDALONE
                && !configuration.isStartingServerAfterSetup()) {
            cmdLines.add("--doNotStart");
        }
        // Secure ?
        if (configuration.isSecure()) {
            if (settings.isTLSEnabled()) {
                cmdLines.add("--enableStartTLS");
            }
            if (settings.isSSLEnabled()) {
                cmdLines.add("--ldapsPort");
                cmdLines.add(String.valueOf(settings.getSSLPortNumber()));
            }
            // Certificate section.
            final Certificate certificate = settings.getCertificate();

            switch (certificate.getType()) {
            case SELF_SIGNED:
                cmdLines.add("--generateSelfSignedCertificate");
                cmdLines.add("--hostName");
                cmdLines.add(settings.getHostName());
                break;
            case JKS:
                cmdLines.add("--useJavaKeystore");
                cmdLines.add(certificate.getKeyStoreFile().getAbsolutePath());
                if (certificate.getKeyStorePin() != null) {
                    cmdLines.add("--keyStorePassword");
                    cmdLines.add(OBFUSCATED_VALUE);
                }
                if (!certificate.getCertNickName().isEmpty()) {
                    cmdLines.add("--certNickname");
                    cmdLines.add(certificate.getCertNickName());
                }
                break;
            case JCEKS:
                cmdLines.add("--useJCEKS");
                cmdLines.add(certificate.getKeyStoreFile().getAbsolutePath());
                if (certificate.getKeyStorePin() != null) {
                    cmdLines.add("--keyStorePassword");
                    cmdLines.add(OBFUSCATED_VALUE);
                }
                if (!certificate.getCertNickName().isEmpty()) {
                    cmdLines.add("--certNickname");
                    cmdLines.add(certificate.getCertNickName());
                }
                break;
            case PKCS12:
                cmdLines.add("--usePkcs12keyStore");
                cmdLines.add(certificate.getKeyStoreFile().getAbsolutePath());
                if (certificate.getKeyStorePin() != null) {
                    cmdLines.add("--keyStorePassword");
                    cmdLines.add(OBFUSCATED_VALUE);
                }
                if (!certificate.getCertNickName().isEmpty()) {
                    cmdLines.add("--certNickname");
                    cmdLines.add(certificate.getCertNickName());
                }
                break;
            case PKCS11:
                cmdLines.add("--usePkcs11Keystore");
                if (certificate.getKeyStorePin() != null) {
                    cmdLines.add("--keyStorePassword");
                    cmdLines.add(OBFUSCATED_VALUE);
                }
                if (!certificate.getCertNickName().isEmpty()) {
                    cmdLines.add("--certNickname");
                    cmdLines.add(certificate.getCertNickName());
                }
                break;
            }
        }
        cmdLines.add("--no-prompt");
        cmdLines.add("--noPropertiesFile");
        return cmdLines;
    }
}
