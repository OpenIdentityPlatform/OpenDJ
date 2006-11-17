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

package javax.jnlp;

/**
 * This is just an empty implementation of DownloadService.  It does define
 * only the methods of the JNLP API used in the class 
 * org.opends.quicksetup.webstart.WebstartDownloader.
 * 
 * We have chosen to do this because we we require the JNLP API to be compiled
 * but the location of the javaws.jar depends on the java distribution, so
 * instead of trying to figure out where javaws.jar is on the java distribution
 * that is being used to compile the source, we just add these classes to the
 * build-tools.jar file that will be used to compile que QuickSetup.
 * 
 * It must be noted that the class 
 * org.opends.quicksetup.webstart.WebstartDownloader will be only executed in
 * the context of a Java Web Start application and that in this case the
 * javaws.jar will be provided by the Java Web Start Runtime environment.  So
 * we are not providing the javaws-stub.jar during runtime: it is used only
 * for compilation.
 *
 */
public interface DownloadService {
	public boolean isResourceCached(java.net.URL url, String version);
	public void removeResource(java.net.URL url, String version)
	 throws java.io.IOException;
	public void loadResource(java.net.URL ref, String version,
			DownloadServiceListener listener) throws java.io.IOException;
}
