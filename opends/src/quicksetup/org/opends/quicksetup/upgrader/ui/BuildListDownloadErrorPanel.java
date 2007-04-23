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

package org.opends.quicksetup.upgrader.ui;

import org.opends.quicksetup.upgrader.RemoteBuildManager;
import org.opends.quicksetup.ui.CustomHTMLEditorKit;
import org.opends.quicksetup.ui.UIFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;

/**
   * This panel represents the big error message the pops up when the
 * panel can't download the build information.
 */
class BuildListDownloadErrorPanel extends JPanel {

  private RemoteBuildManager rbm = null;
  private Throwable reason = null;
  private ChooseVersionPanel chooseVersionPanel;

  private static final long serialVersionUID = 4614415561629811272L;

  /**
   * Creates an instance.
   * @param rbm RemoteBuildManager that is having trouble.
   * @param reason Throwable indicating the error when downloading
   * @param chooseVersionPanel ChooseVersionPanel where the error occurred
   */
  public BuildListDownloadErrorPanel(ChooseVersionPanel chooseVersionPanel,
                                     RemoteBuildManager rbm,
                                     Throwable reason) {
    this.chooseVersionPanel = chooseVersionPanel;
    this.rbm = rbm;
    this.reason = reason;
    layoutPanel();
  }

  private void layoutPanel() {
    setLayout(new GridBagLayout());

    String proxyString = "None";
    Proxy proxy = rbm.getProxy();
    if (proxy != null) {
      SocketAddress addr = proxy.address();
      proxyString = addr.toString();
    }

    String baseContext = "Unspecified";
    URL url = rbm.getBaseContext();
    if (url != null) {
      baseContext = url.toString();
    }

    String html =
            chooseVersionPanel.getMsg("upgrade-choose-version-build-list-error",
            new String[]{
                    baseContext,
                    reason.getLocalizedMessage(),
                    proxyString});

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
        chooseVersionPanel.specifyProxy(getParent());

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

}
