package org.fhcrc.honeycomb.hcimaging.hcimage.region;

import ij.*;
import ij.gui.Roi;
import ij.process.ImageStatistics;
import java.util.*;

import org.fhcrc.honeycomb.hcimaging.hcimage.*;

public class Coordinates extends ArrayList<Coordinate> { 
  private int size = this.size();

  public Coordinates removeClose(double min_dist) {
    LinkedList<Coordinate> far = new LinkedList<Coordinate>();
    far.addAll(this);

    System.out.println("Removing close cells...");
    System.out.format("%-15s %-15s %-15s\n", "X", "Y", "distance");
    for (int i=0; i<far.size(); i++) {
      for (int j=i+1; j<far.size(); j++) {
        double dist = distance(far.get(i), far.get(j));
        System.out.format("%-15s %-15s %-15.2f\n", 
                          far.get(i), far.get(j), dist);
        if (dist < min_dist) {
          far.remove(j);
          System.out.println("Removing...");
        }
      }
    }
    Coordinates res = new Coordinates();
    res.addAll(far);
    return res;
  }

  public Coordinates removeUninteresting(ImagePlus img, Roi[] rois, 
                             double min_area, double max_area,
                             double min_sd,   double max_sd ) {

    //img.show();
    int nSelections = rois.length;
    Coordinates keep = new Coordinates();
    for (int i=0; i<nSelections; i++) {
      if (rois[i] != null) {
        img.setRoi(rois[i]);
        //IJ.wait(500); img.updateAndDraw();

        IJ.run(img,"Measure","");

        ImageStatistics stats = img.getStatistics();
        if (stats.area < max_area && stats.area > min_area &&
            stats.stdDev < max_sd && stats.stdDev > min_sd)
        {
          System.out.println("Keeping...");
          keep.add(this.get(i));
        }
      }
    }
    //img.hide();
    return keep;
  }

  private double distance(Coordinate a, Coordinate b) {
    return Math.sqrt( Math.pow(a.x-b.x,2) + Math.pow(a.y-b.y,2) );
  }
}
