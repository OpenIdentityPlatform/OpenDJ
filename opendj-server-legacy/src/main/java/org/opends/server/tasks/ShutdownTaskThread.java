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
package org.opends.server.tasks;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.DirectoryThread;
import org.opends.server.core.DirectoryServer;

/**
 * This class defines a thread that will be spawned to invoke the Directory
 * Server shutdown process.  This needs to be a separate thread because the task
 * that creates it has to complete before the server can really shut down.
 */
public class ShutdownTaskThread
       extends DirectoryThread
{
  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME =
       "org.opends.server.tasks.ShutdownTaskThread";

  /** The shutdown message that will be used. */
  private LocalizableMessage shutdownMessage;

  /**
   * Creates a new instance of this shutdown task thread with the provided
   * message.
   *
   * @param  shutdownMessage  The shutdown message that will be used.
   */
  public ShutdownTaskThread(LocalizableMessage shutdownMessage)
  {
    super("Shutdown Task Thread");

    this.shutdownMessage = shutdownMessage;

    setDaemon(true);
  }

  /** Invokes the Directory Server shutdown process. */
  @Override
  public void run()
  {
    DirectoryServer.shutDown(CLASS_NAME, shutdownMessage);
  }
}
