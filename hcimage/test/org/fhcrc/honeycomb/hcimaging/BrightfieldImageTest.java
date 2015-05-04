package org.fhcrc.honeycomb.hcimaging;

import org.fhcrc.honeycomb.hcimaging.*;
import org.fhcrc.honeycomb.hcimaging.hcimage.*;
import org.fhcrc.honeycomb.hcimaging.hcexception.*;

import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;

@Ignore
public class BrightfieldImageTest {
  private String image_path = 
    "/home/nodice/Documents/Code/Java/hcimage/test/org/fhcrc/honeycomb/hcimaging/test-files/my_experiment/B02a/WL0/death-compIV_0000_B02a_WL0.tif";
  private HCImage hci = 
    HCImageFactory.getHCImage(new File(image_path));

  @Ignore 
  public void runthrough() {
    HCImage hci = null;
    //hci.flatten(10,50); // Throws exception
    try {
      hci.open();
    } catch (UnopenableImageException uie) {
      System.err.println(uie.getStackTrace());
    }

    HCImage flat = hci.flatten(10,50);
    flat.show();
    System.out.println(flat.getMaximaList(500));
    flat.makeMask("MaxEntropy dark");
    flat.showMask();
  }
}
