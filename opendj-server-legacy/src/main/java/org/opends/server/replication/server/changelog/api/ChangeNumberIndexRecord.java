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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.api;

import org.opends.server.replication.common.CSN;
import org.forgerock.opendj.ldap.DN;

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
