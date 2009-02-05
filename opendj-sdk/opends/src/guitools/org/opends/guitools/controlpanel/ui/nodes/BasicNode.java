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

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

import org.opends.guitools.controlpanel.browser.BasicNodeError;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.RDN;

/**
 * The basic node used to render entries in the 'Manage Entries' tree.
 *
 */
public class BasicNode extends DefaultMutableTreeNode {

  private static final long serialVersionUID = 5441658731908509872L;
  private String localDn;
  private String localRdn;
  private String localRdnWithAttributeName;
  private LDAPURL remoteUrl;
  private String remoteRdn;
  private String remoteRdnWithAttributeName;

  private boolean isLeaf;
  private boolean refreshNeededOnExpansion = true;
  private boolean obsolete;
  private BasicNodeError error;

  private String[] referral;
  private int numSubOrdinates;

  private String displayName;
  private Icon icon;
  private int fontStyle;

  private boolean sizeLimitReached = false;

  private String[] objectClassValues;

  /**
   * Constructor.
   * @param dn the DN of the entry.
   */
  public BasicNode(String dn) {
    localDn = dn;
    localRdn = extractRDN(localDn);
    localRdnWithAttributeName = extractRDN(localDn, true);
    isLeaf = true;
    refreshNeededOnExpansion = true;
    numSubOrdinates = -1;
    displayName = "";
  }


  /**
   * Returns the DN of the local entry.
   * @return the DN of the local entry.
   */
  public String getDN() {
    return localDn;
  }

  /**
   * Returns the RDN value of the local entry.
   * @return the RDN value of the local entry.
   */
  public String getRDN() {
    return localRdn;
  }

  /**
   * Returns the RDN (with the attribute name) of the local entry.
   * @return the RDN (with the attribute name) of the local entry.
   */
  public String getRDNWithAttributeName() {
    return localRdnWithAttributeName;
  }

  /**
   * Returns the URL of the remote entry (if the node does not represent a
   * referral it will be <CODE>null</CODE>).
   * @return the URL of the remote entry (if the node does not represent a
   * referral it will be <CODE>null</CODE>).
   */
  public LDAPURL getRemoteUrl() {
    return remoteUrl;
  }

  /**
   * Sets the remote URL of the node.
   * @param url the remote URL of the node.
   */
  public void setRemoteUrl(LDAPURL url) {
    remoteUrl = url;
    remoteRdn = extractRDN(remoteUrl.getRawBaseDN());
    remoteRdnWithAttributeName = extractRDN(remoteUrl.getRawBaseDN(), true);
  }

  /**
   * Sets the remote URL of the node.
   * @param url the remote URL of the node.
   */
  public void setRemoteUrl(String url) {
    try
    {
      if (url == null)
      {
        remoteUrl = null;
      }
      else
      {
        remoteUrl = LDAPURL.decode(url, false);
      }
      if (remoteUrl == null) {
        remoteRdn = null;
        remoteRdnWithAttributeName = null;
      }
      else {
        remoteRdn = extractRDN(remoteUrl.getRawBaseDN());
        remoteRdnWithAttributeName = extractRDN(remoteUrl.getRawBaseDN(), true);
      }
    }
    catch (Throwable t)
    {
      throw new IllegalArgumentException(
          "The provided url: "+url+" is not valid:"+t, t);
    }
  }

  /**
   * Returns the RDN value of the remote entry.  If the node does not
   * represent a referral it will return <CODE>null</CODE>.
   * @return the RDN value of the remote entry.
   */
  public String getRemoteRDN() {
    return remoteRdn;
  }

  /**
   * Returns the RDN value of the remote entry (with the name of the attribute).
   * If the node does not represent a referral it will return <CODE>null</CODE>.
   * @return the RDN value of the remote entry (with the name of the attribute).
   */
  public String getRemoteRDNWithAttributeName() {
    return remoteRdnWithAttributeName;
  }


  /**
   * Sets whether the node is a leaf or not.
   * @param isLeaf whether the node is a leaf or not.
   */
  public void setLeaf(boolean isLeaf) {
    this.isLeaf = isLeaf;
  }

  /**
   * Returns <CODE>true</CODE> if the node is a leaf and <CODE>false</CODE>
   * otherwise.
   * @return <CODE>true</CODE> if the node is a leaf and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isLeaf() {
    return isLeaf;
  }

  /**
   * Returns <CODE>true</CODE> if the node must be refreshed when it is expanded
   * and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the node must be refreshed when it is expanded
   * and <CODE>false</CODE> otherwise.
   */
  public boolean isRefreshNeededOnExpansion() {
    return refreshNeededOnExpansion;
  }

  /**
   * Sets whether the node must be refreshed when it is expanded or not.
   * @param refreshNeededOnExpansion  whether the node must be refreshed when it
   * is expanded or not.
   */
  public void setRefreshNeededOnExpansion(boolean refreshNeededOnExpansion) {
    this.refreshNeededOnExpansion = refreshNeededOnExpansion;
  }

  /**
   * Returns whether the node is obsolete (and must be refreshed) or not.
   * @return <CODE>true</CODE> if the node is obsolete and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isObsolete() {
    return obsolete;
  }

  /**
   * Sets whether this is node is obsolete (and must be refreshed) or not.
   * @param obsolete whether this is node is obsolete (and must be refreshed) or
   * not.
   */
  public void setObsolete(boolean obsolete) {
    this.obsolete = obsolete;
  }

  /**
   * Returns the error that occurred when updating the node.  Returns
   * <CODE>null</CODE> if no error occurred.
   * @return the error that occurred when updating the node.  Returns
   * <CODE>null</CODE> if no error occurred.
   */
  public BasicNodeError getError() {
    return error;
  }

  /**
   * Sets the error that occurred when updating the node.
   * @param error the error.
   */
  public void setError(BasicNodeError error) {
    this.error = error;
  }


  /**
   * Cached LDAP attributes
   */

  /**
   * Returns the number of subordinates of the entry.
   * @return the number of subordinates of the entry.
   */
  public int getNumSubOrdinates() {
    return numSubOrdinates;
  }

  /**
   * Sets the number of subordinates of the entry.
   * @param number the number of subordinates of the entry.
   */
  public void setNumSubOrdinates(int number) {
    numSubOrdinates = number;
  }

  /**
   * Returns the referrals of the entry. Returns <CODE>null</CODE> if this node
   * is not a referral.
   * @return the referrals of the entry. Returns <CODE>null</CODE> if this node
   * is not a referral.
   */
  public String[] getReferral() {
    return referral;
  }

  /**
   * Sets the referrals of the entry.
   * @param referral the referrals of the entry.
   */
  public void setReferral(String[] referral) {
    this.referral = referral;
  }


  /**
   * Rendering
   */
  /**
   * {@inheritDoc}
   */
  public String toString() {
    return getDisplayName();
  }

  /**
   * Returns the label that will be used to display the entry.
   * @return the label that will be used to display the entry.
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Sets the label that will be used to display the entry.
   * @param name the label that will be used to display the entry.
   */
  public void setDisplayName(String name) {
    displayName = name;
  }

  /**
   * Returns the icon associated with this node.
   * @return the icon associated with this node.
   */
  public Icon getIcon() {
    return icon;
  }


  /**
   * Sets the icon associated with this node.
   * @param icon the icon associated with this node.
   */
  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  /**
   * Returns the font style to be used to render this node.
   * @return the font style to be used to render this node.
   */
  public int getFontStyle() {
    return fontStyle;
  }

  /**
   * Sets the font style to be used to render this node.
   * @param style the font style to be used to render this node.
   */
  public void setFontStyle(int style) {
    fontStyle = style;
  }


  /**
   * Returns the object class values associated with the entry.
   * @return the object class values associated with the entry.
   */
  public String[] getObjectClassValues() {
    return objectClassValues;
  }

  /**
   * Sets the object class values associated with the entry.
   * @param objectClassValues the object class values associated with the entry.
   */
  public void setObjectClassValues(String[] objectClassValues) {
    this.objectClassValues = objectClassValues;
  }

  /**
   * Extracts the RDN value from a DN.
   * @param dn the DN.
   * @param showAttributeName whether the result must include the attribute name
   * or not.
   * @return the RDN value from the DN.
   */
  public static String extractRDN(String dn, boolean showAttributeName) {
    String result;
    if (dn == null)
    {
      result = null;
    }
    else
    {
      try
      {
        DN dnObj = DN.decode(dn);
        if (dnObj.getNumComponents() >= 1) {
          RDN rdn = dnObj.getRDN();
          if (showAttributeName)
          {
            result = rdn.toString();
          }
          else
          {
            result = rdn.getAttributeValue(0).getValue().toString();
          }
        }
        else {
          result = "";
        }
      }
      catch (Throwable t)
      {
        throw new IllegalArgumentException(
            "The provided argument is not a valid dn: "+t, t);
      }
    }
    return result;
  }

  /**
   * Extracts the RDN value from the DN.  The value does not include the name
   * of the attribute.
   * @param dn the DN.
   * @return the RDN value from the DN.
   */
  public static String extractRDN(String dn) {
    return extractRDN(dn, false);
  }


  /**
   * Returns <CODE>true</CODE> if the size limit was reached updating this node
   * (and searching its children) and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the size limit was reached updating this node
   * (and searching its children) and <CODE>false</CODE> otherwise.
   */
  public boolean isSizeLimitReached()
  {
    return sizeLimitReached;
  }


  /**
   * Sets whether the size limit was reached updating this node
   * (and searching its children).
   * @param sizeLimitReached whether the size limit was reached updating this
   * node (and searching its children).
   */
  public void setSizeLimitReached(boolean sizeLimitReached)
  {
    this.sizeLimitReached = sizeLimitReached;
  }
}
