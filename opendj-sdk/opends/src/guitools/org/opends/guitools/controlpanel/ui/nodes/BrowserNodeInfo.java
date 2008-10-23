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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.TreePath;

import org.opends.server.types.LDAPURL;

/**
 * Interface used in the LDAP entries browser code to deal with entries.
 *
 */
public interface BrowserNodeInfo {

  /**
   * URL of the displayed entry.
   * @return the URL of the displayed entry.
   */
  public LDAPURL getURL();


  /**
   * Returns  <CODE>true</CODE> if the displayed entry is the top entry of a
   * suffix and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the displayed entry is the top entry of a
   * suffix and <CODE>false</CODE> otherwise.
   */
  public boolean isSuffix();


  /**
   * Returns <CODE>true</CODE> if the displayed entry is the root node of the
   * server (the dn="" entry) and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the displayed entry is the root node of the
   * server (the dn="" entry) and <CODE>false</CODE> otherwise.
   */
  public boolean isRootNode();

  /**
   * Returns <CODE>true</CODE> if the displayed entry is not located on the
   * current server. An entry is declared 'remote' when the host/port of
   * getURL() is different from the host/port of the DirContext associated to
   * the browser controller. Returns <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the displayed entry is not located on the
   * current server. An entry is declared 'remote' when the host/port of
   * getURL() is different from the host/port of the DirContext associated to
   * the browser controller. Returns <CODE>false</CODE> otherwise.
   *
   */
  public boolean isRemote();


  /**
   * Returns the value of numsubordinates for the entry.
   * -1 if the numsubordinates attribute is not defined.
   * @return the value of numsubordinates for the entry.
   */
  public int getNumSubOrdinates();


  /**
   * Returns the referrals attached to the displayed entry.
   * This is the value of the 'ref' attribute.
   * Returns <CODE>null</CODE> if the attribute is not present.
   * @return the referrals attached to the displayed entry.
   */
  public String[] getReferral();


  /**
   * Returns the error detected while reading this entry.
   * @return the error detected while reading this entry.
   */
  public int getErrorType();


  /**
   * Returns the exception associated to the error.
   * Returns <CODE>null</CODE> if getErrorType() == ERROR_NONE.
   * @return the exception associated to the error.
   */
  public Exception getErrorException();


  /**
   * Returns the argument associated to an error/exception.
   * Always null except when errorType == ERROR_SOLVING_REFERRAL,
   * errorArg contains the String representing the faulty LDAP URL.
   * @return the argument associated to an error/exception.
   */
  public Object getErrorArg();

  /**
   * Returns the basic node associated with the node info.
   * @return the basic node associated with the node info.
   */
  public BasicNode getNode();


  /**
   * Returns the TreePath corresponding to the displayed entry.
   * @return the TreePath corresponding to the displayed entry.
   */
  public TreePath getTreePath();


  /**
   * Tells wether the node passed as parameter represents the same node as this
   * one.
   * @param node the node.
   * @return <CODE>true</CODE> if the node passed as parameter represents the
   * same node as this one and <CODE>false</CODE> otherwise.
   */
  public boolean representsSameNode(BrowserNodeInfo node);

  /**
   * Returns the object class value of the entry that the nodes represents.
   * @return the object class value of the entry that the nodes represents.
   */
  public String[] getObjectClassValues();

  /**
   * Error types
   */
  /**
   * No error happened.
   */
  public static final int ERROR_NONE          = 0;
  /**
   * And error reading the entry occurred.
   */
  public static final int ERROR_READING_ENTRY     = 1;
  /**
   * An error following referrals occurred.
   */
  public static final int ERROR_SOLVING_REFERRAL    = 2;
  /**
   * An error occurred searching the children of the entry.
   */
  public static final int ERROR_SEARCHING_CHILDREN  = 3;



}
