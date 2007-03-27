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
import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.types.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;

/**
 * The TargAttrFilters class represents a targattrfilters rule of an ACI.
 */
public class TargAttrFilters {

    /*
     * A valid targattrfilters rule may have two TargFilterlist parts -- the
     * first one is required.
     */
    TargAttrFilterList firstFilterList=null;
    TargAttrFilterList secondFilterList=null;

    /*
     * Regular expression group position for the first operation value.
     */
    private static final int firstOpPos = 1;

    /*
     * Regular expression group position for the rest of an partially parsed
     * rule.
     */
    private static final int restOfExpressionPos=2;

    /*
     * Regular expression used to match the operation group (either add or del).
     */
    private static final String ADD_OR_DEL_KEYWORD_GROUP = "(add|del)";

    /**
     * Regular expression used to match the second operation of the filter list.
     * If the first was "add" this must be "del", if the first was "del" this
     * must be "add".
     */
    public static final String secondOp =
            "[,]{1}" + ZERO_OR_MORE_WHITESPACE + "del|add" +
            ZERO_OR_MORE_WHITESPACE + EQUAL_SIGN + ZERO_OR_MORE_WHITESPACE;

    /*
     * Regular expression used to match the first targFilterList, it must exist
     * or an exception is thrown.
     */
    private static final String firstOp = "^" + ADD_OR_DEL_KEYWORD_GROUP +
            ZERO_OR_MORE_WHITESPACE + EQUAL_SIGN + ZERO_OR_MORE_WHITESPACE;

    /*
     * Regular expression used to group the remainder of a partially parsed
     * rule.  Any character one or more times.
     */
    private static String restOfExpression = "(.+)";

    /*
     * Regular expression used to match the first operation keyword and the
     * rest of the expression.
     */
    private static String keywordFullPattern = firstOp + restOfExpression;

    /*
     * The enumeration representing the operation.
     */
    EnumTargetOperator op;

    /*
     * A mask used to denote if the rule has add, del or both operations in the
     * composite TargFilterList parts.
     */
    private int operationMask;

    /**
     * Represents an targatterfilters keyword rule.
     * @param op The enumeration representing the operation type.
     *
     * @param firstFilterList  The first filter list class parsed from the rule.
     * This one is required.
     *
     * @param secondFilterList The second filter list class parsed from the
     * rule. This one is optional.
     */
    public TargAttrFilters(EnumTargetOperator op,
                           TargAttrFilterList firstFilterList,
                           TargAttrFilterList secondFilterList ) {
        this.op=op;
        this.firstFilterList=firstFilterList;
        operationMask=firstFilterList.getMask();
        if(secondFilterList != null) {
            //Add the second filter list mask to the mask.
            operationMask |= secondFilterList.getMask();
            this.secondFilterList=secondFilterList;
        }
    }

    /**
     * Decode an targattrfilter rule.
     * @param type The enumeration representing the type of this rule. Defaults
     * to equality for this target.
     *
     * @param expression The string expression to be decoded.
     * @return  A TargAttrFilters class representing the decode expression.
     * @throws AciException If the expression string contains errors and
     * cannot be decoded.
     */
    public static TargAttrFilters decode(EnumTargetOperator type,
                                        String expression) throws AciException {
        Pattern fullPattern=Pattern.compile(keywordFullPattern);
        Matcher matcher = fullPattern.matcher(expression);
        //First match for overall correctness and to get the first operation.
        if(!matcher.find()) {
            int msgID = MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_EXPRESSION;
            String message = getMessage(msgID, expression);
            throw new AciException(msgID, message);
        }
        String firstOp=matcher.group(firstOpPos);
        String subExpression=matcher.group(restOfExpressionPos);
        //This pattern is built dynamically and is used to see if the operations
        //in the two filter list parts (if the second exists) are equal. See
        //comment below.
        String opPattern=
                "[,]{1}" + ZERO_OR_MORE_WHITESPACE  +
                firstOp + ZERO_OR_MORE_WHITESPACE + EQUAL_SIGN +
                ZERO_OR_MORE_WHITESPACE;
        String[] temp=subExpression.split(opPattern);
        /**
         * Check that the initial list operation is not equal to the second.
         * For example:  Matcher find
         *
         *  "add:cn:(cn=foo), add:cn:(cn=bar)"
         *
         * This is invalid.
         */
        if(temp.length > 1) {
            int msgID =
                    MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_OPS_MATCH;
            String message = getMessage(msgID, expression);
            throw new AciException(msgID, message);
        }
        /**
         * Check that there are not too many filter lists. There can only
         * be either one or two.
         */
        String[] filterLists=
                subExpression.split(secondOp, -1);
        if(filterLists.length > 2) {
            int msgID =
                    MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_MAX_FILTER_LISTS;
            String message = getMessage(msgID, expression);
            throw new AciException(msgID, message);
        }
        TargAttrFilterList firstFilterList =
                TargAttrFilterList.decode(getMask(firstOp), filterLists[0]);
        TargAttrFilterList secondFilterList=null;
        //Handle the second filter list if there is one.
        if(filterLists.length == 2) {
            String temp2= filterLists[1].substring(1,filterLists[1].length());
            //Assume the first op is an "add" so this has to be a "del".
            String secondOp="del";
            //If the first op is a "del", the second has to be an "add".
            if(getMask(firstOp) == TARGATTRFILTERS_DELETE)
                secondOp="add";
            secondFilterList =
                    TargAttrFilterList.decode(getMask(secondOp), temp2);
        }
        return new TargAttrFilters(type, firstFilterList, secondFilterList);
    }

    /**
     * Return the mask corrsponding to the specified string.
     * @param op The op string.
     * @return   The mask corresponding to the operation string.
     */
    private static  int getMask(String op) {
        if(op.equals("add"))
            return TARGATTRFILTERS_ADD;
        else
            return TARGATTRFILTERS_DELETE;
    }

    /**
     * Gets the TargFilterList  corresponding to the mask value.
     * @param matchCtx The target match context containing the rights to
     * match against.
     * @return  A TargAttrFilterList matching both the rights of the target
     * match context and the mask of the TargFilterAttrList. May return null.
     */
    public TargAttrFilterList
    getTargAttrFilterList(AciTargetMatchContext matchCtx) {
        TargAttrFilterList filterList=null;
        int mask=ACI_NULL;
        //Set up the wanted mask by evaluating both the target match
        //context's rights and the mask.
        if((matchCtx.hasRights(ACI_WRITE_ADD) || matchCtx.hasRights(ACI_ADD)) &&
                hasMask(TARGATTRFILTERS_ADD))
            mask=TARGATTRFILTERS_ADD;
        else if((matchCtx.hasRights(ACI_WRITE_DELETE) ||
                 matchCtx.hasRights(ACI_DELETE)) &&
                hasMask(TARGATTRFILTERS_DELETE))
            mask=TARGATTRFILTERS_DELETE;
        //Check the first list first, it always has to be there. If it doesn't
        //match then check the second if it exists.
        if(firstFilterList.hasMask(mask))
            filterList=firstFilterList;
        else if((secondFilterList != null) &&
                secondFilterList.hasMask(mask))
            filterList=secondFilterList;
        return filterList;
    }

    /**
     * Check if this TargAttrFilters object is applicable to the target
     * specified match context. This check is only used for the LDAP modify
     * operation.
     * @param matchCtx The target match context containing the information
     * needed to match.
     * @return True if this TargAttrFitlers object is applicable to this
     * target match context.
     */
    public boolean isApplicableMod(AciTargetMatchContext matchCtx) {
        //Get the targFitlerList corresponding to this context's rights.
        TargAttrFilterList attrFilterList=getTargAttrFilterList(matchCtx);
        //If the list is empty return true and go on to the targattr check
        //in AciTargets.isApplicable().
        if(attrFilterList == null)
            return true;
        LinkedHashMap<AttributeType, SearchFilter> filterList  =
                attrFilterList.getAttributeTypeFilterList();
        boolean attrMatched=true;
        AttributeType attrType=matchCtx.getCurrentAttributeType();
        //If the filter list contains the current attribute type; check
        //the attribute types value(s) against the corresponding filter.
        // If the filter list does not contain the attribute type skip the
        // attribute type.
        if((attrType != null) && (filterList.containsKey(attrType))) {
            AttributeValue value=matchCtx.getCurrentAttributeValue();
            SearchFilter filter = filterList.get(attrType);
            attrMatched=matchFilterAttributeValue(attrType, value, filter);
            //This flag causes any targattr checks to be bypassed in AciTargets.
      //      if(attrMatched)
                matchCtx.setTargAttrFiltersMatch(true);
            if(op.equals(EnumTargetOperator.NOT_EQUALITY))
                attrMatched = !attrMatched;
        }
        return attrMatched;
    }

    /**
     * Check if this TargAttrFilters object is applicable to the specified
     * target match context. This check is only used for either LDAP add or
     * delete operations.
     * @param matchCtx The target match context containing the information
     * needed to match.
     * @return True if this TargAttrFilters object is applicable to this
     * target match context.
     */
    public boolean isApplicableAddDel(AciTargetMatchContext matchCtx) {
        TargAttrFilterList attrFilterList=getTargAttrFilterList(matchCtx);
        //List didn't match current operation return true.
        if(attrFilterList == null)
            return true;
        LinkedHashMap<AttributeType, SearchFilter> filterList  =
                attrFilterList.getAttributeTypeFilterList();
        Iterator<AttributeType> iterator=filterList.keySet().iterator();
        boolean attrMatched=true;
        //Get the resource entry.
        Entry resEntry=matchCtx.getResourceEntry();
        //Iterate through each attribute type in the filter list checking
        //the resource entry to see if it has that attribute type. If not
        //go to the next attribute type. If it is found, then check the entries
        //attribute type values against the filter.
        for(;iterator.hasNext() && attrMatched;) {
            AttributeType attrType=iterator.next();
            SearchFilter f=filterList.get(attrType);
            //Found a match in the entry, iterate over each attribute
            //type in the entry and check its values agaist the filter.
            if(resEntry.hasAttribute(attrType)) {
                ListIterator<Attribute> attrIterator=
                        resEntry.getAttribute(attrType).listIterator();
                for(;attrIterator.hasNext() && attrMatched;) {
                    Attribute a=attrIterator.next();
                    attrMatched=matchFilterAttributeValues(a, attrType, f);
                }
            }
        }
        if(op.equals(EnumTargetOperator.NOT_EQUALITY))
            attrMatched = !attrMatched;
        return attrMatched;
    }

    /**
     * Iterate over each attribute type attribute and compare the values
     * against the provided filter.
     * @param a The attribute from the resource entry.
     * @param attrType The attribute type currently working on.
     * @param filter  The filter to evaluate the values against.
     * @return  True if all of the values matched the filter.
     */
    private boolean matchFilterAttributeValues(Attribute a,
                                               AttributeType attrType,
                                               SearchFilter filter) {
        boolean filterMatch=true;
        Iterator<AttributeValue> valIterator = a.getValues().iterator();
        //Iterate through each value and apply the filter against it.
        for(; valIterator.hasNext() && filterMatch;) {
            AttributeValue value=valIterator.next();
            filterMatch=matchFilterAttributeValue(attrType, value, filter);
        }
        return filterMatch;
    }

    /**
     * Matches an specified attribute value against a specified filter. A dummy
     * entry is created with only a single attribute containing the value  The
     * filter is applied against that entry.
     *
     * @param attrType The attribute type currently being evaluated.
     * @param value  The value to match the filter against.
     * @param filter  The filter to match.
     * @return  True if the value matches the filter.
     */
    private boolean matchFilterAttributeValue(AttributeType attrType,
                                              AttributeValue value,
                                              SearchFilter filter) {
        boolean filterMatch;
        LinkedHashSet<AttributeValue> values =
                new LinkedHashSet<AttributeValue>();
        values.add(new AttributeValue(attrType, value.getValue()));
        Attribute attr =
                new Attribute(attrType, attrType.toString(), values);
        Entry e = new Entry(DN.nullDN(), null, null, null);
        e.addAttribute(attr, new ArrayList<AttributeValue>());
        try {
            filterMatch=filter.matchesEntry(e);
        } catch(DirectoryException ex) {
            filterMatch=false;
        }
        return filterMatch;
    }

    /**
     * Return true if the TargAttrFilters mask contains the specified mask.
     * @param mask  The mask to check for.
     * @return  True if the mask matches.
     */
    public boolean hasMask(int mask) {
        return (this.operationMask & mask) != 0;
    }

}
