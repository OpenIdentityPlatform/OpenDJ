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

package org.opends.quicksetup;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.server.util.SetupUtils.*;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.SetupUtils;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents information about the current build that is
 * publicly obtainable by invoking start-ds -F.
 */
public class BuildInformation implements Comparable {

  static private final Logger LOG =
          Logger.getLogger(BuildInformation.class.getName());

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
    args.add(Utils.getScriptPath(
        Utils.getPath(installation.getServerStartCommandFile())));
    args.add("-F"); // full verbose
    ProcessBuilder pb = new ProcessBuilder(args);
    InputStream is = null;
    OutputStream out = null;
    final boolean[] done = {false};
    try {
      Map<String, String> env = pb.environment();
      env.put(SetupUtils.OPENDS_JAVA_HOME, System.getProperty("java.home"));
      // This is required in order the return code to be valid.
      env.put("OPENDS_EXIT_NO_BACKGROUND", "true");
      final Process process = pb.start();
      is = process.getInputStream();
      out = process.getOutputStream();
      final OutputStream fOut = out;
      if (Utils.isWindows())
      {
        // In windows if there is an error we wait the user to click on
        // return to continue.
        Thread t = new Thread(new Runnable()
        {
          public void run()
          {
            while (!done[0])
            {
              try
              {
                Thread.sleep(15000);
                if (!done[0])
                {
                  fOut.write(Constants.LINE_SEPARATOR.getBytes());
                  fOut.flush();
                }
              }
              catch (Throwable t)
              {
                LOG.log(Level.WARNING, "Error writing to process: "+t, t);
              }
            }
          }
        });
        t.start();
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      String line = reader.readLine();
      bi.values.put(NAME, line);
      StringBuilder sb = new StringBuilder();
      while (null != (line = reader.readLine())) {
        if (sb.length() > 0)
        {
          sb.append('\n');
        }
        sb.append(line);
        int colonIndex = line.indexOf(':');
        if (-1 != colonIndex) {
          String name = line.substring(0, colonIndex).trim();
          String value = line.substring(colonIndex + 1).trim();
          bi.values.put(name, value);
        }
      }
      int resultCode = process.waitFor();
      if (resultCode != 0)
      {
        if (sb.length() == 0)
        {
          throw new ApplicationException(
            ReturnCode.START_ERROR,
            INFO_ERROR_CREATING_BUILD_INFO.get(), null);
        }
        else
        {
          try
          {
            checkNotNull(bi.values,
                NAME,
                MAJOR_VERSION,
                MINOR_VERSION,
                POINT_VERSION,
                REVISION_NUMBER);
          }
          catch (Throwable t)
          {
            // We did not get the required information.
            throw new ApplicationException(
                ReturnCode.START_ERROR,
                INFO_ERROR_CREATING_BUILD_INFO_MSG.get(sb.toString()),
                null);
          }
        }
      }
    } catch (IOException e) {
      throw new ApplicationException(
          ReturnCode.START_ERROR,
          INFO_ERROR_CREATING_BUILD_INFO.get(), e);

    } catch (InterruptedException ie) {
      throw new ApplicationException(
          ReturnCode.START_ERROR,
          INFO_ERROR_CREATING_BUILD_INFO.get(), ie);
    } finally {
      done[0] = true;
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
          // ignore;
        }
      }
      if (out != null) {
        try {
          out.close();
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
   * Creates an instance from a string representing a build number
   * of the for MAJOR.MINOR.POINT.REVISION where MAJOR, MINOR, POINT,
   * and REVISION are integers.
   * @param bn String representation of a build number
   * @return a BuildInformation object populated with the information
   * provided in <code>bn</code>
   * @throws IllegalArgumentException if <code>bn</code> is not a build
   * number
   */
  static public BuildInformation fromBuildString(String bn) throws
    IllegalArgumentException
  {
    // -------------------------------------------------------
    // NOTE:  if you change this be sure to change getBuildString()
    // -------------------------------------------------------
    Pattern p = Pattern.compile("((\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+))");
    Matcher m = p.matcher(bn);
    if (!m.matches()) {
      throw new IllegalArgumentException("'" + bn + "' is not a build string");
    }
    BuildInformation bi = new BuildInformation();
    try {
      bi.values.put(MAJOR_VERSION, m.group(2));
      bi.values.put(MINOR_VERSION, m.group(3));
      bi.values.put(POINT_VERSION, m.group(4));
      bi.values.put(REVISION_NUMBER, m.group(5));
    } catch (Exception e) {
      throw new IllegalArgumentException("Error parsing build number " + bn);
    }
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
    bi.values.put(VERSION_QUALIFIER,
            String.valueOf(DynamicConstants.VERSION_QUALIFIER));
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
   * Gets the version qualifier.
   *
   * @return String reprenting the version qualifier
   */
  public String getVersionQualifier() {
    return values.get(VERSION_QUALIFIER);
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
   * Gets the set of IDs representing <code>IncompatibleVersionEvents</code>.
   * @return set of integers representing events
   * @see org.opends.server.util.VersionCompatibilityIssue
   */
  public Set<Integer> getIncompatibilityEventIds() {
    HashSet<Integer> ids = null;
    String idString = values.get(INCOMPATIBILITY_EVENTS);
    if (idString != null) {
      ids = new HashSet<Integer>();
      String[] sa = idString.split(",");
      for (String s : sa) {
        try {
          ids.add(Integer.parseInt(s));
        } catch (NumberFormatException nfe) {
          LOG.log(Level.INFO, "invalid upgrade incompatibility ID " + s);
        }
      }
    }
    return ids;
  }

  /**
   * Returns a build string representation of this object.  A build
   * number is a string formatted MAJOR.MINOR.POINT.REVISION where
   * MAJOR, MINOR, POINT and REVISION are integers.
   * @return String representation of a build number
   */
  public String getBuildString() {
    // -------------------------------------------------------
    // NOTE:  if you change this be sure to change fromBuildString()
    // -------------------------------------------------------
    StringBuilder sb = new StringBuilder();
    sb.append(getMajorVersion());
    sb.append(".");
    sb.append(getMinorVersion());
    sb.append(".");
    sb.append(getPointVersion());
    sb.append(".");
    sb.append(getRevisionNumber());
    return sb.toString();
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
              .append(INFO_GENERAL_BUILD_ID.get())
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
    return this == o ||
            !(o == null || getClass() != o.getClass()) &&
                    compareTo(o) == 0;
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
        throw new ApplicationException(
                ReturnCode.TOOL_ERROR,
                INFO_ERROR_PROP_VALUE.get(prop), null);
      }
    }
  }

}
