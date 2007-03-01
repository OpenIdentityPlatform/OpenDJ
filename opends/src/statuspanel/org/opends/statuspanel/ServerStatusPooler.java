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

package org.opends.statuspanel;

import java.io.File;
import java.util.HashSet;

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.util.Utils;
import org.opends.statuspanel.event.ServerStatusChangeEvent;
import org.opends.statuspanel.event.ServerStatusChangeListener;
import org.opends.statuspanel.i18n.ResourceProvider;


/**
 * This class just reads the status of the server periodically and generates
 * ServerStatusChangeEvent when the status changes.  To receive this events
 * other classes must register using the addServerStatusChangeListener method.
 *
 */
public class ServerStatusPooler
{
  private String dn;
  private String pwd;
  private ServerStatusDescriptor lastDescriptor;
  private boolean stopPooling;
  private Thread t;
  private HashSet<ServerStatusChangeListener> listeners =
    new HashSet<ServerStatusChangeListener>();
  private boolean starting;
  private boolean stopping;
  private ConfigFromFile offLineConf = new ConfigFromFile();
  private ConfigFromLDAP onLineConf = new ConfigFromLDAP();
  private String ldapUrl;
  private int nTriesWithErrorOnline;

  /* The pooling periods */
  private static final int OFFLINE_POOLING_PERIOD = 3000;
  private static final int ONLINE_POOLING_PERIOD = 5000;

  /**
   * Default constructor.
   */
  public ServerStatusPooler()
  {
    /* This is required to retrieve the ldap url to be used by the
     * ConfigFromLDAP class.
     */
    offLineConf.readConfiguration();
    ldapUrl = offLineConf.getLDAPURL();
  }

  /**
   * Starts pooling the server status.  This method does not block the thread
   * that called it.
   *
   */
  public void startPooling()
  {
    stopPooling = false;
    t = new Thread(new Runnable()
    {
      public void run()
      {
        try
        {
          nTriesWithErrorOnline = 0;
          while (!stopPooling)
          {
            long t1 = System.currentTimeMillis();

            ServerStatusDescriptor desc = generateDescriptor();
            if (!desc.equals(lastDescriptor))
            {
              lastDescriptor = desc;
              notifyListeners(lastDescriptor);
            }
            long t2 = System.currentTimeMillis();

            long poolingPeriod =
              desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED?
                  ONLINE_POOLING_PERIOD : OFFLINE_POOLING_PERIOD;

            if (t2 - t1 < poolingPeriod)
            {
              try
              {
                Thread.sleep(poolingPeriod - (t2 - t1));
              }
              catch (Exception ex)
              {
                if (!stopPooling)
                {
                  /* Should not happen. */
                  throw ex;
                }
              }
            }
          }
        }
        catch (Throwable t)
        {
          if (!stopPooling)
          {
            /* This is a bug. */
            t.printStackTrace();
          }
        }
      }
    });
    t.start();
  }

  /**
   * Returns the last server descriptor found.
   * @return the last server descriptor found.
   */
  public ServerStatusDescriptor getLastDescriptor()
  {
    return lastDescriptor;
  }

  /**
   * This method is called to notify that the server is being started.
   */
  public void beginServerStart()
  {
    starting = true;
  }

  /**
   * This method is called to notify that the server start operation ended.
   */
  public void endServerStart()
  {
    starting = false;
  }

  /**
   * This method is called to notify that the server is being stopped.
   */
  public void beginServerStop()
  {
    stopping = true;
  }

  /**
   * This method is called to notify that the server stop operation ended.
   */
  public void endServerStop()
  {
    stopping = false;
  }

  /**
   * Adds a ServerStatusChangeListener that will be notified of updates in
   * the server status.
   * @param l the ServerStatusChangeListener to be added.
   */
  public void addServerStatusChangeListener(ServerStatusChangeListener l)
  {
    listeners.add(l);
  }

  /**
   * Removes a ServerStatusChangeListener.
   * @param l the ServerStatusChangeListener to be removed.
   */
  public void removeServerStatusChangeListener(ServerStatusChangeListener l)
  {
    listeners.remove(l);
  }

  /**
   * Returns <CODE>true</CODE> if this class already has authentication
   * information (setAuthentication method has been called with non-null
   * parameters).
   * @return <CODE>true</CODE> if this class already has authentication
   * information and <CODE>false</CODE> otherwise.
   */
  public boolean isAuthenticated()
  {
    return (dn != null) && (pwd != null);
  }

  /**
   * Sets the authentication information to be used by this class to retrieve
   * information using LDAP.
   * @param dn the authentication Distinguished Name to bind.
   * @param pwd the authentication password to bind.
   */
  public void setAuthentication(String dn, String pwd)
  {
    this.dn = dn;
    this.pwd = pwd;
    if ((ldapUrl != null) && (t != null) && t.isAlive() && !stopPooling)
    {
      /* If we are pooling, stop the pooling update the connection information
       * and restart the pooling.  Set the stopPooling boolean to true to
       * indicate to the code in the Thread 't' to ignore the
       * InterruptedExceptions.
       *
       */
      stopPooling = true;
      t.interrupt();
      try
      {
        t.join(5000);
      }
      catch (Throwable t)
      {
        /* This should not happen: this thread should not be interrupted. */
        t.printStackTrace();
      }
      t = null;
      onLineConf.setConnectionInfo(ldapUrl, dn, pwd);
      startPooling();
    }
    else if (ldapUrl != null)
    {
      onLineConf.setConnectionInfo(ldapUrl, dn, pwd);
    }
  }

  /**
   * This method notifies the ServerStatusChangeListeners that there was an
   * update in the installation progress.
   * @param desc the ServerStatusDescriptor.
   */
  private void notifyListeners(ServerStatusDescriptor desc)
  {
    ServerStatusChangeEvent ev = new ServerStatusChangeEvent(desc);
    for (ServerStatusChangeListener l : listeners)
    {
      l.statusChanged(ev);
    }
  }

  /**
   * Retrieves information of the server.  The method used will depend on the
   * status of the server (started or not).
   * @return a ServerStatusDescriptor object describing the status of the
   * server.
   */
  private ServerStatusDescriptor generateDescriptor()
  {
    ServerStatusDescriptor desc = new ServerStatusDescriptor();

    desc.setAuthenticated((dn != null) && (pwd != null));

    if (starting)
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STARTING);
    }
    else if (stopping)
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STOPPING);
    }
    else if (CurrentInstallStatus.isServerRunning())
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STARTED);
    }
    else
    {
      desc.setStatus(ServerStatusDescriptor.ServerStatus.STOPPED);
    }

    desc.setInstallPath(new File(Utils.getInstallPathFromClasspath()));

    desc.setOpenDSVersion(
        org.opends.server.util.DynamicConstants.FULL_VERSION_STRING);

    if (desc.getStatus() != ServerStatusDescriptor.ServerStatus.STARTED)
    {
      updateDescriptorWithOffLineInfo(desc);
    }
    else
    {
      try
      {
        if ((dn == null) || (pwd == null))
        {
          desc.setAdministrativeUsers(new HashSet<String>());
          desc.setDatabases(new HashSet<DatabaseDescriptor>());
          desc.setListeners(new HashSet<ListenerDescriptor>());
          desc.setOpenConnections(-1);
        }
        else if (ldapUrl != null)
        {
          updateDescriptorWithOnLineInfo(desc);
        }
        else
        {
          /* We cannot retrieve an ldapurl from the config file.  Display
           * what we got in the config file.
           */
          updateDescriptorWithOffLineInfo(desc);
          if (desc.getErrorMessage() != null)
          {
            desc.setErrorMessage(getMsg("could-not-find-valid-ldapurl"));
          }
        }
      }
      catch (Exception ex)
      {
        // Bug
        ex.printStackTrace();
      }
    }

    return desc;
  }

  /**
   * Updates the ServerStatusDescriptor object using the information in the
   * config.ldif file (we use a ConfigFromFile object to do this).
   * @param desc the ServerStatusDescriptor object to be updated.
   */
  private void updateDescriptorWithOffLineInfo(ServerStatusDescriptor desc)
  {
    /* Read the list of administrative users from CurrentInstallStatus
     * (which reads directly config.ldif.  This is the best we can do today
     * when the server is not started.
     */
    offLineConf.readConfiguration();
    desc.setAdministrativeUsers(offLineConf.getAdministrativeUsers());
    desc.setDatabases(offLineConf.getDatabases());
    desc.setListeners(offLineConf.getListeners());
    desc.setErrorMessage(offLineConf.getErrorMessage());
    ldapUrl = offLineConf.getLDAPURL();
    if ((ldapUrl != null) && (dn != null) && (pwd != null))
    {
      onLineConf.setConnectionInfo(ldapUrl, dn, pwd);
    }
    desc.setOpenConnections(-1);
    desc.setJavaVersion(null);
  }

  /**
   * Updates the ServerStatusDescriptor object using the LDAP protocol (we use a
   * ConfigFromLDAP object to do this).
   * @param desc the ServerStatusDescriptor object to be updated.
   */
  private void updateDescriptorWithOnLineInfo(ServerStatusDescriptor desc)
  {
    onLineConf.readConfiguration();
    desc.setAdministrativeUsers(onLineConf.getAdministrativeUsers());
    desc.setDatabases(onLineConf.getDatabases());
    desc.setListeners(onLineConf.getListeners());
    desc.setErrorMessage(onLineConf.getErrorMessage());
    desc.setJavaVersion(onLineConf.getJavaVersion());
    desc.setOpenConnections(onLineConf.getOpenConnections());

    if (desc.getErrorMessage() != null)
    {
      nTriesWithErrorOnline++;
      /* Something happened: if this is the 5th try with the current URL
       * just try to check if the information has changed in the config.ldif
       * file.
       */
      if (nTriesWithErrorOnline >= 5)
      {
        offLineConf.readConfiguration();
        ldapUrl = offLineConf.getLDAPURL();
        onLineConf.setConnectionInfo(ldapUrl, dn, pwd);
        nTriesWithErrorOnline = 0;
      }
    }
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
