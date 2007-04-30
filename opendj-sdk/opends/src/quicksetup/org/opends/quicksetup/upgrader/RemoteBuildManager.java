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

import javax.swing.*;
import java.net.URL;
import java.net.Proxy;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.*;
import java.awt.*;

/**
 * Manages listing and retreival of build packages on a remote host.
 */
public class RemoteBuildManager {

  static private final Logger LOG =
          Logger.getLogger(RemoteBuildManager.class.getName());

  private Application app;

  /**
   * This URL is expected to point at a list of the builds parsable by
   * the <code>RemoteBuildsPageParser</code>.
   */
  private URL buildListUrl;

  private Proxy proxy;

  private String proxyUserName;

  private char[] proxyPw;

  /**
   * Creates an instance.
   * @param app using this tool
   * @param url base context for an OpenDS build list
   * @param proxy Proxy to use for connections; can be null if not proxy is
   * to be used
   */
  public RemoteBuildManager(Application app, URL url, Proxy proxy) {
    this.app = app;
    this.buildListUrl = url;
    this.proxy = proxy;
  }

  /**
   * Gets the base context where the build information is stored.
   * @return URL representing base context of the build repo
   */
  public URL getBaseContext() {
    return this.buildListUrl;
  }

  /**
   * Gets the list of builds from the build repository using a
   * progress monitor to keep the user informed about the status
   * of downloading the build page.
   * @param in InputStream of build information
   * @return list of Build objects
   * @throws IOException if something goes wrong loading the list
   * from the build repository
   */
  public List<Build> listBuilds(InputStream in) throws IOException {
    String dailyBuildsPage = downloadDailyBuildsPage(in);
    return Collections.unmodifiableList(
      RemoteBuildsPageParser.parseBuildList(dailyBuildsPage));
  }

  /**
   * Gets an input stream to download.
   * @param c Component parent
   * @param o Object message to display in the ProgressMonitor
   * @return InputStream for the build list
   * @throws IOException if something goes wrong
   */
  public InputStream getDailyBuildsInputStream(final Component c,
                                               final Object o)
    throws IOException
  {
    URLConnection conn;
    if (proxy == null) {
      conn = buildListUrl.openConnection();
    } else {
      conn = buildListUrl.openConnection(proxy);
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
      pm.setMaximum(conn.getContentLength());
      // pm.setMillisToDecideToPopup(0);
      // pm.setMillisToPopup(0);
      in = pmis;
    } else {
      in = conn.getInputStream();
    }
    return in;
  }

  private String downloadDailyBuildsPage(InputStream in)
          throws IOException
  {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    StringBuilder builder = new StringBuilder();
    String line;
    while (null != (line = reader.readLine())) {
      builder.append(line).append('\n');
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
        if (app != null) {
          app.notifyListeners(".");
        }
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
   * Parser for the web page that lists available builds.  This pag is expected
   * to be a tab-delimited text document where each line represents a build with
   * the following fields:
   * 1.  A build display name (e.g. OpenDS 0.1 Build 036)
   * 2.  A URL where the build's .zip file can be downloaded
   * 3.  A category string for the build (e.g. Weekly Build, Daily Build)
   */
  static private class RemoteBuildsPageParser {

    /**
     * Parses a string representing the build information list into a list
     * of builds sorted by usefulness meaning that release builds are first,
     * followed by weekly builds and finally daily builds.
     * @param page String representing the build info page
     * @return List of Builds
     */
    static public List<Build> parseBuildList(String page) {
      List<Build> builds = new ArrayList<Build>();
      if (page != null) {
        BufferedReader reader = new BufferedReader(new StringReader(page));
        String line;
        try {
          while (null != (line = reader.readLine())) {
            try {
              Build build = parseBuildLine(line);
              builds.add(build);
            } catch (IllegalArgumentException iae) {
              StringBuffer msg = new StringBuffer()
                      .append("Error parsing line '")
                      .append(line)
                      .append("': ")
                      .append(iae.getMessage());
              LOG.log(Level.INFO, msg.toString());
            }
          }
        } catch (IOException e) {
          LOG.log(Level.INFO, "error", e);
        }
      } else {
        LOG.log(Level.WARNING, "build list page is null");
      }
      return builds;
    }

    static private Build parseBuildLine(String line)
            throws IllegalArgumentException {
      String displayName = null;
      String downloadUrlString = null;
      String categoryString = null;
      URL downloadUrl;
      Build.Category category;
      StringTokenizer st = new StringTokenizer(line, "\t");
      if (st.hasMoreTokens()) {
        displayName = st.nextToken();
      }
      if (st.hasMoreTokens()) {
        downloadUrlString = st.nextToken();
      }
      if (st.hasMoreTokens()) {
        categoryString = st.nextToken();
      }
      if (displayName == null ||
              downloadUrlString == null ||
              categoryString == null) {
        StringBuffer msg = new StringBuffer()
                .append("Line '")
                .append(line)
                .append("' is incomplete or is not correctly delimited")
                .append("with tab characters");
        throw new IllegalArgumentException(msg.toString());
      } else {

        try {
          downloadUrl = new URL(downloadUrlString);
        } catch (MalformedURLException e) {
          StringBuffer msg = new StringBuffer()
                  .append("URL '")
                  .append(downloadUrlString)
                  .append("' is invalid");
          throw new IllegalArgumentException(msg.toString());
        }
        category = Build.Category.fromString(categoryString);
        if (category == null) {
          StringBuffer msg = new StringBuffer()
                  .append("Category '")
                  .append(categoryString)
                  .append("' is invalid; must be one of ");
          for (Build.Category c : EnumSet.allOf(Build.Category.class)) {
            msg.append("'").append(c.getKey()).append("' ");
          }
          throw new IllegalArgumentException(msg.toString());
        }
      }
      return new Build(displayName, downloadUrl, category);
    }
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
//      URL buildListUrl = new URL("http://builds.opends.org");
//      RemoteBuildManager rbm = new RemoteBuildManager(null, buildListUrl);
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
