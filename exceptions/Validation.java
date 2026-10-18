package exceptions;

/**
 * Общие проверки параметров фигур. Все методы выбрасывают {@link InvalidFigureException}
 * с указанием параметра и причины.
 */
public final class Validation {

    private Validation() {
    }

    /** Размер должен быть положительным конечным числом (не 0, не отрицательным, не NaN, не бесконечностью). */
    public static double requirePositiveFinite(String name, double value) {
        if (Double.isNaN(value)) {
            throw new InvalidFigureException(name, "значение не является числом (NaN)");
        }
        if (Double.isInfinite(value)) {
            throw new InvalidFigureException(name, "значение должно быть конечным, получено " + value);
        }
        if (value <= 0) {
            throw new InvalidFigureException(name, "значение должно быть положительным, получено " + value);
        }
        return value;
    }

    public static <T> T requireNonNull(String name, T value) {
        if (value == null) {
            throw new InvalidFigureException(name, "значение не должно быть null");
        }
        return value;
    }
}
