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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.PlainDocument;

import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ScheduleType;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.
 NumericLimitedSizeDocumentFilter;
import org.opends.guitools.controlpanel.ui.components.TimeDocumentFilter;
import org.opends.guitools.controlpanel.ui.renderer.
 NoLeftInsetCategoryComboBoxRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.backends.task.RecurringTask;

/**
 * The panel that allows the user to specify when a task will be launched.
 *
 */
public class TaskToSchedulePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 6855081932432566784L;

  private String taskName;

  private JComboBox scheduleType;

  private JTextField time;
  private JTextField day;
  private JComboBox month;
  private JComboBox year;

  private JLabel lTime;
  private JLabel lDay;
  private JLabel lMonth;
  private JLabel lYear;

  private JLabel lDailyTime;
  private JTextField dailyTime;

  private JLabel lWeeklyTime;
  private JLabel lWeeklyDays;
  private JTextField weeklyTime;
  private JCheckBox sunday, monday, tuesday, wednesday, thursday, friday,
  saturday;
  {
    sunday =
      Utilities.createCheckBox(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_SUNDAY.get());
    monday =
      Utilities.createCheckBox(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_MONDAY.get());
    tuesday =
      Utilities.createCheckBox(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TUESDAY.get());
    wednesday =
      Utilities.createCheckBox(
          INFO_CTRL_PANEL_TASK_TO_SCHEDULE_WEDNESDAY.get());
    thursday =
      Utilities.createCheckBox(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_THURSDAY.get());
    friday =
      Utilities.createCheckBox(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_FRIDAY.get());
    saturday =
      Utilities.createCheckBox(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_SATURDAY.get());
  }

  JCheckBox[] weekDays =
  {
      sunday, monday, tuesday, wednesday, thursday, friday, saturday
  };

  private JLabel lMonthlyTime;
  private JLabel lMonthlyDays;
  private JTextField monthlyTime;
  private JCheckBox[] monthDays = new JCheckBox[31];

  private JLabel lCronMinute;
  private JLabel lCronHour;
  private JLabel lCronWeekDay;
  private JLabel lCronMonthDay;
  private JLabel lCronMonth;

  private JTextField cronMinute;
  private JTextField cronHour;
  private JTextField cronWeekDay;
  private JTextField cronMonthDay;
  private JTextField cronMonth;

  private Component launchLaterPanel;
  private Component dailyPanel;
  private Component weeklyPanel;
  private Component monthlyPanel;
  private Component cronPanel;

  private Message LAUNCH_NOW = INFO_CTRL_PANEL_LAUNCH_NOW.get();
  private Message LAUNCH_LATER = INFO_CTRL_PANEL_LAUNCH_LATER.get();
  private Message LAUNCH_DAILY = INFO_CTRL_PANEL_TASK_TO_SCHEDULE_DAILY.get();
  private Message LAUNCH_WEEKLY = INFO_CTRL_PANEL_TASK_TO_SCHEDULE_WEEKLY.get();
  private Message LAUNCH_MONTHLY =
    INFO_CTRL_PANEL_TASK_TO_SCHEDULE_MONTHLY.get();
  private Message CRON = INFO_CTRL_PANEL_TASK_TO_SCHEDULE_CRON.get();

  private ScheduleType schedule;

  /**
   * Default constructor.
   * @param taskName the name of the task to be scheduled.
   */
  public TaskToSchedulePanel(String taskName)
  {
    super();
    this.taskName = taskName;
    createLayout();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    JEditorPane explanation = Utilities.makeHtmlPane(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_SUMMARY.get(taskName).toString(),
        ColorAndFontConstants.defaultFont);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(explanation, gbc);
    gbc.gridy ++;
    gbc.insets.top = 10;
    scheduleType = Utilities.createComboBox();
    scheduleType.setModel(new DefaultComboBoxModel());

    ArrayList<Object> newElements = new ArrayList<Object>();
    newElements.add(new CategorizedComboBoxElement(LAUNCH_NOW,
        CategorizedComboBoxElement.Type.REGULAR));
    newElements.add(COMBO_SEPARATOR);
    newElements.add(new CategorizedComboBoxElement(LAUNCH_LATER,
        CategorizedComboBoxElement.Type.REGULAR));
    newElements.add(COMBO_SEPARATOR);
    newElements.add(new CategorizedComboBoxElement(LAUNCH_DAILY,
        CategorizedComboBoxElement.Type.REGULAR));
    newElements.add(new CategorizedComboBoxElement(LAUNCH_WEEKLY,
        CategorizedComboBoxElement.Type.REGULAR));
    newElements.add(new CategorizedComboBoxElement(LAUNCH_MONTHLY,
        CategorizedComboBoxElement.Type.REGULAR));
    newElements.add(new CategorizedComboBoxElement(CRON,
        CategorizedComboBoxElement.Type.REGULAR));
    updateComboBoxModel(newElements,
        (DefaultComboBoxModel)scheduleType.getModel());
    scheduleType.setRenderer(
        new NoLeftInsetCategoryComboBoxRenderer(scheduleType));
    scheduleType.addItemListener(new IgnoreItemListener(scheduleType));

    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.NONE;
    add(scheduleType, gbc);

    launchLaterPanel = createLaunchLaterPanel();
    dailyPanel = createDailyPanel();
    weeklyPanel = createWeeklyPanel();
    monthlyPanel = createMonthlyPanel();
    cronPanel = createCronPanel();

    scheduleType.addItemListener(new ItemListener()
    {
      public void itemStateChanged(ItemEvent ev)
      {
        Object element = scheduleType.getSelectedItem();
        boolean launchLaterVisible = false;
        boolean launchDailyVisible = false;
        boolean launchWeeklyVisible = false;
        boolean launchMonthlyVisible = false;
        boolean cronVisible = false;
        if (element != null)
        {
          if (element instanceof CategorizedComboBoxElement)
          {
            element = ((CategorizedComboBoxElement)element).getValue();
          }
          launchLaterVisible = element == LAUNCH_LATER;
          launchDailyVisible = element == LAUNCH_DAILY;
          launchWeeklyVisible = element == LAUNCH_WEEKLY;
          launchMonthlyVisible = element == LAUNCH_MONTHLY;
          cronVisible = element == CRON;
        }
        launchLaterPanel.setVisible(launchLaterVisible);
        dailyPanel.setVisible(launchDailyVisible);
        weeklyPanel.setVisible(launchWeeklyVisible);
        monthlyPanel.setVisible(launchMonthlyVisible);
        cronPanel.setVisible(cronVisible);
      }
    });
    launchLaterPanel.setVisible(false);
    dailyPanel.setVisible(false);
    weeklyPanel.setVisible(false);
    monthlyPanel.setVisible(false);
    cronPanel.setVisible(false);

    int width = 0;
    int height = 0;
    Component[] comps =
    {launchLaterPanel, dailyPanel, weeklyPanel, monthlyPanel, cronPanel};
    for (Component comp : comps)
    {
      width = Math.max(width, comp.getPreferredSize().width);
      height = Math.max(height, comp.getPreferredSize().height);
    }

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.insets.left = 30;
    add(launchLaterPanel, gbc);
    add(dailyPanel, gbc);
    add(weeklyPanel, gbc);
    add(monthlyPanel, gbc);
    add(cronPanel, gbc);
    add(Box.createRigidArea(new Dimension(width, height)), gbc);
    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.weighty = 1.0;
    add(Box.createVerticalGlue(), gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    // Reset the schedule and the labels
    if (visible)
    {
      schedule = null;
      setPrimaryValid(lTime);
      setPrimaryValid(lDay);
      setPrimaryValid(lMonth);
      setPrimaryValid(lYear);
      setPrimaryValid(lWeeklyTime);
      setPrimaryValid(lWeeklyDays);
      setPrimaryValid(lMonthlyTime);
      setPrimaryValid(lMonthlyDays);
      setPrimaryValid(lCronMinute);
      setPrimaryValid(lCronHour);
      setPrimaryValid(lCronMonthDay);
      setPrimaryValid(lCronMonth);
      setPrimaryValid(lCronWeekDay);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TITLE.get(taskName);
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    schedule = null;
    ArrayList<Message> errorMessages = new ArrayList<Message>();

    updateErrorMessages(errorMessages);

    if (errorMessages.size() > 0)
    {
      displayErrorDialog(errorMessages);
    }
    else
    {
      schedule = createSchedule();
      Utilities.getParentDialog(this).setVisible(false);
    }
  }

  /**
   * Checks the validity of the provided information and updates the provided
   * collection of messages with the errors that have been found.
   * @param errorMessages the collection of messages to be updated.
   */
  private void updateErrorMessages(Collection<Message> errorMessages)
  {
    Object type =
      ((CategorizedComboBoxElement)scheduleType.getSelectedItem()).getValue();
    if (type == LAUNCH_LATER)
    {
      updateLaunchLaterErrorMessages(errorMessages);
    }
    else if (type == LAUNCH_DAILY)
    {
      updateLaunchDailyErrorMessages(errorMessages);
    }
    else if (type == LAUNCH_WEEKLY)
    {
      updateLaunchWeeklyErrorMessages(errorMessages);
    }
    else if (type == LAUNCH_MONTHLY)
    {
      updateLaunchMonthlyErrorMessages(errorMessages);
    }
    else if (type == CRON)
    {
      updateCronErrorMessages(errorMessages);
    }
  }

  /**
   * Checks the validity of the launch later information and updates
   * the provided collection of messages with the errors that have been found.
   * The associated labels are also updated.
   * @param errorMessages the collection of messages to be updated.
   */
  private void updateLaunchLaterErrorMessages(Collection<Message> errorMessages)
  {
    setPrimaryValid(lTime);
    setPrimaryValid(lDay);
    setPrimaryValid(lMonth);
    setPrimaryValid(lYear);

    int previousErrorNumber = errorMessages.size();

    int y = Integer.parseInt(year.getSelectedItem().toString());
    int d = -1;
    int m = month.getSelectedIndex();
    int[] h = {-1};
    int[] min = {-1};
    checkTime(time, lTime, h, min, errorMessages);
    try
    {
      d = Integer.parseInt(day.getText().trim());
      if ((d < 0) || (d > 31))
      {
        errorMessages.add(ERR_CTRL_PANEL_INVALID_DAY.get());
        setPrimaryInvalid(lDay);
      }
    }
    catch (Exception ex)
    {
      errorMessages.add(ERR_CTRL_PANEL_INVALID_DAY.get());
      setPrimaryInvalid(lDay);
    }

    if (errorMessages.size() == previousErrorNumber)
    {
      GregorianCalendar calendar = new GregorianCalendar(y, m, d, h[0], min[0]);
      Date date = calendar.getTime();
      // Check that the actual date's month date corresponds to a valid day
      // (for instance if user specifies 30th of February, the resulting date
      // is 2nd (or 1st depending of the year) of Mars.
      if (calendar.get(Calendar.MONTH) != m)
      {
        errorMessages.add(ERR_CTRL_PANEL_INVALID_DAY_IN_MONTH.get(d,
            month.getSelectedItem().toString()));
        setPrimaryInvalid(lDay);
        setPrimaryInvalid(lMonth);
      }
      else if (date.before(new Date()))
      {
        errorMessages.add(ERR_CTRL_PANEL_DATE_ALREADY_PASSED.get());
        setPrimaryInvalid(lTime);
        setPrimaryInvalid(lDay);
        setPrimaryInvalid(lMonth);
        setPrimaryInvalid(lYear);
      }
    }
  }

  /**
   * Checks the validity of the launch daily information and updates
   * the provided collection of messages with the errors that have been found.
   * The associated labels are also updated.
   * @param errorMessages the collection of messages to be updated.
   */
  private void updateLaunchDailyErrorMessages(Collection<Message> errorMessages)
  {
    setPrimaryValid(lDailyTime);

    int[] h = {-1};
    int[] min = {-1};
    checkTime(dailyTime, lDailyTime, h, min, errorMessages);
  }

  /**
   * Checks the validity of the launch weekly information and updates
   * the provided collection of messages with the errors that have been found.
   * The associated labels are also updated.
   * @param errorMessages the collection of messages to be updated.
   */
  private void updateLaunchWeeklyErrorMessages(
      Collection<Message> errorMessages)
  {
    setPrimaryValid(lWeeklyTime);
    setPrimaryValid(lWeeklyDays);

    int[] h = {-1};
    int[] min = {-1};
    checkTime(weeklyTime, lWeeklyTime, h, min, errorMessages);

    boolean oneSelected = false;
    for (JCheckBox cb : weekDays)
    {
      if (cb.isSelected())
      {
        oneSelected = true;
        break;
      }
    }
    if (!oneSelected)
    {
      errorMessages.add(ERR_CTRL_PANEL_NO_WEEK_DAY_SELECTED.get());
      setPrimaryInvalid(lWeeklyDays);
    }
  }

  /**
   * Checks the validity of the launch monthly information and updates
   * the provided collection of messages with the errors that have been found.
   * The associated labels are also updated.
   * @param errorMessages the collection of messages to be updated.
   */
  private void updateLaunchMonthlyErrorMessages(
      Collection<Message> errorMessages)
  {
    setPrimaryValid(lMonthlyTime);
    setPrimaryValid(lMonthlyDays);

    int[] h = {-1};
    int[] min = {-1};
    checkTime(monthlyTime, lMonthlyTime, h, min, errorMessages);

    boolean oneSelected = false;
    for (JCheckBox cb : monthDays)
    {
      if (cb.isSelected())
      {
        oneSelected = true;
        break;
      }
    }
    if (!oneSelected)
    {
      errorMessages.add(ERR_CTRL_PANEL_NO_MONTH_DAY_SELECTED.get());
      setPrimaryInvalid(lMonthlyDays);
    }
  }

  /**
   * Checks the validity of the cron schedule information and updates
   * the provided collection of messages with the errors that have been found.
   * The associated labels are also updated.
   * @param errorMessages the collection of messages to be updated.
   */
  private void updateCronErrorMessages(Collection<Message> errorMessages)
  {
    setPrimaryValid(lCronMinute);
    setPrimaryValid(lCronHour);
    setPrimaryValid(lCronMonthDay);
    setPrimaryValid(lCronMonth);
    setPrimaryValid(lCronWeekDay);

    String minute = cronMinute.getText().trim();
    String hour = cronHour.getText().trim();
    String monthDay = cronMonthDay.getText().trim();
    String month = cronMonth.getText().trim();
    String weekDay = cronWeekDay.getText().trim();

    updateCronErrorMessages(minute, lCronMinute,
        ERR_CTRL_PANEL_NO_CRON_MINUTE_PROVIDED.get(),
        ERR_CTRL_PANEL_NOT_VALID_CRON_MINUTE_PROVIDED.get(),
        0, 59,
        errorMessages);
    updateCronErrorMessages(hour, lCronHour,
        ERR_CTRL_PANEL_NO_CRON_HOUR_PROVIDED.get(),
        ERR_CTRL_PANEL_NOT_VALID_CRON_HOUR_PROVIDED.get(),
        0, 23,
        errorMessages);
    updateCronErrorMessages(weekDay, lCronWeekDay,
        ERR_CTRL_PANEL_NO_CRON_WEEK_DAY_PROVIDED.get(),
        ERR_CTRL_PANEL_NOT_VALID_CRON_WEEK_DAY_PROVIDED.get(),
        0, 6,
        errorMessages);
    updateCronErrorMessages(monthDay, lCronMonthDay,
        ERR_CTRL_PANEL_NO_CRON_MONTH_DAY_PROVIDED.get(),
        ERR_CTRL_PANEL_NOT_VALID_CRON_MONTH_DAY_PROVIDED.get(),
        1, 31,
        errorMessages);
    updateCronErrorMessages(month, lCronMonth,
        ERR_CTRL_PANEL_NO_CRON_MONTH_PROVIDED.get(),
        ERR_CTRL_PANEL_NOT_VALID_CRON_MONTH_PROVIDED.get(),
        1, 12,
        errorMessages);
  }

  /**
   * Checks the validity of the cron schedule information tab and updates
   * the provided collection of messages with the errors that have been found.
   * The associated labels are also updated.
   * @param value the value of the cron schedule tab.
   * @param label the label associated with the cron schedule tab.
   * @param errorIfEmpty the message to be displayed if the value tab is empty.
   * @param contentError the message to be displayed if the value tab is not
   * valid.
   * @param minValue the minimum value accepted.
   * @param maxValue the maximum value accepted.
   * @param errorMessages the collection of messages to be updated.
   */
  private void updateCronErrorMessages(String value, JLabel label,
      Message errorIfEmpty, Message contentError, int minValue, int maxValue,
      Collection<Message> errorMessages)
  {
    if (value.length() == 0)
    {
      errorMessages.add(errorIfEmpty);
      setPrimaryInvalid(label);
    }
    else
    {
      try
      {
        RecurringTask.parseTaskTabField(value, minValue, maxValue);
      }
      catch (Exception ex)
      {
        errorMessages.add(contentError);
        setPrimaryInvalid(label);
      }
    }
  }

  /**
   * Returns the schedule type corresponding to the input provided by user.
   * This method assumes that all the date provided by the user has been
   * validated.
   * @return the schedule type corresponding to the input provided by user.
   */
  private ScheduleType createSchedule()
  {
    ScheduleType schedule;
    Object type =
      ((CategorizedComboBoxElement)scheduleType.getSelectedItem()).getValue();
    if (type == LAUNCH_NOW)
    {
      schedule = ScheduleType.createLaunchNow();
    }
    else if (type == LAUNCH_LATER)
    {
      int y = Integer.parseInt(year.getSelectedItem().toString());
      int d = Integer.parseInt(day.getText().trim());
      int m = month.getSelectedIndex();
      String sTime = time.getText().trim();
      int index = sTime.indexOf(':');
      int h = Integer.parseInt(sTime.substring(0, index).trim());
      int min = Integer.parseInt(sTime.substring(index+1).trim());
      GregorianCalendar calendar = new GregorianCalendar(y, m, d, h, min);
      schedule = ScheduleType.createLaunchLater(calendar.getTime());
    }
    else if (type == LAUNCH_DAILY)
    {
      String sTime = dailyTime.getText().trim();
      int index = sTime.indexOf(':');
      int h = Integer.parseInt(sTime.substring(0, index).trim());
      int m = Integer.parseInt(sTime.substring(index+1).trim());
      String cron = m+" "+h+" * * *";
      schedule = ScheduleType.createCron(cron);
    }
    else if (type == LAUNCH_WEEKLY)
    {
      String sTime = weeklyTime.getText().trim();
      int index = sTime.indexOf(':');
      int h = Integer.parseInt(sTime.substring(0, index).trim());
      int m = Integer.parseInt(sTime.substring(index+1).trim());
      StringBuilder sb = new StringBuilder();
      sb.append(m+" "+h+" * * ");

      boolean oneDayAdded = false;
      for (int i=0; i<weekDays.length; i++)
      {
        if (weekDays[i].isSelected())
        {
          if (oneDayAdded)
          {
            sb.append(',');
          }
          sb.append(i);
          oneDayAdded = true;
        }
      }
      schedule = ScheduleType.createCron(sb.toString());
    }
    else if (type == LAUNCH_MONTHLY)
    {
      String sTime = monthlyTime.getText().trim();
      int index = sTime.indexOf(':');
      int h = Integer.parseInt(sTime.substring(0, index).trim());
      int m = Integer.parseInt(sTime.substring(index+1).trim());
      StringBuilder sb = new StringBuilder();
      sb.append(m+" "+h+" ");
      boolean oneDayAdded = false;
      for (int i=0; i<monthDays.length; i++)
      {
        if (monthDays[i].isSelected())
        {
          if (oneDayAdded)
          {
            sb.append(',');
          }
          sb.append(i+1);
          oneDayAdded = true;
        }
      }
      sb.append(" * *");
      schedule = ScheduleType.createCron(sb.toString());
    }
    else if (type == CRON)
    {
      String cron = cronMinute.getText().trim() + " "+
      cronHour.getText().trim() + " "+
      cronMonthDay.getText().trim() + " "+
      cronMonth.getText().trim() + " "+
      cronWeekDay.getText().trim();
      schedule = ScheduleType.createCron(cron);
    }
    else
    {
      throw new RuntimeException("Unknown schedule type: "+type);
    }
    return schedule;
  }

  /**
   * Convenience method to retrieve the time specified by the user.
   * @param time the text field where the user specifies time.
   * @param lTime the label associated with the text field.
   * @param h an integer array of size 1 where the value of the hour specified
   * by the user will be set.
   * @param m an integer array of size 1 where the value of the minute specified
   * by the user will be set.
   * @param errorMessages the collection of error messages that will be updated
   * with the encountered problems.
   */
  private void checkTime(JTextField time, JLabel lTime, int[] h, int[] m,
      Collection<Message> errorMessages)
  {
    String sTime = time.getText().trim();
    int index = sTime.indexOf(':');
    try
    {
      h[0] = Integer.parseInt(sTime.substring(0, index).trim());
      m[0] = Integer.parseInt(sTime.substring(index+1).trim());
      if (h[0] < 0 || h[0] > 23)
      {
        errorMessages.add(ERR_CTRL_PANEL_INVALID_HOUR.get());
        setPrimaryInvalid(lTime);
      }
      if (m[0] < 0 || m[0] > 59)
      {
        errorMessages.add(ERR_CTRL_PANEL_INVALID_MINUTE.get());
        setPrimaryInvalid(lTime);
      }
    }
    catch (Exception ex)
    {
      errorMessages.add(ERR_CTRL_PANEL_INVALID_TIME.get());
      setPrimaryInvalid(lTime);
    }
  }

  /**
   * Tells whether the user chose to close the dialog discarding the provided
   * input.
   * @return <CODE>true</CODE> if the user chose to close the dialog discarding
   * the provided input and <CODE>false</CODE> otherwise.
   */
  public boolean isCanceled()
  {
    return schedule == null;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return scheduleType;
  }

  /**
   * Returns the schedule provided by the user.
   * @return the schedule provided by the user.
   */
  public ScheduleType getSchedule()
  {
    return schedule;
  }

  private Component createLaunchLaterPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    Calendar calendar = Calendar.getInstance();

    int currentYear = calendar.get(Calendar.YEAR);
    int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
    int currentMinute = calendar.get(Calendar.MINUTE);
    int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
    int currentMonth = calendar.get(Calendar.MONTH);

    time = Utilities.createShortTextField();
    PlainDocument plainTextDocument = new PlainDocument();
    time.setDocument(plainTextDocument);
    String sHour = currentHour >= 10 ?
        String.valueOf(currentHour) : "0"+currentHour;
    String sMinute = currentMinute >= 10 ?
        String.valueOf(currentMinute) : "0"+currentMinute;
    time.setText(sHour+":"+sMinute);
    plainTextDocument.setDocumentFilter(new TimeDocumentFilter(time));


    day = Utilities.createShortTextField();
    day.setColumns(4);
    plainTextDocument = new PlainDocument();
    day.setDocument(plainTextDocument);
    day.setText(String.valueOf(currentDay));
    plainTextDocument.setDocumentFilter(
        new NumericLimitedSizeDocumentFilter(day, 2));
    month = Utilities.createComboBox();
    year = Utilities.createComboBox();

    int[][] maxMin =
    {
        {currentYear, currentYear + 5}
    };

    JComboBox[] numericBoxes =
    {
        year
    };

    int[] currentValues =
    {
        currentYear
    };

    for (int i=0; i<maxMin.length; i++)
    {
      int min = maxMin[i][0];
      int max = maxMin[i][1];

      DefaultComboBoxModel model = new DefaultComboBoxModel();

      int selectedIndex = 0;

      int index = 0;
      for (int j=min; j<=max; j++)
      {
        String s;
        if (j < 10)
        {
          s = "0"+j;
        }
        else
        {
          s = String.valueOf(j);
        }
        model.addElement(s);

        if (j == currentValues[i])
        {
          selectedIndex= index;
        }
        index++;
      }

      numericBoxes[i].setModel(model);

      if (selectedIndex != 0)
      {
        numericBoxes[i].setSelectedIndex(selectedIndex);
      }
    }

    DefaultComboBoxModel model = new DefaultComboBoxModel();
    month.setModel(model);

    Message[] monthMessages =
    {
        INFO_CTRL_PANEL_JANUARY.get(),
        INFO_CTRL_PANEL_FEBRUARY.get(),
        INFO_CTRL_PANEL_MARCH.get(),
        INFO_CTRL_PANEL_APRIL.get(),
        INFO_CTRL_PANEL_MAY.get(),
        INFO_CTRL_PANEL_JUNE.get(),
        INFO_CTRL_PANEL_JULY.get(),
        INFO_CTRL_PANEL_AUGUST.get(),
        INFO_CTRL_PANEL_SEPTEMBER.get(),
        INFO_CTRL_PANEL_OCTOBER.get(),
        INFO_CTRL_PANEL_NOVEMBER.get(),
        INFO_CTRL_PANEL_DECEMBER.get(),
    };
    for (Message msg : monthMessages)
    {
      model.addElement(msg.toString());
    }

    month.setSelectedIndex(currentMonth);

    lTime = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME.get());
    lDay = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_DAY.get());
    lMonth = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_MONTH.get());
    lYear = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_YEAR.get());

    gbc.gridy = 0;

    JLabel[] labels = {lTime, lDay, lMonth, lYear};
    JComponent[] comps = {time, day, month, year};
    Message[] inlineHelp =
    {
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME_TOOLTIP.get(),
        null,
        null,
        null
    };

    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    for (int i=0; i<labels.length; i++)
    {
      gbc.gridx = 0;
      gbc.weightx = 0.0;
      gbc.gridwidth = 1;

      panel.add(labels[i], gbc);
      gbc.gridx = 1;
      gbc.insets.left = 10;
      panel.add(comps[i], gbc);
      gbc.gridx = 2;
      gbc.weightx = 1.0;
      gbc.insets.left = 0;
      panel.add(Box.createHorizontalGlue(), gbc);

      if (inlineHelp[i] != null)
      {
        gbc.gridwidth = 2;
        gbc.insets.top = 3;
        gbc.insets.left = 10;
        gbc.gridx = 1;
        gbc.gridy ++;
        panel.add(Utilities.createInlineHelpLabel(inlineHelp[i]), gbc);
      }

      gbc.insets.top = 10;
      gbc.gridy ++;
    }

    gbc.insets.top = 0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    panel.add(Box.createVerticalGlue(), gbc);

    return panel;
  }

  private Component createDailyPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;

    lDailyTime =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME.get());

    dailyTime = Utilities.createShortTextField();
    PlainDocument plainTextDocument = new PlainDocument();
    dailyTime.setDocument(plainTextDocument);
    dailyTime.setColumns(4);
    dailyTime.setText("00:00");
    plainTextDocument.setDocumentFilter(new TimeDocumentFilter(dailyTime));

    panel.add(lDailyTime, gbc);
    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.NONE;
    panel.add(dailyTime, gbc);
    gbc.gridx = 2;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(Box.createHorizontalGlue(), gbc);

    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.insets.top = 3;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    panel.add(Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME_TOOLTIP.get()), gbc);

    return panel;
  }

  private Component createWeeklyPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;

    lWeeklyTime =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME.get());

    weeklyTime = Utilities.createShortTextField();
    PlainDocument plainTextDocument = new PlainDocument();
    weeklyTime.setDocument(plainTextDocument);
    weeklyTime.setColumns(4);
    weeklyTime.setText("00:00");
    plainTextDocument.setDocumentFilter(new TimeDocumentFilter(weeklyTime));

    lWeeklyDays = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_DAYS.get());
    for (JCheckBox cb : weekDays)
    {
      cb.setFont(ColorAndFontConstants.inlineHelpFont);
    }
    sunday.setSelected(true);
    wednesday.setSelected(true);

    gbc.anchor = GridBagConstraints.WEST;
    panel.add(lWeeklyTime, gbc);
    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.gridwidth = weekDays.length;
    gbc.fill = GridBagConstraints.NONE;
    panel.add(weeklyTime, gbc);
    gbc.gridx = 2;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridy ++;
    gbc.gridwidth = weekDays.length + 1;
    gbc.insets.top = 3;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    panel.add(Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME_TOOLTIP.get()), gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.top = 10;
    gbc.weightx = 1.0;
    panel.add(lWeeklyDays, gbc);
    gbc.insets.left = 10;
    gbc.gridwidth = 1;
    for (JCheckBox cb : weekDays)
    {
      gbc.gridx ++;
      panel.add(cb, gbc);
    }
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    gbc.gridx ++;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  private Component createMonthlyPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;

    lMonthlyTime =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME.get());

    monthlyTime = Utilities.createShortTextField();
    PlainDocument plainTextDocument = new PlainDocument();
    monthlyTime.setDocument(plainTextDocument);
    monthlyTime.setColumns(4);
    monthlyTime.setText("00:00");
    plainTextDocument.setDocumentFilter(new TimeDocumentFilter(monthlyTime));

    lMonthlyDays = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_DAYS.get());

    gbc.anchor = GridBagConstraints.WEST;
    panel.add(lMonthlyTime, gbc);
    gbc.gridx = 1;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = 7;
    panel.add(monthlyTime, gbc);
    gbc.gridx = 2;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridy ++;
    gbc.gridwidth = 8;
    gbc.insets.top = 3;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    panel.add(Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_TIME_TOOLTIP.get()), gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.top = 10;
    gbc.weightx = 1.0;
    gbc.gridwidth = 1;
    panel.add(lMonthlyDays, gbc);
    gbc.insets.left = 10;
    gbc.gridwidth = 1;
    for (int i=0 ; i<monthDays.length; i++)
    {
      monthDays[i] = Utilities.createCheckBox(Message.raw(String.valueOf(i+1)));
      monthDays[i].setFont(ColorAndFontConstants.inlineHelpFont);
      int x = i % 7;
      if (x == 0 && i != 0)
      {
        gbc.gridy ++;
        gbc.insets.top = 5;
      }
      if (x != 0)
      {
        gbc.insets.left = 5;
      }
      else
      {
        gbc.insets.left = 10;
      }
      gbc.gridx = x + 1;
      panel.add(monthDays[i], gbc);
    }
    monthDays[0].setSelected(true);
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    gbc.gridx ++;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  private Component createCronPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();

    JEditorPane explanation = Utilities.makeHtmlPane(
        INFO_CTRL_PANEL_CRON_HELP.get().toString(),
        ColorAndFontConstants.inlineHelpFont);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(explanation, gbc);
    gbc.gridy ++;
    gbc.insets.top = 10;

    gbc.gridwidth = 1;
    lCronMinute = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_CRON_MINUTE.get());
    lCronHour = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_CRON_HOUR.get());
    lCronWeekDay = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_CRON_WEEK_DAY.get());
    lCronMonthDay = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_CRON_MONTH_DAY.get());
    lCronMonth = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_TASK_TO_SCHEDULE_CRON_MONTH.get());

    cronMinute = Utilities.createShortTextField();
    cronMinute.setText("*");

    cronHour = Utilities.createShortTextField();
    cronHour.setText("*");

    cronWeekDay = Utilities.createShortTextField();
    cronWeekDay.setText("*");

    cronMonthDay = Utilities.createShortTextField();
    cronMonthDay.setText("*");

    cronMonth = Utilities.createShortTextField();
    cronMonth.setText("*");

    JLabel[] labels = {lCronMinute, lCronHour, lCronWeekDay, lCronMonthDay,
        lCronMonth};
    Component[] comps = {cronMinute, cronHour, cronWeekDay, cronMonthDay,
        cronMonth};
    Message[] help =
    {
      INFO_CTRL_PANEL_CRON_MINUTE_HELP.get(),
      INFO_CTRL_PANEL_CRON_HOUR_HELP.get(),
      INFO_CTRL_PANEL_CRON_WEEK_DAY_HELP.get(),
      INFO_CTRL_PANEL_CRON_MONTH_DAY_HELP.get(),
      INFO_CTRL_PANEL_CRON_MONTH_HELP.get(),
    };

    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    for (int i=0; i<labels.length; i++)
    {
      gbc.gridx = 0;
      gbc.weightx = 0.0;

      gbc.insets.left = 0;
      panel.add(labels[i], gbc);
      gbc.gridx = 1;
      gbc.insets.left = 10;
      panel.add(comps[i], gbc);
      gbc.gridx = 2;
      gbc.weightx = 1.0;
      gbc.insets.left = 0;
      panel.add(Box.createHorizontalGlue(), gbc);
      if (help[i] != null)
      {
        gbc.insets.top = 3;
        gbc.insets.left = 10;
        gbc.gridy ++;
        gbc.gridx = 1;
        panel.add(Utilities.createInlineHelpLabel(help[i]), gbc);
      }

      gbc.insets.top = 10;
      gbc.gridy ++;
    }

    gbc.insets.top = 0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    panel.add(Box.createVerticalGlue(), gbc);

    return panel;
  }

  /**
   * The main method to test this panel.
   * @param args the arguments.
   */
  public static void main(String[] args)
  {
    while (true)
    {
      TaskToSchedulePanel p = new TaskToSchedulePanel("TEST TASK");
      GenericDialog dlg = new GenericDialog(Utilities.createFrame(), p);
      dlg.setModal(true);
      dlg.setVisible(true);
    }
  }
}
