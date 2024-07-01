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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.components;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.util.Date;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opends.guitools.controlpanel.datamodel.ScheduleType;
import org.opends.guitools.controlpanel.ui.GenericDialog;
import org.opends.guitools.controlpanel.ui.TaskToSchedulePanel;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * A class used as component displaying the string representation of a schedule
 * and the possibility of updating it clicking a button.
 */
public class ScheduleSummaryPanel extends JPanel
{
  private static final long serialVersionUID = 3111141404599060028L;
  private ScheduleType schedule = ScheduleType.createLaunchNow();
  private JLabel label;
  private JButton change;
  private TaskToSchedulePanel schedulePanel;
  private GenericDialog scheduleDlg;
  private String taskName;
  private DateFormat formatter =
    DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT);

  /**
   * Default constructor.
   * @param taskName the name of the task to be scheduled.
   */
  public ScheduleSummaryPanel(String taskName)
  {
    super(new GridBagLayout());
    setOpaque(false);
    this.taskName = taskName;
    createLayout();
  }

  /**
   * Returns the schedule represented by this panel.
   * @return the schedule represented by this panel.
   */
  public ScheduleType getSchedule()
  {
    return schedule;
  }

  /**
   * Sets the schedule represented by this panel.
   * @param schedule the schedule represented by this panel.
   */
  public void setSchedule(ScheduleType schedule)
  {
    this.schedule = schedule;
    updateLabel(schedule);
  }

  /**
   * Returns whether the change button is enabled or not.
   * @return <CODE>true</CODE> if the change button is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isChangeEnabled()
  {
    return change.isEnabled();
  }

  /**
   * Sets the enable state of the change button.
   * @param enable whether the change button must be enabled or not.
   */
  public void setChangeEnabled(boolean enable)
  {
    change.setEnabled(enable);
  }

  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    label = Utilities.createDefaultLabel();
    change = Utilities.createButton(INFO_CTRL_PANEL_CHANGE_SCHEDULE.get());
    change.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        changeButtonClicked();
      }
    });
    updateLabel(schedule);

    gbc.fill = GridBagConstraints.NONE;
    add(label, gbc);
    gbc.gridx ++;
    gbc.insets.left = 10;
    add(change, gbc);
    gbc.gridx ++;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 0;
    add(Box.createHorizontalGlue(), gbc);
  }

  private void updateLabel(ScheduleType schedule)
  {
    ScheduleType.Type type = schedule.getType();
    if (type == ScheduleType.Type.LAUNCH_NOW)
    {
      label.setText(INFO_CTRL_PANEL_LAUNCH_NOW_SUMMARY.get().toString());
    }
    else if (type == ScheduleType.Type.LAUNCH_LATER)
    {
      Date date = schedule.getLaunchLaterDate();
      String sDate = formatter.format(date);
      label.setText(INFO_CTRL_PANEL_LAUNCH_LATER_SUMMARY.get(sDate).toString());
    }
    else if (type == ScheduleType.Type.LAUNCH_PERIODICALLY)
    {
      String cron = schedule.getCronValue();
      label.setText(
          INFO_CTRL_PANEL_LAUNCH_PERIODICALLY_SUMMARY.get(cron).toString());
    }
    else
    {
      throw new RuntimeException("Unknown schedule type: "+type);
    }
  }

  private void changeButtonClicked()
  {
    if (schedulePanel == null)
    {
      schedulePanel = new TaskToSchedulePanel(taskName);
      scheduleDlg = new GenericDialog(Utilities.getFrame(this), schedulePanel);
      Utilities.centerGoldenMean(scheduleDlg, Utilities.getParentDialog(this));
      scheduleDlg.setModal(true);
    }
    scheduleDlg.setVisible(true);
    if (!schedulePanel.isCanceled())
    {
      setSchedule(schedulePanel.getSchedule());
    }
  }
}
