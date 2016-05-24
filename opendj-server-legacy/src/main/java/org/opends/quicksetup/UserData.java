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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.installer.DataReplicationOptions;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.HostPort;
import org.opends.server.util.CollectionUtils;

import com.forgerock.opendj.cli.CliConstants;

/**
 * This class is used to provide a data model for the different parameters
 * that the user can provide in the installation wizard.
 */
public class UserData
{
  private String serverLocation;
  private HostPort hostPort = new HostPort(null, 0);
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
  private boolean createAdministrator;
  private boolean quiet;
  private boolean verbose;
  private final boolean interactive;
  private boolean forceOnError;

  private ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType;
  private NewSuffixOptions newSuffixOptions;
  private DataReplicationOptions replicationOptions;
  private SuffixesToReplicateOptions suffixesToReplicateOptions;
  private final Map<ServerDescriptor, AuthenticationData> remoteWithNoReplicationPort;

  private Map<String, JavaArguments> hmJavaArguments;
  private Map<String, JavaArguments> hmDefaultJavaArguments;

  private static String defaultHostName;

  private int connectTimeout = CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT;

  /** The script name to be used to get and set the java arguments for the server runtime. */
  public final static String SERVER_SCRIPT_NAME = "start-ds";
  /** The script name to be used to get and set the java arguments for the (off-line) import. */
  public final static String IMPORT_SCRIPT_NAME = "import-ldif.offline";

  /** Creates a user data object with default values. */
  public UserData() {
    interactive = true;
    startServer = true;
    enableWindowsService = false;
    forceOnError = true;
    verbose = false;

    LinkedList<String> baseDn = CollectionUtils.newLinkedList("dc=example,dc=com");
    NewSuffixOptions defaultNewSuffixOptions = NewSuffixOptions.createEmpty(baseDn);
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

    remoteWithNoReplicationPort = new HashMap<>();

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
    hostPort = new HostPort(hostName, hostPort.getPort());
  }

  /**
   * Returns the server host name.
   * @return the server host name.
   */
  public String getHostName()
  {
    return hostPort.getHost();
  }

  /**
   * Returns the server host name and LDAP port.
   * @return the server host name and LDAP port.
   */
  public HostPort getHostPort()
  {
    return hostPort;
  }

  /**
   * Sets the server LDAP port.
   * @param serverPort the new server LDAP port.
   */
  public void setServerPort(int serverPort)
  {
    hostPort = new HostPort(hostPort.getHost(), serverPort);
  }

  /**
   * Returns the server LDAP port.
   * @return the server LDAP port.
   */
  public int getServerPort()
  {
    return hostPort.getPort();
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
   * Returns the new userRoot backend type.
   *
   * @return the new userRoot backend type.
   */
  public ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> getBackendType()
  {
    return backendType;
  }

  /**
   * Sets the new userRoot backend type.
   *
   * @param backendType
   *          The new backend type. This string must be compatible with
   *          dsconfig tool.
   */
  public void setBackendType(ManagedObjectDefinition<? extends BackendCfgClient, ? extends BackendCfg> backendType)
  {
    this.backendType = backendType;
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
   * Sets whether this session should print messages to the
   * console if in CLI mode.
   * @param quiet where true indicates this session should be quiet
   */
  public void setQuiet(boolean quiet) {
    this.quiet = quiet;
  }

  /**
   * Indicates whether the user has requested quiet mode.
   * <p>
   * Quiet mode in the CLI means that nothing is written to output including
   * prompts for information and whether to continue an operation
   * experiencing errors.
   *
   * @return boolean where true indicates this session should be quiet.
   */
  public boolean isQuiet() {
    return this.quiet;
  }

  /**
   * Sets whether this session should be verbose.
   * @param verbose where true indicates this session should be verbose
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Indicates whether the user has requested verbose mode.
   *
   * @return boolean where true indicates this session should be verbose.
   */
  public boolean isVerbose() {
    return this.verbose;
  }

  /**
   * Sets whether we must continue when there is a non critical error.
   * @param forceOnError where true indicates to continue uninstall if there is
   * a non critical error.
   */
  public void setForceOnError(boolean forceOnError) {
    this.forceOnError = forceOnError;
  }

  /**
   * Indicates whether the user has requested to continue when a non
   * critical error occurs.
   *
   * @return boolean where true indicates to continue uninstall if there is a
   * non critical error.
   */
  public boolean isForceOnError() {
    return this.forceOnError;
  }

  /**
   * Indicates whether the user has requested interactive mode.
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
  public static int getDefaultPort()
  {
    return getDefaultPort(389);
  }

  /**
   * Provides the administration port that will be proposed to the user in the
   * second page of the installation wizard. It will check whether we can use
   * ports of type X444 and if not it will return -1.
   *
   * @return the free port of type x444 if it is available and we can use and -1
   * if not.
   */
  public static int getDefaultAdminConnectorPort()
  {
    return getDefaultPort(4444);
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
    int port = defaultLdapPort - 389 + 636;
    // Try first with the correlated port of the default LDAP port.
    if (Utils.canUseAsPort(port))
    {
      return port;
    }

    return getDefaultPort(636);
  }

  private static int getDefaultPort(int basePort)
  {
    for (int i = 0; i < 10000; i += 1000)
    {
      int port = i + basePort;
      if (Utils.canUseAsPort(port))
      {
        return port;
      }
    }
    return -1;
  }

  /**
   * Provides the port that will be used by default for JMX.
   *
   * @param forbiddenPorts an array of ports that we cannot use.
   * @return the port X689 if it is available and we can use and -1 if not.
   */
  public static int getDefaultJMXPort(int[] forbiddenPorts)
  {
    int defaultJMXPort = -1;

    for (int i=0;i<65000 && defaultJMXPort == -1;i+=1000)
    {
      int port = i + CliConstants.DEFAULT_JMX_PORT;
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
        /**
         * Search for a host name of the form host.example.com on each
         * interface, except the loop back. Prefer interfaces of the form ethX.
         */
        @Override
        public void run()
        {
          try
          {
            SortedMap<String, String> hostNames = new TreeMap<>();
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
  public Map<ServerDescriptor, AuthenticationData> getRemoteWithNoReplicationPort()
  {
    return new HashMap<>(remoteWithNoReplicationPort);
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
    hmJavaArguments = new HashMap<>();
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

    hmDefaultJavaArguments = new HashMap<>(hmJavaArguments);
  }

  private String[] getClientScripts()
  {
    return new String[] {
      "backup.online", "base64", "create-rc-script", "dsconfig",
      "dsreplication", "export-ldif.online",
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
        "upgrade", "verify-index", "backendstat"
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
