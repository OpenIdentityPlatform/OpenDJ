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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPConnection;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.api.Backend;
import org.opends.server.api.WorkQueue;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.jeb.DatabaseContainer;
import org.opends.server.backends.jeb.EntryContainer;
import org.opends.server.backends.jeb.Index;
import org.opends.server.backends.jeb.RootContainer;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.TextAccessLogPublisher;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.AccessLogger;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.TextDebugLogPublisher;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.dsconfig.DSConfig;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OperatingSystem;
import org.opends.server.types.ResultCode;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.LDIFReader;

/**
 * This class defines some utility functions which can be used by test
 * cases.
 */
public final class TestCaseUtils {
  /**
   * The name of the system property that specifies the server build root.
   */
  public static final String PROPERTY_BUILD_ROOT =
       "org.opends.server.BuildRoot";

  /**
   * The name of the system property that specifies the ldap port.
   * Set this prtoperty when running the server if you want to use a given
   * port number, otherwise a port is choosed randomly at test startup time.
   */
  public static final String PROPERTY_LDAP_PORT =
       "org.opends.server.LdapPort";

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


  /**
   * The test text writer for the Debug Logger
   */
  public static TestTextWriter DEBUG_TEXT_WRITER =
      new TestTextWriter();

  /**
   * The test text writer for the Debug Logger
   */
  public static TestTextWriter ERROR_TEXT_WRITER =
      new TestTextWriter();

  /**
   * The test text writer for the Debug Logger
   */
  public static TestTextWriter ACCESS_TEXT_WRITER =
      new TestTextWriter();

  /**
   * Indicates whether the server has already been started.  The value of this
   * constant must not be altered by anything outside the
   * <CODE>startServer</CODE> method.
   */
  public static boolean SERVER_STARTED = false;

  /**
   * The memory-based backend configured for use in the server.
   */
  private static MemoryBackend memoryBackend = null;

  /**
   * The LDAP port the server is bound to on start.
   */
  private static int serverLdapPort;

  /**
   * The JMX port the server is bound to on start.
   */
  private static int serverJmxPort;

  /**
   * The LDAPS port the server is bound to on start.
   */
  private static int serverLdapsPort;

  /**
   * Incremented by one each time the server has restarted.
   */
  private static int serverRestarts = 0;

  /**
   * Starts the Directory Server so that it will be available for use while
   * running the unit tests.  This will only actually start the server once, so
   * subsequent attempts to start it will be ignored because it will already be
   * available.
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
  public static void startServer()
         throws IOException, InitializationException, ConfigException,
                DirectoryException
  {
    try {
      if (SERVER_STARTED)
      {
        return;
      }

      InvocationCounterPlugin.resetStartupCalled();

      // Get the build root and use it to create a test package directory.
      String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
      File   buildDir  = new File(buildRoot, "build");
      File   unitRoot  = new File(buildDir, "unit-tests");
      File   testRoot  = new File(unitRoot, "package");
      File   testSrcRoot = new File(buildRoot + File.separator + "tests" +
                                    File.separator + "unit-tests-testng");

      if (testRoot.exists())
      {
        deleteDirectory(testRoot);
      }
      testRoot.mkdirs();
      //db_verify is second jeb backend used by the jeb verify test cases
      //db_rebuild is the third jeb backend used by the jeb rebuild test cases
      //db_unindexed is the forth backend used by the unindexed search privilege
      //test cases
      String[] subDirectories = { "bak", "bin", "changelogDb", "classes",
                                  "config", "db", "import-tmp", "db_verify",
                                  "ldif", "lib", "locks", "logs", "db_rebuild",
                                  "db_unindexed", "db_index_test",
                                  "db_import_test"};
      for (String s : subDirectories)
      {
        new File(testRoot, s).mkdir();
      }

      // Copy the configuration, schema, and MakeLDIF resources into the
      // appropriate place under the test package.
      File serverClassesDir = new File(buildDir, "classes");
      File unitClassesDir   = new File(unitRoot, "classes");
      File libDir           = new File(buildRoot, "lib");
      File resourceDir      = new File(buildRoot, "resource");
      File testResourceDir  = new File(testSrcRoot, "resource");
      File testConfigDir    = new File(testRoot, "config");
      File testClassesDir   = new File(testRoot, "classes");
      File testLibDir       = new File(testRoot, "lib");
      File testBinDir       = new File(testRoot, "bin");
      
      // Snmp resource
      File   snmpResourceDir = new File(buildRoot + File.separator + "src" +
                                    File.separator + "snmp" + File.separator +
                                    "resource");
      
      File testSnmpResourceDir = new File (testConfigDir + File.separator +
                                    "snmp");

      if (Boolean.getBoolean(PROPERTY_COPY_CLASSES_TO_TEST_PKG)) {
        copyDirectory(serverClassesDir, testClassesDir);
        copyDirectory(unitClassesDir, testClassesDir);
      }

      copyDirectory(libDir, testLibDir);
      copyDirectory(new File(resourceDir, "bin"), testBinDir);
      copyDirectory(new File(resourceDir, "config"), testConfigDir);
      copyDirectory(new File(resourceDir, "schema"),
                    new File(testConfigDir, "schema"));
      copyDirectory(new File(resourceDir, "MakeLDIF"),
                    new File(testConfigDir, "MakeLDIF"));
      copyDirectory(new File(snmpResourceDir, "security"),
                    new File(testSnmpResourceDir, "security"));
      copyFile(new File(testResourceDir, "server.keystore"),
               new File(testConfigDir, "server.keystore"));
      copyFile(new File(testResourceDir, "server.truststore"),
               new File(testConfigDir, "server.truststore"));
      copyFile(new File(testResourceDir, "client.keystore"),
               new File(testConfigDir, "client.keystore"));
      copyFile(new File(testResourceDir, "client.truststore"),
               new File(testConfigDir, "client.truststore"));
      copyFile(new File(testResourceDir, "server-cert.p12"),
               new File(testConfigDir, "server-cert.p12"));
      copyFile(new File(testResourceDir, "client-cert.p12"),
               new File(testConfigDir, "client-cert.p12"));

      for (File f : testBinDir.listFiles())
      {
        try
        {
          FilePermission.setPermissions(f, FilePermission.decodeUNIXMode("755"));
        } catch (Exception e) {}
      }

      // Make the shell scripts in the bin directory executable, if possible.
      OperatingSystem os = DirectoryServer.getOperatingSystem();
      if ((os != null) && OperatingSystem.isUNIXBased(os) &&
          FilePermission.canSetPermissions())
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
        } catch (Exception e) {}
      }

      // Find some free ports for the listeners and write them to the
      // config-chamges.ldif file.
      ServerSocket serverLdapSocket  = null;
      ServerSocket serverJmxSocket   = null;
      ServerSocket serverLdapsSocket = null;

      String ldapPort = System.getProperty(PROPERTY_LDAP_PORT);
      if (ldapPort == null)
      {
        serverLdapSocket = bindFreePort();
        serverLdapPort = serverLdapSocket.getLocalPort();
      }
      else
      {
        serverLdapPort = Integer.valueOf(ldapPort);
        serverLdapSocket = bindPort(serverLdapPort);
      }

      serverJmxSocket = bindFreePort();
      serverJmxPort = serverJmxSocket.getLocalPort();

      serverLdapsSocket = bindFreePort();
      serverLdapsPort = serverLdapsSocket.getLocalPort();

      BufferedReader reader = new BufferedReader(new FileReader(
                                                 new File(testResourceDir,
                                                          "config-changes.ldif")
                                                ));
      FileOutputStream outFile = new FileOutputStream(
          new File(testConfigDir, "config-changes.ldif"));
      PrintStream writer = new PrintStream(outFile);

      String line = reader.readLine();

      while(line != null)
      {
        line = line.replaceAll("#ldapport#", String.valueOf(serverLdapPort));
        line = line.replaceAll("#jmxport#", String.valueOf(serverJmxPort));
        line = line.replaceAll("#ldapsport#", String.valueOf(serverLdapsPort));

        writer.println(line);
        line = reader.readLine();
      }

      writer.close();
      outFile.close();
      reader.close();

      serverLdapSocket.close();
      serverJmxSocket.close();
      serverLdapsSocket.close();

      // Create a configuration for the server.
      DirectoryEnvironmentConfig config = new DirectoryEnvironmentConfig();
      config.setServerRoot(testRoot);
      config.setForceDaemonThreads(true);
      config.setConfigClass(ConfigFileHandler.class);
      config.setConfigFile(new File(testConfigDir, "config.ldif"));

      AccessLogger.addAccessLogPublisher(
          TextAccessLogPublisher.getStartupTextAccessPublisher(
              ACCESS_TEXT_WRITER, false));

      ErrorLogger.addErrorLogPublisher(
         TextErrorLogPublisher.getStartupTextErrorPublisher(
              ERROR_TEXT_WRITER));

      DebugLogger.addDebugLogPublisher(
         TextDebugLogPublisher.getStartupTextDebugPublisher(
              DEBUG_TEXT_WRITER));

      EmbeddedUtils.startServer(config);

      assertTrue(InvocationCounterPlugin.startupCalled());

      // Save config.ldif for when we restart the server
      backupServerConfigLdif();

      SERVER_STARTED = true;

      initializeTestBackend(true);
    } catch (IOException e) {
      e.printStackTrace(originalSystemErr);
      throw e;
    } catch (NumberFormatException e) {
      e.printStackTrace(originalSystemErr);
      throw e;
    } catch (InitializationException e) {
      e.printStackTrace(originalSystemErr);
      throw e;
    } catch (ConfigException e) {
      e.printStackTrace(originalSystemErr);
      throw e;
    } catch (DirectoryException e) {
      e.printStackTrace(originalSystemErr);
      throw e;
    }
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
      long startMs = System.currentTimeMillis();

      clearLoggersContents();

      clearJEBackends();
      restoreServerConfigLdif();
      memoryBackend = null;  // We need it to be recreated and reregistered

      EmbeddedUtils.restartServer(null, null, DirectoryServer.getEnvironmentConfig());
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

  public static List<Long> restartTimesMs = new ArrayList<Long>();
  public static List<Long> getRestartTimesMs() {
    return Collections.unmodifiableList(restartTimesMs);
  }

  private static void outputLogContentsIfError(String prefix) {
    StringBuilder logContents = new StringBuilder(prefix + EOL);
    appendLogsContents(logContents);

    if (logContents.indexOf("ERROR") != -1) {
      originalSystemErr.println(logContents);
    }
  }

  private static void clearJEBackends() throws Exception
  {
    for (Backend backend: DirectoryServer.getBackends().values()) {
      if (backend instanceof BackendImpl) {
        TestCaseUtils.clearJEBackend(false, backend.getBackendID(), null);
      }
    }
  }

  public static void clearDataBackends() throws Exception
  {
    clearJEBackends();
    memoryBackend.clearMemoryBackend();
  }

  private static File getTestConfigDir()
  {
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   buildDir  = new File(buildRoot, "build");
    File   unitRoot  = new File(buildDir, "unit-tests");
    File   testRoot  = new File(unitRoot, "package");
    return new File(testRoot, "config");
  }

  public static File getBuildRoot()
  {
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
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

  /**
   * This can be made public if quiesceServer becomes too heavy-weight in
   * some circumstance.
   */
  private static void waitForOpsToComplete()
  {
    try {
      WorkQueue workQueue = DirectoryServer.getWorkQueue();
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
   * @throws SocketException in case of underlying exception.
   */
  private static ServerSocket bindPort(int port)
          throws IOException
  {
    ServerSocket serverLdapSocket;
    serverLdapSocket = new ServerSocket();
    serverLdapSocket.setReuseAddress(true);
    serverLdapSocket.bind(new InetSocketAddress("127.0.0.1", port));
    return serverLdapSocket;
  }

  /**
   * Find and binds to a free server socket port on the local host.
   * @return the bounded Server socket.
   *
   * @throws IOException in case of underlying exception.
   * @throws SocketException in case of underlying exception.
   */
  public static ServerSocket bindFreePort() throws IOException
  {
    ServerSocket serverLdapSocket;
    serverLdapSocket = new ServerSocket();
    serverLdapSocket.setReuseAddress(true);
    serverLdapSocket.bind(new InetSocketAddress("127.0.0.1", 0));
    return serverLdapSocket;
  }

  /**
   * Shut down the server, if it has been started.
   * @param reason The reason for the shutdown.
   */
  public static void shutdownServer(String reason)
  {
    shutdownServer(Message.raw(reason));
  }

  /**
   * Shut down the server, if it has been started.
   * @param reason The reason for the shutdown.
   */
  public static void shutdownServer(Message reason)
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
  public static void initializeTestBackend(boolean createBaseEntry)
         throws IOException, InitializationException, ConfigException,
                DirectoryException
  {
    startServer();

    DN baseDN = DN.decode(TEST_ROOT_DN_STRING);
    if (memoryBackend == null)
    {
      memoryBackend = new MemoryBackend();
      memoryBackend.setBackendID("test");
      memoryBackend.setBaseDNs(new DN[] { baseDN });
      memoryBackend.initializeBackend();
      DirectoryServer.registerBackend(memoryBackend);
    }

    memoryBackend.clearMemoryBackend();

    if (createBaseEntry)
    {
      Entry e = createEntry(baseDN);
      memoryBackend.addEntry(e, null);
    }
  }

  /**
   * Clears all the entries from the JE backend determined by the
   * be id passed into the method.

   * @param  createBaseEntry  Indicate whether to automatically create the base
   *                          entry and add it to the backend.
   *
   * @param beID  The be id to clear.
   *
   * @param dn   The suffix of the backend to create if the the createBaseEntry
   *             boolean is true.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void clearJEBackend(boolean createBaseEntry, String beID, String dn)
       throws Exception
  {
    BackendImpl backend = (BackendImpl)DirectoryServer.getBackend(beID);
    RootContainer rootContainer = backend.getRootContainer();
    if (rootContainer != null) {
      for (EntryContainer ec : rootContainer.getEntryContainers())
      {
        ec.clear();
        assertEquals(ec.getHighestEntryID().longValue(), 0L);
      }
      rootContainer.resetNextEntryID();

      if (createBaseEntry)
      {
        DN baseDN = DN.decode(dn);
        Entry e = createEntry(baseDN);
        backend = (BackendImpl)DirectoryServer.getBackend(beID);
        backend.addEntry(e, null);
      }
    }
  }

  /**
   * This was used to track down which test was trashing the indexes.
   * We left it here because it might be useful again.
   */
  public static void printUntrustedIndexes()
  {
    try {
      BackendImpl backend = (BackendImpl)DirectoryServer.getBackend("userRoot");
      if (backend == null) {
        return;
      }
      RootContainer rootContainer = backend.getRootContainer();
      for (EntryContainer ec : rootContainer.getEntryContainers())
      {
        List<DatabaseContainer> databases = new ArrayList<DatabaseContainer>();
        ec.listDatabases(databases);
        for (DatabaseContainer dbContainer: databases) {
          if (dbContainer instanceof Index) {
            Index index = (Index)dbContainer;
            if (!index.isTrusted()) {
              originalSystemErr.println("ERROR:  The index " + index.toString() + " is no longer trusted.");
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace(originalSystemErr);
    }
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
    File tempDirectory = File.createTempFile(prefix, null);

    if (!tempDirectory.delete()) {
      throw new IOException("Unable to delete temporary file: "
          + tempDirectory);
    }

    if (!tempDirectory.mkdir()) {
      throw new IOException("Unable to create temporary directory: "
          + tempDirectory);
    }

    return tempDirectory;
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
    if (dir.isDirectory()) {
      // Recursively delete sub-directories and files.
      for (String child : dir.list()) {
        deleteDirectory(new File(dir, child));
      }
    }

    dir.delete();
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
    InputStream in = new FileInputStream(src);
    OutputStream out = new FileOutputStream(dst);

    // Transfer bytes from in to out
    byte[] buf = new byte[8192];
    int len;
    while ((len = in.read(buf)) > 0) {
      out.write(buf, 0, len);
    }
    in.close();
    out.close();
  }

  /**
   * Get the LDAP port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static int getServerLdapPort()
  {
    return serverLdapPort;
  }

  /**
   * Get the JMX port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static int getServerJmxPort()
  {
    return serverJmxPort;
  }

  /**
   * Get the LDAPS port the test environment Directory Server instance is
   * running on.
   *
   * @return The port number.
   */
  public static int getServerLdapsPort()
  {
    return serverLdapsPort;
  }

  /**
   * Get the number of times the server has done an incore restart during
   * the unit tests.
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
    String buildRoot = System.getProperty(PROPERTY_BUILD_ROOT);
    File   testResourceDir = new File(buildRoot + File.separator + "tests" +
                                      File.separator + "unit-tests-testng" +
                                      File.separator + "resource");

    return new File(testResourceDir, filename);
  }

  /**
   * Prevent instantiation.
   */
  private TestCaseUtils() {
    // No implementation.
  }


  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////
  //
  // Various methods for converting LDIF Strings to Entries
  //
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
    LDIFReader reader = new LDIFReader(ldifImportConfig);

    List<Entry> entries = new ArrayList<Entry>();
    Entry entry;
    while ((entry = reader.readEntry()) != null) {
      entries.add(entry);
    }

    return entries;
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
   * This is a convience method that constructs an Entry from the specified
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
   * This is a convience method that constructs an List of EntryS from the
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
  public static void addEntry(Entry entry)
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation = conn.processAdd(entry.getDN(),
                                     entry.getObjectClasses(),
                                     entry.getUserAttributes(),
                                     entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  public static boolean canBind(String dn, String pw) throws Exception
  {
    // Check that the user can bind.
    Socket s = null;
    try {
      s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
      ASN1Reader r = new ASN1Reader(s);
      ASN1Writer w = new ASN1Writer(s);
      r.setIOTimeout(3000);

      BindRequestProtocolOp bindRequest =
        new BindRequestProtocolOp(
                 new ASN1OctetString(dn),
                 3,
                new ASN1OctetString(pw));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      if (bindResponse.getResultCode() == 0) {
        return true;
      }
    } catch (Exception t) {
      t.printStackTrace();
    } finally {
      if (s != null) {
        s.close();
      }
    }
    return false;
  }


  /**
   * Adds the provided entry to the Directory Server using an internal
   * operation.
   *
   * @param  lines  The lines that make up the entry to be added.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void addEntry(String... lines)
         throws Exception
  {
    Entry entry = makeEntry(lines);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation = conn.processAdd(entry.getDN(),
                                     entry.getObjectClasses(),
                                     entry.getUserAttributes(),
                                     entry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS, addOperation
        .getErrorMessage().toString());
  }



  /**
   * Adds the provided set of entries to the Directory Server using internal
   * operations.
   *
   * @param  entries  The entries to be added.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void addEntries(List<Entry> entries)
         throws Exception
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
  public static void addEntries(String... lines)
         throws Exception
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
  public static int applyModifications(String... lines)
         throws Exception
  {
    if (! SERVER_STARTED)
    {
      startServer();
    }

    String path = createTempFile(lines);
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(serverLdapPort),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a",
      "-f", path
    };

    return LDAPModify.mainModify(args, false, null, null);
  }





  /**
   * Creates a temporary text file with the specified contents.  It will be
   * marked for automatic deletion when the JVM exits.
   *
   * @return  The absolute path to the file that was created.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static String createTempFile(String... lines)
          throws Exception
  {
    File f = File.createTempFile("LDAPModifyTestCase", ".txt");
    f.deleteOnExit();

    FileWriter w = new FileWriter(f);
    for (String s : lines)
    {
      w.write(s + System.getProperty("line.separator"));
    }

    w.close();

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

  /**
   * Return a Map constructed via alternating key and value pairs.
   */
  public static LinkedHashMap<String,String> makeMap(String... keyValuePairs) {
    LinkedHashMap<String,String> map = new LinkedHashMap<String,String>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      map.put(keyValuePairs[i], keyValuePairs[i+1]);
    }
    return map;
  }

  // ---------------------------------------------------------------------------
  // ---------------------------------------------------------------------------
  // ---------------------------------------------------------------------------

  // The set of loggers for which the console logger has been disabled.
  private final static Map<Logger, Handler> disabledLogHandlers = new HashMap<Logger,Handler>();

  /** The original System.err print stream.  Use this if you absolutely
   *  must write something to System.err. */
  public final static PrintStream originalSystemErr = System.err;

  /** The original System.out print stream.  Use this if you absolutely
   *  must write something to System.out. */
  public final static PrintStream originalSystemOut = System.out;

  /** System.err is redirected to here so that we can only print it out
   *  if a test fails. */
  private final static ByteArrayOutputStream redirectedSystemErr = new ByteArrayOutputStream();

  /** System.out is redirected to here so that we can only print it out
   *  if a test fails. */
  private final static ByteArrayOutputStream redirectedSystemOut = new ByteArrayOutputStream();

  public synchronized static void suppressOutput() {
    String suppressStr = System.getProperty("org.opends.test.suppressOutput");
    if ((suppressStr != null) && suppressStr.equalsIgnoreCase("true"))
    {
      System.setOut(new PrintStream(redirectedSystemOut));
      System.setErr(new PrintStream(redirectedSystemErr));

      LogManager logManager = LogManager.getLogManager();
      Enumeration<String> loggerNames = logManager.getLoggerNames();
      while (loggerNames.hasMoreElements())
      {
        String loggerName = loggerNames.nextElement();
        Logger logger = logManager.getLogger(loggerName);
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
  public synchronized static String getSystemOutContents() {
    return redirectedSystemOut.toString();
  }

  /**
   * @return everything written to System.err since the last time
   * clearSystemErrContents was called.
   */
  public synchronized static String getSystemErrContents() {
    return redirectedSystemErr.toString();
  }

  /**
   * clear everything written to System.out since the last time
   * clearSystemOutContents was called.
   */
  public synchronized static void clearSystemOutContents() {
    redirectedSystemOut.reset();
  }

  /**
   * clear everything written to System.err since the last time
   * clearSystemErrContents was called.
   */
  public synchronized static void clearSystemErrContents() {
    redirectedSystemErr.reset();
  }

  /**
   * clear everything written to the Access, Error, or Debug loggers
   */
  public synchronized static void clearLoggersContents() {
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
    List<String> messages = TestCaseUtils.ACCESS_TEXT_WRITER.getMessages();
    if (! messages.isEmpty())
    {
      logsContents.append(EOL);
      logsContents.append("Access Log Messages:");
      logsContents.append(EOL);
      for (String message : messages)
      {
        logsContents.append(message);
        logsContents.append(EOL);
      }
    }

    messages = TestCaseUtils.ERROR_TEXT_WRITER.getMessages();
    if (! messages.isEmpty())
    {
      logsContents.append(EOL);
      logsContents.append("Error Log Messages:");
      logsContents.append(EOL);
      for (String message : messages)
      {
        logsContents.append(message);
        logsContents.append(EOL);
      }
    }

    messages = TestCaseUtils.DEBUG_TEXT_WRITER.getMessages();
    if(! messages.isEmpty())
    {
      logsContents.append(EOL);
      logsContents.append("Debug Log Messages:");
      logsContents.append(EOL);
      for (String message : messages)
      {
        logsContents.append(message);
        logsContents.append(EOL);
      }
    }

    String systemOut = TestCaseUtils.getSystemOutContents();
    if (systemOut.length() > 0) {
      logsContents.append(EOL + "System.out contents:" + EOL + systemOut);
    }

    String systemErr = TestCaseUtils.getSystemErrContents();
    if (systemErr.length() > 0) {
      logsContents.append(EOL + "System.err contents:" + EOL + systemErr);
    }
  }

  public synchronized static void unsupressOutput() {
    System.setOut(originalSystemOut);
    System.setErr(originalSystemErr);

    for (Logger l : disabledLogHandlers.keySet())
    {
      Handler h = disabledLogHandlers.get(l);
      l.addHandler(h);
    }
    disabledLogHandlers.clear();
  }

  /**
   * Read the contents of a file and return it as a String.
   */
  public static String readFile(String name)
          throws IOException {
    return readFile(new File(name));
  }

  /**
   * Read the contents of a file and return it as a String.
   */
  public static String readFile(File file)
          throws IOException {
    byte[] bytes = readFileBytes(file);
    return new String(bytes);
  }

  /**
   * Returns the contents of file as a List of the lines as defined by
   * java.io.BufferedReader#readLine() (i.e. the line terminator is not
   * included).  An ArrayList is explicitly returned, so that callers know that
   * random access is not expensive.
   */
  public static ArrayList<String> readFileToLines(File file)
          throws IOException {
    BufferedReader reader =
      new BufferedReader(
        new InputStreamReader(
          new DataInputStream(
                  new FileInputStream(file))));

    ArrayList<String> lines = new ArrayList<String>();
    String line;
    while ((line = reader.readLine()) != null)   {
      lines.add(line);
    }

    return lines;
  }


  /**
   * Read the contents of a file and return it as a String.
   */
  private static byte[] readFileBytes(File file)
          throws IOException {
    FileInputStream fis;
    byte[] bytes;
    fis = new FileInputStream(file);
    bytes = readInputStreamBytes(fis, true);
    return bytes;
  }

  /**
   * @param close - if true, close when finished reading.
   * @return input stream content.
   */
  private static byte[] readInputStreamBytes(InputStream is, boolean close)
          throws IOException {
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
        if (close && is != null) {
          try {
            is.close();
          }
          catch (java.io.IOException ex) {
            // ignore these
          }
        } // end of if (is != null)
      }
    }
    return bytes;
  }


  /**
   * Store the contents of a String in a file.
   */
  public static void writeFile(File file, String contents)
          throws IOException {
    writeFile(file.getAbsolutePath(), contents);
  }

  /**
   * Store the contents of a String in a file.
   */
  public static void writeFile(String name, String contents)
          throws IOException {
    writeFile(name, contents.getBytes());
  }

  /**
   * Store the contents of a String in a file.
   */
  public static void writeFile(String path, byte[] contents)
          throws IOException {
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(path);
      fos.write(contents);
    } finally {
      try {
        if (fos != null) fos.close();
      }
      catch (java.io.IOException e) {
        // ignore these
      }
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
    String[] fullArgs = new String[args.length + 10];
    fullArgs[0] = "-h";
    fullArgs[1] = "127.0.0.1";
    fullArgs[2] = "-p";
    fullArgs[3] = String.valueOf(serverLdapPort);
    fullArgs[4] = "-D";
    fullArgs[5] = "cn=Directory Manager";
    fullArgs[6] = "-w";
    fullArgs[7] = "password";
    fullArgs[8] = "-n";
    fullArgs[9] = "--noPropertiesFile";

    System.arraycopy(args, 0, fullArgs, 10, args.length);

    assertEquals(DSConfig.main(fullArgs, false, System.out, System.err), 0);
  }



  /**
   * Gets the root configuration associated with the active server
   * instance. This root configuration can then be used to access and
   * modify the server's configuration using that administration
   * framework's strongly typed API.
   * <p>
   * Note: were possible the {@link #dsconfig(String...)} method
   * should be used in preference to this method in order to perform
   * end-to-end testing.
   *
   * @return Returns the root configuration associated with the active
   *         server instance.
   * @throws Exception
   *           If the management context could not be initialized
   *           against the active server instance.
   */
  public static RootCfgClient getRootConfiguration() throws Exception
  {
    LDAPConnection connection = JNDIDirContextAdaptor.simpleBind(
        "127.0.0.1",
        serverLdapPort,
        "cn=Directory Manager",
        "password");

    ManagementContext context = LDAPManagementContext
        .createFromContext(connection);
    return context.getRootConfiguration();
  }



  /**
   * Return a String representation of all of the current threads.
   * @return a dump of all Threads on the server
   */
  public static String threadStacksToString()
  {
    Map<Thread,StackTraceElement[]> threadStacks = Thread.getAllStackTraces();


    // Re-arrange all of the elements by thread ID so that there is some logical
    // order.
    TreeMap<Long,Map.Entry<Thread,StackTraceElement[]>> orderedStacks =
         new TreeMap<Long,Map.Entry<Thread,StackTraceElement[]>>();
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
        for (int j=0; j < stackElements.length; j++)
        {
          buffer.append("   ").append(stackElements[j].getClassName());
          buffer.append(".");
          buffer.append(stackElements[j].getMethodName());
          buffer.append("(");
          buffer.append(stackElements[j].getFileName());
          buffer.append(":");
          if (stackElements[j].isNativeMethod())
          {
            buffer.append("native");
          }
          else
          {
            buffer.append(stackElements[j].getLineNumber());
          }
          buffer.append(")").append(EOL);
        }
      }
      buffer.append(EOL);
    }

    return buffer.toString();
  }
}
