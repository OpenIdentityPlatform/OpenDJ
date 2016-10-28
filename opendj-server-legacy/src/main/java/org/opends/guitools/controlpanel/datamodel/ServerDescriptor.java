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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.guitools.controlpanel.datamodel.BasicMonitoringAttributes.*;
import static org.opends.server.util.SchemaUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.guitools.controlpanel.util.ConfigFromConnection;
import org.opends.quicksetup.UserData;
import org.opends.server.tools.tasks.TaskEntry;

import com.forgerock.opendj.util.OperatingSystem;

/**
 * This is just a class used to provide a data model describing what the
 * StatusPanelDialog will show to the user.
 */
public class ServerDescriptor
{
  private static String localHostName = UserData.getDefaultHostName();

  private ServerStatus status;
  private int openConnections;
  private Set<BackendDescriptor> backends = new HashSet<>();
  private Set<ConnectionHandlerDescriptor> listeners = new HashSet<>();
  private ConnectionHandlerDescriptor adminConnector;
  private Set<DN> administrativeUsers = new HashSet<>();
  private String installPath;
  private String instancePath;
  private String openDJVersion;
  private String javaVersion;
  private ArrayList<Exception> exceptions = new ArrayList<>();
  private boolean isWindowsServiceEnabled;
  private boolean isSchemaEnabled;
  private Schema schema;

  private SearchResultEntry rootMonitor;
  private SearchResultEntry jvmMemoryUsage;
  private SearchResultEntry systemInformation;
  private SearchResultEntry entryCaches;
  private SearchResultEntry workQueue;

  private Set<TaskEntry> taskEntries = new HashSet<>();

  private long runningTime = -1;
  private boolean isAuthenticated;
  private String hostName = localHostName;
  private boolean isLocal = true;

  /** Enumeration indicating the status of the server. */
  public enum ServerStatus
  {
    /** Server Started. */
    STARTED,
    /** Server Stopped. */
    STOPPED,
    /** Server Starting. */
    STARTING,
    /** Server Stopping. */
    STOPPING,
    /** Not connected to remote. */
    NOT_CONNECTED_TO_REMOTE,
    /** Status Unknown. */
    UNKNOWN
  }

  /** Default constructor. */
  public ServerDescriptor()
  {
  }

  /**
   * Return the administrative users.
   * @return the administrative users.
   */
  public Set<DN> getAdministrativeUsers()
  {
    return administrativeUsers;
  }

  /**
   * Set the administrative users.
   * @param administrativeUsers the administrative users to set
   */
  public void setAdministrativeUsers(Set<DN> administrativeUsers)
  {
    this.administrativeUsers = Collections.unmodifiableSet(administrativeUsers);
  }

  /**
   * Returns whether the schema is enabled or not.
   * @return {@code true} if the schema is enabled, {@code false} otherwise.
   */
  public boolean isSchemaEnabled()
  {
    return isSchemaEnabled;
  }

  /**
   * Sets whether the schema is enabled or not.
   * @param isSchemaEnabled {@code true} if the schema is enabled, {@code false} otherwise.
   */
  public void setSchemaEnabled(boolean isSchemaEnabled)
  {
    this.isSchemaEnabled = isSchemaEnabled;
  }

  /**
   * Return the instance path where the server databases and configuration is
   * located.
   * @return the instance path where the server databases and configuration is
   * located
   */
  public String getInstancePath()
  {
    return instancePath;
  }

  /**
   * Sets the instance path where the server databases and configuration is
   * located.
   * @param instancePath the instance path where the server databases and
   * configuration is located.
   */
  public void setInstancePath(String instancePath)
  {
    this.instancePath = instancePath;
  }

  /**
   * Return the install path where the server is installed.
   * @return the install path where the server is installed.
   */
  public String getInstallPath()
  {
    return installPath;
  }

  /**
   * Sets the install path where the server is installed.
   * @param installPath the install path where the server is installed.
   */
  public void setInstallPath(String installPath)
  {
    this.installPath = installPath;
  }

  /**
   * Returns whether the install and the instance are on the same server
   * or not.
   * @return whether the install and the instance are on the same server
   * or not.
   */
  public boolean sameInstallAndInstance()
  {
    boolean sameInstallAndInstance;
    String instance = getInstancePath();
    String install = getInstallPath();
    try
    {
      if (instance != null)
      {
        sameInstallAndInstance = instance.equals(install);
        if (!sameInstallAndInstance &&
            (isLocal() || OperatingSystem.isWindows()))
        {
          File f1 = new File(instance);
          File f2 = new File(install);
          sameInstallAndInstance =
            f1.getCanonicalFile().equals(f2.getCanonicalFile());
        }
      }
      else
      {
        sameInstallAndInstance = install == null;
      }
    }
    catch (IOException ioe)
    {
      sameInstallAndInstance = false;
    }
    return sameInstallAndInstance;
  }

  /**
   * Return the java version used to run the server.
   * @return the java version used to run the server.
   */
  public String getJavaVersion()
  {
    return javaVersion;
  }

  /**
   * Set the java version used to run the server.
   * @param javaVersion the java version used to run the server.
   */
  public void setJavaVersion(String javaVersion)
  {
    this.javaVersion = javaVersion;
  }

  /**
   * Returns the number of open connection in the server.
   * @return the number of open connection in the server.
   */
  public int getOpenConnections()
  {
    return openConnections;
  }

  /**
   * Set the number of open connections.
   * @param openConnections the number of open connections.
   */
  public void setOpenConnections(int openConnections)
  {
    this.openConnections = openConnections;
  }

  /**
   * Returns the version of the server.
   * @return the version of the server.
   */
  public String getOpenDSVersion()
  {
    return openDJVersion;
  }

  /**
   * Sets the version of the server.
   * @param openDSVersion the version of the server.
   */
  public void setOpenDJVersion(String openDSVersion)
  {
    this.openDJVersion = openDSVersion;
  }

  /**
   * Returns the status of the server.
   * @return the status of the server.
   */
  public ServerStatus getStatus()
  {
    return status;
  }

  /**
   * Sets the status of the server.
   * @param status the status of the server.
   */
  public void setStatus(ServerStatus status)
  {
    this.status = status;
  }

  /**
   * Returns the task entries.
   * @return the task entries.
   */
  public Set<TaskEntry> getTaskEntries()
  {
    return taskEntries;
  }

  /**
   * Sets the the task entries.
   * @param taskEntries the task entries.
   */
  public void setTaskEntries(Set<TaskEntry> taskEntries)
  {
    this.taskEntries = Collections.unmodifiableSet(taskEntries);
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof ServerDescriptor))
    {
      return false;
    }

    ServerDescriptor desc = (ServerDescriptor) o;
    return desc.getStatus() == getStatus()
        && desc.isLocal() == isLocal()
        && desc.isAuthenticated() == isAuthenticated()
        && desc.getOpenConnections() == getOpenConnections()
        && Objects.equals(getInstallPath(), desc.getInstallPath())
        && Objects.equals(getInstancePath(), desc.getInstancePath())
        && Objects.equals(getJavaVersion(), desc.getJavaVersion())
        && Objects.equals(getOpenDSVersion(), desc.getOpenDSVersion())
        && Objects.equals(desc.getAdministrativeUsers(), getAdministrativeUsers())
        && Objects.equals(desc.getConnectionHandlers(), getConnectionHandlers())
        && Objects.equals(desc.getBackends(), getBackends())
        && Objects.equals(desc.getExceptions(), getExceptions())
        && desc.isSchemaEnabled() == isSchemaEnabled()
        && areSchemasEqual(getSchema(), desc.getSchema())
        && (!OperatingSystem.isWindows() || desc.isWindowsServiceEnabled() == isWindowsServiceEnabled())
        && desc.getTaskEntries().equals(getTaskEntries());
  }

  @Override
  public int hashCode()
  {
    String s = installPath + openDJVersion + javaVersion + isAuthenticated;
    return status.hashCode() + openConnections + s.hashCode();
  }

  /**
   * Return whether we were authenticated when retrieving the information of
   * this ServerStatusDescriptor.
   * @return {@code true} if we were authenticated when retrieving the
   * information of this ServerStatusDescriptor and {@code false} otherwise.
   */
  public boolean isAuthenticated()
  {
    return isAuthenticated;
  }

  /**
   * Sets whether we were authenticated when retrieving the information of
   * this ServerStatusDescriptor.
   * @param isAuthenticated whether we were authenticated when retrieving the
   * information of this ServerStatusDescriptor.
   */
  public void setAuthenticated(boolean isAuthenticated)
  {
    this.isAuthenticated = isAuthenticated;
  }

  /**
   * Returns the backend descriptors of the server.
   * @return the backend descriptors of the server.
   */
  public Set<BackendDescriptor> getBackends()
  {
    return backends;
  }

  /**
   * Sets the backend descriptors of the server.
   * @param backends the database descriptors to set.
   */
  public void setBackends(Set<BackendDescriptor> backends)
  {
    this.backends = Collections.unmodifiableSet(backends);
  }

  /**
   * Returns the listener descriptors of the server.
   * @return the listener descriptors of the server.
   */
  public Set<ConnectionHandlerDescriptor> getConnectionHandlers()
  {
    return listeners;
  }

  /**
   * Sets the listener descriptors of the server.
   * @param listeners the listener descriptors to set.
   */
  public void setConnectionHandlers(Set<ConnectionHandlerDescriptor> listeners)
  {
    this.listeners = Collections.unmodifiableSet(listeners);
  }

  /**
   * Sets the schema of the server.
   * @param schema the schema of the server.
   */
  public void setSchema(Schema schema)
  {
    this.schema = schema;
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
   * Returns the host name of the server.
   * @return the host name of the server.
   */
  public String getHostname()
  {
    return hostName;
  }

  /**
   * Sets the host name of the server.
   * @param hostName the host name of the server.
   */
  public void setHostname(String hostName)
  {
    this.hostName = hostName;
  }

  /**
   * Returns whether we are trying to manage the local host.
   * @return {@code true} if we are trying to manage the local host, {@code false} otherwise.
   */
  public boolean isLocal()
  {
    return isLocal;
  }

  /**
   * Sets whether this server represents the local instance or a remote server.
   * @param isLocal whether this server represents the local instance or a
   * remote server (in another machine or in another installation on the same
   * machine).
   */
  public void setIsLocal(boolean isLocal)
  {
    this.isLocal = isLocal;
  }

  /**
   * Returns the exceptions that occurred while reading the configuration.
   * @return the exceptions that occurred while reading the configuration.
   */
  public List<Exception> getExceptions()
  {
    return Collections.unmodifiableList(exceptions);
  }

  /**
   * Sets the exceptions that occurred while reading the configuration.
   * @param exceptions exceptions that occurred while reading the
   * configuration.
   */
  public void setExceptions(Collection<Exception> exceptions)
  {
    this.exceptions.clear();
    this.exceptions.addAll(exceptions);
  }

  /**
   * Tells whether the windows service is enabled or not.
   * @return {@code true} if the windows service is enabled, {@code false} otherwise.
   */
  public boolean isWindowsServiceEnabled()
  {
    return isWindowsServiceEnabled;
  }

  /**
   * Sets whether the windows service is enabled or not.
   * @param isWindowsServiceEnabled {@code true} if the windows service is
   * enabled, {@code false} otherwise.
   */
  public void setWindowsServiceEnabled(boolean isWindowsServiceEnabled)
  {
    this.isWindowsServiceEnabled = isWindowsServiceEnabled;
  }

  /**
   * Returns whether the server is running under a windows system or not.
   * @return whether the server is running under a windows system or not.
   */
  public boolean isWindows()
  {
    if (isLocal())
    {
      return OperatingSystem.isWindows();
    }
    SearchResultEntry sr = getSystemInformationMonitor();
    if (sr == null)
    {
      return false;
    }
    String os = sr.parseAttribute("operatingSystem").asString();
    return os != null && OperatingSystem.WINDOWS.equals(OperatingSystem.forName(os));
  }

  /**
   * Method used to compare schemas.
   * Returns whether the two schemas are equal.
   * @param schema1 the first schema.
   * @param schema2 the second schema.
   * @return {@code true} if the two schemas are equal, {@code false} otherwise.
   */
  public static boolean areSchemasEqual(Schema schema1, Schema schema2)
  {
    if (schema1 == schema2)
    {
      return true;
    }
    else if (schema2 == null)
    {
      return schema1 != null;
    }
    else if (schema1 == null)
    {
      return false;
    }

    return areAttributeTypesEqual(schema1, schema2)
        && areObjectClassesEqual(schema1, schema2)
        && Objects.equals(schema1.getMatchingRules(), schema2.getMatchingRules())
        && Objects.equals(schema1.getSyntaxes(), schema2.getSyntaxes());
  }

  private static boolean areAttributeTypesEqual(Schema schema1, Schema schema2)
  {
    final List<AttributeType> attrs1 = new ArrayList<>(schema1.getAttributeTypes());
    final List<AttributeType> attrs2 = new ArrayList<>(schema2.getAttributeTypes());
    if (attrs1.size() != attrs2.size())
    {
      return false;
    }
    Collections.sort(attrs1);
    Collections.sort(attrs2);
    for (int i = 0; i < attrs1.size(); i++)
    {
      AttributeType attr1 = attrs1.get(i);
      AttributeType attr2 = attrs2.get(i);
      if (attr2 == null || !areAttributesEqual(attr1, attr2))
      {
        return false;
      }
    }
    return true;
  }

  private static boolean areObjectClassesEqual(Schema schema1, Schema schema2)
  {
    final Collection<ObjectClass> ocs1 = schema1.getObjectClasses();
    final Collection<ObjectClass> ocs2 = schema2.getObjectClasses();
    if (ocs1.size() != ocs2.size())
    {
      return false;
    }
    for (ObjectClass oc1 : ocs1)
    {
      ObjectClass oc2 = schema2.getObjectClass(oc1.getNameOrOID());
      if (oc2.isPlaceHolder() || !areObjectClassesEqual(oc1, oc2))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Method used to compare attributes defined in the schema.
   * Returns whether the two schema attributes are equal.
   * @param schema1 the first schema attribute.
   * @param schema2 the second schema attribute.
   * @return {@code true} if the two schema attributes are equal, {@code false} otherwise.
   */
  private static boolean areAttributesEqual(AttributeType attr1, AttributeType attr2)
  {
    return attr1.getOID().equals(attr2.getOID())
        && attr1.isCollective() == attr2.isCollective()
        && attr1.isNoUserModification() == attr2.isNoUserModification()
        && attr1.isObjectClass() == attr2.isObjectClass()
        && attr1.isObsolete() == attr2.isObsolete()
        && attr1.isOperational() == attr2.isOperational()
        && attr1.isSingleValue() == attr2.isSingleValue()
        && Objects.equals(attr1.getApproximateMatchingRule(), attr2.getApproximateMatchingRule())
        && Objects.equals(getElementDefinitionWithFileName(attr1), getElementDefinitionWithFileName(attr2))
        && Objects.equals(attr1.getDescription(), attr2.getDescription())
        && Objects.equals(attr1.getEqualityMatchingRule(), attr2.getEqualityMatchingRule())
        && Objects.equals(attr1.getOrderingMatchingRule(), attr2.getOrderingMatchingRule())
        && Objects.equals(attr1.getSubstringMatchingRule(), attr2.getSubstringMatchingRule())
        && Objects.equals(attr1.getSuperiorType(), attr2.getSuperiorType())
        && Objects.equals(attr1.getSyntax(), attr2.getSyntax())
        && Objects.equals(attr1.getSyntax().getOID(), attr2.getSyntax().getOID())
        && Objects.equals(attr1.getExtraProperties().keySet(), attr2.getExtraProperties().keySet())
        && Objects.equals(toSet(attr1.getNames()), toSet(attr2.getNames()));
  }

  /**
   * Method used to compare objectclasses defined in the schema.
   * Returns whether the two schema objectclasses are equal.
   * @param schema1 the first schema objectclass.
   * @param schema2 the second schema objectclass.
   * @return {@code true} if the two schema objectclasses are equal, {@code false} otherwise.
   */
  private static boolean areObjectClassesEqual(ObjectClass oc1, ObjectClass oc2)
  {
    return oc1.getOID().equals(oc2.getOID())
        && Objects.equals(getElementDefinitionWithFileName(oc1), getElementDefinitionWithFileName(oc2))
        && Objects.equals(oc1.getDescription(), oc2.getDescription())
        && Objects.equals(oc1.getObjectClassType(), oc2.getObjectClassType())
        && Objects.equals(oc1.getDeclaredOptionalAttributes(), oc2.getDeclaredOptionalAttributes())
        && Objects.equals(oc1.getDeclaredRequiredAttributes(), oc2.getDeclaredRequiredAttributes())
        && Objects.equals(oc1.getSuperiorClasses(), oc2.getSuperiorClasses())
        && Objects.equals(oc1.getExtraProperties().keySet(), oc2.getExtraProperties().keySet())
        && Objects.equals(toSet(oc1.getNames()), toSet(oc2.getNames()));
  }

  private static Set<Object> toSet(Iterable<?> iterable)
  {
    Set<Object> s = new HashSet<>();
    for (Object o : iterable)
    {
      s.add(o);
    }
    return s;
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
   * Sets the admin connector.
   * @param adminConnector the admin connector.
   */
  public void setAdminConnector(ConnectionHandlerDescriptor adminConnector)
  {
    this.adminConnector = adminConnector;
  }

  /**
   * Sets the monitoring entry for the entry caches.
   * @param entryCaches the monitoring entry for the entry caches.
   */
  public void setEntryCachesMonitor(SearchResultEntry entryCaches)
  {
    this.entryCaches = entryCaches;
  }

  /**
   * Sets the monitoring entry for the JVM memory usage.
   * @param jvmMemoryUsage the monitoring entry for the JVM memory usage.
   */
  public void setJvmMemoryUsageMonitor(SearchResultEntry jvmMemoryUsage)
  {
    this.jvmMemoryUsage = jvmMemoryUsage;
  }

  /**
   * Sets the root entry of the monitoring tree.
   * @param rootMonitor the root entry of the monitoring tree.
   */
  public void setRootMonitor(SearchResultEntry rootMonitor)
  {
    this.rootMonitor = rootMonitor;
    runningTime = computeRunningTime(rootMonitor);
  }

  private long computeRunningTime(SearchResultEntry rootMonitor)
  {
    if (rootMonitor != null)
    {
      try
      {
        String start = firstValueAsString(rootMonitor, START_DATE.getAttributeName());
        String current = firstValueAsString(rootMonitor, CURRENT_DATE.getAttributeName());
        Date startTime = ConfigFromConnection.utcParser.parse(start);
        Date currentTime = ConfigFromConnection.utcParser.parse(current);
        return currentTime.getTime() - startTime.getTime();
      }
      catch (Throwable t)
      {
        return -1;
      }
    }
    return -1;
  }

  /**
   * Returns the running time of the server in milliseconds.  Returns -1 if
   * no running time could be found.
   * @return the running time of the server in milliseconds.
   */
  public long getRunningTime()
  {
    return runningTime;
  }

  /**
   * Sets the monitoring entry for the system information.
   * @param systemInformation entry for the system information.
   */
  public void setSystemInformationMonitor(SearchResultEntry systemInformation)
  {
    this.systemInformation = systemInformation;
  }

  /**
   * Sets the monitoring entry of the work queue.
   * @param workQueue entry of the work queue.
   */
  public void setWorkQueueMonitor(SearchResultEntry workQueue)
  {
    this.workQueue = workQueue;
  }

  /**
   * Returns the monitoring entry for the entry caches.
   * @return the monitoring entry for the entry caches.
   */
  public SearchResultEntry getEntryCachesMonitor()
  {
    return entryCaches;
  }

  /**
   * Returns the monitoring entry for the JVM memory usage.
   * @return the monitoring entry for the JVM memory usage.
   */
  public SearchResultEntry getJvmMemoryUsageMonitor()
  {
    return jvmMemoryUsage;
  }

  /**
   * Returns the root entry of the monitoring tree.
   * @return the root entry of the monitoring tree.
   */
  public SearchResultEntry getRootMonitor()
  {
    return rootMonitor;
  }

  /**
   * Returns the monitoring entry for the system information.
   * @return the monitoring entry for the system information.
   */
  public SearchResultEntry getSystemInformationMonitor()
  {
    return systemInformation;
  }

  /**
   * Returns the monitoring entry for the work queue.
   * @return the monitoring entry for the work queue.
   */
  public SearchResultEntry getWorkQueueMonitor()
  {
    return workQueue;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + "(hostName=" + hostName
        + ", openDJVersion=" + openDJVersion
        + ", status=" + status
        + ", isLocal=" + isLocal
        + ", backends=" + backends + ")";
  }
}
