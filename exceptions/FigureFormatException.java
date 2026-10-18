package exceptions;

/**
 * Проверяемое исключение: данные в файле не удаётся преобразовать в список фигур.
 *
 * <p>Наследуется от {@link Exception} (а не от {@code RuntimeException}), потому что
 * некорректный файл — это ожидаемая ситуация, которую вызывающий код обязан
 * обработать: сообщить пользователю и выбрать дальнейшее действие.
 */
public class FigureFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Значение {@link #getLineNumber()}, если ошибка не связана с конкретной строкой. */
    public static final int NO_LINE = -1;

    private final int lineNumber;

    /** Ошибка, не привязанная к строке файла (например, при записи или в бинарном файле). */
    public FigureFormatException(String message) {
        super(message);
        this.lineNumber = NO_LINE;
    }

    /** Ошибка в строке {@code lineNumber} текстового файла (нумерация с 1). */
    public FigureFormatException(int lineNumber, String reason) {
        super("Строка " + lineNumber + ": " + reason);
        this.lineNumber = lineNumber;
    }

    /** То же, но с сохранением исходной причины (NumberFormatException, InvalidFigureException). */
    public FigureFormatException(int lineNumber, String reason, Throwable cause) {
        super("Строка " + lineNumber + ": " + reason, cause);
        this.lineNumber = lineNumber;
    }

    /** Номер строки с ошибкой или {@link #NO_LINE}. */
    public int getLineNumber() {
        return lineNumber;
    }
}
