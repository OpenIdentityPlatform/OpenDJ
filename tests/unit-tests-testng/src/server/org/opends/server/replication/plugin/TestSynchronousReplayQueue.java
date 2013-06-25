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
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A very partial implementation of a Blocking queue that should be used for
 * test purpose only.
 * <p>
 * Not all the methods are implemented.
 */
@SuppressWarnings("javadoc")
public class TestSynchronousReplayQueue implements BlockingQueue<UpdateToReplay>
{
  private LinkedList<UpdateToReplay> list = new LinkedList<UpdateToReplay>();

  @Override
  public boolean add(UpdateToReplay e)
  {
    return this.list.add(e);
  }

  @Override
  public boolean contains(Object o)
  {
    return this.list.contains(o);
  }

  @Override
  public int drainTo(Collection<? super UpdateToReplay> c)
  {
    return 0;
  }

  @Override
  public int drainTo(Collection<? super UpdateToReplay> c, int maxElements)
  {
    return 0;
  }

  @Override
  public boolean offer(UpdateToReplay e)
  {
    return list.add(e);
  }

  @Override
  public boolean offer(UpdateToReplay e, long timeout, TimeUnit unit)
      throws InterruptedException
  {
    return list.add(e);
  }

  @Override
  public UpdateToReplay poll(long timeout, TimeUnit unit)
      throws InterruptedException
  {
    return null;
  }

  @Override
  public void put(UpdateToReplay e) throws InterruptedException
  {
  }

  @Override
  public int remainingCapacity()
  {
    return 0;
  }

  @Override
  public boolean remove(Object o)
  {
    return this.list.remove(o);
  }

  @Override
  public UpdateToReplay take() throws InterruptedException
  {
    return list.removeFirst();
  }

  @Override
  public UpdateToReplay element()
  {
    return null;
  }

  @Override
  public UpdateToReplay peek()
  {
    return this.list.peek();
  }

  @Override
  public UpdateToReplay poll()
  {
    return this.list.poll();
  }

  @Override
  public UpdateToReplay remove()
  {
    return this.list.remove();
  }

  @Override
  public boolean addAll(Collection<? extends UpdateToReplay> c)
  {
    return this.list.addAll(c);
  }

  @Override
  public void clear()
  {
    this.list.clear();
  }

  @Override
  public boolean containsAll(Collection<?> c)
  {
    return this.list.containsAll(c);
  }

  @Override
  public boolean isEmpty()
  {
    return this.list.isEmpty();
  }

  @Override
  public Iterator<UpdateToReplay> iterator()
  {
    return this.list.iterator();
  }

  @Override
  public boolean removeAll(Collection<?> c)
  {
    return this.list.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c)
  {
    return this.list.retainAll(c);
  }

  @Override
  public int size()
  {
    return this.list.size();
  }

  @Override
  public Object[] toArray()
  {
    return this.list.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a)
  {
    return this.list.toArray(a);
  }

}
