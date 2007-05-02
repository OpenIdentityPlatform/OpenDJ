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
import java.util.HashSet;
import java.util.regex.Pattern;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;

/**
 * A class representing an ACI's targetattr keyword.
 */
public class TargetAttr {
    /*
     * Enumeration representing the targetattr operator.
     */
    private EnumTargetOperator operator = EnumTargetOperator.EQUALITY;

    /*
     * Flags that is set if all attributes pattern seen "*".
     */
    private boolean allAttributes = false ;

    /*
     * HashSet of the attribute types parsed by the constructor.
     */
    private HashSet<AttributeType> attributes = new HashSet<AttributeType>();

  /**
   * HashSet of the operational attribute types parsed by the constructor.
   */
  private HashSet<AttributeType> opAttributes = new HashSet<AttributeType>();

    /*
     * Regular expression that matches one or more ATTR_NAME's separated by
     * the "||" token.
     */
    private static final String attrListRegex  =  ZERO_OR_MORE_WHITESPACE +
           ATTR_NAME + ZERO_OR_MORE_WHITESPACE + "(" +
            LOGICAL_OR + ZERO_OR_MORE_WHITESPACE + ATTR_NAME +
            ZERO_OR_MORE_WHITESPACE + ")*";

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
            if (Pattern.matches(ALL_ATTRS_WILD_CARD, attrString) ){
                allAttributes = true ;
            } else {
                if (Pattern.matches(ZERO_OR_MORE_WHITESPACE, attrString)){
                    allAttributes = false;
                } else {
                    if (Pattern.matches(attrListRegex, attrString)) {
                        // Remove the spaces in the attr string and
                        // split the list.
                        Pattern separatorPattern =
                            Pattern.compile(LOGICAL_OR);
                        attrString=
                         attrString.replaceAll(ZERO_OR_MORE_WHITESPACE, "");
                        String[] attributeArray=
                             separatorPattern.split(attrString);
                        //Add each element of array to attributes HashSet
                        //after converting it to AttributeType.
                        arrayToAttributeTypes(attributeArray);
                       //Must be either all operational attrs or all user attrs,
                       //but not both.
                        if(!opAttributes.isEmpty() && !attributes.isEmpty()) {
                            int msgID =
                             MSGID_ACI_TARGETATTR_INVALID_OP_USER_ATTR;
                            String message = getMessage(msgID, attrString);
                            throw new AciException(msgID, message);
                        }
                        //Inequality not allowed with operational attrs.
                        if(!opAttributes.isEmpty() &&
                            operator.equals(EnumTargetOperator.NOT_EQUALITY)) {
                            int msgID =
                               MSGID_ACI_TARGATTR_INVALID_OP_ATTR_INEQUALITY;
                            String message = getMessage(msgID, attrString);
                            throw new AciException(msgID, message);
                        }
                    } else {
                      int msgID =
                         MSGID_ACI_SYNTAX_INVALID_TARGETATTRKEYWORD_EXPRESSION;
                      String message = getMessage(msgID, attrString);
                      throw new AciException(msgID, message);
                    }
                }
            }
        }
    }

    /**
     * Converts each element of an array of attribute type strings
     * to attribute types and adds them to either the attributes HashSet or
     * the operational attributes HashSet if they are operational.
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
          if(attributeType.isOperational())
           opAttributes.add(attributeType);
          else
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
   * Return array holding  operational attribute types to be evaluated
   * in the expression.
   * @return  Array holding attribute types.
   */
  public HashSet<AttributeType> getOpAttributes() {
        return opAttributes;
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
     * is  seen when an ACI is parsed.  This boolean only applies to
     * non-operational attribute types. If the attribute type being evaluated
     * and the isAllAttributes is true, then the evaluation will return false
     * because operational attributes must be explicity defined.
     *
     * If the isAllAttributes boolean is true (and the attribute is
     * non-operational), the second check is skipped and the TargetAttr's
     * operator is checked to see if the method should return false
     * (NOT_EQUALITY) instead of true.
     *
     * If the isAllAttributes boolean is false, then the attribute type is
     * checked to see if it is operational. If it is, then the operational
     * HashSet is searched to see if it contains the operational attribute
     * type. If it is found then true is returned, else false is returned
     * if it isn't found. The NOT_EQUALITY operator is invalid for operational
     * attribute types and is not checked.
     *
     * If the attribute is not operational,  then the TargeAttr's user
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
        //If it is an operational attribute, then access is denied for all
        //attributes wild-card. Operational attributes must be
        // explicitly defined and cannot be negated.
        if(a.isOperational()) {
          ret=false;
        } else
          ret =
             !targetAttr.getOperator().equals(EnumTargetOperator.NOT_EQUALITY);
      }  else {
        ret=false;
          HashSet<AttributeType> attributes=targetAttr.getAttributes();
          HashSet<AttributeType> opAttributes=targetAttr.getOpAttributes();
           //Check if the attribute is operational, if so check the
           //operation HashSet.
           if(a.isOperational()) {
             if(opAttributes.contains(a))
               ret=true;
         } else {
            if(attributes.contains(a))
              ret=true;
            if(targetAttr.getOperator().equals(EnumTargetOperator.NOT_EQUALITY))
              ret = !ret;
          }
       }
      return ret;
    }
}
