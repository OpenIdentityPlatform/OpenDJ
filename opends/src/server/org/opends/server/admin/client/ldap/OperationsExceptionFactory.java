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

package org.opends.server.admin.client.ldap;



import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.OperationsException;



/**
 * A factory for creating <code>OperationsExceptions</code> from
 * <code>NamingExceptions</code>.
 */
final class OperationsExceptionFactory {

  /**
   * Creates a new operations exception factory.
   */
  public OperationsExceptionFactory() {
    // No implementation required.
  }



  /**
   * Creates a new operations exception whose exact type depends upon the
   * provided naming exception.
   *
   * @param e
   *          The naming exception.
   * @return Returns a new operations exception whose exact type depends upon
   *         the provided naming exception.
   */
  public OperationsException createException(NamingException e) {
    if (e instanceof NameNotFoundException) {
      return new ManagedObjectNotFoundException(e);
    } else {
      // FIXME: resolve other naming exceptions.
      return new OperationsException(e);
    }
  }
}
