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

package org.opends.guitools.controlpanel.datamodel;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.AbstractListModel;

/**
 * Note: this implementation does not call automatically fireContentsChanged,
 * its up to the caller of the different methods of this implementation to
 * call it explicitly.  This is done because in general there is a series
 * of calls to the add/remove methods and a single call to notify that
 * things have changed is enough.
 *
 * @param <T>
 */
public class SortableListModel<T> extends AbstractListModel
{
  private static final long serialVersionUID = 3241258779190228463L;
  private SortedSet<T> data = new TreeSet<T>();

  /**
   * Returns the size of the list model.
   * @return the size of the list model.
   */
  public int getSize()
  {
    return data.size();
  }

  /**
   * Sets the comparator to be used to sort the list.
   * @param comp the comparator.
   */
  public void setComparator(Comparator<T> comp)
  {
    SortedSet<T> copy = data;
    data = new TreeSet<T>(comp);
    data.addAll(copy);
  }

  /**
   * Returns the element at the specified index.
   * @param i the index of the element.
   * @return the element at the specified index.
   */
  public T getElementAt(int i)
  {
    int index = 0;
    for (T element : data)
    {
      if (index == i)
      {
        return element;
      }
      index++;
    }
    throw new ArrayIndexOutOfBoundsException(
        "The index "+i+" is bigger than the maximum size: "+getSize());
  }

  /**
   * Adds a value to the list model.
   * @param value the value to be added.
   */
  public void add(T value)
  {
    data.add(value);
  }

  /**
   * Removes a value from the list model.
   * @param value the value to be removed.
   * @return <CODE>true</CODE> if the element was on the list and
   * <CODE>false</CODE> otherwise.
   */
  public boolean remove(T value)
  {
    return data.remove(value);
  }

  /**
   * Clears the list model.
   *
   */
  public void clear()
  {
    data.clear();
  }

  /**
   * Adds all the elements in the collection to the list model.
   * @param newData the collection containing the elements to be added.
   */
  public void addAll(Collection<T> newData)
  {
    data.addAll(newData);
  }

  /**
   * {@inheritDoc}
   */
  public void fireContentsChanged(Object source, int index0, int index1)
  {
    super.fireContentsChanged(source, index0, index1);
  }

  /**
   * Returns the data in this list model ordered.
   * @return the data in this list model ordered.
   */
  public SortedSet<T> getData()
  {
    return new TreeSet<T>(data);
  }
}
