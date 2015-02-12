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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.tools.makeldif;




import org.opends.server.types.IdentifiedException;
import org.forgerock.i18n.LocalizableMessage;


/**
 * This class defines an exception that can be thrown if a problem occurs during
 * MakeLDIF processing.
 */
public class MakeLDIFException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = -918795152848233269L;



  /**
   * Creates a new MakeLDIF exception with the provided information.
   *
   * @param  message  The message for this exception.
   */
  public MakeLDIFException(LocalizableMessage message)
  {
    super(message);
  }



  /**
   * Creates a new MakeLDIF exception with the provided information.
   *
   * @param  message  The message for this exception.
   * @param  cause    The underlying cause for this exception.
   */
  public MakeLDIFException(LocalizableMessage message, Throwable cause)
  {
    super(message, cause);
  }

}

