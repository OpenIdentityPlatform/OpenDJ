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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.quicksetup.util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.installer.webstart.JnlpProperties;


/**
 * This class provides some static convenience methods of different nature.
 *
 */
public class Utils
{
  private static final int BUFFER_SIZE = 1024;

  private static final int MAX_LINE_WIDTH = 80;

  private static final String[] OPEN_DS_JAR_RELATIVE_PATHS =
    { "lib/quicksetup.jar", "lib/OpenDS.jar", "lib/je.jar" };

  /**
   * The relative path where all the binaries (scripts) are.
   */
  private static final String BINARIES_PATH_RELATIVE = "bin";

  /**
   * The relative path where all the libraries (jar files) are.
   */
  private static final String LIBRARIES_PATH_RELATIVE = "lib";

  /**
   * The relative path where the database files are.
   */
  private static final String DATABASES_PATH_RELATIVE = "db";

  /**
   * The relative path where the log files are.
   */
  private static final String LOGS_PATH_RELATIVE = "logs";

  /**
   * The relative path where the LDIF files are.
   */
  private static final String LDIFS_PATH_RELATIVE = "ldif";

  /**
   * The relative path where the backup files are.
   */
  private static final String BACKUPS_PATH_RELATIVE = "bak";

  /**
   * The relative path where the config files are.
   */
  private static final String CONFIG_PATH_RELATIVE = "config";

  /**
   * The relative path to the Configuration LDIF file.
   */
  private static final String CONFIG_FILE_PATH_RELATIVE = "config/config.ldif";

  /**
   * The UNIX setup script file name.
   */
  private static final String UNIX_SETUP_FILE_NAME = "setup";

  /**
   * The Windows setup batch file name.
   */
  private static final String WINDOWS_SETUP_FILE_NAME = "setup.bat";

  /**
   * The UNIX uninstall script file name.
   */
  private static final String UNIX_UNINSTALL_FILE_NAME = "uninstall";

  /**
   * The Windows uninstall batch file name.
   */
  private static final String WINDOWS_UNINSTALL_FILE_NAME = "uninstall.bat";

  /**
   * The UNIX start script file name.
   */
  private static final String UNIX_START_FILE_NAME = "start-ds";

  /**
   * The Windows start batch file name.
   */
  private static final String WINDOWS_START_FILE_NAME = "start-ds.bat";

  /**
   * The UNIX stop script file name.
   */
  private static final String UNIX_STOP_FILE_NAME = "stop-ds";

  /**
   * The Windows stop batch file name.
   */
  private static final String WINDOWS_STOP_FILE_NAME = "stop-ds.bat";

  /**
   * The UNIX status panel script file name.
   */
  private static final String UNIX_STATUSPANEL_FILE_NAME = "statuspanel";

  /**
   * The Windows status panel batch file name.
   */
  private static final String WINDOWS_STATUSPANEL_FILE_NAME = "statuspanel.bat";

  /**
   * The UNIX status command line script file name.
   */
  private static final String UNIX_STATUSCLI_FILE_NAME = "status";

  /**
   * The Windows status command line batch file name.
   */
  private static final String WINDOWS_STATUSCLI_FILE_NAME = "status.bat";

  private Utils()
  {
  }

  /**
   * Center the component location based on its preferred size. The code
   * considers the particular case of 2 screens and puts the component on the
   * center of the left screen
   *
   * @param comp the component to be centered.
   */
  public static void centerOnScreen(Component comp)
  {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    int width = (int) comp.getPreferredSize().getWidth();
    int height = (int) comp.getPreferredSize().getHeight();

    boolean multipleScreen = screenSize.width / screenSize.height >= 2;

    if (multipleScreen)
    {
      comp.setLocation((screenSize.width / 4) - (width / 2),
          (screenSize.height - height) / 2);
    } else
    {
      comp.setLocation((screenSize.width - width) / 2,
          (screenSize.height - height) / 2);
    }
  }

  /**
   * Center the component location of the ref component.
   *
   * @param comp the component to be centered.
   * @param ref the component to be used as reference.
   *
   */
  public static void centerOnComponent(Window comp, Component ref)
  {
    comp.setLocationRelativeTo(ref);
  }

  /**
   * Returns <CODE>true</CODE> if the provided port is free and we can use it,
   * <CODE>false</CODE> otherwise.
   * @param port the port we are analyzing.
   * @return <CODE>true</CODE> if the provided port is free and we can use it,
   * <CODE>false</CODE> otherwise.
   */
  public static boolean canUseAsPort(int port)
  {
    boolean canUseAsPort = false;
    ServerSocket serverSocket = null;
    try
    {
      InetSocketAddress socketAddress = new InetSocketAddress(port);
      serverSocket = new ServerSocket();
      if (!isWindows())
      {
        serverSocket.setReuseAddress(true);
      }
      serverSocket.bind(socketAddress);
      canUseAsPort = true;

      serverSocket.close();

      /* Try to create a socket because sometimes even if we can create a server
       * socket there is already someone listening to the port (is the case
       * of products as Sun DS 6.0).
       */
      try
      {
        new Socket("localhost", port);
        canUseAsPort = false;

      } catch (IOException ioe)
      {
      }

    } catch (IOException ex)
    {
      canUseAsPort = false;
    } finally
    {
      try
      {
        if (serverSocket != null)
        {
          serverSocket.close();
        }
      } catch (Exception ex)
      {
      }
    }

    return canUseAsPort;
  }

  /**
   * Returns <CODE>true</CODE> if the provided port is a priviledged port,
   * <CODE>false</CODE> otherwise.
   * @param port the port we are analyzing.
   * @return <CODE>true</CODE> if the provided port is a priviledged port,
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isPriviledgedPort(int port)
  {
    return (port <= 1024) && !isWindows();
  }

  /**
   * Returns the absolute path for the given parentPath and relativePath.
   * @param parentPath the parent path.
   * @param relativePath the relative path.
   * @return the absolute path for the given parentPath and relativePath.
   */
  public static String getPath(String parentPath, String relativePath)
  {
    File f = new File(new File(parentPath), relativePath);
    try
    {
      /*
       * Do a best effort to avoid having a relative representation (for
       * instance to avoid having ../../../).
       */
      File canonical = f.getCanonicalFile();
      f = canonical;
    }
    catch (IOException ioe)
    {
      /* This is a best effort to get the best possible representation of the
       * file: reporting the error is not necessary.
       */
    }
    return f.toString();
  }

  /**
   * Returns <CODE>true</CODE> if the first provided path is under the second
   * path in the file system.
   * @param descendant the descendant candidate path.
   * @param path the path.
   * @return <CODE>true</CODE> if the first provided path is under the second
   * path in the file system.
   */
  public static boolean isDescendant(String descendant, String path)
  {
    boolean isDescendant = false;
    File f1;
    File f2;

    try
    {
      f1 = (new File(path)).getCanonicalFile();
    }
    catch (IOException ioe)
    {
      f1 = new File(path);
    }

    try
    {
      f2 = (new File(descendant)).getCanonicalFile();
    }
    catch (IOException ioe)
    {
      f2 = new File(descendant);
    }

    f2 = f2.getParentFile();

    while ((f2 != null) && !isDescendant)
    {
      isDescendant = f1.equals(f2);

      if (!isDescendant)
      {
        f2 = f2.getParentFile();
      }
    }
    return isDescendant;
  }

  /**
   * Returns <CODE>true</CODE> if we are running under windows and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we are running under windows and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isWindows()
  {
    return containsOsProperty("windows");
  }

  /**
   * Returns <CODE>true</CODE> if we are running under Mac OS and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we are running under Mac OS and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isMacOS()
  {
    return containsOsProperty("mac os");
  }

  /**
   * Returns <CODE>true</CODE> if we are running under Unix and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we are running under Unix and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isUnix()
  {
    return !isWindows();
  }

  /**
   * Returns <CODE>true</CODE> if the parent directory for the provided path
   * exists and <CODE>false</CODE> otherwise.
   * @param path the path that we are analyzing.
   * @return <CODE>true</CODE> if the parent directory for the provided path
   * exists and <CODE>false</CODE> otherwise.
   */
  public static boolean parentDirectoryExists(String path)
  {
    boolean parentExists = false;
    File f = new File(path);
    if (f != null)
    {
      File parentFile = f.getParentFile();
      if (parentFile != null)
      {
        parentExists = parentFile.isDirectory();
      }
    }
    return parentExists;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided path is a file and exists and
   * <CODE>false</CODE> otherwise.
   * @param path the path that we are analyzing.
   * @return <CODE>true</CODE> if the the provided path is a file and exists and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean fileExists(String path)
  {
    boolean isFile = false;
    File f = new File(path);
    if (f != null)
    {
      isFile = f.isFile();
    }
    return isFile;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided path is a directory, exists
   * and is not empty <CODE>false</CODE> otherwise.
   * @param path the path that we are analyzing.
   * @return <CODE>true</CODE> if the the provided path is a directory, exists
   * and is not empty <CODE>false</CODE> otherwise.
   */
  public static boolean directoryExistsAndIsNotEmpty(String path)
  {
    boolean directoryExistsAndIsNotEmpty = false;
    boolean isDirectory = false;

    File f = new File(path);
    if (f != null)
    {
      isDirectory = f.isDirectory();
    }
    if (isDirectory)
    {
      String[] ch = f.list();

      directoryExistsAndIsNotEmpty = (ch != null) && (ch.length > 0);
    }

    return directoryExistsAndIsNotEmpty;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided string is a DN and
   * <CODE>false</CODE> otherwise.
   * @param dn the String we are analyzing.
   * @return <CODE>true</CODE> if the the provided string is a DN and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isDn(String dn)
  {
    boolean isDn = true;
    try
    {
      new LdapName(dn);
    } catch (Exception ex)
    {
      isDn = false;
    }
    return isDn;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided string is a configuration DN
   * and <CODE>false</CODE> otherwise.
   * @param dn the String we are analyzing.
   * @return <CODE>true</CODE> if the the provided string is a configuration DN
   * and <CODE>false</CODE> otherwise.
   */
  public static boolean isConfigurationDn(String dn)
  {
    boolean isConfigurationDn = false;
    String[] configDns =
      { "cn=config", "cn=schema" };
    for (int i = 0; i < configDns.length && !isConfigurationDn; i++)
    {
      isConfigurationDn = areDnsEqual(dn, configDns[i]);
    }
    return isConfigurationDn;
  }

  /**
   * Returns <CODE>true</CODE> if the the provided strings represent the same
   * DN and <CODE>false</CODE> otherwise.
   * @param dn1 the first dn to compare.
   * @param dn2 the second dn to compare.
   * @return <CODE>true</CODE> if the the provided strings represent the same
   * DN and <CODE>false</CODE> otherwise.
   */
  public static boolean areDnsEqual(String dn1, String dn2)
  {
    boolean areDnsEqual = false;
    try
    {
      LdapName name1 = new LdapName(dn1);
      LdapName name2 = new LdapName(dn2);
      areDnsEqual = name1.equals(name2);
    } catch (Exception ex)
    {
    }

    return areDnsEqual;
  }

  /**
   * Creates the parent path for the provided path.
   * @param path the path.
   * @return <CODE>true</CODE> if the parent path was created or already existed
   * and <CODE>false</CODE> otherwise.
   */
  public static boolean createParentPath(String path)
  {
    boolean parentPathExists = true;
    if (!parentDirectoryExists(path))
    {
      File f = new File(path);
      if (f != null)
      {
        File parentFile = f.getParentFile();
        parentPathExists = parentFile.mkdirs();
      }
    }
    return parentPathExists;
  }

  /**
   * Returns <CODE>true</CODE> if we can write on the provided path and
   * <CODE>false</CODE> otherwise.
   * @param path the path.
   * @return <CODE>true</CODE> if we can write on the provided path and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean canWrite(String path)
  {
    boolean canWrite;
    File file = new File(path);
    if (file.exists())
    {
      canWrite = file.canWrite();
    } else
    {
      File parentFile = file.getParentFile();
      if (parentFile != null)
      {
        canWrite = parentFile.canWrite();
      } else
      {
        canWrite = false;
      }
    }
    return canWrite;
  }

  /**
   * Creates the a directory in the provided path.
   * @param path the path.
   * @return <CODE>true</CODE> if the path was created or already existed (and
   * was a directory) and <CODE>false</CODE> otherwise.
   * @throws IOException if something goes wrong.
   */
  public static boolean createDirectory(String path) throws IOException
  {
    boolean directoryCreated;
    File f = new File(path);
    if (!f.exists())
    {
      directoryCreated = f.mkdirs();
    } else
    {
      directoryCreated = f.isDirectory();
    }
    return directoryCreated;
  }

  /**
   * Creates a file on the specified path with the contents of the provided
   * stream.
   * @param path the path where the file will be created.
   * @param is the InputStream with the contents of the file.
   * @throws IOException if something goes wrong.
   */
  public static void createFile(String path, InputStream is) throws IOException
  {
    FileOutputStream out;
    BufferedOutputStream dest;
    byte[] data = new byte[BUFFER_SIZE];
    int count;

    out = new FileOutputStream(path);

    dest = new BufferedOutputStream(out);

    while ((count = is.read(data, 0, BUFFER_SIZE)) != -1)
    {
      dest.write(data, 0, count);
    }
    dest.flush();
    dest.close();
  }

  /**
   * Creates a file on the specified path with the contents of the provided
   * String.
   * @param path the path where the file will be created.
   * @param content the String with the contents of the file.
   * @throws IOException if something goes wrong.
   */
  public static void createFile(String path, String content) throws IOException
  {
    FileWriter file = new FileWriter(path);
    PrintWriter out = new PrintWriter(file);

    out.println(content);

    out.flush();
    out.close();
  }

  /**
   * This is a helper method that gets a String representation of the elements
   * in the Collection. The String will display the different elements separated
   * by the separator String.
   *
   * @param col
   *          the collection containing the String.
   * @param separator
   *          the separator String to be used.
   * @return the String representation for the collection.
   */
  public static String getStringFromCollection(Collection<String> col,
      String separator)
  {
    String msg = null;
    for (String m : col)
    {
      if (msg == null)
      {
        msg = m;
      } else
      {
        msg += separator + m;
      }
    }
    return msg;
  }

  /**
   * Returns the default server location that will be proposed to the user
   * in the installation.
   * @return the default server location that will be proposed to the user
   * in the installation.
   */
  public static String getDefaultServerLocation()
  {
    String userDir = System.getProperty("user.home");
    String firstLocation =
        userDir + File.separator
            + org.opends.server.util.DynamicConstants.COMPACT_VERSION_STRING;
    String serverLocation = firstLocation;
    int i = 1;
    while (fileExists(serverLocation)
        || directoryExistsAndIsNotEmpty(serverLocation))
    {
      serverLocation = firstLocation + "-" + i;
      i++;
    }
    return serverLocation;
  }

  /**
   * Returns <CODE>true</CODE> if there is more disk space in the provided path
   * than what is specified with the bytes parameter.
   * @param directoryPath the path.
   * @param bytes the disk space.
   * @return <CODE>true</CODE> if there is more disk space in the provided path
   * than what is specified with the bytes parameter.
   */
  public static synchronized boolean hasEnoughSpace(String directoryPath,
      long bytes)
  {
    // TODO This does not work with quotas etc. but at least it seems that
    // we do not write all data on disk if it fails.
    boolean hasEnoughSpace = false;
    File file = null;
    RandomAccessFile raf = null;
    File directory = new File(directoryPath);
    boolean deleteDirectory = false;
    if (!directory.exists())
    {
      deleteDirectory = directory.mkdir();
    }
    try
    {
      file = File.createTempFile("temp" + System.nanoTime(), ".tmp", directory);
      raf = new RandomAccessFile(file, "rw");
      raf.setLength(bytes);
      hasEnoughSpace = true;
    } catch (IOException ex)
    {
    } finally
    {
      if (raf != null)
      {
        try
        {
          raf.close();
        } catch (IOException ex2)
        {
        }
      }
      if (file != null)
      {
        file.delete();
      }
    }
    if (deleteDirectory)
    {
      directory.delete();
    }
    return hasEnoughSpace;
  }

  /**
   * Returns a localized message for a given properties key an throwable.
   * @param key the key of the message in the properties file.
   * @param i18n the ResourceProvider to be used.
   * @param args the arguments of the message in the properties file.
   * @param t the throwable for which we want to get a message.
   *
   * @return a localized message for a given properties key and throwable.
   */
  public static String getThrowableMsg(ResourceProvider i18n, String key,
      String[] args, Throwable t)
  {
    String msg;
    if (args != null)
    {
      msg = i18n.getMsg(key, args);
    } else
    {
      msg = i18n.getMsg(key);
    }

    String detail = t.toString();
    if (detail != null)
    {
      String[] arg =
        { detail };
      msg = msg + "  " + i18n.getMsg("exception-details", arg);
    }
    return msg;
  }

  /**
   * Commodity method to help identifying the OS we are running on.
   * @param s the String that represents an OS.
   * @return <CODE>true</CODE> if there is os java property exists and contains
   * the value specified in s, <CODE>false</CODE> otherwise.
   */
  private static boolean containsOsProperty(String s)
  {
    boolean containsOsProperty = false;

    String osName = System.getProperty("os.name");
    if (osName != null)
    {
      containsOsProperty = osName.toLowerCase().indexOf(s) != -1;
    }

    return containsOsProperty;
  }

  /**
   * Sets the permissions of the provided paths with the provided permission
   * String.
   * @param paths the paths to set permissions on.
   * @param permissions the UNIX-mode file system permission representation
   * (for example "644" or "755")
   * @return the return code of the chmod command.
   * @throws IOException if something goes wrong.
   * @throws InterruptedException if the Runtime.exec method is interrupted.
   */
  public static int setPermissionsUnix(ArrayList<String> paths,
      String permissions) throws IOException, InterruptedException
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

  // Very limited for the moment: apply only permissions to the current user and
  // does not work in non-English environments... to work in non English we
  // should use xcalcs but it does not come in the windows default install...
  // :-(
  // This method is not called for the moment, but the code works, so that is
  // why
  // is kept.
  private static int changePermissionsWindows(String path, String unixPerm)
      throws IOException, InterruptedException
  {
    String windowsPerm;
    int i = Integer.parseInt(unixPerm.substring(0, 1));
    if (Integer.lowestOneBit(i) == 1)
    {
      // Executable: give full permissions
      windowsPerm = "F";
    } else if (Integer.highestOneBit(i) == 4)
    {
      // Writable
      windowsPerm = "W";
    } else if (Integer.highestOneBit(i) == 2)
    {
      // Readable
      windowsPerm = "R";
    } else
    {
      // No permissions
      windowsPerm = "N";
    }

    String user = System.getProperty("user.name");
    String[] args =
      { "cacls", path, "/P", user + ":" + windowsPerm };
    Process p = Runtime.getRuntime().exec(args);

    // TODO: This only works in ENGLISH systems!!!!!!
    p.getOutputStream().write("Y\n".getBytes());
    p.getOutputStream().flush();
    return p.waitFor();
  }

  /**
   * Indicates whether we are in a web start installation or not.
   *
   * @return <CODE>true</CODE> if we are in a web start installation and
   *         <CODE>false</CODE> if not.
   */
  public static boolean isWebStart()
  {
    return "true".equals(System.getProperty(JnlpProperties.IS_WEBSTART));
  }

  /**
   * Returns <CODE>true</CODE> if this is an uninstallation and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if this is an uninstallation and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isUninstall()
  {
    return "true".equals(System.getProperty("org.opends.quicksetup.uninstall"));
  }

  /**
   * Returns <CODE>true</CODE> if this is executed from command line and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if this is executed from command line and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isCli()
  {
    return "true".equals(System.getProperty("org.opends.quicksetup.cli"));
  }

  /**
   * Creates a clear LDAP connection and returns the corresponding LdapContext.
   * This methods uses the specified parameters to create a JNDI environment
   * hashtable and creates an InitialLdapContext instance.
   *
   * @param ldapURL
   *          the target LDAP URL
   * @param dn
   *          passed as Context.SECURITY_PRINCIPAL if not null
   * @param pwd
   *          passed as Context.SECURITY_CREDENTIALS if not null
   * @param timeout
   *          passed as com.sun.jndi.ldap.connect.timeout if > 0
   * @param env
   *          null or additional environment properties
   *
   * @throws NamingException
   *           the exception thrown when instantiating InitialLdapContext
   *
   * @return the created InitialLdapContext.
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   */
  public static InitialLdapContext createLdapContext(String ldapURL, String dn,
      String pwd, int timeout, Hashtable<String, String> env)
      throws NamingException
  {
    if (env != null)
    { // We clone 'env' so that we can modify it freely
      env = new Hashtable<String, String>(env);
    } else
    {
      env = new Hashtable<String, String>();
    }
    env
        .put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapURL);
    if (timeout >= 1)
    {
      env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(timeout));
    }
    if (dn != null)
    {
      env.put(Context.SECURITY_PRINCIPAL, dn);
    }
    if (pwd != null)
    {
      env.put(Context.SECURITY_CREDENTIALS, pwd);
    }

    /* Contains the DirContext and the Exception if any */
    final Object[] pair = new Object[]
      { null, null };
    final Hashtable fEnv = env;
    Thread t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          pair[0] = new InitialLdapContext(fEnv, null);

        } catch (NamingException ne)
        {
          pair[1] = ne;

        } catch (Throwable t)
        {
          pair[1] = t;
        }
      }
    });
    return getInitialLdapContext(t, pair, timeout);
  }

  /**
   * Method used to know if we can connect as administrator in a server with a
   * given password and dn.
   * @param ldapUrl the ldap URL of the server.
   * @param dn the dn to be used.
   * @param pwd the password to be used.
   * @return <CODE>true</CODE> if we can connect and read the configuration and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean canConnectAsAdministrativeUser(String ldapUrl,
      String dn, String pwd)
  {
    boolean canConnectAsAdministrativeUser = false;
    try
    {
      InitialLdapContext ctx =
        Utils.createLdapContext(ldapUrl, dn, pwd, 3000, null);

      /*
       * Search for the config to check that it is the directory manager.
       */
      SearchControls searchControls = new SearchControls();
      searchControls.setCountLimit(1);
      searchControls.setSearchScope(
      SearchControls. OBJECT_SCOPE);
      searchControls.setReturningAttributes(
      new String[] {"dn"});
      ctx.search("cn=config", "objectclass=*", searchControls);

      canConnectAsAdministrativeUser = true;
    } catch (NamingException ne)
    {
      // Nothing to do.
    } catch (Throwable t)
    {
      throw new IllegalStateException("Unexpected throwable.", t);
    }
    return canConnectAsAdministrativeUser;
  }

  /**
   * Returns the path of the installation of the directory server.  Note that
   * this method assumes that this code is being run locally.
   * @return the path of the installation of the directory server.
   */
  public static String getInstallPathFromClasspath()
  {
    String installPath;

    /* Get the install path from the Class Path */
    String sep = System.getProperty("path.separator");
    String[] classPaths = System.getProperty("java.class.path").split(sep);
    String path = null;
    for (int i = 0; i < classPaths.length && (path == null); i++)
    {
      for (int j = 0; j < OPEN_DS_JAR_RELATIVE_PATHS.length &&
      (path == null); j++)
      {
        String normPath = classPaths[i].replace(File.separatorChar, '/');
        if (normPath.endsWith(OPEN_DS_JAR_RELATIVE_PATHS[j]))
        {
          path = classPaths[i];
        }
      }
    }
    File f = new File(path).getAbsoluteFile();
    File binariesDir = f.getParentFile();

    /*
     * Do a best effort to avoid having a relative representation (for
     * instance to avoid having ../../../).
     */
    try
    {
      installPath = binariesDir.getParentFile().getCanonicalPath();
    }
    catch (IOException ioe)
    {
      // Best effort
      installPath = binariesDir.getParent();
    }
    return installPath;
  }

  /**
   * Returns the path to the configuration file of the directory server.  Note
   * that this method assumes that this code is being run locally.
   * @return the path of the configuration file of the directory server.
   */
  public static String getConfigFileFromClasspath()
  {
    return getPath(getInstallPathFromClasspath(), CONFIG_FILE_PATH_RELATIVE);
  }

  /**
   * Returns the list of jar files that might be used to execute the code of
   * the installation and uninstallation.
   * @return the list of jar files that might be used to execute the code of
   * the installation and uninstallation.
   */
  public static String[] getOpenDSJarPaths()
  {
    return OPEN_DS_JAR_RELATIVE_PATHS;
  }


  /**
   * Returns the relative path of the directory containing the binaries of the
   * Open DS installation.  The path is relative to the installation path.
   * @return the relative path of the directory containing the binaries of the
   * Open DS installation.
   */
  public static String getBinariesRelativePath()
  {
    return BINARIES_PATH_RELATIVE;
  }

  /**
   * Returns the relative path of the directory containing the libraries of the
   * Open DS installation.  The path is relative to the installation path.
   * @return the relative path of the directory containing the libraries of the
   * Open DS installation.
   */
  public static String getLibrariesRelativePath()
  {
    return LIBRARIES_PATH_RELATIVE;
  }

  /**
   * Returns the relative path of the directory containing the databases of the
   * Open DS installation.  The path is relative to the installation path.
   * @return the relative path of the directory containing the databases of the
   * Open DS installation.
   */
  public static String getDatabasesRelativePath()
  {
    return DATABASES_PATH_RELATIVE;
  }

  /**
   * Returns the relative path of the directory containing the logs of the
   * Open DS installation.  The path is relative to the installation path.
   * @return the relative path of the directory containing the logs of the
   * Open DS installation.
   */
  public static String getLogsRelativePath()
  {
    return LOGS_PATH_RELATIVE;
  }

  /**
   * Returns the relative path of the directory containing the LDIF files of the
   * Open DS installation.  The path is relative to the installation path.
   * @return the relative path of the directory containing the LDIF files of the
   * Open DS installation.
   */
  public static String getLDIFsRelativePath()
  {
    return LDIFS_PATH_RELATIVE;
  }


  /**
   * Returns the relative path of the directory containing the backup files of
   * the Open DS installation.  The path is relative to the installation path.
   * @return the relative path of the directory containing the backup files of
   * the Open DS installation.
   */
  public static String getBackupsRelativePath()
  {
    return BACKUPS_PATH_RELATIVE;
  }

  /**
   * Returns the relative path of the directory containing the config files of
   * the Open DS installation.  The path is relative to the installation path.
   * @return the relative path of the directory containing the config files of
   * the Open DS installation.
   */
  public static String getConfigRelativePath()
  {
    return CONFIG_PATH_RELATIVE;
  }

  /**
   * Returns the name of the UNIX setup script file name.
   * @return the name of the UNIX setup script file name.
   */
  public static String getUnixSetupFileName()
  {
    return UNIX_SETUP_FILE_NAME;
  }

  /**
   * Returns the name of the Windows setup batch file name.
   * @return the name of the Windows setup batch file name.
   */
  public static String getWindowsSetupFileName()
  {
    return WINDOWS_SETUP_FILE_NAME;
  }

  /**
   * Returns the name of the UNIX uninstall script file name.
   * @return the name of the UNIX uninstall script file name.
   */
  public static String getUnixUninstallFileName()
  {
    return UNIX_UNINSTALL_FILE_NAME;
  }

  /**
   * Returns the name of the Windows uninstall batch file name.
   * @return the name of the Windows uninstall batch file name.
   */
  public static String getWindowsUninstallFileName()
  {
    return WINDOWS_UNINSTALL_FILE_NAME;
  }

  /**
   * Returns the name of the UNIX start script file name.
   * @return the name of the UNIX start script file name.
   */
  public static String getUnixStartFileName()
  {
    return UNIX_START_FILE_NAME;
  }

  /**
   * Returns the name of the Windows start batch file name.
   * @return the name of the Windows start batch file name.
   */
  public static String getWindowsStartFileName()
  {
    return WINDOWS_START_FILE_NAME;
  }

  /**
   * Returns the name of the UNIX stop script file name.
   * @return the name of the UNIX stop script file name.
   */
  public static String getUnixStopFileName()
  {
    return UNIX_STOP_FILE_NAME;
  }

  /**
   * Returns the name of the Windows stop batch file name.
   * @return the name of the Windows stop batch file name.
   */
  public static String getWindowsStopFileName()
  {
    return WINDOWS_STOP_FILE_NAME;
  }

  /**
   * Returns the name of the UNIX status panel script file name.
   * @return the name of the UNIX status panel script file name.
   */
  public static String getUnixStatusPanelFileName()
  {
    return UNIX_STATUSPANEL_FILE_NAME;
  }

  /**
   * Returns the name of the Windows status panel batch file name.
   * @return the name of the Windows status panel batch file name.
   */
  public static String getWindowsStatusPanelFileName()
  {
    return WINDOWS_STATUSPANEL_FILE_NAME;
  }

  /**
   * Returns the name of the UNIX status command line script file name.
   * @return the name of the UNIX status command line script file name.
   */
  public static String getUnixStatusCliFileName()
  {
    return UNIX_STATUSCLI_FILE_NAME;
  }

  /**
   * Returns the name of the Windows status command line batch file name.
   * @return the name of the Windows status command line batch file name.
   */
  public static String getWindowsStatusCliFileName()
  {
    return WINDOWS_STATUSCLI_FILE_NAME;
  }

  /**
   * Displays a confirmation message dialog.
  *
  * @param parent
   *          the parent frame of the confirmation dialog.
   * @param msg
  *          the confirmation message.
  * @param title
  *          the title of the dialog.
  * @return <CODE>true</CODE> if the user confirms the message, or
  * <CODE>false</CODE> if not.
  */
 public static boolean displayConfirmation(JFrame parent, String msg,
     String title)
 {
   return JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(
       parent, msg, title, JOptionPane.YES_NO_OPTION,
       JOptionPane.QUESTION_MESSAGE, null, // don't use a custom
       // Icon
       null, // the titles of buttons
       null); // default button title
 }

  /**
   * Displays an error message dialog.
   *
   * @param parent
   *          the parent frame of the error dialog.
   * @param msg
   *          the error message.
   * @param title
   *          the title for the dialog.
   */
  public static void displayError(JFrame parent, String msg, String title)
  {
    JOptionPane.showMessageDialog(parent, msg, title,
        JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Displays an information message dialog.
   *
   * @param parent
   *          the parent frame of the information dialog.
   * @param msg
   *          the error message.
   * @param title
   *          the title for the dialog.
   */
  public static void displayInformationMessage(JFrame parent, String msg,
      String title)
  {
    JOptionPane.showMessageDialog(parent, msg, title,
        JOptionPane.INFORMATION_MESSAGE);
  }

  /**
   * Returns a Set of relative paths containing the db paths outside the
   * installation.
   * @param installStatus the Current Install Status object.
   * @return a Set of relative paths containing the db paths outside the
   * installation.
   */
  public static Set<String> getOutsideDbs(CurrentInstallStatus installStatus)
  {
    String installPath = getInstallPathFromClasspath();
    Set<String> dbs = installStatus.getDatabasePaths();
    Set<String> outsideDbs = new HashSet<String>();
    for (String relativePath : dbs)
    {
      /* The db paths are relative */
      String fullDbPath = getPath(installPath, relativePath);
      if (!isDescendant(fullDbPath, installPath))
      {
        outsideDbs.add(fullDbPath);
      }
    }
    return outsideDbs;
  }

  /**
   * Returns a Set of relative paths containing the log paths outside the
   * installation.
   * @param installStatus the Current Install Status object.
   * @return a Set of relative paths containing the log paths outside the
   * installation.
   */
  public static Set<String> getOutsideLogs(CurrentInstallStatus installStatus)
  {
    String installPath = getInstallPathFromClasspath();
    Set<String> logs = installStatus.getLogPaths();
    Set<String> outsideLogs = new HashSet<String>();
    for (String relativePath : logs)
    {
      /* The db paths are relative */
      String fullDbPath = getPath(installPath, relativePath);
      if (!isDescendant(fullDbPath, installPath))
      {
        outsideLogs.add(fullDbPath);
      }
    }
    return outsideLogs;
  }

  /**
   * Returns if the server is running on the given path.
   * @param serverPath the installation path of the server.
   * @return <CODE>true</CODE> if the server is running and <CODE>false</CODE>
   * otherwise.
   */
  public static boolean isServerRunning(String serverPath)
  {
    boolean isServerRunning;
    if (isWindows())
    {
      String testPath = serverPath+File.separator+
      "locks"+File.separator+"server.lock";
      File testFile = new File(testPath);

      boolean canWriteFile = false;
      Writer output = null;
      try {
        //use buffering
        //FileWriter always assumes default encoding is OK!
        output = new BufferedWriter( new FileWriter(testFile) );
        output.write("test");
        output.close();
        output = new BufferedWriter( new FileWriter(testFile) );
        output.write("");
        output.close();

        canWriteFile = true;

      }
      catch (Throwable t)
      {
      }
      finally
      {
        if (output != null)
        {
          try
          {
            output.close();
          }
          catch (Throwable t)
          {
          }
        }
      }
      isServerRunning = !canWriteFile;
    }
    else
    {
      isServerRunning = fileExists(serverPath+File.separator+
          "logs"+File.separator+"server.pid");
    }
    return isServerRunning;
  }

  /**
   * This is just a commodity method used to try to get an InitialLdapContext.
   * @param t the Thread to be used to create the InitialLdapContext.
   * @param pair an Object[] array that contains the InitialLdapContext and the
   * Throwable if any occurred.
   * @param timeout the timeout.  If we do not get to create the connection
   * before the timeout a CommunicationException will be thrown.
   * @return the created InitialLdapContext
   * @throws NamingException if something goes wrong during the creation.
   */
  private static InitialLdapContext getInitialLdapContext(Thread t,
      Object[] pair, int timeout) throws NamingException
  {
    try
    {
      if (timeout > 0)
      {
        t.start();
        t.join(timeout);
      } else
      {
        t.run();
      }

    } catch (InterruptedException x)
    {
      // This might happen for problems in sockets
      // so it does not necessarily imply a bug
    }

    boolean throwException = false;

    if ((timeout > 0) && t.isAlive())
    {
      t.interrupt();
      try
      {
        t.join(2000);
      } catch (InterruptedException x)
      {
        // This might happen for problems in sockets
        // so it does not necessarily imply a bug
      }
      throwException = true;
    }

    if ((pair[0] == null) && (pair[1] == null))
    {
      throwException = true;
    }

    if (throwException)
    {
      NamingException xx;
      ConnectException x = new ConnectException("Connection timed out");
      xx = new CommunicationException("Connection timed out");
      xx.initCause(x);
      throw xx;
    }

    if (pair[1] != null)
    {
      if (pair[1] instanceof NamingException)
      {
        throw (NamingException) pair[1];

      } else if (pair[1] instanceof RuntimeException)
      {
        throw (RuntimeException) pair[1];

      } else if (pair[1] instanceof Throwable)
      {
        throw new IllegalStateException("Unexpected throwable occurred",
            (Throwable) pair[1]);
      }
    }
    return (InitialLdapContext) pair[0];
  }

  /**
   * Returns the max size in character of a line to be displayed in the command
   * line.
   * @return the max size in character of a line to be displayed in the command
   * line.
   */
  public static int getCommandLineMaxLineWidth()
  {
    return MAX_LINE_WIDTH;
  }

  /**
   * Puts Swing menus in the Mac OS menu bar, if using the Aqua look and feel,
   * and sets the application name that is displayed in the application menu
   * and in the dock.
   * @param appName
   *          application name to display in the menu bar and the dock.
   */
  public static void setMacOSXMenuBar(String appName)
  {
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty("com.apple.mrj.application.apple.menu.about.name",
                       appName);
  }
}
