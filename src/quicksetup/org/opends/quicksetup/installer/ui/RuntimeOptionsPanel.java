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
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.Set;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.messages.Message;
import org.opends.quicksetup.JavaArguments;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.DataReplicationOptions;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;

/**
 * The panel where the user specifies the runtime settings.
 *
 */
public class RuntimeOptionsPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = -8303034619200476754L;

  private JButton bServer;
  private JButton bImport;
  private JLabel lServer;
  private JLabel lImport;
  private JEditorPane warning;
  private Component lastFocusComponent;

  private JavaArguments serverJavaArgs;
  private JavaArguments importJavaArgs;

  private JavaArguments defaultServerJavaArgs;
  private JavaArguments defaultImportJavaArgs;

  // The size of the LDIF file to be imported used as threshold to display
  // a warning message, telling the user to update the import runtime
  // settings.
  private static final long WARNING_THRESOLD_FOR_IMPORT = 200 * 1024 * 1024;
  private static final int WARNING_THRESOLD_AUTOMATICALLY_GENERATED_IMPORT
      = 100000;
  private static final int WARNING_THRESOLD_REPLICATED_ENTRIES = 100000;

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel and used to
   * initialize the fields of the panel.
   */
  public RuntimeOptionsPanel(GuiApplication application)
  {
    super(application);
    createComponents();
    addFocusListeners();
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.bottom = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.weightx = 1.0;
    panel.add(warning, gbc);
    warning.setVisible(false);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    gbc.insets.bottom = 0;

    JLabel l = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_SERVER_RUNTIME_ARGS_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);

    gbc.insets.top = Math.abs(
        bServer.getPreferredSize().height -
        l.getPreferredSize().height) / 2;
    panel.add(l, gbc);
    gbc.gridx ++;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.weightx = 0.5;
    panel.add(lServer, gbc);
    gbc.gridx ++;
    gbc.insets.top = 0;
    gbc.weightx = 0.0;
    panel.add(bServer, gbc);
    gbc.gridx ++;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    panel.add(Box.createHorizontalGlue(), gbc);

    gbc.gridy++;
    gbc.gridx = 0;
    gbc.weightx = 0.0;

    l = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_IMPORT_RUNTIME_ARGS_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    int importInsetsTop = Math.abs(
        bImport.getPreferredSize().height -
        l.getPreferredSize().height) / 2;
    gbc.insets.top = importInsetsTop + UIFactory.TOP_INSET_SECONDARY_FIELD;
    panel.add(l, gbc);
    gbc.gridx ++;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.weightx = 0.5;
    panel.add(lImport, gbc);
    gbc.gridx ++;
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.weightx = 0.0;
    panel.add(bImport, gbc);
    gbc.gridx ++;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    panel.add(Box.createHorizontalGlue(), gbc);

    gbc.gridx = 0;
    gbc.gridwidth = 4;
    gbc.gridy ++;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    panel.add(Box.createVerticalGlue(), gbc);

    return panel;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
  {
    return INFO_JAVA_RUNTIME_OPTIONS_PANEL_INSTRUCTIONS.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_JAVA_RUNTIME_OPTIONS_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;
    if (fieldName == FieldName.SERVER_JAVA_ARGUMENTS)
    {
      value = serverJavaArgs;
    }
    else if (fieldName == FieldName.IMPORT_JAVA_ARGUMENTS)
    {
      value = importJavaArgs;
    }
    return value;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void beginDisplay(UserData uData)
  {
    if (defaultServerJavaArgs == null)
    {
      defaultServerJavaArgs =
        uData.getDefaultJavaArguments(UserData.SERVER_SCRIPT_NAME);
    }
    if (defaultImportJavaArgs == null)
    {
      defaultImportJavaArgs =
        uData.getDefaultJavaArguments(UserData.IMPORT_SCRIPT_NAME);
    }
    boolean updatePanel = false;
    if (serverJavaArgs == null)
    {
      serverJavaArgs = uData.getJavaArguments(UserData.SERVER_SCRIPT_NAME);
      updatePanel = true;
    }
    if (importJavaArgs == null)
    {
      importJavaArgs = uData.getJavaArguments(UserData.IMPORT_SCRIPT_NAME);
      updatePanel = true;
    }
    if (updatePanel)
    {
      lServer.setText(JavaArguments.getMessageForJLabel(
          serverJavaArgs, defaultServerJavaArgs,
          UIFactory.SECONDARY_FIELD_VALID_FONT).toString());
      lImport.setText(JavaArguments.getMessageForJLabel(
          importJavaArgs, defaultImportJavaArgs,
          UIFactory.SECONDARY_FIELD_VALID_FONT).toString());
    }

    updateWarningMessage(uData);
  }

  /**
   * {@inheritDoc}
   */
  public void endDisplay()
  {
    if (lastFocusComponent != null)
    {
      lastFocusComponent.requestFocusInWindow();
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return false;
  }

  /**
   * Adds the required focus listeners to the fields.
   */
  private void addFocusListeners()
  {
    FocusListener l = new FocusListener()
    {
      public void focusGained(FocusEvent e)
      {
        lastFocusComponent = e.getComponent();
      }

      public void focusLost(FocusEvent e)
      {
      }
    };

    bServer.addFocusListener(l);
    bImport.addFocusListener(l);
    lastFocusComponent = bServer;
  }

  private void changeServerClicked()
  {
    JavaArgumentsDialog dlg = new JavaArgumentsDialog(
        getFrame(), serverJavaArgs,
        INFO_SERVER_JAVA_ARGUMENTS_TITLE.get(),
        INFO_SERVER_JAVA_ARGUMENTS_MSG.get());
    dlg.pack();
    dlg.setModal(true);
    dlg.setVisible(true);
    if (!dlg.isCanceled())
    {
      serverJavaArgs = dlg.getJavaArguments();
      lServer.setText(JavaArguments.getMessageForJLabel(
          serverJavaArgs, defaultServerJavaArgs,
          UIFactory.SECONDARY_FIELD_VALID_FONT).toString());
    }
  }

  private void changeImportClicked()
  {
    JavaArgumentsDialog dlg = new JavaArgumentsDialog(
        getFrame(), importJavaArgs,
        INFO_IMPORT_JAVA_ARGUMENTS_TITLE.get(),
        INFO_IMPORT_JAVA_ARGUMENTS_MSG.get());
    dlg.pack();
    dlg.setModal(true);
    dlg.setVisible(true);
    if (!dlg.isCanceled())
    {
      importJavaArgs = dlg.getJavaArguments();
      lImport.setText(JavaArguments.getMessageForJLabel(
          importJavaArgs, defaultImportJavaArgs,
          UIFactory.SECONDARY_FIELD_VALID_FONT).toString());
    }
  }

  private void createComponents()
  {
    warning = UIFactory.makeHtmlPane(Message.EMPTY,
        UIFactory.INSTRUCTIONS_FONT);
    warning.setOpaque(false);

    lServer = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        Message.EMPTY, UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    bServer = UIFactory.makeJButton(INFO_JAVA_RUNTIME_CHANGE_LABEL.get(),
        INFO_JAVA_RUNTIME_CHANGE_SERVER_TOOLTIP.get());
    bServer.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        changeServerClicked();
      }
    });

    lImport = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        Message.EMPTY, UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    bImport = UIFactory.makeJButton(INFO_JAVA_RUNTIME_CHANGE_LABEL.get(),
        INFO_JAVA_RUNTIME_CHANGE_IMPORT_TOOLTIP.get());
    bImport.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        changeImportClicked();
      }
    });
  }

  private JFrame getFrame()
  {
    Component mainWindow = getMainWindow();
    JFrame frame = null;
    if (mainWindow instanceof JFrame)
    {
      frame = (JFrame)mainWindow;
    }
    return frame;
  }

  private void updateWarningMessage(UserData uData)
  {
    Message msg = null;

    DataReplicationOptions repl = uData.getReplicationOptions();
    SuffixesToReplicateOptions suf = uData.getSuffixesToReplicateOptions();
    boolean createSuffix =
      repl.getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY ||
      repl.getType() == DataReplicationOptions.Type.STANDALONE ||
      suf.getType() == SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;

    if (createSuffix)
    {
      NewSuffixOptions options = uData.getNewSuffixOptions();

      switch (options.getType())
      {
      case IMPORT_FROM_LDIF_FILE:
        File ldifFile = new File(options.getLDIFPaths().getFirst());
        if (ldifFile.length() > WARNING_THRESOLD_FOR_IMPORT)
        {
          msg = INFO_IMPORT_FILE_WARNING_UPDATE_RUNTIME_ARGS.get();
        }
        break;

      case IMPORT_AUTOMATICALLY_GENERATED_DATA:
        if (options.getNumberEntries() >
            WARNING_THRESOLD_AUTOMATICALLY_GENERATED_IMPORT)
        {
          msg =
            INFO_AUTOMATICALLY_GENERATED_DATA_WARNING_UPDATE_RUNTIME_ARGS.
            get();
        }
        break;
      }
    }
    else if (repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      int maxReplicatedEntries = 0;

      Set<SuffixDescriptor> suffixes = suf.getSuffixes();
      for (SuffixDescriptor suffix : suffixes)
      {
        int suffixEntries = 0;
        for (ReplicaDescriptor replica : suffix.getReplicas())
        {
          suffixEntries = Math.max(suffixEntries, replica.getEntries());
        }
        maxReplicatedEntries += suffixEntries;
      }

      if (maxReplicatedEntries > WARNING_THRESOLD_REPLICATED_ENTRIES)
      {
        msg = INFO_REPLICATED_ENTRIES_WARNING_UPDATE_RUNTIME_ARGS.get();
      }
    }

    if (msg != null)
    {
      HtmlProgressMessageFormatter formatter =
        new HtmlProgressMessageFormatter();
      StringBuilder buf = new StringBuilder();
      String space = formatter.getSpace().toString();
      String lBreak = formatter.getLineBreak().toString();
      String title = UIFactory.applyFontToHtml(
          INFO_GENERAL_WARNING.get().toString(),
          UIFactory.TITLE_FONT);
      String details = UIFactory.applyFontToHtml(msg.toString(),
          UIFactory.SECONDARY_FIELD_VALID_FONT);
      buf.append(UIFactory.getIconHtml(UIFactory.IconType.WARNING_LARGE))
          .append(space).append(space)
          .append(title)
          .append(lBreak).append(lBreak)
          .append(details);
      String s = "<form>"+UIFactory.applyErrorBackgroundToHtml(buf.toString())+
      "</form>";

      warning.setText(s);
      warning.setVisible(true);
    }
    else
    {
      warning.setText("");
      warning.setVisible(false);
    }
  }
}
