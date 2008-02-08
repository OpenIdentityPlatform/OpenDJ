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

package org.opends.server.authorization.dseecompat;
import org.opends.messages.Message;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashMap;

/**
 * This class represents a single bind rule of an ACI permission-bind rule
 * pair.
 */
public class BindRule {

    /*
     * This hash table holds the keyword bind rule mapping.
     */
    private HashMap<String, KeywordBindRule> keywordRuleMap =
                                    new HashMap<String, KeywordBindRule>();

    /*
     * True is a boolean "not" was seen.
     */
    private boolean negate=false;

    /*
     * Complex bind rules have left and right values.
     */
    private BindRule left = null;
    private BindRule right = null;

    /*
     * Enumeration of the boolean type of the complex bind rule ("and" or "or").
     */
    private EnumBooleanTypes booleanType = null;

    /*
     * The keyword of a simple bind rule.
     */
    private EnumBindRuleKeyword keyword = null;

    /*
     * Regular expression group position of a bind rule keyword.
     */
    private static final int keywordPos = 1;

    /*
     * Regular expression group position of a bind rule operation.
     */
    private static final int opPos = 2;

    /*
     * Regular expression group position of a bind rule expression.
     */
    private static final int expressionPos = 3;

    /*
     * Regular expression group position of the remainder part of an operand.
     */
    private static final int remainingOperandPos = 1;

    /*
     * Regular expression group position of the remainder of the bind rule.
     */
    private static final int remainingBindrulePos = 2;

    /*
     * Regular expression for valid bind rule operator group.
     */
    private static final String opRegGroup = "([!=<>]+)";

    /*
     * Regular expression for the expression part of a partially parsed
     * bind rule.
     */
    private static final String expressionRegex =
                                  "\"([^\"]+)\"" + ZERO_OR_MORE_WHITESPACE;

    /*
     * Regular expression for a single bind rule.
     */
    private static final String bindruleRegex =
        WORD_GROUP_START_PATTERN + ZERO_OR_MORE_WHITESPACE +
        opRegGroup + ZERO_OR_MORE_WHITESPACE + expressionRegex;

    /*
     * Regular expression of the remainder part of a partially parsed bind rule.
     */
    private static final String remainingBindruleRegex =
        ZERO_OR_MORE_WHITESPACE_START_PATTERN + WORD_GROUP +
        ZERO_OR_MORE_WHITESPACE + "(.*)$";

    /**
     * Constructor that takes an keyword enumeration and corresponding
     * simple bind rule. The keyword string is the key for the keyword rule in
     * the keywordRuleMap. This is a simple bind rule representation:

     * keyword  op  rule
     *
     * An example of a simple bind rule is:
     *
     *  userdn = "ldap:///anyone"
     *
     * @param keyword The keyword enumeration.
     * @param rule The rule corresponding to this keyword.
     */
    private BindRule(EnumBindRuleKeyword keyword, KeywordBindRule rule) {
        this.keyword=keyword;
        this.keywordRuleMap.put(keyword.toString(), rule);
    }


    /*
     * TODO Verify that this handles the NOT boolean properly by
     * creating a unit test.
     *
     * I'm a bit confused by the constructor which takes left and right
     * arguments. Is it always supposed to have exactly two elements?
     * Is it supposed to keep nesting bind rules in a chain until all of
     * them have been processed?  The documentation for this method needs
     * to be a lot clearer.  Also, it doesn't look like it handles the NOT
     * type properly.
     */
    /**
     * Constructor that represents a complex bind rule. The left and right
     * bind rules are saved along with the boolean type operator. A complex
     * bind rule looks like:
     *
     *  bindrule   booleantype   bindrule
     *
     * Each side of the complex bind rule can be complex bind rule(s)
     * itself. An example of a complex bind rule would be:
     *
     * (dns="*.example.com" and (userdn="ldap:///anyone" or
     * (userdn="ldap:///cn=foo,dc=example,dc=com and ip=129.34.56.66)))
     *
     * This constructor should always have two elements. The processing
     * of a complex bind rule is dependent on the boolean operator type.
     * See the evalComplex method for more information.
     *
     *
     * @param left The bind rule left of the boolean.
     * @param right The right bind rule.
     * @param booleanType The boolean type enumeration ("and" or "or").
     */
    private BindRule(BindRule left, BindRule right,
            EnumBooleanTypes booleanType) {
        this.booleanType = booleanType;
        this.left = left;
        this.right = right;
    }

    /*
     * TODO Verify this method handles escaped parentheses by writing
     * a unit test.
     *
     * It doesn't look like the decode() method handles the possibility of
     * escaped parentheses in a bind rule.
     */
    /**
     * Decode an ACI bind rule string representation.
     * @param input The string representation of the bind rule.
     * @return A BindRule class representing the bind rule.
     * @throws AciException If the string is an invalid bind rule.
     */
    public static BindRule decode (String input)
    throws AciException {
        if ((input == null) || (input.length() == 0))
        {
          return null;
        }
        String bindruleStr = input.trim();
        char firstChar = bindruleStr.charAt(0);
        char[] bindruleArray = bindruleStr.toCharArray();

        if (firstChar == '(')
        {
          BindRule bindrule_1 = null;
          int currentPos;
          int numOpen = 0;
          int numClose = 0;

          // Find the associated closed parenthesis
          for (currentPos = 0; currentPos < bindruleArray.length; currentPos++)
          {
            if (bindruleArray[currentPos] == '(')
            {
              numOpen++;
            }
            else if (bindruleArray[currentPos] == ')')
            {
              numClose++;
            }
            if (numClose == numOpen)
            {
              //We found the associated closed parenthesis
              //the parenthesis are removed
              String bindruleStr1 = bindruleStr.substring(1, currentPos);
              bindrule_1 = BindRule.decode(bindruleStr1);
              break;
            }
          }
          /*
           * Check that the number of open parenthesis is the same as
           * the number of closed parenthesis.
           * Raise an exception otherwise.
           */
          if (numOpen > numClose) {
              Message message =
                  ERR_ACI_SYNTAX_BIND_RULE_MISSING_CLOSE_PAREN.get(input);
              throw new AciException(message);
          }
          /*
           * If there are remaining chars => there MUST be an
           * operand (AND / OR)
           * otherwise there is a syntax error
           */
          if (currentPos < (bindruleArray.length - 1))
          {
            String remainingBindruleStr =
                bindruleStr.substring(currentPos + 1);
            return createBindRule(bindrule_1, remainingBindruleStr);
          }
          else
          {
            return bindrule_1;
          }
        }
        else
        {
          StringBuilder b=new StringBuilder(bindruleStr);
          /*
           * TODO Verify by unit test that this negation
           * is correct. This code handles a simple bind rule negation such
           * as:
           *
           *  not userdn="ldap:///anyone"
           */
          boolean negate=determineNegation(b);
          bindruleStr=b.toString();
          Pattern bindrulePattern = Pattern.compile(bindruleRegex);
          Matcher bindruleMatcher = bindrulePattern.matcher(bindruleStr);
          int bindruleEndIndex;
          if (bindruleMatcher.find())
          {
            bindruleEndIndex = bindruleMatcher.end();
            BindRule bindrule_1 = parseAndCreateBindrule(bindruleMatcher);
            bindrule_1.setNegate(negate);
            if (bindruleEndIndex < bindruleStr.length())
            {
              String remainingBindruleStr =
                  bindruleStr.substring(bindruleEndIndex);
              return createBindRule(bindrule_1, remainingBindruleStr);
            }
            else {
              return bindrule_1;
            }
          }
          else {
              Message message =
                  ERR_ACI_SYNTAX_INVALID_BIND_RULE_SYNTAX.get(input);
              throw new AciException(message);
          }
        }
    }


    /**
     * Parses a simple bind rule using the regular expression matcher.
     * @param bindruleMatcher A regular expression matcher holding
     * the engine to use in the creation of a simple bind rule.
     * @return A BindRule determined by the matcher.
     * @throws AciException If the bind rule matcher found errors.
     */
    private static BindRule parseAndCreateBindrule(Matcher bindruleMatcher)
    throws AciException {
        String keywordStr = bindruleMatcher.group(keywordPos);
        String operatorStr = bindruleMatcher.group(opPos);
        String expression = bindruleMatcher.group(expressionPos);
        EnumBindRuleKeyword keyword;
        EnumBindRuleType operator;

        // Get the Keyword
        keyword = EnumBindRuleKeyword.createBindRuleKeyword(keywordStr);
        if (keyword == null)
        {
            Message message =
                WARN_ACI_SYNTAX_INVALID_BIND_RULE_KEYWORD.get(keywordStr);
            throw new AciException(message);
        }

        // Get the operator
        operator = EnumBindRuleType.createBindruleOperand(operatorStr);
        if (operator == null) {
            Message message =
                WARN_ACI_SYNTAX_INVALID_BIND_RULE_OPERATOR.get(operatorStr);
            throw new AciException(message);
        }

        //expression can't be null
        if (expression == null) {
            Message message =
                WARN_ACI_SYNTAX_MISSING_BIND_RULE_EXPRESSION.get(operatorStr);
            throw new AciException(message);
        }
        validateOperation(keyword, operator);
        KeywordBindRule rule = decode(expression, keyword, operator);
        return new BindRule(keyword, rule);
    }

    /**
     * Create a complex bind rule from a substring
     * parsed from the ACI string.
     * @param bindrule The left hand part of a complex bind rule
     * parsed previously.
     * @param remainingBindruleStr The string used to determine the right
     * hand part.
     * @return A BindRule representing a complex bind rule.
     * @throws AciException If the string contains an invalid
     * right hand bind rule string.
     */
    private static BindRule createBindRule(BindRule bindrule,
            String remainingBindruleStr) throws AciException {
        Pattern remainingBindrulePattern =
            Pattern.compile(remainingBindruleRegex);
        Matcher remainingBindruleMatcher =
            remainingBindrulePattern.matcher(remainingBindruleStr);
        if (remainingBindruleMatcher.find()) {
            String remainingOperand =
                remainingBindruleMatcher.group(remainingOperandPos);
            String remainingBindrule =
                remainingBindruleMatcher.group(remainingBindrulePos);
            EnumBooleanTypes operand =
                EnumBooleanTypes.createBindruleOperand(remainingOperand);
            if ((operand == null)
                    || ((operand != EnumBooleanTypes.AND_BOOLEAN_TYPE) &&
                            (operand != EnumBooleanTypes.OR_BOOLEAN_TYPE))) {
                Message message =
                        WARN_ACI_SYNTAX_INVALID_BIND_RULE_BOOLEAN_OPERATOR
                                .get(remainingOperand);
                throw new AciException(message);
            }
            StringBuilder ruleExpr=new StringBuilder(remainingBindrule);
            /* TODO write a unit test to verify.
             * This is a check for something like:
             * bindrule and not (bindrule)
             * or something ill-advised like:
             * and not not not (bindrule).
             */
            boolean negate=determineNegation(ruleExpr);
            remainingBindrule=ruleExpr.toString();
            BindRule bindrule_2 =
                BindRule.decode(remainingBindrule);
            bindrule_2.setNegate(negate);
            return new BindRule(bindrule, bindrule_2, operand);
        } else {
            Message message = ERR_ACI_SYNTAX_INVALID_BIND_RULE_SYNTAX.get(
                remainingBindruleStr);
            throw new AciException(message);
        }
    }

    /**
     * Tries to strip an "not" boolean modifier from the string and
     * determine at the same time if the value should be flipped.
     * For example:
     *
     * not not not bindrule
     *
     * is true.
     *
     * @param ruleExpr The bindrule expression to evaluate. This
     * string will be changed if needed.
     * @return True if the boolean needs to be negated.
     */
    private static boolean determineNegation(StringBuilder ruleExpr)  {
        boolean negate=false;
        String ruleStr=ruleExpr.toString();
        while(ruleStr.regionMatches(true, 0, "not ", 0, 4)) {
            negate = !negate;
            ruleStr = ruleStr.substring(4);
        }
        ruleExpr.replace(0, ruleExpr.length(), ruleStr);
        return negate;
    }

    /**
     * Set the negation parameter as determined by the function above.
     * @param v The value to assign negate to.
     */
    private void setNegate(boolean v) {
        negate=v;
    }

    /*
     * TODO This method needs to handle the userattr keyword. Also verify
     * that the rest of the keywords are handled correctly.
     * TODO Investigate moving this method into EnumBindRuleKeyword class.
     *
     * Does validateOperation need a default case?  Why is USERATTR not in this
     * list? Why is TIMEOFDAY not in this list when DAYOFWEEK is in the list?
     * Would it be more appropriate to put this logic in the
     * EnumBindRuleKeyword class so we can be sure it's always handled properly
     *  for all keywords?
     */
    /**
     * Checks the keyword operator enumeration to make sure it is valid.
     * This method doesn't handle all cases.
     * @param keyword The keyword enumeration to evaluate.
     * @param op The operation enumeration to evaluate.
     * @throws AciException If the operation is not valid for the keyword.
     */
    private static void validateOperation(EnumBindRuleKeyword keyword,
                                        EnumBindRuleType op)
    throws AciException {
        switch (keyword) {
        case USERDN:
        case ROLEDN:
        case GROUPDN:
        case IP:
        case DNS:
        case AUTHMETHOD:
        case DAYOFWEEK:
            if ((op != EnumBindRuleType.EQUAL_BINDRULE_TYPE)
                    && (op != EnumBindRuleType.NOT_EQUAL_BINDRULE_TYPE)) {
                Message message =
                  WARN_ACI_SYNTAX_INVALID_BIND_RULE_KEYWORD_OPERATOR_COMBO
                          .get(keyword.toString(), op.toString());
                throw new AciException(message);
            }
        }
    }

    /*
     * TODO Investigate moving into the EnumBindRuleKeyword class.
     *
     * Should we move the logic in the
     * decode(String,EnumBindRuleKeyword,EnumBindRuleType) method into the
     * EnumBindRuleKeyword class so we can be sure that it's always
     * handled properly for all keywords?
     */
    /**
     * Creates a keyword bind rule suitable for saving in the keyword
     * rule map table. Each individual keyword class will do further
     * parsing and validation of the expression string.  This processing
     * is part of the simple bind rule creation.
     * @param expr The expression string to further parse.
     * @param keyword The keyword to create.
     * @param op The operation part of the bind rule.
     * @return A keyword bind rule class that can be stored in the
     * map table.
     * @throws AciException If the expr string contains a invalid
     * bind rule.
     */
    private static KeywordBindRule decode(String expr,
                                          EnumBindRuleKeyword keyword,
                                          EnumBindRuleType op)
            throws AciException  {
        KeywordBindRule rule ;
        switch (keyword) {
            case USERDN:
            {
                rule = UserDN.decode(expr, op);
                break;
            }
            case ROLEDN:
            {
                //The roledn keyword is not supported. Throw an exception with
                //a message if it is seen in the ACI.
                Message message =
                    WARN_ACI_SYNTAX_ROLEDN_NOT_SUPPORTED.get(expr);
                throw new AciException(message);
            }
            case GROUPDN:
            {
                rule = GroupDN.decode(expr, op);
                break;
            }
            case IP:
            {
                rule=IP.decode(expr, op);
                break;
            }
            case DNS:
            {
                rule = DNS.decode(expr, op);
                break;
            }
            case DAYOFWEEK:
            {
                rule = DayOfWeek.decode(expr, op);
                break;
            }
            case TIMEOFDAY:
            {
                rule=TimeOfDay.decode(expr, op);
                break;
            }
            case AUTHMETHOD:
            {
                rule = AuthMethod.decode(expr, op);
                break;
            }
            case USERATTR:
            {
                rule = UserAttr.decode(expr, op);
                break;
            }
            default:  {
                Message message = WARN_ACI_SYNTAX_INVALID_BIND_RULE_KEYWORD.get(
                    keyword.toString());
                throw new AciException(message);
            }
        }
        return rule;
    }

    /**
     * Evaluate the results of a complex bind rule. If the boolean
     * is an AND type then left and right must be TRUE, else
     * it must be an OR result and one of the bind rules must be
     * TRUE.
     * @param left The left bind rule result to evaluate.
     * @param right The right bind result to evaluate.
     * @return The result of the complex evaluation.
     */
    private EnumEvalResult evalComplex(EnumEvalResult left,
                                       EnumEvalResult right) {
        EnumEvalResult ret=EnumEvalResult.FALSE;
        if(booleanType == EnumBooleanTypes.AND_BOOLEAN_TYPE) {
           if((left == EnumEvalResult.TRUE) && (right == EnumEvalResult.TRUE))
                ret=EnumEvalResult.TRUE;
        } else if((left == EnumEvalResult.TRUE) ||
                  (right == EnumEvalResult.TRUE))
            ret=EnumEvalResult.TRUE;
       return ret;
    }

    /**
     * Evaluate an bind rule against an evaluation context. If it is a simple
     * bind rule (no boolean type) then grab the keyword rule from the map
     * table and call the corresponding evaluate function. If it is a
     * complex rule call the routine above "evalComplex()".
     * @param evalCtx The evaluation context to pass to the keyword
     * evaluation function.
     * @return An result enumeration containing the result of the evaluation.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult ret;
        //Simple bind rules have a null booleanType enumeration.
        if(this.booleanType == null) {
            KeywordBindRule rule=keywordRuleMap.get(keyword.toString());
            ret = rule.evaluate(evalCtx);
        }  else
            ret=evalComplex(left.evaluate(evalCtx),right.evaluate(evalCtx));
        return EnumEvalResult.negateIfNeeded(ret, negate);
    }
}
