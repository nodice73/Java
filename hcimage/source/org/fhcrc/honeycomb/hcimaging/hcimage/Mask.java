package org.fhcrc.honeycomb.hcimaging.hcimage;

import ij.*;
import ij.process.*;
import ij.gui.Roi;
import ij.measure.Measurements;

import org.fhcrc.honeycomb.hcimaging.hcimage.region.*;

public class Mask extends ImagePlus implements Measurements {
  public Mask(String title, ImageProcessor ip) { super(title, ip); }

  public Roi[] makeWandSelections(Coordinates coords) {
    int size = coords.size();

    WindowManager.setTempCurrentImage(this);
    Roi[] rois = new Roi[size];
    for (int i=0; i<size; i++) {
      IJ.doWand(coords.get(i).x, coords.get(i).y);
      rois[i] = getRoi();
    }
    WindowManager.setTempCurrentImage(null);
    return rois;
  }

  public Roi selectForeground() {
    IJ.run(this,"Create Selection","");
    return getRoi();
  }

  public Roi selectBackground() {
    selectForeground();
    IJ.run(this,"Make Inverse","");
    return getRoi();
  }

  public ImageStatistics measureForeground() {
    selectForeground();
    return getStatistics(AREA+AREA_FRACTION+MODE);
  }

  public ImageStatistics measureBackground() {
    selectBackground();
    return getStatistics(AREA+AREA_FRACTION+MODE);
  }
}
