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
import org.opends.server.types.Modification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.ByteString;
import org.opends.server.api.DebugLogPublisher;
import org.opends.server.api.ErrorLogPublisher;
import org.opends.server.api.AccessLogPublisher;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.DirectoryServer;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.io.File;
import java.io.IOException;

/**
 * Class used to manipulate an OpenDS server in the same JVM process as
 * the client class.
 *
 * NOTE:  this class imports classes from the server packages.  You should
 * refer to the class using a fully qualified package name and insure that
 * that this class does not appear in any import statements.
 */
public class InProcessServerController {

  static private final Logger LOG =
          Logger.getLogger(InProcessServerController.class.getName());

  private Installation installation;

  /**
   * Creates a new instance that will operate on <code>application</code>'s
   * installation.
   * @param installation representing the server instance to control
   * @throws IllegalStateException if the the version of the OpenDS code
   * running in this JVM is not the same version as the code whose bits
   * are stored in <code>installation</code>.
   */
  public InProcessServerController(Installation installation)
          throws IllegalStateException
  {

    // Attempting to use DirectoryServer with a configuration file
    // for a different version of the server can cause problems for
    // the server at startup.
    BuildInformation installBi = null;
    BuildInformation currentBi = null;
    try {
      installBi = installation.getBuildInformation();
      currentBi = BuildInformation.getCurrent();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to verify the build version of " +
              "the " + installation + " matches the currently executing " +
              "version.");
    }

    if (!currentBi.equals(installBi)) {
      throw new IllegalStateException("The build version of the " +
              "installation " + installation + " is " + installBi +
              " and does not match the currently executing version " +
              currentBi);
    }

    this.installation=installation;
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
            disableConnectionHandlers ? "true" : "false");
    return startServer();
  }

  /**
   * Stops a server that had been running 'in process'.
   */
  public void stopServer() {
    LOG.log(Level.INFO, "Shutting down in process server");
    StandardOutputSuppressor.suppress();
    try {
      org.opends.server.core.DirectoryServer.shutDown(
              ServerController.class.getName(),
              "quicksetup requests shutdown");
    } finally {
      StandardOutputSuppressor.unsuppress();
    }
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
          org.opends.server.config.ConfigException {
    OperationOutput output = new OperationOutput();

    StandardOutputSuppressor.suppress();

    try {

      org.opends.server.core.DirectoryServer directoryServer =
              org.opends.server.core.DirectoryServer.getInstance();

      // Bootstrap and start the Directory Server.
      LOG.log(Level.FINER, "Bootstrapping directory server");
      directoryServer.bootstrapServer();

      LOG.log(Level.FINER, "Initializing configuration");
      String configClass = "org.opends.server.extensions.ConfigFileHandler";
      String configPath = Utils.getPath(
              installation.getCurrentConfigurationFile());
      directoryServer.initializeConfiguration(configClass, configPath);

      try {

        DebugLogPublisher startupDebugPublisher =
                TextDebugLogPublisher.getStartupTextDebugPublisher(
                        new ServerControllerTextWriter(output) {
                          void storeRecord(String record,
                                           OperationOutput output) {
                            LOG.log(Level.INFO, "server start (debug log): " +
                                    record);
                            output.addDebugMessage(record);
                          }
                        });
        DebugLogger.addDebugLogPublisher(DN.NULL_DN,
                startupDebugPublisher);

        ErrorLogPublisher startupErrorPublisher =
                TextErrorLogPublisher.getStartupTextErrorPublisher(
                        new ServerControllerTextWriter(output) {
                          void storeRecord(String record,
                                           OperationOutput output) {
                            LOG.log(Level.INFO, "server start (error log): " +
                                    record);
                            output.addErrorMessage(record);
                          }
                        });
        ErrorLogger.addErrorLogPublisher(DN.NULL_DN,
                startupErrorPublisher);

        AccessLogPublisher startupAccessPublisher =
                TextAccessLogPublisher.getStartupTextAccessPublisher(
                        new ServerControllerTextWriter(output) {
                          void storeRecord(String record,
                                           OperationOutput output) {
                            LOG.log(Level.INFO, "server start (access log): " +
                                    record);
                            output.addAccessMessage(record);
                          }
                        });
        AccessLogger.addAccessLogPublisher(DN.NULL_DN,
                startupAccessPublisher);

      } catch (Exception e) {
        LOG.log(Level.INFO, "Error installing test log publishers: " +
                e.toString());
      }

      LOG.log(Level.FINER, "Invoking start server");
      directoryServer.startServer();

    } finally {
      StandardOutputSuppressor.unsuppress();
    }

    return output;
  }

  /**
   * Applies configuration or schema customizations.
   * NOTE: Assumes that the server is running in process.
   *
   * @param ldifFile LDIF file to apply
   * @throws IOException if there is an IO Error
   * @throws LDIFException if there is an LDIF error
   * @throws ApplicationException if there is an application specific error
   */
  public void modify(File ldifFile)
          throws IOException, LDIFException,
          ApplicationException {
    try {
      InternalClientConnection cc =
              InternalClientConnection.getRootConnection();
      LDIFImportConfig importCfg =
              new LDIFImportConfig(
                      Utils.getPath(ldifFile));
      LDIFReader ldifReader =
              new LDIFReader(importCfg);
      org.opends.server.util.ChangeRecordEntry cre;
      while (null != (cre = ldifReader.readChangeRecord(false))) {
        if (cre instanceof org.opends.server.util.ModifyChangeRecordEntry) {
          ModifyChangeRecordEntry mcre =
                  (ModifyChangeRecordEntry) cre;
          ByteString dnByteString =
                  ByteStringFactory.create(
                          mcre.getDN().toString());
          ModifyOperation op =
                  cc.processModify(dnByteString, mcre.getModifications());
          ResultCode rc = op.getResultCode();
          if (rc.equals(
                  ResultCode.
                          OBJECTCLASS_VIOLATION)) {
            // try again without schema checking
            DirectoryServer.setCheckSchema(false);
            op = cc.processModify(dnByteString, mcre.getModifications());
            rc = op.getResultCode();
          }
          if (rc.equals(ResultCode.
                  SUCCESS)) {
            LOG.log(Level.INFO, "processed server modification " +
                    (DirectoryServer.checkSchema() ?
                            ":" : "(schema checking off):" +
                            modListToString(op.getModifications())));
            if (!DirectoryServer.checkSchema()) {
              DirectoryServer.setCheckSchema(true);
            }
          } else if (rc.equals(
                  ResultCode.
                          ATTRIBUTE_OR_VALUE_EXISTS)) {
            // ignore this error
            LOG.log(Level.INFO, "ignoring attribute that already exists: " +
                    modListToString(op.getModifications()));
          } else {
            // report the error to the user
            StringBuilder error = op.getErrorMessage();
            if (error != null) {
              throw new ApplicationException(
                      ApplicationException.Type.IMPORT_ERROR,
                      "error processing custom configuration "
                              + error.toString(),
                      null);
            }
          }
        } else {
          throw new ApplicationException(
                  ApplicationException.Type.IMPORT_ERROR,
                  "unexpected change record type " + cre.getClass(),
                  null);
        }
      }
    } catch (Throwable t) {
      throw new ApplicationException(ApplicationException.Type.BUG,
              t.getMessage(), t);
    }
  }

  private String modListToString(
          List<Modification> modifications) {
    StringBuilder modsMsg = new StringBuilder();
    for (int i = 0; i < modifications.size(); i++) {
      modsMsg.append(modifications.get(i).toString());
      if (i < modifications.size() - 1) {
        modsMsg.append(" ");
      }
    }
    return modsMsg.toString();
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
