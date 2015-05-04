import org.fhcrc.honeycomb.hcexperiment.*;
import org.fhcrc.honeycomb.hcimage.*;
import org.fhcrc.honeycomb.hcimage.region.*;
import org.fhcrc.honeycomb.utils.*;
import org.fhcrc.honeycomb.utils.ij.*;

import java.io.*;
import java.util.*;

import ij.IJ;
import ij.ImagePlus;
import ij.io.*;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.process.*;
import ij.measure.*;

/**
 * Mtbf identifies cells and tracks them using brightfield images from
 * microtiter plates.
 * @author Adam Waite
 * @version %I%,%G%
 */
public class Mtbf implements Measurements {
  private static Calibration  calib  = new Calibration();

  private HCExperiment hcsper;

  private File project_path;
  private File results_path;
  private String results_name;
  private PrintWriter out;

  public Mtbf(File project_path) {
    hcsper = new HCExperiment(project_path);

    setupOptions();

    results_path = new File(project_path,"mtbf_results");
    results_name = "mtbf_results.txt";

    try {
      results_path.mkdir();
      out = new PrintWriter(new File(results_path,results_name));
      out.println("well\tlocation\ttimepoint\ttime\tcell\tarea\tsd\tx\ty");
    } catch (IOException e) {
      System.err.println("Can't write to " + out);
      System.exit(0);
    }
  }

  public void run() {
    System.out.println("Running!");

    String threshold_method = "MaxEntropy dark";
    for (String pos : hcsper.getPositions()) {
      System.out.println("Processing position '" + pos + "'...");
      for (String wl : hcsper.getWavelengths(pos)) {
        List<HCImage> tps = hcsper.getTimepoints(pos,wl);

        HCImage first_img = tps.get(0).open(); 
        Coordinates cell_locations = identifyCells(first_img);

        System.out.print("Remaining: ");
        System.out.println(cell_locations);

        if (cell_locations.size() > 0) {
          for (HCImage tp : tps) {
            HCImage orig = tp.open();

            if (orig==null) {
              System.out.println(
                  "Can't open " + tp.getName() + ", skipping...");
              continue;

            } else {
              Roi[] cell_sels = 
                orig.makeMask(threshold_method).
                     makeWandSelections(cell_locations);

              printResult(cell_sels, cell_locations, tp, orig);
              orig.flush();
            }
          }
        } else {
          Roi[] nothing = new Roi[0];
          printResult(nothing, cell_locations, tps.get(0), first_img);
        }
        first_img.flush();
      }
    }
    out.close();
    System.out.println("Done!");
  }

  private Coordinates identifyCells(HCImage first_img) {
    double blur = 10, rb_radius = 50;
    double noise_tolerance = 500;

    HCImage flat = first_img.flatten(blur,rb_radius);

    Coordinates initial = flat.getMaximaList(noise_tolerance);
    Coordinates cells = filter(initial, flat);
    flat.flush();
    return cells;
  }

  private Coordinates filter(Coordinates coords, HCImage img) {
    double min_dist = 20;
    double min_area = 150, max_area = 400, min_sd = 450, max_sd = 800;
    String threshold_method = "MaxEntropy dark";

    Coordinates far_enough = coords.removeClose(min_dist);
    Coordinates interesting = new Coordinates();
    if (far_enough.size() > 0) {
      Roi[] rois = 
        img.makeMask(threshold_method).makeWandSelections(far_enough);

      interesting.addAll(
          far_enough.removeUninteresting(img, rois, 
                                         min_area, max_area, 
                                         min_sd, max_sd));
    }
    return interesting;
  }

  private void
  printResult(Roi[] rois, Coordinates coords, HCImage hci, ImagePlus img) {
    int nSels = rois.length;
    double et=0;
    try {
      et = hcsper.elapsedTime(hci,'h');
    } catch (Exception e) {
      System.err.println(e);
      System.exit(0);
    }

    String well = hci.getWell();
    String loc  = hci.getLocation();
    if (nSels==0) {
      out.println(Write.writeDelim("\t", 
                  Arrays.asList( well, loc,
                                 "NA","NA","0","NA","NA","NA","NA")));
    } else {
      for (int sel=0; sel<nSels; sel++) {
        String timepoint = Integer.toString(hci.getTimepoint());
        String elapsed = Double.toString(et);
        String cell = Integer.toString(sel+1);
        String x = Double.toString(coords.get(sel).x);
        String y = Double.toString(coords.get(sel).y);

        if (rois[sel] == null) {
          out.println(Write.writeDelim("\t", 
              Arrays.asList(
                well, loc, timepoint, elapsed, cell, "NA", "NA", x, y)));
        } else {
          img.setRoi(rois[sel]);
          ImageStatistics stats = img.getStatistics(CENTROID+AREA+STD_DEV);
          out.println(Write.writeDelim("\t", 
              Arrays.asList(
                well, loc, timepoint, elapsed, cell, 
                Double.toString(stats.area), Double.toString(stats.stdDev),
                x, y)));
        }
      }
    }
  }


  private void setupOptions() {
    IJ.run("Set Measurements...", "centroid area standard");
    IJ.run("Options...", "iterations=1 black edm=Overwrite count=1");
    calib.setUnit("pixel");
  }

  public static class Test {
    public static void main(String[] args) {
      File p_path = null;
      if (args.length == 0) {
        p_path = new File(IJ.getDirectory("Choose a directory."));
      } else if (args.length==1) {
        p_path = new File(args[0]);
      } else {
        System.out.println("Usage: java mtbf [path to project]");
      }
      Mtbf mtbf = new Mtbf(p_path);
      mtbf.run();
    }
  }
}
