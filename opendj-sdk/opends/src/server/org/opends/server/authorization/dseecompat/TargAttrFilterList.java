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
import org.opends.server.types.SearchFilter;
import org.opends.server.types.DirectoryException;
import org.opends.server.core.DirectoryServer;
import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import static org.opends.server.messages.MessageHandler.getMessage;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.LinkedHashMap;

/**
 * The TargAttrFilterList class represents an targattrfilters list. A
 * targattrfilters list looks like:
 *
 *   "Op=attr1:F1 [(&& attr2:F2)*]
 */
public class TargAttrFilterList {

    /*
     * The mask coresponding to the operation of this list (add or del).
     */
    private int mask=0;

    /*
     * ListHashMap keyed by the attribute type and mapping to the corresponding
     * search filter. LinkedHashMap is used so everything is in order.
     */
    private LinkedHashMap<AttributeType, SearchFilter> attrFilterList;

    /*
     * Regular expression group count.
     */
    private static int expectedGroupCount=2;

    /*
     * Regular expression attribute group position.
     */
    private static int attributePos=1;

    /*
     * Regular expression filter group position.
     */
    private static int filterPos=2;

    /*
     * Regular expression used to match a filter list including the strange
     * "and" token used to join the multiple attribute type filter pairs.
     */
    private static final String filterListSeperator =
              ZERO_OR_MORE_WHITESPACE  + "&&" + ZERO_OR_MORE_WHITESPACE;

    /*
     * Regular expression used to match an attribute filter pair.
     */
    private static final String attributeFilter=
            ATTR_NAME + ZERO_OR_MORE_WHITESPACE + ":{1}" +
            ZERO_OR_MORE_WHITESPACE + "(\\({1}.*\\){1})";

    /**
     * Construct a class representing an targattrfilters filter list.
     * @param mask The mask representing the operation.
     * @param attrFilterList The list map containing the attribute type
     * filter mappings.
     */
    public TargAttrFilterList(int mask,
                    LinkedHashMap<AttributeType, SearchFilter> attrFilterList) {
        this.mask=mask;
        this.attrFilterList=attrFilterList;
    }

    /**
     * Decode an TargAttrFilterList from the specified expression string.
     * @param mask  The mask representing the operation.
     * @param expression The expression string to decode.
     * @return A TargAttrFilterList class representing the targattrfilters
     * filter list.
     * @throws AciException If the expression string contains errors.
     */
    public static TargAttrFilterList decode(int mask, String expression)
            throws AciException {
        LinkedHashMap<AttributeType, SearchFilter> attrFilterList =
                new LinkedHashMap<AttributeType, SearchFilter>();
        String[] subExpressions=expression.split(filterListSeperator, -1);
        //Iterate over each sub-expression, parse and add them to the list
        //if there are no errors.
        for(String subs : subExpressions) {
            Pattern pattern=Pattern.compile(attributeFilter);
            Matcher matcher=pattern.matcher(subs);
            //Match the attribute:filter pair part of the expression
            if(!matcher.find() || matcher.groupCount() != expectedGroupCount) {
                int msgID =
                    MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LIST_FORMAT;
                String message = getMessage(msgID, expression);
                throw new AciException(msgID, message);
            }
            String attributeName=matcher.group(attributePos).toLowerCase();
            //Strip off any options, so it will match the filter option
            //handling.
            int semicolon = attributeName.indexOf(';');
            if (semicolon != -1)
                attributeName=attributeName.substring(0, semicolon);
            String filterString=matcher.group(filterPos);
            AttributeType attributeType;
            if((attributeType =
                    DirectoryServer.getAttributeType(attributeName)) == null)
                attributeType =
                        DirectoryServer.getDefaultAttributeType(attributeName);
            SearchFilter filter;
            //Check if it is a valid filter and add it to the list map if ok.
            try {
               filter = SearchFilter.createFilterFromString(filterString);
               attrFilterList.put(attributeType, filter);
            } catch (DirectoryException ex) {
                String er=ex.getErrorMessage();
                int msgID =
                   MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LISTS_FILTER;
                String message = getMessage(msgID, filterString, er);
                throw new AciException(msgID, message);
            }
            //Verify the filter components. This check assures that each
            //attribute type in the filter matches the provided attribute
            //type.
            verifyFilterComponents(filter, attributeType);
        }
        return new TargAttrFilterList(mask, attrFilterList);
    }

    /**
     * Verify the filter component attribute types by assuring that each
     * attribute type in the filter matches the specified attribute type.
     * @param filter  The filter to verify.
     * @param type The attribute type to use in the verification.
     * @throws AciException  If the filter contains an attribute type not
     * specified.
     */
    private static void  verifyFilterComponents(SearchFilter filter,
                                                AttributeType type)
            throws AciException {
        switch (filter.getFilterType()) {
            case AND:
            case OR: {
                for (SearchFilter f : filter.getFilterComponents()) {
                    verifyFilterComponents(f, type);
                }
                break;
            }
            case NOT:  {
                SearchFilter f = filter.getNotComponent();
                verifyFilterComponents(f, type);
                break;
            }
            default: {
                AttributeType attrType=filter.getAttributeType();
                if(!attrType.equals(type)) {
                    int msgID =
              MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LISTS_ATTR_FILTER;
                    String message = getMessage(msgID, filter.toString());
                    throw new AciException(msgID, message);
                }
            }
        }
    }

    /**
     * Return the mask of this TargAttrFilterList.
     * @return  The mask value.
     */
    public int getMask() {
        return this.mask;
    }

    /**
     * Check if the mask value of this TargAttrFilterList class contains the
     * specified mask value.
     * @param mask The mask to check for.
     * @return  True if the mask matches the specified value.
     */
    public boolean hasMask(int mask) {
        return (this.mask & mask) != 0;
    }

    /**
     * Return the list map holding the attribute type to filter mappings.
     * @return  The list map.
     */
    public
    LinkedHashMap<AttributeType, SearchFilter> getAttributeTypeFilterList() {
        return  attrFilterList;
    }
}
