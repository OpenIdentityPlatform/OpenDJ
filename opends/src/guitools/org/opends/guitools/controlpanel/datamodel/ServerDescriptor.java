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

package org.opends.guitools.controlpanel.datamodel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.guitools.controlpanel.util.Utilities;

import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;

/**
 * This is just a class used to provide a data model describing what the
 * StatusPanelDialog will show to the user.
 */
public class ServerDescriptor
{
  private ServerStatus status;
  private int openConnections;
  private Set<BackendDescriptor> backends = new HashSet<BackendDescriptor>();
  private Set<ConnectionHandlerDescriptor> listeners =
    new HashSet<ConnectionHandlerDescriptor>();
  private ConnectionHandlerDescriptor adminConnector;
  private Set<DN> administrativeUsers = new HashSet<DN>();
  private File installPath;
  private File instancePath;
  private String openDSVersion;
  private String javaVersion;
  private ArrayList<OpenDsException> exceptions =
    new ArrayList<OpenDsException>();
  private boolean isWindowsServiceEnabled;
  private boolean isSchemaEnabled;
  private Schema schema;

  private boolean isAuthenticated;

  private static String hostName = "locahost";
  static
  {
    try
    {
      hostName = java.net.InetAddress.getLocalHost().getHostName();
    }
    catch (Throwable t)
    {
    }
  };

  /**
   * Enumeration indicating the status of the server.
   *
   */
  public enum ServerStatus
  {
    /**
     * Server Started.
     */
    STARTED,
    /**
     * Server Stopped.
     */
    STOPPED,
    /**
     * Server Starting.
     */
    STARTING,
    /**
     * Server Stopping.
     */
    STOPPING,
    /**
     * Status Unknown.
     */
    UNKNOWN
  }

  /**
   * Default constructor.
   */
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
   * @return <CODE>true</CODE> if the schema is enabled and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isSchemaEnabled()
  {
    return isSchemaEnabled;
  }

  /**
   * Sets whether the schema is enabled or not.
   * @param isSchemaEnabled <CODE>true</CODE> if the schema is enabled and
   * <CODE>false</CODE> otherwise.
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
  public File getInstancePath()
  {
    return instancePath;
  }

  /**
   * Sets the instance path where the server databases and configuration is
   * located.
   * @param instancePath the instance path where the server databases and
   * configuration is located.
   */
  public void setInstancePath(File instancePath)
  {
    this.instancePath = instancePath;
  }

  /**
   * Return the install path where the server is installed.
   * @return the install path where the server is installed.
   */
  public File getInstallPath()
  {
    return installPath;
  }

  /**
   * Sets the install path where the server is installed.
   * @param installPath the install path where the server is installed.
   */
  public void setInstallPath(File installPath)
  {
    this.installPath = installPath;
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
    return openDSVersion;
  }

  /**
   * Sets the version of the server.
   * @param openDSVersion the version of the server.
   */
  public void setOpenDSVersion(String openDSVersion)
  {
    this.openDSVersion = openDSVersion;
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
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals = false;
    if (this != o)
    {
      if (o instanceof ServerDescriptor)
      {
        ServerDescriptor desc = (ServerDescriptor)o;
        equals = desc.getStatus() == getStatus();

        if (equals)
        {
          equals = desc.isAuthenticated() == isAuthenticated();
        }

        if (equals)
        {
          equals = desc.getOpenConnections() == getOpenConnections();
        }

        if (equals)
        {
          equals = desc.getInstallPath().equals(getInstallPath());
        }

        if (equals)
        {
          equals = desc.getInstancePath().equals(getInstancePath());
        }

        if (equals)
        {
          if (desc.getJavaVersion() == null)
          {
            equals = getJavaVersion() == null;
          }
          else
          {
            equals = desc.getJavaVersion().equals(getJavaVersion());
          }
        }

        if (equals)
        {
          equals = desc.getOpenDSVersion().equals(getOpenDSVersion());
        }

        if (equals)
        {
          equals = desc.getAdministrativeUsers().equals(
              getAdministrativeUsers());
        }

        if (equals)
        {
          equals = desc.getConnectionHandlers().equals(getConnectionHandlers());
        }

        if (equals)
        {
          equals = desc.getBackends().equals(getBackends());
        }

        if (equals)
        {
          equals = desc.getExceptions().equals(getExceptions());
        }

        if (equals)
        {
          equals = desc.isSchemaEnabled() == isSchemaEnabled();
        }

        if (equals)
        {
          if (desc.getSchema() == null)
          {
            equals = getSchema() != null;
          }
          else if (getSchema() == null)
          {
            equals = false;
          }
          else
          {
            equals = areSchemasEqual(schema, desc.getSchema());
          }
        }

        if (equals && Utilities.isWindows())
        {
          equals =
            desc.isWindowsServiceEnabled() == isWindowsServiceEnabled();
        }
      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return status.hashCode() + openConnections +
    (String.valueOf(
        installPath+openDSVersion+javaVersion+isAuthenticated)).
        hashCode();
  }

  /**
   * Return whether we were authenticated when retrieving the information of
   * this ServerStatusDescriptor.
   * @return <CODE>true</CODE> if we were authenticated when retrieving the
   * information of this ServerStatusDescriptor and <CODE>false</CODE>
   * otherwise.
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
   * Returns the exceptions that occurred while reading the configuration.
   * @return the exceptions that occurred while reading the configuration.
   */
  public List<OpenDsException> getExceptions()
  {
    return Collections.unmodifiableList(exceptions);
  }

  /**
   * Sets the exceptions that occurred while reading the configuration.
   * @param exceptions exceptions that occurred while reading the
   * configuration.
   */
  public void setExceptions(Collection<OpenDsException> exceptions)
  {
    this.exceptions.clear();
    this.exceptions.addAll(exceptions);
  }

  /**
   * Tells whether the windows service is enabled or not.
   * @return <CODE>true</CODE> if the windows service is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isWindowsServiceEnabled()
  {
    return isWindowsServiceEnabled;
  }

  /**
   * Sets whether the windows service is enabled or not.
   * @param isWindowsServiceEnabled <CODE>true</CODE> if the windows service is
   * enabled and <CODE>false</CODE> otherwise.
   */
  public void setWindowsServiceEnabled(boolean isWindowsServiceEnabled)
  {
    this.isWindowsServiceEnabled = isWindowsServiceEnabled;
  }

  /**
   * Method used to compare schemas.
   * Returns <CODE>true</CODE> if the two schemas are equal and
   * <CODE>false</CODE> otherwise.
   * @param schema1 the first schema.
   * @param schema2 the second schema.
   * @return <CODE>true</CODE> if the two schemas are equal and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean areSchemasEqual(Schema schema1, Schema schema2)
  {
    boolean areEqual = schema1 == schema2;

    if (!areEqual && (schema1 != null) && (schema2 != null))
    {
      areEqual = true;

      // Just compare exhaustively objectclasses and attributes.
      Map<String, AttributeType> attrs1 = schema1.getAttributeTypes();
      Map<String, AttributeType> attrs2 = schema2.getAttributeTypes();
      areEqual = attrs1.size() == attrs2.size();
      for (String name : attrs1.keySet())
      {
        if (!areEqual)
        {
          break;
        }

        AttributeType attr1 = attrs1.get(name);
        AttributeType attr2 = attrs2.get(name);
        if (attr2 != null)
        {
          areEqual = areAttributesEqual(attr1, attr2);
        }
        else
        {
          areEqual = false;
        }
      }
      if (areEqual)
      {
        Map<String, ObjectClass> ocs1 = schema1.getObjectClasses();
        Map<String, ObjectClass> ocs2 = schema2.getObjectClasses();
        areEqual = ocs1.size() == ocs2.size();
        for (String name : ocs1.keySet())
        {
          if (!areEqual)
          {
            break;
          }

          ObjectClass oc1 = ocs1.get(name);
          ObjectClass oc2 = ocs2.get(name);
          if (oc2 != null)
          {
            areEqual = areObjectClassesEqual(oc1, oc2);
          }
          else
          {
            areEqual = false;
          }
        }
      }
      if (areEqual)
      {
        areEqual = schema1.getMatchingRules().equals(
            schema2.getMatchingRules());
      }
      if (areEqual)
      {
        areEqual = schema1.getSyntaxes().equals(schema2.getSyntaxes());
      }
    }
    return areEqual;
  }

  /**
   * Method used to compare attributes defined in the schema.
   * Returns <CODE>true</CODE> if the two schema attributes are equal and
   * <CODE>false</CODE> otherwise.
   * @param schema1 the first schema attribute.
   * @param schema2 the second schema attribute.
   * @return <CODE>true</CODE> if the two schema attributes are equal and
   * <CODE>false</CODE> otherwise.
   */
  private static final boolean areAttributesEqual(AttributeType attr1,
      AttributeType attr2)
  {
    boolean areEqual = attr1.getOID().equals(attr2.getOID()) &&
    attr1.isCollective() == attr2.isCollective() &&
    attr1.isNoUserModification() == attr2.isNoUserModification() &&
    attr1.isObjectClassType() == attr2.isObjectClassType() &&
    attr1.isObsolete() == attr2.isObsolete() &&
    attr1.isOperational() == attr2.isOperational() &&
    attr1.isSingleValue() == attr2.isSingleValue();

    if (areEqual)
    {
      Object[] compareWithEqual = {attr1.getApproximateMatchingRule(),
          attr2.getApproximateMatchingRule(),
          attr1.getDefinitionWithFileName(), attr2.getDefinitionWithFileName(),
          attr1.getDescription(), attr2.getDescription(),
          attr1.getEqualityMatchingRule(), attr2.getEqualityMatchingRule(),
          attr1.getOrderingMatchingRule(), attr2.getOrderingMatchingRule(),
          attr1.getSubstringMatchingRule(), attr2.getSubstringMatchingRule(),
          attr1.getSuperiorType(), attr2.getSuperiorType(),
          attr1.getSyntax(), attr2.getSyntax(),
          attr1.getSyntaxOID(), attr2.getSyntaxOID()
      };

      for (int i=0; i<compareWithEqual.length && areEqual; i++)
      {
        areEqual = areEqual(compareWithEqual[i], compareWithEqual[i+1]);
        i ++;
      }


      if (areEqual)
      {
        Iterable[] iterables = {attr1.getExtraPropertyNames(),
            attr2.getExtraPropertyNames(),
            attr1.getNormalizedNames(), attr2.getNormalizedNames(),
            attr1.getUserDefinedNames(), attr2.getUserDefinedNames()};
        for (int i=0; i<iterables.length && areEqual; i++)
        {
          Set<Object> set1 = new HashSet<Object>();
          Set<Object> set2 = new HashSet<Object>();
          for (Object o : iterables[i])
          {
            set1.add(o);
          }
          for (Object o : iterables[i+1])
          {
            set2.add(o);
          }
          areEqual = set1.equals(set2);
          i ++;
        }
      }
    }

    return areEqual;
  }

  /**
   * Method used to compare objectclasses defined in the schema.
   * Returns <CODE>true</CODE> if the two schema objectclasses are equal and
   * <CODE>false</CODE> otherwise.
   * @param schema1 the first schema objectclass.
   * @param schema2 the second schema objectclass.
   * @return <CODE>true</CODE> if the two schema objectclasses are equal and
   * <CODE>false</CODE> otherwise.
   */
  private static final boolean areObjectClassesEqual(ObjectClass oc1,
      ObjectClass oc2)
  {
    boolean areEqual = oc1.getOID().equals(oc2.getOID()) &&
    oc1.isExtensibleObject() == oc2.isExtensibleObject();
    if (areEqual)
    {
      Object[] compareWithEqual = {
          oc1.getDefinitionWithFileName(), oc2.getDefinitionWithFileName(),
          oc1.getDescription(), oc2.getDescription(),
          oc1.getObjectClassType(), oc2.getObjectClassType(),
          oc1.getOptionalAttributes(), oc2.getOptionalAttributes(),
          oc1.getRequiredAttributes(), oc2.getRequiredAttributes(),
          oc1.getSuperiorClass(), oc2.getSuperiorClass()
      };

      for (int i=0; i<compareWithEqual.length && areEqual; i++)
      {
        areEqual = areEqual(compareWithEqual[i], compareWithEqual[i+1]);
        i ++;
      }
    }

    if (areEqual)
    {
      Iterable[] iterables = {
          oc1.getExtraPropertyNames(), oc2.getExtraPropertyNames(),
          oc1.getNormalizedNames(), oc2.getNormalizedNames(),
          oc1.getUserDefinedNames(), oc2.getUserDefinedNames()};
      for (int i=0; i<iterables.length && areEqual; i++)
      {
        Set<Object> set1 = new HashSet<Object>();
        Set<Object> set2 = new HashSet<Object>();
        for (Object o : iterables[i])
        {
          set1.add(o);
        }
        for (Object o : iterables[i+1])
        {
          set2.add(o);
        }
        areEqual = set1.equals(set2);
        i ++;
      }
    }

    return areEqual;

  }

  /**
   * Commodity method used to compare two objects that might be
   * <CODE>null</CODE>.
   * @param o1 the first object.
   * @param o2 the second object.
   * @return if both objects are <CODE>null</CODE> returns true.  If not returns
   * <CODE>true</CODE> if both objects are equal according to the Object.equal
   * method and <CODE>false</CODE> otherwise.
   */
  private static boolean areEqual(Object o1, Object o2)
  {
    boolean areEqual = false;
    if (o1 != null)
    {
      if (o2 != null)
      {
        areEqual = o1.equals(o2);
      }
      else
      {
        areEqual = false;
      }
    }
    else
    {
      areEqual = o2 == null;
    }
    return areEqual;
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
}
