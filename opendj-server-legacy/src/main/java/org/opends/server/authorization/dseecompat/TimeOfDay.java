/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;

import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.util.TimeThread;

/** This class represents the timeofday keyword in a bind rule. */
public class TimeOfDay implements KeywordBindRule {
    /** Regular expression matching a valid timeofday rule value (0-2359). */
    private static final Pattern timeofdayRegex = Pattern.compile("[0-2]\\d[0-5]\\d");

    /** Enumeration representing the bind rule operation type. */
    private final EnumBindRuleType type;
    /** Holds the time value parsed from the ACI. */
    private final int timeRef;

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
        int valueAsInt = 0;
        if (!timeofdayRegex.matcher(expr).matches())
        {
            LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_TIMEOFDAY.get(expr);
            throw new AciException(message);
         }
        try {
            valueAsInt = Integer.parseInt(expr);
        } catch (NumberFormatException nfe) {
          LocalizableMessage message =
           WARN_ACI_SYNTAX_INVALID_TIMEOFDAY_FORMAT.get(expr, nfe.getMessage());
            throw new AciException(message);
        }
        if (valueAsInt < 0 || valueAsInt > 2359)
        {
            LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_TIMEOFDAY_RANGE.get(expr);
            throw new AciException(message);
        }

        return new TimeOfDay(valueAsInt, type);
    }

    /**
     * Evaluates the timeofday bind rule using the evaluation context
     * passed into the method.
     * @param evalCtx  The evaluation context to use for the evaluation.
     * @return  An enumeration result representing the result of the evaluation.
     */
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched = evaluate() ? EnumEvalResult.TRUE : EnumEvalResult.FALSE;
        return matched.getRet(type, false);
    }

    private boolean evaluate() {
        int currentTime=TimeThread.getHourAndMinute();
        switch (type) {
        case EQUAL_BINDRULE_TYPE:
        case NOT_EQUAL_BINDRULE_TYPE:
            return currentTime != timeRef;
        case LESS_OR_EQUAL_BINDRULE_TYPE:
            return currentTime <= timeRef;
        case LESS_BINDRULE_TYPE:
            return currentTime < timeRef;
        case GREATER_OR_EQUAL_BINDRULE_TYPE:
            return currentTime >= timeRef;
        case GREATER_BINDRULE_TYPE:
            return currentTime > timeRef;
        default:
            return false;
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    @Override
    public final void toString(StringBuilder buffer)
    {
        buffer.append(super.toString());
    }
}
