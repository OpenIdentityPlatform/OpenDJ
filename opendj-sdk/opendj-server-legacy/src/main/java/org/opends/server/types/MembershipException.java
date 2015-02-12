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
package org.opends.server.types;

import org.forgerock.i18n.LocalizableMessage;


/**
 * This class defines an exception that may be thrown if a problem
 * occurs while attempting to iterate across the members of a group.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class MembershipException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>,
   * which implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  private static final long serialVersionUID = -7312072056288770065L;



  /**
   * Indicates whether it is possible to continue iterating through
   * the list of group members.
   */
  private final boolean continueIterating;





  /**
   * Creates a new membership exception with the provided information.
   *
   * @param  errorMessage       The error message for this membership
   *                            exception.
   * @param  continueIterating  Indicates whether it is possible to
   *                            continue iterating through the list of
   *                            group members.
   */
  public MembershipException(LocalizableMessage errorMessage,
                             boolean continueIterating)
  {
    super(errorMessage);

    this.continueIterating = continueIterating;
  }



  /**
   * Creates a new membership exception with the provided information.
   *
   * @param  errorMessage       The error message for this membership
   *                            exception.
   * @param  continueIterating  Indicates whether it is possible to
   *                            continue iterating through the list of
   *                            group members.
   * @param  cause              The underlying cause for this
   *                            membership exception.
   */
  public MembershipException(LocalizableMessage errorMessage,
                             boolean continueIterating,
                             Throwable cause)
  {
    super(errorMessage, cause);


    this.continueIterating = continueIterating;
  }



  /**
   * Retrieves the error message for this membership exception.
   *
   * @return  The error message for this membership exception.
   */
  public LocalizableMessage getErrorMessage()
  {
    return getMessageObject();
  }



  /**
   * Indicates whether it is possible to continue iterating through
   * the list of group members.
   *
   * @return  {@code true} if it is possible to continue iterating
   *          through the list of group members, or {@code false} if
   *          not.
   */
  public boolean continueIterating()
  {
    return continueIterating;
  }
}

