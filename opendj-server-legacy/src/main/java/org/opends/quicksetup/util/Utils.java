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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.quicksetup.util;

import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.forgerock.util.Utils.*;
import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.quicksetup.Installation.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.DynamicConstants.*;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NamingSecurityException;
import javax.naming.NoPermissionException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.installer.DataReplicationOptions;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.server.tools.BackendTypeHelper;
import org.opends.server.util.SetupUtils;

import com.forgerock.opendj.cli.ArgumentConstants;
import com.forgerock.opendj.cli.ClientException;

/** This class provides some static convenience methods of different nature. */
public class Utils
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private Utils() {}

  private static final int BUFFER_SIZE = 1024;
  private static final int MAX_LINE_WIDTH = 80;

  /** Chars that require special treatment when passing them to command-line. */
  private static final char[] CHARS_TO_ESCAPE =
    { ' ', '\t', '\n', '|', ';', '<', '>', '(', ')', '$', '`', '\\', '"', '\'' };

  /** The class name that contains the control panel customizations for products. */
  private static final String CUSTOMIZATION_CLASS_NAME = "org.opends.server.util.ReleaseDefinition";

  /**
   * Returns <CODE>true</CODE> if the provided port is free and we can use it,
   * <CODE>false</CODE> otherwise.
   *
   * @param port
   *          the port we are analyzing.
   * @return <CODE>true</CODE> if the provided port is free and we can use it,
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean canUseAsPort(int port)
  {
    return SetupUtils.canUseAsPort(port);
  }

  /**
   * Returns <CODE>true</CODE> if the provided port is a privileged port,
   * <CODE>false</CODE> otherwise.
   *
   * @param port
   *          the port we are analyzing.
   * @return <CODE>true</CODE> if the provided port is a privileged port,
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean isPrivilegedPort(int port)
  {
    return SetupUtils.isPrivilegedPort(port);
  }

  /**
   * Tells whether the provided java installation supports a given option or
   * not.
   *
   * @param javaHome
   *          the java installation path.
   * @param option
   *          the java option that we want to check.
   * @param installPath
   *          the install path of the server.
   * @return <CODE>true</CODE> if the provided java installation supports a
   *         given option and <CODE>false</CODE> otherwise.
   */
  public static boolean supportsOption(String option, String javaHome, String installPath)
  {
    boolean supported = false;
    logger.info(LocalizableMessage.raw("Checking if options " + option + " are supported with java home: " + javaHome));
    try
    {
      List<String> args = new ArrayList<>();
      args.add(getScript(installPath));

      ProcessBuilder pb = new ProcessBuilder(args);
      Map<String, String> env = pb.environment();
      env.put(SetupUtils.OPENDJ_JAVA_HOME, javaHome);
      env.put("OPENDJ_JAVA_ARGS", option);
      env.put("SCRIPT_UTIL_CMD", "set-full-environment-and-test-java");
      env.remove("OPENDJ_JAVA_BIN");
      // In windows by default the scripts ask the user to click on enter when
      // they fail.  Set this environment variable to avoid it.
      if (isWindows())
      {
        env.put("DO_NOT_PAUSE", "true");
      }
      final Process process = pb.start();
      logger.info(LocalizableMessage.raw("launching " + args + " with env: " + env));
      InputStream is = process.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      String line;
      boolean errorDetected = false;
      while (null != (line = reader.readLine()))
      {
        logger.info(LocalizableMessage.raw("The output: " + line));
        if (line.contains("ERROR:  The detected Java version"))
        {
          if (isWindows())
          {
            // If we are running windows, the process get blocked waiting for
            // user input.  Just wait for a certain time to print the output
            // in the logger and then kill the process.
            Thread t = new Thread(new Runnable()
            {
              @Override
              public void run()
              {
                try
                {
                  Thread.sleep(3000);
                  // To see if the process is over, call the exitValue method.
                  // If it is not over, a IllegalThreadStateException.
                  process.exitValue();
                }
                catch (Throwable t)
                {
                  process.destroy();
                }
              }
            });
            t.start();
          }
          errorDetected = true;
        }
      }
      process.waitFor();
      int returnCode = process.exitValue();
      logger.info(LocalizableMessage.raw("returnCode: " + returnCode));
      supported = returnCode == 0 && !errorDetected;
      logger.info(LocalizableMessage.raw("supported: " + supported));
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error testing option " + option + " on " + javaHome, t));
    }
    return supported;
  }

  private static String getScript(String installPath)
  {
    String libPath = Utils.getPath(installPath, Installation.LIBRARIES_PATH_RELATIVE);
    String scriptUtilFileUnix = isWindows() ? SCRIPT_UTIL_FILE_WINDOWS : SCRIPT_UTIL_FILE_UNIX;
    return Utils.getScriptPath(Utils.getPath(libPath, scriptUtilFileUnix));
  }

  /**
   * Creates a new file attempting to create the parent directories if necessary.
   *
   * @param f
   *          File to create
   * @return boolean indicating whether the file was created; false otherwise
   * @throws IOException
   *           if something goes wrong
   */
  static boolean createFile(File f) throws IOException
  {
    if (f != null)
    {
      File parent = f.getParentFile();
      if (!parent.exists())
      {
        parent.mkdirs();
      }
      return f.createNewFile();
    }
    return false;
  }

  /**
   * Returns the absolute path for the given parentPath and relativePath.
   *
   * @param parentPath
   *          the parent path.
   * @param relativePath
   *          the relative path.
   * @return the absolute path for the given parentPath and relativePath.
   */
  public static String getPath(String parentPath, String relativePath)
  {
    return getPath(new File(new File(parentPath), relativePath));
  }

  /**
   * Returns the String that can be used to launch an script using Runtime.exec.
   * This method is required because in Windows the script that contain a "=" in
   * their path must be quoted.
   *
   * @param script
   *          the script name
   * @return the absolute path for the given parentPath and relativePath.
   */
  public static String getScriptPath(String script)
  {
    return SetupUtils.getScriptPath(script);
  }

  /**
   * Returns the absolute path for the given file. It tries to get the canonical
   * file path. If it fails it returns the string representation.
   *
   * @param f
   *          File to get the path
   * @return the absolute path for the given file.
   */
  public static String getPath(File f)
  {
    if (f != null)
    {
      try
      {
        /*
         * Do a best effort to avoid having a relative representation (for
         * instance to avoid having ../../../).
         */
        f = f.getCanonicalFile();
      }
      catch (IOException ioe)
      {
        /*
         * This is a best effort to get the best possible representation of the
         * file: reporting the error is not necessary.
         */
      }
      return f.toString();
    }
    return null;
  }

  /**
   * Returns <CODE>true</CODE> if the first provided path is under the second
   * path in the file system.
   *
   * @param descendant
   *          the descendant candidate path.
   * @param path
   *          the path.
   * @return <CODE>true</CODE> if the first provided path is under the second
   *         path in the file system; <code>false</code> otherwise or if either
   *         of the files are null
   */
  public static boolean isDescendant(File descendant, File path)
  {
    boolean isDescendant = false;
    if (descendant != null && path != null)
    {
      File parent = descendant.getParentFile();
      while (parent != null && !isDescendant)
      {
        isDescendant = path.equals(parent);
        if (!isDescendant)
        {
          parent = parent.getParentFile();
        }
      }
    }
    return isDescendant;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided path is a file and exists and
   * <CODE>false</CODE> otherwise.
   *
   * @param path
   *          the path that we are analyzing.
   * @return <CODE>true</CODE> if the the provided path is a file and exists and
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean fileExists(String path)
  {
    return new File(path).isFile();
  }

  /**
   * Returns <CODE>true</CODE> if the the provided path is a directory, exists
   * and is not empty <CODE>false</CODE> otherwise.
   *
   * @param path
   *          the path that we are analyzing.
   * @return <CODE>true</CODE> if the the provided path is a directory, exists
   *         and is not empty <CODE>false</CODE> otherwise.
   */
  public static boolean directoryExistsAndIsNotEmpty(String path)
  {
    final File f = new File(path);
    if (f.isDirectory())
    {
      final String[] ch = f.list();
      return ch != null && ch.length > 0;
    }
    return false;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided string is a configuration DN
   * and <CODE>false</CODE> otherwise.
   *
   * @param dn
   *          the String we are analyzing.
   * @return <CODE>true</CODE> if the the provided string is a configuration DN
   *         and <CODE>false</CODE> otherwise.
   */
  public static boolean isConfigurationDn(String dn)
  {
    boolean isConfigurationDn = false;
    String[] configDns = { "cn=config", Constants.SCHEMA_DN };
    for (int i = 0; i < configDns.length && !isConfigurationDn; i++)
    {
      isConfigurationDn = areDnsEqual(dn, configDns[i]);
    }
    return isConfigurationDn;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided strings represent the same DN
   * and <CODE>false</CODE> otherwise.
   *
   * @param dn1
   *          the first dn to compare.
   * @param dn2
   *          the second dn to compare.
   * @return <CODE>true</CODE> if the the provided strings represent the same DN
   *         and <CODE>false</CODE> otherwise.
   */
  public static boolean areDnsEqual(String dn1, String dn2)
  {
    try
    {
      LdapName name1 = new LdapName(dn1);
      LdapName name2 = new LdapName(dn2);
      return name1.equals(name2);
    }
    catch (Exception ex)
    {
      return false;
    }
  }

  /**
   * Creates the parent directory if it does not already exist.
   *
   * @param f
   *          File for which parentage will be insured
   * @return boolean indicating whether the input <code>f</code> has a
   *         parent after this method is invoked.
   */
  static boolean ensureParentsExist(File f)
  {
    final File parent = f.getParentFile();
    return parent.exists() || parent.mkdirs();
  }

  /**
   * Creates the a directory in the provided path.
   *
   * @param f
   *          the path.
   * @return <CODE>true</CODE> if the path was created or already existed (and
   *         was a directory) and <CODE>false</CODE> otherwise.
   * @throws IOException
   *           if something goes wrong.
   */
  static boolean createDirectory(File f) throws IOException
  {
    if (f.exists())
    {
      return f.isDirectory();
    }
    return f.mkdirs();
  }

  /**
   * Creates a file on the specified path with the contents of the provided
   * stream.
   *
   * @param path
   *          the path where the file will be created.
   * @param is
   *          the InputStream with the contents of the file.
   * @throws IOException
   *           if something goes wrong.
   */
  static void createFile(File path, InputStream is) throws IOException
  {
    try (FileOutputStream out = new FileOutputStream(path);
        BufferedOutputStream dest = new BufferedOutputStream(out))
    {
      byte[] data = new byte[BUFFER_SIZE];
      int count;
      while ((count = is.read(data, 0, BUFFER_SIZE)) != -1)
      {
        dest.write(data, 0, count);
      }
    }
  }

  /**
   * Creates a file on the specified path with the contents of the provided
   * String. The file is protected, so that 'others' have no access to it.
   *
   * @param path
   *          the path where the file will be created.
   * @param content
   *          the String with the contents of the file.
   * @throws IOException
   *           if something goes wrong.
   * @throws InterruptedException
   *           if there is a problem changing the permissions of the file.
   */
  public static void createProtectedFile(String path, String content) throws IOException, InterruptedException
  {
    FileWriter file = new FileWriter(path);
    PrintWriter out = new PrintWriter(file);

    out.println(content);

    out.flush();
    out.close();

    if (!isWindows())
    {
      setPermissionsUnix(path, "600");
    }
  }

  /**
   * This is a helper method that gets a LocalizableMessage representation of
   * the elements in the Collection of Messages. The LocalizableMessage will
   * display the different elements separated by the separator String.
   *
   * @param col
   *          the collection containing the messages.
   * @param separator
   *          the separator String to be used.
   * @return the message representation for the collection; null if
   *         <code>col</code> is null
   */
  public static LocalizableMessage getMessageFromCollection(Collection<LocalizableMessage> col, String separator)
  {
    if (col != null)
    {
      final LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      for (LocalizableMessage m : col)
      {
        mb.append(separator).append(m);
      }
      return mb.toMessage();
    }
    return null;
  }

  /**
   * Returns the default server location that will be proposed to the user in
   * the installation.
   *
   * @return the default server location that will be proposed to the user in
   *         the installation.
   */
  public static String getDefaultServerLocation()
  {
    String userDir = System.getProperty("user.home");
    String firstLocation = userDir + File.separator + SHORT_NAME.toLowerCase(Locale.ENGLISH);
    String serverLocation = firstLocation;
    int i = 1;
    while (fileExists(serverLocation) || directoryExistsAndIsNotEmpty(serverLocation))
    {
      serverLocation = firstLocation + "-" + i;
      i++;
    }
    return serverLocation;
  }

  /**
   * Gets a localized representation of the provide TopologyCacheException.
   *
   * @param te
   *          the exception.
   * @return a localized representation of the provide TopologyCacheException.
   */
  public static LocalizableMessage getMessage(TopologyCacheException te)
  {
    LocalizableMessageBuilder buf = new LocalizableMessageBuilder();

    String ldapUrl = te.getLdapUrl();
    if (ldapUrl != null)
    {
      String hostName = ldapUrl.substring(ldapUrl.indexOf("://") + 3);
      buf.append(INFO_SERVER_ERROR.get(hostName));
      buf.append(" ");
    }
    if (te.getType() == TopologyCacheException.Type.TIMEOUT)
    {
      buf.append(INFO_ERROR_CONNECTING_TIMEOUT.get());
    }
    else if (te.getCause() instanceof NamingException)
    {
      buf.append(getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(), te.getCause()));
    }
    else
    {
      logger.warn(LocalizableMessage.raw("Unexpected error: " + te, te));
      // This is unexpected.
      if (te.getCause() != null)
      {
        buf.append(getThrowableMsg(INFO_BUG_MSG.get(), te.getCause()));
      }
      else
      {
        buf.append(getThrowableMsg(INFO_BUG_MSG.get(), te));
      }
    }
    return buf.toMessage();
  }

  /**
   * Sets the permissions of the provided paths with the provided permission
   * String.
   *
   * @param paths
   *          the paths to set permissions on.
   * @param permissions
   *          the UNIX-mode file system permission representation (for example
   *          "644" or "755")
   * @return the return code of the chmod command.
   * @throws IOException
   *           if something goes wrong.
   * @throws InterruptedException
   *           if the Runtime.exec method is interrupted.
   */
  static int setPermissionsUnix(List<String> paths, String permissions) throws IOException,
      InterruptedException
  {
    String[] args = new String[paths.size() + 2];
    args[0] = "chmod";
    args[1] = permissions;
    for (int i = 2; i < args.length; i++)
    {
      args[i] = paths.get(i - 2);
    }
    Process p = Runtime.getRuntime().exec(args);
    return p.waitFor();
  }

  /**
   * Sets the permissions of the provided paths with the provided permission
   * String.
   *
   * @param path
   *          to set permissions on.
   * @param permissions
   *          the UNIX-mode file system permission representation (for example
   *          "644" or "755")
   * @return the return code of the chmod command.
   * @throws IOException
   *           if something goes wrong.
   * @throws InterruptedException
   *           if the Runtime.exec method is interrupted.
   */
  static int setPermissionsUnix(String path, String permissions) throws IOException, InterruptedException
  {
    String[] args = new String[] { "chmod", permissions, path };
    Process p = Runtime.getRuntime().exec(args);
    return p.waitFor();
  }

  /**
   * Returns <CODE>true</CODE> if this is executed from command line and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if this is executed from command line and
   *         <CODE>false</CODE> otherwise.
   */
  public static boolean isCli()
  {
    return "true".equals(System.getProperty(Constants.CLI_JAVA_PROPERTY));
  }

  /**
   * Creates an LDAP+StartTLS connection and returns the corresponding
   * LdapContext. This method first creates an LdapContext with anonymous bind.
   * Then it requests a StartTlsRequest extended operation. The StartTlsResponse
   * is setup with the specified hostname verifier. Negotiation is done using a
   * TrustSocketFactory so that the specified TrustManager gets called during
   * the SSL handshake. If trust manager is null, certificates are not checked
   * during SSL handshake.
   *
   * @param ldapsURL
   *          the target *LDAPS* URL.
   * @param dn
   *          passed as Context.SECURITY_PRINCIPAL if not null.
   * @param pwd
   *          passed as Context.SECURITY_CREDENTIALS if not null.
   * @param timeout
   *          passed as com.sun.jndi.ldap.connect.timeout if > 0.
   * @param env
   *          null or additional environment properties.
   * @param trustManager
   *          null or the trust manager to be invoked during SSL. negociation.
   * @param verifier
   *          null or the hostname verifier to be setup in the StartTlsResponse.
   * @return the established connection with the given parameters.
   * @throws NamingException
   *           the exception thrown when instantiating InitialLdapContext.
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   * @see javax.naming.ldap.StartTlsRequest
   * @see javax.naming.ldap.StartTlsResponse
   * @see org.opends.admin.ads.util.TrustedSocketFactory
   */

  public static InitialLdapContext createStartTLSContext(String ldapsURL, String dn, String pwd, int timeout,
      Hashtable<String, String> env, TrustManager trustManager, HostnameVerifier verifier) throws NamingException
  {
    return ConnectionUtils.createStartTLSContext(ldapsURL, dn, pwd, timeout, env, trustManager, null, verifier);
  }

  /**
   * Returns a message object for the given NamingException. The code assume
   * that we are trying to connect to the local server.
   *
   * @param ne
   *          the NamingException.
   * @return a message object for the given NamingException.
   */
  public static LocalizableMessage getMessageForException(NamingException ne)
  {
    final String detailedException = ne.toString(true);
    if (isCertificateException(ne))
    {
      return INFO_ERROR_READING_CONFIG_LDAP_CERTIFICATE.get(detailedException);
    }
    else if (ne instanceof AuthenticationException)
    {
      return ERR_CANNOT_CONNECT_TO_LOCAL_AUTHENTICATION.get(detailedException);
    }
    else if (ne instanceof NoPermissionException)
    {
      return ERR_CANNOT_CONNECT_TO_LOCAL_PERMISSIONS.get(detailedException);
    }
    else if (ne instanceof NamingSecurityException)
    {
      return ERR_CANNOT_CONNECT_TO_LOCAL_PERMISSIONS.get(detailedException);
    }
    else if (ne instanceof CommunicationException)
    {
      return ERR_CANNOT_CONNECT_TO_LOCAL_COMMUNICATION.get(detailedException);
    }
    else
    {
      return ERR_CANNOT_CONNECT_TO_LOCAL_GENERIC.get(detailedException);
    }
  }

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @return the path of the installation of the directory server.
   */
  public static String getInstallPathFromClasspath()
  {
    String installPath = System.getProperty("org.opends.quicksetup.Root");
    if (installPath != null)
    {
      return installPath;
    }

    /* Get the install path from the Class Path */
    String sep = System.getProperty("path.separator");
    String[] classPaths = System.getProperty("java.class.path").split(sep);
    String path = getInstallPath(classPaths);
    if (path != null)
    {
      File f = new File(path).getAbsoluteFile();
      File librariesDir = f.getParentFile();

      /*
       * Do a best effort to avoid having a relative representation (for
       * instance to avoid having ../../../).
       */
      try
      {
        installPath = librariesDir.getParentFile().getCanonicalPath();
      }
      catch (IOException ioe)
      {
        // Best effort
        installPath = librariesDir.getParent();
      }
    }
    return installPath;
  }

  private static String getInstallPath(final String[] classPaths)
  {
    for (String classPath : classPaths)
    {
      final String normPath = classPath.replace(File.separatorChar, '/');
      if (normPath.endsWith(OPENDJ_BOOTSTRAP_CLIENT_JAR_RELATIVE_PATH)
          || normPath.endsWith(OPENDJ_BOOTSTRAP_JAR_RELATIVE_PATH))
      {
        return classPath;
      }
    }
    return null;
  }

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @param installPath
   *          The installation path
   * @return the path of the installation of the directory server.
   */
  public static String getInstancePathFromInstallPath(String installPath)
  {
    String instancePathFileName = Installation.INSTANCE_LOCATION_PATH;
    File _svcScriptPathName = new File(
        installPath + File.separator + Installation.LIBRARIES_PATH_RELATIVE + File.separator + "_svc-opendj.sh");

    // look for /etc/opt/opendj/instance.loc
    File f = new File(instancePathFileName);
    if (!_svcScriptPathName.exists() || !f.exists())
    {
      // look for <installPath>/instance.loc
      instancePathFileName = installPath + File.separator + Installation.INSTANCE_LOCATION_PATH_RELATIVE;
      f = new File(instancePathFileName);
      if (!f.exists())
      {
        return installPath;
      }
    }

    // Read the first line and close the file.
    try (BufferedReader reader = new BufferedReader(new FileReader(instancePathFileName)))
    {
      String line = reader.readLine();
      File instanceLoc = new File(line.trim());
      return getCanonicalPath(instanceLoc.isAbsolute()
          ? instanceLoc
          : new File(installPath + File.separator + instanceLoc.getPath()));
    }
    catch (Exception e)
    {
      return installPath;
    }
  }

  /**
   * Returns the max size in character of a line to be displayed in the command
   * line.
   *
   * @return the max size in character of a line to be displayed in the command
   *         line.
   */
  public static int getCommandLineMaxLineWidth()
  {
    return MAX_LINE_WIDTH;
  }

  /**
   * Puts Swing menus in the Mac OS menu bar, if using the Aqua look and feel,
   * and sets the application name that is displayed in the application menu and
   * in the dock.
   *
   * @param appName
   *          application name to display in the menu bar and the dock.
   */
  public static void setMacOSXMenuBar(LocalizableMessage appName)
  {
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", String.valueOf(appName));
  }

  /**
   * Returns the file system permissions for a file.
   *
   * @param file
   *          the file for which we want the file permissions.
   * @return the file system permissions for the file.
   */
  static String getFileSystemPermissions(File file)
  {
    String name = file.getName();
    if (file.getParent().endsWith(File.separator + Installation.WINDOWS_BINARIES_PATH_RELATIVE)
        || file.getParent().endsWith(File.separator + Installation.UNIX_BINARIES_PATH_RELATIVE))
    {
      return name.endsWith(".bat") ? "644" : "755";
    }
    else if (name.endsWith(".sh")
          || name.endsWith(Installation.UNIX_SETUP_FILE_NAME)
          || name.endsWith(Installation.UNIX_UNINSTALL_FILE_NAME)
          || name.endsWith(Installation.UNIX_UPGRADE_FILE_NAME)
          || name.endsWith(Installation.MAC_JAVA_APP_STUB_NAME))
    {
      return "755";
    }
    else
    {
      return "644";
    }
  }

  /**
   * Inserts HTML break tags into <code>d</code> breaking it up so that ideally
   * no line is longer than <code>maxll</code> assuming no single word is longer
   * then <code>maxll</code>. If the string already contains HTML tags that
   * cause a line break (e.g break and closing list item tags) they are
   * respected by this method when calculating where to place new breaks to
   * control the maximum line length.
   *
   * @param cs
   *          String to break
   * @param maxll
   *          int maximum line length
   * @return String representing <code>d</code> with HTML break tags inserted
   */
  public static String breakHtmlString(CharSequence cs, int maxll)
  {
    if (cs == null)
    {
      return null;
    }

    String d = cs.toString();
    int len = d.length();
    if (len <= 0 || len <= maxll)
    {
      return d;
    }

    // First see if there are any tags that would cause a natural break in the line.
    // If so start line break point evaluation from that point.
    for (String tag : Constants.BREAKING_TAGS)
    {
      int p = d.lastIndexOf(tag, maxll);
      if (p > 0 && p < len)
      {
        return d.substring(0, p + tag.length()) + breakHtmlString(d.substring(p + tag.length()), maxll);
      }
    }

    // Now look for spaces in which to insert a break.
    // First see if there are any spaces counting backward from the max line length.
    // If there aren't any, then use the first space encountered after the max line length.
    int p = d.lastIndexOf(' ', maxll);
    if (p <= 0)
    {
      p = d.indexOf(' ', maxll);
    }
    if (0 < p && p < len)
    {
      return d.substring(0, p) + Constants.HTML_LINE_BREAK + breakHtmlString(d.substring(p + 1), maxll);
    }
    return d;
  }

  /**
   * Tests a text string to see if it contains HTML.
   *
   * @param text
   *          String to test
   * @return true if the string contains HTML
   */
  static boolean containsHtml(String text)
  {
    return text != null && text.indexOf('<') != -1 && text.indexOf('>') != -1;
  }

  private static EmptyPrintStream emptyStream = new EmptyPrintStream();

  /**
   * Returns a printstream that does not write anything to standard output.
   *
   * @return a printstream that does not write anything to standard output.
   */
  public static EmptyPrintStream getEmptyPrintStream()
  {
    if (emptyStream == null)
    {
      emptyStream = new EmptyPrintStream();
    }
    return emptyStream;
  }

  /**
   * Returns the current time of a server in milliseconds.
   *
   * @param ctx
   *          the connection to the server.
   * @return the current time of a server in milliseconds.
   */
  public static long getServerClock(InitialLdapContext ctx)
  {
    long time = -1;
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(new String[] { "currentTime" });
    String filter = "(objectclass=*)";

    try
    {
      LdapName jndiName = new LdapName("cn=monitor");
      NamingEnumeration<?> listeners = ctx.search(jndiName, filter, ctls);

      try
      {
        while (listeners.hasMore())
        {
          SearchResult sr = (SearchResult) listeners.next();

          String v = getFirstValue(sr, "currentTime");

          TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

          SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
          formatter.setTimeZone(utcTimeZone);

          time = formatter.parse(v).getTime();
        }
      }
      finally
      {
        listeners.close();
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Error retrieving server current time: " + t, t));
    }
    return time;
  }

  /**
   * Checks that the java version we are running is compatible with OpenDS.
   *
   * @throws IncompatibleVersionException
   *           if the java version we are running is not compatible with OpenDS.
   */
  public static void checkJavaVersion() throws IncompatibleVersionException
  {
    try
    {
      com.forgerock.opendj.cli.Utils.checkJavaVersion();
    }
    catch (ClientException e)
    {
      throw new IncompatibleVersionException(e.getMessageObject(), e);
    }
  }

  /**
   * Basic method to know if the host is local or not. This is only used to know
   * if we can perform a port check or not.
   *
   * @param host
   *          the host to analyze.
   * @return <CODE>true</CODE> if it is the local host and <CODE>false</CODE>
   *         otherwise.
   */
  public static boolean isLocalHost(String host)
  {
    if ("localhost".equalsIgnoreCase(host))
    {
      return true;
    }

    try
    {
      InetAddress localAddress = InetAddress.getLocalHost();
      InetAddress[] addresses = InetAddress.getAllByName(host);
      for (InetAddress address : addresses)
      {
        if (localAddress.equals(address))
        {
          return true;
        }
      }
    }
    catch (Throwable t)
    {
      logger.warn(LocalizableMessage.raw("Failing checking host names: " + t, t));
    }
    return false;
  }

  /**
   * Returns the HTML representation of a plain text string which is obtained
   * by converting some special characters (like '<') into its equivalent
   * escaped HTML representation.
   *
   * @param rawString the String from which we want to obtain the HTML
   * representation.
   * @return the HTML representation of the plain text string.
   */
  private static String escapeHtml(String rawString)
  {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < rawString.length(); i++)
    {
      escapeChar(buffer, rawString.charAt(i));
    }
    return buffer.toString();
  }

  private static StringBuilder escapeChar(StringBuilder buffer, char c)
  {
    switch (c)
    {
    case '<':
      return buffer.append("&lt;");
    case '>':
      return buffer.append("&gt;");
    case '&':
      return buffer.append("&amp;");
    case '"':
      return buffer.append("&quot;");
    default:
      return buffer.append(c);
    }
  }

  /**
   * Returns the HTML representation for a given text. without adding any kind
   * of font or style elements.  Just escapes the problematic characters
   * (like '<') and transform the break lines into '\n' characters.
   *
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation for the given text.
   */
  public static String getHtml(String text)
  {
    if (text == null)
    {
      return "";
    }

    text = text.replaceAll("\r\n", "\n");

    StringBuilder buffer = new StringBuilder();
    String[] lines = text.split("[\n\r\u0085\u2028\u2029]");
    for (int i = 0; i < lines.length; i++)
    {
      if (i != 0)
      {
        buffer.append(Constants.HTML_LINE_BREAK);
      }
      buffer.append(escapeHtml(lines[i]));
    }
    return buffer.toString();
  }

  /**
   * Tries to find a customized object in the customization class. If the
   * customization class does not exist or it does not contain the field as the
   * specified type of the object, returns the default value.
   *
   * @param <T>
   *          the type of the customized object.
   * @param fieldName
   *          the name of the field representing an object in the customization
   *          class.
   * @param defaultValue
   *          the default value.
   * @param valueClass
   *          the class of the parameterized value.
   * @return the customized object.
   */
  public static <T> T getCustomizedObject(String fieldName, T defaultValue, Class<T> valueClass)
  {
    try
    {
      Class<?> c = Class.forName(Utils.CUSTOMIZATION_CLASS_NAME);
      Object obj = c.newInstance();

      return valueClass.cast(c.getField(fieldName).get(obj));
    }
    catch (Exception ex)
    {
      //do nothing.
    }
    return defaultValue;
  }

  /**
   * Adds word break tags to the provided html string.
   *
   * @param htmlString
   *          the string.
   * @param from
   *          the first index to start the spacing from.
   * @param spacing
   *          the minimal spacing between word breaks.
   * @return a string containing word breaks.
   */
  public static String addWordBreaks(String htmlString, int from, int spacing)
  {
    StringBuilder sb = new StringBuilder();
    boolean insideTag = false;
    int totalAddedChars = 0;
    int addedChars = 0;
    for (int i = 0; i < htmlString.length(); i++)
    {
      char c = htmlString.charAt(i);
      sb.append(c);
      if (c == '<')
      {
        insideTag = true;
      }
      else if (c == '>' && insideTag)
      {
        insideTag = false;
      }
      if (!insideTag && c != '>')
      {
        addedChars++;
        totalAddedChars++;
      }
      if (addedChars > spacing && totalAddedChars > from && !insideTag)
      {
        sb.append("<wbr>");
        addedChars = 0;
      }
    }
    return sb.toString();
  }

  /**
   * Returns the localized string describing the DataOptions chosen by the user.
   *
   * @param userInstallData
   *          the DataOptions of the user.
   * @return the localized string describing the DataOptions chosen by the user.
   */
  public static String getDataDisplayString(final UserData userInstallData)
  {
    LocalizableMessage msg;

    final DataReplicationOptions repl = userInstallData.getReplicationOptions();
    final SuffixesToReplicateOptions suf = userInstallData.getSuffixesToReplicateOptions();

    boolean createSuffix = repl.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY
                        || repl.getType() == DataReplicationOptions.Type.STANDALONE
                        || suf.getType() == SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;

    if (createSuffix)
    {
      NewSuffixOptions options = userInstallData.getNewSuffixOptions();
      LocalizableMessage arg2 = toArg2(options);

      if (options.getBaseDns().isEmpty())
      {
        msg = INFO_REVIEW_CREATE_NO_SUFFIX.get();
      }
      else
      {
        final String backendType = userInstallData.getBackendType().getUserFriendlyName().toString();
        if (options.getBaseDns().size() > 1)
        {
          msg = INFO_REVIEW_CREATE_SUFFIX.get(
              backendType, joinAsString(Constants.LINE_SEPARATOR, options.getBaseDns()), arg2);
        }
        else
        {
          msg = INFO_REVIEW_CREATE_SUFFIX.get(backendType, options.getBaseDns().getFirst(), arg2);
        }
      }
    }
    else
    {
      final StringBuilder buf = new StringBuilder();
      for (final SuffixDescriptor suffix : suf.getSuffixes())
      {
        if (buf.length() > 0)
        {
          buf.append(Constants.LINE_SEPARATOR);
        }
        buf.append(suffix.getDN());
      }
      msg = INFO_REVIEW_REPLICATE_SUFFIX.get(buf);
    }

    return msg.toString();
  }

  private static LocalizableMessage toArg2(NewSuffixOptions options)
  {
    switch (options.getType())
    {
    case CREATE_BASE_ENTRY:
      return INFO_REVIEW_CREATE_BASE_ENTRY_LABEL.get(options.getBaseDns().getFirst());
    case LEAVE_DATABASE_EMPTY:
      return INFO_REVIEW_LEAVE_DATABASE_EMPTY_LABEL.get();
    case IMPORT_FROM_LDIF_FILE:
      return INFO_REVIEW_IMPORT_LDIF.get(options.getLDIFPaths().getFirst());
    case IMPORT_AUTOMATICALLY_GENERATED_DATA:
      return INFO_REVIEW_IMPORT_AUTOMATICALLY_GENERATED.get(options.getNumberEntries());
    default:
      throw new IllegalArgumentException("Unknown type: " + options.getType());
    }
  }

  /**
   * Returns a localized String representation of the provided SecurityOptions
   * object.
   *
   * @param ops
   *          the SecurityOptions object from which we want to obtain the String
   *          representation.
   * @param html
   *          whether the resulting String must be in HTML or not.
   * @return a localized String representation of the provided SecurityOptions
   *         object.
   */
  public static String getSecurityOptionsString(SecurityOptions ops, boolean html)
  {
    StringBuilder buf = new StringBuilder();

    if (ops.getCertificateType() == SecurityOptions.CertificateType.NO_CERTIFICATE)
    {
      buf.append(INFO_NO_SECURITY.get());
    }
    else
    {
      if (ops.getEnableStartTLS())
      {
        buf.append(INFO_ENABLE_STARTTLS.get());
      }
      if (ops.getEnableSSL())
      {
        if (buf.length() > 0)
        {
          if (html)
          {
            buf.append(Constants.HTML_LINE_BREAK);
          }
          else
          {
            buf.append("\n");
          }
        }
        buf.append(INFO_ENABLE_SSL.get(ops.getSslPort()));
      }
      if (html)
      {
        buf.append(Constants.HTML_LINE_BREAK);
      }
      else
      {
        buf.append("\n");
      }
      buf.append(toCertMsg(ops));
    }

    if (html)
    {
      return "<html>" + UIFactory.applyFontToHtml(buf.toString(), UIFactory.SECONDARY_FIELD_VALID_FONT);
    }
    else
    {
      return buf.toString();
    }
  }

  private static LocalizableMessage toCertMsg(SecurityOptions ops)
  {
    switch (ops.getCertificateType())
    {
    case SELF_SIGNED_CERTIFICATE:
      return INFO_SELF_SIGNED_CERTIFICATE.get();
    case JKS:
      return INFO_JKS_CERTIFICATE.get();
    case JCEKS:
      return INFO_JCEKS_CERTIFICATE.get();
    case PKCS11:
      return INFO_PKCS11_CERTIFICATE.get();
    case PKCS12:
      return INFO_PKCS12_CERTIFICATE.get();
    default:
      throw new IllegalStateException("Unknown certificate options type: " + ops.getCertificateType());
    }
  }

  /**
   * Returns a String representation of the provided command-line.
   *
   * @param cmd
   *          the command-line arguments.
   * @param formatter
   *          the formatted to be used to create the String representation.
   * @return a String representation of the provided command-line.
   */
  public static String getFormattedEquivalentCommandLine(List<String> cmd, ProgressMessageFormatter formatter)
  {
    StringBuilder builder = new StringBuilder();
    builder.append(formatter.getFormattedProgress(LocalizableMessage.raw(cmd.get(0))));
    int initialIndex = 1;
    StringBuilder sbSeparator = new StringBuilder();
    sbSeparator.append(formatter.getSpace());
    if (!isWindows())
    {
      sbSeparator.append("\\");
      sbSeparator.append(formatter.getLineBreak());
      for (int i = 0; i < 10; i++)
      {
        sbSeparator.append(formatter.getSpace());
      }
    }

    String lineSeparator = sbSeparator.toString();
    for (int i = initialIndex; i < cmd.size(); i++)
    {
      String s = cmd.get(i);
      if (s.startsWith("-"))
      {
        builder.append(lineSeparator);
        builder.append(formatter.getFormattedProgress(LocalizableMessage.raw(s)));
      }
      else
      {
        builder.append(formatter.getSpace());
        builder.append(formatter.getFormattedProgress(LocalizableMessage.raw(escapeCommandLineValue(s))));
      }
    }
    return builder.toString();
  }

  /**
   * This method simply takes a value and tries to transform it (with escape or
   * '"') characters so that it can be used in a command line.
   *
   * @param value
   *          the String to be treated.
   * @return the transformed value.
   */
  public static String escapeCommandLineValue(String value)
  {
    StringBuilder b = new StringBuilder();
    if (isUnix())
    {
      for (int i = 0; i < value.length(); i++)
      {
        char c = value.charAt(i);
        boolean charToEscapeFound = false;
        for (int j = 0; j < CHARS_TO_ESCAPE.length && !charToEscapeFound; j++)
        {
          charToEscapeFound = c == CHARS_TO_ESCAPE[j];
        }
        if (charToEscapeFound)
        {
          b.append('\\');
        }
        b.append(c);
      }
    }
    else
    {
      b.append('"').append(value).append('"');
    }

    return b.toString();
  }

  /**
   * Returns the equivalent setup CLI command-line. Note that this command-line
   * does not cover all the replication part of the GUI install.
   *
   * @param userData
   *          the user data.
   * @return the equivalent setup command-line.
   */
  public static List<String> getSetupEquivalentCommandLine(final UserData userData)
  {
    List<String> cmdLine = new ArrayList<>();
    cmdLine.add(getInstallDir() + getSetupFileName());
    cmdLine.add("--cli");

    final ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType =
        userData.getBackendType();
    if (backendType != null)
    {
      cmdLine.add("--" + ArgumentConstants.OPTION_LONG_BACKEND_TYPE);
      cmdLine.add(BackendTypeHelper.filterSchemaBackendName(backendType.getName()));
    }

    for (final String baseDN : getBaseDNs(userData))
    {
      cmdLine.add("--baseDN");
      cmdLine.add(baseDN);
    }

    switch (userData.getNewSuffixOptions().getType())
    {
    case CREATE_BASE_ENTRY:
      cmdLine.add("--addBaseEntry");
      break;

    case IMPORT_AUTOMATICALLY_GENERATED_DATA:
      cmdLine.add("--sampleData");
      cmdLine.add(Integer.toString(userData.getNewSuffixOptions().getNumberEntries()));
      break;

    case IMPORT_FROM_LDIF_FILE:
      for (final String ldifFile : userData.getNewSuffixOptions().getLDIFPaths())
      {
        cmdLine.add("--ldifFile");
        cmdLine.add(ldifFile);
      }

      final String rejectFile = userData.getNewSuffixOptions().getRejectedFile();
      if (rejectFile != null)
      {
        cmdLine.add("--rejectFile");
        cmdLine.add(rejectFile);
      }

      final String skipFile = userData.getNewSuffixOptions().getSkippedFile();
      if (skipFile != null)
      {
        cmdLine.add("--skipFile");
        cmdLine.add(skipFile);
      }
      break;

    default:
      break;
    }

    cmdLine.add("--ldapPort");
    cmdLine.add(Integer.toString(userData.getServerPort()));

    cmdLine.add("--adminConnectorPort");
    cmdLine.add(Integer.toString(userData.getAdminConnectorPort()));

    if (userData.getServerJMXPort() != -1)
    {
      cmdLine.add("--jmxPort");
      cmdLine.add(Integer.toString(userData.getServerJMXPort()));
    }

    cmdLine.add("--rootUserDN");
    cmdLine.add(userData.getDirectoryManagerDn());

    cmdLine.add("--rootUserPassword");
    cmdLine.add(OBFUSCATED_VALUE);

    if (isWindows() && userData.getEnableWindowsService())
    {
      cmdLine.add("--enableWindowsService");
    }

    if (userData.getReplicationOptions().getType() == DataReplicationOptions.Type.STANDALONE
        && !userData.getStartServer())
    {
      cmdLine.add("--doNotStart");
    }

    if (userData.getSecurityOptions().getEnableStartTLS())
    {
      cmdLine.add("--enableStartTLS");
    }

    if (userData.getSecurityOptions().getEnableSSL())
    {
      cmdLine.add("--ldapsPort");
      cmdLine.add(Integer.toString(userData.getSecurityOptions().getSslPort()));
    }

    cmdLine.addAll(getSecurityOptionSetupEquivalentCmdLine(userData));
    cmdLine.add("--no-prompt");
    cmdLine.add("--noPropertiesFile");

    return cmdLine;
  }

  private static List<String> getSecurityOptionSetupEquivalentCmdLine(final UserData userData)
  {
    final List<String> cmdLine = new ArrayList<>();

    switch (userData.getSecurityOptions().getCertificateType())
    {
    case SELF_SIGNED_CERTIFICATE:
      cmdLine.add("--generateSelfSignedCertificate");
      cmdLine.add("--hostName");
      cmdLine.add(userData.getHostName());
      break;

    case JKS:
      cmdLine.add("--useJavaKeystore");
      cmdLine.add(userData.getSecurityOptions().getKeystorePath());
      addKeyStoreAndCert(userData.getSecurityOptions(), cmdLine);
      break;

    case JCEKS:
      cmdLine.add("--useJCEKS");
      cmdLine.add(userData.getSecurityOptions().getKeystorePath());

      addKeyStoreAndCert(userData.getSecurityOptions(), cmdLine);
      break;

    case PKCS12:
      cmdLine.add("--usePkcs12keyStore");
      cmdLine.add(userData.getSecurityOptions().getKeystorePath());

      addKeyStoreAndCert(userData.getSecurityOptions(), cmdLine);
      break;

    case PKCS11:
      cmdLine.add("--usePkcs11Keystore");

      addKeyStoreAndCert(userData.getSecurityOptions(), cmdLine);
      break;

    default:
      break;
    }

    return cmdLine;
  }

  private static void addKeyStoreAndCert(final SecurityOptions securityOptions, final List<String> cmdLine)
  {
    if (securityOptions.getKeystorePassword() != null)
    {
      cmdLine.add("--keyStorePassword");
      cmdLine.add(OBFUSCATED_VALUE);
    }

    for(String alias : securityOptions.getAliasesToUse())
    {
      cmdLine.add("--certNickname");
      cmdLine.add(alias);
    }
  }

  /**
   * Returns the list of equivalent command-lines that must be executed to
   * enable or initialize replication as the setup does.
   *
   * @param subcommand
   *          either {@code "enable"} or {@code "initialize"}
   * @param userData
   *          the user data.
   * @return the list of equivalent command-lines that must be executed to
   *         enable or initialize replication as the setup does.
   */
  public static List<List<String>> getDsReplicationEquivalentCommandLines(String subcommand, UserData userData)
  {
    final List<List<String>> cmdLines = new ArrayList<>();
    final Map<ServerDescriptor, Set<String>> hmServerBaseDNs = getServerDescriptorBaseDNMap(userData);
    for (ServerDescriptor server : hmServerBaseDNs.keySet())
    {
      cmdLines.add(getDsReplicationEquivalentCommandLine(subcommand, userData, hmServerBaseDNs.get(server), server));
    }
    return cmdLines;
  }

  private static void addEnableCommandOptions(UserData userData, ServerDescriptor server, List<String> cmdLine)
  {
    DataReplicationOptions replOptions = userData.getReplicationOptions();
    cmdLine.add("--host1");
    cmdLine.add(server.getHostName());
    cmdLine.add("--port1");
    cmdLine.add(String.valueOf(server.getEnabledAdministrationPorts().get(0)));

    AuthenticationData authData = userData.getReplicationOptions().getAuthenticationData();
    if (!Utils.areDnsEqual(authData.getDn(), ADSContext.getAdministratorDN(userData.getGlobalAdministratorUID())))
    {
      cmdLine.add("--bindDN1");
      cmdLine.add(authData.getDn());
      cmdLine.add("--bindPassword1");
      cmdLine.add(OBFUSCATED_VALUE);
    }
    for (ServerDescriptor s : userData.getRemoteWithNoReplicationPort().keySet())
    {
      if (s.getAdminConnectorURL().equals(server.getAdminConnectorURL()))
      {
        AuthenticationData remoteRepl = userData.getRemoteWithNoReplicationPort().get(server);

        cmdLine.add("--replicationPort1");
        cmdLine.add(String.valueOf(remoteRepl.getPort()));
        if (remoteRepl.useSecureConnection())
        {
          cmdLine.add("--secureReplication1");
        }
      }
    }
    cmdLine.add("--host2");
    cmdLine.add(userData.getHostName());
    cmdLine.add("--port2");
    cmdLine.add(String.valueOf(userData.getAdminConnectorPort()));
    cmdLine.add("--bindDN2");
    cmdLine.add(userData.getDirectoryManagerDn());
    cmdLine.add("--bindPassword2");
    cmdLine.add(OBFUSCATED_VALUE);
    if (replOptions.getReplicationPort() != -1)
    {
      cmdLine.add("--replicationPort2");
      cmdLine.add(String.valueOf(replOptions.getReplicationPort()));
      if (replOptions.useSecureReplication())
      {
        cmdLine.add("--secureReplication2");
      }
    }
  }

  /**
   * Returns the full path of the command-line for a given script name.
   *
   * @param scriptBasicName
   *          the script basic name (with no extension).
   * @return the full path of the command-line for a given script name.
   */
  private static String getCommandLinePath(String scriptBasicName)
  {
    String installDir = getInstallDir();
    if (isWindows())
    {
      return installDir + WINDOWS_BINARIES_PATH_RELATIVE + File.separatorChar + scriptBasicName + ".bat";
    }
    else
    {
      return installDir + UNIX_BINARIES_PATH_RELATIVE + File.separatorChar + scriptBasicName;
    }
  }

  private static String installDir;

  /**
   * Returns the installation directory.
   *
   * @return the installation directory.
   */
  private static String getInstallDir()
  {
    if (installDir == null)
    {
      File f = org.opends.quicksetup.Installation.getLocal().getRootDirectory();
      installDir = getCanonicalPath(f);
      if (installDir.lastIndexOf(File.separatorChar) != installDir.length() - 1)
      {
        installDir += File.separatorChar;
      }
    }
    return installDir;
  }

  private static String getCanonicalPath(File f)
  {
    try
    {
      return f.getCanonicalPath();
    }
    catch (IOException t)
    {
      return f.getAbsolutePath();
    }
  }

  private static List<String> getDsReplicationEquivalentCommandLine(String subcommand, UserData userData,
      Set<String> baseDNs, ServerDescriptor server)
  {
    List<String> cmdLine = new ArrayList<>();
    String cmdName = getCommandLinePath("dsreplication");
    cmdLine.add(cmdName);
    cmdLine.add(subcommand);

    if ("enable".equals(subcommand))
    {
      addEnableCommandOptions(userData, server, cmdLine);
    }
    else if ("initialize".equals(subcommand))
    {
      addInitializeCommandOptions(userData, server, cmdLine);
    }
    else
    {
      throw new IllegalArgumentException("Code is not implemented for subcommand " + subcommand);
    }

    addCommonOptions(userData, baseDNs, cmdLine);
    return cmdLine;
  }

  private static void addInitializeCommandOptions(UserData userData, ServerDescriptor server, List<String> cmdLine)
  {
    cmdLine.add("--hostSource");
    cmdLine.add(server.getHostName());
    cmdLine.add("--portSource");
    cmdLine.add(String.valueOf(server.getEnabledAdministrationPorts().get(0)));

    cmdLine.add("--hostDestination");
    cmdLine.add(userData.getHostName());
    cmdLine.add("--portDestination");
    cmdLine.add(String.valueOf(userData.getAdminConnectorPort()));
  }

  private static void addCommonOptions(UserData userData, Set<String> baseDNs, List<String> cmdLine)
  {
    for (String baseDN : baseDNs)
    {
      cmdLine.add("--baseDN");
      cmdLine.add(baseDN);
    }

    cmdLine.add("--adminUID");
    cmdLine.add(userData.getGlobalAdministratorUID());
    cmdLine.add("--adminPassword");
    cmdLine.add(OBFUSCATED_VALUE);

    cmdLine.add("--trustAll");
    cmdLine.add("--no-prompt");
    cmdLine.add("--noPropertiesFile");
  }

  private static List<String> getBaseDNs(UserData userData)
  {
    List<String> baseDNs = new ArrayList<>();

    DataReplicationOptions repl = userData.getReplicationOptions();
    SuffixesToReplicateOptions suf = userData.getSuffixesToReplicateOptions();

    boolean createSuffix =
        repl.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY
            || repl.getType() == DataReplicationOptions.Type.STANDALONE
            || suf.getType() == SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;

    if (createSuffix)
    {
      NewSuffixOptions options = userData.getNewSuffixOptions();
      baseDNs.addAll(options.getBaseDns());
    }
    else
    {
      Set<SuffixDescriptor> suffixes = suf.getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        baseDNs.add(suffix.getDN());
      }
    }
    return baseDNs;
  }

  private static Map<ServerDescriptor, Set<String>> getServerDescriptorBaseDNMap(UserData userData)
  {
    Map<ServerDescriptor, Set<String>> hm = new HashMap<>();

    Set<SuffixDescriptor> suffixes = userData.getSuffixesToReplicateOptions().getSuffixes();
    AuthenticationData authData = userData.getReplicationOptions().getAuthenticationData();
    String ldapURL = ConnectionUtils.getLDAPUrl(authData.getHostPort(), authData.useSecureConnection());
    for (SuffixDescriptor suffix : suffixes)
    {
      boolean found = false;
      for (ReplicaDescriptor replica : suffix.getReplicas())
      {
        if (ldapURL.equalsIgnoreCase(replica.getServer().getAdminConnectorURL()))
        {
          // This is the server we're configuring
          found = true;
          Set<String> baseDNs = hm.get(replica.getServer());
          if (baseDNs == null)
          {
            baseDNs = new LinkedHashSet<>();
            hm.put(replica.getServer(), baseDNs);
          }
          baseDNs.add(suffix.getDN());
          break;
        }
      }
      if (!found)
      {
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          if (hm.keySet().contains(replica.getServer()))
          {
            hm.get(replica.getServer()).add(suffix.getDN());
            found = true;
            break;
          }
        }
      }
      if (!found)
      {
        // We haven't found the server yet, just take the first one
        ReplicaDescriptor replica = suffix.getReplicas().iterator().next();
        if (replica != null)
        {
          Set<String> baseDNs = new LinkedHashSet<>();
          hm.put(replica.getServer(), baseDNs);
          baseDNs.add(suffix.getDN());
        }
      }
    }
    return hm;
  }

  /**
   * Returns the equivalent dsconfig command-line required to configure the
   * first replicated server in the topology.
   *
   * @param userData
   *          the user data.
   * @return the equivalent dsconfig command-line required to configure the
   *         first replicated server in the topology.
   */
  public static List<List<String>> getDsConfigReplicationEnableEquivalentCommandLines(UserData userData)
  {
    final List<List<String>> cmdLines = new ArrayList<>();
    final String cmdName = getCommandLinePath("dsconfig");

    List<String> connectionArgs = newArrayList(
        "--hostName", userData.getHostName(),
        "--port", String.valueOf(userData.getAdminConnectorPort()),
        "--bindDN", userData.getDirectoryManagerDn(),
        "--bindPassword", OBFUSCATED_VALUE,
        "--trustAll",
        "--no-prompt",
        "--noPropertiesFile");

    List<String> cmdReplicationServer = newArrayList(
        cmdName,
        "create-replication-server",
        "--provider-name", "Multimaster Synchronization",
        "--set", "replication-port:" + userData.getReplicationOptions().getReplicationPort(),
        "--set", "replication-server-id:1",
        "--type", "generic");
    cmdReplicationServer.addAll(connectionArgs);

    cmdLines.add(cmdReplicationServer);
    return cmdLines;
  }
}

/**
 * This class is used to avoid displaying the error message related to display
 * problems that we might have when trying to display the SplashWindow.
 */
class EmptyPrintStream extends PrintStream
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Default constructor. */
  public EmptyPrintStream()
  {
    super(new ByteArrayOutputStream(), true);
  }

  @Override
  public void println(String msg)
  {
    logger.info(LocalizableMessage.raw("EmptyStream msg: " + msg));
  }
}
