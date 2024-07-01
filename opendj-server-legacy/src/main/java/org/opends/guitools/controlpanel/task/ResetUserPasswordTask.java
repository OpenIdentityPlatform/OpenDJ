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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.PasswordModifyExtendedRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.PasswordModifyExtendedResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.ResultHandler;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.browser.ConnectionWithControls;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;

/** The task called when we want to reset the password of the user. */
public class ResetUserPasswordTask extends Task
{
  private Set<String> backendSet;
  private BasicNode node;
  private char[] currentPassword;
  private char[] newPassword;
  private DN dn;
  private boolean useAdminCtx;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param node the node corresponding to the entry whose password is going
   * to be reset.
   * @param controller the BrowserController.
   * @param pwd the new password.
   */
  public ResetUserPasswordTask(ControlPanelInfo info, ProgressDialog dlg,
      BasicNode node, BrowserController controller, char[] pwd)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    this.node = node;
    this.newPassword = pwd;
    dn = node.getDN();

    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        if (dn.isSubordinateOrEqualTo(baseDN.getDn()))
        {
          backendSet.add(backend.getBackendID());
        }
      }
    }

    try
    {
      ConnectionWithControls conn = controller.findConnectionForDisplayedEntry(node);
      if (conn != null && isBoundAs(dn, conn))
      {
        currentPassword = conn.getConnectionWrapper().getBindPassword().toCharArray();
      }
    }
    catch (Throwable t)
    {
    }
    useAdminCtx = controller.isConfigurationNode(node);
  }

  @Override
  public Type getType()
  {
    return Type.MODIFY_ENTRY;
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_RESET_USER_PASSWORD_TASK_DESCRIPTION.get(node.getDN());
  }

  @Override
  public boolean regenerateDescriptor()
  {
    return false;
  }

  @Override
  protected String getCommandLinePath()
  {
    return getCommandLinePath("ldappasswordmodify");
  }

  @Override
  protected List<String> getCommandLineArguments()
  {
    List<String> args = new ArrayList<>();
    if (currentPassword == null)
    {
      args.add("--authzID");
      args.add("dn:"+dn);
    }
    else
    {
      args.add("--currentPassword");
      args.add(String.valueOf(currentPassword));
    }
    args.add("--newPassword");
    args.add(String.valueOf(newPassword));
    args.addAll(getConnectionCommandLineArguments(useAdminCtx, true));
    args.add(getNoPropertiesFileArgument());
    return args;
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    if (!isServerRunning()
        && state == State.RUNNING
        && runningOnSameServer(taskToBeLaunched))
    {
      // All the operations are incompatible if they apply to this
      // backend for safety.  This is a short operation so the limitation
      // has not a lot of impact.
      Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (!backends.isEmpty())
      {
        incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
        return false;
      }
    }
    return true;
  }

  @Override
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;
      final ConnectionWrapper connectionWrapper = useAdminCtx ? getInfo().getConnection()
                                                              : getInfo().getUserDataConnection();
      if (!isServerRunning() || connectionWrapper == null)
      {
        // Fail fast impossible to connect to the server.
        state = State.FINISHED_WITH_ERROR;
      }
      final PasswordModifyExtendedRequest passwordModifyRequest = Requests.newPasswordModifyExtendedRequest();
      if (currentPassword == null)
      {
        passwordModifyRequest.setUserIdentity("dn: " + dn);
      }
      else
      {
        passwordModifyRequest.setOldPassword(currentPassword);
      }
      passwordModifyRequest.setNewPassword(newPassword);
      connectionWrapper.getConnection()
                       .extendedRequestAsync(passwordModifyRequest)
                       .thenOnResultOrException(
                         new ResultHandler<PasswordModifyExtendedResult>()
                         {
                           @Override
                           public void handleResult(final PasswordModifyExtendedResult passwordModifyExtendedResult)
                           {
                             if (lastException == null && currentPassword != null)
                             {
                               try
                               {
                                 // The connections must be updated, just update the environment, which
                                 // is what we use to clone connections and to launch scripts.
                                 // The environment will also be used if we want to reconnect.
                                 rebind(getInfo().getConnection());
                                 if (getInfo().getUserDataConnection() != null)
                                 {
                                   rebind(getInfo().getUserDataConnection());
                                 }
                               }
                               catch (final LdapException e)
                               {
                                 lastException = e;
                                 state = State.FINISHED_WITH_ERROR;
                               }
                             }
                             state = State.FINISHED_SUCCESSFULLY;
                           }
                         },
                         new ExceptionHandler<LdapException>()
                         {
                           @Override
                           public void handleException(final LdapException e)
                           {
                             state = State.FINISHED_WITH_ERROR;
                           }
                         });
  }

  private void rebind(ConnectionWrapper conn) throws LdapException
  {
    conn.getConnection().bind(newSimpleBindRequest(conn.getBindDn().toString(), newPassword));
  }

  /**
   * Returns whether we are bound using the provided entry.  In
   * the case of root entries this is not necessarily the same as using that
   * particular DN (we might be binding using a value specified in
   * ds-cfg-alternate-bind-dn).
   * @param dn the DN.
   * @param conn the connection that we are using to modify the password.
   * @return {@code true} if we are bound using the provided entry.
   */
  private boolean isBoundAs(DN dn, ConnectionWithControls conn)
  {
    final DN bindDN = conn.getConnectionWrapper().getBindDn();
    boolean isBoundAs = dn.equals(bindDN);
    if (!isBoundAs)
    {
      String attrName = ATTR_ROOTDN_ALTERNATE_BIND_DN;
      Filter filter = Filter.valueOf("(|(objectClass=*)(objectclass=ldapsubentry))");
      SearchRequest request = newSearchRequest(dn, BASE_OBJECT, filter, attrName);
      try (ConnectionEntryReader entries = conn.search(request))
      {
        while (entries.hasNext())
        {
          SearchResultEntry sr = entries.readEntry();
          return sr.parseAttribute(attrName).asSetOfDN().contains(bindDN);
        }
      }
      catch (Throwable t)
      {
      }
    }
    return isBoundAs;
  }
}
