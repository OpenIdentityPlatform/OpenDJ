/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.Aci.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;

/**
 * This class implements the dseecompat geteffectiverights evaluation.
 */
public class AciEffectiveRights {

  /**
   * Value used when a aclRights attribute was seen in the search operation
   * attribute set.
   */
  private static final int ACL_RIGHTS = 0x001;

  /**
   * Value used when a aclRightsInfo attribute was seen in the search operation
   * attribute set.
   */
  private static final int ACL_RIGHTS_INFO = 0x002;

  /**
   * Value used when an ACI has a targattrfilters keyword match and the result
   * of the access check was a deny.
   */
  private static final int ACL_TARGATTR_DENY_MATCH = 0x004;

  /**
   * Value used when an ACI has a targattrfilters keyword match and the result
   * of the access check was an allow.
   */
  private static final int ACL_TARGATTR_ALLOW_MATCH = 0x008;

  /**
   * String used to build attribute type name when an aclRights result needs to
   * be added to the return entry.
   */
  private static final String aclRightsAttrStr = "aclRights";

  /**
   * String used to build attribute type name when an AclRightsInfo result needs
   * to be added to the return entry.
   */
  private static final String aclRightsInfoAttrStr = "aclRightsInfo";

  /**
   * String used to build attribute type name when an entryLevel rights
   * attribute type name needs to be added to the return entry.
   */
  private static final String entryLevelStr = "entryLevel";

  /**
   * String used to build attribute type name when an attributeLevel rights
   * attribute type name needs to be added to the return entry.
   */
  private static final String attributeLevelStr = "attributeLevel";

  /**
   * The string that is used as the attribute type name when an aclRights
   * entryLevel evaluation needs to be added to the return entry.
   */
  private static final String aclRightsEntryLevelStr=
                                     aclRightsAttrStr + ";" + entryLevelStr;

  /**
   * The string that is used as the attribute type name when an aclRights
   * attribute level evaluation needs to be added to the return entry. This
   * string has the attribute type name used in the evaluation appended to it to
   * form the final attribute type name.
   */
  private static final String aclRightsAttributeLevelStr=
        aclRightsAttrStr +  ";" + attributeLevelStr;

  /**
   * The string used to build attribute type name when an attribute level
   * aclRightsInfo attribute needs to be added to the return entry. This string
   * has the attribute type name used in the evaluation appended to it to form
   * the final attribute type name.
   */
  private static final String aclRightsInfoAttrLogsStr =
                      aclRightsInfoAttrStr + ";logs;attributeLevel";

  /**
   * The string used to build attribute type name when an entryLevel
   * aclRightsInfo attribute needs to be added to the return entry.
   */
  private static final String aclRightsInfoEntryLogsStr =
                      aclRightsInfoAttrStr + ";logs;entryLevel";

  /**
   * Attribute type used in access evaluation to see if the geteffectiverights
   * related to the "aclRights" attribute can be performed.
   */
  private static AttributeType aclRights;

  /**
   * Attribute type used in access evaluation to see if the geteffectiverights
   * related to the "aclRightsInfo" attribute can be performed.
   */
  private static AttributeType aclRightsInfo;

  /** Attribute type used in the geteffectiverights selfwrite evaluation. */
  private static AttributeType dnAttributeType;

  /**The distinguishedName string. */
  private static final String dnAttrStr = "distinguishedname";

  /**
   * String used to fill in the summary status field when access was allowed.
   */
  private static String ALLOWED="access allowed";

  /**
   * String used to fill in the summary status field when access was not
   * allowed.
   */
  private static String NOT_ALLOWED="access not allowed";

  /** Evaluated as anonymous user. Used to fill in summary field. */
  private static String anonymous="anonymous";

  /** Format used to build the summary string. */
  private static String summaryFormatStr =
        "acl_summary(%s): %s(%s) on entry/attr(%s, %s) to (%s)" +
        " (not proxied) ( reason: %s %s)";

  /**
   * Strings below represent access denied or allowed evaluation reasons. Used
   * to fill in the summary status field. Access evaluated an allow ACI.
   */
  private static String EVALUATED_ALLOW="evaluated allow";

  /** Access evaluated a deny ACI. */
  private static String EVALUATED_DENY="evaluated deny";

  /** Access evaluated deny because there were no allow ACIs. */
  private static String NO_ALLOWS="no acis matched the resource";

  /** Access evaluated deny because no allow or deny ACIs evaluated. */
  private static String NO_ALLOWS_MATCHED="no acis matched the subject";

  /** Access evaluated allow because the clientDN has bypass-acl privileges. */
  private static String SKIP_ACI="user has bypass-acl privileges";

  //TODO add support for the modify-acl privilege?

  /**
   * Attempts to add the geteffectiverights asked for in the search to the entry
   * being returned. The two geteffectiverights attributes that can be requested
   * are: aclRights and aclRightsInfo. The aclRightsInfo attribute will return
   * a summary string describing in human readable form, a summary of each
   * requested evaluation result. Here is a sample aclRightsInfo summary:
   *
   * acl_summary(main): access_not_allowed(proxy) on
   * entry/attr(uid=proxieduser,ou=acis,dc=example,dc=com, NULL) to
   * (uid=superuser,ou=acis,dc=example,dc=com) (not proxied)
   * (reason: no acis matched the resource )
   *
   * The aclRights attribute will return a simple
   * string with the following format:
   *
   *        add:0,delete:0,read:1,write:?,proxy:0
   *
   * A 0 represents access denied, 1 access allowed and ? that evaluation
   * depends on a value of an attribute (targattrfilter keyword present in ACI).
   *
   * There are two levels of rights information:
   *
   *  1. entryLevel - entry level rights information
   *  2. attributeLevel - attribute level rights information
   *
   * The attribute type names are built up using subtypes:
   *
   *    aclRights;entryLevel - aclRights entry level presentation
   *    aclRightsInfo;log;entryLevel;{right} - aclRightsInfo entry level
   *        presentation for each type of right (proxy, read, write, add,
   *        delete).
   *    aclRights;attributeLevel;{attributeType name} - aclRights attribute
   *        level presentation for each attribute type requested.
   *    aclRights;attributeLevel;logs;{right};{attributeType name}
   *        - aclRightsInfo  attribute level presentation for each attribute
   *          type requested.
   *
   * @param handler  The ACI handler to use in the evaluation.
   * @param searchAttributes  The attributes requested in the search.
   * @param container  The LDAP operation container to use in the evaluations.
   * @param e The entry to add the rights attributes to.
   * @param skipCheck  True if ACI evaluation was skipped because bypass-acl
   *                   privilege was found.
   */
  public static void addRightsToEntry(AciHandler handler,
      Set<String> searchAttributes,
      AciLDAPOperationContainer container, final Entry e,
      boolean skipCheck)
  {
    if (aclRights == null)
    {
      aclRights = DirectoryServer.getAttributeType(aclRightsAttrStr
          .toLowerCase());
    }

    if (aclRightsInfo == null)
    {
      aclRightsInfo = DirectoryServer.getAttributeType(aclRightsInfoAttrStr
          .toLowerCase());
    }

    if (dnAttributeType == null)
    {
      dnAttributeType = DirectoryServer.getAttributeType(dnAttrStr);
    }

    // Check if the attributes aclRights and aclRightsInfo were requested and
    // add attributes less those two attributes to a new list of attribute
    // types.
    List<AttributeType> nonRightsAttrs = new LinkedList<>();
    int attrMask = ACI_NULL;
    for (String a : searchAttributes)
    {
      if (aclRightsAttrStr.equalsIgnoreCase(a))
      {
        attrMask |= ACL_RIGHTS;
      }
      else if (aclRightsInfoAttrStr.equalsIgnoreCase(a))
      {
        attrMask |= ACL_RIGHTS_INFO;
      }
      else
      {
        // Check for shorthands for user attributes "*" or operational "+".
        if ("*".equals(a))
        {
          // Add objectclass.
          AttributeType ocType = DirectoryServer.getObjectClassAttributeType();
          nonRightsAttrs.add(ocType);
          nonRightsAttrs.addAll(e.getUserAttributes().keySet());
        }
        else if ("+".equals(a))
        {
          nonRightsAttrs.addAll(e.getOperationalAttributes().keySet());
        }
        else
        {
          nonRightsAttrs.add(DirectoryServer.getAttributeTypeOrDefault(a.toLowerCase()));
        }
      }
    }

    // If the special geteffectiverights attributes were not found or
    // the user does not have both bypass-acl privs and is not allowed to
    // perform rights evaluation -- return the entry unchanged.
    if (attrMask == ACI_NULL
        || (!skipCheck && !rightsAccessAllowed(container, handler, attrMask)))
    {
      return;
    }

    // From here on out, geteffectiverights evaluation is being performed and
    // the container will be manipulated. First set the flag that
    // geteffectiverights evaluation's underway and to use the authZid for
    // authorizationDN (they might be the same).
    container.setGetEffectiveRightsEval();
    container.useAuthzid(true);

    // If no attributes were requested return only entryLevel rights, else
    // return attributeLevel rights and entryLevel rights. Always try and
    // return the specific attribute rights if they exist.
    if (!nonRightsAttrs.isEmpty())
    {
      addAttributeLevelRights(container, handler, attrMask, e, nonRightsAttrs,
          skipCheck, false);
    }
    addAttributeLevelRights(container, handler, attrMask, e, container
        .getSpecificAttributes(), skipCheck, true);
    addEntryLevelRights(container, handler, attrMask, e, skipCheck);
  }



  /**
   * Perform the attributeLevel rights evaluation on a list of specified
   * attribute types. Each attribute has an access check done for the following
   * rights: search, read, compare, add, delete, proxy, selfwrite_add,
   * selfwrite_delete and write. The special rights, selfwrite_add and
   * selfwrite_delete, use the authZid as the attribute value to evaluate
   * against the attribute type being evaluated. The selfwrite_add performs the
   * access check using the ACI_WRITE_ADD right and selfwrite_delete uses
   * ACI_WRITE_ADD right. The write right is made complicated by the
   * targattrfilters keyword, which might depend on an unknown value of an
   * attribute type. For this case a dummy attribute value is used to try and
   * determine if a "?" needs to be placed in the rights string. The special
   * flag ACI_SKIP_PROXY_CHECK is always set, so that proxy evaluation is
   * bypassed in the Aci Handler's accessAllowed method.
   *
   * @param container
   *          The LDAP operation container to use in the evaluations.
   * @param handler
   *          The Aci Handler to use in the access evaluations.
   * @param mask
   *          Mask specifying what rights attribute processing to perform
   *          (aclRights or aclRightsInfo or both).
   * @param retEntry
   *          The entry to return.
   * @param attrList
   *          The list of attribute types to iterate over.
   * @param skipCheck
   *          True if ACI evaluation was skipped because bypass-acl privilege
   *          was found.
   * @param specificAttr
   *          True if this evaluation is result of specific attributes sent in
   *          the request.
   */
  private static void addAttributeLevelRights(
      AciLDAPOperationContainer container, AciHandler handler, int mask,
      final Entry retEntry, List<AttributeType> attrList,
      boolean skipCheck, boolean specificAttr)
  {
    if (attrList == null)
    {
      return;
    }

    for(AttributeType a : attrList) {
      StringBuilder evalInfo=new StringBuilder();
      container.setCurrentAttributeType(a);
      container.setCurrentAttributeValue(null);
      //Perform search check and append results.
      container.setRights(ACI_SEARCH | ACI_SKIP_PROXY_CHECK);
      evalInfo.append(rightsString(container, handler, skipCheck, "search"));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "search");
      evalInfo.append(',');
      //Perform read check and append results.
      container.setRights(ACI_READ | ACI_SKIP_PROXY_CHECK);
      evalInfo.append(rightsString(container, handler, skipCheck, "read"));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "read");
      evalInfo.append(',');
      //Perform compare and append results.
      container.setRights(ACI_COMPARE | ACI_SKIP_PROXY_CHECK);
      evalInfo.append(rightsString(container, handler, skipCheck, "compare"));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "compare");
      evalInfo.append(',');
      //Write right is more complicated. Create a dummy value and set that as
      //the attribute's value. Call the special writeRightsString method, rather
      //than rightsString.
      ByteString val= ByteString.valueOf("dum###Val");
      container.setCurrentAttributeValue(val);
      evalInfo.append(attributeLevelWriteRights(container, handler, skipCheck));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "write");
      evalInfo.append(',');
      //Perform both selfwrite_add and selfwrite_delete and append results.
      ByteString val1 = ByteString.valueOf(container.getClientDN().toString());
      if(!specificAttr)
      {
        container.setCurrentAttributeType(dnAttributeType);
      }
      container.setCurrentAttributeValue(val1);
      container.setRights(ACI_WRITE_ADD | ACI_SKIP_PROXY_CHECK);
      evalInfo.append(rightsString(container, handler, skipCheck,
                      "selfwrite_add"));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "selfwrite_add");
      evalInfo.append(',');
      container.setRights(ACI_WRITE_DELETE | ACI_SKIP_PROXY_CHECK);
      evalInfo.append(rightsString(container, handler, skipCheck,
                       "selfwrite_delete"));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "selfwrite_delete");
      evalInfo.append(',');
      container.setCurrentAttributeType(a);
      container.setCurrentAttributeValue(null);
                container.setRights(ACI_PROXY | ACI_SKIP_PROXY_CHECK);
      evalInfo.append(rightsString(container, handler, skipCheck, "proxy"));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "proxy");
      //It is possible that only the aclRightsInfo attribute type was requested.
      //Only add the aclRights information if the aclRights attribute type was
      //seen.
      if(hasAttrMask(mask, ACL_RIGHTS))  {
        String typeStr=aclRightsAttributeLevelStr + ";" +
                a.getNormalizedPrimaryName();
        AttributeType attributeType = DirectoryServer
            .getDefaultAttributeType(typeStr);
        Attribute attr = Attributes.create(attributeType, evalInfo
            .toString());
        //It is possible that the user might have specified the same attributes
        //in both the search and the specific attribute part of the control.
        //Only try to add the attribute type if it already hasn't been added.
        if(!retEntry.hasAttribute(attributeType))
        {
          retEntry.addAttribute(attr,null);
        }
      }
    }
    container.setCurrentAttributeValue(null);
    container.setCurrentAttributeType(null);
  }



  /**
   * Perform the attributeLevel write rights evaluation. The issue here is that
   * an ACI could contain a targattrfilters keyword that matches the attribute
   * being evaluated. There is no way of knowing if the filter part of the
   * targattrfilter would be successful or not. So if the ACI that allowed
   * access, has an targattrfilter keyword, a "?" is used as the result of the
   * write (depends on attribute value). If the allow ACI doesn't contain a
   * targattrfilters keyword than a "1" is added. If the ACI denies then a "0"
   * is added. If the skipCheck flag is true, then a 1 is used for the write
   * access, since the client DN has bypass privs.
   *
   * @param container
   *          The LDAP operation container to use in the evaluations.
   * @param handler
   *          The Aci Handler to use in the access evaluations.
   * @param skipCheck
   *          True if ACI evaluation was skipped because bypass-acl privilege
   *          was found.
   * @return A string representing the rights information.
   */
  private static String attributeLevelWriteRights(
      AciLDAPOperationContainer container, AciHandler handler,
      boolean skipCheck)
  {
    StringBuilder resString=new  StringBuilder();
    //If the user has bypass-acl privs and the authzid is equal to the
    //authorization dn, create a right string with a '1' and a valid
    //summary. If the user has bypass-acl privs and is querying for
    //another authzid or they don't have privs  -- fall through.
    if(skipCheck && container.isAuthzidAuthorizationDN()) {
      resString.append("write").append(":1");
      container.setEvaluationResult(EnumEvalReason.SKIP_ACI, null);
      container.setEvalSummary(createSummary(container, true));
    } else {
      // Reset everything.
      container.resetEffectiveRightsParams();
      //Reset name.
      container.setTargAttrFiltersAciName(null);
      container.setRights(ACI_WRITE_ADD | ACI_SKIP_PROXY_CHECK);
      final boolean addRet = handler.accessAllowed(container)
              && container.getTargAttrFiltersAciName() == null;
      container.setRights(ACI_WRITE_DELETE | ACI_SKIP_PROXY_CHECK);
      final boolean delRet = handler.accessAllowed(container)
              && container.getTargAttrFiltersAciName() == null;
      //If both booleans are true, then access was allowed by ACIs that did
      //not contain targattrfilters.
      if(addRet && delRet) {
        resString.append("write").append(":1");
      } else {
        //If there is an ACI name then an ACI with a targattrfilters allowed,
        //access. A '?' is needed because that evaluation really depends on an
        //unknown attribute value, not the dummy value. If there is no ACI
        //then one of the above access checks failed and a '0' is needed.
        if(container.getTargAttrFiltersAciName() != null) {
          resString.append("write").append(":?");
        } else {
          resString.append("write").append(":0");
        }
      }
    }
    return resString.toString();
  }



  /**
   * Perform entryLevel rights evaluation. The rights string is added to the
   * entry if the aclRights attribute was seen in the search's requested
   * attribute set.
   *
   * @param container
   *          The LDAP operation container to use in the evaluations.
   * @param handler
   *          The Aci Handler to use in the access evaluations.
   * @param mask
   *          Mask specifying what rights attribute processing to perform
   *          (aclRights or aclRightsInfo or both).
   * @param retEntry
   *          The entry to return.
   * @param skipCheck
   *          True if ACI evaluation was skipped because bypass-acl privilege
   *          was found.
   */
  private static void addEntryLevelRights(AciLDAPOperationContainer container,
      AciHandler handler, int mask, final Entry retEntry,
      boolean skipCheck)
  {
    //Perform access evaluations for rights: add, delete, read, write, proxy.
    StringBuilder evalInfo=new StringBuilder();
    container.setCurrentAttributeType(null);
    container.setRights(ACI_ADD | ACI_SKIP_PROXY_CHECK);
    evalInfo.append(rightsString(container, handler, skipCheck, "add"));
    addEntryLevelRightsInfo(container, mask, retEntry, "add");
    evalInfo.append(',');
    container.setCurrentAttributeType(null);
    container.setRights(ACI_DELETE | ACI_SKIP_PROXY_CHECK);
    evalInfo.append(rightsString(container, handler, skipCheck, "delete"));
    addEntryLevelRightsInfo(container, mask, retEntry, "delete");
    evalInfo.append(',');
    //The read right needs the entry with the full set of attributes. This was
    //saved in the Aci Handlers maysend method.
    container.setCurrentAttributeType(null);
    container.setRights(ACI_READ | ACI_SKIP_PROXY_CHECK);
    evalInfo.append(rightsString(container, handler, skipCheck, "read"));
    addEntryLevelRightsInfo(container, mask, retEntry, "read");
    evalInfo.append(',');
    //Switch back to the entry from the Aci Handler's filterentry method.
    container.setCurrentAttributeType(null);
    container.setRights(ACI_WRITE| ACI_SKIP_PROXY_CHECK);
    evalInfo.append(rightsString(container, handler, skipCheck, "write"));
    addEntryLevelRightsInfo(container, mask, retEntry, "write");
    evalInfo.append(',');
    container.setCurrentAttributeType(null);
    container.setRights(ACI_PROXY| ACI_SKIP_PROXY_CHECK);
    evalInfo.append(rightsString(container, handler, skipCheck, "proxy"));
    addEntryLevelRightsInfo(container, mask, retEntry, "proxy");
    if(hasAttrMask(mask, ACL_RIGHTS)) {
      AttributeType attributeType=
              DirectoryServer.getDefaultAttributeType(aclRightsEntryLevelStr);
      Attribute attr = Attributes.create(attributeType, evalInfo.toString());
      retEntry.addAttribute(attr,null);
    }
  }

  /**
   * Create the rights for aclRights attributeLevel or entryLevel rights
   * evaluation. The only right needing special treatment is the read right
   * with no current attribute type set in the container. For that case the
   * accessAllowedEntry method is used instead of the accessAllowed method.
   *
   * @param container The LDAP operation container to use in the evaluations.
   * @param handler The Aci Handler to use in the access evaluations.
   * @param skipCheck True if ACI evaluation was skipped because bypass-acl
   *                  privilege was found.
   * @param rightStr String used representation of the right we are evaluating.
   * @return  A string representing the aclRights for the current right and
   * attribute type/value combinations.
   */
  private static
  String rightsString(AciLDAPOperationContainer container,
                                            AciHandler handler,
                                            boolean skipCheck, String rightStr){
    StringBuilder resString=new  StringBuilder();
    container.resetEffectiveRightsParams();
    //If the user has bypass-acl privs and the authzid is equal to the
    //authorization dn, create a right string with a '1' and a valid
    //summary. If the user has bypass-acl privs and is querying for
    //another authzid or they don't have privs  -- fall through.
    if(skipCheck && container.isAuthzidAuthorizationDN()) {
      resString.append(rightStr).append(":1");
      container.setEvaluationResult(EnumEvalReason.SKIP_ACI, null);
      container.setEvalSummary(createSummary(container, true));
    } else {
      boolean ret;
      //Check if read right check, if so do accessAllowedEntry.
      if(container.hasRights(ACI_READ) &&
         container.getCurrentAttributeType() == null)
      {
        ret=handler.accessAllowedEntry(container);
      }
      else
      {
        ret=handler.accessAllowed(container);
      }

      resString.append(rightStr).append(ret ? ":1" : ":0");
    }
    return resString.toString();
  }


  /**
   * Check that access is allowed on the aclRights and/or aclRightsInfo
   * attribute types.
   *
   * @param container The LDAP operation container to use in the evaluations.
   * @param handler   The Aci Handler to use in the access evaluations.
   * @param mask Mask specifying what rights attribute processing to perform
   *              (aclRights or aclRightsInfo or both).
   * @return True if access to the geteffectiverights attribute types are
   *         allowed.
   */
  private static
  boolean rightsAccessAllowed(AciLDAPOperationContainer container,
                              AciHandler handler, int mask) {
    boolean retRight=true, retInfo=true;
    if(hasAttrMask(mask, ACL_RIGHTS)) {
        container.setCurrentAttributeType(aclRights);
        container.setRights(ACI_READ | ACI_SKIP_PROXY_CHECK);
        retRight=handler.accessAllowed(container);
    }
    if(hasAttrMask(mask, ACL_RIGHTS_INFO)) {
        container.setCurrentAttributeType(aclRightsInfo);
        container.setRights(ACI_READ | ACI_SKIP_PROXY_CHECK);
        retInfo=handler.accessAllowed(container);
    }
    return retRight && retInfo;
  }


  /**
   * Add aclRightsInfo attributeLevel information to the entry. This is the
   * summary string built from the last access check.
   *
   * @param container  The LDAP operation container to use in the evaluations.
   * @param mask  Mask specifying what rights attribute processing to perform
   *              (aclRights or aclRightsInfo or both).
   * @param aType The attribute type to use in building the attribute type name.
   * @param retEntry The entry to add the rights information to.
   * @param rightStr The string representation of the rights evaluated.
   */
  private static
  void addAttrLevelRightsInfo(AciLDAPOperationContainer container, int mask,
                     AttributeType aType, Entry retEntry,
                     String rightStr) {

    //Check if the aclRightsInfo attribute was requested.
    if(hasAttrMask(mask,ACL_RIGHTS_INFO)) {
      //Build the attribute type.
      String typeStr=
              aclRightsInfoAttrLogsStr + ";" + rightStr + ";" +
              aType.getPrimaryName();
      AttributeType attributeType=
                DirectoryServer.getDefaultAttributeType(typeStr);
      Attribute attr = Attributes.create(attributeType,
          container.getEvalSummary());
      // The attribute type might have already been added, probably
      // not but it is possible.
      if(!retEntry.hasAttribute(attributeType))
      {
        retEntry.addAttribute(attr,null);
      }
    }
  }

  /**
   * Add aclRightsInfo entryLevel rights to the entry to be returned. This is
   * the summary string built from the last access check.
   *
   * @param container   The LDAP operation container to use in the evaluations.
   * @param mask Mask specifying what rights attribute processing to perform
   *              (aclRights or aclRightsInfo or both).
   * @param retEntry  The entry to add the rights information to.
   * @param rightStr The string representation of the rights evaluated.
   */
  private static
   void addEntryLevelRightsInfo(AciLDAPOperationContainer container, int mask,
                       Entry retEntry,
                      String rightStr) {

     //Check if the aclRightsInfo attribute was requested.
     if(hasAttrMask(mask,ACL_RIGHTS_INFO)) {
      String typeStr = aclRightsInfoEntryLogsStr + ";" + rightStr;
       AttributeType attributeType=
                 DirectoryServer.getDefaultAttributeType(typeStr);
       Attribute attr = Attributes.create(attributeType,
           container.getEvalSummary());
       retEntry.addAttribute(attr,null);
     }
   }

  /**
   * Check if the provided mask has a specific rights attr value.
   *
   * @param mask The mask with the attribute flags.
   * @param rightsAttr The rights attr value to check for.
   * @return True if the mask contains the rights attr value.
   */
  private static boolean hasAttrMask(int mask, int rightsAttr) {
        return (mask & rightsAttr) != 0;
  }


  /**
   * Create the summary string used in the aclRightsInfo log string.
   *
   * @param evalCtx The evaluation context to gather information from.
   * @param evalRet The value returned from the access evaluation.
   * @return A summary of the ACI evaluation
   */
  public static String createSummary(AciEvalContext evalCtx, boolean evalRet)
  {
    String srcStr = "main";
    String accessStatus = evalRet ? ALLOWED : NOT_ALLOWED;

    //Try and determine what reason string to use.
    String accessReason = getEvalReason(evalCtx.getEvalReason());
    StringBuilder decideAci =
        getDecidingAci(evalCtx.getEvalReason(), evalCtx.getDecidingAciName());

    //Only manipulate the evaluation context's targattrfilters ACI name
    //if not a selfwrite evaluation and the context's targattrfilter match
    //hashtable is not empty.
    if(!evalCtx.isTargAttrFilterMatchAciEmpty() &&
            !evalCtx.hasRights(ACI_SELF)) {
      //If the allow list was empty then access is '0'.
      if(evalCtx.getAllowList().isEmpty()) {
        evalCtx.setTargAttrFiltersAciName(null);
      } else if(evalRet) {
        //The evaluation returned true, clear the evaluation context's
        //targattrfilters ACI name only if a deny targattrfilters ACI
        //was not seen. It could remove the allow.
        if(!evalCtx.hasTargAttrFiltersMatchOp(ACL_TARGATTR_DENY_MATCH))
        {
          evalCtx.setTargAttrFiltersAciName(null);
        }
      } else {
        //The evaluation returned false. If the reason was an
        //explicit deny evaluation by a non-targattrfilters ACI, clear
        //the evaluation context's targattrfilters ACI name since targattrfilter
        //evaluation is pretty much ignored during geteffectiverights eval.
        //Else, it was a non-explicit deny, if there is not a targattrfilters
        //ACI that might have granted access the deny stands, else there is
        //a targattrfilters ACI that might grant access.
        if(evalCtx.getEvalReason() == EnumEvalReason.EVALUATED_DENY_ACI)
        {
          evalCtx.setTargAttrFiltersAciName(null);
        }
        else if(!evalCtx.hasTargAttrFiltersMatchOp(ACL_TARGATTR_ALLOW_MATCH))
        {
          evalCtx.setTargAttrFiltersAciName(null);
        }
      }
    }
    //Actually build the string.
    String user=anonymous;
    if(!evalCtx.getClientDN().isRootDN())
    {
      user=evalCtx.getClientDN().toString();
    }
    String right=evalCtx.rightToString();
    AttributeType aType=evalCtx.getCurrentAttributeType();
    String attrStr="NULL";
    if(aType != null)
    {
      attrStr=aType.getPrimaryName();
    }
    if(evalCtx.getTargAttrFiltersAciName() != null)
    {
      decideAci.append(", access depends on attr value");
    }
    return String.format(summaryFormatStr, srcStr, accessStatus,
                         right,evalCtx.getResourceDN().toString(),attrStr, user,
                            accessReason, decideAci.toString());
  }

  private static String getEvalReason(EnumEvalReason evalReason)
  {
    if (evalReason == EnumEvalReason.EVALUATED_ALLOW_ACI)
    {
      return EVALUATED_ALLOW;
    }
    else if (evalReason == EnumEvalReason.EVALUATED_DENY_ACI)
    {
      return EVALUATED_DENY;
    }
    else if (evalReason == EnumEvalReason.NO_ALLOW_ACIS)
    {
      return NO_ALLOWS;
    }
    else if (evalReason == EnumEvalReason.NO_MATCHED_ALLOWS_ACIS)
    {
      return NO_ALLOWS_MATCHED;
    }
    else if (evalReason == EnumEvalReason.SKIP_ACI)
    {
      return SKIP_ACI;
    }
    return "";
  }

  private static StringBuilder getDecidingAci(EnumEvalReason evalReason,
      String decidingAciName)
  {
    StringBuilder decideAci = new StringBuilder();
    if (evalReason == EnumEvalReason.EVALUATED_ALLOW_ACI)
    {
      decideAci.append(", deciding_aci: ").append(decidingAciName);
    }
    else if (evalReason == EnumEvalReason.EVALUATED_DENY_ACI)
    {
      decideAci.append(", deciding_aci: ").append(decidingAciName);
    }
    return decideAci;
  }

  /**
   * If the specified ACI is in the targattrfilters hashtable contained in the
   * evaluation context, set the  evaluation context's targattrfilters match
   * variable to either ACL_TARGATTR_DENY_MATCH or ACL_TARGATTR_ALLOW_MATCH
   * depending on the value of the variable denyAci.
   *
   * @param evalCtx The evaluation context to evaluate and save information to.
   * @param aci The ACI to match.
   * @param denyAci True if the evaluation was a allow, false if the
   *                evaluation was an deny or the ACI is not in the table.
   * @return  True if the ACI was found in the hashtable.
   */
  public static
  boolean  setTargAttrAci(AciEvalContext evalCtx, Aci aci, boolean denyAci) {
    if(evalCtx.hasTargAttrFiltersMatchAci(aci)) {
      int flag = denyAci ? ACL_TARGATTR_DENY_MATCH : ACL_TARGATTR_ALLOW_MATCH;
      evalCtx.setTargAttrFiltersMatchOp(flag);
      return true;
    }
    return false;
  }

  /**
   * Finalizes static variables on shutdown so that we release the memory
   * associated with them (for the unit tests) and get fresh copies if we're
   * doing an in-core restart.
   */
  public static void finalizeOnShutdown() {
    AciEffectiveRights.aclRights = null;
    AciEffectiveRights.aclRightsInfo = null;
    AciEffectiveRights.dnAttributeType = null;
  }
}
