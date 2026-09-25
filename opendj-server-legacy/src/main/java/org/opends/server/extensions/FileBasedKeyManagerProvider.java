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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import com.forgerock.opendj.util.FipsStaticUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg3;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.FileBasedKeyManagerProviderCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class defines a key manager provider that will access keys stored in a
 * file located on the Directory Server filesystem.
 */
public class FileBasedKeyManagerProvider
       extends KeyManagerProvider<FileBasedKeyManagerProviderCfg>
       implements ConfigurationChangeListener<FileBasedKeyManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration for this key manager provider. */
  private FileBasedKeyManagerProviderCfg currentConfig;

  /** The path to the key store backing file. */
  private String keyStoreFile;
  /** The key store type to use. */
  private String keyStoreType;
  /** What the key managers handed out by {@link #getKeyManagers()} delegate to. */
  private volatile LoadedKeyManager loaded;

  /**
   * A key manager loaded from the key store file, with the stamps of the files it was loaded
   * from and the aliases the file held private keys under.
   */
  private static final class LoadedKeyManager
  {
    private final List<FileStamp> stamps;
    private final Set<String> keyAliases;
    private final X509ExtendedKeyManager keyManager;

    private LoadedKeyManager(List<FileStamp> stamps, Set<String> keyAliases, X509ExtendedKeyManager keyManager)
    {
      this.stamps = stamps;
      this.keyAliases = keyAliases;
      this.keyManager = keyManager;
    }
  }

  /**
   * The key manager handed out by {@link #getKeyManagers()}. The key store file is looked at
   * again when a handshake chooses its alias, and only then: the certificate chain and the
   * private key of the alias chosen come from the key manager that alias was chosen from,
   * unless the file is loaded again, by another handshake, in between.
   */
  private final class ReloadingKeyManager extends X509ExtendedKeyManager
  {
    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers)
    {
      return currentKeyManager().getClientAliases(keyType, issuers);
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket)
    {
      return currentKeyManager().chooseClientAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine)
    {
      return currentKeyManager().chooseEngineClientAlias(keyType, issuers, engine);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers)
    {
      return currentKeyManager().getServerAliases(keyType, issuers);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
    {
      return currentKeyManager().chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine)
    {
      return currentKeyManager().chooseEngineServerAlias(keyType, issuers, engine);
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias)
    {
      return loaded.keyManager.getCertificateChain(alias);
    }

    @Override
    public PrivateKey getPrivateKey(String alias)
    {
      return loaded.keyManager.getPrivateKey(alias);
    }
  }

  /**
   * Creates a new instance of this file-based key manager provider.  The
   * <CODE>initializeKeyManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public FileBasedKeyManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeKeyManagerProvider(FileBasedKeyManagerProviderCfg cfg)
      throws ConfigException, InitializationException
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    currentConfig = cfg;
    keyStoreFile = getKeyStoreFile(cfg, ccr);
    keyStoreType = getKeyStoreType(cfg, ccr);
    getKeyStorePIN(cfg, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new InitializationException(ccr.getMessages().get(0));
    }

    cfg.addFileBasedChangeListener(this);
  }

  @Override
  public void finalizeKeyManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }

  @Override
  public boolean containsKeyWithAlias(String alias)
  {
    try
    {
      return keyAliases(getKeystore(currentPIN())).contains(alias);
    }
    catch (DirectoryException e)
    {
      // Ignore.
      logger.traceException(e);
    }
    return false;
  }

  /**
   * Returns the PIN the configuration names now, rather than the one it named when the provider
   * was configured: a PIN file may have been renewed since, together with the key store.
   */
  private char[] currentPIN() throws DirectoryException
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    final char[] pin = getKeyStorePIN(currentConfig, ccr);
    if (ccr.getResultCode() != ResultCode.SUCCESS)
    {
      throw new DirectoryException(ccr.getResultCode(), ccr.getMessages().get(0));
    }
    return pin;
  }

  private KeyStore getKeystore(char[] keyStorePIN) throws DirectoryException
  {
    try
    {
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);

      try (FileInputStream inputStream = new FileInputStream(getFileForPath(keyStoreFile)))
      {
        keyStore.load(inputStream, keyStorePIN);
      }
      return keyStore;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_LOAD.get(keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * The key manager returned reads the key store file again when a handshake starts after the
   * file, or the PIN file, has changed, so that a renewed certificate is presented without
   * restarting the server or the component using it.
   */
  @Override
  public KeyManager[] getKeyManagers() throws DirectoryException
  {
    final List<FileStamp> stamps = stampFiles();
    final char[] pin = currentPIN();
    final KeyStore keyStore = getKeystore(pin);
    final Set<String> keyAliases = keyAliases(keyStore);
    if (keyAliases.isEmpty())
    {
      // Troubleshooting message to let now of possible config error
      logger.error(ERR_NO_KEY_ENTRY_IN_KEYSTORE, keyStoreFile);
    }
    final KeyManager[] keyManagers = loadKeyManagers(keyStore, pin);
    if (keyManagers.length != 1 || !(keyManagers[0] instanceof X509ExtendedKeyManager))
    {
      return keyManagers;
    }
    loaded = new LoadedKeyManager(stamps, keyAliases, (X509ExtendedKeyManager) keyManagers[0]);
    return new KeyManager[] { new ReloadingKeyManager() };
  }

  private List<FileStamp> stampFiles()
  {
    final String pinFile = currentConfig.getKeyStorePinFile();
    return FileStamp.of(getFileForPath(keyStoreFile), pinFile != null ? getFileForPath(pinFile) : null);
  }

  /**
   * Returns the key manager to use for a new handshake, first loading the key store file again
   * when it has changed since it was last loaded. A file that cannot be loaded - caught half
   * written, or not matching its PIN - leaves the key manager last loaded in use, and is not
   * tried again until it changes again. So does a file with no private key, or with none under
   * the aliases the file held private keys under before: a connection handler presents the key
   * named by its ssl-cert-nickname, and would find none to present.
   */
  private X509ExtendedKeyManager currentKeyManager()
  {
    LoadedKeyManager current = loaded;
    if (current.stamps.equals(stampFiles()))
    {
      return current.keyManager;
    }
    synchronized (this)
    {
      // stamped again under the lock: stamps taken before it may be those of a write another
      // thread has loaded past meanwhile
      final List<FileStamp> stamps = stampFiles();
      current = loaded;
      if (current.stamps.equals(stamps))
      {
        return current.keyManager;
      }
      try
      {
        final char[] pin = currentPIN();
        final KeyStore keyStore = getKeystore(pin);
        final Set<String> keyAliases = keyAliases(keyStore);
        if (keyAliases.isEmpty())
        {
          throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
              ERR_NO_KEY_ENTRY_IN_KEYSTORE.get(keyStoreFile));
        }
        if (!current.keyAliases.isEmpty() && Collections.disjoint(current.keyAliases, keyAliases))
        {
          throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
              ERR_FILE_KEYMANAGER_NO_KNOWN_KEY_ALIAS.get(keyStoreFile, current.keyAliases));
        }
        final KeyManager[] keyManagers = loadKeyManagers(keyStore, pin);
        if (keyManagers.length != 1 || !(keyManagers[0] instanceof X509ExtendedKeyManager))
        {
          throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
              ERR_FILE_KEYMANAGER_CANNOT_CREATE_FACTORY.get(keyStoreFile, Arrays.toString(keyManagers)));
        }
        loaded = new LoadedKeyManager(stamps, keyAliases, (X509ExtendedKeyManager) keyManagers[0]);
        logger.info(NOTE_FILE_KEYMANAGER_RELOADED, keyStoreFile, currentConfig.dn());
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);
        loaded = new LoadedKeyManager(stamps, current.keyAliases, current.keyManager);
        logger.error(ERR_FILE_KEYMANAGER_CANNOT_RELOAD, keyStoreFile, currentConfig.dn(), e.getMessageObject());
      }
      return loaded.keyManager;
    }
  }

  private KeyManager[] loadKeyManagers(KeyStore keyStore, char[] keyStorePIN) throws DirectoryException
  {
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

      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_CREATE_FACTORY.get(keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public boolean containsAtLeastOneKey()
  {
    try
    {
      return !keyAliases(getKeystore(currentPIN())).isEmpty();
    }
    catch (Exception e) {
      logger.traceException(e);
      return false;
    }
  }

  /** Returns the aliases the key store holds private keys under. */
  private Set<String> keyAliases(KeyStore keyStore) throws DirectoryException
  {
    try
    {
      final Set<String> keyAliases = new TreeSet<>();
      final Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements())
      {
        final String alias = aliases.nextElement();
        if (keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class))
        {
          keyAliases.add(alias);
        }
      }
      return keyAliases;
    }
    catch (KeyStoreException e)
    {
      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_LOAD.get(keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public boolean isConfigurationAcceptable(FileBasedKeyManagerProviderCfg cfg,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(FileBasedKeyManagerProviderCfg cfg,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    int startSize = unacceptableReasons.size();

    final ConfigChangeResult ccr = new ConfigChangeResult();
    getKeyStoreFile(cfg, ccr);
    getKeyStoreType(cfg, ccr);
    getKeyStorePIN(cfg, ccr);
    unacceptableReasons.addAll(ccr.getMessages());

    return startSize == unacceptableReasons.size();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedKeyManagerProviderCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    String newKeyStoreFile = getKeyStoreFile(cfg, ccr);
    String newKeyStoreType = getKeyStoreType(cfg, ccr);
    getKeyStorePIN(cfg, ccr);

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      synchronized (this)
      {
        currentConfig = cfg;
        keyStoreFile  = newKeyStoreFile;
        keyStoreType  = newKeyStoreType;
        // the key managers already handed out load the key store the new configuration names
        // on their next handshake, even where its files are those they were loaded from, and
        // whatever aliases it holds its keys under
        if (loaded != null)
        {
          loaded = new LoadedKeyManager(Collections.<FileStamp> emptyList(), Collections.<String> emptySet(),
              loaded.keyManager);
        }
      }
    }

    return ccr;
  }

  /** Get the path to the key store file. */
  private String getKeyStoreFile(FileBasedKeyManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    String keyStoreFile = cfg.getKeyStoreFile();
    File f = getFileForPath(keyStoreFile);
    if (!f.exists() || !f.isFile())
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(keyStoreFile, cfg.dn()));
    }
    return keyStoreFile;
  }

  /** Get the keystore type. If none is specified, then use the default type. */
  private String getKeyStoreType(FileBasedKeyManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    if (cfg.getKeyStoreType() != null)
    {
      try
      {
        if(cfg.getKeyStoreType().equals("BCFKS")) {
          FipsStaticUtils.registerBcProvider(true);
        }
        KeyStore.getInstance(cfg.getKeyStoreType());
        return cfg.getKeyStoreType();
      }
      catch (KeyStoreException e)
      {
        logger.traceException(e);
        ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_INVALID_TYPE.get(cfg.getKeyStoreType(), cfg.dn(), getExceptionMessage(e)));
      }
    }
    return KeyStore.getDefaultType();
  }

  private char[] getKeyStorePIN(FileBasedKeyManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      return getKeyStorePIN(cfg.getKeyStorePinProperty(),
                            cfg.getKeyStorePinEnvironmentVariable(),
                            cfg.getKeyStorePinFile(),
                            cfg.getKeyStorePin(),
                            cfg.dn(),
                            ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET,
                            ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET,
                            ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE,
                            ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ,
                            ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY);
    }
    catch (InitializationException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
      return null;
    }
  }

  /**
   * Returns the PIN needed to access the contents of a key store. We will offer several places to look for the PIN,
   * and we will do so in the following order:
   * <ol>
   *     <li>In a specified Java property</li>
   *     <li>In a specified environment variable</li>
   *     <li>In a specified file on the server filesystem</li>
   *     <li>As the value of a configuration attribute.</li>
   * </ol>
   * In any case, the PIN must be in the clear.
   * <p>
   * It is acceptable to have no PIN (OPENDJ-18).
   */
  static char[] getKeyStorePIN(final String pinProperty, final String pinEnvVar, final String pinFileName,
                               final String pinString, final DN cfgDN, final Arg2<Object, Object> propertyNotSetMsg,
                               final Arg2<Object, Object> envVarNotSetMsg, final Arg2<Object, Object> noSuchFileMsg,
                               final Arg3<Object, Object, Object> fileCannotReadMsg,
                               final Arg2<Object, Object> fileEmptyMsg) throws InitializationException
  {
    if (pinProperty != null)
    {
      final String pin = System.getProperty(pinProperty);
      if (pin == null)
      {
        throw new InitializationException(propertyNotSetMsg.get(pinProperty, cfgDN));
      }
      return pin.toCharArray();
    }

    if (pinEnvVar != null)
    {
      final String pin = System.getenv(pinEnvVar);
      if (pin == null)
      {
        throw new InitializationException(envVarNotSetMsg.get(pinEnvVar, cfgDN));
      }
      return pin.toCharArray();
    }

    if (pinFileName != null)
    {
      final File pinFile = getFileForPath(pinFileName);
      if (pinFile.exists())
      {
        String pin;
        try (BufferedReader br = new BufferedReader(new FileReader(pinFile)))
        {
          pin = br.readLine();
        }
        catch (IOException e)
        {
          final LocalizableMessage msg = fileCannotReadMsg.get(pinFileName, cfgDN, getExceptionMessage(e));
          throw new InitializationException(msg, e);
        }
        if (pin == null)
        {
          throw new InitializationException(fileEmptyMsg.get(pinFileName, cfgDN));
        }
        return pin.toCharArray();
      }
      else
      {
        throw new InitializationException(noSuchFileMsg.get(pinFileName, cfgDN));
      }
    }

    return pinString != null ? pinString.toCharArray() : null;
  }
}
