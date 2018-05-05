package com.jetbrains.python.inspection;

import static com.jetbrains.python.PyTokenTypes.*;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.inspection.PredicateResult.Result;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.*;
import java.math.BigInteger;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyConstantExpression extends PyInspection {
    private static final String isAlways = "The condition is always ";

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly,
        @NotNull LocalInspectionToolSession session) {
        return new Visitor(holder, session);
    }

    private static class Visitor extends PyInspectionVisitor {

        private Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyIfStatement(PyIfStatement node) {
            super.visitPyIfStatement(node);
            processIfPart(node.getIfPart());
            for (PyIfPart part : node.getElifParts()) {
                processIfPart(part);
            }
        }

        private void processIfPart(@NotNull PyIfPart pyIfPart) {
            final PyExpression condition = pyIfPart.getCondition();
            PredicateResult result = checkBoolExpr(condition);
            if (result.result != Result.UNKNOWN) {
                registerProblem(condition, isAlways + result.result.stringValue());
            }
        }

        //Handle expressions like (int operator int), returns int
        //Boolean key means if it's possible to calculate this
        private Pair<Boolean, BigInteger> getIntOpInt(BigInteger left, BigInteger right, PyElementType operator) {
            BigInteger val = new BigInteger("0");
            if (operator == PLUS) {
                val = left.add(right);
            } else if (operator == MINUS) {
                val = left.add(right.negate());
            } else if (operator == MULT) {
                val = left.multiply(right);
            } else if (operator == FLOORDIV) {
                val = left.divide(right);
            } else if (operator == PERC) {
                val = left.mod(right);
            } else if (operator == EXP) {
                int exp;
                try {
                    exp = right.intValueExact();
                } catch (ArithmeticException e) {
                    return new Pair<>(false, null);
                }
                val = left.pow(exp);
            }
            return new Pair<>(true, val);
        }

        //Handle (int predicate int), returns bool result
        private Result getIntPredicateInt(BigInteger left, BigInteger right, PyElementType operator) {
            int compared = left.compareTo(right);
            boolean result = false;
            if (operator == LT) {
                result = compared < 0;
            } else if (operator == GT) {
                result = compared > 0;
            } else if (operator == EQEQ) {
                result = compared == 0;
            } else if (operator == GE) {
                result = compared >= 0;
            } else if (operator == LE) {
                result = compared <= 0;
            } else if (operator == NE || operator == NE_OLD) {
                result = compared != 0;
            }
            return result ? Result.TRUE : Result.FALSE;
        }

        //Handle (bool predicate bool), returns bool result + extra information
        private PredicateResult getBoolPredicateBool(PredicateResult left, PredicateResult right, PyElementType operator) {
            boolean leftValue = left.result == Result.TRUE;
            boolean rightValue = right.result == Result.TRUE;
            PredicateResult result = new PredicateResult();

            if (operator == AND_KEYWORD) {
                result = left.and(right);
            } else if (operator == OR_KEYWORD) {
                result = left.or(right);
            } else if (operator == NE || operator == NE_OLD) {
                result.result = leftValue != rightValue ? Result.TRUE : Result.FALSE;
                if (left.result == Result.UNKNOWN || right.result == Result.UNKNOWN) {
                    result.result = Result.UNKNOWN;
                    if (left.result == right.result && left.area == right.area) {
                        result.result = Result.FALSE;
                    }
                }
            } else if (operator == EQEQ) {
                result.result = leftValue == rightValue ? Result.TRUE : Result.FALSE;
                if (left.result == Result.UNKNOWN || right.result == Result.UNKNOWN) {
                    result.result = Result.UNKNOWN;
                    if (left.result == right.result && left.area == right.area) {
                        result.result = Result.TRUE;
                    }
                }
            }
            return result;
        }

        //Calculate the value of boolean expression
        private PredicateResult checkBoolExpr(PyExpression condition) {
            if (condition instanceof PyBoolLiteralExpression) {
                return ((PyBoolLiteralExpression)condition).getValue() ? new PredicateResult(Result.TRUE) : new PredicateResult(Result.FALSE);
            }
            if (condition instanceof PyBinaryExpression) {
                return checkBoolBinaryExpr((PyBinaryExpression)condition);
            }
            if (condition instanceof PyPrefixExpression) {
                return checkBoolPrefixExpr((PyPrefixExpression)condition);
            }
            if (condition instanceof PyParenthesizedExpression) {
                return checkBoolExpr(((PyParenthesizedExpression)condition).getContainedExpression());
            }
            return new PredicateResult();
        }

        private PredicateResult checkBoolBinaryExpr(PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();

            PredicateResult boolResult = new PredicateResult();
            //It may consist of boolean expressions
            if (TokenSet.orSet(TokenSet.create(AND_KEYWORD, OR_KEYWORD), EQUALITY_OPERATIONS).contains(operator)) {

                PredicateResult leftResult = checkBoolExpr(binaryExpression.getLeftExpression());
                PredicateResult rightResult = checkBoolExpr(binaryExpression.getRightExpression());

                boolResult = getBoolPredicateBool(leftResult, rightResult, operator);
                if (boolResult.result !=  Result.UNKNOWN) {
                    return boolResult;
                }
            }

            //Or it may consist of integer expressions
            if (TokenSet.orSet(RELATIONAL_OPERATIONS, EQUALITY_OPERATIONS).contains(operator)) {
                PredicateResult numericResult = new PredicateResult();

                Pair<Boolean, BigInteger> leftFlag = calcIntExpr(binaryExpression.getLeftExpression());
                Pair<Boolean, BigInteger> rightFlag = calcIntExpr(binaryExpression.getRightExpression());

                if (leftFlag.getKey() && rightFlag.getKey()) {
                    numericResult.result = getIntPredicateInt(leftFlag.getValue(),
                        rightFlag.getValue(), operator);
                } else if (binaryExpression.getLeftExpression() instanceof PyReferenceExpression
                    && rightFlag.getKey()) {
                    numericResult.value = binaryExpression.getLeftExpression().getName();
                    numericResult.area = getReferenceArea(operator, rightFlag.getValue(), false);
                } else if (binaryExpression.getRightExpression() instanceof PyReferenceExpression
                    && leftFlag.getKey()) {
                    numericResult.value = binaryExpression.getRightExpression().getName();
                    numericResult.area = getReferenceArea(operator, leftFlag.getValue(), true);
                } else {
                    return boolResult;
                }
                return numericResult;
            }
            return boolResult;
        }

        private PredicateResult checkBoolPrefixExpr(PyPrefixExpression prefixExpression) {
            PyExpression condition = prefixExpression.getOperand();
            PyElementType operator = prefixExpression.getOperator();
            PredicateResult result = new PredicateResult();
            if (operator == NOT_KEYWORD) {
                result = checkBoolExpr(condition);
            }
            switch (result.result) {
                case TRUE:
                    return new PredicateResult(Result.FALSE);
                case FALSE:
                    return new PredicateResult(Result.TRUE);
                default:
                    return new PredicateResult();
            }
        }

        //Calculate the value of integer expression
        private Pair<Boolean, BigInteger> calcIntExpr(PyExpression condition) {
            if (condition instanceof PyNumericLiteralExpression) {
                BigInteger val = ((PyNumericLiteralExpression)condition).getBigIntegerValue();
                return new Pair<>(true, val);
            }
            if (condition instanceof PyBinaryExpression) {
                return calcIntBinaryExpr((PyBinaryExpression)condition);
            }
            if (condition instanceof PyPrefixExpression) {
                return calcIntPrefixExpr((PyPrefixExpression)condition);
            }
            if (condition instanceof PyParenthesizedExpression) {
                return calcIntExpr(((PyParenthesizedExpression)condition).getContainedExpression());
            }
            return new Pair<>(false, null);
        }

        private Pair<Boolean, BigInteger> calcIntBinaryExpr(PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();
            if (!TokenSet.andNot(TokenSet.orSet(ADDITIVE_OPERATIONS, MULTIPLICATIVE_OPERATIONS, STAR_OPERATORS),
                TokenSet.create(AT, DIV))
                .contains(operator)) {
                return new Pair<>(false, null);
            }

            Pair<Boolean, BigInteger> leftPair = calcIntExpr(binaryExpression.getLeftExpression());
            Pair<Boolean, BigInteger> rightPair = calcIntExpr(binaryExpression.getRightExpression());

            if (!leftPair.getKey() || !rightPair.getKey()) {
                return new Pair<>(false, null);
            }
            return getIntOpInt(leftPair.getValue(), rightPair.getValue(), operator);
        }

        private Pair<Boolean, BigInteger> calcIntPrefixExpr(PyPrefixExpression prefixExpression) {
            PyElementType operator = prefixExpression.getOperator();
            PyExpression condition = prefixExpression.getOperand();

            if (operator != PLUS && operator != MINUS) {
                return new Pair<>(false, null);
            }

            Pair<Boolean, BigInteger> flag = calcIntExpr(condition);
            if (flag.getKey()) {
                if (operator == MINUS) {
                    return new Pair<>(true, (flag.getValue()).negate());
                }
                return flag;
            }
            return new Pair<>(false, null);
        }

        //Calculate the area of possible values for some reference
        private ValueArea getReferenceArea(PyElementType operator, BigInteger val, boolean reversed) {
            if (operator == LT && !reversed || operator == GT && reversed) {
                return new ValueArea(new Segment(null, val, true, false));
            } else if (operator == GT || operator == LT) {
                return new ValueArea(new Segment(val, null, false, true));
            } else if (operator == EQEQ) {
                return new ValueArea(new Segment(val, val, true, true));
            } else if (operator == GE && !reversed || operator == LE && reversed) {
                return new ValueArea(new Segment(val, null, true, true));
            } else if (operator == LE || operator == GE) {
                return new ValueArea(new Segment(null, val, true, true));
            } else if (operator == NE || operator == NE_OLD) {
                return new ValueArea(new Segment(null, val, true, false)).add(new ValueArea(new Segment(val, null, false, true)));
            }
            // unreachable
            return new ValueArea();
        }

    }
}
