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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.plugins;



import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.admin.std.server.UniqueAttributePluginCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginResult.PostOperation;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostSynchronizationAddOperation;
import org.opends.server.types.operation.PostSynchronizationModifyDNOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

import static org.opends.messages.PluginMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements a Directory Server plugin that can be used to ensure
 * that all values for a given attribute or set of attributes are unique within
 * the server (or optionally, below a specified set of base DNs).  It will
 * examine all add, modify, and modify DN operations to determine whether any
 * new conflicts are introduced.  If a conflict is detected then the operation
 * will be rejected, unless that operation is being applied through
 * synchronization in which case an alert will be generated to notify
 * administrators of the problem.
 */
public class UniqueAttributePlugin
        extends DirectoryServerPlugin<UniqueAttributePluginCfg>
        implements ConfigurationChangeListener<UniqueAttributePluginCfg>,
                   AlertGenerator
{
  /**
   * The debug log tracer that will be used for this plugin.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The set of attributes that will be requested when performing internal
   * search operations.  This indicates that no attributes should be returned.
   */
  private static final LinkedHashSet<String> SEARCH_ATTRS =
       new LinkedHashSet<String>(1);
  static
  {
    SEARCH_ATTRS.add("1.1");
  }



  //Current plugin configuration.
  private UniqueAttributePluginCfg currentConfiguration;



  // The data structure to store the mapping between the attribute value
  // and the corresponding dn.
  private ConcurrentHashMap<AttributeValue,DN> uniqueAttrValue2Dn;



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void initializePlugin(Set<PluginType> pluginTypes,
                                     UniqueAttributePluginCfg configuration)
          throws ConfigException
  {
    configuration.addUniqueAttributeChangeListener(this);
    currentConfiguration = configuration;
    DirectoryServer.registerAlertGenerator(this);

    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case PRE_OPERATION_ADD:
        case PRE_OPERATION_MODIFY:
        case PRE_OPERATION_MODIFY_DN:
        case POST_OPERATION_ADD:
        case POST_OPERATION_MODIFY:
        case POST_OPERATION_MODIFY_DN:
        case POST_SYNCHRONIZATION_ADD:
        case POST_SYNCHRONIZATION_MODIFY:
        case POST_SYNCHRONIZATION_MODIFY_DN:
          // These are acceptable.
          break;

        default:
          Message message =
                  ERR_PLUGIN_UNIQUEATTR_INVALID_PLUGIN_TYPE.get(t.toString());
          throw new ConfigException(message);

      }
    }

    Set<DN> cfgBaseDNs = configuration.getBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : configuration.getType())
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
        {
          throw new ConfigException(ERR_PLUGIN_UNIQUEATTR_ATTR_UNINDEXED.get(
                                         configuration.dn().toString(),
                                         t.getNameOrOID(),
                                         b.getBackendID()));
        }
      }
    }

    uniqueAttrValue2Dn  = new ConcurrentHashMap<AttributeValue,DN>();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void finalizePlugin()
  {
    currentConfiguration.removeUniqueAttributeChangeListener(this);
    DirectoryServer.deregisterAlertGenerator(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation
               doPreOperation(PreOperationAddOperation addOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Entry entry = addOperation.getEntryToAdd();

    Set<DN> baseDNs = getBaseDNs(config, entry.getDN());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    for (AttributeType t : config.getType())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a)
          {
            try
            {
              DN conflictDN = null;
              //Raise an exception if a conflicting concurrent operation is in
              //progress. Otherwise, store this attribute value with its
              //corresponding DN and proceed.
              if((conflictDN=
                      uniqueAttrValue2Dn.putIfAbsent(v, entry.getDN()))==null)
              {
                conflictDN = getConflictingEntryDN(baseDNs, entry.getDN(),
                                                   config, v);
              }
              if (conflictDN != null)
              {
                Message msg = ERR_PLUGIN_UNIQUEATTR_ATTR_NOT_UNIQUE.get(
                    t.getNameOrOID(), v.getValue().toString(),
                    conflictDN.toString());
                return PluginResult.PreOperation.stopProcessing(
                    ResultCode.CONSTRAINT_VIOLATION, msg);
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              Message m = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR.get(
                               de.getResultCode().toString(),
                               de.getMessageObject());

              return PluginResult.PreOperation.stopProcessing(
                    DirectoryServer.getServerErrorResultCode(), m);
            }
          }
        }
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation
               doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    DN entryDN = modifyOperation.getEntryDN();

    Set<DN> baseDNs = getBaseDNs(config, entryDN);
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    for (Modification m : modifyOperation.getModifications())
    {
      Attribute a = m.getAttribute();
      AttributeType t = a.getAttributeType();
      if (! config.getType().contains(t))
      {
        // This modification isn't for a unique attribute.
        continue;
      }

      switch (m.getModificationType())
      {
        case ADD:
        case REPLACE:
          for (AttributeValue v : a)
          {
            try
            {
              DN conflictDN = null;
              //Raise an exception if a conflicting concurrent operation is in
              //progress. Otherwise, store this attribute value with its
              //corresponding DN and proceed.
              if((conflictDN=
                      uniqueAttrValue2Dn.putIfAbsent(v, entryDN))==null)
              {
               conflictDN = getConflictingEntryDN(baseDNs, entryDN, config,
                                                   v);
              }
              if (conflictDN != null)
              {
                Message msg = ERR_PLUGIN_UNIQUEATTR_ATTR_NOT_UNIQUE.get(
                    t.getNameOrOID(), v.getValue().toString(),
                    conflictDN.toString());
                return PluginResult.PreOperation.stopProcessing(
                    ResultCode.CONSTRAINT_VIOLATION, msg);
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              Message message = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR.get(
                                     de.getResultCode().toString(),
                                     de.getMessageObject());

              return PluginResult.PreOperation.stopProcessing(
                    DirectoryServer.getServerErrorResultCode(), message);
            }
          }
          break;

        case INCREMENT:
          // We could calculate the new value, but we'll just take it from the
          // updated entry.
          List<Attribute> attrList =
               modifyOperation.getModifiedEntry().getAttribute(t,
                                                               a.getOptions());
          if (attrList != null)
          {
            for (Attribute updatedAttr : attrList)
            {
              if (! updatedAttr.optionsEqual(a.getOptions()))
              {
                continue;
              }

              for (AttributeValue v : updatedAttr)
              {
                try
                {
                  DN conflictDN = null;
                  //Raise an exception if a conflicting concurrent operation is
                  //in progress. Otherwise, store this attribute value with its
                  //corresponding DN and proceed.
                  if((conflictDN=
                      uniqueAttrValue2Dn.putIfAbsent(v, entryDN))==null)
                  {
                    conflictDN = getConflictingEntryDN(baseDNs, entryDN,
                                                        config, v);
                  }
                  if (conflictDN != null)
                  {
                    Message msg = ERR_PLUGIN_UNIQUEATTR_ATTR_NOT_UNIQUE.get(
                        t.getNameOrOID(), v.getValue().toString(),
                        conflictDN.toString());
                    return PluginResult.PreOperation.stopProcessing(
                        ResultCode.CONSTRAINT_VIOLATION, msg);
                  }
                }
                catch (DirectoryException de)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, de);
                  }

                  Message message = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR.get(
                                         de.getResultCode().toString(),
                                         de.getMessageObject());

                  return PluginResult.PreOperation.stopProcessing(
                      DirectoryServer.getServerErrorResultCode(), message);
                }
              }
            }
          }
          break;

        default:
          // We don't need to look at this modification because it's not a
          // modification type of interest.
          continue;
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PreOperation doPreOperation(
                    PreOperationModifyDNOperation modifyDNOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;

    Set<DN> baseDNs = getBaseDNs(config,
                                 modifyDNOperation.getUpdatedEntry().getDN());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    RDN newRDN = modifyDNOperation.getNewRDN();
    for (int i=0; i < newRDN.getNumValues(); i++)
    {
      AttributeType t = newRDN.getAttributeType(i);
      if (! config.getType().contains(t))
      {
        // We aren't interested in this attribute type.
        continue;
      }

      try
      {
        AttributeValue v = newRDN.getAttributeValue(i);
        DN conflictDN = null;
        //Raise an exception if a conflicting concurrent operation is in
        //progress. Otherwise, store this attribute value with its
        //corresponding DN and proceed.
        if((conflictDN=uniqueAttrValue2Dn.putIfAbsent(
                              v, modifyDNOperation.getEntryDN()))==null)
        {
          conflictDN = getConflictingEntryDN(baseDNs,
            modifyDNOperation.getEntryDN(), config, v);
        }
        if (conflictDN != null)
        {
          Message msg = ERR_PLUGIN_UNIQUEATTR_ATTR_NOT_UNIQUE.get(
              t.getNameOrOID(), v.getValue().toString(),
              conflictDN.toString());
          return PluginResult.PreOperation.stopProcessing(
              ResultCode.CONSTRAINT_VIOLATION, msg);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        Message m = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR.get(
                         de.getResultCode().toString(),
                         de.getMessageObject());

        return PluginResult.PreOperation.stopProcessing(
            DirectoryServer.getServerErrorResultCode(), m);
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void doPostSynchronization(
                         PostSynchronizationAddOperation addOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Entry entry = addOperation.getEntryToAdd();

    Set<DN> baseDNs = getBaseDNs(config, entry.getDN());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return;
    }

    for (AttributeType t : config.getType())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a)
          {
            try
            {
              DN conflictDN = null;
              if((conflictDN=uniqueAttrValue2Dn.get(v)) == null)
              {
                conflictDN = getConflictingEntryDN(baseDNs, entry.getDN(),
                                                    config, v);
              }
              if (conflictDN != null)
              {
                Message m = ERR_PLUGIN_UNIQUEATTR_SYNC_NOT_UNIQUE.get(
                                 t.getNameOrOID(),
                                 addOperation.getConnectionID(),
                                 addOperation.getOperationID(),
                                 v.getValue().toString(),
                                 entry.getDN().toString(),
                                 conflictDN.toString());
                DirectoryServer.sendAlertNotification(this,
                                     ALERT_TYPE_UNIQUE_ATTR_SYNC_CONFLICT, m);
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              Message m = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR_SYNC.get(
                               addOperation.getConnectionID(),
                               addOperation.getOperationID(),
                               entry.getDN().toString(),
                               de.getResultCode().toString(),
                               de.getMessageObject());
              DirectoryServer.sendAlertNotification(this,
                                   ALERT_TYPE_UNIQUE_ATTR_SYNC_ERROR, m);
            }
          }
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void doPostSynchronization(
                         PostSynchronizationModifyOperation modifyOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    DN entryDN = modifyOperation.getEntryDN();

    Set<DN> baseDNs = getBaseDNs(config, entryDN);
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return;
    }

    for (Modification m : modifyOperation.getModifications())
    {
      Attribute a = m.getAttribute();
      AttributeType t = a.getAttributeType();
      if (! config.getType().contains(t))
      {
        // This modification isn't for a unique attribute.
        continue;
      }

      switch (m.getModificationType())
      {
        case ADD:
        case REPLACE:
          for (AttributeValue v : a)
          {
            try
            {
              DN conflictDN = null;
              if((conflictDN=uniqueAttrValue2Dn.get(v)) == null)
              {
               conflictDN = getConflictingEntryDN(baseDNs, entryDN, config,
                                                    v);
              }
              if (conflictDN != null)
              {
                Message message = ERR_PLUGIN_UNIQUEATTR_SYNC_NOT_UNIQUE.get(
                                       t.getNameOrOID(),
                                       modifyOperation.getConnectionID(),
                                       modifyOperation.getOperationID(),
                                       v.getValue().toString(),
                                       entryDN.toString(),
                                       conflictDN.toString());
                DirectoryServer.sendAlertNotification(this,
                                     ALERT_TYPE_UNIQUE_ATTR_SYNC_CONFLICT,
                                     message);
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              Message message = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR_SYNC.get(
                                    modifyOperation.getConnectionID(),
                                    modifyOperation.getOperationID(),
                                    entryDN.toString(),
                                    de.getResultCode().toString(),
                                    de.getMessageObject());
              DirectoryServer.sendAlertNotification(this,
                                   ALERT_TYPE_UNIQUE_ATTR_SYNC_ERROR, message);
            }
          }
          break;

        case INCREMENT:
          // We could calculate the new value, but we'll just take it from the
          // updated entry.
          List<Attribute> attrList =
               modifyOperation.getModifiedEntry().getAttribute(t,
                                                               a.getOptions());
          if (attrList != null)
          {
            for (Attribute updatedAttr : attrList)
            {
              if (! updatedAttr.optionsEqual(a.getOptions()))
              {
                continue;
              }

              for (AttributeValue v : updatedAttr)
              {
                try
                {
                  DN conflictDN = null;
                  if((conflictDN=uniqueAttrValue2Dn.get(v)) == null)
                  {
                   conflictDN = getConflictingEntryDN(baseDNs, entryDN,
                                                        config, v);
                  }
                  if (conflictDN != null)
                  {
                    Message message = ERR_PLUGIN_UNIQUEATTR_SYNC_NOT_UNIQUE.get(
                                           t.getNameOrOID(),
                                           modifyOperation.getConnectionID(),
                                           modifyOperation.getOperationID(),
                                           v.getValue().toString(),
                                           entryDN.toString(),
                                           conflictDN.toString());
                    DirectoryServer.sendAlertNotification(this,
                                         ALERT_TYPE_UNIQUE_ATTR_SYNC_CONFLICT,
                                         message);
                  }
                }
                catch (DirectoryException de)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, de);
                  }

                  Message message =
                       ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR_SYNC.get(
                            modifyOperation.getConnectionID(),
                            modifyOperation.getOperationID(),
                            entryDN.toString(),
                            de.getResultCode().toString(),
                            de.getMessageObject());
                  DirectoryServer.sendAlertNotification(this,
                                       ALERT_TYPE_UNIQUE_ATTR_SYNC_ERROR,
                                       message);
                }
              }
            }
          }
          break;

        default:
          // We don't need to look at this modification because it's not a
          // modification type of interest.
          continue;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void doPostSynchronization(
                         PostSynchronizationModifyDNOperation modifyDNOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;

    Set<DN> baseDNs = getBaseDNs(config,
                                 modifyDNOperation.getUpdatedEntry().getDN());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return;
    }

    RDN newRDN = modifyDNOperation.getNewRDN();
    for (int i=0; i < newRDN.getNumValues(); i++)
    {
      AttributeType t = newRDN.getAttributeType(i);
      if (! config.getType().contains(t))
      {
        // We aren't interested in this attribute type.
        continue;
      }

      try
      {
        AttributeValue v = newRDN.getAttributeValue(i);
        DN conflictDN = null;
        if((conflictDN=uniqueAttrValue2Dn.get(v)) == null)
        {
         conflictDN = getConflictingEntryDN(baseDNs,
                             modifyDNOperation.getEntryDN(), config, v);
        }
        if (conflictDN != null)
        {
          Message m =
               ERR_PLUGIN_UNIQUEATTR_SYNC_NOT_UNIQUE.get(
                    t.getNameOrOID(),
                    modifyDNOperation.getConnectionID(),
                    modifyDNOperation.getOperationID(),
                    v.getValue().toString(),
                    modifyDNOperation.getUpdatedEntry().getDN().toString(),
                    conflictDN.toString());
          DirectoryServer.sendAlertNotification(this,
                               ALERT_TYPE_UNIQUE_ATTR_SYNC_CONFLICT, m);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        Message m = ERR_PLUGIN_UNIQUEATTR_INTERNAL_ERROR_SYNC.get(
                         modifyDNOperation.getConnectionID(),
                         modifyDNOperation.getOperationID(),
                         modifyDNOperation.getUpdatedEntry().getDN().toString(),
                         de.getResultCode().toString(),
                         de.getMessageObject());
        DirectoryServer.sendAlertNotification(this,
                             ALERT_TYPE_UNIQUE_ATTR_SYNC_ERROR, m);
      }
    }
  }



  /**
   * Retrieves the set of base DNs below which uniqueness checks should be
   * performed.  If no uniqueness checks should be performed for the specified
   * entry, then {@code null} will be returned.
   *
   * @param  config   The plugin configuration to use to make the determination.
   * @param  entryDN  The DN of the entry for which the checks will be
   *                  performed.
   */
  private Set<DN> getBaseDNs(UniqueAttributePluginCfg config, DN entryDN)
  {
    Set<DN> baseDNs = config.getBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (DN baseDN : baseDNs)
    {
      if (entryDN.isDescendantOf(baseDN))
      {
        return baseDNs;
      }
    }

    return null;
  }



  /**
   * Retrieves the DN of the first entry identified that conflicts with the
   * provided value.
   *
   * @param  baseDNs   The set of base DNs below which the search is to be
   *                   performed.
   * @param  targetDN  The DN of the entry at which the change is targeted.  If
   *                   a conflict is found in that entry, then it will be
   *                   ignored.
   * @param  config    The plugin configuration to use when making the
   *                   determination.
   * @param  value     The value for which to identify any conflicting entries.
   *
   * @return  The DN of the first entry identified that contains a conflicting
   *          value.
   *
   * @throws  DirectoryException  If a problem occurred while attempting to
   *                              make the determination.
   */
  private DN getConflictingEntryDN(Set<DN> baseDNs, DN targetDN,
                                   UniqueAttributePluginCfg config,
                                   AttributeValue value)
          throws DirectoryException
  {
    SearchFilter filter;
    Set<AttributeType> attrTypes = config.getType();
    if (attrTypes.size() == 1)
    {
      filter = SearchFilter.createEqualityFilter(attrTypes.iterator().next(),
                                                 value);
    }
    else
    {
      ArrayList<SearchFilter> equalityFilters =
           new ArrayList<SearchFilter>(attrTypes.size());
      for (AttributeType t : attrTypes)
      {
        equalityFilters.add(SearchFilter.createEqualityFilter(t, value));
      }
      filter = SearchFilter.createORFilter(equalityFilters);
    }

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    for (DN baseDN : baseDNs)
    {
      InternalSearchOperation searchOperation =
           conn.processSearch(baseDN, SearchScope.WHOLE_SUBTREE,
                              DereferencePolicy.NEVER_DEREF_ALIASES, 2, 0,
                              false, filter, SEARCH_ATTRS);
      for (SearchResultEntry e : searchOperation.getSearchEntries())
      {
        if (! e.getDN().equals(targetDN))
        {
          return e.getDN();
        }
      }

      switch (searchOperation.getResultCode())
      {
        case SUCCESS:
        case NO_SUCH_OBJECT:
          // These are fine.  Either the search was successful or the base DN
          // didn't exist.
          break;

        default:
          // An error occurred that prevented the search from completing
          // successfully.
          throw new DirectoryException(searchOperation.getResultCode(),
                         searchOperation.getErrorMessage().toMessage());
      }
    }

    // If we've gotten here, then no conflict was found.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(PluginCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    UniqueAttributePluginCfg cfg = (UniqueAttributePluginCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      UniqueAttributePluginCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;

    for (PluginCfgDefn.PluginType pluginType : configuration.getPluginType())
    {
      switch (pluginType)
      {
        case PREOPERATIONADD:
        case PREOPERATIONMODIFY:
        case PREOPERATIONMODIFYDN:
        case POSTOPERATIONADD:
        case POSTOPERATIONMODIFY:
        case POSTOPERATIONMODIFYDN:
        case POSTSYNCHRONIZATIONADD:
        case POSTSYNCHRONIZATIONMODIFY:
        case POSTSYNCHRONIZATIONMODIFYDN:
          // These are acceptable.
          break;

        default:
          Message message = ERR_PLUGIN_UNIQUEATTR_INVALID_PLUGIN_TYPE.get(
                                 pluginType.toString());
          unacceptableReasons.add(message);
          configAcceptable = false;
      }
    }

    Set<DN> cfgBaseDNs = configuration.getBaseDN();
    if ((cfgBaseDNs == null) || cfgBaseDNs.isEmpty())
    {
      cfgBaseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }

    for (AttributeType t : configuration.getType())
    {
      for (DN baseDN : cfgBaseDNs)
      {
        Backend b = DirectoryServer.getBackend(baseDN);
        if ((b != null) && (! b.isIndexed(t, IndexType.EQUALITY)))
        {
          unacceptableReasons.add(ERR_PLUGIN_UNIQUEATTR_ATTR_UNINDEXED.get(
                                       configuration.dn().toString(),
                                       t.getNameOrOID(), b.getBackendID()));
          configAcceptable = false;
        }
      }
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 UniqueAttributePluginCfg newConfiguration)
  {
    currentConfiguration = newConfiguration;
    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  public DN getComponentEntryDN()
  {
    return currentConfiguration.dn();
  }



  /**
   * {@inheritDoc}
   */
  public String getClassName()
  {
    return UniqueAttributePlugin.class.getName();
  }



  /**
   * {@inheritDoc}
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>(2);

    alerts.put(ALERT_TYPE_UNIQUE_ATTR_SYNC_CONFLICT,
               ALERT_DESCRIPTION_UNIQUE_ATTR_SYNC_CONFLICT);
    alerts.put(ALERT_TYPE_UNIQUE_ATTR_SYNC_ERROR,
               ALERT_DESCRIPTION_UNIQUE_ATTR_SYNC_ERROR);

    return alerts;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationAddOperation addOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Entry entry = addOperation.getEntryToAdd();

    Set<DN> baseDNs = getBaseDNs(config, entry.getDN());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    //Remove the attribute value from the map.
    for (AttributeType t : config.getType())
    {
      List<Attribute> attrList = entry.getAttribute(t);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a)
          {
            uniqueAttrValue2Dn.remove(v);
          }
        }
      }
    }

    return PluginResult.PostOperation.continueOperationProcessing();
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    DN entryDN = modifyOperation.getEntryDN();

    Set<DN> baseDNs = getBaseDNs(config, entryDN);
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    for (Modification m : modifyOperation.getModifications())
    {
      Attribute a = m.getAttribute();
      AttributeType t = a.getAttributeType();
      if (! config.getType().contains(t))
      {
        // This modification isn't for a unique attribute.
        continue;
      }

      switch (m.getModificationType())
      {
        case ADD:
        case REPLACE:
          for (AttributeValue v : a)
          {
            uniqueAttrValue2Dn.remove(v);
          }
          break;

        case INCREMENT:
          // We could calculate the new value, but we'll just take it from the
          // updated entry.
          List<Attribute> attrList =
               modifyOperation.getModifiedEntry().getAttribute(t,
                                                           a.getOptions());
          if (attrList != null)
          {
            for (Attribute updatedAttr : attrList)
            {
              if (! updatedAttr.optionsEqual(a.getOptions()))
              {
                continue;
              }

              for (AttributeValue v : updatedAttr)
              {
                uniqueAttrValue2Dn.remove(v);
              }
            }
          }
          break;

        default:
          // We don't need to look at this modification because it's not a
          // modification type of interest.
          continue;
      }
    }

    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final PluginResult.PostOperation
       doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    UniqueAttributePluginCfg config = currentConfiguration;
    Set<DN> baseDNs = getBaseDNs(config,
                                 modifyDNOperation.getUpdatedEntry().getDN());
    if (baseDNs == null)
    {
      // The entry is outside the scope of this plugin.
      return PostOperation.continueOperationProcessing();
    }

    RDN newRDN = modifyDNOperation.getNewRDN();
    for (int i=0; i < newRDN.getNumValues(); i++)
    {
      AttributeType t = newRDN.getAttributeType(i);
      if (! config.getType().contains(t))
      {
        // We aren't interested in this attribute type.
        continue;
      }
      AttributeValue v = newRDN.getAttributeValue(i);
      uniqueAttrValue2Dn.remove(v);
    }
    return PostOperation.continueOperationProcessing();
  }
}

