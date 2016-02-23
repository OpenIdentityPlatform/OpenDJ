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

import java.util.ArrayList;
import org.opends.server.types.Control;


/**
 * This class defines common options for all the operations used
 * by the tools.
 */
public class LDAPToolOptions
{

  private boolean showOperations;
  private boolean verbose;
  private boolean continueOnError;
  private String encoding = System.getProperty("file.encoding");
  private ArrayList<Control> controls = new ArrayList<>();

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
  public ArrayList<Control> getControls()
  {
    return controls;
  }

  /**
   * Specifies the set of controls to apply to the operation.
   *
   * @param  controls  The set of controls to apply to the operation.
   */
  public void setControls(ArrayList<Control> controls)
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

