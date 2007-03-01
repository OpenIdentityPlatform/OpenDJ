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

import java.util.ArrayList;
import org.opends.server.protocols.ldap.LDAPControl;




/**
 * This class defines common options for all the operations used
 * by the tools.
 */
public class LDAPToolOptions
{

  private boolean showOperations =  false;
  private boolean verbose = false;
  private boolean continueOnError = false;
  private String encoding = System.getProperty("file.encoding");
  private ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>();

  /**
   * Creates a the tool options instance.
   *
   */
  public LDAPToolOptions()
  {
  }

  /**
   * Set whether to show what would be run but not actually do it.
   *
   * @param showOperations    True if we need to show what needs to be done.
   *
   */

  public void setShowOperations(boolean showOperations)
  {
    this.showOperations = showOperations;
  }

  /**
   * Return the showOperations flag value.
   *
   * @return  <CODE>true</CODE> if the operations should only be displayed, or
   *          <CODE>false</CODE> if they should actually be performed.
   */
  public boolean showOperations()
  {
    return showOperations;
  }

  /**
   * Set verbose flag.
   *
   * @param  verbose  Indicates whether the tool should operate in verbose mode.
   */

  public void setVerbose(boolean verbose)
  {
    this.verbose = verbose;
  }

  /**
   * Return the verbose flag value.
   *
   * @return  <CODE>true</CODE> if the tool should operate in verbose mode, or
   *          <CODE>false</CODE> if not.
   */
  public boolean getVerbose()
  {
    return verbose;
  }

  /**
   * Set whether to use continue on error or not.
   *
   * @param continueOnError    True if processing should continue on
   *                           error, false otherwise.
   *
   */

  public void setContinueOnError(boolean continueOnError)
  {
    this.continueOnError = continueOnError;
  }

  /**
   * Return the continueOnError flag value.
   *
   * @return  <CODE>true</CODE> if the tool should continue processing
   *          operations if an error occurs with a previous operation, or
   *          <CODE>false</CODE> if not.
   */
  public boolean continueOnError()
  {
    return continueOnError;
  }

  /**
   * Return the controls to apply to the operation.
   *
   * @return  The controls to apply to the operation.
   */
  public ArrayList<LDAPControl> getControls()
  {
    return controls;
  }

  /**
   * Specifies the set of controls to apply to the operation.
   *
   * @param  controls  The set of controls to apply to the operation.
   */
  public void setControls(ArrayList<LDAPControl> controls)
  {
    this.controls = controls;
  }

  /**
   * Set the encoding.
   *
   * @param  encodingStr  The encoding to use for string values.
   */
  public void setEncoding(String encodingStr)
  {
    this.encoding = encodingStr;
  }

  /**
   * Return the encoding value.
   *
   * @return  The encoding to use for string values.
   */
  public String getEncoding()
  {
    return encoding;
  }

}

