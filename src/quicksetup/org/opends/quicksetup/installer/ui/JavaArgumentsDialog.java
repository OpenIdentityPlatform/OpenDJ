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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */

package org.opends.quicksetup.installer.ui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.JavaArguments;
import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.SetupUtils;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is a dialog that appears when the user wants to configure
 * java parameters in the runtime settings panel.
 */
public class JavaArgumentsDialog extends JDialog
{
  private static final long serialVersionUID = -7950773258109643264L;
  private JLabel lInitialMemory;
  private JLabel lMaxMemory;
  private JLabel lOtherArguments;

  private JTextField tfInitialMemory;
  private JTextField tfMaxMemory;
  private JTextField tfOtherArguments;

  private JButton cancelButton;
  private JButton okButton;

  private boolean isCanceled = true;

  private Message message;

  private JavaArguments javaArguments;

  private JPanel inputContainer;

  private static final String INPUT_PANEL = "input";
  private static final String CHECKING_PANEL = "checking";

  private boolean isCheckingVisible;

  private static boolean userAgreedWithWebStart;

  /**
   * Constructor of the JavaArgumentsDialog.
   * @param parent the parent frame for this dialog.
   * @param javaArguments the java arguments used to populate this dialog.
   * @param title the title of the dialog.
   * @param message the message to be displayed in top.
   * @throws IllegalArgumentException if options is null.
   */
  public JavaArgumentsDialog(JFrame parent, JavaArguments javaArguments,
      Message title, Message message)
  throws IllegalArgumentException
  {
    super(parent);
    if (javaArguments == null)
    {
      throw new IllegalArgumentException("javaArguments cannot be null.");
    }
    if (title == null)
    {
      throw new IllegalArgumentException("title cannot be null.");
    }
    if (message == null)
    {
      throw new IllegalArgumentException("message cannot be null.");
    }
    setTitle(title.toString());
    this.message = message;
    this.javaArguments = javaArguments;
    getContentPane().add(createPanel());
    pack();

    updateContents();

    int minWidth = (int) getPreferredSize().getWidth();
    int minHeight = (int) getPreferredSize().getHeight();
    addComponentListener(new MinimumSizeComponentListener(this, minWidth,
        minHeight));
    getRootPane().setDefaultButton(okButton);

    addWindowListener(new WindowAdapter()
    {
      @Override
      public void windowClosing(WindowEvent e)
      {
        cancelClicked();
      }
    });
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

    Utilities.centerOnComponent(this, parent);
  }

  /**
   * Returns <CODE>true</CODE> if the user clicked on cancel and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the user clicked on cancel and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCanceled()
  {
    return isCanceled;
  }

  /**
   * Returns the java arguments object representing the input of the user
   * in this panel.  The method assumes that the values in the panel are
   * valid.
   * @return the java arguments object representing the input of the user
   * in this panel.
   */
  public JavaArguments getJavaArguments()
  {
    JavaArguments javaArguments = new JavaArguments();

    String sMaxMemory = tfMaxMemory.getText().trim();
    if (sMaxMemory.length() > 0)
    {
      javaArguments.setMaxMemory(Integer.parseInt(sMaxMemory));
    }
    String sInitialMemory = tfInitialMemory.getText().trim();
    if (sInitialMemory.length() > 0)
    {
      javaArguments.setInitialMemory(Integer.parseInt(sInitialMemory));
    }
    String[] args = getOtherArguments();
    if (args.length > 0)
    {
      javaArguments.setAdditionalArguments(args);
    }
    return javaArguments;
  }

  private String[] getOtherArguments()
  {
    String sArgs = this.tfOtherArguments.getText().trim();
    if (sArgs.length() > 0)
    {
      String[] args = sArgs.split(" ");
      ArrayList<String> array = new ArrayList<String>();
      for (String arg : args)
      {
        if (arg.length() > 0)
        {
          array.add(arg);
        }
      }
      String[] fArgs = new String[array.size()];
      array.toArray(fArgs);
      return fArgs;
    }
    else
    {
      return new String[]{};
    }
  }

  /**
   * Creates and returns the panel of the dialog.
   * @return the panel of the dialog.
   */
  private JPanel createPanel()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    JPanel contentPanel = new JPanel(new GridBagLayout());
    contentPanel.setBackground(UIFactory.DEFAULT_BACKGROUND);

    JPanel topPanel = new JPanel(new GridBagLayout());
    topPanel.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    topPanel.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    Insets insets = UIFactory.getCurrentStepPanelInsets();
    insets.bottom = 0;
    gbc.insets = insets;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = 3;
    gbc.gridx = 0;
    gbc.gridy = 0;
    Message title = INFO_JAVA_RUNTIME_SETTINGS_TITLE.get();
    JLabel l =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, title,
            UIFactory.TextStyle.TITLE);
    l.setOpaque(false);
    topPanel.add(l, gbc);

    JTextComponent instructionsPane =
      UIFactory.makeHtmlPane(message, UIFactory.INSTRUCTIONS_FONT);
    instructionsPane.setOpaque(false);
    instructionsPane.setEditable(false);

    gbc.gridy ++;
    gbc.insets.top = UIFactory.TOP_INSET_INPUT_SUBPANEL;
    topPanel.add(instructionsPane, gbc);

    gbc.gridy ++;
    gbc.insets.top = UIFactory.TOP_INSET_INPUT_SUBPANEL;
    gbc.insets.bottom = UIFactory.TOP_INSET_INPUT_SUBPANEL;

    inputContainer = new JPanel(new CardLayout());
    inputContainer.setOpaque(false);
    inputContainer.add(createInputPanel(), INPUT_PANEL);
    JPanel checkingPanel = UIFactory.makeJPanel();
    checkingPanel.setLayout(new GridBagLayout());
    checkingPanel.add(UIFactory.makeJLabel(UIFactory.IconType.WAIT,
        INFO_GENERAL_CHECKING_DATA.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID),
        new GridBagConstraints());
    inputContainer.add(checkingPanel, CHECKING_PANEL);

    topPanel.add(inputContainer, gbc);
    gbc.weighty = 1.0;
    gbc.gridy ++;
    gbc.insets = UIFactory.getEmptyInsets();
    topPanel.add(Box.createVerticalGlue(), gbc);

    gbc.gridx = 0;
    gbc.gridy = 0;
    contentPanel.add(topPanel, gbc);
    gbc.weighty = 0.0;
    gbc.gridy ++;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    contentPanel.add(createButtonsPanel(), gbc);

    return contentPanel;
  }

  /**
   * Creates and returns the input sub panel: the panel with all the widgets
   * that are used to define the security options.
   * @return the input sub panel.
   */
  private Component createInputPanel()
  {
    JPanel inputPanel = new JPanel(new GridBagLayout());
    inputPanel.setOpaque(false);

    lInitialMemory = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_INITIAL_MEMORY_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    lInitialMemory.setOpaque(false);
    tfInitialMemory = UIFactory.makeJTextField(Message.EMPTY,
        INFO_INITIAL_MEMORY_TOOLTIP.get(), 10, UIFactory.TextStyle.TEXTFIELD);
    lInitialMemory.setLabelFor(tfInitialMemory);

    lMaxMemory = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_MAX_MEMORY_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    lMaxMemory.setOpaque(false);
    tfMaxMemory = UIFactory.makeJTextField(Message.EMPTY,
        INFO_MAX_MEMORY_TOOLTIP.get(), 10, UIFactory.TextStyle.TEXTFIELD);
    lMaxMemory.setLabelFor(tfMaxMemory);

    lOtherArguments = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_OTHER_JAVA_ARGUMENTS_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    lOtherArguments.setOpaque(false);
    tfOtherArguments = UIFactory.makeJTextField(Message.EMPTY,
        INFO_OTHER_JAVA_ARGUMENTS_TOOLTIP.get(), 30,
        UIFactory.TextStyle.TEXTFIELD);
    lOtherArguments.setLabelFor(tfOtherArguments);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    inputPanel.add(lInitialMemory, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    inputPanel.add(tfInitialMemory, gbc);
    gbc.weightx = 0.0;
    gbc.gridx = 2;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    JLabel lMb = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_MEGABYTE_LABEL.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    lMb.setOpaque(false);
    inputPanel.add(lMb, gbc);
    gbc.gridx = 1;
    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.insets.top = 3;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    inputPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_JAVA_ARGUMENTS_LEAVE_EMPTY.get(),
        UIFactory.TextStyle.INLINE_HELP), gbc);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    inputPanel.add(lMaxMemory, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    inputPanel.add(tfMaxMemory, gbc);
    gbc.weightx = 0.0;
    gbc.gridx = 2;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    lMb = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_MEGABYTE_LABEL.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    lMb.setOpaque(false);
    inputPanel.add(lMb, gbc);
    gbc.gridx = 1;
    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.insets.top = 3;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    inputPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_JAVA_ARGUMENTS_LEAVE_EMPTY.get(),
        UIFactory.TextStyle.INLINE_HELP), gbc);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    inputPanel.add(lOtherArguments, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    inputPanel.add(tfOtherArguments, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.weighty = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    inputPanel.add(Box.createVerticalGlue(), gbc);

    return inputPanel;
  }

  /**
   * Creates and returns the buttons OK/CANCEL sub panel.
   * @return the buttons OK/CANCEL sub panel.
   */
  private Component createButtonsPanel()
  {
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    buttonsPanel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 4;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.left = UIFactory.getCurrentStepPanelInsets().left;
    buttonsPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        null, UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth--;
    gbc.insets.left = 0;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    okButton =
      UIFactory.makeJButton(INFO_OK_BUTTON_LABEL.get(),
          INFO_JAVA_ARGUMENTS_OK_BUTTON_TOOLTIP.get());
    buttonsPanel.add(okButton, gbc);
    okButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        okClicked();
      }
    });

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    cancelButton =
      UIFactory.makeJButton(INFO_CANCEL_BUTTON_LABEL.get(),
          INFO_JAVA_ARGUMENTS_CANCEL_BUTTON_TOOLTIP.get());
    buttonsPanel.add(cancelButton, gbc);
    cancelButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        cancelClicked();
      }
    });

    return buttonsPanel;
  }

  /**
   * Method called when user clicks on cancel.
   *
   */
  private void cancelClicked()
  {
    isCanceled = true;
    dispose();
  }

  /**
   * Method called when user clicks on OK.
   *
   */
  private void okClicked()
  {
    BackgroundTask<ArrayList<Message>> worker =
      new BackgroundTask<ArrayList<Message>>()
    {
      @Override
      public ArrayList<Message> processBackgroundTask()
      {
        setValidLater(lInitialMemory, true);
        setValidLater(lMaxMemory, true);
        setValidLater(lOtherArguments, true);
        int initialMemory = -1;
        int maxMemory = -1;
        ArrayList<Message> errorMsgs = new ArrayList<Message>();
        try
        {
          String sInitialMemory = tfInitialMemory.getText().trim();
          if (sInitialMemory.length() > 0)
          {
            initialMemory = Integer.parseInt(sInitialMemory);
            if (initialMemory <= 0)
            {
              initialMemory = -1;
              errorMsgs.add(ERR_INITIAL_MEMORY_VALUE.get());
              setValidLater(lInitialMemory, false);
            }
          }
        }
        catch (Throwable t)
        {
          errorMsgs.add(ERR_INITIAL_MEMORY_VALUE.get());
          setValidLater(lInitialMemory, false);
        }
        try
        {
          String sMaxMemory = tfMaxMemory.getText().trim();
          if (sMaxMemory.length() > 0)
          {
            maxMemory = Integer.parseInt(sMaxMemory);
            if (maxMemory <= 0)
            {
              maxMemory = -1;
              errorMsgs.add(ERR_MAX_MEMORY_VALUE.get());
              setValidLater(lMaxMemory, false);
            }
          }
        }
        catch (Throwable t)
        {
          errorMsgs.add(ERR_MAX_MEMORY_VALUE.get());
          setValidLater(lMaxMemory, false);
        }
        if (maxMemory != -1 && initialMemory != -1)
        {
          if (initialMemory > maxMemory)
          {
            errorMsgs.add(ERR_MAX_MEMORY_BIGGER_THAN_INITIAL_MEMORY.get());
            setValidLater(lMaxMemory, false);
            setValidLater(lInitialMemory, false);
          }
        }
        if (errorMsgs.isEmpty())
        {
          // Try the options together, often there are interdependencies.
          ArrayList<Message> allErrors = new ArrayList<Message>();
          checkAllArgumentsTogether(initialMemory, maxMemory, allErrors);

          if (!allErrors.isEmpty())
          {
            ArrayList<Message> memoryErrors = new ArrayList<Message>();
            checkMemoryArguments(initialMemory, maxMemory, memoryErrors);
            ArrayList<Message> otherErrors = new ArrayList<Message>();
            checkOtherArguments(otherErrors);

            if (!memoryErrors.isEmpty())
            {
              errorMsgs.addAll(memoryErrors);
              if (!otherErrors.isEmpty())
              {
                errorMsgs.addAll(otherErrors);
              }
            }
            else
            {
              if (!otherErrors.isEmpty())
              {
                errorMsgs.addAll(otherErrors);
              }
              else
              {
                setValidLater(lInitialMemory, false);
                setValidLater(lMaxMemory, false);
                setValidLater(lOtherArguments, false);
                // It appears that the arguments are not compatible together.
                errorMsgs.add(
                    ERR_MEMORY_AND_OTHER_ARGUMENTS_NOT_COMPATIBLE.get());
              }
            }
          }
        }
        return errorMsgs;
      }

      @Override
      public void backgroundTaskCompleted(ArrayList<Message> returnValue,
          Throwable throwable)
      {
        setCheckingVisible(false);
        if (throwable != null)
        {
          // Bug
          throwable.printStackTrace();
          displayError(
              Utils.getThrowableMsg(INFO_BUG_MSG.get(), throwable),
              INFO_ERROR_TITLE.get());
          cancelButton.setEnabled(true);
          okButton.setEnabled(true);
        }
        else
        {
          cancelButton.setEnabled(true);
          okButton.setEnabled(true);

          if (returnValue.size() > 0)
          {
            displayError(Utils.getMessageFromCollection(returnValue, "\n"),
                INFO_ERROR_TITLE.get());
          }
          else
          {
            if (displayWebStartWarningIfRequired())
            {
              isCanceled = false;
              dispose();
            }
          }
        }
      }
    };
    setCheckingVisible(true);
    cancelButton.setEnabled(false);
    okButton.setEnabled(false);
    worker.startBackgroundTask();
  }

  /**
   * Displays an error message dialog.
   *
   * @param msg
   *          the error message.
   * @param title
   *          the title for the dialog.
   */
  private void displayError(Message msg, Message title)
  {
    Utilities.displayError(this, msg, title);
    toFront();
  }

  /**
   * Displays a confirmation dialog and returns <CODE>true</CODE> if the user
   * accepts the message displayed in the dialog and <CODE>false</CODE>
   * otherwise.
   *
   * @param msg
   *          the error message.
   * @param title
   *          the title for the dialog.
   * @return <CODE>true</CODE> if the user accepts the message displayed in the
   * dialog and <CODE>false</CODE> otherwise.
   */
  private boolean displayConfirmationDialog(Message msg, Message title)
  {
    toFront();
    return Utilities.displayConfirmation(this, msg, title);
  }

  /**
   * Updates the widgets on the dialog with the contents of the securityOptions
   * object.
   *
   */
  private void updateContents()
  {
    if (javaArguments.getInitialMemory() > 0)
    {
      tfInitialMemory.setText(String.valueOf(javaArguments.getInitialMemory()));
    }
    else
    {
      tfInitialMemory.setText("");
    }
    if (javaArguments.getMaxMemory() > 0)
    {
      tfMaxMemory.setText(String.valueOf(javaArguments.getMaxMemory()));
    }
    else
    {
      tfMaxMemory.setText("");
    }
    if (javaArguments.getAdditionalArguments() != null)
    {
      StringBuilder sb = new StringBuilder();
      for (String arg : javaArguments.getAdditionalArguments())
      {
        if (sb.length() > 0)
        {
          sb.append(" ");
        }
        sb.append(arg);
      }
      tfOtherArguments.setText(sb.toString());
    }
    else
    {
      tfOtherArguments.setText("");
    }
  }

  /**
   * Method that updates the text style of a provided component by calling
   * SwingUtilities.invokeLater.  This method is aimed to be called outside
   * the event thread (calling it from the event thread will also work though).
   * @param comp the component to be updated.
   * @param valid whether to use a TextStyle to mark the component as valid
   * or as invalid.
   */
  private void setValidLater(final JComponent comp, final boolean valid)
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        UIFactory.setTextStyle(comp,
            valid ? UIFactory.TextStyle.PRIMARY_FIELD_VALID :
              UIFactory.TextStyle.PRIMARY_FIELD_INVALID);
      }
    });
  }

  /**
   * This method displays a working progress icon in the panel.
   * @param visible whether the icon must be displayed or not.
   */
  private void setCheckingVisible(boolean visible)
  {
    if (visible != isCheckingVisible && inputContainer != null)
    {
      CardLayout cl = (CardLayout) inputContainer.getLayout();
      if (visible)
      {
        cl.show(inputContainer, CHECKING_PANEL);
      }
      else
      {
        cl.show(inputContainer, INPUT_PANEL);
      }
      isCheckingVisible = visible;
    }
  }

  /**
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  public static void main(String[] args)
  {
    try
    {
      JavaArguments javaArgs = new JavaArguments();
      javaArgs.setInitialMemory(100);
      javaArgs.setMaxMemory(99);
      javaArgs.setAdditionalArguments(new String[]{"" , "-client", "-XX"});
      // UIFactory.initialize();
      JavaArgumentsDialog dlg = new JavaArgumentsDialog(new JFrame(), javaArgs,
          Message.raw("my title"),
          Message.raw("Set the java arguments for the test command-line."));
      dlg.pack();
      dlg.setVisible(true);
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }

  private final static String INSTALL_PATH =
    Utils.getInstallPathFromClasspath();

  private void checkOptions(String options, Collection<Message> errorMsgs,
      JLabel l,  Message errorMsg)
  {
    checkOptions(options, errorMsgs, new JLabel[]{l}, errorMsg);
  }

  private void checkOptions(String options, Collection<Message> errorMsgs,
      JLabel[] ls,  Message errorMsg)
  {
    if (!Utils.isWebStart())
    {
      String javaHome = System.getProperty("java.home");
      if ((javaHome == null) || (javaHome.length() == 0))
      {
        javaHome = System.getenv(SetupUtils.OPENDJ_JAVA_HOME);
      }
      if (!Utils.supportsOption(options, javaHome, INSTALL_PATH))
      {
        for (JLabel l : ls)
        {
          setValidLater(l, false);
        }
        errorMsgs.add(errorMsg);
      }
    }
  }

  private Message getMemoryErrorMessage(Message msg, int memValue)
  {
    // 2048 MB is acceptable max heap size on 32Bit OS
    if (memValue < 2048)
    {
      return msg;
    }
    else
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(msg);
      mb.append("  ");
      mb.append(ERR_MEMORY_32_BIT_LIMIT.get());
      return mb.toMessage();
    }
  }

  private void checkMemoryArguments(int initialMemory, int maxMemory,
      Collection<Message> errorMsgs)
  {
    setValidLater(lInitialMemory, true);
    setValidLater(lMaxMemory, true);
    if (initialMemory != -1)
    {
      if (maxMemory != -1)
      {
        Message msg = getMemoryErrorMessage(ERR_MEMORY_VALUE_EXTENDED.get(
              JavaArguments.getInitialMemoryGenericArgument(),
              JavaArguments.getMaxMemoryGenericArgument()), maxMemory);
        String sMemory =
          JavaArguments.getInitialMemoryArgument(initialMemory) + " "+
          JavaArguments.getMaxMemoryArgument(maxMemory);
        checkOptions(sMemory,
            errorMsgs,
            new JLabel[] {lInitialMemory, lMaxMemory},
            msg);
      }
      else
      {
        Message msg = getMemoryErrorMessage(
            ERR_INITIAL_MEMORY_VALUE_EXTENDED.get(
                JavaArguments.getInitialMemoryGenericArgument()),
                initialMemory);
        checkOptions(JavaArguments.getInitialMemoryArgument(initialMemory),
            errorMsgs,
            lInitialMemory,
            msg);
      }
    }
    else if (maxMemory != -1)
    {
      Message msg = getMemoryErrorMessage(
          ERR_MAX_MEMORY_VALUE_EXTENDED.get(
              JavaArguments.getInitialMemoryGenericArgument()), maxMemory);
      checkOptions(JavaArguments.getMaxMemoryArgument(maxMemory),
          errorMsgs,
          lMaxMemory,
          msg);
    }
  }

  private void checkAllArgumentsTogether(int initialMemory, int maxMemory,
      Collection<Message> errorMsgs)
  {
    setValidLater(lInitialMemory, true);
    setValidLater(lMaxMemory, true);
    setValidLater(lOtherArguments, true);
    ArrayList<JLabel> ls = new ArrayList<JLabel>();
    StringBuilder sb = new StringBuilder();

    if (initialMemory != -1)
    {
      if (maxMemory != -1)
      {
        String sMemory =
          JavaArguments.getInitialMemoryArgument(initialMemory) + " "+
          JavaArguments.getMaxMemoryArgument(maxMemory);
        sb.append(sMemory);
        ls.add(lInitialMemory);
        ls.add(lMaxMemory);
      }
      else
      {
        sb.append(JavaArguments.getInitialMemoryArgument(initialMemory));
        ls.add(lInitialMemory);
      }
    }
    else if (maxMemory != -1)
    {
      sb.append(JavaArguments.getMaxMemoryArgument(maxMemory));
      ls.add(lMaxMemory);
    }

    String[] otherArgs = getOtherArguments();
    if (otherArgs.length > 0)
    {
      ls.add(lOtherArguments);
      for (String arg : otherArgs)
      {
        if (sb.length() > 0)
        {
          sb.append(" ");
        }
        sb.append(arg);
      }
    }
    if (sb.length() > 0)
    {
      checkOptions(sb.toString(), errorMsgs,
          ls.toArray(new JLabel[ls.size()]),
          ERR_GENERIC_JAVA_ARGUMENT.get(sb.toString()));
    }
  }

  private void checkOtherArguments(Collection<Message> errorMsgs)
  {
    setValidLater(lOtherArguments, true);
    ArrayList<JLabel> ls = new ArrayList<JLabel>();
    StringBuilder sb = new StringBuilder();

    String[] otherArgs = getOtherArguments();
    if (otherArgs.length > 0)
    {
      ls.add(lOtherArguments);
      for (String arg : otherArgs)
      {
        if (sb.length() > 0)
        {
          sb.append(" ");
        }
        sb.append(arg);
      }
    }
    if (sb.length() > 0)
    {
      checkOptions(sb.toString(), errorMsgs, lOtherArguments,
          ERR_GENERIC_JAVA_ARGUMENT.get(sb.toString()));
    }
  }

  private boolean displayWebStartWarningIfRequired()
  {
    boolean returnValue = true;
    if (Utils.isWebStart() && !userAgreedWithWebStart)
    {
      JavaArguments args = getJavaArguments();
      if (!args.equals(javaArguments) &&
          ((args.getInitialMemory() != -1) ||
              (args.getMaxMemory() != -1) ||
              (args.getAdditionalArguments().length > 0)))
      {
        returnValue = displayConfirmationDialog(
            INFO_JAVA_ARGUMENTS_CANNOT_BE_CHECKED_IN_WEBSTART.get(),
            INFO_CONFIRMATION_TITLE.get());
        userAgreedWithWebStart = returnValue;
      }
    }
    return returnValue;
  }
}
