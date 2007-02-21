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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;

/**
 * A class representing an ACI's targetattr keyword.
 */
public class TargetAttr {
    private EnumTargetOperator operator = EnumTargetOperator.EQUALITY;
    private boolean allAttributes = false ;
    /*
     * HashSet of the attribute types parsed by the constructor.
     */
    private HashSet<AttributeType> attributes = new HashSet<AttributeType>();
    //private String[] attributes = new String[0];
    private static final String allAttrsRegex  = "\\s*\\*\\s*";
    private static final String noAttrsRegex   = "\\s*";
    private static final String separatorToken = "\\|\\|";
    private static final String attrListRegex  =
        "\\s*(\\w+)\\s*(" + separatorToken + "\\s*(\\w+)\\s*)*";

    /**
     * Constructor creating a class representing a targetattr keyword of an ACI.
     * @param operator The operation enumeration of the targetattr
     * expression (=, !=).
     * @param attrString A string representing the attributes specified in
     * the targetattr expression (ie, dn || cn).
     * @throws AciException If the attrs string is invalid.
     */
    private TargetAttr(EnumTargetOperator operator, String attrString)
    throws AciException {
        this.operator = operator;
        if (attrString != null) {
            if (Pattern.matches(allAttrsRegex, attrString) ){
                allAttributes = true ;
            } else {
                if (Pattern.matches(noAttrsRegex, attrString)){
                    allAttributes = false;
                } else {
                    if (Pattern.matches(attrListRegex, attrString)) {
                        // Remove the spaces in the attr string and
                        // split the list.
                        Pattern separatorPattern =
                            Pattern.compile(separatorToken);
                        attrString=attrString.replaceAll("\\s", "");
                        String[] attributeArray=
                             separatorPattern.split(attrString);
                        //Add each element of array to attributes HashSet
                        //after converting it to AttributeType.
                        arrayToAttributeTypes(attributeArray);
                    } else {
                      int msgID =
                         MSGID_ACI_SYNTAX_INVALID_TARGETATTRKEYWORD_EXPRESSION;
                      String message = getMessage(msgID, operator);
                      throw new AciException(msgID, message);
                    }
                }
            }
        }
    }

    /**
     * Converts each element of an array of attribute type strings
     * to attribute types and adds them to the attributes HashSet.
     * @param attributeArray The array of attribute type strings.
     */
    private void arrayToAttributeTypes(String[] attributeArray) {
        for (int i=0, n=attributeArray.length; i < n; i++) {
            String attribute=attributeArray[i].toLowerCase();
            AttributeType attributeType;
            if((attributeType =
                DirectoryServer.getAttributeType(attribute)) == null)
                attributeType =
                    DirectoryServer.getDefaultAttributeType(attribute);
            attributes.add(attributeType);
        }
    }
    /**
     * Returns the operator enumeration of the targetattr expression.
     * @return The operator enumeration.
     */
    public EnumTargetOperator getOperator() {
        return operator;
    }

    /**
     * This flag is set if the parsing code saw:
     * targetattr="*" or targetattr != "*".
     * @return True if all attributes was seen.
     */
    public boolean isAllAttributes() {
        return allAttributes;
    }

    /**
     * Return array holding each attribute type to be evaluated
     * in the expression.
     * @return Array holding each attribute types.
     */
    public HashSet<AttributeType> getAttributes() {
        return attributes;
    }

    /**
     * Decodes an targetattr expression string into a targetattr class suitable
     * for evaluation.
     * @param operator The operator enumeration of the expression.
     * @param expr The expression string to be decoded.
     * @return A TargetAttr suitable to evaluate this ACI's targetattrs.
     * @throws AciException If the expression string is invalid.
     */
    public static TargetAttr decode(EnumTargetOperator operator, String expr)
            throws AciException  {
        return new TargetAttr(operator, expr);
    }

    /**
     * Perform two checks to see if a specified attribute type is applicable.
     * First, check the targetAttr's isAllAttributes() boolean. The
     * isAllAttributes boolean is set true when the string:
     *
     *       targetattrs="*"
     *
     * is  seen when an ACI is parsed. If the isAllAttributes boolean is
     * true, the second check is skipped and the TargetAttr's operator is
     * checked to see if the method should return false (NOT_EQUALITY)
     * instead of true.
     *
     * If the isAllAttributes boolean is false, then the TargeAttr's
     * attribute type HashSet is searched to see if it contains the
     * specified attribute type. That result could be negated depending
     * on if the TargetAttr's operator is NOT_EQUALITY.
     *
     * @param a The attribute type to evaluate.
     * @param targetAttr The ACI's TargetAttr class to evaluate against.
     * @return The boolean result of the above tests and application
     * TargetAttr's operator value applied to the test result.
     */
    public static boolean isApplicable(AttributeType a,
                          TargetAttr targetAttr) {
      boolean ret;
      if(targetAttr.isAllAttributes()) {
          ret =
             !targetAttr.getOperator().equals(EnumTargetOperator.NOT_EQUALITY);
      }  else {
          ret=false;
          HashSet<AttributeType> attributes=targetAttr.getAttributes();
          if(attributes.contains(a))
              ret=true;
          if(targetAttr.getOperator().equals(EnumTargetOperator.NOT_EQUALITY))
              ret = !ret;
      }
      return ret;
    }
}
