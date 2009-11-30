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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldif;

import org.opends.sdk.DN;



/**
 * A request to modify the content of the Directory in some way. A
 * change record represents one of the following operations:
 * <ul>
 * <li>An {@code Add} operation.
 * <li>An {@code Delete} operation.
 * <li>An {@code Modify} operation.
 * <li>An {@code ModifyDN} operation.
 * </ul>
 */
public interface ChangeRecord
{
  /**
   * Applies a {@code ChangeRecordVisitor} to this {@code ChangeRecord}.
   *
   * @param <R>
   *          The return type of the visitor's methods.
   * @param <P>
   *          The type of the additional parameters to the visitor's
   *          methods.
   * @param v
   *          The change record visitor.
   * @param p
   *          Optional additional visitor parameter.
   * @return A result as specified by the visitor.
   */
  <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);



  /**
   * Returns the distinguished name of the entry being modified by this
   * {@code ChangeRecord}.
   *
   * @return The distinguished name of the entry being modified.
   */
  DN getName();
}
