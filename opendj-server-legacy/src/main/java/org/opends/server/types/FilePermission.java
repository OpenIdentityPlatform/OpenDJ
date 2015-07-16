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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.types;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;

import static org.opends.messages.UtilityMessages.*;

/**
 * This class provides a mechanism for setting file permissions in a
 * more abstract manner than is provided by the underlying operating
 * system and/or filesystem.  It uses a traditional UNIX-style rwx/ugo
 * representation for the permissions and converts them as necessary
 * to the scheme used by the underlying platform.  It does not provide
 * any mechanism for getting file permissions, nor does it provide any
 * way of dealing with file ownership or ACLs.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class FilePermission
{
  /**
   * The bitmask that should be used for indicating whether a file is
   * readable by its owner.
   */
  public static final int OWNER_READABLE = 0x0100;



  /**
   * The bitmask that should be used for indicating whether a file is
   * writable by its owner.
   */
  public static final int OWNER_WRITABLE = 0x0080;



  /**
   * The bitmask that should be used for indicating whether a file is
   * executable by its owner.
   */
  public static final int OWNER_EXECUTABLE = 0x0040;



  /**
   * The bitmask that should be used for indicating whether a file is
   * readable by members of its group.
   */
  public static final int GROUP_READABLE = 0x0020;



  /**
   * The bitmask that should be used for indicating whether a file is
   * writable by members of its group.
   */
  public static final int GROUP_WRITABLE = 0x0010;



  /**
   * The bitmask that should be used for indicating whether a file is
   * executable by members of its group.
   */
  public static final int GROUP_EXECUTABLE = 0x0008;



  /**
   * The bitmask that should be used for indicating whether a file is
   * readable by users other than the owner or group members.
   */
  public static final int OTHER_READABLE = 0x0004;



  /**
   * The bitmask that should be used for indicating whether a file is
   * writable by users other than the owner or group members.
   */
  public static final int OTHER_WRITABLE = 0x0002;



  /**
   * The bitmask that should be used for indicating whether a file is
   * executable by users other than the owner or group members.
   */
  public static final int OTHER_EXECUTABLE = 0x0001;



  /** The encoded representation for this file permission. */
  private int encodedPermission;



  /**
   * Creates a new file permission object with the provided encoded
   * representation.
   *
   * @param  encodedPermission  The encoded representation for this
   *                            file permission.
   */
  public FilePermission(int encodedPermission)
  {
    this.encodedPermission = encodedPermission;
  }



  /**
   * Creates a new file permission with the specified rights for the
   * file owner.  Users other than the owner will not have any rights.
   *
   * @param  ownerReadable    Indicates whether the owner should have
   *                          the read permission.
   * @param  ownerWritable    Indicates whether the owner should have
   *                          the write permission.
   * @param  ownerExecutable  Indicates whether the owner should have
   *                          the execute permission.
   */
  public FilePermission(boolean ownerReadable, boolean ownerWritable,
                        boolean ownerExecutable)
  {
    encodedPermission = 0x0000;

    if (ownerReadable)
    {
      encodedPermission |= OWNER_READABLE;
    }

    if (ownerWritable)
    {
      encodedPermission |= OWNER_WRITABLE;
    }

    if (ownerExecutable)
    {
      encodedPermission |= OWNER_EXECUTABLE;
    }
  }



  /**
   * Creates a new file permission with the specified rights for the
   * file owner, group members, and other users.
   *
   * @param  ownerReadable    Indicates whether the owner should have
   *                          the read permission.
   * @param  ownerWritable    Indicates whether the owner should have
   *                          the write permission.
   * @param  ownerExecutable  Indicates whether the owner should have
   *                          the execute permission.
   * @param  groupReadable    Indicates whether members of the file's
   *                          group should have the read permission.
   * @param  groupWritable    Indicates whether members of the file's
   *                          group should have the write permission.
   * @param  groupExecutable  Indicates whether members of the file's
   *                          group should have the execute
   *                          permission.
   * @param  otherReadable    Indicates whether other users should
   *                          have the read permission.
   * @param  otherWritable    Indicates whether other users should
   *                          have the write permission.
   * @param  otherExecutable  Indicates whether other users should
   *                          have the execute permission.
   */
  public FilePermission(boolean ownerReadable, boolean ownerWritable,
                        boolean ownerExecutable,
                        boolean groupReadable, boolean groupWritable,
                        boolean groupExecutable,
                        boolean otherReadable, boolean otherWritable,
                        boolean otherExecutable)
  {
    encodedPermission = 0x0000;

    if (ownerReadable)
    {
      encodedPermission |= OWNER_READABLE;
    }

    if (ownerWritable)
    {
      encodedPermission |= OWNER_WRITABLE;
    }

    if (ownerExecutable)
    {
      encodedPermission |= OWNER_EXECUTABLE;
    }

    if (groupReadable)
    {
      encodedPermission |= GROUP_READABLE;
    }

    if (groupWritable)
    {
      encodedPermission |= GROUP_WRITABLE;
    }

    if (groupExecutable)
    {
      encodedPermission |= GROUP_EXECUTABLE;
    }

    if (otherReadable)
    {
      encodedPermission |= OTHER_READABLE;
    }

    if (otherWritable)
    {
      encodedPermission |= OTHER_WRITABLE;
    }

    if (otherExecutable)
    {
      encodedPermission |= OTHER_EXECUTABLE;
    }
  }



  /**
   * Indicates whether this file permission includes the owner read
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          owner read permission, or <CODE>false</CODE> if not.
   */
  public boolean isOwnerReadable()
  {
    return is(encodedPermission, OWNER_READABLE);
  }



  /**
   * Indicates whether this file permission includes the owner write
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          owner write permission, or <CODE>false</CODE> if not.
   */
  public boolean isOwnerWritable()
  {
    return is(encodedPermission, OWNER_WRITABLE);
  }



  /**
   * Indicates whether this file permission includes the owner execute
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          owner execute permission, or <CODE>false</CODE> if not.
   */
  public boolean isOwnerExecutable()
  {
    return is(encodedPermission, OWNER_EXECUTABLE);
  }



  /**
   * Indicates whether this file permission includes the group read
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          group read permission, or <CODE>false</CODE> if not.
   */
  public boolean isGroupReadable()
  {
    return is(encodedPermission, GROUP_READABLE);
  }



  /**
   * Indicates whether this file permission includes the group write
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          group write permission, or <CODE>false</CODE> if not.
   */
  public boolean isGroupWritable()
  {
    return is(encodedPermission, GROUP_WRITABLE);
  }



  /**
   * Indicates whether this file permission includes the group execute
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          group execute permission, or <CODE>false</CODE> if not.
   */
  public boolean isGroupExecutable()
  {
    return is(encodedPermission, GROUP_EXECUTABLE);
  }



  /**
   * Indicates whether this file permission includes the other read
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          other read permission, or <CODE>false</CODE> if not.
   */
  public boolean isOtherReadable()
  {
    return is(encodedPermission, OTHER_READABLE);
  }



  /**
   * Indicates whether this file permission includes the other write
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          other write permission, or <CODE>false</CODE> if not.
   */
  public boolean isOtherWritable()
  {
    return is(encodedPermission, OTHER_WRITABLE);
  }



  /**
   * Indicates whether this file permission includes the other execute
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          other execute permission, or <CODE>false</CODE> if not.
   */
  public boolean isOtherExecutable()
  {
    return is(encodedPermission, OTHER_EXECUTABLE);
  }

  private boolean is(int encodedPermissions, int permission)
  {
    return (encodedPermissions & permission) == permission;
  }

  /**
   * Attempts to set the given permissions on the specified file.  If
   * the underlying platform does not allow the full level of
   * granularity specified in the permissions, then an attempt will be
   * made to set them as closely as possible to the provided
   * permissions, erring on the side of security.
   *
   * @param  f  The file to which the permissions should be applied.
   * @param  p  The permissions to apply to the file.
   *
   * @return  <CODE>true</CODE> if the permissions (or the nearest
   *          equivalent) were successfully applied to the specified
   *          file, or <CODE>false</CODE> if was not possible to set
   *          the permissions on the current platform.
   *
   * @throws  FileNotFoundException  If the specified file does not
   *                                 exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              set the file permissions.
   */
  public static boolean setPermissions(File f, FilePermission p)
         throws FileNotFoundException, DirectoryException
  {
    if (!f.exists())
    {
      throw new FileNotFoundException(ERR_FILEPERM_SET_NO_SUCH_FILE.get(f.getAbsolutePath()).toString());
    }
    Path filePath = f.toPath();
    PosixFileAttributeView posix = Files.getFileAttributeView(filePath, PosixFileAttributeView.class);
    if (posix != null)
    {
      StringBuilder posixMode = new StringBuilder();
      toPOSIXString(p, posixMode, "", "", "");
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString(posixMode.toString());
      try
      {
        Files.setPosixFilePermissions(filePath, perms);
      }
      catch (UnsupportedOperationException | ClassCastException | IOException | SecurityException ex)
      {
        throw new DirectoryException(ResultCode.OTHER, ERR_FILEPERM_SET_JAVA_EXCEPTION.get(f.getAbsolutePath()), ex);
      }
      return true;
    }
    return Files.getFileAttributeView(filePath, AclFileAttributeView.class) != null;
  }



  /**
   * Retrieves a three-character string that is the UNIX mode for the
   * provided file permission.  Each character of the string will be a
   * numeric digit from zero through seven.
   *
   * @param  p  The permission to retrieve as a UNIX mode string.
   *
   * @return  The UNIX mode string for the provided permission.
   */
  public static String toUNIXMode(FilePermission p)
  {
    StringBuilder buffer = new StringBuilder(3);
    toUNIXMode(buffer, p);
    return buffer.toString();
  }



  /**
   * Appends a three-character string that is the UNIX mode for the
   * provided file permission to the given buffer.  Each character of
   * the string will be a numeric digit from zero through seven.
   *
   * @param  buffer  The buffer to which the mode string should be
   *                 appended.
   * @param  p       The permission to retrieve as a UNIX mode string.
   */
  public static void toUNIXMode(StringBuilder buffer,
                                FilePermission p)
  {
    byte modeByte = 0x00;
    if (p.isOwnerReadable())
    {
      modeByte |= 0x04;
    }
    if (p.isOwnerWritable())
    {
      modeByte |= 0x02;
    }
    if (p.isOwnerExecutable())
    {
      modeByte |= 0x01;
    }
    buffer.append(modeByte);

    modeByte = 0x00;
    if (p.isGroupReadable())
    {
      modeByte |= 0x04;
    }
    if (p.isGroupWritable())
    {
      modeByte |= 0x02;
    }
    if (p.isGroupExecutable())
    {
      modeByte |= 0x01;
    }
    buffer.append(modeByte);

    modeByte = 0x00;
    if (p.isOtherReadable())
    {
      modeByte |= 0x04;
    }
    if (p.isOtherWritable())
    {
      modeByte |= 0x02;
    }
    if (p.isOtherExecutable())
    {
      modeByte |= 0x01;
    }
    buffer.append(modeByte);
  }



  /**
   * Decodes the provided string as a UNIX mode and retrieves the
   * corresponding file permission.  The mode string must contain
   * three digits between zero and seven.
   *
   * @param  modeString  The string representation of the UNIX mode to
   *                     decode.
   *
   * @return  The file permission that is equivalent to the given UNIX
   *          mode.
   *
   * @throws  DirectoryException  If the provided string is not a
   *                              valid three-digit UNIX mode.
   */
  public static FilePermission decodeUNIXMode(String modeString)
         throws DirectoryException
  {
    if (modeString == null || modeString.length() != 3)
    {
      LocalizableMessage message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(modeString);
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    int encodedPermission = 0x0000;
    switch (modeString.charAt(0))
    {
      case '0':
        break;
      case '1':
        encodedPermission |= OWNER_EXECUTABLE;
        break;
      case '2':
        encodedPermission |= OWNER_WRITABLE;
        break;
      case '3':
        encodedPermission |= OWNER_WRITABLE | OWNER_EXECUTABLE;
        break;
      case '4':
        encodedPermission |= OWNER_READABLE;
        break;
      case '5':
         encodedPermission |= OWNER_READABLE | OWNER_EXECUTABLE;
        break;
      case '6':
        encodedPermission |= OWNER_READABLE | OWNER_WRITABLE;
        break;
      case '7':
        encodedPermission |= OWNER_READABLE | OWNER_WRITABLE |
                             OWNER_EXECUTABLE;
        break;
      default:
      LocalizableMessage message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(modeString);
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    switch (modeString.charAt(1))
    {
      case '0':
        break;
      case '1':
        encodedPermission |= GROUP_EXECUTABLE;
        break;
      case '2':
        encodedPermission |= GROUP_WRITABLE;
        break;
      case '3':
        encodedPermission |= GROUP_WRITABLE | GROUP_EXECUTABLE;
        break;
      case '4':
        encodedPermission |= GROUP_READABLE;
        break;
      case '5':
         encodedPermission |= GROUP_READABLE | GROUP_EXECUTABLE;
        break;
      case '6':
        encodedPermission |= GROUP_READABLE | GROUP_WRITABLE;
        break;
      case '7':
        encodedPermission |= GROUP_READABLE | GROUP_WRITABLE |
                             GROUP_EXECUTABLE;
        break;
      default:
      LocalizableMessage message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(modeString);
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    switch (modeString.charAt(2))
    {
      case '0':
        break;
      case '1':
        encodedPermission |= OTHER_EXECUTABLE;
        break;
      case '2':
        encodedPermission |= OTHER_WRITABLE;
        break;
      case '3':
        encodedPermission |= OTHER_WRITABLE | OTHER_EXECUTABLE;
        break;
      case '4':
        encodedPermission |= OTHER_READABLE;
        break;
      case '5':
         encodedPermission |= OTHER_READABLE | OTHER_EXECUTABLE;
        break;
      case '6':
        encodedPermission |= OTHER_READABLE | OTHER_WRITABLE;
        break;
      case '7':
        encodedPermission |= OTHER_READABLE | OTHER_WRITABLE |
                             OTHER_EXECUTABLE;
        break;
      default:
      LocalizableMessage message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(modeString);
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    return new FilePermission(encodedPermission);
  }



  /**
   * Build a file permissions string in the "rwx" form expected by NIO,
   * but with optional prefix strings before each three character block.
   * <p>
   * For example: "rwxr-xrw-" and "Owner=rwx, Group=r-x", Other=rw-".
   *
   * @param p      The file permissions to use.
   * @param buffer The buffer being appended to.
   * @param owner  The owner prefix, must not be null.
   * @param group  The group prefix, must not be null.
   * @param other  The other prefix, must not be null.
   */
  private static void toPOSIXString(FilePermission p, StringBuilder buffer,
      String owner, String group, String other)
  {
    buffer.append(owner);
    buffer.append(p.isOwnerReadable() ? "r" : "-");
    buffer.append(p.isOwnerWritable() ? "w" : "-");
    buffer.append(p.isOwnerExecutable() ? "x" : "-");

    buffer.append(group);
    buffer.append(p.isGroupReadable() ? "r" : "-");
    buffer.append(p.isGroupWritable() ? "w" : "-");
    buffer.append(p.isGroupExecutable() ? "x" : "-");

    buffer.append(other);
    buffer.append(p.isOtherReadable() ? "r" : "-");
    buffer.append(p.isOtherWritable() ? "w" : "-");
    buffer.append(p.isOtherExecutable() ? "x" : "-");
  }



  /**
   * Retrieves a string representation of this file permission.
   *
   * @return  A string representation of this file permission.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this file permission to the
   * given buffer.
   *
   * @param  buffer  The buffer to which the data should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    toPOSIXString(this, buffer, "Owner=", ", Group=", ", Other=");
  }
}

