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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;

import java.io.Closeable;

import org.opends.server.replication.protocol.UpdateMsg;

/**
 * This cursor allows to iterate through the changes received from a given
 * replica (Directory Server) in the topology.
 */
public interface ReplicaDBCursor extends Closeable, Comparable<ReplicaDBCursor>
{

  /**
   * Get the UpdateMsg where the cursor is currently set.
   *
   * @return The UpdateMsg where the cursor is currently set.
   */
  UpdateMsg getChange();

  /**
   * Go to the next change in the ReplicaDB or in the server Queue.
   *
   * @return false if the cursor is already on the last change before this call.
   */
  boolean next();

  /**
   * Release the resources and locks used by this cursor. This method must be
   * called when the cursor is no longer used. Failure to do it could cause DB
   * deadlock.
   */
  @Override
  void close();

}
