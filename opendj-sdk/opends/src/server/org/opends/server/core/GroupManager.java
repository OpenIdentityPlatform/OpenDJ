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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.Group;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;


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
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener,
                  BackendInitializationListener, ChangeNotificationListener
{



  // A mapping between the DNs of the config entries and the associated
  // group implementations.
  private ConcurrentHashMap<DN,Group> groupImplementations;

  // A mapping between the DNs of all group entries and the corresponding
  // group instances.
  private ConcurrentHashMap<DN,Group> groupInstances;

  // The configuration handler for the Directory Server.
  private ConfigHandler configHandler;



  /**
   * Creates a new instance of this group manager.
   */
  public GroupManager()
  {
    configHandler        = DirectoryServer.getConfigHandler();
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
    // First, get the configuration base entry.
    ConfigEntry baseEntry;
    try
    {
      DN groupImplementationBaseDN =
              DN.decode(DN_GROUP_IMPLEMENTATION_CONFIG_BASE);
      baseEntry = configHandler.getConfigEntry(groupImplementationBaseDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_CANNOT_GET_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    if (baseEntry == null)
    {
      // The group implementation base entry does not exist.  This is not
      // acceptable, so throw an exception.
      int    msgID   = MSGID_CONFIG_GROUP_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Register add and delete listeners with the group implementation base
    // entry.  We don't care about modifications to it.
    baseEntry.registerAddListener(this);
    baseEntry.registerDeleteListener(this);


    // See if the base entry has any children.  If not, then we don't need to do
    // anything else.
    if (! baseEntry.hasChildren())
    {
      return;
    }


    // Iterate through the child entries and process them as group
    // implementation configuration entries.
    for (ConfigEntry childEntry : baseEntry.getChildren().values())
    {
      childEntry.registerChangeListener(this);

      StringBuilder unacceptableReason = new StringBuilder();
      if (! configAddIsAcceptable(childEntry, unacceptableReason))
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_GROUP_ENTRY_UNACCEPTABLE,
                 childEntry.getDN().toString(), unacceptableReason.toString());
        continue;
      }

      try
      {
        ConfigChangeResult result = applyConfigurationAdd(childEntry);
        if (result.getResultCode() != ResultCode.SUCCESS)
        {
          StringBuilder buffer = new StringBuilder();

          List<String> resultMessages = result.getMessages();
          if ((resultMessages == null) || (resultMessages.isEmpty()))
          {
            buffer.append(getMessage(MSGID_CONFIG_UNKNOWN_UNACCEPTABLE_REASON));
          }
          else
          {
            Iterator<String> iterator = resultMessages.iterator();

            buffer.append(iterator.next());
            while (iterator.hasNext())
            {
              buffer.append(EOL);
              buffer.append(iterator.next());
            }
          }

          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   MSGID_CONFIG_GROUP_CANNOT_CREATE_IMPLEMENTATION,
                   childEntry.getDN().toString(), buffer.toString());
        }
      }
      catch (Exception e)
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_GROUP_CANNOT_CREATE_IMPLEMENTATION,
                 childEntry.getDN().toString(), String.valueOf(e));
      }
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
   * Indicates whether the configuration entry that will result from a proposed
   * modification is acceptable to this change listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested update.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed change is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configChangeIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    // Make sure that the entry has an appropriate objectclass for an extended
    // operation handler.
    if (! configEntry.hasObjectClass(OC_GROUP_IMPLEMENTATION))
    {
      int    msgID   = MSGID_CONFIG_GROUP_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry specifies the group implementation class name.
    StringConfigAttribute classNameAttr;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_GROUP_IMPLEMENTATION_CLASS,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      classNameAttr = (StringConfigAttribute)
                      configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int    msgID   = MSGID_CONFIG_GROUP_NO_CLASS_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Class groupClass;
    try
    {
      groupClass = DirectoryServer.loadClass(classNameAttr.pendingValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    try
    {
      Group group = (Group) groupClass.newInstance();
    }
    catch(Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_CLASS;
      String message = getMessage(msgID, groupClass.getName(),
                                  String.valueOf(configEntry.getDN()),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // See if this extended operation handler should be enabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_GROUP_IMPLEMENTATION_ENABLED,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int    msgID   = MSGID_CONFIG_GROUP_NO_ENABLED_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_ENABLED_VALUE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the group implementation entry appears to be
    // acceptable.
    return true;
  }



  /**
   * Attempts to apply a new configuration to this Directory Server component
   * based on the provided changed entry.
   *
   * @param  configEntry  The configuration entry that containing the updated
   *                      configuration for this component.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationChange(ConfigEntry configEntry)
  {
    DN                configEntryDN       = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure that the entry has an appropriate objectclass for a group
    // implementation.
    if (! configEntry.hasObjectClass(OC_GROUP_IMPLEMENTATION))
    {
      int msgID = MSGID_CONFIG_GROUP_INVALID_OBJECTCLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the corresponding group implementation instance if it is active.
    Group groupImplementation = groupImplementations.get(configEntryDN);


    // See if this handler should be enabled or disabled.
    boolean needsEnabled = false;
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_GROUP_IMPLEMENTATION_ENABLED,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_ENABLED), false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int msgID = MSGID_CONFIG_GROUP_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.UNWILLING_TO_PERFORM;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      if (enabledAttr.activeValue())
      {
        if (groupImplementation == null)
        {
          needsEnabled = true;
        }
        else
        {
          // The group implementation is already active, so no action is
          // required.
        }
      }
      else
      {
        if (groupImplementation == null)
        {
          // The group implementation is already disabled, so no action is
          // required and we can short-circuit out of this processing.
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
        else
        {
          // The group implementation is active, so it needs to be disabled.  Do
          // this and return that we were successful.
          groupImplementations.remove(configEntryDN);

          Iterator<Group> iterator = groupInstances.values().iterator();
          while (iterator.hasNext())
          {
            Group g = iterator.next();
            if (g.getClass().getName().equals(
                     groupImplementation.getClass().getName()))
            {
              iterator.remove();
            }
          }

          groupImplementation.finalizeGroupImplementation();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_GROUP_INVALID_ENABLED_VALUE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the group implementation class name.
    // If it has changed, then we will not try to dynamically apply it.
    String className;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_GROUP_IMPLEMENTATION_CLASS,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      StringConfigAttribute classNameAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_GROUP_NO_CLASS_NAME;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      className = classNameAttr.pendingValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_GROUP_INVALID_CLASS_NAME;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    boolean classChanged = false;
    String  oldClassName = null;
    if (groupImplementation != null)
    {
      oldClassName = groupImplementation.getClass().getName();
      classChanged = (! className.equals(oldClassName));
    }


    if (classChanged)
    {
      // This will not be applied dynamically.  Add a message to the response
      // and indicate that admin action is required.
      adminActionRequired = true;
      messages.add(getMessage(MSGID_CONFIG_GROUP_CLASS_ACTION_REQUIRED,
                              String.valueOf(oldClassName),
                              String.valueOf(className),
                              String.valueOf(configEntryDN)));
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    if (needsEnabled)
    {
      try
      {
        Class groupClass = DirectoryServer.loadClass(className);
        groupImplementation = (Group) groupClass.newInstance();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_GROUP_INVALID_CLASS;
        messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      try
      {
        groupImplementation.initializeGroupImplementation(configEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_GROUP_INITIALIZATION_FAILED;
        messages.add(getMessage(msgID, className,
                                String.valueOf(configEntryDN),
                                String.valueOf(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      groupImplementations.put(configEntryDN, groupImplementation);

      // FIXME -- We need to make sure to find all groups of this type in the
      // server before returning.

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // If we've gotten here, then there haven't been any changes to anything
    // that we care about.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Indicates whether the configuration entry that will result from a proposed
   * add is acceptable to this add listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested add.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed entry is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean configAddIsAcceptable(ConfigEntry configEntry,
                                       StringBuilder unacceptableReason)
  {
    // Make sure that no entry already exists with the specified DN.
    DN configEntryDN = configEntry.getDN();
    if (groupImplementations.containsKey(configEntryDN))
    {
      int    msgID   = MSGID_CONFIG_GROUP_EXISTS;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry has an appropriate objectclass for a group
    // implementation.
    if (! configEntry.hasObjectClass(OC_GROUP_IMPLEMENTATION))
    {
      int    msgID   = MSGID_CONFIG_GROUP_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry specifies the group implementation class.
    StringConfigAttribute classNameAttr;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_GROUP_IMPLEMENTATION_CLASS,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      classNameAttr = (StringConfigAttribute)
                      configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int    msgID   = MSGID_CONFIG_GROUP_NO_CLASS_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Class groupClass;
    try
    {
      groupClass = DirectoryServer.loadClass(classNameAttr.pendingValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_CLASS_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }

    Group groupImplementation;
    try
    {
      groupImplementation = (Group) groupClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_CLASS;
      String message = getMessage(msgID, groupClass.getName(),
                                  String.valueOf(configEntryDN),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If the handler is a configurable component, then make sure that its
    // configuration is valid.
    if (groupImplementation instanceof ConfigurableComponent)
    {
      ConfigurableComponent cc = (ConfigurableComponent) groupImplementation;
      LinkedList<String> errorMessages = new LinkedList<String>();
      if (! cc.hasAcceptableConfiguration(configEntry, errorMessages))
      {
        if (errorMessages.isEmpty())
        {
          int msgID = MSGID_CONFIG_GROUP_UNACCEPTABLE_CONFIG;
          unacceptableReason.append(getMessage(msgID,
                                               String.valueOf(configEntryDN)));
        }
        else
        {
          Iterator<String> iterator = errorMessages.iterator();
          unacceptableReason.append(iterator.next());
          while (iterator.hasNext())
          {
            unacceptableReason.append("  ");
            unacceptableReason.append(iterator.next());
          }
        }

        return false;
      }
    }


    // See if this handler should be enabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_GROUP_IMPLEMENTATION_ENABLED,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        int    msgID   = MSGID_CONFIG_GROUP_NO_ENABLED_ATTR;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReason.append(message);
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_GROUP_INVALID_ENABLED_VALUE;
      String message = getMessage(msgID, configEntry.getDN().toString(),
                                  String.valueOf(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the handler entry appears to be acceptable.
    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided added entry.
   *
   * @param  configEntry  The new configuration entry that contains the
   *                      configuration to apply.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationAdd(ConfigEntry configEntry)
  {
    DN                configEntryDN       = configEntry.getDN();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure that the entry has an appropriate objectclass for a group
    // implementation.
    if (! configEntry.hasObjectClass(OC_GROUP_IMPLEMENTATION))
    {
      int    msgID   = MSGID_CONFIG_GROUP_INVALID_OBJECTCLASS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if this group implementation should be enabled or disabled.
    BooleanConfigAttribute enabledAttr;
    try
    {
      BooleanConfigAttribute enabledStub =
           new BooleanConfigAttribute(ATTR_GROUP_IMPLEMENTATION_ENABLED,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_ENABLED),
                               false);
      enabledAttr = (BooleanConfigAttribute)
                    configEntry.getConfigAttribute(enabledStub);

      if (enabledAttr == null)
      {
        // The attribute doesn't exist, so it will be disabled by default.
        int msgID = MSGID_CONFIG_GROUP_NO_ENABLED_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.SUCCESS;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
      else if (! enabledAttr.activeValue())
      {
        // It is explicitly configured as disabled, so we don't need to do
        // anything.
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_GROUP_INVALID_ENABLED_VALUE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Make sure that the entry specifies the group implementation class name.
    String className;
    try
    {
      StringConfigAttribute classStub =
           new StringConfigAttribute(ATTR_GROUP_IMPLEMENTATION_CLASS,
                    getMessage(MSGID_CONFIG_GROUP_DESCRIPTION_CLASS_NAME),
                    true, false, true);
      StringConfigAttribute classNameAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(classStub);

      if (classNameAttr == null)
      {
        int msgID = MSGID_CONFIG_GROUP_NO_CLASS_NAME;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }

      className = classNameAttr.pendingValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_GROUP_INVALID_CLASS_NAME;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Load and initialize the group implementation class, and register it with
    // the Directory Server.
    Group groupImplementation;
    try
    {
      Class groupClass = DirectoryServer.loadClass(className);
      groupImplementation = (Group) groupClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_GROUP_INVALID_CLASS;
      messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    try
    {
      groupImplementation.initializeGroupImplementation(configEntry);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_GROUP_INITIALIZATION_FAILED;
      messages.add(getMessage(msgID, className, String.valueOf(configEntryDN),
                              String.valueOf(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    groupImplementations.put(configEntryDN, groupImplementation);
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * Indicates whether it is acceptable to remove the provided configuration
   * entry.
   *
   * @param  configEntry         The configuration entry that will be removed
   *                             from the configuration.
   * @param  unacceptableReason  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed delete is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry may be removed from the
   *          configuration, or <CODE>false</CODE> if not.
   */
  public boolean configDeleteIsAcceptable(ConfigEntry configEntry,
                                          StringBuilder unacceptableReason)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }



  /**
   * Attempts to apply a new configuration based on the provided deleted entry.
   *
   * @param  configEntry  The new configuration entry that has been deleted.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyConfigurationDelete(ConfigEntry configEntry)
  {
    DN         configEntryDN       = configEntry.getDN();
    ResultCode resultCode          = ResultCode.SUCCESS;
    boolean    adminActionRequired = false;


    // See if the entry is registered as a group implementation.  If so, then
    // deregister it and remove any groups of that type.
    Group groupImplementation = groupImplementations.remove(configEntryDN);
    if (groupImplementation != null)
    {
      Iterator<Group> iterator = groupInstances.values().iterator();
      while (iterator.hasNext())
      {
        Group g = iterator.next();
        if (g.getClass().getName().equals(
                 groupImplementation.getClass().getName()))
        {
          iterator.remove();
        }
      }

      groupImplementation.finalizeGroupImplementation();
    }


    return new ConfigChangeResult(resultCode, adminActionRequired);
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

    for (Group groupImplementation : groupImplementations.values())
    {
      SearchFilter filter;
      try
      {
        filter = groupImplementation.getGroupDefinitionFilter();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
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
            debugCaught(DebugLogLevel.ERROR, e);
          }

          // FIXME -- Is there anything that we need to do here?
          continue;
        }


        InternalSearchOperation internalSearch =
             new InternalSearchOperation(conn, conn.nextOperationID(),
                                         conn.nextMessageID(), null, baseDN,
                                         SearchScope.WHOLE_SUBTREE,
                                         DereferencePolicy.NEVER_DEREF_ALIASES,
                                         0, 0, false, filter, null, null);
        try
        {
          backend.search(internalSearch);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
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
              debugCaught(DebugLogLevel.ERROR, e);
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

    createAndRegisterGroup(entry);
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

    groupInstances.remove(entry.getDN());
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
          debugCaught(DebugLogLevel.ERROR, e);
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
}

