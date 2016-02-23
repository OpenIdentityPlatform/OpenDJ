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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.tools;




/**
 * This class defines options for all the modify operations used
 * by the ldapmodify tool.
 */
public class LDAPModifyOptions extends LDAPToolOptions
{

  private boolean defaultAdd;

  /**
   * Creates the options instance.
   *
   */
  public LDAPModifyOptions()
  {
  }

  /**
   * Set whether to default to adding entries if no changetype is provided.
   *
   * @param  defaultAdd  If entries with no changetype should be considered add
   *                     requests.
   */
  public void setDefaultAdd(boolean defaultAdd)
  {
    this.defaultAdd = defaultAdd;
  }

  /**
   * Get the value of the defaultAdd flag.
   *
   * @return  <CODE>true</CODE> if a default changetype of "add" should be used,
   *          or <CODE>false</CODE> if not.
   */
  public boolean getDefaultAdd()
  {
    return defaultAdd;
  }
}

