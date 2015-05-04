import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;
import ij.process.AutoThresholder;
import ij.process.Blitter;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.Thresholder;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.measure.Measurements;
import ij.gui.Roi;
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
public class CountFluor {
  private static double radius = 20;
  private static boolean SAVE_STEPS = false;
  private static boolean NORMALIZE  = false;
  private static boolean SHOW_STEPS = false;
  private static int MANIP_MAX = 255;
  private static int BGSD = 6;
  private static int WAIT = 2000;
  private static File project_path;
  private static File results_path;
  private static File steps_path;

  // ImageJ objects used by methods
  private static Calibration  calib = new Calibration();
  private static GaussianBlur gb    = new GaussianBlur();
  private static RankFilters rf     = new RankFilters();
  private static MaximumFinder mf   = new MaximumFinder();
  private static ResultsTable rt    = new ResultsTable();
  private static RoiManager rm;
  
  public static String usage() {
    return "\n\nUsage: CountFluor save show n [path_to_project]\n" +
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
      results_path = new File(project_path,"countfluor_results");
      steps_path   = new File(project_path,"countfluor_steps");
      String results_name = "countfluor_results.txt";

      MCSperiment exp = new MCSperiment(project_path);

      results_path.mkdir();
      if (SAVE_STEPS) steps_path.mkdir();

      try {
        out = new PrintWriter(new File(results_path,results_name));
        out.println("well\tlocation\tfilter\ttimepoint\ttime\tcount");
      } catch (IOException e) {
        System.err.println("Can't write to " + out);
      }

      // ImageJ objects used in the code.
      Opener opener           = new Opener();
      BackgroundSubtracter bs = new BackgroundSubtracter();

      calib.setUnit("pixel");

      String cp = exp.currentPosition();
      while(cp != null) {
        System.out.println("Processing position '" + cp + "'...");
        MCImage[] tps;
        while((tps = exp.nextWavelengthSet()) != null) {
          String fc = tps[0].filterCube();
          // Not working with brightfield images.
          if (fc.equals("BF")) continue;
          //if (!fc.equals("RFP")) continue;

          double fluor_per_cell = 0;
          for (int i=0; i<tps.length; i++) {
            String img_name = tps[i].getName();
            //if (i<15 || i>20) continue;
            System.out.println("Processing image: " + img_name);
            ImagePlus orig = 
              opener.openImage(tps[i].imagePath().toString());
            int   area = orig.getWidth() * orig.getHeight();
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


            ImageProcessor pmanip = orig.getProcessor();
            pmanip = pmanip.convertToByte(true);
            ImagePlus manip = new ImagePlus("manip",pmanip);
            if (SHOW_STEPS) shower(manip,WAIT);
            // Make initial background mask.
            ImagePlus rough_mask = makeMask(fc, manip, "Triangle");
            if (SHOW_STEPS) shower(rough_mask,WAIT);

            // Subtract background.
            MaskInfo bg = new MaskInfo(manip, rough_mask);
            pmanip.setMinAndMax(bg.bStats().mean+BGSD*bg.bStats().stdDev,(double) MANIP_MAX);
            if (SHOW_STEPS) shower(manip,WAIT);
            double blur_radius   = 0.5;
            double blur_accuracy = 0.002; 
            gb.blurGaussian(pmanip,blur_radius,blur_radius,blur_accuracy);

            boolean excludeOnEdges = false;
            boolean isEDM = false;
            ImagePlus max =
              new ImagePlus("segmented",
                  mf.findMaxima(pmanip,1,1,MaximumFinder.SEGMENTED,
                                excludeOnEdges,isEDM));
            max.getProcessor().invert();
            if (SHOW_STEPS) shower(max,WAIT);

            int options = 
              ParticleAnalyzer.ADD_TO_MANAGER+ParticleAnalyzer.INCLUDE_HOLES+ParticleAnalyzer.SHOW_NONE;
            int measurements = 0;
            double minSize = 0;
            double maxSize = 1000;
            double minCirc = 0;
            double maxCirc = 1;
            ParticleAnalyzer pa = new ParticleAnalyzer(
                options,measurements,rt,minSize,maxSize,minCirc,maxCirc);
            pa.analyze(max);
            if (rm==null) rm = RoiManager.getInstance();
            rm.runCommand("Save",steps_path+"/"+img_name+".zip");
            
            // Print results.
            out.println(tps[i].getWell()      + "\t" +
                        tps[i].getLocation()  + "\t" +
                        fc                    + "\t" +
                        i                     + "\t" + 
                        exp.elapsedTime(tps[i],'h') + "\t" +
                        rm.getCount());

            rm.runCommand("reset");
            if (SAVE_STEPS) {
              save(steps_path,img_name+"manip",manip);
              save(steps_path,img_name+"segmented",max);
            }
          }
        }
        exp.resetWavelength();
        cp = exp.nextPosition();
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

  public static ImagePlus subtractBackground(ImagePlus img, MaskInfo mi, int bgsd) {
    ImagePlus res = new ImagePlus("bg_sub",img.getProcessor().duplicate());
    ShortProcessor sp = new ShortProcessor(img.getWidth(),img.getHeight());
    sp.setValue(mi.bStats().mean + bgsd*mi.bStats().stdDev);
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

    double blur_radius   = 2.0;
    double blur_accuracy = 0.002; // Shouldn't exceed 0.02 according to 
                                  // source.
    //if (filter.equals("RFP")) method = "MaxEntropy";
    boolean dark_background = true;

    gb.blurGaussian(mask,blur_radius,blur_radius,blur_accuracy);
    mask.setAutoThreshold(method,dark_background,mask.RED_LUT);
    if (SHOW_STEPS) shower(img,WAIT);

    IJ.run(img, "Convert to Mask","");
    for (int i=0;i<3;i++) IJ.run(img, "Dilate","");
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
