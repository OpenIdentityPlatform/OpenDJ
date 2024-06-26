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
package org.opends.server.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

import com.forgerock.opendj.util.OperatingSystem;

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
   * Specific environment variable used by the scripts to find java.
   */
  public static final String OPENDJ_JAVA_HOME = "OPENDJ_JAVA_HOME";

  /**
   * Specific environment variable used by the scripts to set java arguments.
   */
  public static final String OPENDJ_JAVA_ARGS = "OPENDJ_JAVA_ARGS";

  /**
   * The relative path where all the libraries (jar files) are.
   */
  public static final String LIBRARIES_PATH_RELATIVE = "lib";

  /**
   * The relative path where the setup stores the name of the host the user
   * provides. This is used for instance to generate the self-signed admin
   * certificate the first time the server starts.
   */
  public static final String HOST_NAME_FILE = "config" + File.separatorChar
      + "hostname";

  /* These string values must be synchronized with Directory Server's main
   * method.  These string values are considered stable by the server team and
   * not candidates for internationalization. */
  /** Product name. */
  public static final String NAME = "Name";
  /** Build ID. */
  public static final String BUILD_ID = "Build ID";
  /** Major version. */
  public static final String MAJOR_VERSION = "Major Version";
  /** Minor version. */
  public static final String MINOR_VERSION = "Minor Version";
  /** Point version of the product. */
  public static final String POINT_VERSION = "Point Version";
  /** Revision in VCS. */
  public static final String REVISION = "Revision Number";
  /** The VCS url repository. */
  public static final String URL_REPOSITORY = "URL Repository";
  /** The version qualifier. */
  public static final String VERSION_QUALIFIER = "Version Qualifier";
  /** Fix IDs associated with the build. */
  public static final String FIX_IDS = "Fix IDs";
  /** Debug build identifier. */
  public static final String DEBUG_BUILD = "Debug Build";
  /** The OS used during the build. */
  public static final String BUILD_OS = "Build OS";
  /** The user that generated the build. */
  public static final String BUILD_USER = "Build User";
  /** The java version used to generate the build. */
  public static final String BUILD_JAVA_VERSION = "Build Java Version";
  /** The java vendor of the JVM used to build. */
  public static final String BUILD_JAVA_VENDOR = "Build Java Vendor";
  /** The version of the JVM used to create the build. */
  public static final String BUILD_JVM_VERSION = "Build JVM Version";
  /** The vendor of the JVM used to create the build. */
  public static final String BUILD_JVM_VENDOR = "Build JVM Vendor";
  /** The build number. */
  public static final String BUILD_NUMBER = "Build Number";

  /**
   * A variable used to keep the latest read host name from the file written
   * by the setup.
   */
  private static String lastReadHostName;

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
    Set<String> baseDNs = new HashSet<>(1);
    baseDNs.add(baseDN);
    return createTemplateFile(baseDNs, numEntries);
  }

  /**
   * Creates a MakeLDIF template file using the provided information.
   *
   * @param  baseDNs     The base DNs for the data in the template file.
   * @param  numEntries  The number of user entries the template file should
   *                     create.
   *
   * @return  The {@code File} object that references the created template file.
   *
   * @throws  IOException  If a problem occurs while writing the template file.
   */
  public static File createTemplateFile(Set<String> baseDNs,
      int numEntries)
         throws IOException
  {
    File templateFile = File.createTempFile("opendj-install", ".template");
    templateFile.deleteOnExit();

    LinkedList<String> lines = new LinkedList<>();
    int i = 0;
    for (String baseDN : baseDNs)
    {
      i++;
      lines.add("define suffix"+i+"=" + baseDN);
    }
    if (numEntries > 0)
    {
      lines.add("define numusers=" + numEntries);
    }

    for (i=1; i<=baseDNs.size(); i++)
    {
      lines.add("");
      lines.add("branch: [suffix"+i+"]");
      lines.add("");
      lines.add("branch: ou=People,[suffix"+i+"]");

      if (numEntries > 0)
      {
        lines.add("subordinateTemplate: person:[numusers]");
        lines.add("");
      }
    }

    if (!baseDNs.isEmpty() && numEntries > 0)
    {
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

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(templateFile)))
    {
      for (String line : lines)
      {
        writer.write(line);
        writer.newLine();
      }
    }
    return templateFile;
  }

  /**
   * Returns {@code true} if the provided port is free and we can use it,
   * {@code false} otherwise.
   * @param hostname the host name we are analyzing.  Use <CODE>null</CODE>
   * to connect to any address.
   * @param port the port we are analyzing.
   * @return {@code true} if the provided port is free and we can use it,
   * {@code false} otherwise.
   */
  private static boolean canUseAsPort(String hostname, int port)
  {
    try (ServerSocket serverSocket = new ServerSocket())
    {
      InetSocketAddress socketAddress;
      if (hostname != null)
      {
        socketAddress = new InetSocketAddress(hostname, port);
      }
      else
      {
        socketAddress = new InetSocketAddress(port);
      }
      if (!OperatingSystem.isWindows())
      {
        serverSocket.setReuseAddress(true);
      }
      serverSocket.bind(socketAddress);
      serverSocket.close();

      /* Try to create a socket because sometimes even if we can create a server
       * socket there is already someone listening to the port (is the case
       * of products as Sun DS 6.0).
       */
      try (Socket s = new Socket())
      {
    	  s.setReuseAddress(true);
        s.connect(socketAddress, 1000);
        return false;
      } catch (Throwable t)
      {
      }
      return true;
    } catch (IOException ex)
    {
      return false;
    }
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
    return canUseAsPort(null, port);
  }

  /**
   * Returns {@code true} if the provided port is a privileged port,
   * {@code false} otherwise.
   * @param port the port we are analyzing.
   * @return {@code true} if the provided port is a privileged port,
   * {@code false} otherwise.
   */
  public static boolean isPrivilegedPort(int port)
  {
    return port <= 1024 && !OperatingSystem.isWindows();
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
    String s = script;
    if (OperatingSystem.isWindows()
        && s != null && (!s.startsWith("\"") || !s.endsWith("\"")))
    {
      return "\"" + script + "\"";
    }
    return s;
  }

  /**
   * Returns a randomly generated password for a self-signed certificate
   * keystore.
   * @return a randomly generated password for a self-signed certificate
   * keystore.
   */
  public static char[] createSelfSignedCertificatePwd() {
    int pwdLength = 50;
    char[] pwd = new char[pwdLength];
    Random random = new Random();
    for (int pos=0; pos < pwdLength; pos++) {
        int type = getRandomInt(random,3);
        char nextChar = getRandomChar(random,type);
        pwd[pos] = nextChar;
    }
    return pwd;
  }

  /**
   * Export a certificate in a file. If the certificate alias to export is null,
   * It will export the first certificate defined.
   *
   * @param certManager
   *          Certificate manager to use.
   * @param alias
   *          Certificate alias to export. If {@code null} the first certificate
   *          defined will be exported.
   * @param path
   *          Path of the output file.
   * @throws CertificateEncodingException
   *           If the certificate manager cannot encode the certificate.
   * @throws IOException
   *           If a problem occurs while creating or writing in the output file.
   * @throws KeyStoreException
   *           If the certificate manager cannot retrieve the certificate to be
   *           exported.
   */
  public static void exportCertificate(CertificateManager certManager, String alias, String path)
      throws CertificateEncodingException, IOException, KeyStoreException
  {
    final Certificate certificate =
        certManager.getCertificate(alias != null ? alias : certManager.getCertificateAliases()[0]);
    byte[] certificateBytes = certificate.getEncoded();

    try (FileOutputStream outputStream = new FileOutputStream(path, false))
    {
      outputStream.write(certificateBytes);
    }
  }


  /**
   * The next two methods are used to generate the random password for the
   * self-signed certificate.
   */
  private static char getRandomChar(Random random, int type)
  {
    char generatedChar;
    int next = random.nextInt();
    int d;

    switch (type)
    {
    case 0:
      // Will return a digit
      d = next % 10;
      if (d < 0)
      {
        d = d * -1;
      }
      generatedChar = (char) (d+48);
      break;
    case 1:
      // Will return a lower case letter
      d = next % 26;
      if (d < 0)
      {
        d = d * -1;
      }
      generatedChar =  (char) (d + 97);
      break;
    default:
      // Will return a capital letter
      d = next % 26;
      if (d < 0)
      {
        d = d * -1;
      }
      generatedChar = (char) (d + 65) ;
    }

    return generatedChar;
  }

  private static int getRandomInt(Random random,int modulo)
  {
    return random.nextInt() & modulo;
  }

  /**
   * Returns the host name to be used to create self-signed certificates. <br>
   * The method will first try to read the host name file written by the setup
   * where the user provided the host name where OpenDJ has been installed. If
   * the file cannot be read, the class {@link java.net.InetAddress} is used.
   *
   * @param installationRoot the path where the server is installed.
   * @return the host name to be used to create self-signed certificates.
   * @throws UnknownHostException
   *           if a host name could not be used.
   */
  public static String getHostNameForCertificate(
      String installationRoot) throws UnknownHostException
  {
    String hostName = null;
    File f = new File(installationRoot + File.separator + HOST_NAME_FILE);
    try (BufferedReader br = new BufferedReader(new FileReader(f)))
    {
      String s = br.readLine();
      s = s.trim();

      if (s.length() > 0)
      {
        hostName = s;
        lastReadHostName = hostName;
      }
    }
    catch (IOException ioe)
    {
    }
    if (hostName == null)
    {
      hostName = lastReadHostName;
    }
    if (hostName == null)
    {
      hostName = java.net.InetAddress.getLocalHost().getHostName();
    }
    return hostName;
  }
}
