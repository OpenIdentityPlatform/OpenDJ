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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opends.guitools.controlpanel.ui.nodes.BasicNode;

/**
 * This is the class that contains all the AbstractNodeTask objects that
 * are running or that are waiting to be executed.  Basically BrowserController
 * will listen for events and will create a AbstractNodeTask object that
 * will add to this queue in order it to be asynchronously executed.
 *
 * The queue will basically start a certain number of threads and this threads
 * will "consume" the AbstractNodeTask objects that are added to this queue.
 */
class NodeSearcherQueue implements Runnable {

  private final String name;
  private final List<AbstractNodeTask> waitingQueue = new ArrayList<>();
  private final Map<BasicNode, AbstractNodeTask> workingList = new HashMap<>();
  private final Map<BasicNode, BasicNode> cancelList = new HashMap<>();
  private final ThreadGroup threadGroup;


  /**
   * Construct a queue with the specified name.
   * The name is for debugging purpose only.
   * @param name the name of the queue.
   * @param threadCount then number of threads that the queue will use.
   */
  public NodeSearcherQueue(String name, int threadCount) {
    this.name = name;
    threadGroup = new ThreadGroup(name);
    for (int i = 0; i < threadCount; i++) {
      Thread t = new Thread(threadGroup, this, name + "[" + i + "]");
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }
  }

  /**
   * Returns the name of this queue.
   * @return the name of this queue.
   */
  public String getName() {
    return name;
  }


  /** Shutdown this queue. All the associated threads are stopped. */
  public void shutdown() {
    threadGroup.interrupt();
  }


  /**
   * Add an object in this queue.
   * If the object is already in the waiting sub-queue, it is silently ignored.
   * @param nodeTask the task to be added.
   */
  public synchronized void queue(AbstractNodeTask nodeTask) {
    if (nodeTask == null)
    {
      throw new IllegalArgumentException("null argument");
    }
    waitingQueue.add(nodeTask);
    notify();
//    System.out.println("Queued " + nodeTask + " in " + _name);
  }


  /**
   * Cancel an object.
   * If the object is in the waiting sub-queue, it's simply removed from.
   * If the object is in the working subqueue, it's kept in place but marked as
   * cancelled. It's the responsibility of the consumer to detect that and flush
   * the object asap.
   * @param node the node whose associated tasks must be cancelled.
   */
  public synchronized void cancelForNode(BasicNode node) {
    if (node == null)
    {
      throw new IllegalArgumentException("null argument");
    }
    // Remove all the associated tasks from the waiting queue
    for (int i = waitingQueue.size()-1; i >= 0; i--) {
      AbstractNodeTask task = waitingQueue.get(i);
      if (task.getNode() == node) {
        waitingQueue.remove(i);
      }
    }
    // Mark the on-going task as cancelled
    AbstractNodeTask task = workingList.get(node);
    if (task != null) {
      cancelList.put(node, node);
      task.cancel();
    }
    notify();
  }

  /**
   * Tells whether this node is in the working list.
   * @param node the node.
   * @return <CODE>true</CODE> if the provided node is being refreshed and
   * <CODE>false</CODE> otherwise.
   */
  public synchronized boolean isWorking(BasicNode node)
  {
    return workingList.get(node) != null;
  }


  /** Cancel all the object from this queue. */
  public synchronized void cancelAll() {
    waitingQueue.clear();
    for (BasicNode node : workingList.keySet())
    {
      AbstractNodeTask task = workingList.get(node);
      cancelList.put(node, node);
      task.cancel();
    }
  }



  /**
   * Fetch an object from this queue.
   * The returned object is moved from the waiting sub-queue to the working
   * sub-queue.
   * @return the next object to be handled.
   * @throws InterruptedException if the call to fetch was interrupted by
   * another thread.
   */
  private synchronized AbstractNodeTask fetch() throws InterruptedException {
    AbstractNodeTask result = null;

    // Get the first obj from waitingQueue which is
    // not in workingList yet.
    do {
      int waitingSize = waitingQueue.size();
      int i = 0;
      while (i < waitingSize && !canBeFetched(i)) {
        i++;
      }
      if (i == waitingSize) { // Nothing found
        wait();
      }
      else {
        result = waitingQueue.get(i);
        waitingQueue.remove(i);
        workingList.put(result.getNode(), result);
      }
    }
    while (result == null);

//    System.out.println("Fetched " + result + " from " + _name);

    return result;
  }

  /**
   * Whether the task in the waiting queue i can be fetched.
   * @param i the index of the task.
   * @return <CODE>true</CODE> if the task can be fetched and <CODE>false</CODE>
   * otherwise.
   */
  private boolean canBeFetched(int i) {
    AbstractNodeTask task = waitingQueue.get(i);
    return workingList.get(task.getNode()) == null;
  }


  /**
   * Flush an object from this queue.
   * The object is removed from the working sub-queue.
   * @param task the task to be flushed.
   */
  private synchronized void flush(AbstractNodeTask task) {
    if (task == null)
    {
      throw new IllegalArgumentException("null argument");
    }
    workingList.remove(task.getNode());
    cancelList.remove(task.getNode());
    notify();
//    System.out.println("Flushed " + task + " from " + _name);
  }


  /**
   * Return the number of object in this queue (i.e. the  number of object in
   * both sub-queues).
   * @return the number of objects in this queue.
   */
  public int size() {
    return waitingQueue.size() + workingList.size();
  }


  /**
   * The method that is executed by the different threads that are created in
   * the NodeSearchQueue constructor.
   * Basically this method fetches objects from the waiting queue and runs them.
   */
  @Override
  public void run() {
    boolean interrupted = false;
    while (!interrupted)
    {
      try
      {
        // Fetch and process a node also
        // taking care of update events
        AbstractNodeTask task = fetch();
        task.run();
        flush(task);
      }
      catch(InterruptedException x) {
        interrupted = true;
      }
      catch(Exception x) {
        // At this level it is a bug...
        x.printStackTrace();
      }
    }
  }

}
