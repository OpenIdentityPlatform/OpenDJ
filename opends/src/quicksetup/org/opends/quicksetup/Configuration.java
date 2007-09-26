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

import org.opends.quicksetup.util.Utils;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Represents the contents of an OpenDS configuration file.
 */
public class Configuration {

  static private final Logger LOG =
          Logger.getLogger(Configuration.class.getName());

  private String contents = null;
  private Installation install = null;
  private File file = null;

  /**
   * Create a Configuration from a file.
   * @param install of which this configuration is part
   * @param file config.ldif file
   */
  public Configuration(Installation install, File file) {
    if (install == null) {
      throw new NullPointerException("config file cannot be null");
    }
    if (file == null) {
      throw new NullPointerException("config file cannot be null");
    } else if (
            // Leave open the possibility that the file might be
            // config.ldif.<svn rev>
            !file.getName().startsWith("config.ldif")) {
      throw new IllegalArgumentException("file must be a config.ldif file");
    }
    this.install = install;
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
    return getLDAPPort("ds-cfg-listen-port");
  }

  /**
   * Tells whether this server is configured as a replication server or not.
   * @return <CODE>true</CODE> if the server is configured as a Replication
   * Server and <CODE>false</CODE> otherwise.
   * @throws IOException if there were problems reading the information from
   * the configuration file.
   */
  public boolean isReplicationServer() throws IOException
  {
    return getReplicationPort() != -1;
  }

  /**
   * Provides the Replication port as is specified in the config.ldif file.
   * Returns -1 if this server is not a Replication Server.
   *
   * @return the Replication port specified in the config.ldif file.
   * @throws IOException if there were problems reading the information from
   * the configuration file.
   */
  public int getReplicationPort() throws IOException {
    int port = -1;
    String contents = getContents();
    int index = contents.indexOf("cn=replication server");

    if (index != -1) {
      String attrWithPoints = "ds-cfg-replication-port:";
      int index1 = contents.indexOf(attrWithPoints, index);
      if (index1 != -1) {
        int index2 =
                contents.indexOf(Constants.LINE_SEPARATOR, index1);
        if (index2 != -1) {
          String sPort =
                  contents.substring(attrWithPoints.length() +
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

  private int getLDAPPort(String portAttr) throws IOException {
    int port = -1;
    String contents = getContents();
    int index = contents.indexOf("cn=ldap connection handler");

    if (index != -1) {
      String attrWithPoints = portAttr + ":";
      int index1 = contents.indexOf(attrWithPoints, index);
      if (index1 != -1) {
        int index2 =
                contents.indexOf(Constants.LINE_SEPARATOR, index1);
        if (index2 != -1) {
          String sPort =
                  contents.substring(attrWithPoints.length() +
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
   * Returns a Set of relative paths containing the log paths outside the
   * installation.
   * @return a Set of relative paths containing the log paths outside the
   * installation.
   * @throws IOException if there is trouble reading the config file
   */
  public Set<String> getOutsideLogs()
          throws IOException
  {
    return getOutsidePaths(getLogPaths());
  }

  /**
   * Returns a Set of relative paths containing the db paths outside the
   * installation.
   * @return a Set of relative paths containing the db paths outside the
   * installation.
   * @throws IOException if there is trouble reading the config file
   */
  public Set<String> getOutsideDbs()
          throws IOException
  {
    return getOutsidePaths(getDatabasePaths());
  }

  private Set<String> getOutsidePaths(Set<String> paths) {
    Set<String> outsidePaths = new HashSet<String>();
    for (String path : paths) {
      File fullDbPath;
      File pathFile = new File(path);
      if (pathFile.isAbsolute()) {
        fullDbPath = pathFile;
      } else {
        fullDbPath = new File(install.getRootDirectory(), path);
      }

      if (!Utils.isDescendant(fullDbPath, install.getRootDirectory())) {
        outsidePaths.add(Utils.getPath(fullDbPath));
      }
    }
    return outsidePaths;
  }

  /**
   * Provides the contents of the config.ldif file in a String.
   *
   * @return a String representing the contents of the config.ldif file.
   * @throws IOException if there was a problem reading the file
   */
  public String getContents() throws IOException {
    if (contents == null) {
      load();
    }
    return contents;
  }

  /**
   * Returns the list of paths where the databases are installed as they appear
   * in the configuration file.
   *
   * @return the list of paths where the databases are installed as they appear
   * in the configuration file.
   * @throws IOException if there is a problem reading the config file.
   */
  public Set<String> getDatabasePaths() throws IOException {
    return getConfigurationValues("ds-cfg-db-directory");
  }

  /**
   * Returns the list of base dns as they appear in the configuration file.
   *
   * @return the list of base dns as they appear in the configuration file.
   * @throws IOException if there is a problem reading the config file.
   */
  public Set<String> getBaseDNs() throws IOException {
    return getConfigurationValues("ds-cfg-base-dn");
  }

  /**
   * Loads the contents of the configuration file into memory.
   * @throws IOException if there were problems loading the file
   */
  public void load() throws IOException {
    StringBuilder buf = new StringBuilder();
    FileReader reader = new FileReader(file);
    BufferedReader in = new BufferedReader(reader);
    String line;
    // We do not care about encoding: we are just interested in the ports
    while ((line = in.readLine()) != null) {
      buf.append(line).append(Constants.LINE_SEPARATOR);
    }
    reader.close();
    contents = buf.toString().toLowerCase();
  }

  private Set<String> getConfigurationValues(String attrName)
          throws IOException
  {
    Set<String> set = new HashSet<String>();
    attrName += ":";
    String contents = getContents();
    int index1 = contents.indexOf(attrName);
    while (index1 != -1) {
      int index2 = contents.indexOf(Constants.LINE_SEPARATOR, index1);
      String value;
      if (index2 > (index1 + attrName.length())) {
        value = contents.substring(attrName.length() + index1,
                index2).trim();
      } else if (contents.length() > (index1 + attrName.length())) {
        // Assume end of file
        value = contents.substring(
                attrName.length() + index1).trim();
      } else {
        value = null;
      }

      if ((value != null) && (value.length() > 0)) {
        set.add(value);
      }

      index1 = contents.indexOf(attrName,
              index1 + attrName.length());
    }
    return set;
  }

}
