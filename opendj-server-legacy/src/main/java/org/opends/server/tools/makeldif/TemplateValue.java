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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;



import org.forgerock.opendj.ldap.schema.AttributeType;



/**
 * This class defines a value generated from a template line.
 */
public class TemplateValue
{
  /** The generated template value. */
  private StringBuilder templateValue;

  /** The template line used to generate this value. */
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

