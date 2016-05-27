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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.tasks;

import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.tools.BackendToolUtils;
import org.opends.server.types.Entry;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.types.Attribute;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.messages.TaskMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.ServerConstants.*;
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
  public static String getBackendID(Entry configEntry)
  {
    try
    {
      return BackendToolUtils.getStringSingleValuedAttribute(configEntry, ATTR_BACKEND_ID);
    }
    catch (Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getName(), getExceptionMessage(e));
      return null;
    }
  }

  /**
   * Get all the backend configuration entries defined in the server mapped
   * by their backend ID.
   * @return A map of backend IDs to their corresponding configuration entries.
   */
  public static Map<String,Entry> getBackendConfigEntries()
  {
    Map<String,Entry> configEntries = new HashMap<>();

    // FIXME The error messages should not be the LDIF import messages

    // Get the base entry for all backend configuration.
    DN backendBaseDN;
    try
    {
      backendBaseDN = DN.valueOf(DN_BACKEND_BASE);
    }
    catch (Exception e)
    {
      logger.error(ERR_CANNOT_DECODE_BACKEND_BASE_DN, DN_BACKEND_BASE, getExceptionMessage(e));
      return configEntries;
    }

    // Iterate through the immediate children, attempting to parse them as
    // backends.
    try
    {
      ConfigurationHandler configHandler = DirectoryServer.getConfigurationHandler();
      for (DN childrenDn : configHandler.getChildren(backendBaseDN))
      {
        // Get the backend ID attribute from the entry.  If there isn't one, then
        // skip the entry.
        Entry configEntry = null;
        String backendID;
        try
        {
          configEntry = Converters.to(configHandler.getEntry(childrenDn));
          backendID = BackendToolUtils.getStringSingleValuedAttribute(configEntry, ATTR_BACKEND_ID);
          if (backendID == null)
          {
            continue;
          }
        }
        catch (Exception e)
        {
          logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, childrenDn, getExceptionMessage(e));
          continue;
        }

        configEntries.put(backendID, configEntry);
      }
    }
    catch (ConfigException e)
    {
      logger.error(ERR_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY, DN_BACKEND_BASE, e.getMessage());
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
  public static BackendCfg getConfigEntry(Backend<?> backend)
  {
    try
    {
      return getRootConfig().getBackend(backend.getBackendID());
    }
    catch (ConfigException e)
    {
      return null;
    }
  }

  private static RootCfg getRootConfig()
  {
    return DirectoryServer.getInstance().getServerContext().getRootConfig();
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
    enableBackend(backendID, TRUE_VALUE, ERR_TASK_CANNOT_ENABLE_BACKEND);
  }



  /**
   * Disables a backend using an internal modify operation on the
   * backend configuration entry.
   *
   * @param backendID Identifies the backend to be disabled.
   * @throws DirectoryException If the internal modify operation failed.
   */
  public static void disableBackend(String backendID) throws DirectoryException
  {
    enableBackend(backendID, FALSE_VALUE, ERR_TASK_CANNOT_DISABLE_BACKEND);
  }

  private static void enableBackend(String backendID, ByteString enableValue, Arg1<Object> errorMsg)
        throws DirectoryException {
    DN configEntryDN;
    try
    {
      configEntryDN = getRootConfig().getBackend(backendID).dn();
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), e.getMessageObject(), e);
    }

    ModifyRequest modifyRequest = newModifyRequest(configEntryDN)
        .addModification(REPLACE, ATTR_BACKEND_ENABLED, enableValue);
    ModifyOperation modifyOp = getRootConnection().processModify(modifyRequest);

    ResultCode resultCode = modifyOp.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      throw new DirectoryException(resultCode, errorMsg.get(configEntryDN));
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
    if (!attrList.isEmpty())
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
    if (!attrList.isEmpty())
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
  public static int getSingleValueInteger(List<Attribute> attrList, int defaultValue)
  {
    if (!attrList.isEmpty())
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
