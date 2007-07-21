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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import java.util.LinkedList;

/**
 * The AciTargetMatchContext interface provides a
 * view of an AciContainer that exposes information to be
 * used by the Aci.isApplicable() method to determine if
 * an ACI is applicable (targets matched) to the LDAP operation,
 * operation rights and entry and attributes having access
 * checked on.
 */
public interface AciTargetMatchContext {

    /**
     * Set the deny ACI list.
     * @param denyList The deny ACI list.
     */
    public void setDenyList(LinkedList<Aci> denyList);

    /**
     * Set the allow ACI list.
     * @param allowList The list of allow ACIs.
     */
    public void setAllowList(LinkedList<Aci> allowList);

    /**
     * Get the entry being evaluated. This is known as the
     * resource entry.
     * @return The entry being evaluated.
     */
    public Entry getResourceEntry();

    /**
     * Get the current attribute type being evaluated.
     * @return  The attribute type being evaluated.
     */
    public AttributeType getCurrentAttributeType();

    /**
     * The current attribute type value being evaluated.
     * @return The current attribute type value being evaluated.
     */
    public AttributeValue getCurrentAttributeValue();

    /**
     * True if the first attribute of the resource entry is being evaluated.
     * @return True if this is the first attribute.
     */
    public boolean isFirstAttribute();

    /**
     * Set to true if the first attribute of the resource entry is
     * being evaluated.
     * @param isFirst  True if this is the first attribute of the
     * resource entry being evaluated.
     */
    public void setIsFirstAttribute(boolean isFirst);

    /**
     * Set the attribute type to be evaluated.
     * @param type  The attribute type to set to.
     */
    public void setCurrentAttributeType(AttributeType type);

    /**
     * Set the attribute value to be evaluated.
     * @param v The current attribute value to set to.
     */
    public void setCurrentAttributeValue(AttributeValue v);

    /**
     * True if the target matching code found an entry test rule. An
     * entry test rule is an ACI without a targetattr target rule.
     * @param val True if an entry test rule was found.
     */
    public void setEntryTestRule(boolean val);

    /**
     * True if an entry test rule was found.
     * @return True if an entry test rule was found.
     */
    public boolean hasEntryTestRule();

    /**
     * Return the rights for this container's LDAP operation.
     * @return  The rights for the container's LDAP operation.
     */
    public int getRights();

  /**
   * Return the oid string of the control being evaluated.
   *
   * @return The oid string of the control being evaluated.
   */
    public String getControlOID();

    /**
     * Checks if the container's rights has the specified rights.
     * @param  rights The rights to check for.
     * @return True if the container's rights has the specified rights.
     */
    public boolean hasRights(int rights);

    /**
     * Set the rights of the container to the specified rights.
     * @param rights The rights to set the container's rights to.
     */
    public void setRights(int rights);

    /**
     * Set to true  if the ACI had a targattrfilter rule that matched.
     * @param v  The value to use.
     */
    public void setTargAttrFiltersMatch(boolean v);

    /**
     * Return the value of the targAttrFiltersMatch variable. This is set to
     * true if the ACI had a targattrfilter rule that matched.
     * @return  True if the ACI had a targattrfilter rule that matched.
     */
    public boolean getTargAttrFiltersMatch();

    /**
     * Add the specified ACI to a list of ACIs that have a targattrfilters rule
     * that matched. This is used by geteffectiverights to determine the rights
     * of an attribute that possibly might evaluate to true.
     * @param aci The ACI to save.
     */
    public void addTargAttrFiltersMatchAci(Aci aci);

    /**
     * Save the name of the last ACI that matched a targattrfilters rule. This
     * is used by geteffectiverights evaluation.
     * @param name The ACI's name to save.
     */
    void setTargAttrFiltersAciName(String name);

    /**
     * Returns true of a match context is performing a geteffectiverights
     * evaluation.
     * @return  True if a match context is evaluating geteffectiverights.
     */
    boolean isGetEffectiveRightsEval();

  /**
   * This method toggles a mask that indicates that access checking of
   * individual user attributes may or may not be skipped depending
   * on if there is a single ACI containing a targetattr all user
   * attributes rule (targetattr="*").
   *
   * The only case where individual user attribute access checking
   * can be skipped, is when a single ACI matched using a targetattr
   * all user attributes rule and the attribute type being check is not
   * operational.
   *
   * @param v  The mask to this value.
   */
  void setEvalUserAttributes(int v);

  /**
   * This method toggles a mask that indicates that access checking of
   * individual operational attributes may or may not be skipped depending
   * on if there is a single ACI containing a targetattr all operational
   * attributes rule (targetattr="+").
   *
   * The only case where individual operational attribute access checking
   * can be skipped, is when a single ACI matched using a targetattr
   * all operational attributes rule and the attribute type being check is
   * operational.
   *
   * @param v  The mask to this value.
   */
  void setEvalOpAttributes(int v);

  /**
   * Return true if the evaluating ACI either contained an explicitly defined
   * user attribute type in a targeattr target rule or both a targetattr all
   * user attributes rule matched and a explictly defined targetattr target rule
   * matched.
   *
   * @return  True if the above condition was seen.
   */
    boolean hasEvalUserAttributes();

  /**
   * Return true if the evaluating ACI either contained an explicitly defined
   * operational attribute type in a targeattr target rule or both a targetattr
   * all operational attributes rule matched and a explictly defined targetattr
   * target rule matched.
   *
   * @return  True if the above condition was seen.
   */
    boolean hasEvalOpAttributes();


  /**
   * Used to clear the mask used to detect if access checking needs to be
   * performed on individual attributes types. The specified
   * value is cleared from the mask or if the value equals 0 the mask is
   * completely cleared.
   *
   * @param v  The flag to clear or 0 to set the mask to 0.
   */
    public void clearEvalAttributes(int v);
}


