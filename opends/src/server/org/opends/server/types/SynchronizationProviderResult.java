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
package org.opends.server.types;


/**
 * This class defines a data structure that holds information about
 * the result of processing by a synchronization provider.
 */
public class SynchronizationProviderResult
{



  // Indicates whether processing should continue on the operation.
  private boolean continueOperationProcessing;



  /**
   * Creates a new synchronization provider result with the provided
   * information.
   *
   * @param  continueOperationProcessing  Indicates whether processing
   *                                      should continue on the
   *                                      associated operation.
   */
  public SynchronizationProviderResult(
              boolean continueOperationProcessing)
  {
    this.continueOperationProcessing = continueOperationProcessing;
  }



  /**
   * Indicates whether processing on the associated operation should
   * continue.
   *
   * @return  <CODE>true</CODE> if processing on the associated
   *          operation should continue, or <CODE>false</CODE> if it
   *          should stop.
   */
  public boolean continueOperationProcessing()
  {
    return continueOperationProcessing;
  }



  /**
   * Specifies whether processing on the associated operation should
   * continue.
   *
   * @param  continueOperationProcessing  Indicates whether processing
   *                                      should continue on the
   *                                      associated operation.
   */
  public void setContinueOperationProcessing(
                   boolean continueOperationProcessing)
  {
    this.continueOperationProcessing = continueOperationProcessing;
  }



  /**
   * Retrieves a string representation of this post-response plugin
   * result.
   *
   * @return  A string representation of this post-response plugin
   *          result.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this post-response plugin
   * result to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("SynchronizationProviderResult(" +
                  "continueOperationProcessing=");
    buffer.append(continueOperationProcessing);
    buffer.append(")");
  }
}

