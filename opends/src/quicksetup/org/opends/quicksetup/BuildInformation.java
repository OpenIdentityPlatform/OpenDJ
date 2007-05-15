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
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.server.util.DynamicConstants;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Represents information about the current build that is
 * publicly obtainable by invoking start-ds -F.
 */
public class BuildInformation implements Comparable {

  // These string values must be synchronized with Directory
  // Server's main method.  These string values are considered
  // stable by the server team and not candidates for
  // internationalization.
  static private final String NAME = "Name";
  static private final String BUILD_ID = "Build ID";
  static private final String MAJOR_VERSION = "Major Version";
  static private final String MINOR_VERSION = "Minor Version";
  static private final String POINT_VERSION = "Point Version";
  static private final String REVISION_NUMBER = "Revision Number";
  static private final String FIX_IDS = "Fix IDs";
  static private final String DEBUG_BUILD = "Debug Build";
  static private final String BUILD_OS = "Build OS";
  static private final String BUILD_USER = "Build User";
  static private final String BUILD_JAVA_VERSION = "Build Java Version";
  static private final String BUILD_JAVA_VENDOR = "Build Java Vendor";
  static private final String BUILD_JVM_VERSION = "Build JVM Version";
  static private final String BUILD_JVM_VENDOR = "Build JVM Vendor";


  /**
   * Reads build information for a particular installation by reading the
   * output from invoking the start-ds tool with the full information option.
   * @param installation from which to gather build information
   * @return BuildInformation object populated with information
   * @throws ApplicationException if all or some important information could
   * not be determined
   */
  static public BuildInformation create(Installation installation)
          throws ApplicationException {
    BuildInformation bi = new BuildInformation();
    List<String> args = new ArrayList<String>();
    args.add(Utils.getPath(installation.getServerStartCommandFile()));
    args.add("-F"); // full verbose
    ProcessBuilder pb = new ProcessBuilder(args);
    InputStream is = null;
    try {
      Process process = pb.start();
      is = process.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      String line = reader.readLine();
      bi.values.put(NAME, line);
      while (null != (line = reader.readLine())) {
        int colonIndex = line.indexOf(':');
        if (-1 != colonIndex) {
          String name = line.substring(0, colonIndex).trim();
          String value = line.substring(colonIndex + 1).trim();
          bi.values.put(name, value);
        }
      }
    } catch (IOException e) {
      throw new ApplicationException(ApplicationException.Type.START_ERROR,
              "Error creating build info", e);
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
          // ignore;
        }
      }
    }

    // Make sure we got values for important properties that are used
    // in compareTo, equals, and hashCode
    checkNotNull(bi.values,
            NAME,
            MAJOR_VERSION,
            MINOR_VERSION,
            POINT_VERSION,
            REVISION_NUMBER);

    return bi;
  }

  /**
   * Creates an instance from constants present in the current build.
   * @return BuildInformation created from current constant values
   * @throws ApplicationException if all or some important information could
   * not be determined
   */
  public static BuildInformation getCurrent() throws ApplicationException {
    BuildInformation bi = new BuildInformation();
    bi.values.put(NAME, DynamicConstants.FULL_VERSION_STRING);
    bi.values.put(BUILD_ID, DynamicConstants.BUILD_ID);
    bi.values.put(MAJOR_VERSION,
            String.valueOf(DynamicConstants.MAJOR_VERSION));
    bi.values.put(MINOR_VERSION,
            String.valueOf(DynamicConstants.MINOR_VERSION));
    bi.values.put(POINT_VERSION,
            String.valueOf(DynamicConstants.POINT_VERSION));
    bi.values.put(REVISION_NUMBER,
            String.valueOf(DynamicConstants.REVISION_NUMBER));
    bi.values.put(FIX_IDS, DynamicConstants.FIX_IDS);
    bi.values.put(DEBUG_BUILD, String.valueOf(DynamicConstants.DEBUG_BUILD));
    bi.values.put(BUILD_OS, DynamicConstants.BUILD_OS);
    bi.values.put(BUILD_USER, DynamicConstants.BUILD_USER);
    bi.values.put(BUILD_JAVA_VERSION, DynamicConstants.BUILD_JAVA_VERSION);
    bi.values.put(BUILD_JAVA_VENDOR, DynamicConstants.BUILD_JAVA_VENDOR);
    bi.values.put(BUILD_JVM_VERSION, DynamicConstants.BUILD_JVM_VERSION);
    bi.values.put(BUILD_JVM_VENDOR, DynamicConstants.BUILD_JVM_VENDOR);

    // Make sure we got values for important properties that are used
    // in compareTo, equals, and hashCode
    checkNotNull(bi.values,
            NAME,
            MAJOR_VERSION,
            MINOR_VERSION,
            POINT_VERSION,
            REVISION_NUMBER);

    return bi;
  }

  private Map<String, String> values = new HashMap<String, String>();

  /**
   * Gets the name of this build.  This is the first line of the output
   * from invoking start-ds -F.
   * @return String representing the name of the build
   */
  public String getName() {
    return values.get(NAME);
  }

  /**
   * Gets the build ID which is the 14 digit number code like 20070420110336.
   *
   * @return String representing the build ID
   */
  public String getBuildId() {
    return values.get(BUILD_ID);
  }

  /**
   * Gets the major version.
   *
   * @return String representing the major version
   */
  public Integer getMajorVersion() {
    return new Integer(values.get(MAJOR_VERSION));
  }

  /**
   * Gets the minor version.
   *
   * @return String representing the minor version
   */
  public Integer getMinorVersion() {
    return new Integer(values.get(MINOR_VERSION));
  }

  /**
   * Gets the point version.
   *
   * @return String representing the point version
   */
  public Integer getPointVersion() {
    return new Integer(values.get(POINT_VERSION));
  }

  /**
   * Gets the SVN revision number.
   *
   * @return Integer representing the SVN revision number
   */
  public Integer getRevisionNumber() {
    return new Integer(values.get(REVISION_NUMBER));
  }

  /**
   * {@inheritDoc}
   */
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getName());
    String id = getBuildId();
    if (id != null) {
      sb.append(" (")
              .append(ResourceProvider.getInstance().
                      getMsg("general-build-id"))
              .append(": ")
              .append(id)
              .append(")");
    }
    return sb.toString();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(Object o) {
    BuildInformation bi = (BuildInformation) o;
    if (getMajorVersion().equals(bi.getMajorVersion())) {
      if (getMinorVersion().equals(bi.getMinorVersion())) {
        if (getPointVersion().equals(bi.getPointVersion())) {
          if (getRevisionNumber().equals(bi.getRevisionNumber())) {
            return 0;
          } else if (getRevisionNumber() < bi.getRevisionNumber()) {
            return -1;
          }
        } else if (getPointVersion() < bi.getPointVersion()) {
          return -1;
        }
      } else if (getMinorVersion() < bi.getMinorVersion()) {
        return -1;
      }
    } else if (getMajorVersion() < bi.getMajorVersion()) {
      return -1;
    }
    return 1;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return compareTo(o) == 0;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode() {
    int hc = 11;
    hc = 31 * hc + getMajorVersion().hashCode();
    hc = 31 * hc + getMinorVersion().hashCode();
    hc = 31 * hc + getPointVersion().hashCode();
    hc = 31 * hc + getRevisionNumber().hashCode();
    return hc;
  }

  static private void checkNotNull(Map values, String... props)
          throws ApplicationException {
    for (String prop : props) {
      if (null == values.get(prop)) {
        throw new ApplicationException(ApplicationException.Type.TOOL_ERROR,
                "'" + prop + "' could not be determined", null);
      }
    }

  }

}
