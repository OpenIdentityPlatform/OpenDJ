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

package org.opends.guitools.controlpanel.event;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/**
 * This is a listener that is basically used to update dynamically the border
 * of a scroll bar.  This is used when we do not want to display the borders of
 * the scrollpane if no scrollbars are visible.  So the code basically adds
 * a component listener to the scroll pane and depending on whether the scroll
 * bars are displayed or not some border to the scroll pane is added (or not).
 *
 */
public class ScrollPaneBorderListener extends ComponentAdapter
{
  private JScrollPane scroll;
  private Border emptyBorder = new EmptyBorder(0, 0, 0, 0);
  private Border etchedBorder = BorderFactory.createMatteBorder(0, 0, 1, 0,
      ColorAndFontConstants.defaultBorderColor);


  /**
   * The constructor of the listener.
   * @param scroll the scroll pane to update.
   * @param addTopBorder whether we want to add a top border or only a bottom
   * border when the border must be displayed.
   */
  public ScrollPaneBorderListener(JScrollPane scroll, boolean addTopBorder)
  {
    this.scroll = scroll;
    scroll.getHorizontalScrollBar().addComponentListener(this);
    scroll.getVerticalScrollBar().addComponentListener(this);
    if (addTopBorder)
    {
      etchedBorder = BorderFactory.createMatteBorder(1, 0, 1, 0,
          ColorAndFontConstants.defaultBorderColor);
    }
  }

  /**
   * The constructor of the listener.
   * @param scroll the scroll pane to update.
   */
  public ScrollPaneBorderListener(JScrollPane scroll)
  {
    this(scroll, false);
  }

  /**
   * {@inheritDoc}
   */
  public void componentShown(ComponentEvent ev)
  {
    updateBorder();
  }

  /**
   * {@inheritDoc}
   */
  public void componentHidden(ComponentEvent ev)
  {
    updateBorder();
  }

  /**
   * Updates the border depending on whether the scroll bars are visible or not.
   *
   */
  public void updateBorder()
  {
    boolean displayBorder = scroll.getVerticalScrollBar().isVisible() ||
    scroll.getHorizontalScrollBar().isVisible();

    if (displayBorder)
    {
      scroll.setBorder(etchedBorder);
    }
    else
    {
      scroll.setBorder(emptyBorder);
    }
  }
}
