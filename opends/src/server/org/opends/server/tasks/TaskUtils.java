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
package org.opends.server.tasks;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ConfigMessages.
     MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
import static org.opends.server.util.StaticUtils.*;

import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.loggers.Error;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.messages.TaskMessages;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * This class defines a number of static utility methods for server tasks.
 */
public class TaskUtils
{



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
      int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
      StringConfigAttribute idStub =
           new StringConfigAttribute(ATTR_BACKEND_ID,
                                     getMessage(msgID),
                                     true, false, true);
      StringConfigAttribute idAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
      String backendID = idAttr.activeValue();
      return backendID;
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_ID;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                  ce.getMessage());
      Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      return null;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_ID;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                  stackTraceToSingleLineString(e));
      Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
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
    Map<String,ConfigEntry> configEntries = new HashMap<String,ConfigEntry>();

    // FIXME The error messages should not be the LDIF import messages

    // Get the base entry for all backend configuration.
    DN backendBaseDN = null;
    try
    {
      backendBaseDN = DN.decode(DN_BACKEND_BASE);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE, de.getErrorMessage());
      Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
      return configEntries;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
      return configEntries;
    }

    ConfigEntry baseEntry = null;
    try
    {
      baseEntry = DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE, ce.getMessage());
      Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
      return configEntries;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                     message, msgID);
      return configEntries;
    }


    // Iterate through the immediate children, attempting to parse them as
    // backends.
    for (ConfigEntry configEntry : baseEntry.getChildren().values())
    {
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
        StringConfigAttribute idStub =
             new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID),
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
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        continue;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFIMPORT_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        Error.logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
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
  public static ConfigEntry getConfigEntry(Backend backend)
  {
    Map<String,ConfigEntry> configEntries = getBackendConfigEntries();
    return configEntries.get(backend.getBackendID());
  }



  /**
   * Enable or disable a backend using an internal modify operation on the
   * backend configuration entry.
   *
   * @param configEntry The configuration entry of the backend to be enabled
   * or disabled.
   * @param enable Specifies whether the backend should be enabled or disabled.
   * @throws DirectoryException If the internal modify operation failed.
   */
  public static void setBackendEnabled(ConfigEntry configEntry, boolean enable)
       throws DirectoryException
  {
    ArrayList<ASN1OctetString> valueList = new ArrayList<ASN1OctetString>(1);
    if (enable)
    {
      valueList.add(new ASN1OctetString("TRUE"));
    }
    else
    {
      valueList.add(new ASN1OctetString("FALSE"));
    }
    LDAPAttribute a = new LDAPAttribute(ATTR_BACKEND_ENABLED, valueList);

    LDAPModification m = new LDAPModification(ModificationType.REPLACE, a);

    ArrayList<LDAPModification> modList = new ArrayList<LDAPModification>(1);
    modList.add(m);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    String backendDNString = configEntry.getDN().toString();
    ASN1OctetString rawEntryDN =
         new ASN1OctetString(backendDNString);
    ModifyOperation internalModify = conn.processModify(rawEntryDN, modList);

    ResultCode resultCode = internalModify.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      int msgID;
      if (enable)
      {
        msgID = TaskMessages.MSGID_TASK_CANNOT_ENABLE_BACKEND;
      }
      else
      {
        msgID = TaskMessages.MSGID_TASK_CANNOT_DISABLE_BACKEND;
      }
      String message = getMessage(msgID, backendDNString);
      throw new DirectoryException(resultCode, message, msgID);
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
      for (AttributeValue v  : a.getValues())
      {
        String valueString = toLowerCase(v.getStringValue());
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
    ArrayList<String> valueStrings = new ArrayList<String>();

    if (attrList != null && !attrList.isEmpty())
    {
      Attribute attr = attrList.get(0);
      LinkedHashSet<AttributeValue> values = attr.getValues();
      if ((values != null) && (! values.isEmpty()))
      {
        for (AttributeValue value : values)
        {
          valueStrings.add(value.getStringValue());
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
    if (attrList == null || attrList.isEmpty())
    {
      return null;
    }
    String valueString = null;
    Attribute attr = attrList.get(0);
    LinkedHashSet<AttributeValue> values = attr.getValues();
    if ((values != null) && (! values.isEmpty()))
    {
      valueString = values.iterator().next().getStringValue();
    }
    return valueString;
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
      LinkedHashSet<AttributeValue> values = attr.getValues();
      if ((values != null) && (! values.isEmpty()))
      {
        String valueString = values.iterator().next().getStringValue();
        try
        {
          return Integer.parseInt(valueString);
        }
        catch (NumberFormatException e)
        {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }
        }
      }
    }

    return defaultValue;
  }
}
