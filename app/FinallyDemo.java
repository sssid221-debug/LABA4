package app;

import exceptions.InvalidFigureException;
import geometry2d.Circle;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Демонстрация работы {@code finally}: блок выполняется и при успешном завершении,
 * и при исключении (строки с префиксом {@code [finally]} в выводе).
 */
public final class FinallyDemo {

    private FinallyDemo() {
    }

    /** try-with-resources + catch + finally: ресурс закрывается до catch и finally. */
    public static void readFirstLine(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            System.out.println(line == null ? "Файл пуст" : "Первая строка: " + line);
        } catch (IOException e) {
            System.out.println("Ошибка чтения файла: " + e);
        } finally {
            System.out.println("[finally] Попытка чтения завершена: " + path);
        }
    }

    /** То же для проверки параметров: исключение конструктора — {@link InvalidFigureException}. */
    public static void createCircle(double radius) {
        try {
            Circle circle = new Circle(radius);
            System.out.println("Создан " + circle);
        } catch (InvalidFigureException e) {
            System.out.println("Некорректные параметры: " + e.getMessage());
        } finally {
            System.out.println("[finally] Проверка параметров завершена (radius=" + radius + ")");
        }
    }

    public static void main(String[] args) {
        Path existing = Path.of(args.length > 0 ? args[0] : "data/figures.txt");
        System.out.println("--- успешное выполнение ---");
        readFirstLine(existing);
        createCircle(1.5);
        System.out.println("--- выполнение с исключением ---");
        readFirstLine(Path.of("no-such-file.txt"));
        createCircle(-1);
    }
}
