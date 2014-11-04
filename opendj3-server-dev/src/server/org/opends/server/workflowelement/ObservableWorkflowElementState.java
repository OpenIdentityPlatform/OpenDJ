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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2014 ForgeRock AS.
 */
package org.opends.server.workflowelement;

import java.util.Observable;

import org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;

/**
 * This class implements an observable workflow element state.
 * The observable workflow element state notifies observers when the
 * state of the workflow element has changed. Typically, observers are
 * notified when a workflow element is enabled or disabled.
 */
public class ObservableWorkflowElementState extends Observable
{
  private final LocalBackendWorkflowElement observedWorkflowElement;

  /**
   * Creates an instance of an observable object for a given workflow
   * element.
   *
   * @param  observedWorkflowElement
   *         The workflow element to observe.
   */
  public ObservableWorkflowElementState(LocalBackendWorkflowElement observedWorkflowElement)
  {
    this.observedWorkflowElement = observedWorkflowElement;
  }

  /**
   * Gets the observed workflow element.
   *
   * @return the observed workflow element.
   */
  public LocalBackendWorkflowElement getObservedWorkflowElement()
  {
    return observedWorkflowElement;
  }
}
