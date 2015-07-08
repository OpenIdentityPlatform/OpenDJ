/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.messages.TaskMessages;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RawModification;
import org.opends.server.util.ServerConstants;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a number of static utility methods for server tasks.
 */
public class TaskUtils
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();




  /**
   * Get the backend ID of a backend configuration entry.
   *
   * @param configEntry A backend configuration entry.
   * @return The backend ID.
   */
  public static String getBackendID(ConfigEntry configEntry)
  {
    try
    {
      StringConfigAttribute idStub =
           new StringConfigAttribute(
                   ATTR_BACKEND_ID,
                   INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID.get(),
                   true, false, true);
      StringConfigAttribute idAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
      return idAttr.activeValue();
    }
    catch (org.opends.server.config.ConfigException ce)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getDN(), ce.getMessage());
      return null;
    }
    catch (Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getDN(), getExceptionMessage(e));
      return null;
    }
  }

  /**
   * Get all the backend configuration entries defined in the server mapped
   * by their backend ID.
   * @return A map of backend IDs to their corresponding configuration entries.
   */
  public static Map<String,ConfigEntry> getBackendConfigEntries()
  {
    Map<String,ConfigEntry> configEntries = new HashMap<>();

    // FIXME The error messages should not be the LDIF import messages

    // Get the base entry for all backend configuration.
    DN backendBaseDN;
    try
    {
      backendBaseDN = DN.valueOf(DN_BACKEND_BASE);
    }
    catch (DirectoryException de)
    {
      logger.error(ERR_CANNOT_DECODE_BACKEND_BASE_DN, DN_BACKEND_BASE, de.getMessageObject());
      return configEntries;
    }
    catch (Exception e)
    {
      logger.error(ERR_CANNOT_DECODE_BACKEND_BASE_DN, DN_BACKEND_BASE, getExceptionMessage(e));
      return configEntries;
    }

    ConfigEntry baseEntry;
    try
    {
      baseEntry = DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (ConfigException ce)
    {
      logger.error(ERR_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY, DN_BACKEND_BASE, ce.getMessage());
      return configEntries;
    }
    catch (Exception e)
    {
      logger.error(ERR_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY, DN_BACKEND_BASE, getExceptionMessage(e));
      return configEntries;
    }


    // Iterate through the immediate children, attempting to parse them as
    // backends.
    for (ConfigEntry configEntry : baseEntry.getChildren().values())
    {
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID;
      try
      {
        StringConfigAttribute idStub =
             new StringConfigAttribute(
                     ATTR_BACKEND_ID,
                     INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID.get(),
                     true, false, true);
        StringConfigAttribute idAttr =
             (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
        if (idAttr == null)
        {
          continue;
        }
        else
        {
          backendID = idAttr.activeValue();
        }
      }
      catch (org.opends.server.config.ConfigException ce)
      {
        logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getDN(), ce.getMessage());
        continue;
      }
      catch (Exception e)
      {
        logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getDN(), getExceptionMessage(e));
        continue;
      }

      configEntries.put(backendID, configEntry);
    }

    return configEntries;
  }

  /**
   * Get the configuration entry for a given backend.
   *
   * @param backend The backend whose configuration entry is wanted.
   * @return The configuration entry of the backend, or null if it could not
   * be found.
   */
  public static BackendCfg getConfigEntry(Backend backend)
  {
    RootCfg root = ServerManagementContext.getInstance().
         getRootConfiguration();
    try
    {
      return root.getBackend(backend.getBackendID());
    }
    catch (ConfigException e)
    {
      return null;
    }
  }



  /**
   * Enables a backend using an internal modify operation on the
   * backend configuration entry.
   *
   * @param backendID Identifies the backend to be enabled.
   * @throws DirectoryException If the internal modify operation failed.
   */
  public static void enableBackend(String backendID)
       throws DirectoryException
  {
    DN configEntryDN;
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
    try
    {
      BackendCfg cfg = root.getBackend(backendID);
      configEntryDN = cfg.dn();
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject(), e);
    }

    ArrayList<ByteString> valueList = new ArrayList<>(1);
    valueList.add(ServerConstants.TRUE_VALUE);
    LDAPAttribute a = new LDAPAttribute(ATTR_BACKEND_ENABLED, valueList);

    LDAPModification m = new LDAPModification(ModificationType.REPLACE, a);

    ArrayList<RawModification> modList = new ArrayList<>(1);
    modList.add(m);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String backendDNString = configEntryDN.toString();
    ByteString rawEntryDN =
        ByteString.valueOf(backendDNString);
    ModifyOperation internalModify = conn.processModify(rawEntryDN, modList);

    ResultCode resultCode = internalModify.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      LocalizableMessage message =
          TaskMessages.ERR_TASK_CANNOT_ENABLE_BACKEND.get(backendDNString);
      throw new DirectoryException(resultCode, message);
    }
  }



  /**
   * Disables a backend using an internal modify operation on the
   * backend configuration entry.
   *
   * @param backendID Identifies the backend to be disabled.
   * @throws DirectoryException If the internal modify operation failed.
   */
  public static void disableBackend(String backendID)
       throws DirectoryException
  {
    DN configEntryDN;
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
    try
    {
      BackendCfg cfg = root.getBackend(backendID);
      configEntryDN = cfg.dn();
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject(), e);
    }

    ArrayList<ByteString> valueList = new ArrayList<>(1);
    valueList.add(ServerConstants.FALSE_VALUE);
    LDAPAttribute a = new LDAPAttribute(ATTR_BACKEND_ENABLED, valueList);

    LDAPModification m = new LDAPModification(ModificationType.REPLACE, a);

    ArrayList<RawModification> modList = new ArrayList<>(1);
    modList.add(m);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String backendDNString = configEntryDN.toString();
    ByteString rawEntryDN =
        ByteString.valueOf(backendDNString);
    ModifyOperation internalModify = conn.processModify(rawEntryDN, modList);

    ResultCode resultCode = internalModify.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      LocalizableMessage message =
          TaskMessages.ERR_TASK_CANNOT_DISABLE_BACKEND.get(backendDNString);
      throw new DirectoryException(resultCode, message);
    }
  }



  /**
   * Get the single boolean value of an entry attribute that is defined in the
   * schema as a single valued boolean attribute, and that is not expected to
   * have attribute options.
   *
   * @param attrList The attribute value of the entry attribute.
   * @param defaultValue The default value to be returned if there is no
   * recognizable boolean attribute value.
   * @return The boolean value of the attribute, or the provided default value
   * if there is no value.
   */
  public static boolean getBoolean(List<Attribute> attrList,
                                   boolean defaultValue)
  {
    if ((attrList == null) || attrList.isEmpty())
    {
      return defaultValue;
    }

    for (Attribute a : attrList)
    {
      for (ByteString v  : a)
      {
        String valueString = toLowerCase(v.toString());
        if (valueString.equals("true") || valueString.equals("yes") ||
            valueString.equals("on") || valueString.equals("1"))
        {
          return true;
        }
        else if (valueString.equals("false") || valueString.equals("no") ||
                 valueString.equals("off") || valueString.equals("0"))
        {
          return false;
        }
      }
    }

    return defaultValue;
  }



  /**
   * Get the multiple string values of an entry attribute that is defined in the
   * schema as a multi-valued string attribute, and that is not expected to
   * have attribute options.
   *
   * @param attrList The attribute values of the entry attribute.
   * @return The string values of the attribute, empty if there are none.
   */
  public static ArrayList<String> getMultiValueString(List<Attribute> attrList)
  {
    ArrayList<String> valueStrings = new ArrayList<>();

    if (attrList != null && !attrList.isEmpty())
    {
      Attribute attr = attrList.get(0);
      if (!attr.isEmpty())
      {
        for (ByteString value : attr)
        {
          valueStrings.add(value.toString());
        }
      }
    }
    return valueStrings;
  }



  /**
   * Get the single string value of an entry attribute that is defined in the
   * schema as a single valued string attribute, and that is not expected to
   * have attribute options.
   *
   * @param attrList The attribute value of the entry attribute.
   * @return The string value of the attribute, or null if there is none.
   */
  public static String getSingleValueString(List<Attribute> attrList)
  {
    if (attrList != null && !attrList.isEmpty())
    {
      Attribute attr = attrList.get(0);
      if (!attr.isEmpty())
      {
        return attr.iterator().next().toString();
      }
    }
    return null;
  }


  /**
   * Get the single integer value of an entry attribute that is defined in the
   * schema as a single valued integer attribute, and that is not expected to
   * have attribute options.
   *
   * @param attrList The attribute value of the entry attribute.
   * @param defaultValue The default value to be returned if there is no
   * recognizable integer attribute value.
   * @return The integer value of the attribute, or the provided default value
   * if there is no value.
   */
  public static int getSingleValueInteger(List<Attribute> attrList,
                                          int defaultValue)
  {
    if (attrList != null && !attrList.isEmpty())
    {
      Attribute attr = attrList.get(0);
      if (!attr.isEmpty())
      {
        try
        {
          return Integer.parseInt(attr.iterator().next().toString());
        }
        catch (NumberFormatException e)
        {
          logger.traceException(e);
        }
      }
    }

    return defaultValue;
  }
}
