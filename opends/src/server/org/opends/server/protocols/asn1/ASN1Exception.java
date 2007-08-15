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
package org.opends.server.protocols.asn1;
import org.opends.messages.Message;



import org.opends.server.types.IdentifiedException;



/**
 * This class defines an exception that may be thrown if a problem occurs while
 * interacting with an ASN.1 element.
 */
public class ASN1Exception
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = -2640197609704069110L;





  /**
   * Creates a new ASN.1 exception with the provided message.
   *
   * @param  message    The message that explains the problem that occurred.
   */
  public ASN1Exception(Message message)
  {
    super(message);
  }



  /**
   * Creates a new ASN.1 exception with the provided message and root
   * cause.
   *
   * @param  message    The message that explains the problem that occurred.
   * @param  cause      The exception that was caught to trigger this exception.
   */
  public ASN1Exception(Message message, Throwable cause)
  {
    super(message, cause);
  }



}

