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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.PKCS11KeyManagerProviderCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class defines a key manager provider that will access keys stored on a
 * PKCS#11 device.  It will use the Java PKCS#11 interface, which may need to be
 * configured on the underlying system.
 */
public class PKCS11KeyManagerProvider extends KeyManagerProvider<PKCS11KeyManagerProviderCfg> implements
        ConfigurationChangeListener<PKCS11KeyManagerProviderCfg>
{
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /** The keystore type to use when accessing the PKCS#11 keystore. */
    private static final String PKCS11_KEYSTORE_TYPE = "PKCS11";
    /** The PIN needed to access the keystore. */
    private char[] keyStorePIN;
    /** The current configuration for this key manager provider. */
    private PKCS11KeyManagerProviderCfg currentConfig;

    /**
     * Creates a new instance of this PKCS#11 key manager provider.  The
     * <CODE>initializeKeyManagerProvider</CODE> method must be called on the
     * resulting object before it may be used.
     */
    public PKCS11KeyManagerProvider() {
        // No implementation is required.
    }

    @Override
    public void initializeKeyManagerProvider(PKCS11KeyManagerProviderCfg configuration)
            throws ConfigException, InitializationException
    {
        currentConfig = configuration;
        keyStorePIN = getKeyStorePIN(configuration);
        configuration.addPKCS11ChangeListener(this);
    }

    private char[] getKeyStorePIN(PKCS11KeyManagerProviderCfg cfg) throws InitializationException
    {
      return FileBasedKeyManagerProvider.getKeyStorePIN(cfg.getKeyStorePinProperty(),
                                                        cfg.getKeyStorePinEnvironmentVariable(),
                                                        cfg.getKeyStorePinFile(),
                                                        cfg.getKeyStorePin(),
                                                        cfg.dn(),
                                                        ERR_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET,
                                                        ERR_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET,
                                                        ERR_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE,
                                                        ERR_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ,
                                                        ERR_PKCS11_KEYMANAGER_PIN_FILE_EMPTY);
    }

    @Override
    public void finalizeKeyManagerProvider()
    {
        currentConfig.removePKCS11ChangeListener(this);
    }

    @Override
    public KeyManager[] getKeyManagers() throws DirectoryException
    {
        KeyStore keyStore;
        try
        {
            keyStore = KeyStore.getInstance(PKCS11_KEYSTORE_TYPE);
            keyStore.load(null, keyStorePIN);
        }
        catch (Exception e)
        {
            logger.traceException(e);

            LocalizableMessage message = ERR_PKCS11_KEYMANAGER_CANNOT_LOAD.get(getExceptionMessage(e));
            throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
        }

        try
        {
            String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
            keyManagerFactory.init(keyStore, keyStorePIN);
            return keyManagerFactory.getKeyManagers();
        }
        catch (Exception e)
        {
            logger.traceException(e);

            LocalizableMessage message = ERR_PKCS11_KEYMANAGER_CANNOT_CREATE_FACTORY.get(getExceptionMessage(e));
            throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
        }
    }

    @Override
    public boolean isConfigurationAcceptable(PKCS11KeyManagerProviderCfg configuration,
                                             List<LocalizableMessage> unacceptableReasons)
    {
        return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
    }

    @Override
    public boolean isConfigurationChangeAcceptable(PKCS11KeyManagerProviderCfg configuration,
                                                   List<LocalizableMessage> unacceptableReasons)
    {
        try
        {
            getKeyStorePIN(configuration);
            return true;
        }
        catch (InitializationException e)
        {
            unacceptableReasons.add(e.getMessageObject());
            return false;
        }
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(PKCS11KeyManagerProviderCfg configuration)
    {
        final ConfigChangeResult ccr = new ConfigChangeResult();
        try
        {
            keyStorePIN = getKeyStorePIN(configuration);
            currentConfig = configuration;
        }
        catch (InitializationException e)
        {
            ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
            ccr.addMessage(e.getMessageObject());
        }
        return ccr;
    }
}
