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
import static org.opends.server.extensions.FileBasedKeyManagerProvider.getKeyStorePIN;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.LDAPTrustManagerProviderCfg;
import org.forgerock.opendj.server.config.server.TrustManagerProviderCfg;
import org.forgerock.util.Factory;
import org.forgerock.util.Options;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.ExpirationCheckTrustManager;

/** This class defines a trust manager provider that will reference certificates stored in an LDAP backend. */
public class LDAPTrustManagerProvider extends TrustManagerProvider<LDAPTrustManagerProviderCfg>
        implements ConfigurationChangeListener<LDAPTrustManagerProviderCfg>
{
  /** The handle to the configuration for this trust manager. */
  private LDAPTrustManagerProviderCfg currentConfig;
  private Factory<char[]> passwordFactory;

  /** Lazily initialized key store (some services are unavailable at server startup). */
  private KeyStore keyStore;

  /** Creates a new LDAP trust manager provider. */
  public LDAPTrustManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeTrustManagerProvider(LDAPTrustManagerProviderCfg cfg) throws InitializationException
  {
    configure(cfg);
    cfg.addLDAPChangeListener(this);
  }

  private synchronized void configure(final LDAPTrustManagerProviderCfg cfg) throws InitializationException
  {
    keyStore = null;
    passwordFactory = newClearTextPasswordFactory(getTrustStorePIN(cfg));
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
  public synchronized void finalizeTrustManagerProvider()
  {
    keyStore = null;
    currentConfig.removeLDAPChangeListener(this);
  }

  @Override
  public TrustManager[] getTrustManagers() throws DirectoryException
  {
    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(getKeyStore());
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      TrustManager[] newTrustManagers = new TrustManager[trustManagers.length];
      for (int i=0; i < trustManagers.length; i++)
      {
        newTrustManagers[i] = new ExpirationCheckTrustManager((X509TrustManager) trustManagers[i]);
      }
      return newTrustManagers;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_LDAP_TRUSTMANAGER_CANNOT_CREATE_FACTORY.get(currentConfig.getBaseDN(),
                                                                                   getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public boolean isConfigurationAcceptable(TrustManagerProviderCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable((LDAPTrustManagerProviderCfg) cfg, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(LDAPTrustManagerProviderCfg cfg,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      getTrustStorePIN(cfg);
      return true;
    }
    catch (InitializationException e)
    {
      unacceptableReasons.add(e.getMessageObject());
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(LDAPTrustManagerProviderCfg cfg)
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

  private static char[] getTrustStorePIN(LDAPTrustManagerProviderCfg cfg) throws InitializationException
  {
    return getKeyStorePIN(cfg.getTrustStorePinProperty(),
                          cfg.getTrustStorePinEnvironmentVariable(),
                          cfg.getTrustStorePinFile(),
                          cfg.getTrustStorePin(),
                          cfg.dn(),
                          ERR_LDAP_TRUSTMANAGER_PIN_PROPERTY_NOT_SET,
                          ERR_LDAP_TRUSTMANAGER_PIN_ENVAR_NOT_SET,
                          ERR_LDAP_TRUSTMANAGER_PIN_NO_SUCH_FILE,
                          ERR_LDAP_TRUSTMANAGER_PIN_FILE_CANNOT_READ,
                          ERR_LDAP_TRUSTMANAGER_PIN_FILE_EMPTY);
  }
}
