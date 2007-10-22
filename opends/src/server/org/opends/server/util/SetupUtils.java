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
package org.opends.server.util;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import org.opends.server.types.OperatingSystem;


/**
 * This class provides a number of utility methods that may be used during the
 * graphical or command-line setup process.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public class SetupUtils
{
  /**
   * Java property used to known if we are using web start or not.
   */
  public static final String IS_WEBSTART = "org.opends.quicksetup.iswebstart";

  /**
   * Specific environment variable used by the scripts to find java.
   */
  public static final String OPENDS_JAVA_HOME = "OPENDS_JAVA_HOME";

  /**
   * Java property used to know which are the jar files that must be downloaded
   * lazily.  The current code in WebStartDownloader that uses this property
   * assumes that the URL are separated with an space.
   */
  public static final String LAZY_JAR_URLS =
      "org.opends.quicksetup.lazyjarurls";

  /**
   * Java property used to know which is the name of the zip file that must
   * be unzipped and whose contents must be extracted during the Web Start
   * based setup.
   */
  public static final String ZIP_FILE_NAME =
      "org.opends.quicksetup.zipfilename";

  /**
   * The relative path where all the libraries (jar files) are.
   */
  public static final String LIBRARIES_PATH_RELATIVE = "lib";

  private static final String SET_JAVA_HOME_UNIX = "set-java-home";
  private static final String SET_JAVA_HOME_WINDOWS = "set-java-home.bat";

  /**
   * Creates a MakeLDIF template file using the provided information.
   *
   * @param  baseDN      The base DN for the data in the template file.
   * @param  numEntries  The number of user entries the template file should
   *                     create.
   *
   * @return  The {@code File} object that references the created template file.
   *
   * @throws  IOException  If a problem occurs while writing the template file.
   */
  public static File createTemplateFile(String baseDN, int numEntries)
         throws IOException
  {
    File templateFile = File.createTempFile("opends-install", ".template");
    templateFile.deleteOnExit();

    LinkedList<String> lines = new LinkedList<String>();
    lines.add("define suffix=" + baseDN);

    if (numEntries > 0)
    {
      lines.add("define numusers=" + numEntries);
    }

    lines.add("");
    lines.add("branch: [suffix]");
    lines.add("");
    lines.add("branch: ou=People,[suffix]");

    if (numEntries > 0)
    {
      lines.add("subordinateTemplate: person:[numusers]");
      lines.add("");
      lines.add("template: person");
      lines.add("rdnAttr: uid");
      lines.add("objectClass: top");
      lines.add("objectClass: person");
      lines.add("objectClass: organizationalPerson");
      lines.add("objectClass: inetOrgPerson");
      lines.add("givenName: <first>");
      lines.add("sn: <last>");
      lines.add("cn: {givenName} {sn}");
      lines.add("initials: {givenName:1}" +
                "<random:chars:ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}");
      lines.add("employeeNumber: <sequential:0>");
      lines.add("uid: user.{employeeNumber}");
      lines.add("mail: {uid}@maildomain.net");
      lines.add("userPassword: password");
      lines.add("telephoneNumber: <random:telephone>");
      lines.add("homePhone: <random:telephone>");
      lines.add("pager: <random:telephone>");
      lines.add("mobile: <random:telephone>");
      lines.add("street: <random:numeric:5> <file:streets> Street");
      lines.add("l: <file:cities>");
      lines.add("st: <file:states>");
      lines.add("postalCode: <random:numeric:5>");
      lines.add("postalAddress: {cn}${street}${l}, {st}  {postalCode}");
      lines.add("description: This is the description for {cn}.");
    }

    BufferedWriter writer = new BufferedWriter(new FileWriter(templateFile));
    for (String line : lines)
    {
      writer.write(line);
      writer.newLine();
    }

    writer.flush();
    writer.close();

    return templateFile;
  }

  /**
   * Returns {@code true} if we are running under Mac OS and
   * {@code false} otherwise.
   * @return {@code true} if we are running under Mac OS and
   * {@code false} otherwise.
   */
  public static boolean isMacOS()
  {
    return OperatingSystem.MACOS == getOperatingSystem();
  }

  /**
   * Returns {@code true} if we are running under Unix and
   * {@code false} otherwise.
   * @return {@code true} if we are running under Unix and
   * {@code false} otherwise.
   */
  public static boolean isUnix()
  {
    return OperatingSystem.isUNIXBased(getOperatingSystem());
  }

  /**
   * Indicates whether the underlying operating system is a Windows variant.
   *
   * @return  {@code true} if the underlying operating system is a Windows
   *          variant, or {@code false} if not.
   */
  public static boolean isWindows()
  {
      return OperatingSystem.WINDOWS == getOperatingSystem();
  }

  /**
   * Indicates whether the underlying operating system is Windows Vista.
   *
   * @return  {@code true} if the underlying operating system is Windows
   *          Vista, or {@code false} if not.
   */
  public static boolean isVista()
  {
    boolean isVista;
    String os = System.getProperty("os.name");
    if (os != null)
    {
     isVista = isWindows() && (os.toLowerCase().indexOf("vista") != -1);
    }
    else
    {
      isVista = false;
    }
    return isVista;
  }
  /**
   * Returns a String representation of the OS we are running.
   * @return a String representation of the OS we are running.
   */
  public static String getOSString()
  {
    return getOperatingSystem().toString();
  }

  /**
   * Commodity method to help identifying the OS we are running on.
   * @return the OperatingSystem we are running on.
   */
  private static OperatingSystem getOperatingSystem()
  {
    return OperatingSystem.forName(System.getProperty("os.name"));
  }



  /**
   * Write a set-java-home file appropriate for the underlying platform that may
   * be used to set the OPENDS_JAVA_HOME environment variable in a form suitable
   * for the underlying operating system.  If a OPENDS_JAVA_HOME environment
   * variable is currently set, then its value will be used.  Otherwise, it will
   * be dynamically determined from the JVM properties.
   * <BR><BR>
   * Note that if the target file that would be written already exists, then
   * this method will exit without doing anything and leaving the existing file
   * intact.
   *
   *
   * @param  serverRoot  The path to the root of the Directory Server instance
   *                     for which the file will be written.
   *
   * @param overWrite when the set-java-home file exists whether to overwrite it
   *        or not.
   * @return  A handle to the {@code File} object that has been written.
   *
   * @throws  IOException  If a problem occurs while creating or writing to the
   *                       specified file.
   */
  public static File writeSetOpenDSJavaHome(String serverRoot,
      boolean overWrite) throws IOException
  {
    String javaHome = System.getenv(OPENDS_JAVA_HOME);
    if ((javaHome == null) || (javaHome.length() == 0))
    {
      javaHome = System.getProperty("java.home");
    }


    File libDirectory = new File(serverRoot, LIBRARIES_PATH_RELATIVE);

    File setJavaHomeFile;
    if (isWindows())
    {
      setJavaHomeFile = new File(libDirectory, SET_JAVA_HOME_WINDOWS);
      if (setJavaHomeFile.exists())
      {
        if (!overWrite)
        {
          return setJavaHomeFile;
        }
        else
        {
          File f1 = new File(javaHome);
          File f2 = new File(getOpenDSJavaHome(serverRoot));
          if (f1.equals(f2))
          {
            return setJavaHomeFile;
          }
        }
      }
      BufferedWriter writer =
           new BufferedWriter(new FileWriter(setJavaHomeFile));
      writer.write("set "+OPENDS_JAVA_HOME+"=" + javaHome);
      writer.newLine();
      writer.close();
    }
    else
    {
      setJavaHomeFile = new File(libDirectory, SET_JAVA_HOME_UNIX);
      if (setJavaHomeFile.exists())
      {
        if (!overWrite)
        {
          return setJavaHomeFile;
        }
        else
        {
          File f1 = new File(javaHome);
          File f2 = new File(getOpenDSJavaHome(serverRoot));
          if (f1.equals(f2))
          {
            return setJavaHomeFile;
          }
        }
      }
      BufferedWriter writer =
           new BufferedWriter(new FileWriter(setJavaHomeFile));
      writer.write("#!/bin/sh");
      writer.newLine();
      writer.newLine();
      writer.write(OPENDS_JAVA_HOME+"=" + javaHome);
      writer.newLine();
      writer.write("export "+OPENDS_JAVA_HOME);
      writer.newLine();
      writer.close();
    }

    return setJavaHomeFile;
  }

  /**
   * Returns the java home value as it is specified in the set-java-home file.
   * It returns <CODE>null</CODE> if the contents of the file are not valid, the
   * file could not be read or if the file does not exist.
   * @param  serverRoot  The path to the root of the Directory Server instance
   *                     in which the set-java-home file is located.
   * @return the java home value as it is specified in the set-java-home file.
   * @throws  IOException  If a problem occurs while reading the file.
   */
  public static String getOpenDSJavaHome(String serverRoot) throws IOException
  {
    String javaHome = null;

    File libDirectory = new File(serverRoot, LIBRARIES_PATH_RELATIVE);

    File setJavaHomeFile;
    if (isWindows())
    {
      setJavaHomeFile = new File(libDirectory, SET_JAVA_HOME_WINDOWS);
    }
    else
    {
      setJavaHomeFile = new File(libDirectory, SET_JAVA_HOME_UNIX);
    }
    if (setJavaHomeFile.exists())
    {
      BufferedReader reader =
        new BufferedReader(new FileReader(setJavaHomeFile));
      String line = reader.readLine();
      String tag = OPENDS_JAVA_HOME+"=";
      while ((line != null) && (javaHome == null))
      {
        int index = line.indexOf(tag);
        if (index != -1)
        {
          javaHome = line.substring(index + tag.length());
        }
        line = reader.readLine();
      }
    }
    return javaHome;
  }

  /**
   * Returns {@code true} if the provided port is free and we can use it,
   * {@code false} otherwise.
   * @param hostname the host name we are analyzing.
   * @param port the port we are analyzing.
   * @return {@code true} if the provided port is free and we can use it,
   * {@code false} otherwise.
   */
  public static boolean canUseAsPort(String hostname, int port)
  {
    boolean canUseAsPort = false;
    ServerSocket serverSocket = null;
    try
    {
      InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);
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
      Socket s = null;
      try
      {
        s = new Socket();
        s.connect(socketAddress, 1000);
        canUseAsPort = false;

      } catch (Throwable t)
      {
      }
      finally
      {
        if (s != null)
        {
          try
          {
            s.close();
          }
          catch (Throwable t)
          {
          }
        }
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
   * Returns {@code true} if the provided port is free and we can use it,
   * {@code false} otherwise.
   * @param port the port we are analyzing.
   * @return {@code true} if the provided port is free and we can use it,
   * {@code false} otherwise.
   */
  public static boolean canUseAsPort(int port)
  {
    return canUseAsPort("localhost", port);
  }

  /**
   * Returns {@code true} if the provided port is a priviledged port,
   * {@code false} otherwise.
   * @param port the port we are analyzing.
   * @return {@code true} if the provided port is a priviledged port,
   * {@code false} otherwise.
   */
  public static boolean isPriviledgedPort(int port)
  {
    return (port <= 1024) && !isWindows();
  }

  /**
   * Returns the default value for the JMX Port.
   * @return the default value for the JMX Port.
   */
  public static int getDefaultJMXPort()
  {
    return 1689;
  }

  /**
   * Indicates whether we are in a web start installation or not.
   *
   * @return <CODE>true</CODE> if we are in a web start installation and
   *         <CODE>false</CODE> if not.
   */
  public static boolean isWebStart()
  {
    return "true".equals(System.getProperty(IS_WEBSTART));
  }
}

