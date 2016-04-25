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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.util;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JScrollPane;

/**
 * A class used to be able to update the scroll position in different panels.
 * It basically contains two lists of scrollbars and points.
 */
public class ViewPositions
{
  private final List<JScrollPane> scrolls = new ArrayList<>();
  private final List<Point> points = new ArrayList<>();

  /**
   * Returns the size of the lists.
   * @return the size of the lists.
   */
  int size()
  {
    return scrolls.size();
  }

  /**
   * Adds a pair of scrollbar and point to the list.
   * @param scroll the scroll bar.
   * @param p the point.
   */
  void add(JScrollPane scroll, Point p)
  {
    scrolls.add(scroll);
    points.add(p);
  }

  /**
   * Returns the point at the provided index.
   * @param index the index.
   * @return the point at the provided index.
   */
  Point getPoint(int index)
  {
    return points.get(index);
  }

  /**
   * Returns the scroll at the provided index.
   * @param index the index.
   * @return the scroll at the provided index.
   */
  JScrollPane getScrollPane(int index)
  {
    return scrolls.get(index);
  }
}
