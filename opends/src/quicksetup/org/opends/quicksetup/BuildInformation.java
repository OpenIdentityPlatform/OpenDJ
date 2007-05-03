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

  static private final String NAME = "Name";
  static private final String BUILD_ID = "Build ID";
  static private final String MAJOR_VERSION = "Major Version";
  static private final String MINOR_VERSION = "Minor Version";
  static private final String POINT_VERSION = "Point Version";
  static private final String REVISION_NUMBER = "Revision Number";

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
    // Make sure we got values for import properties
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
    return getName() + " rev=" + getRevisionNumber();
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
