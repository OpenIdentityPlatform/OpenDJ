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
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.opends.quicksetup.event;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

/**
 * This class is used to not allowing the user to reduce the size of a component
 * below a certain size.  When we want to set a minimum size on an object we
 * just create the object and then we add it as ComponentListener of the object.
 *
 * This is used basically by the QuickSetupDialog dialog.
 */
public class MinimumSizeComponentListener implements ComponentListener
{
  private Component comp;

  private int minWidth;

  private int minHeight;

  /**
   * Constructor for the MinimumSizeComponentListener.
   *
   * @param comp the component for which we want to set a minimum size
   * @param minWidth the minimum width for the component
   * @param minHeight the minimum height for the component
   */
  public MinimumSizeComponentListener(Component comp, int minWidth, int minHeight)
  {
    this.comp = comp;
    this.minWidth = minWidth + 2;
    // It seems that we must add two points to the minWidth (the border of the frame)
    if (comp instanceof Window)
    {
      this.minWidth += 2;
    }

    this.minHeight = minHeight;
  }

  /**
   * ComponentListener implementation.
   * <p>
   * When the method is called check the size and if it is below the minimum
   * size specified in the constructor, resize it to the minimum size.
   *
   * @param ev the component event.
   */
  @Override
  public void componentResized(ComponentEvent ev)
  {
    int width = comp.getWidth();
    int height = comp.getHeight();
    boolean resize = false;
    if (width < minWidth)
    {
      resize = true;
      width = minWidth;
    }
    if (height < minHeight)
    {
      resize = true;
      height = minHeight;
    }
    if (resize)
    {
      comp.setSize(width, height);
    }
  }

  @Override
  public void componentMoved(ComponentEvent ev)
  {
    // no-op
  }

  @Override
  public void componentShown(ComponentEvent ev)
  {
    // no-op
  }

  @Override
  public void componentHidden(ComponentEvent ev)
  {
    // no-op
  }
}
