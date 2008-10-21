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
package org.opends.build.tools;

import com.vladium.emma.report.*;
import com.vladium.emma.report.html.doc.*;
import com.vladium.emma.data.*;
import com.vladium.util.IntObjectMap;

import java.io.*;
import java.util.*;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class CoverageDiff extends Task {

  private static SVNClientManager ourClientManager =
          SVNClientManager.newInstance();
  private static final String EOL = System.getProperty("line.separator");

  private boolean verbose = false;
  private boolean enabled = true;

  private final int COVERED_MOD_EXE_LINES = 0;
  private final int MOD_EXE_LINES = 1;
  private final int MOD_LINES = 2;
  private final int DEL_LINES = 3;

  private final String ENCODING = "ISO-8859-1";
  private final int IO_BUF_SIZE = 32 * 1024;
  private final LinkedHashMap<String, SrcFileItem> emmaSrcMap =
      new LinkedHashMap<String, SrcFileItem>();
  private final LinkedHashMap<String, Double[]> modCoverageMap =
      new LinkedHashMap<String, Double[]>();

  private final String CSS = "TABLE,TD,TH {border-style:solid; border-color:black;} " +
            "TD,TH {background:white;margin:0;line-height:100%;padding-left:0.5em;padding-right:0.5em;} " +
            "TD {border-width:0 1px 0 0;} TH {border-width:1px 1px 1px 0;} " +
            "TR TD.h {color:red;} " +
            "TABLE {border-spacing:0; border-collapse:collapse;border-width:0 0 1px 1px;} " +
            "P,H1,H2,H3,TH {font-family:verdana,arial,sans-serif;font-size:10pt;} " +
            "TD {font-family:courier,monospace;font-size:10pt;} " +
            "TABLE.hdft {border-spacing:0;border-collapse:collapse;border-style:none;} " +
            "TABLE.hdft TH,TABLE.hdft TD {border-style:none;line-height:normal;} " +
            "TABLE.hdft TH.tl,TABLE.hdft TD.tl {background:#6699CC;color:white;} " +
            "TABLE.hdft TD.nv {background:#6633DD;color:white;} " +
            ".nv A:link {color:white;} .nv A:visited {color:white;} " +
            ".nv A:active {color:yellow;} " +
            "TABLE.hdft A:link {color:white;} " +
            "TABLE.hdft A:visited {color:white;} " +
            "TABLE.hdft A:active {color:yellow;} " +
            ".in {color:#356085;} " +
            "TABLE.s TD {padding-left:0.25em;padding-right:0.25em;} " +
            "TABLE.s TD.ddt {padding-left:0.25em;padding-right:0.25em;color:#AAAAAA;}" +
            "TABLE.s TD.ds {padding-left:0.25em;padding-right:0.25em;text-align:right;background:#F0F0F0;} " +
            "TABLE.s TD.dm {padding-left:0.25em;padding-right:0.25em;text-align:right;background:#BCCFF9;} " +
            "TABLE.s TD.dd {padding-left:0.25em;padding-right:0.25em;text-align:right;background:#AAAAAA;color:#FFFFFF} " +
            "TABLE.s TH {padding-left:0.25em;padding-right:0.25em;text-align:left;background:#F0F0F0;} " +
            "TABLE.s TD.cz {background:#FF9999;} " +
            "TABLE.s TD.cp {background:#FFFF88;} " +
            "TABLE.s TD.cc {background:#CCFFCC;} " +
            "A:link {color:#0000EE;text-decoration:none;} " +
            "A:visited {color:#0000EE;text-decoration:none;} " +
            "A:hover {color:#0000EE;text-decoration:underline;} " +
            "TABLE.cn {border-width:0 0 1px 0;} " +
            "TABLE.s {border-width:1px 0 1px 1px;} " +
            "TD.h {color:red;border-width:0 1px 0 0;} " +
            "TD.f {border-width:0 1px 0 1px;} " +
            "TD.hf {color:red;border-width:0 1px 0 1px;} " +
            "TH.f {border-width:1px 1px 1px 1px;} " +
            "TR.cis TD {background:#F0F0F0;} " +
            "TR.cis TD {border-width:1px 1px 1px 0;} " +
            "TR.cis TD.h {color:red;border-width:1px 1px 1px 0;} " +
            "TR.cis TD.f {border-width:1px 1px 1px 1px;} " +
            "TR.cis TD.hf {color:red;border-width:1px 1px 1px 1px;} " +
            "TD.b {border-style:none;background:transparent;line-height:50%;}  " +
            "TD.bt {border-width:1px 0 0 0;background:transparent;line-height:50%;} " +
            "TR.o TD {background:#F0F0F0;}" +
            "TABLE.it {border-style:none;}" +
            "TABLE.it TD,TABLE.it TH {border-style:none;}";

  private File emmaDataPath;
  private File outputPath;
  private String diffPath;

  //   The SVN revision to perform the diff against when calculating
  //   the coverage diff.  It can be a revision number, a timestamp,
  //   or a revision keyword (BASE, COMMITTED, and PREV make the
  //   most sense).  The primary use case for this setting is to do
  //   a coverage diff against the previous revision when there are
  //   no changes in the working copy.  It defaults to BASE.
  private String fromRevision;

  public void setEmmaDataPath(String file)
  {
    emmaDataPath = new File(file);
  }

  public void setOutputPath(String file)
  {
    outputPath = new File(file);
  }

  public void setDiffPath(String diffArgs)
  {
    diffPath = diffArgs;
  }

  public void setVerbose(String bol)
  {
    verbose = bol.toLowerCase().equals("true");
  }

  public void setEnabled(String bol)
  {
    enabled = bol.toLowerCase().equals("true");
  }

  public void setFromRevision(String fromRevision)
  {
    this.fromRevision = fromRevision;
  }

  public void execute() throws BuildException {
    try {
      innerExecute();
    } catch (BuildException e) {
      throw e;
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void innerExecute() throws BuildException, SVNException
  {
    long start = System.currentTimeMillis();
    verboseOut("Starting to execute coveragediff.");
    verboseOut("diffPath='" + diffPath +"'");
    if(emmaDataPath == null)
    {
      throw new BuildException("emmaDataPath attribute is not set. It must be set to the path of the EMMA data directory");
    }
    if(outputPath == null)
    {
      throw new BuildException("outputPath attribute is not set. It must be set to a valid directory where the report will be generated");
    }
    if(fromRevision == null)
    {
      throw new BuildException("fromRevision attribute is not set. It must be set to the revision from which the diff is generated (e.g. BASE).");
    }

    if(!enabled)
    {
      return;
    }

    // So we can go over http:// and https:// when diff'ing against previous versions
    DAVRepositoryFactory.setup();
    
    IReportDataView emmaDataView = null;
    try
    {
        emmaDataView = loadEmmaData(emmaDataPath);
        verboseOut("Loaded EMMA data.");
    }
    catch(IOException ie)
    {
      System.out.println("WARNING: An error occurred while loading EMMA " +
          "data. Report will not contain any coverage information.");
    }

    try
    {
      processDiffOutput(getDiffOutputReader(), emmaDataView);
    }
    catch(IOException ie)
    {
      System.out.println("ERROR: An error occurred while processing diff output: " + ie.toString() + " Quitting...");
      ie.printStackTrace();
      return;
    }
    System.out.println("Coverage diff completed in " + (System.currentTimeMillis() - start) + " ms.");
  }


  private IReportDataView loadEmmaData(File emmaCoverageDataDir) throws IOException
  {
    if(emmaCoverageDataDir == null)
    {
      throw new IOException("Emma Converage Data Directory is null");
    }

    File[] emmaCoverageDataFiles = emmaCoverageDataDir.listFiles();
    int emmaCoverageDataFileCount = 0;
    IReportDataView m_view;
    IMetaData mdata = null;
    ICoverageData cdata = null;

    if(emmaCoverageDataFiles == null || emmaCoverageDataFiles.length <= 0)
    {
      throw new IOException("No EMMA data files found");
    }

    verboseOut("processing input files ...");

    final long start = System.currentTimeMillis();

    // merge all data files:

    for (final File dataFile : emmaCoverageDataFiles) {
      verboseOut("processing input file [" + dataFile.getAbsolutePath() + "] ...");

      final IMergeable[] fileData = DataFactory.load(dataFile);

      final IMetaData _mdata = (IMetaData) fileData[DataFactory.TYPE_METADATA];
      if (_mdata != null) {
        verboseOut("  loaded " + _mdata.size() + " metadata entries");

        if (mdata == null)
          mdata = _mdata;
        else
          mdata = (IMetaData) mdata.merge(_mdata); // note: later datapath entries override earlier ones
      }

      final ICoverageData _cdata = (ICoverageData) fileData[DataFactory.TYPE_COVERAGEDATA];
      if (_cdata != null) {
        verboseOut("  loaded " + _cdata.size() + " coverage data entries");

        if (cdata == null)
          cdata = _cdata;
        else
          cdata = (ICoverageData) cdata.merge(_cdata); // note: later datapath entries override earlier ones
      }

      ++emmaCoverageDataFileCount;
    }

    verboseOut(emmaCoverageDataFileCount + " file(s) read and merged in " + (System.currentTimeMillis() - start) + " ms");

    if ((mdata == null) || mdata.isEmpty()) {
      System.out.println("nothing to do: no metadata found in any of the data files");
      return null;
    }

    if (cdata == null) {
      System.out.println("nothing to do: no runtime coverage data found in any of the data files");
      return null;
    }

    if (cdata.isEmpty()) {
      System.out.println("no collected coverage data found in any of the data files [Diff output will not include coverage data]");
      return null;
    }
    if (!mdata.hasLineNumberData() || !mdata.hasSrcFileData()) {
      System.out.println("no collected line coverage data found in any of the data files [Diff output will not include coverage data]");
      return null;
    }

    final IReportDataModel model = IReportDataModel.Factory.create (mdata, cdata);
    m_view = model.getView (IReportDataView.HIER_SRC_VIEW);

    verboseOut("  merged metadata contains " + mdata.size() + " entries");
    verboseOut("  merged coverage data contains " + cdata.size() + " entries");

    return m_view;
  }

  private BufferedReader getDiffOutputReader()
          throws IOException, SVNException {
    File workspaceRoot = getProject().getBaseDir();

    File diffFile = new File(outputPath, "svn.diff");

    // Most often this will be 'BASE' but it could also be 'PREVIOUS'
    SVNRevision baseRevision = SVNRevision.parse(fromRevision);
    System.out.println("Doing coverage diff from revision: " + baseRevision.toString());

    ourClientManager.getDiffClient().doDiff(workspaceRoot, baseRevision, 
            workspaceRoot, SVNRevision.WORKING, SVNDepth.INFINITY, false,
            new FileOutputStream(diffFile), null);

    return new BufferedReader(new InputStreamReader(new FileInputStream(
                                                             diffFile)));
  }

  private void processDiffOutput(BufferedReader diffOutput,
                                        IReportDataView emmaDataView)
      throws IOException {

    File file = new File(outputPath, "index.html");
    BufferedWriter writer =
        new BufferedWriter (new OutputStreamWriter (
            new FileOutputStream (file), ENCODING), IO_BUF_SIZE);
    HTMLWriter out = new HTMLWriter(writer);

    System.out.println("Writing report to [" + file.toString() + "]");

    String title = "Coverage Diff Report (generated ";
    title = title + new Date(System.currentTimeMillis ());
    title = title + " )";

    HTMLDocument page = new HTMLDocument (title, ENCODING);
    page.addStyle (CSS);

    String line = diffOutput.readLine();
    ArrayList<String> diffOutputFile = new ArrayList<String>();

    while(line != null)
    {
      //Diffed file
      if(line.length() >6 && line.substring(0, 6).equals("Index:"))
      {
        processDiffOutputFile(page, diffOutputFile, emmaDataView);
        diffOutputFile = new ArrayList<String>();
        diffOutputFile.add(line);
      }
      else
      {
        diffOutputFile.add(line);
      }

      line = diffOutput.readLine();
    }
    processDiffOutputFile(page, diffOutputFile, emmaDataView);

    IElementList overallStats = new ElementList();

    final IElement statTitle = IElement.Factory.create (Tag.Hs[1]);
    statTitle.setText("OVERALL STATS SUMMARY", true);

    overallStats.add(statTitle);

    final HTMLTable statsTable = new HTMLTable (null, null, null, "0");
    statsTable.setClass ("it");
    {
      HTMLTable.IRow row = statsTable.newRow ();
      row.newCell ().setText ("svn diff arg(s):", true);
      row.newCell ().setText ("" + diffPath.toString(), true);

      row = statsTable.newRow ();
      row.newCell ().setText ("total files modified:", true);
      row.newCell ().setText ("" + emmaSrcMap.keySet().size(), false);

      Double[] overallModCoverage = new Double[4];
      overallModCoverage[COVERED_MOD_EXE_LINES] = 0.0;
      overallModCoverage[MOD_EXE_LINES] = 0.0;
      overallModCoverage[MOD_LINES] = 0.0;
      overallModCoverage[DEL_LINES] = 0.0;

      Double[] modCoverage;
      for (Double[] doubles : modCoverageMap.values()) {
        modCoverage = doubles;

        if (modCoverage != null) {
          overallModCoverage[COVERED_MOD_EXE_LINES] += modCoverage[COVERED_MOD_EXE_LINES];
          overallModCoverage[MOD_EXE_LINES] += modCoverage[MOD_EXE_LINES];
          overallModCoverage[MOD_LINES] += modCoverage[MOD_LINES];
          overallModCoverage[DEL_LINES] += modCoverage[DEL_LINES];
        }
      }
      String modCoverageStr = "";
      if(overallModCoverage[MOD_EXE_LINES] > 0)
      {
        modCoverageStr = String.format("%d%% (%.1f/%.1f)",
            (int)(overallModCoverage[COVERED_MOD_EXE_LINES]/overallModCoverage[MOD_EXE_LINES]*100),
            overallModCoverage[COVERED_MOD_EXE_LINES],
            overallModCoverage[MOD_EXE_LINES]);
      }
      else
      {
        modCoverageStr = String.format("%d%% (%.1f/%.1f)", 100,
            overallModCoverage[COVERED_MOD_EXE_LINES],
            overallModCoverage[MOD_EXE_LINES]);
      }

      row = statsTable.newRow ();
      row.newCell ().setText ("total lines modified:", true);
      row.newCell ().setText ("" + overallModCoverage[MOD_LINES].intValue(), true);
      row = statsTable.newRow ();
      row.newCell ().setText ("total lines removed:", true);
      row.newCell ().setText ("" + overallModCoverage[DEL_LINES].intValue(), true);
      row = statsTable.newRow ();
      row.newCell ().setText ("coverage for modified executable lines:", true);
      row.newCell ().setText ("" + modCoverageStr, true);
    }

    overallStats.add(statsTable);

    final IElement coverageTitle = IElement.Factory.create (Tag.Hs[1]);
    statTitle.setText("OVERALL DIFF SUMMARY", true);

    overallStats.add(coverageTitle);

    HTMLTable summaryTable = new HTMLTable ("100%", null, null, "0");
    if(emmaDataView != null)
    {
      addHeaderRow(emmaDataView.getRoot(), summaryTable, true);
    }
    else
    {
      addHeaderRow(null, summaryTable, true);
    }

    Set<Map.Entry<String, SrcFileItem>> items = emmaSrcMap.entrySet();
    boolean odd = true;
    int count = 0;

    for (Map.Entry<String, SrcFileItem> item : items) {

      if (item != null) {
        final String fileName = item.getKey();
        final SrcFileItem srcFileItem = item.getValue();
        final Double[] modCoverage = modCoverageMap.get(fileName);

        addItemRow(fileName, srcFileItem, modCoverage, odd, summaryTable,
            "s" + count, true, true);

        odd = !odd;
        count++;
      }
    }

    overallStats.add(summaryTable);

    page.setHeader(overallStats);

    page.emit(out);
    out.flush();
  }

  private void processDiffOutputFile(HTMLDocument html,
                                            ArrayList<String> diffFile,
                                            IReportDataView emmaDataView)
      throws IOException
  {
    if(diffFile.size() <= 0)
    {
      return;
    }

    Double[] modCoverage = new Double[4];
    modCoverage[COVERED_MOD_EXE_LINES] = 0.0;
    modCoverage[MOD_EXE_LINES] = 0.0;
    modCoverage[MOD_LINES] = 0.0;
    modCoverage[DEL_LINES] = 0.0;

    String fileHeader = diffFile.get(0);
    verboseOut("fileHeader: " + diffFile);

    //Try to get the package information if its a Java file
    File srcFilePath = new File(fileHeader.substring(7));
    SrcFileItem emmaSourceItem = null;
    if(srcFilePath.isFile())
    {
      FileInputStream srcFile = new FileInputStream(srcFilePath);
      String srcFilePackage = parseJavaPackage(srcFile);
      if(emmaDataView != null)
      {
        emmaSourceItem = getEmmaSrcItem(emmaDataView.getRoot(),
                  srcFilePackage, srcFilePath.getName());
      }
    }


    //Figure out the flag for the working copy.
    String workingCopyFlag = null;
    String otherCopyFlag = null;

    String firstFileLine = diffFile.get(2);
    String secondFileLine = diffFile.get(3);
    verboseOut("firstFileLine=" + firstFileLine);
    verboseOut("secondFileLine=" + secondFileLine);
    String revisionStr = "unknown";

    // Skip over binary files
    if (firstFileLine.contains("Cannot display")) {
      return;
    }

    HTMLTable srcTable = null;

    if(firstFileLine.endsWith("(working copy)"))
    {
      workingCopyFlag = firstFileLine.substring(0, 1);
    }
    else
    {
      otherCopyFlag = firstFileLine.substring(0, 1);
      revisionStr = firstFileLine.substring(firstFileLine.lastIndexOf("("));
    }

    if(secondFileLine.endsWith("(working copy)"))
    {
      workingCopyFlag = secondFileLine.substring(0, 1);
    }
    else
    {
      otherCopyFlag = secondFileLine.substring(0, 1);
      revisionStr = secondFileLine.substring(secondFileLine.lastIndexOf("("));
    }

    if(firstFileLine.endsWith("(revision 0)") ||
        secondFileLine.endsWith("(revision 0)"))
    {
      workingCopyFlag = "+";
      otherCopyFlag = "-";
    }

    if(workingCopyFlag == null || otherCopyFlag == null)
    {
      throw new IOException("Error occurred while parsing diff output." + EOL +
        "firstFileLine= '" + firstFileLine + "'" + EOL +
        "secondFileLine= '" + secondFileLine + "'");
    }
    else
    {
      srcTable = new HTMLTable ("100%", null, null, "0");
      srcTable.setClass("s");

      ArrayList<String> diffOutputChunk = new ArrayList<String>();
      Double[] chunkModCoverage;

      for(int i = 4; i < diffFile.size(); i++)
      {
        //Found a chunk indicator.
        if(diffFile.get(i).startsWith("@@"))
        {
          chunkModCoverage = processDiffOutputFileChunk(srcTable, diffOutputChunk, workingCopyFlag,
                otherCopyFlag, emmaSourceItem);

          if(chunkModCoverage != null)
          {
            modCoverage[COVERED_MOD_EXE_LINES] += chunkModCoverage[COVERED_MOD_EXE_LINES];
            modCoverage[MOD_EXE_LINES] += chunkModCoverage[MOD_EXE_LINES];
            modCoverage[MOD_LINES] += chunkModCoverage[MOD_LINES];
            modCoverage[DEL_LINES] += chunkModCoverage[DEL_LINES];
          }

          diffOutputChunk = new ArrayList<String>();
          diffOutputChunk.add(diffFile.get(i));
        }
        //Not any of the above so this line must be diffed text
        else
        {
          diffOutputChunk.add(diffFile.get(i));
        }
      }

      //Finishing process whatever we have queued up
      chunkModCoverage = processDiffOutputFileChunk(srcTable, diffOutputChunk, workingCopyFlag,
          otherCopyFlag, emmaSourceItem);
      if(chunkModCoverage != null)
      {
        modCoverage[COVERED_MOD_EXE_LINES] += chunkModCoverage[COVERED_MOD_EXE_LINES];
        modCoverage[MOD_EXE_LINES] += chunkModCoverage[MOD_EXE_LINES];
        modCoverage[MOD_LINES] += chunkModCoverage[MOD_LINES];
        modCoverage[DEL_LINES] += chunkModCoverage[DEL_LINES];
      }
    }

    final IElement a = IElement.Factory.create (Tag.A);
    a.getAttributes ().set (Attribute.NAME, "s" + emmaSrcMap.keySet().size());

    html.add(a);

    final IElement itemname = IElement.Factory.create (Tag.SPAN);
    {
      itemname.setText (srcFilePath.toString(), true);
      itemname.setClass ("in");
    }

    final IElementList title = new ElementList ();
    {
      title.add (new Text ("DIFF SUMMARY FOR SOURCE FILE [", true));
      title.add (itemname);
      title.add (new Text ("] against ", true));
      title.add (new Text (revisionStr, true));
    }

    html.addH (1, title, null);

    if(emmaSourceItem != null)
    {
      final HTMLTable coverageTable = new HTMLTable ("100%", null, null, "0");
      addHeaderRow(emmaSourceItem, coverageTable, false);
      addItemRow(srcFilePath.toString(), emmaSourceItem, modCoverage, false, coverageTable, null, false, false);

      html.add(coverageTable);

      html.addEmptyP();
    }
    else
    {
      html.addH(2, "Coverage Information Not Available (e.g. file is not in src/, is not java, is an interface, or was deleted)", null);
    }

    if(srcTable != null)
    {
      html.add(srcTable);
    }

    emmaSrcMap.put(srcFilePath.toString(), emmaSourceItem);
    modCoverageMap.put(srcFilePath.toString(), modCoverage);
  }

  private Double[] processDiffOutputFileChunk(HTMLTable table,
                                                 ArrayList<String> diffChunk,
                                                 String workingCopyFlag,
                                                 String otherCopyFlag,
                                                 SrcFileItem emmaSrcItem)
  {

    if(diffChunk.size() <= 0)
    {
      return null;
    }

    int workingCopyBegin;
    int workingCopyRange;
    int otherCopyBegin;
    int otherCopyRange;

    Double[] modCoverage = new Double[4];
    modCoverage[COVERED_MOD_EXE_LINES] = 0.0;
    modCoverage[MOD_EXE_LINES] = 0.0;
    modCoverage[MOD_LINES] = 0.0;
    modCoverage[DEL_LINES] = 0.0;

    IntObjectMap lineCoverageMap = null;
    if(emmaSrcItem != null)
    {
      lineCoverageMap = emmaSrcItem.getLineCoverage ();
    }

    String chunkHeader = diffChunk.get(0);

    int workingCopyBeginIdx = chunkHeader.indexOf(workingCopyFlag);
    int workingCopyCommaIdx = chunkHeader.indexOf(",", workingCopyBeginIdx);
    int workingCopyEndIdx = chunkHeader.indexOf(" ", workingCopyCommaIdx);
    int otherCopyBeginIdx = chunkHeader.indexOf(otherCopyFlag);
    int otherCopyCommaIdx = chunkHeader.indexOf(",", otherCopyBeginIdx);
    int otherCopyEndIdx = chunkHeader.indexOf(" ", otherCopyCommaIdx);
    workingCopyBegin = Integer.parseInt(
        chunkHeader.substring(workingCopyBeginIdx + 1, workingCopyCommaIdx));
    workingCopyRange = Integer.parseInt(
        chunkHeader.substring(workingCopyCommaIdx + 1, workingCopyEndIdx));
    otherCopyBegin = Integer.parseInt(
        chunkHeader.substring(otherCopyBeginIdx + 1, otherCopyCommaIdx));
    otherCopyRange = Integer.parseInt(
        chunkHeader.substring(otherCopyCommaIdx + 1, otherCopyEndIdx));

    String chunkLine;
    SrcFileItem.LineCoverageData lCoverageData = null;
    int workingCopyLine = workingCopyBegin;
    int otherCopyLine = otherCopyBegin;

    final HTMLTable.IRow chunkRow = table.newTitleRow();
    final HTMLTable.ICell chunkCell = chunkRow.newCell();
    chunkCell.setColspan(2);
    chunkCell.setText("Lines " + workingCopyBegin + " - " +
        String.valueOf(workingCopyLine + workingCopyRange), true);

    for(int i = 1; i < diffChunk.size(); i++)
    {
      chunkLine = diffChunk.get(i);

      if(lineCoverageMap != null)
      {
        lCoverageData = (SrcFileItem.LineCoverageData) lineCoverageMap.get (workingCopyLine);
      }

      final HTMLTable.IRow srcRow = table.newRow();
      final HTMLTable.ICell lineNumCell = srcRow.newCell();
      final HTMLTable.ICell lineTxtCell = srcRow.newCell();

      if (chunkLine.length() == 0) {
        lineTxtCell.setText(" ", true);
      } else {
        lineTxtCell.setText(chunkLine.substring(1), true);
      }

      //This line is either a modified line or a unchanged line
      if(!chunkLine.startsWith(otherCopyFlag))
      {
        lineNumCell.setText(String.valueOf(workingCopyLine), true);

        //Determine if this line is a modified line or a unchange line
        if(chunkLine.startsWith(workingCopyFlag))
        {
          lineNumCell.setClass("dm");
          modCoverage[MOD_LINES] ++;

          if(lCoverageData != null)
          {
            modCoverage[MOD_EXE_LINES] ++;
            switch(lCoverageData.m_coverageStatus)
            {
              case SrcFileItem.LineCoverageData.LINE_COVERAGE_ZERO:
                lineTxtCell.setClass ("cz");
                break;

              case SrcFileItem.LineCoverageData.LINE_COVERAGE_PARTIAL:
                lineTxtCell.setClass ("cp");
                modCoverage[COVERED_MOD_EXE_LINES] += 0.5;
                break;

              case SrcFileItem.LineCoverageData.LINE_COVERAGE_COMPLETE:
                lineTxtCell.setClass ("cc");
                modCoverage[COVERED_MOD_EXE_LINES] ++;
                break;
              default:
            }
          }
        }
        else
        {
          lineNumCell.setClass("ds");
        }

      }
      else
      {
        lineNumCell.setClass("dd");
        lineNumCell.setText(String.valueOf(otherCopyLine), true);
        lineTxtCell.setClass("ddt");
        modCoverage[DEL_LINES] ++;
      }

      if(!chunkLine.startsWith(otherCopyFlag))
      {
        workingCopyLine++;
      }
      if(!chunkLine.startsWith(workingCopyFlag))
      {
        otherCopyLine++;
      }
    }

    return modCoverage;
  }

  private String parseJavaPackage(FileInputStream srcFile)
      throws IOException {

    BufferedReader srcFileReader = new BufferedReader(
        new InputStreamReader(srcFile));

    String line = srcFileReader.readLine();
    while(line != null)
    {
      int beginIdx = line.indexOf("package");
      if(beginIdx > -1)
      {
        int endIdx = line.indexOf(";", beginIdx);
        if(endIdx > -1)
        {
          return line.substring(beginIdx + 7, endIdx).trim();
        }
      }
      line = srcFileReader.readLine();
    }

    return null;
  }

  private  SrcFileItem getEmmaSrcItem(IItem rootItem,
                                      String srcPackageName,
                                      String srcFileName)
  {
    if(rootItem == null || srcPackageName == null || srcFileName == null)
    {
      return null;
    }

    for(Iterator packages = rootItem.getChildren(); packages.hasNext();)
    {
      IItem packageItem = (IItem)packages.next();
      if(packageItem.getName().equals(srcPackageName))
      {
        for(Iterator sources = packageItem.getChildren(); sources.hasNext();)
        {
          SrcFileItem sourceItem = (SrcFileItem)sources.next();
          if(sourceItem.getName().equals(srcFileName))
          {
            return sourceItem;
          }
        }
      }
    }
    return null;
  }

  private void addHeaderRow (final IItem item, final HTMLTable table, boolean includeName)
  {

    // header row:
    final HTMLTable.IRow header = table.newTitleRow ();

    if(includeName)
    {
      final HTMLTable.ICell nameCell = header.newCell();
      nameCell.setText("File", true);
    }

    for (int c = 1; c <= 4; ++ c)
    {
      IItemAttribute attr = null;

      if(item != null)
      {
        attr = item.getAttribute (c, 0);
      }

      if (attr != null)
      {
        final HTMLTable.ICell cell = header.newCell ();

        cell.setText (attr.getName (), true);
      }
      else
      {
        final HTMLTable.ICell cell = header.newCell ();
        cell.setText (" ", true);
      }

    }

    if(item != null)
    {
      final HTMLTable.ICell cell = header.newCell();
      cell.setText("mod lines, %", true);
    }
    else
    {
      final HTMLTable.ICell cell = header.newCell ();
      cell.setText (" ", true);
    }

  }

  /*
     * No header row, just data rows.
     */
  private void addItemRow (final String fileName,
                           final IItem item,
                           final Double[] modCoverage,
                           final boolean odd,
                           final HTMLTable table,
                           final String nameHREF,
                           final boolean anchor,
                           final boolean includeName)
  {
    final HTMLTable.IRow row = table.newRow ();
    if (odd) row.setClass ("o");

    if(includeName)
    {
      final HTMLTable.ICell nameCell = row.newCell();
      if(nameHREF != null)
      {
        final String fullHREFName = anchor ? "#".concat (nameHREF) : nameHREF;
        nameCell.add(new HyperRef(fullHREFName, fileName, true));
      }
      else
      {
        nameCell.setText(fileName, true);
      }
    }

    final StringBuffer buf = new StringBuffer (11);

    for (int c = 1; c <=4; ++ c)
    {
      IItemAttribute attr = null;

      if(item != null)
      {
        attr = item.getAttribute (c, 0);
      }

      if (attr != null)
      {
        final HTMLTable.ICell cell = row.newCell ();


        //final boolean fail = (m_metrics [attrID] > 0) && ! attr.passes (item, m_metrics [attrID]);

        buf.setLength (0);
        attr.format (item, buf);

        cell.setText (buf.toString (), true);
        //if (fail) cell.setClass (CSS_DATA_HIGHLIGHT);

      }
      else
      {

        final HTMLTable.ICell cell = row.newCell ();
        cell.setText (" ", true);
      }
    }

    if(item != null && modCoverage != null)
    {
      String modCoverageStr = "";
      if(modCoverage[1] > 0)
      {
        modCoverageStr = String.format("%d%% (%.1f/%.1f)",
            (int)(modCoverage[COVERED_MOD_EXE_LINES]/modCoverage[MOD_EXE_LINES]*100),
            modCoverage[COVERED_MOD_EXE_LINES], modCoverage[MOD_EXE_LINES]);
      }
      else
      {
        modCoverageStr = String.format("%d%% (%.1f/%.1f)", 100,
            modCoverage[COVERED_MOD_EXE_LINES],
            modCoverage[MOD_EXE_LINES]);
      }

      final HTMLTable.ICell cell = row.newCell();
      cell.setText(modCoverageStr, true);
    }
    else
    {
      final HTMLTable.ICell cell = row.newCell ();
      cell.setText (" ", true);
    }
  }

  // Enable this with -Dtest.diff.verbose=true from the commandline
  private void verboseOut(Object msg)
  {
    if (verbose)
    {
      System.out.println(msg.toString());
    }
  }
}
