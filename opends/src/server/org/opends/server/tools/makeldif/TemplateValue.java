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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import org.opends.server.types.AttributeType;



/**
 * This class defines a value generated from a template line.
 */
public class TemplateValue
{
  // The generated template value.
  private StringBuilder templateValue;

  // The template line used to generate this value.
  private TemplateLine templateLine;



  /**
   * Creates a new template value with the provided information.
   *
   * @param  templateLine  The template line used to generate this value.
   */
  public TemplateValue(TemplateLine templateLine)
  {
    this.templateLine = templateLine;

    templateValue = new StringBuilder();
  }



  /**
   * Retrieves the template line used to generate this value.
   *
   * @return  The template line used to generate this value.
   */
  public TemplateLine getTemplateLine()
  {
    return templateLine;
  }



  /**
   * Retrieves the attribute type for this template value.
   *
   * @return  The attribute type for this template value.
   */
  public AttributeType getAttributeType()
  {
    return templateLine.getAttributeType();
  }



  /**
   * Retrieves the generated value.
   *
   * @return  The generated value.
   */
  public StringBuilder getValue()
  {
    return templateValue;
  }



  /**
   * Appends the provided string to this template value.
   *
   * @param  s  The string to append to the template value.
   */
  public void append(String s)
  {
    templateValue.append(s);
  }
}

