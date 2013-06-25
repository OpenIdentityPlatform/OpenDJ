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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import org.opends.server.types.AttributeType;

/**
 * This class defines a line that may appear in a template or branch.  It may
 * contain any number of tags to be evaluated.
 */
public class TemplateLine
{
  // The attribute type for this template line.
  private AttributeType attributeType;

  // The line number on which this template line appears in the template file.
  private int lineNumber;

  // The set of tags for this template line.
  private Tag[] tags;

  // Whether this line corresponds to an URL value or not.
  private boolean isURL;

  // Whether this line corresponds to a base64 encoded value or not.
  private boolean isBase64;


  /**
   * Retrieves the attribute type for this template line.
   *
   * @return  The attribute type for this template line.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * Retrieves the line number on which this template line appears in the
   * template file.
   *
   * @return  The line number on which this template line appears in the
   *          template file.
   */
  public int getLineNumber()
  {
    return lineNumber;
  }



  /**
   * Returns whether the value of this template line corresponds to an URL
   * or not.
   *
   * @return <CODE>true</CODE> if the value of this template line corresponds
   * to an URL and <CODE>false</CODE> otherwise.
   */
  public boolean isURL()
  {
    return isURL;
  }

  /**
   * Returns whether the value of this template line corresponds to a Base64
   * encoded value or not.
   *
   * @return <CODE>true</CODE> if the value of this template line corresponds
   * to a Base64 encoded value and <CODE>false</CODE> otherwise.
   */
  public boolean isBase64()
  {
    return isBase64;
  }


  /**
   * Creates a new template line with the provided information.
   *
   * @param  attributeType  The attribute type for this template line.
   * @param  lineNumber     The line number on which this template line appears
   *                        in the template file.
   * @param  tags           The set of tags for this template line.
   */
  public TemplateLine(AttributeType attributeType, int lineNumber, Tag[] tags)
  {
    this(attributeType, lineNumber, tags, false, false);
  }


  /**
   * Creates a new template line with the provided information.
   *
   * @param  attributeType  The attribute type for this template line.
   * @param  lineNumber     The line number on which this template line appears
   *                        in the template file.
   * @param  tags           The set of tags for this template line.
   * @param  isURL          Whether this template line's value is an URL or not.
   * @param  isBase64       Whether this template line's value is Base64 encoded
   *                        or not.
   */
  public TemplateLine(AttributeType attributeType, int lineNumber, Tag[] tags,
      boolean isURL, boolean isBase64)
  {
    this.attributeType = attributeType;
    this.lineNumber    = lineNumber;
    this.tags          = tags;
    this.isURL         = isURL;
    this.isBase64      = isBase64;
  }


  /**
   * Generates the content for this template line and places it in the provided
   * template entry.
   *
   * @param  templateEntry  The template entry being generated.
   *
   * @return  The result of generating the template line.
   */
  public TagResult generateLine(TemplateEntry templateEntry)
  {
    TemplateValue value = new TemplateValue(this);

    for (Tag t : tags)
    {
      TagResult result = t.generateValue(templateEntry, value);
      if (! (result.keepProcessingLine() && result.keepProcessingEntry() &&
             result.keepProcessingParent() &&
             result.keepProcessingTemplateFile()))
      {
        return result;
      }
    }

    templateEntry.addValue(value);
    return TagResult.SUCCESS_RESULT;
  }
}

