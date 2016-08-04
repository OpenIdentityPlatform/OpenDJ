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

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.CannotRenameException;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.StatusGenericPanel;
import org.opends.guitools.controlpanel.ui.ViewEntryPanel;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.AdminToolMessages;
import org.opends.server.types.Entry;
import org.opends.server.types.Schema;

/** The task that is called when we must modify an entry. */
public class ModifyEntryTask extends Task
{
  private Set<String> backendSet;
  private boolean mustRename;
  private boolean hasModifications;
  private org.forgerock.opendj.ldap.Entry oldEntry;
  private DN oldDn;
  private ArrayList<ModificationItem> modifications;
  private ModificationItem passwordModification;
  private Entry newEntry;
  private BrowserController controller;
  private TreePath treePath;
  private boolean useAdminCtx;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param newEntry the entry containing the new values.
   * @param oldEntry the old entry as we retrieved using JNDI.
   * @param controller the BrowserController.
   * @param path the TreePath corresponding to the node in the tree that we
   * want to modify.
   */
  public ModifyEntryTask(ControlPanelInfo info, ProgressDialog dlg,
      Entry newEntry, org.forgerock.opendj.ldap.Entry oldEntry,
      BrowserController controller, TreePath path)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
    this.controller = controller;
    this.treePath = path;

    DN newDn = newEntry.getName();
    oldDn = oldEntry.getName();
    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        if (newDn.isSubordinateOrEqualTo(baseDN.getDn()) || oldDn.isSubordinateOrEqualTo(baseDN.getDn()))
        {
          backendSet.add(backend.getBackendID());
        }
      }
    }
    mustRename = !newDn.equals(oldDn);
    modifications = getModifications(newEntry, oldEntry, getInfo());

    // Find password modifications
    for (ModificationItem mod : modifications)
    {
      if ("userPassword".equalsIgnoreCase(mod.getAttribute().getID()))
      {
        passwordModification = mod;
        break;
      }
    }
    if (passwordModification != null)
    {
      modifications.remove(passwordModification);
    }
    hasModifications = !modifications.isEmpty()
        || !oldDn.equals(newEntry.getName())
        || passwordModification != null;
  }

  /**
   * Tells whether there actually modifications on the entry.
   * @return <CODE>true</CODE> if there are modifications and <CODE>false</CODE>
   * otherwise.
   */
  public boolean hasModifications()
  {
    return hasModifications;
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
    return INFO_CTRL_PANEL_MODIFY_ENTRY_TASK_DESCRIPTION.get(oldEntry.getName());
  }

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  @Override
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<>();
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
  public boolean regenerateDescriptor()
  {
    return false;
  }

  @Override
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      BasicNode node = (BasicNode)treePath.getLastPathComponent();
      ConnectionWrapper conn = controller.findConnectionForDisplayedEntry(node);
      useAdminCtx = controller.isConfigurationNode(node);
      if (!mustRename)
      {
        if (!modifications.isEmpty()) {
          ModificationItem[] mods =
          new ModificationItem[modifications.size()];
          modifications.toArray(mods);

          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              printEquivalentCommandToModify(newEntry.getName(), modifications,
                  useAdminCtx);
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_MODIFYING_ENTRY.get(oldEntry.getName()),
                      ColorAndFontConstants.progressFont));
            }
          });

          conn.getLdapContext().modifyAttributes(Utilities.getJNDIName(oldEntry.getName().toString()), mods);

          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressDone(
                      ColorAndFontConstants.progressFont));
              controller.notifyEntryChanged(
                  controller.getNodeInfoFromPath(treePath));
              controller.getTree().removeSelectionPath(treePath);
              controller.getTree().setSelectionPath(treePath);
            }
          });
        }
      }
      else
      {
        modifyAndRename(conn, oldDn, oldEntry, newEntry, modifications);
      }
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  @Override
  public void postOperation()
  {
    if (lastException == null
        && state == State.FINISHED_SUCCESSFULLY
        && passwordModification != null)
    {
      try
      {
        Object o = passwordModification.getAttribute().get();
        String sPwd;
        if (o instanceof byte[])
        {
          try
          {
            sPwd = new String((byte[])o, "UTF-8");
          }
          catch (Throwable t)
          {
            throw new RuntimeException("Unexpected error: "+t, t);
          }
        }
        else
        {
          sPwd = String.valueOf(o);
        }
        ResetUserPasswordTask newTask = new ResetUserPasswordTask(getInfo(),
            getProgressDialog(), (BasicNode)treePath.getLastPathComponent(),
            controller, sPwd.toCharArray());
        if (!modifications.isEmpty() || mustRename)
        {
          getProgressDialog().appendProgressHtml("<br><br>");
        }
        StatusGenericPanel.launchOperation(newTask,
            INFO_CTRL_PANEL_RESETTING_USER_PASSWORD_SUMMARY.get(),
            INFO_CTRL_PANEL_RESETTING_USER_PASSWORD_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_RESETTING_USER_PASSWORD_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_RESETTING_USER_PASSWORD_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_RESETTING_USER_PASSWORD_ERROR_DETAILS.get(),
            null,
            getProgressDialog(),
            false,
            getInfo());
        getProgressDialog().setVisible(true);
      }
      catch (NamingException ne)
      {
        // This should not happen
        throw new RuntimeException("Unexpected exception: "+ne, ne);
      }
    }
  }

  /**
   * Modifies and renames the entry.
   * @param conn the connection to the server.
   * @param oldDN the oldDN of the entry.
   * @param originalEntry the original entry.
   * @param newEntry the new entry.
   * @param originalMods the original modifications (these are required since
   * we might want to update them).
   * @throws CannotRenameException if we cannot perform the modification.
   * @throws NamingException if an error performing the modification occurs.
   */
  private void modifyAndRename(ConnectionWrapper conn, final DN oldDN,
      org.forgerock.opendj.ldap.Entry originalEntry,
      final Entry newEntry, final ArrayList<ModificationItem> originalMods)
      throws CannotRenameException, NamingException
  {
    RDN oldRDN = oldDN.rdn();
    RDN newRDN = newEntry.getName().rdn();

    if (rdnTypeChanged(oldRDN, newRDN)
        && userChangedObjectclass(originalMods)
        /* See if the original entry contains the new naming attribute(s) if it does we will be able
        to perform the renaming and then the modifications without problem */
        && !entryContainsRdnTypes(originalEntry, newRDN))
    {
      throw new CannotRenameException(AdminToolMessages.ERR_CANNOT_MODIFY_OBJECTCLASS_AND_RENAME.get());
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        printEquivalentRenameCommand(oldDN, newEntry.getName(), useAdminCtx);
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressWithPoints(
                INFO_CTRL_PANEL_RENAMING_ENTRY.get(oldDN, newEntry.getName()),
                ColorAndFontConstants.progressFont));
      }
    });

    conn.getLdapContext().rename(Utilities.getJNDIName(oldDn.toString()),
        Utilities.getJNDIName(newEntry.getName().toString()));

    final TreePath[] newPath = {null};

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressDone(ColorAndFontConstants.progressFont));
        getProgressDialog().appendProgressHtml("<br>");
        TreePath parentPath = controller.notifyEntryDeleted(
            controller.getNodeInfoFromPath(treePath));
        newPath[0] = controller.notifyEntryAdded(
            controller.getNodeInfoFromPath(parentPath),
            newEntry.getName());
      }
    });

    ModificationItem[] mods = new ModificationItem[originalMods.size()];
    originalMods.toArray(mods);
    if (mods.length > 0)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          DN dn = newEntry.getName();
          printEquivalentCommandToModify(dn, originalMods, useAdminCtx);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_MODIFYING_ENTRY.get(dn),
                  ColorAndFontConstants.progressFont));
        }
      });

      conn.getLdapContext().modifyAttributes(Utilities.getJNDIName(newEntry.getName().toString()), mods);

      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          if (newPath[0] != null)
          {
            controller.getTree().setSelectionPath(newPath[0]);
          }
        }
      });
    }
  }

  private boolean rdnTypeChanged(RDN oldRDN, RDN newRDN)
  {
    if (newRDN.size() != oldRDN.size())
    {
      return true;
    }

    for (AVA ava : newRDN)
    {
      if (!find(oldRDN, ava.getAttributeType()))
      {
        return true;
      }
    }
    return false;
  }

  private boolean find(RDN rdn, AttributeType attrType)
  {
    for (AVA ava : rdn)
    {
      if (attrType.equals(ava.getAttributeType()))
      {
        return true;
      }
    }
    return false;
  }

  private boolean userChangedObjectclass(final ArrayList<ModificationItem> mods)
  {
    for (ModificationItem mod : mods)
    {
      if (ATTR_OBJECTCLASS.equalsIgnoreCase(mod.getAttribute().getID()))
      {
        return true;
      }
    }
    return false;
  }

  private boolean entryContainsRdnTypes(org.forgerock.opendj.ldap.Entry entry, RDN rdn)
  {
    for (AVA ava : rdn)
    {
      org.forgerock.opendj.ldap.Attribute attr = entry.getAttribute(ava.getAttributeName());
      if (attr == null || attr.isEmpty())
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the modifications to apply between two entries.
   * @param newEntry the new entry.
   * @param oldEntry the old entry.
   * @param info the ControlPanelInfo, used to retrieve the schema for instance.
   * @return the modifications to apply between two entries.
   */
  public static ArrayList<ModificationItem> getModifications(Entry newEntry,
      org.forgerock.opendj.ldap.Entry oldEntry, ControlPanelInfo info) {
    ArrayList<ModificationItem> modifications = new ArrayList<>();
    Schema schema = info.getServerDescriptor().getSchema();

    List<org.opends.server.types.Attribute> newAttrs = newEntry.getAttributes();
    newAttrs.add(newEntry.getObjectClassAttribute());
    for (org.opends.server.types.Attribute attr : newAttrs)
    {
      AttributeDescription attrDesc = attr.getAttributeDescription();
      final String attrName = attrDesc.toString();
      if (!ViewEntryPanel.isEditable(attrDesc, schema))
      {
        continue;
      }
      List<ByteString> newValues = new ArrayList<>();
      for (ByteString v : attr)
      {
        newValues.add(v);
      }
      org.forgerock.opendj.ldap.Attribute oldAttr = oldEntry.getAttribute(attrDesc);

      ByteString rdnValue = null;
      for (AVA ava : newEntry.getName().rdn())
      {
        if (ava.getAttributeType().equals(attrDesc.getAttributeType()))
        {
          rdnValue = ava.getAttributeValue();
        }
      }
      boolean isAttributeInNewRdn = rdnValue != null;

      /* Check the attributes of the old DN.  If we are renaming them they
       * will be deleted.  Check that they are on the new entry but not in
       * the new RDN. If it is the case we must add them after the renaming.
       */
      ByteString oldRdnValueToAdd = null;
      /* Check the value in the RDN that will be deleted.  If the value was
       * on the previous RDN but not in the new entry it will be deleted.  So
       * we must avoid to include it as a delete modification in the
       * modifications.
       */
      ByteString oldRdnValueDeleted = null;
      RDN oldRDN = oldEntry.getName().rdn();
      for (AVA ava : oldRDN)
      {
        if (ava.getAttributeType().equals(attrDesc.getAttributeType()))
        {
          ByteString value = ava.getAttributeValue();
          if (attr.contains(value))
          {
            if (rdnValue == null || !rdnValue.equals(value))
            {
              oldRdnValueToAdd = value;
            }
          }
          else
          {
            oldRdnValueDeleted = value;
          }
          break;
        }
      }
      if (oldAttr == null)
      {
        Set<ByteString> vs = new HashSet<>(newValues);
        if (rdnValue != null)
        {
          vs.remove(rdnValue);
        }
        if (!vs.isEmpty())
        {
          modifications.add(new ModificationItem(
              DirContext.ADD_ATTRIBUTE,
              createAttribute(attrName, newValues)));
        }
      } else {
        final List<ByteString> oldValues = toList(oldAttr);
        List<ByteString> toDelete = disjunction(newValues, oldValues);
        if (oldRdnValueDeleted != null)
        {
          toDelete.remove(oldRdnValueDeleted);
        }
        List<ByteString> toAdd = disjunction(oldValues, newValues);
        if (oldRdnValueToAdd != null)
        {
          toAdd.add(oldRdnValueToAdd);
        }
        if (toDelete.size() + toAdd.size() >= newValues.size() &&
            !isAttributeInNewRdn)
        {
          modifications.add(new ModificationItem(
              DirContext.REPLACE_ATTRIBUTE,
              createAttribute(attrName, newValues)));
        }
        else
        {
          if (!toDelete.isEmpty())
          {
            modifications.add(new ModificationItem(
                DirContext.REMOVE_ATTRIBUTE,
                createAttribute(attrName, toDelete)));
          }
          if (!toAdd.isEmpty())
          {
            List<ByteString> vs = new ArrayList<>(toAdd);
            if (rdnValue != null)
            {
              vs.remove(rdnValue);
            }
            if (!vs.isEmpty())
            {
              modifications.add(new ModificationItem(
                  DirContext.ADD_ATTRIBUTE,
                  createAttribute(attrName, vs)));
            }
          }
        }
      }
    }

    /* Check if there are attributes to delete */
    for (org.forgerock.opendj.ldap.Attribute attr : oldEntry.getAllAttributes())
    {
      AttributeDescription attrDesc = attr.getAttributeDescription();
      if (!ViewEntryPanel.isEditable(attrDesc, schema))
      {
        continue;
      }
      org.forgerock.opendj.ldap.Attribute oldAttr = oldEntry.getAttribute(attrDesc);

      if (!newEntry.hasAttribute(AttributeDescription.valueOf(attrDesc.getNameOrOID())) && !oldAttr.isEmpty())
      {
        modifications.add(new ModificationItem(
            DirContext.REMOVE_ATTRIBUTE,
            new BasicAttribute(attrDesc.toString())));
      }
    }
    return modifications;
  }

  private static List<ByteString> toList(org.forgerock.opendj.ldap.Attribute oldAttr)
  {
    List<ByteString> results = new ArrayList<>();
    for (ByteString v : oldAttr)
    {
      results.add(v);
    }
    return results;
  }

  /**
   * Creates a JNDI attribute using an attribute name and a set of values.
   * @param attrName the attribute name.
   * @param values the values.
   * @return a JNDI attribute using an attribute name and a set of values.
   */
  private static Attribute createAttribute(String attrName, List<ByteString> values) {
    Attribute attribute = new BasicAttribute(attrName);
    for (ByteString value : values)
    {
      attribute.add(value.toByteArray());
    }
    return attribute;
  }

  private static List<ByteString> disjunction(List<ByteString> values2, List<ByteString> values1)
  {
    List<ByteString> results = new ArrayList<>();
    for (ByteString v : values1)
    {
      if (!values2.contains(v))
      {
        results.add(v);
      }
    }
    return results;
  }
}
