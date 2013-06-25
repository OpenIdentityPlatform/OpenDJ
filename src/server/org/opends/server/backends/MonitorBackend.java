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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */
package org.opends.server.backends;



import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.*;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.MonitorBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;
import org.opends.server.util.Validator;



/**
 * This class defines a backend to hold Directory Server monitor entries. It
 * will not actually store anything, but upon request will retrieve the
 * requested monitor and dynamically generate the associated entry. It will also
 * construct a base monitor entry with some useful server-wide data.
 */
public class MonitorBackend extends Backend implements
    ConfigurationChangeListener<MonitorBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of user-defined attributes that will be included in the base
  // monitor entry.
  private ArrayList<Attribute> userDefinedAttributes;

  // The set of objectclasses that will be used in monitor entries.
  private HashMap<ObjectClass, String> monitorObjectClasses;

  // The DN of the configuration entry for this backend.
  private DN configEntryDN;

  // The current configuration state.
  private MonitorBackendCfg currentConfig;

  // The DN for the base monitor entry.
  private DN baseMonitorDN;

  // The set of base DNs for this backend.
  private DN[] baseDNs;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;



  /**
   * Creates a new backend with the provided information. All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public MonitorBackend()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addEntry(final Entry entry, final AddOperation addOperation)
      throws DirectoryException
  {
    final Message message = ERR_MONITOR_ADD_NOT_SUPPORTED.get(String
        .valueOf(entry.getDN()));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      final MonitorBackendCfg backendCfg)
  {
    ResultCode resultCode = ResultCode.SUCCESS;
    final boolean adminActionRequired = false;
    final ArrayList<Message> messages = new ArrayList<Message>();

    // Check to see if there is a new set of user-defined attributes.
    final ArrayList<Attribute> userAttrs = new ArrayList<Attribute>();
    try
    {
      final ConfigEntry configEntry = DirectoryServer
          .getConfigEntry(configEntryDN);
      for (final List<Attribute> attrs : configEntry.getEntry()
          .getUserAttributes().values())
      {
        for (final Attribute a : attrs)
        {
          if (!isMonitorConfigAttribute(a))
          {
            userAttrs.add(a);
          }
        }
      }
      for (final List<Attribute> attrs : configEntry.getEntry()
          .getOperationalAttributes().values())
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
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      messages.add(ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY.get(
          String.valueOf(configEntryDN), stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }

    userDefinedAttributes = userAttrs;

    final Message message = INFO_MONITOR_USING_NEW_USER_ATTRS.get();
    messages.add(message);

    currentConfig = backendCfg;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void configureBackend(final Configuration config)
      throws ConfigException
  {
    Validator.ensureNotNull(config);
    Validator.ensureTrue(config instanceof MonitorBackendCfg);

    final MonitorBackendCfg cfg = (MonitorBackendCfg) config;
    final ConfigEntry configEntry = DirectoryServer.getConfigEntry(cfg.dn());

    // Make sure that a configuration entry was provided. If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      final Message message = ERR_MONITOR_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }

    configEntryDN = configEntry.getDN();

    // Get the set of user-defined attributes for the configuration entry. Any
    // attributes that we don't recognize will be included directly in the base
    // monitor entry.
    userDefinedAttributes = new ArrayList<Attribute>();
    for (final List<Attribute> attrs : configEntry.getEntry()
        .getUserAttributes().values())
    {
      for (final Attribute a : attrs)
      {
        if (!isMonitorConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
        }
      }
    }
    for (final List<Attribute> attrs : configEntry.getEntry()
        .getOperationalAttributes().values())
    {
      for (final Attribute a : attrs)
      {
        if (!isMonitorConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
        }
      }
    }

    // Construct the set of objectclasses to include in the base monitor entry.
    monitorObjectClasses = new LinkedHashMap<ObjectClass, String>(2);
    final ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP, true);
    monitorObjectClasses.put(topOC, OC_TOP);

    final ObjectClass monitorOC = DirectoryServer.getObjectClass(
        OC_MONITOR_ENTRY, true);
    monitorObjectClasses.put(monitorOC, OC_MONITOR_ENTRY);

    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);

    // Create the set of base DNs that we will handle. In this case, it's just
    // the DN of the base monitor entry.
    try
    {
      baseMonitorDN = DN.decode(DN_MONITOR_ROOT);
    }
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      final Message message = ERR_MONITOR_CANNOT_DECODE_MONITOR_ROOT_DN
          .get(getExceptionMessage(e));
      throw new ConfigException(message, e);
    }

    // FIXME -- Deal with this more correctly.
    this.baseDNs = new DN[] { baseMonitorDN };

    currentConfig = cfg;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void createBackup(final BackupConfig backupConfig)
      throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    final Message message = ERR_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void deleteEntry(final DN entryDN,
      final DeleteOperation deleteOperation) throws DirectoryException
  {
    final Message message = ERR_MONITOR_DELETE_NOT_SUPPORTED.get(String
        .valueOf(entryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean entryExists(final DN entryDN) throws DirectoryException
  {
    return getDIT().containsKey(entryDN);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void exportLDIF(final LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    // TODO export-ldif reports nonsense for upTime etc.

    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      final Message message = ERR_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER
          .get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
    }

    // Write the base monitor entry to the LDIF.
    try
    {
      ldifWriter.writeEntry(getBaseMonitorEntry());
    }
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        ldifWriter.close();
      }
      catch (final Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }

      final Message message = ERR_MONITOR_UNABLE_TO_EXPORT_BASE
          .get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
    }

    // Get all the monitor providers, convert them to entries, and write them to
    // LDIF.
    for (final MonitorProvider<?> monitorProvider : DirectoryServer
        .getMonitorProviders().values())
    {
      try
      {
        // TODO implementation of export is incomplete
      }
      catch (final Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        try
        {
          ldifWriter.close();
        }
        catch (final Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }
        }

        final Message message = ERR_MONITOR_UNABLE_TO_EXPORT_PROVIDER_ENTRY
            .get(monitorProvider.getMonitorInstanceName(),
                stackTraceToSingleLineString(e));
        throw new DirectoryException(
            DirectoryServer.getServerErrorResultCode(), message);
      }
    }

    // Close the monitor provider and return.
    try
    {
      ldifWriter.close();
    }
    catch (final Exception e)
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
  @Override()
  public void finalizeBackend()
  {
    currentConfig.removeMonitorChangeListener(this);
    try
    {
      DirectoryServer.deregisterBaseDN(baseMonitorDN);
    }
    catch (final Exception e)
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
  @Override()
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry getEntry(final DN entryDN) throws DirectoryException
  {
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      final Message message = ERR_MONITOR_GET_ENTRY_NULL.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message);
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
      final Message message = ERR_MONITOR_INVALID_BASE.get(
          String.valueOf(entryDN), String.valueOf(baseMonitorDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }

    // The DN is associated with a valid monitor/glue entry.
    return getEntry(entryDN, dit);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long getEntryCount()
  {
    return getDIT().size();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult hasSubordinates(final DN entryDN)
      throws DirectoryException
  {
    final NavigableMap<DN, MonitorProvider<?>> dit = getDIT();
    if (!dit.containsKey(entryDN))
    {
      return ConditionResult.UNDEFINED;
    }
    else
    {
      final DN nextDN = dit.higherKey(entryDN);
      if (nextDN == null || !nextDN.isDescendantOf(entryDN))
      {
        return ConditionResult.FALSE;
      }
      else
      {
        return ConditionResult.TRUE;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public LDIFImportResult importLDIF(final LDIFImportConfig importConfig)
      throws DirectoryException
  {
    // This backend does not support LDIF imports.
    final Message message = ERR_MONITOR_IMPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeBackend() throws ConfigException,
      InitializationException
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      final Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          baseMonitorDN.toString(), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      final MonitorBackendCfg backendCfg,
      final List<Message> unacceptableReasons)
  {
    // We'll pretty much accept anything here as long as it isn't one of our
    // private attributes.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isIndexed(final AttributeType attributeType,
      final IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isLocal()
  {
    // For the purposes of this method, this is a local backend.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(final DN entryDN, final boolean subtree)
      throws DirectoryException
  {
    final NavigableMap<DN, MonitorProvider<?>> dit = getDIT();
    if (!dit.containsKey(entryDN))
    {
      return -1L;
    }
    else
    {
      long count = 0;
      final int childDNSize = entryDN.getNumComponents() + 1;
      for (final DN dn : dit.tailMap(entryDN, false).navigableKeySet())
      {
        if (!dn.isDescendantOf(entryDN))
        {
          break;
        }
        else if (subtree || dn.getNumComponents() == childDNSize)
        {
          count++;
        }
      }
      return count;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException("Operation not supported.");
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(final BackupDirectory backupDirectory,
      final String backupID) throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    final Message message = ERR_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void renameEntry(final DN currentDN, final Entry entry,
      final ModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    final Message message = ERR_MONITOR_MODIFY_DN_NOT_SUPPORTED.get(String
        .valueOf(currentDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void replaceEntry(final Entry oldEntry, final Entry newEntry,
      final ModifyOperation modifyOperation) throws DirectoryException
  {
    final Message message = ERR_MONITOR_MODIFY_NOT_SUPPORTED.get(
        String.valueOf(newEntry.getDN()), String.valueOf(configEntryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void restoreBackup(final RestoreConfig restoreConfig)
      throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    final Message message = ERR_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      DN matchedDN = baseDN.getParent();
      while (matchedDN != null)
      {
        if (dit.containsKey(matchedDN))
        {
          break;
        }
        matchedDN = matchedDN.getParent();
      }
      final Message message = ERR_MEMORYBACKEND_ENTRY_DOESNT_EXIST.get(String
          .valueOf(baseDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
          matchedDN, null);
    }

    // Walk through all entries and send the ones that match.
    for (final Map.Entry<DN, MonitorProvider<?>> e : dit.tailMap(baseDN)
        .entrySet())
    {
      final DN dn = e.getKey();
      if (dn.matchesBaseAndScope(baseDN, scope))
      {
        final Entry entry = getEntry(dn, dit);
        if (filter.matchesEntry(entry))
        {
          searchOperation.returnEntry(entry, null);
        }
      }
      else if (scope == SearchScope.BASE_OBJECT || !dn.isDescendantOf(baseDN))
      {
        // No more entries will be in scope.
        break;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup(final BackupConfig backupConfig,
      final StringBuilder unsupportedReason)
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFExport()
  {
    // We can export all the monitor entries as a point-in-time snapshot.
    // TODO implementation of export is incomplete
    // TODO export-ldif reports nonsense for upTime etc.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsRestore()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * Creates an attribute for a monitor entry with the following criteria.
   *
   * @param name
   *          The name for the attribute.
   * @param lowerName
   *          The name for the attribute formatted in all lowercase characters.
   * @param value
   *          The value to use for the attribute.
   * @return The constructed attribute.
   */
  private Attribute createAttribute(final String name, final String lowerName,
      final String value)
  {
    return Attributes.create(name, value);
  }



  /**
   * Retrieves the base monitor entry for the Directory Server.
   *
   * @return The base monitor entry for the Directory Server.
   */
  private Entry getBaseMonitorEntry()
  {
    final HashMap<ObjectClass, String> monitorClasses =
        new LinkedHashMap<ObjectClass, String>(3);
    monitorClasses.putAll(monitorObjectClasses);

    final ObjectClass extensibleObjectOC = DirectoryServer.getObjectClass(
        OC_EXTENSIBLE_OBJECT_LC, true);
    monitorClasses.put(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);

    final HashMap<AttributeType, List<Attribute>> monitorUserAttrs =
        new LinkedHashMap<AttributeType, List<Attribute>>();
    final HashMap<AttributeType, List<Attribute>> monitorOperationalAttrs =
        new LinkedHashMap<AttributeType, List<Attribute>>();

    // Add the "cn" attribute.
    final Attribute cnAttr = createAttribute(ATTR_COMMON_NAME,
        ATTR_COMMON_NAME, "monitor");
    final ArrayList<Attribute> cnList = new ArrayList<Attribute>(1);
    cnList.add(cnAttr);
    monitorUserAttrs.put(cnAttr.getAttributeType(), cnList);

    // Add the server product name.
    final Attribute productNameAttr = createAttribute(ATTR_PRODUCT_NAME,
        ATTR_PRODUCT_NAME_LC, DynamicConstants.PRODUCT_NAME);
    final ArrayList<Attribute> productNameList = new ArrayList<Attribute>(1);
    productNameList.add(productNameAttr);
    monitorUserAttrs.put(productNameAttr.getAttributeType(), productNameList);

    // Add the vendor name.
    final Attribute vendorNameAttr = createAttribute(ATTR_VENDOR_NAME,
        ATTR_VENDOR_NAME_LC, SERVER_VENDOR_NAME);
    final ArrayList<Attribute> vendorNameList = new ArrayList<Attribute>(1);
    vendorNameList.add(vendorNameAttr);
    monitorUserAttrs.put(vendorNameAttr.getAttributeType(), vendorNameList);

    // Add the vendor version.
    final Attribute versionAttr = createAttribute(ATTR_VENDOR_VERSION,
        ATTR_VENDOR_VERSION_LC, DirectoryServer.getVersionString());
    final ArrayList<Attribute> versionList = new ArrayList<Attribute>(1);
    versionList.add(versionAttr);
    monitorUserAttrs.put(versionAttr.getAttributeType(), versionList);

    // Add the server startup time.
    final Attribute startTimeAttr = createAttribute(ATTR_START_TIME,
        ATTR_START_TIME_LC, DirectoryServer.getStartTimeUTC());
    final ArrayList<Attribute> startTimeList = new ArrayList<Attribute>(1);
    startTimeList.add(startTimeAttr);
    monitorUserAttrs.put(startTimeAttr.getAttributeType(), startTimeList);

    // Add the current time.
    final Attribute currentTimeAttr = createAttribute(ATTR_CURRENT_TIME,
        ATTR_CURRENT_TIME_LC, TimeThread.getGMTTime());
    final ArrayList<Attribute> currentTimeList = new ArrayList<Attribute>(1);
    currentTimeList.add(currentTimeAttr);
    monitorUserAttrs.put(currentTimeAttr.getAttributeType(), currentTimeList);

    // Add the uptime as a human-readable string.
    long upSeconds = ((System.currentTimeMillis() - DirectoryServer
        .getStartTime()) / 1000);
    final long upDays = (upSeconds / 86400);
    upSeconds %= 86400;
    final long upHours = (upSeconds / 3600);
    upSeconds %= 3600;
    final long upMinutes = (upSeconds / 60);
    upSeconds %= 60;
    final Message upTimeStr = INFO_MONITOR_UPTIME.get(upDays, upHours,
        upMinutes, upSeconds);
    final Attribute upTimeAttr = createAttribute(ATTR_UP_TIME, ATTR_UP_TIME_LC,
        upTimeStr.toString());
    final ArrayList<Attribute> upTimeList = new ArrayList<Attribute>(1);
    upTimeList.add(upTimeAttr);
    monitorUserAttrs.put(upTimeAttr.getAttributeType(), upTimeList);

    // Add the number of connections currently established.
    final long currentConns = DirectoryServer.getCurrentConnections();
    final Attribute currentConnsAttr = createAttribute(ATTR_CURRENT_CONNS,
        ATTR_CURRENT_CONNS_LC, String.valueOf(currentConns));
    final ArrayList<Attribute> currentConnsList = new ArrayList<Attribute>(1);
    currentConnsList.add(currentConnsAttr);
    monitorUserAttrs.put(currentConnsAttr.getAttributeType(), currentConnsList);

    // Add the maximum number of connections established at one time.
    final long maxConns = DirectoryServer.getMaxConnections();
    final Attribute maxConnsAttr = createAttribute(ATTR_MAX_CONNS,
        ATTR_MAX_CONNS_LC, String.valueOf(maxConns));
    final ArrayList<Attribute> maxConnsList = new ArrayList<Attribute>(1);
    maxConnsList.add(maxConnsAttr);
    monitorUserAttrs.put(maxConnsAttr.getAttributeType(), maxConnsList);

    // Add the total number of connections the server has accepted.
    final long totalConns = DirectoryServer.getTotalConnections();
    final Attribute totalConnsAttr = createAttribute(ATTR_TOTAL_CONNS,
        ATTR_TOTAL_CONNS_LC, String.valueOf(totalConns));
    final ArrayList<Attribute> totalConnsList = new ArrayList<Attribute>(1);
    totalConnsList.add(totalConnsAttr);
    monitorUserAttrs.put(totalConnsAttr.getAttributeType(), totalConnsList);

    // Add all the user-defined attributes.
    for (final Attribute a : userDefinedAttributes)
    {
      final AttributeType type = a.getAttributeType();

      if (type.isOperational())
      {
        List<Attribute> attrs = monitorOperationalAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          monitorOperationalAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
      else
      {
        List<Attribute> attrs = monitorUserAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          monitorUserAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
    }

    // Construct and return the entry.
    final Entry e = new Entry(baseMonitorDN, monitorClasses, monitorUserAttrs,
        monitorOperationalAttrs);
    e.processVirtualAttributes();
    return e;
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

    final HashMap<ObjectClass, String> monitorClasses =
        new LinkedHashMap<ObjectClass, String>(3);
    monitorClasses.putAll(monitorObjectClasses);
    final ObjectClass monitorOC = DirectoryServer.getObjectClass(
        OC_MONITOR_BRANCH, true);
    monitorClasses.put(monitorOC, OC_MONITOR_BRANCH);

    final HashMap<AttributeType, List<Attribute>> monitorUserAttrs =
        new LinkedHashMap<AttributeType, List<Attribute>>();

    final RDN rdn = dn.getRDN();
    if (rdn != null)
    {
      // Add the RDN values
      for (int i = 0; i < rdn.getNumValues(); i++)
      {
        final AttributeType attributeType = rdn.getAttributeType(i);
        final AttributeValue value = rdn.getAttributeValue(attributeType);
        final Attribute attr = Attributes.create(attributeType, value);
        final List<Attribute> attrList = new ArrayList<Attribute>(1);
        attrList.add(attr);
        monitorUserAttrs.put(attributeType, attrList);
      }
    }

    // Construct and return the entry.
    final Entry e = new Entry(dn, monitorClasses, monitorUserAttrs, null);
    e.processVirtualAttributes();
    return e;
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
    final NavigableMap<DN, MonitorProvider<?>> dit =
        new TreeMap<DN, MonitorProvider<?>>();
    for (final MonitorProvider<?> monitorProvider : DirectoryServer
        .getMonitorProviders().values())
    {
      DN dn = DirectoryServer.getMonitorProviderDN(monitorProvider);
      dit.put(dn, monitorProvider);

      // Added glue records.
      for (dn = dn.getParent(); dn != null; dn = dn.getParent())
      {
        if (dit.containsKey(dn))
        {
          break;
        }
        else
        {
          dit.put(dn, null);
        }
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
    final HashMap<ObjectClass, String> monitorClasses =
        new LinkedHashMap<ObjectClass, String>(
        3);
    monitorClasses.putAll(monitorObjectClasses);

    final ObjectClass monitorOC = monitorProvider.getMonitorObjectClass();
    monitorClasses.put(monitorOC, monitorOC.getPrimaryName());

    final List<Attribute> monitorAttrs = monitorProvider.getMonitorData();
    final HashMap<AttributeType, List<Attribute>> attrMap =
        new LinkedHashMap<AttributeType, List<Attribute>>(
          monitorAttrs.size() + 1);

    // Make sure to include the RDN attribute.
    final RDN entryRDN = entryDN.getRDN();
    final AttributeType rdnType = entryRDN.getAttributeType(0);
    final AttributeValue rdnValue = entryRDN.getAttributeValue(0);

    final Attribute rdnAttr = Attributes.create(rdnType, rdnValue);
    final ArrayList<Attribute> rdnList = new ArrayList<Attribute>(1);
    rdnList.add(rdnAttr);
    attrMap.put(rdnType, rdnList);

    // Take the rest of the information from the monitor data.
    for (final Attribute a : monitorAttrs)
    {
      final AttributeType type = a.getAttributeType();

      List<Attribute> attrs = attrMap.get(type);
      if (attrs == null)
      {
        attrs = new ArrayList<Attribute>();
        attrs.add(a);
        attrMap.put(type, attrs);
      }
      else
      {
        attrs.add(a);
      }
    }

    final Entry e = new Entry(entryDN, monitorClasses, attrMap,
        new HashMap<AttributeType, List<Attribute>>(0));
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
    final AttributeType attrType = attribute.getAttributeType();
    if (attrType.hasName(ATTR_COMMON_NAME)
        || attrType.hasName(ATTR_BACKEND_ENABLED.toLowerCase())
        || attrType.hasName(ATTR_BACKEND_CLASS.toLowerCase())
        || attrType.hasName(ATTR_BACKEND_BASE_DN.toLowerCase())
        || attrType.hasName(ATTR_BACKEND_ID.toLowerCase())
        || attrType.hasName(ATTR_BACKEND_WRITABILITY_MODE.toLowerCase()))
    {
      return true;
    }

    return false;
  }

}
