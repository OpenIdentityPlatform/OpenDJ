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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.TreePath;

import org.opends.server.types.LDAPURL;

/** Interface used in the LDAP entries browser code to deal with entries. */
public interface BrowserNodeInfo {

  /**
   * URL of the displayed entry.
   * @return the URL of the displayed entry.
   */
  LDAPURL getURL();


  /**
   * Returns  <CODE>true</CODE> if the displayed entry is the top entry of a
   * suffix and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the displayed entry is the top entry of a
   * suffix and <CODE>false</CODE> otherwise.
   */
  boolean isSuffix();


  /**
   * Returns <CODE>true</CODE> if the displayed entry is the root node of the
   * server (the dn="" entry) and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the displayed entry is the root node of the
   * server (the dn="" entry) and <CODE>false</CODE> otherwise.
   */
  boolean isRootNode();

  /**
   * Returns <CODE>true</CODE> if the displayed entry is not located on the
   * current server. An entry is declared 'remote' when the host/port of
   * getURL() is different from the host/port of the DirContext associated to
   * the browser controller. Returns <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the displayed entry is not located on the
   * current server. An entry is declared 'remote' when the host/port of
   * getURL() is different from the host/port of the DirContext associated to
   * the browser controller. Returns <CODE>false</CODE> otherwise.
   */
  boolean isRemote();


  /**
   * Returns the value of numsubordinates for the entry.
   * -1 if the numsubordinates attribute is not defined.
   * @return the value of numsubordinates for the entry.
   */
  int getNumSubOrdinates();


  /**
   * Returns the value of hassubordinates for the entry.
   * @return the value of hassubordinates for the entry.
   */
  boolean hasSubOrdinates();

  /**
   * Returns the referrals attached to the displayed entry.
   * This is the value of the 'ref' attribute.
   * Returns <CODE>null</CODE> if the attribute is not present.
   * @return the referrals attached to the displayed entry.
   */
  String[] getReferral();


  /**
   * Returns the error detected while reading this entry.
   * @return the error detected while reading this entry.
   */
  int getErrorType();


  /**
   * Returns the exception associated to the error.
   * Returns <CODE>null</CODE> if getErrorType() == ERROR_NONE.
   * @return the exception associated to the error.
   */
  Exception getErrorException();


  /**
   * Returns the argument associated to an error/exception.
   * Always null except when errorType == ERROR_SOLVING_REFERRAL,
   * errorArg contains the String representing the faulty LDAP URL.
   * @return the argument associated to an error/exception.
   */
  Object getErrorArg();

  /**
   * Returns the basic node associated with the node info.
   * @return the basic node associated with the node info.
   */
  BasicNode getNode();


  /**
   * Returns the TreePath corresponding to the displayed entry.
   * @return the TreePath corresponding to the displayed entry.
   */
  TreePath getTreePath();


  /**
   * Tells whether the node passed as parameter represents the same node as this
   * one.
   * @param node the node.
   * @return <CODE>true</CODE> if the node passed as parameter represents the
   * same node as this one and <CODE>false</CODE> otherwise.
   */
  boolean representsSameNode(BrowserNodeInfo node);

  /**
   * Returns the object class value of the entry that the nodes represents.
   * @return the object class value of the entry that the nodes represents.
   */
  String[] getObjectClassValues();

  /** Error types. */
  /** No error happened. */
  int ERROR_NONE          = 0;
  /** And error reading the entry occurred. */
  int ERROR_READING_ENTRY     = 1;
  /** An error following referrals occurred. */
  int ERROR_SOLVING_REFERRAL    = 2;
  /** An error occurred searching the children of the entry. */
  int ERROR_SEARCHING_CHILDREN  = 3;

}
