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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.backends.ndb.importLDIF;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.api.DirectoryThread;
import org.opends.server.backends.ndb.*;
import org.opends.messages.Message;
import static org.opends.messages.NdbMessages.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.opends.server.core.AddOperation;
import org.opends.server.protocols.internal.InternalClientConnection;

/**
 * A thread to process import entries from a queue.  Multiple instances of
 * this class process entries from a single shared queue.
 */
public class WorkThread extends DirectoryThread {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Number of operations to batch on a single transaction.
   */
  private static final int TXN_BATCH_SIZE = 15;

  /*
   * Work queue of work items.
   */
  private BlockingQueue<WorkElement> workQueue;

  /**
   * The number of entries imported by this thread.
   */
  private int importedCount = 0;

  /**
   * Root container.
   */
  private RootContainer rootContainer;

  /**
   * Abstract Transaction object.
   */
  private AbstractTransaction txn;

  /**
   * A flag that is set when the thread has been told to stop processing.
   */
  private boolean stopRequested = false;

  /**
   * The thread number related to a thread.
   */
  private int threadNumber;



  /**
   * Create a work thread instance using the specified parameters.
   *
   * @param workQueue  The work queue to pull work off of.
   * @param threadNumber The thread number.
   * @param rootContainer The root container.
   */
  public WorkThread(BlockingQueue<WorkElement> workQueue, int threadNumber,
                                RootContainer rootContainer)
  {
    super("Import Worker Thread " + threadNumber);
    this.threadNumber = threadNumber;
    this.workQueue = workQueue;
    this.rootContainer = rootContainer;
    this.txn = new AbstractTransaction(rootContainer);
  }

  /**
   * Get the number of entries imported by this thread.
   * @return The number of entries imported by this thread.
   */
   int getImportedCount() {
    return importedCount;
  }

  /**
   * Tells the thread to stop processing.
   */
   void stopProcessing() {
    stopRequested = true;
  }

  /**
   * Run the thread. Read from item from queue and process unless told to stop.
   */
  @Override
  public void run()
  {
    int batchSize = 0;
    try {
      do {
        try {
          WorkElement element = workQueue.poll(1000, TimeUnit.MILLISECONDS);
          if (element != null) {
            process(element);
            batchSize++;
            if (batchSize < TXN_BATCH_SIZE) {
              continue;
            } else {
              batchSize = 0;
              txn.commit();
            }
          }
        }
        catch (InterruptedException e) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      } while (!stopRequested);
      txn.commit();
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new RuntimeException(e);
    }
  }

  /**
   * Process a work element.
   *
   * @param element The work elemenet to process.
   *
   * @throws Exception If an error occurs.
   */
  private void process(WorkElement element) throws Exception
  {
    Entry entry = element.getEntry();
    DNContext context = element.getContext();
    EntryContainer ec = context.getEntryContainer();

    DN entryDN = entry.getDN();
    DN parentDN = context.getEntryContainer().getParentWithinBase(entryDN);

    if (parentDN != null) {
      // If the parent is in the pending map, another thread is working on
      // the parent entry; wait until that thread is done with the parent.
      while (context.isPending(parentDN)) {
        try {
          Thread.sleep(50);
        } catch (Exception e) {
          return;
        }
      }
      if (context.getParentDN() == null) {
        Message msg =
                ERR_NDB_IMPORT_PARENT_NOT_FOUND.get(parentDN.toString());
        rejectLastEntry(context, msg);
        context.removePending(entryDN);
        return;
      }
    } else {
      parentDN = entryDN;
    }

    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    AddOperation addOperation =
      conn.processAdd(entry.getDN(), entry.getObjectClasses(),
      entry.getUserAttributes(), entry.getOperationalAttributes());

    try {
      ec.addEntryNoCommit(entry, addOperation, txn);
      DN contextParentDN = context.getParentDN();
      if ((contextParentDN == null) ||
        !contextParentDN.equals(parentDN)) {
        txn.commit();
      }
      importedCount++;
    } catch (DirectoryException de) {
      if (de.getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
          Message msg = WARN_NDB_IMPORT_ENTRY_EXISTS.get();
          rejectLastEntry(context, msg);
          context.removePending(entryDN);
          txn.close();
      } else {
        txn.close();
        throw de;
      }
    }

    context.setParentDN(parentDN);
    context.removePending(entryDN);

    return;
  }

  /**
   * The synchronized wrapper method to reject the last entry.
   *
   * @param context Import context.
   * @param msg Reject message.
   */
  private static synchronized void rejectLastEntry(DNContext context,
    Message msg)
  {
    context.getLDIFReader().rejectLastEntry(msg);
  }
}
