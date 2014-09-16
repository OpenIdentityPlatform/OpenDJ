/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;

import org.opends.server.replication.common.CSN;
import org.opends.server.types.DN;

/**
 * The Change Number Index Record class represents records stored in the
 * {@link ChangeNumberIndexDB}. It stores data about a change that happened with
 * the replication.
 */
public final class ChangeNumberIndexRecord
{

  /** This is the key used to store this record. */
  private final long changeNumber;
  /** The baseDN where the change happened. */
  private final DN baseDN;
  /** The CSN of the change. */
  private final CSN csn;

  /**
   * Builds an instance of this class.
   *
   * @param changeNumber
   *          the change number
   * @param baseDN
   *          the baseDN
   * @param csn
   *          the replication CSN field
   */
  public ChangeNumberIndexRecord(long changeNumber, DN baseDN, CSN csn)
  {
    this.changeNumber = changeNumber;
    this.baseDN = baseDN;
    this.csn = csn;
  }

  /**
   * Builds an instance of this class, with changeNumber equal to 0.
   * @param baseDN
   *          the baseDN
   * @param csn
   *          the replication CSN field
   *
   * @see #ChangeNumberIndexRecord(long, DN, CSN)
   */
  public ChangeNumberIndexRecord(DN baseDN, CSN csn)
  {
    this(0, baseDN, csn);
  }

  /**
   * Getter for the baseDN field.
   *
   * @return the baseDN
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Getter for the replication CSN field.
   *
   * @return The replication CSN field.
   */
  public CSN getCSN()
  {
    return csn;
  }

  /**
   * Getter for the change number field.
   *
   * @return The change number field.
   */
  public long getChangeNumber()
  {
    return changeNumber;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "changeNumber=" + changeNumber + " csn=" + csn + " baseDN=" + baseDN;
  }
}
