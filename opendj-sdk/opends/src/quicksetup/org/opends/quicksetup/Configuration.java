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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

/**
 * Represents the contents of an OpenDS configuration file.
 */
public class Configuration {

  private String contents = null;

  private File file = null;

  /**
   * Create a Configuration from a file.
   * @param file config.ldif file
   */
  public Configuration(File file) {
    if (file == null) {
      throw new NullPointerException("config file cannot be null");
    } else if (
            // Leave open the possibility that the file might be
            // config.ldif.<svn rev>
            !file.getName().startsWith("config.ldif")) {
      throw new IllegalArgumentException("file must be a config.ldif file");
    }
    this.file = file;
  }

  /**
   * Returns the list of directory manager dns as they appear in the
   * configuration file.
   *
   * @return the list of directory manager dns as they appear in the
   *         configuration file.
   * @throws IOException if there were problems reading the information from
   * the configuration file.
   */
  public Set<String> getDirectoryManagerDns() throws IOException {
    return getConfigurationValues("ds-cfg-alternate-bind-dn");
  }

  /**
   * Provides the LDAP port as is specified in the config.ldif file.
   *
   * @return the LDAP port specified in the config.ldif file.
   * @throws IOException if there were problems reading the information from
   * the configuration file.
   */
  public int getPort() throws IOException {
    return getPort("ds-cfg-listen-port");
  }

  /**
   * Provides the LDAP secure port as is specified in the config.ldif file.
   *
   * @return the LDAP secure port specified in the config.ldif file.
   * @throws IOException if there were problems reading the information from
   * the configuration file.
   */
  public int getSecurePort() throws IOException {
    // TODO find out which is the attribute for this port.
    return getPort("ds-cfg-listen-secure-port");
  }

  /**
   * Returns the list of paths where the logs files are located as they appear
   * in the configuration file.
   *
   * @return the list of paths where the logs files are located as they appear
   *         in the configuration file.
   * @throws IOException if there were problems reading the information from
   * the configuration file.
   */
  public Set<String> getLogPaths() throws IOException {
    return getConfigurationValues("ds-cfg-log-file");
  }

  private int getPort(String portAttr) throws IOException {
    int port = -1;

    int index = getContents().indexOf("cn=ldap connection handler");

    if (index != -1) {
      String attrWithPoints = portAttr + ":";
      int index1 = getContents().indexOf(attrWithPoints, index);
      if (index1 != -1) {
        int index2 =
                getContents().indexOf(
                        System.getProperty("line.separator"), index1);
        if (index2 != -1) {
          String sPort =
                  getContents().substring(attrWithPoints.length() +
                          index1,
                          index2).trim();
          try {
            port = Integer.parseInt(sPort);
          } catch (NumberFormatException nfe) {
            // do nothing;
          }
        }
      }
    }
    return port;
  }

  /**
   * Indicates whether the config.ldif file has been modified (compared to what
   * we had in the zip file). This is used to know if we have configured the
   * current binaries or not.
   *
   * @return <CODE>true</CODE> if the config.ldif file has been modified, or
   *         <CODE>false</CODE> if not.
   * @throws IOException if there were problems reading the information from
   * the configuration file.
   */
  public boolean hasBeenModified() throws IOException {
    boolean isConfigFileModified = getPort() != 389;

    if (!isConfigFileModified) {
      // TODO: this is not really stable
      // Note: a better way might be to diff this file with
      // /config/ldif/upgrade/config.ldif.<svn rev>
      isConfigFileModified =
              getContents().indexOf("# cddl header start") == -1;
    }

    return isConfigFileModified;
  }

  /**
   * Provides the contents of the config.ldif file in a String.
   *
   * @return a String representing the contents of the config.ldif file.
   * @throws IOException if there was a problem reading the file
   */
  private String getContents() throws IOException {
    if (contents == null) {
      load();
    }
    return contents;
  }

  /**
   * Loads the contents of the configuration file into memory.
   * @throws IOException if there were problems loading the file
   */
  public void load() throws IOException {
    StringBuffer buf = new StringBuffer();
    FileReader reader = new FileReader(file);
    BufferedReader in = new BufferedReader(reader);
    String line;
    // We do not care about encoding: we are just interested in the ports
    while ((line = in.readLine()) != null) {
      buf.append(line).append(System.getProperty("line.separator"));
    }
    reader.close();
    contents = buf.toString().toLowerCase();
  }

  private Set<String> getConfigurationValues(String attrName)
          throws IOException
  {
    Set<String> set = new HashSet<String>();
    attrName += ":";
    int index1 = getContents().indexOf(attrName);
    while (index1 != -1) {
      int index2 = getContents().indexOf(
              System.getProperty("line.separator"), index1);
      String value;
      if (index2 > (index1 + attrName.length())) {
        value = getContents().substring(attrName.length() + index1,
                index2).trim();
      } else if (getContents().length() > (index1 + attrName.length())) {
        // Assume end of file
        value = getContents().substring(
                attrName.length() + index1).trim();
      } else {
        value = null;
      }

      if ((value != null) && (value.length() > 0)) {
        set.add(value);
      }

      index1 = getContents().indexOf(attrName,
              index1 + attrName.length());
    }
    return set;
  }

}
