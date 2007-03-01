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

import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.SearchFilter;

/**
 * A class representing an ACI target keyword.
 */
public class Target
{
    private EnumTargetOperator operator = EnumTargetOperator.EQUALITY;
    private LDAPURL targetURL = null;
    private DN urlDN=null;
    private boolean isPattern=false;
    private SearchFilter filter=null;
    private AttributeType targetType;


      /*
     * TODO Save aciDN parameter and use it in matchesPattern re-write.
     *
     * Should the aciDN argument provided to the constructor be stored so that
     * it can be used in the matchesPattern() method?  The DN should only be
     * considered a potential match if it is at or below the entry containing
     * the ACI.
     *
     * TODO Evaluate re-writing pattern (substring) determination code. The
     * current code is similar to current DS6 implementation.
     *
     * I'm confused by the part of the constructor that generates a search
     * filter. First, there is no substring matching rule defined for the
     * DN syntax in the official standard, so technically trying to perform
     * substring matching against DNs is illegal.  Although we do try to use
     * the caseIgnoreSubstringsMatch rule, it is extremely unreliable for DNs
     * because it's just not possible to do substring matching correctly in all
     * cases for them.  Also, the logic in place there will only generate a
     * filter if the DN contains a wildcard, and if it starts with a wildcard
     * (which is handled by the targetDN.startsWith("*") clause), then you'll
     * end up with something like "(target=**dc=example,dc=com)", which isn't
     *  legal.
     */
    /**
     * This constructor parses the target string.
     * @param operator  An enumeration of the operation of this target.
     * @param target A string representation of the target.
     * @param aciDN The dn of the ACI entry used for a descendant check.
     * @throws AciException If the target string is invalid.
     */
    private Target(EnumTargetOperator operator, String target, DN aciDN)
            throws AciException {
        this.operator = operator;
        try {
          String ldapURLRegex = "\\s*(ldap:///[^\\|]+)";
          if (!Pattern.matches(ldapURLRegex, target)) {
              int msgID = MSGID_ACI_SYNTAX_INVALID_TARGETKEYWORD_EXPRESSION;
              String message = getMessage(msgID, target);
              throw new AciException(msgID, message);
          }
          targetURL =  LDAPURL.decode(target, false);
          urlDN=targetURL.getBaseDN();
          String targetDN=urlDN.toNormalizedString();
          if((targetDN.startsWith("*")) ||
             (targetDN.indexOf("*") != -1)) {
              this.isPattern=true;
              String pattern="target=*"+targetDN;
              filter=SearchFilter.createFilterFromString(pattern);
              targetType = DirectoryServer.getAttributeType("target");
              if (targetType == null)
                  targetType =
                          DirectoryServer.getDefaultAttributeType("target");
          } else {
              if(!urlDN.isDescendantOf(aciDN)) {
                  int msgID = MSGID_ACI_SYNTAX_TARGET_DN_NOT_DESCENDENTOF;
                  String message = getMessage(msgID,
                                              urlDN.toNormalizedString(),
                                              aciDN.toNormalizedString());
                  throw new AciException(msgID, message);
              }
          }
        }
        catch (DirectoryException e){
            int msgID = MSGID_ACI_SYNTAX_INVALID_TARGETKEYWORD_EXPRESSION;
            String message = getMessage(msgID, target);
            throw new AciException(msgID, message);
        }
    }

    /**
     *  Decode an expression string representing a target keyword expression.
     * @param operator  An enumeration of the operation of this target.
     * @param expr A string representation of the target.
     * @param aciDN  The DN of the ACI entry used for a descendant check.
     * @return  A Target class representing this target.
     * @throws AciException  If the expression string is invalid.
     */
    public static Target decode(EnumTargetOperator operator,
                                String expr, DN aciDN)
            throws AciException {
        return new Target(operator, expr, aciDN);
    }

    /**
     * Returns the operator of this expression.
     * @return An enumeration of the operation value.
     */
    public EnumTargetOperator getOperator() {
        return operator;
    }

    /**
     * Returns the URL DN of the expression.
     * @return A DN of the URL.
     */
    public DN getDN() {
        return urlDN;
    }

    /**
     * Returns boolean if a pattern was seen during parsing.
     * @return  True if the DN is a wild-card.
     */
    public boolean isPattern() {
        return isPattern;
    }

    /*
     * TODO Evaluate re-writing this method.
     *
     * The matchesPattern() method really needs to be rewritten.  It's using a
     * very inefficient and very error-prone method to make the determination.
     * If you're really going to attempt pattern matching on a DN, then I'd
     * suggest trying a regular expression against the normalized DN rather
     * than a filter.
     */
    /**
     * This method tries to match a pattern against a DN. It builds an entry
     * with a target attribute containing the pattern and then matches against
     * it.
     * @param dn  The DN to try an match.
     * @return True if the pattern matches.
     */
    public boolean matchesPattern(DN dn) {
        boolean ret;
        String targetDN=dn.toNormalizedString();
        LinkedHashSet<AttributeValue> values =
            new LinkedHashSet<AttributeValue>();
        values.add(new AttributeValue(targetType, targetDN));
        Attribute attr = new Attribute(targetType, "target", values);
        Entry e = new Entry(DN.nullDN(), null, null, null);
        e.addAttribute(attr,new ArrayList<AttributeValue>());
        try {
            ret=filter.matchesEntry(e);
        } catch (DirectoryException ex) {
            //TODO information message?
            return false;
        }
        return  ret;
    }
}
