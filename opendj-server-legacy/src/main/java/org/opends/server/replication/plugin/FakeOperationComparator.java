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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2013-2015 ForgeRock AS.
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
