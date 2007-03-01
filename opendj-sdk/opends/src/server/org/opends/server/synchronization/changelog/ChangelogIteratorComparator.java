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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.changelog;

import java.util.Comparator;

import org.opends.server.synchronization.common.ChangeNumber;

/**
 * This Class define a Comparator that allows to know which ChangelogIterator
 * contain the next UpdateMessage in the order defined by the ChangeNumber
 * of the UpdateMessage.
 */
public class ChangelogIteratorComparator
              implements Comparator<ChangelogIterator>
{
  /**
   * Compare the ChangeNumber of the ChangelogIterators.
   *
   * @param o1 first ChangelogIterator.
   * @param o2 second ChangelogIterator.
   * @return result of the comparison.
   */
  public int compare(ChangelogIterator o1, ChangelogIterator o2)
  {
    ChangeNumber csn1 = o1.getChange().getChangeNumber();
    ChangeNumber csn2 = o2.getChange().getChangeNumber();

    return ChangeNumber.compare(csn1, csn2);
  }
}
