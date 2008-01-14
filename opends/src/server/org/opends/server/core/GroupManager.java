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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.GroupImplementationCfgDefn;
import org.opends.server.admin.std.server.GroupImplementationCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.Group;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.workflowelement.localbackend.
            LocalBackendSearchOperation;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a mechanism for interacting with all groups defined in
 * the Directory Server.  It will handle all necessary processing at server
 * startup to identify and load all group implementations, as well as to find
 * all group instances within the server.
 * <BR><BR>
 * FIXME:  At the present time, it assumes that all of the necessary
 * information about all of the groups defined in the server can be held in
 * memory.  If it is determined that this approach is not workable in all cases,
 * then we will need an alternate strategy.
 */
public class GroupManager
       implements ConfigurationChangeListener<GroupImplementationCfg>,
                  ConfigurationAddListener<GroupImplementationCfg>,
                  ConfigurationDeleteListener<GroupImplementationCfg>,
                  BackendInitializationListener,
                  ChangeNotificationListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  //Used by group instances to determine if new groups have been
  //registered or groups deleted.
  private long refreshToken=0;


  // A mapping between the DNs of the config entries and the associated
  // group implementations.
  private ConcurrentHashMap<DN,Group> groupImplementations;

  // A mapping between the DNs of all group entries and the corresponding
  // group instances.
  private ConcurrentHashMap<DN,Group> groupInstances;



  /**
   * Creates a new instance of this group manager.
   */
  public GroupManager()
  {
    groupImplementations = new ConcurrentHashMap<DN,Group>();
    groupInstances       = new ConcurrentHashMap<DN,Group>();

    DirectoryServer.registerBackendInitializationListener(this);
    DirectoryServer.registerChangeNotificationListener(this);
  }



  /**
   * Initializes all group implementations currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the group
   *                           implementation initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the group implementations that is not
   *                                   related to the server configuration.
   */
  public void initializeGroupImplementations()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();


    // Register as an add and delete listener with the root configuration so we
    // can be notified if any group implementation entries are added or removed.
    rootConfiguration.addGroupImplementationAddListener(this);
    rootConfiguration.addGroupImplementationDeleteListener(this);


    //Initialize the existing group implementations.
    for (String name : rootConfiguration.listGroupImplementations())
    {
      GroupImplementationCfg groupConfiguration =
           rootConfiguration.getGroupImplementation(name);
      groupConfiguration.addChangeListener(this);

      if (groupConfiguration.isEnabled())
      {
        String className = groupConfiguration.getJavaClass();
        try
        {
          Group group = loadGroup(className, groupConfiguration, true);
          groupImplementations.put(groupConfiguration.dn(), group);
        }
        catch (InitializationException ie)
        {
          logError(ie.getMessageObject());
          continue;
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      GroupImplementationCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // group implementation.
      String className = configuration.getJavaClass();
      try
      {
        loadGroup(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
                                 GroupImplementationCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    Group group = null;

    // Get the name of the class and make sure we can instantiate it as a group
    // implementation.
    String className = configuration.getJavaClass();
    try
    {
      group = loadGroup(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      groupImplementations.put(configuration.dn(), group);
    }

    // FIXME -- We need to make sure to find all groups of this type in the
    // server before returning.

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
                      GroupImplementationCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // group implementation is in use.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
                                 GroupImplementationCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();

    Group group = groupImplementations.remove(configuration.dn());
    if (group != null)
    {
      Iterator<Group> iterator = groupInstances.values().iterator();
      while (iterator.hasNext())
      {
        Group g = iterator.next();
        if (g.getClass().getName().equals(group.getClass().getName()))
        {
          iterator.remove();
        }
      }

      group.finalizeGroupImplementation();
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      GroupImplementationCfg configuration,
                      List<Message> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // group implementation.
      String className = configuration.getJavaClass();
      try
      {
        loadGroup(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 GroupImplementationCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the existing group implementation if it's already enabled.
    Group existingGroup = groupImplementations.get(configuration.dn());


    // If the new configuration has the group implementation disabled, then
    // disable it if it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingGroup != null)
      {
        Group group = groupImplementations.remove(configuration.dn());
        if (group != null)
        {
          Iterator<Group> iterator = groupInstances.values().iterator();
          while (iterator.hasNext())
          {
            Group g = iterator.next();
            if (g.getClass().getName().equals(group.getClass().getName()))
            {
              iterator.remove();
            }
          }

          group.finalizeGroupImplementation();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the group implementation.  If the group is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the group implementation is disabled, then instantiate
    // the class and initialize and register it as a group implementation.
    String className = configuration.getJavaClass();
    if (existingGroup != null)
    {
      if (! className.equals(existingGroup.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    Group group = null;
    try
    {
      group = loadGroup(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      groupImplementations.put(configuration.dn(), group);
    }

    // FIXME -- We need to make sure to find all groups of this type in the
    // server before returning.

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Loads the specified class, instantiates it as a group implementation, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the group implementation
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the group
   *                        implementation.  It must not be {@code null}.
   * @param  initialize     Indicates whether the group implementation instance
   *                        should be initialized.
   *
   * @return  The possibly initialized group implementation.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the group implementation.
   */
  private Group loadGroup(String className,
                          GroupImplementationCfg configuration,
                          boolean initialize)
          throws InitializationException
  {
    try
    {
      GroupImplementationCfgDefn definition =
           GroupImplementationCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends Group> groupClass =
           propertyDefinition.loadClass(className, Group.class);
      Group group = groupClass.newInstance();

      if (initialize)
      {
        Method method = group.getClass()
            .getMethod("initializeGroupImplementation",
                configuration.configurationClass());
        method.invoke(group, configuration);
      }
      else
      {
        Method method = group.getClass().getMethod("isConfigurationAcceptable",
                                                   GroupImplementationCfg.class,
                                                   List.class);

        List<Message> unacceptableReasons = new ArrayList<Message>();
        Boolean acceptable = (Boolean) method.invoke(group, configuration,
                                                     unacceptableReasons);
        if (! acceptable)
        {
          StringBuilder buffer = new StringBuilder();
          if (! unacceptableReasons.isEmpty())
          {
            Iterator<Message> iterator = unacceptableReasons.iterator();
            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(".  ");
              buffer.append(iterator.next());
            }
          }

          Message message = ERR_CONFIG_GROUP_CONFIG_NOT_ACCEPTABLE.get(
              String.valueOf(configuration.dn()), buffer.toString());
          throw new InitializationException(message);
        }
      }

      return group;
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_GROUP_INITIALIZATION_FAILED.
          get(className, String.valueOf(configuration.dn()),
              stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * Performs any cleanup work that may be needed when the server is shutting
   * down.
   */
  public void finalizeGroupManager()
  {
    deregisterAllGroups();

    for (Group groupImplementation : groupImplementations.values())
    {
      groupImplementation.finalizeGroupImplementation();
    }

    groupImplementations.clear();
  }



  /**
   * Retrieves an {@code Iterable} object that may be used to cursor across the
   * group implementations defined in the server.
   *
   * @return  An {@code Iterable} object that may be used to cursor across the
   *          group implementations defined in the server.
   */
  public Iterable<Group> getGroupImplementations()
  {
    return groupImplementations.values();
  }



  /**
   * Retrieves an {@code Iterable} object that may be used to cursor across the
   * group instances defined in the server.
   *
   * @return  An {@code Iterable} object that may be used to cursor across the
   *          group instances defined in the server.
   */
  public Iterable<Group> getGroupInstances()
  {
    return groupInstances.values();
  }



  /**
   * Retrieves the group instance defined in the entry with the specified DN.
   *
   * @param  entryDN  The DN of the entry containing the definition of the group
   *                  instance to retrieve.
   *
   * @return  The group instance defined in the entry with the specified DN, or
   *          {@code null} if no such group is currently defined.
   */
  public Group getGroupInstance(DN entryDN)
  {
    Group group = groupInstances.get(entryDN);
    if (group == null)
    {
      // FIXME -- Should we try to retrieve the corresponding entry and see if
      // it is a group?
    }

    return group;
  }



  /**
   * {@inheritDoc}  In this case, the server will search the backend to find
   * all group instances that it may contain and register them with this group
   * manager.
   */
  public void performBackendInitializationProcessing(Backend backend)
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedList<Control> requestControls = new LinkedList<Control>();
    requestControls.add(new Control(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE,
                                    false));
    for (DN configEntryDN : groupImplementations.keySet())
    {
      SearchFilter filter;
      Group groupImplementation = groupImplementations.get(configEntryDN);
      try
      {
        filter = groupImplementation.getGroupDefinitionFilter();
        if (! backend.isIndexed(filter))
        {
          logError(WARN_GROUP_FILTER_NOT_INDEXED.get(String.valueOf(filter),
                        String.valueOf(configEntryDN), backend.getBackendID()));
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Is there anything that we need to do here?
        continue;
      }


      for (DN baseDN : backend.getBaseDNs())
      {
        try
        {
          if (! backend.entryExists(baseDN))
          {
            continue;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // FIXME -- Is there anything that we need to do here?
          continue;
        }


        InternalSearchOperation internalSearch =
             new InternalSearchOperation(conn, conn.nextOperationID(),
                                         conn.nextMessageID(), requestControls,
                                         baseDN,
                                         SearchScope.WHOLE_SUBTREE,
                                         DereferencePolicy.NEVER_DEREF_ALIASES,
                                         0, 0, false, filter, null, null);
        LocalBackendSearchOperation localSearch =
          new LocalBackendSearchOperation(internalSearch);
        try
        {
          backend.search(localSearch);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // FIXME -- Is there anything that we need to do here?
          continue;
        }

        for (SearchResultEntry entry : internalSearch.getSearchEntries())
        {
          try
          {
            Group groupInstance = groupImplementation.newInstance(entry);
            groupInstances.put(entry.getDN(), groupInstance);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            // FIXME -- Handle this.
            continue;
          }
        }
      }
    }
  }



  /**
   * {@inheritDoc}  In this case, the server will de-register all group
   * instances associated with entries in the provided backend.
   */
  public void performBackendFinalizationProcessing(Backend backend)
  {
    Iterator<Map.Entry<DN,Group>> iterator =
         groupInstances.entrySet().iterator();
    while (iterator.hasNext())
    {
      Map.Entry<DN,Group> mapEntry = iterator.next();
      DN groupEntryDN = mapEntry.getKey();
      if (backend.handlesEntry(groupEntryDN))
      {
        iterator.remove();
      }
    }
  }



  /**
   * {@inheritDoc}  In this case, each entry is checked to see if it contains
   * a group definition, and if so it will be instantiated and registered with
   * this group manager.
   */
  public void handleAddOperation(PostResponseAddOperation addOperation,
                                 Entry entry)
  {
    List<Control> requestControls = addOperation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE))
        {
          return;
        }
      }
    }
    synchronized (groupInstances)
    {
      createAndRegisterGroup(entry);
      refreshToken++;
    }
  }



  /**
   * {@inheritDoc}  In this case, if the entry is associated with a registered
   * group instance, then that group instance will be deregistered.
   */
  public void handleDeleteOperation(PostResponseDeleteOperation deleteOperation,
                                    Entry entry)
  {
    List<Control> requestControls = deleteOperation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE))
        {
          return;
        }
      }
    }
    synchronized (groupInstances)
    {
      groupInstances.remove(entry.getDN());
      refreshToken++;
    }
  }



  /**
   * {@inheritDoc}  In this case, if the entry is associated with a registered
   * group instance, then that instance will be recreated from the contents of
   * the provided entry and re-registered with the group manager.
   */
  public void handleModifyOperation(PostResponseModifyOperation modifyOperation,
                                    Entry oldEntry, Entry newEntry)
  {
    List<Control> requestControls = modifyOperation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE))
        {
          return;
        }
      }
    }


    if (groupInstances.containsKey(oldEntry.getDN()))
    {
      synchronized (groupInstances)
      {
        if (! oldEntry.getDN().equals(newEntry.getDN()))
        {
          // This should never happen, but check for it anyway.
          groupInstances.remove(oldEntry.getDN());
        }

        createAndRegisterGroup(newEntry);
        refreshToken++;
      }
    }
  }



  /**
   * {@inheritDoc}  In this case, if the entry is associated with a registered
   * group instance, then that instance will be recreated from the contents of
   * the provided entry and re-registered with the group manager under the new
   * DN, and the old instance will be deregistered.
   */
  public void handleModifyDNOperation(
                   PostResponseModifyDNOperation modifyDNOperation,
                   Entry oldEntry, Entry newEntry)
  {
    List<Control> requestControls = modifyDNOperation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE))
        {
          return;
        }
      }
    }

    if (groupInstances.containsKey(oldEntry.getDN()))
    {
      synchronized (groupInstances)
      {
        createAndRegisterGroup(newEntry);
        groupInstances.remove(oldEntry.getDN());
        refreshToken++;
      }
    }
  }



  /**
   * Attempts to create a group instance from the provided entry, and if that is
   * successful then register it with the server, overwriting any existing
   * group instance that may be registered with the same DN.
   *
   * @param  entry  The entry containing the potential group definition.
   */
  private void createAndRegisterGroup(Entry entry)
  {
    for (Group groupImplementation : groupImplementations.values())
    {
      try
      {
        if (groupImplementation.isGroupDefinition(entry))
        {
          Group groupInstance = groupImplementation.newInstance(entry);
          groupInstances.put(entry.getDN(), groupInstance);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Do we need to do anything else?
      }
    }
  }



  /**
   * Removes all group instances that might happen to be registered with the
   * group manager.  This method is only intended for testing purposes and
   * should not be called by any other code.
   */
  void deregisterAllGroups()
  {
    groupInstances.clear();
  }


  /**
   * Compare the specified token against the current group manager
   * token value. Can be used to reload cached group instances if there has
   * been a group instance change.
   *
   * @param token The current token that the group class holds.
   *
   * @return {@code true} if the group class should reload its nested groups,
   *         or {@code false} if it shouldn't.
   */
  public boolean hasInstancesChanged(long token)  {
    return token != this.refreshToken;
  }

  /**
   * Return the current refresh token value. Can be used to
   * reload cached group instances if there has been a group instance change.
   *
   * @return The current token value.
   */
  public long refreshToken() {
    return this.refreshToken;
  }
}

