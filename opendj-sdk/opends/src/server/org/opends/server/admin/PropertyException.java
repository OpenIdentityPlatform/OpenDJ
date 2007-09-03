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

package org.opends.server.admin;



import org.opends.messages.Message;



/**
 * Exceptions thrown as a result of errors that occurred when decoding
 * and modifying property values.
 */
public abstract class PropertyException extends AdminRuntimeException {

  /**
   * Version ID required by serializable classes.
   */
  private static final long serialVersionUID = -8465109598081914482L;

  // The property definition associated with the property that caused
  // the exception.
  private final PropertyDefinition<?> pd;



  /**
   * Creates property exception without a cause.
   *
   * @param pd
   *          The property definition associated with the property
   *          that caused the exception.
   * @param message
   *          The message.
   */
  protected PropertyException(PropertyDefinition<?> pd, Message message) {
    super(message);
    this.pd = pd;
  }



  /**
   * Creates property exception with a cause.
   *
   * @param pd
   *          The property definition associated with the property
   *          that caused the exception.
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  protected PropertyException(PropertyDefinition<?> pd, Message message,
      Throwable cause) {
    super(message, cause);
    this.pd = pd;
  }



  /**
   * Get the property definition associated with the property that
   * caused the exception.
   *
   * @return Returns the property definition associated with the
   *         property that caused the exception.
   */
  public final PropertyDefinition<?> getPropertyDefinition() {
    return pd;
  }

}
