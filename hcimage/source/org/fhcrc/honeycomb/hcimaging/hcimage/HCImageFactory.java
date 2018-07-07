package org.fhcrc.honeycomb.hcimaging.hcimage;
import org.fhcrc.honeycomb.hcimaging.hcimage.*;
import java.io.File;

public class HCImageFactory {
  public static HCImage getHCImage(File path) {
    HCImageInfo info = new HCImageInfo(path);
    if (info.filter_cube.matches("BF")) {
      return BrightfieldImage.getInstance(info);
      //return new BrightfieldImage(info); 
    }
    else {
      return FluorescenceImage.getInstance(info);
    }
  }

  public static class Test {
    public static void main(String[] args) {
      HCImage hci = null;
      if (args.length == 1) {
        try {
          hci = HCImageFactory.getHCImage(new File(args[0]));
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else {
        System.out.println(
            "\n\tUsage: java HCImageFactory$Test [path to image]\n");
      }
      System.out.println("The class of hci is " + hci.getClass().getName());
      System.out.println(hci.toString());

      //HCImage hci2 = hci.hccopy();
      System.out.println(
          "The class of hci2 is " + hci.getClass().getName());
      System.out.println("It's info is " + hci.toString());
    }
  }
} 
