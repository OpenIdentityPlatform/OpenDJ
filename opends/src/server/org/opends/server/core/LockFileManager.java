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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.Backend;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a mechanism for allowing the Directory Server to utilize
 * file locks as provided by the underlying OS.  File locks may be exclusive or
 * shared, and will be visible between different processes on the same system.
 */
public class LockFileManager
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.LockFileManager";



  // A map between the filenames and the lock files for exclusive locks.
  private static HashMap<String,FileLock> exclusiveLocks =
       new HashMap<String,FileLock>();

  // A map between the filenames and the lock files for shared locks.
  private static HashMap<String,FileLock> sharedLocks =
       new HashMap<String,FileLock>();

  // A map between the filenames and reference counts for shared locks.
  private static HashMap<String,Integer> sharedLockReferences =
       new HashMap<String,Integer>();

  // The lock providing threadsafe access to the lock map data.
  private static ReentrantLock mapLock = new ReentrantLock();



  /**
   * Attempts to acquire a shared lock on the specified file.
   *
   * @param  lockFile       The file for which to obtain the shared lock.
   * @param  failureReason  A buffer that can be used to hold a reason that the
   *                        lock could not be acquired.
   *
   * @return  <CODE>true</CODE> if the lock was obtained successfully, or
   *          <CODE>false</CODE> if it could not be obtained.
   */
  public static boolean acquireSharedLock(String lockFile,
                                          StringBuilder failureReason)
  {
    assert debugEnter(CLASS_NAME, "acquireSharedLock",
                      String.valueOf(lockFile));

    mapLock.lock();

    try
    {
      // Check to see if there's already an exclusive lock on the file.  If so,
      // then we can't get a shared lock on it.
      if (exclusiveLocks.containsKey(lockFile))
      {
        int msgID = MSGID_FILELOCKER_LOCK_SHARED_REJECTED_BY_EXCLUSIVE;
        failureReason.append(getMessage(msgID, lockFile));
        return false;
      }


      // Check to see if we already hold a shared lock on the file.  If so, then
      // increase its refcount and return true.
      FileLock sharedLock = sharedLocks.get(lockFile);
      if (sharedLock != null)
      {
        int numReferences = sharedLockReferences.get(lockFile);
        numReferences++;
        sharedLockReferences.put(lockFile, numReferences);
        return true;
      }


      // We don't hold a lock on the file so we need to create it.  First,
      // create the file only if it doesn't already exist.
      File f = getFileForPath(lockFile);
      try
      {
        if (! f.exists())
        {
          f.createNewFile();
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "acquireSharedLock", e);

        int msgID = MSGID_FILELOCKER_LOCK_SHARED_FAILED_CREATE;
        failureReason.append(getMessage(msgID, lockFile,
                                        stackTraceToSingleLineString(e)));
        return false;
      }


      // Open the file for reading and get the corresponding file channel.
      FileChannel channel;
      try
      {
        RandomAccessFile raf = new RandomAccessFile(lockFile, "r");
        channel = raf.getChannel();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "acquireSharedLock", e);

        int msgID = MSGID_FILELOCKER_LOCK_SHARED_FAILED_OPEN;
        failureReason.append(getMessage(msgID, lockFile,
                                        stackTraceToSingleLineString(e)));
        return false;
      }


      // Try to obtain a shared lock on the file channel.
      FileLock fileLock;
      try
      {
        fileLock = channel.tryLock(0L, Long.MAX_VALUE, true);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "acquireSharedLock", e);

        int msgID = MSGID_FILELOCKER_LOCK_SHARED_FAILED_LOCK;
        failureReason.append(getMessage(msgID,
                                        stackTraceToSingleLineString(e)));
        return false;
      }


      // If we could not get the lock, then return false.  Otherwise, put it in
      // the shared lock table with a reference count of 1 and return true.
      if (fileLock == null)
      {
        int msgID = MSGID_FILELOCKER_LOCK_SHARED_NOT_GRANTED;
        failureReason.append(getMessage(msgID, lockFile));
        return false;
      }
      else
      {
        sharedLocks.put(lockFile, fileLock);
        sharedLockReferences.put(lockFile, 1);
        return true;
      }
    }
    finally
    {
      mapLock.unlock();
    }
  }



  /**
   * Attempts to acquire an exclusive lock on the specified file.
   *
   * @param  lockFile       The file for which to obtain the exclusive lock.
   * @param  failureReason  A buffer that can be used to hold a reason that the
   *                        lock could not be acquired.
   *
   * @return  <CODE>true</CODE> if the lock was obtained successfully, or
   *          <CODE>false</CODE> if it could not be obtained.
   */
  public static boolean acquireExclusiveLock(String lockFile,
                                             StringBuilder failureReason)
  {
    assert debugEnter(CLASS_NAME, "acquireExclusiveLock",
                      String.valueOf(lockFile));

    mapLock.lock();

    try
    {
      // Check to see if there's already an exclusive lock on the file.  If so,
      // then we can't get another exclusive lock on it.
      if (exclusiveLocks.containsKey(lockFile))
      {
        int msgID = MSGID_FILELOCKER_LOCK_EXCLUSIVE_REJECTED_BY_EXCLUSIVE;
        failureReason.append(getMessage(msgID, lockFile));
        return false;
      }


      // Check to see if we already hold a shared lock on the file.  If so, then
      // we can't get an exclusive lock on it.
      if (sharedLocks.containsKey(lockFile))
      {
        int msgID = MSGID_FILELOCKER_LOCK_EXCLUSIVE_REJECTED_BY_SHARED;
        failureReason.append(getMessage(msgID, lockFile));
        return false;
      }


      // We don't hold a lock on the file so we need to create it.  First,
      // create the file only if it doesn't already exist.
      File f = getFileForPath(lockFile);
      try
      {
        if (! f.exists())
        {
          f.createNewFile();
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "acquireExclusiveLock", e);

        int msgID = MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_CREATE;
        failureReason.append(getMessage(msgID, lockFile,
                                        stackTraceToSingleLineString(e)));
        return false;
      }


      // Open the file read+write and get the corresponding file channel.
      FileChannel channel;
      try
      {
        RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
        channel = raf.getChannel();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "acquireExclusiveLock", e);

        int msgID = MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_OPEN;
        failureReason.append(getMessage(msgID, lockFile,
                                        stackTraceToSingleLineString(e)));
        return false;
      }


      // Try to obtain an exclusive lock on the file channel.
      FileLock fileLock;
      try
      {
        fileLock = channel.tryLock(0L, Long.MAX_VALUE, false);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "acquireExclusiveLock", e);

        int msgID = MSGID_FILELOCKER_LOCK_EXCLUSIVE_FAILED_LOCK;
        failureReason.append(getMessage(msgID, lockFile,
                                        stackTraceToSingleLineString(e)));
        return false;
      }


      // If we could not get the lock, then return false.  Otherwise, put it in
      // the exclusive lock table and return true.
      if (fileLock == null)
      {
        int msgID = MSGID_FILELOCKER_LOCK_EXCLUSIVE_NOT_GRANTED;
        failureReason.append(getMessage(msgID, lockFile));
        return false;
      }
      else
      {
        exclusiveLocks.put(lockFile, fileLock);
        return true;
      }
    }
    finally
    {
      mapLock.unlock();
    }
  }



  /**
   * Attempts to release the lock on the specified file.  If an exclusive lock
   * is held, then it will be released.  If a shared lock is held, then its
   * reference count will be reduced, and the lock will be released if the
   * resulting reference count is zero.  If we don't know anything about the
   * requested file, then don't do anything.
   *
   * @param  lockFile       The file for which to release the associated lock.
   * @param  failureReason  A buffer that can be used to hold information about
   *                        a problem that occurred preventing the successful
   *                        release.
   *
   * @return  <CODE>true</CODE> if the lock was found and released successfully,
   *          or <CODE>false</CODE> if a problem occurred that might have
   *          prevented the lock from being released.
   */
  public static boolean releaseLock(String lockFile,
                                    StringBuilder failureReason)
  {
    assert debugEnter(CLASS_NAME, "acquireExclusiveLock",
                      String.valueOf(lockFile));

    mapLock.lock();

    try
    {
      // See if we hold an exclusive lock on the file.  If so, then release it
      // and get remove it from the lock table.
      FileLock lock = exclusiveLocks.remove(lockFile);
      if (lock != null)
      {
        try
        {
          lock.release();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "releaseLock", e);

          int msgID = MSGID_FILELOCKER_UNLOCK_EXCLUSIVE_FAILED_RELEASE;
          failureReason.append(getMessage(msgID, lockFile,
                                          stackTraceToSingleLineString(e)));
          return false;
        }

        try
        {
          lock.channel().close();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "releaseLock", e);

          // Even though we couldn't close the channel for some reason, this
          // should still be OK because we released the lock above.
        }

        return true;
      }


      // See if we hold a shared lock on the file.  If so, then reduce its
      // refcount and release only if the resulting count is zero.
      lock = sharedLocks.get(lockFile);
      if (lock != null)
      {
        int refCount = sharedLockReferences.get(lockFile);
        refCount--;
        if (refCount <= 0)
        {
          sharedLocks.remove(lockFile);
          sharedLockReferences.remove(lockFile);

          try
          {
            lock.release();
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "releaseLock", e);

            int msgID = MSGID_FILELOCKER_UNLOCK_SHARED_FAILED_RELEASE;
            failureReason.append(getMessage(msgID, lockFile,
                                            stackTraceToSingleLineString(e)));
            return false;
          }

          try
          {
            lock.channel().close();
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "releaseLock", e);

            // Even though we couldn't close the channel for some reason, this
            // should still be OK because we released the lock above.
          }
        }
        else
        {
          sharedLockReferences.put(lockFile, refCount);
        }

        return true;
      }


      // We didn't find a reference to the file.  We'll have to return false
      // since either we lost the reference or we're trying to release a lock
      // we never had.  Both of them are bad.
      int msgID = MSGID_FILELOCKER_UNLOCK_UNKNOWN_FILE;
      failureReason.append(getMessage(msgID, lockFile));
      return false;
    }
    finally
    {
      mapLock.unlock();
    }
  }



  /**
   * Retrieves the filename that should be used for the lock file for the
   * Directory Server instance.
   *
   * @return  The filename that should be used for the lock file for the
   *          Directory Server instance.
   */
  public static String getServerLockFileName()
  {
    assert debugEnter(CLASS_NAME, "getServerLockFileName");

    StringBuilder buffer = new StringBuilder();
    buffer.append(DirectoryServer.getServerRoot());
    buffer.append(File.separator);
    buffer.append(LOCKS_DIRECTORY);
    buffer.append(File.separator);
    buffer.append(SERVER_LOCK_FILE_NAME);
    buffer.append(LOCK_FILE_SUFFIX);

    return buffer.toString();
  }



  /**
   * Retrieves the filename that should be used for the lock file for the
   * specified backend.
   *
   * @param  backend  The backend for which to retrieve the filename for the
   *                  lock file.
   *
   * @return  The filename that should be used for the lock file for the
   *          specified backend.
   */
  public static String getBackendLockFileName(Backend backend)
  {
    assert debugEnter(CLASS_NAME, "getBackendLockFileName",
                      String.valueOf(backend));

    StringBuilder buffer = new StringBuilder();
    buffer.append(DirectoryServer.getServerRoot());
    buffer.append(File.separator);
    buffer.append(LOCKS_DIRECTORY);
    buffer.append(File.separator);
    buffer.append(BACKEND_LOCK_FILE_PREFIX);
    buffer.append(backend.getBackendID());
    buffer.append(LOCK_FILE_SUFFIX);

    return buffer.toString();
  }
}

