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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.quicksetup.ui;

import org.forgerock.i18n.LocalizableMessage;

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
  private LocalizableMessage label;

  private LocalizableMessage tooltip;

  private FieldType type;

  private LabelType labelType;

  private int size;

  /**
   * This enum contains the different type of labels that can be associated with
   * this LabelFieldDescriptor.
   */
  public enum LabelType
  {
    /** Primary label. */
    PRIMARY,
    /** Secondary label. */
    SECONDARY
  }

  /**
   * This enum contains the different type of fields that can be associated with
   * this LabelFieldDescriptor.
   */
  public enum FieldType
  {
    /** Editable text field. */
    TEXTFIELD,
    /** Password field. */
    PASSWORD,
    /** Read only field. */
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
  public LabelFieldDescriptor(LocalizableMessage label, LocalizableMessage tooltip, FieldType type,
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
  public LocalizableMessage getLabel()
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
  public LocalizableMessage getTooltip()
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
