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

package org.opends.server.types;

import org.opends.messages.Message;

/**
 * This class defines an exception that is thrown in the case of
 * problems with encryption key managagment, and is a wrapper for a
 * variety of other cipher related exceptions.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public class CryptoManagerException extends OpenDsException {
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>,
   * which implements the <CODE>java.io.Serializable</CODE>
   * interface. This value was generated using the
   * <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  static final long serialVersionUID = -5890763923778143774L;

  /**
   * Creates an exception with the given message.
   * @param message the message message.
   */
  public CryptoManagerException(Message message) {
    super(message);
   }

  /**
   * Creates an exception with the given message and underlying
   * cause.
   * @param message The message message.
   * @param cause  The underlying cause.
   */
  public CryptoManagerException(Message message, Exception cause) {
    super(message, cause);
  }
}
