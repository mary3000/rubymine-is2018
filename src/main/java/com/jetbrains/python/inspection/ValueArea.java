package com.jetbrains.python.inspection;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

//Describes possible values of some reference
public class ValueArea {
  //Uses the set of segments
  public TreeSet<Segment> segments;
  private Comparator<Segment> cmp = (Segment o1, Segment o2) -> (o1.compareTo(o2));

  ValueArea() {
    segments = new TreeSet<>(cmp);
  }

  ValueArea(Segment seg) {
    segments = new TreeSet<>(cmp);
    segments.add(seg);
  }

  //Return (this && other)
  public ValueArea and(ValueArea other) {
    ValueArea answer = new ValueArea();

    for (Segment seg : other.segments) {
      answer.add(and(seg));
    }
    return answer;
  }

  //Return (this && other), other is a segment
  public ValueArea and(Segment otherSegment) {
    ValueArea answer = new ValueArea();

    for (Segment seg : segments) {
      answer.add(seg.and(otherSegment));
    }

    return answer;
  }

  //Return (this || other)
  public ValueArea or(ValueArea other) {
    ValueArea answer = new ValueArea();

    Iterator<Segment> iter = segments.iterator();
    Iterator<Segment> otherIter = other.segments.iterator();

    if (!iter.hasNext() || !otherIter.hasNext()) {
      if (iter.hasNext()) {
        answer.add(iter.next());
      }
      if (otherIter.hasNext()) {
        answer.add(otherIter.next());
      }
      return answer;
    }

    Segment seg = iter.next();
    Segment otherSeg = otherIter.next();
    while (true) {
      //If segments do not inersect:
      if (seg.compareTo(otherSeg) < 0) {
        answer.add(seg);
        if (!nextIterator(iter, otherIter, seg, otherSeg)) {
          answer.add(otherSeg);
          return answer;
        }
        continue;
      } else if (seg.compareTo(otherSeg) > 0) {
        answer.add(otherSeg);
        if (!nextIterator(otherIter, iter, otherSeg, seg)) {
          answer.add(seg);
          return answer;
        }
        continue;
      }

      //...And if they intersect (need to collapse)
      if (collapseSegments(iter, otherIter, seg, otherSeg, answer)) {
        return answer;
      }
    }
  }

  private boolean collapseSegments(Iterator<Segment> iter, Iterator<Segment> otherIter, Segment seg, Segment otherSeg, ValueArea answer) {
    BigInteger left = null;
    BigInteger right = null;
    boolean lBorder = true;
    boolean rBorder = true;

    if (seg.left != null && otherSeg.left != null) {
      if (seg.left.compareTo(otherSeg.left) < 0) {
        left = seg.left;
        lBorder = seg.includeLeft;
      } else if (seg.left.compareTo(otherSeg.left) > 0) {
        left = otherSeg.left;
        lBorder = otherSeg.includeLeft;
      } else {
        left = seg.left;
        lBorder = seg.includeLeft || otherSeg.includeLeft;
      }
    }

    while (seg.compareTo(otherSeg) == 0) {
      if (seg.right == null || otherSeg.right == null) {
        answer.add(new Segment(left, null, lBorder, true));
        return true;
      }
      if (seg.right.compareTo(otherSeg.right) <= 0) {
        right = otherSeg.right;
        rBorder = otherSeg.includeRight;
        if (seg.right.compareTo(otherSeg.right) == 0) {
          rBorder = seg.includeRight || otherSeg.includeRight;
        }
        if (!nextIterator(iter, otherIter, seg, otherSeg)) {
          answer.add(new Segment(left, right, lBorder, rBorder));
          return true;
        }
      } else if (otherSeg.right.compareTo(seg.right) < 0) {
        right = seg.right;
        rBorder = seg.includeRight;
        if (!nextIterator(otherIter, iter, otherSeg, seg)) {
          answer.add(new Segment(left, right, lBorder, rBorder));
          return true;
        }
      }
    }
    answer.add(new Segment(left, right, lBorder, rBorder));
    return false;
  }

  private boolean nextIterator(Iterator<Segment> iter, Iterator<Segment> otherIter, Segment seg, Segment otherSeg) {
    if (iter.hasNext()) {
      seg.setSegment(iter.next());
    } else if (otherIter.hasNext()) {
      otherSeg.setSegment(otherIter.next());
    } else {
      return false;
    }
    return true;
  }

  //Just collapse areas
  public ValueArea add(ValueArea other) {
    segments.addAll(other.segments);
    return this;
  }

  //Add one more segment
  public void add(Segment otherSegment) {
    if (otherSegment == null) {
      return;
    }
    segments.add(otherSegment);
  }
}