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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.opends.guitools.controlpanel.datamodel.Category;
import org.opends.guitools.controlpanel.ui.border.AccordionElementBorder;

/**
 * The panel representing a category.  It contains a CategoryButton and a panel
 * that is displayed when the CategoryButton is in a certain state.  They
 * are used on the left side of the main Control Panel.
 *
 */
public class CategoryPanel extends JPanel {
  private static final long serialVersionUID = 8941374689175404431L;
  private JPanel panel;
  private JComponent child;

  private CategoryButton expandButton;
  private boolean expanded = true;

  static final Border categoryBorder = new AccordionElementBorder();

  /**
   * Constructor the the panel.
   * @param child the component that must be displayed by this panel if its
   * CategoryButton is in a certain state.
   * @param category the Category associated with the panel.
   */
  public CategoryPanel(JComponent child, Category category)
  {
    this.child = child;
    setLayout(new BorderLayout());
    panel = new JPanel(new BorderLayout());
    add(panel, BorderLayout.CENTER);
    panel.add(child, BorderLayout.CENTER);

    expandButton = new CategoryButton(category);
    expandButton.setSelected(isExpanded());
    expandButton.addChangeListener(new CollapseListener());
    add(expandButton, BorderLayout.NORTH);

    setBorder(categoryBorder);
  }

  /**
   * Sets whether the state of the panel is extended or not (if expanded the
   * Component provided in the constructor will be displayed).
   * @param expanded whether the panel is extended or not.
   */
  public void setExpanded(boolean expanded) {
    boolean oldExpanded = this.expanded;
    if (oldExpanded != expanded) {
      expandButton.setSelected(expanded);
      this.expanded = expanded;

      //setCollapseHeight(expanded? childPrefSize.height : 0);
      child.setVisible(expanded);
      firePropertyChange("expanded", oldExpanded, expanded);
    }
  }

  /**
   * Returns <CODE>true</CODE> if the panel is extended and <CODE>false</CODE>
   * otherwise.
   * @return <CODE>true</CODE> if the panel is extended and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isExpanded()
  {
    return expanded;
  }

  /**
   * {@inheritDoc}
   */
  public void setForeground(Color foreground)
  {
    super.setForeground(foreground);
    if (expandButton != null)
    {
      expandButton.setForeground(foreground);
    }
  }


  /**
   * The custom listener used to display the child component.
   *
   */
  private class CollapseListener implements ChangeListener
  {
    /**
     * {@inheritDoc}
     */
    public void stateChanged(ChangeEvent event) {
      setExpanded(expandButton.isSelected());
    }
  }
}
