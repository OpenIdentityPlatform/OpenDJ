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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
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
 * @param <T> type of the elements in this list model
 */
public class SortableListModel<T> extends AbstractListModel<T>
{
  private static final long serialVersionUID = 3241258779190228463L;
  private SortedSet<T> data = new TreeSet<>();

  @Override
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
    data = new TreeSet<>(comp);
    data.addAll(copy);
  }

  @Override
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

  /** Clears the list model. */
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

  @Override
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
    return new TreeSet<>(data);
  }
}
