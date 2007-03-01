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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.config.ConfigAttribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InvokableMethod;




/**
 * This class defines an interface that may be implemented by
 * Directory Server components that have methods that may be invoked
 * either via adding configuration entries (e.g., task plugins) or
 * through JMX.
 */
public interface InvokableComponent
{
  /**
   * Retrieves the DN of the configuration entry with which this
   * component is associated.
   *
   * @return  The DN of the configuration entry with which this
   *          component is associated.
   */
  public DN getInvokableComponentEntryDN();



  /**
   * Retrieves a list of the methods that may be invoked for this
   * component.
   *
   * @return  A list of the methods that may be invoked for this
   *          component.
   */
  public InvokableMethod[] getOperationSignatures();



  /**
   * Invokes the specified method with the provided arguments.
   *
   * @param  methodName  The name of the method to invoke.
   * @param  arguments   The set of configuration attributes holding
   *                     the arguments to use for the method.
   *
   * @return  The return value for the method, or <CODE>null</CODE>
   *          if it did not return a value.
   *
   * @throws  DirectoryException  If there was no such method, or if
   *                              an error occurred while attempting
   *                              to invoke it.
   */
  public Object invokeMethod(String methodName,
                             ConfigAttribute[] arguments)
         throws DirectoryException;
}

