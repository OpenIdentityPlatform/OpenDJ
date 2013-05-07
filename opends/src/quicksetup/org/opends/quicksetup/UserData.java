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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 */

package org.opends.quicksetup;

import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.installer.DataReplicationOptions;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.util.Utils;

/**
 * This class is used to provide a data model for the different parameters
 * that the user can provide in the installation wizard.
 */
public class UserData
{
  private String serverLocation;

  private String hostName;

  private int serverPort;

  private int adminConnectorPort;

  private String directoryManagerDn;

  private String directoryManagerPwd;

  private String globalAdministratorUID;

  private String globalAdministratorPassword;

  private SecurityOptions securityOptions;
  private int serverJMXPort = -1;

  private boolean startServer;

  private boolean stopServer;

  private boolean enableWindowsService;

  private NewSuffixOptions newSuffixOptions;

  private DataReplicationOptions replicationOptions;

  private boolean createAdministrator;

  private SuffixesToReplicateOptions suffixesToReplicateOptions;

  private final Map<ServerDescriptor, AuthenticationData>
  remoteWithNoReplicationPort;

  private boolean quiet;

  private boolean verbose;

  private boolean interactive;

  private boolean forceOnError;

  private Map<String, JavaArguments> hmJavaArguments;
  private Map<String, JavaArguments> hmDefaultJavaArguments;

  private static String defaultHostName;

  private int connectTimeout = ConnectionUtils.getDefaultLDAPTimeout();

  /**
   * The script name to be used to get and set the java arguments for the
   * server runtime.
   */
  public static String SERVER_SCRIPT_NAME = "start-ds";
  /**
   * The script name to be used to get and set the java arguments for the
   * (off-line) import.
   */
  public static String IMPORT_SCRIPT_NAME = "import-ldif.offline";

  /**
   * Creates a user data object with default values.
   */
  public UserData() {
    interactive = true;
    startServer = true;
    enableWindowsService = false;
    forceOnError = true;
    verbose = false;

    LinkedList<String> baseDn = new LinkedList<String>();
    baseDn.add("dc=example,dc=com");
    NewSuffixOptions defaultNewSuffixOptions = NewSuffixOptions.createBaseEntry(
        baseDn);
    setNewSuffixOptions(defaultNewSuffixOptions);

    // See what we can propose as port
    int defaultLdapPort = getDefaultPort();
    if (defaultLdapPort != -1)
    {
      setServerPort(defaultLdapPort);
    }

//  See what we can propose as port
    int defaultAdminPort = getDefaultAdminConnectorPort();
    if (defaultAdminPort != -1)
    {
      setAdminConnectorPort(defaultAdminPort);
    }

    setHostName(getDefaultHostName());

    setDirectoryManagerDn(Constants.DIRECTORY_MANAGER_DN);

    setNewSuffixOptions(defaultNewSuffixOptions);
    DataReplicationOptions repl = DataReplicationOptions.createStandalone();
    setReplicationOptions(repl);
    setGlobalAdministratorUID(Constants.GLOBAL_ADMIN_UID);

    SuffixesToReplicateOptions suffixes =
      new SuffixesToReplicateOptions(
          SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES,
          new HashSet<SuffixDescriptor>(),
          new HashSet<SuffixDescriptor>());
    setSuffixesToReplicateOptions(suffixes);
    SecurityOptions sec = SecurityOptions.createNoCertificateOptions();
    sec.setSslPort(getDefaultSslPort(defaultLdapPort));
    setSecurityOptions(sec);

    remoteWithNoReplicationPort =
      new HashMap<ServerDescriptor, AuthenticationData>();

    createDefaultJavaArguments();
  }

  /**
   * Sets the location of the server (installation path).
   * @param serverLocation the new server location (installation path).
   */
  public void setServerLocation(String serverLocation)
  {
    this.serverLocation = serverLocation;
  }

  /**
   * Returns the location of the server (installation path).
   * @return the location of the server (installation path).
   */
  public String getServerLocation()
  {
    return serverLocation;
  }

  /**
   * Sets the host name.
   * @param hostName the server host name.
   */
  public void setHostName(String hostName)
  {
    this.hostName = hostName;
  }

  /**
   * Returns the server host name.
   * @return the server host name.
   */
  public String getHostName()
  {
    return hostName;
  }

  /**
   * Sets the server LDAP port.
   * @param serverPort the new server LDAP port.
   */
  public void setServerPort(int serverPort)
  {
    this.serverPort = serverPort;
  }

  /**
   * Returns the server LDAP port.
   * @return the server LDAP port.
   */
  public int getServerPort()
  {
    return serverPort;
  }

  /**
   * Sets the admin connector port.
   * @param adminConnectorPort the new admin connector port.
   */
  public void setAdminConnectorPort(int adminConnectorPort)
  {
    this.adminConnectorPort = adminConnectorPort;
  }

  /**
   * Returns the admin connector port.
   * @return the admin connector port.
   */
  public int getAdminConnectorPort()
  {
    return adminConnectorPort;
  }

  /**
   * Sets the server JMX port.
   * @param serverJMXPort the new server JMX port.
   */
  public void setServerJMXPort(int serverJMXPort)
  {
    this.serverJMXPort = serverJMXPort;
  }

  /**
   * Returns the server JMX port.
   * @return the server JMX port.
   */
  public int getServerJMXPort()
  {
    return serverJMXPort;
  }

  /**
   * Returns the Directory Manager DN.
   * @return the Directory Manager DN.
   */
  public String getDirectoryManagerDn()
  {
    return directoryManagerDn;
  }

  /**
   * Sets the new Directory Manager DN.
   * @param directoryManagerDn the new Directory Manager DN.
   */
  public void setDirectoryManagerDn(String directoryManagerDn)
  {
    this.directoryManagerDn = directoryManagerDn;
  }

  /**
   * Returns the Directory Manager password.
   * @return the Directory Manager password.
   */
  public String getDirectoryManagerPwd()
  {
    return directoryManagerPwd;
  }

  /**
   * Sets the new Directory Manager password.
   * @param directoryManagerPwd the new Directory Manager password.
   */
  public void setDirectoryManagerPwd(String directoryManagerPwd)
  {
    this.directoryManagerPwd = directoryManagerPwd;
  }

  /**
   * Returns <CODE>true</CODE> if the server must be started once the
   * installation is finished, <CODE>false</CODE> if not.
   * @return <CODE>true</CODE> if the server must be started once the
   * installation is finished, <CODE>false</CODE> if not.
   */
  public boolean getStartServer()
  {
    return startServer;
  }

  /**
   * Sets whether we want to start the server once the installation is finished
   * or not.
   * @param startServer the boolean indicating whether to start the server or
   * not.
   */
  public void setStartServer(boolean startServer)
  {
    this.startServer = startServer;
  }

  /**
   * Sets whether to stop the server or not.
   * @param stopServer stop the server or not.
   */
  public void setStopServer(boolean stopServer)
  {
    this.stopServer = stopServer;
  }

  /**
   * Returns whether the user wants to stop the server or not.
   * @return <CODE>true</CODE> if the user wants to stop the server and <CODE>\
   * false</CODE> otherwise.
   */
  public boolean getStopServer()
  {
    return stopServer;
  }

  /**
   * Returns <CODE>true</CODE> if the windows service must be enabled during
   * installation, <CODE>false</CODE> if not.
   * @return <CODE>true</CODE> if the windows service must be enabled during
   * installation, <CODE>false</CODE> if not.
   */
  public boolean getEnableWindowsService()
  {
    return enableWindowsService;
  }

  /**
   * Sets whether we want to enable windows service during installation or not.
   * @param enableWindowsService the boolean indicating whether we want to
   * enable windows service during installation or not.
   */
  public void setEnableWindowsService(boolean enableWindowsService)
  {
    this.enableWindowsService = enableWindowsService;
  }

  /**
   * Returns the NewSuffixOptions object representing the data in the New Suffix
   * Data Options panel.
   * @return the NewSuffixOptions object representing the data in the New Suffix
   * Data Options panel.
   */
  public NewSuffixOptions getNewSuffixOptions()
  {
    return newSuffixOptions;
  }

  /**
   * Sets the NewSuffixOptions object representing the data in the New Suffix
   * Data Options panel.
   * @param newSuffixOptions the NewSuffixOptions object representing the data
   * in the New Suffix Data Options panel.
   */
  public void setNewSuffixOptions(NewSuffixOptions newSuffixOptions)
  {
    this.newSuffixOptions = newSuffixOptions;
  }

  /**
   * Returns the DataReplicationOptions object representing the data in the
   * Data Replication panel.
   * @return the DataReplicationOptions object representing the data in the
   * Data Replication panel.
   */
  public DataReplicationOptions getReplicationOptions()
  {
    return replicationOptions;
  }

  /**
   * Sets the DataReplicationOptions object representing the data in the
   * Data Replication panel.
   * @param replicationOptions the DataReplicationOptions object
   * representing the data in the Data Replication panel.
   */
  public void setReplicationOptions(
      DataReplicationOptions replicationOptions)
  {
    this.replicationOptions = replicationOptions;
  }

  /**
   * Returns whether must create a global administrator or not.
   * @return <CODE>true</CODE> if we must create a global administrator and
   * <CODE>false</CODE> otherwise.
   */
  public boolean mustCreateAdministrator()
  {
    return createAdministrator;
  }

  /**
   * Sets whether must create a global administrator or not.
   * @param createAdministrator whether we must create a global administrator or
   * not.
   */
  public void createAdministrator(boolean createAdministrator)
  {
    this.createAdministrator = createAdministrator;
  }

  /**
   * Returns the UID of the global administrator.
   * @return the UID of the global administrator.
   */
  public String getGlobalAdministratorUID()
  {
    return globalAdministratorUID;
  }

  /**
   * Sets the UID of the global administrator.
   * @param globalAdministratorUID the UID of the global administrator.
   */
  public void setGlobalAdministratorUID(String globalAdministratorUID)
  {
    this.globalAdministratorUID = globalAdministratorUID;
  }

  /**
   * Returns the password of the global administrator.
   * @return the password of the global administrator.
   */
  public String getGlobalAdministratorPassword()
  {
    return globalAdministratorPassword;
  }

  /**
   * Sets the password of the global administrator.
   * @param globalAdministratorPwd the password of the global administrator.
   */
  public void setGlobalAdministratorPassword(String globalAdministratorPwd)
  {
    this.globalAdministratorPassword = globalAdministratorPwd;
  }

  /**
   * Sets the suffixes to replicate options.
   * @param suffixesToReplicateOptions the suffixes to replicate options
   * object.
   */
  public void setSuffixesToReplicateOptions(
      SuffixesToReplicateOptions suffixesToReplicateOptions)
  {
    this.suffixesToReplicateOptions = suffixesToReplicateOptions;
  }

  /**
   * Returns the suffixes to replicate options.
   * @return the suffixes to replicate options.
   */
  public SuffixesToReplicateOptions getSuffixesToReplicateOptions()
  {
    return suffixesToReplicateOptions;
  }

  /**
   * Returns the SecurityOptions representing the SSL/StartTLS configuration
   * chosen by the user.
   * @return the SecurityOptions representing the SSL/StartTLS configuration
   * chosen by the user.
   */
  public SecurityOptions getSecurityOptions()
  {
    return securityOptions;
  }

  /**
   * Sets the SecurityOptions representing the SSL/StartTLS configuration
   * chosen by the user.
   * @param securityOptions the SecurityOptions representing the SSL/StartTLS
   * configuration chosen by the user.
   */
  public void setSecurityOptions(SecurityOptions securityOptions)
  {
    this.securityOptions = securityOptions;
  }

  /**
   * Sets whether or not this session should print messages to the
   * console if in CLI mode.
   * @param quiet where true indicates this sesssion should be quiet
   */
  public void setQuiet(boolean quiet) {
    this.quiet = quiet;
  }

  /**
   * Indicates whether or not the user has requested quiet mode.
   * <p>
   * Quiet mode in the CLI means that nothing is written to output including
   * prompts for information and whether or not to continue an operation
   * experiencing errors.
   *
   * @return boolean where true indicates this session should be quiet.
   */
  public boolean isQuiet() {
    return this.quiet;
  }

  /**
   * Sets whether or not this session should be verbose.
   * @param verbose where true indicates this sesssion should be verbose
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Indicates whether or not the user has requested verbose mode.
   *
   * @return boolean where true indicates this session should be verbose.
   */
  public boolean isVerbose() {
    return this.verbose;
  }

  /**
   * Sets whether or not we must continue when there is a non critical error.
   * @param forceOnError where true indicates to continue uninstall if there is
   * a non critical error.
   */
  public void setForceOnError(boolean forceOnError) {
    this.forceOnError = forceOnError;
  }

  /**
   * Indicates whether or not the user has requested to continue when a non
   * critical error occurs.
   *
   * @return boolean where true indicates to continue uninstall if there is a
   * non critical error.
   */
  public boolean isForceOnError() {
    return this.forceOnError;
  }

  /**
   * Indicates whether or not the user has requested interactive mode.
   * <p>
   * Interactive mode in the CLI means that the CLI will prompt the user
   * for more information if it is required.  Interactivity does NOT
   * affect prompts to the user regarding actions like continuing an operation
   * that is experiencing errors.
   *
   * @return boolean where true indicates this session should be interactive
   */
  public boolean isInteractive() {
    return this.interactive;
  }

  /**
   * Provides the port that will be proposed to the user in the second page of
   * the installation wizard. It will check whether we can use ports of type
   * X389 and if not it will return -1.
   *
   * @return the free port of type x389 if it is available and we can use and -1
   * if not.
   */
  static public int getDefaultPort()
  {
    int defaultPort = -1;

    for (int i=0;i<10000 && (defaultPort == -1);i+=1000)
    {
      int port = i + 389;
      if (Utils.canUseAsPort(port))
      {
        defaultPort = port;
      }
    }
    return defaultPort;
  }

  /**
   * Provides the administration port that will be proposed to the user in the
   * second page of the installation wizard. It will check whether we can use
   * ports of type X444 and if not it will return -1.
   *
   * @return the free port of type x444 if it is available and we can use and -1
   * if not.
   */
  static public int getDefaultAdminConnectorPort()
  {
    int defaultPort = -1;

    for (int i=0;i<10000 && (defaultPort == -1);i+=1000)
    {
      int port = i + 4444;
      if (Utils.canUseAsPort(port))
      {
        defaultPort = port;
      }
    }
    return defaultPort;
  }

  /**
   * Provides the port that will be proposed to the user in the security dialog
   *  of the installation wizard. It will check whether we can use ports of type
   * X636 and if not it will return -1.
   * @param defaultLdapPort the default port used for LDAP.
   *
   * @return the free port of type X636 if it is available and we can use and -1
   * if not.
   */
  public static int getDefaultSslPort(int defaultLdapPort)
  {
    int defaultPort = -1;

    int port = defaultLdapPort - 389 + 636;
    // Try first with the correlated port of the default LDAP port.
    if (Utils.canUseAsPort(port))
    {
      defaultPort = port;
    }

    for (int i=0;i<10000 && (defaultPort == -1);i+=1000)
    {
      port = i + 636;
      if (Utils.canUseAsPort(port))
      {
        defaultPort = port;
      }
    }
    return defaultPort;
  }

  /**
   * Provides the port that will be used by default for JMX.
   *
   * @param forbiddenPorts an array of ports that we cannot use.
   * @return the port X689 if it is available and we can use and -1 if not.
   */
  static public int getDefaultJMXPort(int[] forbiddenPorts)
  {
    int defaultJMXPort = -1;

    for (int i=0;i<65000 && (defaultJMXPort == -1);i+=1000)
    {
      int port = i + org.opends.server.util.SetupUtils.getDefaultJMXPort();
      boolean isForbidden = false;
      if (forbiddenPorts != null)
      {
        for (int j=0; j<forbiddenPorts.length && !isForbidden; j++)
        {
          isForbidden = forbiddenPorts[j] == port;
        }
      }
      if (!isForbidden && Utils.canUseAsPort(port))
      {
        defaultJMXPort = port;
      }
    }
    return defaultJMXPort;
  }

  /**
   * Provides the default host name that will be proposed to the user for the
   * local host.
   * @return the default host name that will be proposed to the user for the
   * local host.
   */
  public static String getDefaultHostName()
  {
    if (defaultHostName == null)
    {
      // Run a thread in the background in order to avoid blocking the
      // application if reverse DNS lookups take a long time.
      final CountDownLatch latch = new CountDownLatch(1);
      Thread t = new Thread(new Runnable()
      {
        // Search for a host name of the form host.example.com on each
        // interface, except the loop back. Prefer interfaces of the form ethX.
        public void run()
        {
          try
          {
            SortedMap<String, String> hostNames = new TreeMap<String, String>();
            Enumeration<NetworkInterface> i = NetworkInterface
                .getNetworkInterfaces();
            while (i.hasMoreElements())
            {
              NetworkInterface n = i.nextElement();

              // Skip loop back interface.
              if (n.isLoopback())
              {
                continue;
              }

              // Check each interface address (IPv4 and IPv6).
              String ipv4HostName = null;
              String ipv6HostName = null;
              Enumeration<InetAddress> j = n.getInetAddresses();
              while (j.hasMoreElements())
              {
                InetAddress address = j.nextElement();
                String hostAddress = address.getHostAddress();
                String hostName = address.getCanonicalHostName();

                // Ignore hostnames which are IP addresses.
                if (!hostAddress.equals(hostName))
                {
                  if (address instanceof Inet4Address)
                  {
                    ipv4HostName = hostName;
                  }
                  else if (address instanceof Inet6Address)
                  {
                    ipv6HostName = hostName;
                  }
                }
              }

              // Remember the host name if it looks fully qualified.
              String fqHostName = null;
              if (ipv4HostName != null && ipv4HostName.contains("."))
              {
                fqHostName = ipv4HostName;
              }
              else if (ipv6HostName != null && ipv6HostName.contains("."))
              {
                fqHostName = ipv6HostName;
              }

              if (fqHostName != null)
              {
                hostNames.put(n.getName(), fqHostName);

                // This looks like a fully qualified name on a ethX interface,
                // so
                // use that and break out.
                if (n.getName().startsWith("eth"))
                {
                  defaultHostName = fqHostName;
                  break;
                }
              }
            }

            if (defaultHostName == null && !hostNames.isEmpty())
            {
              // No ethX host name, so try any other host name that was found.
              defaultHostName = hostNames.values().iterator().next();
            }
          }
          catch (Exception e)
          {
            // Ignore - we'll default to the loopback address later.
          }

          latch.countDown();
        }
      });

      try
      {
        t.setDaemon(true);
        t.start();
        latch.await(1, TimeUnit.SECONDS);
      }
      catch (Exception e)
      {
        // Ignore - we'll default to the loopback address later.
      }

      if (defaultHostName == null)
      {
        // No host names found, so use the loop back.
        try
        {
          defaultHostName = InetAddress.getLocalHost().getHostName();
        }
        catch (Exception e)
        {
          // Not much we can do here.
          defaultHostName = "localhost";
        }
      }
    }
    return defaultHostName;
  }

  /**
   * Returns a Map containing as key a ServerDescriptor and as value an Integer
   * corresponding to the Replication Port chosen by the user.
   *
   * Only the servers that have no replication port appear on this map.
   * @return a Map containing as key a ServerDescriptor and as value an
   * AuthenticationData corresponding to the Replication Port chosen by the
   * user.
   */
  public Map<ServerDescriptor, AuthenticationData>
  getRemoteWithNoReplicationPort()
  {
    HashMap<ServerDescriptor, AuthenticationData> copy =
      new HashMap<ServerDescriptor, AuthenticationData>();
    copy.putAll(remoteWithNoReplicationPort);
    return copy;
  }

  /**
   * Sets a the Replication Ports chosen by the user in the remote servers.
   * @param remoteWithNoReplicationPort the Map containing as key a
   * ServerDescriptor and as value an AuthenticationData corresponding to the
   * Replication Port chosen by the user.
   */
  public void setRemoteWithNoReplicationPort(
      Map<ServerDescriptor, AuthenticationData> remoteWithNoReplicationPort)
  {
    this.remoteWithNoReplicationPort.clear();
    this.remoteWithNoReplicationPort.putAll(remoteWithNoReplicationPort);
  }

  /**
   * Returns the different script names for which there are java arguments.
   * @return the different script names for which there are java arguments.
   */
  public Set<String> getScriptNamesForJavaArguments()
  {
    return hmJavaArguments.keySet();
  }

  /**
   * Returns the java arguments associated with a script name.  Returns
   * <CODE>null</CODE> if no java arguments are defined.
   * @param scriptName the script name.
   * @return the java arguments associated with a script name.
   */
  public JavaArguments getJavaArguments(String scriptName)
  {
    return hmJavaArguments.get(scriptName);
  }

  /**
   * Returns the default java arguments associated with a script name.  Returns
   * <CODE>null</CODE> if no java arguments are defined.
   * @param scriptName the script name.
   * @return the default java arguments associated with a script name.
   */
  public JavaArguments getDefaultJavaArguments(String scriptName)
  {
    return hmDefaultJavaArguments.get(scriptName);
  }

  /**
   * Sets the java arguments associated with a script name.
   * @param scriptName the script name.
   * @param args the java arguments associated with a script name.
   */
  public void setJavaArguments(String scriptName, JavaArguments args)
  {
    hmJavaArguments.put(scriptName, args);
  }



  private void createDefaultJavaArguments()
  {
    hmJavaArguments = new HashMap<String, JavaArguments>();
    int maxMemoryMb = 256;
    int minMemoryMb = 128;
    final int maxMemoryBytes = maxMemoryMb * 1024 * 1024;
    // If the current max memory is bigger than the max heap we want to set,
    // assume that the JVM ergonomics are going to be able to allocate enough
    // memory.
    long currentMaxMemoryBytes = Runtime.getRuntime().maxMemory();
    if (currentMaxMemoryBytes > maxMemoryBytes)
    {
      maxMemoryMb = -1;
      minMemoryMb = -1;
    }
    for (String clientScript : getClientScripts())
    {
      JavaArguments javaArgument = new JavaArguments();
      javaArgument.setInitialMemory(8);
      javaArgument.setAdditionalArguments(new String[] {"-client"});
      hmJavaArguments.put(clientScript, javaArgument);
    }
    for (String serverScript : getServerScripts())
    {
      JavaArguments javaArgument = new JavaArguments();
      javaArgument.setInitialMemory(minMemoryMb);
      javaArgument.setMaxMemory(maxMemoryMb);
      javaArgument.setAdditionalArguments(new String[] {"-server"});
      hmJavaArguments.put(serverScript, javaArgument);
    }

    JavaArguments controlPanelJavaArgument = new JavaArguments();
    controlPanelJavaArgument.setInitialMemory(64);
    controlPanelJavaArgument.setMaxMemory(128);
    controlPanelJavaArgument.setAdditionalArguments(new String[] {"-client"});
    hmJavaArguments.put("control-panel", controlPanelJavaArgument);

    hmDefaultJavaArguments =
      new HashMap<String, JavaArguments>(hmJavaArguments);
  }

  private String[] getClientScripts()
  {
    return new String[] {
      "backup.online", "base64", "create-rc-script", "dsconfig",
      "dsreplication", "dsframework", "export-ldif.online",
      "import-ldif.online", "ldapcompare", "ldapdelete",
      "ldapmodify", "ldappasswordmodify", "ldapsearch", "list-backends",
      "manage-account", "manage-tasks", "restore.online", "stop-ds",
      "status", "uninstall", "setup"
    };
  }

  private String[] getServerScripts()
  {
    return new String[]
    {
        "backup.offline", "dsreplication.offline",
        "encode-password", "export-ldif.offline",
        IMPORT_SCRIPT_NAME, "ldif-diff", "ldifmodify", "ldifsearch",
        "make-ldif", "rebuild-index", "restore.offline", SERVER_SCRIPT_NAME,
        "upgrade", "verify-index", "dbtest"
    };
  }

  /**
   * Sets the timeout to be used to establish a connection.
   * @param connectTimeout the timeout to be used to establish a connection.
   */
  public void setConnectTimeout(int connectTimeout)
  {
    this.connectTimeout = connectTimeout;
  }

  /**
   * Returns the timeout to be used to connect in milliseconds.
   * @return the timeout to be used to connect in milliseconds.  Returns
   * {@code 0} if there is no timeout.
   */
  public int getConnectTimeout()
  {
    return connectTimeout;
  }
}
