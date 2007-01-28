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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.statuspanel;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.core.DirectoryServer;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ObjectClass;
import org.opends.statuspanel.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;

/**
 * This class is used to retrieve configuration information directly from the
 * config.ldif file.
 *
 */
public class ConfigFromFile
{
  private final ObjectClass connectionHandlerOc =
    DirectoryServer.getObjectClass("ds-cfg-connection-handler", true);
  private final ObjectClass ldapConnectionHandlerOc =
    DirectoryServer.getObjectClass("ds-cfg-ldap-connection-handler", true);
  private final ObjectClass jmxConnectionHandlerOc =
    DirectoryServer.getObjectClass("ds-cfg-jmx-connection-handler", true);
  private final ObjectClass backendOc =
    DirectoryServer.getObjectClass("ds-cfg-backend", true);
  private final ObjectClass administrativeUserOc =
    DirectoryServer.getObjectClass("ds-cfg-root-dn", true);

  private HashSet<ListenerDescriptor> listeners =
    new HashSet<ListenerDescriptor>();
  private HashSet<DatabaseDescriptor> databases =
    new HashSet<DatabaseDescriptor>();
  private HashSet<String> administrativeUsers = new HashSet<String>();
  private String errorMessage;


  /**
   * Default constructor.
   *
   */
  public ConfigFromFile()
  {
  }

  /**
   * Reads the configuration from the config.ldif file.  When calling this
   * method the thread is blocked until all the configuration is read.
   *
   */
  public void readConfiguration()
  {
    errorMessage = null;
    try
    {
      LDIFImportConfig c = new LDIFImportConfig(
          Utils.getConfigFileFromClasspath());
      LDIFReader reader = new LDIFReader(c);
      for (Entry entry = reader.readEntry(false); entry != null;
      entry = reader.readEntry(false))
      {
        updateConfig(entry);
      }
    }
    catch (IOException ioe)
    {
      errorMessage = Utils.getThrowableMsg(getI18n(),
          "error-reading-config-file", null, ioe);
    }
    catch (LDIFException le)
    {
      errorMessage = Utils.getThrowableMsg(getI18n(),
          "error-reading-config-file", null, le);
    }
    catch (Throwable t)
    {
      // Bug
      t.printStackTrace();
      errorMessage = Utils.getThrowableMsg(getI18n(),
          "error-reading-config-file", null, t);
    }
  }

  /**
   * Returns the Administrative User DNs found in the config.ldif.
   * @return the Administrative User DNs found in the config.ldif.
   */
  public HashSet<String> getAdministrativeUsers()
  {
    return administrativeUsers;
  }

  /**
   * Returns the database descriptors found in the config.ldif.
   * @return the database descriptors found in the config.ldif.
   */
  public HashSet<DatabaseDescriptor> getDatabases()
  {
    return databases;
  }

  /**
   * Returns the listener descriptors found in the config.ldif.
   * @return the listeners descriptors found in the config.ldif.
   */
  public HashSet<ListenerDescriptor> getListeners()
  {
    return listeners;
  }

  /**
   * Returns the error message that we got when retrieving the information
   * from the config.ldif file.
   * @return the error message that we got when retrieving the information
   * from the config.ldif file.
   */
  public String getErrorMessage()
  {
    return errorMessage;
  }

  /**
   * Returns the ldap URL that we can use to connect to the server based in
   * what we found in the config.ldif file.
   * @return the ldap URL that we can use to connect to the server based in
   * what we found in the config.ldif file.
   */
  public String getLDAPURL()
  {
    String url = null;

    for (ListenerDescriptor desc : getListeners())
    {
      if (desc.getState() == ListenerDescriptor.State.ENABLED)

      {
        int port = -1;
        try
        {
          String addressPort = desc.getAddressPort();
          int index = addressPort.indexOf(":");
          if (index != -1)
          {
            port = Integer.parseInt(addressPort.substring(index+1));
          }
          else
          {
            port = Integer.parseInt(addressPort);
          }
        }
        catch (Exception ex)
        {
          // Could not get the port
        }

        if (port != -1)
        {
          if (desc.getProtocol() == ListenerDescriptor.Protocol.LDAP)
          {
            url = "ldap://localhost:"+port;
            /* We prefer to test using the LDAP port: do not continue
             * searching */
            break;
          }
          else if (desc.getProtocol() == ListenerDescriptor.Protocol.LDAPS)
          {
            url = "ldaps://localhost:"+port;
          }
        }
      }
    }
    return url;
  }

  /**
   * An convenience method to know if the provided ID corresponds to a
   * configuration backend or not.
   * @param id the backend ID to analyze
   * @return <CODE>true</CODE> if the the id corresponds to a configuration
   * backend and <CODE>false</CODE> otherwise.
   */
  static boolean isConfigBackend(String id)
  {
    return "tasks".equalsIgnoreCase(id) ||
    "schema".equalsIgnoreCase(id) ||
    "config".equalsIgnoreCase(id) ||
    "monitor".equalsIgnoreCase(id) ||
    "backup".equalsIgnoreCase(id);
  }


  /**
   * Updates the configuration data we expose to the user with the provided
   * entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfig(Entry entry)
  {

   if (entry.hasObjectClass(connectionHandlerOc))
   {
     updateConfigWithConnectionHandler(entry);
   }
   else if (entry.hasObjectClass(backendOc))
   {
     updateConfigWithBackend(entry);
   }
   else if (entry.hasObjectClass(administrativeUserOc))
   {
     updateConfigWithAdministrativeUser(entry);
   }
  }

  /**
   * Updates the listener configuration data we expose to the user with the
   * provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithConnectionHandler(Entry entry)
  {
    String address = getFirstValue(entry, "ds-cfg-listen-address");
    String port = getFirstValue(entry, "ds-cfg-listen-port");
    String addressPort;

    boolean isSecure = "true".equalsIgnoreCase(
        getFirstValue(entry, "ds-cfg-use-ssl"));

    ListenerDescriptor.Protocol protocol;
    String protocolDescription;

    ListenerDescriptor.State state;
    if (entry.hasObjectClass(ldapConnectionHandlerOc))
    {
      addressPort = address+":"+port;
      if (isSecure)
      {
        protocolDescription = getMsg("ldaps-protocol-label");
        protocol = ListenerDescriptor.Protocol.LDAPS;
      }
      else
      {
        protocolDescription = getMsg("ldap-protocol-label");
        protocol = ListenerDescriptor.Protocol.LDAP;
      }
      boolean enabled = "true".equalsIgnoreCase(
          getFirstValue(entry, "ds-cfg-connection-handler-enabled"));
      if (enabled)
      {
        state = ListenerDescriptor.State.ENABLED;
      }
      else
      {
        state = ListenerDescriptor.State.DISABLED;
      }
    }
    else if (entry.hasObjectClass(jmxConnectionHandlerOc))
    {
      addressPort = "0.0.0.0:"+port;
      if (isSecure)
      {
        protocolDescription = getMsg("jmx-secure-protocol-label");
        protocol = ListenerDescriptor.Protocol.JMXS;
      }
      else
      {
        protocolDescription = getMsg("jmx-protocol-label");
        protocol = ListenerDescriptor.Protocol.JMX;
      }
      boolean enabled = "true".equalsIgnoreCase(
          getFirstValue(entry, "ds-cfg-connection-handler-enabled"));
      if (enabled)
      {
        state = ListenerDescriptor.State.ENABLED;
      }
      else
      {
        state = ListenerDescriptor.State.DISABLED;
      }
    }
    else
    {
      addressPort = getMsg("unknown-label");
      protocolDescription = null;
      protocol = ListenerDescriptor.Protocol.OTHER;
      /* Try to figure a name from the cn */
      String cn = getFirstValue(entry, "cn");
      if (cn != null)
      {
        int index = cn.toLowerCase().indexOf("connection handler");
        if (index > 0)
        {
          protocolDescription = cn.substring(0, index).trim();
        }
        else
        {
          protocolDescription = cn;
        }
      }
      else
      {
        protocolDescription = getMsg("undefined-protocol-label");
      }
      state = ListenerDescriptor.State.UNKNOWN;
    }
    listeners.add(new ListenerDescriptor(addressPort, protocol,
        protocolDescription, state));
  }

  /**
   * Updates the database configuration data we expose to the user with the
   * provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithBackend(Entry entry)
  {
    String baseDn = getFirstValue(entry, "ds-cfg-backend-base-dn");
    String id = getFirstValue(entry, "ds-cfg-backend-id");
    int nEntries = -1; // Unknown

    if (!isConfigBackend(id))
    {
      databases.add(new DatabaseDescriptor(id, baseDn, nEntries));
    }
  }

  /**
   * Updates the administrative user configuration data we expose to the user
   * with the provided entry object.
   * @param entry the entry to analyze.
   */
  private void updateConfigWithAdministrativeUser(Entry entry)
  {
    administrativeUsers.addAll(getValues(entry, "ds-cfg-alternate-bind-dn"));
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /*
   * The following 2 methods are convenience methods to retrieve String values
   * from an entry.
   */
  private Set<String> getValues(Entry entry, String attrName)
  {
    Set<String> values = new HashSet<String>();
    List<Attribute> attrs = entry.getAttribute(attrName);
    if ((attrs != null) && attrs.size() > 0)
    {
      Attribute attr = attrs.iterator().next();
      LinkedHashSet<AttributeValue> vs = attr.getValues();
      if ((vs != null) && (vs.size() > 0))
      {
        for (AttributeValue v : vs)
        {
          values.add(v.getStringValue());
        }
      }
    }
    return values;
  }

  private String getFirstValue(Entry entry, String attrName)
  {
    String v = null;
    Set<String> values = getValues(entry, attrName);
    if (values.size() > 0)
    {
      v = values.iterator().next();
    }
    return v;
  }
}
