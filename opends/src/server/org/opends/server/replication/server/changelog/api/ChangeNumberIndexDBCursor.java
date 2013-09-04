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

import org.opends.server.replication.common.CSN;

/**
 * Iterator into the changelog database. Once it is not used anymore, a
 * ChangelogDBIterator must be closed to release all the resources into the
 * database.
 */
public interface ChangeNumberIndexDBCursor extends Closeable
{

  /**
   * Getter for the replication CSN field.
   *
   * @return The replication CSN field.
   */
  CSN getCSN();

  /**
   * Getter for the baseDN field.
   *
   * @return The service ID.
   */
  String getBaseDN();

  /**
   * Getter for the draftCN field.
   *
   * @return The draft CN field.
   */
  int getDraftCN();

  /**
   * Skip to the next record of the database.
   *
   * @return true if has next, false otherwise
   * @throws ChangelogException
   *           When database exception raised.
   */
  boolean next() throws ChangelogException;

  /**
   * Release the resources and locks used by this Iterator. This method must be
   * called when the iterator is no longer used. Failure to do it could cause DB
   * deadlock.
   */
  @Override
  void close();
}
