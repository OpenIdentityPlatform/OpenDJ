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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.common;

import org.opends.server.replication.protocol.ECLUpdateMsg;
import org.opends.server.types.DirectoryException;

/**
 * This interface defines a session used to search the external changelog
 * in the Directory Server.
 */
public interface ExternalChangeLogSession
{
  /**
   * Returns the next message available for the ECL (blocking).
   * @return the next available message from the ECL.
   * @throws DirectoryException When an error occurs.
   */
  public abstract ECLUpdateMsg getNextUpdate()
  throws DirectoryException;

  /**
   * Closes the session.
   * @throws DirectoryException when needed.
   */
  public abstract void close()
  throws DirectoryException;

}
