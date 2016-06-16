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
package org.opends.server.core;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Utils;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.meta.GroupImplementationCfgDefn;
import org.forgerock.opendj.server.config.server.GroupImplementationCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.DITCacheMap;
import org.opends.server.api.Group;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginResult.PostOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.operation.PluginOperation;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostSynchronizationAddOperation;
import org.opends.server.types.operation.PostSynchronizationDeleteOperation;
import org.opends.server.types.operation.PostSynchronizationModifyDNOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;

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
public class GroupManager extends InternalDirectoryServerPlugin
       implements ConfigurationChangeListener<GroupImplementationCfg>,
                  ConfigurationAddListener<GroupImplementationCfg>,
                  ConfigurationDeleteListener<GroupImplementationCfg>,
                  BackendInitializationListener
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Used by group instances to determine if new groups have been registered or groups deleted. */
  private volatile long refreshToken;

  /** A mapping between the DNs of the config entries and the associated group implementations. */
  private ConcurrentMap<DN, Group<?>> groupImplementations;

  /** A mapping between the DNs of all group entries and the corresponding group instances. */
  private DITCacheMap<Group<?>> groupInstances;

  /** Lock to protect internal data structures. */
  private final ReadWriteLock lock;

  /** Dummy configuration DN for Group Manager. */
  private static final String CONFIG_DN = "cn=Group Manager,cn=config";

  private final ServerContext serverContext;

  /**
   * Creates a new instance of this group manager.
   *
   * @param serverContext
   *          The server context.
   * @throws DirectoryException
   *           If a problem occurs while creating an instance of the group
   *           manager.
   */
  public GroupManager(ServerContext serverContext) throws DirectoryException
  {
    super(DN.valueOf(CONFIG_DN), EnumSet.of(PluginType.POST_OPERATION_ADD,
        PluginType.POST_OPERATION_DELETE, PluginType.POST_OPERATION_MODIFY,
        PluginType.POST_OPERATION_MODIFY_DN,
        PluginType.POST_SYNCHRONIZATION_ADD,
        PluginType.POST_SYNCHRONIZATION_DELETE,
        PluginType.POST_SYNCHRONIZATION_MODIFY,
        PluginType.POST_SYNCHRONIZATION_MODIFY_DN), true);
    this.serverContext = serverContext;

    groupImplementations = new ConcurrentHashMap<>();
    groupInstances = new DITCacheMap<>();

    lock = new ReentrantReadWriteLock();

    DirectoryServer.registerInternalPlugin(this);
    DirectoryServer.registerBackendInitializationListener(this);
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
    RootCfg rootConfiguration = serverContext.getRootConfig();
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
        try
        {
          Group<?> group = loadGroup(groupConfiguration.getJavaClass(), groupConfiguration, true);
          groupImplementations.put(groupConfiguration.dn(), group);
        }
        catch (InitializationException ie)
        {
          // Log error but keep going
          logger.error(ie.getMessageObject());
        }
      }
    }
  }

  @Override
  public boolean isConfigurationAddAcceptable(
                      GroupImplementationCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      try
      {
        loadGroup(configuration.getJavaClass(), configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
                                 GroupImplementationCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return ccr;
    }

    Group<?> group = null;
    try
    {
      group = loadGroup(configuration.getJavaClass(), configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      groupImplementations.put(configuration.dn(), group);
    }

    // FIXME -- We need to make sure to find all groups of this type in the
    // server before returning.
    return ccr;
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
                      GroupImplementationCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // FIXME -- We should try to perform some check to determine whether the
    // group implementation is in use.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
                                 GroupImplementationCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    Group<?> group = groupImplementations.remove(configuration.dn());
    if (group != null)
    {
      lock.writeLock().lock();
      try
      {
        Iterator<Group<?>> iterator = groupInstances.values().iterator();
        while (iterator.hasNext())
        {
          Group<?> g = iterator.next();
          if (g.getClass().getName().equals(group.getClass().getName()))
          {
            iterator.remove();
          }
        }
      }
      finally
      {
        lock.writeLock().unlock();
      }

      group.finalizeGroupImplementation();
    }

    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      GroupImplementationCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      try
      {
        loadGroup(configuration.getJavaClass(), configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 GroupImplementationCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    // Get the existing group implementation if it's already enabled.
    Group<?> existingGroup = groupImplementations.get(configuration.dn());

    // If the new configuration has the group implementation disabled, then
    // disable it if it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingGroup != null)
      {
        Group<?> group = groupImplementations.remove(configuration.dn());
        if (group != null)
        {
          lock.writeLock().lock();
          try
          {
            Iterator<Group<?>> iterator = groupInstances.values().iterator();
            while (iterator.hasNext())
            {
              Group<?> g = iterator.next();
              if (g.getClass().getName().equals(group.getClass().getName()))
              {
                iterator.remove();
              }
            }
          }
          finally
          {
            lock.writeLock().unlock();
          }

          group.finalizeGroupImplementation();
        }
      }

      return ccr;
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
        ccr.setAdminActionRequired(true);
      }

      return ccr;
    }

    Group<?> group = null;
    try
    {
      group = loadGroup(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ie.getMessageObject());
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      groupImplementations.put(configuration.dn(), group);
    }

    // FIXME -- We need to make sure to find all groups of this type in the
    // server before returning.
    return ccr;
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
  private static Group<?> loadGroup(String className,
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
        group.initializeGroupImplementation(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        if (!group.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reason = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(ERR_CONFIG_GROUP_CONFIG_NOT_ACCEPTABLE.get(
              configuration.dn(), reason));
        }
      }

      return group;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_GROUP_INITIALIZATION_FAILED.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }

  /** Performs any cleanup work that may be needed when the server is shutting down. */
  public void finalizeGroupManager()
  {
    DirectoryServer.deregisterInternalPlugin(this);
    DirectoryServer.deregisterBackendInitializationListener(this);

    deregisterAllGroups();

    for (Group<?> groupImplementation : groupImplementations.values())
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
  public Iterable<Group<?>> getGroupImplementations()
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
  public Iterable<Group<?>> getGroupInstances()
  {
    lock.readLock().lock();
    try
    {
      // Return a copy to protect from structural changes.
      return new ArrayList<>(groupInstances.values());
    }
    finally
    {
      lock.readLock().unlock();
    }
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
  public Group<?> getGroupInstance(DN entryDN)
  {
    lock.readLock().lock();
    try
    {
      return groupInstances.get(entryDN);
    }
    finally
    {
      lock.readLock().unlock();
    }
  }

  /**
   * {@inheritDoc}  In this case, the server will search the backend to find
   * all group instances that it may contain and register them with this group
   * manager.
   */
  @Override
  public void performBackendPreInitializationProcessing(Backend<?> backend)
  {
    InternalClientConnection conn = getRootConnection();

    LDAPControl control = new LDAPControl(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE, false);
    for (DN configEntryDN : groupImplementations.keySet())
    {
      SearchFilter filter;
      Group<?> groupImplementation = groupImplementations.get(configEntryDN);
      try
      {
        filter = groupImplementation.getGroupDefinitionFilter();
        if (backend.getEntryCount() > 0 && ! backend.isIndexed(filter))
        {
          logger.warn(WARN_GROUP_FILTER_NOT_INDEXED, filter, configEntryDN, backend.getBackendID());
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
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
          logger.traceException(e);
          continue;
        }

        SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, filter)
            .addControl(control);
        InternalSearchOperation internalSearch =
            new InternalSearchOperation(conn, nextOperationID(), nextMessageID(), request);
        LocalBackendSearchOperation localSearch =
          new LocalBackendSearchOperation(internalSearch);
        try
        {
          backend.search(localSearch);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // FIXME -- Is there anything that we need to do here?
          continue;
        }

        lock.writeLock().lock();
        try
        {
          for (SearchResultEntry entry : internalSearch.getSearchEntries())
          {
            try
            {
              Group<?> groupInstance = groupImplementation.newInstance(serverContext, entry);
              groupInstances.put(entry.getName(), groupInstance);
              refreshToken++;
            }
            catch (DirectoryException e)
            {
              logger.traceException(e);
              // Nothing specific to do, as it's already logged.
            }
          }
        }
        finally
        {
          lock.writeLock().unlock();
        }
      }
    }
  }

  /**
   * {@inheritDoc}  In this case, the server will de-register all group
   * instances associated with entries in the provided backend.
   */
  @Override
  public void performBackendPostFinalizationProcessing(Backend<?> backend)
  {
    lock.writeLock().lock();
    try
    {
      Iterator<Map.Entry<DN, Group<?>>> iterator = groupInstances.entrySet().iterator();
      while (iterator.hasNext())
      {
        Map.Entry<DN, Group<?>> mapEntry = iterator.next();
        DN groupEntryDN = mapEntry.getKey();
        if (backend.handlesEntry(groupEntryDN))
        {
          iterator.remove();
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void performBackendPostInitializationProcessing(Backend<?> backend) {
    // Nothing to do.
  }

  @Override
  public void performBackendPreFinalizationProcessing(Backend<?> backend) {
    // Nothing to do.
  }

  /**
   * In this case, each entry is checked to see if it contains
   * a group definition, and if so it will be instantiated and
   * registered with this group manager.
   */
  private void doPostAdd(PluginOperation addOperation, Entry entry)
  {
    if (hasGroupMembershipUpdateControl(addOperation))
    {
      return;
    }

    createAndRegisterGroup(entry);
  }

  private static boolean hasGroupMembershipUpdateControl(PluginOperation operation)
  {
    List<Control> requestControls = operation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE.equals(c.getOID()))
        {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * In this case, if the entry is associated with a registered
   * group instance, then that group instance will be deregistered.
   */
  private void doPostDelete(PluginOperation deleteOperation, Entry entry)
  {
    if (hasGroupMembershipUpdateControl(deleteOperation))
    {
      return;
    }

    lock.writeLock().lock();
    try
    {
      if (groupInstances.removeSubtree(entry.getName(), null))
      {
        refreshToken++;
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * Scan the list of provided modifications looking for any changes to the objectClass,
   * which might change the entry to another kind of group, or even to a non-group.
   *
   * @param modifications  List of modifications to the current group
   *
   * @return {@code true} if the objectClass is changed in any way, {@code false} otherwise.
   */
  private boolean updatesObjectClass(List<Modification> modifications)
  {
    for (Modification mod : modifications)
    {
      if (mod.getAttribute().getAttributeDescription().getAttributeType().isObjectClass())
      {
        return true;
      }
    }
    return false;
  }

  /**
   * In this case, if the entry is associated with a registered
   * group instance, then that instance will be recreated from
   * the contents of the provided entry and re-registered with
   * the group manager.
   */
  private void doPostModify(PluginOperation modifyOperation,
          Entry oldEntry, Entry newEntry,
          List<Modification> modifications)
  {
    if (hasGroupMembershipUpdateControl(modifyOperation))
    {
      return;
    }

    lock.readLock().lock();
    try
    {
      if (!groupInstances.containsKey(oldEntry.getName()))
      {
        // If the modified entry is not in any group instance, it's probably
        // not a group, exit fast
        return;
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    lock.writeLock().lock();
    try
    {
      Group<?> group = groupInstances.get(oldEntry.getName());
      if (group != null)
      {
        if (!oldEntry.getName().equals(newEntry.getName())
            || !group.mayAlterMemberList()
            || updatesObjectClass(modifications))
        {
          groupInstances.remove(oldEntry.getName());
          // This updates the refreshToken
          createAndRegisterGroup(newEntry);
        }
        else
        {
          group.updateMembers(modifications);
        }
      }
    }
    catch (UnsupportedOperationException | DirectoryException e)
    {
      logger.traceException(e);
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * In this case, if the entry is associated with a registered
   * group instance, then that instance will be recreated from
   * the contents of the provided entry and re-registered with
   * the group manager under the new DN, and the old instance
   * will be deregistered.
   */
  private void doPostModifyDN(PluginOperation modifyDNOperation,
          Entry oldEntry, Entry newEntry)
  {
    if (hasGroupMembershipUpdateControl(modifyDNOperation))
    {
      return;
    }

    lock.writeLock().lock();
    try
    {
      Set<Group<?>> groupSet = new HashSet<>();
      final DN oldDN = oldEntry.getName();
      final DN newDN = newEntry.getName();
      groupInstances.removeSubtree(oldDN, groupSet);
      for (Group<?> group : groupSet)
      {
        final DN groupDN = group.getGroupDN();
        final DN renamedGroupDN = groupDN.rename(oldDN, newDN);
        group.setGroupDN(renamedGroupDN);
        groupInstances.put(renamedGroupDN, group);
      }
      if (!groupSet.isEmpty())
      {
        refreshToken++;
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  @Override
  public PostOperation doPostOperation(
          PostOperationAddOperation addOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (addOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostAdd(addOperation, addOperation.getEntryToAdd());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public PostOperation doPostOperation(
          PostOperationDeleteOperation deleteOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (deleteOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostDelete(deleteOperation, deleteOperation.getEntryToDelete());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public PostOperation doPostOperation(
          PostOperationModifyOperation modifyOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (modifyOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostModify(modifyOperation,
            modifyOperation.getCurrentEntry(),
            modifyOperation.getModifiedEntry(),
            modifyOperation.getModifications());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public PostOperation doPostOperation(
          PostOperationModifyDNOperation modifyDNOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (modifyDNOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostModifyDN(modifyDNOperation,
            modifyDNOperation.getOriginalEntry(),
            modifyDNOperation.getUpdatedEntry());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public void doPostSynchronization(
      PostSynchronizationAddOperation addOperation)
  {
    Entry entry = addOperation.getEntryToAdd();
    if (entry != null)
    {
      doPostAdd(addOperation, entry);
    }
  }

  @Override
  public void doPostSynchronization(
      PostSynchronizationDeleteOperation deleteOperation)
  {
    Entry entry = deleteOperation.getEntryToDelete();
    if (entry != null)
    {
      doPostDelete(deleteOperation, entry);
    }
  }

  @Override
  public void doPostSynchronization(
      PostSynchronizationModifyOperation modifyOperation)
  {
    Entry entry = modifyOperation.getCurrentEntry();
    Entry modEntry = modifyOperation.getModifiedEntry();
    if (entry != null && modEntry != null)
    {
      doPostModify(modifyOperation, entry, modEntry, modifyOperation.getModifications());
    }
  }

  @Override
  public void doPostSynchronization(
      PostSynchronizationModifyDNOperation modifyDNOperation)
  {
    Entry oldEntry = modifyDNOperation.getOriginalEntry();
    Entry newEntry = modifyDNOperation.getUpdatedEntry();
    if (oldEntry != null && newEntry != null)
    {
      doPostModifyDN(modifyDNOperation, oldEntry, newEntry);
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
    for (Group<?> groupImplementation : groupImplementations.values())
    {
      try
      {
        if (groupImplementation.isGroupDefinition(entry))
        {
          Group<?> groupInstance = groupImplementation.newInstance(serverContext, entry);

          lock.writeLock().lock();
          try
          {
            groupInstances.put(entry.getName(), groupInstance);
            refreshToken++;
          }
          finally
          {
            lock.writeLock().unlock();
          }
        }
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);
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
    lock.writeLock().lock();
    try
    {
      groupInstances.clear();
    }
    finally
    {
      lock.writeLock().unlock();
    }
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
