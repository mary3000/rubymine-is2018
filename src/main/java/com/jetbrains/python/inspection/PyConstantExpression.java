package com.jetbrains.python.inspection;

import static com.jetbrains.python.PyTokenTypes.*;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.icons.AllIcons.Process.Big;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.TokenSet;
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

        enum CalcResult {TRUE, FALSE, UNKNOWN}

        private void processIfPart(@NotNull PyIfPart pyIfPart) {
            final PyExpression condition = pyIfPart.getCondition();
            CalcResult result = checkBool(condition);
            if (result != CalcResult.UNKNOWN) {
                registerProblem(condition, isAlways + stringRepresentation(result));
            }
        }

        private String stringRepresentation(CalcResult result) {
            return result == CalcResult.TRUE ? "true" : "false";
        }

        private CalcResult getIntPredicateInt(BigInteger left, BigInteger right, PyElementType operator) {
            if (!TokenSet.orSet(RELATIONAL_OPERATIONS, EQUALITY_OPERATIONS).contains(operator)) {
                return CalcResult.UNKNOWN;
            }
            int expr = left.compareTo(right);
            boolean result = false;
            if (operator == LT) {
                result = expr < 0;
            }
            if (operator == GT) {
                result = expr > 0;
            }
            if (operator == EQEQ) {
                result = expr == 0;
            }
            if (operator == GE) {
                result = expr >= 0;
            }
            if (operator == LE) {
                result = expr <= 0;
            }
            if (operator == NE || operator == NE_OLD) {
                result = expr != 0;
            }
            return result ? CalcResult.TRUE : CalcResult.FALSE;
        }

        private CalcResult getBoolPredicateBool(CalcResult left, CalcResult right, PyElementType operator) {
            if (left == CalcResult.UNKNOWN || right == CalcResult.UNKNOWN) {
                return CalcResult.UNKNOWN;
            }

            boolean leftValue = left == CalcResult.TRUE;
            boolean rightValue = right == CalcResult.TRUE;
            boolean result = false;

            if (operator == AND_KEYWORD) {
                result = leftValue && rightValue;
            } else if (operator == OR_KEYWORD) {
                result = leftValue || rightValue;
            } else if (operator == NOT_KEYWORD || operator == NE || operator == NE_OLD) {
                result = leftValue != rightValue;
            } else if (operator == EQEQ) {
                result = leftValue == rightValue;
            }

            return result ? CalcResult.TRUE : CalcResult.FALSE;
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

        private CalcResult checkBool(PyExpression condition) {
            if (condition instanceof PyBoolLiteralExpression) {
                return ((PyBoolLiteralExpression)condition).getValue() ? CalcResult.TRUE : CalcResult.FALSE;
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
            return CalcResult.UNKNOWN;
        }

        private CalcResult checkBoolBinary(PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();


            CalcResult boolResult = CalcResult.UNKNOWN;
            if (TokenSet.orSet(TokenSet.create(AND_KEYWORD, OR_KEYWORD, NOT_KEYWORD), EQUALITY_OPERATIONS).contains(operator)) {

                CalcResult leftResult = checkBool(binaryExpression.getLeftExpression());
                CalcResult rightResult = checkBool(binaryExpression.getRightExpression());

                boolResult = getBoolPredicateBool(leftResult, rightResult, operator);
            }

            CalcResult numericResult = CalcResult.UNKNOWN;
            if (TokenSet.orSet(RELATIONAL_OPERATIONS, EQUALITY_OPERATIONS).contains(operator)) {

                Pair leftFlag = calcNumeric(binaryExpression.getLeftExpression());
                Pair rightFlag = calcNumeric(binaryExpression.getRightExpression());

                if ((boolean) leftFlag.getKey() && (boolean) rightFlag.getKey()) {
                    numericResult = getIntPredicateInt((BigInteger) leftFlag.getValue(),
                        (BigInteger) rightFlag.getValue(), operator);
                }
            }

            return boolResult == CalcResult.UNKNOWN ? numericResult : boolResult;
        }

        private CalcResult checkBoolPrefix(PyPrefixExpression prefixExpression) {
            PyExpression condition = prefixExpression.getOperand();
            PyElementType operator = prefixExpression.getOperator();
            CalcResult result = CalcResult.UNKNOWN;
            if (operator == NOT_KEYWORD) {
                result = checkBool(condition);
            }
            switch (result) {
                case TRUE:
                    return CalcResult.FALSE;
                case FALSE:
                    return CalcResult.TRUE;
                default:
                    return CalcResult.UNKNOWN;
            }
        }

        private Pair<Boolean, BigInteger> calcNumeric(PyExpression condition) {
            if (condition instanceof PyNumericLiteralExpression) {
                BigInteger val = ((PyNumericLiteralExpression)condition).getBigIntegerValue();
                return new Pair<>(true, val);
            }
            if (condition instanceof PyBinaryExpression) {
                return calcNumericBinary((PyBinaryExpression)condition);
            }
            if (condition instanceof PyPrefixExpression) {
                return calcNumericPrefix((PyPrefixExpression)condition);
            }
            if (condition instanceof PyParenthesizedExpression) {
                return calcNumeric(((PyParenthesizedExpression)condition).getContainedExpression());
            }
            return new Pair<>(false, null);
        }

        private Pair<Boolean, BigInteger> calcNumericBinary(PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();
            if (!TokenSet.andNot(TokenSet.orSet(ADDITIVE_OPERATIONS, MULTIPLICATIVE_OPERATIONS, STAR_OPERATORS),
                TokenSet.create(AT, DIV))
                .contains(operator)) {
                return new Pair<>(false, null);
            }

            Pair leftPair = calcNumeric(binaryExpression.getLeftExpression());
            Pair rightPair = calcNumeric(binaryExpression.getRightExpression());

            if (!(boolean)leftPair.getKey() || !(boolean)rightPair.getKey()) {
                return new Pair<>(false, null);
            }
            return getIntOpInt((BigInteger)leftPair.getValue(), (BigInteger)rightPair.getValue(), operator);
        }

        private Pair<Boolean, BigInteger> calcNumericPrefix(PyPrefixExpression prefixExpression) {
            PyElementType operator = prefixExpression.getOperator();
            PyExpression condition = prefixExpression.getOperand();

            if (operator != PLUS && operator != MINUS) {
                return new Pair<>(false, null);
            }

            Pair flag = calcNumeric(condition);
            if ((boolean)flag.getKey()) {
                if (operator == MINUS) {
                    return new Pair<>(true, ((BigInteger)flag.getValue()).negate());
                }
                return flag;
            }
            return new Pair<>(false, null);
        }

    }
}
