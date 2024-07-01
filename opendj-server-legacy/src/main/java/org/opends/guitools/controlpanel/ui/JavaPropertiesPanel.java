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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JLabel;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The panel where the user can specify the java arguments and java home to be
 * used in the command-lines.
 */
public class JavaPropertiesPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -7886215660289880597L;

  private JLabel javaHome;
  private boolean initialized;

  /** Default constructor. */
  public JavaPropertiesPanel()
  {
    super();
    createLayout();
  }

  @Override
  public void okClicked()
  {
    cancelClicked();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_JAVA_PROPERTIES_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return javaHome;
  }

  @Override
  public boolean requiresScroll()
  {
    return false;
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    javaHome = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_EDIT_JAVA_PROPERTIES_FILE.get());
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 1;
    add(javaHome, gbc);
  }

  @Override
  public void toBeDisplayed(boolean visible)
  {
    boolean isLocal = true;
    if (getInfo() != null)
    {
      isLocal = getInfo().getServerDescriptor().isLocal();
    }
    if (visible && isLocal && !initialized)
    {
      initialized = true;
    }
  }

  @Override
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    // Nothing to do
  }
}
