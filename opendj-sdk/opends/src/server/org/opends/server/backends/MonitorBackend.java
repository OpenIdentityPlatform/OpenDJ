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



import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.api.Backend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.BackendMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.BackendCfg;


/**
 * This class defines a backend to hold Directory Server monitor entries.  It
 * will not actually store anything, but upon request will retrieve the
 * requested monitor and dynamically generate the associated entry.  It will
 * also construct a base monitor entry with some useful server-wide data.
 */
public class MonitorBackend
       extends Backend
       implements ConfigurationChangeListener<BackendCfg>
{
  // The set of user-defined attributes that will be included in the base
  // monitor entry.
  private ArrayList<Attribute> userDefinedAttributes;

  // The set of objectclasses that will be used in monitor entries.
  private HashMap<ObjectClass,String> monitorObjectClasses;

  // The DN of the configuration entry for this backend.
  private DN configEntryDN;

  // The current configuration state.
  private BackendCfg currentConfig;

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
  public void initializeBackend(ConfigEntry configEntry, DN[] baseDNs)
         throws ConfigException, InitializationException
  {
    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      int    msgID   = MSGID_MONITOR_CONFIG_ENTRY_NULL;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }

    BackendCfg cfg = BackendConfigManager.getBackendCfg(configEntry);
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_MONITOR_CANNOT_DECODE_MONITOR_ROOT_DN;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }

    // FIXME -- Deal with this more correctly.
    this.baseDNs = new DN[] { baseMonitorDN };


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


    // Register with the Directory Server as a configurable component.
    currentConfig = cfg;
    cfg.addChangeListener(this);


    // Register the monitor base as a private suffix.
    try
    {
      DirectoryServer.registerBaseDN(baseMonitorDN, this, true, false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_BACKEND_CANNOT_REGISTER_BASEDN;
      String message = getMessage(msgID, baseMonitorDN.toString(),
                                  getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }
  }



  /**
   * Performs any necessary work to finalize this backend, including closing any
   * underlying databases or connections and deregistering any suffixes that it
   * manages with the Directory Server.  This may be called during the
   * Directory Server shutdown process or if a backend is disabled with the
   * server online.  It must not return until the backend is closed.
   * <BR><BR>
   * This method may not throw any exceptions.  If any problems are encountered,
   * then they may be logged but the closure should progress as completely as
   * possible.
   */
  public void finalizeBackend()
  {
    currentConfig.removeChangeListener(this);

    try
    {
      DirectoryServer.deregisterBaseDN(baseMonitorDN, false);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
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
   * Retrieves the set of base-level DNs that may be used within this backend.
   *
   * @return  The set of base-level DNs that may be used within this backend.
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
    return DirectoryServer.getMonitorProviders().size() + 1;
  }



  /**
   * Indicates whether the data associated with this backend may be considered
   * local (i.e., in a repository managed by the Directory Server) rather than
   * remote (i.e., in an external repository accessed by the Directory Server
   * but managed through some other means).
   *
   * @return  <CODE>true</CODE> if the data associated with this backend may be
   *          considered local, or <CODE>false</CODE> if it is remote.
   */
  public boolean isLocal()
  {
    // For the purposes of this method, this is a local backend.
    return true;
  }



  /**
   * Retrieves the requested entry from this backend.
   *
   * @param  entryDN  The distinguished name of the entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if the entry does not
   *          exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to retrieve
   *                              the entry.
   */
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the requested entry was null, then throw an exception.
    if (entryDN == null)
    {
      int    msgID   = MSGID_MONITOR_GET_ENTRY_NULL;
      String message = getMessage(msgID);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }


    // If the requested entry was the monitor base entry, then retrieve it.
    if (entryDN.equals(baseMonitorDN))
    {
      return getBaseMonitorEntry();
    }


    // See if the monitor base entry is the immediate parent for the requested
    // entry.  If not, then throw an exception.
    DN parentDN = entryDN.getParentDNInSuffix();
    if ((parentDN == null) || (! parentDN.equals(baseMonitorDN)))
    {
      if (baseMonitorDN.isAncestorOf(entryDN))
      {
        int    msgID   = MSGID_MONITOR_BASE_TOO_DEEP;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    String.valueOf(baseMonitorDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                     baseMonitorDN, null);
      }
      else
      {
        int    msgID   = MSGID_MONITOR_INVALID_BASE;
        String message = getMessage(msgID, String.valueOf(entryDN),
                                    String.valueOf(baseMonitorDN));
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID);
      }
    }


    // Get the RDN for the requested DN and make sure it is single-valued.
    RDN entryRDN = entryDN.getRDN();
    if (entryRDN.isMultiValued())
    {
      int msgID = MSGID_MONITOR_MULTIVALUED_RDN;
      String message = getMessage(msgID, String.valueOf(entryDN));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                   baseMonitorDN, null);
    }


    // Get the RDN value and see if it matches the instance name for one of
    // the directory server monitor providers.
    String rdnValue = entryRDN.getAttributeValue(0).getStringValue();
    MonitorProvider monitorProvider =
         DirectoryServer.getMonitorProvider(rdnValue.toLowerCase());
    if (monitorProvider == null)
    {
      int    msgID   = MSGID_MONITOR_NO_SUCH_PROVIDER;
      String message = getMessage(msgID, String.valueOf(rdnValue));
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message, msgID,
                                   baseMonitorDN, null);
    }


    // Take the data from the monitor provider and stuff it into an entry.
    return getMonitorEntry(entryDN, monitorProvider);
  }



  /**
   * Indicates whether an entry with the specified DN exists in the backend.
   * The default implementation obtains a read lock and calls
   * <CODE>getEntry</CODE>, but backend implementations may override this with a
   * more efficient version that does not require a lock.  The caller is not
   * required to hold any locks on the specified DN.
   *
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists in this backend,
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   */
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    if (entryDN.equals(baseMonitorDN))
    {
      return true;
    }

    DN parentDN = entryDN.getParentDNInSuffix();
    if ((parentDN == null) || (! parentDN.equals(baseMonitorDN)))
    {
      return false;
    }

    RDN rdn = entryDN.getRDN();
    if (rdn.isMultiValued())
    {
      return false;
    }

    String rdnValue = rdn.getAttributeValue(0).getStringValue();
    MonitorProvider monitorProvider =
         DirectoryServer.getMonitorProvider(toLowerCase(rdnValue));
    return (monitorProvider != null);
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
    String upTimeStr = getMessage(MSGID_MONITOR_UPTIME, upDays, upHours,
                                  upMinutes, upSeconds);
    Attribute upTimeAttr = createAttribute(ATTR_UP_TIME, ATTR_UP_TIME_LC,
                                           upTimeStr);
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
  private Entry getMonitorEntry(DN entryDN, MonitorProvider monitorProvider)
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

    LinkedHashSet<AttributeValue> rdnValues =
         new LinkedHashSet<AttributeValue>(1);
    rdnValues.add(rdnValue);

    Attribute rdnAttr = new Attribute(rdnType, entryRDN.getAttributeName(0),
                                      rdnValues);
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
    AttributeType type = DirectoryServer.getAttributeType(lowerName);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(name);
    }

    LinkedHashSet<AttributeValue> attrValues =
         new LinkedHashSet<AttributeValue>(1);
    attrValues.add(new AttributeValue(type, new ASN1OctetString(value)));

    return new Attribute(type, name, attrValues);
  }



  /**
   * Adds the provided entry to this backend.  This method must ensure that the
   * entry is appropriate for the backend and that no entry already exists with
   * the same DN.
   *
   * @param  entry         The entry to add to this backend.
   * @param  addOperation  The add operation with which the new entry is
   *                       associated.  This may be <CODE>null</CODE> for adds
   *                       performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to add the
   *                              entry.
   */
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    int    msgID   = MSGID_MONITOR_ADD_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(entry.getDN()));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Removes the specified entry from this backend.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the backend supports a subtree delete operation and the client
   * included the appropriate information in the request).
   *
   * @param  entryDN          The DN of the entry to remove from this backend.
   * @param  deleteOperation  The delete operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          deletes performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to remove the
   *                              entry.
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    int    msgID   = MSGID_MONITOR_DELETE_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(entryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Replaces the specified entry with the provided entry in this backend.  The
   * backend must ensure that an entry already exists with the same DN as the
   * provided entry.
   *
   * @param  entry            The new entry to use in place of the existing
   *                          entry with the same DN.
   * @param  modifyOperation  The modify operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          modifications performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to replace
   *                              the entry.
   */
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
         throws DirectoryException
  {
    int    msgID   = MSGID_MONITOR_MODIFY_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(entry.getDN()),
                                String.valueOf(configEntryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.
   *
   * @param  currentDN          The current DN of the entry to be replaced.
   * @param  entry              The new content to use for the entry.
   * @param  modifyDNOperation  The modify DN operation with which this action
   *                            is associated.  This may be <CODE>null</CODE>
   *                            for modify DN operations performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to perform
   *                              the rename.
   */
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    int    msgID   = MSGID_MONITOR_MODIFY_DN_NOT_SUPPORTED;
    String message = getMessage(msgID, String.valueOf(currentDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Processes the specified search in this backend.  Matching entries should be
   * provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param  searchOperation  The search operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs while processing the
   *                              search.
   */
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    // Get the base entry for the search, if possible.  If it doesn't exist,
    // then this will throw an exception.
    DN    baseDN    = searchOperation.getBaseDN();
    Entry baseEntry = getEntry(baseDN);


    // Figure out whether the base is the monitor base entry or one of its
    // children for a specific monitor.
    SearchScope  scope  = searchOperation.getScope();
    SearchFilter filter = searchOperation.getFilter();
    if (baseMonitorDN.equals(baseDN))
    {
      // If it is a base-level or subtree search, then we need to look at the
      // base monitor entry.
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


      // Iterate through all of the monitor providers defined in the server.
      // Get an entry for each and compare it against the filter.
      for (MonitorProvider monitorProvider :
           DirectoryServer.getMonitorProviders().values())
      {
        DN providerDN = DirectoryServer.getMonitorProviderDN(monitorProvider);
        Entry monitorEntry = getMonitorEntry(providerDN, monitorProvider);
        if (filter.matchesEntry(monitorEntry))
        {
          searchOperation.returnEntry(monitorEntry, null);
        }
      }
    }
    else
    {
      // Look at the scope for the search.  We only need to return something if
      // it is a base-level or subtree search.
      if ((scope == SearchScope.BASE_OBJECT) ||
          (scope == SearchScope.WHOLE_SUBTREE))
      {
        if (filter.matchesEntry(baseEntry))
        {
          searchOperation.returnEntry(baseEntry, null);
        }
      }
    }
  }



  /**
   * Retrieves the OIDs of the controls that may be supported by this backend.
   *
   * @return  The OIDs of the controls that may be supported by this backend.
   */
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return  The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }



  /**
   * Indicates whether this backend provides a mechanism to export the data it
   * contains to an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF export
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFExport()
  {
    // We can export all the monitor entries as a point-in-time snapshot.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void exportLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFExportConfig exportConfig)
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
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        ldifWriter.close();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e2);
        }
      }

      int    msgID   = MSGID_MONITOR_UNABLE_TO_EXPORT_BASE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }


    // Get all the monitor providers, convert them to entries, and write them to
    // LDIF.
    for (MonitorProvider monitorProvider :
         DirectoryServer.getMonitorProviders().values())
    {
      try
      {
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        try
        {
          ldifWriter.close();
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e2);
          }
        }

        int    msgID   = MSGID_MONITOR_UNABLE_TO_EXPORT_PROVIDER_ENTRY;
        String message = getMessage(msgID,
                                    monitorProvider.getMonitorInstanceName(),
                                    stackTraceToSingleLineString(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, msgID);
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
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Indicates whether this backend provides a mechanism to import its data from
   * an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF import
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFImport()
  {
    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void importLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFImportConfig importConfig)
         throws DirectoryException
  {
    // This backend does not support LDIF imports.
    int    msgID   = MSGID_MONITOR_IMPORT_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Indicates whether this backend provides a backup mechanism of any kind.
   * This method is used by the backup process when backing up all backends to
   * determine whether this backend is one that should be skipped.  It should
   * only return <CODE>true</CODE> for backends that it is not possible to
   * archive directly (e.g., those that don't store their data locally, but
   * rather pass through requests to some other repository).
   *
   * @return  <CODE>true</CODE> if this backend provides any kind of backup
   *          mechanism, or <CODE>false</CODE> if it does not.
   */
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * Indicates whether this backend provides a mechanism to perform a backup of
   * its contents in a form that can be restored later, based on the provided
   * configuration.
   *
   * @param  backupConfig       The configuration of the backup for which to
   *                            make the determination.
   * @param  unsupportedReason  A buffer to which a message can be appended
   *                            explaining why the requested backup is not
   *                            supported.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          performing backups with the provided configuration, or
   *          <CODE>false</CODE> if not.
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
  public void createBackup(ConfigEntry configEntry, BackupConfig backupConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    int    msgID   = MSGID_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param  backupDirectory  The backup directory structure with which the
   *                          specified backup is associated.
   * @param  backupID         The backup ID for the backup to be removed.
   *
   * @throws  DirectoryException  If it is not possible to remove the specified
   *                              backup for some reason (e.g., no such backup
   *                              exists or there are other backups that are
   *                              dependent upon it).
   */
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    int    msgID   = MSGID_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Indicates whether this backend provides a mechanism to restore a backup.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          restoring backups, or <CODE>false</CODE> if not.
   */
  public boolean supportsRestore()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void restoreBackup(ConfigEntry configEntry,
                            RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    int    msgID   = MSGID_MONITOR_BACKUP_AND_RESTORE_NOT_SUPPORTED;
    String message = getMessage(msgID);
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message,
                                 msgID);
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  backendCfg          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean isConfigurationChangeAcceptable(
       BackendCfg backendCfg,
       List<String> unacceptableReasons)
  {
    // We'll pretty much accept anything here as long as it isn't one of our
    // private attributes.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(BackendCfg backendCfg)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    userDefinedAttributes = userAttrs;

    int    msgID   = MSGID_MONITOR_USING_NEW_USER_ATTRS;
    String message = getMessage(msgID);
    messages.add(message);


    currentConfig = backendCfg;
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

