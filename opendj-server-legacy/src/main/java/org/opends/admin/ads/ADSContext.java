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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import static org.forgerock.opendj.ldap.Filter.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.util.Utils.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.ldap.Rdn;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.admin.ads.ADSContextException.ErrorType;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.quicksetup.Constants;
import org.opends.server.types.HostPort;

/** Class used to update and read the contents of the Administration Data. */
public class ADSContext
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Enumeration containing the different server properties syntaxes that could
   * be stored in the ADS.
   */
  public enum ADSPropertySyntax
  {
    /** String syntax. */
    STRING,
    /** Integer syntax. */
    INTEGER,
    /** Boolean syntax. */
    BOOLEAN,
    /** Certificate;binary syntax. */
    CERTIFICATE_BINARY
  }

  /** Enumeration containing the different server properties that are stored in the ADS. */
  public enum ServerProperty
  {
    /** The ID used to identify the server (hostname + port). */
    ID("id",ADSPropertySyntax.STRING),
    /** The host name of the server. */
    HOST_NAME("hostname",ADSPropertySyntax.STRING),
    /** The LDAP port of the server. */
    LDAP_PORT("ldapport",ADSPropertySyntax.INTEGER),
    /** The JMX port of the server. */
    JMX_PORT("jmxport",ADSPropertySyntax.INTEGER),
    /** The JMX secure port of the server. */
    JMXS_PORT("jmxsport",ADSPropertySyntax.INTEGER),
    /** The LDAPS port of the server. */
    LDAPS_PORT("ldapsport",ADSPropertySyntax.INTEGER),
    /** The administration connector port of the server. */
    ADMIN_PORT("adminport",ADSPropertySyntax.INTEGER),
    /** The certificate used by the server. */
    CERTIFICATE("certificate",ADSPropertySyntax.STRING),
    /** The path where the server is installed. */
    INSTANCE_PATH("instancepath",ADSPropertySyntax.STRING),
    /** The description of the server. */
    DESCRIPTION("description",ADSPropertySyntax.STRING),
    /** The OS of the machine where the server is installed. */
    HOST_OS("os",ADSPropertySyntax.STRING),
    /** Whether LDAP is enabled or not. */
    LDAP_ENABLED("ldapEnabled",ADSPropertySyntax.BOOLEAN),
    /** Whether LDAPS is enabled or not. */
    LDAPS_ENABLED("ldapsEnabled",ADSPropertySyntax.BOOLEAN),
    /** Whether ADMIN is enabled or not. */
    ADMIN_ENABLED("adminEnabled",ADSPropertySyntax.BOOLEAN),
    /** Whether StartTLS is enabled or not. */
    STARTTLS_ENABLED("startTLSEnabled",ADSPropertySyntax.BOOLEAN),
    /** Whether JMX is enabled or not. */
    JMX_ENABLED("jmxEnabled",ADSPropertySyntax.BOOLEAN),
    /** Whether JMX is enabled or not. */
    JMXS_ENABLED("jmxsEnabled",ADSPropertySyntax.BOOLEAN),
    /** The location of the server. */
    LOCATION("location",ADSPropertySyntax.STRING),
    /** The groups to which this server belongs. */
    GROUPS("memberofgroups",ADSPropertySyntax.STRING),
    /** The unique name of the instance key public-key certificate. */
    INSTANCE_KEY_ID("ds-cfg-key-id",ADSPropertySyntax.STRING),
    /**
     * The instance key-pair public-key certificate. Note: This attribute
     * belongs to an instance key entry, separate from the server entry and
     * named by the ds-cfg-key-id attribute from the server entry.
     */
    INSTANCE_PUBLIC_KEY_CERTIFICATE("ds-cfg-public-key-certificate", ADSPropertySyntax.CERTIFICATE_BINARY);

    private final String attrName;
    private final ADSPropertySyntax attSyntax;

    /**
     * Private constructor.
     *
     * @param n
     *          the name of the attribute.
     * @param s
     *          the name of the syntax.
     */
    private ServerProperty(String n, ADSPropertySyntax s)
    {
      attrName = n;
      attSyntax = s;
    }

    /**
     * Returns the attribute name.
     *
     * @return the attribute name.
     */
    public String getAttributeName()
    {
      return attrName;
    }

    /**
     * Returns the attribute syntax.
     *
     * @return the attribute syntax.
     */
    public ADSPropertySyntax getAttributeSyntax()
    {
      return attSyntax;
    }
  }

  /** Default global admin UID. */
  public static final String GLOBAL_ADMIN_UID = "admin";
  private static final DN ADMIN_DATA = DN.valueOf("cn=admin data");

  /** The list of server properties that are multivalued. */
  private static final Set<ServerProperty> MULTIVALUED_SERVER_PROPERTIES = new HashSet<>();
  static
  {
    MULTIVALUED_SERVER_PROPERTIES.add(ServerProperty.GROUPS);
  }

  /** The default server group which will contain all registered servers. */
  private static final String ALL_SERVERGROUP_NAME = "all-servers";

  /** Enumeration containing the different server group properties that are stored in the ADS. */
  private enum ServerGroupProperty
  {
    /** The UID of the server group. */
    UID("cn"),
    /** The description of the server group. */
    DESCRIPTION("description"),
    /** The members of the server group. */
    MEMBERS("uniqueMember");

    private final String attrName;

    /**
     * Private constructor.
     *
     * @param n
     *          the attribute name.
     */
    private ServerGroupProperty(String n)
    {
      attrName = n;
    }

    /**
     * Returns the attribute name.
     *
     * @return the attribute name.
     */
    public String getAttributeName()
    {
      return attrName;
    }
  }

  /** The list of server group properties that are multivalued. */
  private static final Set<ServerGroupProperty> MULTIVALUED_SERVER_GROUP_PROPERTIES = new HashSet<>();
  static
  {
    MULTIVALUED_SERVER_GROUP_PROPERTIES.add(ServerGroupProperty.MEMBERS);
  }

  /** The enumeration containing the different Administrator properties. */
  public enum AdministratorProperty
  {
    /** The UID of the administrator. */
    UID("id", ADSPropertySyntax.STRING),
    /** The password of the administrator. */
    PASSWORD("password", ADSPropertySyntax.STRING),
    /** The description of the administrator. */
    DESCRIPTION("description", ADSPropertySyntax.STRING),
    /** The DN of the administrator. */
    ADMINISTRATOR_DN("administrator dn", ADSPropertySyntax.STRING),
    /** The administrator privilege. */
    PRIVILEGE("privilege", ADSPropertySyntax.STRING);

    private final String attrName;
    private final ADSPropertySyntax attrSyntax;

    /**
     * Private constructor.
     *
     * @param n
     *          the name of the attribute.
     * @param s
     *          the name of the syntax.
     */
    private AdministratorProperty(String n, ADSPropertySyntax s)
    {
      attrName = n;
      attrSyntax = s;
    }

    /**
     * Returns the attribute name.
     *
     * @return the attribute name.
     */
    public String getAttributeName()
    {
      return attrName;
    }

    /**
     * Returns the attribute syntax.
     *
     * @return the attribute syntax.
     */
    public ADSPropertySyntax getAttributeSyntax()
    {
      return attrSyntax;
    }
  }

  /** The context used to retrieve information. */
  private final ConnectionWrapper connectionWrapper;

  /**
   * Constructor of the ADSContext.
   *
   * @param connectionWrapper
   *          provide an Ldap Connection
   */
  public ADSContext(ConnectionWrapper connectionWrapper)
  {
    this.connectionWrapper = connectionWrapper;
  }

  /**
   * Returns the connection used to retrieve information by this ADSContext.
   *
   * @return the connection
   */
  public ConnectionWrapper getConnection()
  {
    return connectionWrapper;
  }

  /**
   * Returns the host name and port number of this connection.
   *
   * @return the hostPort of this connection
   */
  public HostPort getHostPort()
  {
    return connectionWrapper.getHostPort();
  }

  /**
   * Method called to register a server in the ADS.
   *
   * @param serverProperties
   *          the properties of the server.
   * @throws ADSContextException
   *           if the server could not be registered.
   */
  public void registerServer(Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    DN dn = makeDNFromServerProperties(serverProperties);

    AddRequest request = newAddRequest(dn);
    for (Map.Entry<ServerProperty, Object> entry : serverProperties.entrySet())
    {
      ServerProperty prop = entry.getKey();
      Attribute attribute = makeAttrFromServerProperty(prop, entry.getValue());
      if (attribute != null)
      {
        request.addAttribute(attribute);
      }
    }
    // TODO: use another structural objectclass
    request.addAttribute("objectclass", "top", "ds-cfg-branch", "extensibleobject");
    try
    {
      // This check is required because by default the server container entry
      // does not exist.
      if (!isExistingEntry(getServerContainerDN()))
      {
        createContainerEntry(getServerContainerDN());
      }
      throwIfNotSuccess(connectionWrapper.getConnection().add(request));
      if (serverProperties.containsKey(ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE))
      {
        registerInstanceKeyCertificate(serverProperties, dn);
      }

      // register this server into "all" groups
      Map<ServerGroupProperty, Object> serverGroupProperties = new HashMap<>();
      Set<String> memberList = getServerGroupMemberList(ALL_SERVERGROUP_NAME);
      if (memberList == null)
      {
        memberList = new HashSet<>();
      }
      String newMember = "cn=" + Rdn.escapeValue(serverProperties.get(ServerProperty.ID));

      memberList.add(newMember);
      serverGroupProperties.put(ServerGroupProperty.MEMBERS, memberList);

      updateServerGroup(ALL_SERVERGROUP_NAME, serverGroupProperties);

      // Update the server property "GROUPS"
      Set<?> rawGroupList = (Set<?>) serverProperties.get(ServerProperty.GROUPS);
      Set<String> groupList = new HashSet<>();
      if (rawGroupList != null)
      {
        for (Object elm : rawGroupList)
        {
          groupList.add(elm.toString());
        }
      }
      groupList.add(ALL_SERVERGROUP_NAME);
      serverProperties.put(ServerProperty.GROUPS, groupList);
      updateServer(serverProperties);
    }
    catch (ADSContextException ace)
    {
      throw ace;
    }
    catch (LdapException x)
    {
      if (x.getResult().getResultCode().equals(ResultCode.ENTRY_ALREADY_EXISTS))
      {
        throw new ADSContextException(ErrorType.ALREADY_REGISTERED);
      }
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Method called to update the properties of a server in the ADS.
   *
   * @param serverProperties
   *          the new properties of the server.
   * @param newServerId
   *          The new server Identifier, or null.
   * @throws ADSContextException
   *           if the server could not be registered.
   */
  private void updateServer(Map<ServerProperty, Object> serverProperties)
      throws ADSContextException
  {
    DN dn = makeDNFromServerProperties(serverProperties);

    try
    {
      ModifyRequest request = newModifyRequest(dn);
      for (Map.Entry<ServerProperty, Object> entry : serverProperties.entrySet())
      {
        ServerProperty prop = entry.getKey();
        Attribute attr = makeAttrFromServerProperty(prop, entry.getValue());
        if (attr != null)
        {
          request.addModification(new Modification(REPLACE, attr));
        }
      }
      throwIfNotSuccess(connectionWrapper.getConnection().modify(request));

      if (serverProperties.containsKey(ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE))
      {
        registerInstanceKeyCertificate(serverProperties, dn);
      }
    }
    catch (ADSContextException ace)
    {
      throw ace;
    }
    catch (EntryNotFoundException x)
    {
      throw new ADSContextException(ErrorType.NOT_YET_REGISTERED);
    }
    catch (Exception x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Method called to unregister a server in the ADS. Note that the server's
   * instance key-pair public-key certificate entry (created in
   * <tt>registerServer()</tt>) is left untouched.
   *
   * @param serverProperties
   *          the properties of the server.
   * @throws ADSContextException
   *           if the server could not be unregistered.
   */
  public void unregisterServer(Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    DN dn = makeDNFromServerProperties(serverProperties);
    Connection conn = connectionWrapper.getConnection();
    try
    {
      // Unregister the server from the server groups.
      String member = "cn=" + Rdn.escapeValue(serverProperties.get(ServerProperty.ID));
      Set<Map<ServerGroupProperty, Object>> serverGroups = readServerGroupRegistry();
      for (Map<ServerGroupProperty, Object> serverGroup : serverGroups)
      {
        Set<?> memberList = (Set<?>) serverGroup.get(ServerGroupProperty.MEMBERS);
        if (memberList != null && memberList.remove(member))
        {
          Map<ServerGroupProperty, Object> serverGroupProperties = new HashMap<>();
          serverGroupProperties.put(ServerGroupProperty.MEMBERS, memberList);
          String groupName = (String) serverGroup.get(ServerGroupProperty.UID);
          updateServerGroup(groupName, serverGroupProperties);
        }
      }

      throwIfNotSuccess(conn.delete(newDeleteRequest(dn)));
    }
    catch (EntryNotFoundException x)
    {
      throw new ADSContextException(ErrorType.NOT_YET_REGISTERED);
    }
    catch (LdapException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }

    String serverID = getServerID(serverProperties);
    if (serverID != null)
    {
      // Unregister the server in server groups
      String memberAttrName = ServerGroupProperty.MEMBERS.getAttributeName();
      Filter filter = Filter.valueOf("(" + memberAttrName + "=cn=" + serverID + ")");
      SearchRequest request = newSearchRequest(getServerGroupContainerDN(), SINGLE_LEVEL, filter);
      try (ConnectionEntryReader entryReader = conn.search(request);)
      {
        while (entryReader.hasNext())
        {
          SearchResultEntry sr = entryReader.readEntry();
          DN groupDn = sr.getName();
          Attribute newAttr = new LinkedAttribute(memberAttrName);
          for (Attribute attr : sr.getAllAttributes())
          {
            AttributeType attrType = attr.getAttributeDescription().getAttributeType();
            if (attrType.hasName(memberAttrName))
            {
              for (ByteString value : attr)
              {
                if (!value.toString().equalsIgnoreCase("cn=" + serverID))
                {
                  newAttr.add(value);
                }
              }
            }
          }
          ModificationType modType = newAttr.size() > 0 ? REPLACE : DELETE;
          ModifyRequest modRequest = newModifyRequest(groupDn)
              .addModification(new Modification(modType, newAttr));
          throwIfNotSuccess(conn.modify(modRequest));
        }
      }
      catch (EntryNotFoundException x)
      {
        throw new ADSContextException(ErrorType.BROKEN_INSTALL);
      }
      catch (AuthorizationException x)
      {
        throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
      }
      catch (IOException x)
      {
        throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
      }
    }
  }

  /**
   * Returns whether a given server is already registered or not.
   *
   * @param serverProperties
   *          the server properties.
   * @return <CODE>true</CODE> if the server was registered and
   *         <CODE>false</CODE> otherwise.
   * @throws ADSContextException
   *           if something went wrong.
   */
  private boolean isServerAlreadyRegistered(Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    return isExistingEntry(makeDNFromServerProperties(serverProperties));
  }

  /**
   * Returns whether a given administrator is already registered or not.
   *
   * @param uid
   *          the administrator UID.
   * @return <CODE>true</CODE> if the administrator was registered and
   *         <CODE>false</CODE> otherwise.
   * @throws ADSContextException
   *           if something went wrong.
   */
  private boolean isAdministratorAlreadyRegistered(String uid) throws ADSContextException
  {
    return isExistingEntry(getAdministratorDN(uid));
  }

  /**
   * A convenience method that takes some server properties as parameter and if
   * there is no server registered associated with those properties, registers
   * it and if it is already registered, updates it.
   *
   * @param serverProperties
   *          the server properties.
   * @return 0 if the server was registered; 1 if updated (i.e., the server
   *         entry was already in ADS).
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public int registerOrUpdateServer(Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    try
    {
      registerServer(serverProperties);
      return 0;
    }
    catch (ADSContextException x)
    {
      if (x.getError() == ErrorType.ALREADY_REGISTERED)
      {
        updateServer(serverProperties);
        return 1;
      }

      throw x;
    }
  }

  /**
   * Returns the member list of a group of server.
   *
   * @param serverGroupId
   *          The group name.
   * @return the member list of a group of server.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private Set<String> getServerGroupMemberList(String serverGroupId) throws ADSContextException
  {
    String dn = "cn=" + Rdn.escapeValue(serverGroupId) + "," + getServerGroupContainerDN();

    SearchRequest request = newSearchRequest(dn, BASE_OBJECT, "(objectclass=*)");
    try (ConnectionEntryReader entryReader = getConnection().getConnection().search(request))
    {
      Set<String> result = new HashSet<>();
      if (!entryReader.hasNext())
      {
        return result;
      }
      for (Attribute attr : entryReader.readEntry().getAllAttributes())
      {
        AttributeType attrType = attr.getAttributeDescription().getAttributeType();
        if (attrType.hasName(ServerGroupProperty.MEMBERS.getAttributeName()))
        {
          // We have the members list
          toStrings(result, attr);
          break;
        }
      }
      return result;
    }
    catch (EntryNotFoundException x)
    {
      return new HashSet<>();
    }
    catch (AuthorizationException x)
    {
      throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
    }
    catch (IOException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  private void toStrings(Set<String> result, Attribute attr)
  {
    for (ByteString value : attr)
    {
      result.add(value.toString());
    }
  }

  /**
   * Returns a set containing the servers that are registered in the ADS.
   *
   * @return a set containing the servers that are registered in the ADS.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public Set<Map<ServerProperty, Object>> readServerRegistry() throws ADSContextException
  {
    Set<Map<ServerProperty, Object>> result = new HashSet<>();

    SearchRequest request = newSearchRequest(getServerContainerDN(), SINGLE_LEVEL, objectClassPresent());
    try (ConnectionEntryReader entryReader = connectionWrapper.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();
        Map<ServerProperty, Object> properties = makePropertiesFromServerAttrs(sr);
        Object keyId = properties.get(ServerProperty.INSTANCE_KEY_ID);
        if (keyId != null)
        {
          SearchRequest request2 = newSearchRequest(
              getInstanceKeysContainerDN(), SINGLE_LEVEL, Filter.valueOf("(ds-cfg-key-id=" + keyId + ")"),
              "ds-cfg-public-key-certificate;binary");
          try (ConnectionEntryReader entryReader2 = connectionWrapper.getConnection().search(request2))
          {
            boolean found = false;
            while (entryReader2.hasNext())
            {
              SearchResultEntry certEntry = entryReader2.readEntry();
              Attribute certAttr = certEntry.getAttribute("ds-cfg-public-key-certificate;binary");
              properties.put(ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE, certAttr.firstValue().toByteArray());
              found = true;
            }
            if (!found)
            {
              logger.warn(LocalizableMessage.raw("Could not find public key for " + properties));
            }
          }
          catch (EntryNotFoundException x)
          {
            logger.warn(LocalizableMessage.raw("Could not find public key for " + properties));
          }
        }
        result.add(properties);
      }
      return result;
    }
    catch (EntryNotFoundException x)
    {
      throw new ADSContextException(ErrorType.BROKEN_INSTALL);
    }
    catch (AuthorizationException x)
    {
      throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
    }
    catch (IOException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Creates a Server Group in the ADS.
   *
   * @param serverGroupProperties
   *          the properties of the server group to be created.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private void createServerGroup(Map<ServerGroupProperty, Object> serverGroupProperties) throws ADSContextException
  {
    String dn = makeDNFromServerGroupProperties(serverGroupProperties);
    AddRequest request = newAddRequest(dn)
        .addAttribute("objectclass", "top", "groupOfUniqueNames");
    for (Map.Entry<ServerGroupProperty, Object> entry : serverGroupProperties.entrySet())
    {
      ServerGroupProperty prop = entry.getKey();
      request.addAttribute(makeAttrFromServerGroupProperty(prop, entry.getValue()));
    }

    try
    {
      throwIfNotSuccess(connectionWrapper.getConnection().add(request));
    }
    catch (LdapException x)
    {
      if (x.getResult().getResultCode().equals(ResultCode.ENTRY_ALREADY_EXISTS))
      {
        throw new ADSContextException(ErrorType.ALREADY_REGISTERED);
      }
      throw new ADSContextException(ErrorType.BROKEN_INSTALL, x);
    }
  }

  /**
   * Updates the properties of a Server Group in the ADS.
   *
   * @param serverGroupProperties
   *          the new properties of the server group to be updated.
   * @param groupID
   *          The group name.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private void updateServerGroup(String groupID, Map<ServerGroupProperty, Object> serverGroupProperties)
      throws ADSContextException
  {
    String dn = "cn=" + Rdn.escapeValue(groupID) + "," + getServerGroupContainerDN();
    try
    {
      // Entry renaming ?
      if (serverGroupProperties.containsKey(ServerGroupProperty.UID))
      {
        String newGroupId = serverGroupProperties.get(ServerGroupProperty.UID).toString();
        if (!newGroupId.equals(groupID))
        {
          // Rename to entry
          String newDN = ("cn=" + Rdn.escapeValue(newGroupId) + "," + getServerGroupContainerDN());
          throwIfNotSuccess(connectionWrapper.getConnection().modifyDN(dn, newDN));
          dn = newDN;
        }

        // In any case, we remove the "cn" attribute.
        serverGroupProperties.remove(ServerGroupProperty.UID);
      }
      if (serverGroupProperties.isEmpty())
      {
        return;
      }

      // Transform 'properties' into 'attributes'
      ModifyRequest request = newModifyRequest(dn);
      for (Map.Entry<ServerGroupProperty, Object> entry : serverGroupProperties.entrySet())
      {
        ServerGroupProperty prop = entry.getKey();
        request.addModification(new Modification(
            REPLACE, makeAttrFromServerGroupProperty(prop, entry.getValue())));
      }
      throwIfNotSuccess(connectionWrapper.getConnection().modify(request));
    }
    catch (EntryNotFoundException x)
    {
      throw new ADSContextException(ErrorType.NOT_YET_REGISTERED);
    }
    catch (LdapException x)
    {
      if (x.getResult().getResultCode().equals(ResultCode.ENTRY_ALREADY_EXISTS))
      {
        throw new ADSContextException(ErrorType.ALREADY_REGISTERED);
      }
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns a set containing the server groups that are defined in the ADS.
   *
   * @return a set containing the server groups that are defined in the ADS.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private Set<Map<ServerGroupProperty, Object>> readServerGroupRegistry() throws ADSContextException
  {
    SearchRequest request = newSearchRequest(getServerGroupContainerDN(), SINGLE_LEVEL, objectClassPresent());
    try (ConnectionEntryReader entryReader = connectionWrapper.getConnection().search(request))
    {
      Set<Map<ServerGroupProperty, Object>> result = new HashSet<>();
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();
        result.add(makePropertiesFromServerGroupAttrs(sr));
      }
      return result;
    }
    catch (EntryNotFoundException x)
    {
      throw new ADSContextException(ErrorType.BROKEN_INSTALL);
    }
    catch (AuthorizationException x)
    {
      throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
    }
    catch (IOException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns a set containing the administrators that are defined in the ADS.
   *
   * @return a set containing the administrators that are defined in the ADS.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public Set<Map<AdministratorProperty, Object>> readAdministratorRegistry() throws ADSContextException
  {
    Set<Map<AdministratorProperty, Object>> result = new HashSet<>();
    SearchRequest request = newSearchRequest(
        getAdministratorContainerDN(), SINGLE_LEVEL, objectClassPresent(),
        "cn", "userpassword", "ds-privilege-name", "description");
    try (ConnectionEntryReader entryReader = connectionWrapper.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        SearchResultEntry sr = entryReader.readEntry();
        result.add(makePropertiesFromAdministratorAttrs(sr.getName().rdn(), sr.getAllAttributes()));
      }
    }
    catch (EntryNotFoundException x)
    {
      throw new ADSContextException(ErrorType.BROKEN_INSTALL);
    }
    catch (AuthorizationException x)
    {
      throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
    }
    catch (IOException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }

    return result;
  }

  /**
   * Creates the Administration Data in the server. The call to this method
   * assumes that OpenDJ.jar has already been loaded.
   *
   * @param backendName
   *          the backend name which will handle admin information.
   *          <CODE>null</CODE> to use the default backend name for the admin
   *          information.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public void createAdminData(String backendName) throws ADSContextException
  {
    // Add the administration suffix
    createAdministrationSuffix(backendName);
    createAdminDataContainers();
  }

  /** Create container entries. */
  private void createAdminDataContainers() throws ADSContextException
  {
    // Create the DIT below the administration suffix
    if (!isExistingEntry(getAdministrationSuffixDN()))
    {
      createTopContainerEntry();
    }
    if (!isExistingEntry(getAdministratorContainerDN()))
    {
      createAdministratorContainerEntry();
    }
    if (!isExistingEntry(getServerContainerDN()))
    {
      createContainerEntry(getServerContainerDN());
    }
    if (!isExistingEntry(getServerGroupContainerDN()))
    {
      createContainerEntry(getServerGroupContainerDN());
    }

    // Add the default "all-servers" group
    if (!isExistingEntry((getAllServerGroupDN())))
    {
      Map<ServerGroupProperty, Object> allServersGroupsMap = new HashMap<>();
      allServersGroupsMap.put(ServerGroupProperty.UID, ALL_SERVERGROUP_NAME);
      createServerGroup(allServersGroupsMap);
    }

    // Create the CryptoManager instance key DIT below the administration suffix
    if (!isExistingEntry(getInstanceKeysContainerDN()))
    {
      createContainerEntry(getInstanceKeysContainerDN());
    }

    // Create the CryptoManager secret key DIT below the administration suffix
    if (!isExistingEntry(getSecretKeysContainerDN()))
    {
      createContainerEntry(getSecretKeysContainerDN());
    }
  }

  /**
   * Removes the administration data.
   *
   * @param removeAdministrators
   *          {@code true} if administrators should be removed. It may not be
   *          possible to remove administrators if the operation is being
   *          performed by one of the administrators because it will cause the
   *          administrator to be disconnected.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public void removeAdminData(boolean removeAdministrators) throws ADSContextException
  {
    DN[] dns = { getServerContainerDN(), getServerGroupContainerDN(),
      removeAdministrators ? getAdministratorContainerDN() : null };
    try
    {
      for (DN dn : dns)
      {
        if (dn != null)
        {
          if (isExistingEntry(dn))
          {
            DeleteRequest request = newDeleteRequest(dn)
                .addControl(SubtreeDeleteRequestControl.newControl(true));
            connectionWrapper.getConnection().delete(request);
          }
        }
      }
      // Recreate the container entries:
      createAdminDataContainers();
    }
    catch (LdapException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns <CODE>true</CODE> if the server contains Administration Data and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if the server contains Administration Data and
   *         <CODE>false</CODE> otherwise.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public boolean hasAdminData() throws ADSContextException
  {
    DN[] dns = { getAdministratorContainerDN(), getAllServerGroupDN(), getServerContainerDN(),
      getInstanceKeysContainerDN(), getSecretKeysContainerDN() };
    boolean hasAdminData = true;
    for (int i = 0; i < dns.length && hasAdminData; i++)
    {
      hasAdminData = isExistingEntry(dns[i]);
    }
    return hasAdminData;
  }

  /**
   * Returns the DN of the administrator for a given UID.
   *
   * @param uid
   *          the UID to be used to generate the DN.
   * @return the DN of the administrator for the given UID:
   */
  public static DN getAdministratorDN(String uid)
  {
    return DN.valueOf("cn=" + Rdn.escapeValue(uid) + "," + getAdministratorContainerDN());
  }

  /**
   * Creates an Administrator in the ADS.
   *
   * @param adminProperties
   *          the properties of the administrator to be created.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public void createAdministrator(Map<AdministratorProperty, Object> adminProperties) throws ADSContextException
  {
    AddRequest request = newAddRequest(getAdministratorDN(getAdministratorUID(adminProperties)));
    addAttrsFromAdministratorProperties(request, adminProperties, true);

    try
    {
      throwIfNotSuccess(connectionWrapper.getConnection().add(request));
    }
    catch (AuthorizationException x)
    {
      throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
    }
    catch (LdapException x)
    {
      if (x.getResult().getResultCode().equals(ResultCode.ENTRY_ALREADY_EXISTS))
      {
        throw new ADSContextException(ErrorType.ALREADY_REGISTERED);
      }
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Deletes the administrator in the ADS.
   *
   * @param adminProperties
   *          the properties of the administrator to be deleted.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  public void deleteAdministrator(Map<AdministratorProperty, Object> adminProperties) throws ADSContextException
  {
    DN dnCentralAdmin = getAdministratorDN(getAdministratorUID(adminProperties));
    try
    {
      throwIfNotSuccess(connectionWrapper.getConnection().delete(newDeleteRequest(dnCentralAdmin)));
    }
    catch (EntryNotFoundException x)
    {
      throw new ADSContextException(ErrorType.NOT_YET_REGISTERED);
    }
    catch (AuthorizationException x)
    {
      throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
    }
    catch (LdapException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Returns the DN of the suffix that contains the administration data.
   *
   * @return the DN of the suffix that contains the administration data.
   */
  public static DN getAdministrationSuffixDN()
  {
    return ADMIN_DATA;
  }

  /**
   * This method returns the DN of the entry that corresponds to the given host
   * name and installation path.
   *
   * @param hostname
   *          the host name.
   * @param ipath
   *          the installation path.
   * @return the DN of the entry that corresponds to the given host name and
   *         installation path.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private static DN makeDNFromHostnameAndPath(String hostname, String ipath) throws ADSContextException
  {
    return DN.valueOf("cn=" + Rdn.escapeValue(hostname + "@" + ipath) + "," + getServerContainerDN());
  }

  /**
   * This method returns the DN of the entry that corresponds to the given host
   * name port representation.
   *
   * @param serverUniqueId
   *          the host name and port.
   * @return the DN of the entry that corresponds to the given host name and
   *         port.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private static DN makeDNFromServerUniqueId(String serverUniqueId) throws ADSContextException
  {
    return DN.valueOf("cn=" + Rdn.escapeValue(serverUniqueId) + "," + getServerContainerDN());
  }

  /**
   * This method returns the DN of the entry that corresponds to the given
   * server group properties.
   *
   * @param serverGroupProperties
   *          the server group properties
   * @return the DN of the entry that corresponds to the given server group
   *         properties.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private static String makeDNFromServerGroupProperties(Map<ServerGroupProperty, Object> serverGroupProperties)
      throws ADSContextException
  {
    String serverGroupId = (String) serverGroupProperties.get(ServerGroupProperty.UID);
    if (serverGroupId == null)
    {
      throw new ADSContextException(ErrorType.MISSING_NAME);
    }
    return "cn=" + Rdn.escapeValue(serverGroupId) + "," + getServerGroupContainerDN();
  }

  /**
   * This method returns the DN of the entry that corresponds to the given
   * server properties.
   *
   * @param serverProperties
   *          the server properties.
   * @return the DN of the entry that corresponds to the given server
   *         properties.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private static DN makeDNFromServerProperties(Map<ServerProperty, Object> serverProperties)
      throws ADSContextException
  {
    String serverID = getServerID(serverProperties);
    if (serverID != null)
    {
      return makeDNFromServerUniqueId(serverID);
    }

    String hostname = getHostname(serverProperties);
    try
    {
      String ipath = getInstallPath(serverProperties);
      return makeDNFromHostnameAndPath(hostname, ipath);
    }
    catch (ADSContextException ace)
    {
      ServerDescriptor s = ServerDescriptor.createStandalone(serverProperties);
      return makeDNFromServerUniqueId(s.getHostPort(true).toString());
    }
  }

  /**
   * Enrich the provided add request with the attributes for some administrator properties.
   *
   * @param adminProperties
   *          the administrator properties.
   * @param passwordRequired
   *          Indicates if the properties should include the password.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private static void addAttrsFromAdministratorProperties(AddRequest request,
      Map<AdministratorProperty, Object> adminProperties, boolean passwordRequired) throws ADSContextException
  {
    if (passwordRequired)
    {
      request.addAttribute("userPassword", getAdministratorPassword(adminProperties));
    }
    request.addAttribute("objectclass", "top", "person");
    request.addAttribute("sn", GLOBAL_ADMIN_UID);
    if (adminProperties.containsKey(AdministratorProperty.DESCRIPTION))
    {
      request.addAttribute("description", adminProperties.get(AdministratorProperty.DESCRIPTION));
    }
    if (adminProperties.containsKey(AdministratorProperty.PRIVILEGE))
    {
      LinkedList<?> privileges = (LinkedList<?>) adminProperties.get(AdministratorProperty.PRIVILEGE);
      for (Object o : privileges)
      {
        String p = o.toString();
        if (p.startsWith("-"))
        {
          request.removeAttribute("ds-privilege-name", p.substring(1));
        }
        else
        {
          request.addAttribute("ds-privilege-name", p);
        }
      }
    }
    else
    {
      request.addAttribute(addRootPrivileges());
    }

    // Add the RootDNs Password policy so the password do not expire.
    request.addAttribute("ds-pwp-password-policy-dn", "cn=Root Password Policy,cn=Password Policies,cn=config");
  }

  /**
   * Builds an attribute which contains 'root' privileges.
   *
   * @return The attribute which contains 'root' privileges.
   */
  private static Attribute addRootPrivileges()
  {
    Attribute privilege = new LinkedAttribute("ds-privilege-name");
    privilege.add("bypass-acl");
    privilege.add("modify-acl");
    privilege.add("config-read");
    privilege.add("config-write");
    privilege.add("ldif-import");
    privilege.add("ldif-export");
    privilege.add("backend-backup");
    privilege.add("backend-restore");
    privilege.add("server-shutdown");
    privilege.add("server-restart");
    privilege.add("disconnect-client");
    privilege.add("cancel-request");
    privilege.add("password-reset");
    privilege.add("update-schema");
    privilege.add("privilege-change");
    privilege.add("unindexed-search");
    privilege.add("subentry-write");
    privilege.add("changelog-read");
    return privilege;
  }

  /**
   * Returns the attribute for a given server property.
   *
   * @param property
   *          the server property.
   * @param value
   *          the value.
   * @return the attribute for a given server property.
   */
  private static Attribute makeAttrFromServerProperty(ServerProperty property, Object value)
  {
    switch (property)
    {
    case INSTANCE_PUBLIC_KEY_CERTIFICATE:
      // used in separate instance key entry
      return null;
    case GROUPS:
      return new LinkedAttribute(ServerProperty.GROUPS.getAttributeName(), ((Collection<?>) value));
    default:
      return new LinkedAttribute(property.getAttributeName(), value);
    }
  }

  /**
   * Returns the attribute for a given server group property.
   *
   * @param property
   *          the server group property.
   * @param value
   *          the value.
   * @return the attribute for a given server group property.
   */
  private static Attribute makeAttrFromServerGroupProperty(ServerGroupProperty property, Object value)
  {
    switch (property)
    {
    case MEMBERS:
      return new LinkedAttribute(ServerGroupProperty.MEMBERS.getAttributeName(), (Collection<?>) value);
    default:
      return new LinkedAttribute(property.getAttributeName(), value);
    }
  }

  /**
   * Returns the properties of a server group for some LDAP attributes.
   *
   * @param entry
   *          the LDAP entry.
   * @return the properties of a server group for some LDAP attributes.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private Map<ServerGroupProperty, Object> makePropertiesFromServerGroupAttrs(SearchResultEntry entry)
      throws ADSContextException
  {
    Map<ServerGroupProperty, Object> result = new HashMap<>();
    for (ServerGroupProperty prop : ServerGroupProperty.values())
    {
      Attribute attr = entry.getAttribute(prop.getAttributeName());
      if (attr == null)
      {
        continue;
      }
      Object value;

      if (attr.size() >= 1 && MULTIVALUED_SERVER_GROUP_PROPERTIES.contains(prop))
      {
        Set<String> set = new HashSet<>();
        toStrings(set, attr);
        value = set;
      }
      else
      {
        value = attr.firstValueAsString();
      }

      result.put(prop, value);
    }
    return result;
  }

  /**
   * Returns the properties of a server for some LDAP attributes.
   *
   * @param entry
   *          the entry.
   * @return the properties of a server for some LDAP attributes.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private Map<ServerProperty, Object> makePropertiesFromServerAttrs(SearchResultEntry entry) throws ADSContextException
  {
    Map<ServerProperty, Object> result = new HashMap<>();
    for (Attribute attr : entry.getAllAttributes())
    {
      AttributeType attrType = attr.getAttributeDescription().getAttributeType();
      Object value;

      ServerProperty prop = null;
      ServerProperty[] props = ServerProperty.values();
      for (int i = 0; i < props.length && prop == null; i++)
      {
        String v = props[i].getAttributeName();
        if (attrType.hasName(v))
        {
          prop = props[i];
        }
      }
      if (prop == null)
      {
        // Do not handle it
      }
      else
      {
        if (attr.size() >= 1 && MULTIVALUED_SERVER_PROPERTIES.contains(prop))
        {
          Set<String> set = new HashSet<>();
          toStrings(set, attr);
          value = set;
        }
        else
        {
          value = attr.firstValueAsString();
        }

        result.put(prop, value);
      }
    }
    return result;
  }

  /**
   * Returns the properties of an administrator for some rdn and LDAP
   * attributes.
   *
   * @param rdn
   *          the RDN.
   * @param attrs
   *          the LDAP attributes.
   * @return the properties of an administrator for the given rdn and LDAP
   *         attributes.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private Map<AdministratorProperty, Object> makePropertiesFromAdministratorAttrs(RDN rdn, Iterable<Attribute> attrs)
  {
    Map<AdministratorProperty, Object> result = new HashMap<>();
    result.put(AdministratorProperty.ADMINISTRATOR_DN, rdn + "," + getAdministratorContainerDN());
    for (Attribute attr : attrs)
    {
      AttributeType attrName = attr.getAttributeDescription().getAttributeType();

      if (attrName.hasName("cn"))
      {
        result.put(AdministratorProperty.UID, attr.firstValueAsString());
      }
      else if (attrName.hasName("userpassword"))
      {
        result.put(AdministratorProperty.PASSWORD, new String(attr.firstValue().toByteArray()));
      }
      else if (attrName.hasName("description"))
      {
        result.put(AdministratorProperty.DESCRIPTION, attr.firstValueAsString());
      }
      else if (attrName.hasName("ds-privilege-name"))
      {
        LinkedHashSet<String> privileges = new LinkedHashSet<>();
        toStrings(privileges, attr);
        result.put(AdministratorProperty.PRIVILEGE, privileges);
      }
    }
    return result;
  }

  /**
   * Returns the parent entry of the server entries.
   *
   * @return the parent entry of the server entries.
   */
  private static DN getServerContainerDN()
  {
    return DN.valueOf("cn=Servers," + getAdministrationSuffixDN());
  }

  /**
   * Returns the parent entry of the administrator entries.
   *
   * @return the parent entry of the administrator entries.
   */
  public static DN getAdministratorContainerDN()
  {
    return DN.valueOf("cn=Administrators," + getAdministrationSuffixDN());
  }

  /**
   * Returns the parent entry of the server group entries.
   *
   * @return the parent entry of the server group entries.
   */
  private static DN getServerGroupContainerDN()
  {
    return DN.valueOf("cn=Server Groups," + getAdministrationSuffixDN());
  }

  /**
   * Returns the all server group entry DN.
   *
   * @return the all server group entry DN.
   */
  private static DN getAllServerGroupDN()
  {
    return DN.valueOf("cn=" + Rdn.escapeValue(ALL_SERVERGROUP_NAME) + "," + getServerGroupContainerDN());
  }

  /**
   * Returns the host name for the given properties.
   *
   * @param serverProperties
   *          the server properties.
   * @return the host name for the given properties.
   * @throws ADSContextException
   *           if the host name could not be found or its value is not valid.
   */
  private static String getHostname(Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    String result = (String) serverProperties.get(ServerProperty.HOST_NAME);
    if (result == null)
    {
      throw new ADSContextException(ErrorType.MISSING_HOSTNAME);
    }
    else if (result.length() == 0)
    {
      throw new ADSContextException(ErrorType.NOVALID_HOSTNAME);
    }
    return result;
  }

  /**
   * Returns the Server ID for the given properties.
   *
   * @param serverProperties
   *          the server properties.
   * @return the server ID for the given properties or null.
   */
  private static String getServerID(Map<ServerProperty, Object> serverProperties)
  {
    String result = (String) serverProperties.get(ServerProperty.ID);
    if (result != null && result.length() == 0)
    {
      result = null;
    }
    return result;
  }

  /**
   * Returns the install path for the given properties.
   *
   * @param serverProperties
   *          the server properties.
   * @return the install path for the given properties.
   * @throws ADSContextException
   *           if the install path could not be found or its value is not valid.
   */
  private static String getInstallPath(Map<ServerProperty, Object> serverProperties) throws ADSContextException
  {
    String result = (String) serverProperties.get(ServerProperty.INSTANCE_PATH);
    if (result == null)
    {
      throw new ADSContextException(ErrorType.MISSING_IPATH);
    }
    else if (result.length() == 0)
    {
      throw new ADSContextException(ErrorType.NOVALID_IPATH);
    }
    return result;
  }

  /**
   * Returns the Administrator UID for the given properties.
   *
   * @param adminProperties
   *          the server properties.
   * @return the Administrator UID for the given properties.
   * @throws ADSContextException
   *           if the administrator UID could not be found.
   */
  private static String getAdministratorUID(Map<AdministratorProperty, Object> adminProperties)
      throws ADSContextException
  {
    String result = (String) adminProperties.get(AdministratorProperty.UID);
    if (result == null)
    {
      throw new ADSContextException(ErrorType.MISSING_ADMIN_UID);
    }
    return result;
  }

  /**
   * Returns the Administrator password for the given properties.
   *
   * @param adminProperties
   *          the server properties.
   * @return the Administrator password for the given properties.
   * @throws ADSContextException
   *           if the administrator password could not be found.
   */
  private static String getAdministratorPassword(Map<AdministratorProperty, Object> adminProperties)
      throws ADSContextException
  {
    String result = (String) adminProperties.get(AdministratorProperty.PASSWORD);
    if (result == null)
    {
      throw new ADSContextException(ErrorType.MISSING_ADMIN_PASSWORD);
    }
    return result;
  }

  // LDAP utilities

  /**
   * Tells whether an entry with the provided DN exists.
   *
   * @param dn
   *          the DN to check.
   * @return <CODE>true</CODE> if the entry exists and <CODE>false</CODE> if it
   *         does not.
   * @throws ADSContextException
   *           if an error occurred while checking if the entry exists or not.
   */
  private boolean isExistingEntry(DN dn) throws ADSContextException
  {
    SearchRequest request = newSearchRequest(dn, BASE_OBJECT, objectClassPresent(), NO_ATTRIBUTES);
    try (ConnectionEntryReader entryReader = getConnection().getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        entryReader.readEntry();
        return true;
      }
      return false;
    }
    catch (EntryNotFoundException x)
    {
      return false;
    }
    catch (AuthorizationException x)
    {
      throw new ADSContextException(ErrorType.ACCESS_PERMISSION);
    }
    catch (IOException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Creates a container entry with the given dn.
   *
   * @param dn
   *          the entry of the new entry to be created.
   * @throws ADSContextException
   *           if the entry could not be created.
   */
  private void createContainerEntry(DN dn) throws ADSContextException
  {
    createEntry(newAddRequest(dn).addAttribute("objectclass", "top", "ds-cfg-branch"));
  }

  /**
   * Creates the administrator container entry.
   *
   * @throws ADSContextException
   *           if the entry could not be created.
   */
  private void createAdministratorContainerEntry() throws ADSContextException
  {
    AddRequest request = newAddRequest(getAdministratorContainerDN())
        .addAttribute("objectclass", "groupofurls")
        .addAttribute("memberURL", "ldap:///" + getAdministratorContainerDN() + "??one?(objectclass=*)")
        .addAttribute("description", "Group of identities which have full access.");
    createEntry(request);
  }

  /**
   * Creates the top container entry.
   *
   * @throws ADSContextException
   *           if the entry could not be created.
   */
  private void createTopContainerEntry() throws ADSContextException
  {
    AddRequest request = newAddRequest(getAdministrationSuffixDN())
        .addAttribute("objectclass", "top", "ds-cfg-branch");
    createEntry(request);
  }

  /**
   * Creates an entry with the provided add request.
   *
   * @param addRequest
   *          the add request.
   * @throws ADSContextException
   *           if the entry could not be created.
   */
  private void createEntry(AddRequest request) throws ADSContextException
  {
    try
    {
      throwIfNotSuccess(getConnection().getConnection().add(request));
    }
    catch (LdapException e)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, e);
    }
  }

  private void throwIfNotSuccess(Result result) throws LdapException
  {
    ResultCode rc = result.getResultCode();
    if (rc.isExceptional())
    {
      throw LdapException.newLdapException(result);
    }
  }

  /**
   * Creates the Administration Suffix.
   *
   * @param backendName
   *          the backend name to be used for the Administration Suffix. If this
   *          value is null the default backendName for the Administration
   *          Suffix will be used.
   * @throws ADSContextException
   *           if something goes wrong.
   */
  private void createAdministrationSuffix(String backendName) throws ADSContextException
  {
    ADSContextHelper helper = new ADSContextHelper();
    String ben = backendName;
    if (backendName == null)
    {
      ben = getDefaultBackendName();
    }
    helper.createAdministrationSuffix(connectionWrapper, ben);
  }

  /**
   * Returns the default backend name of the administration data.
   *
   * @return the default backend name of the administration data.
   */
  public static String getDefaultBackendName()
  {
    return "adminRoot";
  }

  /**
   * Returns the LDIF file of the administration data.
   *
   * @return the LDIF file of the administration data.
   */
  static String getAdminLDIFFile()
  {
    return "config" + File.separator + "admin-backend.ldif";
  }

  /** CryptoManager related types, fields, and methods. */

  /**
   * Returns the parent entry of the server key entries in ADS.
   *
   * @return the parent entry of the server key entries in ADS.
   */
  static DN getInstanceKeysContainerDN()
  {
    return DN.valueOf("cn=instance keys," + getAdministrationSuffixDN());
  }

  /**
   * Returns the parent entry of the secret key entries in ADS.
   *
   * @return the parent entry of the secret key entries in ADS.
   */
  private static DN getSecretKeysContainerDN()
  {
    return DN.valueOf("cn=secret keys," + getAdministrationSuffixDN());
  }

  /**
   * Tells whether the provided server is registered in the registry.
   *
   * @param server
   *          the server.
   * @param registry
   *          the registry.
   * @return <CODE>true</CODE> if the server is registered in the registry and
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean isRegistered(ServerDescriptor server, Set<Map<ADSContext.ServerProperty, Object>> registry)
  {
    for (Map<ADSContext.ServerProperty, Object> s : registry)
    {
      ServerDescriptor servInRegistry = ServerDescriptor.createStandalone(s);
      if (servInRegistry.getId().equals(server.getId()))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Register instance key-pair public-key certificate provided in
   * serverProperties: generate a key-id attribute if one is not provided (as
   * expected); add an instance key public-key certificate entry for the key
   * certificate; and associate the certificate entry with the server entry via
   * the key ID attribute.
   *
   * @param serverProperties
   *          Properties of the server being registered to which the instance
   *          key entry belongs.
   * @param serverEntryDn
   *          The server's ADS entry DN.
   * @throws ADSContextException
   *           In case there is a problem registering the instance public key certificate ID
   */
  private void registerInstanceKeyCertificate(Map<ServerProperty, Object> serverProperties, DN serverEntryDn)
      throws ADSContextException
  {
    ADSContextHelper helper = new ADSContextHelper();
    helper.registerInstanceKeyCertificate(connectionWrapper, serverProperties, serverEntryDn);
  }

  /**
   * Return the set of valid (i.e., not tagged as compromised) instance key-pair
   * public-key certificate entries in ADS. NOTE: calling this method assumes
   * that all the jar files are present in the classpath.
   *
   * @return The set of valid (i.e., not tagged as compromised) instance
   *         key-pair public-key certificate entries in ADS represented as a Map
   *         from ds-cfg-key-id value to ds-cfg-public-key-certificate;binary
   *         value. Note that the collection might be empty.
   * @throws ADSContextException
   *           in case of problems with the entry search.
   * @see org.opends.server.crypto.CryptoManagerImpl#getTrustedCertificates
   */
  public Map<String, byte[]> getTrustedCertificates() throws ADSContextException
  {
    final DN baseDN = getInstanceKeysContainerDN();
    ADSContextHelper helper = new ADSContextHelper();
    final String FILTER_OC_INSTANCE_KEY = "(objectclass=" + helper.getOcCryptoInstanceKey() + ")";
    final String FILTER_NOT_COMPROMISED = "(!(" + helper.getAttrCryptoKeyCompromisedTime() + "=*))";
    final Filter searchFilter = Filter.valueOf("(&" + FILTER_OC_INSTANCE_KEY + FILTER_NOT_COMPROMISED + ")");

    String instanceKeyId = ADSContext.ServerProperty.INSTANCE_KEY_ID.getAttributeName();
    String instanceKeyCertificate =
        ADSContext.ServerProperty.INSTANCE_PUBLIC_KEY_CERTIFICATE.getAttributeName() + ";binary";
    SearchRequest request =
        newSearchRequest(baseDN, SINGLE_LEVEL, searchFilter, instanceKeyId, instanceKeyCertificate);

    final Map<String, byte[]> certificateMap = new HashMap<>();
    try (ConnectionEntryReader entryReader = connectionWrapper.getConnection().search(request))
    {
      while (entryReader.hasNext())
      {
        final SearchResultEntry entry = entryReader.readEntry();
        final Attribute keyIDAttr = entry.getAttribute(instanceKeyId);
        final Attribute keyCertAttr = entry.getAttribute(instanceKeyCertificate);
        if (null == keyIDAttr || null == keyCertAttr)
        {
          continue;// schema viol.
        }
        certificateMap.put(keyIDAttr.firstValueAsString(), keyCertAttr.firstValue().toByteArray());
      }
      return certificateMap;
    }
    catch (IOException x)
    {
      throw new ADSContextException(ErrorType.ERROR_UNEXPECTED, x);
    }
  }

  /**
   * Merge the contents of this ADSContext with the contents of the provided
   * ADSContext. Note that only the contents of this ADSContext will be updated.
   *
   * @param adsCtx
   *          the other ADSContext to merge the contents with.
   * @throws ADSContextException
   *           if there was an error during the merge.
   */
  public void mergeWithRegistry(ADSContext adsCtx) throws ADSContextException
  {
    try
    {
      mergeAdministrators(adsCtx);
      mergeServerGroups(adsCtx);
      mergeServers(adsCtx);
    }
    catch (ADSContextException adce)
    {
      LocalizableMessage msg = ERR_ADS_MERGE.get(getHostPort(), adsCtx.getHostPort(), adce.getMessageObject());
      throw new ADSContextException(ErrorType.ERROR_MERGING, msg, adce);
    }
  }

  /**
   * Merge the administrator contents of this ADSContext with the contents of
   * the provided ADSContext. Note that only the contents of this ADSContext
   * will be updated.
   *
   * @param adsCtx
   *          the other ADSContext to merge the contents with.
   * @throws ADSContextException
   *           if there was an error during the merge.
   */
  private void mergeAdministrators(ADSContext adsCtx) throws ADSContextException
  {
    Set<Map<AdministratorProperty, Object>> admins2 = adsCtx.readAdministratorRegistry();
    SortedSet<String> notDefinedAdmins = new TreeSet<>();
    for (Map<AdministratorProperty, Object> admin2 : admins2)
    {
      String uid = (String) admin2.get(AdministratorProperty.UID);
      if (!isAdministratorAlreadyRegistered(uid))
      {
        notDefinedAdmins.add(uid);
      }
    }
    if (!notDefinedAdmins.isEmpty())
    {
      LocalizableMessage msg = ERR_ADS_ADMINISTRATOR_MERGE.get(
          adsCtx.getHostPort(), getHostPort(),
          joinAsString(Constants.LINE_SEPARATOR, notDefinedAdmins), getHostPort());
      throw new ADSContextException(ErrorType.ERROR_MERGING, msg, null);
    }
  }

  /**
   * Merge the groups contents of this ADSContext with the contents of the
   * provided ADSContext. Note that only the contents of this ADSContext will be
   * updated.
   *
   * @param adsCtx
   *          the other ADSContext to merge the contents with.
   * @throws ADSContextException
   *           if there was an error during the merge.
   */
  private void mergeServerGroups(ADSContext adsCtx) throws ADSContextException
  {
    Set<Map<ServerGroupProperty, Object>> serverGroups1 = readServerGroupRegistry();
    Set<Map<ServerGroupProperty, Object>> serverGroups2 = adsCtx.readServerGroupRegistry();

    for (Map<ServerGroupProperty, Object> group2 : serverGroups2)
    {
      Map<ServerGroupProperty, Object> group1 = null;
      String uid2 = (String) group2.get(ServerGroupProperty.UID);
      for (Map<ServerGroupProperty, Object> gr : serverGroups1)
      {
        String uid1 = (String) gr.get(ServerGroupProperty.UID);
        if (uid1.equalsIgnoreCase(uid2))
        {
          group1 = gr;
          break;
        }
      }

      if (group1 != null)
      {
        // Merge the members, keep the description on this ADS.
        Set<String> member1List = getServerGroupMemberList(uid2);
        if (member1List == null)
        {
          member1List = new HashSet<>();
        }
        Set<String> member2List = adsCtx.getServerGroupMemberList(uid2);
        if (member2List != null && !member2List.isEmpty())
        {
          member1List.addAll(member2List);
          Map<ServerGroupProperty, Object> newProperties = new HashMap<>();
          newProperties.put(ServerGroupProperty.MEMBERS, member1List);
          updateServerGroup(uid2, newProperties);
        }
      }
      else
      {
        createServerGroup(group2);
      }
    }
  }

  /**
   * Merge the server contents of this ADSContext with the contents of the
   * provided ADSContext. Note that only the contents of this ADSContext will be
   * updated.
   *
   * @param adsCtx
   *          the other ADSContext to merge the contents with.
   * @throws ADSContextException
   *           if there was an error during the merge.
   */
  private void mergeServers(ADSContext adsCtx) throws ADSContextException
  {
    for (Map<ServerProperty, Object> server2 : adsCtx.readServerRegistry())
    {
      if (!isServerAlreadyRegistered(server2))
      {
        registerServer(server2);
      }
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "(" + connectionWrapper + ")";
  }
}
