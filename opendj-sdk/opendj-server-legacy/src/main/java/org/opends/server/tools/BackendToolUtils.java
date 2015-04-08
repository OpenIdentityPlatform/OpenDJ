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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.tools;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class provides utility functions for all JE related client tools.
 */
public class BackendToolUtils
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int ERROR = 1;
  private static final int SUCCESS = 0;

  /**
   * Retrieves information about the backends defined in the Directory Server
   * configuration.
   *
   * @param backendList
   *          A list into which instantiated (but not initialized) backend
   *          instances will be placed.
   * @param entryList
   *          A list into which the config entries associated with the backends
   *          will be placed.
   * @param dnList
   *          A list into which the set of base DNs for each backend will be
   *          placed.
   * @return 0 if everything went fine. 1 if an error occurred.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static int getBackends(final ArrayList<Backend> backendList, final ArrayList<BackendCfg> entryList,
      final ArrayList<List<DN>> dnList)
  {
    try
    {
      final DN backendBaseDN = getBackendBaseDN();
      final ConfigEntry baseEntry = getBaseEntry(backendBaseDN);

      // Iterate through the immediate children, attempting to parse them as backends.
      final RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
      for (final ConfigEntry configEntry : baseEntry.getChildren().values())
      {
        final String backendID = getBackendID(configEntry);
        final String backendClassName = getBackendClassName(configEntry);
        if (backendID == null || backendClassName == null)
        {
          continue;
        }

        final Class<?> backendClass = getBackendClass(backendClassName, configEntry);
        final Backend backend;
        final BackendCfg cfg;
        try
        {
          backend = (Backend) backendClass.newInstance();
          backend.setBackendID(backendID);
          cfg = root.getBackend(backendID);
          backend.configureBackend(cfg, DirectoryServer.getInstance().getServerContext());
        }
        catch (final Exception e)
        {
          logger.error(
              ERR_CANNOT_INSTANTIATE_BACKEND_CLASS, backendClassName, configEntry.getDN(), getExceptionMessage(e));
          return ERROR;
        }

        backendList.add(backend);
        entryList.add(cfg);
        dnList.add(getBaseDNsForEntry(configEntry));
      }

      return SUCCESS;
    }
    catch (final Exception e)
    {
      // Error message has already been logged.
      return ERROR;
    }
  }

  private static List<DN> getBaseDNsForEntry(final ConfigEntry configEntry) throws Exception
  {
    try
    {
      final DNConfigAttribute baseDNStub = new DNConfigAttribute(
          ATTR_BACKEND_BASE_DN, INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS.get(), true, true, true);
      final DNConfigAttribute baseDNAttr = (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
      if (baseDNAttr != null)
      {
        return baseDNAttr.activeValues();
      }
      logger.error(ERR_NO_BASES_FOR_BACKEND, configEntry.getDN());
      return null;
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BASES_FOR_BACKEND, configEntry.getDN(), getExceptionMessage(e));
      throw e;
    }
  }

  private static Class<?> getBackendClass(String backendClassName, ConfigEntry configEntry) throws Exception
  {
    try
    {
      return Class.forName(backendClassName);
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_LOAD_BACKEND_CLASS, backendClassName, configEntry.getDN(), getExceptionMessage(e));
      throw e;
    }
  }

  private static String getBackendClassName(final ConfigEntry configEntry) throws Exception
  {
    try
    {
      final StringConfigAttribute classStub = new StringConfigAttribute(
          ATTR_BACKEND_CLASS, INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS.get(), true, false, false);
      final StringConfigAttribute classAttr = (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
      return classAttr != null ? classAttr.activeValue() : null;
    }
    catch (final org.opends.server.config.ConfigException ce)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_CLASS, configEntry.getDN(), ce.getMessage());
      throw ce;
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_CLASS, configEntry.getDN(), getExceptionMessage(e));
      throw e;
    }
  }

  private static String getBackendID(final ConfigEntry configEntry) throws Exception
  {
    try
    {
      final StringConfigAttribute idStub = new StringConfigAttribute(
          ATTR_BACKEND_ID, INFO_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID.get(), true, false, true);
      final StringConfigAttribute idAttr = (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
      return idAttr != null ? idAttr.activeValue() : null;
    }
    catch (final org.opends.server.config.ConfigException ce)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getDN(), ce.getMessage());
      throw ce;
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getDN(), getExceptionMessage(e));
      throw e;
    }
  }

  private static ConfigEntry getBaseEntry(final DN backendBaseDN) throws Exception
  {
    try
    {
      return DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (final ConfigException ce)
    {
      logger.error(ERR_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY, DN_BACKEND_BASE, ce.getMessage());
      throw ce;
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY, DN_BACKEND_BASE, getExceptionMessage(e));
      throw e;
    }
  }

  private static DN getBackendBaseDN() throws Exception
  {
    try
    {
      return DN.valueOf(DN_BACKEND_BASE);
    }
    catch (final DirectoryException de)
    {
      logger.error(ERR_CANNOT_DECODE_BACKEND_BASE_DN, DN_BACKEND_BASE, de.getMessageObject());
      throw de;
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DECODE_BACKEND_BASE_DN, DN_BACKEND_BASE, getExceptionMessage(e));
      throw e;
    }
  }

}
