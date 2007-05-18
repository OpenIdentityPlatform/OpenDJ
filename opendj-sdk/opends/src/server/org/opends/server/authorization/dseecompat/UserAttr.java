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

import static org.opends.server.messages.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.*;
/*
 * TODO Evaluate making this class more efficient.
 *
 * This class isn't as efficient as it could be.  For example, the evalVAL()
 * method should be able to use cached versions of the attribute type and
 * filter. The evalURL() and evalDN() methods should also be able to use a
 * cached version of the attribute type.
 */
/**
 * This class implements the  userattr bind rule keyword.
 */
public class UserAttr implements KeywordBindRule {

    /**
     * This enumeration is the various types the userattr can have after
     * the "#" token.
     */
    private enum UserAttrType {
        USERDN, GROUPDN, ROLEDN, URL, VALUE
    }

    /*
     * Filter used in  internal search.
     */
    private static SearchFilter filter;

    /*
     * Used to create an attribute type that can compare the value below in
     * an entry returned from an internal search.
     */
    private  String attrStr=null;

    /*
     * Used to compare a attribute value returned from a search against this
     * value which might have been defined in the ACI userattr rule.
     */
    private  String attrVal=null;

    /*
     * Contains the type of the userattr, one of the above enumerations.
     */
    private UserAttrType userAttrType=null;

    /*
     * An enumeration representing the bind rule type.
     */
    private EnumBindRuleType type=null;

    /*
     * The class used to hold the parent inheritance information.
     */
    private ParentInheritance parentInheritance=null;

    static {
        /*
         * Set up the filter used to search private and public contexts.
         */
        try {
            filter=SearchFilter.createFilterFromString("(objectclass=*)");
        } catch (DirectoryException ex) {
            //TODO should never happen, error message?
        }
    }

    /**
     * Create an non-USERDN/GROUPDN instance of the userattr keyword class.
     * @param attrStr The attribute name in string form. Kept in string form
     * until processing.
     * @param attrVal The attribute value in string form -- used in the USERDN
     * evaluation for the parent hierarchy expression.
     * @param userAttrType The userattr type of the rule
     * "USERDN, GROUPDN, ...".
     * @param type The bind rule type "=, !=".
     */
    private UserAttr(String attrStr, String attrVal, UserAttrType userAttrType,
            EnumBindRuleType type) {
        this.attrStr=attrStr;
        this.attrVal=attrVal;
        this.userAttrType=userAttrType;
        this.type=type;
    }

    /**
     * Create an USERDN or GROUPDN  instance of the userattr keyword class.
     * @param userAttrType The userattr type of the rule (USERDN or GROUPDN)
     * only.
     * @param type The bind rule type "=, !=".
     * @param parentInheritance The parent inheritance class to use for parent
     * inheritance checks if any.
     */
    private UserAttr(UserAttrType userAttrType, EnumBindRuleType type,
                     ParentInheritance parentInheritance) {
        this.userAttrType=userAttrType;
        this.type=type;
        this.parentInheritance=parentInheritance;
    }
    /**
     * Decode an string containing the userattr bind rule expression.
     * @param expression The expression string.
     * @param type The bind rule type.
     * @return A class suitable for evaluating a userattr bind rule.
     * @throws AciException If the string contains an invalid expression.
     */
    public static KeywordBindRule decode(String expression,
                                         EnumBindRuleType type)
    throws AciException {
        String[] vals=expression.split("#");
        if(vals.length != 2) {
            int msgID = MSGID_ACI_SYNTAX_INVALID_USERATTR_EXPRESSION;
            String message = getMessage(msgID, expression);
            throw new AciException(msgID, message);
        }
        UserAttrType userAttrType=getType(vals[1]);
        switch (userAttrType) {
                case GROUPDN:
                case USERDN: {
                    ParentInheritance parentInheritance =
                            new ParentInheritance(vals[0], false);
                    return new UserAttr (userAttrType, type, parentInheritance);
                }
                case ROLEDN: {
                  //The roledn keyword is not supported. Throw an exception with
                  //a message if it is seen in the expression.
                  int msgID=MSGID_ACI_SYNTAX_ROLEDN_NOT_SUPPORTED;
                  String message = getMessage(msgID, expression);
                  throw new AciException(msgID, message);
                }
         }
         return new UserAttr(vals[0], vals[1], userAttrType, type);
    }

    /**
     * Evaluate the expression using an evaluation context.
     * @param evalCtx   The evaluation context to use in the evaluation of the
     * userattr expression.
     * @return  An enumeration containing the result of the evaluation.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched;

        switch(userAttrType) {
        case ROLEDN:
        case GROUPDN:
        case USERDN: {
            matched=evalDNKeywords(evalCtx);
            break;
        }
        case URL: {
            matched=evalURL(evalCtx);
            break;
        }
        default:
            matched=evalVAL(evalCtx);
        }
        return matched;
    }

    /** Evaluate a VALUE userattr type. Look in client entry for an
     *  attribute value and in the resource entry for the same
     *  value. If both entries have the same value than return true.
     * @param evalCtx The evaluation context to use.
     * @return An enumeration containing the result of the
     * evaluation.
     */
    private EnumEvalResult evalVAL(AciEvalContext evalCtx) {
        EnumEvalResult matched= EnumEvalResult.FALSE;
        boolean undefined=false;
        AttributeType attrType;
        if((attrType = DirectoryServer.getAttributeType(attrStr)) == null)
            attrType = DirectoryServer.getDefaultAttributeType(attrStr);
        InternalClientConnection conn =
                InternalClientConnection.getRootConnection();
        InternalSearchOperation op =
                conn.processSearch(evalCtx.getClientDN(),
                        SearchScope.BASE_OBJECT,
                        DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                        filter, null);
        LinkedList<SearchResultEntry> result = op.getSearchEntries();
        if (!result.isEmpty()) {
            AttributeValue val=new AttributeValue(attrType, attrVal);
            SearchResultEntry resultEntry = result.getFirst();
            if(resultEntry.hasValue(attrType, null, val)) {
                Entry e=evalCtx.getResourceEntry();
                if(e.hasValue(attrType, null, val))
                    matched=EnumEvalResult.TRUE;
            }
        }
        return matched.getRet(type, undefined);
    }

    /**
     * Parses the substring after the '#' character to determine the userattr
     * type.
     * @param expr The string with the substring.
     * @return An enumeration containing the type.
     * @throws AciException If the substring contains an invalid type (roledn
     * or groupdn).
     */
    private static UserAttrType getType(String expr) throws AciException {
        UserAttrType userAttrType;
        if(expr.equalsIgnoreCase("userdn"))
            userAttrType=UserAttrType.USERDN;
        else if(expr.equalsIgnoreCase("groupdn")) {
             userAttrType=UserAttrType.GROUPDN;
      /*
            int msgID = MSGID_ACI_SYNTAX_INVALID_USERATTR_KEYWORD;
            String message = getMessage(msgID, "The groupdn userattr" +
                    "keyword is not supported.");
            throw new AciException(msgID, message);
        */
        } else if(expr.equalsIgnoreCase("roledn")) {
            userAttrType=UserAttrType.ROLEDN;
            /*
            int msgID = MSGID_ACI_SYNTAX_INVALID_USERATTR_KEYWORD;
            String message = getMessage(msgID, "The roledn userattr" +
                    "keyword is not supported.");
            throw new AciException(msgID, message);
            */
        } else if(expr.equalsIgnoreCase("ldapurl"))
            userAttrType=UserAttrType.URL;
        else
            userAttrType=UserAttrType.VALUE;
        return userAttrType;
    }

    /**
     * Evaluate an URL userattr type. Look into the resource entry for the
     * specified attribute and values. Assume it is an URL. Decode it an try
     * and match it against the client entry attribute.
     * @param evalCtx  The evaluation context to evaluate with.
     * @return An enumeration containing a result of the URL evaluation.
     */
    private EnumEvalResult evalURL(AciEvalContext evalCtx) {
        EnumEvalResult matched= EnumEvalResult.FALSE;
        boolean undefined=false;
        AttributeType attrType;
        if((attrType = DirectoryServer.getAttributeType(attrStr)) == null)
            attrType = DirectoryServer.getDefaultAttributeType(attrStr);
        List<Attribute> attrs=evalCtx.getResourceEntry().getAttribute(attrType);
        if(!attrs.isEmpty()) {
            for(Attribute a : attrs) {
                LinkedHashSet<AttributeValue> vals=a.getValues();
                for(AttributeValue v : vals) {
                    String urlStr=v.getStringValue();
                    LDAPURL url;
                    try {
                       url=LDAPURL.decode(urlStr, true);
                    } catch (DirectoryException e) {
                        break;
                    }
                    matched=UserDN.evalURL(evalCtx, url);
                    if(matched != EnumEvalResult.FALSE)
                        break;
                }
                if(matched == EnumEvalResult.TRUE)
                    break;
                if(matched == EnumEvalResult.ERR) {
                    undefined=true;
                    break;
                }
            }
        }
        return matched.getRet(type, undefined);
    }

    /**
     * Evaluate the DN type userattr keywords. These are roledn, userdn and
     * groupdn. The processing is the same for all three, although roledn is
     * a slightly different. For the roledn userattr keyword, a very simple
     * parent inheritance class was created. The rest of the processing is the
     * same for all three keywords.
     *
     * @param evalCtx The evaluation context to evaluate with.
     * @return An enumeration containing a result of the USERDN evaluation.
     */
    private EnumEvalResult evalDNKeywords(AciEvalContext evalCtx) {
        EnumEvalResult matched= EnumEvalResult.FALSE;
        boolean undefined=false, stop=false;
        int numLevels=parentInheritance.getNumLevels();
        int[] levels=parentInheritance.getLevels();
        AttributeType attrType=parentInheritance.getAttributeType();
        for(int i=0;((i < numLevels) && !stop); i++ ) {
            //The ROLEDN keyword will always enter this statement. The others
            //might. For the add operation, the resource itself (level 0)
            //must never be allowed to give access.
            if(levels[i] == 0) {
                if(evalCtx.isAddOperation()) {
                    undefined=true;
                } else if (evalCtx.getResourceEntry().hasAttribute(attrType)) {
                    matched =
                        evalEntryAttr(evalCtx.getResourceEntry(),
                                evalCtx,attrType);
                   if(matched.equals(EnumEvalResult.TRUE))
                        stop=true;
                }
            } else {
                DN pDN=
                        getDNParentLevel(levels[i], evalCtx.getResourceDN());
                if(pDN == null)
                    continue;
                InternalClientConnection conn =
                        InternalClientConnection.getRootConnection();
                InternalSearchOperation op = conn.processSearch(pDN,
                        SearchScope.BASE_OBJECT,
                        DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                        filter, null);
                LinkedList<SearchResultEntry> result =
                        op.getSearchEntries();
                if (!result.isEmpty()) {
                    Entry e = result.getFirst();
                    if (e.hasAttribute(attrType)) {
                        matched = evalEntryAttr(e, evalCtx, attrType);
                        if(matched.equals(EnumEvalResult.TRUE))
                            stop=true;
                    }
                }
            }
        }
        return matched.getRet(type, undefined);
    }

    /**
     * This method returns a parent DN based on the level. Not very
     * sophisticated but it works.
     * @param l The level.
     * @param dn The DN to get the parent of.
     * @return Parent DN based on the level or null if the level is greater
     * than the  rdn count.
     */
    private DN getDNParentLevel(int l, DN dn) {
        int rdns=dn.getNumComponents();
        if(l > rdns)
            return null;
        DN theDN=dn;
        for(int i=0; i < l;i++) {
            theDN=theDN.getParent();
        }
        return theDN;
    }


    /**
     * This method evaluates the user attribute type and calls the correct
     * evalaution method. The three user attribute types that can be selected
     * are USERDN or GROUPDN.
     *
     * @param e The entry to use in the evaluation.
     * @param evalCtx The evaluation context to use in the evaluation.
     * @param attributeType The attribute type to use in the evaluation.
     * @return The result of the evaluation routine.
     */
    private EnumEvalResult evalEntryAttr(Entry e, AciEvalContext evalCtx,
                                         AttributeType attributeType) {
        EnumEvalResult result=EnumEvalResult.FALSE;
        switch (userAttrType) {
            case USERDN: {
                result=UserDN.evaluate(e, evalCtx.getClientDN(),
                                       attributeType);
                break;
            }
            case GROUPDN: {
                result=GroupDN.evaluate(e, evalCtx, attributeType);
                break;
            }
        }
        return result;
    }

}
