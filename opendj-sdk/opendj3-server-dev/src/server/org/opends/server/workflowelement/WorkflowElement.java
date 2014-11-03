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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.workflowelement;

import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Operation;

/**
 * This class defines the super class for all the workflow elements. A workflow
 * element is a task in a workflow. A workflow element can wrap a physical
 * repository such as a local backend, a remote LDAP server or a local LDIF
 * file. A workflow element can also be used to route operations. This is the
 * case for load balancing and distribution. And workflow element can be used
 * in a virtual environment to transform data (DN and attribute renaming,
 * attribute value renaming...).
 */
public abstract class WorkflowElement implements Observer
{

  /** The observable state of the workflow element. */
  private ObservableWorkflowElementState observableState =
    new ObservableWorkflowElementState(this);


  /**
   * The list of observers who want to be notified when a workflow element
   * required by the observer is created. The key of the map is a string that
   * identifies the newly created workflow element.
   */
  private static ConcurrentMap<String, List<Observer>>
    newWorkflowElementNotificationList =
      new ConcurrentHashMap<String, List<Observer>>();

  /**
   * Provides the observable state of the workflow element.
   * This method is intended to be called by the WorkflowElementConfigManager
   * that wants to notify observers that the workflow element state has
   * changed (in particular when a workflow element has been disabled).
   *
   * @return the observable state of the workflow element
   */
  protected final ObservableWorkflowElementState getObservableState()
  {
    return observableState;
  }

  /**
   * Registers with a specific workflow element to be notified when the
   * workflow element state has changed. This notification system is
   * mainly used to be warned when a workflow element is enabled or
   * disabled.
   * <p>
   * If the workflow element <code>we</code> is not <code>null</code>
   * then the <code>observer</code> is registered with the list of objects
   * to notify when <code>we</code> has changed.
   * <p>
   * If the workflow element <code>we</code> is <code>null</code> then
   * the <code>observer</code> is registered with a static list of objects
   * to notify when a workflow element named <code>weid</code> is created.
   *
   * @param we        the workflow element. If <code>null</code> then observer
   *                  is registered with a list of workflow element
   *                  identifiers.
   * @param weid      the identifier of the workflow element. This parameter
   *                  is useless when <code>we</code> is not <code>null</code>
   * @param observer  the observer to notify when the workflow element state
   *                  has been modified
   */
  public static void registereForStateUpdate(WorkflowElement we, String weid, Observer observer)
  {
    // If the workflow element "we" exists then register the observer with "we"
    // else register the observer with a static list of workflow element
    // identifiers
    if (we != null)
    {
      ObservableWorkflowElementState westate = we.getObservableState();
      westate.addObserver(observer);
    }
    else
    {
      if (weid == null)
      {
        return;
      }

      List<Observer> observers = newWorkflowElementNotificationList.get(weid);
      if (observers == null)
      {
        // create the list of observers
        observers = new CopyOnWriteArrayList<Observer>();
        observers.add(observer);
        newWorkflowElementNotificationList.put(weid, observers);
      }
      else
      {
        // update the observer list
        observers.add(observer);
      }
    }
  }


  /**
   * Deregisters an observer that was registered with a specific workflow
   * element.
   * <p>
   * If the workflow element <code>we</code> is not <code>null</code>
   * then the <code>observer</code> is deregistered with the list of objects
   * to notify when <code>we</code> has changed.
   * <p>
   * If the workflow element <code>we</code> is <code>null</code> then
   * the <code>observer</code> is deregistered with a static list of objects
   * to notify when a workflow element named <code>weid</code> is created.
   *
   * @param we        the workflow element. If <code>null</code> then observer
   *                  is deregistered with a list of workflow element
   *                  identifiers.
   * @param weid      the identifier of the workflow element. This parameter
   *                  is useless when <code>we</code> is not <code>null</code>
   * @param observer  the observer to deregister
   */
  public static void deregisterForStateUpdate(WorkflowElement we, String weid, Observer observer)
  {
    // If the workflow element "we" exists then deregister the observer
    // with "we" else deregister the observer with a static list of
    // workflow element identifiers
    if (we != null)
    {
      we.getObservableState().deleteObserver(observer);
    }

    if (weid != null)
    {
      List<Observer> observers = newWorkflowElementNotificationList.get(weid);
      if (observers != null)
      {
        observers.remove(observer);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void update(Observable o, Object arg)
  {
    // By default, do nothing when notification hits the workflow element.
  }

  /**
   * Performs any finalization that might be required when this
   * workflow element is unloaded.  No action is taken in the default
   * implementation.
   */
  public abstract void finalizeWorkflowElement();

  /**
   * Executes the workflow element for an operation.
   *
   * @param operation the operation to execute
   *
   * @throws CanceledOperationException if this operation should be
   * canceled
   */
  public abstract void execute(Operation operation)
      throws CanceledOperationException;


  /**
   * Indicates whether the workflow element encapsulates a private
   * local backend.
   *
   * @return <code>true</code> if the workflow element encapsulates a private
   *         local backend, <code>false</code> otherwise
   */
  public abstract boolean isPrivate();

  /**
   * Provides the workflow element identifier.
   *
   * @return the workflow element identifier
   */
  public abstract String getWorkflowElementID();
}
