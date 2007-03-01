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
package org.opends.server.protocols.jmx;

import java.security.Principal;

/**
 * Represents a Ldap authentication ID used for JMX connection authentication.
 */
public class OpendsJmxPrincipal implements Principal
{
  /**
   * The authentication ID.
   */
  private String authID;


  /**
   * Create a new OpendsJmxPrincipal object.
   *
   * @param authID The JMX Connection ID to be registered into
   * this Object.
   */
  public OpendsJmxPrincipal(String authID)
  {
    this.authID = authID;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object another)
  {
    return authID.equals(another);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return authID.hashCode() ;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return authID.toString();
  }

  /**
   * {@inheritDoc}
   */
  public String getName()
  {
    return authID;
  }
}
