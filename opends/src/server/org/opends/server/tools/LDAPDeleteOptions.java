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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines options for all the delete operations used
 * by the ldapdelete tool.
 */
public class LDAPDeleteOptions extends LDAPToolOptions
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.LDAPDeleteOptions";

  private boolean deleteSubtree = false;

  /**
   * Creates the options instance.
   *
   */
  public LDAPDeleteOptions()
  {
    assert debugConstructor(CLASS_NAME);
  }

  /**
   * Set whether to delete the entire subtree or not.
   *
   * @param  deleteSubtree  Indicates whether to delete the entire subtree.
   */
  public void setDeleteSubtree(boolean deleteSubtree)
  {
    this.deleteSubtree = deleteSubtree;
  }

  /**
   * Get the value of the deleteSubtree flag.
   *
   * @return  <CODE>true</CODE> if the subtree delete control should be
   *          included in the request, or <CODE>false</CODE> if not.
   */
  public boolean getDeleteSubtree()
  {
    return deleteSubtree;
  }
}

