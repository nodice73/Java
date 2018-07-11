import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.measure.*;

import java.io.*;
import java.util.*;

import org.fhcrc.honeycomb.hcimaging.*;
import org.fhcrc.honeycomb.hcimaging.hcimage.*;

/**
 * Analyzes fluorescent images.
 * @author Adam Waite
 * @version $Id$
 *
 * Requires the hcimage library.
 */
public class Bioact3_ implements PlugIn {
  private static final String[] thresholds = 
    {"MaxEntropy","Default","Huang","RenyiEntropy"};
  private static final String[] wls = {"WL0","WL1","WL2","WL3","WL4","WL5"};

  private int start_slice = 1;
  private double max_slices = 150;
  private String thresh = thresholds[0];
  private double area_threshold = 50;
  private String wl = wls[1];
  private boolean isBatch = true;
  private boolean saveIntermediates = false;

  private RoiManager rm;
  private ImagePlus img;
  private String stack_dir;

  private double rb_radius = 50.0;
  private String rb_params = "rolling="+rb_radius+" sliding";
  private String rb_stack = rb_params+" stack";

  private HCExperiment hce;
  private HCImage hci;

  private File project_path;
  private File results_path;
  private File intermediates_path;
  private final String results_name = "bioact_results.txt";
  private final String intermediates_folder = "intermediates";
  private PrintWriter out;

  private int nSlices;
  private int total_area;
  private Roi[] selections;

  public void run(String arg) {
    if (arg.equals("")) {
      showDialog();
      if (isBatch) {
        project_path = new File(IJ.getDirectory("Choose a directory."));

        try {
          hce = new HCExperiment(project_path);
        } catch (FileNotFoundException e) {
          IJ.handleException(e);
        }

        results_path = project_path;
        intermediates_path = new File(project_path, intermediates_folder);

        if (saveIntermediates) {
            intermediates_path.mkdir();
        }

        writeHeader();

        for (String pos : hce.getPositions()) {
          java.util.List<HCImage> images = hce.getTimepoints(pos,wl);
          hce.setStartTime(images.get(0));

          stack_dir = project_path+"/"+pos+"/"+wl+"/";

          IJ.run("Image Sequence...", "open="+stack_dir+
              " number="+max_slices+ " starting=1 increment=1 "+
              "file=[.tif] sort");
          IJ.run("Set Scale...", 
              "distance=0 known=0 pixel=1 unit=pixel global");

          img = IJ.getImage();
          //img.hide();
          bioact(images);

          img = IJ.getImage();
          img.changes = false;
          img.close();
        }
      } else {
        img = IJ.getImage();
        File wl_path  = new File(IJ.getDirectory("image"));
        File pos_path = wl_path.getParentFile(); 
        project_path  = pos_path.getParentFile();

        results_path = wl_path;
        stack_dir    = wl_path.toString();

        try {
          hce = new HCExperiment(project_path);
        } catch (FileNotFoundException e) {
          IJ.handleException(e);
        }

        java.util.List<HCImage> images = 
          hce.getTimepoints(pos_path.getName(),wl_path.getName());
        writeHeader();

        bioact(images);
      }
    } else {
      project_path = new File(arg);
    }
    out.close();
  }

  private void bioact(java.util.List<HCImage> images) {
    nSlices    = img.getNSlices();
    total_area = img.getWidth()*img.getHeight();
    makeStackSelections(img);

    img.changes = false;
    img.close();

    IJ.run("Image Sequence...", "open="+stack_dir+"/"+" number="+max_slices+
           " starting=1 increment=1 "+"file=[.tif] sort ");
    IJ.run("Set Scale...", "distance=0 known=0 pixel=1 unit=pixel global");
    //IJ.run("Subtract Background...", rb_stack);

    img = IJ.getImage();

    double running_background_mean = 0;
    double final_running_background_mean = 0;
    boolean final_bg = false;
    for (int i=1; i<=nSlices; i++) {
      IJ.run("Select None");
      HCImage image = images.get(i-1);
      img.setSlice(i);
      //IJ.log("Slice: "+i);

      ImageStatistics all_stats = img.getStatistics();
      double mean_intensity = all_stats.mean;

      img.setRoi(selections[i-1]);
      if (img.getRoi()==null)  IJ.run("Select All");

      //get background stats
      ImageStatistics bg_stats = img.getStatistics();

      // get foreground stats
      IJ.run("Make Inverse");
      ImageStatistics fg_stats = img.getStatistics();

      // calculate % area occupied by cells
      double cell_area_percent = 100*fg_stats.area/total_area;
		
      // get the exposure time
	  double expTime = image.getExposureTime();
	   
      double background;
      if (cell_area_percent < area_threshold) {
        // if cells DO NOT yet cover the percentage
        // specified by the area threshold,
        // calculate the bg intensity
        background = bg_stats.mean;
      } else {
        if (!final_bg) {
          // if cells DO cover a greater percentage of the area than
          // specified by the area threshold, use previous background.
          final_running_background_mean = running_background_mean;
        }
        background = final_running_background_mean;
        final_bg = true;
      }	
		
      // Calculate the running mean background intensity
      double delta = bg_stats.mean - running_background_mean;
      running_background_mean += delta/i;

      //IJ.log("mean intensity: " + all_stats.mean);
      //IJ.log("mean bg intensity: " + bg_stats.mean);
      //IJ.log("bg area: " + bg_stats.area);
      //IJ.log("fg area: " + fg_stats.area);
      //IJ.log("running bg: " + running_background_mean);

      //subtract mean background from mean intensity and multiply by area
      double bs_intden = (all_stats.mean-background)*total_area;

      // subtract mean background from mean intensity and multiply by area,
      // then normalize to exposure time.
      double norm_bs_intden = (all_stats.mean-background)*total_area/expTime;
      //IJ.log("BS INTDEN: "+bs_intden);
      //IJ.log("\n");

      // print the results
      out.println(image.getPosition()+"\t"+image.getWavelength()+"\t"+i+"\t"+
                  hce.elapsedTime(image,'m')+"\t"+norm_bs_intden+"\t"+
                  expTime+"\t"+bs_intden+"\t"+background+"\t"+mean_intensity+
                  "\t"+rb_radius+"\t"+thresh+"\t"+area_threshold+"\t"+
                  cell_area_percent+"\t"+total_area+"\t");
    }
  }

  private void makeStackSelections(ImagePlus img) {
    img.setSlice(start_slice);

    String img_name = "";
    if (saveIntermediates) {
        img_name = (new File(IJ.getDirectory("image"))).getParentFile().getName();
    }

    IJ.run("Select None");
    IJ.run("Gaussian Blur...","sigma=1 stack");
    if (saveIntermediates) {
        IJ.saveAs("Tiff",
               (new File(intermediates_path,
                         img_name+"_blurred.tif")).toString());
    }

    IJ.run("Subtract Background...", rb_stack);
    if (saveIntermediates) {
        IJ.saveAs("Tiff",
               (new File(intermediates_path,
                         img_name+"_subtracted.tif")).toString());
    }
    //IJ.run("blah");

    IJ.run("8-bit");
    if (saveIntermediates) {
        IJ.saveAs("Tiff",
               (new File(intermediates_path,
                         img_name+"_8bit.tif")).toString());
    }

    IJ.run("Maximum...", "radius=3 stack");
    if (saveIntermediates) {
        IJ.saveAs("Tiff",
               (new File(intermediates_path,
                         img_name+"_maximum.tif")).toString());
    }

    IJ.setAutoThreshold(img, thresh+" dark");

    IJ.run("Convert to Mask"," black");
    if (saveIntermediates) {
        IJ.saveAs("Tiff",
               (new File(intermediates_path,
                         img_name+"_mask.tif")).toString());
    }

    IJ.run("Fill Holes","stack");
    if (saveIntermediates) {
        IJ.saveAs("Tiff",
               (new File(intermediates_path,
                         img_name+"_mask_filled.tif")).toString());
    }

    IJ.run("Options...", 
           "iterations=2 count=1 black edm=Overwrite do=Nothing");

    IJ.run("Dilate", "stack");
    if (saveIntermediates) {
        IJ.saveAs("Tiff",
               (new File(intermediates_path,
                         img_name+"_mask_dilated.tif")).toString());
    }
    IJ.run("Options...",
           "iterations=1 count=1 black edm=Overwrite do=Nothing");


    selections = new Roi[nSlices];
    for (int i=1; i<=nSlices;i++) {
      img.setSlice(i);
      IJ.run("Create Selection");
      if (img.getStatistics().max==255) {
        IJ.run("Make Inverse");
        IJ.wait(500);
      }
      selections[i-1] = img.getRoi();
    }
  }

  private void showDialog() {
    int DUNITS = 0;
    int COL = 5;
    GenericDialog gd = new GenericDialog("Bioact parameters");

    gd.addNumericField("Start slice", start_slice, DUNITS);
    gd.addNumericField("Max slices", max_slices, DUNITS);
    gd.addNumericField("Area threshold", area_threshold, DUNITS, COL, "%");
    gd.addChoice("Threshold", thresholds, thresh);
    gd.addChoice("Wavelength", wls, wl);
    gd.addCheckbox("Batch Mode", isBatch);
    gd.addCheckbox("Save intermediate images", saveIntermediates);
    gd.showDialog();

    start_slice = (int) gd.getNextNumber();
    max_slices = gd.getNextNumber();
    area_threshold = gd.getNextNumber();
    thresh     = gd.getNextChoice();
    wl         = gd.getNextChoice();
    isBatch    = gd.getNextBoolean();
    saveIntermediates = gd.getNextBoolean();

    if (gd.wasCanceled()) throw new RuntimeException(Macro.MACRO_CANCELED);
  }

  private void writeHeader() {
    try {
      out = new PrintWriter(new File(results_path,results_name));
    } catch (IOException e) {
      System.err.println("Can't write to " + out);
      System.exit(0);
    }
    out.println(
        "position\twl\tslice\tmin.elapsed\texpTcorr_bs_intden\texptime.sec\tbs.intden\tmean_bg_intden\tmean_intden\trb_radius\tthreshold\tarea_threshold\tcellarea_percent\ttotal_area");
  }
}
