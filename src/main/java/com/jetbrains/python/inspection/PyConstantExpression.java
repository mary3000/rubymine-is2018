package com.jetbrains.python.inspection;

import static com.jetbrains.python.PyTokenTypes.*;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import java.math.BigInteger;
import java.util.List;
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

        enum PredicateResult {
            TRUE, FALSE, UNKNOWN;

            private String stringValue() {
                return this == TRUE ? "true" : "false";
            }
        }

        private void processIfPart(@NotNull PyIfPart pyIfPart) {
            final PyExpression condition = pyIfPart.getCondition();
            PredicateResult result = checkBool(condition);
            if (result != PredicateResult.UNKNOWN) {
                registerProblem(condition, isAlways + result.stringValue());
            }
        }

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

        private PredicateResult getIntPredicateInt(BigInteger left, BigInteger right, PyElementType operator) {
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
            return result ? PredicateResult.TRUE : PredicateResult.FALSE;
        }

        private PredicateResult getBoolPredicateBool(PredicateResult left, PredicateResult right, PyElementType operator) {
            boolean leftValue = left == PredicateResult.TRUE;
            boolean rightValue = right == PredicateResult.TRUE;
            PredicateResult result = PredicateResult.UNKNOWN;

            if (operator == AND_KEYWORD) {
                result = leftValue && rightValue ? PredicateResult.TRUE : PredicateResult.UNKNOWN;
                if (left == PredicateResult.FALSE || right == PredicateResult.FALSE) {
                    result = PredicateResult.FALSE;
                }
            } else if (operator == OR_KEYWORD) {
                result = leftValue || rightValue ? PredicateResult.TRUE : PredicateResult.UNKNOWN;
                if (left == PredicateResult.FALSE && right == PredicateResult.FALSE) {
                    result = PredicateResult.FALSE;
                }
            } else if (operator == NE || operator == NE_OLD) {
                result = leftValue != rightValue ? PredicateResult.TRUE : PredicateResult.FALSE;
                if (left == PredicateResult.UNKNOWN || right == PredicateResult.UNKNOWN) {
                    result = PredicateResult.UNKNOWN;
                }
            } else if (operator == EQEQ) {
                result = leftValue == rightValue ? PredicateResult.TRUE : PredicateResult.FALSE;
                if (left == PredicateResult.UNKNOWN || right == PredicateResult.UNKNOWN) {
                    result = PredicateResult.UNKNOWN;
                }
            }
            return result;
        }

        private PredicateResult checkBool(PyExpression condition) {
            if (condition instanceof PyBoolLiteralExpression) {
                return ((PyBoolLiteralExpression)condition).getValue() ? PredicateResult.TRUE : PredicateResult.FALSE;
            }
            if (condition instanceof PyBinaryExpression) {
                return checkBoolBinary((PyBinaryExpression)condition);
            }
            if (condition instanceof PyPrefixExpression) {
                return checkBoolPrefix((PyPrefixExpression)condition);
            }
            if (condition instanceof PyParenthesizedExpression) {
                return checkBool(((PyParenthesizedExpression)condition).getContainedExpression());
            }
            return PredicateResult.UNKNOWN;
        }

        private PredicateResult checkBoolBinary(PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();

            PredicateResult boolResult = PredicateResult.UNKNOWN;
            if (TokenSet.orSet(TokenSet.create(AND_KEYWORD, OR_KEYWORD), EQUALITY_OPERATIONS).contains(operator)) {

                PredicateResult leftResult = checkBool(binaryExpression.getLeftExpression());
                PredicateResult rightResult = checkBool(binaryExpression.getRightExpression());

                boolResult = getBoolPredicateBool(leftResult, rightResult, operator);
            }

            PredicateResult numericResult = PredicateResult.UNKNOWN;
            if (TokenSet.orSet(RELATIONAL_OPERATIONS, EQUALITY_OPERATIONS).contains(operator)) {

                Pair<Boolean, BigInteger> leftFlag = calcInt(binaryExpression.getLeftExpression());
                Pair<Boolean, BigInteger> rightFlag = calcInt(binaryExpression.getRightExpression());

                if (leftFlag.getKey() && rightFlag.getKey()) {
                    numericResult = getIntPredicateInt(leftFlag.getValue(), rightFlag.getValue(), operator);
                }
            }

            return boolResult == PredicateResult.UNKNOWN ? numericResult : boolResult;
        }

        private PredicateResult checkBoolPrefix(PyPrefixExpression prefixExpression) {
            PyExpression condition = prefixExpression.getOperand();
            PyElementType operator = prefixExpression.getOperator();
            PredicateResult result = PredicateResult.UNKNOWN;
            if (operator == NOT_KEYWORD) {
                result = checkBool(condition);
            }
            switch (result) {
                case TRUE:
                    return PredicateResult.FALSE;
                case FALSE:
                    return PredicateResult.TRUE;
                default:
                    return PredicateResult.UNKNOWN;
            }
        }

        private Pair<Boolean, BigInteger> calcInt(PyExpression condition) {
            if (condition instanceof PyNumericLiteralExpression) {
                BigInteger val = ((PyNumericLiteralExpression)condition).getBigIntegerValue();
                return new Pair<>(true, val);
            }
            if (condition instanceof PyBinaryExpression) {
                return calcIntBinary((PyBinaryExpression)condition);
            }
            if (condition instanceof PyPrefixExpression) {
                return calcIntPrefix((PyPrefixExpression)condition);
            }
            if (condition instanceof PyParenthesizedExpression) {
                return calcInt(((PyParenthesizedExpression)condition).getContainedExpression());
            }
            return new Pair<>(false, null);
        }

        private Pair<Boolean, BigInteger> calcIntBinary(PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();
            if (!TokenSet.andNot(TokenSet.orSet(ADDITIVE_OPERATIONS, MULTIPLICATIVE_OPERATIONS, STAR_OPERATORS),
                TokenSet.create(AT, DIV))
                .contains(operator)) {
                return new Pair<>(false, null);
            }

            Pair<Boolean, BigInteger> leftPair = calcInt(binaryExpression.getLeftExpression());
            Pair<Boolean, BigInteger> rightPair = calcInt(binaryExpression.getRightExpression());

            if (!leftPair.getKey() || !rightPair.getKey()) {
                return new Pair<>(false, null);
            }
            return getIntOpInt(leftPair.getValue(), rightPair.getValue(), operator);
        }

        private Pair<Boolean, BigInteger> calcIntPrefix(PyPrefixExpression prefixExpression) {
            PyElementType operator = prefixExpression.getOperator();
            PyExpression condition = prefixExpression.getOperand();

            if (operator != PLUS && operator != MINUS) {
                return new Pair<>(false, null);
            }

            Pair<Boolean, BigInteger> flag = calcInt(condition);
            if (flag.getKey()) {
                if (operator == MINUS) {
                    return new Pair<>(true, (flag.getValue()).negate());
                }
                return flag;
            }
            return new Pair<>(false, null);
        }

    }
}
