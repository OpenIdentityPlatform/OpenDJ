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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
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
       implements ConfigChangeListener, ConfigAddListener, ConfigDeleteListener
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.RootDNConfigManager";



  // A mapping between each root DN user entry and a list of the alternate
  // bind DNs for that user.
  private ConcurrentHashMap<DN,List<DN>> bindMappings;



  /**
   * Creates a new instance of this root DN config manager.
   */
  public RootDNConfigManager()
  {
    assert debugConstructor(CLASS_NAME);

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
    assert debugEnter(CLASS_NAME, "initializeRootDNs");


    // First, get the base configuration entry for the root DNs.
    ConfigHandler configHandler = DirectoryServer.getConfigHandler();
    ConfigEntry   baseEntry;
    try
    {
      DN configBase = DN.decode(DN_ROOT_DN_CONFIG_BASE);
      baseEntry = configHandler.getConfigEntry(configBase);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeRootDNs", e);

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


    // Register with the configuration base entry as an add and delete listener.
    // So that we will be notified of attempts to create or remove root DN
    // entries.
    baseEntry.registerAddListener(this);
    baseEntry.registerDeleteListener(this);


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
    assert debugEnter(CLASS_NAME, "configChangeIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


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
      assert debugException(CLASS_NAME, "configChangeIsAcceptable", e);

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
    assert debugEnter(CLASS_NAME, "applyConfigurationChange",
                      String.valueOf(configEntry));


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
      assert debugException(CLASS_NAME, "applyConfigurationChange", e);

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
            assert debugException(CLASS_NAME, "applyConfigurationChange", de);

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
    assert debugEnter(CLASS_NAME, "configAddIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


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
      assert debugException(CLASS_NAME, "configAddIsAcceptable", e);

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
    assert debugEnter(CLASS_NAME, "applyConfigurationAdd",
                      String.valueOf(configEntry));


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
      assert debugException(CLASS_NAME, "applyConfigurationAdd", e);

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
            assert debugException(CLASS_NAME, "applyConfigurationAdd", de);

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
    assert debugEnter(CLASS_NAME, "configDeleteIsAcceptable",
                      String.valueOf(configEntry), "java.lang.StringBuilder");


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
    assert debugEnter(CLASS_NAME, "applyConfigurationDelete",
                      String.valueOf(configEntry));


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
}

