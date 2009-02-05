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
package org.opends.server.admin;



import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;



/**
 * Check if information found in "cn=admin data" is coherent with
 * cn=config. If and inconsistency is detected, we log a warning
 * message and update "cn=admin data"
 */
public final class AdministrationDataSync
{

  /**
   * The root connection.
   */
  private InternalClientConnection internalConnection;

  /**
   * The attribute name used to store the port. TODO Use the default
   * one.
   */
  private static final String LDAP_PORT = "ds-cfg-listen-port";



  /**
   * Create an object that will syncrhonize configuration and the
   * admin data.
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
   * cn=config. If and inconsistancy is detected, we log a warning
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
    String adminPort = getAttr("cn=Administration Connector,cn=config",
        LDAP_PORT);
    if (adminPort == null)
    {
      // best effort.
      return;
    }

    LinkedList<Modification> mods = new LinkedList<Modification>();
    // adminport
    String attName = "adminport";
    AttributeType attrType = DirectoryServer.getAttributeType(attName
        .toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(attName.toLowerCase());
    }
    mods.add(new Modification(ModificationType.REPLACE, Attributes.create(
        attrType, adminPort)));

    // adminEnabled
    attName = "adminEnabled";
    attrType = DirectoryServer.getAttributeType(attName.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(attName.toLowerCase());
    }
    mods.add(new Modification(ModificationType.REPLACE, Attributes.create(
        attrType, "true")));

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
    String ldapPort = getAttr(
        "cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        LDAP_PORT);
    String ldapsPort = getAttr(
        "cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config",
        LDAP_PORT);
    boolean ldapsPortEnable = false;
    String val = getAttr(
        "cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config",
        "ds-cfg-enabled");
    if (val != null)
    {
      ldapsPortEnable = val.toLowerCase().equals("true");
    }
    if ((ldapPort == null) && (ldapsPort == null))
    {
      // best effort (see assumption)
      return null;
    }

    // Get the IP address of the local host.
    String hostName = "";
    try
    {
      hostName = java.net.InetAddress.getLocalHost().getCanonicalHostName();
    }
    catch (Throwable t)
    {
      // best effort.
      return null;
    }

    // Look for a local server with the Ldap Port.
    InternalSearchOperation op = null;
    String attrName = "hostname";
    AttributeType hostnameType = DirectoryServer.getAttributeType(attrName);
    if (hostnameType == null)
    {
      hostnameType = DirectoryServer.getDefaultAttributeType(attrName);
    }
    try
    {
      op = internalConnection.processSearch("cn=Servers,cn=admin data",
          SearchScope.SINGLE_LEVEL, "objectclass=*");
      if (op.getResultCode() == ResultCode.SUCCESS)
      {
        Entry entry = null;
        for (Entry currentEntry : op.getSearchEntries())
        {
          String currentHostname = currentEntry.getAttributeValue(hostnameType,
              DirectoryStringSyntax.DECODER);
          try
          {
            String currentIPAddress = java.net.InetAddress.getByName(
                currentHostname).getCanonicalHostName();
            if (currentIPAddress.equals(hostName))
            {
              // Check if one of the port match
              attrName = "ldapport";
              AttributeType portType = DirectoryServer
                  .getAttributeType(attrName);
              if (portType == null)
              {
                portType = DirectoryServer.getDefaultAttributeType(attrName);
              }
              String currentport = currentEntry.getAttributeValue(portType,
                  DirectoryStringSyntax.DECODER);
              if (currentport.equals(ldapPort))
              {
                entry = currentEntry;
                break;
              }
              if (ldapsPortEnable)
              {
                attrName = "ldapsport";
                portType = DirectoryServer.getAttributeType(attrName);
                if (portType == null)
                {
                  portType = DirectoryServer.getDefaultAttributeType(attrName);
                }
                currentport = currentEntry.getAttributeValue(portType,
                    DirectoryStringSyntax.DECODER);
                if (currentport.equals(ldapsPort))
                {
                  entry = currentEntry;
                  break;
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

        if (entry != null)
        {
          returnDN = entry.getDN();
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
    // Prepare the ldap search
    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    }
    catch (LDAPException e)
    {
      // can not happen
      // best effort.
      // TODO Log an Error.
      return null;
    }

    LinkedHashSet<String> attributes = new LinkedHashSet<String>(1);
    attributes.add(attrName);
    InternalSearchOperation search = internalConnection.processSearch(
        ByteString.valueOf(baseDN), SearchScope.BASE_OBJECT,
        DereferencePolicy.DEREF_ALWAYS, 0, 0, false, filter, attributes);

    if ((search.getResultCode() != ResultCode.SUCCESS))
    {
      // can not happen
      // best effort.
      // TODO Log an Error.
      return null;
    }

    SearchResultEntry adminConnectorEntry = null;

    /*
     * Read the port from the PORT attribute
     */
    LinkedList<SearchResultEntry> result = search.getSearchEntries();
    if (!result.isEmpty())
    {
      adminConnectorEntry = result.getFirst();
    }

    AttributeType attrType = DirectoryServer.getAttributeType(attrName);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(attrName);
    }

    List<Attribute> attrs = adminConnectorEntry.getAttribute(attrType);

    if (attrs == null)
    {
      // can not happen
      // best effort.
      // TODO Log an Error.
      return null;
    }

    // Get the attribute value
    return attrs.get(0).iterator().next().toString();
  }

}
