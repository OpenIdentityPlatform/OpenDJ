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
 * Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.core;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;

/**
 *
 * This class implements the work queue strategy.
 */
public class WorkQueueStrategy implements QueueingStrategy {

  /**
   * Put the request in the work queue.
   *
   * @param operation Operation to put in the work queue.
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs in the Directory Server.
   */
  @Override
  public void enqueueRequest(Operation operation) throws DirectoryException {
    DirectoryServer.enqueueRequest(operation);
  }
}
