package org.fhcrc.honeycomb.hcimaging.hcimage;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Measurements;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;
import ij.gui.Roi;

/** MaskInfo returns information about the foreground and background
 * of an image, as defined by a mask.
 * @author Adam Waite
 * @version %I%,%G%
 */
public class MaskInfo {
  private ImageStatistics fstats;
  private ImageStatistics bstats;
  private Roi f_roi;
  private Roi b_roi;

  /**
   * Returns statistics about foreground region.
   * @return statistics about foreground region.
   */
  public ImageStatistics getForegroundStats() { return fstats; }

  /**
   * Returns statistics about background region.
   * @return statistics about background region.
   */
  public ImageStatistics getBackgroundStats() { return bstats; }

  /**
   * Returns the <code>Roi</code> corresponding to the background
   * region.
   * @return the background region.
   */
  public Roi getBackgroundRoi() { return b_roi; }

  /**
   * Returns the <code>Roi</code> corresponding to the foreground
   * region.
   * @return the foreground region.
   */
  public Roi getForegroundRoi() { return f_roi; }

  /**
   * Returns all measured information about both regions.
   * @return the measurements for all regions.
   */
  public String toString() {
    return(
        "\n\t\tForeground\t\tBackground" +
      "\n\tsd\t\t" + fstats.stdDev + "\t\t" +  bstats.stdDev +
      "\n\tarea " + fstats.area + "\t\t" + bstats.area +
      "\n\tmean " + fstats.mean + "\t\t" + bstats.mean +
      "\n\tmedian " + fstats.median + "\t\t" + bstats.median
    );
  }

  public MaskInfo(ImagePlus im, ImagePlus bgmask) {
    RoiManager rm = new RoiManager(false);

    IJ.run(bgmask, "Create Selection","");

    Roi ri1 = bgmask.getRoi();
    ri1.setName("bg-mask");
    rm.addRoi(ri1);

    IJ.run(bgmask, "Make Inverse","");
    Roi ri2 = bgmask.getRoi();
    ri2.setName("bg-mask");
    rm.addRoi(ri2);

    Roi[] roi = rm.getRoisAsArray();

    im.setRoi(roi[0]);
    // Median has to be specified, so they all do.
    ImageStatistics stats = im.getStatistics(Measurements.MEAN+
                                             Measurements.MEDIAN+
                                             Measurements.STD_DEV+
                                             Measurements.AREA);
    im.setRoi(roi[1]);
    ImageStatistics inv_stats = im.getStatistics(Measurements.MEAN+
                                             Measurements.MEDIAN+
                                             Measurements.STD_DEV+
                                             Measurements.AREA);

    if (stats.stdDev <= inv_stats.stdDev) {
      // First selection is background. This should be true 
      // if a mask is passed as the image, as well.
      bstats = stats;
      b_roi = ri1;
      fstats = inv_stats;
      f_roi = ri2;
    } else {
      // Inverse selection is background.
      bstats = inv_stats;
      b_roi = ri2;
      fstats = stats;
      f_roi = ri1;
    }

    //im.show();
    //IJ.wait(2000);
    //im.hide();
    rm.close();
  }
}
