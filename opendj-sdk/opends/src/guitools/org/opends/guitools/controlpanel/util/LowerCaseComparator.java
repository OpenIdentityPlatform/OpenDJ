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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.util;

import java.util.Comparator;

/**
 * Class used to compare Strings without take into account the case.  It can
 * be used to sort Strings in TreeSets for instance.
 *
 */
public class LowerCaseComparator implements Comparator<String>
{
  /**
   * {@inheritDoc}
   */
  public int compare(String s1, String s2)
  {
    if ((s1 != null) && (s2 != null))
    {
      return s1.toLowerCase().compareTo(s2.toLowerCase());
    }
    else if (s2 != null)
    {
      return -1;
    }
    else if (s1 != null)
    {
      return 1;
    }
    else
    {
      return 0;
    }
  }
}
