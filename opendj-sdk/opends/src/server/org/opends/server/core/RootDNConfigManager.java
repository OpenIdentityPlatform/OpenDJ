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
package org.opends.server.core;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a utility that will be used to manage the set of root DNs
 * defined in the Directory Server.  The root DN accounts will exist below
 * "cn=Root DNs,cn=config" and must have the ds-cfg-root-dn objectclass.  They
 * may optionally have a ds-cfg-bind-dn that may be used to provide an alternate
 * DN for use when binding to the server.
 */
public class RootDNConfigManager
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener,
                  ConfigurableComponent
{



  // A mapping between each root DN user entry and a list of the alternate
  // bind DNs for that user.
  private ConcurrentHashMap<DN,List<DN>> bindMappings;

  // The DN of the entry that serves as the base for the root DN
  // configuration entries.
  private DN rootDNConfigBaseDN;

  // The set of privileges that will be automatically inherited by root users.
  private LinkedHashSet<Privilege> rootPrivileges;



  /**
   * Creates a new instance of this root DN config manager.
   */
  public RootDNConfigManager()
  {

    bindMappings = new ConcurrentHashMap<DN,List<DN>>();
  }



  /**
   * Initializes all root DNs currently defined in the Directory Server
   * configuration.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a configuration problem causes the root DN
   *                           initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the root DNs that is not related to the
   *                                   server configuration.
   */
  public void initializeRootDNs()
         throws ConfigException, InitializationException
  {


    // First, get the base configuration entry for the root DNs.
    ConfigHandler configHandler = DirectoryServer.getConfigHandler();
    ConfigEntry   baseEntry;
    try
    {
      rootDNConfigBaseDN = DN.decode(DN_ROOT_DN_CONFIG_BASE);
      baseEntry = configHandler.getConfigEntry(rootDNConfigBaseDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_ROOTDN_CANNOT_GET_BASE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new ConfigException(msgID, message, e);
    }

    if (baseEntry == null)
    {
      int    msgID   = MSGID_CONFIG_ROOTDN_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
    }


    // Get the set of privileges that root users should have by default.
    rootPrivileges = new LinkedHashSet<Privilege>(
                              Privilege.getDefaultRootPrivileges());

    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ROOT_PRIVILEGE;
    MultiChoiceConfigAttribute rootPrivStub =
         new MultiChoiceConfigAttribute(ATTR_DEFAULT_ROOT_PRIVILEGE_NAME,
                                        getMessage(msgID), false, true, false,
                                        Privilege.getPrivilegeNames());
    try
    {
      MultiChoiceConfigAttribute rootPrivAttr =
           (MultiChoiceConfigAttribute)
           baseEntry.getConfigAttribute(rootPrivStub);
      if (rootPrivAttr != null)
      {
        ArrayList<Privilege> privList = new ArrayList<Privilege>();
        for (String value : rootPrivAttr.activeValues())
        {
          String privName = toLowerCase(value);
          Privilege p = Privilege.privilegeForName(privName);
          if (p == null)
          {
            msgID = MSGID_CONFIG_ROOTDN_UNRECOGNIZED_PRIVILEGE;
            String message = getMessage(msgID, ATTR_DEFAULT_ROOT_PRIVILEGE_NAME,
                                        String.valueOf(rootDNConfigBaseDN),
                                        String.valueOf(value));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          }
          else
          {
            privList.add(p);
          }
        }

        rootPrivileges = new LinkedHashSet<Privilege>(privList);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_ROOTDN_ERROR_DETERMINING_ROOT_PRIVILEGES;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Register with the configuration base entry as an add and delete listener.
    // So that we will be notified of attempts to create or remove root DN
    // entries.  Also, register with the server as a configurable component so
    // that we can detect and apply any changes to the root
    baseEntry.registerAddListener(this);
    baseEntry.registerDeleteListener(this);
    DirectoryServer.registerConfigurableComponent(this);


    // See if the base entry has any children.  If not, then we don't need to
    // do anything else.
    if (! baseEntry.hasChildren())
    {
      return;
    }


    // Iterate through the child entries and process them as root DN entries.
    for (ConfigEntry childEntry : baseEntry.getChildren().values())
    {
      StringBuilder unacceptableReason = new StringBuilder();
      if (! configAddIsAcceptable(childEntry, unacceptableReason))
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_ROOTDN_ENTRY_UNACCEPTABLE,
                 String.valueOf(childEntry.getDN()),
                 unacceptableReason.toString());
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
                   MSGID_CONFIG_ROOTDN_CANNOT_CREATE,
                   String.valueOf(childEntry.getDN()), buffer.toString());
        }
      }
      catch (Exception e)
      {
        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 MSGID_CONFIG_PWGENERATOR_CANNOT_CREATE_GENERATOR,
                 childEntry.getDN().toString(), String.valueOf(e));
      }
    }
  }



  /**
   * Retrieves the set of privileges that should automatically be granted to
   * root users when they authenticate.
   *
   * @return  The set of privileges that should automatically be granted to root
   *          users when they authenticate.
   */
  public Set<Privilege> getRootPrivileges()
  {

    return rootPrivileges;
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


    // Make sure that the entry has an appropriate objectclass for a root DN.
    if (! configEntry.hasObjectClass(OC_ROOT_DN))
    {
      int    msgID   = MSGID_CONFIG_ROOTDN_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntry.getDN().toString());
      unacceptableReason.append(message);
      return false;
    }


    // See if the entry has any alternate DNs.  If so, then make sure they are
    // valid.
    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN;
    DNConfigAttribute alternateDNsStub =
         new DNConfigAttribute(ATTR_ROOTDN_ALTERNATE_BIND_DN, getMessage(msgID),
                               false, true, false);
    try
    {
      DNConfigAttribute alternateDNsAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(alternateDNsStub);

      if (alternateDNsAttr != null)
      {
        // There were alternate DNs provided, so see if there are any duplicate
        // values that are already registered for a different root DN.
        for (DN alternateBindDN : alternateDNsAttr.pendingValues())
        {
          DN rootDN = DirectoryServer.getActualRootBindDN(alternateBindDN);
          if ((rootDN != null) && (! rootDN.equals(configEntry.getDN())))
          {
            msgID = MSGID_CONFIG_ROOTDN_CONFLICTING_MAPPING;
            String message = getMessage(msgID, String.valueOf(alternateBindDN),
                                        String.valueOf(configEntry.getDN()),
                                        String.valueOf(rootDN));
            unacceptableReason.append(message);
            return false;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_ROOTDN_CANNOT_PARSE_ALTERNATE_BIND_DNS;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                  stackTraceToSingleLineString(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the root DN entry appears to be acceptable.
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


    // Get the set of alternate bind DNs for the entry, if there are any.
    List<DN> alternateBindDNs = null;
    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN;
    DNConfigAttribute alternateDNsStub =
         new DNConfigAttribute(ATTR_ROOTDN_ALTERNATE_BIND_DN, getMessage(msgID),
                               false, true, false);
    try
    {
      DNConfigAttribute alternateDNsAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(alternateDNsStub);
      if (alternateDNsAttr != null)
      {
        alternateBindDNs = alternateDNsAttr.activeValues();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_ROOTDN_CANNOT_PARSE_ALTERNATE_BIND_DNS;
      messages.add(getMessage(msgID, stackTraceToSingleLineString(e)));

      resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      List<DN> existingMappings = bindMappings.get(configEntryDN);
      if (existingMappings != null)
      {
        for (DN mappedDN : existingMappings)
        {
          if ((alternateBindDNs == null) ||
              (! alternateBindDNs.contains(mappedDN)))
          {
            DirectoryServer.deregisterAlternateRootBindDN(mappedDN);
          }
        }
      }

      if (alternateBindDNs == null)
      {
        alternateBindDNs = new ArrayList<DN>(0);
      }
      else
      {
        for (DN alternateBindDN : alternateBindDNs)
        {
          try
          {
            DirectoryServer.registerAlternateRootDN(configEntryDN,
                                                    alternateBindDN);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, de);
            }

            msgID = MSGID_CONFIG_ROOTDN_CANNOT_REGISTER_ALTERNATE_BIND_DN;
            messages.add(getMessage(msgID, String.valueOf(alternateBindDN),
                                    String.valueOf(configEntryDN),
                                    de.getErrorMessage()));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
          }
        }
      }

      bindMappings.put(configEntryDN, alternateBindDNs);
    }


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


    // Make sure that no entry already exists with the specified DN, and that
    // there is no other root user with a conflicting alternate bind DN.
    DN configEntryDN = configEntry.getDN();
    if (bindMappings.containsKey(configEntryDN) ||
        (DirectoryServer.getActualRootBindDN(configEntryDN) != null))
    {
      int    msgID   = MSGID_CONFIG_ROOTDN_EXISTS;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReason.append(message);
      return false;
    }


    // Make sure that the entry has the root DN objectclass.
    if (! configEntry.hasObjectClass(OC_ROOT_DN))
    {
      int    msgID   = MSGID_CONFIG_ROOTDN_INVALID_OBJECTCLASS;
      String message = getMessage(msgID, configEntryDN.toString());
      unacceptableReason.append(message);
      return false;
    }




    // See if the entry has any alternate DNs.  If so, then make sure they are
    // valid.
    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN;
    DNConfigAttribute alternateDNsStub =
         new DNConfigAttribute(ATTR_ROOTDN_ALTERNATE_BIND_DN, getMessage(msgID),
                               false, true, false);
    try
    {
      DNConfigAttribute alternateDNsAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(alternateDNsStub);

      if (alternateDNsAttr != null)
      {
        // There were alternate DNs provided, so see if there are any duplicate
        // values that are already registered for a different root DN.
        for (DN alternateBindDN : alternateDNsAttr.pendingValues())
        {
          DN rootDN = DirectoryServer.getActualRootBindDN(alternateBindDN);
          if ((rootDN != null) && (! rootDN.equals(configEntryDN)))
          {
            msgID = MSGID_CONFIG_ROOTDN_CONFLICTING_MAPPING;
            String message = getMessage(msgID, String.valueOf(alternateBindDN),
                                        String.valueOf(configEntryDN),
                                        String.valueOf(rootDN));
            unacceptableReason.append(message);
            return false;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_ROOTDN_CANNOT_PARSE_ALTERNATE_BIND_DNS;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      unacceptableReason.append(message);
      return false;
    }


    // If we've gotten here then the root DN entry appears to be acceptable.
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


    // Get the set of alternate bind DNs for the entry, if there are any.
    List<DN> alternateBindDNs = null;
    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ALTERNATE_BIND_DN;
    DNConfigAttribute alternateDNsStub =
         new DNConfigAttribute(ATTR_ROOTDN_ALTERNATE_BIND_DN, getMessage(msgID),
                               false, true, false);
    try
    {
      DNConfigAttribute alternateDNsAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(alternateDNsStub);
      if (alternateDNsAttr != null)
      {
        alternateBindDNs = alternateDNsAttr.activeValues();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_ROOTDN_CANNOT_PARSE_ALTERNATE_BIND_DNS;
      messages.add(getMessage(msgID, stackTraceToSingleLineString(e)));

      resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      List<DN> existingMappings = bindMappings.get(configEntryDN);
      if (existingMappings != null)
      {
        for (DN mappedDN : existingMappings)
        {
          if ((alternateBindDNs == null) ||
              (! alternateBindDNs.contains(mappedDN)))
          {
            DirectoryServer.deregisterAlternateRootBindDN(mappedDN);
          }
        }
      }

      if (alternateBindDNs == null)
      {
        alternateBindDNs = new ArrayList<DN>(0);
      }
      else
      {
        for (DN alternateBindDN : alternateBindDNs)
        {
          try
          {
            DirectoryServer.registerAlternateRootDN(configEntryDN,
                                                    alternateBindDN);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, de);
            }

            msgID = MSGID_CONFIG_ROOTDN_CANNOT_REGISTER_ALTERNATE_BIND_DN;
            messages.add(getMessage(msgID, String.valueOf(alternateBindDN),
                                    String.valueOf(configEntryDN)));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }
          }
        }
      }

      bindMappings.put(configEntryDN, alternateBindDNs);
      DirectoryServer.registerRootDN(configEntryDN);
      configEntry.registerChangeListener(this);
    }


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


    // See if the entry is registered as a root DN.  If so, then deregister it
    // and any alternate bind DNs that it might have.
    List<DN> alternateBindDNs = bindMappings.remove(configEntryDN);
    if (alternateBindDNs != null)
    {
      for (DN alternateBindDN : alternateBindDNs)
      {
        DirectoryServer.deregisterAlternateRootBindDN(alternateBindDN);
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired);
  }



  /**
   * Retrieves the DN of the configuration entry with which this
   * component is associated.
   *
   * @return  The DN of the configuration entry with which this
   *          component is associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return rootDNConfigBaseDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated
   * with this configurable component.
   *
   * @return  The set of configuration attributes that are associated
   *          with this configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    LinkedList<String> currentValues = new LinkedList<String>();
    for (Privilege p : rootPrivileges)
    {
      currentValues.add(p.getName());
    }

    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ROOT_PRIVILEGE;
    attrList.add(new MultiChoiceConfigAttribute(
                          ATTR_DEFAULT_ROOT_PRIVILEGE_NAME, getMessage(msgID),
                          false, true, false, Privilege.getPrivilegeNames(),
                          currentValues));

    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an
   * acceptable configuration for this component.  If it does not,
   * then detailed information about the problem(s) should be added to
   * the provided list.
   *
   * @param  configEntry          The configuration entry for which to
   *                              make the determination.
   * @param  unacceptableReasons  A list that can be used to hold
   *                              messages about why the provided
   *                              entry does not have an acceptable
   *                              configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an
   *          acceptable configuration for this component, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {


    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ROOT_PRIVILEGE;
    MultiChoiceConfigAttribute rootPrivStub =
         new MultiChoiceConfigAttribute(ATTR_DEFAULT_ROOT_PRIVILEGE_NAME,
                                        getMessage(msgID), false, true, false,
                                        Privilege.getPrivilegeNames());
    try
    {
      MultiChoiceConfigAttribute rootPrivAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(rootPrivStub);
      if (rootPrivAttr != null)
      {
        for (String value : rootPrivAttr.activeValues())
        {
          String privName = toLowerCase(value);
          Privilege p = Privilege.privilegeForName(privName);
          if (p == null)
          {
            msgID = MSGID_CONFIG_ROOTDN_UNRECOGNIZED_PRIVILEGE;
            String message = getMessage(msgID, ATTR_DEFAULT_ROOT_PRIVILEGE_NAME,
                                        String.valueOf(rootDNConfigBaseDN),
                                        String.valueOf(value));
            unacceptableReasons.add(message);
            return false;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_CONFIG_ROOTDN_ERROR_DETERMINING_ROOT_PRIVILEGES;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained
   * in the provided entry.  Information about the result of this
   * processing should be added to the provided message list.
   * Information should always be added to this list if a
   * configuration change could not be applied.  If detailed results
   * are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not
   * changed) should also be included.
   *
   * @param  configEntry      The entry containing the new
   *                          configuration to apply for this
   *                          component.
   * @param  detailedResults  Indicates whether detailed information
   *                          about the processing should be added to
   *                          the list.
   *
   * @return  Information about the result of the configuration
   *          update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {


    ResultCode        resultCode          = ResultCode.SUCCESS;
    ArrayList<String> messages            = new ArrayList<String>();
    boolean           adminActionRequired = false;


    LinkedHashSet<Privilege> newRootPrivileges =
         new LinkedHashSet<Privilege>(Privilege.getDefaultRootPrivileges());

    int msgID = MSGID_CONFIG_ROOTDN_DESCRIPTION_ROOT_PRIVILEGE;
    MultiChoiceConfigAttribute rootPrivStub =
         new MultiChoiceConfigAttribute(ATTR_DEFAULT_ROOT_PRIVILEGE_NAME,
                                        getMessage(msgID), false, true, false,
                                        Privilege.getPrivilegeNames());
    try
    {
      MultiChoiceConfigAttribute rootPrivAttr =
           (MultiChoiceConfigAttribute)
           configEntry.getConfigAttribute(rootPrivStub);
      if (rootPrivAttr != null)
      {
        ArrayList<Privilege> privList = new ArrayList<Privilege>();
        for (String value : rootPrivAttr.activeValues())
        {
          String privName = toLowerCase(value);
          Privilege p = Privilege.privilegeForName(privName);
          if (p == null)
          {
            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
            }

            msgID = MSGID_CONFIG_ROOTDN_UNRECOGNIZED_PRIVILEGE;
            messages.add(getMessage(msgID, ATTR_DEFAULT_ROOT_PRIVILEGE_NAME,
                                    String.valueOf(rootDNConfigBaseDN),
                                    String.valueOf(value)));
          }
          else
          {
            privList.add(p);
          }
        }

        newRootPrivileges = new LinkedHashSet<Privilege>(privList);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
      }

      msgID = MSGID_CONFIG_ROOTDN_ERROR_DETERMINING_ROOT_PRIVILEGES;
      messages.add(getMessage(msgID, stackTraceToSingleLineString(e)));
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      rootPrivileges = newRootPrivileges;

      if (detailedResults)
      {
        msgID = MSGID_CONFIG_ROOTDN_UPDATED_PRIVILEGES;
        messages.add(getMessage(msgID));
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

