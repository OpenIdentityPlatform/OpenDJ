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
 * This class implements an observable workflow element state.
 * The observable workflow element state notifies observers when the
 * state of the workflow element has changed. Typically, observers are
 * notified when a workflow element is enabled or disabled.
 */
public class ObservableWorkflowElementState
    extends ObservableWorkflowElement
{
  // The "enabled" state of the observed workflow element.
  // By default, a workflow element is enabled (otherwise this
  // instance of workflow element state would not exist).
  private boolean enabled = true;


  /**
   * Creates an instance of an observable object for a given workflow
   * element.
   *
   * @param  observedWorkflowElement
   *         The workflow element to observe.
   */
  public ObservableWorkflowElementState(
      WorkflowElement<?> observedWorkflowElement)
  {
    super(observedWorkflowElement);
  }


  /**
   * Allows the observed workflow element to indicate its new state
   * (enabled or disabled).
   *
   * @param enabled  the new "enabled" state of the observed workflow element
   */
  public void setWorkflowElementEnabled(
      boolean enabled)
  {
    if (this.enabled != enabled)
    {
      setChanged();
      this.enabled = enabled;
    }
  }


  /**
   * Indicates whether the observed workflow element is enabled or not.
   *
   * @return <code>true</code> if the observed workflow element is enabled.
   */
  public boolean workflowElementIsEnabled()
  {
    return enabled;
  }
}
