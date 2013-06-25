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
package org.opends.server.replication.server;

import java.util.Comparator;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMsg;

/**
 * Class to use for establishing an order within UpdateMessages.
 */
public class UpdateComparator implements Comparator<UpdateMsg>
{
  /**
   * The UpdateComparator Singleton.
   */
  public static UpdateComparator comparator = new UpdateComparator();

  /**
   * Private constructor.
   */
  private UpdateComparator()
  {
    super();
  }

  /**
   * Compares two UpdateMessages.
   *
   * @param msg1 first UpdateMsg to compare
   * @param msg2 second UpdateMsg to compare
   * @return -1 if msg1 < msg2
   *          0 if msg1 == msg2
   *          1 if msg1 > msg2
   */
  public int compare(UpdateMsg msg1, UpdateMsg msg2)
  {
    return ChangeNumber.compare(msg1.getChangeNumber(), msg2.getChangeNumber());
  }
}
