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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

/** This class provides utility functions for all backend client tools. */
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
  public static int getBackends(final List<Backend<?>> backendList, final List<BackendCfg> entryList,
      final List<List<DN>> dnList)
  {
    try
    {
      // Iterate through the immediate children, attempting to parse them as backends.
      final RootCfg root = DirectoryServer.getInstance().getServerContext().getRootConfig();
      ConfigurationHandler configHandler = DirectoryServer.getConfigurationHandler();
      final DN backendBaseDN = getBackendBaseDN();
      for (final DN childrenDn : configHandler.getChildren(backendBaseDN))
      {
        Entry configEntry = Converters.to(configHandler.getEntry(childrenDn));
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
              ERR_CANNOT_INSTANTIATE_BACKEND_CLASS, backendClassName, configEntry.getName(), getExceptionMessage(e));
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

  /**
   * Returns a string from the single valued attribute in provided entry.
   *
   * @param entry the entry
   * @param attrName the attribute name
   * @return the string value if available or {@code null}
   */
  public static String getStringSingleValuedAttribute(Entry entry, String attrName)
  {
    List<Attribute> attributes = entry.getAttribute(attrName);
    if (!attributes.isEmpty())
    {
      Attribute attribute = attributes.get(0);
      for (ByteString byteString : attribute)
      {
        return byteString.toString();
      }
    }
    return null;
  }

  private static List<DN> getBaseDNsForEntry(final Entry configEntry) throws Exception
  {
    try
    {
      List<Attribute> attributes = configEntry.getAttribute(ATTR_BACKEND_BASE_DN);
      if (!attributes.isEmpty())
      {
        Attribute attribute = attributes.get(0);
        List<DN> dns = new ArrayList<>();
        for (ByteString byteString : attribute)
        {
          dns.add(DN.valueOf(byteString.toString()));
        }
        return dns;
      }
      logger.error(ERR_NO_BASES_FOR_BACKEND, configEntry.getName());
      return null;
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BASES_FOR_BACKEND, configEntry.getName(), getExceptionMessage(e));
      throw e;
    }
  }

  private static Class<?> getBackendClass(String backendClassName, Entry configEntry) throws Exception
  {
    try
    {
      return Class.forName(backendClassName);
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_LOAD_BACKEND_CLASS, backendClassName, configEntry.getName(), getExceptionMessage(e));
      throw e;
    }
  }

  private static String getBackendClassName(final Entry configEntry) throws Exception
  {
    try
    {
      return getStringSingleValuedAttribute(configEntry, ATTR_BACKEND_CLASS);
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_CLASS, configEntry.getName(), getExceptionMessage(e));
      throw e;
    }
  }

  private static String getBackendID(final Entry configEntry) throws Exception
  {
    try
    {
      return getStringSingleValuedAttribute(configEntry, ATTR_BACKEND_ID);
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DETERMINE_BACKEND_ID, configEntry.getName(), getExceptionMessage(e));
      throw e;
    }
  }

  private static DN getBackendBaseDN() throws Exception
  {
    try
    {
      return DN.valueOf(DN_BACKEND_BASE);
    }
    catch (final Exception e)
    {
      logger.error(ERR_CANNOT_DECODE_BACKEND_BASE_DN, DN_BACKEND_BASE, getExceptionMessage(e));
      throw e;
    }
  }
}
