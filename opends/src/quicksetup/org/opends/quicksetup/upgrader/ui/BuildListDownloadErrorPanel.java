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

package org.opends.quicksetup.upgrader.ui;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.upgrader.RemoteBuildManager;
import org.opends.quicksetup.ui.CustomHTMLEditorKit;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.WebProxyDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This panel represents the big error message the pops up when the
 * panel can't download the build information.
 */
class BuildListDownloadErrorPanel extends JPanel {

  static private final Logger LOG =
          Logger.getLogger(BuildListDownloadErrorPanel.class.getName());

  private RemoteBuildManager rbm = null;
  private Throwable reason = null;

  private static final long serialVersionUID = 4614415561629811272L;

  /**
   * Creates an instance.
   *
   * @param rbm    RemoteBuildManager that is having trouble.
   * @param reason Throwable indicating the error when downloading
   */
  public BuildListDownloadErrorPanel(RemoteBuildManager rbm,
                                     Throwable reason) {
    this.rbm = rbm;
    this.reason = reason;
    layoutPanel();
  }

  private void layoutPanel() {
    setLayout(new GridBagLayout());

    String proxyString = INFO_GENERAL_NONE.get().toString();
    Proxy proxy = rbm.getProxy();
    if (proxy != null) {
      SocketAddress addr = proxy.address();
      proxyString = addr.toString();
    }

    String baseContext = INFO_GENERAL_UNSPECIFIED.get().toString();
    URL url = rbm.getBaseContext();
    if (url != null) {
      baseContext = url.toString();
    }

    Message html =
            INFO_UPGRADE_CHOOSE_VERSION_BUILD_LIST_ERROR.get(baseContext,
                    reason.getLocalizedMessage(),
                    proxyString, baseContext);

    /* This helps with debugger the HTML rendering
    StringBuffer content = new StringBuffer();
    try {
      FileInputStream fis = new FileInputStream("/tmp/error-html");
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
      String line = null;
      while (null != (line = reader.readLine())) {
        content.append(line);
      }
      html = content.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    */

    CustomHTMLEditorKit ek = new CustomHTMLEditorKit();
    ek.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        Component dlg = SwingUtilities.getAncestorOfClass(Window.class,
                BuildListDownloadErrorPanel.this);
        specifyProxy(dlg);

        // Since the proxy info may change we need
        // to regenerate the text
        removeAll();
        layoutPanel();
        repaint();
        validate();
      }
    });
    add(UIFactory.makeHtmlPane(html, ek, UIFactory.INSTRUCTIONS_FONT));
  }

  /**
   * Displays a dialog prompting the user for proxy information
   * after which applys the new proxy information to the available
   * RemoteBuildManager.
   *
   * @param parent Component that will server as parent to the dialog
   */
  void specifyProxy(final Component parent) {
    Runnable proxySpecifier = new Runnable() {
      public void run() {
        String host = null;
        Integer port = null;
        Proxy proxy = rbm.getProxy();
        if (proxy != null) {
          SocketAddress address = proxy.address();
          if (address instanceof InetSocketAddress) {
            host = ((InetSocketAddress) address).getHostName();
            port = ((InetSocketAddress) address).getPort();
          }
        }
        String user = rbm.getProxyUserName();
        char[] pw = rbm.getProxyPassword();
        WebProxyDialog dlg;
        if (parent instanceof Dialog) {
          dlg = new WebProxyDialog((Dialog) parent, host, port, user, pw);
        } else if (parent instanceof Frame) {
          dlg = new WebProxyDialog((Frame) parent, host, port, user, pw);
        } else {
          dlg = new WebProxyDialog((Frame) null, host, port, user, pw);
        }
        dlg.setVisible(true);
        SocketAddress address = dlg.getSocketAddress();
        if (address != null) {
          proxy = new Proxy(Proxy.Type.HTTP, address);
          rbm.setProxy(proxy);
          rbm.setProxyUserName(dlg.getUserName());
          rbm.setProxyPassword(dlg.getPassword());
        }
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      proxySpecifier.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(proxySpecifier);
      } catch (Throwable t) {
        LOG.log(Level.INFO, "error waiting for event thread", t);
      }
    }
  }
}
