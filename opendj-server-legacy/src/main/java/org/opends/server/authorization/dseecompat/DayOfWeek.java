/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class implements the dayofweek bind rule keyword.
 */
public class DayOfWeek  implements KeywordBindRule {

    /** List containing the enumeration of the day of the week. */
    private List<EnumDayOfWeek> days;

    /** Enumeration representing the bind rule operation type. */
    private EnumBindRuleType type;

    /**
     * Create a class representing a dayofweek bind rule keyword.
     * @param days  A list of day of the week enumerations.
     * @param type An enumeration representing the bind rule type.
     */
    private DayOfWeek(List<EnumDayOfWeek> days, EnumBindRuleType type) {
        this.days=days;
        this.type=type;
    }

    /**
     * Decode an string representing a dayofweek bind rule.
     * @param expr A string representation of the bind rule.
     * @param type  An enumeration representing the bind rule type.
     * @return  A keyword bind rule class that can be used to evaluate
     * this bind rule.
     * @throws AciException  If the expression string is invalid.
     */
    public static KeywordBindRule decode(String expr, EnumBindRuleType type)
    throws AciException
    {
        List<EnumDayOfWeek> days = new LinkedList<>();
        String[] dayArray=expr.split(",", -1);
        for (String element : dayArray)
        {
          EnumDayOfWeek day=EnumDayOfWeek.createDayOfWeek(element);
          if (day == null)
          {
              LocalizableMessage message = WARN_ACI_SYNTAX_INVALID_DAYOFWEEK.get(expr);
              throw new AciException(message);
          }
          days.add(day);
        }
        return new DayOfWeek(days, type);
    }

    /**
     * Performs evaluation of a dayofweek bind rule using the provided
     * evaluation context.
     * @param evalCtx  An evaluation context to use in the evaluation.
     * @return An enumeration evaluation result.
     */
    @Override
    public EnumEvalResult evaluate(AciEvalContext evalCtx) {
        EnumEvalResult matched=EnumEvalResult.FALSE;
        GregorianCalendar calendar = new GregorianCalendar();
        EnumDayOfWeek dayofweek
            = EnumDayOfWeek.getDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK));
        if(days.contains(dayofweek))
            matched=EnumEvalResult.TRUE;
        return matched.getRet(type, false);
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder();
      toString(sb);
      return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public final void toString(StringBuilder buffer)
    {
      buffer.append(super.toString());
    }

}
