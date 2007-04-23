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
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.DynamicConstants;

import javax.swing.*;
import java.net.URL;
import java.net.Proxy;
import java.net.URLConnection;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;
import java.io.*;
import java.awt.*;

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

    /**
     * Nightly build descriptor.
     */
    NIGHTLY,

    /**
     * Weekly build descriptor.
     */
    WEEKLY

  }

  private Application app;

  private URL url;

  private Proxy proxy;

  private String proxyUserName;

  private char[] proxyPw;

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
   * Gets the base context where the build information is stored.
   * @return URL representing base context of the build repo
   */
  public URL getBaseContext() {
    return this.url;
  }

  /**
   * Gets a list of builds found in the remote repository.
   * @return List of Build objects representing remote builds
   * @throws IOException if there was a problem contacting the build
   * repository
   */
  public List<Build> listBuilds() throws IOException {
    return listBuilds(null, null);
  }

  /**
   * Gets the list of builds from the build repository using a
   * progress monitor to keep the user informed about the status
   * of downloading the build page.
   * @param c Component to act as parent of the progress monitor
   * @param o message to display in the progress monitor
   * @return list of Build objects
   * @throws IOException if something goes wrong loading the list
   * from the build repository
   */
  public List<Build> listBuilds(Component c, Object o) throws IOException {
    List<Build> buildList = new ArrayList<Build>();
    String dailyBuildsPage = downloadDailyBuildsPage(c, o);
    Pattern p = Pattern.compile("\\d{14}");
    Matcher m = p.matcher(dailyBuildsPage);
    Set<String> buildIds = new HashSet<String>();
    while (m.find()) {
      buildIds.add(dailyBuildsPage.substring(m.start(), m.end()));
    }

//    for (String buildId : buildIds) {
//      // TODO:  this needs to be changed
//      URL buildUrl =
//              new URL(url, "daily-builds/" +
//                      buildId +
//                      "/OpenDS/build/package/OpenDS-0.1.zip");
//      buildList.add(new Build(url, buildId));
//    }

    // This is encoded in build.xml.  We might need a more dynamic
    // way of getting this information.
    StringBuilder latestContextSb = new StringBuilder()
            .append("daily-builds/latest/OpenDS/build/package/OpenDS-")
            .append(DynamicConstants.MAJOR_VERSION)
            .append(".")
            .append(DynamicConstants.MINOR_VERSION)
            .append(DynamicConstants.VERSION_QUALIFIER)
            .append(".zip");
    Build latest = new Build(new URL(url, latestContextSb.toString()),
                            "Latest");
    buildList.add(latest);
    Collections.sort(buildList);
    return buildList;
  }

  private String downloadDailyBuildsPage(Component c, Object o)
          throws IOException
  {
    URL dailyBuildsUrl = new URL(url, "daily-builds");
    URLConnection conn;
    if (proxy == null) {
      conn = dailyBuildsUrl.openConnection();
    } else {
      conn = dailyBuildsUrl.openConnection(proxy);
    }
    String proxyAuthString = createProxyAuthString();
    if (proxyAuthString != null) {
      conn.setRequestProperty("Proxy-Authorization", "Basic " +
              proxyAuthString);
    }
    InputStream in;
    if (c != null) {
      ProgressMonitorInputStream pmis =
              new ProgressMonitorInputStream(c, o, conn.getInputStream());
      ProgressMonitor pm = pmis.getProgressMonitor();
      pm.setMillisToDecideToPopup(0);
      in = pmis;
    } else {
      in = conn.getInputStream();
    }
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
   * @throws IOException if the build could not be downloaded
   */
  public void download(Build build, File destination) throws IOException {
    download(build.getUrl(), destination);
  }

  private void download(URL url, File destination) throws IOException {
    URLConnection conn = null;
    if (proxy == null) {
      conn = url.openConnection();
    } else {
      conn = url.openConnection(proxy);
    }
    String proxyAuthString = createProxyAuthString();
    if (proxyAuthString != null) {
      conn.setRequestProperty("Proxy-Authorization", "Basic " +
              proxyAuthString);
    }
    InputStream is = null;
    FileOutputStream fos = null;

    // If the destination already exists blow it away, then
    // create the new file.
    if (destination.exists()) {
      if (!destination.delete()) {
        throw new IOException("Could not overwrite existing file " +
                Utils.getPath(destination));
      }
    }
    Utils.createFile(destination);

    try {
      is = conn.getInputStream();
      fos = new FileOutputStream(destination);
      int i = 0;
      int bytesRead = 0;
      byte[] buf = new byte[1024];
      while ((i = is.read(buf)) != -1) {
        fos.write(buf, 0, i);
        bytesRead += i;
      }
    } finally {
      if (is != null) {
        is.close();
      }
      if (fos != null) {
        fos.close();
      }
    }
  }

  /**
   * Sets the proxy object this class will use when establishing network
   * connections.
   * @param proxy to use when establishing connections
   */
  public void setProxy(Proxy proxy) {
    this.proxy = proxy;
  }

  /**
   * Gets the proxy object this class uses when establishing network
   * connections.
   * @return Proxy to use when establishing connections
   */
  public Proxy getProxy() {
    return this.proxy;
  }

  /**
   * Sets the user name this class will use to authenticate to its
   * proxy when establishing network connections.
   * @param user this class is acting on behalf of
   */
  public void setProxyUserName(String user) {
    this.proxyUserName = user;
  }

  /**
   * Sets the user name this class will use to authenticate to its
   * proxy when establishing network connections.
   * @return String representing the name of the user of which this class is
   * acting on behalf
   */
  public String getProxyUserName() {
    return this.proxyUserName;
  }

  /**
   * Sets the password this class will use to authenticate to its
   * proxy when establishing network connections.
   * @param pw char[] representing the password of the user of which this class
   * is acting on behalf
   */
  public void setProxyPassword(char[] pw) {
    this.proxyPw = pw;
  }

  /**
   * Sets the password this class will use to authenticate to its
   * proxy when establishing network connections.
   * @return char[] representing the password of the user of which this class is
   * acting on behalf
   */
  public char[] getProxyPassword() {
    return this.proxyPw;
  }

  private String createProxyAuthString() {
    return createAuthenticationString(getProxyUserName(), getProxyPassword());
  }

  static private String createAuthenticationString(String user, char[] pw) {
    String s = null;
    if (user != null && pw != null) {
      StringBuilder sb = new StringBuilder()
              .append(user)
              .append(":")
              .append(pw);
      s = org.opends.server.util.Base64.encode(sb.toString().getBytes());
    }
    return s;
  }

  /**
   * For testing only.
   * @param args command line arguments
   */
//  public static void main(String[] args) {
//    try {
//      Properties systemSettings = System.getProperties();
//      systemSettings.put("http.proxyHost", "webcache.central.sun.com");
//      systemSettings.put("http.proxyPort", "8080");
//      systemSettings.put("https.proxyHost", "webcache.central.sun.com");
//      systemSettings.put("https.proxyPort", "8080");
//
//      System.setProperties(systemSettings);
//
//      URL url = new URL("http://builds.opends.org");
//      RemoteBuildManager rbm = new RemoteBuildManager(null, url);
//      //List<Build> builds = rbm.listBuilds();
//      //for (Build build : builds) {
//      //  System.out.println("build " + build);
//      //}
//      rbm.download(new URL("https://opends.dev.java.net/" +
//              "files/documents/4926/55351/OpenDS-0.1-build035.zip"),
//              new File("/tmp/OpenDS-xxx.zip"));
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//
//  }

}
