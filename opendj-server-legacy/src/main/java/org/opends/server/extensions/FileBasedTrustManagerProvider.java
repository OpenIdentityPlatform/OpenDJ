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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.extensions;

import com.forgerock.opendj.util.FipsStaticUtils;
import org.forgerock.i18n.LocalizableMessage;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.TrustManagerProviderCfg;
import org.forgerock.opendj.server.config.server.FileBasedTrustManagerProviderCfg;
import org.opends.server.api.TrustManagerProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.util.ExpirationCheckTrustManager;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.FileBasedKeyManagerProvider.getKeyStorePIN;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.util.StaticUtils.isFips;

/**
 * This class defines a trust manager provider that will reference certificates
 * stored in a file located on the Directory Server filesystem.
 */
public class FileBasedTrustManagerProvider
       extends TrustManagerProvider<FileBasedTrustManagerProviderCfg>
       implements ConfigurationChangeListener<FileBasedTrustManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The handle to the configuration for this trust manager. */
  private FileBasedTrustManagerProviderCfg currentConfig;

  /** The path to the trust store backing file. */
  private String trustStoreFile;

  /** The trust store type to use. */
  private String trustStoreType;

  /**
   * The number of configuration changes applied. It is counted after the fields above are set,
   * and read before them.
   */
  private volatile int configurationChanges;

  /**
   * A trust manager loaded from the trust store file, with the configuration change and the
   * stamps of the files it was loaded under.
   */
  private static final class LoadedTrustManager
  {
    private final int configurationChanges;
    private final List<FileStamp> stamps;
    private final X509TrustManager trustManager;

    private LoadedTrustManager(int configurationChanges, List<FileStamp> stamps, X509TrustManager trustManager)
    {
      this.configurationChanges = configurationChanges;
      this.stamps = stamps;
      this.trustManager = trustManager;
    }

    private boolean isLoadedFrom(int configurationChanges, List<FileStamp> stamps)
    {
      return this.configurationChanges == configurationChanges && this.stamps.equals(stamps);
    }
  }

  /**
   * What a trust manager handed out by {@link #getTrustManagers()} delegates to. Each trust
   * manager handed out loads the file on its own, so asking the provider again, as a component
   * does to check a configuration change, leaves those in use alone.
   */
  private final class TrustStoreFollower
  {
    /** Whether the trust manager handed out is an extended one, which needs an extended one to delegate to. */
    private final boolean extended;
    /** The trust manager last loaded, when this one was handed out or by a check since. */
    private volatile LoadedTrustManager loaded;

    private TrustStoreFollower(LoadedTrustManager loaded)
    {
      this.extended = loaded.trustManager instanceof X509ExtendedTrustManager;
      this.loaded = loaded;
    }

    /**
     * Returns the trust manager to check a certificate with, first loading the trust store file
     * again when it has changed since it was last loaded, or the configuration has. A file that
     * cannot be loaded leaves the trust manager last loaded in use, and is not tried again until
     * it changes again.
     */
    private X509TrustManager currentTrustManager()
    {
      LoadedTrustManager current = loaded;
      if (current.isLoadedFrom(configurationChanges, stampFiles()))
      {
        return current.trustManager;
      }
      synchronized (this)
      {
        // stamped again under the lock: stamps taken before it may be those of a write another
        // thread has loaded past meanwhile
        final int changes = configurationChanges;
        final List<FileStamp> stamps = stampFiles();
        current = loaded;
        if (current.isLoadedFrom(changes, stamps))
        {
          return current.trustManager;
        }
        try
        {
          final TrustManager[] trustManagers = loadTrustManagers(currentPIN());
          // a plain trust manager handed out takes either kind, for the server may have turned to
          // FIPS mode since, which leaves out the expiration check, as getTrustManagers() does then
          if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)
              || extended && !(trustManagers[0] instanceof X509ExtendedTrustManager))
          {
            throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                ERR_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY.get(trustStoreFile, Arrays.toString(trustManagers)));
          }
          loaded = new LoadedTrustManager(changes, stamps, (X509TrustManager) trustManagers[0]);
          logger.info(NOTE_FILE_TRUSTMANAGER_RELOADED, trustStoreFile, currentConfig.dn());
        }
        catch (DirectoryException e)
        {
          logger.traceException(e);
          loaded = new LoadedTrustManager(changes, stamps, current.trustManager);
          logger.error(ERR_FILE_TRUSTMANAGER_CANNOT_RELOAD, trustStoreFile, currentConfig.dn(), e.getMessageObject());
        }
        return loaded.trustManager;
      }
    }
  }

  /** The trust manager handed out by {@link #getTrustManagers()} over a plain trust manager. */
  private final class ReloadingTrustManager implements X509TrustManager
  {
    private final TrustStoreFollower follower;

    private ReloadingTrustManager(TrustStoreFollower follower)
    {
      this.follower = follower;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
      follower.currentTrustManager().checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
      follower.currentTrustManager().checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
      return follower.currentTrustManager().getAcceptedIssuers();
    }
  }

  /** The trust manager handed out by {@link #getTrustManagers()} over an extended trust manager. */
  private final class ReloadingExtendedTrustManager extends X509ExtendedTrustManager
  {
    private final TrustStoreFollower follower;

    private ReloadingExtendedTrustManager(TrustStoreFollower follower)
    {
      this.follower = follower;
    }

    private X509ExtendedTrustManager current()
    {
      return (X509ExtendedTrustManager) follower.currentTrustManager();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
      current().checkClientTrusted(chain, authType);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException
    {
      current().checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException
    {
      current().checkClientTrusted(chain, authType, engine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
      current().checkServerTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException
    {
      current().checkServerTrusted(chain, authType, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException
    {
      current().checkServerTrusted(chain, authType, engine);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
      return current().getAcceptedIssuers();
    }
  }

  /**
   * Creates a new instance of this file-based trust manager provider.  The
   * <CODE>initializeTrustManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public FileBasedTrustManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeTrustManagerProvider(FileBasedTrustManagerProviderCfg cfg)
          throws ConfigException, InitializationException
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    currentConfig = cfg;
    trustStoreFile = getTrustStoreFile(cfg, ccr);
    trustStoreType = getTrustStoreType(cfg, ccr);
    getTrustStorePIN(cfg, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new InitializationException(ccr.getMessages().get(0));
    }

    cfg.addFileBasedChangeListener(this);
  }

  @Override
  public void finalizeTrustManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }

  /**
   * {@inheritDoc}
   * <p>
   * The trust manager returned reads the trust store file again when a certificate is checked
   * after the file, or the PIN file, has changed, so that a renewed trust store is used without
   * restarting the server or the component using it.
   */
  @Override
  public TrustManager[] getTrustManagers() throws DirectoryException
  {
    final int changes = configurationChanges;
    final List<FileStamp> stamps = stampFiles();
    final TrustManager[] trustManagers = loadTrustManagers(currentPIN());
    if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager))
    {
      return trustManagers;
    }
    final TrustStoreFollower follower =
        new TrustStoreFollower(new LoadedTrustManager(changes, stamps, (X509TrustManager) trustManagers[0]));
    // an extended trust manager stays one, and a plain one stays plain, for JSSE adds checks
    // of its own around a plain one
    return new TrustManager[] { follower.extended
        ? new ReloadingExtendedTrustManager(follower) : new ReloadingTrustManager(follower) };
  }

  /**
   * Returns the PIN the configuration names now, rather than the one it named when the provider
   * was configured: a PIN file may have been renewed since, together with the trust store.
   */
  private char[] currentPIN() throws DirectoryException
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    final char[] pin = getTrustStorePIN(currentConfig, ccr);
    if (ccr.getResultCode() != ResultCode.SUCCESS)
    {
      throw new DirectoryException(ccr.getResultCode(), ccr.getMessages().get(0));
    }
    return pin;
  }

  private List<FileStamp> stampFiles()
  {
    final String pinFile = currentConfig.getTrustStorePinFile();
    return FileStamp.of(getFileForPath(trustStoreFile), pinFile != null ? getFileForPath(pinFile) : null);
  }

  private TrustManager[] loadTrustManagers(char[] trustStorePIN) throws DirectoryException
  {
    KeyStore trustStore;
    try (FileInputStream inputStream = new FileInputStream(getFileForPath(trustStoreFile)))
    {
      trustStore = KeyStore.getInstance(trustStoreType);
      trustStore.load(inputStream, trustStorePIN);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_FILE_TRUSTMANAGER_CANNOT_LOAD.get(trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }

    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(trustStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      TrustManager[] newTrustManagers = new TrustManager[trustManagers.length];
      if (isFipsMode()) {
    	  newTrustManagers = trustManagers;
      } else {
	      for (int i=0; i < trustManagers.length; i++)
	      {
	        newTrustManagers[i] = new ExpirationCheckTrustManager((X509TrustManager) trustManagers[i]);
	      }
      }
      return newTrustManagers;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
              ERR_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY.get(trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  /**
   * Tells whether the server runs in FIPS mode, where the trust managers loaded are handed out as
   * they are, without an expiration check around them.
   *
   * @return {@code true} if the server runs in FIPS mode
   */
  boolean isFipsMode()
  {
    return isFips();
  }

  @Override
  public boolean isConfigurationAcceptable(TrustManagerProviderCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    FileBasedTrustManagerProviderCfg config = (FileBasedTrustManagerProviderCfg) cfg;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(FileBasedTrustManagerProviderCfg cfg,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    int startSize = unacceptableReasons.size();

    final ConfigChangeResult ccr = new ConfigChangeResult();
    getTrustStoreFile(cfg, ccr);
    getTrustStoreType(cfg, ccr);
    getTrustStorePIN(cfg, ccr);
    unacceptableReasons.addAll(ccr.getMessages());

    return startSize == unacceptableReasons.size();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedTrustManagerProviderCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    String newTrustStoreFile = getTrustStoreFile(cfg, ccr);
    String newTrustStoreType = getTrustStoreType(cfg, ccr);
    getTrustStorePIN(cfg, ccr);

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      synchronized (this)
      {
        currentConfig = cfg;
        trustStoreFile  = newTrustStoreFile;
        trustStoreType  = newTrustStoreType;
        // the trust managers already handed out load the trust store the new configuration
        // names on their next check, even where its files are those they were loaded from
        configurationChanges++;
      }
    }

    return ccr;
  }

  /** Get the path to the key store file. */
  private String getTrustStoreFile(FileBasedTrustManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    final String keyStoreFile = cfg.getTrustStoreFile();
    final File f = getFileForPath(keyStoreFile);
    if (!f.exists() || !f.isFile())
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_TRUSTMANAGER_NO_SUCH_FILE.get(keyStoreFile, cfg.dn()));
    }
    return keyStoreFile;
  }

  /** Get the keystore type. If none is specified, then use the default type. */
  private String getTrustStoreType(FileBasedTrustManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    final String trustStoreType = cfg.getTrustStoreType();
    if (trustStoreType != null)
    {
      if(trustStoreType.equals("BCFKS")) {
        FipsStaticUtils.registerBcProvider(true);
      }
      try
      {
        KeyStore.getInstance(trustStoreType);
        return trustStoreType;
      }
      catch (KeyStoreException e)
      {
        logger.traceException(e);
        ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_TRUSTMANAGER_INVALID_TYPE.get(trustStoreType, cfg.dn(), getExceptionMessage(e)));
      }
    }
    return KeyStore.getDefaultType();
  }

  private char[] getTrustStorePIN(FileBasedTrustManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      return getKeyStorePIN(cfg.getTrustStorePinProperty(),
                            cfg.getTrustStorePinEnvironmentVariable(),
                            cfg.getTrustStorePinFile(),
                            cfg.getTrustStorePin(),
                            cfg.dn(),
                            ERR_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET,
                            ERR_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET,
                            ERR_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE,
                            ERR_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ,
                            ERR_FILE_TRUSTMANAGER_PIN_FILE_EMPTY);
    }
    catch (InitializationException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
      return null;
    }
  }
}
