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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012 ForgeRock AS
 */
package org.opends.server.tools.dsreplication;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.CoreMessages.*;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.messages.Message;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.PointAdder;

/**
 * The class that is in charge of taking the different information provided
 * by the user through the command-line and actually executing the local
 * purge.
 *
 */
public class LocalPurgeHistorical
{
  private static final Logger LOG =
    Logger.getLogger(LocalPurgeHistorical.class.getName());

  private final PurgeHistoricalUserData uData;
  private final ConsoleApplication app;
  private final ProgressMessageFormatter formatter;
  private final String configFile;
  private final String configClass;

  /**
   * The default constructor.
   * @param uData the object containing the information provided by the user.
   * @param app the console application that is used to write the progress
   * and error messages.
   * @param formatter the formatter to be used to generated the messages.
   * @param configFile the file that contains the configuration.  This is
   * required to initialize properly the server.
   * @param configClass the class to be used to read the configuration.  This is
   * required to initialize properly the server.
   */
  public LocalPurgeHistorical(PurgeHistoricalUserData uData,
      ConsoleApplication app,
      ProgressMessageFormatter formatter, String configFile,
      String configClass)
  {
    this.uData = uData;
    this.app = app;
    this.formatter = formatter;
    this.configFile = configFile;
    this.configClass = configClass;
  }

  /**
   * Executes the purge historical operation locally.
   * @return the result code.
   */
  public ReplicationCliReturnCode execute()
  {
    boolean applyTimeout = uData.getMaximumDuration() > 0;

    long startTime = TimeThread.getTime();
    long purgeMaxTime = getTimeoutInSeconds() * 1000L;

    long endMaxTime = startTime + purgeMaxTime;

    app.printProgress(formatter.getFormattedProgress(
        INFO_REPLICATION_PURGE_HISTORICAL_LOCAL_ENVIRONMENT.get()));

    PointAdder pointAdder = new PointAdder(app);
    pointAdder.start();

    Class<?> cfgClass;

    try
    {
      cfgClass = Class.forName(configClass);
    }
    catch (Exception e)
    {
      pointAdder.stop();
      Message message =
        ERR_CANNOT_LOAD_CONFIG_HANDLER_CLASS.get(
            configClass, StaticUtils.stackTraceToSingleLineString(e));
      app.println(message);
      LOG.log(Level.SEVERE, "Error loading configuration class "+configClass+
          ": "+e, e);
      return ReplicationCliReturnCode.ERROR_LOCAL_PURGE_HISTORICAL_CLASS_LOAD;
    }

    try
    {
      // Create a configuration for the server.
      DirectoryEnvironmentConfig environmentConfig =
        new DirectoryEnvironmentConfig();
      environmentConfig.setConfigClass(cfgClass);
      environmentConfig.setConfigFile(new File(configFile));
      environmentConfig.setDisableConnectionHandlers(true);
      EmbeddedUtils.startServer(environmentConfig);
    }
    catch (OpenDsException ode)
    {
      pointAdder.stop();
      Message message = ode.getMessageObject();
        ERR_CANNOT_LOAD_CONFIG_HANDLER_CLASS.get(
            configClass, StaticUtils.stackTraceToSingleLineString(ode));
      app.println(message);
      LOG.log(Level.SEVERE, "Error starting server with file "+configFile+
          ": "+ode, ode);
      return ReplicationCliReturnCode.ERROR_LOCAL_PURGE_HISTORICAL_SERVER_START;
    }
    pointAdder.stop();
    app.printProgress(formatter.getFormattedDone());
    app.printlnProgress();
    app.printlnProgress();
    app.printProgress(formatter.getFormattedProgress(
        INFO_REPLICATION_PURGE_HISTORICAL_LOCAL_STARTING.get()));
    app.printlnProgress();

    if (applyTimeout && timeoutOccurred(endMaxTime))
    {
      return handleTimeout();
    }

    try
    {
      // launch the job
      for (String baseDN : uData.getBaseDNs())
      {
        DN dn = DN.decode(baseDN);
        // We can assume that this is an LDAP replication domain
        LDAPReplicationDomain domain =
            LDAPReplicationDomain.retrievesReplicationDomain(dn);

        domain.purgeConflictsHistorical(null, startTime + purgeMaxTime);
      }

    }
    catch (DirectoryException de)
    {
      if (de.getResultCode() == ResultCode.ADMIN_LIMIT_EXCEEDED)
      {
        return handleTimeout();
      }
      else
      {
        return handleGenericExecuting(de);
      }
    }
    return ReplicationCliReturnCode.SUCCESSFUL;
  }

  private ReplicationCliReturnCode handleGenericExecuting(OpenDsException ode)
  {
    LOG.log(Level.SEVERE, "Error executing purge historical: "+ode, ode);
    app.println();
    app.println(ERR_REPLICATION_PURGE_HISTORICAL_EXECUTING.get(
        ode.getMessageObject()));
    return ReplicationCliReturnCode.ERROR_LOCAL_PURGE_HISTORICAL_EXECUTING;
  }

  private ReplicationCliReturnCode handleTimeout()
  {
    app.println();
    app.println(ERR_REPLICATION_PURGE_HISTORICAL_TIMEOUT.get(
        getTimeoutInSeconds()));
    return ReplicationCliReturnCode.ERROR_LOCAL_PURGE_HISTORICAL_TIMEOUT;
  }

  /**
   * Returns the time-out provided by the user in seconds.
   * @return the time-out provided by the user in seconds.
   */
  private int getTimeoutInSeconds()
  {
    return uData.getMaximumDuration();
  }

  /**
   * A method that tells whether the maximum time to execute the operation was
   * elapsed or not.
   * @param endMaxTime the latest time in milliseconds when the operation should
   * be completed.
   * @return {@code true} if the time-out occurred and {@code false} otherwise.
   */
  private boolean timeoutOccurred(long endMaxTime)
  {
    return TimeThread.getTime() > endMaxTime;
  }
}
