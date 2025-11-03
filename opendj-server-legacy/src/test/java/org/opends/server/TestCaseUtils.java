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
 * Portions Copyright 2013 Manuel Gaupp
 * Portions Copyright 2018-2025 3A Systems, LLC
 */
package org.opends.server;

import static org.forgerock.opendj.server.embedded.ConfigParameters.configParams;
import static org.forgerock.opendj.server.embedded.ConnectionParameters.connectionParams;
import static org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer.manageEmbeddedDirectoryServer;

import static org.opends.server.loggers.TextAccessLogPublisher.getStartupTextAccessPublisher;
import static org.opends.server.loggers.TextErrorLogPublisher.*;
import static org.opends.server.loggers.TextHTTPAccessLogPublisher.getStartupTextHTTPAccessPublisher;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.opends.server.util.ServerConstants.PROPERTY_RUNNING_UNIT_TESTS;
import static org.testng.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.forgerock.opendj.ldap.tools.LDAPSearch;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.dsconfig.DSConfig;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.WorkQueue;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.backends.pluggable.BackendImpl;
import org.opends.server.backends.pluggable.EntryContainer;
import org.opends.server.backends.pluggable.RootContainer;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.loggers.AccessLogPublisher;
import org.opends.server.loggers.AccessLogger;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.HTTPAccessLogPublisher;
import org.opends.server.loggers.HTTPAccessLogger;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPReader;
import com.forgerock.opendj.ldap.tools.LDAPModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.LDIFReader;

import com.forgerock.opendj.util.OperatingSystem;

import static org.mockito.Mockito.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/** This class defines some utility functions which can be used by test cases. */
@SuppressWarnings("javadoc")
public final class TestCaseUtils {
  /** The name of the system property that specifies the server build root. */
  public static final String PROPERTY_BUILD_ROOT = "org.opends.server.BuildRoot";
  public static final String PROPERTY_BUILD_DIR  = "org.opends.server.BuildDir";

   /**
   * The name of the system property that specifies an existing OpenDS
   * installation root (inside or outside of the source tree).
   */
  public static final String PROPERTY_INSTALLED_ROOT =
      "org.opends.server.InstalledRoot";

  /**
   * The name of the system property that specifies an LDIF file
   * with changes compare to the default config.ldif.
   */
  public static final String PROPERTY_CONFIG_CHANGE_FILE =
      "org.opends.server.ConfigChangeFile";

  /**
   * The name of the system property that specifies if the test instance
   * directory needs to be wiped out before starting the setup or not. This will
   * let the caller the possibility to copy some files (ie extensions) inside
   * the test instance directory before the server starts up.
   */
  public static final String PROPERTY_CLEANUP_REQUIRED =
      "org.opends.server.CleanupDirectories";

  /**
   * The name of the system property that specifies the ldap port.
   * Set this property when running the server if you want to use a given
   * port number, otherwise a port is chosen randomly at test startup time.
   */
  public static final String PROPERTY_LDAP_PORT =
       "org.opends.server.LdapPort";

  /**
   * The name of the system property that specifies the admin port. Set this
   * property when running the server if you want to use a given port number,
   * otherwise a port is chosen randomly at test startup time.
   */
  public static final String PROPERTY_ADMIN_PORT =
       "org.opends.server.AdminPort";

  /**
   * If this System property is set to true, then the classes/ directory
   * will be copied into the server package setup for the tests.  This allows
   * the server tools (e.g. ldapsearch) to be used on a live server, but it
   * takes a while to copy all of the files, so we don't do it by default.
   */
  public static final String PROPERTY_COPY_CLASSES_TO_TEST_PKG =
       "org.opends.test.copyClassesToTestPackage";

  /**
   * The string representation of the DN that will be used as the base entry for
   * the test backend.  This must not be changed, as there are a number of test
   * cases that depend on this specific value of "o=test".
   */
  public static final String TEST_ROOT_DN_STRING = "o=test";

  /** The backend if for the test backend. */
  public static final String TEST_BACKEND_ID = "test";

  /**
   * The string representation of the OpenDMK jar file location
   * that will be used as base to determine if snmp is included or not.
   */
  public static final String PROPERTY_OPENDMK_LOCATION =
          "org.opends.server.snmp.opendmk";

  /** The test text writer for the Debug Logger. */
  public static TestTextWriter DEBUG_TEXT_WRITER = new TestTextWriter();

  /** The test text writer for the Error Logger. */
  public static TestTextWriter ERROR_TEXT_WRITER = new TestTextWriter();

  /** The test text writer for the Access Logger. */
  public static TestTextWriter ACCESS_TEXT_WRITER = new TestTextWriter();

  /** The test text writer for the HTTP Access Logger. */
  public static TestTextWriter HTTP_ACCESS_TEXT_WRITER = new TestTextWriter();

  /**
   * Indicates whether the server has already been started.  The value of this
   * constant must not be altered by anything outside the
   * <CODE>startServer</CODE> method.
   */
  public static boolean SERVER_STARTED;

  /**
   * This is used to store the schema as it was before starting the fake server
   * (for example, it could have been the real schema) so test tearDown can set it back.
   */
  private static Schema schemaBeforeStartingFakeServer;

  /** Incremented by one each time the server has restarted. */
  private static int serverRestarts;

  /** The paths to directories and files used in the tests. */
  public static TestPaths paths = new TestPaths();

  /** The ports used in the tests. */
  private static TestPorts ports;

  /** The embedded server used in the tests. */
  private static EmbeddedDirectoryServer server;

  /** The host name of the server used in the tests. */
  private static String hostname;

  /**
   * Setup in-memory versions of everything needed to run unit tests with the
   * {@link DirectoryServer} class.
   * <p>
   * This method is trying hard to provide sensible defaults and core data you
   * would expect from a normal install, including AttributeTypes, etc.
   *
   * @see #shutdownFakeServer() Matching method that must be called in the test
   *      tear down.
   */
  public static void startFakeServer() throws Exception
  {
	DirectoryServer.bootstrapClient();
    schemaBeforeStartingFakeServer = DirectoryServer.getInstance().getServerContext().getSchema();
    DirectoryServer.getInstance().getServerContext().getSchemaHandler().updateSchema(Schema.getDefaultSchema());
  }

  public static class TestPaths
  {
    final String buildRoot;
    final File buildDir;
    final File unitRoot;
    final String installedRoot;
    final File testInstallRoot;
    public final File testInstanceRoot;
    final File testConfigDir;
    final File configFile;
    final File testSrcRoot;

    TestPaths()
    {
      installedRoot = System.getProperty(PROPERTY_INSTALLED_ROOT);
      buildRoot = System.getProperty(PROPERTY_BUILD_ROOT,System.getProperty("user.dir"));
      String buildDirStr = System.getProperty(PROPERTY_BUILD_DIR, buildRoot + File.separator + "target");
      buildDir = new File(buildDirStr);
      unitRoot  = new File(buildDir, "unit-tests"+((testName!=null)?"/"+testName:""));
      if (installedRoot == null)
      {
         testInstallRoot = new File(unitRoot, "package-install");
         testInstanceRoot = new File(unitRoot, "package-instance");
      }
      else
      {
         testInstallRoot = new File(unitRoot, "package");
         testInstanceRoot = testInstallRoot;
      }
      testConfigDir = new File(testInstanceRoot, "config");
      configFile = new File(testConfigDir, "config.ldif");
      testSrcRoot = new File(buildRoot + File.separator + "tests" + File.separator + "unit-tests-testng");
    }
  }

  static class TestPorts
  {
    /** The LDAP port the server is bound to on start. */
    final int serverLdapPort;
    /** The Administration port the server is bound to on start. */
    final int serverAdminPort;
    /** The JMX port the server is bound to on start. */
    final int serverJmxPort;
    /** The LDAPS port the server is bound to on start. */
    final int serverLdapsPort;

    TestPorts() throws IOException
    {
      final int[] ports = findFreePorts(4);
      serverLdapPort = getFreePort(PROPERTY_LDAP_PORT, ports[0]);
      serverAdminPort = getFreePort(PROPERTY_ADMIN_PORT, ports[1]);
      serverJmxPort = ports[2];
      serverLdapsPort = ports[3];
    }
  }

  public static void startServer() throws Exception
  {
    System.setProperty(PROPERTY_RUNNING_UNIT_TESTS, "true");
    try {
      if (SERVER_STARTED)
      {
        return;
      }
      InvocationCounterPlugin.resetStartupCalled();
      initializePortsAndServer();
      deployDirectoryDirsAndFiles();
      setupLoggers();
      writeBuildInfoFile();
      server.start();
      assertTrue(InvocationCounterPlugin.startupCalled());
      // Save config.ldif for when we restart the server
      backupServerConfigLdif();
      SERVER_STARTED = true;
      initializeTestBackend(true);
    }
    catch (Exception e)
    {
      e.printStackTrace(originalSystemErr);
      throw e;
    }
  }

  private static void initializePortsAndServer() throws Exception
  {
    ports = new TestPorts();
    hostname = "127.0.0.1";
    server = manageEmbeddedDirectoryServer(
        configParams()
          .serverRootDirectory(paths.testInstallRoot.getPath())
          .serverInstanceDirectory(paths.testInstanceRoot.getPath())
          .configurationFile(paths.configFile.getPath()),
        connectionParams()
          .bindDn("cn=Directory Manager")
          .bindPassword("password")
          .hostName(hostname)
          .ldapPort(ports.serverLdapPort)
          .adminPort(ports.serverAdminPort),
         System.out,
         System.err);
  }
  public static void cleanTestPath() throws IOException {
	  deleteDirectory(paths.unitRoot);
  }
  
  /**
   * Setup the directory server in separate install root directory and instance root directory.
   * After this method the directory server should be ready to be started.
   */
  private static void deployDirectoryDirsAndFiles() throws IOException
  {
    // cleanup directories if necessary
    String cleanupRequiredString = System.getProperty(PROPERTY_CLEANUP_REQUIRED, "true");
    boolean cleanupRequired = !"false".equalsIgnoreCase(cleanupRequiredString);

    //originalSystemErr.println("start "+paths.unitRoot);
    if (cleanupRequired) {
      deleteDirectory(paths.testInstallRoot);
      deleteDirectory(paths.testInstanceRoot);
      paths.testInstallRoot.mkdirs();
      paths.testInstanceRoot.mkdirs();
    }

    // deploy the server to separate install directory and instance directory

    File testInstanceSchema = new File(paths.testInstanceRoot, "config" + File.separator + "schema");
    testInstanceSchema.mkdirs();

    // db_verify is second jeb backend used by the jeb verify test cases
    // db_rebuild is the third jeb backend used by the jeb rebuild test cases
    // db_unindexed is the forth backend used by the unindexed search privilege
    // test cases
    String[] installSubDirectories = { "bin", "lib", "bat", "config" };
    String[] instanceSubDirectories =
        { "bak", "changelogDb", "classes", "config", "db", "import-tmp", "db_verify", "ldif", "locks", "logs",
          "db_rebuild", "db_unindexed", "db_index_test", "db_import_test" };
    for (String s : installSubDirectories)
    {
      new File(paths.testInstallRoot, s).mkdir();
    }
    for (String s : instanceSubDirectories)
    {
      new File(paths.testInstanceRoot, s).mkdir();
    }

    // Copy the configuration, schema, and MakeLDIF resources into the
    // appropriate place under the test package.
    File makeLdifResourcesDir = Paths.get(paths.buildRoot, "..", "opendj-core", "src", "main", "resources",
                                          "org", "forgerock", "opendj", "ldif").toAbsolutePath().toFile();
    File serverClassesDir = new File(paths.buildDir, "classes");
    File unitClassesDir = new File(paths.unitRoot, "classes");
    File libDir = new File(paths.buildDir.getPath() + "/package/opendj/lib");
    File upgradeDir = new File(paths.buildDir.getPath() + "/package/opendj/template/config/upgrade");
    File resourceDir = new File(paths.buildRoot, "resource");
    File testResourceDir = new File(paths.testSrcRoot, "resource");
    // Set the class variable
    File testSchemaDir = new File(paths.testInstanceRoot, "config");
    File testClassesDir = new File(paths.testInstanceRoot, "classes");
    File testLibDir = new File(paths.testInstallRoot, "lib");
    File testBinDir = new File(paths.testInstallRoot, "bin");

    // Snmp resource
    String opendmkJarFileLocation = System.getProperty(PROPERTY_OPENDMK_LOCATION);

    File opendmkJar = new File(opendmkJarFileLocation, "jdmkrt.jar");

    File snmpResourceDir =
        new File(paths.buildRoot + File.separator + "src" + File.separator + "snmp" + File.separator + "resource");
    File snmpConfigDir = new File(snmpResourceDir, "config");
    File testSnmpResourceDir = new File(paths.testConfigDir + File.separator + "snmp");

    if (Boolean.getBoolean(PROPERTY_COPY_CLASSES_TO_TEST_PKG))
    {
      copyDirectory(serverClassesDir, testClassesDir);
      copyDirectory(unitClassesDir, testClassesDir);
    }

    if (paths.installedRoot != null)
    {
      copyDirectory(new File(paths.installedRoot), paths.testInstallRoot);

      // Get the instance location
    }
    else
    {
      copyDirectory(libDir, testLibDir);
      copyDirectory(new File(resourceDir, "bin"), testBinDir);
      copyDirectory(new File(resourceDir, "config"), paths.testConfigDir);
      // copy upgrade directory
      copyDirectory(upgradeDir, new File(paths.testConfigDir, "upgrade"));
      copyDirectory(new File(resourceDir, "schema"), new File(testSchemaDir, "schema"));
      copyDirectory(makeLdifResourcesDir, new File(paths.testConfigDir, "MakeLDIF"));
      copyDirectory(new File(snmpResourceDir, "security"), new File(testSnmpResourceDir, "security"));
      copyFileFromTo("server.keystore", testResourceDir, paths.testConfigDir);
      copyFileFromTo("server.truststore", testResourceDir, paths.testConfigDir);
      copyFileFromTo("client.keystore", testResourceDir, paths.testConfigDir);
      copyFileFromTo("client-emailAddress.keystore", testResourceDir, paths.testConfigDir);
      copyFileFromTo("client.truststore", testResourceDir, paths.testConfigDir);
      copyFileFromTo("server-cert.p12", testResourceDir, paths.testConfigDir);
      copyFileFromTo("client-cert.p12", testResourceDir, paths.testConfigDir);

      // Update the install.loc file
      File installLoc = new File(paths.testInstallRoot + File.separator + "instance.loc");
      installLoc.deleteOnExit();
      try (FileWriter w = new FileWriter(installLoc))
      {
        w.write(paths.testInstanceRoot.getAbsolutePath());
      }

      if (opendmkJar.exists())
      {
        appendFile(new File(snmpConfigDir, "config.snmp.ldif"), new File(paths.testConfigDir, "config.ldif"));
      }

      for (File f : testBinDir.listFiles())
      {
        try
        {
          FilePermission.setPermissions(f, FilePermission.decodeUNIXMode("755"));
        }
        catch (Exception e)
        {
        }
      }

      // Make the shell scripts in the bin directory executable, if possible.
      if (OperatingSystem.isUnixBased())
      {
        try
        {
          FilePermission perm = FilePermission.decodeUNIXMode("755");
          for (File f : testBinDir.listFiles())
          {
            if (f.getName().endsWith(".sh"))
            {
              FilePermission.setPermissions(f, perm);
            }
          }
        }
        catch (Exception e)
        {
        }
      }
    }
    copyTestConfigChangesFile();
  }

  private static void copyTestConfigChangesFile() throws FileNotFoundException, IOException
  {
    File testResourceDir = new File(paths.testSrcRoot, "resource");
    String defaultConfigChangeFile = testResourceDir + File.separator + "config-changes.ldif";
    String configChangeFile = System.getProperty(PROPERTY_CONFIG_CHANGE_FILE, defaultConfigChangeFile);

    try (BufferedReader reader = new BufferedReader(new FileReader(new File(configChangeFile)));
        FileOutputStream outFile = new FileOutputStream(new File(paths.testConfigDir, "config-changes.ldif"));
        PrintStream writer = new PrintStream(outFile))
    {
      String line;
      while ((line = reader.readLine()) != null)
      {
        line = line
            .replaceAll("#ldapport#", String.valueOf(ports.serverLdapPort))
            .replaceAll("#adminport#", String.valueOf(ports.serverAdminPort))
            .replaceAll("#jmxport#", String.valueOf(ports.serverJmxPort))
            .replaceAll("#ldapsport#", String.valueOf(ports.serverLdapsPort));

        writer.println(line);
      }
    }
  }

  private static void setupLoggers()
  {
    AccessLogger.getInstance().addLogPublisher(
        (AccessLogPublisher) getStartupTextAccessPublisher(ACCESS_TEXT_WRITER, false));

    HTTPAccessLogger.getInstance().addLogPublisher(
        (HTTPAccessLogPublisher) getStartupTextHTTPAccessPublisher(HTTP_ACCESS_TEXT_WRITER));

    // Enable more verbose error logger.
    ErrorLogger.getInstance().addLogPublisher(
        (ErrorLogPublisher) getToolStartupTextErrorPublisher(ERROR_TEXT_WRITER));
    
    ErrorLogger.getInstance().addLogPublisher(
            (ErrorLogPublisher) getServerStartupTextErrorPublisher(ERROR_TEXT_WRITER));

  }

  public static void setupTrace() {
    DebugLogger.getInstance().addPublisherIfRequired(DEBUG_TEXT_WRITER);
  }

  private static void writeBuildInfoFile() throws IOException
  {
    try (final FileWriter buildInfoWriter = new FileWriter(new File(paths.testConfigDir, "buildinfo")))
    {
      buildInfoWriter.write(BuildVersion.binaryVersion().toString());
    }
  }

  private static int getFreePort(String portPropertyName, int defaultPort) throws IOException
  {
    String port = System.getProperty(portPropertyName);
    if (port == null)
    {
      return defaultPort;
    }
    int portNb = Integer.parseInt(port);
    // Check this port is free
    bindPort(portNb).close();
    return portNb;
  }

  /**
   * Similar to startServer, but it will restart the server each time it is
   * called.  Since this is somewhat expensive, it should be called under
   * two circumstances.  Either in an @AfterClass method for a test that
   * makes lots of configuration changes to the server, or in a @BeforeClass
   * method for a test that is very sensitive to running in a clean server.
   *
   * @throws  IOException  If a problem occurs while interacting with the
   *                       filesystem to prepare the test package root.
   *
   * @throws  InitializationException  If a problem occurs while starting the
   *                                   server.
   *
   * @throws  ConfigException  If there is a problem with the server
   *                           configuration.
   */
  public static synchronized void restartServer()
         throws IOException, InitializationException, ConfigException,
                DirectoryException, Exception
  {
    if (!SERVER_STARTED) {
      startServer();
      return;
    }

    try {
      new File(DirectoryServer.getEnvironmentConfig().getSchemaDirectory(), "99-user.ldif").delete();

      long startMs = System.currentTimeMillis();

      clearLoggersContents();

      server.stop(TestCaseUtils.class.getSimpleName(), LocalizableMessage.raw("restart server for tests"));

      restoreServerConfigLdif();

      server.start();

      clearJEBackends();
      initializeTestBackend(true);

      // This generates too much noise, so it's disabled by default.
      // outputLogContentsIfError("Potential problem during in-core restart.  You be the judge.");

      // Keep track of these so we can report how long they took in the test summary
      long durationMs = System.currentTimeMillis() - startMs;
      restartTimesMs.add(durationMs);

      serverRestarts++;
    } catch (Exception e) {
      e.printStackTrace(originalSystemErr);
      throw e;
    }
  }

  /**
   * Returns the embedded server used for tests.
   *
   * @return the embedded server.
   */
  public static EmbeddedDirectoryServer getServer()
  {
    return server;
  }

  private static List<Long> restartTimesMs = new ArrayList<>();
  public static List<Long> getRestartTimesMs() {
    return Collections.unmodifiableList(restartTimesMs);
  }

  private static void clearJEBackends() throws Exception
  {
    for (LocalBackend<?> backend : getServerContext().getBackendConfigManager().getLocalBackends())
    {
      if (backend instanceof BackendImpl) {
        clearBackend(backend.getBackendID());
      }
    }
  }

  public static void clearDataBackends() throws Exception
  {
    clearJEBackends();
    clearMemoryBackend(TEST_BACKEND_ID);
  }

  private static File getTestConfigDir()
  {
    if (paths.testConfigDir == null) {
      throw new RuntimeException("The testConfigDir variable is not set yet!");
    }
    return paths.testConfigDir;
  }

  public static File getBuildRoot()
  {
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT,System.getProperty("user.dir"));
    return new File(buildRoot);
  }

  private static void backupServerConfigLdif() throws IOException
  {
    File testConfigDir = getTestConfigDir();
    copyFile(new File(testConfigDir, "config.ldif"),
             new File(testConfigDir, "config.ldif.for-restart"));
  }

  private static void restoreServerConfigLdif() throws IOException {
    File testConfigDir = getTestConfigDir();
    File from = new File(testConfigDir, "config.ldif.for-restart");
    File to = new File(testConfigDir, "config.ldif");

    // Sometimes this fails because config.ldif is in use, so we wait
    // and try it again.
    try {
      copyFile(from, to);
    } catch (IOException e) {
      sleep(1000);
      copyFile(from, to);
    }
  }

  /**
   * Bring the server to a quiescent state.  This includes waiting for all
   * operations to complete.  This can be used in a @BeforeMethod setup method
   * to make sure that the server has finished processing all operations
   * from previous tests.
   */
  public static void quiesceServer()
  {
    waitForOpsToComplete();
  }

  /** This can be made public if quiesceServer becomes too heavy-weight in some circumstance. */
  private static void waitForOpsToComplete()
  {
    try {
      WorkQueue<?> workQueue = DirectoryServer.getWorkQueue();
      final long NO_TIMEOUT = -1;
      workQueue.waitUntilIdle(NO_TIMEOUT);
    } catch (Exception e) {
      // Ignore it, maybe the server hasn't been started.
    }
  }

  /**
   * Binds to the given socket port on the local host.
   * @return the bounded Server socket.
   *
   * @throws IOException in case of underlying exception.
   */
  private static ServerSocket bindPort(int port)
          throws IOException
  {
	ServerSocket serverLdapSocket;
    serverLdapSocket = new ServerSocket();
    serverLdapSocket.setReuseAddress(true);
    serverLdapSocket.bind(new InetSocketAddress(port));
    return serverLdapSocket;
  }

  static int port = 65535;
  /**
   * Find and binds to a free server socket port on the local host. Avoid allocating ephemeral ports since these may
   * be used by client applications such as dsconfig. Instead scan through ports starting from a reasonably high number
   * which avoids most reserved services (see /etc/services) and continues up to the beginning of the ephemeral port
   * range. On most Linux OSes this is 32768, but may be higher.
   *
   * @return the bounded Server socket.
   *
   * @throws IOException in case of underlying exception.
   */
  public synchronized static ServerSocket bindFreePort() throws IOException
  {
	  for (; port > 1024;)
	  {
         ServerSocket res=null;
	     try
	     {
           res=bindPort(port--);
	       return res;
	     }
	     catch (BindException e){
             if (res!=null) {
                 try {
                     res.close();
                 } catch (IOException ex) {}
             }
             res=null;
         }
	  }
	  throw new BindException("Unable to bind to a free port");
  }

  /**
   * Find a free port on the local host.
   *
   * @throws IOException
   *           in case of underlying exception.
   * @return the free port number found
   */
  public static int findFreePort() throws IOException
  {
    return findFreePorts(1)[0];
  }

  /**
   * Find nb free ports on the local host.
   *
   * @param nb
   *          the number of free ports to find
   * @throws IOException
   *           in case of underlying exception.
   * @return an array with the free port numbers found
   */
  public static int[] findFreePorts(int nb) throws IOException
  {
    final ServerSocket[] sockets = new ServerSocket[nb];
    try
    {
      final int[] ports = new int[nb];
      for (int i = 0; i < nb; i++)
      {
        sockets[i] = bindFreePort();
        ports[i] = sockets[i].getLocalPort();
      }
      close(sockets);
      return ports;
    }
    finally
    {
      
    }
  }

  /**
   * Finds a free server socket port on the local host.
   *
   * @return The free port.
   */
  public static SocketAddress findFreeSocketAddress()
  {
    try (ServerSocket serverLdapSocket = bindFreePort())
    {
      return serverLdapSocket.getLocalSocketAddress();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * Undo all the setup done by #startFakeServer().
   *
   * @throws DirectoryException
   *            If the initial schema contains warning
   * @see #startFakeServer() Matching method that starts the fake server
   */
  public static void shutdownFakeServer() throws DirectoryException
  {
    DirectoryServer.getInstance().getServerContext().getSchemaHandler().updateSchema(schemaBeforeStartingFakeServer);
  }

  /** Returns the server context. */
  public static ServerContext getServerContext()
  {
    ServerContext serverContext = DirectoryServer.getInstance().getServerContext();
    if (serverContext == null)
    {
      throw new RuntimeException("Server context is null");
    }
    return serverContext;
  }

  /**
   * Shut down the server. This should only be called at the end of the test
   * suite and not by any unit tests.
   *
   * @param reason
   *          The reason for the shutdown.
   */
  static void shutdownServer(LocalizableMessage reason)
  {
    if (SERVER_STARTED)
    {
      InvocationCounterPlugin.resetShutdownCalled();
      DirectoryServer.shutDown("org.opends.server.TestCaseUtils", reason);
      assertTrue(InvocationCounterPlugin.shutdownCalled());
      SERVER_STARTED = false;
    }
  }

  /**
   * Initializes a memory-based backend that may be used to perform operations
   * while testing the server.  This will ensure that the memory backend is
   * created in the server if it does not yet exist, and that it is empty.  Note
   * that the base DN for the test backend will always be "o=test", and it must
   * not be changed.  It is acceptable for test cases using this backend to
   * hard-code their sample data to use this base DN, although they may still
   * reference the <CODE>TEST_ROOT_DN_STRING</CODE> constant if they wish.
   *
   * @param  createBaseEntry  Indicate whether to automatically create the base
   *                          entry and add it to the backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void initializeTestBackend(boolean createBaseEntry) throws Exception
  {
    initializeMemoryBackend(TEST_BACKEND_ID, TEST_ROOT_DN_STRING, createBaseEntry);
  }

  /**
   * Initializes a memory-based backend that may be used to perform operations
   * while testing the server.  This will ensure that the memory backend is
   * created in the server if it does not yet exist, and that it is empty.
   *
   * @param  backendID        the ID of the backend to create
   * @param  namingContext    the naming context to create in the backend
   * @param  createBaseEntry  Indicate whether to automatically create the base
   *                          entry and add it to the backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void initializeMemoryBackend(
      String backendID,
      String namingContext,
      boolean createBaseEntry
      ) throws Exception
  {
    startServer();

    DN baseDN = DN.valueOf(namingContext);

    // Retrieve backend. Warning: it is important to perform this each time,
    // because a test may have disabled then enabled the backend (i.e a test
    // performing an import task). As it is a memory backend, when the backend
    // is re-enabled, a new backend object is in fact created and old reference
    // to memory backend must be invalidated. So to prevent this problem, we
    // retrieve the memory backend reference each time before cleaning it.
    BackendConfigManager backendConfigManager = getServerContext().getBackendConfigManager();
    MemoryBackend memoryBackend = (MemoryBackend) backendConfigManager.getLocalBackendById(backendID);

    if (memoryBackend == null)
    {
      memoryBackend = new MemoryBackend();
      memoryBackend.setBackendID(backendID);
      memoryBackend.setBaseDNs(baseDN);
      memoryBackend.configureBackend(null, getServerContext());
      memoryBackend.openBackend();
      backendConfigManager.registerLocalBackend(memoryBackend);
    }

    memoryBackend.clearMemoryBackend();

    if (createBaseEntry)
    {
      Entry e = createEntry(baseDN);
      memoryBackend.addEntry(e, null);
    }
  }

  /** Clears a memory-based backend. */
  public static void clearMemoryBackend(String backendID) throws Exception
  {
    MemoryBackend memoryBackend =
        (MemoryBackend) getServerContext().getBackendConfigManager().getLocalBackendById(backendID);
    // FIXME JNR I suspect we could call finalizeBackend() here (but also in other
    // places in this class), because finalizeBackend() calls clearMemoryBackend().
    if (memoryBackend != null)
    {
      memoryBackend.clearMemoryBackend();
    }
  }

  /**
   * Clears all the entries from the backend determined by the backend id passed into the method.
   *
   * @throws Exception If an unexpected problem occurs.
   */
  public static void clearBackend(String backendId) throws Exception
  {
    clearBackend(backendId, null);
  }

  /**
   * Clears all the entries from the backend determined by the backend id passed into the method.
   *
   * @param backendId  The backend id to clear
   * @param baseDN   If not null, the suffix of the backend to create
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void clearBackend(String backendId, String baseDN) throws Exception
  {
    LocalBackend<?> b = getServerContext().getBackendConfigManager().getLocalBackendById(backendId);
    if (clearBackend(b) && baseDN != null)
    {
      Entry e = createEntry(DN.valueOf(baseDN));
      b.addEntry(e, mock(AddOperation.class));
    }
  }

  private static boolean clearBackend(LocalBackend<?> b)
  {
    if (b instanceof BackendImpl)
    {
      final BackendImpl<?> backend = (BackendImpl<?>) b;
      final RootContainer rootContainer = backend.getRootContainer();
      if (rootContainer != null)
      {
        for (EntryContainer ec : rootContainer.getEntryContainers())
        {
          ec.clear();
          // assertEquals(ec.getHighestEntryID().longValue(), 0L);
        }
        rootContainer.resetNextEntryID();
        return true;
      }
    }
    return false;
  }

  /**
   * Create a temporary directory with the specified prefix.
   *
   * @param prefix
   *          The directory prefix.
   * @return The temporary directory.
   * @throws IOException
   *           If the temporary directory could not be created.
   */
  public static File createTemporaryDirectory(String prefix)
      throws IOException {
    File tmpDir = File.createTempFile(prefix, null);
    if (!tmpDir.delete()) {
      throw new IOException("Unable to delete temporary file: " + tmpDir);
    }
    if (!tmpDir.mkdir()) {
      throw new IOException("Unable to create temporary directory: " + tmpDir);
    }
    return tmpDir;
  }

  /**
   * Copy a directory and its contents.
   *
   * @param src
   *          The name of the directory to copy.
   * @param dst
   *          The name of the destination directory.
   * @throws IOException
   *           If the directory could not be copied.
   */
  public static void copyDirectory(File src, File dst) throws IOException {
    if (src.isDirectory()) {
      // Create the destination directory if it does not exist.
      if (!dst.exists()) {
        dst.mkdirs();
      }

      // Recursively copy sub-directories and files.
      for (String child : src.list()) {
        copyDirectory(new File(src, child), new File(dst, child));
      }
    } else {
      copyFile(src, dst);
    }
  }

  /**
   * Delete a directory and its contents.
   *
   * @param dir
   *          The name of the directory to delete.
   * @throws IOException
   *           If the directory could not be deleted.
   */
  public static void deleteDirectory(File dir) throws IOException {
    if (dir == null || !dir.exists())
    {
      return;
    }

    if (dir.isDirectory()) {
      // Recursively delete sub-directories and files.
      for (String child : dir.list()) {
        deleteDirectory(new File(dir, child));
      }
    }

    dir.delete();
  }

  private static void copyFileFromTo(String filename, File fromDir, File toDir) throws IOException
  {
    copyFile(new File(fromDir, filename), new File(toDir, filename));
  }

  /**
   * Copy a file.
   *
   * @param src
   *          The name of the source file.
   * @param dst
   *          The name of the destination file.
   * @throws IOException
   *           If the file could not be copied.
   */
  public static void copyFile(File src, File dst) throws IOException {
    copyOrAppend(src, dst, false);
  }

  public static void appendFile(File src, File dst) throws IOException
  {
    copyOrAppend(src, dst, true);
  }

  private static void copyOrAppend(File src, File dst, boolean append)
      throws IOException
  {
    try (InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst, append))
    {
      // Transfer bytes from in to out
      byte[] buf = new byte[8192];
      int len;
      while ((len = in.read(buf)) > 0)
      {
        out.write(buf, 0, len);
      }
    }
  }

  /**
   * Get the LDAP port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static int getServerLdapPort()
  {
    return ports.serverLdapPort;
  }

  /**
   * Get the Admin port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static int getServerAdminPort()
  {
    return ports.serverAdminPort;
  }

  /**
   * Get the JMX port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static int getServerJmxPort()
  {
    return ports.serverJmxPort;
  }

  /**
   * Get the LDAPS port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static int getServerLdapsPort()
  {
    return ports.serverLdapsPort;
  }

  /**
   * Get the number of times the server has done a restart during the unit tests.
   *
   * @return the number of server restarts.
   */
  public static int getNumServerRestarts()
  {
    return serverRestarts;
  }

  /**
   * Method for getting a file from the test resources directory.
   *
   * @return The directory as a File
   */
  public static File getTestResource(String filename)
  {
    File testResourceDir = new File(paths.testSrcRoot, "resource");
    return new File(testResourceDir, filename);
  }

  public static File getUnitTestRootPath()
  {
    return paths.unitRoot;
  }

  /** Get the complete path to the OpenDJ archive. */
  public static File getOpenDJArchivePath()
  {
    String qualifier = DynamicConstants.VERSION_QUALIFIER;
    String openDJArchiveName =
        DynamicConstants.SHORT_NAME.toLowerCase()
        + "-"
        + DynamicConstants.VERSION_NUMBER_STRING
        + (qualifier != null && !qualifier.isEmpty() ? "-" + qualifier : "");
    return getBuildRoot().toPath().resolve("target/package").resolve(openDJArchiveName + ".zip").toFile();
  }

  /** Prevent instantiation. */
  private TestCaseUtils() {
    // No implementation.
  }

  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////
  // Various methods for converting LDIF Strings to Entries
  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a modifiable List of entries parsed from the provided LDIF.
   * It's best to call this after the server has been initialized so
   * that schema checking happens.
   * <p>
   * Also take a look at the makeLdif method below since this makes
   * expressing LDIF a little bit cleaner.
   *
   * @param ldif of the entries to parse.
   * @return a List of EntryS parsed from the ldif string.
   * @see #makeLdif
   */
  public static List<Entry> entriesFromLdifString(String ldif) throws Exception {
    LDIFImportConfig ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
    ldifImportConfig.setValidateSchema(false);

    try (LDIFReader reader = new LDIFReader(ldifImportConfig))
    {
      List<Entry> entries = new ArrayList<>();
      Entry entry;
      while ((entry = reader.readEntry()) != null)
      {
        entries.add(entry);
      }
      return entries;
    }
  }

  /**
   * This is used as a convenience when and LDIF string only includes a single
   * entry. It's best to call this after the server has been initialized so
   * that schema checking happens.
   * <p>
   * Also take a look at the makeLdif method below since this makes
   * expressing LDIF a little bit cleaner.
   *
   * @return the first Entry parsed from the ldif String
   * @see #makeLdif
   */
  public static Entry entryFromLdifString(String ldif) throws Exception {
    return entriesFromLdifString(ldif).get(0);
  }

  /**
   * This method provides the minor convenience of not having to specify the
   * newline character at the end of every line of LDIF in test code.
   * This is an admittedly small advantage, but it does make things a little
   * easier and less error prone.  For example, this
   *
     <pre>
       private static final String JOHN_SMITH_LDIF = TestCaseUtils.makeLdif(
          "dn: cn=John Smith,dc=example,dc=com",
          "objectclass: inetorgperson",
          "cn: John Smith",
          "sn: Smith",
          "givenname: John");

     </pre>

   is a <bold>little</bold> easier to work with than

     <pre>
       private static final String JOHN_SMITH_LDIF =
          "dn: cn=John Smith,dc=example,dc=com\n" +
          "objectclass: inetorgperson\n" +
          "cn: John Smith\n" +
          "sn: Smith\n" +
          "givenname: John\n";

     </pre>
   *
   * @return the concatenation of each line followed by a newline character
   */
  public static String makeLdif(String... lines) {
    StringBuilder buffer = new StringBuilder();
    for (String line : lines) {
      buffer.append(line).append(EOL);
    }
    // Append an extra line so we can append LDIF Strings.
    buffer.append(EOL);
    return buffer.toString();
  }

  /**
   * This is a convenience method that constructs an Entry from the specified
   * lines of LDIF.  Here's a sample usage
   *
   <pre>
   Entry john = TestCaseUtils.makeEntry(
      "dn: cn=John Smith,dc=example,dc=com",
      "objectclass: inetorgperson",
      "cn: John Smith",
      "sn: Smith",
      "givenname: John");
   </pre>
   * @see #makeLdif
   */
  public static Entry makeEntry(String... lines) throws Exception {
     return entryFromLdifString(makeLdif(lines));
  }

  /**
   * This is a convenience method that constructs an List of EntryS from the
   * specified lines of LDIF.  Here's a sample usage
   *
   <pre>
   List<Entry> smiths = TestCaseUtils.makeEntries(
      "dn: cn=John Smith,dc=example,dc=com",
      "objectclass: inetorgperson",
      "cn: John Smith",
      "sn: Smith",
      "givenname: John",
      "",
      "dn: cn=Jane Smith,dc=example,dc=com",
      "objectclass: inetorgperson",
      "cn: Jane Smith",
      "sn: Smith",
      "givenname: Jane");
   </pre>
   * @see #makeLdif
   */
  public static List<Entry> makeEntries(String... lines) throws Exception {
     return entriesFromLdifString(makeLdif(lines));
  }

  /**
   * Adds the provided entry to the Directory Server using an internal
   * operation.
   *
   * @param  entry  The entry to be added.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void addEntry(Entry entry) throws Exception
  {
    AddOperation addOperation = getRootConnection().processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,entry.toString());
  }

  /**
   * Deletes the provided entry from the Directory Server using an
   * internal operation.
   *
   * @param  entry  The entry to be deleted.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void deleteEntry(Entry entry) throws Exception
  {
    deleteEntry(entry.getName());
  }

  /**
   * Deletes the provided entry from the Directory Server using an
   * internal operation.
   *
   * @param  dn  The dn of entry to be deleted
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void deleteEntry(DN dn) throws Exception
  {
    DeleteOperation deleteOperation = getRootConnection().processDelete(dn);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }

  public static boolean canBind(String dn, String pw) throws Exception
  {
    // Check that the user can bind.
    try (Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort()))
    {
      TestCaseUtils.configureSocket(s);
      ASN1Reader r = ASN1.getReader(s.getInputStream());
      ASN1Writer w = ASN1.getWriter(s.getOutputStream());

      BindRequestProtocolOp bindRequest =
        new BindRequestProtocolOp(
                 ByteString.valueOfUtf8(dn),
                 3,
                 ByteString.valueOfUtf8(pw));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      message.write(w);

      message = LDAPReader.readMessage(r);
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      return bindResponse.getResultCode() == 0;
    } catch (Exception t) {
      t.printStackTrace();
      return false;
    }
  }

  /**
   * Configures a socket for use in unit tests. This should only be used if the
   * socket is not expected to timeout.
   *
   * @param s
   *          The socket.
   * @throws Exception
   *           If an unexpected exception occurred while configuring the socket.
   */
  public static void configureSocket(Socket s) throws Exception
  {
	  s.setReuseAddress(true);
	  s.setSoTimeout(60 * 1000);
  }

  /**
   * Adds the provided entry to the Directory Server using an internal
   * operation.
   *
   * @param  lines  The lines that make up the entry to be added.
   * @return the added entry
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static Entry addEntry(String... lines) throws Exception
  {
    final Entry entry = makeEntry(lines);
    AddOperation addOperation = getRootConnection().processAdd(entry);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
        addOperation.getErrorMessage().toString());
    final Entry e = DirectoryServer.getEntry(entry.getName());
    assertNotNull(e);
    return e;
  }

  /**
   * Adds the provided entry to the Directory Server using an internal
   * operation.
   *
   * @param  lines  The lines that make up the entry to be added.
   *
   * @return result code for this operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static ResultCode addEntryOperation(String... lines) throws Exception
  {
    Entry entry = makeEntry(lines);
    AddOperation addOperation = getRootConnection().processAdd(entry);
    return addOperation.getResultCode();
  }

  /**
   * Adds the provided set of entries to the Directory Server using internal
   * operations.
   *
   * @param  entries  The entries to be added.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void addEntries(List<Entry> entries) throws Exception
  {
    for (Entry entry : entries)
    {
      addEntry(entry);
    }
  }

  /**
   * Adds the provided set of entries to the Directory Server using internal
   * operations.
   *
   * @param  lines  The lines defining the entries to add.  If there are
   *                multiple entries, then they should be separated by blank
   *                lines.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void addEntries(String... lines) throws Exception
  {
    for (Entry entry : makeEntries(lines))
    {
      addEntry(entry);
    }
  }

  /**
   * Applies a set of modifications to the server as described in the provided
   * set of lines (using LDIF change form).  The changes will be applied over
   * LDAP using the ldapmodify tool using the "cn=Directory Manager" account.
   *
   * @param  lines  The set of lines including the changes to make to the
   *                server.
   *
   * @return  The result code from applying the set of modifications.  if it is
   *          nonzero, then there was a failure of some kind.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static int applyModifications(boolean useAdminPort, String... lines)
         throws Exception
  {
    if (! SERVER_STARTED)
    {
      startServer();
    }

    String path = createTempFile(lines);
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(ports.serverLdapPort),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };
    String[] adminArgs =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(ports.serverAdminPort),
      "-Z", "-X",
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    if (useAdminPort) {
      return LDAPModify.run(nullPrintStream(), nullPrintStream(), adminArgs);
    }
    return LDAPModify.run(nullPrintStream(), nullPrintStream(), args);
  }

  /**
   * Creates a temporary text file with the specified contents.  It will be
   * marked for automatic deletion when the JVM exits.
   *
   * @return  The absolute path to the file that was created.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static String createTempFile(String... lines) throws Exception
  {
    File f = File.createTempFile("LDAPModifyTestCase", ".txt");
    f.deleteOnExit();

    final String EOL = System.getProperty("line.separator");
    try (FileWriter w = new FileWriter(f))
    {
      for (String s : lines)
      {
        w.write(s + EOL);
      }
    }

    return f.getAbsolutePath();
  }

  /** Convenience method so we don't have to catch InterruptedException everywhere. */
  public static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      // Ignore it.
    }
  }

  /** Return a Map constructed via alternating key and value pairs. */
  public static Map<String, String> makeMap(String... keyValuePairs)
  {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      map.put(keyValuePairs[i], keyValuePairs[i+1]);
    }
    return map;
  }

  // ---------------------------------------------------------------------------
  // ---------------------------------------------------------------------------
  // ---------------------------------------------------------------------------

  /** The set of loggers for which the console logger has been disabled. */
  private static final Map<Logger, Handler> disabledLogHandlers = new HashMap<>();

  /** The original System.err print stream.  Use this if you absolutely
   *  must write something to System.err. */
  public static final PrintStream originalSystemErr = System.err;

  /** The original System.out print stream.  Use this if you absolutely
   *  must write something to System.out. */
  public static final PrintStream originalSystemOut = System.out;

  /** System.err is redirected to here so that we can only print it out if a test fails. */
  private static final ByteArrayOutputStream redirectedSystemErr = new ByteArrayOutputStream();

  /** System.out is redirected to here so that we can only print it out if a test fails. */
  private static final ByteArrayOutputStream redirectedSystemOut = new ByteArrayOutputStream();

  public static synchronized void suppressOutput() {
    String suppressStr = System.getProperty("org.opends.test.suppressOutput");
    if ("true".equalsIgnoreCase(suppressStr))
    {
      System.setOut(new PrintStream(redirectedSystemOut));
      System.setErr(new PrintStream(redirectedSystemErr));

      LogManager logManager = LogManager.getLogManager();
      Enumeration<String> loggerNames = logManager.getLoggerNames();
      while (loggerNames.hasMoreElements())
      {
        String loggerName = loggerNames.nextElement();
        Logger logger = logManager.getLogger(loggerName);
        if(logger == null)
        {
          break;
        }
        for (Handler h : logger.getHandlers())
        {
          if (h instanceof ConsoleHandler)
          {
            disabledLogHandlers.put(logger, h);
            logger.removeHandler(h);
            break;
          }
        }
      }
    }
  }

  /**
   * @return everything written to System.out since the last time
   * clearSystemOutContents was called.
   */
  public static synchronized String getSystemOutContents() {
    return redirectedSystemOut.toString();
  }

  /**
   * @return everything written to System.err since the last time
   * clearSystemErrContents was called.
   */
  public static synchronized String getSystemErrContents() {
    return redirectedSystemErr.toString();
  }

  /**
   * Clear everything written to System.out since the last time
   * clearSystemOutContents was called.
   */
  public static synchronized void clearSystemOutContents() {
    redirectedSystemOut.reset();
  }

  /**
   * Clear everything written to System.err since the last time
   * clearSystemErrContents was called.
   */
  public static synchronized void clearSystemErrContents() {
    redirectedSystemErr.reset();
  }

  /** Clear everything written to the Access, Error, or Debug loggers. */
  public static synchronized void clearLoggersContents() {
    ACCESS_TEXT_WRITER.clear();
    ERROR_TEXT_WRITER.clear();
    DEBUG_TEXT_WRITER.clear();
    clearSystemOutContents();
    clearSystemErrContents();
  }

  /**
   * Append the contents of the Access Log, Error Log, Debug Loggers,
   * System.out, System.err to the specified buffer.
   */
  public static void appendLogsContents(StringBuilder logsContents)
  {
    appendMessages(logsContents, TestCaseUtils.ERROR_TEXT_WRITER, "Error Log Messages:");
    appendMessages(logsContents, TestCaseUtils.DEBUG_TEXT_WRITER, "Debug Log Messages:");
    appendMessages(logsContents, TestCaseUtils.ACCESS_TEXT_WRITER, "Access Log Messages:");
    
    appendStreamContent(logsContents, TestCaseUtils.getSystemOutContents(), "System.out");
    appendStreamContent(logsContents, TestCaseUtils.getSystemErrContents(), "System.err");
    
    if (new File(paths.testInstanceRoot, "logs").listFiles()!=null) {
	    for (final File logFile : Arrays.asList(new File(paths.testInstanceRoot, "logs").listFiles())) {
	    	 try {
				appendStreamContent(logsContents, readFile(logFile.getPath()), logFile.getPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}   
    }
  }

  private static void appendStreamContent(StringBuilder out, String content, String name)
  {
    if (content.length() > 0)
    {
      out.append(EOL).append(name).append(" contents:").append(EOL).append(content);
    }
  }

  private static void appendMessages(StringBuilder out,
      TestTextWriter textWriter, String loggerType)
  {
    List<String> messages = textWriter.getMessages();
    if (!messages.isEmpty())
    {
      out.append(EOL);
      out.append(loggerType);
      out.append(EOL);
      for (String message : messages)
      {
        out.append(message);
        out.append(EOL);
      }
    }
  }

  public static synchronized void unsupressOutput() {
	  String suppressStr = System.getProperty("org.opends.test.suppressOutput");
	  if ("true".equalsIgnoreCase(suppressStr))
	  {
		  System.setOut(originalSystemOut);
		  System.setErr(originalSystemErr);
		
		    for (Map.Entry<Logger, Handler> entry : disabledLogHandlers.entrySet())
		    {
		      Logger l = entry.getKey();
		      Handler h = entry.getValue();
		      l.addHandler(h);
		    }
		    disabledLogHandlers.clear();
	    }
  }

  /** Read the contents of a file and return it as a String. */
  public static String readFile(String name) throws IOException {
    return readFile(new File(name));
  }

  /** Read the contents of a file and return it as a String. */
  public static String readFile(File file) throws IOException {
    return new String(readFileBytes(file));
  }

  /**
   * Returns the contents of file as a List of the lines as defined by
   * java.io.BufferedReader#readLine() (i.e. the line terminator is not
   * included).  An ArrayList is explicitly returned, so that callers know that
   * random access is not expensive.
   */
  public static ArrayList<String> readFileToLines(File file) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

    ArrayList<String> lines = new ArrayList<>();
    String line;
    while ((line = reader.readLine()) != null)   {
      lines.add(line);
    }

    return lines;
  }

  /** Read the contents of a file and return it as a String. */
  private static byte[] readFileBytes(File file) throws IOException {
    FileInputStream fis = new FileInputStream(file);
    return readInputStreamBytes(fis, true);
  }

  /**
   * @param close - if true, close when finished reading.
   * @return input stream content.
   */
  private static byte[] readInputStreamBytes(InputStream is, boolean close) throws IOException {
    byte[] bytes = null;
    if (is != null) {
      ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);
      try {
        byte[] buf = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buf)) != -1) {
          bout.write(buf, 0, bytesRead);
        } // end of while ((read(buf) != -1)
        bytes = bout.toByteArray();
      }
      finally {
        if (close)
        {
          close(is);
        }
      }
    }
    return bytes;
  }

  /** Store the contents of a String in a file. */
  public static void writeFile(File file, String contents) throws IOException {
    writeFile(file.getAbsolutePath(), contents);
  }

  /** Store the contents of a String in a file. */
  public static void writeFile(String name, String contents) throws IOException {
    writeFile(name, contents.getBytes());
  }

  /** Store the contents of a String in a file. */
  public static void writeFile(String path, byte[] contents) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(path))
    {
      fos.write(contents);
    }
  }

  /**
   * Invokes the dsconfig tool with the provided set of arguments.  Note that
   * the address, port, bind DN (cn=Directory Manager), and password will always
   * be provided, so they should not be included in the argument list.  The
   * given arguments should include only the subcommand and its associated
   * options, along with any other global options that may not be included by
   * default.
   * <BR><BR>
   * An assertion will be used to ensure that the dsconfig invocation is
   * successful.  If running dsconfig returns a non-zero result, then an
   * assertion error will be thrown.
   *
   * @param  args  The set of arguments that should be provided when invoking
   *               the dsconfig tool
   */
  public static void dsconfig(String... args)
  {
    String[] fullArgs = new String[args.length + 11];
    fullArgs[0] = "-h";
    fullArgs[1] = hostname;
    fullArgs[2] = "-p";
    fullArgs[3] = String.valueOf(ports.serverAdminPort);
    fullArgs[4] = "-D";
    fullArgs[5] = "cn=Directory Manager";
    fullArgs[6] = "-w";
    fullArgs[7] = "password";
    fullArgs[8] = "-n";
    fullArgs[9] = "--noPropertiesFile";
    fullArgs[10] = "-X";

    System.arraycopy(args, 0, fullArgs, 11, args.length);

    assertEquals(DSConfig.main(fullArgs, System.out, System.err), 0);
  }

  /**
   * Return a String representation of all of the current threads.
   * @return a dump of all Threads on the server
   */
  public static String threadStacksToString()
  {
    Map<Thread,StackTraceElement[]> threadStacks = Thread.getAllStackTraces();

    // Re-arrange all of the elements by thread ID so that there is some logical order.
    Map<Long, Map.Entry<Thread, StackTraceElement[]>> orderedStacks = new TreeMap<>();
    for (Map.Entry<Thread,StackTraceElement[]> e : threadStacks.entrySet())
    {
      orderedStacks.put(e.getKey().getId(), e);
    }

    final StringBuilder buffer = new StringBuilder();
    for (Map.Entry<Thread,StackTraceElement[]> e : orderedStacks.values())
    {
      Thread t                          = e.getKey();
      StackTraceElement[] stackElements = e.getValue();

      long id = t.getId();

      buffer.append("id=");
      buffer.append(id);
      buffer.append(" ---------- ");
      buffer.append(t.getName());
      buffer.append(" ----------");
      buffer.append(EOL);

      if (stackElements != null)
      {
        for (StackTraceElement stackElement : stackElements)
        {
          buffer.append("   ").append(stackElement.getClassName());
          buffer.append(".");
          buffer.append(stackElement.getMethodName());
          buffer.append("(");
          buffer.append(stackElement.getFileName());
          buffer.append(":");
          if (stackElement.isNativeMethod())
          {
            buffer.append("native");
          }
          else
          {
            buffer.append(stackElement.getLineNumber());
          }
          buffer.append(")").append(EOL);
        }
      }
      buffer.append(EOL);
    }

    return buffer.toString();
  }

  public static void enableBackend(String backendID)
  {
    setBackendEnabled(backendID, true);
  }

  public static void disableBackend(String backendID)
  {
    setBackendEnabled(backendID, false);
  }

  private static void setBackendEnabled(String backendID, boolean enabled)
  {
    dsconfig("set-backend-prop", "--backend-name", backendID,
             "--set", "enabled:" + enabled);
  }

  public static HashSet<PluginType> getPluginTypes(Entry e)
  {
    HashSet<PluginType> pluginTypes = new HashSet<>();
    for (Attribute a : e.getAllAttributes("ds-cfg-plugin-type"))
    {
      for (ByteString v : a)
      {
        pluginTypes.add(PluginType.forName(v.toString().toLowerCase()));
      }
    }
    return pluginTypes;
  }

  /** Saves a thread dump in a file with the provided id used in file prefix. */
  public static void generateThreadDump(String id)
  {
    String date = new SimpleDateFormat("yyyyMMdd_hhmmss").format(new Date().getTime());
    try (BufferedWriter writer = new BufferedWriter(new FileWriter("/tmp/thread_dump_" + id + "_" + date)))
    {
      writer.write(generateThreadDump());
    }
    catch (Exception e)
    {
      // do nothing
    }
  }

   /** Generates a thread dump programmatically. */
  public static String generateThreadDump() {
    final StringBuilder dump = new StringBuilder();
    final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
    for (ThreadInfo threadInfo : threadInfos) {
      dump.append("\"" + threadInfo.getThreadName() + "\"" +
              (threadInfo.isDaemon() ? " daemon" : "") +
              " prio=" + threadInfo.getPriority() +
              " Id=" + threadInfo.getThreadId() + " " +
              threadInfo.getThreadState());
      if (threadInfo.getLockName() != null) {
        dump.append(" on " + threadInfo.getLockName());
      }
      if (threadInfo.getLockOwnerName() != null) {
        dump.append(" owned by \"" + threadInfo.getLockOwnerName() +
                "\" Id=" + threadInfo.getLockOwnerId());
      }
      if (threadInfo.isSuspended()) {
        dump.append(" (suspended)");
      }
      if (threadInfo.isInNative()) {
        dump.append(" (in native)");
      }
      dump.append('\n');
      StackTraceElement[] stackTrace = threadInfo.getStackTrace();
      int i = 0;
      for (; i < stackTrace.length; i++) {
        StackTraceElement ste = stackTrace[i];
        dump.append("\tat " + ste.toString());
        dump.append('\n');
        if (i == 0 && threadInfo.getLockInfo() != null) {
          Thread.State ts = threadInfo.getThreadState();
          switch (ts) {
            case BLOCKED:
              dump.append("\t-  blocked on " + threadInfo.getLockInfo());
              dump.append('\n');
              break;
            case WAITING:
            case TIMED_WAITING:
              dump.append("\t-  waiting on " + threadInfo.getLockInfo());
              dump.append('\n');
              break;
            default:
          }
        }

        for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
          if (mi.getLockedStackDepth() == i) {
            dump.append("\t-  locked " + mi);
            dump.append('\n');
          }
        }
      }
      if (i < stackTrace.length) {
        dump.append("\t...");
        dump.append('\n');
      }

      LockInfo[] locks = threadInfo.getLockedSynchronizers();
      if (locks.length > 0) {
        dump.append("\n\tNumber of locked synchronizers = " + locks.length);
        dump.append('\n');
        for (LockInfo li : locks) {
          dump.append("\t- " + li);
          dump.append('\n');
        }
      }
      dump.append('\n');
    }
    return dump.toString();
  }

//  /** FIXME Replace with {@link Assert#assertNotEquals(Object, Object)} once we upgrade to testng >= 6.1. */
//  public static void assertNotEquals(Object actual1, Object actual2)
//  {
//    assertNotEquals(actual1, actual2, null);
//  }

//  /** FIXME Replace with {@link Assert#assertNotEquals(Object, Object, String)} once we upgrade to testng >= 6.1. */
//  public static void assertNotEquals(Object actual1, Object actual2, String message)
//  {
//    try
//    {
//      Assert.assertEquals(actual1, actual2);
//      Assert.fail(message);
//    }
//    catch (AssertionError e)
//    {
//      // this is good: they are not equals
//      return;
//    }
//  }

  public static int runLdapSearchTrustCertificateForSession(final String[] args)
  {
    return runLdapSearchTrustCertificateForSession(nullPrintStream(), System.err, args);
  }

  public static int runLdapSearchTrustCertificateForSession(final PrintStream out,
                                                            final PrintStream err,
                                                            final String[] args)
  {
    final InputStream stdin = System.in;
    try
    {
      // Since hostnames are different between the client.truststore (CN=OpenDJ Test Certificate, O=OpenDJ.org) and the
      // one given in parameter (127.0.0.1), ldapsearch tool prompt user to know what to do (either untrust the server
      // certificate, trust it for the session only or trust it permanently).
      // Default option is session trust, we just hit enter in stdin to have a non blocking unit test.
      System.setIn(new ByteArrayInputStream(System.lineSeparator().getBytes()));
      return LDAPSearch.run(nullPrintStream(), System.err, args);
    }
    finally
    {
      System.setIn(stdin);
    }
  }

  	static String testName=null;
	public static void setTestName(String name) {
		testName=name;
		paths=new TestPaths();
		//originalSystemErr.println(paths.unitRoot);
	}
}
