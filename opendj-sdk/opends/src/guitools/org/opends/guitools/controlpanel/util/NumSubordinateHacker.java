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

import java.util.ArrayList;
import java.util.Collection;

import org.opends.server.types.DN;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.OpenDsException;

/**
 * Class used to handle the case where numsubordinates does not work between
 * databases.
 *
 */
public class NumSubordinateHacker {
  String serverHost;
  int serverPort;
  ArrayList<DN> unreliableEntryList;
  boolean isUnreliableEntryListEmpty;

  /**
   * Default constructor.
   *
   */
  public NumSubordinateHacker() {
    serverHost = "not-initialized";
    serverPort = -1;
    unreliableEntryList = new ArrayList<DN>();
  }

  /**
    * Tells wether the list of unreliable contains children of
    * the entry with LDAPUrl parentUrl.
    * @param parentUrl the LDAPURL of the parent.
    * @return <CODE>true</CODE> if the list of unreliable entries contains a
    * children of the parentUrl.  Returns <CODE>false</CODE> otherwise.
    */
  public boolean containsChildrenOf(LDAPURL parentUrl) {
    boolean containsChildren = false;

    if (!isUnreliableEntryListEmpty) {
      boolean isInServer = serverHost.equals(parentUrl.getHost()) &&
      (serverPort == parentUrl.getPort());

      if (isInServer) {
        for (DN dn : unreliableEntryList)
        {
          try
          {
            if (dn.equals(DN.decode(parentUrl.getRawBaseDN())))
            {
              containsChildren = true;
              break;
            }
          }
          catch (OpenDsException oe)
          {
            throw new IllegalStateException("Error decoding DN of url: "+
                parentUrl);
          }
        }
      }
    }
    return containsChildren;
  }

  /**
    * Tells wether the list of unreliable contains the entry with LDAPURL
    * url.
    * It assumes that we previously called containsChildrenOf (there's no check
    * of the host/port).
    * @param url the LDAPURL of the parent.
    * @return <CODE>true</CODE> if the url correspond to an unreliable
    * entry and <CODE>false</CODE> otherwise.
    */
  public boolean contains(LDAPURL url) {
    boolean contains = false;
    if (!isUnreliableEntryListEmpty) {
      boolean isInServer = serverHost.equals(url.getHost()) &&
      (serverPort == url.getPort());

      if (isInServer) {
        for (DN dn : unreliableEntryList)
        {
          try
          {
            if (dn.equals(DN.decode(url.getRawBaseDN())))
            {
              contains = true;
              break;
            }
          }
          catch (OpenDsException oe)
          {
            throw new IllegalStateException("Error decoding DN of url: "+
                url);
          }
        }
      }
    }
    return contains;
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
        unreliableEntryList.add(subSuffixDN.getParent());
      }
      isUnreliableEntryListEmpty = unreliableEntryList.isEmpty();
    }
    this.serverHost = serverHost;
    this.serverPort = serverPort;
  }
}
