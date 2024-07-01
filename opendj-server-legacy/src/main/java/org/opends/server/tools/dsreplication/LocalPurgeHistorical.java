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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools.dsreplication;

import static org.forgerock.opendj.server.embedded.ConfigParameters.configParams;
import static org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer.*;

import static org.opends.messages.AdminToolMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.TimeThread;
import org.opends.server.util.cli.PointAdder;

import com.forgerock.opendj.cli.ConsoleApplication;

/**
 * The class that is in charge of taking the different information provided
 * by the user through the command-line and actually executing the local
 * purge.
 *
 */
public class LocalPurgeHistorical
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final PurgeHistoricalUserData uData;
  private final ConsoleApplication app;
  private final ProgressMessageFormatter formatter;
  private final String configFile;

  /**
   * The default constructor.
   * @param uData the object containing the information provided by the user.
   * @param app the console application that is used to write the progress
   * and error messages.
   * @param formatter the formatter to be used to generated the messages.
   * @param configFile the file that contains the configuration.  This is
   * required to initialize properly the server.
   */
  public LocalPurgeHistorical(PurgeHistoricalUserData uData,
      ConsoleApplication app,
      ProgressMessageFormatter formatter, String configFile)
  {
    this.uData = uData;
    this.app = app;
    this.formatter = formatter;
    this.configFile = configFile;
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

    app.print(formatter.getFormattedProgress(
        INFO_REPLICATION_PURGE_HISTORICAL_LOCAL_ENVIRONMENT.get()));

    PointAdder pointAdder = new PointAdder(app);
    pointAdder.start();

    try
    {
      EmbeddedDirectoryServer server = manageEmbeddedDirectoryServerForRestrictedOps(
          configParams()
            .configurationFile(configFile)
            .disableConnectionHandlers(true));
      server.start();
    }
    catch (OpenDsException ode)
    {
      pointAdder.stop();
      app.println(ode.getMessageObject());
      logger.error(LocalizableMessage.raw("Error starting server with file "+configFile+
          ": "+ode, ode));
      return ReplicationCliReturnCode.ERROR_LOCAL_PURGE_HISTORICAL_SERVER_START;
    }
    pointAdder.stop();
    app.print(formatter.getFormattedDone());
    app.println();
    app.println();
    app.print(formatter.getFormattedProgress(
        INFO_REPLICATION_PURGE_HISTORICAL_LOCAL_STARTING.get()));
    app.println();

    if (applyTimeout && timeoutOccurred(endMaxTime))
    {
      return handleTimeout();
    }

    try
    {
      // launch the job
      for (DN baseDN : uData.getBaseDNs())
      {
        // We can assume that this is an LDAP replication domain
        LDAPReplicationDomain domain = LDAPReplicationDomain.retrievesReplicationDomain(baseDN);
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
    logger.error(LocalizableMessage.raw("Error executing purge historical: "+ode, ode));
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
