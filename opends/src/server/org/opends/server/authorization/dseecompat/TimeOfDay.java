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
import org.opends.server.util.TimeThread;
import java.util.regex.Pattern;

/**
 * This class represents the timeofday keyword in a bind rule.
 */
public class TimeOfDay implements KeywordBindRule {

    /*
     * Regular expression matching a valid timeofday rule value (0-2359).
     */
    private static final String timeofdayRegex = "[0-2]\\d[0-5]\\d";

    /*
     *  Enumeration representing the bind rule operation type.
     */
    private EnumBindRuleType type=null;

    /*
     * Holds the time value parsed from the ACI.
     */
    private int timeRef;

    /**
     * Constructor to create a timeofday keyword class.
     * @param timeVal The time value to check for (0-2359).
     * @param type An enumeration of the type of the expression.
     */
    private TimeOfDay(int timeVal, EnumBindRuleType type) {
        this.timeRef=timeVal;
        this.type=type;
    }

    /**
     * Decodes a string representation of a timeofday bind rule expression.
     * @param expr A string representation of the expression.
     * @param type An enumeration of the type of the expression.
     * @return  A TimeOfDay class representing the expression.
     * @throws AciException If the expression is invalid.
     */
    public static TimeOfDay decode(String expr,  EnumBindRuleType type)
    throws AciException  {
        if (!Pattern.matches(timeofdayRegex, expr))
        {
            int msgID = MSGID_ACI_SYNTAX_INVALID_TIMEOFDAY;
            String message = getMessage(msgID,expr);
            throw new AciException(msgID, message);
         }
        int valueAsInt = Integer.parseInt(expr);
        if ((valueAsInt < 0) || (valueAsInt > 2359))
        {
            int msgID = MSGID_ACI_SYNTAX_INVALID_TIMEOFDAY_RANGE;
            String message = getMessage(msgID,expr);
            throw new AciException(msgID, message);
        }

        return new TimeOfDay(valueAsInt, type);
    }

    /**
     * Evaluates the timeofday bind rule using the evaluation context
     * passed into the method.
     * @param evalCtx  The evaluation context to use for the evaluation.
     * @return  An enumeration result representing the result of the
     * evaluation.
     */
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched=EnumEvalResult.FALSE;

        int currentTime=TimeThread.getHourAndMinute();
        //check the type
        switch (type) {
        case LESS_OR_EQUAL_BINDRULE_TYPE:
            if (currentTime <= timeRef)
            {
                matched=EnumEvalResult.TRUE;
            }
            break;

        case LESS_BINDRULE_TYPE:
            if (currentTime < timeRef)
            {
                matched=EnumEvalResult.TRUE;
            }
            break;

        case GREATER_OR_EQUAL_BINDRULE_TYPE:
            if (currentTime >= timeRef)
            {
                matched=EnumEvalResult.TRUE;
            }
            break;

        case GREATER_BINDRULE_TYPE:
            if (currentTime > timeRef)
            {
                matched=EnumEvalResult.TRUE;
            }
        }
        return matched.getRet(type, false);
    }
}
