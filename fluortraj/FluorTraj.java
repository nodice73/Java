import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.io.FileSaver;
import ij.process.ImageStatistics;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;
import ij.process.AutoThresholder;
import ij.process.Blitter;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.Thresholder;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.GaussianBlur;
import ij.measure.Calibration;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import org.fhcrc.honeycomb.mc.MCSperiment;
import org.fhcrc.honeycomb.mc.MCImage;
import org.fhcrc.honeycomb.ijutils.MaskInfo;


/** Analyze fluorescence trajectories over the course of an experiment.  
 * Generates intensity versus time information for each wavelength 
 * of a given <code>MCSperiment</code>
 */
public class FluorTraj {
  private static double radius = 20;
  private static boolean SAVE_STEPS = false;
  private static boolean NORMALIZE  = false;
  private static boolean SHOW_STEPS = false;
  private static int BGSD = 6;
  private static int WAIT = 2000;
  private static File project_path;
  private static File results_path;
  private static File steps_path;

  // ImageJ objects used by methods
  private static Calibration  calib = new Calibration();
  private static GaussianBlur gb    = new GaussianBlur();
  
  public static String usage() {
    return "\n\nUsage: FluorTraj save show n [path_to_project]\n" +
           "\tsave \tsave intermediate steps.\n" +
           "\tshow\tshow intermediate steps.\n" +
           "\tn\tthe number of SD above mean background to subtract from the image.\n";
  }

  public static void main(String[] args) throws IOException {
    PrintWriter out = null;

    try {
      if (args.length > 4  || args.length == 0)
        throw new Exception( usage() ); 

      for (int i=0; i<args.length-1; i++) {
        if (args[i].equals("save")) SAVE_STEPS = true;
        else if (args[i].equals("show")) SHOW_STEPS = true;
        else if (args[i].matches("[0-9]+")) BGSD = Integer.parseInt(args[i]);
        else {
          System.err.println(usage());
          System.exit(1);
        }
      }

      project_path = new File(args[args.length-1]);
      System.err.println("In " + project_path);
      results_path = new File(project_path,"fluortraj_results");
      steps_path   = new File(project_path,"intermediate_steps");
      String results_name = "fluortraj_results.txt";

      MCSperiment exp = new MCSperiment(project_path);

      results_path.mkdir();
      if (SAVE_STEPS) steps_path.mkdir();

      try {
        out = new PrintWriter(new File(results_path,results_name));
        out.println(
            "well\tlocation\tfilter\texp\ttarea\ttimepoint\ttime" + 
            "\tfmean\tfmedian\tfsd\tfarea");
      } catch (IOException e) {
        System.err.println("Can't write to " + out);
      }

      // ImageJ objects used in the code.
      Opener opener           = new Opener();
      BackgroundSubtracter bs = new BackgroundSubtracter();

      calib.setUnit("pixel");

      String cp = exp.getCurrentPosition();
      while(cp != null) {
        System.out.println("Processing position '" + cp + "'...");
        MCImage[] tps;
        while((tps = exp.getNextWavelengthSet()) != null) {
          String fc = tps[0].getFilterCube();
          // Not working with brightfield images.
          if (fc.equals("BF")) continue;
          //if (!fc.equals("RFP")) continue;

          double fluor_per_cell = 0;
          for (int i=0; i<tps.length; i++) {
            //if (i<15 || i>20) continue;
            System.out.println("Processing image: " + tps[i].getName());
            ImagePlus orig = 
              opener.openImage(tps[i].getImagePath().toString());
            int area = orig.getWidth() * orig.getHeight();
            //short omin = (short)orig.getProcessor().getMin();
            //short omax = (short)orig.getProcessor().getMax();

            if (SHOW_STEPS) shower(orig,WAIT);

            // booleans are createBackground, lightBackground,
            // useParaboloid, doPresmooth, correctCorners
            boolean createBackground = false;
            boolean lightBackground  = false;
            boolean useParaboloid    = true;
            boolean doPresmooth      = true;
            boolean correctCorners   = true;

            bs.rollingBallBackground(orig.getProcessor(),
                                     radius,
                                     createBackground,
                                     lightBackground,
                                     useParaboloid,
                                     doPresmooth,
                                     correctCorners);


            // Make initial background mask.
            ImagePlus rough_mask = makeMask(fc, orig, "Li");
            if (SHOW_STEPS) shower(rough_mask,WAIT);

            // Subtract background.
            MaskInfo rough_info = new MaskInfo(orig, rough_mask);
            ImagePlus bgsub     = subtractBackground(orig, rough_info, BGSD);
            if (SHOW_STEPS) shower(bgsub,WAIT);

            // Make second background mask.  This will give a more
            // accurate area estimation.
            //save(steps_path,(tps[i].getName()+"_bgsub1"), bgsub);

            ImagePlus strict_mask = makeMask(fc, bgsub, "Triangle");
            if (SHOW_STEPS) shower(strict_mask,WAIT);

            // Add back BGSD*bSd  
            ShortProcessor nsd = 
              new ShortProcessor(bgsub.getWidth(),bgsub.getHeight());
            ImageStatistics rough_bg = rough_info.getBackgroundStats();
            nsd.setValue(BGSD*rough_bg.mean);
            nsd.fill();
            bgsub.getProcessor().copyBits(nsd,0,0,Blitter.ADD);
            if (SHOW_STEPS) shower(bgsub,WAIT);

            // Get median value of foreground pixels.
            MaskInfo strict_info = new MaskInfo(bgsub, strict_mask);
            ImageStatistics strict_fg = strict_info.getForegroundStats();

            // Normalize to median foreground intensity 
            // ("per cell" intensity).
            // Divide by median
            ImageProcessor bsfloat = bgsub.getProcessor().convertToFloat();
            FloatProcessor flp = 
              new FloatProcessor(bgsub.getWidth(),bgsub.getHeight());
            flp.setValue(strict_fg.median);
            flp.fill();
            bsfloat.copyBits(flp,0,0,Blitter.DIVIDE);
            ImagePlus mednorm = new ImagePlus("mednorm", bsfloat);
            if (SHOW_STEPS) shower(mednorm,WAIT);

            // Get final values.
            MaskInfo final_info = new MaskInfo(mednorm, strict_mask);
            ImageStatistics final_fg = final_info.getForegroundStats();

            
            // Print results.
            out.println(tps[i].getWell()      + "\t" +
                        tps[i].getLocation()  + "\t" +
                        fc                    + "\t" +
                        tps[i].getExposureTime() + "\t" +
                        area                  + "\t" +
                        i                     + "\t" + 
                        exp.getElapsedTime(tps[i],'h') + "\t" +
                        final_fg.mean    + "\t" +
                        final_fg.median  + "\t" +
                        final_fg.stdDev  + "\t" +
                        final_fg.area);

            if (SAVE_STEPS) {
              save(steps_path,(tps[i].getName()+"_rbbgsub"),orig);
              save(steps_path,(tps[i].getName()+"_rough_mask"), rough_mask);
              save(steps_path,(tps[i].getName()+"_strict_mask"), strict_mask);
              save(steps_path,(tps[i].getName()+"_bgsub"), bgsub);
              save(steps_path,(tps[i].getName()+"_med-normalized"), mednorm);
            }
            orig.flush();
            bgsub.flush();
            rough_mask.flush();
            strict_mask.flush();
            mednorm.flush();
          }
        }
        exp.resetWavelength();
        cp = exp.getNextPosition();
      }
    } catch (NumberFormatException e) {
      System.err.println(e);
      e.printStackTrace();
    } catch (IOException e) {
      System.err.println(e);
      e.printStackTrace();
    } catch (Exception e) {
      System.err.println(e);
      e.printStackTrace();
    } finally {
      out.close();
    }
  }

  public static ImagePlus subtractBackground(ImagePlus img, MaskInfo mi, 
                                             int bgsd)
  {
    ImageStatistics bg_stats = mi.getBackgroundStats();
    ImagePlus res = new ImagePlus("bg_sub",img.getProcessor().duplicate());
    ShortProcessor sp = new ShortProcessor(img.getWidth(),img.getHeight());
    sp.setValue(bg_stats.mean + bgsd*bg_stats.stdDev);
    sp.fill();
    res.getProcessor().copyBits(sp,0,0,Blitter.SUBTRACT);
    return res;
  }

  public static void shower(ImagePlus img, int time) {
    img.updateAndDraw();
    img.show();
    IJ.wait(time);
    img.hide();
  }

  public static ImagePlus makeMask(String filter, ImagePlus ip, String method) {
    ImageProcessor mask = ip.getProcessor().duplicate();
    ImagePlus img       = new ImagePlus(ip.getTitle(),mask);
    // Thresholding doesn't work properly without normalizing.
    //IJ.run(img, "Enhance Contrast","saturated=0.0 normalize");

    double blur_radius   = 2.0;
    double blur_accuracy = 0.002; // Shouldn't exceed 0.02 according to 
                                  // source.
    //if (filter.equals("RFP")) method = "MaxEntropy";
    boolean dark_background = true;

    gb.blurGaussian(mask,blur_radius,blur_radius,blur_accuracy);
    mask.setAutoThreshold(method,dark_background,mask.RED_LUT);
    if (SHOW_STEPS) shower(img,WAIT);

    IJ.run(img, "Convert to Mask","");
    //IJ.run(img, "Erode","");
    return img;
  }

  public static boolean
  save(File path, String name, ImageProcessor ip) throws NullPointerException {
    ImagePlus imp = new ImagePlus(name,ip);
    //IJ.saveAs(imp, "tiff",(new File(path,name)).toString());
    return new FileSaver(imp).saveAsTiff((new File(path, (name+".tiff"))).toString());
  }

  public static boolean
  save(File path, String name, ImagePlus ip) throws NullPointerException {
    //IJ.saveAs(ip, "tiff",(new File(path,name)).toString());
    return new FileSaver(ip).saveAsTiff((new File(path, (name+".tiff"))).toString());
  }
}
