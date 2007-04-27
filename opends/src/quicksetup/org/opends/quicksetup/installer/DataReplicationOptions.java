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


package org.opends.quicksetup.installer;

/**
 * This class is used to provide a data model for the Data Replication
 * Options panel of the installer.
 *
 */
public class DataReplicationOptions
{
  /**
   * This enumeration is used to know what the user wants to do for the data
   * (import data or not, what use as source of the data...).
   *
   */
  public enum Type
  {
    /**
     * Standalone server.
     */
    STANDALONE,
    /**
     * Replicate Contents and this is the first server in topology..
     */
    FIRST_IN_TOPOLOGY,
    /**
     * Replicate Contents of the new Suffix with existings server.
     */
    IN_EXISTING_TOPOLOGY
  }

  private Type type;
  private AuthenticationData authenticationData;

  /**
   * Constructor for the DataReplicationOptions object.
   *
   * If the Data Replication Options is STANDALONE or FIRST_IN_TOPOLOGY no
   * args are considered.
   *
   * If the Data Options is IN_EXISTING_TOPOLOGY the args is the authentication
   * data on the remote server (AuthenticationData object).
   *
   * @param type the Type of DataReplicationOptions.
   * @param args the different argument objects (depending on the Type
   * specified)
   */
  public DataReplicationOptions(Type type, Object... args)
  {
    this.type = type;

    switch (type)
    {
    case IN_EXISTING_TOPOLOGY:
      authenticationData = (AuthenticationData)args[0];
      break;

    default:
      // If there is something put it.
      if ((args != null) && (args.length > 0))
      {
        authenticationData = (AuthenticationData)args[0];
      }
    }
  }

  /**
   * Returns the type of DataReplicationOptions represented by this object
   * (replicate or not).
   *
   * @return the type of DataReplicationOptions.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the AuthenticationData to the server used to replicate.
   * If it is standalone returns null.
   *
   * @return the AuthenticationData to the server used to replicate.
   */
  public AuthenticationData getAuthenticationData()
  {
    return authenticationData;
  }
}

