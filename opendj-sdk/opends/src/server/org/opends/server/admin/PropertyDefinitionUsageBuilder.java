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
package org.opends.server.admin;



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
      PropertyDefinitionVisitor<String, Void> {

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
    public String visitAttributeType(AttributeTypePropertyDefinition d,
        Void p) {
      return "OID";
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitBoolean(BooleanPropertyDefinition d, Void p) {
      return "BOOLEAN";
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitClass(ClassPropertyDefinition d, Void p) {
      if (isDetailed && !d.getInstanceOfInterface().isEmpty()) {
        return "CLASS <= " + d.getInstanceOfInterface().get(0);
      } else {
        return "CLASS";
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitDN(DNPropertyDefinition d, Void p) {
      if (isDetailed && d.getBaseDN() != null) {
        return "DN <= " + d.getBaseDN();
      } else {
        return "DN";
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitDuration(DurationPropertyDefinition d, Void p) {
      StringBuilder builder = new StringBuilder();
      DurationUnit unit = d.getBaseUnit();

      if (isDetailed && d.getLowerLimit() > 0) {
        builder.append(DurationUnit.toString(d.getLowerLimit()));
        builder.append(" <= ");
      }

      builder.append("DURATION(");
      builder.append(unit.getShortName());
      builder.append(')');

      if (isDetailed) {
        if (d.getUpperLimit() != null) {
          builder.append(" <= ");
          builder.append(DurationUnit.toString(d.getUpperLimit()));
        }

        if (d.isAllowUnlimited()) {
          builder.append(" | unlimited");
        }
      }

      return builder.toString();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> String visitEnum(EnumPropertyDefinition<E> d,
        Void p) {
      if (!isDetailed) {
        // Use the last word in the property name.
        String name = d.getName();
        int i = name.lastIndexOf('-');
        if (i == -1 || i == (name.length() - 1)) {
          return name.toUpperCase();
        } else {
          return name.substring(i + 1).toUpperCase();
        }
      } else {
        Set<String> values = new TreeSet<String>();

        for (Object value : EnumSet.allOf(d.getEnumClass())) {
          values.add(value.toString().trim().toLowerCase());
        }

        boolean isFirst = true;
        StringBuilder builder = new StringBuilder();
        for (String s : values) {
          if (!isFirst) {
            builder.append(" | ");
          }
          builder.append(s);
          isFirst = false;
        }

        return builder.toString();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitInteger(IntegerPropertyDefinition d, Void p) {
      StringBuilder builder = new StringBuilder();

      if (isDetailed) {
        builder.append(d.getLowerLimit());
        builder.append(" <= ");
      }

      builder.append("INTEGER");

      if (isDetailed) {
        if (d.getUpperLimit() != null) {
          builder.append(" <= ");
          builder.append(d.getUpperLimit());
        } else if (d.isAllowUnlimited()) {
          builder.append(" | unlimited");
        }
      }

      return builder.toString();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitIPAddress(IPAddressPropertyDefinition d, Void p) {
      return "HOST_NAME";
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitIPAddressMask(IPAddressMaskPropertyDefinition d,
        Void p) {
      return "IP_ADDRESS_MASK";
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitSize(SizePropertyDefinition d, Void p) {
      StringBuilder builder = new StringBuilder();

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

      return builder.toString();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitString(StringPropertyDefinition d, Void p) {
      if (d.getPattern() != null) {
        return d.getPatternUsage();
      } else {
        return "STRING";
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitUnknown(PropertyDefinition d, Void p)
        throws UnknownPropertyDefinitionException {
      return "?";
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
  public String getUsage(PropertyDefinition<?> pd) {
    return pd.accept(pimpl, null);
  };

}
