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

package org.opends.guitools.controlpanel.ui;

import java.awt.Color;
import java.awt.Font;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.opends.guitools.controlpanel.util.Utilities;

/**
 * Class containing some Fonts and Colors used in the Control Panel.
 *
 */
public class ColorAndFontConstants
{
  /**
   * Foreground color (the color of normal text).
   */
  public static final Color foreground =
    UIManager.getColor("TextField.foreground");
  /**
   * Background color (the color of the panels).
   */
  public static final Color background;
  private static Color toggleButtonColor;
  /**
   * The border to be used for a text area.
   */
  public static final Border textAreaBorder;
  static
  {
    Border border = new javax.swing.border.EmptyBorder(0, 0, 0, 0);
    Color bg = Color.white;
    try
    {
      if ((foreground.getGreen() + foreground.getRed() + foreground.getBlue()) >
      (200 * 3))
      {
        // This is done to avoid problem in high contrast UIs
        bg = UIManager.getColor("TextField.background");
      }
      else
      {
        bg = Color.white;
      }
      toggleButtonColor = UIManager.getColor("ToggleButton.background");
      if (toggleButtonColor == null)
      {
        toggleButtonColor = new Color(200, 200, 200);
      }

      JScrollPane scroll = new JScrollPane(new JTextArea());
      border = scroll.getViewportBorder();
      if (border == null)
      {
        border = scroll.getBorder();
      }
    }
    catch (Throwable t)
    {
    }
    textAreaBorder = border;
    background = bg;
  }
  /**
   * The text color of buttons.
   */
  public static final Color buttonForeground =
    UIManager.getColor("Button.foreground");
  /**
   * The text color of the category items.
   */
  public static final Color categoryForeground = foreground;
  /**
   * The text color of the BasicExpander components.
   */
  public static final Color expanderForeground = foreground;
  /**
   * The grey color background that is used for instance as background for the
   * buttons in the dialogs (in the bottom of the dialogs).
   */
  public static final Color greyBackground = Utilities.isWindows() ?
  UIManager.getColor("MenuBar.background") :
    UIManager.getColor("Panel.background");
  /**
   * The default border color.
   */
  public static final Color defaultBorderColor =
  Utilities.deriveColorHSB(toggleButtonColor, 0, 0, -.2f);

  /**
   * The grid color for the table.
   */
  public static final Color gridColor =
  Utilities.isMacOS() ? defaultBorderColor :
  UIManager.getColor("Table.gridColor");
  /**
   * The color of the text in the table.
   */
  public static final Color tableForeground = foreground;
  /**
   * The background color of the table.
   */
  public static final Color tableBackground = background;
  /**
   * The text color of the tree.
   */
  public static final Color treeForeground = foreground;
  /**
   * The background color of the tree.
   */
  public static final Color treeBackground = background;
  /**
   * The color of the background when the mouse is over (this is used in some
   * components, like the accordion components or some tables to have a visual
   * hint that some components can be clicked).
   */
  public static final Color mouseOverBackground =
  UIManager.getColor("TextField.selectionBackground");
  /**
   * Text color indicating that a field is valid.
   */
  public static final Color validFontColor = foreground;

  /**
   * The color of the text when the mouse is over (this is used in some
   * components, like the accordion components or some tables to have a visual
   * hint that some components can be clicked).
   */
  public static final Color mouseOverForeground =
  UIManager.getColor("TextField.selectionForeground");
  /**
   * The color of the background when the mouse is pressed (this is used in some
   * components, like the accordion components or some tables to have a visual
   * hint that some components can be clicked).
   */
  public static final Color pressedBackground =
    Utilities.deriveColorHSB(mouseOverBackground,
        0, 0, -.20f);
  /**
   * The color of the text when the mouse is pressed (this is used in some
   * components, like the accordion components or some tables to have a visual
   * hint that some components can be clicked).
   */
  public static final Color pressedForeground =
    Utilities.deriveColorHSB(mouseOverForeground,
        0, 0, +.20f);

  /**
   * The default font of the labels.
   */
  public static final Font defaultFont = UIManager.getFont("Label.font");
  /**
   * The font of the BasicExpander component.
   */
  public static final Font expanderFont = defaultFont.deriveFont(Font.BOLD);
  /**
   * The inline help font.
   */
  public static final Font inlineHelpFont = defaultFont.deriveFont(
  (float)(defaultFont.getSize() - 2));
  /**
   * The font of the table header.
   */
  public final static Font headerFont =
  UIManager.getFont("TableHeader.font").deriveFont(Font.BOLD);
  /**
   * The font to be used in the title of the error panes.
   */
  public static final Font errorTitleFont =
  defaultFont.deriveFont(Font.BOLD).deriveFont(13f);
  /**
   * The font to be used in the CategoryButton component.
   */
  public static final Font categoryFont =
    UIManager.getFont("Label.font").deriveFont(Font.BOLD);
  /**
   * The top border of the accordion component.
   */
  public static final Color topAccordionBorderColor = Utilities.deriveColorHSB(
      toggleButtonColor, 0, 0, .2f);
  /**
   * The font to be used in primary labels.
   */
  public static final Font primaryFont = defaultFont.deriveFont(Font.BOLD);
  /**
   * The font to be used in the tree.
   */
  public static final Font treeFont = UIManager.getFont("Tree.font");
  /**
   * The font to be used in the table.
   */
  public final static Font tableFont = UIManager.getFont("Table.font");
  /**
   * The font to be used in the title of the TitlePanel component.
   */
  public final static Font titleFont =
  defaultFont.deriveFont(Font.BOLD).deriveFont(14f);
  /**
   * Text color indicating that a field is not valid.
   */
  public static final Color invalidFontColor = Color.red;
  /**
   * The font to be used when the field associated with a primary label is not
   * valid.
   */
  public static final Font primaryInvalidFont =
    primaryFont.deriveFont(Font.ITALIC);
  /**
   * The font to be used when the field associated with a normal label is not
   * valid.
   */
  public static final Font invalidFont = defaultFont.deriveFont(Font.ITALIC);
  /**
   * The font to be used in the progress dialog's 'Details' section.
   */
  public static final Font progressFont = UIManager.getFont("EditorPane.font");

}
