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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;

/** This class implements the dayofweek bind rule keyword. */
public class DayOfWeek  implements KeywordBindRule {
    /** List containing the enumeration of the day of the week. */
    private final List<EnumDayOfWeek> days;
    /** Enumeration representing the bind rule operation type. */
    private final EnumBindRuleType type;

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
        {
          matched=EnumEvalResult.TRUE;
        }
        return matched.getRet(type, false);
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
