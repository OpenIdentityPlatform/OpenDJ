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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.synchronization.common.ServerState;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.plugin.MultimasterSynchronization;
import org.opends.server.synchronization.plugin.PersistentServerState;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import org.testng.annotations.BeforeClass;

/**
 * An abstract class that all synchronization unit test should extend.
 */
@Test(groups = { "precommit", "synchronization" })
public abstract class SynchronizationTestCase extends DirectoryServerTestCase
{

  /**
  * The internal connection used for operation
  */
  protected InternalClientConnection connection;

  /**
   * Created entries that need to be deleted for cleanup
   */
  protected LinkedList<DN> entryList = new LinkedList<DN>();

  protected Entry synchroPluginEntry;

  protected Entry synchroServerEntry;

  protected Entry changeLogEntry;

  /**
   * schema check flag
   */
  protected boolean schemaCheck;

  /**
   *
   */
  MultimasterSynchronization mms = null;

  /**
   * The synchronization plugin entry
   */
  protected String synchroPluginStringDN;

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *         If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();

    // Create an internal connection
    connection = new InternalClientConnection();
  }

  /**
   * Open a changelog session to the local Changelog server.
   *
   */
  protected ChangelogBroker openChangelogSession(
      final DN baseDn, short serverId, int window_size,
      int port, int timeout, boolean emptyOldChanges)
          throws Exception, SocketException
  {
    PersistentServerState state = new PersistentServerState(baseDn);
    if (emptyOldChanges)
      state.loadState();
    ChangelogBroker broker = new ChangelogBroker(
        state, baseDn, serverId, 0, 0, 0, 0, window_size);
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + port);
    broker.start(servers);
    if (timeout != 0)
      broker.setSoTimeout(timeout);
    if (emptyOldChanges)
    {
      /*
       * loop receiving update until there is nothing left
       * to make sure that message from previous tests have been consumed.
       */
      try
      {
        while (true)
        {
          broker.receive();
        }
      }
      catch (Exception e)
      { }
    }
    return broker;
  }
  
  /**
   * Open a new session to the Changelog Server 
   * starting with a given ServerState.
   */
  protected ChangelogBroker openChangelogSession(
      final DN baseDn, short serverId, int window_size,
      int port, int timeout, ServerState state)
          throws Exception, SocketException
  {
    ChangelogBroker broker = new ChangelogBroker(
        state, baseDn, serverId, 0, 0, 0, 0, window_size);
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + port);
    broker.start(servers);
    if (timeout != 0)
      broker.setSoTimeout(timeout);
    
    return broker;
  }

  /**
   * suppress all the entries created by the tests in this class
   */
  protected void cleanEntries()
  {
    DeleteOperation op;
    // Delete entries
    try
    {
      while (true)
      {
        DN dn = entryList.removeLast();
        op = new DeleteOperation(connection, InternalClientConnection
            .nextOperationID(), InternalClientConnection.nextMessageID(), null,
            dn);

        op.run();;
      }
    }
    catch (NoSuchElementException e) {
      // done
    }
  }

  /**
   * Clean up the environment. return null;
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @AfterClass
  public void classCleanUp() throws Exception
  {
    DirectoryServer.setCheckSchema(schemaCheck);

    // WORKAROUND FOR BUG #639 - BEGIN -
    if (mms != null)
    {
      DirectoryServer.deregisterSynchronizationProvider(mms);
      mms.finalizeSynchronizationProvider();
    }
    // WORKAROUND FOR BUG #639 - END -

    cleanEntries();
  }

  /**
   * Configure the Synchronization for this test.
   */
  protected void configureSynchronization() throws Exception
  {
    //
    // Add the Multimaster synchronization plugin
    DirectoryServer.getConfigHandler().addEntry(synchroPluginEntry, null);
    entryList.add(synchroPluginEntry.getDN());
    assertNotNull(DirectoryServer.getConfigEntry(DN
        .decode(synchroPluginStringDN)),
        "Unable to add the Multimaster synchronization plugin");

    // WORKAROUND FOR BUG #639 - BEGIN -
    DN dn = DN.decode(synchroPluginStringDN);
    ConfigEntry mmsConfigEntry = DirectoryServer.getConfigEntry(dn);
    mms = new MultimasterSynchronization();
    try
    {
      mms.initializeSynchronizationProvider(mmsConfigEntry);
    }
    catch (ConfigException e)
    {
      assertTrue(false,
          "Unable to initialize the Multimaster synchronization plugin");
    }
    DirectoryServer.registerSynchronizationProvider(mms);
    // WORKAROUND FOR BUG #639 - END -

    //
    // Add the changelog server
    DirectoryServer.getConfigHandler().addEntry(changeLogEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(changeLogEntry.getDN()),
        "Unable to add the changeLog server");
    entryList.add(changeLogEntry.getDN());

    //
    // We also have a replicated suffix (synchronization domain)
    DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
    entryList.add(synchroServerEntry.getDN());
  }

}
