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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.TreeSet;

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

  /**
   * Collects all the elements from the provided iterable into the provided collection.
   *
   * @param <C>
   *          The type of the collection
   * @param <E>
   *          The type of the iterable's elements
   * @param iterable
   *          the iterable from which to read elements
   * @param outputCollection
   *          the collection where to add the iterable's elements
   * @return the provided collection
   */
  public static <C extends Collection<E>, E> C collect(Iterable<E> iterable, C outputCollection)
  {
    for (E e : iterable)
    {
      outputCollection.add(e);
    }
    return outputCollection;
  }

  /**
   * Returns whether the provided iterable is empty, i.e. whether it has not elements.
   *
   * @param iterable
   *          the iterable for which to omake the determination
   * @return {@code true} if the iterable is empty, {@code false} otherwise.
   */
  public static boolean isEmpty(Iterable<?> iterable)
  {
    return !iterable.iterator().hasNext();
  }
}
