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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.util;

import java.awt.Point;
import java.util.ArrayList;

import javax.swing.JScrollPane;

/**
 * A class used to be able to update the scroll position in different panels.
 * It basically contains two lists of scrollbars and points.
 *
 */
public class ViewPositions
{
  private ArrayList<JScrollPane> scrolls = new ArrayList<JScrollPane>();
  private ArrayList<Point> points = new ArrayList<Point>();

  /**
   * Returns the size of the lists.
   * @return the size of the lists.
   */
  public int size()
  {
    return scrolls.size();
  }

  /**
   * Adds a pair of scrollbar and point to the list.
   * @param scroll the scroll bar.
   * @param p the point.
   */
  public void add(JScrollPane scroll, Point p)
  {
    scrolls.add(scroll);
    points.add(p);
  }

  /**
   * Clears the contents of both lists.
   *
   */
  public void clear()
  {
    scrolls.clear();
    points.clear();
  }

  /**
   * Returns the point at the provided index.
   * @param index the index.
   * @return the point at the provided index.
   */
  public Point getPoint(int index)
  {
    return points.get(index);
  }

  /**
   * Returns the scroll at the provided index.
   * @param index the index.
   * @return the scroll at the provided index.
   */
  public JScrollPane getScrollPane(int index)
  {
    return scrolls.get(index);
  }
}
