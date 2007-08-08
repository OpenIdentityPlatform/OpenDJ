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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement;

import org.opends.server.types.Operation;


/**
 * This class defines the super class for all the workflow elements. A workflow
 * element is a task in a workflow. A workflow element can wrap a physical
 * repository such as a local backend, a remote LDAP server or a local ldif
 * file. A workflow element can also be used to route operations. This is the
 * case for load balancing and distribution. And workflow element can be used
 * in a virtual environment to transform data (DN and attribute renaming,
 * attribute value renaming...).
 */
public abstract class WorkflowElement
{

  // The workflow element identifier.
  private String workflowElementID = null;


  /**
   * Indicates whether the workflow element encapsulates a private
   * local backend.
   */
  protected boolean isPrivate = false;


  /**
   * Creates a new instance of the workflow element.
   *
   * @param workflowElementID  the workflow element identifier as defined
   *                           in the configuration.
   */
  public WorkflowElement(String workflowElementID)
  {
    this.workflowElementID = workflowElementID;
  }


  /**
   * Executes the workflow element for an operation.
   *
   * @param operation the operation to execute
   */
  public abstract void execute(
      Operation operation
      );


  /**
   * Indicates whether the workflow element encapsulates a private
   * local backend.
   *
   * @return <code>true</code> if the workflow element encapsulates a private
   *         local backend, <code>false</code> otherwise
   */
  public boolean isPrivate()
  {
    return isPrivate;
  }


  /**
   * Provides the workflow element identifier.
   *
   * @return the worflow element identifier
   */
  public String getWorkflowElementID()
  {
    return workflowElementID;
  }
}
