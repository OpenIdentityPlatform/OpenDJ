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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.opends.server.util.StaticUtils.*;



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
  UNINDEXED_SEARCH("unindexed-search");



  /**
   * A map that will be used to hold a mapping between privilege names
   * and enum values.
   */
  private static final HashMap<String,Privilege> PRIV_MAP =
       new HashMap<String,Privilege>();



  /**
   * The set of privileges that will be automatically assigned to root
   * users if the root privilege set is not specified in the
   * configuration.
   */
  private static final HashSet<Privilege> DEFAULT_ROOT_PRIV_SET =
       new HashSet<Privilege>();



  /**
   * The names of the available privileges defined in this class.
   */
  private static final HashSet<String> PRIV_NAMES =
       new HashSet<String>();



  // The human-readable name for this privilege.
  private final String privilegeName;



  static
  {
    PRIV_MAP.put("bypass-acl", BYPASS_ACL);
    PRIV_MAP.put("modify-acl", MODIFY_ACL);
    PRIV_MAP.put("config-read", CONFIG_READ);
    PRIV_MAP.put("config-write", CONFIG_WRITE);
    PRIV_MAP.put("jmx-read", JMX_READ);
    PRIV_MAP.put("jmx-write", JMX_WRITE);
    PRIV_MAP.put("jmx-notify", JMX_NOTIFY);
    PRIV_MAP.put("ldif-import", LDIF_IMPORT);
    PRIV_MAP.put("ldif-export", LDIF_EXPORT);
    PRIV_MAP.put("backend-backup", BACKEND_BACKUP);
    PRIV_MAP.put("backend-restore", BACKEND_RESTORE);
    PRIV_MAP.put("server-shutdown", SERVER_SHUTDOWN);
    PRIV_MAP.put("server-restart", SERVER_RESTART);
    PRIV_MAP.put("proxied-auth", PROXIED_AUTH);
    PRIV_MAP.put("disconnect-client", DISCONNECT_CLIENT);
    PRIV_MAP.put("cancel-request", CANCEL_REQUEST);
    PRIV_MAP.put("password-reset", PASSWORD_RESET);
    PRIV_MAP.put("data-sync", DATA_SYNC);
    PRIV_MAP.put("update-schema", UPDATE_SCHEMA);
    PRIV_MAP.put("privilege-change", PRIVILEGE_CHANGE);
    PRIV_MAP.put("unindexed-search", UNINDEXED_SEARCH);

    PRIV_NAMES.add("bypass-acl");
    PRIV_NAMES.add("modify-acl");
    PRIV_NAMES.add("config-read");
    PRIV_NAMES.add("config-write");
    PRIV_NAMES.add("jmx-read");
    PRIV_NAMES.add("jmx-write");
    PRIV_NAMES.add("jmx-notify");
    PRIV_NAMES.add("ldif-import");
    PRIV_NAMES.add("ldif-export");
    PRIV_NAMES.add("backend-backup");
    PRIV_NAMES.add("backend-restore");
    PRIV_NAMES.add("server-shutdown");
    PRIV_NAMES.add("server-restart");
    PRIV_NAMES.add("proxied-auth");
    PRIV_NAMES.add("disconnect-client");
    PRIV_NAMES.add("cancel-request");
    PRIV_NAMES.add("password-reset");
    PRIV_NAMES.add("data-sync");
    PRIV_NAMES.add("update-schema");
    PRIV_NAMES.add("privilege-change");
    PRIV_NAMES.add("unindexed-search");

    DEFAULT_ROOT_PRIV_SET.add(BYPASS_ACL);
    DEFAULT_ROOT_PRIV_SET.add(MODIFY_ACL);
    DEFAULT_ROOT_PRIV_SET.add(CONFIG_READ);
    DEFAULT_ROOT_PRIV_SET.add(CONFIG_WRITE);
    DEFAULT_ROOT_PRIV_SET.add(LDIF_IMPORT);
    DEFAULT_ROOT_PRIV_SET.add(LDIF_EXPORT);
    DEFAULT_ROOT_PRIV_SET.add(BACKEND_BACKUP);
    DEFAULT_ROOT_PRIV_SET.add(BACKEND_RESTORE);
    DEFAULT_ROOT_PRIV_SET.add(SERVER_SHUTDOWN);
    DEFAULT_ROOT_PRIV_SET.add(SERVER_RESTART);
    DEFAULT_ROOT_PRIV_SET.add(DISCONNECT_CLIENT);
    DEFAULT_ROOT_PRIV_SET.add(CANCEL_REQUEST);
    DEFAULT_ROOT_PRIV_SET.add(PASSWORD_RESET);
    DEFAULT_ROOT_PRIV_SET.add(UPDATE_SCHEMA);
    DEFAULT_ROOT_PRIV_SET.add(PRIVILEGE_CHANGE);
    DEFAULT_ROOT_PRIV_SET.add(UNINDEXED_SEARCH);
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
    return PRIV_NAMES;
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

