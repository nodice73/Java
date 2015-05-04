// vim: set filetype=java tabstop=2 shiftwidth=2 expandtab :
import ij.IJ;
import ij.WindowManager;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.io.FileSaver;
import ij.plugin.RGBStackMerge;
import ij.plugin.ContrastEnhancer;

import org.fhcrc.honeycomb.hcimaging.*;
import org.fhcrc.honeycomb.hcimaging.hcimage.*;
import org.fhcrc.honeycomb.hcimaging.hcexception.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;

/** MovieMaker combines wavelengths of a <code>MCSperiment</code>
 * object to make a RGB (or any subset) movie of each position.  Does
 * normalization and background subtraction to optimize end result.
 *
 * @author Adam Waite
 * @since $Id: MovieMaker.java 1443 2012-09-06 03:53:40Z ajwaite $
 */
public class MovieMaker {
  private static final Map<String,String> channel_map;
  static {
    Map<String,String> temp = new HashMap<String,String>();
    temp.put("CFP","blue");
    temp.put("GFP","green");
    temp.put("YFP","green");
    temp.put("RFP","red");
    channel_map = Collections.unmodifiableMap(temp);
  }

  private HCExperiment hce = null;
  private ContrastEnhancer ce;

  private File project_path;
  private File merged_path;

  public MovieMaker(String project_path) {
    this.project_path = new File(project_path);
    merged_path       = new File(project_path,"merged");
    merged_path.mkdir();
  }

  public void process() {
    System.out.print("Generating HCExperiment...");
    
    try {
      hce = new HCExperiment(project_path);
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.println("Done!");

    double radius = 50;
    for (String pos : hce.getPositions()) {
      System.out.println("Processing position '" + pos + "'...");
      File movie_path = new File(merged_path,pos);
      movie_path.mkdir();

      int max_tps = hce.getMaxTimepoints(pos);
      for (int tp=0; tp<max_tps; tp++) {
        updateProgress(tp+1,max_tps);
        Set<HCImage> set = hce.getWavelengthSet(pos,tp);

        List<HCImage> images = new ArrayList<HCImage>(set.size());
        for (HCImage hci : set) {
          if (hci.getFilterCube().equals("BF")) continue;
          try {
            hci.open();
          } catch (UnopenableImageException e) {
            System.out.println("Can't open image:\n"+hci);
          }
          IJ.run(hci, "Divide...","value="+hci.getExposureTime());
          hci.flatten(radius);
          IJ.run(hci, "Enhance Contrast","saturated=1e-3 normalize");
          images.add(hci);
        }
        if (images.size() == 0) continue;

        ImagePlus [] image_array = new ImagePlus[images.size()];
        placeImages(images, image_array);

        ImagePlus merged = RGBStackMerge.mergeChannels(image_array,false);

        for (HCImage hci : set) { hci.flush(); }
        for (ImagePlus imp : images) { imp.flush(); }

//        merged.setSlice(1);
//        IJ.run(merged, "Enhance Contrast","saturated=0.4 normalize");
        merged = merged.flatten();

        FileSaver saver = new FileSaver(merged);
        String name = pos + "_" + zeroPad(tp) + ".tiff";
        saver.saveAsTiff((new File(movie_path,name)).toString());
        merged.flush();
      }
      System.out.println();
    }
  }

  private boolean nullsInArray(ImagePlus[] images) {
    boolean hasNull = false;
    for (int i=0; i<images.length; i++) {
      if (images[i] == null) hasNull = true;
    }

    if (hasNull) {
      for (int i=0; i<images.length; i++) {
        if (images[i] != null) images[i].flush();
      }
    }

    return true;
  }

  private void placeImages(List<HCImage> list, ImagePlus[] arr) {
    for (HCImage hci : list) {
      String col = channel_map.get(hci.getFilterCube());
      if (col.equals("red")) {
        arr[0] = hci;
      } else if (col.equals("green")) {
        arr[1] = hci;
      } else if (col.equals("blue")) {
        arr[2] = hci;
      } else {
        throw new RuntimeException("Don't recognize that filter cube.");
      }
    }
  }

  private String zeroPad(int n) {
    return String.format("%03d",n);
  }

  private void updateProgress(int count, int total) {
    System.out.print("\r ");
    System.out.print("\r  Timepoint " + count+"/"+total+" "
                     + IJ.freeMemory());
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) 
      throw new Exception("Usage: MovieMaker [path_to_project]");

    MovieMaker mm = new MovieMaker(args[0]);
    mm.process();
    //mm.merge();
    //mm.cleanUp();
  }

}
