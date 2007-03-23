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



/**
 * A default behavior provider which indicates undefined behavior. It should
 * be used by properties which have no default values or behavior as such. For
 * example, a description property, when left unset, has no default value and no
 * side-effects.
 *
 * @param <T>
 *          The type of values represented by this provider.
 */
public final class UndefinedDefaultBehaviorProvider<T> implements
    DefaultBehaviorProvider<T> {

  /**
   * Create an undefined default behavior provider.
   */
  public UndefinedDefaultBehaviorProvider() {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
    return v.visitUndefined(this, p);
  }

}
