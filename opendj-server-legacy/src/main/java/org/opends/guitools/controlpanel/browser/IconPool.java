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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.browser;

import java.util.HashMap;
import java.util.Set;
import java.util.SortedSet;

import javax.swing.ImageIcon;

import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.server.util.ServerConstants;

import static org.opends.messages.AdminToolMessages.*;

/**
 * This class is used as a cache containing the icons that are used by the
 * BrowserController to update the nodes.  It keeps some icons associated with
 * some entry types, to suffixes, to the root node, etc.
 */
public class IconPool {

  /** Mask for the leaf node. */
  public static final int MODIFIER_LEAF   = 0x01;
  /** Mask for the referral node. */
  public static final int MODIFIER_REFERRAL = 0x02;
  /** Mask for the node that has an error. */
  public static final int MODIFIER_ERROR    = 0x04;

  private final HashMap<String, ImageIcon> iconTable = new HashMap<>();
  private final HashMap<String, String> pathTable = new HashMap<>();
  private final HashMap<String, String> descriptionTable = new HashMap<>();
  private ImageIcon defaultLeafIcon;
  private ImageIcon suffixIcon;
  private ImageIcon defaultContainerIcon;
  private ImageIcon rootNodeIcon;
  private ImageIcon errorIcon;
  private ImageIcon errorMaskIcon;
  private ImageIcon referralMaskIcon;

  /** The path that contains the icons. */
  public static final String IMAGE_PATH =
    "org/opends/guitools/controlpanel/images";


  private static final String[] ICON_PATH = {
    ServerConstants.OC_PERSON,  "ds-user.png",
    ServerConstants.OC_ORGANIZATION, "ds-folder.png",
    ServerConstants.OC_ORGANIZATIONAL_UNIT_LC,  "ds-ou.png",
    ServerConstants.OC_GROUP_OF_NAMES_LC, "ds-group.png",
    ServerConstants.OC_GROUP_OF_ENTRIES_LC, "ds-group.png",
    ServerConstants.OC_GROUP_OF_UNIQUE_NAMES_LC,  "ds-group.png",
    ServerConstants.OC_GROUP_OF_URLS_LC,  "ds-group.png",
    ServerConstants.OC_VIRTUAL_STATIC_GROUP,  "ds-group.png",
    "passwordpolicy",   "ds-ppol.png"
  };

  private static final String[] DESCRIPTION = {
    ServerConstants.OC_PERSON, INFO_PERSON_ICON_DESCRIPTION.get().toString(),
    ServerConstants.OC_ORGANIZATION, INFO_ORGANIZATION_ICON_DESCRIPTION.get()
      .toString(),
    ServerConstants.OC_ORGANIZATIONAL_UNIT_LC,
    INFO_ORGANIZATIONAL_UNIT_ICON_DESCRIPTION.get().toString(),
    ServerConstants.OC_GROUP_OF_NAMES_LC, INFO_STATIC_GROUP_ICON_DESCRIPTION
      .get().toString(),
    ServerConstants.OC_GROUP_OF_ENTRIES_LC, INFO_STATIC_GROUP_ICON_DESCRIPTION
      .get().toString(),
    ServerConstants.OC_GROUP_OF_UNIQUE_NAMES_LC,
      INFO_STATIC_GROUP_ICON_DESCRIPTION.get().toString(),
    ServerConstants.OC_GROUP_OF_URLS_LC, INFO_DYNAMIC_GROUP_ICON_DESCRIPTION
      .get().toString(),
    ServerConstants.OC_VIRTUAL_STATIC_GROUP,
    INFO_VIRTUAL_STATIC_GROUP_ICON_DESCRIPTION.get().toString(),
    "passwordpolicy", INFO_PASSWORD_POLICY_ICON_DESCRIPTION.get().toString()
  };

  private final String GENERIC_OBJECT_DESCRIPTION = "Generic entry";

  /** The default constructor. */
  public IconPool() {
    // Recopy ICON_PATH in pathTable for fast access
    for (int i = 0; i < ICON_PATH.length; i = i+2) {
      pathTable.put(ICON_PATH[i], ICON_PATH[i+1]);
    }
    for (int i = 0; i < DESCRIPTION.length; i = i+2) {
      descriptionTable.put(DESCRIPTION[i], DESCRIPTION[i+1]);
    }
  }


  /**
   * If objectClass is null, a default icon is used.
   * @param objectClasses the objectclass values of the entry for which we want
   * an icon.
   * @param modifiers the modifiers associated with the entry (if there was
   * an error, if it is a referral, etc.).
   * @return the icon corresponding to the provided object classes and
   * modifiers.
   */
  public ImageIcon getIcon(SortedSet<String> objectClasses, int modifiers) {
    String key = makeKey(objectClasses, modifiers);
    ImageIcon result = iconTable.get(key);
    if (result == null) {
      result = makeIcon(objectClasses, modifiers);
      iconTable.put(key, result);
    }
    return result;
  }

  /**
   * Creates an icon for a given path.
   * @param path the path of the icon.
   * @param description the description of the icon
   * @return the associated ImageIcon.
   */
  private ImageIcon createIcon(String path, String description)
  {
    ImageIcon icon = Utilities.createImageIcon(path);
    if (description != null)
    {
      icon.setDescription(description);
      icon.getAccessibleContext().setAccessibleDescription(description);
    }
    return icon;
  }

  /**
   * Returns the icon associated with a leaf node.
   * @return the icon associated with a leaf node.
   */
  public ImageIcon getDefaultLeafIcon() {
    if (defaultLeafIcon == null) {
      defaultLeafIcon = createIcon(IMAGE_PATH + "/ds-generic.png",
          GENERIC_OBJECT_DESCRIPTION);
    }
    return defaultLeafIcon;
  }


  /**
   * Returns the icon associated with a container node.
   * @return the icon associated with a container node.
   */
  public ImageIcon getDefaultContainerIcon() {
    if (defaultContainerIcon == null) {
      defaultContainerIcon = createIcon(IMAGE_PATH + "/ds-folder.png",
      "Folder entry");
    }
    return defaultContainerIcon;
  }

  /**
   * Returns the icon associated with a suffix node.
   * @return the icon associated with a suffix node.
   */
  public ImageIcon getSuffixIcon() {
    if (suffixIcon == null) {
      suffixIcon = createIcon(IMAGE_PATH + "/ds-suffix.png",
      "Suffix entry");
    }
    return suffixIcon;
  }

  /**
   * Returns the icon associated with a root node.
   * @return the icon associated with a root node.
   */
  public ImageIcon getIconForRootNode() {
    if (rootNodeIcon == null) {
      rootNodeIcon = createIcon(IMAGE_PATH + "/ds-directory.png",
      "Root entry");
    }
    return rootNodeIcon;
  }

  /**
   * Returns the icon associated with a node for which an error occurred.
   * @return the icon associated with a node for which an error occurred.
   */
  public ImageIcon getErrorIcon() {
    if (errorIcon == null) {
      errorIcon = UIFactory.getImageIcon(UIFactory.IconType.ERROR);
    }
    return errorIcon;
  }


  /**
   * Returns the icon associated with the error mask icon.
   * @return the icon associated with the error mask icon.
   */
  public ImageIcon getErrorMaskIcon() {
    if (errorMaskIcon == null) {
      errorMaskIcon = UIFactory.getImageIcon(UIFactory.IconType.ERROR);
    }
    return errorMaskIcon;
  }


  /**
   * Returns the icon associated with the referral mask icon.
   * @return the icon associated with the referral mask icon.
   */
  public ImageIcon getReferralMaskIcon() {
    if (referralMaskIcon == null) {
      referralMaskIcon = createIcon(IMAGE_PATH + "/ds-referral.png",
      "Referral mask");
    }
    return referralMaskIcon;
  }


  /**
   * Returns an icon for a given objectclass applying some modifiers.
   * @param objectClasses the objectclasses of the entry
   * @param modifiers the modifiers of the icon (if the entry is inactivated,
   * if it is a referral...).
   * @return an icon for a given objectclass applying some modifiers.
   */
  private ImageIcon makeIcon(Set<String> objectClasses, int modifiers) {
    ImageIcon result;

    // Find the icon associated to the object class
    if (objectClasses == null || objectClasses.isEmpty()) {
      result = getDefaultContainerIcon();
    }
    else {
      String iconFile = null;
      for (String value : objectClasses)
      {
        iconFile = pathTable.get(value.toLowerCase());
        if (iconFile != null)
        {
          break;
        }
      }
      if (iconFile == null) {
        if ((modifiers & MODIFIER_LEAF) != 0) {
          result = getDefaultLeafIcon();
        }
        else {
          result = getDefaultContainerIcon();
        }
      }
      else {
        String description = null;
        for (String value : objectClasses)
        {
          description = descriptionTable.get(value.toLowerCase());
          if (description != null)
          {
            break;
          }
        }
        if (description == null)
        {
          description = GENERIC_OBJECT_DESCRIPTION;
        }
        result = createIcon(IMAGE_PATH + "/" + iconFile,
            description);
      }
    }

    // Alter this icon according the modifiers
    if ((modifiers & MODIFIER_REFERRAL) != 0) {
      result = getReferralMaskIcon();
    }
    if ((modifiers & MODIFIER_ERROR) != 0) {
      result = getErrorMaskIcon();
    }

    return result;
  }


  private String makeKey(SortedSet<String> ocValues, int modifiers) {
    // TODO: verify the performance of IconPool.makeKey()
    StringBuilder result = new StringBuilder();
    if(ocValues != null) {
      result.append(Utilities.getStringFromCollection(ocValues, ""));
    }
    result.append(modifiers);
    return result.toString();
  }

}
