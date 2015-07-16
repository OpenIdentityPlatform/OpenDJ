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

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.CannotRenameException;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.StatusGenericPanel;
import org.opends.guitools.controlpanel.ui.ViewEntryPanel;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.AdminToolMessages;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;

/**
 * The task that is called when we must modify an entry.
 */
public class ModifyEntryTask extends Task
{
  private Set<String> backendSet;
  private boolean mustRename;
  private boolean hasModifications;
  private CustomSearchResult oldEntry;
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
      Entry newEntry, CustomSearchResult oldEntry,
      BrowserController controller, TreePath path)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
    this.controller = controller;
    this.treePath = path;
    DN newDn = newEntry.getName();
    try
    {
      oldDn = DN.valueOf(oldEntry.getDN());
      for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (newDn.isDescendantOf(baseDN.getDn()) ||
              oldDn.isDescendantOf(baseDN.getDn()))
          {
            backendSet.add(backend.getBackendID());
          }
        }
      }
      mustRename = !newDn.equals(oldDn);
    }
    catch (OpenDsException ode)
    {
      throw new RuntimeException("Could not parse DN: " + oldEntry.getDN(), ode);
    }
    modifications = getModifications(newEntry, oldEntry, getInfo());
    // Find password modifications
    for (ModificationItem mod : modifications)
    {
      if (mod.getAttribute().getID().equalsIgnoreCase("userPassword"))
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

  /** {@inheritDoc} */
  public Type getType()
  {
    return Type.MODIFY_ENTRY;
  }

  /** {@inheritDoc} */
  public Set<String> getBackends()
  {
    return backendSet;
  }

  /** {@inheritDoc} */
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_MODIFY_ENTRY_TASK_DESCRIPTION.get(oldEntry.getDN());
  }


  /** {@inheritDoc} */
  protected String getCommandLinePath()
  {
    return null;
  }

  /** {@inheritDoc} */
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<>();
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  public boolean regenerateDescriptor()
  {
    return false;
  }

  /** {@inheritDoc} */
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      BasicNode node = (BasicNode)treePath.getLastPathComponent();
      InitialLdapContext ctx = controller.findConnectionForDisplayedEntry(node);
      useAdminCtx = controller.isConfigurationNode(node);
      if (!mustRename)
      {
        if (!modifications.isEmpty()) {
          ModificationItem[] mods =
          new ModificationItem[modifications.size()];
          modifications.toArray(mods);

          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              printEquivalentCommandToModify(newEntry.getName(), modifications,
                  useAdminCtx);
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_MODIFYING_ENTRY.get(oldEntry.getDN()),
                      ColorAndFontConstants.progressFont));
            }
          });

          ctx.modifyAttributes(Utilities.getJNDIName(oldEntry.getDN()), mods);

          SwingUtilities.invokeLater(new Runnable()
          {
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
        modifyAndRename(ctx, oldDn, oldEntry, newEntry, modifications);
      }
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  /** {@inheritDoc} */
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
   * @param ctx the connection to the server.
   * @param oldDN the oldDN of the entry.
   * @param originalEntry the original entry.
   * @param newEntry the new entry.
   * @param originalMods the original modifications (these are required since
   * we might want to update them).
   * @throws CannotRenameException if we cannot perform the modification.
   * @throws NamingException if an error performing the modification occurs.
   */
  private void modifyAndRename(DirContext ctx, final DN oldDN,
  CustomSearchResult originalEntry, final Entry newEntry,
  final ArrayList<ModificationItem> originalMods)
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
      public void run()
      {
        printEquivalentRenameCommand(oldDN, newEntry.getName(), useAdminCtx);
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressWithPoints(
                INFO_CTRL_PANEL_RENAMING_ENTRY.get(oldDN, newEntry.getName()),
                ColorAndFontConstants.progressFont));
      }
    });

    ctx.rename(Utilities.getJNDIName(oldDn.toString()),
        Utilities.getJNDIName(newEntry.getName().toString()));

    final TreePath[] newPath = {null};

    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressDone(ColorAndFontConstants.progressFont));
        getProgressDialog().appendProgressHtml("<br>");
        TreePath parentPath = controller.notifyEntryDeleted(
            controller.getNodeInfoFromPath(treePath));
        newPath[0] = controller.notifyEntryAdded(
            controller.getNodeInfoFromPath(parentPath),
            newEntry.getName().toString());
      }
    });


    ModificationItem[] mods = new ModificationItem[originalMods.size()];
    originalMods.toArray(mods);
    if (mods.length > 0)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
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

      ctx.modifyAttributes(Utilities.getJNDIName(newEntry.getName().toString()), mods);

      SwingUtilities.invokeLater(new Runnable()
      {
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
    if (newRDN.getNumValues() != oldRDN.getNumValues())
    {
      return true;
    }

    for (int i = 0; i < newRDN.getNumValues(); i++)
    {
      if (!find(oldRDN, newRDN.getAttributeName(i)))
      {
        return true;
      }
    }
    return false;
  }

  private boolean find(RDN rdn, String attrName)
  {
    for (int j = 0; j < rdn.getNumValues(); j++)
    {
      if (attrName.equalsIgnoreCase(rdn.getAttributeName(j)))
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

  private boolean entryContainsRdnTypes(CustomSearchResult entry, RDN rdn)
  {
    for (int i = 0; i < rdn.getNumValues(); i++)
    {
      List<Object> values = entry.getAttributeValues(rdn.getAttributeName(i));
      if (!!values.isEmpty())
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
      CustomSearchResult oldEntry, ControlPanelInfo info) {
    ArrayList<ModificationItem> modifications = new ArrayList<>();
    Schema schema = info.getServerDescriptor().getSchema();

    List<org.opends.server.types.Attribute> newAttrs = newEntry.getAttributes();
    newAttrs.add(newEntry.getObjectClassAttribute());
    for (org.opends.server.types.Attribute attr : newAttrs)
    {
      String attrName = attr.getNameWithOptions();
      if (!ViewEntryPanel.isEditable(attrName, schema))
      {
        continue;
      }
      AttributeType attrType = schema.getAttributeType(attr.getName().toLowerCase());
      if (attrType == null)
      {
        attrType = DirectoryServer.getDefaultAttributeType(attr.getName().toLowerCase());
      }
      List<ByteString> newValues = new ArrayList<>();
      Iterator<ByteString> it = attr.iterator();
      while (it.hasNext())
      {
        newValues.add(it.next());
      }
      List<Object> oldValues = oldEntry.getAttributeValues(attrName);

      boolean isAttributeInNewRdn = false;
      ByteString rdnValue = null;
      RDN rdn = newEntry.getName().rdn();
      for (int i=0; i<rdn.getNumValues() && !isAttributeInNewRdn; i++)
      {
        isAttributeInNewRdn =
          rdn.getAttributeName(i).equalsIgnoreCase(attrName);
        if (isAttributeInNewRdn)
        {
          rdnValue = rdn.getAttributeValue(i);
        }
      }

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
      RDN oldRDN = null;
      try
      {
        oldRDN = DN.valueOf(oldEntry.getDN()).rdn();
      }
      catch (DirectoryException de)
      {
        throw new RuntimeException("Unexpected error parsing DN: "+
            oldEntry.getDN(), de);
      }
      for (int i=0; i<oldRDN.getNumValues(); i++)
      {
        if (oldRDN.getAttributeName(i).equalsIgnoreCase(attrName))
        {
          ByteString value = oldRDN.getAttributeValue(i);
          if (containsValue(attr, value))
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
      if (oldValues == null)
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
        List<ByteString> toDelete = getValuesToDelete(oldValues, newValues);
        if (oldRdnValueDeleted != null)
        {
          toDelete.remove(oldRdnValueDeleted);
        }
        List<ByteString> toAdd = getValuesToAdd(oldValues, newValues);
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
    for (String attrName : oldEntry.getAttributeNames())
    {
      if (!ViewEntryPanel.isEditable(attrName, schema))
      {
        continue;
      }
      List<Object> oldValues = oldEntry.getAttributeValues(attrName);
      String attrNoOptions =
        Utilities.getAttributeNameWithoutOptions(attrName).toLowerCase();

      List<org.opends.server.types.Attribute> attrs = newEntry.getAttribute(attrNoOptions);
      if (!find(attrs, attrName) && !oldValues.isEmpty())
      {
        modifications.add(new ModificationItem(
            DirContext.REMOVE_ATTRIBUTE,
            new BasicAttribute(attrName)));
      }
    }
    return modifications;
  }

  private static boolean find(List<org.opends.server.types.Attribute> attrs, String attrName)
  {
    if (attrs != null)
    {
      for (org.opends.server.types.Attribute attr : attrs)
      {
        if (attr.getNameWithOptions().equalsIgnoreCase(attrName))
        {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsValue(org.opends.server.types.Attribute attr, Object value)
  {
    for (Iterator<ByteString> it = attr.iterator(); it.hasNext();)
    {
      if (value.equals(it.next()))
      {
        return true;
      }
    }
    return false;
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

  /**
   * Creates a ByteString for an attribute and a value (the one we got using JNDI).
   * @param value the value found using JNDI.
   * @return a ByteString object.
   */
  private static ByteString createAttributeValue(Object value)
  {
    if (value instanceof String)
    {
      return ByteString.valueOf((String) value);
    }
    else if (value instanceof byte[])
    {
      return ByteString.wrap((byte[]) value);
    }
    return ByteString.valueOf(String.valueOf(value));
  }

  /**
   * Returns the set of ByteString that must be deleted.
   * @param oldValues the old values of the entry.
   * @param newValues the new values of the entry.
   * @return the set of ByteString that must be deleted.
   */
  private static List<ByteString> getValuesToDelete(List<Object> oldValues,
      List<ByteString> newValues)
  {
    List<ByteString> valuesToDelete = new ArrayList<>();
    for (Object o : oldValues)
    {
      ByteString oldValue = createAttributeValue(o);
      if (!newValues.contains(oldValue))
      {
        valuesToDelete.add(oldValue);
      }
    }
    return valuesToDelete;
  }

  /**
   * Returns the set of ByteString that must be added.
   * @param oldValues the old values of the entry.
   * @param newValues the new values of the entry.
   * @return the set of ByteString that must be added.
   */
  private static List<ByteString> getValuesToAdd(List<Object> oldValues,
    List<ByteString> newValues)
  {
    List<ByteString> valuesToAdd = new ArrayList<>();
    for (ByteString newValue : newValues)
    {
      if (!contains(oldValues, newValue))
      {
        valuesToAdd.add(newValue);
      }
    }
    return valuesToAdd;
  }

  private static boolean contains(List<Object> oldValues, ByteString newValue)
  {
    for (Object o : oldValues)
    {
      if (createAttributeValue(o).equals(newValue))
      {
        return true;
      }
    }
    return false;
  }
}
