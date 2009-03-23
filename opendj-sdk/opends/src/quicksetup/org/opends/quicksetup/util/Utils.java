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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.quicksetup.util;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

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
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.TrustManager;

import org.opends.admin.ads.TopologyCacheException;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.quicksetup.*;

import org.opends.server.util.SetupUtils;
import org.opends.messages.MessageBuilder;
import org.opends.messages.MessageDescriptor;


/**
 * This class provides some static convenience methods of different nature.
 *
 */
public class Utils
{
  private static final Logger LOG =
          Logger.getLogger(Utils.class.getName());

  private static final int BUFFER_SIZE = 1024;

  private static final int MAX_LINE_WIDTH = 80;

  private Utils()
  {
  }

  /**
   * Enumeration that specify if the operation applies to the install directory
   * only, to the instance directory only, or both.
   */
  public static enum  Dir {
    /**
     * all directories.
     */
    ALL,

    /**
     * The install directory.
     */
    INSTALL,

    /***
     * The instance directory.
     */
    INSTANCE;
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
    return SetupUtils.canUseAsPort(port);
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
    return SetupUtils.isPriviledgedPort(port);
  }



  /**
   * Tells whether the provided java installation supports a given option or
   * not.
   * @param javaHome the java installation path.
   * @param option the java option that we want to check.
   * @param installPath the install path of the server.
   * @return <CODE>true</CODE> if the provided java installation supports a
   * given option and <CODE>false</CODE> otherwise.
   */
  public static boolean supportsOption(String option, String javaHome,
      String installPath)
  {
    boolean supported = false;
    try
    {
      List<String> args = new ArrayList<String>();
      String script;
      String libPath = Utils.getPath(installPath,
          Installation.LIBRARIES_PATH_RELATIVE);
      if (Utils.isWindows())
      {
        script = Utils.getScriptPath(Utils.getPath(libPath,
            Installation.SCRIPT_UTIL_FILE_WINDOWS));
      }
      else
      {
        script = Utils.getScriptPath(Utils.getPath(libPath,
            Installation.SCRIPT_UTIL_FILE_UNIX));
      }
      args.add(script);
      ProcessBuilder pb = new ProcessBuilder(args);
      Map<String, String> env = pb.environment();
      env.put(SetupUtils.OPENDS_JAVA_HOME, javaHome);
      env.put("OPENDS_JAVA_ARGS", option);
      env.put("SCRIPT_UTIL_CMD", "set-full-environment-and-test-java");
      env.remove("OPENDS_JAVA_BIN");
      // In windows by default the scripts ask the user to click on enter when
      // they fail.  Set this environment variable to avoid it.
      if (Utils.isWindows())
      {
        env.put("DO_NOT_PAUSE", "true");
      }
      Process process = pb.start();
      LOG.log(Level.INFO, "launching "+args);
      InputStream is = process.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      String line;
      while (null != (line = reader.readLine())) {
        LOG.log(Level.INFO, "The output: "+line);
        if (line.indexOf("ERROR:  The detected Java version") != -1)
        {
          try
          {
            process.destroy();
            return false;
          }
          catch (Throwable t)
          {
            return false;
          }
          finally
          {
          }
        }
      }
      process.waitFor();
      int returnCode = process.exitValue();
      LOG.log(Level.INFO, "returnCode: "+returnCode);
      supported = returnCode == 0;
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error testing option "+option+" on "+javaHome, t);
    }
    return supported;
  }

  /**
   * Creates a new file attempting to create the parent directories
   * if necessary.
   * @param f File to create
   * @return boolean indicating whether the file was created; false otherwise
   * @throws IOException if something goes wrong
   */
  public static boolean createFile(File f) throws IOException {
    boolean success = false;
    if (f != null) {
      File parent = f.getParentFile();
      if (!parent.exists()) {
        parent.mkdirs();
      }
      success = f.createNewFile();
    }
    return success;
  }

  /**
   * Returns the absolute path for the given parentPath and relativePath.
   * @param parentPath the parent path.
   * @param relativePath the relative path.
   * @return the absolute path for the given parentPath and relativePath.
   */
  public static String getPath(String parentPath, String relativePath)
  {
    return getPath(new File(new File(parentPath), relativePath));
  }

  /**
   * Returns the String that can be used to launch an script using Runtime.exec.
   * This method is required because in Windows the script that contain a "="
   * in their path must be quoted.
   * @param script the script name
   * @return the absolute path for the given parentPath and relativePath.
   */
  public static String getScriptPath(String script)
  {
    return SetupUtils.getScriptPath(script);
  }

  /**
   * Returns the absolute path for the given parentPath and relativePath.
   * @param f File to get the path
   * @return the absolute path for the given parentPath and relativePath.
   */
  public static String getPath(File f)
  {
    String path = null;
    if (f != null) {
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
      path = f.toString();
    }
    return path;
  }

  /**
   * Returns <CODE>true</CODE> if the first provided path is under the second
   * path in the file system.
   * @param descendant the descendant candidate path.
   * @param path the path.
   * @return <CODE>true</CODE> if the first provided path is under the second
   * path in the file system; <code>false</code> otherwise or if
   * either of the files are null
   */
  public static boolean isDescendant(File descendant, File path) {
    boolean isDescendant = false;
    if (descendant != null && path != null) {
      File parent = descendant.getParentFile();
      while ((parent != null) && !isDescendant) {
        isDescendant = path.equals(parent);
        if (!isDescendant) {
          parent = parent.getParentFile();
        }
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
    return SetupUtils.isWindows();
  }

  /**
   * Returns <CODE>true</CODE> if we are running under Mac OS and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we are running under Mac OS and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isMacOS()
  {
    return SetupUtils.isMacOS();
  }

  /**
   * Returns <CODE>true</CODE> if we are running under Unix and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if we are running under Unix and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isUnix()
  {
    return SetupUtils.isUnix();
  }

  /**
   * Returns a String representation of the OS we are running.
   * @return a String representation of the OS we are running.
   */
  public static String getOSString()
  {
    return SetupUtils.getOSString();
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
   * Returns <CODE>true</CODE> if the the provided path is a directory, exists
   * and is empty <CODE>false</CODE> otherwise.
   * @param path the path that we are analyzing.
   * @return <CODE>true</CODE> if the the provided path is a directory, exists
   * and is empty <CODE>false</CODE> otherwise.
   */
  public static boolean directoryExistsAndIsEmpty(String path)
  {
    boolean directoryExistsAndIsEmpty = false;
    boolean isDirectory = false;

    File f = new File(path);
    if (f != null)
    {
      isDirectory = f.isDirectory();
    }
    if (isDirectory)
    {
      String[] ch = f.list();

      directoryExistsAndIsEmpty = (ch == null) || (ch.length == 0);
    }

    return directoryExistsAndIsEmpty;
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
      { "cn=config", Constants.SCHEMA_DN };
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
   * Creates the parent directory if it does not already exist.
   * @param f File for which parentage will be insured
   * @return boolean indicating whether or not the input <code>f</code>
   * has a parent after this method is invoked.
   */
  static public boolean insureParentsExist(File f) {
    File parent = f.getParentFile();
    boolean b = parent.exists();
    if (!b) {
      b = parent.mkdirs();
    }
    return b;
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
  public static boolean createDirectory(String path) throws IOException {
    return createDirectory(new File(path));
  }

  /**
   * Creates the a directory in the provided path.
   * @param f the path.
   * @return <CODE>true</CODE> if the path was created or already existed (and
   * was a directory) and <CODE>false</CODE> otherwise.
   * @throws IOException if something goes wrong.
   */
  public static boolean createDirectory(File f) throws IOException
  {
    boolean directoryCreated;
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
  public static void createFile(String path, InputStream is)
          throws IOException
  {
    createFile(new File(path), is);
  }

  /**
   * Creates a file on the specified path with the contents of the provided
   * stream.
   * @param path the path where the file will be created.
   * @param is the InputStream with the contents of the file.
   * @throws IOException if something goes wrong.
   */
  public static void createFile(File path, InputStream is) throws IOException
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
   * String.  The file is protected, so that 'others' have no access to it.
   * @param path the path where the file will be created.
   * @param content the String with the contents of the file.
   * @throws IOException if something goes wrong.
   * @throws InterruptedException if there is a problem changing the permissions
   * of the file.
   */
  public static void createProtectedFile(String path, String content)
  throws IOException, InterruptedException
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
    StringBuilder msg = new StringBuilder();
    for (String m : col)
    {

      if (msg.length() > 0)
      {
        msg.append(separator);
      }
      msg.append(m);
    }
    return msg.toString();
  }

  /**
   * This is a helper method that gets a Message representation of the elements
   * in the Collection of Messages. The Message will display the different
   * elements separated by the separator String.
   *
   * @param col
   *          the collection containing the messages.
   * @param separator
   *          the separator String to be used.
   * @return the message representation for the collection;
   *          null if <code>col</code> is null
   */
  public static Message getMessageFromCollection(Collection<Message> col,
                                                 String separator) {
    Message message = null;
    if (col != null) {
      MessageBuilder mb = null;
      for (Message m : col) {
        if (mb == null) {
          mb = new MessageBuilder(m);
        } else {
          mb.append(separator).append(m);
        }
      }
      if (mb == null) mb = new MessageBuilder();
      message = mb.toMessage();
    }
    return message;
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
            + org.opends.server.util.DynamicConstants.SHORT_NAME;
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
   * @param message prefix
   * @param t the throwable for which we want to get a message.
   *
   * @return a localized message for a given properties key and throwable.
   */
  public static Message getThrowableMsg(Message message, Throwable t)
  {
    MessageBuilder mb = new MessageBuilder(message);
    MessageDescriptor.Arg1<CharSequence> tag;
    if (isOutOfMemory(t))
    {
      tag = INFO_EXCEPTION_OUT_OF_MEMORY_DETAILS;
    }
    else
    {
      tag = INFO_EXCEPTION_DETAILS;
    }
    String detail = t.toString();
    if (detail != null)
    {
      mb.append("  ").append(tag.get(detail));
    }
    return mb.toMessage();
  }

  /**
   * Gets a localized representation of the provide TopologyCacheException.
   * @param te the exception.
   * @return a localized representation of the provide TopologyCacheException.
   */
  public static Message getMessage(TopologyCacheException te)
  {
    MessageBuilder buf = new MessageBuilder();

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
      buf.append(getThrowableMsg(INFO_ERROR_CONNECTING_TO_LOCAL.get(),
          te.getCause()));
    }
    else
    {
      LOG.log(Level.WARNING, "Unexpected error: "+te, te);
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

  /**
   * Sets the permissions of the provided paths with the provided permission
   * String.
   * @param path to set permissions on.
   * @param permissions the UNIX-mode file system permission representation
   * (for example "644" or "755")
   * @return the return code of the chmod command.
   * @throws IOException if something goes wrong.
   * @throws InterruptedException if the Runtime.exec method is interrupted.
   */
  public static int setPermissionsUnix(String path,
      String permissions) throws IOException, InterruptedException
  {
    String[] args = new String[3];
    args[0] = "chmod";
    args[1] = permissions;
    args[2] = path;
    Process p = Runtime.getRuntime().exec(args);
    return p.waitFor();
  }

  /**
   * Returns the String that can be used to represent a given host name in a
   * LDAP URL.
   * This method must be used when we have IPv6 addresses (the address in the
   * LDAP URL must be enclosed with brackets).
   * @param host the host name.
   * @return the String that can be used to represent a given host name in a
   * LDAP URL.
   */
  public static String getHostNameForLdapUrl(String host)
  {
    return ConnectionUtils.getHostNameForLdapUrl(host);
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
    return SetupUtils.isWebStart();
  }

  /**
   * Returns <CODE>true</CODE> if this is executed from command line and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if this is executed from command line and
   * <CODE>false</CODE> otherwise.
   */
  public static boolean isCli()
  {
    return "true".equals(System.getProperty(Constants.CLI_JAVA_PROPERTY));
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
    return ConnectionUtils.createLdapContext(ldapURL, dn, pwd, timeout, env);
  }

  /**
   * Creates an LDAPS connection and returns the corresponding LdapContext.
   * This method uses the TrusteSocketFactory class so that the specified
   * trust manager gets called during the SSL handshake. If trust manager is
   * null, certificates are not verified during SSL handshake.
   *
   * @param ldapsURL      the target *LDAPS* URL.
   * @param dn            passed as Context.SECURITY_PRINCIPAL if not null.
   * @param pwd           passed as Context.SECURITY_CREDENTIALS if not null.
   * @param timeout       passed as com.sun.jndi.ldap.connect.timeout if > 0.
   * @param env           null or additional environment properties.
   * @param trustManager  null or the trust manager to be invoked during SSL
   * negociation.
   *
   * @return the established connection with the given parameters.
   *
   * @throws NamingException the exception thrown when instantiating
   * InitialLdapContext.
   *
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   * @see org.opends.admin.ads.util.TrustedSocketFactory
   */
  public static InitialLdapContext createLdapsContext(String ldapsURL,
      String dn, String pwd, int timeout, Hashtable<String, String> env,
      TrustManager trustManager) throws NamingException {
    return ConnectionUtils.createLdapsContext(ldapsURL, dn, pwd, timeout, env,
        trustManager, null);
  }

  /**
   * Creates an LDAP+StartTLS connection and returns the corresponding
   * LdapContext.
   * This method first creates an LdapContext with anonymous bind. Then it
   * requests a StartTlsRequest extended operation. The StartTlsResponse is
   * setup with the specified hostname verifier. Negotiation is done using a
   * TrustSocketFactory so that the specified TrustManager gets called during
   * the SSL handshake.
   * If trust manager is null, certificates are not checked during SSL
   * handshake.
   *
   * @param ldapsURL      the target *LDAPS* URL.
   * @param dn            passed as Context.SECURITY_PRINCIPAL if not null.
   * @param pwd           passed as Context.SECURITY_CREDENTIALS if not null.
   * @param timeout       passed as com.sun.jndi.ldap.connect.timeout if > 0.
   * @param env           null or additional environment properties.
   * @param trustManager  null or the trust manager to be invoked during SSL.
   * negociation.
   * @param verifier      null or the hostname verifier to be setup in the
   * StartTlsResponse.
   *
   * @return the established connection with the given parameters.
   *
   * @throws NamingException the exception thrown when instantiating
   * InitialLdapContext.
   *
   * @see javax.naming.Context
   * @see javax.naming.ldap.InitialLdapContext
   * @see javax.naming.ldap.StartTlsRequest
   * @see javax.naming.ldap.StartTlsResponse
   * @see org.opends.admin.ads.util.TrustedSocketFactory
   */

  public static InitialLdapContext createStartTLSContext(String ldapsURL,
      String dn, String pwd, int timeout, Hashtable<String, String> env,
      TrustManager trustManager, HostnameVerifier verifier)
  throws NamingException
  {
    return ConnectionUtils.createStartTLSContext(ldapsURL, dn, pwd, timeout,
        env, trustManager, null, verifier);
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
    return ConnectionUtils.canConnectAsAdministrativeUser(ldapUrl, dn, pwd);
  }

/**
 * Tells whether the provided Throwable was caused because of a problem with
 * a certificate while trying to establish a connection.
 * @param t the Throwable to analyze.
 * @return <CODE>true</CODE> if the provided Throwable was caused because of a
 * problem with a certificate while trying to establish a connection and
 * <CODE>false</CODE> otherwise.
 */
  public static boolean isCertificateException(Throwable t)
  {
    return ConnectionUtils.isCertificateException(t);
  }

  /**
   * Returns the default LDAP timeout in milliseconds when we try to connect to
   * a server.
   * @return the default LDAP timeout in milliseconds when we try to connect to
   * a server.
   */
  public static int getDefaultLDAPTimeout()
  {
    return ConnectionUtils.getDefaultLDAPTimeout();
  }


  /**
   * Returns the path of the installation of the directory server.  Note that
   * this method assumes that this code is being run locally.
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
    String path = null;
    for (int i = 0; i < classPaths.length && (path == null); i++)
    {
      for (int j = 0; j < Installation.OPEN_DS_JAR_RELATIVE_PATHS.length &&
      (path == null); j++)
      {
        String normPath = classPaths[i].replace(File.separatorChar, '/');
        if (normPath.endsWith(Installation.OPEN_DS_JAR_RELATIVE_PATHS[j]))
        {
          path = classPaths[i];
        }
      }
    }
    if (path != null) {
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

  /**
   * Returns the path of the installation of the directory server.  Note that
   * this method assumes that this code is being run locally.
   * @param installPath The installation path
   * @return the path of the installation of the directory server.
   */
  public static String getInstancePathFromClasspath(String installPath)
  {
    String instancePathFileName = Installation.INSTANCE_LOCATION_PATH;
    File configureScriptPath = new File(installPath + File.separator +
      Installation.UNIX_CONFIGURE_FILE_NAME);

    // look for /etc/opt/opends/instance.loc
    File f = new File(instancePathFileName);
    if (!configureScriptPath.exists() || !f.exists()) {
      // look for <installPath>/instance.loc
      instancePathFileName = installPath + File.separator +
              Installation.INSTANCE_LOCATION_PATH_RELATIVE;
      f = new File(instancePathFileName);
      if (!f.exists()) {
        return installPath;
      }
    }

    BufferedReader reader;
    try
    {
      reader = new BufferedReader(new FileReader(instancePathFileName));
    }
    catch (Exception e)
    {
      return installPath;
    }


    // Read the first line and close the file.
    String line;
    try
    {
      line = reader.readLine();
      File instanceLoc =  new File (line.trim());
      if (instanceLoc.isAbsolute())
      {
        return instanceLoc.getAbsolutePath();
      }
      else
      {
        return new File(installPath + File.separator + instanceLoc.getPath())
            .getAbsolutePath();
      }
    }
    catch (Exception e)
    {
      return installPath;
    }
    finally
    {
      try
      {
        reader.close();
      } catch (Exception e) {}
    }
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
  public static void setMacOSXMenuBar(Message appName)
  {
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty("com.apple.mrj.application.apple.menu.about.name",
                       String.valueOf(appName));
  }

  /**
    * Tells whether this throwable has been generated for an out of memory
    * error or not.
    * @param t the throwable to analyze.
    * @return <CODE>true</CODE> if the throwable was generated by an out of
    * memory error and false otherwise.
    */
  private static boolean isOutOfMemory(Throwable t)
  {
    boolean isOutOfMemory = false;
    while (!isOutOfMemory && (t != null))
    {
      if (t instanceof OutOfMemoryError)
      {
        isOutOfMemory = true;
      }
      else if (t instanceof IOException)
      {
        String msg = t.toString();
        if (msg != null)
        {
          isOutOfMemory = msg.indexOf("Not enough space") != -1;
        }
      }
      t = t.getCause();
    }
    return isOutOfMemory;
  }

  /**
   * Returns the number of entries contained in the zip file.  This is used to
   * update properly the progress bar ratio.
   * @return the number of entries contained in the zip file.
   */
  static public int getNumberZipEntries()
  {
    // TODO  we should get this dynamically during build
    return 165;
  }

  /**
   * Determines whether one file is the parent of another.
   * @param ancestor possible parent of <code>descendant</code>
   * @param descendant possible child 0f <code>ancestor</code>
   * @return return true if ancestor is a parent of descendant
   */
  static public boolean isParentOf(File ancestor, File descendant) {
    if (ancestor != null) {
      if (ancestor.equals(descendant)) {
        return false;
      }
      while ((descendant != null) && !ancestor.equals(descendant)) {
        descendant = descendant.getParentFile();
      }
    }
    return (ancestor != null) && (descendant != null);
  }

  /**
   * Creates a string consisting of the string representation of the
   * elements in the <code>list</code> separated by <code>separator</code>.
   * @param list the list to print
   * @param separator to use in separating elements
   * @return String representing the list
   */
  static public String listToString(List<?> list, String separator) {
    return listToString(list, separator, null, null);
  }

  /**
   * Creates a message consisting of the string representation of the
   * elements in the <code>list</code> separated by <code>separator</code>.
   * @param list the list to print
   * @param separator to use in separating elements
   * @param prefix prepended to each individual element in the list before
   *        adding to the returned string.
   * @param suffix appended to each individual element in the list before
   *        adding to the returned string.
   * @return String representing the list
   */
  static public Message listToMessage(List<Message> list, String separator,
                                      String prefix, String suffix) {
    MessageBuilder sb = new MessageBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (prefix != null) {
        sb.append(prefix);
      }
      sb.append(list.get(i));
      if (suffix != null) {
        sb.append(suffix);
      }
      if (i < list.size() - 1) {
        sb.append(separator);
      }
    }
    return sb.toMessage();
  }

  /**
   * Creates a string consisting of the string representation of the
   * elements in the <code>list</code> separated by <code>separator</code>.
   * @param list the list to print
   * @param separator to use in separating elements
   * @param prefix prepended to each individual element in the list before
   *        adding to the returned string.
   * @param suffix appended to each individual element in the list before
   *        adding to the returned string.
   * @return String representing the list
   */
  static public String listToString(List<?> list, String separator,
                                    String prefix, String suffix) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (prefix != null) {
        sb.append(prefix);
      }
      sb.append(list.get(i));
      if (suffix != null) {
        sb.append(suffix);
      }
      if (i < list.size() - 1) {
        sb.append(separator);
      }
    }
    return sb.toString();
  }

  /**
   * Creates a string consisting of the string representation of the
   * elements in the <code>list</code> separated by <code>separator</code>.
   * @param array the list to print
   * @param separator to use in separating elements
   * @return String representing the list
   */
  static public String stringArrayToString(String[] array, String separator) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < array.length; i++) {
      sb.append(array[i]);
      if (i < array.length - 1) {
        sb.append(separator);
      }
    }
    return sb.toString();
  }

  /**
   * Returns the file system permissions for a file.
   * @param path the file for which we want the file permissions.
   * @return the file system permissions for the file.
   */
  static public String getFileSystemPermissions(String path)
  {
    return getFileSystemPermissions(new File(path));
  }

  /**
   * Returns the file system permissions for a file.
   * @param file the file for which we want the file permissions.
   * @return the file system permissions for the file.
   */
  static public String getFileSystemPermissions(File file)
  {
    String perm;
    String name = file.getName();
    if (file.getParent().endsWith(
        File.separator + Installation.WINDOWS_BINARIES_PATH_RELATIVE) ||
        file.getParent().endsWith(
        File.separator + Installation.UNIX_BINARIES_PATH_RELATIVE)) {
      if (name.endsWith(".bat")) {
        perm = "644";
      }
      else {
        perm = "755";
      }
    } else if (name.endsWith(".sh")) {
      perm = "755";
    } else if (name.endsWith(Installation.UNIX_SETUP_FILE_NAME) ||
            name.endsWith(Installation.UNIX_UNINSTALL_FILE_NAME) ||
            name.endsWith(Installation.UNIX_UPGRADE_FILE_NAME)) {
      perm = "755";
    } else if (name.endsWith(Installation.MAC_JAVA_APP_STUB_NAME)) {
      perm = "755";
    } else {
      perm = "644";
    }
    return perm;
  }

  /**
   * Returns a string representing the installation's current build information
   * useful for presenting to the user.  If the build string could not be
   * determined for any reason a localized String 'unknown' is returned.
   * @param installation whose build information is sought
   * @return String representing the application's build.
   */
  static public String getBuildString(Installation installation) {
    String b = null;
    try {
      BuildInformation bi = installation.getBuildInformation();
      if (bi != null) {
        b = bi.toString();
      }
    } catch (ApplicationException e) {
      LOG.log(Level.INFO, "error trying to determine current build string", e);
    }
    if (b == null) {
      b = INFO_UPGRADE_BUILD_ID_UNKNOWN.get().toString();
    }
    return b;
  }

  /**
   * Returns the String representation of the first value of an attribute in a
   * LDAP entry.
   * @param entry the entry.
   * @param attrName the attribute name.
   * @return the String representation of the first value of an attribute in a
   * LDAP entry.
   * @throws NamingException if there is an error processing the entry.
   */
  static public String getFirstValue(SearchResult entry, String attrName)
  throws NamingException
  {
    return ConnectionUtils.getFirstValue(entry, attrName);
  }

  /**
   * Inserts HTML break tags into <code>d</code> breaking it up
   * so that ideally no line is longer than <code>maxll</code>
   * assuming no single word is longer then <code>maxll</code>.
   * If the string already contains HTML tags that cause a line
   * break (e.g break and closing list item tags) they are
   * respected by this method when calculating where to place
   * new breaks to control the maximum line length.
   *
   * @param cs String to break
   * @param maxll int maximum line length
   * @return String representing <code>d</code> with HTML break
   *         tags inserted
   */
  static public String breakHtmlString(CharSequence cs, int maxll) {
    if (cs != null) {
      String d = cs.toString();
      int len = d.length();
      if (len <= 0)
        return d;
      if (len > maxll) {

        // First see if there are any tags that would cause a
        // natural break in the line.  If so start line break
        // point evaluation from that point.
        for (String tag : Constants.BREAKING_TAGS) {
          int p = d.lastIndexOf(tag, maxll);
          if (p > 0 && p < len) {
            return d.substring(0, p + tag.length()) +
                   breakHtmlString(
                           d.substring(p + tag.length()),
                           maxll);
          }
        }

        // Now look for spaces in which to insert a break.
        // First see if there are any spaces counting backward
        // from the max line length.  If there aren't any, then
        // use the first space encountered after the max line
        // lenght.
        int p = d.lastIndexOf(' ', maxll);
        if (p <= 0) {
          p = d.indexOf(' ', maxll);
        }
        if (p > 0 && p < len) {
          return d.substring(0, p) +
                  Constants.HTML_LINE_BREAK +
                 breakHtmlString(d.substring(p + 1), maxll);
        } else {
          return d;
        }
      } else {
        return d;
      }
    } else {
      return null;
    }
  }

  /**
   * Converts existing HTML break tags to native line sparators.
   * @param s string to convert
   * @return converted string
   */
  static public String convertHtmlBreakToLineSeparator(String s) {
    return s.replaceAll("\\<br\\>", Constants.LINE_SEPARATOR);
  }

  /**
   * Strips any potential HTML markup from a given string.
   * @param s string to strip
   * @return resulting string
   */
  static public String stripHtml(String s) {
    String o = null;
    if (s != null) {

      // This is not a comprehensive solution but addresses
      // the few tags that we have in Resources.properties
      // at the moment.  Note that the following might strip
      // out more than is intended for non-tags like
      // '<your name here>' or for funky tags like
      // '<tag attr="1 > 0">'. See test class for cases that
      // might cause problems.
      o = s.replaceAll("\\<.*?\\>","");

    }
    return o;
  }

  /**
   * Tests a text string to see if it contains HTML.
   * @param text String to test
   * @return true if the string contains HTML
   */
  static public boolean containsHtml(String text) {
    return (text != null &&
            text.indexOf('<') != -1 &&
            text.indexOf('>') != -1);
  }

  private static EmptyPrintStream emptyStream = new EmptyPrintStream();

  /**
   * Returns a printstream that does not write anything to standard output.
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
   * @param ctx the connection to the server.
   * @return the current time of a server in milliseconds.
   */
  public static long getServerClock(InitialLdapContext ctx)
  {
    long time = -1;
    String v = null;
    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            "currentTime"
        });
    String filter = "(objectclass=*)";

    try
    {
      LdapName jndiName = new LdapName("cn=monitor");
      NamingEnumeration listeners = ctx.search(jndiName, filter, ctls);

      while(listeners.hasMore())
      {
        SearchResult sr = (SearchResult)listeners.next();

        v = getFirstValue(sr, "currentTime");

        TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

        SimpleDateFormat formatter =
             new SimpleDateFormat("yyyyMMddHHmmss'Z'");
        formatter.setTimeZone(utcTimeZone);

        time = formatter.parse(v).getTime();
      }
    }
    catch (Throwable t)
    {
      LOG.log(Level.WARNING, "Error retrieving server current time: "+t, t);
    }
    return time;
  }

  /**
   * Checks that the java version we are running is compatible with OpenDS.
   * @throws IncompatibleVersionException if the java version we are running
   * is not compatible with OpenDS.
   */
  public static void checkJavaVersion() throws IncompatibleVersionException
  {
    String vendor = System.getProperty("java.vendor");
    String version = System.getProperty("java.version");
    for (CompatibleJava i : CompatibleJava.values())
    {
      if (i.getVendor().equalsIgnoreCase(vendor))
      {
        // Compare versions.
        boolean versionCompatible =
          i.getVersion().compareToIgnoreCase(version) <= 0;
        if (!versionCompatible)
        {
          String javaBin = System.getProperty("java.home")+File.separator+
          "bin"+File.separator+"java";
          throw new IncompatibleVersionException(
              ERR_INCOMPATIBLE_VERSION.get(i.getVersion(), version, javaBin),
              null);
        }
      }
    }
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
  static String escapeHtml(String rawString)
  {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < rawString.length(); i++)
    {
      char c = rawString.charAt(i);
      switch (c)
      {
      case '<':
        buffer.append("&lt;");
        break;

      case '>':
        buffer.append("&gt;");
        break;

      case '&':
        buffer.append("&amp;");
        break;

      case '"':
        buffer.append("&quot;");
        break;

      default:
        buffer.append(c);
        break;
      }
    }

    return buffer.toString();
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
    StringBuilder buffer = new StringBuilder();
    if (text != null) {
      text = text.replaceAll("\r\n", "\n");
      String[] lines = text.split("[\n\r\u0085\u2028\u2029]");
      for (int i = 0; i < lines.length; i++)
      {
        if (i != 0)
        {
          buffer.append(Constants.HTML_LINE_BREAK);
        }
        buffer.append(escapeHtml(lines[i]));
      }
    }
    return buffer.toString();
  }
}

/**
 * This class is used to avoid displaying the error message related to display
 * problems that we might have when trying to display the SplashWindow.
 *
 */
class EmptyPrintStream extends PrintStream {
  private static final Logger LOG =
    Logger.getLogger(EmptyPrintStream.class.getName());

  /**
   * Default constructor.
   *
   */
  public EmptyPrintStream()
  {
    super(new ByteArrayOutputStream(), true);
  }

  /**
   * {@inheritDoc}
   */
  public void println(String msg)
  {
    LOG.log(Level.INFO, "EmptyStream msg: "+msg);
  }
}
