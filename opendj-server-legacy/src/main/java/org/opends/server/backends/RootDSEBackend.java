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
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2024 3A Systems, LLC.
 */
package org.opends.server.backends;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.server.RootDSEBackendCfg;
import org.forgerock.util.Reject;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.LocalBackend;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.BackendConfigManager.NamingContextFilter;
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
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.LDIFWriter;

/**
 * This class defines a backend to hold the Directory Server root DSE.  It is a
 * kind of meta-backend in that it will dynamically generate the root DSE entry
 * (although there will be some caching) for base-level searches, and will
 * simply redirect to other backends for operations in other scopes.
 * <BR><BR>
 * This should not be treated like a regular backend when it comes to
 * initializing the server configuration.  It should only be initialized after
 * all other backends are configured.  As such, it should have a special entry
 * in the configuration rather than being placed under the cn=Backends branch
 * with the other backends.
 */
public class RootDSEBackend
       extends LocalBackend<RootDSEBackendCfg>
       implements ConfigurationChangeListener<RootDSEBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The set of standard "static" attributes that we will always include in the
   * root DSE entry and won't change while the server is running.
   */
  private List<Attribute> staticDSEAttributes;
  /** The set of user-defined attributes that will be included in the root DSE entry. */
  private List<Attribute> userDefinedAttributes;
  /**
   * Indicates whether the attributes of the root DSE should always be treated
   * as user attributes even if they are defined as operational in the schema.
   */
  private boolean showAllAttributes;
  /**
   * Indicates whether sub-suffixes should also be included in the list of public naming contexts.
   */
  private boolean showSubordinatesNamingContexts;

  /** The set of objectclasses that will be used in the root DSE entry. */
  private Map<ObjectClass, String> dseObjectClasses;

  /** The current configuration state. */
  private RootDSEBackendCfg currentConfig;
  /** The DN of the configuration entry for this backend. */
  private DN configEntryDN;

  /** The DN for the root DSE. */
  private DN rootDSEDN;
  /** The set of base DNs for this backend. */
  private Set<DN> baseDNs;

  private ServerContext serverContext;

  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public RootDSEBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }

  @Override
  public void configureBackend(RootDSEBackendCfg config, ServerContext serverContext) throws ConfigException
  {
    Reject.ifNull(config);
    this.serverContext = serverContext;
    currentConfig = config;
    configEntryDN = config.dn();
  }

  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    Entry configEntry = DirectoryServer.getConfigEntry(configEntryDN);

    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      LocalizableMessage message = ERR_ROOTDSE_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }

    userDefinedAttributes = new ArrayList<>();
    addAllUserDefinedAttrs(userDefinedAttributes, configEntry);

    // Create the set of base DNs that we will handle.  In this case, it's just
    // the root DSE.
    rootDSEDN    = DN.rootDN();
    baseDNs = Collections.singleton(rootDSEDN);

    // Determine whether all root DSE attributes should be treated as user
    // attributes.
    showAllAttributes = currentConfig.isShowAllAttributes();
    showSubordinatesNamingContexts = currentConfig.isShowSubordinateNamingContexts();

    // Construct the set of "static" attributes that will always be present in
    // the root DSE.
    staticDSEAttributes = new ArrayList<>();
    staticDSEAttributes.add(Attributes.create(ATTR_VENDOR_NAME, SERVER_VENDOR_NAME));
    staticDSEAttributes.add(Attributes.create(ATTR_VENDOR_VERSION,
                                 DirectoryServer.getVersionString()));
    staticDSEAttributes.add(Attributes.create("fullVendorVersion",
                                 BuildVersion.binaryVersion().toString()));

    // Construct the set of objectclasses to include in the root DSE entry.
    dseObjectClasses = new HashMap<>(configEntry.getObjectClasses().size());
    dseObjectClasses.put(getTopObjectClass(), OC_TOP);
    dseObjectClasses.put(serverContext.getSchema().getObjectClass(OC_ROOT_DSE), OC_ROOT_DSE);
    dseObjectClasses.putAll(configEntry.getObjectClasses());
    // Set the backend ID for this backend. The identifier needs to be
    // specific enough to avoid conflict with user backend identifiers.
    setBackendID("__root.dse__");

    // Register as a change listener.
    currentConfig.addChangeListener(this);
  }

  /**
   * Get the set of user-defined attributes for the configuration entry. Any
   * attributes that we do not recognize will be included directly in the root DSE.
   */
  private void addAllUserDefinedAttrs(List<Attribute> userDefinedAttrs, Entry configEntry)
  {
    for (Attribute a : configEntry.getAllAttributes())
    {
      if (!isDSEConfigAttribute(a))
      {
        userDefinedAttrs.add(a);
      }
    }
  }

  @Override
  public void closeBackend()
  {
    currentConfig.removeChangeListener(this);
  }

  /**
   * Indicates whether the provided attribute is one that is used in the
   * configuration of this backend.
   *
   * @param  attribute  The attribute for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute is one that is used in
   *          the configuration of this backend, <CODE>false</CODE> if not.
   */
  private boolean isDSEConfigAttribute(Attribute attribute)
  {
    AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
    return attrType.hasName(ATTR_ROOT_DSE_SUBORDINATE_BASE_DN)
        || attrType.hasName(ATTR_ROOTDSE_SHOW_ALL_ATTRIBUTES)
        || attrType.hasName(ATTR_COMMON_NAME);
  }

  @Override
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

  @Override
  public synchronized long getEntryCount()
  {
    // There is always just a single entry in this backend.
    return 1;
  }

  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    final long ret = getNumberOfChildren(entryDN);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    return ConditionResult.valueOf(ret != 0);
  }

  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    checkNotNull(baseDN, "baseDN must not be null");
    if (!baseDN.isRootDN())
    {
      return -1;
    }
    return 1;
  }

  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    checkNotNull(parentDN, "parentDN must not be null");
    if (!parentDN.isRootDN())
    {
      return -1;
    }
    return 0;
  }

  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    // If the requested entry was the root DSE, then create and return it.
    if (entryDN == null || entryDN.isRootDN())
    {
      return getRootDSE();
    }
    return null;
  }

  /**
   * Retrieves the root DSE entry for the Directory Server.
   *
   * @return The root DSE entry for the Directory Server.
   */
  public Entry getRootDSE()
  {
    return getRootDSE(null);
  }

  /**
   * Retrieves the root DSE entry for the Directory Server.
   *
   * @param connection
   *          The client connection, or {@code null} if there is no associated
   *          client connection.
   * @return The root DSE entry for the Directory Server.
   */
  private Entry getRootDSE(ClientConnection connection)
  {
    Map<AttributeType, List<Attribute>> dseUserAttrs = new HashMap<>();
    Map<AttributeType, List<Attribute>> dseOperationalAttrs = new HashMap<>();

    BackendConfigManager manager = serverContext.getBackendConfigManager();
    NamingContextFilter[] filters = showSubordinatesNamingContexts ?
        new NamingContextFilter[] {PUBLIC} : new NamingContextFilter[] {PUBLIC, TOP_LEVEL};
    Set<DN> publicNamingContexts = manager.getNamingContexts(filters);
    Attribute publicNamingContextAttr = createAttribute(ATTR_NAMING_CONTEXTS, publicNamingContexts);
    addAttribute(publicNamingContextAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "ds-private-naming-contexts" attribute.
    Attribute privateNamingContextAttr = createAttribute(
        ATTR_PRIVATE_NAMING_CONTEXTS, manager.getNamingContexts(PRIVATE));
    addAttribute(privateNamingContextAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedControl" attribute.
    Attribute supportedControlAttr = createAttribute(ATTR_SUPPORTED_CONTROL, DirectoryServer.getSupportedControls());
    addAttribute(supportedControlAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedExtension" attribute.
    Attribute supportedExtensionAttr = createAttribute(ATTR_SUPPORTED_EXTENSION,
        DirectoryServer.getSupportedExtensions());
    addAttribute(supportedExtensionAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedFeature" attribute.
    Attribute supportedFeatureAttr = createAttribute(ATTR_SUPPORTED_FEATURE, DirectoryServer.getSupportedFeatures());
    addAttribute(supportedFeatureAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedSASLMechanisms" attribute.
    Attribute supportedSASLMechAttr = createAttribute(
        ATTR_SUPPORTED_SASL_MECHANISMS, DirectoryServer.getSupportedSASLMechanisms());
    addAttribute(supportedSASLMechAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedLDAPVersions" attribute.
    TreeSet<String> versionStrings = new TreeSet<>();
    for (Integer ldapVersion : DirectoryServer.getSupportedLDAPVersions())
    {
      versionStrings.add(ldapVersion.toString());
    }
    Attribute supportedLDAPVersionAttr = createAttribute(
        ATTR_SUPPORTED_LDAP_VERSION, versionStrings);
    addAttribute(supportedLDAPVersionAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedAuthPasswordSchemes" attribute.
    Attribute supportedAuthPWSchemesAttr = createAttribute(
        ATTR_SUPPORTED_AUTH_PW_SCHEMES, DirectoryServer.getAuthPasswordStorageSchemes().keySet());
    addAttribute(supportedAuthPWSchemesAttr, dseUserAttrs, dseOperationalAttrs);

    // Obtain TLS protocol and cipher support.
    Collection<String> supportedTlsProtocols;
    Collection<String> supportedTlsCiphers;
    if (connection != null)
    {
      // Only return the list of enabled protocols / ciphers for the connection
      // handler to which the client is connected.
      supportedTlsProtocols = connection.getConnectionHandler().getEnabledSSLProtocols();
      supportedTlsCiphers = connection.getConnectionHandler().getEnabledSSLCipherSuites();
    }
    else
    {
      try
      {
        final SSLContext context = SSLContext.getDefault();
        final SSLParameters parameters = context.getSupportedSSLParameters();
        supportedTlsProtocols = Arrays.asList(parameters.getProtocols());
        supportedTlsCiphers = Arrays.asList(parameters.getCipherSuites());
      }
      catch (Exception e)
      {
        // A default SSL context should always be available.
        supportedTlsProtocols = Collections.emptyList();
        supportedTlsCiphers = Collections.emptyList();
      }
    }

    // Add the "supportedTLSProtocols" attribute.
    Attribute supportedTLSProtocolsAttr = createAttribute(
        ATTR_SUPPORTED_TLS_PROTOCOLS, supportedTlsProtocols);
    addAttribute(supportedTLSProtocolsAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedTLSCiphers" attribute.
    Attribute supportedTLSCiphersAttr = createAttribute(
        ATTR_SUPPORTED_TLS_CIPHERS, supportedTlsCiphers);
    addAttribute(supportedTLSCiphersAttr, dseUserAttrs, dseOperationalAttrs);

    addAll(staticDSEAttributes, dseUserAttrs, dseOperationalAttrs);
    addAll(userDefinedAttributes, dseUserAttrs, dseOperationalAttrs);

    // Construct and return the entry.
    Entry e = new Entry(rootDSEDN, dseObjectClasses, dseUserAttrs,
                        dseOperationalAttrs);
    e.processVirtualAttributes();
    return e;
  }

  private void addAll(Collection<Attribute> attributes,
      Map<AttributeType, List<Attribute>> userAttrs, Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    for (Attribute a : attributes)
    {
      AttributeType type = a.getAttributeDescription().getAttributeType();

      final Map<AttributeType, List<Attribute>> attrsMap = type.isOperational() && !showAllAttributes
          ? operationalAttrs
          : userAttrs;
      List<Attribute> attrs = attrsMap.get(type);
      if (attrs == null)
      {
        attrs = new ArrayList<>();
        attrsMap.put(type, attrs);
      }
      attrs.add(a);
    }
  }

  private void addAttribute(Attribute attribute,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    if (!attribute.isEmpty())
    {
      List<Attribute> attrs = newArrayList(attribute);
      final AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
      if (showAllAttributes || !attrType.isOperational())
      {
        userAttrs.put(attrType, attrs);
      }
      else
      {
        operationalAttrs.put(attrType, attrs);
      }
    }
  }

  /**
   * Creates an attribute for the root DSE with the following
   * criteria.
   *
   * @param name
   *          The name for the attribute.
   * @param values
   *          The set of values to use for the attribute.
   * @return The constructed attribute.
   */
  private Attribute createAttribute(String name, Collection<? extends Object> values)
  {
    AttributeBuilder builder = new AttributeBuilder(name);
    builder.addAllStrings(values);
    return builder.toAttribute();
  }

  @Override
  public boolean entryExists(DN entryDN) throws DirectoryException
  {
    return entryDN.isRootDN();
  }

  @Override
  public void addEntry(Entry entry, AddOperation addOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(entry.getName(), getBackendID()));
  }

  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_DELETE_NOT_SUPPORTED.get(entryDN, getBackendID()));
  }

  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_ROOTDSE_MODIFY_NOT_SUPPORTED.get(newEntry.getName(), configEntryDN));
  }

  @Override
  public void renameEntry(DN currentDN, Entry entry, ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(currentDN, getBackendID()));
  }

  @Override
  public void search(SearchOperation searchOperation)
         throws DirectoryException, CanceledOperationException {
    DN baseDN = searchOperation.getBaseDN();
    if (! baseDN.isRootDN())
    {
      LocalizableMessage message = ERR_ROOTDSE_INVALID_SEARCH_BASE.
          get(searchOperation.getConnectionID(), searchOperation.getOperationID(), baseDN);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    SearchFilter filter = searchOperation.getFilter();
    switch (searchOperation.getScope().asEnum())
    {
      case BASE_OBJECT:
        Entry dseEntry = getRootDSE(searchOperation.getClientConnection());
        if (filter.matchesEntry(dseEntry))
        {
          searchOperation.returnEntry(dseEntry, null);
        }
        break;

      case SINGLE_LEVEL:
      case WHOLE_SUBTREE:
      case SUBORDINATES:
        // nothing to return
        break;
      default:
        LocalizableMessage message = ERR_ROOTDSE_INVALID_SEARCH_SCOPE.
            get(searchOperation.getConnectionID(),
                searchOperation.getOperationID(),
                searchOperation.getScope());
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
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
    // We will only export the DSE entry itself.
    return BackendOperation.LDIF_EXPORT.equals(backendOperation);
  }

  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER.get(
          stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                                   message);
    }

    // Write the root DSE entry itself to it.  Make sure to close the LDIF
    // writer when we're done.
    try
    {
      ldifWriter.writeEntry(getRootDSE());
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_ROOTDSE_UNABLE_TO_EXPORT_DSE.get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                                   message);
    }
    finally
    {
      close(ldifWriter);
    }
  }

  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    LocalizableMessage message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    LocalizableMessage message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    LocalizableMessage message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  @Override
  public boolean isConfigurationAcceptable(RootDSEBackendCfg config,
                                           List<LocalizableMessage> unacceptableReasons,
                                           ServerContext serverContext)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(RootDSEBackendCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    boolean configIsAcceptable = true;



    return configIsAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(RootDSEBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    boolean newShowAll = cfg.isShowAllAttributes();

    // Check to see if there is a new set of user-defined attributes.
    ArrayList<Attribute> userAttrs = new ArrayList<>();
    try
    {
      Entry configEntry = DirectoryServer.getConfigEntry(configEntryDN);
      addAllUserDefinedAttrs(userAttrs, configEntry);
    }
    catch (ConfigException e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY.get(
              configEntryDN, stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      if (showAllAttributes != newShowAll)
      {
        showAllAttributes = newShowAll;
        ccr.addMessage(INFO_ROOTDSE_UPDATED_SHOW_ALL_ATTRS.get(
                ATTR_ROOTDSE_SHOW_ALL_ATTRIBUTES, showAllAttributes));
      }
      showSubordinatesNamingContexts = cfg.isShowSubordinateNamingContexts();
      userDefinedAttributes = userAttrs;
      ccr.addMessage(INFO_ROOTDSE_USING_NEW_USER_ATTRS.get());
    }

    return ccr;
  }
}
