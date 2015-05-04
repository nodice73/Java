package org.fhcrc.honeycomb.hcimaging.hcimage;

import org.fhcrc.honeycomb.hcimaging.*;
import org.fhcrc.honeycomb.hcimaging.hcexception.*;
import org.fhcrc.honeycomb.hcimaging.hcimage.region.*;

import java.io.*;

import ij.*;
import ij.process.*;
import ij.gui.Roi;
import ij.plugin.ImageCalculator;
import ij.measure.ResultsTable;

public class BrightfieldImage extends HCImage {
  private static ImageCalculator ic = new ImageCalculator();
  private static ResultsTable rt    = new ResultsTable();
  private BrightfieldImage(HCImageInfo info) { super(info); }

  public static HCImage getInstance(HCImageInfo info) {
    return new BrightfieldImage(info); 
  }

  public HCImage copy() { return new BrightfieldImage(this.info); }

  public boolean isFluorescence() { return false; }


  /**
   * Removes low-frequency variation from the image.
   * @param blur_radius the radius of the gaussian blur applied to the
   * original image.  Should be large enough to remove all features of
   * interest from the image.
   * @param rb_radius the radius of the "rolling ball" used in the rolling
   * ball algorithm.
   * @return the flattened <code>HCImage</code>
   */
  public void flatten(double blur_radius, double rb_radius) {
    if (!isOpen) throw new HCImageNotOpenException();
    ImagePlus blurred = new ImagePlus("",getProcessor().duplicate());
    IJ.run(this, "Gaussian Blur...", "sigma="+blur_radius);
    IJ.run(this, "Subtract Background...", 
                    "rolling="+rb_radius+" light sliding");
//    flat.setProcessor(
 //       (ic.run("Subtract create 32-bit", this, blurred)).getProcessor());
  }
  
  /** Stub method.  Must provide a blur radius for brightfield images.
   */
  public void flatten(double rb_rad) throws IllegalArgumentException {
    throw new IllegalArgumentException("Must supply a blur radius.");
  }

  public Mask makeMask(String threshold_method) {
    mask = new Mask(getTitle()+"_mask", getProcessor().duplicate());
    IJ.run(mask, "Select None","");
    IJ.run(mask,"16-bit","");
    IJ.run(mask, "Gaussian Blur...", "sigma=1");
    IJ.run(mask, "8-bit","");
    IJ.run(mask, "Variance...", "radius=2");
    IJ.setAutoThreshold(mask, threshold_method);
    IJ.run(mask, "Convert to Mask","");
    IJ.run(mask, "Fill Holes","");
    return mask;
  }

  public Coordinates getMaximaList(double noise) {
    ImagePlus blurred = new ImagePlus(getTitle()+"_blurred", 
                                      getProcessor().duplicate());
                                    
    IJ.run(blurred, "Gaussian Blur...", "sigma=1");
    IJ.run(blurred,"Find Maxima...", "noise="+noise+" output=[List]");

    rt = ResultsTable.getResultsTable();
    int nMax = rt.getCounter();
    Coordinates coords = new Coordinates();
    for (int i=0; i<nMax; i++) {
      coords.add( new Coordinate((int) rt.getValue("X",i), 
                                 (int) rt.getValue("Y",i)) );
    }
    return coords;
  }
}
