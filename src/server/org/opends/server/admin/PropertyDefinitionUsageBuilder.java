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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.admin;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import java.text.NumberFormat;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;



/**
 * A property definition visitor which can be used to generate syntax
 * usage information.
 */
public final class PropertyDefinitionUsageBuilder {

  /**
   * Underlying implementation.
   */
  private class MyPropertyDefinitionVisitor extends
      PropertyDefinitionVisitor<Message, Void> {

    // Flag indicating whether detailed syntax information will be
    // generated.
    private final boolean isDetailed;

    // The formatter to use for numeric values.
    private final NumberFormat numberFormat;



    // Private constructor.
    private MyPropertyDefinitionVisitor(boolean isDetailed) {
      this.isDetailed = isDetailed;

      this.numberFormat = NumberFormat.getNumberInstance();
      this.numberFormat.setGroupingUsed(true);
      this.numberFormat.setMaximumFractionDigits(2);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends ConfigurationClient, S extends Configuration>
    Message visitAggregation(AggregationPropertyDefinition<C, S> d, Void p) {
      return Message.raw("NAME");
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitAttributeType(AttributeTypePropertyDefinition d,
        Void p) {
      return Message.raw("OID");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitACI(ACIPropertyDefinition d,
        Void p) {
      return Message.raw("ACI");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitBoolean(BooleanPropertyDefinition d, Void p) {
      if (isDetailed) {
        return Message.raw("false | true");
      } else {
        return Message.raw("BOOLEAN");
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitClass(ClassPropertyDefinition d, Void p) {
      if (isDetailed && !d.getInstanceOfInterface().isEmpty()) {
        return Message.raw("CLASS <= " + d.getInstanceOfInterface().get(0));
      } else {
        return Message.raw("CLASS");
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitDN(DNPropertyDefinition d, Void p) {
      if (isDetailed && d.getBaseDN() != null) {
        return Message.raw("DN <= " + d.getBaseDN());
      } else {
        return Message.raw("DN");
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitDuration(DurationPropertyDefinition d, Void p) {
      MessageBuilder builder = new MessageBuilder();
      DurationUnit unit = d.getBaseUnit();

      if (isDetailed && d.getLowerLimit() > 0) {
        builder.append(DurationUnit.toString(d.getLowerLimit()));
        builder.append(" <= ");
      }

      builder.append("DURATION (");
      builder.append(unit.getShortName());
      builder.append(")");

      if (isDetailed) {
        if (d.getUpperLimit() != null) {
          builder.append(" <= ");
          builder.append(DurationUnit.toString(d.getUpperLimit()));
        }

        if (d.isAllowUnlimited()) {
          builder.append(" | unlimited");
        }
      }

      return builder.toMessage();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Message visitEnum(EnumPropertyDefinition<E> d,
        Void p) {
      if (!isDetailed) {
        // Use the last word in the property name.
        String name = d.getName();
        int i = name.lastIndexOf('-');
        if (i == -1 || i == (name.length() - 1)) {
          return Message.raw(name.toUpperCase());
        } else {
          return Message.raw(name.substring(i + 1).toUpperCase());
        }
      } else {
        Set<String> values = new TreeSet<String>();

        for (Object value : EnumSet.allOf(d.getEnumClass())) {
          values.add(value.toString().trim().toLowerCase());
        }

        boolean isFirst = true;
        MessageBuilder builder = new MessageBuilder();
        for (String s : values) {
          if (!isFirst) {
            builder.append(" | ");
          }
          builder.append(s);
          isFirst = false;
        }

        return builder.toMessage();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitInteger(IntegerPropertyDefinition d, Void p) {
      MessageBuilder builder = new MessageBuilder();

      if (isDetailed) {
        builder.append(String.valueOf(d.getLowerLimit()));
        builder.append(" <= ");
      }

      builder.append("INTEGER");

      if (isDetailed) {
        if (d.getUpperLimit() != null) {
          builder.append(" <= ");
          builder.append(String.valueOf(d.getUpperLimit()));
        } else if (d.isAllowUnlimited()) {
          builder.append(" | unlimited");
        }
      }

      return builder.toMessage();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitIPAddress(IPAddressPropertyDefinition d, Void p) {
      return Message.raw("HOST_NAME");
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitIPAddressMask(IPAddressMaskPropertyDefinition d,
        Void p) {
      return Message.raw("IP_ADDRESS_MASK");
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitSize(SizePropertyDefinition d, Void p) {
      MessageBuilder builder = new MessageBuilder();

      if (isDetailed && d.getLowerLimit() > 0) {
        SizeUnit unit = SizeUnit.getBestFitUnitExact(d.getLowerLimit());
        builder.append(numberFormat.format(unit.fromBytes(d.getLowerLimit())));
        builder.append(' ');
        builder.append(unit.getShortName());
        builder.append(" <= ");
      }

      builder.append("SIZE");

      if (isDetailed) {
        if (d.getUpperLimit() != null) {
          long upperLimit = d.getUpperLimit();
          SizeUnit unit = SizeUnit.getBestFitUnitExact(upperLimit);

          // Quite often an upper limit is some power of 2 minus 1. In those
          // cases lets use a "less than" relation rather than a "less than
          // or equal to" relation. This will result in a much more readable
          // quantity.
          if (unit == SizeUnit.BYTES && upperLimit < Long.MAX_VALUE) {
            unit = SizeUnit.getBestFitUnitExact(upperLimit + 1);
            if (unit != SizeUnit.BYTES) {
              upperLimit += 1;
              builder.append(" < ");
            } else {
              builder.append(" <= ");
            }
          } else {
            builder.append(" <= ");
          }

          builder.append(numberFormat.format(unit.fromBytes(upperLimit)));
          builder.append(' ');
          builder.append(unit.getShortName());
        }

        if (d.isAllowUnlimited()) {
          builder.append(" | unlimited");
        }
      }

      return builder.toMessage();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Message visitString(StringPropertyDefinition d, Void p) {
      if (d.getPattern() != null) {
        if (isDetailed) {
          MessageBuilder builder = new MessageBuilder();
          builder.append(d.getPatternUsage());
          builder.append(" - ");
          builder.append(d.getPatternSynopsis());
          return builder.toMessage();
        } else {
          return Message.raw(d.getPatternUsage());
        }
      } else {
        return Message.raw("STRING");
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Message visitUnknown(PropertyDefinition<T> d, Void p)
        throws UnknownPropertyDefinitionException {
      return Message.raw("?");
    }
  }

  // Underlying implementation.
  private final MyPropertyDefinitionVisitor pimpl;



  /**
   * Creates a new property usage builder.
   *
   * @param isDetailed
   *          Indicates whether or not the generated usage should
   *          contain detailed information such as constraints.
   */
  public PropertyDefinitionUsageBuilder(boolean isDetailed) {
    this.pimpl = new MyPropertyDefinitionVisitor(isDetailed);
  }



  /**
   * Generates the usage information for the provided property
   * definition.
   *
   * @param pd
   *          The property definitions.
   * @return Returns the usage information for the provided property
   *         definition.
   */
  public Message getUsage(PropertyDefinition<?> pd) {
    return pd.accept(pimpl, null);
  };

}
