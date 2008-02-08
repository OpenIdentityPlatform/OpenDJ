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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.util;
import org.opends.messages.Message;
import org.opends.quicksetup.BuildInformation;

import static org.opends.messages.VersionMessages.*;

import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.Collection;

/**
 * Record for version compatibility issues (also known as 'flag days') which
 * are events associated with particular builds or builds between which upgrade
 * or reversion may required additional steps, notification of issues, or
 * be prohibitted altogether.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class VersionCompatibilityIssue {

  //***************************************************
  //
  //  TO DEFINE A NEW ISSUE:
  //
  //  Step 1:  Select (or add to) effects from the list
  //           below that will cause the upgrade or
  //           reversion tools to behave in particular
  //           ways.  If you add to this list you will
  //           likely need to update the UpgradeOracle
  //           and ReversionOracle code.
  //
  //  Step 2:  [scroll down]...
  //
  //***************************************************

  /**
   * Effects cause the upgrade and revision tools to behave
   * in specific ways in response to compatibility issues.
   */
  public enum Effect {

    /**
     * Before a reversion can take place there must be a complete
     * data export to LDIF followed by a complete data import after
     * the operation has completed.  Assigning this effect to an
     * issue will cause a detailed set of instructions to appear in
     * the reversion tool explaining how to perform the task.
     */
    REVERSION_DATA_EXPORT_AND_REIMPORT_REQUIRED,

    /**
     * Before an upgrade can take place there must be a complete
     * data export to LDIF followed by a complete data import after
     * the operation has completed.  Assigning this effect to an
     * issue will cause a detailed set of instructions to appear in
     * the upgrade tool explaining how to perform the task.
     */
    UPGRADE_DATA_EXPORT_AND_REIMPORT_REQUIRED,

    /**
     * Indicates that the upgrader will show an informational message to the
     * administrator.  Use this effect when you want to have the
     * upgrader show the user an informational message during upgrade
     * but the message does not dictate that an action be performed.
     * For instance you might want to let the user know that due to
     * a data format incompatibility, it will be more difficult to
     * revert this build to its previous version following this upgrade.
     *
     * If you want the message to be scarier, use
     * <code>UPGRADE_SHOW_WARNING_MESSAGE</code> instead.
     */
    UPGRADE_SHOW_INFO_MESSAGE,

    /**
     * Indicates that the reverter tool will show a message to the
     * administrator.  Use this effect when you want to have the
     * reverter show the user an informational message during upgrade
     * but the message does not dictate that an action be performed.
     *
     * If you want the message to be scarier, use
     * <code>REVERSION_SHOW_WARNING_MESSAGE</code> instead.
     */
    REVERSION_SHOW_INFO_MESSAGE,

    /**
     * Indicates that the upgrader will show a message to the
     * administrator.  Use this effect when you want to have the
     * upgrader show the user an informational message during upgrade
     * but the message does not dictate that an action be performed.
     * For instance you might want to let the user know that due to
     * a data format incompatibility, it will be more difficult to
     * revert this build to its previous version following this upgrade.
     *
     * If you want the message to be less scary, use
     * <code>UPGRADE_SHOW_INFO_MESSAGE</code> instead.
     */
    UPGRADE_SHOW_WARNING_MESSAGE,

    /**
     * Indicates that the reverter tool will show a message to the
     * administrator.  Use this effect when you want to have the
     * reverter show the user an informational message during upgrade
     * but the message does not dictate that an action be performed.
     *
     * If you want the message to be less scary, use
     * <code>REVERSION_SHOW_INFO_MESSAGE</code> instead.
     */
    REVERSION_SHOW_WARNING_MESSAGE,

    /**
     * Indicates that the user needs to perform some manual action
     * (for which there is not effect currently defined such as
     * <code>UPGRADE_DATA_EXPORT_AND_REIMPORT_REQUIRED</code>) in order for
     * the operation to be successful.  The action itself should
     * be described in detail in the upgrade message.
     */
    UPGRADE_MANUAL_ACTION_REQUIRED,

    /**
     * Indicates that the user needs to perform some manual action
     * (for which there is not effect currently defined such as
     * <code>REVERSION_DATA_EXPORT_AND_REIMPORT_REQUIRED</code>) in order for
     * the operation to be successful.  The action itself should
     * be described in detail in the reversion message.
     */
    REVERSION_MANUAL_ACTION_REQUIRED,

    /**
     * Indicates that it is not possible to upgrade between to builds
     * between which lies a flag day.  The upgrader will refuse to
     * operate in this case.
     */
    UPGRADE_NOT_POSSIBLE,

    /**
     * Indicates that it is not possible to revert between to builds
     * between which lies a flag day.  The reverter will refuse to run
     * in this case.
     */
    REVERSION_NOT_POSSIBLE,

    /**
     * Indicates that for some reason the server should not be restarted
     * following a reversion.  There might be situations where the admin
     * needs to perform some actions before the server restarts (such as
     * the database format being incompatible and the data needing an
     * export followed by a reimport).  This effect need not be included
     * with <code>UPGRADE_DATA_EXPORT_AND_REIMPORT_REQUIRED</code> and
     * <code>REVERSION_DATA_EXPORT_AND_REIMPORT_REQUIRED</code> as this
     * is assumed.
     */
    NO_SERVER_RESTART_FOLLOWING_REVERSION,

  }

  //***************************************************
  //
  //  TO DEFINE A NEW ISSUE:
  //
  // STEP 1:  [scroll up]
  //
  // STEP 2:  Define an cause below.  A cause must be a specific
  //          event.  For instance 'upgrade of the database libraries'
  //          on 12/17/2006.  A cause associates the effect you selected
  //          in Step 1, detailed reversion and/or upgrade messages and
  //          a unique ID.
  //
  //          A single issue may be apply to multiple branches of the
  //          codebase.  For instance a single event might cause a flag
  //          day between upgrade/reversions from 1.0 to 2.0 as well as
  //          upgrading from 1.0 to 1.1.  Therefore you must make sure
  //          that causes that appear in multiple branches have the same
  //          ID.  Also, IDs should be unique among all causes in the
  //          codebase.
  //
  // STEP 3:  [scroll down]
  //
  //***************************************************

  /**
   * Unique descriptor of an event that created a flag day for one
   * or more versions of the OpenDS codebase.
   */
  public enum Cause {
    /**
     * Incompatible changes in the backend configuration (the db directory
     * attribute has been modified).
     */
    BACKEND_CONFIGURATION_CHANGE_1(
        6, // Unique ID.  See javadoc for more information.
        INFO_3708_UPGRADE.get(),
        INFO_3708_REVERSION.get(),
        Effect.REVERSION_NOT_POSSIBLE,
        Effect.UPGRADE_NOT_POSSIBLE),

    /**
     * Incompatible changes in the cryptomanager and specially in the way
     * replication works.  These changes were committed on several revisions
     * and the flagday that has been chosen corresponds to revision 3294
     * (opends 1.0.0 build 6 of 16/10/2007)
     */
    REPLICATION_SECURITY_CHANGE_1(
            5, // Unique ID.  See javadoc for more information.
            INFO_3294_UPGRADE.get(),
            INFO_3294_REVERSION.get(),
            Effect.REVERSION_NOT_POSSIBLE,
            Effect.UPGRADE_NOT_POSSIBLE),

    /**
     * Incompatible property name change committed on 09/05/2007
     * and described in the SVN log for rev 2974.
     */
    PROPERTY_CHANGE_1(
            4, // Unique ID.  See javadoc for more information.
            INFO_2974_UPGRADE.get(),
            INFO_2974_REVERSION.get(),
            Effect.REVERSION_NOT_POSSIBLE,
            Effect.UPGRADE_NOT_POSSIBLE),

    /**
     * Database format change committed on 6/7/2007
     * and described in the SVN log for rev 2049.
     */
    DB_FORMAT_CHANGE_2(
            3, // Unique ID.  See javadoc for more information.
            INFO_2049_UPGRADE.get(),
            INFO_2049_REVERSION.get(),
            Effect.REVERSION_DATA_EXPORT_AND_REIMPORT_REQUIRED,
            Effect.UPGRADE_SHOW_WARNING_MESSAGE),

    /**
     * Database format change committed on 4/6/2007
     * and described in the SVN log for rev 1582.
     */
    DB_FORMAT_CHANGE_1(
            2,  // Unique ID.  See javadoc for more information.
            INFO_1582_UPGRADE.get(),
            INFO_1582_REVERSION.get(),
            Effect.REVERSION_DATA_EXPORT_AND_REIMPORT_REQUIRED,
            Effect.UPGRADE_SHOW_WARNING_MESSAGE),

    /**
     * Upgrade of Berkley DB library to 3.2.13 on
     * 12/17/2006.
     */
    BERKLEY_UPGRADE_1(
            1,  // Unique ID.  See javadoc for more information.
            INFO_890_UPGRADE.get(),
            INFO_890_REVERSION.get(),
            Effect.REVERSION_DATA_EXPORT_AND_REIMPORT_REQUIRED,
            Effect.UPGRADE_SHOW_WARNING_MESSAGE);

    /**
     * Gets a <code>Cause</code> from its unique ID.  If no cause
     * is associated with <code>id</code> this method returns null.
     * @param id of a cause
     * @return Cause with <code>id</code>
     */
    static Cause fromId(int id) {
      Cause cause = null;
      EnumSet<Cause> es = EnumSet.allOf(Cause.class);
      for (Cause c : es) {
        if (c.getId() == id) {
          cause = c;
          break;
        }
      }
      return cause;
    }

    private int id;
    private Set<Effect> effects = new HashSet<Effect>();
    private Message upgradeMsg;
    private Message reversionMsg;

    /**
     * Creates a parameterized instance.
     *
     * @param id of this cause.  It would get very complicated to try to
     *        deal with releases as a graph and attempting to compare
     *        versions to see what issues apply during an upgrade/reversion
     *        between two releases.  Therefore IDs are used by the tools
     *        to identify issues would have already been seen during a previous
     *        upgrade and do not need to be rehashed.
     *        <p>
     *        So if an issue exists in the 1.0 branch, an upgrade from 2.0
     *        to 3.0 will suppress the issue since it would presumably already
     *        been dealt with when 2.0 was installed or upgraded to.  Likewise
     *        if an issue is assocated with a particular minor version (1.1 for
     *        instance) major upgrades (1.0 to 2.0) will avoid presenting the
     *        issue.
     *
     *        <ol>
     *        <li>IDs must be unique among different causes in all branches
     *        of the OpenDS code.</li>
     *
     *        <li>Causes in different branches representing the same issue
     *        must have identical IDs.</li>
     *
     *        <li>The IDs are advertised by the server when start-ds -F
     *        is invoked.  Therefore they should be kept to as few
     *        characters as possible.</li>
     *        </ol>
     *
     * @param upgradeMessage a message to be shown to the user during an
     *        upgrade between two different version between which this issue
     *        lies.  This message might detail instructions for manual actions
     *        that must be performed (when used with the
     *        <code>UPGRADE_MANUAL_ACTION_REQUIRED</code>) or give the
     *        user a warning message (when used with
     *        <code>UPGRADE_SHOW_WARNING_MESSAGE</code>).  If a message is
     *        present but no effects that would dictate how message is to
     *        be presented <code>UPGRADE_SHOW_INFO_MESSAGE</code> is
     *        assumed.  This parameter may also be null in which case no
     *        action will be taken during upgrade.
     *
     * @param reversionMessage a message to be shown to the user during a
     *        reversion between two different version between which this issue
     *        lies.  This message might detail instructions for manual actions
     *        that must be performed (when used with the
     *        <code>REVERSION_MANUAL_ACTION_REQUIRED</code>) or give the
     *        user a warning message (when used with
     *        <code>REVERSION_SHOW_WARNING_MESSAGE</code>).  If a message is
     *        present but no effects that would dictate how message is to
     *        be presented <code>REVERSION_SHOW_INFO_MESSAGE</code> is
     *        assumed.  This parameter may also be null in which case no
     *        action will be taken during reversion.
     *
     * @param effects of this cause which cause the upgrade/reversion tools
     *        to behave in particular ways
     */
    private Cause(int id, Message upgradeMessage, Message reversionMessage,
          Effect... effects) {
      this.id = id;
      this.upgradeMsg = upgradeMessage;
      this.reversionMsg = reversionMessage;
      if (effects != null) {
        for (Effect c : effects) {
          this.effects.add(c);
        }
      }
    }

    /**
     * Gets the ID of this cause.
     * @return id of this cause
     */
    public int getId() {
      return this.id;
    }

    /**
     * Gets the set of effects that cause the upgrade/reversion
     * tools to behave in particular ways.
     *
     * @return set of effects
     */
    public Set<Effect> getEffects() {
      return Collections.unmodifiableSet(effects);
    }

    /**
     * Gets a localized message to be shown to the user during
     * the upgrade process.
     *
     * @return a message to be shown to the user during an
     *         upgrade between two different version between which this issue
     *         lies.  This message might detail instructions for manual actions
     *         that must be performed (when used with the
     *         <code>UPGRADE_MANUAL_ACTION_REQUIRED</code>) or just give the
     *         user useful information (when used with
     *         <code>UPGRADE_SHOW_INFO_MESSAGE</code>)
     */
    public Message getLocalizedUpgradeMessage() {
      return upgradeMsg;
    }

    /**
     * Gets a localized message to be shown to the user during
     * the reversion process.
     *
     * @return a message to be shown to the user during an
     *         upgrade between two different version between which this issue
     *         lies.  This message might detail instructions for manual actions
     *         that must be performed (when used with the
     *         <code>REVERSION_MANUAL_ACTION_REQUIRED</code>) or just give the
     *         user useful information (when used with
     *         <code>REVERSION_SHOW_INFO_MESSAGE</code>)
     */
    public Message getLocalizedReversionMessage() {
      return reversionMsg;
    }

  }

  /**
   * Container for registered issues.
   */
  static private final Set<VersionCompatibilityIssue>
          VERSION_COMPATIBILITY_ISSUES =
          new HashSet<VersionCompatibilityIssue>();

  //***************************************************
  //
  //  TO DEFINE A NEW ISSUE:
  //
  // STEP 2:  [scroll up]
  //
  // STEP 3:  Associate the cause with a particular build.
  //
  // DONE
  //
  //***************************************************

  static {
    //
    register(Cause.BACKEND_CONFIGURATION_CHANGE_1,
        new BuildVersion(1, 0, 0, 3708));
    register(Cause.REPLICATION_SECURITY_CHANGE_1,
        new BuildVersion(1, 0, 0, 3294));
    register(Cause.PROPERTY_CHANGE_1, new BuildVersion(1, 0, 0, 3053));
    register(Cause.DB_FORMAT_CHANGE_2, new BuildVersion(0, 9, 0, 2049));
    register(Cause.DB_FORMAT_CHANGE_1, new BuildVersion(0, 1, 0, 1582));
    register(Cause.BERKLEY_UPGRADE_1, new BuildVersion(0, 1, 0, 890));
  }

  static private void register(Cause cause,
                               BuildVersion version) {
    VERSION_COMPATIBILITY_ISSUES.add(new VersionCompatibilityIssue(cause,
            version));
  }

  /**
   * Gets the list of all registered issues.
   *
   * @return list of issues sorted by build version in which
   *         they appear
   */
  static public List<VersionCompatibilityIssue> getAllEvents() {
    List<VersionCompatibilityIssue> issueList =
            new ArrayList<VersionCompatibilityIssue>
                    (VERSION_COMPATIBILITY_ISSUES);
    Collections.sort(issueList, VERSION_COMPARATOR);
    return Collections.unmodifiableList(issueList);
  }

  /**
   * Gets the list of all registered issues excluding the
   * issues specified by <code>excludeIds</code>.
   *
   * @param excludeIds collection of IDs representing issues
   *        that will not be returned in the list
   * @param current build version
   * @param neu build version
   *
   * @return list of issues sorted by build version in which
   *         they appear
   */
  static public List<VersionCompatibilityIssue> getEvents(
          Collection<Integer> excludeIds, BuildInformation current,
          BuildInformation neu)
  {
    if (excludeIds == null) excludeIds = Collections.emptySet();
    List<VersionCompatibilityIssue> issueList =
            new ArrayList<VersionCompatibilityIssue>();
    for (VersionCompatibilityIssue evt : VERSION_COMPATIBILITY_ISSUES) {
      if (!excludeIds.contains(evt.getCause().getId())) {
        boolean isUpgrade = neu.compareTo(current) >= 0;
        BuildVersion newVersion = new BuildVersion(neu.getMajorVersion(),
            neu.getMinorVersion(), neu.getPointVersion(),
            neu.getRevisionNumber());
        BuildVersion currentVersion = new BuildVersion(
            current.getMajorVersion(), current.getMinorVersion(),
            current.getPointVersion(), current.getRevisionNumber());
        if (isUpgrade)
        {
          // If the currentVersion is newer than the issue described, then there
          // is no problem.  This can occur for instance when we discovered a
          // flag day too late (and we added the flag day description to the
          // code way after the revision).
          if (currentVersion.compareTo(evt.getVersion()) < 0)
          {
            issueList.add(evt);
          }
        }
        else
        {
          // If the newVersion in the reversion is newer than the issue
          // described, then there is no problem.  This can occur for instance
          // when we discovered a flag day too late (and we added the flag day
          // description to the code way after the revision).
          if (currentVersion.compareTo(evt.getVersion()) < 0)
          {
            issueList.add(evt);
          }
        }
      }
    }
    Collections.sort(issueList, VERSION_COMPARATOR);
    return Collections.unmodifiableList(issueList);
  }

  /**
   * Returns events that have happened in between the SVN revision numbers
   * of two different builds.  Note that this method does not necessarily
   * return all events that are pertinent.  For instance a partilar event
   * may have happend in a branch that we don't care about for the current
   * upgrade.  So this method should really just be used as a fall-back
   * in the case where we are upgrading/reverting a build that was not
   * instrumented to return the Upgrade Event IDs using start-ds -F.
   *
   * @param from build from which events will be returned
   * @return List or IncompatibleVersionEvent objects
   */
  static public List<VersionCompatibilityIssue> getEvents(BuildVersion from) {
    List<VersionCompatibilityIssue> issueList =
            new ArrayList<VersionCompatibilityIssue>();
    for (VersionCompatibilityIssue evt : VERSION_COMPATIBILITY_ISSUES) {
      BuildVersion evtVer = evt.getVersion();
      if (evtVer.compareTo(from) >= 0) {
        issueList.add(evt);
      }
    }
    Collections.sort(issueList, VERSION_COMPARATOR);
    return issueList;
  }

  /**
   * Comparator used to sort issues by the build version for
   * which they apply.
   */
  static private final Comparator<VersionCompatibilityIssue>
          VERSION_COMPARATOR = new Comparator<VersionCompatibilityIssue>()
  {
    public int compare(VersionCompatibilityIssue o1,
                       VersionCompatibilityIssue o2) {
      return o1.getVersion().compareTo(o2.getVersion());
    }
  };

  private Cause cause;
  private BuildVersion version;

  private VersionCompatibilityIssue(Cause cause, BuildVersion version) {
    this.cause = cause;
    this.version = version;
  }

  /**
   * Gets the cause of this issue.
   * @return the cause
   */
  public Cause getCause() {
    return this.cause;
  }

  /**
   * Gets the build version for which this issue applies.
   * @return build version
   */
  public BuildVersion getVersion() {
    return this.version;
  }

  /**
   * Retrieves a string representation of this version compatibility issue.
   *
   * @return  A string representation of this version compatibility issue.
   */
  public String toString() {
    return Integer.toString(cause.getId());
  }

}
