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
package org.opends.server.changelog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.sleepycat.je.DatabaseException;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.DirectoryThread;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.messages.MessageHandler;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.synchronization.SynchMessages.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;

/**
 * Changelog Listener.
 *
 * This singleton is the main object of the changelog server
 * It waits for the incoming connections and create listener
 * and publisher objects for
 * connection with LDAP servers and with changelog servers
 *
 * It is responsible for creating the changelog cache and managing it
 */
public class Changelog implements Runnable, ConfigurableComponent
{
  static private short serverId;
  static private String serverURL;

  private static ServerSocket listenSocket;
  private static Thread myListenThread;
  private static Thread myConnectThread;

  private static boolean runListen = true;

  /* The list of changelog servers configured by the administrator */
  private List<String> configuredChangelogs;

  /* This table is used to store the list of dn for which we are currently
   * handling servers.
   */
  private static HashMap<DN, ChangelogCache> baseDNs =
          new HashMap<DN, ChangelogCache>();

  private String localhostname = "null";
  private String localURL = "null";
  private static boolean shutdown = false;
  private List<String> changelogServers = null;
  private short changelogServerId;
  private DN configDn;
  private List<ConfigAttribute> configAttributes =
          new ArrayList<ConfigAttribute>();

  static String CHANGELOG_SERVER_ATTR = "ds-cfg-changelog-server";
  static String SERVER_ID_ATTR = "ds-cfg-server-id";
  static String CHANGELOG_PORT_ATTR = "ds-cfg-changelog-port";

  /**
   * Creates a new Changelog using the provided configuration entry.
   *
   * @param config The configuration entry where configuration can be found.
   * @throws ConfigException When Configuration entry is invalid.
   */
  public Changelog(ConfigEntry config) throws ConfigException
  {
    IntegerConfigAttribute changelogPortStub =
      new IntegerConfigAttribute(CHANGELOG_PORT_ATTR, "changelog port",
        true, false, false, true, 0,
        true, 65535);
    IntegerConfigAttribute changelogPortAttr =
      (IntegerConfigAttribute) config.getConfigAttribute(changelogPortStub);
    /* if there is no changelog port configured, this process must not be a
     * changelog server
     */
    if (changelogPortAttr == null)
    {
      throw new ConfigException(MSGID_NEED_CHANGELOG_PORT,
          MessageHandler.getMessage(MSGID_NEED_CHANGELOG_PORT,
              config.getDN().toString())  );
    }
    int changelogPort = changelogPortAttr.activeIntValue();
    configAttributes.add(changelogPortAttr);

    /*
     * read the server Id information
     * this is a single valued integer, its value must fit on a
     * short integer
     */
    IntegerConfigAttribute serverIdStub =
      new IntegerConfigAttribute(SERVER_ID_ATTR, "server ID", true, false,
          false, true, 0, true, 65535);
    IntegerConfigAttribute serverIdAttr =
      (IntegerConfigAttribute) config.getConfigAttribute(serverIdStub);

    if (serverIdAttr == null)
    {
      throw new ConfigException(MSGID_NEED_SERVER_ID,
          MessageHandler.getMessage(MSGID_NEED_SERVER_ID,
              config.getDN().toString())  );
    }
    changelogServerId = (short) serverIdAttr.activeIntValue();
    configAttributes.add(serverIdAttr);

    /*
     * read the centralized changelog server configuration
     * this is a multivalued attribute
     */
    StringConfigAttribute changelogStub =
      new StringConfigAttribute(CHANGELOG_SERVER_ATTR,
          "changelog server information", true,
          true, false);
    StringConfigAttribute changelogServer =
      (StringConfigAttribute) config.getConfigAttribute(changelogStub);
    if (changelogServer == null)
    {
      throw new ConfigException(MSGID_NEED_CHANGELOG_SERVER,
          MessageHandler.getMessage(MSGID_NEED_CHANGELOG_SERVER,
              config.getDN().toString()) );
    }
    changelogServers = changelogServer.activeValues();
    configAttributes.add(changelogServer);

    initialize(changelogServerId, changelogPort, changelogServers);

    configDn = config.getDN();
    DirectoryServer.registerConfigurableComponent(this);
  }

  /**
   * {@inheritDoc}
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configDn;
  }

  /**
   * {@inheritDoc}
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    return configAttributes ;
  }

  /**
   * {@inheritDoc}
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
      List<String> unacceptableReasons)
  {
    // TODO NYI
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
      boolean detailedResults)
  {
    // TODO NYI
    return null;
  }

  /**
   * spawn the listen thread and the connect thread.
   * Used a a workaround because there can be only one run method
   */
  public void run()
  {
    if (runListen)
    {
      runListen = false;
      runListen();
    }
    else
      runConnect();
  }

  /**
   * The run method for the Listen thread.
   * This thread accept incoming connections on the changelog server
   * ports from other changelog servers or from LDAP servers
   * and spawn further thread responsible for handling those connections
   */

  private void runListen()
  {
    Socket newSocket = null;
    while (shutdown == false)
    {
      // Wait on the changelog port.
      // Read incoming messages and create LDAP or Changelog listener and
      // Publisher.

      try
      {
        newSocket =  listenSocket.accept();
        ServerHandler handler = new ServerHandler(
                                     new SocketSession(newSocket));
        handler.start(null);
      } catch (IOException e)
      {
        // ignore
        // TODO add some logging to allow problem debugging
      }
    }
  }

  /**
   * This method manage the connection with the other LDAP servers
   * it periodically that this changelog server is correctly connected
   * to all the other changelog servers and if not attempt to
   * do the connection.
   */
  private void runConnect()
  {
    while (shutdown == false)
    {
      /*
       * periodically check that we are connected to all other
       * changelog servers and if not establish the connection
       */
      for (ChangelogCache changelogCache: baseDNs.values())
      {
        Set<String> connectedChangelogs = changelogCache.getChangelogs();
        /*
         * check that all changelog in the config are in the connected Set
         * if not create the connection
         */
        for (String serverURL : configuredChangelogs)
        {
          if ((serverURL.compareTo(localURL) != 0) &&
              (!connectedChangelogs.contains(serverURL)))
          {
            this.connect(serverURL, changelogCache.getBaseDn());
          }
        }
      }
      try
      {
        synchronized (this)
        {
          /* check if we are connected every second */
          wait(1000);
        }
      } catch (InterruptedException e)
      {
        // ignore error, will try to connect again or shutdown
      }
    }
  }

  /**
   * Establish a connection to the server with the address and port.
   *
   * @param serverURL  The address and port for the server, separated by a
   *                    colon.
   * @param baseDn     The baseDn of the connection
   */
  private void connect(String serverURL, DN baseDn)
  {
    String token[] = serverURL.split(":");
    String hostname = token[0];
    String port = token[1];

    try
    {
      InetSocketAddress ServerAddr = new InetSocketAddress(
                     InetAddress.getByName(hostname), Integer.parseInt(port));
      Socket socket = new Socket();
      socket.connect(ServerAddr, 500);

      ServerHandler handler = new ServerHandler(
                                      new SocketSession(socket));
      handler.start(baseDn);
    }
    catch (IOException e)
    {
      // ignore
    }

  }

  /**
   * initialization function for the changelog.
   *
   * @param  changelogId       The unique identifier for this changelog.
   * @param  changelogPort     The port on which the changelog should listen.
   * @param  changelogServers  The set of changelog servers that have been
   *                           defined.
   *
   */
  private void initialize(short changelogId, int changelogPort,
                         List<String> changelogServers)
  {
    try
    {
      ChangelogDB.initialize("changelogDb");
      /*
       * create changelog cache
       */
      configuredChangelogs = changelogServers;
      serverId = changelogId;

      /*
       * Open changelog socket
       */
      localhostname = InetAddress.getLocalHost().getHostName();
      serverURL = localhostname + ":" + String.valueOf(changelogPort);
      localURL = localhostname + ":" + String.valueOf(changelogPort);
      listenSocket = new ServerSocket(changelogPort);

      /*
       * create working threads
       */
      myListenThread = new DirectoryThread(this, "Changelog Listener");
      myListenThread.start();
      myConnectThread = new DirectoryThread(this, "Changelog Connect");
      myConnectThread.start();

    } catch (DatabaseException e)
    {
      int msgID = MSGID_COULD_NOT_INITIALIZE_DB;
      String message = getMessage(msgID, "changelogDb");
      logError(ErrorLogCategory.SYNCHRONIZATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    } catch (ChangelogDBException e)
    {
      int msgID = MSGID_COULD_NOT_READ_DB;
      String message = getMessage(msgID, "changelogDb");
      message += getMessage(e.getMessageID());
      logError(ErrorLogCategory.SYNCHRONIZATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    } catch (UnknownHostException e)
    {
      int msgID = MSGID_UNKNOWN_HOSTNAME;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.SYNCHRONIZATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    } catch (IOException e)
    {
      int msgID = MSGID_COULD_NOT_BIND_CHANGELOG;
      String message = getMessage(msgID, changelogPort, e.getMessage());
      logError(ErrorLogCategory.SYNCHRONIZATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }
  }

  /**
   * Retrieves the unique identifier for this changelog.
   *
   * @return  The unique identifier for this changelog.
   */
  public static short getServerId()
  {
    return serverId;
  }

  /**
   * Retrieves the host and port for this changelog, separated by a colon.
   *
   * @return  The host and port for this changelog, separated by a colon.
   */
  public static String getServerURL()
  {
    return serverURL;
  }

  /**
   * Get the ChangelogCache associated to the base DN given in parameter.
   *
   * @param baseDn The base Dn for which the ChangelogCache must be returned.
   * @return The ChangelogCache associated to the base DN given in parameter.
   */
  public static ChangelogCache getChangelogCache(DN baseDn)
  {
    ChangelogCache changelogCache;

    synchronized (baseDNs)
    {
      changelogCache = baseDNs.get(baseDn);
      if (changelogCache == null)
        changelogCache = new ChangelogCache(baseDn);
      baseDNs.put(baseDn, changelogCache);
    }

    return changelogCache;
  }

  /**
   * Shutdown the Changelog service and all its connections.
   */
  public static void shutdown()
  {
    shutdown = true;

    // shutdown the connect thread
    myConnectThread.interrupt();

    // shutdown the listener thread
    try
    {
      listenSocket.close();
    } catch (IOException e)
    {
      // changelog service is closing anyway.
    }

    // shutdown all the ChangelogCaches
    for (ChangelogCache changelogCache : baseDNs.values())
    {
      changelogCache.shutdown();
    }

    ChangelogDB.shutdownDbEnvironment();
  }
}
