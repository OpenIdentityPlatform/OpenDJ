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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

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
import org.forgerock.opendj.server.config.server.MonitorBackendCfg;
import org.forgerock.util.Reject;
import org.opends.server.api.Backend;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;

/**
 * This class defines a backend to hold Directory Server monitor entries. It
 * will not actually store anything, but upon request will retrieve the
 * requested monitor and dynamically generate the associated entry. It will also
 * construct a base monitor entry with some useful server-wide data.
 */
public class MonitorBackend extends Backend<MonitorBackendCfg> implements
    ConfigurationChangeListener<MonitorBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of user-defined attributes that will be included in the base monitor entry. */
  private ArrayList<Attribute> userDefinedAttributes;
  /** The set of objectclasses that will be used in monitor entries. */
  private final HashMap<ObjectClass, String> monitorObjectClasses = new LinkedHashMap<>(2);
  /** The DN of the configuration entry for this backend. */
  private DN configEntryDN;
  /** The current configuration state. */
  private MonitorBackendCfg currentConfig;
  /** The DN for the base monitor entry. */
  private DN baseMonitorDN;
  /** The set of base DNs for this backend. */
  private Set<DN> baseDNs;

  /**
   * Creates a new backend with the provided information. All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public MonitorBackend()
  {
    super();
  }

  @Override
  public void addEntry(final Entry entry, final AddOperation addOperation)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(entry.getName(), getBackendID()));
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      final MonitorBackendCfg backendCfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Check to see if there is a new set of user-defined attributes.
    final ArrayList<Attribute> userAttrs = new ArrayList<>();
    try
    {
      final Entry configEntry = DirectoryServer.getConfigEntry(configEntryDN);
      addAllNonMonitorConfigAttributes(userAttrs, configEntry.getUserAttributes().values());
      addAllNonMonitorConfigAttributes(userAttrs, configEntry.getOperationalAttributes().values());
    }
    catch (final Exception e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY.get(
          configEntryDN, stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }

    userDefinedAttributes = userAttrs;

    ccr.addMessage(INFO_MONITOR_USING_NEW_USER_ATTRS.get());

    currentConfig = backendCfg;
    return ccr;
  }

  private void addAllNonMonitorConfigAttributes(final List<Attribute> userAttrs, Collection<List<Attribute>> attrbutes)
  {
    for (final List<Attribute> attrs : attrbutes)
    {
      for (final Attribute a : attrs)
      {
        if (!isMonitorConfigAttribute(a))
        {
          userAttrs.add(a);
        }
      }
    }
  }

  @Override
  public void configureBackend(final MonitorBackendCfg config, ServerContext serverContext)
      throws ConfigException
  {
    Reject.ifNull(config);

    final MonitorBackendCfg cfg = config;
    final Entry configEntry = DirectoryServer.getConfigEntry(cfg.dn());

    // Make sure that a configuration entry was provided. If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      final LocalizableMessage message = ERR_MONITOR_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }

    configEntryDN = configEntry.getName();

    // Get the set of user-defined attributes for the configuration entry. Any
    // attributes that we don't recognize will be included directly in the base
    // monitor entry.
    userDefinedAttributes = new ArrayList<>();
    addAll(userDefinedAttributes, configEntry.getUserAttributes().values());
    addAll(userDefinedAttributes, configEntry.getOperationalAttributes().values());

    // Construct the set of objectclasses to include in the base monitor entry.
    monitorObjectClasses.put(CoreSchema.getTopObjectClass(), OC_TOP);
    monitorObjectClasses.put(DirectoryServer.getSchema().getObjectClass(OC_MONITOR_ENTRY), OC_MONITOR_ENTRY);

    // Create the set of base DNs that we will handle. In this case, it's just
    // the DN of the base monitor entry.
    try
    {
      baseMonitorDN = DN.valueOf(DN_MONITOR_ROOT);
    }
    catch (final Exception e)
    {
      logger.traceException(e);

      final LocalizableMessage message = ERR_MONITOR_CANNOT_DECODE_MONITOR_ROOT_DN
          .get(getExceptionMessage(e));
      throw new ConfigException(message, e);
    }

    this.baseDNs = Collections.singleton(baseMonitorDN);

    currentConfig = cfg;
  }

  private void addAll(ArrayList<Attribute> attributes, Collection<List<Attribute>> attributesToAdd)
  {
    addAllNonMonitorConfigAttributes(attributes, attributesToAdd);
  }

  @Override
  public void createBackup(final BackupConfig backupConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void deleteEntry(final DN entryDN,
      final DeleteOperation deleteOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_DELETE_NOT_SUPPORTED.get(entryDN, getBackendID()));
  }

  @Override
  public boolean entryExists(final DN entryDN) throws DirectoryException
  {
    return getDIT().containsKey(entryDN);
  }

  @Override
  public void exportLDIF(final LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    // TODO export-ldif reports nonsense for upTime etc.
    try (LDIFWriter ldifWriter = newLDIFWriter(exportConfig))
    {
      // Write the base monitor entry to the LDIF.
      try
      {
        ldifWriter.writeEntry(getBaseMonitorEntry());
      }
      catch (final Exception e)
      {
        logger.traceException(e);
        final LocalizableMessage message = ERR_MONITOR_UNABLE_TO_EXPORT_BASE.get(stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
      }

      // Get all the monitor providers, convert them to entries, and write them to LDIF.
      for (final MonitorProvider<?> monitorProvider : DirectoryServer.getMonitorProviders().values())
      {
        try
        {
          // TODO implementation of export is incomplete
        }
        catch (final Exception e)
        {
          logger.traceException(e);
          final LocalizableMessage message =
              ERR_MONITOR_UNABLE_TO_EXPORT_PROVIDER_ENTRY.get(monitorProvider.getMonitorInstanceName(),
                  stackTraceToSingleLineString(e));
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
        }
      }
    }
    catch (IOException ignoreOnClose)
    {
      logger.traceException(ignoreOnClose);
    }
  }

  private LDIFWriter newLDIFWriter(final LDIFExportConfig exportConfig) throws DirectoryException
  {
    try
    {
      return new LDIFWriter(exportConfig);
    }
    catch (final Exception e)
    {
      logger.traceException(e);

      final LocalizableMessage message = ERR_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER.get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
    }
  }

  @Override
  public void closeBackend()
  {
    currentConfig.removeMonitorChangeListener(this);
    try
    {
      DirectoryServer.deregisterBaseDN(baseMonitorDN);
    }
    catch (final Exception e)
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
  public Entry getEntry(final DN entryDN) throws DirectoryException
  {
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKEND_GET_ENTRY_NULL.get(getBackendID()));
    }

    // If the requested entry was the monitor base entry, then retrieve it
    // without constructing the DIT.
    if (entryDN.equals(baseMonitorDN))
    {
      return getBaseMonitorEntry();
    }

    // From now on we'll need the DIT.
    final Map<DN, MonitorProvider<?>> dit = getDIT();
    if (!dit.containsKey(entryDN))
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_MONITOR_INVALID_BASE.get(entryDN, baseMonitorDN));
    }

    // The DN is associated with a valid monitor/glue entry.
    return getEntry(entryDN, dit);
  }

  @Override
  public long getEntryCount()
  {
    return getDIT().size();
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
  public ConditionResult hasSubordinates(final DN entryDN)
      throws DirectoryException
  {
    final NavigableMap<DN, MonitorProvider<?>> dit = getDIT();
    if (dit.containsKey(entryDN))
    {
      final DN nextDN = dit.higherKey(entryDN);
      return ConditionResult.valueOf(nextDN != null && nextDN.isSubordinateOrEqualTo(entryDN));
    }
    return ConditionResult.UNDEFINED;
  }

  @Override
  public LDIFImportResult importLDIF(final LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    // Register with the Directory Server as a configurable component.
    currentConfig.addMonitorChangeListener(this);

    // Register the monitor base as a private suffix.
    try
    {
      DirectoryServer.registerBaseDN(baseMonitorDN, this, true);
    }
    catch (final Exception e)
    {
      logger.traceException(e);

      final LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          baseMonitorDN, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      final MonitorBackendCfg backendCfg,
      final List<LocalizableMessage> unacceptableReasons)
  {
    // We'll pretty much accept anything here as long as it isn't one of our
    // private attributes.
    return true;
  }

  @Override
  public boolean isIndexed(final AttributeType attributeType,
      final IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  @Override
  public long getNumberOfEntriesInBaseDN(final DN baseDN) throws DirectoryException {
    checkNotNull(baseDN, "baseDN must not be null");
    return getNumberOfSubordinates(baseDN, true) + 1;
  }

  @Override
  public long getNumberOfChildren(final DN parentDN) throws DirectoryException {
    checkNotNull(parentDN, "parentDN must not be null");
    return getNumberOfSubordinates(parentDN, false);
  }

  private long getNumberOfSubordinates(final DN entryDN, final boolean includeSubtree) throws DirectoryException
  {
    final NavigableMap<DN, MonitorProvider<?>> dit = getDIT();
    if (!dit.containsKey(entryDN))
    {
      return -1L;
    }
    long count = 0;
    final int childDNSize = entryDN.size() + 1;
    for (final DN dn : dit.tailMap(entryDN, false).navigableKeySet())
    {
      if (!dn.isSubordinateOrEqualTo(entryDN))
      {
        break;
      }
      else if (includeSubtree || dn.size() == childDNSize)
      {
        count++;
      }
    }
    return count;
  }

  @Override
  public void removeBackup(final BackupDirectory backupDirectory,
      final String backupID) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void renameEntry(final DN currentDN, final Entry entry,
      final ModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(currentDN, getBackendID()));
  }

  @Override
  public void replaceEntry(final Entry oldEntry, final Entry newEntry,
      final ModifyOperation modifyOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_MONITOR_MODIFY_NOT_SUPPORTED.get(newEntry.getName(), configEntryDN));
  }

  @Override
  public void restoreBackup(final RestoreConfig restoreConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  @Override
  public void search(final SearchOperation searchOperation)
      throws DirectoryException
  {
    // Get the base DN, scope, and filter for the search.
    final DN baseDN = searchOperation.getBaseDN();
    final SearchScope scope = searchOperation.getScope();
    final SearchFilter filter = searchOperation.getFilter();

    // Compute the current monitor DIT.
    final NavigableMap<DN, MonitorProvider<?>> dit = getDIT();

    // Resolve the base entry and return no such object if it does not exist.
    if (!dit.containsKey(baseDN))
    {
      // Not found, so find the nearest match.
      DN matchedDN = baseDN.parent();
      while (matchedDN != null)
      {
        if (dit.containsKey(matchedDN))
        {
          break;
        }
        matchedDN = matchedDN.parent();
      }
      final LocalizableMessage message = ERR_BACKEND_ENTRY_DOESNT_EXIST.get(baseDN, getBackendID());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
          matchedDN, null);
    }

    // Walk through all entries and send the ones that match.
    for (final Map.Entry<DN, MonitorProvider<?>> e : dit.tailMap(baseDN).entrySet())
    {
      final DN dn = e.getKey();
      if (dn.isInScopeOf(baseDN, scope))
      {
        final Entry entry = getEntry(dn, dit);
        if (filter.matchesEntry(entry))
        {
          searchOperation.returnEntry(entry, null);
        }
      }
      else if (scope == SearchScope.BASE_OBJECT || !dn.isSubordinateOrEqualTo(baseDN))
      {
        // No more entries will be in scope.
        break;
      }
    }
  }

  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    // We can export all the monitor entries as a point-in-time snapshot.
    // TODO implementation of export is incomplete
    // TODO export-ldif reports nonsense for upTime etc.
    return false;
  }

  /**
   * Retrieves the base monitor entry for the Directory Server.
   *
   * @return The base monitor entry for the Directory Server.
   */
  private Entry getBaseMonitorEntry()
  {
    final ObjectClass extensibleObjectOC = CoreSchema.getExtensibleObjectObjectClass();
    final HashMap<ObjectClass, String> monitorClasses = newObjectClasses(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);

    final HashMap<AttributeType, List<Attribute>> monitorUserAttrs = new LinkedHashMap<>();
    final HashMap<AttributeType, List<Attribute>> monitorOperationalAttrs = new LinkedHashMap<>();

    put(monitorUserAttrs, Attributes.create(ATTR_COMMON_NAME, "monitor"));
    put(monitorUserAttrs, Attributes.create(ATTR_PRODUCT_NAME, DynamicConstants.PRODUCT_NAME));
    put(monitorUserAttrs, Attributes.create(ATTR_VENDOR_NAME, SERVER_VENDOR_NAME));
    put(monitorUserAttrs, Attributes.create(ATTR_VENDOR_VERSION, DirectoryServer.getVersionString()));
    put(monitorUserAttrs, Attributes.create(ATTR_START_TIME, DirectoryServer.getStartTimeUTC()));
    put(monitorUserAttrs, Attributes.create(ATTR_CURRENT_TIME, TimeThread.getGMTTime()));
    put(monitorUserAttrs, Attributes.create(ATTR_UP_TIME, getHumanReadableUpTime()));

    // Add the number of connections currently established.
    final long currentConns = DirectoryServer.getCurrentConnections();
    put(monitorUserAttrs, Attributes.create(ATTR_CURRENT_CONNS, String.valueOf(currentConns)));

    // Add the maximum number of connections established at one time.
    final long maxConns = DirectoryServer.getMaxConnections();
    put(monitorUserAttrs, Attributes.create(ATTR_MAX_CONNS, String.valueOf(maxConns)));

    // Add the total number of connections the server has accepted.
    final long totalConns = DirectoryServer.getTotalConnections();
    put(monitorUserAttrs, Attributes.create(ATTR_TOTAL_CONNS, String.valueOf(totalConns)));

    // Add all the user-defined attributes.
    for (final Attribute a : userDefinedAttributes)
    {
      final AttributeType type = a.getAttributeDescription().getAttributeType();

      final HashMap<AttributeType, List<Attribute>> attrsMap =
          type.isOperational() ? monitorOperationalAttrs : monitorUserAttrs;
      List<Attribute> attrs = attrsMap.get(type);
      if (attrs == null)
      {
        attrs = new ArrayList<>();
        attrsMap.put(type, attrs);
      }
      attrs.add(a);
    }

    return newEntry(baseMonitorDN, monitorClasses, monitorUserAttrs, monitorOperationalAttrs);
  }

  private String getHumanReadableUpTime()
  {
    long upSeconds = (System.currentTimeMillis() - DirectoryServer.getStartTime()) / 1000;
    final long upDays = upSeconds / 86400;
    upSeconds %= 86400;
    final long upHours = upSeconds / 3600;
    upSeconds %= 3600;
    final long upMinutes = upSeconds / 60;
    upSeconds %= 60;
    return INFO_MONITOR_UPTIME.get(upDays, upHours, upMinutes, upSeconds).toString();
  }

  private void put(final HashMap<AttributeType, List<Attribute>> attrsMap, final Attribute attr)
  {
    attrsMap.put(attr.getAttributeDescription().getAttributeType(), newArrayList(attr));
  }

  /**
   * Retrieves the branch monitor entry for the Directory Server.
   *
   * @param dn
   *          to get.
   * @return The branch monitor entry for the Directory Server.
   */
  private Entry getBranchMonitorEntry(final DN dn)
  {
    final ObjectClass monitorOC = DirectoryServer.getSchema().getObjectClass(OC_MONITOR_BRANCH);
    final HashMap<ObjectClass, String> monitorClasses = newObjectClasses(monitorOC, OC_MONITOR_BRANCH);

    final HashMap<AttributeType, List<Attribute>> monitorUserAttrs = new LinkedHashMap<>();

    final RDN rdn = dn.rdn();
    if (rdn != null)
    {
      for (AVA ava : rdn)
      {
        final AttributeType attributeType = ava.getAttributeType();
        final ByteString value = ava.getAttributeValue();
        monitorUserAttrs.put(attributeType, Attributes.createAsList(attributeType, value));
      }
    }

    return newEntry(dn, monitorClasses, monitorUserAttrs, null);
  }

  /**
   * Returns a map containing records for each DN in the monitor backend's DIT.
   * Each record maps the entry DN to the associated monitor provider, or
   * {@code null} if the entry is a glue (branch) entry.
   *
   * @return A map containing records for each DN in the monitor backend's DIT.
   */
  private NavigableMap<DN, MonitorProvider<?>> getDIT()
  {
    final NavigableMap<DN, MonitorProvider<?>> dit = new TreeMap<>();
    for (final MonitorProvider<?> monitorProvider : DirectoryServer.getMonitorProviders().values())
    {
      DN dn = DirectoryServer.getMonitorProviderDN(monitorProvider);
      dit.put(dn, monitorProvider);

      // Added glue records.
      for (dn = dn.parent(); dn != null; dn = dn.parent())
      {
        if (dit.containsKey(dn))
        {
          break;
        }
        dit.put(dn, null);
      }
    }
    return dit;
  }

  /**
   * Creates the monitor entry having the specified DN.
   *
   * @param entryDN
   *          The name of the monitor entry.
   * @param dit
   *          The monitor DIT.
   * @return Returns the monitor entry having the specified DN.
   */
  private Entry getEntry(final DN entryDN,
      final Map<DN, MonitorProvider<?>> dit)
  {
    // Get the monitor provider.
    final MonitorProvider<?> monitorProvider = dit.get(entryDN);
    if (monitorProvider != null)
    {
      return getMonitorEntry(entryDN, monitorProvider);
    }
    else if (entryDN.equals(baseMonitorDN))
    {
      // The monitor base entry needs special treatment.
      return getBaseMonitorEntry();
    }
    else
    {
      // Create a generic glue branch entry.
      return getBranchMonitorEntry(entryDN);
    }
  }

  /**
   * Generates and returns a monitor entry based on the contents of the provided
   * monitor provider.
   *
   * @param entryDN
   *          The DN to use for the entry.
   * @param monitorProvider
   *          The monitor provider to use to obtain the information for the
   *          entry.
   * @return The monitor entry generated from the information in the provided
   *         monitor provider.
   */
  private Entry getMonitorEntry(final DN entryDN,
      final MonitorProvider<?> monitorProvider)
  {
    final ObjectClass monitorOC = monitorProvider.getMonitorObjectClass();
    final HashMap<ObjectClass, String> monitorClasses = newObjectClasses(monitorOC, monitorOC.getNameOrOID());

    final MonitorData monitorAttrs = monitorProvider.getMonitorData();
    final Map<AttributeType, List<Attribute>> attrMap = asMap(monitorAttrs);

    // Make sure to include the RDN attribute.
    final AVA ava = entryDN.rdn().getFirstAVA();
    final AttributeType rdnType = ava.getAttributeType();
    final ByteString rdnValue = ava.getAttributeValue();
    attrMap.put(rdnType, Attributes.createAsList(rdnType, rdnValue));

    return newEntry(entryDN, monitorClasses, attrMap, new HashMap<AttributeType, List<Attribute>>(0));
  }

  private Map<AttributeType, List<Attribute>> asMap(MonitorData monitorAttrs)
  {
    final Map<AttributeType, List<Attribute>> results = new LinkedHashMap<>(monitorAttrs.size() + 1);
    for (final Attribute a : monitorAttrs)
    {
      final AttributeType type = a.getAttributeDescription().getAttributeType();

      List<Attribute> attrs = results.get(type);
      if (attrs == null)
      {
        attrs = new ArrayList<>();
        results.put(type, attrs);
      }
      attrs.add(a);
    }
    return results;
  }

  private HashMap<ObjectClass, String> newObjectClasses(ObjectClass objectClass, String objectClassName)
  {
    final HashMap<ObjectClass, String> monitorClasses = new LinkedHashMap<>(monitorObjectClasses.size() + 1);
    monitorClasses.putAll(monitorObjectClasses);
    monitorClasses.put(objectClass, objectClassName);
    return monitorClasses;
  }

  private Entry newEntry(final DN dn, final Map<ObjectClass, String> objectClasses,
      final Map<AttributeType, List<Attribute>> userAttrs, Map<AttributeType, List<Attribute>> opAttrs)
  {
    final Entry e = new Entry(dn, objectClasses, userAttrs, opAttrs);
    e.processVirtualAttributes();
    return e;
  }

  /**
   * Indicates whether the provided attribute is one that is used in the
   * configuration of this backend.
   *
   * @param attribute
   *          The attribute for which to make the determination.
   * @return <CODE>true</CODE> if the provided attribute is one that is used in
   *         the configuration of this backend, <CODE>false</CODE> if not.
   */
  private boolean isMonitorConfigAttribute(final Attribute attribute)
  {
    final AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
    return attrType.hasName(ATTR_COMMON_NAME)
        || attrType.hasName(ATTR_BACKEND_ENABLED)
        || attrType.hasName(ATTR_BACKEND_CLASS)
        || attrType.hasName(ATTR_BACKEND_BASE_DN)
        || attrType.hasName(ATTR_BACKEND_ID)
        || attrType.hasName(ATTR_BACKEND_WRITABILITY_MODE);
  }
}
