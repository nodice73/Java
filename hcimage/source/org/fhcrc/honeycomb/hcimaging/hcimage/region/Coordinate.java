package org.fhcrc.honeycomb.hcimaging.hcimage.region;

public class Coordinate {
  public int x, y;
  public Coordinate(int x, int y) {
    this.x = x; this.y = y;
  }
  public String toString() { return ( "("+x+","+y+")" ); }
}
