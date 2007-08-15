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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import org.opends.messages.Message;

/**
 * This is a commodity class used to couple a label and a text component with
 * a FieldName.  It is mainly used by the QuickSetupStepPanel classes to
 * retrieve easily the labels and fields associated with a FieldName to provide
 * its content or to mark the labels as valid or invalid.  It is also used
 * during the creation of the components as it describes the different
 * properties.
 */

public class LabelFieldDescriptor
{
  private Message label;

  private Message tooltip;

  private FieldType type;

  private LabelType labelType;

  private int size;

  /**
   * This enum contains the different type of labels that can be associated with
   * this LabelFieldDescriptor.
   *
   */
  public enum LabelType
  {
    /**
     * Primary label.
     */
    PRIMARY,
    /**
     * Secondary label.
     */
    SECONDARY
  }

  /**
   * This enum contains the different type of fields that can be associated with
   * this LabelFieldDescriptor.
   *
   */
  public enum FieldType
  {
    /**
     * Editable text field.
     */
    TEXTFIELD,
    /**
     * Password field.
     */
    PASSWORD,
    /**
     * Read only field.
     */
    READ_ONLY
  }

  /**
   * Constructor of this LabelFieldDescriptor.
   * @param label the String of the label.
   * @param tooltip the tooltip of the field.
   * @param type the type of field.
   * @param labelType the type of label.
   * @param size the size of the field.
   */
  public LabelFieldDescriptor(Message label, Message tooltip, FieldType type,
      LabelType labelType, int size)
  {
    this.label = label;
    this.tooltip = tooltip;
    this.type = type;
    this.labelType = labelType;
    this.size = size;
  }

  /**
   * Returns the String displayed by the label.
   * @return the String displayed by the label.
   */
  public Message getLabel()
  {
    return label;
  }

  /**
   * Returns the size of the field.
   * @return the size of the field.
   */
  public int getSize()
  {
    return size;
  }

  /**
   * Returns the tooltip used in the field.
   * @return the tooltip used in the field.
   */
  public Message getTooltip()
  {
    return tooltip;
  }

  /**
   * Returns the field type.
   * @return the field type.
   */
  public FieldType getType()
  {
    return type;
  }

  /**
   * Returns the label type.
   * @return the label type.
   */
  public LabelType getLabelType()
  {
    return labelType;
  }
}
