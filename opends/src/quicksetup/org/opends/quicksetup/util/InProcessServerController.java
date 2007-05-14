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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;

import org.opends.quicksetup.*;
import org.opends.server.loggers.debug.TextDebugLogPublisher;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.TextAccessLogPublisher;
import org.opends.server.loggers.AccessLogger;
import org.opends.server.types.DN;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.api.AccessLogPublisher;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Class used to manipulate an OpenDS server in the same JVM process as
 * the client class.
 */
public class InProcessServerController {

  static private final Logger LOG =
          Logger.getLogger(InProcessServerController.class.getName());

  private Installation installation;

  /**
   * Creates a new instance that will operate on <code>application</code>'s
   * installation.
   * @param installation representing the server instance to control
   */
  public InProcessServerController(Installation installation) {
    this.installation = installation;
  }

  /**
   * Starts the directory server within this process.
   * @param disableConnectionHandlers boolean that when true starts the
   * the server mode that is otherwise up and running but will not accept any
   * connections from external clients (i.e., does not create or initialize the
   * connection handlers). This could be useful, for example, in an upgrade mode
   * where it might be helpful to start the server but don't want it to appear
   * externally as if the server is online without connection handlers
   * listening.
   * @return OperationOutput object containing output from the start server
   * command invocation.
   * @throws org.opends.server.config.ConfigException
   *         If there is a problem with the Directory Server
   *         configuration that prevents a critical component
   *         from being instantiated.
   *
   * @throws org.opends.server.types.InitializationException
   *         If some other problem occurs while
   *         attempting to initialize and start the
   *         Directory Server.
   */
  public OperationOutput startServer(boolean disableConnectionHandlers)
          throws
          org.opends.server.types.InitializationException,
          org.opends.server.config.ConfigException {
    LOG.log(Level.INFO, "Starting in process server with connection handlers " +
            (disableConnectionHandlers ? "disabled" : "enabled"));
    System.setProperty(
            "org.opends.server.DisableConnectionHandlers",
            disableConnectionHandlers ? "true" : null);
    return startServer();
  }

  /**
   * Stops a server that had been running 'in process'.
   */
  public void stopServer() {
    LOG.log(Level.INFO, "Shutting down in process server");
    org.opends.server.core.DirectoryServer.shutDown(
            ServerController.class.getName(),
            "quicksetup requests shutdown");
  }

  /**
   * Starts the OpenDS server in this process.
   * @return OperationOutput with the results of the operation.
   * @throws org.opends.server.config.ConfigException
   *  If there is a problem with the Directory Server
   *  configuration that prevents a critical component
   *  from being instantiated.
   *
   * @throws org.opends.server.types.InitializationException
   *  If some other problem occurs while
   *  attempting to initialize and start the
   *  Directory Server.
   */
  public OperationOutput startServer()
          throws
          org.opends.server.types.InitializationException,
          org.opends.server.config.ConfigException
  {
    OperationOutput output = new OperationOutput();

    // Bootstrap and start the Directory Server.
    LOG.log(Level.FINER, "Bootstrapping directory server");
    org.opends.server.core.DirectoryServer directoryServer =
            org.opends.server.core.DirectoryServer.getInstance();

    directoryServer.bootstrapServer();
    String configClass = "org.opends.server.extensions.ConfigFileHandler";
    String configPath = Utils.getPath(
            installation.getCurrentConfigurationFile());
    directoryServer.initializeConfiguration(configClass, configPath);

    try {

      DebugLogPublisher startupDebugPublisher =
              TextDebugLogPublisher.getStartupTextDebugPublisher(
                      new ServerControllerTextWriter(output) {
                        void storeRecord(String record, OperationOutput output)
                        {
                          output.addDebugMessage(record);
                        }
                      });
      DebugLogger.removeAllDebugLogPublishers();
      DebugLogger.addDebugLogPublisher(DN.NULL_DN,
              startupDebugPublisher);

      ErrorLogPublisher startupErrorPublisher =
              TextErrorLogPublisher.getStartupTextErrorPublisher(
                      new ServerControllerTextWriter(output) {
                        void storeRecord(String record, OperationOutput output)
                        {
                          output.addErrorMessage(record);
                        }
                      });

      ErrorLogger.removeAllErrorLogPublishers();
      ErrorLogger.addErrorLogPublisher(DN.NULL_DN,
              startupErrorPublisher);

      AccessLogPublisher startupAccessPublisher =
              TextAccessLogPublisher.getStartupTextAccessPublisher(
                      new ServerControllerTextWriter(output) {
                        void storeRecord(String record, OperationOutput output)
                        {
                          output.addAccessMessage(record);
                        }
                      });

      AccessLogger.removeAllAccessLogPublishers();
      AccessLogger.addAccessLogPublisher(DN.NULL_DN,
              startupAccessPublisher);

    } catch (Exception e) {
      LOG.log(Level.INFO, "Error installing test log publishers: " +
              e.toString());
    }

    LOG.log(Level.FINER, "Invoking start server");
    directoryServer.startServer();

    return output;
  }

  /**
   * Pushes messages published by the server loggers into OperationOutput.
   */
  private abstract class ServerControllerTextWriter implements TextWriter {

    private int bytesWritten = 0;
    private OperationOutput output = null;

    abstract void storeRecord(String record, OperationOutput output);

    ServerControllerTextWriter(OperationOutput output) {
      this.output = output;
    }

    public void writeRecord(String record) {
      if (record != null) {
        bytesWritten += bytesWritten;
        storeRecord(record, output);
      }
    }

    public void flush() {
      // do nothing;
    }

    public void shutdown() {
      // do nothing;
    }

    public long getBytesWritten() {
      return bytesWritten;
    }
  }

}
