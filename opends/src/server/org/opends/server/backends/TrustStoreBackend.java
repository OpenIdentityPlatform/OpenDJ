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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends;



import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.util.*;
import java.security.KeyStore;
import java.security.KeyStoreException;

import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.util.Validator;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.ExpirationCheckTrustManager;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.TrustStoreBackendCfg;
import org.opends.server.admin.Configuration;
import org.opends.messages.Message;
import static org.opends.messages.BackendMessages.*;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.naming.ldap.Rdn;
import java.security.cert.Certificate;
import java.net.UnknownHostException;


/**
 * This class defines a backend used to provide an LDAP view of public keys
 * stored in a key store.
 */
public class TrustStoreBackend
     extends Backend
       implements ConfigurationChangeListener<TrustStoreBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The current configuration state.
  private TrustStoreBackendCfg configuration;

  // The DN for the base entry.
  private DN baseDN;

  // The set of base DNs for this backend.
  private DN[] baseDNs;

  // The base entry.
  private Entry baseEntry;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  // The PIN needed to access the trust store backing file.
  private char[] trustStorePIN;

  // The path to the trust store backing file.
  private String trustStoreFile;

  // The type of trust store backing file to use.
  private String trustStoreType;

  // The certificate manager for the trust store.
  private CertificateManager certificateManager;


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


  /**
   * {@inheritDoc}
   */
  public void configureBackend(Configuration config) throws ConfigException
  {
    Validator.ensureNotNull(config);
    Validator.ensureTrue(config instanceof TrustStoreBackendCfg);

    configuration = (TrustStoreBackendCfg)config;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeBackend()
         throws ConfigException, InitializationException
  {
    DN configEntryDN = configuration.dn();

    // Create the set of base DNs that we will handle.  In this case, it's just
    // the DN of the base trust store entry.
    SortedSet<DN> baseDNSet = configuration.getBackendBaseDN();
    if (baseDNSet.size() != 1)
    {
      Message message = ERR_TRUSTSTORE_REQUIRES_ONE_BASE_DN.get(
           String.valueOf(configEntryDN));
      throw new InitializationException(message);
    }
    baseDN = baseDNSet.first();
    baseDNs = new DN[] {baseDN};

    // Get the path to the trust store file.
    trustStoreFile = configuration.getTrustStoreFile();


    // Get the trust store type.  If none is specified, then use the default
    // type.
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, kse);
      }

      Message message = ERR_TRUSTSTORE_INVALID_TYPE.
          get(String.valueOf(trustStoreType), String.valueOf(configEntryDN),
              getExceptionMessage(kse));
      throw new InitializationException(message);
    }


    // Get the PIN needed to access the contents of the trust store file.  We
    // will offer several places to look for the PIN, and we will do so in the
    // following order:
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    // In any case, the PIN must be in the clear.  If no PIN is provided, then
    // it will be assumed that none is required to access the information in the
    // trust store.
    String pinProperty = configuration.getTrustStorePinProperty();
    if (pinProperty == null)
    {
      String pinEnVar = configuration.getTrustStorePinEnvironmentVariable();
      if (pinEnVar == null)
      {
        String pinFilePath = configuration.getTrustStorePinFile();
        if (pinFilePath == null)
        {
          String pinStr = configuration.getTrustStorePin();
          if (pinStr == null)
          {
            trustStorePIN = null;
          }
          else
          {
            trustStorePIN = pinStr.toCharArray();
          }
        }
        else
        {
          File pinFile = getFileForPath(pinFilePath);
          if (! pinFile.exists())
          {
            try
            {
              // Generate a PIN.
              trustStorePIN = createKeystorePassword();

              // Store the PIN in the pin file.
              createPINFile(pinFile.getPath(), new String(trustStorePIN));
            }
            catch (Exception e)
            {
              Message message = ERR_TRUSTSTORE_PIN_FILE_CANNOT_CREATE.get(
                   String.valueOf(pinFilePath), String.valueOf(configEntryDN));
              throw new InitializationException(message);
            }
          }
          else
          {
            String pinStr;

            BufferedReader br = null;
            try
            {
              br = new BufferedReader(new FileReader(pinFile));
              pinStr = br.readLine();
            }
            catch (IOException ioe)
            {
              Message message = ERR_TRUSTSTORE_PIN_FILE_CANNOT_READ.
                  get(String.valueOf(pinFilePath),
                      String.valueOf(configEntryDN), getExceptionMessage(ioe));
              throw new InitializationException(message, ioe);
            }
            finally
            {
              try
              {
                br.close();
              } catch (Exception e) {
                // ignore
              }
            }

            if (pinStr == null)
            {
              Message message = ERR_TRUSTSTORE_PIN_FILE_EMPTY.get(
                  String.valueOf(pinFilePath), String.valueOf(configEntryDN));
              throw new InitializationException(message);
            }
            else
            {
              trustStorePIN     = pinStr.toCharArray();
            }
          }
        }
      }
      else
      {
        String pinStr = System.getenv(pinEnVar);
        if (pinStr == null)
        {
          Message message = ERR_TRUSTSTORE_PIN_ENVAR_NOT_SET.get(
              String.valueOf(pinProperty), String.valueOf(configEntryDN));
          throw new InitializationException(message);
        }
        else
        {
          trustStorePIN = pinStr.toCharArray();
        }
      }
    }
    else
    {
      String pinStr = System.getProperty(pinProperty);
      if (pinStr == null)
      {
        Message message = ERR_TRUSTSTORE_PIN_PROPERTY_NOT_SET.get(
            String.valueOf(pinProperty), String.valueOf(configEntryDN));
        throw new InitializationException(message);
      }
      else
      {
        trustStorePIN = pinStr.toCharArray();
      }
    }

    // Create a certificate manager.
    certificateManager =
         new CertificateManager(getFileForPath(trustStoreFile).getPath(),
                                trustStoreType,
                                new String(trustStorePIN));

    // Construct the trust store base entry.
    LinkedHashMap<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>(2);
    objectClasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass branchOC =
         DirectoryServer.getObjectClass("ds-cfg-branch", true);
    objectClasses.put(branchOC, "ds-cfg-branch");

    LinkedHashMap<AttributeType,List<Attribute>> opAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(0);
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(1);

    RDN rdn = baseDN.getRDN();
    int numAVAs = rdn.getNumValues();
    for (int i=0; i < numAVAs; i++)
    {
      LinkedHashSet<AttributeValue> valueSet =
           new LinkedHashSet<AttributeValue>(1);
      valueSet.add(rdn.getAttributeValue(i));

      AttributeType attrType = rdn.getAttributeType(i);
      ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(new Attribute(attrType, attrType.getNameOrOID(),
                                 valueSet));

      userAttrs.put(attrType, attrList);
    }

    baseEntry = new Entry(baseDN, objectClasses, userAttrs,
                                opAttrs);


    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);


    // Register this as a change listener.
    configuration.addTrustStoreChangeListener(this);


    // Register the trust store base as a private suffix.
    try
    {
      DirectoryServer.registerBaseDN(baseDN, this, true, false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          String.valueOf(baseDN), String.valueOf(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeBackend()
  {
    configuration.addTrustStoreChangeListener(this);

    try
    {
      DirectoryServer.deregisterBaseDN(baseDN, false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return numEntries;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isLocal()
  {
    // For the purposes of this method, this is a local backend.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      Message message = ERR_TRUSTSTORE_GET_ENTRY_NULL.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }


    // If the requested entry was the backend base entry, then retrieve it.
    if (entryDN.equals(baseDN))
    {
      return baseEntry.duplicate(true);
    }


    // See if the requested entry was one level below the backend base entry.
    // If so, then it must point to a trust store entry.
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      return null;
    }
    else if (parentDN.equals(baseDN))
    {
      try
      {
        return getCertEntry(entryDN);
      }
      catch (DirectoryException e)
      {
        return null;
      }
    }
    else
    {
      return null;
    }
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
    AttributeType t =
         DirectoryServer.getAttributeType(ATTR_CERT_ALIAS, true);
    AttributeValue v = entryDN.getRDN().getAttributeValue(t);
    if (v == null)
    {
      Message message = ERR_TRUSTSTORE_DN_DOES_NOT_SPECIFY_CERTIFICATE.
           get(String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   baseDN, null);
    }

    String certAlias = v.getStringValue();
    ByteString certValue;
    try
    {
      Certificate cert = certificateManager.getCertificate(certAlias);
      if (cert == null)
      {
        Message message = ERR_TRUSTSTORE_CERTIFICATE_NOT_FOUND.get(
            String.valueOf(entryDN), certAlias);
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
      }
      certValue = new ASN1OctetString(cert.getEncoded());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_TRUSTSTORE_CANNOT_RETRIEVE_CERT.get(
          certAlias, trustStoreFile, e.getMessage());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    // Construct the certificate entry to return.
    LinkedHashMap<ObjectClass,String> ocMap =
        new LinkedHashMap<ObjectClass,String>(2);
    ocMap.put(DirectoryServer.getTopObjectClass(), OC_TOP);

    ObjectClass objectClass =
         DirectoryServer.getObjectClass(OC_INSTANCE_KEY, true);
    ocMap.put(objectClass, OC_INSTANCE_KEY);

    LinkedHashMap<AttributeType,List<Attribute>> opAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(0);
    LinkedHashMap<AttributeType,List<Attribute>> userAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>(3);

    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(1);
    valueSet.add(v);

    ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(new Attribute(t, t.getNameOrOID(), valueSet));
    userAttrs.put(t, attrList);


    t = DirectoryServer.getAttributeType(ATTR_ADS_CERTIFICATE, true);
    valueSet = new LinkedHashSet<AttributeValue>(1);
    valueSet.add(new AttributeValue(t,
                          certValue));
    attrList = new ArrayList<Attribute>(1);
    LinkedHashSet<String> options = new LinkedHashSet<String>(1);
    options.add("binary");
    attrList.add(new Attribute(t, t.getNameOrOID(), options, valueSet));
    userAttrs.put(t, attrList);


    Entry e = new Entry(entryDN, ocMap, userAttrs, opAttrs);
    e.processVirtualAttributes();
    return e;
  }



  /**
   * {@inheritDoc}
   */
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    DN entryDN = entry.getDN();

    if (entryDN.equals(baseDN))
    {
      Message message = ERR_TRUSTSTORE_INVALID_BASE.get(
           String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, message);
    }

    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      Message message = ERR_TRUSTSTORE_INVALID_BASE.get(
           String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    if (parentDN.equals(baseDN))
    {
      addCertificate(entry);
    }
    else
    {
      Message message = ERR_TRUSTSTORE_INVALID_BASE.get(
           String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

  }



  /**
   * {@inheritDoc}
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    if (entryDN.equals(baseDN))
    {
      Message message = ERR_TRUSTSTORE_INVALID_BASE.get(
           String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null || !parentDN.equals(baseDN))
    {
      Message message = ERR_TRUSTSTORE_INVALID_BASE.get(
           String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    deleteCertificate(entryDN);
  }



  /**
   * {@inheritDoc}
   */
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
         throws DirectoryException
  {
    Message message = ERR_TRUSTSTORE_MODIFY_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    Message message = ERR_TRUSTSTORE_MODIFY_DN_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
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
    if (this.baseDN.equals(baseDN))
    {
      if ((scope == SearchScope.BASE_OBJECT) ||
          (scope == SearchScope.WHOLE_SUBTREE))
      {
        if (filter.matchesEntry(baseEntry))
        {
          searchOperation.returnEntry(baseEntry, null);
        }
      }

      String[] aliases = null;
      try
      {
        aliases = certificateManager.getCertificateAliases();
      }
      catch (KeyStoreException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      if (aliases == null)
      {
        aliases = new String[0];
      }

      if ((scope != SearchScope.BASE_OBJECT) && (! (aliases.length == 0) ))
      {
        AttributeType certAliasType =
             DirectoryServer.getAttributeType(ATTR_CERT_ALIAS, true);
        for (String alias : aliases)
        {
          DN certDN = makeChildDN(this.baseDN, certAliasType,
                                  alias);

          Entry certEntry;
          try
          {
            certEntry = getCertEntry(certDN);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            continue;
          }

          if (filter.matchesEntry(certEntry))
          {
            searchOperation.returnEntry(certEntry, null);
          }

        }
      }
    }
    else if (this.baseDN.equals(baseDN.getParentDNInSuffix()))
    {
      Entry certEntry = getCertEntry(baseDN);

      if ((scope == SearchScope.BASE_OBJECT) ||
          (scope == SearchScope.WHOLE_SUBTREE))
      {
        if (filter.matchesEntry(certEntry))
        {
          searchOperation.returnEntry(certEntry, null);
        }
      }
    }
    else
    {
      Message message = ERR_TRUSTSTORE_INVALID_BASE.get(String.valueOf(baseDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsLDIFExport()
  {
    // We do not support LDIF exports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    Message message = ERR_TRUSTSTORE_IMPORT_AND_EXPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsLDIFImport()
  {
    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    // This backend does not support LDIF imports.
    Message message = ERR_TRUSTSTORE_IMPORT_AND_EXPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void createBackup(BackupConfig backupConfig)
       throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_TRUSTSTORE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_TRUSTSTORE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsRestore()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_TRUSTSTORE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }


  /**
   * {@inheritDoc}
   */
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    return ConditionResult.UNDEFINED;
  }

  /**
   * {@inheritDoc}
   */
  public long numSubordinates(DN entryDN) throws DirectoryException
  {
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       TrustStoreBackendCfg configuration, List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();


    // Get the path to the trust store file.
    String newTrustStoreFile = configuration.getTrustStoreFile();
    try
    {
      File f = getFileForPath(newTrustStoreFile);
      if (!(f.exists() && f.isFile()))
      {
        unacceptableReasons.add(ERR_TRUSTSTORE_NO_SUCH_FILE.get(
                String.valueOf(newTrustStoreFile),
                String.valueOf(cfgEntryDN)));
        configAcceptable = false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      unacceptableReasons.add(ERR_TRUSTSTORE_CANNOT_DETERMINE_FILE.get(
              String.valueOf(cfgEntryDN),
              getExceptionMessage(e)));
      configAcceptable = false;
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, kse);
        }

        Message message = ERR_TRUSTSTORE_INVALID_TYPE.get(
                String.valueOf(storeType),
                String.valueOf(cfgEntryDN),
                getExceptionMessage(kse));
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }


    // If there is a PIN property, then make sure the corresponding
    // property is set.
    String pinProp = configuration.getTrustStorePinProperty();
    if (pinProp != null)
    {
      if (System.getProperty(pinProp) == null)
      {
        Message message = ERR_TRUSTSTORE_PIN_PROPERTY_NOT_SET.get(
                String.valueOf(pinProp),
                String.valueOf(cfgEntryDN));
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }


    // If there is a PIN environment variable, then make sure the corresponding
    // environment variable is set.
    String pinEnVar = configuration.getTrustStorePinEnvironmentVariable();
    if (pinEnVar != null)
    {
      if (System.getenv(pinEnVar) == null)
      {
        Message message = ERR_TRUSTSTORE_PIN_ENVAR_NOT_SET.get(
                String.valueOf(pinEnVar),
                String.valueOf(cfgEntryDN));
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }


    // If there is a PIN file, then make sure the file is readable if it exists.
    String pinFile = configuration.getTrustStorePinFile();
    if (pinFile != null)
    {
      File f = new File(pinFile);
      if (f.exists())
      {
        String pinStr = null;

        BufferedReader br = null;
        try
        {
          br = new BufferedReader(new FileReader(pinFile));
          pinStr = br.readLine();
        }
        catch (IOException ioe)
        {
          Message message = ERR_TRUSTSTORE_PIN_FILE_CANNOT_READ.get(
                  String.valueOf(pinFile),
                  String.valueOf(cfgEntryDN),
                  getExceptionMessage(ioe));
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
        finally
        {
          try
          {
            br.close();
          } catch (Exception e) {
            // ignore
          }
        }

        if (pinStr == null)
        {
          Message message =  ERR_TRUSTSTORE_PIN_FILE_EMPTY.get(
                  String.valueOf(pinFile),
                  String.valueOf(cfgEntryDN));
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
      }
    }


    return configAcceptable;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(TrustStoreBackendCfg cfg)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();
    DN configEntryDN = cfg.dn();

    // Get the path to the trust store file.
    String newTrustStoreFile = cfg.getTrustStoreFile();
    File f = getFileForPath(newTrustStoreFile);
    if (! (f.exists() && f.isFile()))
    {
      resultCode = DirectoryServer.getServerErrorResultCode();

      messages.add(ERR_TRUSTSTORE_NO_SUCH_FILE.get(
              String.valueOf(newTrustStoreFile),
              String.valueOf(configEntryDN)));
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, kse);
      }

      messages.add(ERR_TRUSTSTORE_INVALID_TYPE.get(
              String.valueOf(newTrustStoreType),
              String.valueOf(configEntryDN),
              getExceptionMessage(kse)));

      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    // Get the PIN needed to access the contents of the trust store file.  We
    // will offer several places to look for the PIN, and we will do so in the
    // following order:
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    // In any case, the PIN must be in the clear.  If no PIN is provided, then
    // it will be assumed that none is required to access the information in the
    // trust store.
    char[] newPIN = null;
    String newPINProperty = cfg.getTrustStorePinProperty();
    if (newPINProperty == null)
    {
      String newPINEnVar = cfg.getTrustStorePinEnvironmentVariable();
      if (newPINEnVar == null)
      {
        String newPINFile = cfg.getTrustStorePinFile();
        if (newPINFile == null)
        {
          String pinStr = cfg.getTrustStorePin();
          if (pinStr == null)
          {
            newPIN = null;
          }
          else
          {
            newPIN = pinStr.toCharArray();
          }
        }
        else
        {
          File pinFile = getFileForPath(newPINFile);
          if (! pinFile.exists())
          {
            try
            {
              // Generate a PIN.
              newPIN = createKeystorePassword();

              // Store the PIN in the pin file.
              createPINFile(pinFile.getPath(), new String(newPIN));
            }
            catch (Exception e)
            {
              resultCode = DirectoryServer.getServerErrorResultCode();

              messages.add(ERR_TRUSTSTORE_PIN_FILE_CANNOT_CREATE.get(
                      String.valueOf(newPINFile),
                      String.valueOf(configEntryDN)));
            }
          }
          else
          {
            String pinStr = null;

            BufferedReader br = null;
            try
            {
              br = new BufferedReader(new FileReader(pinFile));
              pinStr = br.readLine();
            }
            catch (IOException ioe)
            {
              resultCode = DirectoryServer.getServerErrorResultCode();

              messages.add(ERR_TRUSTSTORE_PIN_FILE_CANNOT_READ.get(
                      String.valueOf(newPINFile),
                      String.valueOf(configEntryDN),
                      getExceptionMessage(ioe)));
            }
            finally
            {
              try
              {
                br.close();
              } catch (Exception e) {
                // ignore
              }
            }

            if (pinStr == null)
            {
              resultCode = DirectoryServer.getServerErrorResultCode();

              messages.add(ERR_TRUSTSTORE_PIN_FILE_EMPTY.get(
                      String.valueOf(newPINFile),
                      String.valueOf(configEntryDN)));
            }
            else
            {
              newPIN = pinStr.toCharArray();
            }
          }
        }
      }
      else
      {
        String pinStr = System.getenv(newPINEnVar);
        if (pinStr == null)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(ERR_TRUSTSTORE_PIN_ENVAR_NOT_SET.get(
                  String.valueOf(newPINEnVar),
                  String.valueOf(configEntryDN)));
        }
        else
        {
          newPIN = pinStr.toCharArray();
        }
      }
    }
    else
    {
      String pinStr = System.getProperty(newPINProperty);
      if (pinStr == null)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();

        messages.add(ERR_TRUSTSTORE_PIN_PROPERTY_NOT_SET.get(
                String.valueOf(newPINProperty),
                String.valueOf(configEntryDN)));
      }
      else
      {
        newPIN = pinStr.toCharArray();
      }
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      trustStoreFile = newTrustStoreFile;
      trustStoreType = newTrustStoreType;
      trustStorePIN  = newPIN;
      configuration  = cfg;
      certificateManager =
           new CertificateManager(getFileForPath(trustStoreFile).getPath(),
                                  trustStoreType,
                                  new String(trustStorePIN));
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * Create a new child DN from a given parent DN.  The child RDN is formed
   * from a given attribute type and string value.
   * @param parentDN The DN of the parent.
   * @param rdnAttrType The attribute type of the RDN.
   * @param rdnStringValue The string value of the RDN.
   * @return A new child DN.
   */
  public static DN makeChildDN(DN parentDN, AttributeType rdnAttrType,
                               String rdnStringValue)
  {
    AttributeValue attrValue =
         new AttributeValue(rdnAttrType, rdnStringValue);
    return parentDN.concat(RDN.create(rdnAttrType, attrValue));
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
    KeyStore keyStore;
    try
    {
      keyStore = KeyStore.getInstance(trustStoreType);

      FileInputStream inputStream =
           new FileInputStream(getFileForPath(trustStoreFile));
      keyStore.load(inputStream, trustStorePIN);
      inputStream.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_TRUSTSTORE_CANNOT_LOAD.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }


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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_TRUSTSTORE_CANNOT_CREATE_FACTORY.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
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
    KeyStore trustStore;
    try
    {
      trustStore = KeyStore.getInstance(trustStoreType);

      FileInputStream inputStream =
           new FileInputStream(getFileForPath(trustStoreFile));
      trustStore.load(inputStream, trustStorePIN);
      inputStream.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_TRUSTSTORE_CANNOT_LOAD.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }


    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory =
           TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(trustStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      TrustManager[] newTrustManagers = new TrustManager[trustManagers.length];
      for (int i=0; i < trustManagers.length; i++)
      {
        newTrustManagers[i] = new ExpirationCheckTrustManager(
                                       (X509TrustManager) trustManagers[i]);
      }
      return newTrustManagers;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_TRUSTSTORE_CANNOT_CREATE_FACTORY.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
  }


  private void addCertificate(Entry entry)
       throws DirectoryException
  {
    DN entryDN = entry.getDN();

    // Make sure that the DN specifies a certificate alias.
    AttributeType t =
         DirectoryServer.getAttributeType(ATTR_CERT_ALIAS, true);
    AttributeValue v = entryDN.getRDN().getAttributeValue(t);
    if (v == null)
    {
      Message message = ERR_TRUSTSTORE_DN_DOES_NOT_SPECIFY_CERTIFICATE.get(
           String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   baseDN, null);
    }
    String certAlias = v.getStringValue();

    try
    {
      if (certificateManager.aliasInUse(certAlias))
      {
        Message message = ERR_TRUSTSTORE_ALIAS_IN_USE.get(
             String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS,
                                     message);
      }

      ObjectClass ocSelfSignedCertRequest =
           DirectoryServer.getObjectClass(OC_SELF_SIGNED_CERT_REQUEST, true);
      if (entry.hasObjectClass(ocSelfSignedCertRequest))
      {
        try
        {
          certificateManager.generateSelfSignedCertificate(
             certAlias,
             getADSCertificateSubjectDN(),
             getADSCertificateValidity());
        }
        catch (Exception e)
        {
          Message message = ERR_TRUSTSTORE_CANNOT_GENERATE_CERT.get(
              certAlias, trustStoreFile, getExceptionMessage(e));
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message, e);
        }
      }
      else
      {
        List<Attribute> certAttrs = entry.getAttribute(ATTR_ADS_CERTIFICATE);
        if (certAttrs == null)
        {
          Message message =
               ERR_TRUSTSTORE_ENTRY_MISSING_CERT_ATTR.get(
                    String.valueOf(entryDN), ATTR_ADS_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message);
        }
        if (certAttrs.size() != 1)
        {
          Message message =
               ERR_TRUSTSTORE_ENTRY_HAS_MULTIPLE_CERT_ATTRS.get(
                    String.valueOf(entryDN), ATTR_ADS_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message);
        }

        LinkedHashSet<AttributeValue> certValues = certAttrs.get(0).getValues();
        if (certValues == null)
        {
          Message message =
               ERR_TRUSTSTORE_ENTRY_MISSING_CERT_VALUE.get(
                    String.valueOf(entryDN), ATTR_ADS_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message);
        }
        if (certValues.size() != 1)
        {
          Message message =
               ERR_TRUSTSTORE_ENTRY_HAS_MULTIPLE_CERT_VALUES.get(
                    String.valueOf(entryDN), ATTR_ADS_CERTIFICATE);
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message);
        }

        byte[] certBytes = certValues.iterator().next().getValueBytes();
        try
        {
          File tempDir = getFileForPath("config");
          File tempFile = File.createTempFile(configuration.getBackendId(),
                                              certAlias, tempDir);
          try
          {
            FileOutputStream outputStream =
                 new FileOutputStream(tempFile.getPath(), false);
            try
            {
              outputStream.write(certBytes);
            }
            finally
            {
              outputStream.close();
            }

            certificateManager.addCertificate(certAlias, tempFile);
          }
          finally
          {
            tempFile.delete();
          }
        }
        catch (IOException e)
        {
          Message message = ERR_TRUSTSTORE_CANNOT_WRITE_CERT.get(
              certAlias, getExceptionMessage(e));
          throw new DirectoryException(
               DirectoryServer.getServerErrorResultCode(), message, e);
        }
      }
    }
    catch (Exception e)
    {
      Message message = ERR_TRUSTSTORE_CANNOT_ADD_CERT.get(
           certAlias, trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(
           DirectoryServer.getServerErrorResultCode(), message, e);
    }

  }


  private void deleteCertificate(DN entryDN)
       throws DirectoryException
  {
    // Make sure that the DN specifies a certificate alias.
    AttributeType t =
         DirectoryServer.getAttributeType(ATTR_CERT_ALIAS, true);
    AttributeValue v = entryDN.getRDN().getAttributeValue(t);
    if (v == null)
    {
      Message message = ERR_TRUSTSTORE_DN_DOES_NOT_SPECIFY_CERTIFICATE.get(
           String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   baseDN, null);
    }
    String certAlias = v.getStringValue();

    try
    {
      if (!certificateManager.aliasInUse(certAlias))
      {
        Message message = ERR_TRUSTSTORE_INVALID_BASE.get(
             String.valueOf(entryDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                     message);
      }

      certificateManager.removeCertificate(certAlias);
    }
    catch (Exception e)
    {
      Message message = ERR_TRUSTSTORE_CANNOT_DELETE_CERT.get(
           certAlias, trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(
           DirectoryServer.getServerErrorResultCode(), message, e);
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
  private static String getADSCertificateSubjectDN()
       throws UnknownHostException
  {
    String hostname =
         java.net.InetAddress.getLocalHost().getCanonicalHostName();
    return "cn=" + Rdn.escapeValue(hostname) + ",O=OpenDS Certificate";
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
        d = d * (-1);
      }
      generatedChar = (char) (d+48);
      break;
    case 1:
      // Will return a lower case letter
      d = next % 26;
      if (d < 0)
      {
        d = d * (-1);
      }
      generatedChar =  (char) (d + 97);
      break;
    default:
      // Will return a capital letter
      d = (next % 26);
      if (d < 0)
      {
        d = d * (-1);
      }
      generatedChar = (char) (d + 65) ;
    }

    return generatedChar;
  }

  private static int getRandomInt(Random random,int modulo)
  {
    return (random.nextInt() & modulo);
  }

  /**
   * Creates a PIN file on the specified path.
   * @param path the path where the PIN file will be created.
   * @param pin The PIN to store in the file.
   * @throws IOException if something goes wrong.
   */
  public static void createPINFile(String path, String pin)
       throws IOException
  {
    FileWriter file = new FileWriter(path);
    PrintWriter out = new PrintWriter(file);

    out.println(pin);

    out.flush();
    out.close();

    if(FilePermission.canSetPermissions()) {
      try {
        if (!FilePermission.setPermissions(new File(path),
                                           new FilePermission(0600)))
        {
          // Log a warning that the permissions were not set.
          Message message = WARN_TRUSTSTORE_SET_PERMISSIONS_FAILED.get(path);
          ErrorLogger.logError(message);
        }
      } catch(DirectoryException e) {
        // Log a warning that the permissions were not set.
        Message message = WARN_TRUSTSTORE_SET_PERMISSIONS_FAILED.get(path);
        ErrorLogger.logError(message);
      }
    }
  }

}

