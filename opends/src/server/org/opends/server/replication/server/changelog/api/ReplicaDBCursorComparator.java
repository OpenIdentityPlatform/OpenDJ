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
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.api;

import java.util.Comparator;

import org.opends.server.replication.common.ChangeNumber;

/**
 * This class defines a {@link Comparator} that allows to know which
 * {@link ReplicaDBCursor} contain the next {@link UpdateMsg} in the order
 * defined by the {@link ChangeNumber} of the {@link UpdateMsg}.
 */
public class ReplicaDBCursorComparator implements Comparator<ReplicaDBCursor>
{
  /**
   * Compare the {@link ChangeNumber} of the {@link ReplicaDBCursor}.
   *
   * @param o1
   *          first cursor.
   * @param o2
   *          second cursor.
   * @return result of the comparison.
   */
  @Override
  public int compare(ReplicaDBCursor o1, ReplicaDBCursor o2)
  {
    ChangeNumber csn1 = o1.getChange().getChangeNumber();
    ChangeNumber csn2 = o2.getChange().getChangeNumber();

    return ChangeNumber.compare(csn1, csn2);
  }
}
