package storage;

import exceptions.FigureFormatException;
import exceptions.InvalidFigureException;
import geometry2d.Circle;
import geometry2d.Figure;
import geometry2d.Rectangle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Текстовое хранилище (java.nio + буферизованные потоки, UTF-8).
 *
 * <p>Формат: одна фигура в строке, поля через {@code ;}, дробная часть через точку.
 * <pre>
 * CIRCLE;радиус
 * RECTANGLE;сторонаA;сторонаB
 * </pre>
 * Пустые строки и строки, начинающиеся с {@code #}, пропускаются (но учитываются в нумерации строк).
 *
 * <p>Поведение при ошибке: первая же некорректная строка отклоняет всю загрузку —
 * выбрасывается {@link FigureFormatException} с номером строки, а уже прочитанные
 * фигуры отбрасываются (метод ничего не возвращает).
 */
public class TextFigureStorage implements FigureStorage {

    public static final String SEPARATOR = ";";
    public static final String CIRCLE = "CIRCLE";
    public static final String RECTANGLE = "RECTANGLE";

    private static final String BOM = "﻿";

    @Override
    public List<Figure> read(Path path) throws IOException, FigureFormatException {
        Objects.requireNonNull(path, "path");
        List<Figure> figures = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith(BOM)) {
                    line = line.substring(1); // файл сохранён с BOM (например, Блокнотом Windows)
                }
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                figures.add(parseLine(lineNumber, text));
            }
        }
        return figures;
    }

    @Override
    public void write(Path path, List<Figure> figures) throws IOException, FigureFormatException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(figures, "figures");

        // Сначала преобразуем все фигуры в строки: если одну из них нельзя записать,
        // существующий файл не будет затёрт наполовину.
        List<String> lines = new ArrayList<>(figures.size());
        for (int i = 0; i < figures.size(); i++) {
            lines.add(format(i + 1, figures.get(i)));
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static Figure parseLine(int lineNumber, String text) throws FigureFormatException {
        String[] parts = text.split(SEPARATOR, -1);
        String type = parts[0].strip().toUpperCase(Locale.ROOT);
        return switch (type) {
            case CIRCLE -> parseCircle(lineNumber, parts);
            case RECTANGLE -> parseRectangle(lineNumber, parts);
            default -> throw new FigureFormatException(lineNumber,
                    "неизвестный тип фигуры '" + parts[0].strip() + "' (допустимы " + CIRCLE + ", " + RECTANGLE + ")");
        };
    }

    private static Figure parseCircle(int lineNumber, String[] parts) throws FigureFormatException {
        requireParameterCount(lineNumber, CIRCLE, 1, parts);
        double radius = parseNumber(lineNumber, "radius", parts[1]);
        try {
            return new Circle(radius);
        } catch (InvalidFigureException e) {
            throw new FigureFormatException(lineNumber, e.getMessage(), e);
        }
    }

    private static Figure parseRectangle(int lineNumber, String[] parts) throws FigureFormatException {
        requireParameterCount(lineNumber, RECTANGLE, 2, parts);
        double sideA = parseNumber(lineNumber, "sideA", parts[1]);
        double sideB = parseNumber(lineNumber, "sideB", parts[2]);
        try {
            return new Rectangle(sideA, sideB);
        } catch (InvalidFigureException e) {
            throw new FigureFormatException(lineNumber, e.getMessage(), e);
        }
    }

    private static void requireParameterCount(int lineNumber, String type, int expected, String[] parts)
            throws FigureFormatException {
        int actual = parts.length - 1;
        if (actual != expected) {
            throw new FigureFormatException(lineNumber,
                    "для типа " + type + " ожидается параметров: " + expected + ", получено: " + actual);
        }
    }

    private static double parseNumber(int lineNumber, String name, String text) throws FigureFormatException {
        try {
            return Double.parseDouble(text.strip());
        } catch (NumberFormatException e) {
            throw new FigureFormatException(lineNumber,
                    "параметр '" + name + "': не удаётся преобразовать '" + text.strip() + "' в число", e);
        }
    }

    private static String format(int index, Figure figure) throws FigureFormatException {
        if (figure == null) {
            throw new FigureFormatException("Элемент списка №" + index + " равен null");
        }
        if (figure instanceof Circle circle) {
            return CIRCLE + SEPARATOR + circle.getRadius();
        }
        if (figure instanceof Rectangle rectangle) {
            return RECTANGLE + SEPARATOR + rectangle.getSideA() + SEPARATOR + rectangle.getSideB();
        }
        throw new FigureFormatException("Элемент списка №" + index + ": тип "
                + figure.getClass().getName() + " не поддерживается текстовым форматом");
    }
}
