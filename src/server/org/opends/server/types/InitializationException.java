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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;




/**
 * This class defines an exception that may be thrown if a problem
 * occurs while trying to initialize a Directory Server component.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class InitializationException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>,
   * which implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  private static final long serialVersionUID = -6121147544833914730L;





  /**
   * Creates a new initialization exception with the provided message.
   *
   * @param  message    The message that explains the problem that
   *                    occurred.
   */
  public InitializationException(Message message)
  {
    super(message);
  }



  /**
   * Creates a new initialization exception with the provided message
   * and root cause.
   *
   * @param  message    The message that explains the problem that
   *                    occurred.
   * @param  cause      The exception that was caught to trigger this
   *                    exception.
   */
  public InitializationException(Message message,
                                 Throwable cause)
  {
    super(message, cause);
  }

}

