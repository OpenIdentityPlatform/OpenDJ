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

import static org.opends.server.authorization.dseecompat.Aci.*;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.*;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * This class implements the dseecompat geteffectiverights evaluation.
 */
public class AciEffectiveRights {

  //Value used when a aclRights attribute was seen in the search operation
  //attribute set.
  private static final int ACL_RIGHTS = 0x001;

  //Value used when a aclRightsInfo attribute was seen in the search operation
  //attribute set.
  private static final int ACL_RIGHTS_INFO = 0x002;

  //Value used when an ACI has a targattrfilters keyword match and the result
  //of the access check was a deny.
  private static final int ACL_TARGATTR_DENY_MATCH = 0x004;

  //Value used when an ACI has a targattrfilters keyword match and the result
  //of the access check was an allow.
  private static final int ACL_TARGATTR_ALLOW_MATCH = 0x008;

  //String used to build attribute type name when an aclRights result needs to
  //be added to the return entry.
  private static final String aclRightsAttrStr = "aclRights";

  //String used to build attribute type name when an AclRightsInfo result needs
  //to be added to the return entry.
  private static final String aclRightsInfoAttrStr = "aclRightsInfo";

  //String used to build attribute type name when an entryLevel rights
  //attribute type name needs to be added to the return entry.
  private static final String entryLevelStr = "entryLevel";

  //String used to build attribute type name when an attributeLevel rights
  //attribute type name needs to be added to the return entry.
  private static final String attributeLevelStr = "attributeLevel";

  //The string that is used as the attribute type name when an
  //aclRights entryLevel evaluation needs to be added to the return entry.
  private static final String aclRightsEntryLevelStr=
                                     aclRightsAttrStr + ";" + entryLevelStr;

  //The string that is used as the  attribute type name when an
  //aclRights attribute level evaluation needs to be added to the return entry.
  //This string has the attribute type name used in the evaluation appended to
  //it to form the final attribute type name.
  private static final String aclRightsAttributeLevelStr=
        aclRightsAttrStr +  ";" + attributeLevelStr;

  //The string used to build attribute type name when an attribute level
  //aclRightsInfo attribute needs to be added to the return entry. This string
  //has the attribute type name used in the evaluation appended to it to form
  //the final attribute type name.
  private static final String aclRightsInfoAttrLogsStr =
                      aclRightsInfoAttrStr + ";logs;attributeLevel";

  //The string used to build attribute type name when an entryLevel
  //aclRightsInfo attribute needs to be added to the return entry.
  private static final String aclRightsInfoEntryLogsStr =
                      aclRightsInfoAttrStr + ";logs;entryLevel";

  //Attribute type used in access evaluation to see if the geteffectiverights
  //related to the "aclRights" attribute can be performed.
  private static AttributeType aclRights = null;

  //Attribute type used in access evaluation to see if the geteffectiverights
  //related to the "aclRightsInfo" attribute can be performed.
  private static AttributeType aclRightsInfo = null;

  //Attribute type used in the geteffectiverights selfwrite evaluation.
  private static AttributeType dnAttributeType=null;

  //The distinguishedName string.
  private static final String dnAttrStr = "distinguishedname";

  //String used to fill in the summary status field when access was allowed.
  private static String ALLOWED="access allowed";

  //String used to fill in the summary status field when access was not allowed.
  private static String NOT_ALLOWED="access not allowed";

  //Evaluated as anonymous user. Used to fill in summary field.
  private static String anonymous="anonymous";

  //Format used to build the summary string.
  private static String summaryFormatStr =
        "acl_summary(%s): %s(%s) on entry/attr(%s, %s) to (%s)" +
        " (not proxied) ( reason: %s %s)";

  //Strings below represent access denied or allowed evaluation reasons.
  //Used to fill in the summary status field.
  //Access evaluated an allow ACI.
  private static String EVALUATED_ALLOW="evaluated allow";

  //Access evaluated a deny ACI.
  private static String EVALUATED_DENY="evaluated deny";

  //Access evaluated deny because there were no allow ACIs.
  private static String NO_ALLOWS="no acis matched the resource";

  //Access evaluated deny because no allow or deny ACIs evaluated.
  private static String NO_ALLOWS_MATCHED="no acis matched the subject";

  //Access evaluated allow because the clientDN has bypass-acl privileges.
  private static String SKIP_ACI="user has bypass-acl privileges";

  //TODO add support for the modify-acl privilige?

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
   * @return  A SearchResultEntry with geteffectiverights information possibly
   *          added to it.
   */
  public static SearchResultEntry
  addRightsToEntry(AciHandler handler, LinkedHashSet<String> searchAttributes,
            AciLDAPOperationContainer container,SearchResultEntry e,
            boolean skipCheck) {
    List<AttributeType> nonRightsAttrs = new LinkedList<AttributeType>();
    int attrMask=ACI_NULL;
    if(aclRights == null)
      aclRights =
               DirectoryServer.getAttributeType(aclRightsAttrStr.toLowerCase());
    if(aclRightsInfo == null)
      aclRightsInfo =
           DirectoryServer.getAttributeType(aclRightsInfoAttrStr.toLowerCase());
    if(dnAttributeType == null)
      dnAttributeType = DirectoryServer.getAttributeType(dnAttrStr);
    //Check if the attributes aclRights and aclRightsInfo were requested and
    //add attributes less those two attributes to a new list of attribute types.
    for(String a : searchAttributes) {
      if(a.equalsIgnoreCase(aclRightsAttrStr))
        attrMask |= ACL_RIGHTS;
      else if(a.equalsIgnoreCase(aclRightsInfoAttrStr))
        attrMask |= ACL_RIGHTS_INFO;
      else {
          //Check for shorthands for user attributes "*" or operational "+".
          if(a.equals("*")) {
              //Add objectclass.
              AttributeType ocType =
                      DirectoryServer.getObjectClassAttributeType();
              nonRightsAttrs.add(ocType);
              nonRightsAttrs.addAll(e.getUserAttributes().keySet());
          } else if (a.equals("+"))
              nonRightsAttrs.addAll(e.getOperationalAttributes().keySet());
          else {
              AttributeType attrType;
              if((attrType = DirectoryServer.getAttributeType(a)) == null)
                  attrType = DirectoryServer.getDefaultAttributeType(a);
              nonRightsAttrs.add(attrType);
          }
      }
    }
      //If the special geteffectiverights attributes were not found or
    //the user does not have both bypass-acl privs and is not allowed to
    //perform rights evalation -- return the entry unchanged.
    if(attrMask == ACI_NULL ||
      (!skipCheck && !rightsAccessAllowed(container,handler,attrMask)))
       return e;
    //From here on out, geteffectiverights evaluation is being performed and the
    //container will be manipulated. First set the flag that geteffectiverights
    //evaluation's underway and to use the authZid for authorizationDN (they
    //might be the same).
    container.setGetEffectiveRightsEval();
    container.useAuthzid(true);
    //If no attributes were requested return only entryLevel rights, else
    //return attributeLevel rights and entryLevel rights. Always try and
    //return the specific attribute rights if they exist.
    if(nonRightsAttrs.isEmpty()) {
      e=addAttributeLevelRights(container,handler,attrMask,e,
              container.getSpecificAttributes(), skipCheck, true);
      e=addEntryLevelRights(container,handler,attrMask,e, skipCheck);
    } else {
      e=addAttributeLevelRights(container,handler,attrMask,e,
              nonRightsAttrs, skipCheck, false);
      e=addAttributeLevelRights(container,handler,attrMask,e,
              container.getSpecificAttributes(), skipCheck, true);
      e=addEntryLevelRights(container,handler,attrMask,e,skipCheck);
    }
    return e;
  }


  /**
   * Perform the attributeLevel rights evaluation on a list of specified
   * attribute types. Each attribute has an access check done for the following
   * rights: search, read, compare, add, delete, proxy, selfwrite_add,
   * selfwrite_delete and write.
   *
   * The special rights, selfwrite_add and selfwrite_delete, use the authZid as
   * the attribute value to evaluate against the attribute type being
   * evaluated. The selfwrite_add performs the access check using the
   * ACI_WRITE_ADD right and selfwrite_delete uses ACI_WRITE_ADD right.
   *
   * The write right is made complicated by the targattrfilters keyword, which
   * might depend on an unknown value of an attribute type. For this case a
   * dummy attribute value is used to try and determine if a "?" needs to be
   * placed in the rights string.
   *
   * The special flag ACI_SKIP_PROXY_CHECK is always set, so that proxy
   * evaluation is bypassed in the Aci Handler's accessAllowed method.
   *
   * @param container The LDAP operation container to use in the evaluations.
   * @param handler  The Aci Handler to use in the access evaluations.
   * @param mask  Mask specifing what rights attribute processing to perform
   *              (aclRights or aclRightsInfo or both).
   * @param retEntry  The entry to return.
   * @param attrList The list of attribute types to iterate over.
   * @param skipCheck True if ACI evaluation was skipped because bypass-acl
   *                  privilege was found.
   * @param  specificAttr True if this evaluation is result of specific
   *                      attributes sent in the request.
   * @return  A SearchResultEntry with geteffectiverights attribute level
   *          information added to it.
   */
  private static
  SearchResultEntry addAttributeLevelRights(AciLDAPOperationContainer container,
                                        AciHandler handler, int mask,
                                        SearchResultEntry retEntry,
                                        List<AttributeType> attrList,
                                        boolean skipCheck,
                                        boolean specificAttr) {

    //The attribute list might be null.
    if(attrList == null)
      return retEntry;
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
      AttributeValue val=new AttributeValue(a, "dum###Val");
      container.setCurrentAttributeValue(val);
      evalInfo.append(attributeLevelWriteRights(container, handler, skipCheck));
      addAttrLevelRightsInfo(container, mask, a, retEntry, "write");
      evalInfo.append(',');
      //Perform both selfwrite_add and selfwrite_delete and append results.
      ByteString clientDNStr=
              new ASN1OctetString(container.getClientDN().toString());
      AttributeValue val1=new AttributeValue(a, clientDNStr);
      if(!specificAttr)
        container.setCurrentAttributeType(dnAttributeType);
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
        AttributeType attributeType=
                DirectoryServer.getDefaultAttributeType(typeStr);
        LinkedHashSet<AttributeValue> vals =
                new LinkedHashSet<AttributeValue>();
        vals.add(new AttributeValue(attributeType, evalInfo.toString()));
        Attribute attr =
                new Attribute(attributeType, typeStr, vals);
        //It is possible that the user might have specified the same attributes
        //in both the search and the specific attribute part of the control.
        //Only try to add the attribute type if it already hasn't been added.
        if(!retEntry.hasAttribute(attributeType))
         retEntry.addAttribute(attr,null);
      }
    }
    container.setCurrentAttributeValue(null);
    container.setCurrentAttributeType(null);
    return retEntry;
  }

  /**
   * Perform the attributeLevel write rights evaluation. The issue here is that
   * an ACI could contain a targattrfilters keyword that matches the attribute
   * being evaluated.
   *
   * There is no way of knowing if the filter part of the targattrfilter would
   * be successful or not. So if the ACI that allowed access, has an
   * targattrfilter keyword, a "?" is used as the result of the write (depends
   * on attribute value).
   *
   * If the allow ACI doesn't contain a targattrfilters keyword than a
   * "1" is added. If the ACI denies then a "0" is added. If the skipCheck flag
   * is true, then a 1 is used for the write access, since the client DN has
   * bypass privs.
   *
   * @param container The LDAP operation container to use in the evaluations.
   * @param handler The Aci Handler to use in the access evaluations.
   * @param skipCheck True if ACI evaluation was skipped because bypass-acl
   *                  privilege was found.
   * @return A string representing the rights information.
   */
  private static
  String attributeLevelWriteRights(AciLDAPOperationContainer container,
                                   AciHandler handler,  boolean skipCheck){
    boolean addRet=false, delRet=false;
    StringBuilder resString=new  StringBuilder();
    //If the user has bypass-acl privs and the authzid is equal to the
    //authorization dn, create a right string with a '1' and a valid
    //summary. If the user has bypass-acl privs and is querying for
    //another authzid or they don't have privs  -- fall through.
    if(skipCheck && container.isAuthzidAuthorizationDN()) {
      resString.append("write").append(":1");
      container.setEvalReason(EnumEvalReason.SKIP_ACI);
      container.setDecidingAci(null);
      createSummary(container, true, "main");
    } else {
     //Reset everything.
      container.resetEffectiveRightsParams();
      //Reset name.
      container.setTargAttrFiltersAciName(null);
      container.setRights(ACI_WRITE_ADD | ACI_SKIP_PROXY_CHECK);
      if(handler.accessAllowed(container)) {
        if(container.getTargAttrFiltersAciName() == null)
          addRet=true;
      }
      container.setRights(ACI_WRITE_DELETE | ACI_SKIP_PROXY_CHECK);
      if(handler.accessAllowed(container)) {
        if(container.getTargAttrFiltersAciName() == null)
          delRet=true;
      }
      //If both booleans are true, then access was allowed by ACIs that did
      //not contain targattrfilters.
      if(addRet && delRet)
        resString.append("write").append(":1");
      else {
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
   * @param container The LDAP operation container to use in the evaluations.
   * @param handler The Aci Handler to use in the access evaluations.
   * @param mask Mask specifing what rights attribute processing to perform
   *              (aclRights or aclRightsInfo or both).
   * @param retEntry The entry to return.
   * @param skipCheck True if ACI evaluation was skipped because bypass-acl
   *                  privilege was found.
   * @return A SearchResultEntry with geteffectiverights entryLevel rights
   *          information added to it.
   */

  private static SearchResultEntry
  addEntryLevelRights(AciLDAPOperationContainer container,
                                           AciHandler handler,
                                           int mask, SearchResultEntry retEntry,
                                           boolean skipCheck) {
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
    container.setCurrentAttributeType(null);
    //The read right needs the entry with the full set of attributes. This was
    //saved in the Aci Handlers maysend method.
    container.useFullResourceEntry(true);
    container.setRights(ACI_READ | ACI_SKIP_PROXY_CHECK);
    evalInfo.append(rightsString(container, handler, skipCheck, "read"));
    addEntryLevelRightsInfo(container, mask, retEntry, "read");
    evalInfo.append(',');
    //Switch back to the entry from the Aci Handler's filterentry method.
    container.useFullResourceEntry(false);
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
      LinkedHashSet<AttributeValue> vals = new LinkedHashSet<AttributeValue>();
      vals.add(new AttributeValue(attributeType, evalInfo.toString()));
      Attribute attr =
              new Attribute(attributeType, aclRightsEntryLevelStr, vals);
      retEntry.addAttribute(attr,null);
    }
    return retEntry;
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
      container.setEvalReason(EnumEvalReason.SKIP_ACI);
      container.setDecidingAci(null);
      createSummary(container, true, "main");
    } else {
      boolean ret;
      //Check if read right check, if so do accessAllowedEntry.
      if(container.hasRights(ACI_READ) &&
         container.getCurrentAttributeType() == null)
        ret=handler.accessAllowedEntry(container);
      else
        ret=handler.accessAllowed(container);
      if(ret)
        resString.append(rightStr).append(":1");
      else
        resString.append(rightStr).append(":0");
    }
    return resString.toString();
  }


  /**
   * Check that access is allowed on the aclRights and/or aclRightsInfo
   * attribute types.
   *
   * @param container The LDAP operation container to use in the evaluations.
   * @param handler   The Aci Handler to use in the access evaluations.
   * @param mask Mask specifing what rights attribute processing to perform
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
    return !(!retRight || !retInfo);
  }


  /**
   * Add aclRightsInfo attributeLevel information to the entry. This is the
   * summary string built from the last access check.
   *
   * @param container  The LDAP operation container to use in the evaluations.
   * @param mask  Mask specifing what rights attribute processing to perform
   *              (aclRights or aclRightsInfo or both).
   * @param aType The attribute type to use in building the attribute type name.
   * @param retEntry The entry to add the rights information to.
   * @param rightStr The string representation of the rights evaluated.
   */
  private static
  void addAttrLevelRightsInfo(AciLDAPOperationContainer container, int mask,
                     AttributeType aType, SearchResultEntry retEntry,
                     String rightStr) {

    //Check if the aclRightsInfo attribute was requested.
    if(hasAttrMask(mask,ACL_RIGHTS_INFO)) {
      //Build the attribute type.
      String typeStr=
              aclRightsInfoAttrLogsStr + ";" + rightStr + ";" +
              aType.getPrimaryName();
         AttributeType attributeType=
                DirectoryServer.getDefaultAttributeType(typeStr);
      LinkedHashSet<AttributeValue> vals = new LinkedHashSet<AttributeValue>();
      vals.add(new AttributeValue(attributeType, container.getEvalSummary()));
      Attribute attr =
                     new Attribute(attributeType, typeStr, vals);
      //The attribute type might have already been added, probably not but it
      //is possible.
      if(!retEntry.hasAttribute(attributeType))
          retEntry.addAttribute(attr,null);
    }
  }

  /**
   * Add aclRightsInfo entryLevel rights to the entry to be returned. This is
   * the summary string built from the last access check.
   *
   * @param container   The LDAP operation container to use in the evaluations.
   * @param mask Mask specifing what rights attribute processing to perform
   *              (aclRights or aclRightsInfo or both).
   * @param retEntry  The entry to add the rights information to.
   * @param rightStr The string representation of the rights evaluated.
   */
  private static
   void addEntryLevelRightsInfo(AciLDAPOperationContainer container, int mask,
                       SearchResultEntry retEntry,
                      String rightStr) {

     //Check if the aclRightsInfo attribute was requested.
     if(hasAttrMask(mask,ACL_RIGHTS_INFO)) {
       String typeStr=
               aclRightsInfoEntryLogsStr + ";" + rightStr;
          AttributeType attributeType=
                 DirectoryServer.getDefaultAttributeType(typeStr);
       LinkedHashSet<AttributeValue> vals = new LinkedHashSet<AttributeValue>();
       vals.add(new AttributeValue(attributeType, container.getEvalSummary()));
       Attribute attr =
                      new Attribute(attributeType, typeStr, vals);
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
   * @param srcStr String that can be used to specify where the summary call's
   *               origin is.
   */
 public static
  void createSummary(AciEvalContext evalCtx, boolean evalRet, String srcStr) {
    String accessStatus=NOT_ALLOWED;
    if(evalRet)
      accessStatus=ALLOWED;
    String accessReason="";
    StringBuilder decideAci=new StringBuilder("");
    //Try and determine what reason string to use.
    if(evalCtx.getEvalReason() == EnumEvalReason.EVALUATED_ALLOW_ACI) {
      accessReason=EVALUATED_ALLOW;
      decideAci.append(", deciding_aci: ").append(evalCtx.getDecidingAciName());
    } else if(evalCtx.getEvalReason() == EnumEvalReason.EVALUATED_DENY_ACI) {
      accessReason=EVALUATED_DENY;
      decideAci.append(", deciding_aci: ").append(evalCtx.getDecidingAciName());
    }  else if(evalCtx.getEvalReason() == EnumEvalReason.NO_ALLOW_ACIS)
      accessReason=NO_ALLOWS;
    else if(evalCtx.getEvalReason() == EnumEvalReason.NO_MATCHED_ALLOWS_ACIS)
      accessReason=NO_ALLOWS_MATCHED;
    else if(evalCtx.getEvalReason() == EnumEvalReason.SKIP_ACI)
      accessReason=SKIP_ACI;
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
          evalCtx.setTargAttrFiltersAciName(null);
      } else {
        //The evaluation returned false. If the reason was an
        //explicit deny evaluation by a non-targattrfilters ACI, clear
        //the evaluation context's targattrfilters ACI name since targattrfilter
        //evaluation is pretty much ignored during geteffectiverights eval.
        //Else, it was a non-explicit deny, if there is not a targattrfilters
        //ACI that might have granted access the deny stands, else there is
        //a targattrfilters ACI that might grant access.
        if(evalCtx.getEvalReason() == EnumEvalReason.EVALUATED_DENY_ACI)
          evalCtx.setTargAttrFiltersAciName(null);
        else if(!evalCtx.hasTargAttrFiltersMatchOp(ACL_TARGATTR_ALLOW_MATCH))
          evalCtx.setTargAttrFiltersAciName(null);
      }
    }
    //Actually build the string.
    String user=anonymous;
    if(!evalCtx.getClientDN().isNullDN())
      user=evalCtx.getClientDN().toString();
    String right=evalCtx.rightToString();
    AttributeType aType=evalCtx.getCurrentAttributeType();
    String attrStr="NULL";
    if(aType != null)
      attrStr=aType.getPrimaryName();
    if(evalCtx.getTargAttrFiltersAciName() != null)
      decideAci.append(", access depends on attr value");
    String summaryStr = String.format(summaryFormatStr, srcStr, accessStatus,
                         right,evalCtx.getResourceDN().toString(),attrStr, user,
                            accessReason, decideAci.toString());
    evalCtx.setEvalSummary(summaryStr);
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
    boolean ret=false;
    if(evalCtx.hasTargAttrFiltersMatchAci(aci)) {
       if(denyAci)
        evalCtx.setTargAttrFiltersMatchOp(ACL_TARGATTR_DENY_MATCH);
      else
        evalCtx.setTargAttrFiltersMatchOp(ACL_TARGATTR_ALLOW_MATCH);
      ret=true;
    }
    return ret;
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
