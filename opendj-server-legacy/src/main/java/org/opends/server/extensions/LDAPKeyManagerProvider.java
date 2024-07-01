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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.forgerock.opendj.adapter.server3x.Adapters.newRootConnectionFactory;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.OpenDJProvider.newLDAPKeyStore;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;
import static org.forgerock.util.Options.defaultOptions;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.LDAPKeyManagerProviderCfg;
import org.forgerock.util.Factory;
import org.forgerock.util.Options;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/** This class defines a key manager provider that will access keys stored in an LDAP backend. */
public class LDAPKeyManagerProvider extends KeyManagerProvider<LDAPKeyManagerProviderCfg>
        implements ConfigurationChangeListener<LDAPKeyManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration for this key manager provider. */
  private LDAPKeyManagerProviderCfg currentConfig;
  private Factory<char[]> passwordFactory;

  /** Lazily initialized key store (some services are unavailable at server startup). */
  private KeyStore keyStore;

  /** Creates a new LDAP key manager provider. */
  public LDAPKeyManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeKeyManagerProvider(LDAPKeyManagerProviderCfg cfg) throws InitializationException
  {
    configure(cfg);
    cfg.addLDAPChangeListener(this);
  }

  private synchronized void configure(final LDAPKeyManagerProviderCfg cfg) throws InitializationException
  {
    keyStore = null;
    passwordFactory = newClearTextPasswordFactory(getKeyStorePIN(cfg));
    currentConfig = cfg;
  }

  private synchronized KeyStore getKeyStore()
  {
    if (keyStore == null)
    {
      final Options options = defaultOptions().set(GLOBAL_PASSWORD, passwordFactory);
      keyStore = newLDAPKeyStore(newRootConnectionFactory(), currentConfig.getBaseDN(), options);
    }
    return keyStore;
  }

  @Override
  public synchronized void finalizeKeyManagerProvider()
  {
    keyStore = null;
    currentConfig.removeLDAPChangeListener(this);
  }

  @Override
  public boolean containsKeyWithAlias(String alias)
  {
    try
    {
      return getKeyStore().entryInstanceOf(alias, PrivateKeyEntry.class);
    }
    catch (KeyStoreException e)
    {
      // Ignore.
      logger.traceException(e);
    }
    return false;
  }

  @Override
  public KeyManager[] getKeyManagers() throws DirectoryException
  {
    try
    {
      String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
      keyManagerFactory.init(getKeyStore(), passwordFactory.newInstance());
      return keyManagerFactory.getKeyManagers();
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_LDAP_KEYMANAGER_CANNOT_CREATE_FACTORY.get(currentConfig.getBaseDN(),
                                                                                 getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public boolean containsAtLeastOneKey()
  {
    try
    {
      // Not strictly correct since this test includes trusted certs and secret keys, but it should be sufficient.
      // A more accurate approach is to query each alias, but this could be expensive when the key store is large.
      return getKeyStore().size() > 0;
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
    return false;
  }

  @Override
  public boolean isConfigurationAcceptable(LDAPKeyManagerProviderCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(LDAPKeyManagerProviderCfg cfg,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      getKeyStorePIN(cfg);
      return true;
    }
    catch (InitializationException e)
    {
      unacceptableReasons.add(e.getMessageObject());
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(LDAPKeyManagerProviderCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      configure(cfg);
    }
    catch (InitializationException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }

  private static char[] getKeyStorePIN(LDAPKeyManagerProviderCfg cfg) throws InitializationException
  {
    return FileBasedKeyManagerProvider.getKeyStorePIN(cfg.getKeyStorePinProperty(),
                                                      cfg.getKeyStorePinEnvironmentVariable(),
                                                      cfg.getKeyStorePinFile(),
                                                      cfg.getKeyStorePin(),
                                                      cfg.dn(),
                                                      ERR_LDAP_KEYMANAGER_PIN_PROPERTY_NOT_SET,
                                                      ERR_LDAP_KEYMANAGER_PIN_ENVAR_NOT_SET,
                                                      ERR_LDAP_KEYMANAGER_PIN_NO_SUCH_FILE,
                                                      ERR_LDAP_KEYMANAGER_PIN_FILE_CANNOT_READ,
                                                      ERR_LDAP_KEYMANAGER_PIN_FILE_EMPTY);
  }
}
