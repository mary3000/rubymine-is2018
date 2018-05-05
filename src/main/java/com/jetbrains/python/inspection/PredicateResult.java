package com.jetbrains.python.inspection;

//Used to store expression result and the area of valid values for some reference (if it has)
public class PredicateResult {
  enum Result {
    //The result may be definite, true or false, or not calculated yet (unknown)
    TRUE, FALSE, UNKNOWN;
    public String stringValue() {
      return this == TRUE ? "true" : "false";
    }
  }

  public Result result;
  public String value;
  public ValueArea area;

  PredicateResult() {
    result = Result.UNKNOWN;
    value = "";
    area = new ValueArea();
  }

  PredicateResult(Result result) {
    this.result = result;
  }

  //Return (this && other)
  public PredicateResult and(PredicateResult other) {
    boolean isLeftTrue = result == Result.TRUE;
    boolean isRightTrue = other.result == Result.TRUE;
    PredicateResult answer = new PredicateResult();

    answer.result = isLeftTrue && isRightTrue ? Result.TRUE : Result.UNKNOWN;
    if (result == Result.FALSE || other.result == Result.FALSE) {
      answer.result = Result.FALSE;
    }
    //If expressions have the same value, merge the areas
    if (answer.result == Result.UNKNOWN && value.equals(other.value)) {
      answer.value = value;
      answer.area = area.and(other.area);
      if (answer.area.segments.isEmpty()) {
        answer.result = Result.FALSE;
      }
    }
    return answer;
  }

  //Return (this || other)
  public PredicateResult or(PredicateResult other) {
    boolean isLeftTrue = result == Result.TRUE;
    boolean isRightTrue = other.result == Result.TRUE;
    PredicateResult answer = new PredicateResult();

    answer.result = isLeftTrue || isRightTrue ? Result.TRUE : Result.UNKNOWN;
    if (result == Result.FALSE && other.result == Result.FALSE) {
      answer.result = Result.FALSE;
    }
    //If expressions have the same value, merge the areas
    if (answer.result == Result.UNKNOWN && value.equals(other.value)) {
      answer.value = value;
      answer.area = area.or(other.area);
      if (answer.area.segments.size() == 1 && answer.area.segments.first().left == null && answer.area.segments.first().right
          == null) {
        answer.result = Result.TRUE;
      }
    }
    return answer;
  }
}
