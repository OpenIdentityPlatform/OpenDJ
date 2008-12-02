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
package org.opends.server.workflowelement;


/**
 * This class implements an observable workflow element status.
 * The observable workflow element status notifies observers when
 * the status of the workflow element has changed.
 * <p>
 * The status of a workflow reflects the "health" of the workflow element.
 * The measure of the health is defined by an index - the saturation index -
 * whose value may vary within the range [0 - 100].
 * <p>
 * An index value of 100 means that the workflow element fully operational.
 * An index value of 0 means that the workflow element is no more operational.
 * An value in between means that the workflow element is in a degraded mode.
 * The lower the index value, the more degraded the workflow element is.
 */
public class ObservableWorkflowElementStatus
    extends ObservableWorkflowElement
{
  /**
   * The health indicator (aka saturation index) of the workflow element.
   */
  private int saturationIndex = 100;
  private Object saturationIndexLock = new Object();


  /**
   * Creates an instance of an observable status for a given workflow
   * element.
   *
   * @param  observedWorkflowElement
   *         The workflow element which exposes its status.
   */
  public ObservableWorkflowElementStatus(
      WorkflowElement<?> observedWorkflowElement
      )
  {
    super(observedWorkflowElement);
  }


  /**
   * Provides the saturation index of the workflow element.
   *
   * @return the saturation index of the workflow element.
   */
  public int getSaturationIndex()
  {
    return saturationIndex;
  }


  /**
   * Updates the saturation index of the workflow element. Once the
   * index has been updated, all the observers registered with this
   * index are notified.
   *
   * @param  newValue
   *         The new value of the saturation index.
   */
  public void setSaturationIndex(int newValue)
  {
    synchronized (saturationIndexLock)
    {
      if (newValue != saturationIndex)
      {
        this.saturationIndex = newValue;
        setChanged();
      }
    }

    // If the value has been updated then notify all the observers.
    if (hasChanged())
    {
      notifyObservers();
    }
  }
}
