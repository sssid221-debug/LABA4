package app;

import exceptions.FigureFormatException;
import exceptions.InvalidFigureException;
import geometry2d.Figure;
import geometry3d.Cylinder;
import storage.BinaryFigureStorage;
import storage.FigureStorage;
import storage.TextFigureStorage;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;

/**
 * Сценарий лабораторной работы № 3.
 *
 * <p>Запуск: {@code java -cp build app.Main <входной.txt> <выходной.bin> [высота]}.
 * Если пути не заданы, программа спросит их у пользователя; высота по умолчанию — {@value #DEFAULT_HEIGHT}.
 *
 * <p>Коды завершения: 0 — успех; 1 — ошибка чтения или записи файла; 2 — неверные аргументы;
 * 3 — цилиндр не создан из-за недопустимой высоты (список при этом всё равно сохраняется).
 */
public final class Main {

    static final double DEFAULT_HEIGHT = 10.0;

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, new Random());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** Вся логика вынесена из {@code main}, чтобы её можно было проверить из {@link Checks}. */
    public static int run(String[] args, Random random) {
        Path input;
        Path output;
        double height = DEFAULT_HEIGHT;
        try {
            String inputName = args.length > 0 ? args[0] : null;
            String outputName = args.length > 1 ? args[1] : null;
            if (inputName == null || outputName == null) {
                try (Scanner scanner = new Scanner(System.in)) {
                    if (inputName == null) {
                        inputName = prompt(scanner, "Путь к текстовому файлу с фигурами: ");
                    }
                    if (outputName == null) {
                        outputName = prompt(scanner, "Путь к выходному бинарному файлу: ");
                    }
                }
            }
            if (inputName == null || outputName == null) {
                System.err.println("Пути к файлам не заданы.");
                return 2;
            }
            input = Path.of(inputName);
            output = Path.of(outputName);
            if (args.length > 2) {
                height = Double.parseDouble(args[2].strip());
            }
        } catch (InvalidPathException | NumberFormatException e) {
            System.err.println("Некорректные аргументы: " + e.getMessage());
            return 2;
        }

        // Работаем только через тип интерфейса.
        FigureStorage textStorage = new TextFigureStorage();
        FigureStorage binaryStorage = new BinaryFigureStorage();

        // 1. Чтение текстового файла.
        List<Figure> figures;
        try {
            figures = textStorage.read(input);
        } catch (FigureFormatException e) {
            System.err.println("Ошибка формата файла " + input + ": " + e.getMessage());
            return 1;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Не удалось прочитать файл " + input + ": " + e);
            return 1;
        }

        // 2. Вывод фигур, площадей и периметров.
        System.out.println("Прочитано фигур: " + figures.size() + " (файл " + input + ")");
        for (int i = 0; i < figures.size(); i++) {
            Figure figure = figures.get(i);
            System.out.printf(Locale.ROOT, "%d. %s: площадь = %.4f, периметр = %.4f%n",
                    i + 1, figure, figure.area(), figure.perimeter());
        }

        // 3. Цилиндр со случайным основанием из списка.
        int exitCode = 0;
        if (figures.isEmpty()) {
            System.out.println("Список пуст: цилиндр не создаётся.");
        } else {
            int index = random.nextInt(figures.size());
            Figure base = figures.get(index);
            try {
                Cylinder cylinder = new Cylinder(base, height);
                System.out.println("Основание цилиндра (случайная фигура №" + (index + 1) + "): " + cylinder.getBase());
                System.out.println("Высота цилиндра: " + cylinder.getHeight());
                System.out.printf(Locale.ROOT, "Объём цилиндра: %.4f%n", cylinder.volume());
            } catch (InvalidFigureException e) {
                System.err.println("Цилиндр не создан: " + e.getMessage());
                exitCode = 3;
            }
        }

        // 4. Сохранение списка в бинарный файл.
        try {
            binaryStorage.write(output, figures);
            // Сообщение — после выхода из try-with-resources внутри write(): файл уже закрыт.
            System.out.println("Список сохранён в бинарный файл " + output + " (фигур: " + figures.size() + ")");
        } catch (FigureFormatException | IOException e) {
            System.err.println("Не удалось записать файл " + output + ": " + e);
            return 1;
        }
        return exitCode;
    }

    private static String prompt(Scanner scanner, String message) {
        System.out.print(message);
        return scanner.hasNextLine() ? scanner.nextLine().strip() : null;
    }
}
