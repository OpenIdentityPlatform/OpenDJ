/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;

import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.AdminMessages.*;
import java.io.IOException;
import java.security.KeyStoreException;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.naming.ldap.Rdn;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.LDAPConnectionHandlerCfgDefn.
  SSLClientAuthPolicy;
import org.opends.server.admin.std.server.AdministrationConnectorCfg;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.KeyManagerProviderCfg;
import org.opends.server.admin.std.server.FileBasedKeyManagerProviderCfg;
import org.opends.server.admin.std.server.FileBasedTrustManagerProviderCfg;
import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.SynchronousStrategy;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.opends.server.types.AddressMask;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.CertificateManager;
import org.opends.server.admin.std.server.TrustManagerProviderCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.util.SetupUtils;

/**
 * This class is a wrapper on top of LDAPConnectionHandler to manage
 * the administration connector, which is an LDAPConnectionHandler with specific
 * (limited) configuration properties.
 */
public final class AdministrationConnector
  implements ConfigurationChangeListener<AdministrationConnectorCfg> {

  /**
   * Default Administration Connector port.
   */
  public static final int DEFAULT_ADMINISTRATION_CONNECTOR_PORT = 4444;

  /**
   * Validity (in days) of the generated certificate.
   */
  public static final int ADMIN_CERT_VALIDITY = 2 * 365;

 // Friendly name of the administration connector
  private static final String FRIENDLY_NAME = "Administration Connector";

  // The tracer object for the debug logger.
  private static final DebugTracer TRACER = getTracer();
  private LDAPConnectionHandler adminConnectionHandler;
  private AdministrationConnectorCfg config;  //
  // Predefined values for Administration Connector configuration
  //
  private static final String ADMIN_CLASS_NAME =
    "org.opends.server.protocols.ldap.LDAPConnectionHandler";
  private static final boolean ADMIN_ALLOW_LDAP_V2 = true;
  private static final boolean ADMIN_ALLOW_START_TLS = false;
  private static final SortedSet<AddressMask> ADMIN_ALLOWED_CLIENT =
    new TreeSet<AddressMask>();
  private static final SortedSet<AddressMask> ADMIN_DENIED_CLIENT =
    new TreeSet<AddressMask>();
  private static final boolean ADMIN_ENABLED = true;
  private static final boolean ADMIN_KEEP_STATS = true;
  private static final boolean ADMIN_USE_SSL = true;
  private static final int ADMIN_ACCEPT_BACKLOG = 128;
  private static final boolean ADMIN_ALLOW_TCP_REUSE_ADDRESS = true;
  private static final long ADMIN_MAX_BLOCKED_WRITE_TIME_LIMIT = 120000; // 2mn
  private static final int ADMIN_MAX_REQUEST_SIZE = 5000000; // 5 Mb
  private static final int ADMIN_NUM_REQUEST_HANDLERS = 1;
  private static final boolean ADMIN_SEND_REJECTION_NOTICE = true;
  private static final boolean ADMIN_USE_TCP_KEEP_ALIVE = true;
  private static final boolean ADMIN_USE_TCP_NO_DELAY = true;
  private static final SSLClientAuthPolicy ADMIN_SSL_CLIENT_AUTH_POLICY =
    SSLClientAuthPolicy.DISABLED;
  private static final SortedSet<String> ADMIN_SSL_CIPHER_SUITE =
    new TreeSet<String>();
  private static final SortedSet<String> ADMIN_SSL_PROTOCOL =
    new TreeSet<String>();

  /**
   * Initializes this administration connector provider based on the
   * information in the provided administration connector configuration.
   *
   * @param configuration
   *          The connection handler configuration that contains the
   *          information to use to initialize this connection
   *          handler.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeAdministrationConnector(
    AdministrationConnectorCfg configuration)
    throws ConfigException, InitializationException {

    this.config = configuration;

    // Create a fake LDAP connection handler configuration
    LDAPConnectionHandlerCfg ldapConnectionHandlerCfg =
      new FakeLDAPConnectionHandlerCfg(config);

    createSelfSignedCertifIfNeeded();

    // Administration Connector uses the LDAP connection handler implementation
    adminConnectionHandler =
      new LDAPConnectionHandler(new SynchronousStrategy(), FRIENDLY_NAME);
    adminConnectionHandler.
      initializeConnectionHandler(ldapConnectionHandlerCfg);

    // Register this as a change listener.
    config.addChangeListener(this);
  }

  /**
   * Create an instance of the administration connector.
   */
  public AdministrationConnector() {
    // Do nothing.
  }

  /**
   * Retrieves the connection handler linked to this administration connector.
   *
   * @return The connection handler linked to this administration connector.
   */
  public LDAPConnectionHandler getConnectionHandler() {
    return adminConnectionHandler;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
    AdministrationConnectorCfg configuration,
    List<Message> unacceptableReasons) {
    LDAPConnectionHandlerCfg cfg =
      new FakeLDAPConnectionHandlerCfg(configuration);
    return adminConnectionHandler.isConfigurationAcceptable(
      cfg, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
    AdministrationConnectorCfg configuration) {
    return new ConfigChangeResult(
      ResultCode.SUCCESS, true, new ArrayList<Message>());
  }

  /**
   * This private class implements a fake LDAP connection Handler configuration.
   * This allows to re-use the LDAPConnectionHandler as it is.
   *
   */
  private static class FakeLDAPConnectionHandlerCfg
    implements LDAPConnectionHandlerCfg {

    private final AdministrationConnectorCfg config;

    public FakeLDAPConnectionHandlerCfg(AdministrationConnectorCfg config) {
      this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    public Class<? extends LDAPConnectionHandlerCfg> configurationClass() {
      return LDAPConnectionHandlerCfg.class;
    }

    /**
     * {@inheritDoc}
     */
    public void addLDAPChangeListener(
      ConfigurationChangeListener<LDAPConnectionHandlerCfg> listener) {
      // do nothing. change listener already added.
    }

    /**
     * {@inheritDoc}
     */
    public void removeLDAPChangeListener(
      ConfigurationChangeListener<LDAPConnectionHandlerCfg> listener) {
      // do nothing. change listener already added.
    }

    /**
     * {@inheritDoc}
     */
    public int getAcceptBacklog() {
      return ADMIN_ACCEPT_BACKLOG;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAllowLDAPV2() {
      return ADMIN_ALLOW_LDAP_V2;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAllowStartTLS() {
      return ADMIN_ALLOW_START_TLS;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAllowTCPReuseAddress() {
      return ADMIN_ALLOW_TCP_REUSE_ADDRESS;
    }

    /**
     * {@inheritDoc}
     */
    public String getJavaClass() {
      return ADMIN_CLASS_NAME;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isKeepStats() {
      return ADMIN_KEEP_STATS;
    }

    /**
     * {@inheritDoc}
     */
    public String getKeyManagerProvider() {
      return config.getKeyManagerProvider();
    }

    /**
     * {@inheritDoc}
     */
    public DN getKeyManagerProviderDN() {
      return config.getKeyManagerProviderDN();
    }

    /**
     * {@inheritDoc}
     */
    public SortedSet<InetAddress> getListenAddress() {
      return config.getListenAddress();
    }

    /**
     * {@inheritDoc}
     */
    public int getListenPort() {
      return config.getListenPort();
    }

    /**
     * {@inheritDoc}
     */
    public long getMaxBlockedWriteTimeLimit() {
      return ADMIN_MAX_BLOCKED_WRITE_TIME_LIMIT;
    }

    /**
     * {@inheritDoc}
     */
    public long getMaxRequestSize() {
      return ADMIN_MAX_REQUEST_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    public int getNumRequestHandlers() {
      return ADMIN_NUM_REQUEST_HANDLERS;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSendRejectionNotice() {
      return ADMIN_SEND_REJECTION_NOTICE;
    }

    /**
     * {@inheritDoc}
     */
    public String getSSLCertNickname() {
      return config.getSSLCertNickname();
    }

    /**
     * {@inheritDoc}
     */
    public SortedSet<String> getSSLCipherSuite() {
      return ADMIN_SSL_CIPHER_SUITE;
    }

    /**
     * {@inheritDoc}
     */
    public SSLClientAuthPolicy getSSLClientAuthPolicy() {
      return ADMIN_SSL_CLIENT_AUTH_POLICY;
    }

    /**
     * {@inheritDoc}
     */
    public SortedSet<String> getSSLProtocol() {
      return ADMIN_SSL_PROTOCOL;
    }

    /**
     * {@inheritDoc}
     */
    public String getTrustManagerProvider() {
      return config.getTrustManagerProvider();
    }

    /**
     * {@inheritDoc}
     */
    public DN getTrustManagerProviderDN() {
      return config.getTrustManagerProviderDN();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isUseSSL() {
      return ADMIN_USE_SSL;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isUseTCPKeepAlive() {
      return ADMIN_USE_TCP_KEEP_ALIVE;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isUseTCPNoDelay() {
      return ADMIN_USE_TCP_NO_DELAY;
    }

    /**
     * {@inheritDoc}
     */
    public void addChangeListener(
      ConfigurationChangeListener<ConnectionHandlerCfg> listener) {
      // do nothing. change listener already added.
    }

    /**
     * {@inheritDoc}
     */
    public void removeChangeListener(
      ConfigurationChangeListener<ConnectionHandlerCfg> listener) {
      // do nothing. change listener already added.
    }

    /**
     * {@inheritDoc}
     */
    public SortedSet<AddressMask> getAllowedClient() {
      return ADMIN_ALLOWED_CLIENT;
    }

    /**
     * {@inheritDoc}
     */
    public SortedSet<AddressMask> getDeniedClient() {
      return ADMIN_DENIED_CLIENT;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEnabled() {
      return ADMIN_ENABLED;
    }

    /**
     * {@inheritDoc}
     */
    public DN dn() {
      return config.dn();
    }
  }

  /**
   * Creates a self-signed JKS certificate if needed.
   */
  private void createSelfSignedCertifIfNeeded()
    throws InitializationException {

    try {

      // Check if certificate generation is needed
      String certAlias = config.getSSLCertNickname();
      KeyManagerProviderCfg keyMgrConfig =
        getAdminConnectorKeyManagerConfig(config.getKeyManagerProvider());
      TrustManagerProviderCfg trustMgrConfig =
        getAdminConnectorTrustManagerConfig(config.getTrustManagerProvider());

      if (!(keyMgrConfig instanceof FileBasedKeyManagerProviderCfg) ||
        !(trustMgrConfig instanceof FileBasedTrustManagerProviderCfg)) {
        // The default config has been changed, nothing to do
        return;
      }

      FileBasedKeyManagerProviderCfg fbKeyManagerConfig =
        (FileBasedKeyManagerProviderCfg) keyMgrConfig;
      String keystorePath =
        getFullPath(fbKeyManagerConfig.getKeyStoreFile());
      FileBasedTrustManagerProviderCfg fbTrustManagerConfig =
        (FileBasedTrustManagerProviderCfg) trustMgrConfig;
      String truststorePath =
        getFullPath(fbTrustManagerConfig.getTrustStoreFile());
      String pinFilePath = getFullPath(fbKeyManagerConfig.getKeyStorePinFile());

      // Check that either we do not have any file,
      // or we have the 3 required files (keystore, truststore, pin file)
      boolean keystore = false;
      boolean truststore = false;
      boolean pinFile = false;
      int nbFiles = 0;
      if (new File(keystorePath).exists()) {
        keystore = true;
        nbFiles++;
      }
      if (new File(truststorePath).exists()) {
        truststore = true;
        nbFiles++;
      }
      if (new File(pinFilePath).exists()) {
        pinFile = true;
        nbFiles++;
      }
      if (nbFiles == 3) {
        // nothing to do
        return;
      }
      if (nbFiles != 0) {
        // 1 or 2 files are missing : error
        String err = "";
        if (!keystore) {
          err += keystorePath + " ";
        }
        if (!truststore) {
          err += truststorePath + " ";
        }
        if (!pinFile) {
          err += pinFilePath + " ";
        }
        Message message =
          ERR_ADMIN_CERTIFICATE_GENERATION_MISSING_FILES.get(err);
        logError(message);
        throw new InitializationException(message);
      }

      // Generate a password
      String pwd = new String(SetupUtils.createSelfSignedCertificatePwd());

      // Generate a self-signed certificate
      CertificateManager certManager = new CertificateManager(
        getFullPath(fbKeyManagerConfig.getKeyStoreFile()),
        fbKeyManagerConfig.getKeyStoreType(),
        pwd);
      String subjectDN = "cn=" +
        Rdn.escapeValue(InetAddress.getLocalHost().getHostName()) +
        ",O=" + FRIENDLY_NAME + " Self-Signed Certificate";
      certManager.generateSelfSignedCertificate(
        certAlias, subjectDN, ADMIN_CERT_VALIDITY);

      // Export the certificate
      String tempCertPath = getFullPath("config" + File.separator +
        "admin-cert.txt");
      SetupUtils.exportCertificate(certManager, certAlias,
        tempCertPath);

      // Create a new trust store and import the server certificate into it
      CertificateManager trustManager = new CertificateManager(
        truststorePath,
        CertificateManager.KEY_STORE_TYPE_JKS,
        pwd);
      trustManager.addCertificate(certAlias, new File(tempCertPath));

      // Generate a password file
      if (!new File(pinFilePath).exists()) {
        FileWriter file = new FileWriter(pinFilePath);
        PrintWriter out = new PrintWriter(file);
        out.println(pwd);
        out.flush();
        out.close();
        file.close();
      }

      // Change the password file permission if possible
      if (FilePermission.canSetPermissions()) {
        try {
          if (!FilePermission.setPermissions(new File(pinFilePath),
            new FilePermission(0600))) {
            // Log a warning that the permissions were not set.
            Message message =
              WARN_ADMIN_SET_PERMISSIONS_FAILED.get(pinFilePath);
            ErrorLogger.logError(message);
          }
        } catch (DirectoryException e) {
          // Log a warning that the permissions were not set.
          Message message = WARN_ADMIN_SET_PERMISSIONS_FAILED.get(pinFilePath);
          ErrorLogger.logError(message);
        }
      }

      // Delete the exported certificate
      File f = new File(tempCertPath);
      f.delete();

    } catch (ConfigException e) {
      handleCertifExceptions(e);
    } catch (KeyStoreException e) {
      handleCertifExceptions(e);
    } catch (IOException e) {
      handleCertifExceptions(e);
    } catch (CertificateEncodingException e) {
      handleCertifExceptions(e);
    }
  }

  private void handleCertifExceptions(Exception e) throws
    InitializationException {
    if (debugEnabled()) {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
    Message message = ERR_ADMIN_CERTIFICATE_GENERATION.get(e.getMessage());
    logError(message);
    throw new InitializationException(message);
  }

  private KeyManagerProviderCfg getAdminConnectorKeyManagerConfig(String name)
    throws ConfigException {
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
    return root.getKeyManagerProvider(name);
  }

  private TrustManagerProviderCfg getAdminConnectorTrustManagerConfig(
    String name)
    throws ConfigException {
    RootCfg root =
      ServerManagementContext.getInstance().getRootConfiguration();
    return root.getTrustManagerProvider(name);
  }

  private static String getFullPath(String path) {
    File file = new File(path);
    if (!file.isAbsolute()) {
      path = DirectoryServer.getInstanceRoot() + File.separator + path;
    }

    return path;
  }
}
