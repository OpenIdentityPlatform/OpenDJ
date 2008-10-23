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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.server.core;

import org.opends.server.types.AbstractOperation;
import org.opends.server.types.DirectoryException;

/**
 * This interface defines request handling strategies.
 */
public interface QueueingStrategy {

  /**
   * Put the request in the queue.
   *
   * @param operation Operation to put in the queue.
   * @throws org.opends.server.types.DirectoryException
   *          If a problem occurs in the Directory Server.
   */
  public void enqueueRequest(AbstractOperation operation)
    throws DirectoryException;

}
