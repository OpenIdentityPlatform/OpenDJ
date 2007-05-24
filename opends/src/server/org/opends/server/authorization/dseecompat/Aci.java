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

import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.AciMessages.*;
import java.util.regex.Pattern;

/**
 * The Aci class represents ACI strings.
 */
public class Aci  {

    /*
     * The body of the ACI is the version, name and permission-bind rule
     * pairs.
     */
    private AciBody body;

    /*
     * The ACI targets.
     */
    private AciTargets targets=null;

    /**
     * Version that we support.
     */
    public static final String supportedVersion="3.0";

    /*
     * String representation of the ACI used.
     */
    private String aciString;

    /*
     * The DN of the entry containing this ACI.
     */
    private DN dn;

    /**
     * Regular expression matching a word group.
     */
    public static final String WORD_GROUP="(\\w+)";

    /**
     * Regular expression matching a word group at the start of a
     * pattern.
     */
    public static final String WORD_GROUP_START_PATTERN = "^" + WORD_GROUP;

    /**
     * Regular expression matching a white space.
     */
    public static final String ZERO_OR_MORE_WHITESPACE="\\s*";

    /**
     * Regular expression matching a white space at the start of a pattern.
     */
    public static final String ZERO_OR_MORE_WHITESPACE_START_PATTERN =
                                             "^" + ZERO_OR_MORE_WHITESPACE ;

    /**
     * Regular expression matching a white space at the end of a pattern.
     */
    public static final String ZERO_OR_MORE_WHITESPACE_END_PATTERN =
                                             ZERO_OR_MORE_WHITESPACE  + "$";

    /**
     * Regular expression matching a ACL statement separator.
     */
    public static final String ACI_STATEMENT_SEPARATOR =
                ZERO_OR_MORE_WHITESPACE + ";" + ZERO_OR_MORE_WHITESPACE;

    /*
     * This regular expression is used to do a quick syntax check
     * when an ACI is being decoded.
     */
    private static final String aciRegex =
           ZERO_OR_MORE_WHITESPACE_START_PATTERN + AciTargets.targetsRegex +
           ZERO_OR_MORE_WHITESPACE + AciBody.bodyRegx +
           ZERO_OR_MORE_WHITESPACE_END_PATTERN;

    /**
     * Regular expression that graciously matches an attribute type name. Must
     * begin with an ASCII letter or digit, and contain only ASCII letters,
     * digit characters, hyphens, semi-colons and underscores.
     * They are case insensitive.
     */
    public  static final String ATTR_NAME =
              "((?i)[a-z\\d]{1}[[a-z]\\d-_.;]*(?-i))";

    /**
      * Regular expression matching a LDAP URL.
      */
     public  static final String LDAP_URL = ZERO_OR_MORE_WHITESPACE  +
                                                 "(ldap:///[^\\|]+)";

    /**
     *  String used to check for NULL ldap URL.
     */
     public static final String NULL_LDAP_URL = "ldap:///";

    /**
     * Regular expression used to match token that joins expressions (||).
     */
    public static final String LOGICAL_OR = "\\|\\|";

    /**
     * Regular expression used to match an open parenthesis.
     */
    public static final String OPEN_PAREN = "\\(";

    /**
     * Regular expression used to match a closed parenthesis.
     */
    public static final String CLOSED_PAREN = "\\)";

    /**
     * Regular expression used to match a single equal sign.
     */
    public static final String EQUAL_SIGN = "={1}";

    /**
     * Regular expression the matches "*".
     */
    public static final String ALL_ATTRS_WILD_CARD = ZERO_OR_MORE_WHITESPACE +
                                           "\\*" + ZERO_OR_MORE_WHITESPACE;

    /**
     * ACI_ADD is used to set the container rights for a LDAP add operation.
     */
    public static final int ACI_ADD = 0x0020;

    /**
     * ACI_DELETE is used to set the container rights for a LDAP
     * delete operation.
     */
    public static final int ACI_DELETE = 0x0010;

    /**
     * ACI_READ is used to set the container rights for a LDAP
     * search operation.
     */
    public static final int ACI_READ = 0x0004;

    /**
     * ACI_WRITE is used to set the container rights for a LDAP
     * modify operation.
     */
    public static final int ACI_WRITE = 0x0008;

    /**
     * ACI_COMPARE is used to set the container rights for a LDAP
     * compare operation.
     */
    public static final int ACI_COMPARE = 0x0001;

    /**
     * ACI_SEARCH is used to set the container rights a LDAP search operation.
     */
    public static final int ACI_SEARCH = 0x0002;

    /**
     * ACI_SELF is used for the SELFWRITE right.
     */
    public static final int ACI_SELF = 0x0040;

    /**
     * ACI_ALL is used to as a mask for all of the above. These
     * six below are not masked by the ACI_ALL.
     */
    public static final int ACI_ALL = 0x007F;

    /**
     * ACI_PROXY is used for the PROXY right.
     */
    public static final int ACI_PROXY = 0x0080;

    /**
     * ACI_IMPORT is used to set the container rights for a LDAP
     * modify dn operation.
     */
    public static final int ACI_IMPORT = 0x0100;

    /**
     * ACI_EXPORT is used to set the container rights for a LDAP
     * modify dn operation.
     */
    public static final int ACI_EXPORT = 0x0200;

    /**
     * ACI_WRITE_ADD is used by the LDAP modify operation.
     */
    public static final int ACI_WRITE_ADD = 0x800;

    /**
     * ACI_WRITE_DELETE is used by the LDAP modify operation.
     */
    public static final int ACI_WRITE_DELETE = 0x400;

    /**
     * ACI_SKIP_PROXY_CHECK is used to bypass the proxy access check.
     */
    public static final int ACI_SKIP_PROXY_CHECK = 0x4000;

    /**
     * TARGATTRFILTER_ADD is used to specify that a
     * targattrfilters ADD operation was seen in the ACI. For example,
     * given an ACI with:
     *
     * (targattrfilters="add=mail:(mail=*@example.com)")
     *
     * The TARGATTRFILTERS_ADD flag would be set during ACI parsing in the
     * TargAttrFilters class.
     */
    public static final int TARGATTRFILTERS_ADD = 0x1000;

    /**
     * TARGATTRFILTER_DELETE is used to specify that a
     * targattrfilters DELETE operation was seen in the ACI. For example,
     * given an ACI with:
     *
     * (targattrfilters="del=mail:(mail=*@example.com)")
     *
     * The TARGATTRFILTERS_DELETE flag would be set during ACI parsing in the
     * TargAttrFilters class.
     */
    public static final int TARGATTRFILTERS_DELETE = 0x2000;

    /**
     * ACI_ATTR_STAR_MATCHED is the flag set when the evaluation reason of a
     * AciHandler.maysend ACI_READ access evaluation was the result of an
     * ACI targetattr all attributes expression (targetattr="*") target match.
     * For this flag to be set, there must be only one ACI matching.
     *
     * This flag and ACI_FOUND_ATTR_RULE are used in the
     * AciHandler.filterEntry.accessAllowedAttrs method to skip access
     * evaluation if the flag is ACI_ATTR_STAR_MATCHED (all attributes match)
     * and the attribute type is not operational.
     */
    public static final int ACI_ATTR_STAR_MATCHED = 0x0008;

    /**
     * ACI_FOUND_ATTR_RULE is the flag set when the evaluation reason of a
     * AciHandler.maysend ACI_READ access evaluation was the result of an
     * ACI targetattr specific attribute expression
     * (targetattr="some attribute type") target match.
     */
    public static final int ACI_FOUND_ATTR_RULE = 0x0010;

    /**
     * ACI_NULL is used to set the container rights to all zeros. Used
     * by LDAP modify.
     */
    public static final int ACI_NULL = 0x0000;

    /**
     * Construct a new Aci from the provided arguments.
     * @param input The string representation of the ACI.
     * @param dn The DN of entry containing the ACI.
     * @param body The body of the ACI.
     * @param targets The targets of the ACI.
     */
    private  Aci(String input, DN dn, AciBody body, AciTargets targets) {
        this.aciString  = input;
        this.dn=dn;
        this.body=body;
        this.targets=targets;
    }

    /**
     * Decode an ACI byte string.
     * @param byteString The ByteString containing the ACI string.
     * @param dn DN of the ACI entry.
     * @return  Returns a decoded ACI representing the string argument.
     * @throws AciException If the parsing of the ACI string fails.
     */
    public static Aci decode (ByteString byteString, DN dn)
    throws AciException {
        String input=byteString.stringValue();
        //Perform a quick pattern check against the string to catch any
        //obvious syntax errors.
        if (!Pattern.matches(aciRegex, input)) {
            int msgID = MSGID_ACI_SYNTAX_GENERAL_PARSE_FAILED;
            String message = getMessage(msgID, input);
            throw new AciException(msgID, message);
        }
        //Decode the body first.
        AciBody body=AciBody.decode(input);
        //Create a substring from the start of the string to start of
        //the body. That should be the target.
        String targetStr = input.substring(0, body.getMatcherStartPos());
        //Decode that target string using the substring.
        AciTargets targets=AciTargets.decode(targetStr, dn);
        return new Aci(input, dn, body, targets);
    }

    /**
     * Return the string representation of the ACI. This was the string that
     * was used to create the Aci class.
     * @return A string representation of the ACI.
     */
    public String toString() {
        return aciString;
    }

    /**
     * Returns the targets of the ACI.
     * @return Any AciTargets of the ACI. There may be no targets
     * so this might be null.
     */
    public AciTargets getTargets() {
        return targets;
    }

    /**
     * Return the DN of the entry containing the ACI.
     * @return The DN of the entry containing the ACI.
     */
    public DN getDN() {
        return dn;
    }

    /**
     * Test if the given ACI is applicable using the target match information
     * provided. The ACI target can have four keywords at this time:
     *
     *       1. target - checked in isTargetApplicable.
     *       2. targetscope - checked in isTargetApplicable.
     *       3. targetfilter - checked in isTargetFilterApplicable.
     *       4. targetattr - checked in isTargetAttrApplicable.
     *
     * One and two are checked for match first. If they return true, then
     * three is checked. Lastly four is checked.
     *
     * @param aci The ACI to test.
     * @param matchCtx The target matching context containing all the info
     * needed to match ACI targets.
     * @return  True if this ACI targets are applicable or match.
     */
    public static boolean
    isApplicable(Aci aci, AciTargetMatchContext matchCtx) {
        int ctxRights=matchCtx.getRights();
       //First check if the ACI and context have similar rights.
        if(!aci.hasRights(ctxRights)) {
           //TODO This check might be able to be removed further testing
           //     is needed.
           if(!(aci.hasRights(ACI_SEARCH| ACI_READ) &&
                 matchCtx.hasRights(ACI_SEARCH | ACI_READ)))
              return false;
        }
        return AciTargets.isTargetApplicable(aci, matchCtx) &&
                AciTargets.isTargetFilterApplicable(aci, matchCtx) &&
                AciTargets.isTargAttrFiltersApplicable(aci, matchCtx) &&
                AciTargets.isTargetAttrApplicable(aci, matchCtx);
    }

    /**
     * Check if the body of the ACI matches the rights specified.
     * @param rights Bit mask representing the rights to match.
     * @return True if the body's rights match one of the rights specified.
     */
    public boolean hasRights(int rights) {
        return body.hasRights(rights);
    }

    /**
     * Re-direct has access type to the body's hasAccessType method.
     * @param accessType The access type to match.
     * @return  True if the body's hasAccessType determines a permission
     * contains this access type (allow or deny are valid types).
     */
    public boolean hasAccessType(EnumAccessType accessType) {
        return body.hasAccessType(accessType);
    }

    /**
     * Evaluate this ACI using the evaluation context provided. Re-direct
     * that calls the body's evaluate method.
     * @param evalCtx The evaluation context to evaluate with.
     * @return EnumEvalResult that contains the evaluation result of this
     * aci evaluation.
     */
    private EnumEvalResult evaluate(AciEvalContext evalCtx) {
        return body.evaluate(evalCtx);
    }

    /**
     * Static class used to evaluate an ACI and evaluation context.
     * @param evalCtx  The context to evaluate with.
     * @param aci The ACI to evaluate.
     * @return EnumEvalResult that contains the evaluation result of the aci
     * evaluation.
     */
    public static EnumEvalResult evaluate(AciEvalContext evalCtx, Aci aci) {
        return aci.evaluate(evalCtx);
    }

  /**
   * Returns the name string of this ACI.
   * @return The name string.
   */
    public String getName() {
      return this.body.getName();
    }
}
