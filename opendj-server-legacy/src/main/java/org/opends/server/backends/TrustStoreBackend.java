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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;

import javax.naming.ldap.Rdn;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.server.TrustStoreBackendCfg;
import org.forgerock.util.Reject;
import org.opends.server.api.LocalBackend;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.FilePermission;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.Platform.KeyType;
import org.opends.server.util.SetupUtils;

/**
 * This class defines a backend used to provide an LDAP view of public keys
 * stored in a key store.
 */
public class TrustStoreBackend extends LocalBackend<TrustStoreBackendCfg>
       implements ConfigurationChangeListener<TrustStoreBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  /** The current configuration state. */
  private TrustStoreBackendCfg configuration;
  /** The set of base DNs for this backend. */
  private SortedSet<DN> baseDNs;
  /** The base entry. */
  private Entry baseEntry;

  /** The PIN needed to access the trust store backing file. */
  private char[] trustStorePIN;
  /** The path to the trust store backing file. */
  private String trustStoreFile;
  /** The type of trust store backing file to use. */
  private String trustStoreType;

  /** The certificate manager for the trust store. */
  private CertificateManager certificateManager;
  /** The server context. */
  private ServerContext serverContext;

  /**
   * Creates a new backend.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public TrustStoreBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }

  private DN getBaseDN()
  {
    return baseDNs.first();
  }

  @Override
  public void configureBackend(TrustStoreBackendCfg config, ServerContext serverContext) throws ConfigException
  {
    this.serverContext = serverContext;
    Reject.ifNull(config);
    configuration = config;
  }

  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    DN configEntryDN = configuration.dn();

    // Create the set of base DNs that we will handle.  In this case, it's just
    // the DN of the base trust store entry.
    SortedSet<DN> baseDNSet = configuration.getBaseDN();
    if (baseDNSet.size() != 1)
    {
      throw new InitializationException(ERR_TRUSTSTORE_REQUIRES_ONE_BASE_DN.get(configEntryDN));
    }
    baseDNs = baseDNSet;

    // Get the path to the trust store file.
    trustStoreFile = configuration.getTrustStoreFile();

    // Get the trust store type. If none is specified, then use the default type.
    trustStoreType = configuration.getTrustStoreType();
    if (trustStoreType == null)
    {
      trustStoreType = KeyStore.getDefaultType();
    }

    try
    {
      KeyStore.getInstance(trustStoreType);
    }
    catch (KeyStoreException kse)
    {
      logger.traceException(kse);
      throw new InitializationException(ERR_TRUSTSTORE_INVALID_TYPE.get(
          trustStoreType, configEntryDN, getExceptionMessage(kse)));
    }

    trustStorePIN = getTrustStorePIN(configuration, true);
    final String keyStorePath = getFileForPath(trustStoreFile).getPath();
    certificateManager = new CertificateManager(keyStorePath, trustStoreType, trustStorePIN);

    // Generate a self-signed certificate, if there is none.
    generateInstanceCertificateIfAbsent();

    // Construct the trust store base entry.
    LinkedHashMap<ObjectClass,String> objectClasses = new LinkedHashMap<>(2);
    objectClasses.put(CoreSchema.getTopObjectClass(), OC_TOP);
    objectClasses.put(serverContext.getSchema().getObjectClass("ds-cfg-branch"), "ds-cfg-branch");

    LinkedHashMap<AttributeType,List<Attribute>> userAttrs = new LinkedHashMap<>(1);
    for (AVA ava : getBaseDN().rdn())
    {
      AttributeType attrType = ava.getAttributeType();
      userAttrs.put(attrType, Attributes.createAsList(attrType, ava.getAttributeValue()));
    }

    baseEntry = new Entry(getBaseDN(), objectClasses, userAttrs, null);

    // Register this as a change listener.
    configuration.addTrustStoreChangeListener(this);

    // Register the trust store base as a private suffix.
    try
    {
      serverContext.getBackendConfigManager().registerBaseDN(getBaseDN(), this, true);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new InitializationException(ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(getBaseDN(), e), e);
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
  private static char[] getTrustStorePIN(TrustStoreBackendCfg cfg, boolean createPinFileIfNeeded)
          throws InitializationException {
    final String pinProperty = cfg.getTrustStorePinProperty();
    if (pinProperty != null)
    {
        final String pin = System.getProperty(pinProperty);
        if (pin == null)
        {
            throw new InitializationException(ERR_TRUSTSTORE_PIN_PROPERTY_NOT_SET.get(pinProperty, cfg.dn()));
        }
        return pin.toCharArray();
    }

    final String pinEnvVar = cfg.getTrustStorePinEnvironmentVariable();
    if (pinEnvVar != null)
    {
        final String pin = System.getenv(pinEnvVar);
        if (pin == null)
        {
            throw new InitializationException(ERR_TRUSTSTORE_PIN_ENVAR_NOT_SET.get(pinEnvVar, cfg.dn()));
        }
        return pin.toCharArray();
    }

    final String pinFileName = cfg.getTrustStorePinFile();
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
            final LocalizableMessage msg = ERR_TRUSTSTORE_PIN_FILE_CANNOT_READ.get(pinFileName,
                                                                                   cfg.dn(), getExceptionMessage(e));
            throw new InitializationException(msg, e);
        }
        if (pin == null)
        {
            throw new InitializationException(ERR_TRUSTSTORE_PIN_FILE_EMPTY.get(pinFileName, cfg.dn()));
        }
        return pin.toCharArray();
      }
      else if (createPinFileIfNeeded)
      {
        try
        {
          // Generate and store the PIN in the pin file.
          final char[] trustStorePIN1 = createKeystorePassword();
          createPINFile(pinFile.getPath(), new String(trustStorePIN1));
          return trustStorePIN1;
        }
        catch (Exception e)
        {
          throw new InitializationException(ERR_TRUSTSTORE_PIN_FILE_CANNOT_CREATE.get(pinFileName, cfg.dn()));
        }
      }
      else
      {
        return null;
      }
    }
    return cfg.getTrustStorePin() != null ? cfg.getTrustStorePin().toCharArray() : null;
  }

  @Override
  public void closeBackend()
  {
    configuration.addTrustStoreChangeListener(this);

    try
    {
      serverContext.getBackendConfigManager().deregisterBaseDN(getBaseDN());
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }

  @Override
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

  @Override
  public long getEntryCount()
  {
    int numEntries = 1;

    try
    {
      String[] aliases = certificateManager.getCertificateAliases();
      if (aliases != null)
      {
        numEntries += aliases.length;
      }
    }
    catch (KeyStoreException e)
    {
      logger.traceException(e);
    }

    return numEntries;
  }

  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
          ERR_BACKEND_GET_ENTRY_NULL.get(getBackendID()));
    }

    // If the requested entry was the backend base entry, then retrieve it.
    if (entryDN.equals(getBaseDN()))
    {
      return baseEntry.duplicate(true);
    }

    // See if the requested entry was one level below the backend base entry.
    // If so, then it must point to a trust store entry.
    DN parentDN = serverContext.getBackendConfigManager().getParentDNInSuffix(entryDN);
    if (parentDN != null && parentDN.equals(getBaseDN()))
    {
      try
      {
        return getCertEntry(entryDN);
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);
      }
    }
    return null;
  }

  /**
   * Generates an entry for a certificate based on the provided DN.  The
   * DN must contain an RDN component that specifies the alias of the
   * certificate, and that certificate alias must exist in the key store.
   *
   * @param  entryDN  The DN of the certificate to retrieve.
   *
   * @return  The requested certificate entry.
   *
   * @throws  DirectoryException  If the specified alias does not exist, or if
   *                              the DN does not specify any alias.
   */
  private Entry getCertEntry(DN entryDN)
         throws DirectoryException
  {
    // Make sure that the DN specifies a certificate alias.
    AttributeType t = serverContext.getSchema().getAttributeType(ATTR_CRYPTO_KEY_ID);
    ByteString v = entryDN.rdn().getAttributeValue(t);
    if (v == null)
    {
      LocalizableMessage message = ERR_TRUSTSTORE_DN_DOES_NOT_SPECIFY_CERTIFICATE.get(entryDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message, getBaseDN(), null);
    }

    String certAlias = v.toString();
    ByteString certValue;
    try
    {
      Certificate cert = certificateManager.getCertificate(certAlias);
      if (cert == null)
      {
        LocalizableMessage message = ERR_TRUSTSTORE_CERTIFICATE_NOT_FOUND.get(entryDN, certAlias);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }
      certValue = ByteString.wrap(cert.getEncoded());
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_RETRIEVE_CERT.get(
          certAlias, trustStoreFile, e.getMessage());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    // Construct the certificate entry to return.
    LinkedHashMap<ObjectClass,String> ocMap = new LinkedHashMap<>(2);
    ocMap.put(CoreSchema.getTopObjectClass(), OC_TOP);
    ocMap.put(serverContext.getSchema().getObjectClass(OC_CRYPTO_INSTANCE_KEY), OC_CRYPTO_INSTANCE_KEY);

    LinkedHashMap<AttributeType,List<Attribute>> opAttrs = new LinkedHashMap<>(0);
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs = new LinkedHashMap<>(3);

    userAttrs.put(t, Attributes.createAsList(t, v));

    t = serverContext.getSchema().getAttributeType(ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE);
    AttributeBuilder builder = new AttributeBuilder(t);
    builder.setOption("binary");
    builder.add(certValue);
    userAttrs.put(t, builder.toAttributeList());

    Entry e = new Entry(entryDN, ocMap, userAttrs, opAttrs);
    e.processVirtualAttributes();
    return e;
  }

  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    DN entryDN = entry.getName();

    if (entryDN.equals(getBaseDN()))
    {
      LocalizableMessage message = ERR_TRUSTSTORE_INVALID_BASE.get(entryDN);
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
    }

    DN parentDN = serverContext.getBackendConfigManager().getParentDNInSuffix(entryDN);
    if (parentDN == null)
    {
      LocalizableMessage message = ERR_TRUSTSTORE_INVALID_BASE.get(entryDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    if (parentDN.equals(getBaseDN()))
    {
      addCertificate(entry);
    }
    else
    {
      LocalizableMessage message = ERR_TRUSTSTORE_INVALID_BASE.get(entryDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }
  }

  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    if (entryDN.equals(getBaseDN()))
    {
      LocalizableMessage message = ERR_TRUSTSTORE_INVALID_BASE.get(entryDN);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    DN parentDN = serverContext.getBackendConfigManager().getParentDNInSuffix(entryDN);
    if (parentDN == null || !parentDN.equals(getBaseDN()))
    {
      LocalizableMessage message = ERR_TRUSTSTORE_INVALID_BASE.get(entryDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    deleteCertificate(entryDN);
  }

  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_NOT_SUPPORTED.get(oldEntry.getName(), getBackendID()));
  }

  @Override
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(currentDN, getBackendID()));
  }

  @Override
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    // Get the base entry for the search, if possible.  If it doesn't exist,
    // then this will throw an exception.
    DN    baseDN    = searchOperation.getBaseDN();
    Entry baseEntry = getEntry(baseDN);

    // Look at the base DN and see if it's the trust store base DN, or a
    // trust store entry DN.
    SearchScope  scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();
    if (getBaseDN().equals(baseDN))
    {
      if ((scope == SearchScope.BASE_OBJECT || scope == SearchScope.WHOLE_SUBTREE)
          && filter.matchesEntry(baseEntry))
      {
        searchOperation.returnEntry(baseEntry, null);
      }

      String[] aliases = null;
      try
      {
        aliases = certificateManager.getCertificateAliases();
      }
      catch (KeyStoreException e)
      {
        logger.traceException(e);
      }

      if (aliases == null)
      {
        aliases = new String[0];
      }

      if (scope != SearchScope.BASE_OBJECT && aliases.length != 0)
      {
        AttributeType certAliasType = serverContext.getSchema().getAttributeType(ATTR_CRYPTO_KEY_ID);
        for (String alias : aliases)
        {
          DN certDN = makeChildDN(getBaseDN(), certAliasType, alias);

          Entry certEntry;
          try
          {
            certEntry = getCertEntry(certDN);
          }
          catch (Exception e)
          {
            logger.traceException(e);
            continue;
          }

          if (filter.matchesEntry(certEntry))
          {
            searchOperation.returnEntry(certEntry, null);
          }
        }
      }
    }
    else if (getBaseDN().equals(serverContext.getBackendConfigManager().getParentDNInSuffix(baseDN)))
    {
      Entry certEntry = getCertEntry(baseDN);

      if ((scope == SearchScope.BASE_OBJECT || scope == SearchScope.WHOLE_SUBTREE)
          && filter.matchesEntry(certEntry))
      {
        searchOperation.returnEntry(certEntry, null);
      }
    }
    else
    {
      LocalizableMessage message = ERR_TRUSTSTORE_INVALID_BASE.get(baseDN);
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }
  }

  @Override
  public Set<String> getSupportedControls()
  {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    return false;
  }

  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void createBackup(BackupConfig backupConfig)
       throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public ConditionResult hasSubordinates(DN entryDN)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_HAS_SUBORDINATES_NOT_SUPPORTED.get());
  }

  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    checkNotNull(baseDN, "baseDN must not be null");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    checkNotNull(parentDN, "parentDN must not be null");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
       TrustStoreBackendCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    final DN cfgEntryDN = configuration.dn();

    // Get the path to the trust store file.
    String newTrustStoreFile = configuration.getTrustStoreFile();
    try
    {
      File f = getFileForPath(newTrustStoreFile);
      if (!f.exists() || !f.isFile())
      {
        unacceptableReasons.add(ERR_TRUSTSTORE_NO_SUCH_FILE.get(newTrustStoreFile, cfgEntryDN));
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      unacceptableReasons.add(ERR_TRUSTSTORE_CANNOT_DETERMINE_FILE.get(cfgEntryDN, getExceptionMessage(e)));
    }

    // Check to see if the trust store type is acceptable.
    String storeType = configuration.getTrustStoreType();
    if (storeType != null)
    {
      try
      {
        KeyStore.getInstance(storeType);
      }
      catch (KeyStoreException kse)
      {
        logger.traceException(kse);

        unacceptableReasons.add(ERR_TRUSTSTORE_INVALID_TYPE.get(storeType, cfgEntryDN, getExceptionMessage(kse)));
      }
    }

    try
    {
      getTrustStorePIN(configuration, false);
    }
    catch (InitializationException e)
    {
      unacceptableReasons.add(e.getMessageObject());
    }

    return unacceptableReasons.isEmpty();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(TrustStoreBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    DN configEntryDN = cfg.dn();

    // Get the path to the trust store file.
    String newTrustStoreFile = cfg.getTrustStoreFile();
    File f = getFileForPath(newTrustStoreFile);
    if (!f.exists() || !f.isFile())
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_TRUSTSTORE_NO_SUCH_FILE.get(newTrustStoreFile, configEntryDN));
    }

    // Get the trust store type.  If none is specified, then use the default
    // type.
    String newTrustStoreType = cfg.getTrustStoreType();
    if (newTrustStoreType == null)
    {
      newTrustStoreType = KeyStore.getDefaultType();
    }

    try
    {
      KeyStore.getInstance(newTrustStoreType);
    }
    catch (KeyStoreException kse)
    {
      logger.traceException(kse);

      ccr.addMessage(ERR_TRUSTSTORE_INVALID_TYPE.get(newTrustStoreType, configEntryDN, getExceptionMessage(kse)));
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
    }

    char[] newPIN = null;
    try
    {
      newPIN = getTrustStorePIN(cfg, true);
    }
    catch (InitializationException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      trustStoreFile = newTrustStoreFile;
      trustStoreType = newTrustStoreType;
      trustStorePIN  = newPIN;
      configuration  = cfg;
      final String keyStorePath = getFileForPath(trustStoreFile).getPath();
      certificateManager = new CertificateManager(keyStorePath, trustStoreType, trustStorePIN);
    }

    return ccr;
  }

  private static DN makeChildDN(DN parentDN, AttributeType rdnAttrType, String rdnStringValue)
  {
    ByteString attrValue = ByteString.valueOfUtf8(rdnStringValue);
    return parentDN.child(new RDN(rdnAttrType, attrValue));
  }

  /**
   * Retrieves a set of <CODE>KeyManager</CODE> objects that may be used for
   * interactions requiring access to a key manager.
   *
   * @return  A set of <CODE>KeyManager</CODE> objects that may be used for
   *          interactions requiring access to a key manager.
   *
   * @throws DirectoryException  If a problem occurs while attempting to obtain
   *                             the set of key managers.
   */
  public KeyManager[] getKeyManagers()
         throws DirectoryException
  {
    final KeyStore keyStore = loadKeyStore();

    try
    {
      String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
      KeyManagerFactory keyManagerFactory =
           KeyManagerFactory.getInstance(keyManagerAlgorithm);
      keyManagerFactory.init(keyStore, trustStorePIN);
      return keyManagerFactory.getKeyManagers();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_CREATE_FACTORY.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                                   message, e);
    }
  }

  private KeyStore loadKeyStore() throws DirectoryException
  {
    try (FileInputStream inputStream = new FileInputStream(getFileForPath(trustStoreFile)))
    {
      final KeyStore keyStore = KeyStore.getInstance(trustStoreType);
      keyStore.load(inputStream, trustStorePIN);
      return keyStore;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_LOAD.get(trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  /**
   * Retrieves a set of {@code TrustManager} objects that may be used
   * for interactions requiring access to a trust manager.
   *
   * @return  A set of {@code TrustManager} objects that may be used
   *          for interactions requiring access to a trust manager.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to obtain the set of trust managers.
   */
  public TrustManager[] getTrustManagers()
         throws DirectoryException
  {
    KeyStore trustStore = loadKeyStore();

    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory =
           TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(trustStore);
      return trustManagerFactory.getTrustManagers();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_CREATE_FACTORY.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                                   message, e);
    }
  }

  /**
   * Returns the key associated with the given alias, using the trust
   * store pin to recover it.
   *
   * @param   alias The alias name.
   *
   * @return  The requested key, or null if the given alias does not exist
   *          or does not identify a key-related entry.
   *
   * @throws  DirectoryException  If an error occurs while retrieving the key.
   */
  public Key getKey(String alias)
         throws DirectoryException
  {
    KeyStore trustStore = loadKeyStore();

    try
    {
      return trustStore.getKey(alias, trustStorePIN);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_TRUSTSTORE_ERROR_READING_KEY.get(
           alias, trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                                   message, e);
    }
  }

  private void addCertificate(Entry entry)
       throws DirectoryException
  {
    DN entryDN = entry.getName();

    // Make sure that the DN specifies a certificate alias.
    AttributeType t = serverContext.getSchema().getAttributeType(ATTR_CRYPTO_KEY_ID);
    ByteString v = entryDN.rdn().getAttributeValue(t);
    if (v == null)
    {
      LocalizableMessage message = ERR_TRUSTSTORE_DN_DOES_NOT_SPECIFY_CERTIFICATE.get(entryDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message, getBaseDN(), null);
    }

    String certAlias = v.toString();
    try
    {
      if (certificateManager.aliasInUse(certAlias))
      {
        LocalizableMessage message = ERR_TRUSTSTORE_ALIAS_IN_USE.get(entryDN);
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
      }

      if (entry.hasObjectClass(serverContext.getSchema().getObjectClass(OC_SELF_SIGNED_CERT_REQUEST)))
      {
        try
        {
          final KeyType keyType = KeyType.getTypeOrDefault(certAlias);
          certificateManager.generateSelfSignedCertificate(
             keyType,
             certAlias,
             getADSCertificateSubjectDN(keyType),
             getADSCertificateValidity());
        }
        catch (Exception e)
        {
          LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_GENERATE_CERT.get(
              certAlias, trustStoreFile, getExceptionMessage(e));
          throw new DirectoryException(
               DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
        }
      }
      else
      {
        Iterator<Attribute> certAttrs = entry.getAllAttributes(ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE).iterator();
        if (!certAttrs.hasNext())
        {
          LocalizableMessage message =
               ERR_TRUSTSTORE_ENTRY_MISSING_CERT_ATTR.get(entryDN, ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message);
        }
        Attribute certAttr = certAttrs.next();
        if (certAttrs.hasNext())
        {
          LocalizableMessage message =
               ERR_TRUSTSTORE_ENTRY_HAS_MULTIPLE_CERT_ATTRS.get(entryDN, ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message);
        }

        Iterator<ByteString> i = certAttr.iterator();

        if (!i.hasNext())
        {
          LocalizableMessage message =
               ERR_TRUSTSTORE_ENTRY_MISSING_CERT_VALUE.get(entryDN, ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message);
        }

        ByteString certBytes = i.next();

        if (i.hasNext())
        {
          LocalizableMessage message =
               ERR_TRUSTSTORE_ENTRY_HAS_MULTIPLE_CERT_VALUES.get(entryDN, ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message);
        }

        try
        {
          File tempDir = getFileForPath("config");
          File tempFile = File.createTempFile(configuration.getBackendId(),
                                              certAlias, tempDir);
          try (FileOutputStream outputStream = new FileOutputStream(tempFile.getPath(), false))
          {
            certBytes.copyTo(outputStream);
            certificateManager.addCertificate(certAlias, tempFile);
          }
          finally
          {
            tempFile.delete();
          }
        }
        catch (IOException e)
        {
          LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_WRITE_CERT.get(
              certAlias, getExceptionMessage(e));
          throw new DirectoryException(
               DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
        }
      }
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_ADD_CERT.get(
           certAlias, trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(
           DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  private void deleteCertificate(DN entryDN)
       throws DirectoryException
  {
    // Make sure that the DN specifies a certificate alias.
    AttributeType t = serverContext.getSchema().getAttributeType(ATTR_CRYPTO_KEY_ID);
    ByteString v = entryDN.rdn().getAttributeValue(t);
    if (v == null)
    {
      LocalizableMessage message = ERR_TRUSTSTORE_DN_DOES_NOT_SPECIFY_CERTIFICATE.get(entryDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message, getBaseDN(), null);
    }

    String certAlias = v.toString();
    try
    {
      if (!certificateManager.aliasInUse(certAlias))
      {
        LocalizableMessage message = ERR_TRUSTSTORE_INVALID_BASE.get(entryDN);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }

      certificateManager.removeCertificate(certAlias);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_TRUSTSTORE_CANNOT_DELETE_CERT.get(
           certAlias, trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(
           DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  /**
   * Returns the validity period to be used to generate the ADS certificate.
   * @return The validity period to be used to generate the ADS certificate.
   */
  private static int getADSCertificateValidity()
  {
    return 20 * 365;
  }

  /**
   * Returns the Subject DN to be used to generate the ADS certificate.
   * @return The Subject DN to be used to generate the ADS certificate.
   * @throws java.net.UnknownHostException If the server host name could not be
   *                                       determined.
   */
  private static String getADSCertificateSubjectDN(KeyType keyType) throws UnknownHostException
  {
    final String hostName = SetupUtils.getHostNameForCertificate(DirectoryServer.getServerRoot());
    return "cn=" + Rdn.escapeValue(hostName) + ",O=OpenDJ " + keyType + " Certificate";
  }

  /**
   * Create a randomly generated password for a certificate keystore.
   * @return A randomly generated password for a certificate keystore.
   */
  private static char[] createKeystorePassword() {
    int pwdLength = 50;
    char[] pwd = new char[pwdLength];
    Random random = new Random();
    for (int pos=0; pos < pwdLength; pos++) {
        int type = getRandomInt(random,3);
        char nextChar = getRandomChar(random,type);
        pwd[pos] = nextChar;
    }
    return pwd;
  }

  private static char getRandomChar(Random random, int type)
  {
    char generatedChar;
    int next = random.nextInt();
    int d;

    switch (type)
    {
    case 0:
      // Will return a digit
      d = next % 10;
      if (d < 0)
      {
        d = d * -1;
      }
      generatedChar = (char) (d+48);
      break;
    case 1:
      // Will return a lower case letter
      d = next % 26;
      if (d < 0)
      {
        d = d * -1;
      }
      generatedChar =  (char) (d + 97);
      break;
    default:
      // Will return a capital letter
      d = next % 26;
      if (d < 0)
      {
        d = d * -1;
      }
      generatedChar = (char) (d + 65) ;
    }

    return generatedChar;
  }

  private static int getRandomInt(Random random,int modulo)
  {
    return random.nextInt() & modulo;
  }

  private static void createPINFile(String path, String pin)
       throws IOException
  {
    try (final FileWriter file = new FileWriter(path);
         final PrintWriter out = new PrintWriter(file))
    {
      out.println(pin);
      out.flush();
    }

    try {
      if (!FilePermission.setPermissions(new File(path), new FilePermission(0600)))
      {
        // Log a warning that the permissions were not set.
        logger.warn(WARN_TRUSTSTORE_SET_PERMISSIONS_FAILED, path);
      }
    } catch(DirectoryException e) {
      // Log a warning that the permissions were not set.
      logger.warn(WARN_TRUSTSTORE_SET_PERMISSIONS_FAILED, path);
    }
  }

  /**
   * Generates a self-signed certificate with well-known alias if there is none.
   * @throws InitializationException If an error occurs while interacting with
   *                                 the key store.
   */
  private void generateInstanceCertificateIfAbsent() throws InitializationException
  {
    final String certAlias = ADS_CERTIFICATE_ALIAS;
    try
    {
      if (certificateManager.aliasInUse(certAlias))
      {
        return;
      }
    }
    catch (Exception e)
    {
      LocalizableMessage message =
          ERR_TRUSTSTORE_CANNOT_ADD_CERT.get(certAlias, trustStoreFile, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }

    try
    {
      final KeyType keyType = KeyType.getTypeOrDefault(certAlias);
      certificateManager.generateSelfSignedCertificate(keyType, certAlias, getADSCertificateSubjectDN(keyType),
          getADSCertificateValidity());
    }
    catch (Exception e)
    {
      LocalizableMessage message =
          ERR_TRUSTSTORE_CANNOT_GENERATE_CERT.get(certAlias, trustStoreFile, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }
  }
}
