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
package org.opends.server.tools.dsconfig;



import java.text.NumberFormat;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.messages.DSConfigMessages.*;

import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.DurationPropertyDefinition;
import org.opends.server.admin.DurationUnit;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyValueVisitor;
import org.opends.server.admin.SizePropertyDefinition;
import org.opends.server.admin.SizeUnit;



/**
 * A class responsible for displaying property values. This class
 * takes care of using locale specific formatting rules.
 */
final class PropertyValuePrinter {

  /**
   * Perform property type specific print formatting.
   */
  private static class MyPropertyValueVisitor extends
      PropertyValueVisitor<Message, Void> {

    // The requested size unit (null if the property's unit should be
    // used).
    private final SizeUnit sizeUnit;

    // The requested time unit (null if the property's unit should be
    // used).
    private final DurationUnit timeUnit;

    // Whether or not values should be displayed in a script-friendly
    // manner.
    private final boolean isScriptFriendly;

    // The formatter to use for numeric values.
    private final NumberFormat numberFormat;



    // Private constructor.
    private MyPropertyValueVisitor(SizeUnit sizeUnit, DurationUnit timeUnit,
        boolean isScriptFriendly) {
      this.sizeUnit = sizeUnit;
      this.timeUnit = timeUnit;
      this.isScriptFriendly = isScriptFriendly;

      this.numberFormat = NumberFormat.getNumberInstance();
      if (this.isScriptFriendly) {
        numberFormat.setGroupingUsed(false);
        numberFormat.setMaximumFractionDigits(2);
      } else {
        numberFormat.setGroupingUsed(true);
        numberFormat.setMaximumFractionDigits(2);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitBoolean(BooleanPropertyDefinition d, Boolean v,
        Void p) {
      if (v == false) {
        return INFO_VALUE_FALSE.get();
      } else {
        return INFO_VALUE_TRUE.get();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitDuration(DurationPropertyDefinition d, Long v, Void p) {
      if (d.getUpperLimit() == null && (v < 0 || v == Long.MAX_VALUE)) {
        return INFO_VALUE_UNLIMITED.get();
      }

      MessageBuilder builder = new MessageBuilder();
      long ms = d.getBaseUnit().toMilliSeconds(v);

      if (timeUnit == null && !isScriptFriendly && ms != 0) {
        // Use human-readable string representation by default.
        builder.append(DurationUnit.toString(ms));
      } else {
        // Use either the specified unit or the property definition's
        // base unit.
        DurationUnit unit = timeUnit;
        if (unit == null) {
          unit = d.getBaseUnit();
        }

        builder.append(numberFormat.format(unit.fromMilliSeconds(ms)));
        builder.append(' ');
        builder.append(unit.getShortName());
      }

      return builder.toMessage();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitSize(SizePropertyDefinition d, Long v, Void p) {
      if (d.isAllowUnlimited() && v < 0) {
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

      MessageBuilder builder = new MessageBuilder();
      builder.append(numberFormat.format(unit.fromBytes(v)));
      builder.append(' ');
      builder.append(unit.getShortName());

      return builder.toMessage();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Message visitUnknown(PropertyDefinition<T> d, T v, Void p) {
      // For all other property definition types the default encoding
      // will do.
      String s = d.encodeValue(v);
      if (isScriptFriendly) {
        return Message.raw("%s", s);
      } else if (s.trim().length() == 0 || s.contains(",")) {
        // Quote empty strings or strings containing commas
        // non-scripting mode.
        return Message.raw("\"%s\"", s);
      } else {
        return Message.raw("%s", s);
      }
    }

  }

  // The property value printer implementation.
  private final MyPropertyValueVisitor pimpl;



  /**
   * Creates a new property value printer.
   *
   * @param sizeUnit
   *          The user requested size unit or <code>null</code> if
   *          best-fit.
   * @param timeUnit
   *          The user requested time unit or <code>null</code> if
   *          best-fit.
   * @param isScriptFriendly
   *          If values should be displayed in a script friendly
   *          manner.
   */
  public PropertyValuePrinter(SizeUnit sizeUnit, DurationUnit timeUnit,
      boolean isScriptFriendly) {
    this.pimpl = new MyPropertyValueVisitor(sizeUnit, timeUnit,
        isScriptFriendly);
  }



  /**
   * Print a property value according to the rules of this property
   * value printer.
   *
   * @param <T>
   *          The type of property value.
   * @param pd
   *          The property definition.
   * @param value
   *          The property value.
   * @return Returns the string representation of the property value
   *         encoded according to the rules of this property value
   *         printer.
   */
  public <T> Message print(PropertyDefinition<T> pd, T value) {
    return pd.accept(pimpl, value, null);
  }
}
