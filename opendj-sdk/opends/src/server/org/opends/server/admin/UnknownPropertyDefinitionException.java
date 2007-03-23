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



/**
 * Indicates that an unknown type of property definition was encountered. This
 * can occur as the management prototype develops and new kinds of property
 * definitions are added.
 */
public final class UnknownPropertyDefinitionException
    extends PropertyException {

  // Generated serialization ID.
  private static final long serialVersionUID = 7042646409131322385L;

  // The visitor parameter if there was one.
  private Object parameter;



  /**
   * Creates a new unknown property definition exception.
   *
   * @param d
   *          The unknown property definition.
   * @param p
   *          The visitor parameter if there was one.
   */
  public UnknownPropertyDefinitionException(PropertyDefinition d, Object p) {
    super(d);
    this.parameter = p;
  }



  /**
   * Get the visitor parameter if there was one.
   *
   * @return Returns the visitor parameter if there was one.
   */
  public Object getParameter() {
    return parameter;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getMessage() {
    return "Unhandled property definition type encountered \""
        + getPropertyDefinition().getClass().getName() + "\"";
  }
}
