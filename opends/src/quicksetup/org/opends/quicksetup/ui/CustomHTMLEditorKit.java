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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.FormView;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Class used to be able to detect events in the button inside an HTML pane.
 */
public class CustomHTMLEditorKit extends HTMLEditorKit
{
  private HashSet<ActionListener> listeners = new HashSet<ActionListener>();
  private static final long serialVersionUID = 298103926252426388L;

  /**
   * Default constructor.
   */
  public CustomHTMLEditorKit()
  {
    super();
  }

  /**
   * {@inheritDoc}
   */
  public ViewFactory getViewFactory()
  {
    return new MyHTMLFactory();
  }

  /**
   * Adds an action listener.
   * @param l the action listener to add.
   */
  public void addActionListener(ActionListener l)
  {
    listeners.add(l);
  }

  /**
   * Removes an action listener.
   * @param l the action listener to remove.
   */
  public void removeActionListener(ActionListener l)
  {
    listeners.remove(l);
  }

  /**
   * Class used to be able to detect events in the button inside an HTML pane.
   */
  class MyHTMLFactory extends HTMLFactory
  {
    /**
     * {@inheritDoc}
     */
    public View create(Element elem)
    {
      View v = super.create(elem);
      if (v instanceof FormView)
      {
        v = new MyFormView(elem);
      }
      return v;
    }
  }

  /**
   * Class used to be able to detect events in the button inside an HTML pane.
   */
  class MyFormView extends FormView
  {
    /**
     * Creates a new FormView object.
     *
     * @param elem the element to decorate
     */
    MyFormView(Element elem)
    {
      super(elem);
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent ev)
    {
      if (ev != null && ev.getWhen() != lastActionWhen) {
        lastActionWhen = ev.getWhen();
        for (ActionListener l: listeners)
        {
          l.actionPerformed(ev);
        }
      }
    }
  }

  private static long lastActionWhen = 0;

}
