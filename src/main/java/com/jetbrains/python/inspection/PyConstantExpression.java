package com.jetbrains.python.inspection;

import static com.jetbrains.python.PyTokenTypes.COMPARISON_OPERATIONS;
import static com.jetbrains.python.PyTokenTypes.*;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import java.math.BigDecimal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyConstantExpression extends PyInspection {
    public static final String isAlways = "The condition is always ";

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
            if (checkBool(pyIfPart)) {
                return;
            }
            if (checkIntOpInt(pyIfPart)) {
                return;
            }


        }

        private boolean checkBool(@NotNull PyIfPart pyIfPart) {
            final PyExpression condition = pyIfPart.getCondition();
            if (condition instanceof PyBoolLiteralExpression) {
                registerProblem(condition, isAlways + ((PyBoolLiteralExpression) condition).getValue());
                return true;
            }
            return false;
        }

        private boolean checkIntOpInt(@NotNull PyIfPart pyIfPart) {
            final PyExpression condition = pyIfPart.getCondition();

            if (!(condition instanceof PyBinaryExpression)) {
                return false;
            }
            PyBinaryExpression binaryExpression = (PyBinaryExpression) condition;
            PyElementType operator = (binaryExpression).getOperator();

            PyExpression left = binaryExpression.getLeftExpression();
            PyExpression right = binaryExpression.getRightExpression();
            if ((RELATIONAL_OPERATIONS.contains(operator) || EQUALITY_OPERATIONS.contains(operator))
                && left instanceof PyNumericLiteralExpression
                && right instanceof PyNumericLiteralExpression) {
                BigDecimal leftValue = ((PyNumericLiteralExpression) left).getBigDecimalValue();
                BigDecimal rightValue = ((PyNumericLiteralExpression) right).getBigDecimalValue();
                registerProblem(condition, isAlways + boolValue(calculateStatement(leftValue, rightValue, operator)));
                return true;
            }
            return false;
        }

        private String boolValue(boolean b) {
            return b ? "true" : "false";
        }

        private boolean calculateStatement(BigDecimal left, BigDecimal right, PyElementType operator) {
            int result = left.compareTo(right);
            if (operator == LT) {
                return result < 0;
            }
            if (operator == GT) {
                return result > 0;
            }
            if (operator == EQEQ) {
                return result == 0;
            }
            if (operator == GE) {
                return result >= 0;
            }
            if (operator == LE) {
                return result <= 0;
            }
            if (operator == NE || operator == NE_OLD) {
                return result != 0;
            }

            //Unreachable
            return false;
        }

    }
}
