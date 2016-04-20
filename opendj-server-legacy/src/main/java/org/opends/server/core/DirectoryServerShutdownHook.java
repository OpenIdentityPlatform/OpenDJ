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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;


import org.opends.server.api.DirectoryThread;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.CoreMessages.*;
/**
 * This class defines a shutdown hook that will be invoked automatically when
 * the JVM is shutting down.  It may be able to detect certain kinds of shutdown
 * events that are not invoked by the Directory Server itself (e.g., an
 * administrator killing the Java process).
 */
public class DirectoryServerShutdownHook
       extends DirectoryThread
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.DirectoryServerShutdownHook";



  /**
   * Creates a new shutdown hook that will stop the Directory Server when it is
   * determined that the JVM is shutting down.
   */
  public DirectoryServerShutdownHook()
  {
    super("Directory Server Shutdown Hook");

  }



  /**
   * Invokes the shutdown hook to signal the Directory Server to stop running.
   */
  @Override
  public void run()
  {
    logger.trace(
        "Directory Server shutdown hook has been invoked.");

    DirectoryServer.shutDown(CLASS_NAME,
                             ERR_SHUTDOWN_DUE_TO_SHUTDOWN_HOOK.get());
  }
}

