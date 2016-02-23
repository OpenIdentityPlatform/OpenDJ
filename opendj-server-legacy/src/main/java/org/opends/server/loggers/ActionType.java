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
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.loggers;



/**
 * This enumeration defines the post rotation actions possible.
 */
enum ActionType
{
  /**
   * The action type that indicates the rotated file should be compressed using
   * the gzip mechanism.
   */
  GZIP_COMPRESS("GZIP COMPRESSION"),



  /**
   * The action type that indicates the rotated file should be compressed using
   * the zip mechanism.
   */
  ZIP_COMPRESS("ZIP COMPRESSION"),



  /**
   * The action type that indicates the rotated file should be encrypted.
   */
  ENCRYPT("ENCRYPT"),



  /**
   * The action type that indicates the rotated file should be digitally signed.
   */
  SIGN("SIGN"),



  /**
   * An invalid rotation action type.
   */
  INVALID("INVALID");


  private String action;

  /**
   * Creates the action type.
   *
   * @param  action  The action.
   */
  private ActionType(String action)
  {
    this.action = action;
  }


  /**
   * Retrieves a string representation of this action.
   *
   * @return  A string representation of this action.
   */
  public String toString()
  {
    return action;
  }
}

