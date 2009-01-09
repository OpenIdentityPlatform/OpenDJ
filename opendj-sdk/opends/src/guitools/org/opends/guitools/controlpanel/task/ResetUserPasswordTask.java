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

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.config.ConfigConstants;
import org.opends.server.tools.LDAPPasswordModify;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;

/**
 * The task called when we want to reset the password of the user.
 *
 */
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
    backendSet = new HashSet<String>();
    this.node = node;
    this.newPassword = pwd;
    try
    {
      dn = DN.decode(node.getDN());
      for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (dn.isDescendantOf(baseDN.getDn()))
          {
            backendSet.add(backend.getBackendID());
          }
        }
      }
    }
    catch (OpenDsException ode)
    {
      throw new IllegalStateException("Could not parse DN: "+node.getDN(), ode);
    }
    try
    {
      InitialLdapContext ctx =
        controller.findConnectionForDisplayedEntry(node);
      if ((ctx != null) && isBoundAs(dn, ctx))
      {
        currentPassword = ConnectionUtils.getBindPassword(ctx).toCharArray();
      }
    }
    catch (Throwable t)
    {
    }
    useAdminCtx = controller.isConfigurationNode(node);
  }

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    return Type.MODIFY_ENTRY;
  }

  /**
   * {@inheritDoc}
   */
  public Set<String> getBackends()
  {
    return backendSet;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTaskDescription()
  {
    return INFO_CTRL_PANEL_RESET_USER_PASSWORD_TASK_DESCRIPTION.get(
        node.getDN());
  }

  /**
   * {@inheritDoc}
   */
  public boolean regenerateDescriptor()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  protected String getCommandLinePath()
  {
    return getCommandLinePath("ldappasswordmodify");
  }

  /**
   * {@inheritDoc}
   */
  protected ArrayList<String> getCommandLineArguments()
  {
    ArrayList<String> args = new ArrayList<String>();
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

  /**
   * {@inheritDoc}
   */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (!isServerRunning())
    {
      if (state == State.RUNNING)
      {
        // All the operations are incompatible if they apply to this
        // backend for safety.  This is a short operation so the limitation
        // has not a lot of impact.
        Set<String> backends =
          new TreeSet<String>(taskToBeLaunched.getBackends());
        backends.retainAll(getBackends());
        if (backends.size() > 0)
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this,
              taskToBeLaunched));
          canLaunch = false;
        }
      }
    }
    return canLaunch;
  }

  /**
   * {@inheritDoc}
   */
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;
    try
    {
      ArrayList<String> arguments = getCommandLineArguments();

      String[] args = new String[arguments.size()];

      arguments.toArray(args);

      returnCode = LDAPPasswordModify.mainPasswordModify(args, false,
            outPrintStream, errorPrintStream);

      if (returnCode != 0)
      {
        state = State.FINISHED_WITH_ERROR;
      }
      else
      {
        if ((lastException == null) && (currentPassword != null))
        {
          // The connections must be updated, just update the environment, which
          // is what we use to clone connections and to launch scripts.
          // The environment will also be used if we want to reconnect.
          getInfo().getDirContext().addToEnvironment(
              Context.SECURITY_CREDENTIALS,
              String.valueOf(newPassword));
          if (getInfo().getUserDataDirContext() != null)
          {
            getInfo().getUserDataDirContext().addToEnvironment(
                Context.SECURITY_CREDENTIALS,
                String.valueOf(newPassword));
          }
        }
        state = State.FINISHED_SUCCESSFULLY;
      }
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  /**
   * Returns <CODE>true</CODE> if we are bound using the provided entry.  In
   * the case of root entries this is not necessarily the same as using that
   * particular DN (we might be binding using a value specified in
   * ds-cfg-alternate-bind-dn).
   * @param dn the DN.
   * @param ctx the connection that we are using to modify the password.
   * @return <CODE>true</CODE> if we are bound using the provided entry.
   */
  private boolean isBoundAs(DN dn, InitialLdapContext ctx)
  {
    boolean isBoundAs = false;
    DN bindDN = DN.nullDN();
    try
    {
      String b = ConnectionUtils.getBindDN(ctx);
      bindDN = DN.decode(b);
      isBoundAs = dn.equals(bindDN);
    }
    catch (Throwable t)
    {
      // Ignore
    }
    if (!isBoundAs)
    {
      try
      {
        SearchControls ctls = new SearchControls();
        ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
        String filter =
          "(|(objectClass=*)(objectclass=ldapsubentry))";
        String attrName = ConfigConstants.ATTR_ROOTDN_ALTERNATE_BIND_DN;
        ctls.setReturningAttributes(new String[] {attrName});
        NamingEnumeration<SearchResult> entries =
          ctx.search(Utilities.getJNDIName(dn.toString()), filter, ctls);

        while (entries.hasMore())
        {
          SearchResult sr = entries.next();
          Set<String> dns = ConnectionUtils.getValues(sr, attrName);
          for (String sDn : dns)
          {
            if (bindDN.equals(DN.decode(sDn)))
            {
              isBoundAs = true;
              break;
            }
          }
        }
      }
      catch (Throwable t)
      {
      }
    }
    return isBoundAs;
  }
}
