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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents the body of an ACI. The body of the ACI is the
 * version, name, and permission-bind rule pairs.
 */
public class AciBody {

    private static final int VERSION = 1;
    private static final int NAME = 2;
    private static final int PERM = 1;
    private static final int RIGHTS = 2;
    private static final int BINDRULE = 3;
    private int startPos=0;
    /*
     * The name of the ACI, currently not used but parsed.
     */
    private String name = null;
    /*
     * The version of the ACi, current not used but parsed and checked
     * for 3.0.
     */
    private String version = null;
    /*
     This structure represents a permission-bind rule pairs. There can be
     several of these.
    */
    private List<PermBindRulePair> permBindRulePairs;
    /*
     * TODO Define constants for these regular expressions to make them more
     * readable.
     * The regular expressions would probably be a lot easier
     * to understand if you defined a number of constants for the
     * individual components and then concatenated them.  For example,
     * "\\s*" could be defined in a constant named ZERO_OR_MORE_SPACES.
     * This would also help make it easier to understand which parentheses
     * were part of the regex and which were part of the ACI syntax.
     */
    private static final String permissionRegex = "(\\w+)\\s*\\(([^()]+)\\)";
    private static final String bindRuleRegex = "(.+?\"[)]*)\\s*;";
    private static final String actionRegex =
            "\\s*" + permissionRegex + "\\s*" + bindRuleRegex;
    private static final String versionRegex = "(\\d\\.\\d)";
    private static final String versionToken = "(?i)version";
    private static final String aclToken = "(?i)acl";
    /**
     * Regular expression used to parse the body of an ACI.
     */
    public static final String bodyRegx =
        "\\(\\s*" + versionToken + "\\s*"
        + versionRegex + "\\s*;\\s*" + aclToken + "\\s*\"(.*)\"\\s*;\\s*"
        + actionRegex + "\\s*\\)";
    /**
     * Regular expression used to parse the header of the ACI body. The
     * header is version and acl name.
     */
    public static final String header =
        "\\(\\s*" + versionToken + "\\s*"
        + versionRegex + "\\s*;\\s*" + aclToken + "\\s*\"(.*?)\"\\s*;";

    /**
     * Construct an ACI body from the specified version, name and
     * permission-bind rule pairs.
     *
     * @param verision The version of the ACI.
     * @param name The name of the ACI.
     * @param startPos The start position in the string of the ACI body.
     * @param permBindRulePairs The set of fully parsed permission-bind rule
     * pairs pertaining to this ACI.
     */
    private AciBody(String verision, String name, int startPos,
            List<PermBindRulePair> permBindRulePairs) {
        this.version=verision;
        this.name=name;
        this.startPos=startPos;
        this.permBindRulePairs=permBindRulePairs;
    }

    /**
     * Decode an ACI string representing the ACI body.
     *
     * @param input String representation of the ACI body.
     * @return An AciBody class representing the decoded ACI body string.
     * @throws AciException If the provided string contains errors.
     */
    public static AciBody decode(String input)
    throws AciException {
        String version=null, name=null;
        int startPos=0;
        List<PermBindRulePair> permBindRulePairs=
                new ArrayList<PermBindRulePair>();
        Pattern bodyPattern = Pattern.compile(header);
        Matcher bodyMatcher = bodyPattern.matcher(input);
        if(bodyMatcher.find()) {
            startPos=bodyMatcher.start();
            version  = bodyMatcher.group(VERSION);
            if (!version.equalsIgnoreCase(Aci.supportedVersion)) {
                int msgID = MSGID_ACI_SYNTAX_INVAILD_VERSION;
                String message = getMessage(msgID, version);
                throw new AciException(msgID, message);
            }
            name = bodyMatcher.group(NAME);
        }
        Pattern bodyPattern1 = Pattern.compile(actionRegex);
        Matcher bodyMatcher1 = bodyPattern1.matcher(input);
        /*
         * The may be many permission-bind rule pairs.
         */
        while(bodyMatcher1.find()) {
         String perm=bodyMatcher1.group(PERM);
         String rights=bodyMatcher1.group(RIGHTS);
         String bRule=bodyMatcher1.group(BINDRULE);
         PermBindRulePair pair = PermBindRulePair.decode(perm, rights, bRule);
         permBindRulePairs.add(pair);
        }
        return new AciBody(version, name, startPos, permBindRulePairs);
    }

    /**
     * Checks all of the permissions in this body for a specific access type.
     * Need to walk down each permission-bind rule pair and call it's
     * hasAccessType method.
     *
     * @param accessType The access type enumeration to search for.
     * @return True if the access type is found in a permission of
     * a permission bind rule pair.
     */
    public boolean hasAccessType(EnumAccessType accessType) {
        List<PermBindRulePair>pairs=getPermBindRulePairs();
         for(PermBindRulePair p : pairs) {
             if(p.hasAccessType(accessType))
                 return true;
         }
         return false;
    }

    /**
     * Search through each permission bind rule associated with this body and
     * try and match a single right of the specified rights.
     *
     * @param rights The rights that are used in the match.
     * @return True if a one or more right of the specified rights matches
     * a body's permission rights.
     */
    public boolean hasRights(int rights) {
        List<PermBindRulePair>pairs=getPermBindRulePairs();
        for(PermBindRulePair p : pairs) {
            if(p.hasRights(rights))
                return true;
        }
        return false;
    }

    /**
     * Retrieve the permission-bind rule pairs of this ACI body.
     *
     * @return The permission-bind rule pairs.
     */
    private List<PermBindRulePair> getPermBindRulePairs() {
        return permBindRulePairs;
    }

    /**
     * Get the start position in the ACI string of the ACI body.
     *
     * @return Index into the ACI string of the ACI body.
     */
    public int getMatcherStartPos() {
        return startPos;
    }

    //TODO Evaluate adding support for the "absolute" deny access
    //     type precedence operator.

    /**
     * Performs an evaluation of the permission-bind rule pairs
     * using the evaluation context. The method walks down
     * each PermBindRulePair object and:
     *
     *  1. Skips a pair if the evaluation context rights don't
     *     apply to that ACI. For example, an LDAP search would skip
     *     an ACI pair that allows writes.
     *
     *  2. The pair's bind rule is evaluated using the evaluation context.
     *  3. The result of the evaluation is itself evaluated. See comments
     *     below in the code.
     *
     * @param evalCtx The evaluation context to evaluate against.
     * @return An enumeration result of the evaluation.
     */
    public  EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult res=EnumEvalResult.FALSE;
        List<PermBindRulePair>pairs=getPermBindRulePairs();
        for(PermBindRulePair p : pairs) {
            if(!p.hasRights(evalCtx.getRights()))
                continue;
           res=p.getBindRule().evaluate(evalCtx);
           // The evaluation result could be FAIL. Stop processing and return
           //FAIL. Maybe an internal search failed.
           if((res != EnumEvalResult.TRUE) &&
              (res != EnumEvalResult.FALSE)) {
               res=EnumEvalResult.FAIL;
               break;
           //If the access type is DENY and the pair evaluated to TRUE,
           //then stop processing and return TRUE. A deny pair
           //succeeded.
           } else if((p.hasAccessType(EnumAccessType.DENY)) &&
                     (res == EnumEvalResult.TRUE)) {
               res=EnumEvalResult.TRUE;
               break;
           //An allow access type evaluated TRUE, stop processing
           //and return TRUE.
           } else if((p.hasAccessType(EnumAccessType.ALLOW) &&
                     (res == EnumEvalResult.TRUE))) {
               res=EnumEvalResult.TRUE;
               break;
           }
        }
        return res;
    }
}
