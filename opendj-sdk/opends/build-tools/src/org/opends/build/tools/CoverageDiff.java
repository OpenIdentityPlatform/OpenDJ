package org.opends.build.tools;

import com.vladium.emma.*;
import com.vladium.emma.report.*;
import com.vladium.emma.data.*;
import com.vladium.util.IntObjectMap;

import java.io.*;
import java.util.Iterator;
import java.util.ArrayList;

public class CoverageDiff {


  private static boolean verbose = false;

  public static void main(String[] args) {

    if(args.length < 1 || args[0] == null)
    {
      System.out.println("Please specify emma data location");
      return;
    }
    IReportDataView emmaDataView = null;
    try
    {
      emmaDataView = loadEmmaData(new File(args[0].trim()));

    }
    catch(IOException ie)
    {
      System.out.println("An error occured while loading EMMA data: " + ie.toString());
    }

    if(emmaDataView == null)
    {
      System.out.println(System.in);
    }
    else
    {
      try
      {
        processDiffOutput(new BufferedReader(new InputStreamReader(System.in)), emmaDataView);
      }
      catch(IOException ie)
      {
        System.out.println("An error occured while processing diff output: " + ie.toString());
      }
    }
  }


  private static IReportDataView loadEmmaData(File emmaCoverageDataDir) throws IOException {
    File[] emmaCoverageDataFiles = emmaCoverageDataDir.listFiles();
    int emmaCoverageDataFileCount = 0;
    IReportDataView m_view = null;
    IMetaData mdata = null;
    ICoverageData cdata = null;

    if(emmaCoverageDataFiles == null || emmaCoverageDataFiles.length <= 0)
    {
      throw new IOException("No EMMA data files found");
    }

    if (verbose) System.out.println("processing input files ...");

    final long start = verbose ? System.currentTimeMillis() : 0;

    // merge all data files:

    for (int f = 0; f < emmaCoverageDataFiles.length; ++f) {
      final File dataFile = emmaCoverageDataFiles[f];
      if (verbose)
        System.out.println("processing input file [" + dataFile.getAbsolutePath() + "] ...");

      final IMergeable[] fileData = DataFactory.load(dataFile);

      final IMetaData _mdata = (IMetaData) fileData[DataFactory.TYPE_METADATA];
      if (_mdata != null) {
        if (verbose)
          System.out.println("  loaded " + _mdata.size() + " metadata entries");

        if (mdata == null)
          mdata = _mdata;
        else
          mdata = (IMetaData) mdata.merge(_mdata); // note: later datapath entries override earlier ones
      }

      final ICoverageData _cdata = (ICoverageData) fileData[DataFactory.TYPE_COVERAGEDATA];
      if (_cdata != null) {
        if (verbose)
          System.out.println("  loaded " + _cdata.size() + " coverage data entries");

        if (cdata == null)
          cdata = _cdata;
        else
          cdata = (ICoverageData) cdata.merge(_cdata); // note: later datapath entries override earlier ones
      }

      ++emmaCoverageDataFileCount;
    }

    if (verbose) {
      final long end = System.currentTimeMillis();

      System.out.println(emmaCoverageDataFileCount + " file(s) read and merged in " + (end - start) + " ms");
    }

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

    if (verbose) {
      if (mdata != null) {
        System.out.println("  merged metadata contains " + mdata.size() + " entries");
      }

      if (cdata != null) {
        System.out.println("  merged coverage data contains " + cdata.size() + " entries");
      }
    }

    return m_view;
  }

  private static BufferedReader getDiffOutputReader() throws IOException {

    Process child = Runtime.getRuntime().exec("svn diff");
    InputStream diffOutputStream = child.getInputStream();
    BufferedReader diffOutput = new BufferedReader(
        new InputStreamReader(diffOutputStream));
    return diffOutput;

  }

  private static void processDiffOutput(BufferedReader diffOutput,
                                        IReportDataView emmaDataView)
      throws IOException {

    String line = diffOutput.readLine();
    ArrayList<String> diffOutputFile = new ArrayList<String>();

    while(line != null)
    {
      //Diffed file
      if(line.length() >6 && line.substring(0, 6).equals("Index:"))
      {
        processDiffOutputFile(diffOutputFile, emmaDataView);
        diffOutputFile = new ArrayList<String>();
        diffOutputFile.add(line);
      }
      else
      {
        diffOutputFile.add(line);
      }

      line = diffOutput.readLine();
    }
    processDiffOutputFile(diffOutputFile, emmaDataView);
  }

  private static void processDiffOutputFile(ArrayList<String> diffFile,
                                            IReportDataView emmaDataView)
      throws IOException
  {
    if(diffFile.size() <= 0)
    {
      return;
    }
    
    String fileHeader = diffFile.get(0);

    File srcFilePath = new File(fileHeader.substring(7));
    FileInputStream srcFile = new FileInputStream(srcFilePath);
    String srcFilePackage = parseJavaPackage(srcFile);
    SrcFileItem emmaSourceItem = getEmmaSrcItem(emmaDataView.getRoot(),
                  srcFilePackage, srcFilePath.getName());

    if(emmaSourceItem == null)
    {
      System.out.println(fileHeader);
      System.out.println("Coverage: Not Available");
      for(int i = 1; i < diffFile.size(); i++)
      {
        System.out.println(diffFile.get(i));
      }
    }
    else
    {
      System.out.println(fileHeader);

      System.out.print("Coverage: ");
      String name;
      StringBuffer buf = new StringBuffer();
      for(int i = 1; i <= 4; i++)
      {
        buf.setLength(0);
        emmaSourceItem.getAttribute(i, 0).format(emmaSourceItem, buf);
        name = emmaSourceItem.getAttribute(i, 0).getName();
        System.out.print(name);
        for(int j = 0; j < buf.length() - name.length() + 1; j++)
        {
          System.out.print(" ");
        }
      }
      System.out.print("\n          ");
      for(int i = 1; i <= 4; i++)
      {
        buf.setLength(0);
        emmaSourceItem.getAttribute(i, 0).format(emmaSourceItem, buf);
        System.out.print(buf + " ");
      }
      System.out.println("");

      System.out.println(diffFile.get(1));

      //Figure out the flag for the working copy.
      String workingCopyFlag = null;
      String otherCopyFlag = null;

      String firstFileLine = diffFile.get(2);
      String secondFileLine = diffFile.get(3);
      System.out.println(firstFileLine);
      System.out.println(secondFileLine);

      if(firstFileLine.endsWith("(working copy)"))
      {
        workingCopyFlag = firstFileLine.substring(0, 1);
      }
      else
      {
        otherCopyFlag = firstFileLine.substring(0, 1);
      }

      if(secondFileLine.endsWith("(working copy)"))
      {
        workingCopyFlag = secondFileLine.substring(0, 1);
      }
      else
      {
        otherCopyFlag = secondFileLine.substring(0, 1);
      }

      if(firstFileLine.endsWith("(revision 0)") &&
          secondFileLine.endsWith("(revision 0)"))
      {
        workingCopyFlag = "+";
        otherCopyFlag = "-";
      }

      if(workingCopyFlag == null || otherCopyFlag == null ||
          srcFilePackage == null)
      {
        for(int i = 4; i < diffFile.size(); i++)
        {
          System.out.println(diffFile.get(i));
        }
      }
      else
      {

        ArrayList<String> diffOutputChunk = new ArrayList<String>();

        for(int i = 4; i < diffFile.size(); i++)
        {
          //Found a chunk indicator.
          if(diffFile.get(i).startsWith("@@"))
          {
            processDiffOutputFileChunk(diffOutputChunk, workingCopyFlag,
                otherCopyFlag, emmaSourceItem);
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
        processDiffOutputFileChunk(diffOutputChunk, workingCopyFlag,
            otherCopyFlag, emmaSourceItem);
      }
    }

  }

  private static void processDiffOutputFileChunk(ArrayList<String> diffChunk,
                                                 String workingCopyFlag,
                                                 String otherCopyFlag,
                                                 SrcFileItem emmaSrcItem)
  {

    if(diffChunk.size() <= 0)
    {
      return;
    }

    int workingCopyBegin;
    int workingCopyRange;
    int otherCopyBegin;
    int otherCopyRange;

    IntObjectMap lineCoverageMap = null;
    if(emmaSrcItem != null)
    {
      lineCoverageMap = emmaSrcItem.getLineCoverage ();
    }

    String chunkHeader = diffChunk.get(0);
    System.out.println(chunkHeader);

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
    int workingCopyLineIncrement = 0;
    int otherCopyLineIncrement = 0;
    for(int i = 1; i < diffChunk.size(); i++)
    {
      chunkLine = diffChunk.get(i);
      //System.out.print(workingCopyBegin + workingCopyLineIncrement + " ");
      if(lineCoverageMap != null)
      {
        lCoverageData = (SrcFileItem.LineCoverageData) lineCoverageMap.get (workingCopyBegin + workingCopyLineIncrement);
      }

      if(!chunkLine.startsWith(otherCopyFlag) && lCoverageData != null)
      {

        switch(lCoverageData.m_coverageStatus)
        {
          case SrcFileItem.LineCoverageData.LINE_COVERAGE_ZERO:
            System.out.println(chunkLine.charAt(0) + "N" + chunkLine.substring(1));
            break;
          case SrcFileItem.LineCoverageData.LINE_COVERAGE_PARTIAL:
            System.out.println(chunkLine.charAt(0) + "P" + chunkLine.substring(1));
            break;
          case SrcFileItem.LineCoverageData.LINE_COVERAGE_COMPLETE:
            System.out.println(chunkLine.charAt(0) + "C" + chunkLine.substring(1));
            break;
          default:
            System.out.println(chunkLine.charAt(0) + "U" + chunkLine.substring(1));
        }
      }
      else
      {
        System.out.println(chunkLine.charAt(0) + " " + chunkLine.substring(1));
      }

      if(!chunkLine.startsWith(otherCopyFlag))
      {
        workingCopyLineIncrement++;
      }
      if(!chunkLine.startsWith(workingCopyFlag))
      {
        otherCopyLineIncrement++;
      }
    }
  }

  private static String parseJavaPackage(FileInputStream srcFile)
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
          return  line.substring(beginIdx + 7, endIdx).trim();
        }
      }
      line = srcFileReader.readLine();
    }

    return null;
  }

  private static SrcFileItem getEmmaSrcItem(IItem rootItem,
                                      String srcPackageName,
                                      String srcFileName)
  {
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
}
