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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.util;

import static org.opends.messages.AdminToolMessages.*;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ConnectionHandlerDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.server.admin.std.meta.AdministrationConnectorCfgDefn;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.InitializationException;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;

/**
 * An abstract class providing some common interface for the class that read
 * the configuration (and if the server is running, the monitoring information).
 *
 */
public abstract class ConfigReader
{
  /**
   * The class used to read the configuration from a file.
   */
  public static String configClassName;
  /**
   * The configuration file full path (-INSTANCE_ROOT-/config/config.ldif).
   */
  public static String configFile;
  /**
   * The error that occurred when setting the environment (null if no error
   * occurred).
   */
  protected static OpenDsException environmentSettingException;
  static
  {
    // This allows testing of configuration components when the OpenDS.jar
    // in the classpath does not necessarily point to the server's
    // This is done here since both implementations of ConfigReader require it.
    String installRoot = System.getProperty("org.opends.quicksetup.Root");
    if (installRoot == null) {
      installRoot = Utilities.getServerRootDirectory().getAbsolutePath();
    }
    String instanceRoot =
      Utilities.getInstanceRootDirectory(installRoot).getAbsolutePath();
    configFile = instanceRoot + File.separator + "config" + File.separator +
    "config.ldif";
    configClassName = ReadOnlyConfigFileHandler.class.getName();
    try
    {
      DirectoryEnvironmentConfig env = DirectoryServer.getEnvironmentConfig();
      env.setServerRoot(new File(installRoot));
      DirectoryServer.bootstrapClient();
      DirectoryServer.initializeJMX();
      DirectoryServer instance = DirectoryServer.getInstance();
      instance.initializeConfiguration(configClassName, configFile);
      instance.initializeSchema();
    }
    catch (Throwable t)
    {
      environmentSettingException = new OfflineUpdateException(
          ERR_CTRL_PANEL_SETTING_ENVIRONMENT.get(t.getMessage().toString()), t);
    }
  }

  /**
   * The exceptions that occurred reading the configuration.
   */
  protected List<OpenDsException> exceptions = Collections.emptyList();

  /**
   * Whether the configuration has already been read or not.
   */
  protected boolean configRead = false;

  /**
   * The set of connection listeners.
   */
  protected Set<ConnectionHandlerDescriptor> listeners = Collections.emptySet();

  /**
   * The administration connector.
   */
  protected ConnectionHandlerDescriptor adminConnector;

  /**
   * The set of backend descriptors.
   */
  protected Set<BackendDescriptor> backends = Collections.emptySet();

  /**
   * The set of administrative users.
   */
  protected Set<DN> administrativeUsers = Collections.emptySet();

  /**
   * The replication serve port (-1 if the replication server port is not
   * defined).
   */
  protected int replicationPort = -1;

  /**
   * The java version used to run the server.
   */
  protected String javaVersion;

  /**
   * The number of connections opened on the server.
   */
  protected int numberConnections;

  /**
   * Whether the schema checking is enabled or not.
   */
  protected boolean isSchemaEnabled;

  /**
   * The schema used by the server.
   */
  protected Schema schema;

  /**
   * Returns the Administrative User DNs found in the config.ldif.  The set
   * must be unmodifiable (the inheriting classes must take care of this).
   * @return the Administrative User DNs found in the config.ldif.
   */
  public Set<DN> getAdministrativeUsers()
  {
    return administrativeUsers;
  }

  /**
   * Returns the backend descriptors found in the config.ldif.  The set
   * must be unmodifiable (the inheriting classes must take care of this).
   * @return the backend descriptors found in the config.ldif.
   */
  public Set<BackendDescriptor> getBackends()
  {
    return backends;
  }

  /**
   * Returns the listener descriptors found in the config.ldif.  The set
   * must be unmodifiable (the inheriting classes must take care of this).
   * @return the listeners descriptors found in the config.ldif.
   */
  public Set<ConnectionHandlerDescriptor> getConnectionHandlers()
  {
    return listeners;
  }

  /**
   * Returns the admin connector.
   * @return the admin connector.
   */
  public ConnectionHandlerDescriptor getAdminConnector()
  {
    return adminConnector;
  }

  /**
   * Returns the list of exceptions that were encountered reading the
   * configuration.  The list must be unmodifiable (the inheriting classes must
   * take care of this).
   * @return the list of exceptions that were encountered reading the
   * configuration.
   */
  public List<OpenDsException> getExceptions()
  {
    return exceptions;
  }

  /**
   * Returns <CODE>true</CODE> if the configuration has been read at least once
   * and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the configuration has been read at least once
   * and <CODE>false</CODE> otherwise.
   */
  public boolean isConfigRead()
  {
    return configRead;
  }

  /**
   * Returns the replication server port. -1 if no replication server port is
   * defined.
   * @return the replication server port. -1 if no replication server port is
   * defined.
   */
  public int getReplicationPort()
  {
    return replicationPort;
  }

  /**
   * Returns <CODE>true</CODE> if the schema check is enabled and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the schema check is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isSchemaEnabled()
  {
    return isSchemaEnabled;
  }

  /**
   * Returns the java version used to run the server. <CODE>null</CODE> if no
   * java version is used (because the server is down).
   * @return the java version used to run the server. <CODE>null</CODE> if no
   * java version is used (because the server is down).
   */
  public String getJavaVersion()
  {
    return javaVersion;
  }

  /**
   * Returns the number of open connections on the server.   -1 if the server
   * is down.
   * @return the number of open connections on the server.
   */
  public int getOpenConnections()
  {
    return numberConnections;
  }

  /**
   * Returns the schema of the server.
   * @return the schema of the server.
   */
  public Schema getSchema()
  {
    return schema;
  }

  /**
   * Reads the schema from the files.
   * @throws ConfigException if an error occurs reading the schema.
   * @throws InitializationException if an error occurs trying to find out
   * the schema files.
   */
  protected void readSchema() throws ConfigException, InitializationException
  {
    SchemaLoader loader = new SchemaLoader();
    loader.readSchema();
    schema = loader.getSchema().duplicate();
  }

  /**
   * Method that transforms the VLV sort order value as it is defined in the
   * schema to a list of VLVSortOrder objects.
   * @param s the string in the configuration.
   * @return  a list of VLVSortOrder objects.
   */
  protected List<VLVSortOrder> getVLVSortOrder(String s)
  {
    ArrayList<VLVSortOrder> sortOrder = new ArrayList<VLVSortOrder>();
    if (s != null)
    {
      String[] attrNames = s.split(" ");
      for (int i=0; i<attrNames.length; i++)
      {
        if (attrNames[i].startsWith("+"))
        {
          sortOrder.add(new VLVSortOrder(attrNames[i].substring(1), true));
        }
        else if (attrNames[i].startsWith("-"))
        {
          sortOrder.add(new VLVSortOrder(attrNames[i].substring(1), false));
        }
        else
        {
          sortOrder.add(new VLVSortOrder(attrNames[i], true));
        }
      }
    }
    return sortOrder;
  }

  /**
   * Returns the comparator to be used to sort InetAddresses.
   * @return the comparator to be used to sort InetAddresses.
   */
  protected Comparator<InetAddress> getInetAddressComparator()
  {
    return AdministrationConnectorCfgDefn.getInstance().
    getListenAddressPropertyDefinition();
  }
}
