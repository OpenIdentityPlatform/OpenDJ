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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.util;

import java.util.ArrayList;
import java.util.Collection;

import org.opends.server.types.DN;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.OpenDsException;

/** Class used to handle the case where numsubordinates does not work between databases. */
public class NumSubordinateHacker {
  String serverHost = "not-initialized";
  int serverPort = -1;
  final ArrayList<DN> unreliableEntryList = new ArrayList<>();
  boolean isUnreliableEntryListEmpty;

  /**
    * Tells whether the list of unreliable contains children of
    * the entry with LDAPUrl parentUrl.
    * @param parentUrl the LDAPURL of the parent.
    * @return <CODE>true</CODE> if the list of unreliable entries contains a
    * children of the parentUrl.  Returns <CODE>false</CODE> otherwise.
    */
  public boolean containsChildrenOf(LDAPURL parentUrl) {
    if (!isUnreliableEntryListEmpty) {
      boolean isInServer = serverHost.equalsIgnoreCase(String.valueOf(parentUrl.getHost()))
          && serverPort == parentUrl.getPort();
      if (isInServer) {
        try
        {
          for (DN dn : unreliableEntryList)
          {
            if (dn.equals(DN.valueOf(parentUrl.getRawBaseDN())))
            {
              return true;
            }
          }
        }
        catch (OpenDsException oe)
        {
          throw new RuntimeException("Error decoding DN of url: "+ parentUrl);
        }
      }
    }
    return false;
  }

  /**
    * Tells whether the list of unreliable contains the entry with LDAPURL
    * url.
    * It assumes that we previously called containsChildrenOf (there's no check
    * of the host/port).
    * @param url the LDAPURL of the parent.
    * @return <CODE>true</CODE> if the url correspond to an unreliable
    * entry and <CODE>false</CODE> otherwise.
    */
  public boolean contains(LDAPURL url) {
    if (!isUnreliableEntryListEmpty) {
      boolean isInServer =
        serverHost.equalsIgnoreCase(String.valueOf(url.getHost())) &&
      serverPort == url.getPort();
      if (isInServer) {
        for (DN dn : unreliableEntryList)
        {
          try
          {
            if (dn.equals(DN.valueOf(url.getRawBaseDN())))
            {
              return true;
            }
          }
          catch (OpenDsException oe)
          {
            throw new RuntimeException("Error decoding DN of url: "+
                url);
          }
        }
      }
    }
    return false;
  }

  /**
   * This method construct a list with the entries the entries that are parents
   * of the suffix entries.  This list is needed to overpass the fact that
   * numsubordinates does not work between databases.
   * @param allSuffixes a collection with all the suffixes.
   * @param rootSuffixes a collection with the root suffixes.
   * @param serverHost the name of the host where the server is installed.
   * @param serverPort the LDAP(s) port of the server.
   */
  public void update(Collection<DN> allSuffixes,
      Collection<DN> rootSuffixes,
      String serverHost,
      int serverPort)
  {
    allSuffixes.removeAll(rootSuffixes);
    Collection<DN> subSuffixes = allSuffixes;
    synchronized (unreliableEntryList) {
      unreliableEntryList.clear();

      for (DN subSuffixDN : subSuffixes) {
        unreliableEntryList.add(subSuffixDN.parent());
      }
      isUnreliableEntryListEmpty = unreliableEntryList.isEmpty();
    }
    this.serverHost = serverHost;
    this.serverPort = serverPort;
  }
}
