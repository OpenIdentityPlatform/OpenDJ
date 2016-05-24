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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;

import java.text.NumberFormat;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.BooleanPropertyDefinition;
import org.forgerock.opendj.config.DurationPropertyDefinition;
import org.forgerock.opendj.config.DurationUnit;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyValueVisitor;
import org.forgerock.opendj.config.SizePropertyDefinition;
import org.forgerock.opendj.config.SizeUnit;

/**
 * A class responsible for displaying property values. This class takes care of using locale specific formatting rules.
 */
final class PropertyValuePrinter {

    /** Perform property type specific print formatting. */
    private static final class MyPropertyValueVisitor extends PropertyValueVisitor<LocalizableMessage, Void> {

        /** The requested size unit (null if the property's unit should be used). */
        private final SizeUnit sizeUnit;
        /** The requested time unit (null if the property's unit should be used). */
        private final DurationUnit timeUnit;
        /** Whether values should be displayed in a script-friendly manner. */
        private final boolean isScriptFriendly;
        /** The formatter to use for numeric values. */
        private final NumberFormat numberFormat;

        /** Private constructor. */
        private MyPropertyValueVisitor(SizeUnit sizeUnit, DurationUnit timeUnit, boolean isScriptFriendly) {
            this.sizeUnit = sizeUnit;
            this.timeUnit = timeUnit;
            this.isScriptFriendly = isScriptFriendly;

            numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setGroupingUsed(!this.isScriptFriendly);
            numberFormat.setMaximumFractionDigits(2);
        }

        @Override
        public LocalizableMessage visitBoolean(BooleanPropertyDefinition pd, Boolean v, Void p) {
            return v ? INFO_VALUE_TRUE.get() : INFO_VALUE_FALSE.get();
        }

        @Override
        public LocalizableMessage visitDuration(DurationPropertyDefinition pd, Long v, Void p) {
            if (pd.getUpperLimit() == null && (v < 0 || v == Long.MAX_VALUE)) {
                return INFO_VALUE_UNLIMITED.get();
            }

            LocalizableMessageBuilder builder = new LocalizableMessageBuilder();
            long ms = pd.getBaseUnit().toMilliSeconds(v);

            if (timeUnit == null && !isScriptFriendly && ms != 0) {
                // Use human-readable string representation by default.
                builder.append(DurationUnit.toString(ms));
            } else {
                // Use either the specified unit or the property definition's
                // base unit.
                DurationUnit unit = timeUnit;
                if (unit == null) {
                    unit = pd.getBaseUnit();
                }

                builder.append(numberFormat.format(unit.fromMilliSeconds(ms)));
                builder.append(' ');
                builder.append(unit.getShortName());
            }

            return builder.toMessage();
        }

        @Override
        public LocalizableMessage visitSize(SizePropertyDefinition pd, Long v, Void p) {
            if (pd.isAllowUnlimited() && v < 0) {
                return INFO_VALUE_UNLIMITED.get();
            }

            SizeUnit unit = sizeUnit;
            if (unit == null) {
                if (isScriptFriendly) {
                    // Assume users want a more accurate value.
                    unit = SizeUnit.getBestFitUnitExact(v);
                } else {
                    unit = SizeUnit.getBestFitUnit(v);
                }
            }

            LocalizableMessageBuilder builder = new LocalizableMessageBuilder();
            builder.append(numberFormat.format(unit.fromBytes(v)));
            builder.append(' ');
            builder.append(unit.getShortName());

            return builder.toMessage();
        }

        @Override
        public <T> LocalizableMessage visitUnknown(PropertyDefinition<T> pd, T v, Void p) {
            // For all other property definition types the default encoding
            // will do.
            String s = pd.encodeValue(v);
            if (isScriptFriendly) {
                return LocalizableMessage.raw("%s", s);
            } else if (s.trim().length() == 0 || s.contains(",")) {
                // Quote empty strings or strings containing commas
                // non-scripting mode.
                return LocalizableMessage.raw("\"%s\"", s);
            } else {
                return LocalizableMessage.raw("%s", s);
            }
        }

    }

    /** The property value printer implementation. */
    private final MyPropertyValueVisitor pimpl;

    /**
     * Creates a new property value printer.
     *
     * @param sizeUnit
     *            The user requested size unit or <code>null</code> if best-fit.
     * @param timeUnit
     *            The user requested time unit or <code>null</code> if best-fit.
     * @param isScriptFriendly
     *            If values should be displayed in a script friendly manner.
     */
    public PropertyValuePrinter(SizeUnit sizeUnit, DurationUnit timeUnit, boolean isScriptFriendly) {
        this.pimpl = new MyPropertyValueVisitor(sizeUnit, timeUnit, isScriptFriendly);
    }

    /**
     * Print a property value according to the rules of this property value printer.
     *
     * @param <T>
     *            The type of property value.
     * @param pd
     *            The property definition.
     * @param value
     *            The property value.
     * @return Returns the string representation of the property value encoded according to the rules of this property
     *         value printer.
     */
    public <T> LocalizableMessage print(PropertyDefinition<T> pd, T value) {
        return pd.accept(pimpl, value, null);
    }
}
