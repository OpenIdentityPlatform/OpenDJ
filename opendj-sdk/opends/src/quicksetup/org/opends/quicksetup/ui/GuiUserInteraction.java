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

package org.opends.quicksetup.ui;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.UserInteraction;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.util.Utils;

import javax.swing.*;
import javax.swing.plaf.basic.BasicOptionPaneUI;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * This class supports user interactions for a graphical application.
 */
public class GuiUserInteraction implements UserInteraction {

  static private final int MAX_CHARS_PER_LINE = 100;

  private Component parent = null;

  /**
   * Creates an instance.
   * @param parent Component acting as parent to dialogs supporting interaction.
   */
  public GuiUserInteraction(Component parent) {
    this.parent = parent;
  }

  /**
   * {@inheritDoc}
   */
  public Object confirm(Message summary, Message details,
                        Message title, MessageType type, Message[] options,
                        Message def) {
    return confirm(summary, details, null, title, type, options, def, null);
  }

  /**
   * {@inheritDoc}
   */
  public Object confirm(Message summary, Message details, Message fineDetails,
                        Message title, MessageType type, Message[] options,
                        Message def, Message viewDetailsOption) {
    int optionType;
    if (options != null) {
      if (options.length == 2) {
        optionType = JOptionPane.YES_NO_OPTION;
      } else if (options.length == 3) {
        optionType = JOptionPane.YES_NO_CANCEL_OPTION;
      } else {
        throw new IllegalArgumentException(
                "unsupported number of options: " + options.length);
      }
    } else {
      throw new NullPointerException("options cannot be null");
    }
    int msgType;
    switch(type) {
        case PLAIN: msgType = JOptionPane.PLAIN_MESSAGE; break;
        case ERROR: msgType = JOptionPane.ERROR_MESSAGE; break;
        case INFORMATION: msgType = JOptionPane.INFORMATION_MESSAGE; break;
        case WARNING: msgType = JOptionPane.WARNING_MESSAGE; break;
        case QUESTION: msgType = JOptionPane.QUESTION_MESSAGE; break;
        default: throw new IllegalArgumentException("unsupported MessageType");
    }
    JOptionPane op;
    if (fineDetails != null) {
      op = new DetailsOptionPane(MAX_CHARS_PER_LINE, fineDetails);
    } else {
      op = new MaxCharactersPerLineOptionPane(MAX_CHARS_PER_LINE);
    }

    // Create the main message using HTML formatting.  The max
    // characters per line functionality of the extends options
    // pane does not affect message that are components so we
    // have to format this ourselves.
    MessageBuilder sb = new MessageBuilder();
    sb.append(Constants.HTML_BOLD_OPEN);
    sb.append(Utils.breakHtmlString(summary, MAX_CHARS_PER_LINE));
    sb.append(Constants.HTML_BOLD_CLOSE);
    sb.append(Constants.HTML_LINE_BREAK);
    sb.append(Constants.HTML_LINE_BREAK);

    sb.append(Utils.breakHtmlString(details, MAX_CHARS_PER_LINE));
    JEditorPane ep = UIFactory.makeHtmlPane(
            sb.toMessage(),
            UIFactory.INSTRUCTIONS_FONT);
    ep.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
    op.setMessage(ep);
    op.setOptionType(optionType);
    op.setMessageType(msgType);
    op.setOptions(options);
    op.setInitialValue(def);
    JDialog dlg = op.createDialog(parent, String.valueOf(title));
    dlg.setVisible(true);
    return op.getValue();
  }

  /**
   * {@inheritDoc}
   */
  public String createUnorderedList(List list) {
    StringBuilder sb = new StringBuilder();
    if (list != null) {
      sb.append(Constants.HTML_UNORDERED_LIST_OPEN);
      for (Object o : list) {
        sb.append(Constants.HTML_LIST_ITEM_OPEN);
        sb.append(o.toString());
        sb.append(Constants.HTML_LIST_ITEM_CLOSE);
      }
      sb.append(Constants.HTML_UNORDERED_LIST_CLOSE);
    }
    return sb.toString();
  }

  /**
   * {@inheritDoc}
   */
  public String promptForString(Message prompt, Message title,
                                String defaultValue) {
    Object o = JOptionPane.showInputDialog(
            parent, prompt.toString(), title.toString(),
            JOptionPane.QUESTION_MESSAGE,
            null, null, defaultValue);
    return o != null ? o.toString() : null;
  }

  /**
   * JOptionPane that controls the number of characters that are allowed
   * to appear on a single line in the input area of the dialog.
   */
  private class MaxCharactersPerLineOptionPane extends JOptionPane {

    /** Implements serializable. */
    static final long serialVersionUID = 8984664928623358120L;

    private int maxCharactersPerLineCount;

    /**
     * Creates an instance.
     * @param maxCharactersPerLine the maximum number of characters that
     *        are allowed on a single line in the dialog.
     */
    public MaxCharactersPerLineOptionPane(int maxCharactersPerLine) {
      this.maxCharactersPerLineCount = maxCharactersPerLine;
    }

    /**
     * {@inheritDoc}
     */
    public int getMaxCharactersPerLineCount() {
      return maxCharactersPerLineCount;
    }

  }

  /**
   * JOptionPane that controls the number of characters that are allowed
   * to appear on a single line in the input area of the dialog.
   */
  private class DetailsOptionPane extends MaxCharactersPerLineOptionPane {

    static final long serialVersionUID = -7813059467702205272L;

    private static final int MAX_DETAILS_COMPONENT_HEIGHT = 200;

    private boolean detailsShowing = false;

    private Component detailsComponent;

    private JDialog dialog;

    /**
     * Creates an instance.
     * @param maxCharactersPerLine the maximum number of characters that
     *        are allowed on a single line in the dialog.
     * @param details String of HTML representing the details section of the
     *        dialog.
     */
    public DetailsOptionPane(int maxCharactersPerLine,
                                         Message details) {
      super(maxCharactersPerLine);
      detailsComponent = createDetailsComponent(details);
    }

    /**
     * {@inheritDoc}
     */
    public Component add(Component comp) {
      if ("OptionPane.buttonArea".equals(comp.getName())) {
        JPanel detailsButtonsPanel = new JPanel();
        detailsButtonsPanel.setLayout(
                new BoxLayout(detailsButtonsPanel,
                              BoxLayout.LINE_AXIS));
        final Message showDetailsLabel = INFO_SHOW_DETAILS_BUTTON_LABEL.get();
        final Message hideDetailsLabel = INFO_HIDE_DETAILS_BUTTON_LABEL.get();
        final JButton btnDetails = new JButton(showDetailsLabel.toString());
        btnDetails.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            Dimension current = dialog.getSize();
            if (!detailsShowing) {
              // detailsComponent.setVisible(true);
              dialog.setSize(current.width,
                      current.height + getExpansionHeight());
              btnDetails.setText(hideDetailsLabel.toString());
            } else {
              // detailsComponent.setVisible(false);
              dialog.setSize(current.width,
                      current.height - getExpansionHeight());
              btnDetails.setText(showDetailsLabel.toString());
            }
            detailsShowing = !detailsShowing;
          }
        });

        JPanel detailsBottom = new JPanel();
        Border border = UIManager.getBorder("OptionPane.buttonAreaBorder");
        if (border != null) {
          detailsBottom.setBorder(border);
        }
        detailsBottom.setLayout(
                new BasicOptionPaneUI.ButtonAreaLayout(
                        UIManager.getBoolean("OptionPane.sameSizeButtons"),
                        UIManager.getInt("OptionPane.buttonPadding")));
        detailsBottom.add(btnDetails);
        detailsButtonsPanel.add(detailsBottom);
        detailsButtonsPanel.add(Box.createHorizontalGlue());
        detailsButtonsPanel.add(comp);
        super.add(detailsButtonsPanel);
      } else {
        super.add(comp);
      }
      return comp;
    }

    /**
     * {@inheritDoc}
     */
    public JDialog createDialog(Component parentComponent, String title)
            throws HeadlessException
    {
      this.dialog = super.createDialog(parentComponent, title);
      Dimension d = dialog.getSize();
      add(detailsComponent);
      this.dialog.pack();
      dialog.setSize(d);
      return dialog;
    }

    private Component createDetailsComponent(Message details) {
      JPanel detailsPanel = new JPanel();
      detailsPanel.setLayout(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();

      gbc.insets = new Insets(15, 0, 0, 0);
      gbc.fill = GridBagConstraints.HORIZONTAL;
      detailsPanel.add(UIFactory.makeJLabel(null,
              INFO_DETAILS_LABEL.get(),
              UIFactory.TextStyle.PRIMARY_FIELD_VALID), gbc);

      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.gridx++;
      gbc.weightx = 1.0;
      gbc.weighty = 1.0;
      gbc.fill = GridBagConstraints.BOTH;

      JEditorPane ep;
      if (Utils.containsHtml(String.valueOf(details))) {
        ep = UIFactory.makeHtmlPane(details, UIFactory.INSTRUCTIONS_FONT);
      } else {
        ep = UIFactory.makeTextPane(details, UIFactory.TextStyle.INSTRUCTIONS);
      }
      ep.setOpaque(false);

      detailsPanel.add(new JScrollPane(ep), gbc);
      return detailsPanel;
    }

    private int getExpansionHeight() {
      return (int) Math.min(detailsComponent.getPreferredSize().getHeight(),
              MAX_DETAILS_COMPONENT_HEIGHT);
    }

  }

//  public static void main(String[] args) {
//    new GuiUserInteraction(null).confirm(
//            "Summary",
//            "Details",
//            "Title",
//            MessageType.ERROR,
//            new String[]{"Yes","No"},"No");
//  }

}
