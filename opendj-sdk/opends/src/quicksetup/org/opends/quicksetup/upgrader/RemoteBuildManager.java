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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.Application;

import java.net.URL;
import java.net.Proxy;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;
import java.io.*;

/**
 * Manages listing and retreival of build packages on a remote host.
 */
public class RemoteBuildManager {

  static private final Logger LOG =
          Logger.getLogger(RemoteBuildManager.class.getName());

  /**
   * Describes build types.
   */
  enum BuildType {

    NIGHTLY,

    WEEKLY

  }

  /**
   * Representation of an OpenDS build package.
   */
  public class Build {

    private URL url;
    private String id;

    /**
     * Creates an instance.
     * @param url where the build package can be accessed
     * @param id of the new build
     */
    Build(URL url, String id) {
      this.url = url;
      this.id = id;
    }

    /**
     * Gets the URL where the build can be accessed.
     * @return URL representing access to the build package
     */
    public URL getUrl() {
      return url;
    }

    /**
     * Gets the builds ID number, a 14 digit number representing the time
     * the build was created.
     * @return String represenging the build
     */
    public String getId() {
      return id;
    }

  }

  private Application app;

  private Proxy proxy;

  private URL url;

  /**
   * Creates an instance.
   * @param app using this tool
   * @param url base context for an OpenDS build repository
   */
  public RemoteBuildManager(Application app, URL url) {
    this.app = app;
    this.url = url;
  }

  /**
   * Gets a list of builds found in the remote repository.
   * @return List of Build objects representing remote builds
   * @throws IOException if there was a problem contacting the build
   * repository
   */
  public List<Build> listBuilds() throws IOException {
    List<Build> buildList = new ArrayList<Build>();
    String dailyBuildsPage = getDailyBuildsPage();
    Pattern p = Pattern.compile("\\d{14}");
    Matcher m = p.matcher(dailyBuildsPage);
    Set<String> buildIds = new HashSet<String>();
    while (m.find()) {
      buildIds.add(dailyBuildsPage.substring(m.start(), m.end()));
    }
    for (String buildId : buildIds) {
      buildList.add(new Build(url, buildId));
    }
    return buildList;
  }

  private String getDailyBuildsPage() throws IOException {
    URL dailyBuildsUrl = new URL(url, "daily-builds");
    URLConnection conn;
    if (proxy == null) {
      conn = dailyBuildsUrl.openConnection();
    } else {
      conn = dailyBuildsUrl.openConnection(proxy);
    }
    InputStream in = conn.getInputStream();
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    StringBuilder builder = new StringBuilder();
    String line;
    while (null != (line = reader.readLine())) {
      builder.append(line);
    }
    return builder.toString();
  }

  /**
   * Downloads a particular build from the build repository to a specific
   * location on the local file system.
   * @param build to download
   * @param destination directory for the newly downloaded file
   */
  public void download(Build build, File destination) {

  }

  /**
   * For testing only.
   * @param args command line arguments
   */
  public static void main(String[] args) {
    try {
      Properties systemSettings = System.getProperties();
      systemSettings.put("http.proxyHost", "webcache.central.sun.com");
      systemSettings.put("http.proxyPort", "8080");
      System.setProperties(systemSettings);
      URL url = new URL("http://builds.opends.org");
      RemoteBuildManager rbm = new RemoteBuildManager(null, url);
      List<Build> builds = rbm.listBuilds();
      for (Build build : builds) {
        System.out.println("build " + build.getId());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}
