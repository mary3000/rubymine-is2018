package com.jetbrains.python.inspection;

import java.math.BigInteger;

public class Segment {
  //Left and right ends of the segment
  //(end == null) means (end == infinity)
  public BigInteger left = null;
  public BigInteger right = null;
  //Included/excluded ends
  public boolean includeLeft = true;
  public boolean includeRight = true;

  Segment(BigInteger left, BigInteger right, boolean includeLeft, boolean includeRight) {
    this.left = left;
    this.right = right;
    this.includeLeft = includeLeft;
    this.includeRight = includeRight;
  }

  public void setSegment(Segment other) {
    left = other.left;
    right = other.right;
    includeLeft = other.includeLeft;
    includeRight = other.includeRight;
  }

  //Return the intersection of 2 segments
  public Segment and(Segment other) {
    //Intersection is empty
    if (this.compareTo(other) != 0) {
      return null;
    }

    BigInteger left = this.left;
    boolean lBorder = includeLeft;
    if (other.left != null) {
      if (left != null) {
        if (left.compareTo(other.left) < 0) {
          left = other.left;
          lBorder = other.includeLeft;
        } else if (left.compareTo(other.left) == 0) {
          lBorder = includeLeft && other.includeLeft;
        }
      } else {
        left = other.left;
        lBorder = other.includeLeft;
      }
    }

    BigInteger right = this.right;
    boolean rBorder = includeRight;
    if (other.right != null) {
      if (right != null) {
        if (right.compareTo(other.right) > 0) {
          right = other.right;
          rBorder = other.includeRight;
        } else if (left.compareTo(other.left) == 0) {
          rBorder = includeRight && other.includeRight;
        }
      } else {
        right = other.right;
        rBorder = other.includeRight;
      }
    }
    return new Segment(left, right, lBorder, rBorder);
  }

  //Compare segments: s1 < s2 if s1.right < s2.left
  //If segments intersect, they are equal
  public int compareTo(Segment other) {
    if (left == null && right == null) {
      return 0;
    }
    if (left == null) {
      if (other.left == null) {
        return 0;
      }
      if (right.compareTo(other.left) < 0) {
        return -1;
      }
      if (right.compareTo(other.left) > 0) {
        return 0;
      }
      return includeRight || other.includeLeft ? 0 : -1;
    }
    if (other.left == null) {
      return -other.compareTo(this);
    }

    if (other.right == null) {
      if (right == null) {
        return 0;
      }
      if (right.compareTo(other.left) < 0) {
        return -1;
      }
      if (right.compareTo(other.left) > 0) {
        return 0;
      }
      return includeRight || other.includeLeft ? 0 : -1;
    }
    if (right == null) {
      return -other.compareTo(this);
    }

    if (right.compareTo(other.left) < 0) {
      return -1;
    }

    if (other.right.compareTo(left) < 0) {
      return 1;
    }

    if (right.compareTo(other.left) == 0) {
      return includeRight || other.includeLeft ? 0 : -1;
    }

    if (other.right.compareTo(left) < 0) {
      return includeLeft || other.includeRight ? 0 : 1;
    }

    return 0;
  }

}
