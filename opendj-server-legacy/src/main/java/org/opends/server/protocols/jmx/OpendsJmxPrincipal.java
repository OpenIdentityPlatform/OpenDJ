/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import java.security.Principal;

/** Represents a Ldap authentication ID used for JMX connection authentication. */
public class OpendsJmxPrincipal implements Principal
{
  /** The authentication ID. */
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

  @Override
  public boolean equals(Object another)
  {
    return authID.equals(another);
  }

  @Override
  public int hashCode()
  {
    return authID.hashCode() ;
  }

  @Override
  public String toString()
  {
    return authID;
  }

  @Override
  public String getName()
  {
    return authID;
  }
}
