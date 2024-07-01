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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.quicksetup.ui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.FormView;
import javax.swing.text.html.HTMLEditorKit;

/** Class used to be able to detect events in the button inside an HTML pane. */
public class CustomHTMLEditorKit extends HTMLEditorKit
{
  private final Set<ActionListener> listeners = new HashSet<>();
  private static final long serialVersionUID = 298103926252426388L;

  /** Default constructor. */
  public CustomHTMLEditorKit()
  {
    super();
  }

  @Override
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

  /** Class used to be able to detect events in the button inside an HTML pane. */
  private class MyHTMLFactory extends HTMLFactory
  {
    @Override
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

  /** Class used to be able to detect events in the button inside an HTML pane. */
  private class MyFormView extends FormView
  {
    /**
     * Creates a new FormView object.
     *
     * @param elem the element to decorate
     */
    private MyFormView(Element elem)
    {
      super(elem);
    }

    @Override
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

    @Override
    protected Component createComponent()
    {
      Component comp = super.createComponent();
      if (comp instanceof JButton)
      {
        ((JButton)comp).setOpaque(false);
      }
      return comp;
    }
  }

  private static long lastActionWhen;

}
