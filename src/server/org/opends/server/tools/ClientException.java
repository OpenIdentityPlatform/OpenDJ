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
package org.opends.server.tools;
import org.opends.messages.Message;



import org.opends.server.types.IdentifiedException;



/**
 * This class defines an exception that may be thrown if a local problem occurs
 * in a Directory Server client.
 */
public class ClientException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = 1384120263337669664L;



  // The exit code that may be used if the client considers this to be a fatal
  // problem.
  private int exitCode;



  /**
   * Creates a new client exception with the provided message.
   *
   * @param  exitCode   The exit code that may be used if the client considers
   *                    this to be a fatal problem.
   * @param  message    The message that explains the problem that occurred.
   */
  public ClientException(int exitCode, Message message)
  {
    super(message);

    this.exitCode  = exitCode;
  }



  /**
   * Creates a new client exception with the provided message and root cause.
   *
   * @param  exitCode   The exit code that may be used if the client considers
   *                    this to be a fatal problem.
   * @param  message    The message that explains the problem that occurred.
   * @param  cause      The exception that was caught to trigger this exception.
   */
  public ClientException(int exitCode, Message message, Throwable cause)
  {
    super(message, cause);

    this.exitCode  = exitCode;
  }



  /**
   * Retrieves the exit code that the client may use if it considers this to be
   * a fatal problem.
   *
   * @return  The exit code that the client may use if it considers this to be
   *          a fatal problem.
   */
  public int getExitCode()
  {
    return exitCode;
  }


}

