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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package javax.jnlp;

/**
 * This is the interface definition of DownloadServiceListener.
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
import java.net.URL;

public interface DownloadServiceListener {
	public void downloadFailed(URL url, String version);
	public void progress(URL url, String version, long readSoFar, long total,
		      int overallPercent);
	public void upgradingArchive(URL url, String version, int patchPercent,
		      int overallPercent);
	public void validating(URL url, String version, long entry, long total,
		      int overallPercent);
}
