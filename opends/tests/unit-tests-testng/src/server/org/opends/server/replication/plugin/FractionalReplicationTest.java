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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/**
 * Various tests around fractional replication
 */
@SuppressWarnings("javadoc")
public class FractionalReplicationTest extends ReplicationTestCase {

  /** The RS */
  private ReplicationServer replicationServer = null;
  /** RS port */
  private int replServerPort = -1;
  /** Represents the real domain to test (replays and filters) */
  private Entry fractionalDomainCfgEntry = null;
  /** The domain used to send updates to the reald domain */
  private FakeReplicationDomain replicationDomain = null;

  /** Ids of servers */
  private static final int DS1_ID = 1; // fractional domain
  private static final int DS2_ID = 2; // fake domain
  private static final int RS_ID = 91; // replication server

  private final String testName = this.getClass().getSimpleName();

  /** Fractional mode */
  private static final int EXCLUDE_FRAC_MODE = 0;
  private static final int INCLUDE_FRAC_MODE = 1;

  int initWindow = 100;
  private ChangeNumberGenerator gen = null;

  /** The tracer object for the debug logger */
  private static final DebugTracer TRACER = getTracer();

  /** Number of seconds before generating an error if some conditions not met */
  private static final int TIMEOUT = 10000;

  /** Uuid of the manipulated entry */
  private static final String ENTRY_UUID =
    "11111111-1111-1111-1111-111111111111";
  private static final String ENTRY_UUID2 =
    "22222222-2222-2222-2222-222222222222";
  private static final String ENTRY_UUID3 =
    "33333333-3333-3333-3333-333333333333";
  /** Dn of the manipulated entry */
  private static String ENTRY_DN = "uid=1," + TEST_ROOT_DN_STRING;

  /**
   * Optional attribute not part of concerned attributes of the fractional
   * configuration during tests. It should not be impacted by fractional
   * mechanism
   */
  private static final String OPTIONAL_ATTR = "description";

  /**
   * Optional attribute used as synchronization attribute to know when the
   * modify operation has been processed (used as add new attribute in the
   * modify operation) It may or may not be part of the filtered attributes,
   * depending on the fractional test mode : exclusive or inclusive
   */
  private static final String SYNCHRO_OPTIONAL_ATTR = "seeAlso";

  /** Second test backend */
  private static final String TEST2_ROOT_DN_STRING = "dc=example,dc=com";
  private static final String TEST2_ORG_DN_STRING = "o=test2," + TEST2_ROOT_DN_STRING;
  private static String ENTRY_DN2 = "uid=1," + TEST2_ORG_DN_STRING;

  private void debugInfo(String s) {
    logError(Message.raw(Category.SYNC, Severity.NOTICE, s));
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST **" + s);
    }
  }

  /**
   * Before starting the tests configure some stuff
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    // Find  a free port for the replicationServer
    ServerSocket socket = TestCaseUtils.bindFreePort();
    replServerPort = socket.getLocalPort();
    socket.close();
  }

  /**
   * Returns a bunch of single values for fractional-exclude configuration
   * attribute
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testExcludePrecommitProvider")
  private Object[][] testExcludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional-exclude configuration
   * attribute
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testExcludeNightlyProvider")
  private Object[][] testExcludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"INETORGPERSON", "DISPLAYNAME"}},
      { 2, new String[] {"inetOrgPerson", "2.16.840.1.113730.3.1.241"}},
      { 3, new String[] {"2.16.840.1.113730.3.2.2", "displayName"}},
      { 4, new String[] {"2.16.840.1.113730.3.2.2", "2.16.840.1.113730.3.1.241"}},
      { 5, new String[] {"inetOrgPerson", "displayName", "carLicense"}},
      { 6, new String[] {"organizationalPerson", "title", "postalCode"}},
      { 7, new String[] {"2.5.6.7", "title", "postalCode"}},
      { 8, new String[] {"2.5.6.7", "TITLE", "2.5.4.17"}},
      { 9, new String[] {"2.5.6.7", "2.5.4.12", "2.5.4.17"}},
      { 10, new String[] {"*", "roomNumber"}},
      { 11, new String[] {"*", "0.9.2342.19200300.100.1.6"}},
      { 12, new String[] {"*", "postOfficeBox", "0.9.2342.19200300.100.1.6"}},
      { 13, new String[] {"*", "2.5.4.18", "0.9.2342.19200300.100.1.6"}}
    };
  }

  /**
   * Calls the testExclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testExcludePrecommitProvider")
  public void testExcludePrecommit(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testExclude(testProviderLineId, fractionalConf);
  }

  /**
   * Calls the testExclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testExcludeNightlyProvider", groups = "slow")
  public void testExcludeNightly(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testExclude(testProviderLineId, fractionalConf);
  }

  /**
   * Performs Add and Modify operations including attributes that are excluded
   * with the passed fractional configuration and checks that these attributes
   * are not part of the concerned entry.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testExclude(int testProviderLineId,
    String... fractionalConf) throws Exception
  {

    String testcase = "testExclude" + testProviderLineId;

    initTest();

    try
    {
      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      createFractionalDomain(true, EXCLUDE_FRAC_MODE, fractionalConf);

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // perform add operation
      sendAddMsg(true, fractionalConf);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = null;
      try
      {
        newEntry = getEntry(DN.decode(ENTRY_DN), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      checkEntryFilteredAfterAdd(newEntry, EXCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(true, fractionalConf);

      // Wait for modify operation being replayed and
      // check that entry does not contain forbidden attributes
      Entry entry = null;
      boolean synchroAttrFound = false;
      int timeout = TIMEOUT;
      while(timeout>0)
      {
        try
        {
          entry = getEntry(DN.decode(ENTRY_DN), TIMEOUT, true);
          if (entry.hasAttribute(DirectoryServer.getAttributeType(SYNCHRO_OPTIONAL_ATTR.toLowerCase())))
          {
            synchroAttrFound = true;
            break;
          }
          Thread.sleep(1000);
          timeout--;
        } catch (Exception e)
        {
          fail("Error waiting for modify operation being replayed : " + e.getMessage());
        }
      }
      assertTrue(synchroAttrFound, "Modify operation not replayed");
      checkEntryFilteredAfterModify(entry, EXCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Returns a bunch of single values for fractional-include configuration
   * attribute
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testIncludePrecommitProvider")
  private Object[][] testIncludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional-include configuration
   * attribute
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testIncludeNightlyProvider")
  private Object[][] testIncludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"INETORGPERSON", "DISPLAYNAME"}},
      { 2, new String[] {"inetOrgPerson", "2.16.840.1.113730.3.1.241"}},
      { 3, new String[] {"2.16.840.1.113730.3.2.2", "displayName"}},
      { 4, new String[] {"2.16.840.1.113730.3.2.2", "2.16.840.1.113730.3.1.241"}},
      { 5, new String[] {"inetOrgPerson", "displayName", "carLicense"}},
      { 6, new String[] {"organizationalPerson", "title", "postalCode"}},
      { 7, new String[] {"2.5.6.7", "title", "postalCode"}},
      { 8, new String[] {"2.5.6.7", "TITLE", "2.5.4.17"}},
      { 9, new String[] {"2.5.6.7", "2.5.4.12", "2.5.4.17"}},
      { 10, new String[] {"*", "roomNumber"}},
      { 11, new String[] {"*", "0.9.2342.19200300.100.1.6"}},
      { 12, new String[] {"*", "postOfficeBox", "0.9.2342.19200300.100.1.6"}},
      { 13, new String[] {"*", "2.5.4.18", "0.9.2342.19200300.100.1.6"}}
    };
  }

  /**
   * Calls the testInclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testIncludePrecommitProvider")
  public void testIncludePrecommit(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testInclude(testProviderLineId, fractionalConf);
  }

  /**
   * Calls the testInclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testIncludeNightlyProvider", groups = "slow")
  public void testIncludeNightly(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testInclude(testProviderLineId, fractionalConf);
  }

  /**
   * Performs Add and Modify operations including attributes that are excluded
   * with the passed fractional configuration and checks that these attributes
   * are not part of the concerned entry.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testInclude(int testProviderLineId,
    String... fractionalConf) throws Exception
  {

    String testcase = "testInclude" + testProviderLineId;

    initTest();

    try
    {
      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      createFractionalDomain(true, INCLUDE_FRAC_MODE, fractionalConf);

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // perform add operation
      sendAddMsg(true, fractionalConf);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = null;
      try
      {
        newEntry = getEntry(DN.decode(ENTRY_DN), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      checkEntryFilteredAfterAdd(newEntry, INCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(true, fractionalConf);

      // Wait for modify operation being replayed and
      // check that entry does not contain forbidden attributes
      Entry entry = null;
      boolean synchroAttrFound = false;
      int timeout = TIMEOUT;
      while(timeout>0)
      {
        try
        {
          entry = getEntry(DN.decode(ENTRY_DN), TIMEOUT, true);
          if (entry.hasAttribute(DirectoryServer.getAttributeType(SYNCHRO_OPTIONAL_ATTR.toLowerCase())))
          {
            synchroAttrFound = true;
            break;
          }
          Thread.sleep(1000);
          timeout--;
        } catch (Exception e)
        {
          fail("Error waiting for modify operation being replayed : " + e.getMessage());
        }
      }
      assertTrue(synchroAttrFound, "Modify operation not replayed");
      checkEntryFilteredAfterModify(entry, INCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Creates connects (to the RS) and starts the fake replication domain
   * Use the passed generation id.
   */
  private void createFakeReplicationDomain(boolean firstBackend, long generationId)
  {
    try{
      List<String> replicationServers = new ArrayList<String>();
      replicationServers.add("localhost:" + replServerPort);

      replicationDomain = new FakeReplicationDomain(
            (firstBackend ? TEST_ROOT_DN_STRING : TEST2_ROOT_DN_STRING), DS2_ID, replicationServers, 100, 1000, generationId);

      // Test connection
      assertTrue(replicationDomain.isConnected());
      int rdPort = -1;
      // Check connected server port
      String serverStr = replicationDomain.getReplicationServer();
      int index = serverStr.lastIndexOf(':');
      if ((index == -1) || (index >= serverStr.length()))
        fail("Enable to find port number in: " + serverStr);
      String rdPortStr = serverStr.substring(index + 1);
      try
      {
        rdPort = Integer.valueOf(rdPortStr);
      } catch (Exception e)
      {
        fail("Enable to get an int from: " + rdPortStr);
      }
      assertEquals(rdPort, replServerPort);
    } catch (Exception e)
    {
      fail("createreplicationDomain " + e.getMessage());
    }
  }

  private void initTest()
  {
    replicationDomain = null;
    fractionalDomainCfgEntry = null;
    replicationServer = null;

    // Initialize the test backend
    try {
      TestCaseUtils.initializeTestBackend(false);
    } catch(Exception e) {
      fail("Could not initialize backend : " + e.getMessage());
    }

    // initialize cn generator
    gen = new ChangeNumberGenerator(DS2_ID, 0L);
  }

  private void endTest()
  {
    if (replicationDomain != null)
    {
      replicationDomain.disableService();
      replicationDomain = null;
    }

    if (fractionalDomainCfgEntry != null)
    {
      removeDomain(fractionalDomainCfgEntry);
      fractionalDomainCfgEntry = null;
    }

    if (replicationServer != null)
    {
      replicationServer.clearDb();
      replicationServer.remove();
      StaticUtils.recursiveDelete(new File(DirectoryServer.getInstanceRoot(),
                 replicationServer.getDbDirName()));
      replicationServer = null;
    }
  }

  /**
   * Creates a fractional domain with the passed configuration.
   * Before that, initializes the backend with the root entry and if requested
   * with the correct fractional configuration in it
   */
  private void createFractionalDomain(boolean initializeDomain,
    int fractionalMode, String... fractionalConf)
  {
    try
    {
      String fractModeAttrName = null;
      String opFractModeAttrName = null;
      boolean addSynchroAttribute = false;
      switch (fractionalMode)
      {
        case EXCLUDE_FRAC_MODE:
          fractModeAttrName = "ds-cfg-fractional-exclude";
          opFractModeAttrName = "ds-sync-fractional-exclude";
          break;
        case INCLUDE_FRAC_MODE:
          fractModeAttrName = "ds-cfg-fractional-include";
          opFractModeAttrName = "ds-sync-fractional-include";
          // For inclusive mode, we use an attribute that is added in the modify
          // operation to know when the modify operation has been played. The added
          // attribute can only be part of the include config to be taken into account
          addSynchroAttribute = true;
          break;
        default:
          fail("Unexpected fractional mode.");
      }

      /**
       * Create a root entry with potentially with fractional configuration before domain creation
       */

      // Create base entry with correct fractional config
      String topEntryLdif = null;

      if (initializeDomain)
      {
        // Add first backend top entry
        topEntryLdif = "dn: " + TEST_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: organization\n" +
        "o: " + TEST_BACKEND_ID + "\n" +
        "entryUUID: " + ENTRY_UUID3 + "\n";

        // Add fractional config
        int i=0;
        int size = fractionalConf.length;
        for (String fracCfgValue : fractionalConf) // Add fractional operational attributes
        {
          if (i==0)
          {
            // First string is the class
            topEntryLdif += opFractModeAttrName + ": " + fracCfgValue + ":";
          }
          else
          {
            // Other strings are attributes
            String endString = (addSynchroAttribute ? ("," + SYNCHRO_OPTIONAL_ATTR + "\n") : "\n");
            topEntryLdif += fracCfgValue + ( (i<size-1) ? "," : endString);
          }
            i++;
        }
      }
      else
      {
        // Add second backend top entry
        topEntryLdif = "dn: " + TEST2_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: domain\n" +
        "dc: example\n";
      }
      addEntry(TestCaseUtils.entryFromLdifString(topEntryLdif));

      /**
       * Create the domain with the passed fractional configuration
       */

      // Create a config entry ldif, matching passed settings
      String configEntryLdif = "dn: cn=" + testName + ", cn=domains, " +
        SYNCHRO_PLUGIN_DN + "\n" + "objectClass: top\n" +
        "objectClass: ds-cfg-replication-domain\n" + "cn: " + testName + "\n" +
        "ds-cfg-base-dn: " + (initializeDomain ? TEST_ROOT_DN_STRING : TEST2_ROOT_DN_STRING) + "\n" +
        "ds-cfg-replication-server: localhost:" + replServerPort + "\n" +
        "ds-cfg-server-id: " + DS1_ID + "\n";

      int i=0;
      int size = fractionalConf.length;
      for (String fracCfgValue : fractionalConf) // Add fractional configuration attributes
      {
        if (i==0)
        {
          // First string is the class
          configEntryLdif += fractModeAttrName + ": " + fracCfgValue + ":";
        }
        else
        {
          // Other strings are attributes
          String endString = (addSynchroAttribute ? ("," + SYNCHRO_OPTIONAL_ATTR + "\n") : "\n");
          configEntryLdif += fracCfgValue + ( (i<size-1) ? "," : endString);
        }
          i++;
      }
      fractionalDomainCfgEntry = TestCaseUtils.entryFromLdifString(configEntryLdif);

      // Add the config entry to create the replicated domain
      DirectoryServer.getConfigHandler().addEntry(fractionalDomainCfgEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(fractionalDomainCfgEntry.getDN()),
        "Unable to add the domain config entry: " + configEntryLdif);
    }
    catch(Exception e)
    {
      fail("createFractionalDomain error: " + e.getMessage());
    }
  }

  /**
   * Creates a new ReplicationServer.
   */
  private void createReplicationServer(String testCase)
  {
    try
    {
      SortedSet<String> replServers = new TreeSet<String>();

      String dir = testName + RS_ID + testCase + "Db";
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(replServerPort, dir, 0, RS_ID, 0, 100,
        replServers);
      replicationServer = new ReplicationServer(conf);
    } catch (Exception e)
    {
      fail("createReplicationServer " + e.getMessage());
    }
  }

  /**
   * This class is the minimum implementation of a Concrete ReplicationDomain
   * used to be able to connect to the RS with a known genid. Also to be able
   * to send updates
   */
  private class FakeReplicationDomain extends ReplicationDomain
  {
    // A blocking queue that is used to receive updates from
    // the Replication Service.

    BlockingQueue<UpdateMsg> queue = new LinkedBlockingQueue<UpdateMsg>();

    // A string that will be exported should exportBackend be called.
    String exportString = null;

    // A StringBuilder that will be used to build a new String should the
    // import be called.
    StringBuilder importString = null;
    private int exportedEntryCount;
    private long generationID = -1;

    public FakeReplicationDomain(
      String serviceID,
      int serverID,
      Collection<String> replicationServers,
      int window,
      long heartbeatInterval,
      long generationId) throws ConfigException
    {
      super(serviceID, serverID, 100);
      generationID = generationId;
      startPublishService(replicationServers, window, heartbeatInterval, 500);
      startListenService();
    }

    public void initExport(String exportString, int exportedEntryCount)
    {
      this.exportString = exportString;
      this.exportedEntryCount = exportedEntryCount;
    }

    @Override
    public long countEntries() throws DirectoryException
    {
      return exportedEntryCount;
    }

    @Override
    protected void exportBackend(OutputStream output) throws DirectoryException
    {
      try
      {
        output.write(exportString.getBytes());
        output.flush();
        output.close();
      } catch (IOException e)
      {
        throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
          ERR_BACKEND_EXPORT_ENTRY.get("", ""));
      }
    }

    @Override
    public long getGenerationID()
    {
      return generationID;
    }

    @Override
    protected void importBackend(InputStream input) throws DirectoryException
    {
      byte[] buffer = new byte[1000];

      int ret;
      do
      {
        try
        {
          ret = input.read(buffer, 0, 1000);
        } catch (IOException e)
        {
          throw new DirectoryException(
            ResultCode.OPERATIONS_ERROR,
            ERR_BACKEND_EXPORT_ENTRY.get("", ""));
        }
        importString.append(new String(buffer, 0, ret));
      } while (ret >= 0);
    }

    @Override
    public boolean processUpdate(UpdateMsg updateMsg, AtomicBoolean shutdown)
    {
      if (queue != null)
        queue.add(updateMsg);
      return true;
    }
  }

  private static final String REPLICATION_GENERATION_ID =
    "ds-sync-generation-id";

  private long readGenIdFromSuffixRootEntry(String rootDn)
  {
    long genId=-1;
    try
    {
      DN baseDn = DN.decode(rootDn);
      Entry resultEntry = getEntry(baseDn, 1000, true);
      if (resultEntry==null)
      {
        debugInfo("Entry not found <" + rootDn + ">");
      }
      else
      {
        debugInfo("Entry found <" + rootDn + ">");

        AttributeType synchronizationGenIDType =
          DirectoryServer.getAttributeType(REPLICATION_GENERATION_ID);
        List<Attribute> attrs =
          resultEntry.getAttribute(synchronizationGenIDType);
        if (attrs != null)
        {
          Attribute attr = attrs.get(0);
          if (attr.size() == 1)
          {
            genId =
                Long.decode(attr.iterator().next().getValue().toString());
          }
        }

      }
    }
    catch(Exception e)
    {
      fail("Exception raised in readGenId", e);
    }
    return genId;
  }

  /**
   * Send the AddMsg (from the fake replication domain) for the passed entry
   * containing the attributes defined in the passed fractional configuration
   */
  private void sendAddMsg(boolean firstBackend, String... fractionalConf)
    {
      String entryLdif = "dn: " + (firstBackend ? ENTRY_DN : ENTRY_DN2) + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n";
        String classStr = "";
        if ( fractionalConf[0].equalsIgnoreCase("inetOrgPerson") ||
        fractionalConf[0].equalsIgnoreCase("2.16.840.1.113730.3.2.2"))
        {
          classStr = "objectClass: " + fractionalConf[0] + "\n";
        }
        entryLdif += classStr + "uid: 1\n" +
        "entryUUID: " + ENTRY_UUID + "\n" +
        "sn: snValue\n" + "cn: cnValue\n" +
        OPTIONAL_ATTR + ": " + OPTIONAL_ATTR + "Value\n";

      // Add attributes concerned by fractional configuration
      boolean first = true;
      for (String fracCfgValue : fractionalConf)
      {
        if (!first)
        {
          // First string is the class
          entryLdif += fracCfgValue + ": " + fracCfgValue + "Value\n";
        }
        first = false;
      }

      Entry entry = null;
      try
      {
        entry = TestCaseUtils.entryFromLdifString(entryLdif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        entry.getDN().toString(),
        ENTRY_UUID,
        null,
        entry.getObjectClassAttribute(),
        entry.getAttributes(), new ArrayList<Attribute>());

      replicationDomain.publish(addMsg);
    }

  /**
   * Send (from the fake replication domain) a ModifyMsg for the passed entry
   * modifying attributes defined in the passed fractional configuration
   */
  private void sendModifyMsg(boolean firstBackend, String... fractionalConf)
    {

      // Create modifications on the fractional attributes
      List<Modification> mods = new ArrayList<Modification>();
      boolean first = true;
      for (String fracCfgValue : fractionalConf)
      {
        if (!first)
        {
          // First string is the class
          Attribute attr =
            Attributes.create(fracCfgValue.toLowerCase(), fracCfgValue + "NewValue");
          Modification mod = new Modification(ModificationType.REPLACE, attr);
          mods.add(mod);
        }
        first = false;
      }

      // Add modification for the special attribute (modified attribute)
      Attribute attr =
        Attributes.create(OPTIONAL_ATTR.toLowerCase(), OPTIONAL_ATTR + "NewValue");
      Modification mod = new Modification(ModificationType.REPLACE, attr);
      mods.add(mod);

      // Add modification for the synchro attribute (added attribute)
      attr =
        Attributes.create(SYNCHRO_OPTIONAL_ATTR.toLowerCase(), SYNCHRO_OPTIONAL_ATTR + "Value");
      mod = new Modification(ModificationType.ADD, attr);
      mods.add(mod);

      DN entryDn = null;
      try
      {
        entryDn = DN.decode((firstBackend ? ENTRY_DN : ENTRY_DN2));
      } catch (Exception e)
      {
        fail("Cannot create dn entry: " + e.getMessage());
      }
      ModifyMsg modifyMsg = new ModifyMsg(gen.newChangeNumber(), entryDn, mods,
        ENTRY_UUID);

      replicationDomain.publish(modifyMsg);
    }

  /**
   * Utility method : Add an entry in the database
   */
  private void addEntry(Entry entry) throws Exception
  {
    AddOperationBasis addOp = new AddOperationBasis(connection,
      InternalClientConnection.nextOperationID(), InternalClientConnection.
      nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
      entry.getUserAttributes(), entry.getOperationalAttributes());
    addOp.setInternalOperation(true);
    addOp.run();
    assertNotNull(getEntry(entry.getDN(), 1000, true));
  }

  /**
   * Check that the just added entry (newEntry) meets the fractional criteria
   * regarding the passed configuration : mode and attributes to be filtered/not
   * filtered
   */
  private void checkEntryFilteredAfterAdd(Entry newEntry,
    int fractionalMode, String... fractionalConf) throws Exception
  {
    try
    {
      // Is the added entry of the expected object class ?
      String objectClassStr = fractionalConf[0];
      if (!objectClassStr.equals("*"))
      {
        ObjectClass objectClass = DirectoryServer.getObjectClass(objectClassStr.toLowerCase());

        assertTrue(newEntry.hasObjectClass(objectClass));
      }

      // Go through each interesting attribute and check it is present or not
      // according to the fractional mode
      boolean first = true;
      switch (fractionalMode)
      {
        case EXCLUDE_FRAC_MODE:
          // Exclude mode: attributes should not be there, but OPTIONAL_ATTR
          // attribute should
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              assertFalse(newEntry.hasAttribute(DirectoryServer.
                getAttributeType(fracAttr.toLowerCase())));
            }
            first = false;
          }
          checkEntryAttributeValue(newEntry, OPTIONAL_ATTR, OPTIONAL_ATTR + "Value");
          break;
        case INCLUDE_FRAC_MODE:
          // Include mode: attributes should be there, but OPTIONAL_ATTR
          // attribute should not
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              checkEntryAttributeValue(newEntry, fracAttr, fracAttr + "Value");
            }
            first = false;
          }
          assertFalse(newEntry.hasAttribute(DirectoryServer.
                getAttributeType(OPTIONAL_ATTR.toLowerCase())));
          break;
        default:
          fail("Unexpected fractional mode.");
      }
    }
    catch(Exception e)
    {
      fail("checkEntryFilteredAfterAdd error: "  +
        e.getClass().getName() + " :" + e.getMessage() +
        " " + stackTraceToSingleLineString(e)
        );
    }
  }

  /**
   * Check that the just modified entry (entry) meets the fractional criteria
   * regarding the passed configuration : mode and attributes to be filtered/not
   * filtered
   */
  private void checkEntryFilteredAfterModify(Entry entry,
    int fractionalMode, String... fractionalConf) throws Exception
  {
    try
    {
      // Is the added entry of the expected object class ?
      String objectClassStr = fractionalConf[0];
      if (!objectClassStr.equals("*"))
      {
        ObjectClass objectClass = DirectoryServer.getObjectClass(objectClassStr.toLowerCase());

        assertTrue(entry.hasObjectClass(objectClass));
      }

      // Go through each interesting attribute and check it has been modifed or
      // not according to the fractional mode
      boolean first = true;
      switch (fractionalMode)
      {
        case EXCLUDE_FRAC_MODE:
          // Exclude mode: attributes should not be there, but OPTIONAL_ATTR
          // attribute should have been modified
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              assertFalse(entry.hasAttribute(DirectoryServer.
                getAttributeType(fracAttr.toLowerCase())));
            }
            first = false;
          }
          checkEntryAttributeValue(entry, OPTIONAL_ATTR, OPTIONAL_ATTR + "NewValue");
          break;
        case INCLUDE_FRAC_MODE:
          // Include mode: attributes should have been modified, but OPTIONAL_ATTR
          // attribute should not be there
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              checkEntryAttributeValue(entry, fracAttr, fracAttr + "NewValue");
            }
            first = false;
          }
          assertFalse(entry.hasAttribute(DirectoryServer.
                getAttributeType(OPTIONAL_ATTR.toLowerCase())));
          break;
        default:
          fail("Unexpected fractional mode.");
      }
      // In both modes, SYNCHRO_OPTIONAL_ATTR attribute should have been added
      checkEntryAttributeValue(entry, SYNCHRO_OPTIONAL_ATTR, SYNCHRO_OPTIONAL_ATTR + "Value");
    }
    catch(Exception e)
    {
      fail("checkEntryFilteredAfterModify error: "  +
        e.getClass().getName() + " :" + e.getMessage());
    }
  }

  /**
   * Check that the provided entry has a single value attribute which has the
   * expected attribute value
   */
  private static void checkEntryAttributeValue(Entry entry, String attributeName,
    String attributeValue)
  {
    List<Attribute> attrs = entry.getAttribute(attributeName.toLowerCase());
    assertNotNull(attrs, "Was expecting attribute " + attributeName + "=" +
      attributeValue + " but got no attribute");
    assertEquals(attrs.size(), 1);
    Attribute attr = attrs.get(0);
    assertNotNull(attr);
    Iterator<AttributeValue> attrValues = attr.iterator();
    assertNotNull(attrValues);
    assertTrue(attrValues.hasNext());
    AttributeValue attrValue = attrValues.next();
    assertNotNull(attrValue);
    assertFalse(attrValues.hasNext());
    assertEquals(attrValue.toString(), attributeValue, "Was expecting attribute " +
      attributeName + "=" + attributeValue + " but got value: " + attrValue.toString());

  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateExcludePrecommitProvider")
  private Object[][] testInitWithFullUpdateExcludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, true, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateExcludeNightlyProvider")
  private Object[][] testInitWithFullUpdateExcludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, false, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateExcludePrecommitProvider")
  public void testInitWithFullUpdateExcludePrecommit(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateExclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateExcludeNightlyProvider", groups = "slow")
  public void testInitWithFullUpdateExcludeNightly(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateExclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Configures a domain which is not fractional to fractional exclusive,
   * then emulates an online full update to initialize the fractional domain and
   * have it operational.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testInitWithFullUpdateExclude(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    String testcase = "testInitWithFullUpdateExclude" + testProviderLineId;

    initTest();

    // We need a backend with a real configuration in cn=config as at import time
    // the real domain will check for backend existence in cn=config. So we use
    // dc=example,dc=com for this particular test.
    // Clear the backend
   LDAPReplicationDomain.clearJEBackend(false, "userRoot", TEST2_ROOT_DN_STRING);

    try
    {
      /*
       * Create replication server and connect fractional domain to it then fake
       * domain
       */

      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      // without initializing the backend
      createFractionalDomain(false, EXCLUDE_FRAC_MODE, fractionalConf);

      // The domain should go in bad gen as backend is not initialized with
      // fractional data
      LDAPReplicationDomain fractionalReplicationDomain =
        MultimasterReplication.findDomain(DN.decode(TEST2_ROOT_DN_STRING), null);
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.BAD_GEN_ID_STATUS, 5);

      // create fake domain to perform the full update
      long generationId = readGenIdFromSuffixRootEntry(TEST2_ROOT_DN_STRING);
      assertTrue(generationId != 0L);
      createFakeReplicationDomain(false, generationId);

      /*
       * Create the LDIF that will be used to initialize the domain from the
       * fake one. Initialize the fake domain with it.
       */

      //      Top Entry
      String exportLdif = "dn: " + TEST2_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: domain\n" +
        "dc: example\n" +
        "ds-sync-generation-id: " + generationId + "\n";
      if (importedDomainIsFractional)
      {
        //                Add fractional config
        int i=0;
        int size = fractionalConf.length;
        for (String fracCfgValue : fractionalConf) // Add fractional operational attributes
        {
          if (i==0)
          {
            // First string is the class
            exportLdif += "ds-sync-fractional-exclude: " + fracCfgValue + ":";
          }
          else
          {
            // Other strings are attributes
            exportLdif += fracCfgValue + ( (i<size-1) ? "," : "\n");
          }
            i++;
        }
      }
      //      Org Entry
      exportLdif += "\ndn: " + TEST2_ORG_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: organization\n" +
        "o: test2\n\n";
      //      User entry
      exportLdif += "dn: " + ENTRY_DN2 + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "sn: snValue\n" + "cn: cnValue\n" +
        "uid: 1\n" + "entryUUID: " +
        ENTRY_UUID + "\n" + OPTIONAL_ATTR + ": " + OPTIONAL_ATTR + "Value\n";
      if (!importedDomainIsFractional)
      {
        //                Add attributes concerned by fractional configuration
        boolean first = true;
        for (String fracCfgValue : fractionalConf)
        {
          if (!first)
          {
            // First string is the class
            exportLdif += fracCfgValue + ": " + fracCfgValue + "Value\n";
          }
          first = false;
        }
      }
      exportLdif += "\n"; // Needed ?

      replicationDomain.initExport(exportLdif, 2);

      // Perform full update from fake domain to fractional domain
      replicationDomain.initializeRemote(DS1_ID);

      /*
       * Check fractional domain is operational and that filtering has been done
       * during the full update
       */

      // The domain should go back in normal status
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.NORMAL_STATUS, 15);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = null;
      try
      {
        newEntry = getEntry(DN.decode(ENTRY_DN2), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been created: " + e.getMessage());
      }
      checkEntryFilteredAfterAdd(newEntry, EXCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(false, fractionalConf);

      // Wait for modify operation being replayed and
      // check that entry does not contain forbidden attributes
      Entry entry = null;
      boolean synchroAttrFound = false;
      int timeout = TIMEOUT;
      while(timeout>0)
      {
        try
        {
          entry = getEntry(DN.decode(ENTRY_DN2), TIMEOUT, true);
          if (entry.hasAttribute(DirectoryServer.getAttributeType(SYNCHRO_OPTIONAL_ATTR.toLowerCase())))
          {
            synchroAttrFound = true;
            break;
          }
          Thread.sleep(1000);
          timeout--;
        } catch (Exception e)
        {
          fail("Error waiting for modify operation being replayed : " + e.getMessage());
        }
      }
      assertTrue(synchroAttrFound, "Modify operation not replayed");
      checkEntryFilteredAfterModify(entry, EXCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Wait for the passed domain to have the desired status or fail if timeout
   * waiting.
   */
  private void waitForDomainStatus(ReplicationDomain replicationDomain,
    ServerStatus expectedStatus, int nSec)
  {
    int toWait = nSec;
    ServerStatus serverStatus = null;
    while(nSec > 0)
    {
      serverStatus = replicationDomain.getStatus();
      if ( serverStatus ==  expectedStatus )
      {
        debugInfo("waitForDomainStatus: expected replication " +
          "domain status obtained after " + (toWait-nSec) + " second(s).");
        return;
      }
      TestCaseUtils.sleep(1000);
      nSec--;
    }
    fail("Did not get expected replication domain status: expected <" + expectedStatus +
      "> but got <" + serverStatus + ">, after " + toWait + " second(s)");
  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateIncludePrecommitProvider")
  private Object[][] testInitWithFullUpdateIncludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, true, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateIncludeNightlyProvider")
  private Object[][] testInitWithFullUpdateIncludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, false, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateIncludePrecommitProvider")
  public void testInitWithFullUpdateIncludePrecommit(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateInclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateIncludeNightlyProvider", groups = "slow")
  public void testInitWithFullUpdateIncludeNightly(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateInclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Configures a domain which is not fractional to fractional inclusive,
   * then emulates an online full update to initialize the fractional domain and
   * have it operational.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testInitWithFullUpdateInclude(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    String testcase = "testInitWithFullUpdateInclude" + testProviderLineId;

    initTest();

    // We need a backend with a real configuration in cn=config as at import time
    // the real domain will check for backend existence in cn=config. So we use
    // dc=example,dc=com for this particular test.
    // Clear the backend
    LDAPReplicationDomain.clearJEBackend(false, "userRoot", TEST2_ROOT_DN_STRING);

    try
    {
      /*
       * Create replication server and connect fractional domain to it then fake
       * domain
       */

      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      // without initializing the backend
      createFractionalDomain(false, INCLUDE_FRAC_MODE, fractionalConf);

      // The domain should go in bad gen as backend is not initialized with
      // fractional data
      LDAPReplicationDomain fractionalReplicationDomain =
        MultimasterReplication.findDomain(DN.decode(TEST2_ROOT_DN_STRING), null);
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.BAD_GEN_ID_STATUS, 5);

      // create fake domain to perform the full update
      long generationId = readGenIdFromSuffixRootEntry(TEST2_ROOT_DN_STRING);
      assertTrue(generationId != 0L);
      createFakeReplicationDomain(false, generationId);

      /*
       * Create the LDIF that will be used to initialize the domain from the
       * fake one. Initialize the fake domain with it.
       */

      //      Top Entry
      String exportLdif = "dn: " + TEST2_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: domain\n" +
        "dc: example\n" +
        "ds-sync-generation-id: " + generationId + "\n";
      if (importedDomainIsFractional)
      {
        //                Add fractional config
        int i=0;
        int size = fractionalConf.length;
        for (String fracCfgValue : fractionalConf) // Add fractional operational attributes
        {
          if (i==0)
          {
            // First string is the class
            exportLdif += "ds-sync-fractional-include: " + fracCfgValue + ":";
          }
          else
          {
            // Other strings are attributes
            exportLdif += fracCfgValue + ( (i<size-1) ? "," : "," + SYNCHRO_OPTIONAL_ATTR + "\n");
          }
            i++;
        }
      }
      //      Org Entry
      exportLdif += "\ndn: " + TEST2_ORG_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: organization\n" +
        "o: test2\n\n";
      //      User entry
      exportLdif += "dn: " + ENTRY_DN2 + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" +
        "sn: snValue\n" + "cn: cnValue\n" + "uid: 1\n" + "entryUUID: " +
        ENTRY_UUID + "\n" + OPTIONAL_ATTR + ": " + OPTIONAL_ATTR + "Value\n";
      //                Add attributes concerned by fractional configuration
      boolean first = true;
      for (String fracCfgValue : fractionalConf)
      {
        if (!first)
        {
          // First string is the class
          exportLdif += fracCfgValue + ": " + fracCfgValue + "Value\n";
        }
        first = false;
      }
      exportLdif += "\n"; // Needed ?

      replicationDomain.initExport(exportLdif, 2);

      // Perform full update from fake domain to fractional domain
      replicationDomain.initializeRemote(DS1_ID);

      /*
       * Chack fractional domain is operational and that filtering has been done
       * during the full update
       */

      // The domain should go back in normal status
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.NORMAL_STATUS, 15);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = null;
      try
      {
        newEntry = getEntry(DN.decode(ENTRY_DN2), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been created: " + e.getMessage());
      }
      checkEntryFilteredAfterAdd(newEntry, INCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(false, fractionalConf);

      // Wait for modify operation being replayed and
      // check that entry does not contain forbidden attributes
      Entry entry = null;
      boolean synchroAttrFound = false;
      int timeout = TIMEOUT;
      while(timeout>0)
      {
        try
        {
          entry = getEntry(DN.decode(ENTRY_DN2), TIMEOUT, true);
          if (entry.hasAttribute(DirectoryServer.getAttributeType(SYNCHRO_OPTIONAL_ATTR.toLowerCase())))
          {
            synchroAttrFound = true;
            break;
          }
          Thread.sleep(1000);
          timeout--;
        } catch (Exception e)
        {
          fail("Error waiting for modify operation being replayed : " + e.getMessage());
        }
      }
      assertTrue(synchroAttrFound, "Modify operation not replayed");
      checkEntryFilteredAfterModify(entry, INCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Tests an add operation on an entry with RDN containing forbidden attribute
   * by fractional exclude configuration
   */
  @Test
  public void testAddWithForbiddenAttrInRDNExclude()
  {
     String testcase = "testAddWithForbiddenAttrInRDNExclude";

    initTest();

    try
    {
      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      createFractionalDomain(true, EXCLUDE_FRAC_MODE,
        new String[] {"inetOrgPerson", "displayName", "description"});

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      /*
       * Perform add operation with forbidden attribute in RDN
       */
      String entryLdif = "dn: displayName=ValueToBeKept," +
        TEST_ROOT_DN_STRING + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "sn: snValue\n" + "cn: cnValue\n" +
        "entryUUID: " + ENTRY_UUID + "\n" +
        "displayName: ValueToBeKept\ndisplayName: displayNameValue\n";

      Entry entry = null;
      try
      {
        entry = TestCaseUtils.entryFromLdifString(entryLdif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        entry.getDN().toString(),
        ENTRY_UUID,
        null,
        entry.getObjectClassAttribute(),
        entry.getAttributes(), new ArrayList<Attribute>());

      replicationDomain.publish(addMsg);

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */

      Entry newEntry = null;
      try
      {
        newEntry = getEntry(entry.getDN(), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(entry.getDN(), newEntry.getDN());
      ObjectClass objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");

      /**
       * Now perform same test, but with 2 forbidden attributes in RDN, using '+'
       */

      /*
       * Perform add operation with forbidden attribute in RDN
       */
      entryLdif = "dn: displayName=ValueToBeKept+description=ValueToBeKeptToo," +
        TEST_ROOT_DN_STRING + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "entryUUID: " + ENTRY_UUID2 + "\n" +
        "sn: snValue\n" + "cn: cnValue\n" +
        "displayName: ValueToBeKept\ndisplayName: displayNameValue\n" +
        "description: descriptionValue\ndescription: ValueToBeKeptToo\n";

      try
      {
        entry = TestCaseUtils.entryFromLdifString(entryLdif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      addMsg = new AddMsg(gen.newChangeNumber(),
        entry.getDN().toString(),
        ENTRY_UUID2,
        null,
        entry.getObjectClassAttribute(),
        entry.getAttributes(), new ArrayList<Attribute>());

      replicationDomain.publish(addMsg);

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */

      try
      {
        newEntry = getEntry(entry.getDN(), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(entry.getDN(), newEntry.getDN());
      objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "description", "ValueToBeKeptToo");
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Tests an add operation on an entry with RDN containing forbidden attribute
   * by fractional include configuration
   */
  @Test
  public void testAddWithForbiddenAttrInRDNInclude()
  {
     String testcase = "testAddWithForbiddenAttrInRDNInclude";

    initTest();

    try
    {
      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      createFractionalDomain(true, INCLUDE_FRAC_MODE,
        new String[] {"inetOrgPerson", "carLicense"});

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      /*
       * Perform add operation with forbidden attribute in RDN
       */
      String entryLdif = "dn: displayName=ValueToBeKept," +
        TEST_ROOT_DN_STRING + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "sn: snValue\n" + "cn: cnValue\n" +
        "entryUUID: " + ENTRY_UUID + "\n" +
        "displayName: ValueToBeKept\ndisplayName: displayNameValue\n" +
        "carLicense: cirLicenseValue\n";


      Entry entry = null;
      try
      {
        entry = TestCaseUtils.entryFromLdifString(entryLdif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        entry.getDN().toString(),
        ENTRY_UUID,
        null,
        entry.getObjectClassAttribute(),
        entry.getAttributes(), new ArrayList<Attribute>());

      replicationDomain.publish(addMsg);

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */

      Entry newEntry = null;
      try
      {
        newEntry = getEntry(entry.getDN(), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(entry.getDN(), newEntry.getDN());
      ObjectClass objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "carLicense", "cirLicenseValue");

      /**
       * Now perform same test, but with 2 forbidden attributes in RDN, using '+'
       */

      /*
       * Perform add operation with forbidden attribute in RDN
       */
      entryLdif = "dn: displayName=ValueToBeKept+description=ValueToBeKeptToo," +
        TEST_ROOT_DN_STRING + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "sn: snValue\n" + "cn: cnValue\n" +
        "entryUUID: " + ENTRY_UUID2 + "\n" +
        "displayName: ValueToBeKept\ndisplayName: displayNameValue\n" +
        "description: descriptionValue\ndescription: ValueToBeKeptToo\n" +
        "carLicense: cirLicenseValue\n";

      try
      {
        entry = TestCaseUtils.entryFromLdifString(entryLdif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      addMsg = new AddMsg(gen.newChangeNumber(),
        entry.getDN().toString(),
        ENTRY_UUID2,
        null,
        entry.getObjectClassAttribute(),
        entry.getAttributes(), new ArrayList<Attribute>());

      replicationDomain.publish(addMsg);

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */

      try
      {
        newEntry = getEntry(entry.getDN(), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(entry.getDN(), newEntry.getDN());
      objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "description", "ValueToBeKeptToo");
      checkEntryAttributeValue(newEntry, "carLicense", "cirLicenseValue");
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Tests modify dn operation on an entry with old RDN containing forbidden
   * attribute by fractional exclude configuration
   */
  @Test
  public void testModifyDnWithForbiddenAttrInRDNExclude()
  {
     String testcase = "testModifyDnWithForbiddenAttrInRDNExclude";

    initTest();

    try
    {
      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      createFractionalDomain(true, EXCLUDE_FRAC_MODE,
        new String[] {"inetOrgPerson", "displayName", "description"});

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      /*
       * Perform add operation with forbidden attribute in RDN
       */
      String entryName = "displayName=ValueToBeKept+description=ValueToBeRemoved," + TEST_ROOT_DN_STRING ;
      String entryLdif = "dn: " + entryName + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "sn: snValue\n" + "cn: cnValue\n" +
        "entryUUID: " + ENTRY_UUID + "\n" +
        "displayName: ValueToBeKept\ndescription: ValueToBeRemoved\n";

      Entry entry = null;
      try
      {
        entry = TestCaseUtils.entryFromLdifString(entryLdif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        entry.getDN().toString(),
        ENTRY_UUID,
        null,
        entry.getObjectClassAttribute(),
        entry.getAttributes(), new ArrayList<Attribute>());

      replicationDomain.publish(addMsg);

      /*
       * check that entry has been created and has attribute values from RDN
       */

      Entry newEntry = null;
      try
      {
        newEntry = getEntry(entry.getDN(), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(entry.getDN(), newEntry.getDN());
      ObjectClass objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "description", "ValueToBeRemoved");

      /*
       * Perform modify dn operation by renaming the entry keeping only one of
       * the forbidden attributes
       */

      String newEntryName = "displayName=ValueToBeKept," + TEST_ROOT_DN_STRING ;
      DN newEntryDn = null;
      try
      {
        newEntryDn = DN.decode(newEntryName);
      } catch(DirectoryException e)
      {
        fail("Could not get DN from string: " + newEntryName);
      }

      // Create modify dn message to modify the entry.
      ModifyDNMsg modDnMsg = new ModifyDNMsg(entryName, gen.newChangeNumber(),
        ENTRY_UUID, ENTRY_UUID3, false, TEST_ROOT_DN_STRING,
        "displayName=ValueToBeKept", null);

      replicationDomain.publish(modDnMsg);

      /*
       * check that entry has been renamed  and has only attribute left in the
       * new RDN
       */

      try
      {
        newEntry = getEntry(newEntryDn, TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(newEntryDn, newEntry.getDN());
      objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      assertNull(newEntry.getAttribute("description"));
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Tests modify dn operation on an entry with old RDN containing forbidden
   * attribute by fractional include configuration
   */
  @Test
  public void testModifyDnWithForbiddenAttrInRDNInclude()
  {
    String testcase = "testModifyDnWithForbiddenAttrInRDNInclude";

    initTest();

    try
    {
      // create replication server
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      createFractionalDomain(true, INCLUDE_FRAC_MODE,
        new String[] {"inetOrgPerson", "carLicense"});

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      /*
       * Perform add operation with forbidden attribute in RDN
       */
      String entryName = "displayName=ValueToBeKept+description=ValueToBeRemoved," + TEST_ROOT_DN_STRING ;
      String entryLdif = "dn: " + entryName + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "sn: snValue\n" + "cn: cnValue\n" +
        "entryUUID: " + ENTRY_UUID + "\n" +
        "displayName: ValueToBeKept\ndescription: ValueToBeRemoved\n";

      Entry entry = null;
      try
      {
        entry = TestCaseUtils.entryFromLdifString(entryLdif);
      } catch (Exception e)
      {
        fail(e.getMessage());
      }

      // Create an update message to add an entry.
      AddMsg addMsg = new AddMsg(gen.newChangeNumber(),
        entry.getDN().toString(),
        ENTRY_UUID,
        null,
        entry.getObjectClassAttribute(),
        entry.getAttributes(), new ArrayList<Attribute>());

      replicationDomain.publish(addMsg);

      /*
       * check that entry has been created and has attribute values from RDN
       */

      Entry newEntry = null;
      try
      {
        newEntry = getEntry(entry.getDN(), TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(entry.getDN(), newEntry.getDN());
      ObjectClass objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "description", "ValueToBeRemoved");

      /*
       * Perform modify dn operation by renaming the entry keeping only one of
       * the forbidden attributes
       */

      String newEntryName = "displayName=ValueToBeKept," + TEST_ROOT_DN_STRING ;
      DN newEntryDn = null;
      try
      {
        newEntryDn = DN.decode(newEntryName);
      } catch(DirectoryException e)
      {
        fail("Could not get DN from string: " + newEntryName);
      }

      // Create modify dn message to modify the entry.
      ModifyDNMsg modDnMsg = new ModifyDNMsg(entryName, gen.newChangeNumber(),
        ENTRY_UUID, ENTRY_UUID3, false, TEST_ROOT_DN_STRING,
        "displayName=ValueToBeKept", null);

      replicationDomain.publish(modDnMsg);

      /*
       * check that entry has been renamed  and has only attribute left in the
       * new RDN
       */

      try
      {
        newEntry = getEntry(newEntryDn, TIMEOUT, true);
      } catch(Exception e)
      {
        fail("Entry has not been added: " + e.getMessage());
      }
      assertNotNull(newEntry);
      assertEquals(newEntryDn, newEntry.getDN());
      objectClass = DirectoryServer.getObjectClass("inetOrgPerson".toLowerCase());
      assertTrue(newEntry.hasObjectClass(objectClass));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      assertNull(newEntry.getAttribute("description"));
    }
    finally
    {
      endTest();
    }
  }
}
