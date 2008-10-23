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

package org.opends.guitools.controlpanel.browser;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Canvas;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.MemoryImageSource;
import java.awt.image.PixelGrabber;
import java.util.HashMap;
import java.util.Set;
import java.util.SortedSet;

import javax.swing.ImageIcon;

import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.ui.UIFactory;

/**
 * This class is used as a cache containing the icons that are used by the
 * BrowserController to update the nodes.  It keeps some icons associated with
 * some entry types, to suffixes, to the root node, etc.
 */
public class IconPool {

  /**
   * Mask for the leaf node.
   */
  public static final int MODIFIER_LEAF   = 0x01;
  /**
   * Mask for the referral node.
   */
  public static final int MODIFIER_REFERRAL = 0x02;
  /**
   * Mask for the node that has an error.
   */
  public static final int MODIFIER_ERROR    = 0x04;

  private HashMap<String, ImageIcon> iconTable =
    new HashMap<String, ImageIcon>();
  private HashMap<String, String> pathTable = new HashMap<String, String>();
  private HashMap<String, String> descriptionTable =
    new HashMap<String, String>();
  private ImageIcon defaultLeafIcon;
  private ImageIcon suffixIcon;
  private ImageIcon defaultContainerIcon;
  private ImageIcon rootNodeIcon;
  private ImageIcon errorIcon;
  private ImageIcon errorMaskIcon;
  private ImageIcon referralMaskIcon;

  /**
   * The path that contains the icons.
   */
  public static final String IMAGE_PATH =
    "org/opends/guitools/controlpanel/images";


  private static final String[] ICON_PATH = {
    "person",  "ds-user.png",
    "organization", "ds-folder.png",
    "organizationalunit",  "ds-ou.png",
    "groupofuniquenames",  "ds-group.png",
    "groupofurls",  "ds-group.png",
    "ds-virtual-static-group",  "ds-group.png",
    "passwordpolicy",   "ds-ppol.png"
  };

  private static final String[] DESCRIPTION = {
    "person", INFO_PERSON_ICON_DESCRIPTION.get().toString(),
    "organization", INFO_ORGANIZATION_ICON_DESCRIPTION.get().toString(),
    "organizationalunit",
    INFO_ORGANIZATIONAL_UNIT_ICON_DESCRIPTION.get().toString(),
    "groupofuniquenames", INFO_STATIC_GROUP_ICON_DESCRIPTION.get().toString(),
    "groupofurls", INFO_DYNAMIC_GROUP_ICON_DESCRIPTION.get().toString(),
    "ds-virtual-static-group",
    INFO_VIRTUAL_STATIC_GROUP_ICON_DESCRIPTION.get().toString(),
    "passwordpolicy", INFO_PASSWORD_POLICY_ICON_DESCRIPTION.get().toString()
  };

  private String GENERIC_OBJECT_DESCRIPTION = "Generic entry";

  /**
   * The default constructor.
   *
   */
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
    ImageIcon result;

    String key = makeKey(objectClasses, modifiers);
    result = iconTable.get(key);
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
    if ((objectClasses == null) || (objectClasses.size() == 0)) {
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
      result = maskedIcon(result, getErrorMaskIcon());
    }

    return result;
  }


  private String makeKey(SortedSet<String> ocValues, int modifiers) {
    // TODO: verify the performance of IconPool.makeKey()
    StringBuilder result = new StringBuilder();
    if(ocValues != null) {
      result.append(Utilities.getStringFromCollection(ocValues, ""));
    }
    result.append(String.valueOf(modifiers));
    return result.toString();
  }



    /**
     * Returns a RemoteImage corresponding to the superposition of the icon
     * Image and the mask Image.
     *
     * @param icon the RemoteImage that we want to bar.
     * @param mask the ImageIcond to be used as mask.
     * @return a RemoteImage corresponding to the superposition of the icon
     * Image and the mask Image.
     */
  public static ImageIcon maskedIcon(ImageIcon icon, ImageIcon mask) {
    ImageIcon fReturn;
    int TRANSPARENT = 16711165;  // The value of a transparent pixel

    int h = icon.getIconHeight();
    int w = icon.getIconWidth();

    if (mask.getImageLoadStatus() != MediaTracker.COMPLETE) {
      return null;
    }
    Image maskImage = mask.getImage();

    Image scaledMaskImage = maskImage.getScaledInstance(w, h ,
        Image.SCALE_SMOOTH);

    ImageIcon scaledMask = new ImageIcon(scaledMaskImage);
    if (scaledMask.getImageLoadStatus() != MediaTracker.COMPLETE) {
      return null;
    }

    int[] iconPixels = new int[w * h];
    try {
      PixelGrabber pg =
        new PixelGrabber(icon.getImage(), 0, 0, w, h, iconPixels, 0, w);
      pg.grabPixels();

      if ((pg.status() & ImageObserver.ABORT) !=0) {
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    int[] filterPixels = new int[w * h];
    try {
      PixelGrabber pgf =
        new PixelGrabber(scaledMask.getImage(), 0, 0, w, h, filterPixels, 0, w);
      pgf.grabPixels();

      if ((pgf.status() & ImageObserver.ABORT) !=0) {
        fReturn = null;
        return fReturn;
      }
    } catch (Exception e) {
      e.printStackTrace();
      fReturn = null;
      return fReturn;
    }


    int[] newPixels = new int[w * h];

    for( int i = 0; i < h; i++)
      for (int j = 0; j < w; j++)
        if (filterPixels[j + i*w] != TRANSPARENT) {
          newPixels[j + i*w] = filterPixels[j + i*w];
        } else {
          newPixels[j + i*w] = iconPixels[j + i*w];
        }
    Canvas component = new Canvas();

    Image newImage = component.getToolkit().createImage(
        new MemoryImageSource(
            w, h, ColorModel.getRGBdefault(), newPixels, 0, w));
    fReturn = new ImageIcon(newImage, icon.getDescription());

    return fReturn;
  }
}
