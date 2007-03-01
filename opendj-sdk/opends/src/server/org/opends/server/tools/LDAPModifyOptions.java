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
package org.opends.server.tools;




/**
 * This class defines options for all the modify operations used
 * by the ldapmodify tool.
 */
public class LDAPModifyOptions extends LDAPToolOptions
{

  private boolean defaultAdd = false;

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

