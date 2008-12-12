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
package org.opends.server.backends;



import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.MonitorBackendCfg;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attributes;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;
import org.opends.server.util.Validator;



/**
 * This class defines a backend to hold Directory Server monitor entries.  It
 * will not actually store anything, but upon request will retrieve the
 * requested monitor and dynamically generate the associated entry.  It will
 * also construct a base monitor entry with some useful server-wide data.
 */
public class MonitorBackend
       extends Backend
       implements ConfigurationChangeListener<MonitorBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The set of user-defined attributes that will be included in the base
  // monitor entry.
  private ArrayList<Attribute> userDefinedAttributes;

  // The set of objectclasses that will be used in monitor entries.
  private HashMap<ObjectClass,String> monitorObjectClasses;

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
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public MonitorBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public void configureBackend(Configuration config)
         throws ConfigException
  {
    Validator.ensureNotNull(config);
    Validator.ensureTrue(config instanceof MonitorBackendCfg);

    MonitorBackendCfg cfg = (MonitorBackendCfg)config;
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(cfg.dn());


    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      Message message = ERR_MONITOR_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }

    configEntryDN = configEntry.getDN();


    // Get the set of user-defined attributes for the configuration entry.  Any
    // attributes that we don't recognize will be included directly in the base
    // monitor entry.
    userDefinedAttributes = new ArrayList<Attribute>();
    for (List<Attribute> attrs :
         configEntry.getEntry().getUserAttributes().values())
    {
      for (Attribute a : attrs)
      {
        if (! isMonitorConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
        }
      }
    }
    for (List<Attribute> attrs :
         configEntry.getEntry().getOperationalAttributes().values())
    {
      for (Attribute a : attrs)
      {
        if (! isMonitorConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
        }
      }
    }


    // Construct the set of objectclasses to include in the base monitor entry.
    monitorObjectClasses = new LinkedHashMap<ObjectClass,String>(2);
    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP, true);
    monitorObjectClasses.put(topOC, OC_TOP);

    ObjectClass monitorOC = DirectoryServer.getObjectClass(OC_MONITOR_ENTRY,
                                                           true);
    monitorObjectClasses.put(monitorOC, OC_MONITOR_ENTRY);


    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);

    // Create the set of base DNs that we will handle.  In this case, it's just
    // the DN of the base monitor entry.
    try
    {
      baseMonitorDN = DN.decode(DN_MONITOR_ROOT);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_MONITOR_CANNOT_DECODE_MONITOR_ROOT_DN.get(getExceptionMessage(e));
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
  public void initializeBackend()
         throws ConfigException, InitializationException
  {
    // Register with the Directory Server as a configurable component.
    currentConfig.addMonitorChangeListener(this);


    // Register the monitor base as a private suffix.
    try
    {
      DirectoryServer.registerBaseDN(baseMonitorDN, this, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
          baseMonitorDN.toString(), getExceptionMessage(e));
      throw new InitializationException(message, e);
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
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
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
  private boolean isMonitorConfigAttribute(Attribute attribute)
  {
    AttributeType attrType = attribute.getAttributeType();
    if (attrType.hasName(ATTR_COMMON_NAME) ||
        attrType.hasName(ATTR_BACKEND_ENABLED.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_CLASS.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_BASE_DN.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_ID.toLowerCase()) ||
        attrType.hasName(ATTR_BACKEND_WRITABILITY_MODE.toLowerCase()))
    {
      return true;
    }

    return false;
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
  public long getEntryCount()
  {
    return DirectoryServer.getMonitorProviders().size() + 1;
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
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    long ret = numSubordinates(entryDN, false);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    else if(ret == 0)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      return ConditionResult.TRUE;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(DN entryDN, boolean subtree)
         throws DirectoryException
  {
    // If the requested entry was null, then return undefined.
    if (entryDN == null)
    {
      return -1;
    }


    // If the requested entry was the monitor base entry, then return
    // the number of monitor providers.
    if (entryDN.equals(baseMonitorDN))
    {
      // This backend is only 1 level deep so the count is the same for
      // subtree and immediate subordinates.
      return DirectoryServer.getMonitorProviders().size();
    }


    // See if the monitor base entry is the immediate parent for the requested
    // entry.  If not, then its undefined.
    DN parentDN = entryDN.getParentDNInSuffix();
    if ((parentDN == null) || (! parentDN.equals(baseMonitorDN)))
    {
      return -1;
    }


    // Get the RDN for the requested DN and make sure it is single-valued.
    RDN entryRDN = entryDN.getRDN();
    if (entryRDN.isMultiValued())
    {
      return -1;
    }


    // Get the RDN value and see if it matches the instance name for one of
    // the directory server monitor providers.
    String rdnValue = entryRDN.getAttributeValue(0).getStringValue();
    MonitorProvider<? extends MonitorProviderCfg> monitorProvider =
         DirectoryServer.getMonitorProvider(rdnValue.toLowerCase());
    if (monitorProvider == null)
    {
      return -1;
    }

    return 0;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      Message message = ERR_MONITOR_GET_ENTRY_NULL.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }


    // If the requested entry was the monitor base entry, then retrieve it.
    if (entryDN.equals(baseMonitorDN))
    {
      return getBaseMonitorEntry();
    }

    if (!isATreeNode(entryDN)) {
      Message message = ERR_MONITOR_INVALID_BASE.get(
          String.valueOf(entryDN), String.valueOf(baseMonitorDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message);
    }


    // Get the RDN for the requested DN and make sure it is single-valued.
    RDN entryRDN = entryDN.getRDN();
    if (entryRDN.isMultiValued())
    {
      Message message =
          ERR_MONITOR_MULTIVALUED_RDN.get(String.valueOf(entryDN));
      throw new DirectoryException(
              ResultCode.NO_SUCH_OBJECT, message, baseMonitorDN, null);
    }

    String rdnValue = getRdns(entryDN);
    MonitorProvider<? extends MonitorProviderCfg> monitorProvider =
         DirectoryServer.getMonitorProvider(rdnValue.toLowerCase());

    // Could be a monitor branch
    if (monitorProvider == null) {
       return getBranchMonitorEntry(entryDN);
    }

    // Take the data from the monitor provider and stuff it into an entry.
    return getMonitorEntry(entryDN, monitorProvider);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
      return this.isATreeNode(entryDN);
  }



  /**
   * Retrieves the base monitor entry for the Directory Server.
   *
   * @return  The base monitor entry for the Directory Server.
   */
  public Entry getBaseMonitorEntry()
  {
    HashMap<ObjectClass,String> monitorClasses =
         new LinkedHashMap<ObjectClass,String>(3);
    monitorClasses.putAll(monitorObjectClasses);

    ObjectClass extensibleObjectOC =
         DirectoryServer.getObjectClass(OC_EXTENSIBLE_OBJECT_LC, true);
    monitorClasses.put(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);

    HashMap<AttributeType,List<Attribute>> monitorUserAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    HashMap<AttributeType,List<Attribute>> monitorOperationalAttrs =
         new LinkedHashMap<AttributeType,List<Attribute>>();


    // Add the "cn" attribute.
    Attribute cnAttr = createAttribute(ATTR_COMMON_NAME, ATTR_COMMON_NAME,
                                       "monitor");
    ArrayList<Attribute> cnList = new ArrayList<Attribute>(1);
    cnList.add(cnAttr);
    monitorUserAttrs.put(cnAttr.getAttributeType(), cnList);


    // Add the server product name.
    Attribute productNameAttr = createAttribute(ATTR_PRODUCT_NAME,
                                                ATTR_PRODUCT_NAME_LC,
                                                DynamicConstants.PRODUCT_NAME);
    ArrayList<Attribute> productNameList = new ArrayList<Attribute>(1);
    productNameList.add(productNameAttr);
    monitorUserAttrs.put(productNameAttr.getAttributeType(), productNameList);


    // Add the vendor name.
    Attribute vendorNameAttr = createAttribute(ATTR_VENDOR_NAME,
                                               ATTR_VENDOR_NAME_LC,
                                               SERVER_VENDOR_NAME);
    ArrayList<Attribute> vendorNameList = new ArrayList<Attribute>(1);
    vendorNameList.add(vendorNameAttr);
    monitorUserAttrs.put(vendorNameAttr.getAttributeType(), vendorNameList);


    // Add the vendor version.
    Attribute versionAttr = createAttribute(ATTR_VENDOR_VERSION,
                                            ATTR_VENDOR_VERSION_LC,
                                            DirectoryServer.getVersionString());
    ArrayList<Attribute> versionList = new ArrayList<Attribute>(1);
    versionList.add(versionAttr);
    monitorUserAttrs.put(versionAttr.getAttributeType(), versionList);


    // Add the server startup time.
    Attribute startTimeAttr =
         createAttribute(ATTR_START_TIME, ATTR_START_TIME_LC,
                         DirectoryServer.getStartTimeUTC());
    ArrayList<Attribute> startTimeList = new ArrayList<Attribute>(1);
    startTimeList.add(startTimeAttr);
    monitorUserAttrs.put(startTimeAttr.getAttributeType(), startTimeList);


    // Add the current time.
    Attribute currentTimeAttr =
         createAttribute(ATTR_CURRENT_TIME, ATTR_CURRENT_TIME_LC,
                         TimeThread.getGMTTime());
    ArrayList<Attribute> currentTimeList = new ArrayList<Attribute>(1);
    currentTimeList.add(currentTimeAttr);
    monitorUserAttrs.put(currentTimeAttr.getAttributeType(), currentTimeList);


    // Add the uptime as a human-readable string.
    long upSeconds =
         ((System.currentTimeMillis() - DirectoryServer.getStartTime()) / 1000);
    long upDays = (upSeconds / 86400);
    upSeconds %= 86400;
    long upHours = (upSeconds / 3600);
    upSeconds %= 3600;
    long upMinutes = (upSeconds / 60);
    upSeconds %= 60;
    Message upTimeStr =
        INFO_MONITOR_UPTIME.get(upDays, upHours, upMinutes, upSeconds);
    Attribute upTimeAttr = createAttribute(ATTR_UP_TIME, ATTR_UP_TIME_LC,
                                           upTimeStr.toString());
    ArrayList<Attribute> upTimeList = new ArrayList<Attribute>(1);
    upTimeList.add(upTimeAttr);
    monitorUserAttrs.put(upTimeAttr.getAttributeType(), upTimeList);


    // Add the number of connections currently established.
    long currentConns = DirectoryServer.getCurrentConnections();
    Attribute currentConnsAttr = createAttribute(ATTR_CURRENT_CONNS,
                                                 ATTR_CURRENT_CONNS_LC,
                                                 String.valueOf(currentConns));
    ArrayList<Attribute> currentConnsList = new ArrayList<Attribute>(1);
    currentConnsList.add(currentConnsAttr);
    monitorUserAttrs.put(currentConnsAttr.getAttributeType(), currentConnsList);


    // Add the maximum number of connections established at one time.
    long maxConns = DirectoryServer.getMaxConnections();
    Attribute maxConnsAttr = createAttribute(ATTR_MAX_CONNS,
                                             ATTR_MAX_CONNS_LC,
                                             String.valueOf(maxConns));
    ArrayList<Attribute> maxConnsList = new ArrayList<Attribute>(1);
    maxConnsList.add(maxConnsAttr);
    monitorUserAttrs.put(maxConnsAttr.getAttributeType(), maxConnsList);


    // Add the total number of connections the server has accepted.
    long totalConns = DirectoryServer.getTotalConnections();
    Attribute totalConnsAttr = createAttribute(ATTR_TOTAL_CONNS,
                                               ATTR_TOTAL_CONNS_LC,
                                               String.valueOf(totalConns));
    ArrayList<Attribute> totalConnsList = new ArrayList<Attribute>(1);
    totalConnsList.add(totalConnsAttr);
    monitorUserAttrs.put(totalConnsAttr.getAttributeType(), totalConnsList);


    // Add all the user-defined attributes.
    for (Attribute a : userDefinedAttributes)
    {
      AttributeType type = a.getAttributeType();

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
    Entry e = new Entry(baseMonitorDN, monitorClasses, monitorUserAttrs,
                        monitorOperationalAttrs);
    e.processVirtualAttributes();
    return e;
  }

   /**
   * Retrieves the branch monitor entry for the Directory Server.
   * @param dn to get.
   * @return  The branch monitor entry for the Directory Server.
   */
  public Entry getBranchMonitorEntry(DN dn) {

    Entry e=null;
    // Construct the set of objectclasses to include in the base monitor entry.
    HashMap<ObjectClass,String> monitorClasses =
         new LinkedHashMap<ObjectClass,String>(3);
    monitorClasses.putAll(monitorObjectClasses);
    ObjectClass monitorOC = DirectoryServer.getObjectClass(OC_MONITOR_BRANCH,
                                                           true);
    monitorClasses.put(monitorOC, OC_MONITOR_BRANCH);

    // Iterate through all of the monitor providers defined in the server.

    for (MonitorProvider<? extends MonitorProviderCfg> monitorProvider :
           DirectoryServer.getMonitorProviders().values()) {

      DN providerDN = DirectoryServer.getMonitorProviderDN(monitorProvider);
      if (providerDN.isDescendantOf(dn)) {
         // Construct and return the entry.
         e = new Entry(dn, monitorClasses, null, null);
         break;
      }
    }

    return e;
  }

  /**
   * Generates and returns a monitor entry based on the contents of the
   * provided monitor provider.
   *
   * @param  entryDN          The DN to use for the entry.
   * @param  monitorProvider  The monitor provider to use to obtain the
   *                          information for the entry.
   *
   * @return  The monitor entry generated from the information in the provided
   *          monitor provider.
   */
  private Entry getMonitorEntry(DN entryDN,
                     MonitorProvider<? extends MonitorProviderCfg>
                          monitorProvider)
  {
    HashMap<ObjectClass,String> monitorClasses =
         new LinkedHashMap<ObjectClass,String>(3);
    monitorClasses.putAll(monitorObjectClasses);

    ObjectClass monitorOC = monitorProvider.getMonitorObjectClass();
    monitorClasses.put(monitorOC, monitorOC.getPrimaryName());

    List<Attribute> monitorAttrs = monitorProvider.getMonitorData();
    HashMap<AttributeType,List<Attribute>> attrMap =
         new LinkedHashMap<AttributeType,List<Attribute>>(
                  monitorAttrs.size()+1);


    // Make sure to include the RDN attribute.
    RDN            entryRDN = entryDN.getRDN();
    AttributeType  rdnType  = entryRDN.getAttributeType(0);
    AttributeValue rdnValue = entryRDN.getAttributeValue(0);

    Attribute rdnAttr = Attributes.create(rdnType, rdnValue);
    ArrayList<Attribute> rdnList = new ArrayList<Attribute>(1);
    rdnList.add(rdnAttr);
    attrMap.put(rdnType, rdnList);


    // Take the rest of the information from the monitor data.
    for (Attribute a : monitorAttrs)
    {
      AttributeType type = a.getAttributeType();

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

    Entry e = new Entry(entryDN, monitorClasses, attrMap,
                        new HashMap<AttributeType,List<Attribute>>(0));
    e.processVirtualAttributes();
    return e;
  }



  /**
   * Creates an attribute for a monitor entry with the following criteria.
   *
   * @param  name       The name for the attribute.
   * @param  lowerName  The name for the attribute formatted in all lowercase
   *                    characters.
   * @param  value      The value to use for the attribute.
   *
   * @return  The constructed attribute.
   */
  private Attribute createAttribute(String name, String lowerName,
                                    String value)
  {
    return Attributes.create(name, value);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Message message =
        ERR_MONITOR_ADD_NOT_SUPPORTED.get(String.valueOf(entry.getDN()));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    Message message =
        ERR_MONITOR_DELETE_NOT_SUPPORTED.get(String.valueOf(entryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    Message message = ERR_MONITOR_MODIFY_NOT_SUPPORTED.get(
        String.valueOf(newEntry.getDN()), String.valueOf(configEntryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    Message message =
        ERR_MONITOR_MODIFY_DN_NOT_SUPPORTED.get(String.valueOf(currentDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    // Get the base entry for the search, if possible.  If it doesn't exist,
    // then this will throw an exception.
    DN    baseDN    = searchOperation.getBaseDN();
    Entry baseEntry = getEntry(baseDN);

    if (baseEntry==null) {
      Message message =
          ERR_MONITOR_NO_SUCH_PROVIDER.get(String.valueOf(baseDN.toString()));
      throw new DirectoryException(
              ResultCode.NO_SUCH_OBJECT, message, baseMonitorDN, null);

    }

    // Figure out whether the base is the monitor base entry or one of its
    // children for a specific monitor.
    SearchScope  scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();
    if ((scope == SearchScope.BASE_OBJECT) ||
        (scope == SearchScope.WHOLE_SUBTREE))
    {
      if (filter.matchesEntry(baseEntry))
      {
        searchOperation.returnEntry(baseEntry, null);
      }


      // If it is a base-level search, then we're done.
      if (scope == SearchScope.BASE_OBJECT)
      {
        return;
      }
    }

    HashSet<DN> branches = new HashSet<DN>();
    // Iterate through all of the monitor providers defined in the server.
    // Get an entry for each and compare it against the filter.
    for (MonitorProvider<? extends MonitorProviderCfg> monitorProvider :
           DirectoryServer.getMonitorProviders().values()) {

      DN providerDN = DirectoryServer.getMonitorProviderDN(monitorProvider);
      if (baseDN.isAncestorOf(providerDN)) {
         DN parentDN = providerDN.getParentDNInSuffix();
         while ((parentDN!=null) && (!parentDN.equals(baseMonitorDN)) &&
                 (!parentDN.equals(baseDN))) {
            if ((!branches.contains(parentDN)) && (
                    isABranch(parentDN))) {
              Entry branchEntry = getBranchMonitorEntry(parentDN);
              if (filter.matchesEntry(branchEntry)) {
                  searchOperation.returnEntry(branchEntry, null);
              }
              branches.add(parentDN);
            }
            parentDN = parentDN.getParentDNInSuffix();
         }
         if (!providerDN.equals(baseDN)) {
             Entry monitorEntry = getMonitorEntry(providerDN, monitorProvider);
             if (filter.matchesEntry(monitorEntry)) {
                searchOperation.returnEntry(monitorEntry, null);
             }
         }

      }
    }
  }

  private boolean isABranch(DN dn) {
      boolean found=false;
      for (MonitorProvider<? extends MonitorProviderCfg> monitorProvider :
           DirectoryServer.getMonitorProviders().values()) {
         DN providerDN = DirectoryServer.getMonitorProviderDN(monitorProvider);
         if (dn.equals(providerDN)) {
             found=true;
         }
      }
      return (!found);
  }

  private boolean isATreeNode(DN dn) {
      boolean found=false;

      if (dn.equals(baseMonitorDN)) {
          return true;
      }
      for (MonitorProvider<? extends MonitorProviderCfg> monitorProvider :
           DirectoryServer.getMonitorProviders().values()) {
         DN providerDN = DirectoryServer.getMonitorProviderDN(monitorProvider);
         if ((dn.isAncestorOf(providerDN)) ||
                 (dn.equals(providerDN))) {
             found=true;
             return (found);
         }
      }
      return (found);
  }

  private String getRdns(DN dn) {
      int index = dn.toString().lastIndexOf(",");
      return dn.toString().substring(3, index);
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
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // TODO export-ldif reports nonsense for upTime etc.

    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER.get(
          stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }


    // Write the base monitor entry to the LDIF.
    try
    {
      ldifWriter.writeEntry(getBaseMonitorEntry());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        ldifWriter.close();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }
      }

      Message message = ERR_MONITOR_UNABLE_TO_EXPORT_BASE.get(
          stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }


    // Get all the monitor providers, convert them to entries, and write them to
    // LDIF.
    for (MonitorProvider<?> monitorProvider :
         DirectoryServer.getMonitorProviders().values())
    {
      try
      {
        // TODO implementation of export is incomplete
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        try
        {
          ldifWriter.close();
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }
        }

        Message message = ERR_MONITOR_UNABLE_TO_EXPORT_PROVIDER_ENTRY.
            get(monitorProvider.getMonitorInstanceName(),
                stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message);
      }
    }


    // Close the monitor provider and return.
    try
    {
      ldifWriter.close();
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
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    // This backend does not support LDIF imports.
    Message message = ERR_MONITOR_IMPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
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
   * {@inheritDoc}
   */
  @Override()
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       MonitorBackendCfg backendCfg,
       List<Message> unacceptableReasons)
  {
    // We'll pretty much accept anything here as long as it isn't one of our
    // private attributes.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 MonitorBackendCfg backendCfg)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Check to see if there is a new set of user-defined attributes.
    ArrayList<Attribute> userAttrs = new ArrayList<Attribute>();
    try
    {
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(configEntryDN);
      for (List<Attribute> attrs :
           configEntry.getEntry().getUserAttributes().values())
      {
        for (Attribute a : attrs)
        {
          if (! isMonitorConfigAttribute(a))
          {
            userAttrs.add(a);
          }
        }
      }
      for (List<Attribute> attrs :
           configEntry.getEntry().getOperationalAttributes().values())
      {
        for (Attribute a : attrs)
        {
          if (! isMonitorConfigAttribute(a))
          {
            userAttrs.add(a);
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }


      messages.add(ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY.get(
              String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    userDefinedAttributes = userAttrs;

    Message message = INFO_MONITOR_USING_NEW_USER_ATTRS.get();
    messages.add(message);


    currentConfig = backendCfg;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}
