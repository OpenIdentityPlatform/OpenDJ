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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.admin;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultEntry;

import static org.opends.server.protocols.internal.Requests.*;

/**
 * Check if information found in "cn=admin data" is coherent with
 * cn=config. If and inconsistency is detected, we log a warning
 * message and update "cn=admin data"
 */
public final class AdministrationDataSync
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The root connection. */
  private InternalClientConnection internalConnection;

  /** The attribute name used to store the port. TODO Use the default one. */
  private static final String LDAP_PORT = "ds-cfg-listen-port";

  /**
   * Create an object that will synchronize configuration and the admin data.
   *
   * @param internalConnection
   *          The root connection.
   */
  public AdministrationDataSync(InternalClientConnection internalConnection)
  {
    this.internalConnection = internalConnection;
  }

  /**
   * Check if information found in "cn=admin data" is coherent with
   * cn=config. If and inconsistency is detected, we log a warning
   * message and update "cn=admin data"
   */
  public void synchronize()
  {
    // Check if the admin connector is in sync
    checkAdminConnector();
  }

  /**
   * Check if the admin connector is in sync. The desynchronization
   * could occurs after the upgrade from 1.0.
   */
  private void checkAdminConnector()
  {
    // Look for the server registration in "cn=admin data"
    DN serverEntryDN = searchServerEntry();
    if (serverEntryDN == null)
    {
      // Nothing to do
      return;
    }

    // Get the admin port
    String adminPort = getAttr("cn=Administration Connector,cn=config", LDAP_PORT);
    if (adminPort == null)
    {
      // best effort.
      return;
    }

    AttributeType attrType1 = DirectoryServer.getAttributeTypeOrDefault("adminport".toLowerCase());
    AttributeType attrType2 = DirectoryServer.getAttributeTypeOrDefault("adminEnabled".toLowerCase());

    LinkedList<Modification> mods = new LinkedList<>();
    mods.add(new Modification(ModificationType.REPLACE, Attributes.create(attrType1, adminPort)));
    mods.add(new Modification(ModificationType.REPLACE, Attributes.create(attrType2, "true")));

    // Process modification
    internalConnection.processModify(serverEntryDN, mods);
  }

  /**
   * Look for the DN of the local register server. Assumption: default
   * Connection Handler naming is used.
   *
   * @return The DN of the local register server or null.
   */
  private DN searchServerEntry()
  {
    DN returnDN = null;

    // Get the LDAP and LDAPS port
    String ldapPort = getAttr("cn=LDAP Connection Handler,cn=Connection Handlers,cn=config", LDAP_PORT);
    String ldapsPort = getAttr("cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config", LDAP_PORT);
    boolean ldapsPortEnable = false;
    String val = getAttr("cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config", "ds-cfg-enabled");
    if (val != null)
    {
      ldapsPortEnable = "true".equals(val.toLowerCase());
    }
    if (ldapPort == null && ldapsPort == null)
    {
      // best effort (see assumption)
      return null;
    }

    // Get the IP address of the local host.
    String hostName;
    try
    {
      hostName = InetAddress.getLocalHost().getCanonicalHostName();
    }
    catch (Throwable t)
    {
      // best effort.
      return null;
    }

    // Look for a local server with the Ldap Port.
    try
    {
      SearchRequest request = newSearchRequest(DN.valueOf("cn=Servers,cn=admin data"), SearchScope.SINGLE_LEVEL);
      InternalSearchOperation op = internalConnection.processSearch(request);
      if (op.getResultCode() == ResultCode.SUCCESS)
      {
        Entry entry = findSameHostAndPort(op.getSearchEntries(), hostName, ldapPort, ldapsPortEnable, ldapsPort);
        if (entry != null)
        {
          returnDN = entry.getName();
        }
      }
    }
    catch (DirectoryException e)
    {
      // never happens because the filter is always valid.
      return null;
    }
    return returnDN;
  }

  private Entry findSameHostAndPort(LinkedList<SearchResultEntry> searchResultEntries,
      String hostName, String ldapPort, boolean ldapsPortEnable, String ldapsPort)
  {
    for (Entry currentEntry : searchResultEntries)
    {
      String currentHostname = currentEntry.parseAttribute("hostname").asString();
      try
      {
        String currentIPAddress = InetAddress.getByName(currentHostname).getCanonicalHostName();
        if (currentIPAddress.equals(hostName))
        {
          // Check if one of the port match
          String currentport = currentEntry.parseAttribute("ldapport").asString();
          if (currentport.equals(ldapPort))
          {
            return currentEntry;
          }
          if (ldapsPortEnable)
          {
            currentport = currentEntry.parseAttribute("ldapsport").asString();
            if (currentport.equals(ldapsPort))
            {
              return currentEntry;
            }
          }
        }
      }
      catch (Exception e)
      {
        // best effort.
        continue;
      }
    }
    return null;
  }

  /**
   * Gets an attribute value from an entry.
   *
   * @param DN
   *          The DN of the entry.
   * @param attrName
   *          The attribute name.
   * @return The attribute value or {@code null} if the value could
   *         not be retrieved.
   */
  private String getAttr(String baseDN, String attrName)
  {
    InternalSearchOperation search;
    try
    {
      SearchRequest request = Requests.newSearchRequest(DN.valueOf(baseDN), SearchScope.BASE_OBJECT)
          .addAttribute(attrName);
      search = internalConnection.processSearch(request);
      if (search.getResultCode() != ResultCode.SUCCESS)
      {
        // can not happen
        // best effort.
        // TODO Log an Error.
        return null;
      }
    }
    catch (DirectoryException e)
    {
      // can not happen
      // best effort.
      logger.traceException(e);
      return null;
    }

    // Read the port from the PORT attribute
    SearchResultEntry adminConnectorEntry = null;
    LinkedList<SearchResultEntry> result = search.getSearchEntries();
    if (!result.isEmpty())
    {
      adminConnectorEntry = result.getFirst();
    }

    AttributeType attrType = DirectoryServer.getAttributeTypeOrDefault(attrName);
    List<Attribute> attrs = adminConnectorEntry.getAttribute(attrType);
    if (attrs != null)
    {
      // Get the attribute value
      return attrs.get(0).iterator().next().toString();
    }

    // can not happen
    // best effort.
    // TODO Log an Error.
    return null;
  }
}
