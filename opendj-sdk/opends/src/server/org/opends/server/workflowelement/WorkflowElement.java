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


import java.util.List;
import org.opends.server.admin.std.server.WorkflowElementCfg;
import org.opends.server.types.Operation;
import org.opends.server.types.CanceledOperationException;


/**
 * This class defines the super class for all the workflow elements. A workflow
 * element is a task in a workflow. A workflow element can wrap a physical
 * repository such as a local backend, a remote LDAP server or a local LDIF
 * file. A workflow element can also be used to route operations. This is the
 * case for load balancing and distribution. And workflow element can be used
 * in a virtual environment to transform data (DN and attribute renaming,
 * attribute value renaming...).
 *
 * @param  <T>  The type of configuration handled by this workflow element.
 */
public abstract class WorkflowElement
       <T extends WorkflowElementCfg>
{
  // Indicates whether the workflow element encapsulates a private local
  // backend.
  private boolean isPrivate = false;


  // An information indicating the type of the current workflow element.
  // This information is for debug and tooling purpose only.
  private String workflowElementTypeInfo = "not defined";


  // The workflow element identifier.
  private String workflowElementID = null;


  /**
   * Creates a new instance of the workflow element.
   */
  public WorkflowElement()
  {
    // There is nothing to do in the constructor.
  }

  /**
   * Initializes the instance of the workflow element.
   *
   * @param workflowElementID  the workflow element identifier as defined
   *                           in the configuration.
   * @param workflowElementTypeInfo  an information to indicate the type of
   *                                 the current workflow element. For example
   *                                 "Backend" if the current workflow element
   *                                 is a local backend workflow element.
   */
  public void initialize(
      String workflowElementID,
      String workflowElementTypeInfo)
  {
    this.workflowElementID = workflowElementID;
    this.workflowElementTypeInfo = workflowElementTypeInfo;
  }


  /**
   * Get the type of the workflow element. The type is a string information
   * indicating which type is the current workflow element. This information
   * is intended to be used by tools for trace and debug purpose.
   *
   * @return the type of the workflow element.
   */
  public String getWorkflowElementTypeInfo()
  {
    return this.workflowElementTypeInfo;
  }


  /**
   * Indicates whether the provided configuration is acceptable for
   * this workflow element.
   *
   * @param  configuration        The workflow element configuration for
   *                              which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this workflow element, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
      T configuration,
      List<String> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by workflow element
    // implementations that wish to perform more detailed validation.
    return true;
  }


  /**
   * Performs any finalization that might be required when this
   * workflow element is unloaded.  No action is taken in the default
   * implementation.
   */
  public void finalizeWorkflowElement()
  {
    // No action is required by default.
  }

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
  public boolean isPrivate()
  {
    return isPrivate;
  }


  /**
   * Specifies whether the workflow element encapsulates a private local
   * backend.
   *
   * @param  isPrivate  Indicates whether the workflow element encapsulates a
   *                    private local backend.
   */
  protected void setPrivate(boolean isPrivate)
  {
    this.isPrivate = isPrivate;
  }


  /**
   * Provides the workflow element identifier.
   *
   * @return the workflow element identifier
   */
  public String getWorkflowElementID()
  {
    return workflowElementID;
  }
}

