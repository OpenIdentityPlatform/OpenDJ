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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.admin.ads;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import javax.naming.InvalidNameException;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.NotContextException;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * Class used to update and read the contents of the Administration Data.
 */
public class ADSContext
{

  /**
   * Enumeration containing the different server properties that are stored in
   * the ADS.
   */
  public enum ServerProperty
  {
    /**
     * The ID used to identify the server.
     */
    ID("cn"),
    /**
     * The host name of the server.
     */
    HOSTNAME("hostname"),
    /**
     * The LDAP port of the server.
     */
    PORT("ldapport"),
    /**
     * The JMX port of the server.
     */
    JMX_PORT("jmxport"),
    /**
     * The LDAPS port of the server.
     */
    SECURE_PORT("ldapsport"),
    /**
     * The certificate used by the server.
     */
    CERTIFICATE("certificate"),
    /**
     * The path where the server is installed.
     */
    INSTANCE_PATH("instancepath"),
    /**
     * The description of the server.
     */
    DESCRIPTION("description"),
    /**
     * The OS of the machine where the server is installed.
     */
    HOST_OS("os"),
    /**
     * Whether LDAP is enabled or not.
     */
    LDAP_ENABLED("ldapEnabled"),
    /**
     * Whether LDAPS is enabled or not.
     */
    LDAPS_ENABLED("ldapsEnabled"),
    /**
     * Whether StartTLS is enabled or not.
     */
    STARTTLS_ENABLED("startTLSEnabled"),
    /**
     * Whether JMX is enabled or not.
     */
    JMX_ENABLED("jmxEnabled"),
    /**
     * The location of the server.
     */
    LOCATION("location"),
    /**
     * The groups to which this server belongs.
     */
    GROUPS("memberofgroups");

    private String attrName;
    /**
     * Private constructor.
     * @param n the name of the attribute.
     */
    private ServerProperty(String n)
    {
      attrName = n;
    }

    /**
     * Returns the attribute name.
     * @return the attribute name.
     */
    public String getAttributeName()
    {
      return attrName;
    }
  };

  /**
   * The list of server properties that are multivalued.
   */
  private final static Set<ServerProperty> MULTIVALUED_SERVER_PROPERTIES =
    new HashSet<ServerProperty>();
  static
  {
    MULTIVALUED_SERVER_PROPERTIES.add(ServerProperty.GROUPS);
  }

  /**
   * Enumeration containing the different server group properties that are
   * stored in the ADS.
   */
  public enum ServerGroupProperty
  {
    /**
     * The UID of the server group.
     */
    UID("cn"),
    /**
     * The description of the server group.
     */
    DESCRIPTION("description"),
    /**
     * The members of the server group.
     */
    MEMBERS("members");

    private String attrName;

    /**
     * Private constructor.
     * @param n the attribute name.
     */
    private ServerGroupProperty(String n)
    {
      attrName = n;
    }

    /**
     * Returns the attribute name.
     * @return the attribute name.
     */
    public String getAttributeName()
    {
      return attrName;
    }
  };

  /**
   * The list of server group properties that are multivalued.
   */
  private final static
  Set<ServerGroupProperty> MULTIVALUED_SERVER_GROUP_PROPERTIES =
    new HashSet<ServerGroupProperty>();
  static
  {
    MULTIVALUED_SERVER_GROUP_PROPERTIES.add(ServerGroupProperty.MEMBERS);
  }

  /**
   * The enumeration containing the different Administrator properties.
   */
  public enum AdministratorProperty
  {
    /**
     * The UID of the administrator.
     */
    UID,
    /**
     * The password of the administrator.
     */
    PASSWORD,
    /**
     * The description of the administrator.
     */
    DESCRIPTION,
    /**
     * The DN of the administrator.
     */
    ADMINISTRATOR_DN
  };

  /**
   * Character used to separate hostname from ipath in RDN.
   */
  public final static String HNP_SEPARATOR = "@";

  // The context used to retrieve information
  InitialLdapContext dirContext;


  /**
   * Constructor of the ADSContext.
   * @param dirContext the DirContext that must be used to retrieve information.
   */
  public ADSContext(InitialLdapContext dirContext)
  {
    this.dirContext = dirContext;
  }


  /**
   * Returns the DirContext used to retrieve information by this ADSContext.
   * @return the DirContext used to retrieve information by this ADSContext.
   */
  public InitialLdapContext getDirContext()
  {
    return dirContext;
  }

  /**
   * Method called to register a server in the ADS.
   * @param serverProperties the properties of the server.
   * @throws ADSContextException if the server could not be registered.
   */
  public void registerServer(Map<ServerProperty, Object> serverProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromServerProperties(serverProperties);
    BasicAttributes attrs = makeAttrsFromServerProperties(serverProperties);
    try
    {
      DirContext ctx = dirContext.createSubcontext(dn, attrs);
      ctx.close();
    }
    catch (NameAlreadyBoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ALREADY_REGISTERED);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }


  /**
   * Method called to udpate the properties of a server in the ADS.
   * @param serverProperties the new properties of the server.
   * @throws ADSContextException if the server could not be registered.
   */
  public void updateServer(Map<ServerProperty, Object> serverProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromServerProperties(serverProperties);
    BasicAttributes attrs = makeAttrsFromServerProperties(serverProperties);
    try
    {
      dirContext.modifyAttributes(dn, InitialLdapContext.REPLACE_ATTRIBUTE,
          attrs);
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.NOT_YET_REGISTERED);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Method called to unregister a server in the ADS.
   * @param serverProperties the properties of the server.
   * @throws ADSContextException if the server could not be unregistered.
   */
  public void unregisterServer(Map<ServerProperty, Object> serverProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromServerProperties(serverProperties);
    try
    {
      dirContext.destroySubcontext(dn);
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.NOT_YET_REGISTERED);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns whether a given server is already registered or not.
   * @param serverProperties the server properties.
   * @return <CODE>true</CODE> if the server was registered and
   * <CODE>false</CODE> otherwise.
   * @throws ADSContextException if something went wrong.
   */
  public boolean isServerAlreadyRegistered(
      Map<ServerProperty, Object> serverProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromServerProperties(serverProperties);
    return isExistingEntry(dn);
  }

  /**
   * Returns whether a given administrator is already registered or not.
   * @param adminProperties the administrator properties.
   * @return <CODE>true</CODE> if the administrator was registered and
   * <CODE>false</CODE> otherwise.
   * @throws ADSContextException if something went wrong.
   */
  public boolean isAdministratorAlreadyRegistered(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromAdministratorProperties(adminProperties);
    return isExistingEntry(dn);
  }

  /**
   * A convenience method that takes some server properties as parameter and
   * if there is no server registered associated with those properties,
   * registers it and if it is already registered, updates it.
   * @param serverProperties the server properties.
   * @throws ADSContextException if something goes wrong.
   */
  public void registerOrUpdateServer(
      Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    try
    {
      registerServer(serverProperties);
    }
    catch(ADSContextException x)
    {
      if (x.getError() == ADSContextException.ErrorType.ALREADY_REGISTERED)
      {
        updateServer(serverProperties);
      }
      else
      {
        throw x;
      }
    }
  }

  /**
   * Returns the properties of a server for a given host name and installation
   * path.
   * @param hostname the host Name.
   * @param ipath the installation path.
   * @return the properties of a server for a given host name and installation
   * path.
   * @throws ADSContextException if something goes wrong.
   */
  public Map<ServerProperty, Object> lookupServerRegistry(String hostname,
      String ipath) throws ADSContextException
  {
    LdapName dn = makeDNFromHostnameAndPath(hostname, ipath);
    Map<ServerProperty, Object> result;
    try
    {
      result = makePropertiesFromServerAttrs(hostname, ipath,
          dirContext.getAttributes(dn));
    }
    catch (NameNotFoundException x)
    {
      result = null;
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }

    return result;
  }


  /**
   * Returns a set containing the servers that are registered in the ADS.
   * @return a set containing the servers that are registered in the ADS.
   * @throws ADSContextException if something goes wrong.
   */
  public Set<Map<ServerProperty,Object>> readServerRegistry()
  throws ADSContextException
  {
    Set<Map<ServerProperty,Object>> result =
      new HashSet<Map<ServerProperty,Object>>();
    try
    {
      NamingEnumeration ne;
      SearchControls sc = new SearchControls();

      sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      ne = dirContext.search(getServerContainerDN(), "(objectclass=*)", sc);
      while (ne.hasMore())
      {
        SearchResult sr = (SearchResult)ne.next();
        Map<ServerProperty,Object> properties =
          makePropertiesFromServerAttrs(sr.getName(), sr.getAttributes());
        result.add(properties);
      }
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.BROKEN_INSTALL);
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }

    return result;
  }

  /**
   * Returns a set of the server properties that are registered in the ADS and
   * that contain the properties specified in serverProperties.
   * @param serverProperties the properties that are used as search criteria.
   * @return a set of the server properties that are registered in the ADS and
   * that contain the properties specified in serverProperties.
   * @throws ADSContextException if something goes wrong.
   */
  public Set<Map<ServerProperty, Object>> searchServerRegistry(
      Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    Set<Map<ServerProperty, Object>> result =
      new HashSet<Map<ServerProperty, Object>>();
    StringBuffer filter = new StringBuffer();

    // Build the filter according the properties passed in serverProperties
    int operandCount = 0;
    if (serverProperties.containsKey(ServerProperty.HOSTNAME))
    {
      filter.append("(cn=");
      filter.append(serverProperties.get(ServerProperty.HOSTNAME));
      filter.append("*");
      if (serverProperties.containsKey(ServerProperty.INSTANCE_PATH))
      {
        filter.append(HNP_SEPARATOR);
        filter.append(serverProperties.get(ServerProperty.INSTANCE_PATH));
      }
      filter.append(")");
      operandCount++;
    }
    if (serverProperties.containsKey(ServerProperty.PORT))
    {
      filter.append("(");
      filter.append(ServerProperty.PORT);
      filter.append("=");
      filter.append(serverProperties.get(ServerProperty.PORT));
      filter.append(")");
      operandCount++;
    }
    if (operandCount >= 2)
    {
      filter.insert(0, '(');
      filter.append("&)");
    }

    // Search the ADS
    try
    {
      NamingEnumeration ne;
      SearchControls sc = new SearchControls();

      sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      ne = dirContext.search(getServerContainerDN(), filter.toString(), sc);
      while (ne.hasMore())
      {
        SearchResult sr = (SearchResult)ne.next();
        Map<ServerProperty, Object> properties = makePropertiesFromServerAttrs(
            sr.getName(), sr.getAttributes());
        result.add(properties);
      }
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.BROKEN_INSTALL);
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }

    return result;
  }

  /**
   * Creates a Server Group in the ADS.
   * @param serverGroupProperties the properties of the server group to be
   * created.
   * @throws ADSContextException if somethings goes wrong.
   */
  public void createServerGroup(
      Map<ServerGroupProperty, Object> serverGroupProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromServerGroupProperties(serverGroupProperties);
    BasicAttributes attrs = makeAttrsFromServerGroupProperties(
        serverGroupProperties);
    try
    {
      DirContext ctx = dirContext.createSubcontext(dn, attrs);
      ctx.close();
    }
    catch (NameAlreadyBoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ALREADY_REGISTERED);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Updates the properties of a Server Group in the ADS.
   * @param serverGroupProperties the new properties of the server group to be
   * updated.
   * @throws ADSContextException if somethings goes wrong.
   */
  public void updateServerGroup(
      Map<ServerGroupProperty, Object> serverGroupProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromServerGroupProperties(serverGroupProperties);
    BasicAttributes attrs =
      makeAttrsFromServerGroupProperties(serverGroupProperties);
    try
    {
      dirContext.modifyAttributes(dn, DirContext.REPLACE_ATTRIBUTE, attrs);
    }
    catch (NameAlreadyBoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ALREADY_REGISTERED);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Deletes a Server Group in the ADS.
   * @param serverGroupProperties the properties of the server group to be
   * deleted.
   * @throws ADSContextException if somethings goes wrong.
   */
  public void deleteServerGroup(
      Map<ServerGroupProperty, Object> serverGroupProperties)
  throws ADSContextException
  {
    LdapName dn = makeDNFromServerGroupProperties(serverGroupProperties);
    try
    {
      dirContext.destroySubcontext(dn);
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns a set containing the server groups that are defined in the ADS.
   * @return a set containing the server groups that are defined in the ADS.
   * @throws ADSContextException if something goes wrong.
   */
  public Set<Map<ServerGroupProperty, Object>> readServerGroupRegistry()
  throws ADSContextException
  {
    Set<Map<ServerGroupProperty, Object>> result =
      new HashSet<Map<ServerGroupProperty, Object>>();
    try
    {
      NamingEnumeration ne;
      SearchControls sc = new SearchControls();

      sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      ne = dirContext.search(getServerGroupContainerDN(), "(objectclass=*)",
          sc);
      while (ne.hasMore())
      {
        SearchResult sr = (SearchResult)ne.next();
        Map<ServerGroupProperty, Object> properties =
          makePropertiesFromServerGroupAttrs(sr.getAttributes());
        result.add(properties);
      }
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.BROKEN_INSTALL);
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
    return result;
  }


  /**
   * Returns a set containing the administrators that are defined in the ADS.
   * @return a set containing the administrators that are defined in the ADS.
   * @throws ADSContextException if something goes wrong.
   */
  public Set<Map<AdministratorProperty, Object>> readAdministratorRegistry()
  throws ADSContextException
  {
    Set<Map<AdministratorProperty, Object>> result =
      new HashSet<Map<AdministratorProperty, Object>>();
    try {
      NamingEnumeration ne;
      SearchControls sc = new SearchControls();

      sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      ne = dirContext.search(getAdministratorContainerDN(), "(objectclass=*)",
          sc);
      while (ne.hasMore())
      {
        SearchResult sr = (SearchResult)ne.next();

        Map<AdministratorProperty, Object> properties =
          makePropertiesFromAdministratorAttrs(
              sr.getName(), sr.getAttributes());

        result.add(properties);
      }
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.BROKEN_INSTALL);
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }

    return result;
  }

  /**
   * Creates the Administration Data in the server.
   * NOTE: until we use the Administration Framework this does not work.
   * TODO: remove the installPath parameter once we are integrated in the
   * Administration Framework.
   * The call to this method assumes that OpenDS.jar has already been loaded.
   * So this should not be called by the Java Web Start before being sure that
   * this jar is loaded.
   * @throws ADSContextException if something goes wrong.
   */
  public void createAdminData() throws ADSContextException
  {
    // Add the administration suffix
    createAdministrationSuffix();

    // Create the DIT below the administration suffix
    createTopContainerEntry();
    createAdministratorContainerEntry();
    createContainerEntry(getServerContainerDN());
    createContainerEntry(getServerGroupContainerDN());
  }

  /**
   * NOTE: this can only be called locally.
   * The call to this method assumes that OpenDS.jar has already been loaded.
   * So this should not be called by the Java Web Start before being sure that
   * this jar is loaded.
   * @param serverProperties the properties of the server to register.
   * @param installPath the installation path of the server.
   * @param backendName the backend where we create the administration data.
   * @throws ADSContextException if something goes wrong.
   */
  public static void createOfflineAdminData(
      Map<ServerProperty, Object> serverProperties, String installPath,
      String backendName)
  throws ADSContextException
  {
    // Add the administration suffix
    createOfflineAdministrationSuffix(installPath);

    // Create the DIT below the administration suffix
    try
    {
      File ldifFile = File.createTempFile("ads", ".ldif");
      ldifFile.deleteOnExit();

      LinkedList<String> lines = new LinkedList<String>();

      lines.add("dn: "+getAdministrationSuffixDN());
      lines.add("objectclass: extensibleobject");
      lines.add("aci: "+getTopContainerACI());
      lines.add("");

      lines.add("dn: "+getAdministratorContainerDN());
      lines.add("objectclass: groupOfUniqueNames");
      lines.add("objectclass: groupofurls");
      lines.add("memberURL: ldap:///" + getAdministratorContainerDN() +
      "??one?(objectclass=*)");
      lines.add("description: Group of identities which have full access.");

      lines.add("dn: "+getServerContainerDN());
      lines.add("objectclass: extensibleobject");
      lines.add("");

      lines.add("dn: "+getServerGroupContainerDN());
      lines.add("objectclass: extensibleobject");
      lines.add("");

      LdapName dn = makeDNFromServerProperties(serverProperties);
      BasicAttributes attrs = makeAttrsFromServerProperties(serverProperties);
      lines.add("dn: "+dn.toString());
      NamingEnumeration<String> ids = attrs.getIDs();
      while (ids.hasMoreElements())
      {
        String attrID = ids.nextElement();
        Attribute attr = attrs.get(attrID);
        try
        {
          NamingEnumeration values = attr.getAll();
          while (values.hasMoreElements())
          {
            lines.add(attrID+": "+values.nextElement());
          }
        }
        catch (NamingException ne)
        {
          // This should not happen
          throw new ADSContextException(
              ADSContextException.ErrorType.ERROR_UNEXPECTED, ne);
        }
      }

      BufferedWriter writer = new BufferedWriter(new FileWriter(ldifFile));
      for (String line : lines)
      {
        writer.write(line);
        writer.newLine();
      }

      writer.flush();
      writer.close();

      ArrayList<String> argList = new ArrayList<String>();
      argList.add("-C");
      argList.add(
          org.opends.server.extensions.ConfigFileHandler.class.getName());

      argList.add("-c");
      argList.add(installPath+File.separator+"config"+File.separator+
          "config.ldif");

      argList.add("-n");
      argList.add(backendName);
      argList.add("-t");
      argList.add(ldifFile.getAbsolutePath());
      argList.add("-S");
      argList.add("0");

      String[] args = new String[argList.size()];
      argList.toArray(args);

      try
      {

        int result = org.opends.server.tools.ImportLDIF.mainImportLDIF(args);

        if (result != 0)
        {
          throw new ADSContextException(
              ADSContextException.ErrorType.ERROR_UNEXPECTED);
        }
      } catch (Throwable t)
      {
//      This should not happen
        throw new ADSContextException(
            ADSContextException.ErrorType.ERROR_UNEXPECTED, t);
      }
    }
    catch (IOException ioe)
    {
//    This should not happen
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, ioe);
    }
  }

  /**
   * Removes the administration data.
   * @throws ADSContextException if something goes wrong.
   */
  public void removeAdminData() throws ADSContextException
  {
    removeAdministrationSuffix();
  }


  /**
   * Returns <CODE>true</CODE> if the server contains Administration Data and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the server contains Administration Data and
   * <CODE>false</CODE> otherwise.
   * @throws ADSContextException if something goes wrong.
   */
  public boolean hasAdminData() throws ADSContextException
  {
    return isExistingEntry(nameFromDN(getAdministrationSuffixDN()));
  }

  /**
   * Returns the DN of the administrator for a given UID.
   * @param uid the UID to be used to generate the DN.
   * @return the DN of the administrator for the given UID:
   */
  public static String getAdministratorDN(String uid)
  {
    return "cn=" + Rdn.escapeValue(uid) + "," + getAdministratorContainerDN();
  }

  /**
   * Creates an Administrator in the ADS.
   * @param adminProperties the properties of the administrator to be created.
   * @throws ADSContextException if something goes wrong.
   */
  public void createAdministrator(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException {
    LdapName dnCentralAdmin =
      makeDNFromAdministratorProperties(adminProperties);
    BasicAttributes attrs = makeAttrsFromAdministratorProperties(
        adminProperties);

    try
    {
      DirContext ctx = dirContext.createSubcontext(dnCentralAdmin, attrs);
      ctx.close();
    }
    catch (NameAlreadyBoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ALREADY_REGISTERED);
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Deletes the administrator in the ADS.
   * @param adminProperties the properties of the administrator to be deleted.
   * @throws ADSContextException if something goes wrong.
   */
  public void deleteAdministrator(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException {

    LdapName dnCentralAdmin =
      makeDNFromAdministratorProperties(adminProperties);

    try
    {
      dirContext.destroySubcontext(dnCentralAdmin);
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.NOT_YET_REGISTERED);
    }
    catch (NotContextException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.NOT_YET_REGISTERED);
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Updates and administrator registered in the ADS.
   * @param adminProperties the new properties of the administrator.
   * @throws ADSContextException if something goes wrong.
   */
  public void updateAdministrator(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException
  {

    LdapName dnCentralAdmin =
      makeDNFromAdministratorProperties(adminProperties);
    BasicAttributes attrs = makeAttrsFromAdministratorProperties(
        adminProperties);

    try
    {
      dirContext.modifyAttributes(dnCentralAdmin, DirContext.REPLACE_ATTRIBUTE,
          attrs);
    }
    catch (NameNotFoundException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.NOT_YET_REGISTERED);
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch (NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns the DN of the suffix that contains the administration data.
   * @return the DN of the suffix that contains the administration data.
   */
  public static String getAdministrationSuffixDN()
  {
    return "cn=admin data";
  }

  /**
   * Used to modify the configuration on the server that must be managed; this
   * setups the ACIs on the server so that the Administrator can access the
   * server configuration.
   * TODO: complete this.
   * @param dirCtx the DirContext to the server that must be updated.
   * @param enable whether to enable or disable the access to the server.
   * @return <CODE>true</CODE> if something modified and <CODE>false</CODE>
   * otherwise.
   * @throws ADSContextException if the ACIs could not be set up.
   */
  public static boolean setupACIOnServer(LdapContext dirCtx,
      boolean enable) throws ADSContextException
  {
    boolean result;
    Attributes currentAttrs;
    Attribute currentAttr, newAttr;
    ModificationItem modItem;

    try
    {
      // Get the ACI value on the root entry
      currentAttrs = dirCtx.getAttributes("", new String[] { "aci" });
      currentAttr = currentAttrs.get("aci");

      // Check what ACIs values must be added or removed
      newAttr = new BasicAttribute("aci");
      modItem = null;
      if (enable)
      {
        if ((currentAttr == null) || !currentAttr.contains(getAdminACI1()))
        {
          newAttr.add(getAdminACI1());
        }
        if ((currentAttr == null) || !currentAttr.contains(getAdminACI2()))
        {
          newAttr.add(getAdminACI2());
        }
        if (newAttr.size() >= 1)
        {
          modItem = new ModificationItem(LdapContext.ADD_ATTRIBUTE, newAttr);
        }
      }
      else
      {
        if ((currentAttr != null) && currentAttr.contains(getAdminACI1()))
        {
          newAttr.add(getAdminACI1());
        }
        if ((currentAttr != null) && currentAttr.contains(getAdminACI2()))
        {
          newAttr.add(getAdminACI2());
        }
        if (newAttr.size() >= 1)
        {
          modItem = new ModificationItem(LdapContext.REMOVE_ATTRIBUTE, newAttr);
        }
      }

      // Update the ACI values on the root entry
      if (modItem != null)
      {
        dirCtx.modifyAttributes("", new ModificationItem[] { modItem});
        result = true;
      }
      else
      {
        result = false;
      }
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
    return result;
  }

  /**
   * This method returns the DN of the entry that corresponds to the given host
   * name and installation path.
   * @param hostname the host name.
   * @param ipath the installation path.
   * @return the DN of the entry that corresponds to the given host name and
   * installation path.
   * @throws ADSContextException if something goes wrong.
   */
  private static LdapName makeDNFromHostnameAndPath(String hostname,
      String ipath) throws ADSContextException
  {
    String cnValue = Rdn.escapeValue(hostname + HNP_SEPARATOR + ipath);
    return nameFromDN("cn=" + cnValue + "," + getServerContainerDN());
  }


  /**
   * This method returns the DN of the entry that corresponds to the given
   * server group properties.
   * @param serverGroupProperties the server group properties
   * @return the DN of the entry that corresponds to the given server group
   * properties.
   * @throws ADSContextException if something goes wrong.
   */
  private LdapName makeDNFromServerGroupProperties(
      Map<ServerGroupProperty, Object> serverGroupProperties)
  throws ADSContextException
  {
    String serverGroupId = (String)serverGroupProperties.get(
        ServerGroupProperty.UID);
    if (serverGroupId == null)
    {
      throw new ADSContextException(ADSContextException.ErrorType.MISSING_NAME);
    }
    return nameFromDN("cn=" + Rdn.escapeValue(serverGroupId) + "," +
          getServerGroupContainerDN());
  }

  /**
   * This method returns the DN of the entry that corresponds to the given
   * server properties.
   * @param serverProperties the server properties.
   * @return the DN of the entry that corresponds to the given server
   * properties.
   * @throws ADSContextException if something goes wrong.
   */
  private static LdapName makeDNFromServerProperties(
      Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    String hostname = getHostname(serverProperties);
    String ipath = getInstallPath(serverProperties);
    return makeDNFromHostnameAndPath(hostname, ipath);
  }

  /**
   * This method returns the DN of the entry that corresponds to the given
   * administrator properties.
   * @param adminProperties the administrator properties.
   * @return the DN of the entry that corresponds to the given administrator
   * properties.
   * @throws ADSContextException if something goes wrong.
   */
  private LdapName makeDNFromAdministratorProperties(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException
  {
    String adminUid = getAdministratorUID(adminProperties);

    String dnCentralAdmin = getAdministratorDN(adminUid);

    return nameFromDN(dnCentralAdmin);
  }

  /**
   * Returns the attributes for some administrator properties.
   * @param adminProperties the administrator properties.
   * @return the attributes for the given administrator properties.
   * @throws ADSContextException if something goes wrong.
   */
  private BasicAttributes makeAttrsFromAdministratorProperties(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException
  {
    BasicAttributes attrs = new BasicAttributes();
    String adminPassword = getAdministratorPassword(adminProperties);
    attrs.put("objectclass", "person");
    attrs.put("sn", "admin");
    attrs.put("userPassword", adminPassword);
    return attrs;
  }

  /**
   * Returns the attributes for some server properties.
   * @param serverProperties the server properties.
   * @return the attributes for the given server properties.
   * @throws ADSContextException if something goes wrong.
   */
  private static BasicAttributes makeAttrsFromServerProperties(
      Map<ServerProperty, Object> serverProperties)
  {
    BasicAttributes result = new BasicAttributes();

    // Transform 'properties' into 'attributes'
    for (ServerProperty prop: serverProperties.keySet())
    {
      Attribute attr = makeAttrFromServerProperty(prop,
          serverProperties.get(prop));
      if (attr != null)
      {
        result.put(attr);
      }
    }
    // Add the objectclass attribute value
    result.put("objectclass", "extensibleobject");
    return result;
  }

  /**
   * Returns the attribute for a given server property.
   * @param property the server property.
   * @return the attribute for a given server property.
   * @throws ADSContextException if something goes wrong.
   */
  private static Attribute makeAttrFromServerProperty(ServerProperty property,
      Object value)
  {
    Attribute result;

    switch(property)
    {
    case HOSTNAME:
      result = null;
      break;
    case INSTANCE_PATH:
      result = null;
      break;
    case GROUPS:
      result = new BasicAttribute(ServerProperty.GROUPS.getAttributeName());
      Iterator groupIterator = ((Set)value).iterator();
      while (groupIterator.hasNext())
      {
        result.add(groupIterator.next());
      }
      break;
    default:
      result = new BasicAttribute(property.getAttributeName(), value);
    }
    return result;
  }

  /**
   * Returns the attributes for some server group properties.
   * @param serverProperties the server group properties.
   * @return the attributes for the given server group properties.
   * @throws ADSContextException if something goes wrong.
   */
  private BasicAttributes makeAttrsFromServerGroupProperties(
      Map<ServerGroupProperty, Object> serverGroupProperties)
  {
    BasicAttributes result = new BasicAttributes();

    // Transform 'properties' into 'attributes'
    for (ServerGroupProperty prop: serverGroupProperties.keySet())
    {
      Attribute attr = makeAttrFromServerGroupProperty(prop,
          serverGroupProperties.get(prop));
      if (attr != null)
      {
        result.put(attr);
      }
    }
    // Add the objectclass attribute value
    result.put("objectclass", "extensibleobject");
    return result;
  }

  /**
   * Returns the attribute for a given server group property.
   * @param property the server group property.
   * @return the attribute for a given server group property.
   * @throws ADSContextException if something goes wrong.
   */
  private Attribute makeAttrFromServerGroupProperty(
      ServerGroupProperty property, Object value)
  {
    Attribute result;

    switch(property)
    {
    case MEMBERS:
      result = new BasicAttribute(
          ServerGroupProperty.MEMBERS.getAttributeName());
      Iterator memberIterator = ((Set)value).iterator();
      while (memberIterator.hasNext())
      {
        result.add(memberIterator.next());
      }
      break;
    default:
      result = new BasicAttribute(property.getAttributeName(), value);
    }

    return result;
  }

  /**
   * Returns the properties of a server group for some LDAP attributes.
   * @param attrs the LDAP attributes.
   * @return the properties of a server group for some LDAP attributes.
   * @throws ADSContextException if something goes wrong.
   */
  private Map<ServerGroupProperty, Object> makePropertiesFromServerGroupAttrs(
      Attributes attrs) throws ADSContextException
  {
    HashMap<ServerGroupProperty, Object> result =
      new HashMap<ServerGroupProperty, Object>();
    try
    {
      NamingEnumeration ne = attrs.getAll();
      while (ne.hasMore())
      {
        Attribute attr = (Attribute)ne.next();
        String attrID = attr.getID();
        Object value = null;

        ServerGroupProperty prop = null;
        ServerGroupProperty[] props = ServerGroupProperty.values();
        for (int i=0; i<props.length && (prop == null); i++)
        {
          String v = props[i].getAttributeName();
          if (attrID.equalsIgnoreCase(v))
          {
            prop = props[i];
          }
        }
        if (prop == null)
        {
          throw new ADSContextException(
              ADSContextException.ErrorType.ERROR_UNEXPECTED);
        }

        if (attr.size() >= 1 &&
            MULTIVALUED_SERVER_GROUP_PROPERTIES.contains(prop))
        {

          Set<String> set = new HashSet<String>();
          NamingEnumeration ae = attr.getAll();
          while (ae.hasMore())
          {
            set.add((String)ae.next());
          }
          value = set;
        }
        else
        {
          value = attr.get(0);
        }

        result.put(prop, value);
      }
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
    return result;
  }

  /**
   * Returns the properties of a server for some LDAP attributes.
   * @param attrs the LDAP attributes.
   * @return the properties of a server for some LDAP attributes.
   * @throws ADSContextException if something goes wrong.
   */
  private Map<ServerProperty, Object> makePropertiesFromServerAttrs(
      Attributes attrs) throws ADSContextException
  {
    HashMap<ServerProperty, Object> result =
      new HashMap<ServerProperty, Object>();
    try
    {
      NamingEnumeration ne = attrs.getAll();
      while (ne.hasMore())
      {
        Attribute attr = (Attribute)ne.next();
        String attrID = attr.getID();
        Object value = null;

        if (attrID.endsWith(";binary"))
        {
          attrID = attrID.substring(0, attrID.lastIndexOf(";binary"));
        }

        ServerProperty prop = null;
        ServerProperty[] props = ServerProperty.values();
        for (int i=0; i<props.length && (prop == null); i++)
        {
          String v = props[i].getAttributeName();
          if (attrID.equalsIgnoreCase(v))
          {
            prop = props[i];
          }
        }
        if (prop == null)
        {
          throw new ADSContextException(
              ADSContextException.ErrorType.ERROR_UNEXPECTED);
        }

        if (attr.size() >= 1 && MULTIVALUED_SERVER_PROPERTIES.contains(prop))
        {
          Set<String> set = new HashSet<String>();
          NamingEnumeration ae = attr.getAll();
          while (ae.hasMore())
          {
            set.add((String)ae.next());
          }
          value = set;
        }
        else
        {
          value = attr.get(0);
        }

        result.put(prop, value);
      }
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
    return result;
  }

  /**
   * Returns the properties of a server group for an RDN and some LDAP
   * attributes.
   * @param rdnName the RDN.
   * @param attrs the LDAP attributes.
   * @return the properties of a server group for an RDN and some LDAP
   * attributes.
   * @throws ADSContextException if something goes wrong.
   */
  Map<ServerProperty, Object> makePropertiesFromServerAttrs(String rdnName,
      Attributes attrs) throws ADSContextException
  {
    String hostName, ipath;
    LdapName nameObj;

    nameObj = nameFromDN(rdnName);

    //
    // Extract the hostname and ipath from the dn
    //
    Rdn rdnObj = nameObj.getRdn(nameObj.size() - 1);
    String hostNamePath = (String)Rdn.unescapeValue((String)rdnObj.getValue());
    int sepIndex = hostNamePath.indexOf(HNP_SEPARATOR);
    if (sepIndex != -1)
    {
      hostName = hostNamePath.substring(0, sepIndex);
      ipath = hostNamePath.substring(sepIndex+1, hostNamePath.length());
    }
    else
    { // Emergency logic...
      hostName = hostNamePath;
      ipath = "undefined";
    }

    //
    // Delegate...
    //
    return makePropertiesFromServerAttrs(hostName, ipath, attrs);
  }

  /**
   * Returns the properties of a server for some host name, installation path
   * and LDAP attributes.
   * @param hostName the host name.
   * @param ipath the installation path.
   * @param attrs the LDAP attributes.
   * @return the properties of a server for the given host name, installation
   * path and LDAP attributes.
   * @throws ADSContextException if something goes wrong.
   */
  Map<ServerProperty, Object> makePropertiesFromServerAttrs(String hostName,
      String ipath, Attributes attrs) throws ADSContextException
  {
    Map<ServerProperty, Object> result = new HashMap<ServerProperty, Object>();

    //
    // Put hostname and ipath
    //
    result.put(ServerProperty.HOSTNAME, hostName);
    result.put(ServerProperty.INSTANCE_PATH, ipath);

    //
    // Get other properties from the attributes
    //
    result.putAll(makePropertiesFromServerAttrs(attrs));

    return result;
  }


  /**
   * Returns the properties of an administrator for some rdn and LDAP
   * attributes.
   * @param rdn the RDN.
   * @param attrs the LDAP attributes.
   * @return the properties of an administrator for the given rdn and LDAP
   * attributes.
   * @throws ADSContextException if something goes wrong.
   */
  private Map<AdministratorProperty, Object>
  makePropertiesFromAdministratorAttrs(String rdn, Attributes attrs)
  throws ADSContextException
  {
    Map<AdministratorProperty, Object> result =
      new HashMap<AdministratorProperty, Object>();
    String dn = rdn + "," + getAdministratorContainerDN();
    result.put(AdministratorProperty.ADMINISTRATOR_DN, dn);

    try
    {
      NamingEnumeration ne = attrs.getAll();
      while (ne.hasMore()) {
        Attribute attr = (Attribute)ne.next();
        String attrID = attr.getID();
        Object value = null;

        if (attrID.equalsIgnoreCase("cn"))
        {
          value = attr.get(0);
          result.put(AdministratorProperty.UID, value);
        }
        else if (attrID.equalsIgnoreCase("userpassword"))
        {
          value = attr.get(0);
          result.put(AdministratorProperty.PASSWORD, value);
        }
        else if (attrID.equalsIgnoreCase("description"))
        {
          value = attr.get(0);
          result.put(AdministratorProperty.DESCRIPTION, value);
        }
      }
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }

    return result;
  }

  /**
   * Returns the parent entry of the server entries.
   * @return the parent entry of the server entries.
   */
  private static String getServerContainerDN()
  {
    return "cn=Servers," + getAdministrationSuffixDN();
  }

  /**
   * Returns the parent entry of the administrator entries.
   * @return the parent entry of the administrator entries.
   */
  private static String getAdministratorContainerDN()
  {
    return "cn=Administrators," + getAdministrationSuffixDN();
  }

  /**
   * Returns the parent entry of the server group entries.
   * @return the parent entry of the server group entries.
   */
  private static String getServerGroupContainerDN()
  {
    return "cn=Server Groups," + getAdministrationSuffixDN();
  }

  /**
   * Returns the host name for the given properties.
   * @param serverProperties the server properties.
   * @return the host name for the given properties.
   * @throws ADSContextException if the host name could not be found or its
   * value is not valid.
   */
  private static String getHostname(
      Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    String result = (String)serverProperties.get(ServerProperty.HOSTNAME);
    if (result == null)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.MISSING_HOSTNAME);
    }
    else if (result.length() == 0)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.NOVALID_HOSTNAME);
    }
    return result;
  }

  /**
   * Returns the install path for the given properties.
   * @param serverProperties the server properties.
   * @return the install path for the given properties.
   * @throws ADSContextException if the install path could not be found or its
   * value is not valid.
   */
  private static String getInstallPath(
      Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    String result = (String)serverProperties.get(ServerProperty.INSTANCE_PATH);
    if (result == null)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.MISSING_IPATH);
    }
    else if (result.length() == 0)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.NOVALID_IPATH);
    }
    return result;
  }


  /**
   * Returns the Administrator UID for the given properties.
   * @param adminProperties the server properties.
   * @return the Administrator UID for the given properties.
   * @throws ADSContextException if the administrator UID could not be found.
   */
  private String getAdministratorUID(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException {
    String result = (String)adminProperties.get(
        AdministratorProperty.UID);
    if (result == null)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.MISSING_ADMIN_UID);
    }
    return result;
  }

  /**
   * Returns the Administrator password for the given properties.
   * @param adminProperties the server properties.
   * @return the Administrator password for the given properties.
   * @throws ADSContextException if the administrator password could not be
   * found.
   */
  private String getAdministratorPassword(
      Map<AdministratorProperty, Object> adminProperties)
  throws ADSContextException {
    String result = (String)adminProperties.get(
        AdministratorProperty.PASSWORD);
    if (result == null)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.MISSING_ADMIN_PASSWORD);
    }
    return result;
  }


  //
  // LDAP utilities
  //
  /**
   * Returns the LdapName object for the given dn.
   * @return the LdapName object for the given dn.
   * @throws ADSContextException if a valid LdapName could not be retrieved
   * for the given dn.
   */
  private static LdapName nameFromDN(String dn) throws ADSContextException
  {
    LdapName result = null;
    try
    {
      result = new LdapName(dn);
    }
    catch (InvalidNameException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
    return result;
  }

  /**
   * Tells whether an entry with the provided DN exists.
   * @return <CODE>true</CODE> if the entry exists and <CODE>false</CODE> if
   * it does not.
   * @throws ADSContextException if an error occurred while checking if the
   * entry exists or not.
   */
  private boolean isExistingEntry(LdapName dn) throws ADSContextException
  {
    boolean result;

    try
    {
      SearchControls sc = new SearchControls();

      sc.setSearchScope(SearchControls.OBJECT_SCOPE);
      result = getDirContext().search(dn, "(objectclass=*)", sc).hasMore();
    }
    catch (NameNotFoundException x)
    {
      result = false;
    }
    catch (NoPermissionException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ACCESS_PERMISSION);
    }
    catch(javax.naming.NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }

    return result;
  }

  /**
   * Creates a container entry with the given dn.
   * @param dn the entry of the new entry to be created.
   * @throws ADSContextException if the entry could not be created.
   */
  private void createContainerEntry(String dn) throws ADSContextException
  {
    BasicAttributes attrs = new BasicAttributes();

    attrs.put("objectclass", "extensibleobject");
    createEntry(dn, attrs);
  }

  /**
   * Creates the administrator container entry.
   * @throws ADSContextException if the entry could not be created.
   */
  private void createAdministratorContainerEntry() throws ADSContextException
  {
    BasicAttributes attrs = new BasicAttributes();

    attrs.put("objectclass", "groupOfUniqueNames");
    attrs.put("objectclass", "groupofurls");
    attrs.put("memberURL", "ldap:///" + getAdministratorContainerDN() +
        "??one?(objectclass=*)");
    attrs.put("description", "Group of identities which have full access.");
    createEntry(getAdministratorContainerDN(), attrs);
  }


  /**
   * Creates the top container entry.
   * @throws ADSContextException if the entry could not be created.
   */
  private void createTopContainerEntry() throws ADSContextException
  {
    BasicAttributes attrs = new BasicAttributes();

    attrs.put("objectclass", "extensibleobject");
    attrs.put("aci", getTopContainerACI());
    createEntry(getAdministrationSuffixDN(), attrs);
  }


  /**
   * Creates an entry with the provided dn and attributes.
   * @param dn the dn of the entry.
   * @param attrs the attributes of the entry.
   * @throws ADSContextException if the entry could not be created.
   */
  private void createEntry(String dn, Attributes attrs)
  throws ADSContextException {
    try
    {
      DirContext ctx = getDirContext().createSubcontext(nameFromDN(dn), attrs);
      ctx.close();
    }
    catch(NamingException x)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns the DN of the ACI container entry.
   * @return the DN of the ACI container entry.
   */
  private static String getTopContainerACI()
  {
    return
    "(targetattr = \"*\")" +
    "(version 3.0;" +
    "acl \"Enable full access for Directory Services Managers group\";" +
    "allow (all)" +
    "(groupdn = \"ldap:///" + getAdministratorContainerDN() + "\");" +
    ")";
  }

  /**
   * Creates the Administration Suffix.
   * @throws ADSContextException if something goes wrong.
   */
  private void createAdministrationSuffix()
  throws ADSContextException
  {
    // TODO: use new administration framework.
  }

  /**
   * Creates the Administration Suffix when the server is down.  This can only
   * be called locally.
   * @param installPath the installation path of the server
   * @throws ADSContextException if something goes wrong.
   */
  private static void createOfflineAdministrationSuffix(String installPath)
  throws ADSContextException
  {
    // TODO: the call to this method assumes
    // that OpenDS.jar has already been loaded.  So this should not be called by
    // the Java Web Start before being sure that this jar is loaded.
    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(org.opends.server.extensions.ConfigFileHandler.class.getName());

    argList.add("-c");
    argList.add(installPath+File.separator+"config"+File.separator+
        "config.ldif");

    argList.add("-b");
    argList.add(getAdministrationSuffixDN());

    String[] args = new String[argList.size()];
    argList.toArray(args);

    int returnValue = org.opends.server.tools.ConfigureDS.configMain(args);

    if (returnValue != 0)
    {
      throw new ADSContextException(
          ADSContextException.ErrorType.ERROR_UNEXPECTED);
    }
  }

  /**
   * Removes the administration suffix.
   * @throws ADSContextException
   */
  private void removeAdministrationSuffix() throws ADSContextException
  {
    // TODO: use new administration framework
  }

  /**
   * Returns the first ACI required to provide access to administrators.
   * @return the first ACI required to provide access to administrators.
   */
  private static String getAdminACI1()
  {
    return
    "(targetattr = \"*\") " +
    "(version 3.0; " +
    "acl \"Enable full access for Global Administrators.\"; " +
    "allow (all)(userdn = \"ldap:///" +
    getAdministratorDN("*") +
    "\");)";
  }


  /**
   * Returns the second ACI required to provide access to administrators.
   * @return the second ACI required to provide access to administrators.
   */
  private static String getAdminACI2()
  {
    return
    "(targetattr = \"aci\") (targetscope = \"base\") " +
    "(version 3.0; " +
    "acl \"Enable root ACI modification by Global Administrators.\"; "+
    "allow (all)(userdn = \"ldap:///" +
    getAdministratorDN("*") +
    "\");)";
  }
}
