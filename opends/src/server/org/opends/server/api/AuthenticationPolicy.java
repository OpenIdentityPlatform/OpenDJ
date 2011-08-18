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
 *      Copyright 2011 ForgeRock AS.
 */

package org.opends.server.api;



import org.opends.server.types.DN;



/**
 * An abstract authentication policy.
 */
public abstract class AuthenticationPolicy
{
  /**
   * Creates a new abstract authentication policy.
   */
  protected AuthenticationPolicy()
  {
    // No implementation required.
  }



  /**
   * Returns the name of the configuration entry associated with this
   * authentication policy.
   *
   * @return The name of the configuration entry associated with this
   *         authentication policy.
   */
  public abstract DN getDN();



  /**
   * Performs any necessary work to finalize this authentication policy.
   * <p>
   * The default implementation is to do nothing.
   */
  public void finalizeAuthenticationPolicy()
  {
    // Do nothing by default.
  }
}
