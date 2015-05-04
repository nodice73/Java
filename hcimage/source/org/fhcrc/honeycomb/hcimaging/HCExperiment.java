// vim: set filetype=java tabstop=2 shiftwidth=2 expandtab :

package org.fhcrc.honeycomb.hcimaging;

import org.fhcrc.honeycomb.hcimaging.hcimage.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** HCExperiment is a class designed to represent the hierarchy of our 
 * automated microscopy experiments.
 * @author Adam Waite
 * @since $Date: 2012-09-05 18:03:36 -0700 (Wed, 05 Sep 2012) $
 */
public class HCExperiment {
	private static final short TURRETS = 6;
	private int timepoint  = 0;
	private int position   = 0;
  private int wavelength = 0;
	private String[] positions;

	private File project_path;
  private static long experiment_start_time = 0;
  private LinkedHashMap<String, LinkedHashMap<String, ArrayList<HCImage>>> 
    projectMap = 
    new LinkedHashMap<String, LinkedHashMap<String, ArrayList<HCImage>>>();
  private HashMap<String,Integer> maxMap = new HashMap<String,Integer>();

  private static final int TIMEPOINT_DIGITS = 4;
  private static final String POSITION_PATTERN = "[A-Z]\\d+[a-z]?"; 
  private static final String WAVELENGTH_PATTERN = "WL\\d"; 
  private static final String FILE_REGEX = 
    ".*_\\d{4}_[A-Z]\\d{2}[a-z]?_+WL\\d\\.tif";

  private static FilenameFilter filter(final String regex) {
    return new FilenameFilter() {
      private Pattern pattern = Pattern.compile(regex);
      public boolean accept(File dir, String str) {
        return pattern.matcher(str).matches();
      }
    };
  }

	/** 
   * Constructs an HCExperiment object.
   * @param project_path a path to the folder containing the experiment.
	*/
  public HCExperiment(String project_path) throws FileNotFoundException {
    this(new File(project_path));
  }

	/** 
   * Constructs an HCExperiment object.
   * @param project_path a path to the folder containing the experiment.
	*/
	public HCExperiment(File project_path) throws FileNotFoundException {
    if (!project_path.exists()) {
      throw new FileNotFoundException("Folder " + project_path + 
          " doesn't exist.");
    }

    this.project_path = project_path;

    boolean isFirstImage = true;
    File[] pos_paths = project_path.listFiles(filter(POSITION_PATTERN));
    
    if (pos_paths.length == 0) {
      File [] files = project_path.listFiles();
      String saw = "";
      for (File file : files) {
        saw += file.toString();
        saw += "\n";
      }
      throw new FileNotFoundException( "In '" + project_path + "':" +
          "No folders match position filter.  Saw "+saw);
    }

    Arrays.sort(pos_paths);

    for (int p=0; p<pos_paths.length; p++) {
      File [] wls = pos_paths[p].listFiles(filter(WAVELENGTH_PATTERN));

      if (wls.length == 0) {
        throw new FileNotFoundException( "In '" + pos_paths[p] + "':" + 
            "No folders match wavelength filter");
      }
      Arrays.sort(wls);

      LinkedHashMap<String, ArrayList<HCImage>> wlMap = 
        new LinkedHashMap<String, ArrayList<HCImage>>();
      int max_timepoints = 0;
      for (int w=0; w<wls.length; w++) {
        File[] tps = wls[w].listFiles(filter(FILE_REGEX));
        if (tps.length == 0) {
          throw new FileNotFoundException(
              "In '" + pos_paths[p] + "', '" + wls[w] + "':" +
              "No folders match timepoint filter");
        }
        if (tps.length > max_timepoints) max_timepoints = tps.length;
        Arrays.sort(tps);


        ArrayList<HCImage> mctps = new ArrayList<HCImage>();
        for (int i=0; i<tps.length; i++) 
          mctps.add( HCImageFactory.getHCImage(tps[i]) );

        if (isFirstImage) {
          setStartTime(mctps.get(0));
          isFirstImage = false;
        }

        wlMap.put(wls[w].getName(), mctps);
      }
      maxMap.put(pos_paths[p].getName(),max_timepoints);
      projectMap.put(pos_paths[p].getName(), wlMap);
    }
	}

	/** Returns the project path.
	 * @return the project path as a <code>File<\code> object.
	 */
  public File getProjectPath() { return project_path; }

	/** Returns the project name.
	 * @return the project name as a <code>String<\code>.  The project name
   * is the name of the folder containing the positions.
	 */
  public String getProjectName() { return project_path.getName(); }

  /** 
   * Sets the experiment start time.  Calculations made by
   * <code>getExperimentTime</code> methods use this number.
   * @param image an <code>HCImage</code> whose acquisition time will
   * be used as the start time.
   */
  public void setStartTime(HCImage image) {
    experiment_start_time = image.getAcquisitionTime();
  }

  /** 
   * Returns the experiment start date.  
   * @return the experiment start date.
   */
  public Date getStartDate() { return new Date(experiment_start_time); }

  /** 
   * Returns the experiment start time.  
   * @return the experiment start time.
   */
  public long getStartTime() { return experiment_start_time; }

  /**
   * Get the amount of time that has elapsed in the experiment since
   * <code>experiment_start_time</code>
   * @param image the image whose acquisition time will be compared
   * to the start time.
   * @param unit the unit desired.  Can be "d" for days, "h" for hours,
   * or "m" for minutes.
   * @return the number of hours elapsed since the start of the
   * experiment.
   * @throws Exception if the unit is not recognized.
   */
  public double elapsedTime (HCImage image, char unit) {
    int conversion = 1000 * 60;
    double et = image.getAcquisitionTime() - experiment_start_time;
    switch (unit) {
      case 'm': break;
      case 'h': conversion = conversion * 60; break;
      case 'd': conversion = conversion * 60 * 24; break;
      default: throw new RuntimeException("Don't recognize unit " + unit);
    }
    return et/conversion;
  }

  /** 
   * Returns the number of positions in the experiment.
   * @return the number of positions in the experiment.
   */
  public int getNPositions() { return projectMap.size(); }

  /** 
   * Returns the number of wavelengths for a specified position.
   * @param position the position.
   * @return the number of wavelengths.
   */
  public int getNWavelengths(String position) { 
    return projectMap.get(position).size();
  }

  /** 
   * Returns the number of timepoints for a specified position and
   * wavelength.
   * @param position the position.
   * @param wavelength the wavelength.
   * @return the number of timepoints.
   */
  public int getNTimepoints(String position, String wavelength) {
    return projectMap.get(position).get(wavelength).size();
  }

  /** 
   * Returns the maximum number of timepoints for a specified position
   * @param position the position.
   * @return the maximum number of timepoints.
   */
  public int getMaxTimepoints(String position) {
    return maxMap.get(position);
  }

  /** 
   * Returns the <code>Set</code> of positions in the experiment.
   * @return the <code>Set</code> of positions in the experiment.
   */
  public Set<String> getPositions() { return projectMap.keySet(); }

  /** 
   * Returns the <code>Set</code> of wavelengths for a given position.
   * @param position the desired position.
   * @return the <code>Set</code> of wavelengths for a given position.
   */
  public Set<String> getWavelengths(String position) { 
    return projectMap.get(position).keySet();
  }

  /** 
   * Returns the <code>Set</code> of wavelengths for a given position and
   * timepoint.
   * @param position the desired position.
   * @param timepoint the desired timepoint.
   * @return the <code>Set</code> of <code>HCImage</code>s for a given 
   * position and timepoint.
   */
  public Set<HCImage> getWavelengthSet(String position, int timepoint) { 
    Set<HCImage> set = new HashSet<HCImage>();
    Set<String> wls  = getWavelengths(position);

    for (String wl : wls) {
      List<HCImage> tps = getTimepoints(position,wl);
      for (HCImage tp : tps) {
        if (tp.getTimepoint() == timepoint) set.add(tp);
      }
    }
    return set;
  }

  /** 
   * Returns a <code>List</code> of <code>HCImage</code>s for a given 
   * position and wavelength.
   * @param position the desired position.
   * @param wavelength the desired wavelength.
   * @return a <code>List</code> of <code>HCImage</code>s.
   */
  public List<HCImage> getTimepoints(String position, String wavelength) {
    return projectMap.get(position).get(wavelength);
  }
}
