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
import static org.opends.server.authorization.dseecompat.AciMessages.*;
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

    private String aciString;

    /*
     * The DN of the entry containing this ACI.
     */
    private DN dn;

    /*
     * This regular expression is used to do a quick syntax check
     * when an ACI is being decoded.
     */
    private static final String aciRegex =
            "^\\s*" + AciTargets.targetsRegex + "\\s*"+
             AciBody.bodyRegx + "\\s*$";

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

    //MPD remove ConfigException after fixing David's problem
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
        return AciTargets.isTargetApplicable(aci, matchCtx) &&
                AciTargets.isTargetFilterApplicable(aci, matchCtx) &&
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
}
