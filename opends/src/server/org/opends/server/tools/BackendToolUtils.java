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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;

import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.server.ServerManagementContext;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides utility functions for all JE related client tools.
 */
public class BackendToolUtils
{
  /**
   * Retrieves information about the backends defined in the Directory Server
   * configuration.
   *
   * @param  backendList  A list into which instantiated (but not initialized)
   *                      backend instances will be placed.
   * @param  entryList    A list into which the config entries associated with
   *                      the backends will be placed.
   * @param  dnList       A list into which the set of base DNs for each backend
   *                      will be placed.
   *
   * @return 0 if everything went fine. 1 if an error occurred.
   *
   */
  @SuppressWarnings("unchecked")
  public static int getBackends(ArrayList<Backend> backendList,
                                ArrayList<BackendCfg> entryList,
                                ArrayList<List<DN>> dnList)
  {
    // Get the base entry for all backend configuration.
    DN backendBaseDN;
    try
    {
      backendBaseDN = DN.decode(DN_BACKEND_BASE);
    }
    catch (DirectoryException de)
    {
      Message message = ERR_CANNOT_DECODE_BACKEND_BASE_DN.get(
          DN_BACKEND_BASE, de.getMessageObject());
      logError(message);
      return 1;
    }
    catch (Exception e)
    {
      Message message = ERR_CANNOT_DECODE_BACKEND_BASE_DN.get(
          DN_BACKEND_BASE, getExceptionMessage(e));
      logError(message);
      return 1;
    }

    ConfigEntry baseEntry;
    try
    {
      baseEntry = DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (ConfigException ce)
    {
      Message message = ERR_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY.get(
          DN_BACKEND_BASE, ce.getMessage());
      logError(message);
      return 1;
    }
    catch (Exception e)
    {
      Message message = ERR_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY.get(
          DN_BACKEND_BASE, getExceptionMessage(e));
      logError(message);
      return 1;
    }


    // Iterate through the immediate children, attempting to parse them as
    // backends.
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
    for (ConfigEntry configEntry : baseEntry.getChildren().values())
    {
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID;
      try
      {

        StringConfigAttribute idStub =
             new StringConfigAttribute(ATTR_BACKEND_ID,
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
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_DETERMINE_BACKEND_ID.get(
            String.valueOf(configEntry.getDN()), ce.getMessage());
        logError(message);
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_DETERMINE_BACKEND_ID.get(
            String.valueOf(configEntry.getDN()), getExceptionMessage(e));
        logError(message);
        return 1;
      }


      // Get the backend class name attribute from the entry.  If there isn't
      // one, then just skip the entry.
      String backendClassName;
      try
      {

        StringConfigAttribute classStub =
             new StringConfigAttribute(
                     ATTR_BACKEND_CLASS,
                     INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS.get(),
                     true, false, false);
        StringConfigAttribute classAttr =
             (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          continue;
        }
        else
        {
          backendClassName = classAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        Message message = ERR_CANNOT_DETERMINE_BACKEND_CLASS.get(
            String.valueOf(configEntry.getDN()), ce.getMessage());
        logError(message);
        return 1;
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_DETERMINE_BACKEND_CLASS.get(
            String.valueOf(configEntry.getDN()), getExceptionMessage(e));
        logError(message);
        return 1;
      }

      Class backendClass;
      try
      {
        backendClass = Class.forName(backendClassName);
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_LOAD_BACKEND_CLASS.
            get(backendClassName, String.valueOf(configEntry.getDN()),
                getExceptionMessage(e));
        logError(message);
        return 1;
      }

      Backend backend;
      BackendCfg cfg;
      try
      {
        backend = (Backend) backendClass.newInstance();
        backend.setBackendID(backendID);
        cfg = root.getBackend(backendID);
        backend.configureBackend(cfg);
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_INSTANTIATE_BACKEND_CLASS.
            get(backendClassName, String.valueOf(configEntry.getDN()),
                getExceptionMessage(e));
        logError(message);
        return 1;
      }


      // Get the base DN attribute from the entry.  If there isn't one, then
      // just skip this entry.
      List<DN> baseDNs = null;
      try
      {

        DNConfigAttribute baseDNStub =
             new DNConfigAttribute(
                     ATTR_BACKEND_BASE_DN,
                     INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS.get(),
                     true, true, true);
        DNConfigAttribute baseDNAttr =
             (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
        if (baseDNAttr == null)
        {
          Message message =
              ERR_NO_BASES_FOR_BACKEND.get(String.valueOf(configEntry.getDN()));
          logError(message);
        }
        else
        {
          baseDNs = baseDNAttr.activeValues();
        }
      }
      catch (Exception e)
      {
        Message message = ERR_CANNOT_DETERMINE_BASES_FOR_BACKEND.get(
            String.valueOf(configEntry.getDN()), getExceptionMessage(e));
        logError(message);
        return 1;
      }


      backendList.add(backend);
      entryList.add(cfg);
      dnList.add(baseDNs);
    }
    return 0;
  }
}
