package exceptions;

/**
 * Непроверяемое исключение: параметры фигуры недопустимы.
 *
 * <p>Наследуется от {@link IllegalArgumentException}, потому что причина всегда
 * одна и та же: конструктору передан неподходящий аргумент. Это ошибка
 * программиста или некорректных входных данных, а не «штатная» ситуация, поэтому
 * исключение непроверяемое: его не нужно объявлять в {@code throws}.
 */
public class InvalidFigureException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String parameter;

    /**
     * @param parameter имя параметра, например {@code radius}
     * @param reason    причина ошибки
     */
    public InvalidFigureException(String parameter, String reason) {
        super("Параметр '" + parameter + "': " + reason);
        this.parameter = parameter;
    }

    /** Имя параметра, значение которого оказалось недопустимым. */
    public String getParameter() {
        return parameter;
    }
}
