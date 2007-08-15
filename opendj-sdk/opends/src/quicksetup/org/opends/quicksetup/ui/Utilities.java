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

import org.opends.quicksetup.util.Utils;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.StringTokenizer;

/**
 * A set of utilities specific to GUI QuickSetup applications.
 */
public class Utilities {

  /**
   * Creates a panel with a field and a browse button.
   * @param lbl JLabel for the field
   * @param tf JTextField for holding the browsed data
   * @param but JButton for invoking browse action
   * @return the created panel.
   */
  static public JPanel createBrowseButtonPanel(JLabel lbl,
                                         JTextComponent tf,
                                         JButton but)
  {
    GridBagConstraints gbc = new GridBagConstraints();

    JPanel panel = UIFactory.makeJPanel();
    panel.setLayout(new GridBagLayout());

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = 4;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(lbl, gbc);

    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    gbc.gridwidth--;
    gbc.weightx = 0.1;
    panel.add(tf, gbc);

    gbc.insets.left = UIFactory.LEFT_INSET_BROWSE;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    panel.add(but, gbc);

    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  /**
   * Sets a frames image icon to the standard OpenDS icon appropriate
   * for the running platform.
   *
   * @param frame for which the icon will be set
   */
  static public void setFrameIcon(JFrame frame) {
    UIFactory.IconType ic;
    if (Utils.isMacOS()) {
      ic = UIFactory.IconType.MINIMIZED_MAC;
    } else {
      ic = UIFactory.IconType.MINIMIZED;
    }
    frame.setIconImage(UIFactory.getImageIcon(ic).getImage());
  }

  /**
   * Center the component location based on its preferred size. The code
   * considers the particular case of 2 screens and puts the component on the
   * center of the left screen
   *
   * @param comp the component to be centered.
   */
  public static void centerOnScreen(Component comp)
  {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    int width = (int) comp.getPreferredSize().getWidth();
    int height = (int) comp.getPreferredSize().getHeight();

    boolean multipleScreen = screenSize.width / screenSize.height >= 2;

    if (multipleScreen)
    {
      comp.setLocation((screenSize.width / 4) - (width / 2),
          (screenSize.height - height) / 2);
    } else
    {
      comp.setLocation((screenSize.width - width) / 2,
          (screenSize.height - height) / 2);
    }
  }

  /**
   * Center the component location of the ref component.
   *
   * @param comp the component to be centered.
   * @param ref the component to be used as reference.
   *
   */
  public static void centerOnComponent(Window comp, Component ref)
  {
    comp.setLocationRelativeTo(ref);
  }

  /**
   * Displays a confirmation message dialog.
  *
  * @param parent
   *          the parent frame of the confirmation dialog.
   * @param msg
  *          the confirmation message.
  * @param title
  *          the title of the dialog.
  * @return <CODE>true</CODE> if the user confirms the message, or
  * <CODE>false</CODE> if not.
  */
 public static boolean displayConfirmation(JFrame parent, Message msg,
     Message title)
 {
   return JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(
           parent, wrapMsg(String.valueOf(msg), 100), String.valueOf(title),
           JOptionPane.YES_NO_OPTION,
           JOptionPane.QUESTION_MESSAGE,
           null, // don't use a custom Icon
           null, // the titles of buttons
           null); // default button title
 }

  /**
   * Displays an error message dialog.
   *
   * @param parent
   *          the parent component of the error dialog.
   * @param msg
 *          the error message.
   * @param title
   *          the title for the dialog.
   */
  public static void displayError(Component parent, Message msg, Message title)
  {
    JOptionPane.showMessageDialog(parent,
            wrapMsg(String.valueOf(msg), 100),
            String.valueOf(title), JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Displays an information message dialog.
   *
   * @param parent
   *          the parent frame of the information dialog.
   * @param msg
 *          the error message.
   * @param title
   *          the title for the dialog.
   */
  public static void displayInformationMessage(JFrame parent, Message msg,
      Message title)
  {
    JOptionPane.showMessageDialog(parent,
            wrapMsg(String.valueOf(msg), 100), String.valueOf(title),
            JOptionPane.INFORMATION_MESSAGE);
  }

  /**
   * Private method used to wrap the messages that are displayed in dialogs
   * of type JOptionPane.
   * @param msg the message.
   * @param width the maximum width of the column.
   * @return the wrapped message.
   */
  public static String wrapMsg(String msg, int width)
  {
    StringBuilder   buffer        = new StringBuilder();
    StringTokenizer lineTokenizer = new StringTokenizer(msg, "\n", true);
    while (lineTokenizer.hasMoreTokens())
    {
      String line = lineTokenizer.nextToken();
      if (line.equals("\n"))
      {
        // It's an end-of-line character, so append it as-is.
        buffer.append(line);
      }
      else if (line.length() < width)
      {
        // The line fits in the specified width, so append it as-is.
        buffer.append(line);
      }
      else
      {
        // The line doesn't fit in the specified width, so it needs to be
        // wrapped.  Do so at space boundaries.
        StringBuilder   lineBuffer    = new StringBuilder();
        StringBuilder   delimBuffer   = new StringBuilder();
        StringTokenizer wordTokenizer = new StringTokenizer(line, " ", true);
        while (wordTokenizer.hasMoreTokens())
        {
          String word = wordTokenizer.nextToken();
          if (word.equals(" "))
          {
            // It's a space, so add it to the delim buffer only if the line
            // buffer is not empty.
            if (lineBuffer.length() > 0)
            {
              delimBuffer.append(word);
            }
          }
          else if (word.length() > width)
          {
            // This is a long word that can't be wrapped, so we'll just have to
            // make do.
            if (lineBuffer.length() > 0)
            {
              buffer.append(lineBuffer);
              buffer.append("\n");
              lineBuffer = new StringBuilder();
            }
            buffer.append(word);

            if (wordTokenizer.hasMoreTokens())
            {
              // The next token must be a space, so remove it.  If there are
              // still more tokens after that, then append an EOL.
              wordTokenizer.nextToken();
              if (wordTokenizer.hasMoreTokens())
              {
                buffer.append("\n");
              }
            }

            if (delimBuffer.length() > 0)
            {
              delimBuffer = new StringBuilder();
            }
          }
          else
          {
            // It's not a space, so see if we can fit it on the current line.
            int newLineLength = lineBuffer.length() + delimBuffer.length() +
            word.length();
            if (newLineLength < width)
            {
              // It does fit on the line, so add it.
              lineBuffer.append(delimBuffer).append(word);

              if (delimBuffer.length() > 0)
              {
                delimBuffer = new StringBuilder();
              }
            }
            else
            {
              // It doesn't fit on the line, so end the current line and start
              // a new one.
              buffer.append(lineBuffer);
              buffer.append("\n");

              lineBuffer = new StringBuilder();
              lineBuffer.append(word);

              if (delimBuffer.length() > 0)
              {
                delimBuffer = new StringBuilder();
              }
            }
          }
        }

        // If there's anything left in the line buffer, then add it to the
        // final buffer.
        buffer.append(lineBuffer);
      }
    }
    return buffer.toString();
  }
}
