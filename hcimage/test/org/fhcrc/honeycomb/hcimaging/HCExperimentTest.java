package org.fhcrc.honeycomb.hcimaging;

import org.fhcrc.honeycomb.hcimaging.*;
import org.fhcrc.honeycomb.hcimaging.hcimage.*;

import org.junit.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.io.FileNotFoundException;

public class HCExperimentTest {
  private String test_path = 
  "/home/nodice/Documents/Code/Java/hcimage/test/org/fhcrc/honeycomb/hcimaging/test-files/my_experiment";
  private HCExperiment hce;

  @Before
  public void loopThrough() {
    try {
      hce = new HCExperiment(test_path);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void wavelengthSet() {
    String position = hce.getPositions().iterator().next();
    int timepoint = 0;

    Set<HCImage> set = hce.getWavelengthSet(position,timepoint);

    System.out.println(set);
    for (HCImage img : set) {
      assertEquals("Timepoints not equal", timepoint, img.getTimepoint());
    }
  }
}
