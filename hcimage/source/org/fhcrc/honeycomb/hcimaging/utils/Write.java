package org.fhcrc.honeycomb.hcimaging.util;

import java.util.List;

public class Write {
  public static String writeDelim(String delim, List<String> list) {
    StringBuilder sb = new StringBuilder();
    for (String part : list) {
      sb.append(part);
      sb.append("\t");
    }
    return sb.toString();
  }
}
