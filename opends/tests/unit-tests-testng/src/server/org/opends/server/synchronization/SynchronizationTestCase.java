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
package org.opends.server.synchronization;

import static org.opends.server.loggers.Error.logError;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.schema.IntegerSyntax;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.synchronization.common.ServerState;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.plugin.MultimasterSynchronization;
import org.opends.server.synchronization.plugin.PersistentServerState;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.AttributeType;
import org.opends.server.types.LockManager;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
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
    schemaCheck = DirectoryServer.checkSchema();

    // Create an internal connection
    connection = InternalClientConnection.getRootConnection();
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
    ServerState state;
    if (emptyOldChanges)
       state = new PersistentServerState(baseDn);
    else
       state = new ServerState();

    ChangelogBroker broker = new ChangelogBroker(
        state, baseDn, serverId, 0, 0, 0, 0, window_size, 0);
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + port);
    broker.start(servers);
    if (timeout != 0)
      broker.setSoTimeout(timeout);
    TestCaseUtils.sleep(100); // give some time to the broker to connect
                              // to the changelog server.
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
      { 
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.NOTICE,
            "SynchronizationTestCase/openChangelogSession" + e.getMessage(), 1);
      }
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
        state, baseDn, serverId, 0, 0, 0, 0, window_size, 0);
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:" + port);
    broker.start(servers);
    if (timeout != 0)
      broker.setSoTimeout(timeout);

    return broker;
  }

  /**
   * Open a changelog session with flow control to the local Changelog server.
   *
   */
  protected ChangelogBroker openChangelogSession(
      final DN baseDn, short serverId, int window_size,
      int port, int timeout, int maxSendQueue, int maxRcvQueue,
      boolean emptyOldChanges)
          throws Exception, SocketException
  {
    ServerState state;
    if (emptyOldChanges)
       state = new PersistentServerState(baseDn);
    else
       state = new ServerState();

    ChangelogBroker broker = new ChangelogBroker(
        state, baseDn, serverId, maxRcvQueue, 0,
        maxSendQueue, 0, window_size, 0);
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
      { 
      }
    }
    return broker;
  }

  /**
   * suppress all the entries created by the tests in this class
   */
  protected void cleanEntries()
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "SynchronizationTestCase/Cleaning entries" , 1);

    DeleteOperation op;
    // Delete entries
    try
    {
      while (true)
      {
        DN dn = entryList.removeLast();
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.NOTICE,
            "cleaning entry " + dn, 1);

        op = new DeleteOperation(connection, InternalClientConnection
            .nextOperationID(), InternalClientConnection.nextMessageID(), null,
            dn);

        op.run();
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
      logError(ErrorLogCategory.SYNCHRONIZATION,
          ErrorLogSeverity.NOTICE,
          "SynchronizationTestCase/FinalizeSynchronization Provider" , 1);

      DirectoryServer.deregisterSynchronizationProvider(mms);
      mms.finalizeSynchronizationProvider();
      mms = null;
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
    if (changeLogEntry!=null)
    {
      DirectoryServer.getConfigHandler().addEntry(changeLogEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(changeLogEntry.getDN()),
        "Unable to add the changeLog server");
      entryList.add(changeLogEntry.getDN());
    }
    
    if (synchroServerEntry!=null)
    {
      // We also have a replicated suffix (synchronization domain)
      DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
      assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
      "Unable to add the synchronized suffix");
      entryList.add(synchroServerEntry.getDN());
    }
  }

  /**
   * Retrieve the number of replayed updates for a given synchronization
   * domain from the monitor entry.
   * @return The number of replayed updates.
   * @throws Exception If an error occurs.
   */
  protected long getReplayedUpdatesCount(DN syncDN) throws Exception
  {
    String monitorFilter =
         "(&(cn=synchronization*)(base-dn=" + syncDN + "))";

    InternalSearchOperation op;
    op = connection.processSearch(
         ByteStringFactory.create("cn=monitor"),
         SearchScope.SINGLE_LEVEL,
         LDAPFilter.decode(monitorFilter));
    SearchResultEntry entry = op.getSearchEntries().getFirst();

    AttributeType attrType =
         DirectoryServer.getDefaultAttributeType("replayed-updates");
    return entry.getAttributeValue(attrType, IntegerSyntax.DECODER).longValue();
  }

  /**
   * Check that the entry with the given dn has the given valueString value
   * for the given attrTypeStr attribute type.
   */
  protected boolean checkEntryHasAttribute(DN dn, String attrTypeStr,
      String valueString, int timeout, boolean hasAttribute) throws Exception
  {
    boolean found;
    int count = timeout/100;
    if (count<1)
      count=1;

    do
    {
      Entry newEntry;
      Lock lock = null;
      for (int j=0; j < 3; j++)
      {
        lock = LockManager.lockRead(dn);
        if (lock != null)
        {
          break;
        }
      }

      if (lock == null)
      {
        throw new Exception("could not lock entry " + dn);
      }

      try
      {
        newEntry = DirectoryServer.getEntry(dn);


        if (newEntry == null)
          fail("The entry " + dn +
          " has incorrectly been deleted from the database.");
        List<Attribute> tmpAttrList = newEntry.getAttribute(attrTypeStr);
        Attribute tmpAttr = tmpAttrList.get(0);

        AttributeType attrType =
          DirectoryServer.getAttributeType(attrTypeStr, true);
        found = tmpAttr.hasValue(new AttributeValue(attrType, valueString));

      }
      finally
      {
        LockManager.unlock(dn, lock);
      }

      if (found != hasAttribute)
        Thread.sleep(100);
    } while ((--count > 0) && (found != hasAttribute));
    return found;
  }

  /**
   * Retrieves an entry from the local Directory Server.
   * @throws Exception When the entry cannot be locked.
   */
  protected Entry getEntry(DN dn, int timeout, boolean exist) throws Exception
  {
    int count = timeout/200;
    if (count<1)
      count=1;
    Thread.sleep(50);
    boolean found = DirectoryServer.entryExists(dn);
    while ((count> 0) && (found != exist))
    {
      Thread.sleep(200);

      found = DirectoryServer.entryExists(dn);
      count--;
    }

    Lock lock = null;
    for (int i=0; i < 3; i++)
    {
      lock = LockManager.lockRead(dn);
      if (lock != null)
      {
        break;
      }
    }

    if (lock == null)
    {
      throw new Exception("could not lock entry " + dn);
    }

    try
    {
      Entry entry = DirectoryServer.getEntry(dn);
      if (entry == null)
        return null;
      else
        return entry.duplicate(true);
    }
    finally
    {
      LockManager.unlock(dn, lock);
    }
  }

}
