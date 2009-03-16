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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * A very partial implementation of a Blocking queue that should
 * be used for test purpose only.
 *
 * Only the offer and take methods are implemented.
 *
 */
public class TestSynchronousReplayQueue implements BlockingQueue<UpdateToReplay>
{
  LinkedList<UpdateToReplay> list = new LinkedList<UpdateToReplay>();

  public boolean add(UpdateToReplay e)
  {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean contains(Object o)
  {
    // TODO Auto-generated method stub
    return false;
  }

  public int drainTo(Collection<? super UpdateToReplay> c)
  {
    // TODO Auto-generated method stub
    return 0;
  }

  public int drainTo(Collection<? super UpdateToReplay> c, int maxElements)
  {
    // TODO Auto-generated method stub
    return 0;
  }

  public boolean offer(UpdateToReplay e)
  {
    list.add(e);
    return true;
  }

  public boolean offer(UpdateToReplay e, long timeout, TimeUnit unit)
      throws InterruptedException
  {
    // TODO Auto-generated method stub
    return false;
  }

  public UpdateToReplay poll(long timeout, TimeUnit unit)
      throws InterruptedException
  {
    // TODO Auto-generated method stub
    return null;
  }

  public void put(UpdateToReplay e) throws InterruptedException
  {
    // TODO Auto-generated method stub

  }

  public int remainingCapacity()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  public boolean remove(Object o)
  {
    // TODO Auto-generated method stub
    return false;
  }

  public UpdateToReplay take() throws InterruptedException
  {
    return list.pop();
  }

  public UpdateToReplay element()
  {
    // TODO Auto-generated method stub
    return null;
  }

  public UpdateToReplay peek()
  {
    // TODO Auto-generated method stub
    return null;
  }

  public UpdateToReplay poll()
  {
    // TODO Auto-generated method stub
    return null;
  }

  public UpdateToReplay remove()
  {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean addAll(Collection<? extends UpdateToReplay> c)
  {
    // TODO Auto-generated method stub
    return false;
  }

  public void clear()
  {
    // TODO Auto-generated method stub

  }

  public boolean containsAll(Collection<?> c)
  {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean isEmpty()
  {
    // TODO Auto-generated method stub
    return false;
  }

  public Iterator<UpdateToReplay> iterator()
  {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean removeAll(Collection<?> c)
  {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean retainAll(Collection<?> c)
  {
    // TODO Auto-generated method stub
    return false;
  }

  public int size()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  public Object[] toArray()
  {
    // TODO Auto-generated method stub
    return null;
  }

  public <T> T[] toArray(T[] a)
  {
    // TODO Auto-generated method stub
    return null;
  }

}
