package comp0012.main;

import org.apache.bcel.generic.Type;

public class CmpEvaluator {
    public static boolean performComparison(
            CmpType cmpType,
            Type number1Type,
            Type number2Type,
            Number number1, Number number2) {
        NumType bestType = getBestType(number1Type, number2Type);
        if (number2 == null && !isZeroComparison(cmpType)) {
            throw new IllegalArgumentException("Number 2 is null and of type " + number2Type
                    + " Number 1 is " + number1 + " and of type " + number2Type
                    + " comparison is of type " + cmpType);
        }
        switch (cmpType) {
            case EQUAL:
                return performEqualComparison(bestType, number1, number2);
            case NOT_EQUAL:
                return !performEqualComparison(bestType, number1, number2);
            case EQUAL_ZERO:
                return performEqualComparison(bestType, number1, 0);
            case NOT_EQUAL_ZERO:
                return !performEqualComparison(bestType, number1, 0);
            case GREATER:
                return performGreaterComparison(bestType, number1, number2);
            case GREATER_EQUAL:
                return performGreaterEqualComparison(bestType, number1, number2);
            case LESS:
                return performLessComparison(bestType, number1, number2);
            case LESS_EQUAL:
                return performLessEqualComparison(bestType, number1, number2);
            case GREATER_EQUAL_ZERO:
                return performGreaterEqualComparison(bestType, number1, 0);
            case GREATER_ZERO:
                return performGreaterComparison(bestType, number1, 0);
            case LESS_EQUAL_ZERO:
                return performLessEqualComparison(bestType, number1, 0);
            case LESS_ZERO:
                return performLessComparison(bestType, number1, 0);
            default:
                throw new IllegalArgumentException("Illegal comparison type: " + cmpType);
        }
    }

    private static NumType convertToOurTypes(Type type) {
        if (type.equals(Type.DOUBLE)) {
            return NumType.DOUBLE;
        } else if (type.equals(Type.FLOAT)) {
            return NumType.FLOAT;
        } else if (type.equals(Type.INT)) {
            return NumType.INT;
        } else if (type.equals(Type.LONG)) {
            return NumType.LONG;
        } else {
            return NumType.OTHER;
        }
    }

    private static NumType getBestType(Type number1Type, Type number2Type) {
        if (number2Type == null) {
            return convertToOurTypes(number1Type);
        }
        if (number1Type.equals(number2Type)) {
            return convertToOurTypes(number1Type);
        } else {
            if (areFloatingType(number1Type, number2Type)) {
                if (convertToOurTypes(number1Type) == NumType.DOUBLE
                        || convertToOurTypes(number2Type) == NumType.DOUBLE) {
                    return NumType.DOUBLE;
                } else {
                    return NumType.FLOAT;
                }
            } else {
                if (convertToOurTypes(number1Type) == NumType.LONG
                        || convertToOurTypes(number2Type) == NumType.LONG) {
                    return NumType.LONG;
                } else {
                    return NumType.INT;
                }
            }
        }
    }

    private static boolean areFloatingType(Type number1Type, Type number2Type) {
        return number1Type.equals(Type.DOUBLE) || number2Type.equals(Type.DOUBLE)
                || number1Type.equals(Type.FLOAT) || number2Type.equals(Type.FLOAT);
    }

    private static boolean performEqualComparison(NumType typeToUse,
            Number number1, Number number2) {
        switch (typeToUse) {
            case DOUBLE:
                return number1.doubleValue() == number2.doubleValue();
            case FLOAT:
                return number1.floatValue() == number2.floatValue();
            case INT:
                return number1.intValue() == number2.intValue();
            case LONG:
                return number1.longValue() == number2.longValue();
            default:
                throw new IllegalArgumentException("ERROR in equal comparison, invalid type: " + typeToUse);
        }
    }

    private static boolean performGreaterEqualComparison(NumType typeToUse,
            Number number1, Number number2) {
        switch (typeToUse) {
            case DOUBLE:
                return number1.doubleValue() >= number2.doubleValue();
            case FLOAT:
                return number1.floatValue() >= number2.floatValue();
            case INT:
                return number1.intValue() >= number2.intValue();
            case LONG:
                return number1.longValue() >= number2.longValue();
            default:
                throw new IllegalArgumentException("ERROR in greater equal comparison, invalid type: " + typeToUse);
        }
    }

    private static boolean performLessEqualComparison(NumType typeToUse,
            Number number1, Number number2) {
        switch (typeToUse) {
            case DOUBLE:
                return number1.doubleValue() <= number2.doubleValue();
            case FLOAT:
                return number1.floatValue() <= number2.floatValue();
            case INT:
                return number1.intValue() <= number2.intValue();
            case LONG:
                return number1.longValue() <= number2.longValue();
            default:
                throw new IllegalArgumentException("ERROR in less equal comparison, invalid type: " + typeToUse);
        }
    }

    private static boolean performLessComparison(NumType typeToUse,
            Number number1, Number number2) {
        switch (typeToUse) {
            case DOUBLE:
                return number1.doubleValue() < number2.doubleValue();
            case FLOAT:
                return number1.floatValue() < number2.floatValue();
            case INT:
                return number1.intValue() < number2.intValue();
            case LONG:
                return number1.longValue() < number2.longValue();
            default:
                throw new IllegalArgumentException("ERROR in less comparison, invalid type: " + typeToUse);
        }
    }

    private static boolean performGreaterComparison(NumType typeToUse,
            Number number1, Number number2) {
        switch (typeToUse) {
            case DOUBLE:
                return number1.doubleValue() > number2.doubleValue();
            case FLOAT:
                return number1.floatValue() > number2.floatValue();
            case INT:
                return number1.intValue() > number2.intValue();
            case LONG:
                return number1.longValue() > number2.longValue();
            default:
                throw new IllegalArgumentException("ERROR in greater comparison, invalid type: " + typeToUse);
        }
    }

    public static boolean isZeroComparison(CmpType type) {
        return type == CmpType.EQUAL_ZERO
                || type == CmpType.GREATER_EQUAL_ZERO
                || type == CmpType.LESS_EQUAL_ZERO
                || type == CmpType.GREATER_ZERO
                || type == CmpType.LESS_ZERO
                || type == CmpType.NOT_EQUAL_ZERO;
    }
}
