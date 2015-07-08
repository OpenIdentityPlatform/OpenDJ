/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class implements an enumeration that defines the set of
 * privileges available in the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public enum Privilege
{
  /**
   * The privilege that provides the ability to bypass access control
   * evaluation.
   */
  BYPASS_ACL("bypass-acl"),



  /**
   * The privilege that provides the ability to bypass server
   * lockdown mode.
   */
  BYPASS_LOCKDOWN("bypass-lockdown"),



  /**
   * The privilege that provides the ability to modify access control
   * rules.
   */
  MODIFY_ACL("modify-acl"),



  /**
   * The privilege that provides the ability to read the server
   * configuration.
   */
  CONFIG_READ("config-read"),



  /**
   * The privilege that provides the ability to update the server
   * configuration.
   */
  CONFIG_WRITE("config-write"),



  /**
   * The privilege that provides the ability to perform read
   * operations via JMX.
   */
  JMX_READ("jmx-read"),



  /**
   * The privilege that provides the ability to perform write
   * operations via JMX.
   */
  JMX_WRITE("jmx-write"),



  /**
   * The privilege that provides the ability to subscribe to JMX
   * notifications.
   */
  JMX_NOTIFY("jmx-notify"),



  /**
   * The privilege that provides the ability to perform LDIF import
   * operations.
   */
  LDIF_IMPORT("ldif-import"),



  /**
   * The privilege that provides the ability to perform LDIF export
   * operations.
   */
  LDIF_EXPORT("ldif-export"),



  /**
   * The privilege that provides the ability to perform backend backup
   * operations.
   */
  BACKEND_BACKUP("backend-backup"),



  /**
   * The privilege that provides the ability to perform backend
   * restore operations.
   */
  BACKEND_RESTORE("backend-restore"),



  /**
   * The privilege that provides the ability to lockdown a server.
   */
  SERVER_LOCKDOWN("server-lockdown"),



  /**
   * The privilege that provides the ability to request a server
   * shutdown.
   */
  SERVER_SHUTDOWN("server-shutdown"),



  /**
   * The privilege that provides the ability to request a server
   * restart.
   */
  SERVER_RESTART("server-restart"),



  /**
   * The privilege that provides the ability to perform proxied
   * authorization or request an alternate authorization identity.
   */
  PROXIED_AUTH("proxied-auth"),



  /**
   * The privilege that provides the ability to terminate arbitrary
   * client connections.
   */
  DISCONNECT_CLIENT("disconnect-client"),



  /**
   * The privilege that provides the ability to cancel arbitrary
   * client requests.
   */
  CANCEL_REQUEST("cancel-request"),



  /**
   * The privilege that provides the ability to reset user passwords.
   */
  PASSWORD_RESET("password-reset"),



  /**
   * The privilege that provides the ability to participate in a
   * data synchronization environment.
   */
  DATA_SYNC("data-sync"),



  /**
   * The privilege that provides the ability to update the server
   * schema.
   */
  UPDATE_SCHEMA("update-schema"),



  /**
   * The privilege that provides the ability to change the set of
   * privileges for a user, or to change the set of privileges
   * automatically assigned to a root user.
   */
  PRIVILEGE_CHANGE("privilege-change"),



  /**
   * The privilege that provides the ability to perform an unindexed
   * search in the JE backend.
   */
  UNINDEXED_SEARCH("unindexed-search"),



  /**
   * The privilege that provides the ability to perform write
   * operations on LDAP subentries.
   */
  SUBENTRY_WRITE("subentry-write"),



  /**
   * The privilege that provides the ability to perform read
   * operations on the changelog.
   */
  CHANGELOG_READ("changelog-read");


  /** A map that will be used to hold a mapping between privilege names and enum values. */
  private static final Map<String, Privilege> PRIV_MAP = new HashMap<>();

  /**
   * The set of privileges that will be automatically assigned to root
   * users if the root privilege set is not specified in the configuration.
   */
  private static final Set<Privilege> DEFAULT_ROOT_PRIV_SET = new HashSet<>();


  /** The human-readable name for this privilege. */
  private final String privilegeName;



  static
  {
    for (Privilege privilege : Privilege.values())
    {
      PRIV_MAP.put(privilege.privilegeName, privilege);
    }

    DEFAULT_ROOT_PRIV_SET.add(BYPASS_ACL);
    DEFAULT_ROOT_PRIV_SET.add(BYPASS_LOCKDOWN);
    DEFAULT_ROOT_PRIV_SET.add(MODIFY_ACL);
    DEFAULT_ROOT_PRIV_SET.add(CONFIG_READ);
    DEFAULT_ROOT_PRIV_SET.add(CONFIG_WRITE);
    DEFAULT_ROOT_PRIV_SET.add(LDIF_IMPORT);
    DEFAULT_ROOT_PRIV_SET.add(LDIF_EXPORT);
    DEFAULT_ROOT_PRIV_SET.add(BACKEND_BACKUP);
    DEFAULT_ROOT_PRIV_SET.add(BACKEND_RESTORE);
    DEFAULT_ROOT_PRIV_SET.add(SERVER_LOCKDOWN);
    DEFAULT_ROOT_PRIV_SET.add(SERVER_SHUTDOWN);
    DEFAULT_ROOT_PRIV_SET.add(SERVER_RESTART);
    DEFAULT_ROOT_PRIV_SET.add(DISCONNECT_CLIENT);
    DEFAULT_ROOT_PRIV_SET.add(CANCEL_REQUEST);
    DEFAULT_ROOT_PRIV_SET.add(PASSWORD_RESET);
    DEFAULT_ROOT_PRIV_SET.add(UPDATE_SCHEMA);
    DEFAULT_ROOT_PRIV_SET.add(PRIVILEGE_CHANGE);
    DEFAULT_ROOT_PRIV_SET.add(UNINDEXED_SEARCH);
    DEFAULT_ROOT_PRIV_SET.add(SUBENTRY_WRITE);
    DEFAULT_ROOT_PRIV_SET.add(CHANGELOG_READ);
  }



  /**
   * Creates a new privilege with the provided name.
   *
   * @param  privilegeName  The human-readable name for this policy.
   */
  private Privilege(String privilegeName)
  {
    this.privilegeName = privilegeName;
  }



  /**
   * Retrieves the name for this privilege.
   *
   * @return  The name for this privilege.
   */
  public String getName()
  {
    return privilegeName;
  }



  /**
   * Retrieves the privilege with the specified name.
   *
   * @param  lowerPrivName  The name of the privilege to retrieve,
   *                        formatted in all lowercase characters.
   *
   * @return  The requested privilege, or {@code null} if the provided
   *          value is not the name of a valid privilege.
   */
  public static Privilege privilegeForName(String lowerPrivName)
  {
    return PRIV_MAP.get(lowerPrivName);
  }



  /**
   * Retrieves the human-readable name for this privilege.
   *
   * @return  The human-readable name for this privilege.
   */
  @Override
  public String toString()
  {
    return privilegeName;
  }



  /**
   * Retrieves the set of available privilege names.
   *
   * @return  The set of available privilege names.
   */
  public static Set<String> getPrivilegeNames()
  {
    return PRIV_MAP.keySet();
  }



  /**
   * Retrieves the set of privileges that should be automatically
   * granted to root users if the root privilege set is not specified
   * in the configuration.
   *
   * @return  The set of privileges that should be automatically
   *          granted to root users if the root privilege set is not
   *          specified in the configuration.
   */
  public static Set<Privilege> getDefaultRootPrivileges()
  {
    return DEFAULT_ROOT_PRIV_SET;
  }
}

