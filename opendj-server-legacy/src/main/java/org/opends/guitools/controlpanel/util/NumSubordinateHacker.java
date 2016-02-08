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
package org.opends.guitools.controlpanel.util;

import java.util.ArrayList;
import java.util.Collection;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.LDAPURL;

/** Class used to handle the case where numsubordinates does not work between databases. */
public class NumSubordinateHacker {
  private String serverHost = "not-initialized";
  private int serverPort = -1;
  private final ArrayList<DN> unreliableEntryList = new ArrayList<>();
  private boolean isUnreliableEntryListEmpty;

  /**
    * Tells whether the list of unreliable contains children of
    * the entry with LDAPUrl parentUrl.
    * @param parentUrl the LDAPURL of the parent.
    * @return <CODE>true</CODE> if the list of unreliable entries contains a
    * children of the parentUrl.  Returns <CODE>false</CODE> otherwise.
    */
  public boolean containsChildrenOf(LDAPURL parentUrl)
  {
    return contains(parentUrl);
  }

  /**
    * Tells whether the list of unreliable contains the entry with LDAPURL url.
    * It assumes that we previously called containsChildrenOf (there's no check
    * of the host/port).
    * @param url the LDAPURL of the parent.
    * @return <CODE>true</CODE> if the url correspond to an unreliable
    * entry and <CODE>false</CODE> otherwise.
    */
  public boolean contains(LDAPURL url) {
    if (!isUnreliableEntryListEmpty) {
      boolean isInServer = serverHost.equalsIgnoreCase(url.getHost()) && serverPort == url.getPort();
      if (isInServer) {
        return unreliableEntryList.contains(DN.valueOf(url.getRawBaseDN()));
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
