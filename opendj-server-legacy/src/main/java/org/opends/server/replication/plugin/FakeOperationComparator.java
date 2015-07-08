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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.Comparator;

/**
 * This Class implements a Comparator that can be used to build TreeSet
 * containing FakeOperations sorted by the CSN order.
 */
public class FakeOperationComparator implements Comparator<FakeOperation>
{
  /** {@inheritDoc} */
  @Override
  public int compare(FakeOperation op1, FakeOperation op2)
  {
    if (op1 == null)
    {
      return -1;
    }
    return op1.getCSN().compareTo(op2.getCSN());
  }
}
