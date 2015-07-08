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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.util;

import java.util.*;

/**
 * Utility class for {@link Collection}s.
 */
public final class CollectionUtils
{

  private CollectionUtils()
  {
    // private for utility classes
  }

  /**
   * Creates a new {@link ArrayList} with the provided elements.
   *
   * @param <E>
   *          the elements' type
   * @param elements
   *          the elements to add to the new ArrayList
   * @return a new ArrayList with the provided elements
   */
  public static <E> ArrayList<E> newArrayList(E... elements)
  {
    return new ArrayList<>(Arrays.asList(elements));
  }

  /**
   * Creates a new {@link LinkedList} with the provided elements.
   *
   * @param <E>
   *          the elements' type
   * @param elements
   *          the elements to add to the new LinkedList
   * @return a new LinkedList with the provided elements
   */
  public static <E> LinkedList<E> newLinkedList(E... elements)
  {
    return new LinkedList<>(Arrays.asList(elements));
  }

  /**
   * Creates a new {@link HashSet} with the provided elements.
   *
   * @param <E>
   *          the elements' type
   * @param elements
   *          the elements to add to the new HashSet
   * @return a new HashSet with the provided elements
   */
  public static <E> HashSet<E> newHashSet(E... elements)
  {
    return new HashSet<>(Arrays.asList(elements));
  }

  /**
   * Creates a new {@link LinkedHashSet} with the provided elements.
   *
   * @param <E>
   *          the elements' type
   * @param elements
   *          the elements to add to the new LinkedHashSet
   * @return a new LinkedHashSet with the provided elements
   */
  public static <E> LinkedHashSet<E> newLinkedHashSet(E... elements)
  {
    return new LinkedHashSet<>(Arrays.asList(elements));
  }

  /**
   * Creates a new {@link TreeSet} with the provided elements.
   *
   * @param <E>
   *          the elements' type
   * @param elements
   *          the elements to add to the new TreeSet
   * @return a new TreeSet with the provided elements
   */
  public static <E> TreeSet<E> newTreeSet(E... elements)
  {
    return new TreeSet<>(Arrays.asList(elements));
  }
}
