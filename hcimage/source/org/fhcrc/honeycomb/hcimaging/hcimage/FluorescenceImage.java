package org.fhcrc.honeycomb.hcimaging.hcimage;

import java.io.File;
import org.fhcrc.honeycomb.hcimaging.hcimage.region.*;
import ij.*;
import ij.process.*;

public class FluorescenceImage extends HCImage {
  private FluorescenceImage(HCImageInfo info) { super(info); }

  public static HCImage getInstance(HCImageInfo info) {
    return new FluorescenceImage(info);
  }

  public boolean isFluorescence() { return true; }

  public Coordinates getMaximaList(double noise) {
    throw new UnsupportedOperationException();
  }
  public Mask makeMask(String threshold_name) {
    throw new UnsupportedOperationException();
  }

  /** Stub method.  Fluorescence images do not use a blur radius.
   */
  public void flatten(double blur, double radius) 
    throws IllegalArgumentException
  {
    throw new IllegalArgumentException("Blur radius not used.");
  }

  public void flatten(double radius) {
    if (!isOpen) throw new RuntimeException("HCImage not open.");
    IJ.run(this, "Subtract Background...", "rolling="+radius+" sliding");
  }
}
