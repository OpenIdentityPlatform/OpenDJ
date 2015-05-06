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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.Comparator;

/**
 * This comparator is used to sort trees in order of priority
 * for preloading into the cache.
 */
class TreePreloadComparator implements Comparator<Tree>
{

  /**
   * Calculate the relative priority of a tree for preloading.
   *
   * @param tree A handle to the tree.
   * @return 1 for id2entry tree, 2 for dn2id tree, 3 for all others.
   */
  private static int priority(Tree tree)
  {
    String indexName = tree.getName().getIndexId();
    if (indexName.endsWith(SuffixContainer.ID2ENTRY_INDEX_NAME))
    {
      return 1;
    }
    else if (indexName.endsWith(SuffixContainer.DN2ID_INDEX_NAME))
    {
      return 2;
    }
    else
    {
      return 3;
    }
  }

  /**
   * Compares its two arguments for order.  Returns a negative integer,
   * zero, or a positive integer as the first argument is less than, equal
   * to, or greater than the second.
   *
   * @param tree1 the first object to be compared.
   * @param tree2 the second object to be compared.
   * @return a negative integer, zero, or a positive integer as the
   *         first argument is less than, equal to, or greater than the
   *         second.
   **/
  @Override
  public int compare(Tree tree1, Tree tree2)
  {
    return priority(tree1) - priority(tree2);
  }
}
