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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.types.Entry;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;



/**
 * This class provides a simple change notification listener that simply counts
 * the number of times that it is invoked during processing.
 */
public class TestChangeNotificationListener
       implements ChangeNotificationListener
{
  // The number of times that the listener has been invoked for add operations.
  private AtomicInteger addCount;

  // The number of times that the listener has been invoked for delete
  // operations.
  private AtomicInteger deleteCount;

  // The number of times that the listener has been invoked for modify
  // operations.
  private AtomicInteger modifyCount;

  // The number of times that the listener has been invoked for modify DN
  // operations.
  private AtomicInteger modifyDNCount;



  /**
   * Creates a new instance of this change notification listener.
   */
  public TestChangeNotificationListener()
  {
    addCount      = new AtomicInteger(0);
    deleteCount   = new AtomicInteger(0);
    modifyCount   = new AtomicInteger(0);
    modifyDNCount = new AtomicInteger(0);
  }




  /**
   * {@inheritDoc}
   */
  public void handleAddOperation(PostResponseAddOperation addOperation,
                                 Entry entry)
  {
    addCount.incrementAndGet();
  }



  /**
   * {@inheritDoc}
   */
  public void handleDeleteOperation(PostResponseDeleteOperation deleteOperation,
                                    Entry entry)
  {
    deleteCount.incrementAndGet();
  }



  /**
   * {@inheritDoc}
   */
  public void handleModifyOperation(PostResponseModifyOperation modifyOperation,
                                    Entry oldEntry, Entry newEntry)
  {
    modifyCount.incrementAndGet();
  }



  /**
   * {@inheritDoc}
   */
  public void handleModifyDNOperation(
                   PostResponseModifyDNOperation modifyDNOperation,
                   Entry oldEntry, Entry newEntry)
  {
    modifyDNCount.incrementAndGet();
  }



  /**
   * Resets all of the counts to zero.
   */
  public void reset()
  {
    addCount.set(0);
    deleteCount.set(0);
    modifyCount.set(0);
    modifyDNCount.set(0);
  }



  /**
   * Retrieves the current invocation count for add operations.
   *
   * @return  The current invocation count for add operations.
   */
  public int getAddCount()
  {
    return addCount.get();
  }



  /**
   * Retrieves the current invocation count for delete operations.
   *
   * @return  The current invocation count for delete operations.
   */
  public int getDeleteCount()
  {
    return deleteCount.get();
  }



  /**
   * Retrieves the current invocation count for modify operations.
   *
   * @return  The current invocation count for modify operations.
   */
  public int getModifyCount()
  {
    return modifyCount.get();
  }



  /**
   * Retrieves the current invocation count for modify DN operations.
   *
   * @return  The current invocation count for modify DN operations.
   */
  public int getModifyDNCount()
  {
    return modifyDNCount.get();
  }
}

